import type { AttachmentStore } from "./attachment-store.js";
import type { BridgeRunner, HistoryCapableBridgeRunner } from "./bridge-runner.js";
import type { LocalHistoryStore } from "./local-history-store.js";
import type { SessionStore } from "./session-store.js";
import type { BridgeLifecycleState, BridgeSecurityConfig, BridgeSecurityState } from "./types.js";

export interface BuildBridgeAppOptions {
  runner?: BridgeRunner;
  store?: SessionStore;
  security?: BridgeSecurityConfig;
  localHistoryStore?: LocalHistoryStore | null;
}

export interface SessionSocket {
  send(payload: string): void;
  close(code?: number, data?: string): void;
  on(event: "close", listener: () => void): void;
}

export interface BridgeLifecycleController {
  isDraining(): boolean;
  beginDrain(reason?: string, graceMs?: number): BridgeLifecycleState;
  buildLifecycleState(): BridgeLifecycleState;
  broadcastLifecycle(): void;
  attachSessionSocket(sessionId: string, socket: SessionSocket): void;
  detachSessionSocket(sessionId: string, socket: SessionSocket): void;
}

export interface BridgeAppDependencies {
  runner: BridgeRunner;
  historyRunner: HistoryCapableBridgeRunner | null;
  localHistoryStore: LocalHistoryStore | null;
  store: SessionStore;
  attachmentStore: AttachmentStore;
  security: BridgeSecurityConfig;
  securityState: BridgeSecurityState;
  lifecycle: BridgeLifecycleController;
}

export class BridgeServiceError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly payload: unknown,
    message = "bridge-service-error",
  ) {
    super(message);
  }
}

export function isBridgeServiceError(error: unknown): error is BridgeServiceError {
  return error instanceof BridgeServiceError;
}
