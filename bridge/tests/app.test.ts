import { afterEach, describe, expect, test, vi } from "vitest";
import type { HistoryCapableBridgeRunner } from "../src/bridge-runner.js";
import { buildBridgeApp } from "../src/app.js";
import { SessionStore } from "../src/session-store.js";
import type { SessionApprovalInput, SessionApprovalResult, SessionView } from "../src/types.js";

class TestRunner implements HistoryCapableBridgeRunner {
  readonly mode = "mock" as const;
  readonly initializeSession = vi.fn(async (sessionId: string) => {
    this.store.update(sessionId, { threadId: "thread-test" });
  });
  readonly submitInput = vi.fn(async () => undefined);
  readonly approve = vi.fn(async (_sessionId: string, input: SessionApprovalInput): Promise<SessionApprovalResult> => ({
    requestId: input.requestId ?? 1,
    decision: input.decision ?? "approve",
    method: "item/commandExecution/requestApproval",
    status: input.decision === "reject_and_interrupt" ? "idle" : "running",
  }));
  readonly interrupt = vi.fn(async () => undefined);

  constructor(private readonly store: SessionStore) {}

  subscribe(): () => void {
    return () => undefined;
  }

  async listSessionViews(): Promise<SessionView[]> {
    return [
      {
        id: "thread-history",
        title: "历史会话",
        subtitle: "openai • 空闲 • D:\\workspace\\history",
        lastUpdated: "2026-05-19T01:00:00.000Z",
        transcriptPreview: "你：之前说过什么？\n\nCodex：这里是历史回复。",
        source: "history",
        cwd: "D:\\workspace\\history",
        model: "openai",
        approvalMode: "manual",
        status: "idle",
        threadId: "thread-history",
        activeTurnId: null,
        lastError: null,
        createdAt: "2026-05-19T01:00:00.000Z",
        updatedAt: "2026-05-19T01:00:00.000Z",
      },
    ];
  }

  async getSessionView(sessionId: string): Promise<SessionView | null> {
    if (sessionId !== "thread-history") {
      return null;
    }

    return {
      id: "thread-history",
      title: "历史会话",
      subtitle: "openai • 空闲 • D:\\workspace\\history",
      lastUpdated: "2026-05-19T01:00:00.000Z",
      transcriptPreview: "你：之前说过什么？\n\nCodex：这里是历史回复。",
      source: "history",
      cwd: "D:\\workspace\\history",
      model: "openai",
      approvalMode: "manual",
      status: "idle",
      threadId: "thread-history",
      activeTurnId: null,
      lastError: null,
      createdAt: "2026-05-19T01:00:00.000Z",
      updatedAt: "2026-05-19T01:00:00.000Z",
    };
  }

  async attachSession(sessionId: string) {
    if (sessionId !== "thread-history") {
      return null;
    }

    return this.store.attach({
      id: "thread-history",
      cwd: "D:\\workspace\\history",
      model: "openai",
      approvalMode: "manual",
      status: "idle",
      threadId: "thread-history",
      activeTurnId: null,
      lastError: null,
      createdAt: "2026-05-19T01:00:00.000Z",
      updatedAt: "2026-05-19T01:00:00.000Z",
    });
  }
}

describe("buildBridgeApp", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  test("creates sessions and returns runner mode in health", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });

    const health = await app.inject({ method: "GET", url: "/health" });
    expect(health.statusCode).toBe(200);
    expect(health.json()).toMatchObject({ ok: true, runnerMode: "mock" });

    const create = await app.inject({
      method: "POST",
      url: "/api/session",
      payload: {
        cwd: "D:\\workspace\\codex-mobile",
        model: "gpt-5.5",
        approvalMode: "manual",
      },
    });

    expect(create.statusCode).toBe(201);
    expect(runner.initializeSession).toHaveBeenCalledTimes(1);
    expect(create.json()).toMatchObject({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      threadId: "thread-test",
    });

    await app.close();
  });

  test("rejects invalid requests and missing sessions", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });

    const invalidCreate = await app.inject({
      method: "POST",
      url: "/api/session",
      payload: {},
    });
    expect(invalidCreate.statusCode).toBe(400);

    const missingInput = await app.inject({
      method: "POST",
      url: "/api/session/missing/input",
      payload: {
        text: "hello",
      },
    });
    expect(missingInput.statusCode).toBe(404);

    const invalidApprove = await app.inject({
      method: "POST",
      url: "/api/session/missing/approve",
      payload: {
        decision: "approve",
      },
    });
    expect(invalidApprove.statusCode).toBe(404);

    await app.close();
  });

  test("returns history sessions and allows sending input to an attached thread", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });

    const sessions = await app.inject({ method: "GET", url: "/api/sessions" });
    expect(sessions.statusCode).toBe(200);
    expect(sessions.json()).toMatchObject({
      items: [
        {
          id: "thread-history",
          title: "历史会话",
        },
      ],
    });

    const detail = await app.inject({ method: "GET", url: "/api/session/thread-history" });
    expect(detail.statusCode).toBe(200);
    expect(detail.json()).toMatchObject({
      id: "thread-history",
      transcriptPreview: "你：之前说过什么？\n\nCodex：这里是历史回复。",
    });

    const input = await app.inject({
      method: "POST",
      url: "/api/session/thread-history/input",
      payload: {
        text: "你是谁",
      },
    });
    expect(input.statusCode).toBe(202);
    expect(runner.submitInput).toHaveBeenCalledWith("thread-history", "你是谁");

    await app.close();
  });

  test("forwards approval decisions to the runner", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
    });

    const approve = await app.inject({
      method: "POST",
      url: `/api/session/${session.id}/approve`,
      payload: {
        requestId: "req-1",
        decision: "reject",
      },
    });

    expect(approve.statusCode).toBe(200);
    expect(approve.json()).toMatchObject({
      ok: true,
      requestId: "req-1",
      decision: "reject",
      status: "running",
    });
    expect(runner.approve).toHaveBeenCalledWith(session.id, {
      requestId: "req-1",
      decision: "reject",
    });

    await app.close();
  });
});
