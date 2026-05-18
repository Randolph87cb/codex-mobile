import Fastify, { type FastifyInstance } from "fastify";
import websocket from "@fastify/websocket";
import { z } from "zod";
import { isHistoryCapableRunner, type BridgeRunner } from "./bridge-runner.js";
import { AppServerRunner } from "./app-server-runner.js";
import { MockRunner } from "./mock-runner.js";
import { authorizeApiRequest, buildBridgeSecurityState, resolveBridgeSecurityConfig, validateSessionCwd } from "./security.js";
import { SessionStore } from "./session-store.js";
import type { BridgeSecurityConfig } from "./types.js";

const createSessionSchema = z.object({
  cwd: z.string().trim().min(1),
  model: z.string().trim().min(1).default("gpt-5.5"),
  approvalMode: z.enum(["manual", "auto"]).default("manual"),
});

const inputSchema = z.object({
  text: z.string().trim().min(1),
});

interface BuildBridgeAppOptions {
  runner?: BridgeRunner;
  store?: SessionStore;
  security?: BridgeSecurityConfig;
}

export async function buildBridgeApp(options: BuildBridgeAppOptions = {}): Promise<FastifyInstance> {
  const app = Fastify({ logger: true });
  const store = options.store ?? new SessionStore();
  const runner = options.runner ?? createRunner(store);
  const historyRunner = isHistoryCapableRunner(runner) ? runner : null;
  const security = options.security ?? resolveBridgeSecurityConfig();
  const securityState = buildBridgeSecurityState(security);

  await app.register(websocket);

  app.addHook("onClose", async () => {
    await runner.close?.();
  });

  app.addHook("onRequest", async (request, reply) => {
    const pathname = new URL(request.raw.url ?? "/", "http://bridge.local").pathname;
    if (!pathname.startsWith("/api/")) {
      return;
    }

    const authorization = authorizeApiRequest(request.headers.authorization, security);
    if (!authorization.ok) {
      reply.header("WWW-Authenticate", "Bearer");
      return reply.status(401).send({
        error: "unauthorized",
        message: authorization.message,
      });
    }
  });

  app.get("/health", async () => ({
    ok: true,
    service: "codex-mobile-bridge",
    runnerMode: runner.mode,
    security: securityState,
  }));

  app.get("/api/sessions", async () => ({
    items: historyRunner ? await historyRunner.listSessionViews() : store.list(),
  }));

  app.post("/api/session", async (request, reply) => {
    const parsed = createSessionSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: parsed.error.flatten(),
      });
    }

    const validatedCwd = validateSessionCwd(parsed.data.cwd, security);
    if (!validatedCwd.ok) {
      return reply.status(validatedCwd.error === "invalid-cwd" ? 400 : 403).send({
        error: validatedCwd.error,
        message: validatedCwd.message,
      });
    }

    const session = store.create({
      ...parsed.data,
      cwd: validatedCwd.cwd ?? parsed.data.cwd,
    });
    try {
      await runner.initializeSession(session.id);
    } catch (error) {
      store.delete(session.id);
      return reply.status(502).send({
        error: "session-initialize-failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }

    return reply.status(201).send(store.get(session.id));
  });

  app.get("/api/session/:id", async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    if (historyRunner) {
      const view = await historyRunner.getSessionView(params.id);
      if (view) {
        return view;
      }
    }

    const session = store.get(params.id);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    return session;
  });

  app.post("/api/session/:id/input", async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = inputSchema.safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    let session = store.get(params.id) ?? undefined;
    if (!session && historyRunner) {
      session = (await historyRunner.attachSession(params.id)) ?? undefined;
    }
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    try {
      await runner.submitInput(session.id, body.data.text);
    } catch (error) {
      return reply.status(502).send({
        error: "turn-start-failed",
        message: error instanceof Error ? error.message : String(error),
      });
    }

    return reply.status(202).send({ accepted: true });
  });

  app.post("/api/session/:id/interrupt", async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = store.get(params.id);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    await runner.interrupt(params.id);
    return { ok: true };
  });

  app.post("/api/session/:id/approve", async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = store.get(params.id);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    return {
      ok: false,
      message: "approval workflow is not wired yet",
    };
  });

  app.get("/api/session/:id/ws", { websocket: true }, (socket, request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = store.get(params.id);
    if (!session) {
      socket.send(JSON.stringify({ error: "session-not-found" }));
      socket.close();
      return;
    }

    socket.send(
      JSON.stringify({
        type: "session.started",
        sessionId: session.id,
        timestamp: new Date().toISOString(),
        data: {
          cwd: session.cwd,
          model: session.model,
          status: session.status,
          threadId: session.threadId,
        },
      }),
    );

    const unsubscribe = runner.subscribe(session.id, (event) => {
      socket.send(JSON.stringify(event));
    });

    socket.on("close", () => {
      unsubscribe();
    });
  });

  return app;
}

function createRunner(store: SessionStore): BridgeRunner {
  const mode = process.env.CODEX_MOBILE_RUNNER ?? "mock";
  return mode === "app-server" ? new AppServerRunner(store) : new MockRunner(store);
}
