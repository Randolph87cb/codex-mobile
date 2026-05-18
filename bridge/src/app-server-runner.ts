import { AppServerClient, type JsonRpcNotification, type JsonRpcServerRequest } from "./app-server-client.js";
import type { BridgeEventListener, HistoryCapableBridgeRunner } from "./bridge-runner.js";
import {
  buildSessionViewFromRecord,
  buildSessionViewFromThread,
  mapThreadStatus,
  type AppServerThread,
} from "./session-view.js";
import { SessionStore } from "./session-store.js";
import type {
  ApprovalDecision,
  JsonRpcRequestId,
  SessionApprovalInput,
  SessionApprovalResult,
  SessionRecord,
  SessionStatus,
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

interface AppServerRpcClient {
  request<TResult>(method: string, params: unknown): Promise<TResult>;
  onNotification(listener: (message: JsonRpcNotification) => void): () => void;
  onServerRequest(listener: (message: JsonRpcServerRequest) => void): () => void;
  sendResponse(id: JsonRpcRequestId, result: unknown): void;
  sendError(id: JsonRpcRequestId, code: number, message: string, data?: unknown): void;
  close(): Promise<void>;
}

class ApprovalError extends Error {
  constructor(readonly code: "session-not-found" | "approval-not-found" | "approval-request-id-required") {
    super(code);
  }
}

export class AppServerRunner implements HistoryCapableBridgeRunner {
  readonly mode = "app-server" as const;
  private readonly listeners = new Map<string, Set<BridgeEventListener>>();
  private readonly threadToSession = new Map<string, string>();
  private readonly pendingApprovals = new Map<string, PendingApproval>();
  private readonly sessionApprovalKeys = new Map<string, string[]>();
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

  async initializeSession(sessionId: string): Promise<void> {
    const session = this.store.get(sessionId);
    if (!session) {
      throw new Error("session-not-found");
    }

    const result = await this.client.request<{ thread: { id: string } }>("thread/start", {
      cwd: session.cwd,
      model: session.model,
      approvalPolicy: this.getApprovalPolicy(session.approvalMode),
    });

    this.rememberThreadSession(result.thread.id, sessionId);
    this.store.update(sessionId, {
      threadId: result.thread.id,
      status: "idle",
      lastError: null,
    });
  }

  async submitInput(sessionId: string, text: string): Promise<void> {
    const session = this.store.get(sessionId);
    if (!session?.threadId) {
      throw new Error("thread-not-initialized");
    }

    this.store.update(sessionId, { status: "running", lastError: null });
    this.emit(sessionId, {
      type: "run.status",
      sessionId,
      timestamp: new Date().toISOString(),
      data: { status: "running", mode: this.mode },
    });

    const result = await this.client.request<{ turn: { id: string } }>("turn/start", {
      threadId: session.threadId,
      cwd: session.cwd,
      model: session.model,
      approvalPolicy: this.getApprovalPolicy(session.approvalMode),
      input: [
        {
          type: "text",
          text,
        },
      ],
    });

    this.store.update(sessionId, {
      activeTurnId: result.turn.id,
      status: "running",
    });
  }

  async interrupt(sessionId: string): Promise<void> {
    const session = this.store.get(sessionId);
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

  async listSessionViews(): Promise<SessionView[]> {
    const result = await this.client.request<{ data: AppServerThread[] }>("thread/list", {
      sortDirection: "desc",
      sortKey: "updated_at",
    });

    const views = result.data.map((thread) => {
      const session = this.store.findByThreadId(thread.id);
      return buildSessionViewFromThread(thread, session);
    });
    const threadIds = new Set(result.data.map((thread) => thread.id));

    for (const session of this.store.list()) {
      if (session.threadId && threadIds.has(session.threadId)) {
        continue;
      }
      views.push(buildSessionViewFromRecord(session));
    }

    return views.sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  }

  async getSessionView(sessionId: string): Promise<SessionView | null> {
    const session = this.store.get(sessionId);
    if (session?.threadId) {
      const thread = await this.tryReadThread(session.threadId);
      return thread ? buildSessionViewFromThread(thread, session) : buildSessionViewFromRecord(session);
    }

    if (session) {
      return buildSessionViewFromRecord(session);
    }

    const attachedSession = this.store.findByThreadId(sessionId);
    if (attachedSession?.threadId) {
      const thread = await this.tryReadThread(attachedSession.threadId);
      return thread ? buildSessionViewFromThread(thread, attachedSession) : buildSessionViewFromRecord(attachedSession);
    }

    const thread = await this.tryReadThread(sessionId);
    return thread ? buildSessionViewFromThread(thread) : null;
  }

  async attachSession(sessionId: string): Promise<SessionRecord | null> {
    const existing = this.store.get(sessionId);
    if (existing) {
      return existing;
    }

    const byThreadId = this.store.findByThreadId(sessionId);
    if (byThreadId) {
      return byThreadId;
    }

    const thread = await this.tryReadThread(sessionId);
    if (!thread) {
      return null;
    }

    const attached = this.store.attach({
      id: thread.id,
      cwd: thread.cwd,
      model: thread.modelProvider ?? "openai",
      approvalMode: "manual",
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
    this.rememberThreadSession(thread.id, attached.id);
    return attached;
  }

  private handleNotification(notification: JsonRpcNotification): void {
    switch (notification.method) {
      case "thread/status/changed":
        this.handleThreadStatusChanged(notification.params);
        break;
      case "turn/started":
        this.handleTurnStarted(notification.params);
        break;
      case "item/agentMessage/delta":
        this.handleAgentMessageDelta(notification.params);
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

    this.store.update(sessionId, { status });
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
    this.store.update(sessionId, {
      activeTurnId: null,
      status,
      lastError: payload.turn.status === "failed" ? JSON.stringify(payload.turn.error) : null,
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

  private getApprovalPolicy(mode: SessionRecord["approvalMode"]): "on-request" | "never" {
    return mode === "manual" ? "on-request" : "never";
  }

  private rememberThreadSession(threadId: string, sessionId: string): void {
    this.threadToSession.set(threadId, sessionId);
  }

  private resolveSessionIdByThreadId(threadId: string): string | undefined {
    const mapped = this.threadToSession.get(threadId);
    if (mapped) {
      return mapped;
    }

    const session = this.store.findByThreadId(threadId);
    if (!session) {
      return undefined;
    }

    this.rememberThreadSession(threadId, session.id);
    return session.id;
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
