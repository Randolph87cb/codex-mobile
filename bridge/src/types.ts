export type SessionStatus = "idle" | "running" | "awaiting_approval" | "error";
export type JsonRpcRequestId = string | number;
export type ApprovalDecision = "approve" | "approve_for_session" | "reject" | "reject_and_interrupt";
export type ApprovalMode = "manual" | "auto";
export type ReasoningEffort = "minimal" | "low" | "medium" | "high" | "xhigh";
export type ServiceTier = "default" | "fast";
export type SandboxMode = "read-only" | "workspace-write" | "danger-full-access";
export type GoalCapability = "unknown" | "supported" | "unsupported";

export interface CreateSessionInput {
  cwd: string;
  model: string;
  approvalMode: ApprovalMode;
  reasoningEffort: ReasoningEffort;
  serviceTier: ServiceTier;
  sandboxMode: SandboxMode;
}

export interface SessionRecord {
  id: string;
  cwd: string;
  model: string;
  approvalMode: ApprovalMode;
  reasoningEffort: ReasoningEffort;
  serviceTier: ServiceTier;
  sandboxMode: SandboxMode;
  status: SessionStatus;
  threadId: string | null;
  activeTurnId: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  lastActivityAt?: string;
}

export interface SessionView {
  id: string;
  title: string;
  subtitle: string;
  lastUpdated: string;
  transcriptPreview: string | null;
  archived: boolean;
  source: "local" | "history";
  cwd: string;
  model: string;
  approvalMode: ApprovalMode;
  reasoningEffort: ReasoningEffort;
  serviceTier: ServiceTier;
  sandboxMode: SandboxMode;
  status: SessionStatus;
  threadId: string | null;
  activeTurnId: string | null;
  lastError: string | null;
  pendingApproval?: PendingApprovalView | null;
  goal?: SessionGoal | null;
  goalCapability?: GoalCapability;
  createdAt: string;
  updatedAt: string;
}

export interface SessionGoal {
  objective: string;
  status: string;
  tokenBudget: number | null;
  tokensUsed: number;
  timeUsedSeconds: number;
  createdAt: string;
  updatedAt: string;
}

export interface SessionGoalState {
  capability: GoalCapability;
  goal: SessionGoal | null;
}

export interface AccountQuotaWindow {
  usedPercent: number;
  windowDurationMins: number;
  resetsAt: string | null;
}

export interface AccountQuotaCredits {
  hasCredits: boolean;
  unlimited: boolean;
  balance: string | null;
}

export interface AccountQuotaSnapshot {
  limitId: string | null;
  planType: string | null;
  rateLimitReachedType: string | null;
  fiveHours: AccountQuotaWindow | null;
  oneWeek: AccountQuotaWindow | null;
  credits: AccountQuotaCredits | null;
}

export interface SessionGoalUpdateInput {
  objective?: string;
  status?: string;
  tokenBudget?: number | null;
}

export interface PendingApprovalView {
  requestId: JsonRpcRequestId;
  method: string;
  paramsSummary: string;
}

export interface SessionInput {
  text?: string;
  attachments?: SessionInputAttachmentRef[];
}

export interface SessionInputAttachmentRef {
  id?: string;
  path?: string;
}

export interface ResolvedSessionInput {
  text: string;
  attachments: ResolvedSessionInputAttachment[];
}

export interface ResolvedSessionInputAttachment {
  id: string;
  kind: "image";
  path: string;
  displayName: string;
  mimeType: string;
}

export interface UploadedImageAttachment {
  id: string;
  kind: "image";
  displayName: string;
  mimeType: string;
  path: string;
  savedPath?: string;
  savedRelativePath?: string;
  createdAt: string;
}

export interface SessionApprovalInput {
  requestId?: JsonRpcRequestId;
  decision?: ApprovalDecision;
}

export interface SessionApprovalResult {
  requestId: JsonRpcRequestId;
  status: SessionStatus;
  decision: ApprovalDecision;
  method: string;
}

export interface BridgeEvent {
  type:
    | "session.started"
    | "goal.updated"
    | "goal.cleared"
    | "bridge.lifecycle"
    | "assistant.delta"
    | "assistant.done"
    | "activity"
    | "tool.request"
    | "tool.result"
    | "run.status"
    | "run.interrupted"
    | "error";
  sessionId: string;
  timestamp: string;
  data: Record<string, unknown>;
}

export interface BridgeLifecycleState {
  phase: "running" | "restarting";
  draining: boolean;
  reason: string | null;
  startedAt: string;
  drainStartedAt: string | null;
  drainGraceMs: number | null;
  bridgeVersion: string;
}

export interface BridgeSecurityConfig {
  token: string | null;
  allowedCwds: string[];
}

export interface BridgeSecurityState {
  tokenAuthEnabled: boolean;
  cwdWhitelistEnabled: boolean;
}
