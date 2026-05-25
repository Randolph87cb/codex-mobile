import { AppServerClient, type JsonRpcNotification, type JsonRpcServerRequest } from "./app-server-client.js";
import type { BridgeEventListener, HistoryCapableBridgeRunner } from "./bridge-runner.js";
import {
  buildSessionViewFromRecord,
  buildSessionViewFromThread,
  deriveThreadLastActivityAt,
  formatThreadItemAsTranscriptBlock,
  mapThreadStatus,
  type AppServerThread,
} from "./session-view.js";
import { SessionStore } from "./session-store.js";
import type {
  AccountQuotaSnapshot,
  ApprovalDecision,
  CreateSessionInput,
  GoalCapability,
  JsonRpcRequestId,
  PendingApprovalView,
  ReasoningEffort,
  ResolvedSessionInput,
  SandboxMode,
  SessionApprovalInput,
  SessionApprovalResult,
  SessionGoal,
  SessionGoalState,
  SessionGoalUpdateInput,
  SessionRecord,
  SessionStatus,
  ServiceTier,
  SessionView,
} from "./types.js";

type ApprovalRequestMethod =
  | "item/commandExecution/requestApproval"
  | "item/fileChange/requestApproval"
  | "item/permissions/requestApproval"
  | "applyPatchApproval"
  | "execCommandApproval";

interface PendingApproval {
  requestId: JsonRpcRequestId;
  sessionId: string;
  method: ApprovalRequestMethod;
  params: Record<string, unknown>;
}

interface ActivityPayload {
  itemType: string;
  itemId?: string;
  title: string;
  body: string | null;
  transcriptBlock: string;
  summary: string;
}

interface AppServerRpcClient {
  request<TResult>(method: string, params: unknown): Promise<TResult>;
  onNotification(listener: (message: JsonRpcNotification) => void): () => void;
  onServerRequest(listener: (message: JsonRpcServerRequest) => void): () => void;
  sendResponse(id: JsonRpcRequestId, result: unknown): void;
  sendError(id: JsonRpcRequestId, code: number, message: string, data?: unknown): void;
  close(): Promise<void>;
}

interface ThreadResumeResult {
  thread: AppServerThread;
  cwd?: string;
  model?: string;
  approvalPolicy?: "on-request" | "never";
  serviceTier?: "fast" | null;
  reasoningEffort?: ReasoningEffort | null;
  sandbox?:
    | "read-only"
    | "workspace-write"
    | "danger-full-access"
    | {
        type?: "readOnly" | "workspaceWrite" | "dangerFullAccess" | "externalSandbox";
      }
    | null;
}

interface AppServerGoal {
  threadId: string;
  objective: string;
  status: string;
  tokenBudget?: number | null;
  tokensUsed?: number;
  timeUsedSeconds?: number;
  createdAt?: number | string | null;
  updatedAt?: number | string | null;
}

interface AppServerRateLimitWindow {
  usedPercent?: number;
  windowDurationMins?: number | null;
  resetsAt?: number | null;
}

interface AppServerCreditsSnapshot {
  hasCredits?: boolean;
  unlimited?: boolean;
  balance?: string | null;
}

interface AppServerRateLimitSnapshot {
  limitId?: string | null;
  primary?: AppServerRateLimitWindow | null;
  secondary?: AppServerRateLimitWindow | null;
  credits?: AppServerCreditsSnapshot | null;
  planType?: string | null;
  rateLimitReachedType?: string | null;
}

interface AppServerAccountRateLimitsResponse {
  rateLimits?: AppServerRateLimitSnapshot | null;
  rateLimitsByLimitId?: Record<string, AppServerRateLimitSnapshot | undefined> | null;
}

class ApprovalError extends Error {
  constructor(readonly code: "session-not-found" | "approval-not-found" | "approval-request-id-required") {
    super(code);
  }
}

class BusySessionError extends Error {
  constructor(readonly status: Extract<SessionStatus, "running" | "awaiting_approval">) {
    super("thread-busy");
  }
}

class GoalCapabilityError extends Error {
  constructor(readonly code: "goal-not-supported" | "goal-session-unavailable") {
    super(code);
  }
}

const ManagedAttachedApprovalMode: SessionRecord["approvalMode"] = "auto";
const ManagedAttachedSandboxMode: SessionRecord["sandboxMode"] = "danger-full-access";

export class AppServerRunner implements HistoryCapableBridgeRunner {
  readonly mode = "app-server" as const;
  private readonly listeners = new Map<string, Set<BridgeEventListener>>();
  private readonly pendingApprovals = new Map<string, PendingApproval>();
  private readonly sessionApprovalKeys = new Map<string, string[]>();
  private readonly reasoningActivityBodies = new Map<string, string>();
  private readonly goalByThreadId = new Map<string, SessionGoal | null>();
  private goalCapability: GoalCapability = "unknown";
  private readonly client: AppServerRpcClient;

  constructor(
    private readonly store: SessionStore,
    client?: AppServerRpcClient,
  ) {
    this.client =
      client ??
      new AppServerClient({
        cwd: process.cwd(),
        clientInfo: {
          name: "codex-mobile-bridge",
          version: "0.1.0",
        },
      });

    this.client.onNotification((notification) => {
      this.handleNotification(notification);
    });
    this.client.onServerRequest((request) => {
      this.handleServerRequest(request);
    });
  }

  async createSession(input: CreateSessionInput): Promise<SessionRecord> {
    const result = await this.client.request<ThreadResumeResult>("thread/start", {
      cwd: input.cwd,
      model: input.model,
      approvalPolicy: this.getApprovalPolicy(input.approvalMode),
      sandbox: input.sandboxMode,
      ...buildServiceTierParams(input.serviceTier),
    });
    return this.store.attach({
      id: result.thread.id,
      cwd: result.cwd ?? input.cwd,
      model: result.model ?? input.model,
      approvalMode: mapApprovalMode(result.approvalPolicy) ?? input.approvalMode,
      reasoningEffort: mapReasoningEffort(result.reasoningEffort) ?? input.reasoningEffort,
      serviceTier: mapServiceTier(result.serviceTier) ?? input.serviceTier,
      sandboxMode: mapSandboxMode(result.sandbox) ?? input.sandboxMode,
      status: mapThreadStatus(result.thread.status),
      threadId: result.thread.id,
      activeTurnId: null,
      lastError: null,
      createdAt:
        typeof result.thread.createdAt === "number"
          ? new Date(result.thread.createdAt * 1000).toISOString()
          : typeof result.thread.createdAt === "string" && result.thread.createdAt.trim()
            ? result.thread.createdAt
            : new Date().toISOString(),
      updatedAt:
        typeof result.thread.updatedAt === "number"
          ? new Date(result.thread.updatedAt * 1000).toISOString()
          : typeof result.thread.updatedAt === "string" && result.thread.updatedAt.trim()
            ? result.thread.updatedAt
            : new Date().toISOString(),
      lastActivityAt: resolveThreadActivityTimestamp(result.thread),
    });
  }

