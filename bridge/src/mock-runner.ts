import { setTimeout as delay } from "node:timers/promises";
import type { BridgeEventListener, BridgeRunner } from "./bridge-runner.js";
import type { BridgeEvent } from "./types.js";
import { SessionStore } from "./session-store.js";

export class MockRunner implements BridgeRunner {
  readonly mode = "mock" as const;
  private readonly listeners = new Map<string, Set<BridgeEventListener>>();

  constructor(private readonly store: SessionStore) {}

  async initializeSession(_sessionId: string): Promise<void> {
    return;
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

  async submitInput(sessionId: string, text: string): Promise<void> {
    const session = this.store.get(sessionId);
    if (!session) {
      throw new Error("session-not-found");
    }

    this.store.update(sessionId, { status: "running" });
    this.emit({
      type: "run.status",
      sessionId,
      timestamp: new Date().toISOString(),
      data: { status: "running" },
    });

    const chunks = [
      `收到输入：${text}`,
      "当前仍在使用 mock runner。",
      "下一步需要把这里替换成真实 codex app-server 会话桥接。",
    ];

    for (const textChunk of chunks) {
      await delay(250);
      this.emit({
        type: "assistant.delta",
        sessionId,
        timestamp: new Date().toISOString(),
        data: { text: textChunk },
      });
    }

    await delay(120);
    this.store.update(sessionId, { status: "idle" });
    this.emit({
      type: "assistant.done",
      sessionId,
      timestamp: new Date().toISOString(),
      data: { status: "idle" },
    });
  }

  async interrupt(sessionId: string): Promise<void> {
    this.store.update(sessionId, { status: "idle" });
    this.emit({
      type: "run.interrupted",
      sessionId,
      timestamp: new Date().toISOString(),
      data: { status: "idle" },
    });
  }

  private emit(event: BridgeEvent): void {
    const listeners = this.listeners.get(event.sessionId);
    if (!listeners) {
      return;
    }

    for (const listener of listeners) {
      listener(event);
    }
  }
}
