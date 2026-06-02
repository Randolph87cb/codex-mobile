import { z } from "zod";
import { BridgeServiceError } from "./app-context.js";
import type { BridgeAppDependencies } from "./app-context.js";
import { validateSessionCwd } from "./security.js";
import type {
  CreateSessionInput,
  ResolvedSessionInputAttachment,
  SessionApprovalInput,
  SessionApprovalResult,
  SessionGoalState,
  SessionGoalUpdateInput,
  SessionInput,
  SessionRecord,
  SessionView,
} from "./types.js";

export const createSessionSchema = z.object({
  cwd: z.string().trim().min(1),
  model: z.string().trim().min(1).default("gpt-5.5"),
  approvalMode: z.enum(["manual", "auto"]).default("manual"),
  reasoningEffort: z.enum(["minimal", "low", "medium", "high", "xhigh"]).default("medium"),
  serviceTier: z.enum(["default", "fast"]).default("default"),
  sandboxMode: z.enum(["read-only", "workspace-write", "danger-full-access"]).default("workspace-write"),
});

export const updateSessionConfigSchema = z.object({
  cwd: z.string().trim().min(1).optional(),
  model: z.string().trim().min(1).optional(),
  approvalMode: z.enum(["manual", "auto"]).optional(),
  reasoningEffort: z.enum(["minimal", "low", "medium", "high", "xhigh"]).optional(),
  serviceTier: z.enum(["default", "fast"]).optional(),
  sandboxMode: z.enum(["read-only", "workspace-write", "danger-full-access"]).optional(),
});

export const updateSessionTitleSchema = z.object({
  title: z.string().trim().min(1).max(200),
});

export const updateSessionGoalSchema = z.object({
  objective: z.string().trim().min(1).max(2_000).optional(),
  status: z.string().trim().min(1).max(64).optional(),
  tokenBudget: z.number().int().min(1).nullable().optional(),
}).superRefine((value, context) => {
  if (value.objective !== undefined || value.status !== undefined || value.tokenBudget !== undefined) {
    return;
  }

  context.addIssue({
    code: z.ZodIssueCode.custom,
    message: "objective, status, or tokenBudget is required",
    path: ["objective"],
  });
});

export const inputSchema = z.object({
  text: z.string().optional(),
  attachments: z.array(
    z.object({
      id: z.string().trim().min(1).optional(),
      path: z.string().trim().min(1).optional(),
    }).superRefine((value, context) => {
      if (value.id || value.path) {
        return;
      }

      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "attachment id or path is required",
        path: ["id"],
      });
    }),
  ).optional(),
}).superRefine((value, context) => {
  const hasText = value.text?.trim().length ? value.text.trim().length > 0 : false;
  const hasAttachments = (value.attachments?.length ?? 0) > 0;
  if (hasText || hasAttachments) {
    return;
  }

  context.addIssue({
    code: z.ZodIssueCode.custom,
    message: "either text or attachments is required",
    path: ["text"],
  });
});

export const listSessionsQuerySchema = z.object({
  archived: z.enum(["true", "false"]).optional(),
});

export const approveSchema = z.object({
  requestId: z.union([z.string().min(1), z.number()]).optional(),
  decision: z.enum(["approve", "approve_for_session", "reject", "reject_and_interrupt"]).default("approve"),
});

type SessionAttachmentResolver = (refs: NonNullable<SessionInput["attachments"]>) => ResolvedSessionInputAttachment[];

export interface BridgeSessionService {
  createSession(input: CreateSessionInput): Promise<SessionRecord>;
  updateSessionConfig(sessionId: string, input: z.infer<typeof updateSessionConfigSchema>): Promise<SessionRecord>;
  renameSessionTitle(sessionId: string, title: string): Promise<SessionView>;
  listSessionViews(archived: boolean): Promise<Array<SessionRecord | SessionView>>;
  getSessionView(sessionId: string): Promise<SessionRecord | SessionView | null>;
  getAccountQuota(): Promise<unknown>;
  getSessionGoal(sessionId: string): Promise<SessionGoalState>;
  updateSessionGoal(sessionId: string, input: SessionGoalUpdateInput): Promise<SessionGoalState>;
  clearSessionGoal(sessionId: string): Promise<{ ok: true; capability: SessionGoalState["capability"]; cleared: boolean }>;
  submitInput(sessionId: string, input: SessionInput): Promise<void>;
  interrupt(sessionId: string): Promise<void>;
  approve(sessionId: string, input: SessionApprovalInput): Promise<SessionApprovalResult>;
  archive(sessionId: string): Promise<{ ok: true }>;
  unarchive(sessionId: string): Promise<{ ok: true }>;
  getRealtimeSessionState(sessionId: string): Promise<{ session: SessionRecord; view: SessionView | null } | null>;
}