  async submitInput(sessionId: string, input: ResolvedSessionInput): Promise<void> {
    const originalSession = this.store.get(sessionId);
    const session = originalSession ? await this.syncSessionWithThreadRead(originalSession) : undefined;
    if (!session?.threadId) {
      throw new Error("thread-not-initialized");
    }
    if (session.status === "running" || session.status === "awaiting_approval") {
      throw new BusySessionError(session.status);
    }

    this.store.update(sessionId, {
      status: "running",
      lastError: null,
      lastActivityAt: new Date().toISOString(),
    });
    this.emit(sessionId, {
      type: "run.status",
      sessionId,
      timestamp: new Date().toISOString(),
      data: { status: "running", mode: this.mode },
    });

    let result: { turn: { id: string } };
    try {
      result = await this.startTurnWithResumeRetry(session, input);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.store.update(sessionId, {
        status: "error",
        lastError: message,
      });
      this.emit(sessionId, {
        type: "error",
        sessionId,
        timestamp: new Date().toISOString(),
        data: {
          error: {
            message,
          },
        },
      });
      this.emitStatus(sessionId, "error", {
        reason: "turn-start-validation-failed",
      });
      throw error;
    }

    this.store.update(sessionId, {
      activeTurnId: result.turn.id,
      status: "running",
    });
  }

  async interrupt(sessionId: string): Promise<void> {
    const originalSession = this.store.get(sessionId);
    const session = originalSession ? await this.syncSessionWithThreadRead(originalSession) : undefined;
    if (!session?.threadId || !session.activeTurnId) {
      return;
    }

    await this.client.request("turn/interrupt", {
      threadId: session.threadId,
      turnId: session.activeTurnId,
    });

    this.store.update(sessionId, {
      activeTurnId: null,
      status: "idle",
    });
    this.clearPendingApprovals(sessionId);
    this.clearActivityState(sessionId);
    this.emit(sessionId, {
      type: "run.interrupted",
      sessionId,
      timestamp: new Date().toISOString(),
      data: { status: "idle" },
    });
  }

  async approve(sessionId: string, input: SessionApprovalInput): Promise<SessionApprovalResult> {
    const session = this.store.get(sessionId);
    if (!session) {
      throw new ApprovalError("session-not-found");
    }

    const pending = this.takePendingApproval(sessionId, input.requestId);
    if (!pending) {
      throw new ApprovalError(input.requestId === undefined ? "approval-not-found" : "approval-not-found");
    }

    const decision = input.decision ?? "approve";
    const response = this.buildApprovalResponse(pending, decision);
    this.client.sendResponse(pending.requestId, response);

    const status = this.getStatusAfterApproval(sessionId, pending.method, decision);
    this.store.update(sessionId, { status, lastError: null });
    this.emit(sessionId, {
      type: "tool.result",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        requestId: pending.requestId,
        method: pending.method,
        decision,
        result: response,
      },
    });
    this.emitStatus(sessionId, status, {
      requestId: pending.requestId,
      method: pending.method,
      decision,
    });

