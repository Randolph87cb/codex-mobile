import { afterEach, describe, expect, test, vi } from "vitest";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";
import type { HistoryCapableBridgeRunner } from "../src/bridge-runner.js";
import { buildBridgeApp } from "../src/app.js";
import { SessionStore } from "../src/session-store.js";
import type {
  BridgeSecurityConfig,
  ResolvedSessionInput,
  SessionApprovalInput,
  SessionApprovalResult,
  SessionView,
} from "../src/types.js";

class TestRunner implements HistoryCapableBridgeRunner {
  readonly mode = "mock" as const;
  readonly initializeSession = vi.fn(async (sessionId: string) => {
    this.store.update(sessionId, { threadId: "thread-test" });
  });
  readonly submitInput = vi.fn(async (_sessionId: string, _input: ResolvedSessionInput) => undefined);
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
        reasoningEffort: "medium",
        serviceTier: "default",
        sandboxMode: "workspace-write",
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
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
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
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
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
  const tempDirs: string[] = [];

  afterEach(() => {
    vi.restoreAllMocks();
  });

  function createSecurityConfig(overrides: Partial<BridgeSecurityConfig> = {}): BridgeSecurityConfig {
    return {
      token: null,
      allowedCwds: [],
      ...overrides,
    };
  }

  test("creates sessions and returns runner mode in health", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });

    const health = await app.inject({ method: "GET", url: "/health" });
    expect(health.statusCode).toBe(200);
    expect(health.json()).toMatchObject({
      ok: true,
      runnerMode: "mock",
      security: {
        tokenAuthEnabled: false,
        cwdWhitelistEnabled: false,
      },
    });

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
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
      threadId: "thread-test",
    });

    await app.close();
  });

  test("keeps health public and protects api routes with bearer token", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({
      store,
      runner,
      security: createSecurityConfig({ token: "bridge-secret" }),
    });

    const health = await app.inject({ method: "GET", url: "/health" });
    expect(health.statusCode).toBe(200);
    expect(health.json()).toMatchObject({
      security: {
        tokenAuthEnabled: true,
        cwdWhitelistEnabled: false,
      },
    });

    const unauthenticated = await app.inject({ method: "GET", url: "/api/sessions" });
    expect(unauthenticated.statusCode).toBe(401);
    expect(unauthenticated.headers["www-authenticate"]).toBe("Bearer");
    expect(unauthenticated.json()).toMatchObject({
      error: "unauthorized",
      message: "missing bearer token",
    });

    const authenticated = await app.inject({
      method: "GET",
      url: "/api/sessions",
      headers: {
        authorization: "Bearer bridge-secret",
      },
    });
    expect(authenticated.statusCode).toBe(200);

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

  test("enforces cwd whitelist only when configured", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({
      store,
      runner,
      security: createSecurityConfig({
        allowedCwds: ["d:\\workspace\\codex-mobile"],
      }),
    });

    const relativeCreate = await app.inject({
      method: "POST",
      url: "/api/session",
      payload: {
        cwd: ".",
        model: "gpt-5.5",
        approvalMode: "manual",
      },
    });
    expect(relativeCreate.statusCode).toBe(400);
    expect(relativeCreate.json()).toMatchObject({
      error: "invalid-cwd",
    });

    const forbiddenCreate = await app.inject({
      method: "POST",
      url: "/api/session",
      payload: {
        cwd: "D:\\other",
        model: "gpt-5.5",
        approvalMode: "manual",
      },
    });
    expect(forbiddenCreate.statusCode).toBe(403);
    expect(forbiddenCreate.json()).toMatchObject({
      error: "cwd-not-allowed",
    });

    const allowedCreate = await app.inject({
      method: "POST",
      url: "/api/session",
      payload: {
        cwd: "D:\\workspace\\codex-mobile\\bridge",
        model: "gpt-5.5",
        approvalMode: "manual",
      },
    });
    expect(allowedCreate.statusCode).toBe(201);
    expect(allowedCreate.json()).toMatchObject({
      cwd: "D:\\workspace\\codex-mobile\\bridge",
    });

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
    expect(runner.submitInput).toHaveBeenCalledWith("thread-history", {
      text: "你是谁",
      attachments: [],
    });

    await app.close();
  });

  test("serves uploaded image content from attachment route", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    const payload = Buffer.from("png-image-content");

    const upload = await app.inject({
      method: "POST",
      url: "/api/attachment/image",
      payload: {
        displayName: "sample.png",
        mimeType: "image/png",
        contentBase64: payload.toString("base64"),
      },
    });

    expect(upload.statusCode).toBe(201);
    const uploaded = upload.json<{ id: string; path: string }>();
    const attachmentId = uploaded.id;
    expect(uploaded.path).toContain(attachmentId);

    const content = await app.inject({
      method: "GET",
      url: `/api/attachment/image/${attachmentId}/content`,
    });

    expect(content.statusCode).toBe(200);
    expect(content.headers["content-type"]).toContain("image/png");
    expect(content.headers["cache-control"]).toBe("no-store");
    expect(content.body).toBe(payload.toString());

    await app.close();
  });

  test("rejects invalid image uploads", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });

    const unsupportedMimeType = await app.inject({
      method: "POST",
      url: "/api/attachment/image",
      payload: {
        displayName: "sample.svg",
        mimeType: "image/svg+xml",
        contentBase64: Buffer.from("<svg/>").toString("base64"),
      },
    });
    expect(unsupportedMimeType.statusCode).toBe(400);
    expect(unsupportedMimeType.json()).toMatchObject({
      error: "invalid-image-upload",
      message: "unsupported-image-mime-type",
    });

    const invalidBase64 = await app.inject({
      method: "POST",
      url: "/api/attachment/image",
      payload: {
        displayName: "sample.jpg",
        mimeType: "image/jpeg",
        contentBase64: "not-base64!!",
      },
    });
    expect(invalidBase64.statusCode).toBe(400);
    expect(invalidBase64.json()).toMatchObject({
      error: "invalid-image-upload",
      message: "invalid-image-base64",
    });

    await app.close();
  });

  test("updates persisted session config through patch route", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
    });

    const response = await app.inject({
      method: "PATCH",
      url: `/api/session/${session.id}/config`,
      payload: {
        cwd: "D:\\workspace\\codex-mobile\\bridge",
        model: "gpt-5",
        approvalMode: "auto",
        reasoningEffort: "high",
        serviceTier: "fast",
        sandboxMode: "danger-full-access",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      id: session.id,
      cwd: "D:\\workspace\\codex-mobile\\bridge",
      model: "gpt-5",
      approvalMode: "auto",
      reasoningEffort: "high",
      serviceTier: "fast",
      sandboxMode: "danger-full-access",
    });

    await app.close();
  });

  test("accepts default service tier and keeps it as ordinary mode", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "fast",
      sandboxMode: "danger-full-access",
    });

    const response = await app.inject({
      method: "PATCH",
      url: `/api/session/${session.id}/config`,
      payload: {
        serviceTier: "default",
        sandboxMode: "workspace-write",
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      id: session.id,
      serviceTier: "default",
      sandboxMode: "workspace-write",
    });

    await app.close();
  });

  test("uploads an image and resolves legacy attachment ids into localImage input items", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
    });

    const upload = await app.inject({
      method: "POST",
      url: "/api/attachment/image",
      payload: {
        displayName: "sample.jpg",
        mimeType: "image/jpeg",
        contentBase64: Buffer.from("fake-image").toString("base64"),
      },
    });

    expect(upload.statusCode).toBe(201);
    const attachmentId = upload.json<{ id: string; path: string }>().id;

    const input = await app.inject({
      method: "POST",
      url: `/api/session/${session.id}/input`,
      payload: {
        text: "帮我看这张图",
        attachments: [
          {
            id: attachmentId,
          },
        ],
      },
    });

    expect(input.statusCode).toBe(202);
    expect(runner.submitInput).toHaveBeenCalledWith(
      session.id,
      expect.objectContaining({
        text: "帮我看这张图",
        attachments: [
          expect.objectContaining({
            id: attachmentId,
            kind: "image",
            displayName: "sample.jpg",
            mimeType: "image/jpeg",
          }),
        ],
      }),
    );

    await app.close();
  });

  test("uploads an image and allows input to reference the staged file path", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
    });

    const upload = await app.inject({
      method: "POST",
      url: "/api/attachment/image",
      payload: {
        displayName: "staged.png",
        mimeType: "image/png",
        contentBase64: Buffer.from("fake-image-path").toString("base64"),
      },
    });

    expect(upload.statusCode).toBe(201);
    const uploaded = upload.json<{ id: string; path: string }>();
    expect(uploaded.path).toContain(uploaded.id);

    const input = await app.inject({
      method: "POST",
      url: `/api/session/${session.id}/input`,
      payload: {
        attachments: [
          {
            path: uploaded.path,
          },
        ],
      },
    });

    expect(input.statusCode).toBe(202);
    expect(runner.submitInput).toHaveBeenCalledWith(
      session.id,
      expect.objectContaining({
        text: "",
        attachments: [
          expect.objectContaining({
            id: uploaded.id,
            kind: "image",
            path: uploaded.path,
            displayName: "staged.png",
            mimeType: "image/png",
          }),
        ],
      }),
    );

    await app.close();
  });

  test("rejects attachment paths that were not staged by bridge upload", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
    });

    const response = await app.inject({
      method: "POST",
      url: `/api/session/${session.id}/input`,
      payload: {
        attachments: [
          {
            path: "D:\\workspace\\codex-mobile\\not-staged.png",
          },
        ],
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "attachment-not-found",
      message: "attachment-not-found:D:\\workspace\\codex-mobile\\not-staged.png",
    });

    await app.close();
  });

  test("serves whitelisted image files and rejects paths outside the whitelist", async () => {
    const allowedRoot = await mkdtemp(path.join(tmpdir(), "codex-mobile-bridge-image-file-"));
    tempDirs.push(allowedRoot);
    const filePath = path.join(allowedRoot, "history-image.png");
    await writeFile(filePath, "history-image-content");

    const blockedRoot = await mkdtemp(path.join(tmpdir(), "codex-mobile-bridge-image-file-blocked-"));
    tempDirs.push(blockedRoot);
    const blockedFilePath = path.join(blockedRoot, "outside.jpg");
    await writeFile(blockedFilePath, "outside-image-content");

    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({
      store,
      runner,
      security: createSecurityConfig({
        allowedCwds: [allowedRoot.toLowerCase()],
      }),
    });

    const allowedResponse = await app.inject({
      method: "GET",
      url: `/api/image/file?path=${encodeURIComponent(filePath)}`,
    });
    expect(allowedResponse.statusCode).toBe(200);
    expect(allowedResponse.headers["content-type"]).toContain("image/png");
    expect(allowedResponse.body).toBe("history-image-content");

    const forbiddenResponse = await app.inject({
      method: "GET",
      url: `/api/image/file?path=${encodeURIComponent(blockedFilePath)}`,
    });
    expect(forbiddenResponse.statusCode).toBe(403);
    expect(forbiddenResponse.json()).toMatchObject({
      error: "image-path-not-allowed",
    });

    await app.close();
  });

  test("returns 409 when thread is already active in another client", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    runner.submitInput.mockRejectedValueOnce(new Error("thread-busy"));
    const app = await buildBridgeApp({ store, runner });
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
    });

    const response = await app.inject({
      method: "POST",
      url: `/api/session/${session.id}/input`,
      payload: {
        text: "hello",
      },
    });

    expect(response.statusCode).toBe(409);
    expect(response.json()).toMatchObject({
      error: "thread-busy",
    });

    await app.close();
  });

  test("attaches history sessions for interrupt and approve routes", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });

    const interrupt = await app.inject({
      method: "POST",
      url: "/api/session/thread-history/interrupt",
    });
    expect(interrupt.statusCode).toBe(200);
    expect(runner.interrupt).toHaveBeenCalledWith("thread-history");

    const approve = await app.inject({
      method: "POST",
      url: "/api/session/thread-history/approve",
      payload: {
        requestId: "req-history",
        decision: "approve",
      },
    });
    expect(approve.statusCode).toBe(200);
    expect(runner.approve).toHaveBeenCalledWith("thread-history", {
      requestId: "req-history",
      decision: "approve",
    });

    await app.close();
  });

  test("attaches history sessions for websocket subscriptions", async () => {
    const store = new SessionStore();
    const runner = new TestRunner(store);
    const app = await buildBridgeApp({ store, runner });
    await app.listen({ port: 0, host: "127.0.0.1" });
    const address = app.server.address();
    if (!address || typeof address === "string") {
      throw new Error("unexpected-server-address");
    }

    const payload = await new Promise<string>((resolve, reject) => {
      const socket = new WebSocket(`ws://127.0.0.1:${address.port}/api/session/thread-history/ws`);
      socket.addEventListener("message", (event) => {
        resolve(String(event.data));
        socket.close();
      });
      socket.addEventListener("error", () => {
        reject(new Error("websocket-connect-failed"));
      });
    });

    expect(JSON.parse(payload)).toMatchObject({
      type: "session.started",
      sessionId: "thread-history",
      data: {
        threadId: "thread-history",
        sandboxMode: "workspace-write",
      },
    });

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
      reasoningEffort: "medium",
      serviceTier: "default",
      sandboxMode: "workspace-write",
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

  afterEach(async () => {
    while (tempDirs.length > 0) {
      const dir = tempDirs.pop();
      if (!dir) {
        continue;
      }

      await rm(dir, { recursive: true, force: true });
    }
  });
});
