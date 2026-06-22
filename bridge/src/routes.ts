import type { FastifyInstance, FastifyReply } from "fastify";
import { z } from "zod";
import { isBridgeServiceError, type BridgeAppDependencies } from "./app-context.js";
import {
  buildAttachmentTooLargeError,
  isAttachmentUploadTooLargeError,
  sendDownloadFile,
  sendImageFile,
  type BridgeAttachmentService,
} from "./attachment-service.js";
import { buildBridgeLifecycleEvent, isLoopbackRequest } from "./lifecycle-service.js";
import {
  approveSchema,
  createSessionSchema,
  inputSchema,
  listSessionsQuerySchema,
  type BridgeSessionService,
  updateSessionConfigSchema,
  updateSessionGoalSchema,
  updateSessionTitleSchema,
} from "./session-service.js";

const drainBridgeSchema = z.object({
  reason: z.string().trim().min(1).max(200).optional(),
  graceMs: z.number().int().min(0).max(15_000).optional(),
});

const restartBridgeSchema = z.object({
  reason: z.string().trim().min(1).max(200).optional(),
  graceMs: z.number().int().min(0).max(15_000).optional(),
});

interface RegisterBridgeRoutesOptions {
  deps: BridgeAppDependencies;
  sessionService: BridgeSessionService;
  attachmentService: BridgeAttachmentService;
  uploadBodyLimitBytes: number;
}

export async function registerBridgeRoutes(
  app: FastifyInstance,
  options: RegisterBridgeRoutesOptions,
): Promise<void> {
  registerHealthAndLifecycleRoutes(app, options);
  registerAdminRoutes(app, options);
  registerAccountRoutes(app, options);
  registerAttachmentRoutes(app, options);
  registerSessionRoutes(app, options);
  registerRealtimeRoutes(app, options);
}

function registerAdminRoutes(
  app: FastifyInstance,
  { deps }: RegisterBridgeRoutesOptions,
): void {
  app.post("/api/admin/restart", async (request, reply) => {
    const body = restartBridgeSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    const reason = body.data.reason ?? "mobile bridge restart requested";
    const graceMs = body.data.graceMs ?? 2_000;
    const lifecycle = deps.lifecycle.beginDrain(reason, graceMs);
    deps.lifecycle.broadcastLifecycle();

    try {
      const scheduled = await deps.restartScheduler.scheduleRestart({
        reason,
        graceMs: lifecycle.drainGraceMs ?? graceMs,
      });
      return reply.status(202).send({
        ok: true,
        phase: scheduled.phase,
        message: scheduled.message,
        lifecycle: deps.lifecycle.buildLifecycleState(),
      });
    } catch (error) {
      request.log.error({ err: error }, "failed to schedule bridge restart");
      deps.lifecycle.cancelDrain();
      deps.lifecycle.broadcastLifecycle();
      return reply.status(500).send({
        error: "restart-schedule-failed",
        message: "bridge 重启调度失败，请检查 Windows 侧日志。",
        lifecycle: deps.lifecycle.buildLifecycleState(),
      });
    }
  });
}

function registerHealthAndLifecycleRoutes(
  app: FastifyInstance,
  { deps }: RegisterBridgeRoutesOptions,
): void {
  app.get("/health", async () => ({
    ok: true,
    service: "codex-mobile-bridge",
    runnerMode: deps.runner.mode,
    bridgeVersion: deps.lifecycle.buildLifecycleState().bridgeVersion,
    startedAt: deps.lifecycle.buildLifecycleState().startedAt,
    lifecycle: deps.lifecycle.buildLifecycleState(),
    security: deps.securityState,
  }));

  app.post("/internal/lifecycle/drain", async (request, reply) => {
    if (!isLoopbackRequest(request)) {
      return reply.status(403).send({
        error: "forbidden",
        message: "bridge drain is only allowed from loopback clients",
      });
    }

    const body = drainBridgeSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    deps.lifecycle.beginDrain(body.data.reason, body.data.graceMs);
    deps.lifecycle.broadcastLifecycle();
    return {
      ok: true,
      lifecycle: deps.lifecycle.buildLifecycleState(),
    };
  });
}