    return {
      requestId: pending.requestId,
      status,
      decision,
      method: pending.method,
    };
  }

  subscribe(sessionId: string, listener: BridgeEventListener): () => void {
    const set = this.listeners.get(sessionId) ?? new Set<BridgeEventListener>();
    set.add(listener);
    this.listeners.set(sessionId, set);

    return () => {
      const current = this.listeners.get(sessionId);
      current?.delete(listener);
      if (current && current.size === 0) {
        this.listeners.delete(sessionId);
      }
    };
  }

  async close(): Promise<void> {
    await this.client.close();
  }

  async listSessionViews(archived = false): Promise<SessionView[]> {
    const threads = await this.listThreads(archived);
    const views = threads.map((thread) => {
      const session = this.store.findByThreadId(thread.id);
      return buildSessionViewFromThread(
        thread,
        session,
        session ? this.getPendingApprovalView(session.id) : null,
        archived,
      );
    });

    if (archived) {
      return views.sort((left, right) => right.lastUpdated.localeCompare(left.lastUpdated));
    }

    const knownThreadIds = new Set(threads.map((thread) => thread.id));
    const archivedThreadIds = new Set((await this.listThreads(true)).map((thread) => thread.id));
    for (const session of this.store.list()) {
      if (knownThreadIds.has(session.id) || archivedThreadIds.has(session.id)) {
        continue;
      }
      views.push(buildSessionViewFromRecord(session, this.getPendingApprovalView(session.id)));
    }

    return views.sort((left, right) => right.lastUpdated.localeCompare(left.lastUpdated));
  }

  async getSessionView(threadId: string): Promise<SessionView | null> {
    const session = this.store.get(threadId);
    if (session?.threadId) {
      const thread = await this.tryReadThread(session.threadId);
      if (!thread) {
        return this.decorateSessionViewWithGoal(
          buildSessionViewFromRecord(session, this.getPendingApprovalView(session.id)),
        );
      }

      const synced = this.applyThreadSnapshot(session, thread);
      return this.decorateSessionViewWithGoal(
        buildSessionViewFromThread(thread, synced, this.getPendingApprovalView(synced.id)),
      );
    }

    if (session) {
      return this.decorateSessionViewWithGoal(
        buildSessionViewFromRecord(session, this.getPendingApprovalView(session.id)),
      );
    }

    const thread = await this.tryReadThread(threadId);
    return thread ? this.decorateSessionViewWithGoal(buildSessionViewFromThread(thread)) : null;
  }

  async getAccountQuota(): Promise<AccountQuotaSnapshot> {
    const result = await this.client.request<AppServerAccountRateLimitsResponse>("account/rateLimits/read", undefined);
    return mapAccountQuotaSnapshot(result);
  }

  async getSessionGoal(sessionId: string): Promise<SessionGoalState> {
    const threadId = await this.resolveGoalThreadId(sessionId);
    if (!threadId) {
      throw new GoalCapabilityError("goal-session-unavailable");
    }

    return this.readGoalState(threadId);
  }

  async updateSessionGoal(sessionId: string, input: SessionGoalUpdateInput): Promise<SessionGoalState> {
    const threadId = await this.resolveGoalThreadId(sessionId);
    if (!threadId) {
      throw new GoalCapabilityError("goal-session-unavailable");
    }

    try {
      const result = await this.client.request<{ goal: AppServerGoal }>("thread/goal/set", {
        threadId,
        ...(input.objective !== undefined ? { objective: input.objective } : {}),
        ...(input.status !== undefined ? { status: input.status } : {}),
        ...(input.tokenBudget !== undefined ? { tokenBudget: input.tokenBudget } : {}),
      });
      const goal = mapSessionGoal(result.goal);
      this.goalCapability = "supported";
      this.goalByThreadId.set(threadId, goal);
      return {
        capability: this.goalCapability,
        goal,
      };
    } catch (error) {
      if (isGoalUnsupportedError(error)) {
        this.goalCapability = "unsupported";
        throw new GoalCapabilityError("goal-not-supported");
      }
      throw error;
    }
  }

  async clearSessionGoal(sessionId: string): Promise<{ capability: GoalCapability; cleared: boolean }> {
    const threadId = await this.resolveGoalThreadId(sessionId);
    if (!threadId) {
      throw new GoalCapabilityError("goal-session-unavailable");
    }

    try {
      const result = await this.client.request<{ cleared?: boolean }>("thread/goal/clear", { threadId });
      this.goalCapability = "supported";
      this.goalByThreadId.set(threadId, null);
      return {
        capability: this.goalCapability,
        cleared: result.cleared ?? true,
      };
    } catch (error) {
      if (isGoalUnsupportedError(error)) {
        this.goalCapability = "unsupported";
        throw new GoalCapabilityError("goal-not-supported");
      }
      throw error;
    }
  }

  async attachSession(threadId: string): Promise<SessionRecord | null> {
    const existing = this.store.get(threadId);
    if (existing) {
      const managed = this.enforceManagedSessionPolicies(existing);
      return (await this.refreshAttachedSession(managed)) ?? managed;
    }

    const resumed = await this.tryResumeThread(threadId, {
      approvalMode: ManagedAttachedApprovalMode,
      sandboxMode: ManagedAttachedSandboxMode,
    });
    if (resumed) {
      return this.syncSessionWithThreadRead(this.attachResumedThread(resumed));
    }

    const thread = await this.tryReadThread(threadId);
    if (!thread) {
      return null;
    }

    const attached = this.store.attach({
      id: thread.id,
      cwd: thread.cwd,
      model: thread.modelProvider ?? "openai",
      approvalMode: ManagedAttachedApprovalMode,
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: ManagedAttachedSandboxMode,
      status: mapThreadStatus(thread.status),
      threadId: thread.id,
      activeTurnId: null,
      lastError: null,
      createdAt:
        typeof thread.createdAt === "number"
          ? new Date(thread.createdAt * 1000).toISOString()
          : typeof thread.createdAt === "string" && thread.createdAt.trim()
            ? thread.createdAt
            : new Date().toISOString(),
      updatedAt:
        typeof thread.updatedAt === "number"
          ? new Date(thread.updatedAt * 1000).toISOString()
          : typeof thread.updatedAt === "string" && thread.updatedAt.trim()
            ? thread.updatedAt
            : new Date().toISOString(),
    });
    return this.applyThreadSnapshot(attached, thread);
  }

  async archiveSession(sessionId: string): Promise<void> {
    const threadId = await this.resolveArchivableThreadId(sessionId);
    if (!threadId) {
      throw new Error("session-not-archivable");
    }

    await this.client.request("thread/archive", { threadId });
    const attached = this.store.get(threadId);
    const resolvedSessionId = attached?.id ?? threadId;
    if (attached) {
      this.store.delete(threadId);
    }
    this.goalByThreadId.delete(threadId);
    this.clearPendingApprovals(resolvedSessionId);
    this.clearActivityState(resolvedSessionId);
  }

  async unarchiveSession(sessionId: string): Promise<void> {
    const threadId = await this.resolveArchivableThreadId(sessionId);
    if (!threadId) {
      throw new Error("session-not-archivable");
    }

    await this.client.request("thread/unarchive", { threadId });
  }

  private enforceManagedSessionPolicies(session: SessionRecord): SessionRecord {
    if (
      session.approvalMode === ManagedAttachedApprovalMode &&
      session.sandboxMode === ManagedAttachedSandboxMode
    ) {
      return session;
    }

    return (
      this.store.update(session.id, {
        approvalMode: ManagedAttachedApprovalMode,
        sandboxMode: ManagedAttachedSandboxMode,
      }) ?? {
        ...session,
        approvalMode: ManagedAttachedApprovalMode,
        sandboxMode: ManagedAttachedSandboxMode,
      }
    );
  }

  private handleNotification(notification: JsonRpcNotification): void {
    switch (notification.method) {
      case "thread/goal/updated":
        this.handleGoalUpdated(notification.params);
        break;
      case "thread/goal/cleared":
        this.handleGoalCleared(notification.params);
        break;
      case "thread/status/changed":
        this.handleThreadStatusChanged(notification.params);
        break;
      case "turn/started":
        this.handleTurnStarted(notification.params);
        break;
      case "item/agentMessage/delta":
        this.handleAgentMessageDelta(notification.params);
        break;
      case "item/started":
        this.handleItemLifecycleNotification(notification.params);
        break;
      case "item/completed":
        this.handleItemLifecycleNotification(notification.params);
        break;
      case "item/fileChange/patchUpdated":
        this.handleFileChangePatchUpdated(notification.params);
        break;
      case "item/mcpToolCall/progress":
        this.handleMcpToolCallProgress(notification.params);
        break;
      case "item/reasoning/summaryTextDelta":
        this.handleReasoningSummaryTextDelta(notification.params);
        break;
      case "turn/completed":
        this.handleTurnCompleted(notification.params);
        break;
      case "error":
        this.handleError(notification.params);
        break;
      default:
        break;
    }
  }

  private handleServerRequest(request: JsonRpcServerRequest): void {
    if (!isApprovalRequestMethod(request.method)) {
      this.client.sendError(
        request.id,
        -32601,
        `Server request ${request.method} is not supported by codex-mobile-bridge yet.`,
      );
      return;
    }

    const params = isRecord(request.params) ? request.params : {};
    const threadId = typeof params.threadId === "string" ? params.threadId : null;
    const sessionId = threadId ? this.resolveSessionIdByThreadId(threadId) : undefined;
    if (!sessionId) {
      this.client.sendError(request.id, -32000, "approval-session-not-found");
      return;
    }

    this.addPendingApproval({
      requestId: request.id,
      sessionId,
      method: request.method,
      params,
    });
    this.store.update(sessionId, { status: "awaiting_approval" });
    this.emitStatus(sessionId, "awaiting_approval", {
      requestId: request.id,
      method: request.method,
    });
    this.emit(sessionId, {
      type: "tool.request",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        requestId: request.id,
        method: request.method,
        params,
      },
    });
  }

  private handleGoalUpdated(params: unknown): void {
    const payload = params as {
      threadId: string;
      goal?: AppServerGoal;
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId || !payload.goal) {
      return;
    }

    const goal = mapSessionGoal(payload.goal);
    this.goalCapability = "supported";
    this.goalByThreadId.set(payload.threadId, goal);
    this.emit(sessionId, {
      type: "goal.updated",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        goal,
        goalCapability: this.goalCapability,
      },
    });
  }

  private handleGoalCleared(params: unknown): void {
    const payload = params as {
      threadId: string;
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    this.goalCapability = "supported";
    this.goalByThreadId.set(payload.threadId, null);
    this.emit(sessionId, {
      type: "goal.cleared",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        goalCapability: this.goalCapability,
      },
    });
  }

  private handleThreadStatusChanged(params: unknown): void {
    const payload = params as {
      threadId: string;
      status: {
        type: string;
        activeFlags?: string[];
      };
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    const status =
      payload.status.type === "active" && payload.status.activeFlags?.includes("waitingOnApproval")
        ? "awaiting_approval"
        : payload.status.type === "active"
          ? "running"
          : payload.status.type === "systemError"
            ? "error"
            : "idle";

    const patch: Partial<Omit<SessionRecord, "id" | "createdAt">> = { status };
    if (status === "idle" || status === "error") {
      patch.activeTurnId = null;
    }
    if (status !== "error") {
      patch.lastError = null;
    }

    this.store.update(sessionId, patch);
    if (status !== "awaiting_approval") {
      this.clearPendingApprovals(sessionId);
    }
    if (status === "idle" || status === "error") {
      this.clearActivityState(sessionId);
    }
    this.emitStatus(sessionId, status, { sourceStatus: payload.status });
  }

  private handleTurnStarted(params: unknown): void {
    const payload = params as {
      threadId: string;
      turn: {
        id: string;
      };
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    this.store.update(sessionId, {
      activeTurnId: payload.turn.id,
      status: "running",
      lastActivityAt: new Date().toISOString(),
    });
  }

  private handleAgentMessageDelta(params: unknown): void {
    const payload = params as {
      threadId: string;
      turnId: string;
      itemId: string;
      delta: string;
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    this.emit(sessionId, {
      type: "assistant.delta",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        text: payload.delta,
        turnId: payload.turnId,
        itemId: payload.itemId,
      },
    });
    this.store.update(sessionId, {
      lastActivityAt: new Date().toISOString(),
    });
  }

  private handleTurnCompleted(params: unknown): void {
    const payload = params as {
      threadId: string;
      turn: {
        id: string;
        status: "completed" | "interrupted" | "failed" | "inProgress";
        error: unknown;
      };
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    const status = payload.turn.status === "failed" ? "error" : "idle";
    this.clearPendingApprovals(sessionId);
    this.clearActivityState(sessionId);
    this.store.update(sessionId, {
      activeTurnId: null,
      status,
      lastError: payload.turn.status === "failed" ? JSON.stringify(payload.turn.error) : null,
      lastActivityAt: new Date().toISOString(),
    });

    this.emit(sessionId, {
      type: payload.turn.status === "interrupted" ? "run.interrupted" : "assistant.done",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        turnId: payload.turn.id,
        status: payload.turn.status,
        error: payload.turn.error,
      },
    });
    this.emitStatus(sessionId, status, {
      turnId: payload.turn.id,
      sourceTurnStatus: payload.turn.status,
    });
  }

  private handleItemLifecycleNotification(params: unknown): void {
    const payload = params as {
      threadId: string;
      turnId: string;
      item: {
        id?: string;
        type?: string;
      };
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    const activity = buildActivityPayload(
      payload.item.type ?? "unknown",
      payload.item.id,
      formatThreadItemAsTranscriptBlock(payload.item),
    );
    if (!activity) {
      return;
    }

    this.emitActivity(sessionId, activity);
  }

  private handleFileChangePatchUpdated(params: unknown): void {
    const payload = params as {
      threadId: string;
      itemId: string;
      changes?: Array<{
        path?: string;
        kind?: string;
      }>;
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    const changedPaths = (payload.changes ?? [])
      .map((change) => change.path?.trim())
      .filter((path): path is string => Boolean(path));
    if (changedPaths.length === 0) {
      return;
    }

    this.emitActivity(sessionId, {
      itemType: "fileChange",
      itemId: payload.itemId,
      title: "文件修改进度",
      body: changedPaths.slice(0, 6).map((path) => `涉及：${path}`).join("\n"),
      transcriptBlock: [
        "系统：文件修改进度",
        ...changedPaths.slice(0, 6).map((path) => `涉及：${path}`),
      ].join("\n"),
      summary: "文件修改进度",
    });
  }

  private handleMcpToolCallProgress(params: unknown): void {
    const payload = params as {
      threadId: string;
      itemId: string;
      message?: string;
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    const message = payload.message?.trim();
    if (!message) {
      return;
    }

    this.emitActivity(sessionId, {
      itemType: "mcpToolCall",
      itemId: payload.itemId,
      title: "工具调用进度",
      body: message,
      transcriptBlock: `系统：工具调用进度\n${message}`,
      summary: message,
    });
  }

  private handleReasoningSummaryTextDelta(params: unknown): void {
    const payload = params as {
      threadId: string;
      itemId: string;
      delta?: string;
    };
    const sessionId = this.resolveSessionIdByThreadId(payload.threadId);
    if (!sessionId) {
      return;
    }

    const delta = payload.delta?.trim();
    if (!delta) {
      return;
    }

    const activityKey = buildActivityKey(sessionId, payload.itemId, "reasoning");
    const nextBody = mergeActivityBodies(this.reasoningActivityBodies.get(activityKey) ?? null, delta);
    this.reasoningActivityBodies.set(activityKey, nextBody);
    this.emitActivity(sessionId, {
      itemType: "reasoning",
      itemId: payload.itemId,
      title: "推理摘要",
      body: nextBody,
      transcriptBlock: `系统：推理摘要\n${nextBody}`,
      summary: delta,
    });
  }

  private handleError(params: unknown): void {
    for (const [sessionId] of this.listeners) {
      this.emit(sessionId, {
        type: "error",
        sessionId,
        timestamp: new Date().toISOString(),
        data: {
          error: params ?? {},
        },
      });
    }
  }

  private emit(sessionId: string, event: Parameters<BridgeEventListener>[0]): void {
    const listeners = this.listeners.get(sessionId);
    if (!listeners) {
      return;
    }

    for (const listener of listeners) {
      listener(event);
    }
  }

  private async decorateSessionViewWithGoal(view: SessionView): Promise<SessionView> {
    const threadId = view.threadId;
    if (!threadId) {
      return {
        ...view,
        goal: null,
        goalCapability: "unsupported",
      };
    }

    const goalState = await this.readGoalState(threadId);
    return {
      ...view,
      goal: goalState.goal,
      goalCapability: goalState.capability,
    };
  }

  private async readGoalState(threadId: string): Promise<SessionGoalState> {
    if (this.goalCapability === "unsupported") {
      return {
        capability: this.goalCapability,
        goal: null,
      };
    }

    try {
      const result = await this.client.request<{ goal: AppServerGoal | null }>("thread/goal/get", { threadId });
      const goal = result.goal ? mapSessionGoal(result.goal) : null;
      this.goalCapability = "supported";
      this.goalByThreadId.set(threadId, goal);
      return {
        capability: this.goalCapability,
        goal,
      };
    } catch (error) {
      if (isGoalUnsupportedError(error)) {
        this.goalCapability = "unsupported";
        this.goalByThreadId.delete(threadId);
        return {
          capability: this.goalCapability,
          goal: null,
        };
      }
      throw error;
    }
  }

  private async tryReadThread(threadId: string): Promise<AppServerThread | null> {
    try {
      const result = await this.client.request<{ thread: AppServerThread }>("thread/read", {
        threadId,
        includeTurns: true,
      });
      return result.thread;
    } catch {
      return null;
    }
  }

  private async listThreads(archived: boolean): Promise<AppServerThread[]> {
    const result = await this.client.request<{ data: AppServerThread[] }>("thread/list", {
      archived,
      sortDirection: "desc",
      sortKey: "updated_at",
    });
    return result.data;
  }

  private async startTurnWithResumeRetry(
    session: SessionRecord,
    input: ResolvedSessionInput,
  ): Promise<{ turn: { id: string } }> {
    try {
      return await this.startTurn(session, input);
    } catch (error) {
      if (!shouldResumeMissingThread(error)) {
        throw error;
      }

      const resumed = await this.resumeThreadSession(session);
      return this.startTurn(resumed, input);
    }
  }

  private async startTurn(
    session: SessionRecord,
    input: ResolvedSessionInput,
  ): Promise<{ turn: { id: string } }> {
    return this.client.request<{ turn: { id: string } }>("turn/start", {
      threadId: session.threadId,
      cwd: session.cwd,
      model: session.model,
      approvalPolicy: this.getApprovalPolicy(session.approvalMode),
      sandboxPolicy: buildSandboxPolicy(session.sandboxMode, session.cwd),
      effort: session.reasoningEffort,
      ...buildServiceTierParams(session.serviceTier),
      input: buildTurnInput(input),
    });
  }

  private async tryResumeAttachedSession(session: SessionRecord): Promise<SessionRecord | null> {
    try {
      return await this.resumeThreadSession(session);
    } catch {
      return null;
    }
  }

  private async refreshAttachedSession(session: SessionRecord): Promise<SessionRecord | null> {
    const resumed = (await this.tryResumeAttachedSession(session)) ?? session;
    return this.syncSessionWithThreadRead(resumed);
  }

  private async resumeThreadSession(session: SessionRecord): Promise<SessionRecord> {
    if (!session.threadId) {
      return session;
    }

    const resumed = await this.client.request<ThreadResumeResult>("thread/resume", {
      threadId: session.threadId,
      cwd: session.cwd,
      model: session.model,
      approvalPolicy: this.getApprovalPolicy(session.approvalMode),
      sandbox: session.sandboxMode,
      excludeTurns: true,
      ...buildServiceTierParams(session.serviceTier),
    });

    return (
      this.store.update(session.id, {
        threadId: resumed.thread.id,
        cwd: resumed.cwd ?? session.cwd,
        model: resumed.model ?? session.model,
        approvalMode: mapApprovalMode(resumed.approvalPolicy) ?? session.approvalMode,
        reasoningEffort: mapReasoningEffort(resumed.reasoningEffort) ?? session.reasoningEffort,
        serviceTier: mapServiceTier(resumed.serviceTier) ?? session.serviceTier,
        sandboxMode: mapSandboxMode(resumed.sandbox) ?? session.sandboxMode,
        status: mapThreadStatus(resumed.thread.status),
        lastError: null,
      }) ?? session
    );
  }

  private async tryResumeThread(
    threadId: string,
    sessionOverrides?: Pick<SessionRecord, "approvalMode" | "sandboxMode">,
  ): Promise<ThreadResumeResult | null> {
    try {
      return await this.client.request<ThreadResumeResult>("thread/resume", {
        threadId,
        approvalPolicy: sessionOverrides
          ? this.getApprovalPolicy(sessionOverrides.approvalMode)
          : undefined,
        sandbox: sessionOverrides?.sandboxMode,
        excludeTurns: true,
      });
    } catch {
      if (!sessionOverrides) {
        return null;
      }

      try {
        return await this.client.request<ThreadResumeResult>("thread/resume", {
          threadId,
          excludeTurns: true,
        });
      } catch {
        return null;
      }
    }
  }

  private async resolveArchivableThreadId(sessionId: string): Promise<string | null> {
    const direct = this.store.get(sessionId);
    if (direct?.threadId) {
      return direct.threadId;
    }

    if (await this.tryReadThread(sessionId)) {
      return sessionId;
    }

    return null;
  }

  private async resolveGoalThreadId(sessionId: string): Promise<string | null> {
    return this.resolveArchivableThreadId(sessionId);
  }

  private attachResumedThread(resumed: ThreadResumeResult): SessionRecord {
    return this.store.attach({
      id: resumed.thread.id,
      cwd: resumed.cwd ?? resumed.thread.cwd,
      model: resumed.model ?? resumed.thread.modelProvider ?? "openai",
      approvalMode: mapApprovalMode(resumed.approvalPolicy) ?? ManagedAttachedApprovalMode,
      reasoningEffort: mapReasoningEffort(resumed.reasoningEffort) ?? "medium",
      serviceTier: mapServiceTier(resumed.serviceTier) ?? "default",
      sandboxMode: mapSandboxMode(resumed.sandbox) ?? ManagedAttachedSandboxMode,
      status: mapThreadStatus(resumed.thread.status),
      threadId: resumed.thread.id,
      activeTurnId: null,
      lastError: null,
      createdAt:
        typeof resumed.thread.createdAt === "number"
          ? new Date(resumed.thread.createdAt * 1000).toISOString()
          : typeof resumed.thread.createdAt === "string" && resumed.thread.createdAt.trim()
            ? resumed.thread.createdAt
            : new Date().toISOString(),
      updatedAt:
        typeof resumed.thread.updatedAt === "number"
          ? new Date(resumed.thread.updatedAt * 1000).toISOString()
          : typeof resumed.thread.updatedAt === "string" && resumed.thread.updatedAt.trim()
            ? resumed.thread.updatedAt
            : new Date().toISOString(),
      lastActivityAt: resolveThreadActivityTimestamp(resumed.thread),
    });
  }

  private async syncSessionWithThreadRead(session: SessionRecord): Promise<SessionRecord> {
    if (!session.threadId) {
      return session;
    }

    const thread = await this.tryReadThread(session.threadId);
    if (!thread) {
      return session;
    }

    return this.applyThreadSnapshot(session, thread);
  }

  private applyThreadSnapshot(session: SessionRecord, thread: AppServerThread): SessionRecord {
    const status = this.normalizeThreadStatusForSession(
      session.threadId ?? session.id,
      mapThreadStatus(thread.status),
    );
    const activeTurnId = findLatestActiveTurnId(thread);
    const lastError = extractLatestThreadError(thread);
    return (
      this.store.update(session.id, {
        cwd: thread.cwd || session.cwd,
        status,
        activeTurnId:
          activeTurnId ??
          (status === "running" || status === "awaiting_approval" ? session.activeTurnId : null),
        lastError: status === "error" ? lastError ?? session.lastError : lastError,
        lastActivityAt: deriveThreadLastActivityAt(thread) ?? session.lastActivityAt,
      }) ?? session
    );
  }

  private getApprovalPolicy(mode: SessionRecord["approvalMode"]): "on-request" | "never" {
    return mode === "manual" ? "on-request" : "never";
  }

  private resolveSessionIdByThreadId(threadId: string): string | undefined {
    return this.store.get(threadId)?.id ?? this.store.findByThreadId(threadId)?.id;
  }

  private emitStatus(sessionId: string, status: SessionStatus, extraData: Record<string, unknown> = {}): void {
    this.emit(sessionId, {
      type: "run.status",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        status,
        ...extraData,
      },
    });
  }

  private getPendingApprovalView(sessionId: string): PendingApprovalView | null {
    const requestKey = this.sessionApprovalKeys.get(sessionId)?.at(-1);
    if (!requestKey) {
      return null;
    }

    const pending = this.pendingApprovals.get(requestKey);
    if (!pending) {
      return null;
    }

    return {
      requestId: pending.requestId,
      method: pending.method,
      paramsSummary: formatPendingApprovalSummary(pending.method, pending.params),
    };
  }

  private normalizeThreadStatusForSession(sessionId: string, status: SessionStatus): SessionStatus {
    if (status !== "awaiting_approval") {
      return status;
    }

    return this.getPendingApprovalView(sessionId) ? "awaiting_approval" : "running";
  }

  private addPendingApproval(pending: PendingApproval): void {
    const requestKey = this.toRequestKey(pending.requestId);
    this.pendingApprovals.set(requestKey, pending);

    const keys = this.sessionApprovalKeys.get(pending.sessionId) ?? [];
    keys.push(requestKey);
    this.sessionApprovalKeys.set(pending.sessionId, keys);
  }

  private takePendingApproval(sessionId: string, requestId?: JsonRpcRequestId): PendingApproval | null {
    const keys = [...(this.sessionApprovalKeys.get(sessionId) ?? [])];
    if (keys.length === 0) {
      return null;
    }

    let requestKey: string | undefined;
    if (requestId !== undefined) {
      requestKey = this.toRequestKey(requestId);
      if (!keys.includes(requestKey)) {
        return null;
      }
    } else if (keys.length === 1) {
      [requestKey] = keys;
    } else {
      throw new ApprovalError("approval-request-id-required");
    }

    const pending = requestKey ? this.pendingApprovals.get(requestKey) ?? null : null;
    if (!pending || !requestKey) {
      return null;
    }

    this.pendingApprovals.delete(requestKey);
    this.sessionApprovalKeys.set(
      sessionId,
      keys.filter((key) => key !== requestKey),
    );
    return pending;
  }

  private clearPendingApprovals(sessionId: string): void {
    const keys = this.sessionApprovalKeys.get(sessionId) ?? [];
    for (const key of keys) {
      this.pendingApprovals.delete(key);
    }
    this.sessionApprovalKeys.delete(sessionId);
  }

  private clearActivityState(sessionId: string): void {
    for (const key of this.reasoningActivityBodies.keys()) {
      if (key.startsWith(`${sessionId}:`)) {
        this.reasoningActivityBodies.delete(key);
      }
    }
  }

  private emitActivity(sessionId: string, activity: ActivityPayload): void {
    this.store.update(sessionId, {
      lastActivityAt: new Date().toISOString(),
    });
    this.emit(sessionId, {
      type: "activity",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        itemType: activity.itemType,
        itemId: activity.itemId,
        title: activity.title,
        body: activity.body,
        transcriptBlock: activity.transcriptBlock,
        summary: activity.summary,
      },
    });
  }

  private buildApprovalResponse(pending: PendingApproval, decision: ApprovalDecision): Record<string, unknown> {
    switch (pending.method) {
      case "item/commandExecution/requestApproval":
        return { decision: mapTurnApprovalDecision(decision) };
      case "item/fileChange/requestApproval":
        return { decision: mapTurnApprovalDecision(decision) };
      case "item/permissions/requestApproval":
        return decision === "approve_for_session"
          ? {
              permissions: getPermissionsPayload(pending.params),
              scope: "session",
            }
          : decision === "approve"
            ? {
                permissions: getPermissionsPayload(pending.params),
                scope: "turn",
              }
            : {
                permissions: {},
              };
      case "applyPatchApproval":
      case "execCommandApproval":
        return { decision: mapLegacyApprovalDecision(decision) };
    }
  }

  private getStatusAfterApproval(
    sessionId: string,
    method: ApprovalRequestMethod,
    decision: ApprovalDecision,
  ): SessionStatus {
    const remaining = this.sessionApprovalKeys.get(sessionId) ?? [];
    if (remaining.length > 0) {
      return "awaiting_approval";
    }

    return isImmediateInterruptDecision(method, decision) ? "idle" : "running";
  }

  private toRequestKey(requestId: JsonRpcRequestId): string {
    return `${typeof requestId}:${String(requestId)}`;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function formatPendingApprovalSummary(method: string, params: Record<string, unknown>): string {
  return `等待审批：${method}\n${stringifyApprovalParams(params)}`;
}

function stringifyApprovalParams(params: Record<string, unknown>): string {
  if (Object.keys(params).length === 0) {
    return "无附加参数";
  }

  try {
    return JSON.stringify(params, null, 2) ?? "无附加参数";
  } catch {
    return "[参数无法序列化]";
  }
}

function isApprovalRequestMethod(method: string): method is ApprovalRequestMethod {
  return (
    method === "item/commandExecution/requestApproval" ||
    method === "item/fileChange/requestApproval" ||
    method === "item/permissions/requestApproval" ||
    method === "applyPatchApproval" ||
    method === "execCommandApproval"
  );
}

function mapTurnApprovalDecision(decision: ApprovalDecision): string {
  switch (decision) {
    case "approve":
      return "accept";
    case "approve_for_session":
      return "acceptForSession";
    case "reject":
      return "decline";
    case "reject_and_interrupt":
      return "cancel";
  }
}

function mapLegacyApprovalDecision(decision: ApprovalDecision): string {
  switch (decision) {
    case "approve":
      return "approved";
    case "approve_for_session":
      return "approved_for_session";
    case "reject":
      return "denied";
    case "reject_and_interrupt":
      return "abort";
  }
}

function isImmediateInterruptDecision(method: ApprovalRequestMethod, decision: ApprovalDecision): boolean {
  if (decision !== "reject_and_interrupt") {
    return false;
  }

  return method !== "item/permissions/requestApproval";
}

function getPermissionsPayload(params: Record<string, unknown>): Record<string, unknown> {
  const permissions = params.permissions;
  return isRecord(permissions) ? permissions : {};
}

function isGoalUnsupportedError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false;
  }

  return /method not found|unsupported|not supported|unknown method|no such table:\s*thread_goals|no such table:\s*thread_goals_new/i.test(error.message);
}

function mapSessionGoal(goal: AppServerGoal): SessionGoal {
  return {
    objective: goal.objective,
    status: goal.status,
    tokenBudget: goal.tokenBudget ?? null,
    tokensUsed: goal.tokensUsed ?? 0,
    timeUsedSeconds: goal.timeUsedSeconds ?? 0,
    createdAt: toGoalIsoString(goal.createdAt),
    updatedAt: toGoalIsoString(goal.updatedAt),
  };
}

function resolveThreadActivityTimestamp(thread: AppServerThread): string {
  return deriveThreadLastActivityAt(thread) ?? new Date().toISOString();
}

function mapAccountQuotaSnapshot(response: AppServerAccountRateLimitsResponse): AccountQuotaSnapshot {
  const selected = response.rateLimitsByLimitId?.codex ?? response.rateLimits ?? null;
  return {
    limitId: selected?.limitId ?? null,
    planType: selected?.planType ?? null,
    rateLimitReachedType: selected?.rateLimitReachedType ?? null,
    fiveHours: findQuotaWindow(selected, 300),
    oneWeek: findQuotaWindow(selected, 10_080),
    credits: selected?.credits
      ? {
          hasCredits: selected.credits.hasCredits ?? false,
          unlimited: selected.credits.unlimited ?? false,
          balance: selected.credits.balance ?? null,
        }
      : null,
  };
}

function findQuotaWindow(
  snapshot: AppServerRateLimitSnapshot | null,
  windowDurationMins: number,
): AccountQuotaSnapshot["fiveHours"] {
  const windows = collectQuotaWindows(snapshot);
  for (const window of windows) {
    if (!window || window.windowDurationMins !== windowDurationMins) {
      continue;
    }

    return {
      usedPercent: normalizeUsedPercent(window.usedPercent),
      windowDurationMins,
      resetsAt: toQuotaResetIsoString(window.resetsAt),
    };
  }

  return null;
}

function collectQuotaWindows(snapshot: AppServerRateLimitSnapshot | null): AppServerRateLimitWindow[] {
  if (!snapshot || !isRecord(snapshot)) {
    return [];
  }

  return Object.values(snapshot)
    .flatMap((value) => toQuotaWindow(value))
    .filter((window): window is AppServerRateLimitWindow => window != null);
}

function toQuotaWindow(value: unknown): AppServerRateLimitWindow | null {
  if (!isRecord(value)) {
    return null;
  }

  return typeof value.windowDurationMins === "number" ? (value as AppServerRateLimitWindow) : null;
}

function normalizeUsedPercent(value: number | undefined): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return 0;
  }

  return Math.max(0, Math.min(100, Math.round(value)));
}

