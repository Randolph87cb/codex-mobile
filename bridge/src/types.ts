export type SessionStatus = "idle" | "running" | "awaiting_approval" | "error";

export interface CreateSessionInput {
  cwd: string;
  model: string;
  approvalMode: "manual" | "auto";
}

export interface SessionRecord {
  id: string;
  cwd: string;
  model: string;
  approvalMode: "manual" | "auto";
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
  approvalMode: "manual" | "auto";
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