function registerAccountRoutes(
  app: FastifyInstance,
  { sessionService }: RegisterBridgeRoutesOptions,
): void {
  app.get("/api/account/quota", async (_request, reply) => {
    try {
      return await sessionService.getAccountQuota();
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });
}

function registerAttachmentRoutes(
  app: FastifyInstance,
  { deps, attachmentService, uploadBodyLimitBytes }: RegisterBridgeRoutesOptions,
): void {
  app.post("/api/attachment/image", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("attachment-upload"));
    }
    try {
      const attachment = await attachmentService.uploadImage(request);
      return reply.status(201).send(attachment);
    } catch (error) {
      if (isAttachmentUploadTooLargeError(error)) {
        return reply.status(413).send(buildAttachmentTooLargeError(uploadBodyLimitBytes, "image"));
      }
      if (isBridgeServiceError(error)) {
        return reply.status(error.statusCode).send(error.payload);
      }
      return reply.status(400).send({
        error: "invalid-image-upload",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  app.post("/api/attachment/video", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("attachment-upload"));
    }
    try {
      const attachment = await attachmentService.uploadVideo(request);
      return reply.status(201).send(attachment);
    } catch (error) {
      if (isAttachmentUploadTooLargeError(error)) {
        return reply.status(413).send(buildAttachmentTooLargeError(uploadBodyLimitBytes, "video"));
      }
      if (isBridgeServiceError(error)) {
        return reply.status(error.statusCode).send(error.payload);
      }
      return reply.status(400).send({
        error: "invalid-video-upload",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  app.get("/api/attachment/image/:id/content", async (request, reply) => {
    const params = z.object({ id: z.string().trim().min(1) }).parse(request.params);
    const image = await attachmentService.readImageContent(params.id);
    if (!image) {
      return reply.status(404).send({ error: "attachment-not-found" });
    }

    return sendImageFile(reply, image.filePath, image.mimeType);
  });

  app.get("/api/image/file", async (request, reply) => {
    const query = z.object({ path: z.string().trim().min(1) }).safeParse(request.query);
    if (!query.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: query.error.flatten(),
      });
    }

    try {
      const file = await attachmentService.resolveDownloadableFile(query.data.path, "image-path-not-allowed");
      return sendImageFile(reply, file.filePath, file.mimeType);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.get("/api/file/download", async (request, reply) => {
    const query = z.object({ path: z.string().trim().min(1) }).safeParse(request.query);
    if (!query.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: query.error.flatten(),
      });
    }

    try {
      const file = await attachmentService.resolveDownloadableFile(query.data.path, "file-path-not-allowed");
      return sendDownloadFile(reply, file.filePath, file.displayName, file.mimeType);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });
}

function registerSessionRoutes(
  app: FastifyInstance,
  { deps, sessionService }: RegisterBridgeRoutesOptions,
): void {
  app.get("/api/sessions", async (request, reply) => {
    const query = listSessionsQuerySchema.safeParse(request.query ?? {});
    if (!query.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: query.error.flatten(),
      });
    }

    return {
      items: await sessionService.listSessionViews(query.data.archived === "true"),
    };
  });

  app.post("/api/session", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-create"));
    }

    const parsed = createSessionSchema.safeParse(request.body);
    if (!parsed.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: parsed.error.flatten(),
      });
    }

    try {
      const session = await sessionService.createSession(parsed.data);
      return reply.status(201).send(session);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.patch("/api/session/:id/config", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-config-update"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = updateSessionConfigSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    try {
      return await sessionService.updateSessionConfig(params.id, body.data);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.patch("/api/session/:id/title", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-title-update"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = updateSessionTitleSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    try {
      return await sessionService.renameSessionTitle(params.id, body.data.title);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.get("/api/session/:id", async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const view = await sessionService.getSessionView(params.id);
    if (!view) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    return view;
  });

  app.get("/api/session/:id/goal", async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    try {
      return await sessionService.getSessionGoal(params.id);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.put("/api/session/:id/goal", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-goal-update"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = updateSessionGoalSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    try {
      return await sessionService.updateSessionGoal(params.id, body.data);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.delete("/api/session/:id/goal", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-goal-clear"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    try {
      return await sessionService.clearSessionGoal(params.id);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.post("/api/session/:id/input", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-input"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = inputSchema.safeParse(request.body);
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    try {
      await sessionService.submitInput(params.id, body.data);
      return reply.status(202).send({ accepted: true });
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.post("/api/session/:id/archive", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-archive"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    try {
      return await sessionService.archive(params.id);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.post("/api/session/:id/unarchive", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-unarchive"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    try {
      return await sessionService.unarchive(params.id);
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.post("/api/session/:id/interrupt", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-interrupt"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    try {
      await sessionService.interrupt(params.id);
      return { ok: true };
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });

  app.post("/api/session/:id/approve", async (request, reply) => {
    if (deps.lifecycle.isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-approve"));
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = approveSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    try {
      const result = await sessionService.approve(params.id, body.data);
      return {
        ok: true,
        requestId: result.requestId,
        decision: result.decision,
        method: result.method,
        status: result.status,
      };
    } catch (error) {
      return sendServiceError(reply, error);
    }
  });
}

function registerRealtimeRoutes(
  app: FastifyInstance,
  { deps, sessionService }: RegisterBridgeRoutesOptions,
): void {
  app.get("/api/session/:id/ws", { websocket: true }, async (socket, request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const state = await sessionService.getRealtimeSessionState(params.id);
    if (!state) {
      socket.send(JSON.stringify({ error: "session-not-found" }));
      socket.close();
      return;
    }

    socket.send(
      JSON.stringify({
        type: "session.started",
        sessionId: state.session.id,
        timestamp: new Date().toISOString(),
        data: {
          cwd: state.view?.cwd ?? state.session.cwd,
          model: state.view?.model ?? state.session.model,
          approvalMode: state.view?.approvalMode ?? state.session.approvalMode,
          reasoningEffort: state.view?.reasoningEffort ?? state.session.reasoningEffort,
          serviceTier: state.view?.serviceTier ?? state.session.serviceTier,
          sandboxMode: state.view?.sandboxMode ?? state.session.sandboxMode,
          status: state.view?.status ?? state.session.status,
          threadId: state.view?.threadId ?? state.session.threadId,
          pendingApproval: state.view?.pendingApproval ?? null,
          goal: state.view?.goal ?? null,
          goalCapability: state.view?.goalCapability ?? "unknown",
        },
      }),
    );

    if (deps.lifecycle.isDraining()) {
      socket.send(JSON.stringify(buildBridgeLifecycleEvent(state.session.id, deps.lifecycle.buildLifecycleState())));
    }

    const trackedSocket = socket as Parameters<typeof deps.lifecycle.attachSessionSocket>[1];
    deps.lifecycle.attachSessionSocket(state.session.id, trackedSocket);
    const unsubscribe = deps.runner.subscribe(state.session.id, (event) => {
      socket.send(JSON.stringify(event));
    });

    socket.on("close", () => {
      unsubscribe();
      deps.lifecycle.detachSessionSocket(state.session.id, trackedSocket);
    });
  });
}

function buildBridgeRestartingError(action: string) {
  return {
    error: "bridge-restarting",
    action,
    message: "bridge is restarting and temporarily not accepting new mutating requests",
  };
}

function sendServiceError(reply: FastifyReply, error: unknown) {
  if (isBridgeServiceError(error)) {
    return reply.status(error.statusCode).send(error.payload);
  }

  throw error;
}
