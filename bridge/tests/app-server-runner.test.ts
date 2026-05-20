import { describe, expect, test, vi } from "vitest";
import { AppServerRunner } from "../src/app-server-runner.js";
import type {
  JsonRpcNotification,
  JsonRpcServerRequest,
} from "../src/app-server-client.js";
import type { BridgeEvent } from "../src/types.js";
import { SessionStore } from "../src/session-store.js";

class FakeAppServerClient {
  readonly request = vi.fn(async (_method: string, _params: unknown) => ({}));
  readonly close = vi.fn(async () => undefined);
  readonly sentResponses: Array<{ id: string | number; result: unknown }> = [];
  readonly sentErrors: Array<{ id: string | number; code: number; message: string; data?: unknown }> = [];
  private notificationListener?: (message: JsonRpcNotification) => void;
  private serverRequestListener?: (message: JsonRpcServerRequest) => void;

  onNotification(listener: (message: JsonRpcNotification) => void): () => void {
    this.notificationListener = listener;
    return () => {
      this.notificationListener = undefined;
    };
  }

  onServerRequest(listener: (message: JsonRpcServerRequest) => void): () => void {
    this.serverRequestListener = listener;
    return () => {
      this.serverRequestListener = undefined;
    };
  }

  sendResponse(id: string | number, result: unknown): void {
    this.sentResponses.push({ id, result });
  }

  sendError(id: string | number, code: number, message: string, data?: unknown): void {
    this.sentErrors.push({ id, code, message, data });
  }

  emitNotification(message: JsonRpcNotification): void {
    this.notificationListener?.(message);
  }

  emitServerRequest(message: JsonRpcServerRequest): void {
    this.serverRequestListener?.(message);
  }
}

function createSessionStore(): SessionStore {
  const store = new SessionStore();
  store.attach({
    id: "sess-1",
    cwd: "D:\\workspace\\codex-mobile",
    model: "gpt-5.5",
    approvalMode: "manual",
    reasoningEffort: "medium",
    serviceTier: "default",
    sandboxMode: "workspace-write",
    status: "running",
    threadId: "thread-1",
    activeTurnId: "turn-1",
    lastError: null,
    createdAt: "2026-05-19T01:00:00.000Z",
    updatedAt: "2026-05-19T01:00:00.000Z",
  });
  return store;
}