function toQuotaResetIsoString(value: number | null | undefined): string | null {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    return null;
  }

  return new Date(value * 1000).toISOString();
}

function toGoalIsoString(value: number | string | null | undefined): string {
  if (typeof value === "number" && Number.isFinite(value)) {
    return new Date(value * 1000).toISOString();
  }

  if (typeof value === "string" && value.trim()) {
    return value;
  }

  return new Date().toISOString();
}

function shouldResumeMissingThread(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false;
  }

  return /thread not found/i.test(error.message);
}

function mapApprovalMode(approvalPolicy: "on-request" | "never" | undefined): SessionRecord["approvalMode"] | null {
  if (approvalPolicy === "on-request") {
    return "manual";
  }
  if (approvalPolicy === "never") {
    return "auto";
  }
  return null;
}

function mapReasoningEffort(
  reasoningEffort: ReasoningEffort | null | undefined,
): SessionRecord["reasoningEffort"] | null {
  switch (reasoningEffort) {
    case "minimal":
    case "low":
    case "medium":
    case "high":
    case "xhigh":
      return reasoningEffort;
    default:
      return null;
  }
}

function mapServiceTier(
  serviceTier: "fast" | null | undefined,
): SessionRecord["serviceTier"] | null {
  switch (serviceTier) {
    case "fast":
      return serviceTier;
    default:
      return null;
  }
}

