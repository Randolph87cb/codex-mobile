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
    id: "thread-1",
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
  test("reads account quota and maps 5-hour and 1-week windows by duration", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "account/rateLimits/read") {
        return {
          rateLimits: {
            limitId: "fallback",
            primary: {
              usedPercent: 99,
              windowDurationMins: 15,
              resetsAt: 1716080900,
            },
          },
          rateLimitsByLimitId: {
            codex: {
              limitId: "codex",
              planType: "prolite",
              rateLimitReachedType: null,
              credits: {
                hasCredits: false,
                unlimited: false,
                balance: "0",
              },
              primary: {
                usedPercent: 6,
                windowDurationMins: 300,
                resetsAt: 1779709914,
              },
              secondary: {
                usedPercent: 16,
                windowDurationMins: 10080,
                resetsAt: 1780188081,
              },
            },
          },
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    const quota = await runner.getAccountQuota();

    expect(client.request).toHaveBeenCalledWith("account/rateLimits/read", undefined);
    expect(quota).toMatchObject({
      limitId: "codex",
      planType: "prolite",
      fiveHours: {
        usedPercent: 6,
        windowDurationMins: 300,
        resetsAt: "2026-05-25T11:51:54.000Z",
      },
      oneWeek: {
        usedPercent: 16,
        windowDurationMins: 10080,
        resetsAt: "2026-05-31T00:41:21.000Z",
      },
      credits: {
        hasCredits: false,
        unlimited: false,
        balance: "0",
      },
    });
  });

  test("finds quota windows even when the snapshot shape grows beyond primary and secondary", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "account/rateLimits/read") {
        return {
          rateLimitsByLimitId: {
            codex: {
              limitId: "codex",
              planType: "prolite",
              primary: {
                usedPercent: 3,
                windowDurationMins: 60,
                resetsAt: 1779700000,
              },
              secondary: {
                usedPercent: 10,
                windowDurationMins: 300,
                resetsAt: 1779709914,
              },
              tertiary: {
                usedPercent: 16,
                windowDurationMins: 10080,
                resetsAt: 1780188081,
              },
            },
          },
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    const quota = await runner.getAccountQuota();

    expect(quota.fiveHours).toMatchObject({
      usedPercent: 10,
      windowDurationMins: 300,
    });
    expect(quota.oneWeek).toMatchObject({
      usedPercent: 16,
      windowDurationMins: 10080,
    });
  });

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

    const runner = new AppServerRunner(store, client);
    const created = await runner.createSession({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "high",
      serviceTier: "default",
      sandboxMode: "read-only",
    });
    await runner.submitInput(created.id, {
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
    expect(store.get("thread-2")?.serviceTier).toBe("default");
    expect(store.get("thread-2")?.sandboxMode).toBe("read-only");
  });

  test("submits image attachments as localImage input blocks", async () => {
    const store = createSessionStore();
    store.update("thread-1", {
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
    await runner.submitInput("thread-1", {
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
    expect(store.get("thread-1")).toMatchObject({
      activeTurnId: "turn-image",
      status: "running",
      lastError: null,
    });
  });

  test("submits image-only input with a fallback text block", async () => {
    const store = createSessionStore();
    store.update("thread-1", {
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
    await runner.submitInput("thread-1", {
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
    runner.subscribe("thread-1", (event) => {
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

    expect(store.get("thread-1")?.status).toBe("awaiting_approval");
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

    const result = await runner.approve("thread-1", {
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
    expect(store.get("thread-1")?.status).toBe("running");
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
    runner.subscribe("thread-1", (event) => {
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
    runner.subscribe("thread-1", (event) => {
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

    await runner.approve("thread-1", {
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

    const view = await runner.getSessionView("thread-1");

    expect(view).toMatchObject({
      id: "thread-1",
      status: "awaiting_approval",
      pendingApproval: {
        requestId: "req-view",
        method: "item/commandExecution/requestApproval",
      },
    });
    expect(view?.pendingApproval?.paramsSummary).toContain("等待审批：item/commandExecution/requestApproval");
    expect(view?.pendingApproval?.paramsSummary).toContain("Copy-Item");
  });

  test("treats stale waitingOnApproval thread status as running when no pending approval remains", async () => {
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
            turns: [
              {
                id: "turn-1",
                status: "inProgress",
              },
            ],
          },
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);

    const view = await runner.getSessionView("thread-1");

    expect(view).toMatchObject({
      id: "thread-1",
      status: "running",
      pendingApproval: null,
    });
    expect(store.get("thread-1")?.status).toBe("running");
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
    expect(store.get("thread-1")?.status).toBe("running");
  });

  test("retries turn/start after resuming a historical thread when app-server reports thread not found", async () => {
    const store = createSessionStore();
    store.update("thread-1", {
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
    await runner.submitInput("thread-1", {
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
    expect(store.get("thread-1")).toMatchObject({
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
          approvalPolicy: "never",
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
    const attached = await runner.attachSession("thread-1");

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "thread/resume",
      expect.objectContaining({
        threadId: "thread-1",
        approvalPolicy: "never",
        sandbox: "danger-full-access",
        excludeTurns: true,
      }),
    );
    expect(attached).toMatchObject({
      id: "thread-1",
      status: "idle",
      activeTurnId: null,
      approvalMode: "auto",
      sandboxMode: "danger-full-access",
    });
  });

  test("forces managed policies when first attaching a historical thread by thread id", async () => {
    const store = new SessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/resume") {
        return {
          thread: {
            id: "thread-history",
            cwd: "D:\\workspace\\codex-pet-suite",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080400,
            status: { type: "idle" },
            turns: [],
          },
          cwd: "D:\\workspace\\codex-pet-suite",
          model: "gpt-5.5",
          approvalPolicy: "never",
          reasoningEffort: "high",
          sandbox: "danger-full-access",
        };
      }

      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-history",
            cwd: "D:\\workspace\\codex-pet-suite",
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
    const attached = await runner.attachSession("thread-history");

    expect(client.request).toHaveBeenNthCalledWith(
      1,
      "thread/resume",
      expect.objectContaining({
        threadId: "thread-history",
        approvalPolicy: "never",
        sandbox: "danger-full-access",
        excludeTurns: true,
      }),
    );
    expect(attached).toMatchObject({
      id: "thread-history",
      threadId: "thread-history",
      cwd: "D:\\workspace\\codex-pet-suite",
      approvalMode: "auto",
      sandboxMode: "danger-full-access",
      reasoningEffort: "high",
      status: "idle",
    });
  });

  test("filters archived thread lists and keeps archived attached sessions out of active list", async () => {
    const store = createSessionStore();
    store.attach({
      id: "thread-archived",
      cwd: "D:\\workspace\\archived",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
      status: "idle",
      threadId: "thread-archived",
      activeTurnId: null,
      lastError: null,
      createdAt: "2026-05-19T02:00:00.000Z",
      updatedAt: "2026-05-19T02:00:00.000Z",
    });
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string, params: any) => {
      if (method === "thread/list" && params?.archived === false) {
        return {
          data: [
            {
              id: "thread-1",
              cwd: "D:\\workspace\\codex-mobile",
              modelProvider: "openai",
              createdAt: 1716080000,
              updatedAt: 1716080300,
              status: { type: "inactive" },
              turns: [],
            },
          ],
        };
      }

      if (method === "thread/list" && params?.archived === true) {
        return {
          data: [
            {
              id: "thread-archived",
              cwd: "D:\\workspace\\archived",
              modelProvider: "openai",
              createdAt: 1716080000,
              updatedAt: 1716080400,
              status: { type: "inactive" },
              turns: [],
            },
          ],
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    const activeViews = await runner.listSessionViews(false);
    const archivedViews = await runner.listSessionViews(true);

    expect(activeViews.map((view) => view.id)).toEqual(["thread-1"]);
    expect(activeViews.every((view) => !view.archived)).toBe(true);
    expect(archivedViews.map((view) => view.id)).toEqual(["thread-archived"]);
    expect(archivedViews.every((view) => view.archived)).toBe(true);
  });

  test("sorts active sessions by last activity instead of local refresh time", async () => {
    const store = new SessionStore();
    store.attach({
      id: "thread-1",
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
      status: "idle",
      threadId: "thread-1",
      activeTurnId: null,
      lastError: null,
      createdAt: "2026-05-19T01:00:00.000Z",
      updatedAt: "2026-05-19T09:00:00.000Z",
      lastActivityAt: "2026-05-19T01:30:00.000Z",
    });
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string, params: any) => {
      if (method === "thread/list" && params?.archived === false) {
        return {
          data: [
            {
              id: "thread-1",
              cwd: "D:\\workspace\\codex-mobile",
              modelProvider: "openai",
              createdAt: 1716080000,
              updatedAt: "2026-05-19T02:00:00.000Z",
              status: { type: "inactive" },
              turns: [],
            },
            {
              id: "thread-2",
              cwd: "D:\\workspace\\another",
              modelProvider: "openai",
              createdAt: 1716080000,
              updatedAt: "2026-05-19T03:00:00.000Z",
              status: { type: "inactive" },
              turns: [],
            },
          ],
        };
      }

      if (method === "thread/list" && params?.archived === true) {
        return { data: [] };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    const views = await runner.listSessionViews(false);

    expect(views.map((view) => view.id)).toEqual(["thread-2", "thread-1"]);
    expect(views[1]?.lastUpdated).toBe("2026-05-19T02:00:00.000Z");
  });

  test("archives attached thread-backed sessions and removes them from local store", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    const runner = new AppServerRunner(store, client);

    await runner.archiveSession("thread-1");

    expect(client.request).toHaveBeenCalledWith("thread/archive", {
      threadId: "thread-1",
    });
    expect(store.get("thread-1")).toBeUndefined();
  });

  test("unarchives history sessions by thread id", async () => {
    const store = new SessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string) => {
      if (method === "thread/read") {
        return {
          thread: {
            id: "thread-history",
            cwd: "D:\\workspace\\codex-pet-suite",
            modelProvider: "openai",
            createdAt: 1716080000,
            updatedAt: 1716080400,
            status: { type: "inactive" },
            turns: [],
          },
        };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    await runner.unarchiveSession("thread-history");

    expect(client.request).toHaveBeenCalledWith("thread/unarchive", {
      threadId: "thread-history",
    });
  });

  test("blocks turn/start when the thread is already active in another client", async () => {
    const store = createSessionStore();
    store.update("thread-1", {
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
      runner.submitInput("thread-1", {
        text: "新输入",
        attachments: [],
      }),
    ).rejects.toThrow("thread-busy");
    expect(store.get("thread-1")).toMatchObject({
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
    store.update("thread-1", {
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
    await runner.interrupt("thread-1");

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
    expect(store.get("thread-1")).toMatchObject({
      status: "idle",
      activeTurnId: null,
    });
  });

  test("reads, updates, and clears thread goals through app-server methods", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    client.request.mockImplementation(async (method: string, params: unknown) => {
      if (method === "thread/goal/get") {
        expect(params).toEqual({ threadId: "thread-1" });
        return {
          goal: {
            threadId: "thread-1",
            objective: "把详情页实时流稳定下来",
            status: "active",
            tokenBudget: 200000,
            tokensUsed: 3200,
            timeUsedSeconds: 240,
            createdAt: 1776272400,
            updatedAt: 1776272640,
          },
        };
      }

      if (method === "thread/goal/set") {
        expect(params).toEqual({
          threadId: "thread-1",
          objective: "把详情页实时流稳定下来",
          tokenBudget: 250000,
        });
        return {
          goal: {
            threadId: "thread-1",
            objective: "把详情页实时流稳定下来",
            status: "active",
            tokenBudget: 250000,
            tokensUsed: 3300,
            timeUsedSeconds: 245,
            createdAt: 1776272400,
            updatedAt: 1776272700,
          },
        };
      }

      if (method === "thread/goal/clear") {
        expect(params).toEqual({ threadId: "thread-1" });
        return { cleared: true };
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);
    await expect(runner.getSessionGoal("thread-1")).resolves.toMatchObject({
      capability: "supported",
      goal: {
        objective: "把详情页实时流稳定下来",
        status: "active",
        tokenBudget: 200000,
        tokensUsed: 3200,
        timeUsedSeconds: 240,
      },
    });
    await expect(
      runner.updateSessionGoal("thread-1", {
        objective: "把详情页实时流稳定下来",
        tokenBudget: 250000,
      }),
    ).resolves.toMatchObject({
      capability: "supported",
      goal: {
        tokenBudget: 250000,
        tokensUsed: 3300,
      },
    });
    await expect(runner.clearSessionGoal("thread-1")).resolves.toEqual({
      capability: "supported",
      cleared: true,
    });
  });

  test("downgrades missing thread_goals schema errors to unsupported goal capability", async () => {
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
            status: { type: "idle" },
            turns: [],
          },
        };
      }

      if (method === "thread/goal/get") {
        throw new Error("failed to read thread goal: error returned from database: (code: 1) no such table: thread_goals");
      }

      if (method === "thread/goal/set") {
        throw new Error("failed to write thread goal: error returned from database: (code: 1) no such table: thread_goals");
      }

      if (method === "thread/goal/clear") {
        throw new Error("failed to clear thread goal: error returned from database: (code: 1) no such table: thread_goals");
      }

      return {};
    });

    const runner = new AppServerRunner(store, client);

    await expect(runner.getSessionGoal("thread-1")).resolves.toEqual({
      capability: "unsupported",
      goal: null,
    });
    await expect(runner.getSessionView("thread-1")).resolves.toMatchObject({
      id: "thread-1",
      goal: null,
      goalCapability: "unsupported",
    });
    await expect(
      runner.updateSessionGoal("thread-1", {
        objective: "验证 goal 可用性",
      }),
    ).rejects.toThrow("goal-not-supported");
    await expect(runner.clearSessionGoal("thread-1")).rejects.toThrow("goal-not-supported");
  });

  test("emits goal updates and clears as bridge events", async () => {
    const store = createSessionStore();
    const client = new FakeAppServerClient();
    const runner = new AppServerRunner(store, client);
    const events: BridgeEvent[] = [];
    runner.subscribe("thread-1", (event) => {
      events.push(event);
    });

    client.emitNotification({
      method: "thread/goal/updated",
      params: {
        threadId: "thread-1",
        goal: {
          threadId: "thread-1",
          objective: "把详情页实时流稳定下来",
          status: "paused",
          tokenBudget: 200000,
          tokensUsed: 6400,
          timeUsedSeconds: 480,
          createdAt: 1776272400,
          updatedAt: 1776272880,
        },
      },
    });
    client.emitNotification({
      method: "thread/goal/cleared",
      params: {
        threadId: "thread-1",
      },
    });

    expect(events).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: "goal.updated",
          data: expect.objectContaining({
            goal: expect.objectContaining({
              objective: "把详情页实时流稳定下来",
              status: "paused",
              tokenBudget: 200000,
              tokensUsed: 6400,
            }),
            goalCapability: "supported",
          }),
        }),
        expect.objectContaining({
          type: "goal.cleared",
          data: expect.objectContaining({
            goalCapability: "supported",
          }),
        }),
      ]),
    );
  });
});

