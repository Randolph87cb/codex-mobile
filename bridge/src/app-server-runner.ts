import { AppServerClient, type JsonRpcNotification, type JsonRpcServerRequest } from "./app-server-client.js";
import type { BridgeEventListener, HistoryCapableBridgeRunner } from "./bridge-runner.js";
import {
  buildSessionViewFromRecord,
  buildSessionViewFromThread,
  mapThreadStatus,
  type AppServerThread,
} from "./session-view.js";
import { SessionStore } from "./session-store.js";
import type { SessionRecord, SessionView } from "./types.js";

export class AppServerRunner implements HistoryCapableBridgeRunner {
  readonly mode = "app-server" as const;
  private readonly listeners = new Map<string, Set<BridgeEventListener>>();
  private readonly threadToSession = new Map<string, string>();
  private readonly client: AppServerClient;

  constructor(private readonly store: SessionStore) {
    this.client = new AppServerClient({
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
      approvalPolicy: "never",
    });

    this.threadToSession.set(result.thread.id, sessionId);
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
    this.emit(sessionId, {
      type: "run.interrupted",
      sessionId,
      timestamp: new Date().toISOString(),
      data: { status: "idle" },
    });
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

    return this.store.attach({
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
    const threadId = typeof request.params === "object" && request.params && "threadId" in request.params
      ? String((request.params as { threadId: string }).threadId)
      : null;
    const sessionId = threadId ? this.threadToSession.get(threadId) : undefined;

    if (sessionId) {
      this.store.update(sessionId, { status: "awaiting_approval" });
      this.emit(sessionId, {
        type: "tool.request",
        sessionId,
        timestamp: new Date().toISOString(),
        data: {
          requestId: request.id,
          method: request.method,
          params: request.params ?? {},
        },
      });
    }

    this.client.sendError(
      request.id,
      -32601,
      `Server request ${request.method} is not supported by codex-mobile-bridge yet.`,
    );
  }

  private handleThreadStatusChanged(params: unknown): void {
    const payload = params as {
      threadId: string;
      status: {
        type: string;
        activeFlags?: string[];
      };
    };
    const sessionId = this.threadToSession.get(payload.threadId);
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
    this.emit(sessionId, {
      type: "run.status",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        status,
        sourceStatus: payload.status,
      },
    });
  }

  private handleTurnStarted(params: unknown): void {
    const payload = params as {
      threadId: string;
      turn: {
        id: string;
      };
    };
    const sessionId = this.threadToSession.get(payload.threadId);
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
    const sessionId = this.threadToSession.get(payload.threadId);
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
    const sessionId = this.threadToSession.get(payload.threadId);
    if (!sessionId) {
      return;
    }

    const status = payload.turn.status === "failed" ? "error" : "idle";
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
}