function buildServiceTierParams(serviceTier: ServiceTier): { serviceTier?: "fast" } {
  if (serviceTier === "fast") {
    return { serviceTier };
  }

  return {};
}

function mapSandboxMode(
  sandbox:
    | ThreadResumeResult["sandbox"]
    | undefined,
): SessionRecord["sandboxMode"] | null {
  if (sandbox === "read-only" || sandbox === "workspace-write" || sandbox === "danger-full-access") {
    return sandbox;
  }

  if (!sandbox || typeof sandbox !== "object") {
    return null;
  }

  switch (sandbox.type) {
    case "readOnly":
      return "read-only";
    case "workspaceWrite":
      return "workspace-write";
    case "dangerFullAccess":
      return "danger-full-access";
    default:
      return null;
  }
}

function buildSandboxPolicy(
  sandboxMode: SandboxMode,
  cwd: string,
):
  | { type: "dangerFullAccess" }
  | { type: "readOnly"; networkAccess: false }
  | {
      type: "workspaceWrite";
      writableRoots: string[];
      networkAccess: false;
      excludeTmpdirEnvVar: false;
      excludeSlashTmp: false;
    } {
  switch (sandboxMode) {
    case "danger-full-access":
      return { type: "dangerFullAccess" };
    case "read-only":
      return {
        type: "readOnly",
        networkAccess: false,
      };
    default:
      return {
        type: "workspaceWrite",
        writableRoots: [cwd],
        networkAccess: false,
        excludeTmpdirEnvVar: false,
        excludeSlashTmp: false,
      };
  }
}

