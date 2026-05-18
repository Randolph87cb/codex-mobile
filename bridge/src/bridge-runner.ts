import type {
  BridgeEvent,
  SessionApprovalInput,
  SessionApprovalResult,
  SessionRecord,
  SessionView,
} from "./types.js";

export type BridgeEventListener = (event: BridgeEvent) => void;

export interface BridgeRunner {
  readonly mode: "mock" | "app-server";
  initializeSession(sessionId: string): Promise<void>;
  submitInput(sessionId: string, text: string): Promise<void>;
  approve(sessionId: string, input: SessionApprovalInput): Promise<SessionApprovalResult>;
  interrupt(sessionId: string): Promise<void>;
  subscribe(sessionId: string, listener: BridgeEventListener): () => void;
  close?(): Promise<void>;
}

export interface HistoryCapableBridgeRunner extends BridgeRunner {
  listSessionViews(): Promise<SessionView[]>;
  getSessionView(sessionId: string): Promise<SessionView | null>;
  attachSession(sessionId: string): Promise<SessionRecord | null>;
}

export function isHistoryCapableRunner(runner: BridgeRunner): runner is HistoryCapableBridgeRunner {
  return (
    "listSessionViews" in runner &&
    "getSessionView" in runner &&
    "attachSession" in runner
  );
}
