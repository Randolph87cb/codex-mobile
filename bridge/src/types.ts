export type SessionStatus = "idle" | "running" | "awaiting_approval" | "error";
export type JsonRpcRequestId = string | number;
export type ApprovalDecision = "approve" | "approve_for_session" | "reject" | "reject_and_interrupt";
export type ApprovalMode = "manual" | "auto";
export type ReasoningEffort = "minimal" | "low" | "medium" | "high" | "xhigh";
export type ServiceTier = "fast" | "flex";

export interface CreateSessionInput {
  cwd: string;
  model: string;
  approvalMode: ApprovalMode;
  reasoningEffort: ReasoningEffort;
  serviceTier: ServiceTier;
}

export interface SessionRecord {
  id: string;
  cwd: string;
  model: string;
  approvalMode: ApprovalMode;
  reasoningEffort: ReasoningEffort;
  serviceTier: ServiceTier;
  status: SessionStatus;
  threadId: string | null;
  activeTurnId: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SessionView {
  id: string;
  title: string;
  subtitle: string;
  lastUpdated: string;
  transcriptPreview: string | null;
  source: "local" | "history";
  cwd: string;
  model: string;
  approvalMode: ApprovalMode;
  reasoningEffort: ReasoningEffort;
  serviceTier: ServiceTier;
  status: SessionStatus;
  threadId: string | null;
  activeTurnId: string | null;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SessionInput {
  text: string;
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
    | "assistant.delta"
    | "assistant.done"
    | "tool.request"
    | "tool.result"
    | "run.status"
    | "run.interrupted"
    | "error";
  sessionId: string;
  timestamp: string;
  data: Record<string, unknown>;
}

export interface BridgeSecurityConfig {
  token: string | null;
  allowedCwds: string[];
}

export interface BridgeSecurityState {
  tokenAuthEnabled: boolean;
  cwdWhitelistEnabled: boolean;
}