describe("AppServerRunner", () => {
  test("uses session approval mode and sandbox mode for thread/start and turn/start", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/start") {
        return { thread: { id: "thread-2" }, serviceTier: null };
      }

      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-2",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080300,
            status: { type: "idle" },
            turns: [],
          },
        };
      }

      if (method === "turn/start") {
        return { turn: { id: "turn-2" } };
      }

      return {};
    });

    store.attach({
      id: "sess-2",
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "high",
      serviceTier: "default",
      sandboxMode: "read-only",
      status: "idle",
      threadId: null,
      activeTurnId: null,
      lastError: null,
      createdAt: "2026-05-19T01:10:00.000Z",
      updatedAt: "2026-05-19T01:10:00.000Z",
    });

    const runner = new AppServerRunner(store, client);
    await runner.initializeSession("sess-2");
    await runner.submitInput("sess-2", {
      text: "请继续",
      attachments: [],
    });

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "thread/start",
      expect.objectContaining({
        approvalPolicy: "on-request",
        sandbox: "read-only",
      }),
    );
    expect(client.request.mock.calls[0]?.[1]).not.toHaveProperty("serviceTier");
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "thread/read",
      expect.objectContaining({
        threadId: "thread-2",
        includeTurns: true,
      }),
    );
    expect(client.request).toHaveBeenNthCalledWith(
      3,
      "turn/start",
      expect.objectContaining({
        approvalPolicy: "on-request",
        effort: "high",
        sandboxPolicy: {
          type: "readOnly",
          networkAccess: false,
        },
      }),
    );
    expect(client.request.mock.calls[2]?.[1]).not.toHaveProperty("serviceTier");
    expect(store.get("sess-2")?.serviceTier).toBe("default");
    expect(store.get("sess-2")?.sandboxMode).toBe("read-only");
  });

  test("submits image attachments as localImage input blocks", async () => {
    const store = createSessionStore();
    store.update("sess-1", {
      status: "idle",
      activeTurnId: null,
    });

    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080300,
            status: { type: "idle" },
            turns: [],
          },
        };
      }

      if (method === "turn/start") {
        return { turn: { id: "turn-image" } };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    await runner.submitInput("sess-1", {
      text: "看这张图",
      attachments: [
        {
          id: "att-1",
          kind: "image",
          displayName: "demo.jpg",
          mimeType: "image/jpeg",
          path: "C:\\temp\\demo.jpg",
          createdAt: "2026-05-19T10:00:00.000Z",
        },
      ],
    });

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "thread/read",
      expect.objectContaining({
        threadId: "thread-1",
        includeTurns: true,
      }),
    );
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "turn/start",
      expect.objectContaining({
        threadId: "thread-1",
        input: [
          {
            type: "text",
            text: "看这张图",
          },
          {
            type: "localImage",
            path: "C:\\temp\\demo.jpg",
          },
        ],
      }),
    );
    expect(store.get("sess-1")).toMatchObject({
      activeTurnId: "turn-image",
      status: "running",
      lastError: null,
    });
  });

  test("submits image-only input with a fallback text block", async () => {
    const store = createSessionStore();
    store.update("sess-1", {
      status: "idle",
      activeTurnId: null,
    });

    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080300,
            status: { type: "idle" },
            turns: [],
          },
        };
      }

      if (method === "turn/start") {
        return { turn: { id: "turn-image-only" } };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    await runner.submitInput("sess-1", {
      text: "",
      attachments: [
        {
          id: "att-2",
          kind: "image",
          displayName: "only.jpg",
          mimeType: "image/jpeg",
          path: "C:\\temp\\only.jpg",
          createdAt: "2026-05-19T10:10:00.000Z",
        },
      ],
    });

    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "turn/start",
      expect.objectContaining({
        input: [
          {
            type: "text",
            text: "请查看这个图片附件。",
          },
          {
            type: "localImage",
            path: "C:\\temp\\only.jpg",
          },
        ],
      }),
    );
  });

  test("stores approval requests and resolves them through approve()", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    const runner = new AppServerRunner(store, client);
    const events: BridgeEvent[] = [];
    runner.subscribe("sess-1", (event) => {
      events.push(event);
    });

    client.emitServerRequest({
      id: "req-1",
      method: "item/commandExecution/requestApproval",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        itemId: "item-1",
        command: "git push",
      },
    });

    expect(store.get("sess-1")?.status).toBe("awaiting_approval");
    expect(events).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: "run.status",
          data: expect.objectContaining({
            status: "awaiting_approval",
            requestId: "req-1",
          }),
        }),
        expect.objectContaining({
          type: "tool.request",
          data: expect.objectContaining({
            requestId: "req-1",
            method: "item/commandExecution/requestApproval",
          }),
        }),
      ]),
    );

    const result = await runner.approve("sess-1", {
      requestId: "req-1",
      decision: "reject",
    });

    expect(result).toMatchObject({
      requestId: "req-1",
      decision: "reject",
      status: "running",
      method: "item/commandExecution/requestApproval",
    });
    expect(client.sentResponses).toEqual([
      {
        id: "req-1",
        result: {
          decision: "decline",
        },
      },
    ]);
    expect(store.get("sess-1")?.status).toBe("running");
    expect(events).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: "tool.result",
          data: expect.objectContaining({
            requestId: "req-1",
            decision: "reject",
          }),
        }),
        expect.objectContaining({
          type: "run.status",
          data: expect.objectContaining({
            status: "running",
            requestId: "req-1",
          }),
        }),
      ]),
    );
  });

  test("emits activity events for operation items", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    const runner = new AppServerRunner(store, client);
    const events: BridgeEvent[] = [];
    runner.subscribe("sess-1", (event) => {
      events.push(event);
    });

    client.emitNotification({
      method: "item/started",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        item: {
          id: "item-command",
          type: "commandExecution",
          status: "inProgress",
          command: "npm test",
          cwd: "D:\\workspace\\codex-mobile\\bridge",
        },
      },
    });

    client.emitNotification({
      method: "item/fileChange/patchUpdated",
      params: {
        threadId: "thread-1",
        itemId: "item-file",
        changes: [
          {
            path: "android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt",
            kind: "update",
          },
        ],
      },
    });

    expect(events).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: "activity",
          data: expect.objectContaining({
            itemType: "commandExecution",
            itemId: "item-command",
            summary: "命令执行",
            title: "命令执行",
            body: expect.stringContaining("命令：npm test"),
            transcriptBlock: expect.stringContaining("命令：npm test"),
          }),
        }),
        expect.objectContaining({
          type: "activity",
          data: expect.objectContaining({
            itemType: "fileChange",
            itemId: "item-file",
            summary: "文件修改进度",
            title: "文件修改进度",
            body: expect.stringContaining("涉及：android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt"),
            transcriptBlock: expect.stringContaining("涉及：android/app/src/main/java/com/openai/codexmobile/AppViewModel.kt"),
          }),
        }),
      ]),
    );
  });

  test("aggregates reasoning summary deltas by item id", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    const runner = new AppServerRunner(store, client);
    const events: BridgeEvent[] = [];
    runner.subscribe("sess-1", (event) => {
      events.push(event);
    });

    client.emitNotification({
      method: "item/reasoning/summaryTextDelta",
      params: {
        threadId: "thread-1",
        itemId: "reasoning-1",
        delta: "README 大体跟上了。",
      },
    });
    client.emitNotification({
      method: "item/reasoning/summaryTextDelta",
      params: {
        threadId: "thread-1",
        itemId: "reasoning-1",
        delta: "还要检查 docs/api.md。",
      },
    });

    const reasoningEvents = events.filter((event) => event.type === "activity");
    expect(reasoningEvents).toHaveLength(2);
    expect(reasoningEvents[0]).toEqual(
      expect.objectContaining({
        data: expect.objectContaining({
          itemType: "reasoning",
          itemId: "reasoning-1",
          title: "推理摘要",
          body: "README 大体跟上了。",
          summary: "README 大体跟上了。",
        }),
      }),
    );
    expect(reasoningEvents[1]).toEqual(
      expect.objectContaining({
        data: expect.objectContaining({
          itemType: "reasoning",
          itemId: "reasoning-1",
          title: "推理摘要",
          body: "README 大体跟上了。\n还要检查 docs/api.md。",
          summary: "还要检查 docs/api.md。",
        }),
      }),
    );
  });

  test("maps permission approvals to requested profile and session scope", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    const runner = new AppServerRunner(store, client);

    client.emitServerRequest({
      id: 7,
      method: "item/permissions/requestApproval",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        itemId: "item-7",
        cwd: "D:\\workspace\\codex-mobile",
        permissions: {
          network: {
            enabled: true,
          },
        },
      },
    });

    await runner.approve("sess-1", {
      requestId: 7,
      decision: "approve_for_session",
    });

    expect(client.sentResponses).toEqual([
      {
        id: 7,
        result: {
          permissions: {
            network: {
              enabled: true,
            },
          },
          scope: "session",
        },
      },
    ]);
  });

  test("includes pending approval metadata in session detail views", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080300,
            status: { type: "active", activeFlags: ["waitingOnApproval"] },
            turns: [],
          },
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    client.emitServerRequest({
      id: "req-view",
      method: "item/commandExecution/requestApproval",
      params: {
        threadId: "thread-1",
        command: "Copy-Item D:\\tmp\\pet D:\\workspace\\pet",
      },
    });

    const view = await runner.getSessionView("sess-1");

    expect(view).toMatchObject({
      id: "sess-1",
      status: "awaiting_approval",
      pendingApproval: {
        requestId: "req-view",
        method: "item/commandExecution/requestApproval",
      },
    });
    expect(view?.pendingApproval?.paramsSummary).toContain("等待审批：item/commandExecution/requestApproval");
    expect(view?.pendingApproval?.paramsSummary).toContain("Copy-Item");
  });

  test("rejects unsupported non-approval server requests", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    const runner = new AppServerRunner(store, client);

    client.emitServerRequest({
      id: 99,
      method: "item/tool/requestUserInput",
      params: {
        threadId: "thread-1",
        turnId: "turn-1",
        itemId: "item-99",
        questions: [],
      },
    });

    expect(client.sentErrors).toEqual([
      {
        id: 99,
        code: -32601,
        message: "Server request item/tool/requestUserInput is not supported by codex-mobile-bridge yet.",
        data: undefined,
      },
    ]);
    expect(store.get("sess-1")?.status).toBe("running");
  });

  test("retries turn/start after resuming a historical thread when app-server reports thread not found", async () => {
    const store = createSessionStore();
    store.update("sess-1", {
      status: "idle",
      activeTurnId: null,
    });
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080300,
            status: { type: "idle" },
            turns: [],
          },
        };
      }

      if (method === "turn/start") {
        if (client.request.mock.calls.filter(([calledMethod]) => calledMethod === "turn/start").length === 1) {
          throw new Error("thread not found: thread-1");
        }
        return { turn: { id: "turn-2" } };
      }

      if (method === "thread/resume") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080300,
            status: { type: "inactive" },
            turns: [],
          },
          cwd: "D:\\workspace\\codex-mobile",
          model: "gpt-5.5",
          approvalPolicy: "on-request",
          sandbox: "workspace-write",
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    await runner.submitInput("sess-1", {
      text: "继续这个历史线程",
      attachments: [],
    });

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "thread/read",
      expect.objectContaining({
        threadId: "thread-1",
        includeTurns: true,
      }),
    );
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "turn/start",
      expect.objectContaining({
        threadId: "thread-1",
      }),
    );
    expect(client.request).toHaveBeenNthCalledWith(
      3,
      "thread/resume",
      expect.objectContaining({
        threadId: "thread-1",
        excludeTurns: true,
      }),
    );
    expect(client.request).toHaveBeenNthCalledWith(
      4,
      "turn/start",
      expect.objectContaining({
        threadId: "thread-1",
      }),
    );
    expect(store.get("sess-1")).toMatchObject({
      activeTurnId: "turn-2",
      status: "running",
      lastError: null,
    });
  });

  test("syncs external idle status and clears stale active turn when attaching an existing session", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/resume") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080300,
            status: { type: "idle" },
            turns: [],
          },
          cwd: "D:\\workspace\\codex-mobile",
          model: "gpt-5.5",
          approvalPolicy: "on-request",
          serviceTier: "fast",
          reasoningEffort: "medium",
          sandbox: "danger-full-access",
        };
      }

      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080400,
            status: { type: "idle" },
            turns: [],
          },
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    const attached = await runner.attachSession("sess-1");

    expect(attached).toMatchObject({
      id: "sess-1",
      status: "idle",
      activeTurnId: null,
      sandboxMode: "danger-full-access",
    });
  });

  test("blocks turn/start when the thread is already active in another client", async () => {
    const store = createSessionStore();
    store.update("sess-1", {
      status: "idle",
      activeTurnId: null,
    });

    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080400,
            status: { type: "active", activeFlags: [] },
            turns: [
              {
                id: "turn-external",
                status: "inProgress",
                error: null,
                startedAt: 1716080300,
                completedAt: null,
                items: [],
              },
            ],
          },
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    await expect(
      runner.submitInput("sess-1", {
        text: "新输入",
        attachments: [],
      }),
    ).rejects.toThrow("thread-busy");
    expect(store.get("sess-1")).toMatchObject({
      status: "running",
      activeTurnId: "turn-external",
    });
    expect(client.request).toHaveBeenCalledTimes(1);
    expect(client.request).toHaveBeenCalledWith(
      "thread/read",
      expect.objectContaining({
        threadId: "thread-1",
        includeTurns: true,
      }),
    );
  });

  test("discovers the active turn from thread/read before interrupting", async () => {
    const store = createSessionStore();
    store.update("sess-1", {
      status: "running",
      activeTurnId: null,
    });

    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-1",
            cwd: "D:\\workspace\\codex-mobile",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080400,
            status: { type: "active", activeFlags: [] },
            turns: [
              {
                id: "turn-external",
                status: "inProgress",
                error: null,
                startedAt: 1716080300,
                completedAt: null,
                items: [],
              },
            ],
          },
        };
      }

      if (method === "turn/interrupt") {
        return {};
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    await runner.interrupt("sess-1");

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "thread/read",
      expect.objectContaining({
        threadId: "thread-1",
        includeTurns: true,
      }),
    );
    expect(client.request).toHaveBeenNthCalledWith(
      2,
      "turn/interrupt",
      {
        threadId: "thread-1",
        turnId: "turn-external",
      },
    );
    expect(store.get("sess-1")).toMatchObject({
      status: "idle",
      activeTurnId: null,
    });
  });
});