export function createSessionService(
  deps: BridgeAppDependencies,
  resolveSessionInputAttachments: SessionAttachmentResolver,
): BridgeSessionService {
  return {
    createSession,
    updateSessionConfig,
    renameSessionTitle,
    listSessionViews,
    getSessionView,
    getAccountQuota,
    getSessionGoal,
    updateSessionGoal,
    clearSessionGoal,
    submitInput,
    interrupt,
    approve,
    archive,
    unarchive,
    getRealtimeSessionState,
  };

  async function createSession(input: CreateSessionInput): Promise<SessionRecord> {
    const validatedCwd = validateSessionCwd(input.cwd, deps.security);
    if (!validatedCwd.ok) {
      throw new BridgeServiceError(
        validatedCwd.error === "invalid-cwd" ? 400 : 403,
        {
          error: validatedCwd.error,
          message: validatedCwd.message,
        },
      );
    }

    try {
      return await deps.runner.createSession({
        ...input,
        cwd: validatedCwd.cwd ?? input.cwd,
      });
    } catch (error) {
      throw new BridgeServiceError(502, {
        error: "session-initialize-failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  }

  async function updateSessionConfig(
    sessionId: string,
    input: z.infer<typeof updateSessionConfigSchema>,
  ): Promise<SessionRecord> {
    const session = deps.store.get(sessionId) ?? await deps.historyRunner?.attachSession(sessionId) ?? null;
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    let cwd = input.cwd;
    if (cwd !== undefined) {
      const validatedCwd = validateSessionCwd(cwd, deps.security);
      if (!validatedCwd.ok) {
        throw new BridgeServiceError(
          validatedCwd.error === "invalid-cwd" ? 400 : 403,
          {
            error: validatedCwd.error,
            message: validatedCwd.message,
          },
        );
      }
      cwd = validatedCwd.cwd ?? cwd;
    }

    return deps.store.update(
      session.id,
      omitUndefinedFields({
        cwd,
        model: input.model,
        approvalMode: input.approvalMode,
        reasoningEffort: input.reasoningEffort,
        serviceTier: input.serviceTier,
        sandboxMode: input.sandboxMode,
      }),
    ) ?? session;
  }

  async function renameSessionTitle(sessionId: string, title: string): Promise<SessionView> {
    if (!deps.historyRunner) {
      throw new BridgeServiceError(501, { error: "rename-not-supported" });
    }

    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    try {
      return await deps.historyRunner.renameSessionTitle(session.id, title);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message === "session-title-required") {
        throw new BridgeServiceError(400, { error: message });
      }
      if (message === "session-not-renamable") {
        throw new BridgeServiceError(409, { error: message });
      }

      throw new BridgeServiceError(502, {
        error: "session-title-update-failed",
        message,
      });
    }
  }

  async function listSessionViews(archived: boolean): Promise<Array<SessionRecord | SessionView>> {
    return deps.historyRunner ? await deps.historyRunner.listSessionViews(archived) : deps.store.list();
  }

  async function getSessionView(sessionId: string): Promise<SessionRecord | SessionView | null> {
    if (deps.historyRunner) {
      const view = await deps.historyRunner.getSessionView(sessionId);
      if (view) {
        return view;
      }
    }

    return deps.store.get(sessionId) ?? null;
  }

  async function getAccountQuota(): Promise<unknown> {
    if (!deps.historyRunner) {
      throw new BridgeServiceError(501, { error: "quota-not-supported" });
    }

    try {
      return await deps.historyRunner.getAccountQuota();
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (/method not found|unsupported|not supported|unknown method/i.test(message)) {
        throw new BridgeServiceError(501, { error: "quota-not-supported" });
      }

      throw new BridgeServiceError(502, {
        error: "account-quota-failed",
        message,
      });
    }
  }

  async function getSessionGoal(sessionId: string): Promise<SessionGoalState> {
    ensureGoalSupport();
    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    try {
      return await deps.historyRunner!.getSessionGoal(session.id);
    } catch (error) {
      throw mapGoalError(error);
    }
  }

  async function updateSessionGoal(
    sessionId: string,
    input: SessionGoalUpdateInput,
  ): Promise<SessionGoalState> {
    ensureGoalSupport();
    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    try {
      return await deps.historyRunner!.updateSessionGoal(session.id, input);
    } catch (error) {
      throw mapGoalError(error);
    }
  }

  async function clearSessionGoal(
    sessionId: string,
  ): Promise<{ ok: true; capability: SessionGoalState["capability"]; cleared: boolean }> {
    ensureGoalSupport();
    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    try {
      const result = await deps.historyRunner!.clearSessionGoal(session.id);
      return {
        ok: true,
        ...result,
      };
    } catch (error) {
      throw mapGoalError(error);
    }
  }

  async function submitInput(sessionId: string, input: SessionInput): Promise<void> {
    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    try {
      const resolvedAttachments = resolveSessionInputAttachments(input.attachments ?? []);
      await deps.runner.submitInput(session.id, {
        text: buildRunnerInputText(input.text?.trim() ?? "", resolvedAttachments),
        attachments: resolvedAttachments,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message.startsWith("attachment-not-found:")) {
        throw new BridgeServiceError(400, {
          error: "attachment-not-found",
          message,
        });
      }
      if (message === "thread-busy") {
        throw new BridgeServiceError(409, {
          error: "thread-busy",
          message: "thread is already running in another client",
        });
      }

      throw new BridgeServiceError(502, {
        error: "turn-start-failed",
        message,
      });
    }
  }

  async function interrupt(sessionId: string): Promise<void> {
    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    await deps.runner.interrupt(session.id);
  }

  async function approve(sessionId: string, input: SessionApprovalInput): Promise<SessionApprovalResult> {
    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    try {
      return await deps.runner.approve(session.id, input);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message === "approval-request-id-required") {
        throw new BridgeServiceError(409, { error: message });
      }
      if (message === "approval-not-found") {
        throw new BridgeServiceError(409, { error: message });
      }
      if (message === "session-not-found") {
        throw new BridgeServiceError(404, { error: message });
      }

      throw new BridgeServiceError(502, {
        error: "approval-submit-failed",
        message,
      });
    }
  }

  async function archive(sessionId: string): Promise<{ ok: true }> {
    ensureArchiveSupport();
    try {
      await deps.historyRunner!.archiveSession(sessionId);
      return { ok: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message === "session-not-archivable") {
        throw new BridgeServiceError(409, { error: message });
      }

      throw new BridgeServiceError(502, {
        error: "session-archive-failed",
        message,
      });
    }
  }

  async function unarchive(sessionId: string): Promise<{ ok: true }> {
    ensureArchiveSupport();
    try {
      await deps.historyRunner!.unarchiveSession(sessionId);
      return { ok: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message === "session-not-archivable") {
        throw new BridgeServiceError(409, { error: message });
      }

      throw new BridgeServiceError(502, {
        error: "session-unarchive-failed",
        message,
      });
    }
  }

  async function getRealtimeSessionState(
    sessionId: string,
  ): Promise<{ session: SessionRecord; view: SessionView | null } | null> {
    const session = await resolveSessionRecord(sessionId);
    if (!session) {
      return null;
    }

    const view = deps.historyRunner ? await deps.historyRunner.getSessionView(session.id) : null;
    return { session, view };
  }

  async function resolveSessionRecord(sessionId: string): Promise<SessionRecord | null> {
    if (deps.historyRunner) {
      const attached = await deps.historyRunner.attachSession(sessionId);
      if (attached) {
        return attached;
      }
    }

    return deps.store.get(sessionId) ?? null;
  }

  function ensureGoalSupport(): void {
    if (!deps.historyRunner) {
      throw new BridgeServiceError(501, { error: "goal-not-supported" });
    }
  }

  function ensureArchiveSupport(): void {
    if (!deps.historyRunner) {
      throw new BridgeServiceError(501, { error: "archive-not-supported" });
    }
  }
}

function mapGoalError(error: unknown): BridgeServiceError {
  const message = error instanceof Error ? error.message : String(error);
  if (message === "goal-not-supported") {
    return new BridgeServiceError(501, { error: message });
  }
  if (message === "goal-session-unavailable") {
    return new BridgeServiceError(409, { error: message });
  }

  return new BridgeServiceError(502, {
    error: "session-goal-failed",
    message,
  });
}

function omitUndefinedFields<T extends Record<string, unknown>>(value: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(value).filter(([, entry]) => entry !== undefined),
  ) as Partial<T>;
}

function buildRunnerInputText(
  text: string,
  attachments: Array<{
    kind: "image" | "video";
    displayName: string;
    path: string;
    savedPath?: string;
  }>,
): string {
  const normalizedText = text.trim();
  const nonImageLinks = attachments
    .filter((attachment) => attachment.kind !== "image")
    .map((attachment) => buildBridgeFileMarkdown(attachment.displayName, attachment.savedPath ?? attachment.path))
    .filter((value): value is string => Boolean(value));

  if (nonImageLinks.length === 0) {
    return normalizedText;
  }

  const prefix = normalizedText || "请查看我上传的文件。";
  return [prefix, ...nonImageLinks].join("\n");
}

function buildBridgeFileMarkdown(label: string, rawPath: string): string | null {
  const normalizedPath = rawPath.trim();
  if (!normalizedPath) {
    return null;
  }

  return `[${label}](bridge-file://${encodeURIComponent(normalizedPath)})`;
}
