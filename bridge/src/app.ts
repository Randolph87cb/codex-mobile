import Fastify, { type FastifyInstance } from "fastify";
import multipart from "@fastify/multipart";
import websocket from "@fastify/websocket";
import { AttachmentStore } from "./attachment-store.js";
import { type BuildBridgeAppOptions, type BridgeAppDependencies } from "./app-context.js";
import { buildAttachmentTooLargeError, createAttachmentService } from "./attachment-service.js";
import { isHistoryCapableRunner, type BridgeRunner } from "./bridge-runner.js";
import { AppServerRunner } from "./app-server-runner.js";
import { createBridgeLifecycleController } from "./lifecycle-service.js";
import { MockRunner } from "./mock-runner.js";
import { registerBridgeRoutes } from "./routes.js";
import { buildBridgeSecurityState, authorizeApiRequest, resolveBridgeSecurityConfig } from "./security.js";
import { createSessionService } from "./session-service.js";
import { SessionStore } from "./session-store.js";

export async function buildBridgeApp(options: BuildBridgeAppOptions = {}): Promise<FastifyInstance> {
  const uploadBodyLimitBytes = resolveBridgeBodyLimitBytes();
  const app = Fastify({
    logger: true,
    bodyLimit: uploadBodyLimitBytes,
  });
  const store = options.store ?? new SessionStore();
  const attachmentStore = new AttachmentStore();
  const runner = options.runner ?? createRunner(store);
  const historyRunner = isHistoryCapableRunner(runner) ? runner : null;
  const security = options.security ?? resolveBridgeSecurityConfig();
  const bridgeVersion = process.env.CODEX_MOBILE_BRIDGE_VERSION ?? "0.1.0";
  const bridgeStartedAt = new Date().toISOString();

  const deps: BridgeAppDependencies = {
    runner,
    historyRunner,
    store,
    attachmentStore,
    security,
    securityState: buildBridgeSecurityState(security),
    lifecycle: createBridgeLifecycleController({
      bridgeVersion,
      bridgeStartedAt,
    }),
  };

  const attachmentService = createAttachmentService({
    attachmentStore,
    security,
    resolveSessionRecord: async (sessionId) => {
      if (historyRunner) {
        const session = await historyRunner.attachSession(sessionId);
        if (session) {
          return session;
        }
      }

      return store.get(sessionId) ?? null;
    },
  });
  const sessionService = createSessionService(
    deps,
    attachmentService.resolveSessionInputAttachments,
  );

  await app.register(websocket);
  await app.register(multipart, {
    limits: {
      fileSize: uploadBodyLimitBytes,
      files: 1,
    },
  });

  app.setErrorHandler((error, request, reply) => {
    const pathname = new URL(request.raw.url ?? "/", "http://bridge.local").pathname;
    if ((error as { code?: string }).code === "FST_ERR_CTP_BODY_TOO_LARGE"
      && (pathname === "/api/attachment/image" || pathname === "/api/attachment/video")) {
      return reply.status(413).send(
        buildAttachmentTooLargeError(
          uploadBodyLimitBytes,
          pathname === "/api/attachment/video" ? "video" : "image",
        ),
      );
    }

    return reply.send(error);
  });

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

  await registerBridgeRoutes(app, {
    deps,
    sessionService,
    attachmentService,
    uploadBodyLimitBytes,
  });

  return app;
}

function resolveBridgeBodyLimitBytes(): number {
  const configuredMb = Number.parseInt(process.env.BRIDGE_BODY_LIMIT_MB ?? "", 10);
  const limitMb = Number.isFinite(configuredMb) && configuredMb > 0 ? configuredMb : 64;
  return limitMb * 1024 * 1024;
}

function createRunner(store: SessionStore): BridgeRunner {
  const mode = process.env.CODEX_MOBILE_RUNNER ?? "mock";
  return mode === "app-server" ? new AppServerRunner(store) : new MockRunner(store);
}
