import type {
  AccountQuotaSnapshot,
  BridgeEvent,
  CreateSessionInput,
  ResolvedSessionInput,
  SessionApprovalInput,
  SessionApprovalResult,
  SessionGoalState,
  SessionGoalUpdateInput,
  SessionRecord,
  SessionView,
} from "./types.js";

export type BridgeEventListener = (event: BridgeEvent) => void;

export interface BridgeRunner {
  readonly mode: "mock" | "app-server";
  createSession(input: CreateSessionInput): Promise<SessionRecord>;
  submitInput(sessionId: string, input: ResolvedSessionInput): Promise<void>;
  approve(sessionId: string, input: SessionApprovalInput): Promise<SessionApprovalResult>;
  interrupt(sessionId: string): Promise<void>;
  subscribe(sessionId: string, listener: BridgeEventListener): () => void;
  close?(): Promise<void>;
}

export interface HistoryCapableBridgeRunner extends BridgeRunner {
  listSessionViews(archived?: boolean): Promise<SessionView[]>;
  getSessionView(sessionId: string): Promise<SessionView | null>;
  getAccountQuota(): Promise<AccountQuotaSnapshot>;
  getSessionGoal(sessionId: string): Promise<SessionGoalState>;
  updateSessionGoal(sessionId: string, input: SessionGoalUpdateInput): Promise<SessionGoalState>;
  clearSessionGoal(sessionId: string): Promise<{ capability: SessionGoalState["capability"]; cleared: boolean }>;
  attachSession(sessionId: string): Promise<SessionRecord | null>;
  archiveSession(sessionId: string): Promise<void>;
  unarchiveSession(sessionId: string): Promise<void>;
}

export function isHistoryCapableRunner(runner: BridgeRunner): runner is HistoryCapableBridgeRunner {
  return (
    "listSessionViews" in runner &&
    "getSessionView" in runner &&
    "getAccountQuota" in runner &&
    "getSessionGoal" in runner &&
    "updateSessionGoal" in runner &&
    "clearSessionGoal" in runner &&
    "attachSession" in runner &&
    "archiveSession" in runner &&
    "unarchiveSession" in runner
  );
}