function buildTurnInput(input: ResolvedSessionInput): Array<Record<string, unknown>> {
  const items: Array<Record<string, unknown>> = [];
  const trimmedText = input.text.trim();
  if (trimmedText) {
    items.push({
      type: "text",
      text: trimmedText,
    });
  } else if (input.attachments.length > 0) {
    items.push({
      type: "text",
      text: "请查看这个图片附件。",
    });
  }

  for (const attachment of input.attachments) {
    if (attachment.kind !== "image") {
      continue;
    }

    items.push({
      type: "localImage",
      path: attachment.path,
    });
  }

  return items;
}

function findLatestActiveTurnId(thread: AppServerThread): string | null {
  const turns = [...(thread.turns ?? [])].sort(compareTurnsDescending);
  for (const turn of turns) {
    if (turn.status === "inProgress") {
      return turn.id;
    }
  }

  return null;
}

function extractLatestThreadError(thread: AppServerThread): string | null {
  const turns = [...(thread.turns ?? [])].sort(compareTurnsDescending);
  for (const turn of turns) {
    const message = turn.error?.message?.trim();
    if (message) {
      return message;
    }
  }

  return null;
}

function compareTurnsDescending(
  left: { startedAt?: number | null; completedAt?: number | null },
  right: { startedAt?: number | null; completedAt?: number | null },
): number {
  const leftTimestamp = left.startedAt ?? left.completedAt ?? 0;
  const rightTimestamp = right.startedAt ?? right.completedAt ?? 0;
  return rightTimestamp - leftTimestamp;
}

function summarizeTranscriptBlock(block: string): string {
  const firstMeaningfulLine = block
    .split(/\r?\n/)
    .map((line) => line.trim())
    .find((line) => line.length > 0 && line !== "输出：");

  if (!firstMeaningfulLine) {
    return "收到新的操作事件。";
  }

  if (firstMeaningfulLine.startsWith("系统：")) {
    return firstMeaningfulLine.slice("系统：".length).trim() || "收到新的操作事件。";
  }

  return firstMeaningfulLine;
}

function buildActivityPayload(
  itemType: string,
  itemId: string | undefined,
  transcriptBlock: string | null,
): ActivityPayload | null {
  if (!transcriptBlock) {
    return null;
  }

  const lines = transcriptBlock
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
  if (lines.length === 0) {
    return null;
  }

  const firstLine = lines[0] ?? "";
  const title = firstLine.startsWith("系统：") ? firstLine.slice("系统：".length).trim() : firstLine;
  if (!title) {
    return null;
  }

  const body = lines.slice(1).join("\n").trim() || null;
  return {
    itemType,
    itemId,
    title,
    body,
    transcriptBlock,
    summary: body ? title : summarizeTranscriptBlock(transcriptBlock),
  };
}

function buildActivityKey(sessionId: string, itemId: string | undefined, itemType: string): string {
  return `${sessionId}:${itemId ?? itemType}`;
}

function mergeActivityBodies(currentBody: string | null, delta: string): string {
  if (!currentBody) {
    return delta;
  }
  return `${currentBody}\n${delta}`.trim();
}
