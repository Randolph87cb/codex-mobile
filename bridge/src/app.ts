import Fastify, { type FastifyInstance, type FastifyReply, type FastifyRequest } from "fastify";
import multipart from "@fastify/multipart";
import websocket from "@fastify/websocket";
import { createReadStream } from "node:fs";
import { access } from "node:fs/promises";
import path from "node:path";
import { z } from "zod";
import { AttachmentStore } from "./attachment-store.js";
import { isHistoryCapableRunner, type BridgeRunner, type HistoryCapableBridgeRunner } from "./bridge-runner.js";
import { AppServerRunner } from "./app-server-runner.js";
import { MockRunner } from "./mock-runner.js";
import { authorizeApiRequest, buildBridgeSecurityState, resolveBridgeSecurityConfig, validateSessionCwd } from "./security.js";
import { SessionStore } from "./session-store.js";
import type { BridgeLifecycleState, BridgeSecurityConfig } from "./types.js";

const createSessionSchema = z.object({
  cwd: z.string().trim().min(1),
  model: z.string().trim().min(1).default("gpt-5.5"),
  approvalMode: z.enum(["manual", "auto"]).default("manual"),
  reasoningEffort: z.enum(["minimal", "low", "medium", "high", "xhigh"]).default("medium"),
  serviceTier: z.enum(["default", "fast"]).default("default"),
  sandboxMode: z.enum(["read-only", "workspace-write", "danger-full-access"]).default("workspace-write"),
});

const updateSessionConfigSchema = z.object({
  cwd: z.string().trim().min(1).optional(),
  model: z.string().trim().min(1).optional(),
  approvalMode: z.enum(["manual", "auto"]).optional(),
  reasoningEffort: z.enum(["minimal", "low", "medium", "high", "xhigh"]).optional(),
  serviceTier: z.enum(["default", "fast"]).optional(),
  sandboxMode: z.enum(["read-only", "workspace-write", "danger-full-access"]).optional(),
});

const updateSessionGoalSchema = z.object({
  objective: z.string().trim().min(1).max(2_000).optional(),
  status: z.string().trim().min(1).max(64).optional(),
  tokenBudget: z.number().int().min(1).nullable().optional(),
}).superRefine((value, context) => {
  if (value.objective !== undefined || value.status !== undefined || value.tokenBudget !== undefined) {
    return;
  }

  context.addIssue({
    code: z.ZodIssueCode.custom,
    message: "objective, status, or tokenBudget is required",
    path: ["objective"],
  });
});

const inputSchema = z.object({
  text: z.string().optional(),
  attachments: z.array(
    z.object({
      id: z.string().trim().min(1).optional(),
      path: z.string().trim().min(1).optional(),
    }).superRefine((value, context) => {
      if (value.id || value.path) {
        return;
      }

      context.addIssue({
        code: z.ZodIssueCode.custom,
        message: "attachment id or path is required",
        path: ["id"],
      });
    }),
  ).optional(),
}).superRefine((value, context) => {
  const hasText = value.text?.trim().length ? value.text.trim().length > 0 : false;
  const hasAttachments = (value.attachments?.length ?? 0) > 0;
  if (hasText || hasAttachments) {
    return;
  }

  context.addIssue({
    code: z.ZodIssueCode.custom,
    message: "either text or attachments is required",
    path: ["text"],
  });
});

const uploadImageSchema = z.object({
  displayName: z.string().trim().min(1),
  mimeType: z.string().trim().min(1),
  contentBase64: z.string().trim().min(1),
  sessionId: z.string().trim().min(1).optional(),
});

const imageFileQuerySchema = z.object({
  path: z.string().trim().min(1),
});

const fileDownloadQuerySchema = z.object({
  path: z.string().trim().min(1),
});

const listSessionsQuerySchema = z.object({
  archived: z.enum(["true", "false"]).optional(),
});

const approveSchema = z.object({
  requestId: z.union([z.string().min(1), z.number()]).optional(),
  decision: z.enum(["approve", "approve_for_session", "reject", "reject_and_interrupt"]).default("approve"),
});

const drainBridgeSchema = z.object({
  reason: z.string().trim().min(1).max(200).optional(),
  graceMs: z.number().int().min(0).max(15_000).optional(),
});

interface BuildBridgeAppOptions {
  runner?: BridgeRunner;
  store?: SessionStore;
  security?: BridgeSecurityConfig;
}

interface SessionSocket {
  send(payload: string): void;
  close(code?: number, data?: string): void;
  on(event: "close", listener: () => void): void;
}

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
  const securityState = buildBridgeSecurityState(security);
  const bridgeVersion = process.env.CODEX_MOBILE_BRIDGE_VERSION ?? "0.1.0";
  const bridgeStartedAt = new Date().toISOString();
  const sessionSockets = new Map<string, Set<SessionSocket>>();
  let drainStartedAt: string | null = null;
  let drainReason: string | null = null;
  let drainGraceMs: number | null = null;

  await app.register(websocket);
  await app.register(multipart, {
    limits: {
      fileSize: uploadBodyLimitBytes,
      files: 1,
    },
  });

  app.setErrorHandler((error, request, reply) => {
    const pathname = new URL(request.raw.url ?? "/", "http://bridge.local").pathname;
    if ((error as { code?: string }).code === "FST_ERR_CTP_BODY_TOO_LARGE" && pathname === "/api/attachment/image") {
      return reply.status(413).send(buildImageTooLargeError(uploadBodyLimitBytes));
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

  app.get("/health", async () => ({
    ok: true,
    service: "codex-mobile-bridge",
    runnerMode: runner.mode,
    bridgeVersion,
    startedAt: bridgeStartedAt,
    lifecycle: buildLifecycleState(),
    security: securityState,
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

    drainStartedAt = new Date().toISOString();
    drainReason = body.data.reason ?? "bridge restart requested";
    drainGraceMs = normalizeDrainGraceMs(body.data.graceMs);
    broadcastBridgeLifecycle("restarting", drainReason, drainGraceMs);

    return {
      ok: true,
      lifecycle: buildLifecycleState(),
    };
  });

  app.get("/api/sessions", async (request, reply) => {
    const query = listSessionsQuerySchema.safeParse(request.query ?? {});
    if (!query.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: query.error.flatten(),
      });
    }

    const archived = query.data.archived === "true";
    return {
      items: historyRunner ? await historyRunner.listSessionViews(archived) : store.list(),
    };
  });

  app.get("/api/account/quota", async (_request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("account-quota"));
    }
    if (!historyRunner) {
      return reply.status(501).send({ error: "quota-not-supported" });
    }

    try {
      return await historyRunner.getAccountQuota();
    } catch (error) {
      return sendAccountQuotaError(reply, error);
    }
  });

  app.post("/api/attachment/image", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("attachment-upload"));
    }
    try {
      const upload = request.isMultipart()
        ? await parseMultipartImageUpload(request, attachmentStore)
        : await parseJsonImageUpload(request.body, attachmentStore);
      const session = upload.sessionId
        ? await resolveSessionRecord(upload.sessionId, store, historyRunner)
        : undefined;
      if (upload.sessionId && !session) {
        return reply.status(404).send({
          error: "session-not-found",
        });
      }

      let attachment = upload.kind === "multipart"
        ? await attachmentStore.createImage({
          displayName: upload.displayName,
          mimeType: upload.mimeType,
          content: upload.content,
        })
        : await attachmentStore.createImageFromBase64({
          displayName: upload.displayName,
          mimeType: upload.mimeType,
          contentBase64: upload.contentBase64,
        });

      if (session) {
        const saveRoot = resolveSessionImageSaveRoot(session.cwd, security);
        attachment = await attachmentStore.saveImageToDirectory(attachment, saveRoot.targetDir, saveRoot.cwd);
      }

      return reply.status(201).send({
        id: attachment.id,
        path: attachment.path,
        kind: attachment.kind,
        displayName: attachment.displayName,
        mimeType: attachment.mimeType,
        savedPath: attachment.savedPath,
        savedRelativePath: attachment.savedRelativePath,
        createdAt: attachment.createdAt,
      });
    } catch (error) {
      if (isImageUploadTooLargeError(error)) {
        return reply.status(413).send(buildImageTooLargeError(uploadBodyLimitBytes));
      }
      return reply.status(400).send({
        error: "invalid-image-upload",
        message: error instanceof Error ? error.message : String(error),
      });
    }
  });

  app.get("/api/attachment/image/:id/content", async (request, reply) => {
    const params = z.object({ id: z.string().trim().min(1) }).parse(request.params);
    const attachment = attachmentStore.getImage(params.id);
    if (!attachment) {
      return reply.status(404).send({ error: "attachment-not-found" });
    }

    return sendImageFile(reply, attachment.path, attachment.mimeType);
  });

  app.get("/api/image/file", async (request, reply) => {
    const query = imageFileQuerySchema.safeParse(request.query);
    if (!query.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: query.error.flatten(),
      });
    }

    const candidatePath = path.resolve(query.data.path);
    if (!canServeLocalPath(candidatePath, security, attachmentStore)) {
      return reply.status(403).send({
        error: "image-path-not-allowed",
      });
    }

    return sendImageFile(reply, candidatePath, mimeTypeFromPath(candidatePath));
  });

  app.get("/api/file/download", async (request, reply) => {
    const query = fileDownloadQuerySchema.safeParse(request.query);
    if (!query.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: query.error.flatten(),
      });
    }

    const candidatePath = path.resolve(query.data.path);
    if (!canServeLocalPath(candidatePath, security, attachmentStore)) {
      return reply.status(403).send({
        error: "file-path-not-allowed",
      });
    }

    return sendDownloadFile(
      reply,
      candidatePath,
      path.basename(candidatePath),
      mimeTypeFromPath(candidatePath),
    );
  });

  app.post("/api/session", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-create"));
    }
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

  app.patch("/api/session/:id/config", async (request, reply) => {
    if (isDraining()) {
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

    const session = store.get(params.id) ?? await historyRunner?.attachSession(params.id) ?? null;
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    let cwd = body.data.cwd;
    if (cwd !== undefined) {
      const validatedCwd = validateSessionCwd(cwd, security);
      if (!validatedCwd.ok) {
        return reply.status(validatedCwd.error === "invalid-cwd" ? 400 : 403).send({
          error: validatedCwd.error,
          message: validatedCwd.message,
        });
      }
      cwd = validatedCwd.cwd ?? cwd;
    }

    const updated = store.update(
      session.id,
      omitUndefinedFields({
        cwd,
        model: body.data.model,
        approvalMode: body.data.approvalMode,
        reasoningEffort: body.data.reasoningEffort,
        serviceTier: body.data.serviceTier,
        sandboxMode: body.data.sandboxMode,
      }),
    );
    return updated ?? session;
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

  app.get("/api/session/:id/goal", async (request, reply) => {
    if (!historyRunner) {
      return reply.status(501).send({ error: "goal-not-supported" });
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = await resolveSessionRecord(params.id, store, historyRunner);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    try {
      const result = await historyRunner.getSessionGoal(session.id);
      return result;
    } catch (error) {
      return sendGoalError(reply, error);
    }
  });

  app.put("/api/session/:id/goal", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-goal-update"));
    }
    if (!historyRunner) {
      return reply.status(501).send({ error: "goal-not-supported" });
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = updateSessionGoalSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    const session = await resolveSessionRecord(params.id, store, historyRunner);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    try {
      return await historyRunner.updateSessionGoal(session.id, body.data);
    } catch (error) {
      return sendGoalError(reply, error);
    }
  });

  app.delete("/api/session/:id/goal", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-goal-clear"));
    }
    if (!historyRunner) {
      return reply.status(501).send({ error: "goal-not-supported" });
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = await resolveSessionRecord(params.id, store, historyRunner);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    try {
      const result = await historyRunner.clearSessionGoal(session.id);
      return {
        ok: true,
        ...result,
      };
    } catch (error) {
      return sendGoalError(reply, error);
    }
  });

  app.post("/api/session/:id/input", async (request, reply) => {
    if (isDraining()) {
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

    let session = store.get(params.id) ?? undefined;
    if (!session && historyRunner) {
      session = (await historyRunner.attachSession(params.id)) ?? undefined;
    }
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    try {
      const resolvedAttachments = (body.data.attachments ?? []).map((attachment) => {
        const uploaded = attachment.path
          ? attachmentStore.getImageByPath(path.resolve(attachment.path))
          : attachment.id
            ? attachmentStore.getImage(attachment.id)
            : undefined;
        if (!uploaded) {
          throw new Error(`attachment-not-found:${attachment.path ?? attachment.id ?? "unknown"}`);
        }

        return {
          id: uploaded.id,
          kind: uploaded.kind,
          path: uploaded.savedPath ?? uploaded.path,
          displayName: uploaded.displayName,
          mimeType: uploaded.mimeType,
        } as const;
      });

      await runner.submitInput(session.id, {
        text: body.data.text?.trim() ?? "",
        attachments: resolvedAttachments,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message.startsWith("attachment-not-found:")) {
        return reply.status(400).send({
          error: "attachment-not-found",
          message,
        });
      }
      if (message === "thread-busy") {
        return reply.status(409).send({
          error: "thread-busy",
          message: "thread is already running in another client",
        });
      }

      return reply.status(502).send({
        error: "turn-start-failed",
        message,
      });
    }

    return reply.status(202).send({ accepted: true });
  });

  app.post("/api/session/:id/archive", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-archive"));
    }
    if (!historyRunner) {
      return reply.status(501).send({ error: "archive-not-supported" });
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    try {
      await historyRunner.archiveSession(params.id);
      return { ok: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message === "session-not-archivable") {
        return reply.status(409).send({ error: message });
      }

      return reply.status(502).send({
        error: "session-archive-failed",
        message,
      });
    }
  });

  app.post("/api/session/:id/unarchive", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-unarchive"));
    }
    if (!historyRunner) {
      return reply.status(501).send({ error: "archive-not-supported" });
    }

    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    try {
      await historyRunner.unarchiveSession(params.id);
      return { ok: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message === "session-not-archivable") {
        return reply.status(409).send({ error: message });
      }

      return reply.status(502).send({
        error: "session-unarchive-failed",
        message,
      });
    }
  });

  app.post("/api/session/:id/interrupt", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-interrupt"));
    }
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = await resolveSessionRecord(params.id, store, historyRunner);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    await runner.interrupt(session.id);
    return { ok: true };
  });

  app.post("/api/session/:id/approve", async (request, reply) => {
    if (isDraining()) {
      return reply.status(503).send(buildBridgeRestartingError("session-approve"));
    }
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = await resolveSessionRecord(params.id, store, historyRunner);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    const body = approveSchema.safeParse(request.body ?? {});
    if (!body.success) {
      return reply.status(400).send({
        error: "invalid-request",
        issues: body.error.flatten(),
      });
    }

    try {
      const result = await runner.approve(session.id, body.data);
      return {
        ok: true,
        requestId: result.requestId,
        decision: result.decision,
        method: result.method,
        status: result.status,
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      if (message === "approval-request-id-required") {
        return reply.status(409).send({ error: message });
      }
      if (message === "approval-not-found") {
        return reply.status(409).send({ error: message });
      }
      if (message === "session-not-found") {
        return reply.status(404).send({ error: message });
      }

      return reply.status(502).send({
        error: "approval-submit-failed",
        message,
      });
    }
  });

  app.get("/api/session/:id/ws", { websocket: true }, async (socket, request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = await resolveSessionRecord(params.id, store, historyRunner);
    if (!session) {
      socket.send(JSON.stringify({ error: "session-not-found" }));
      socket.close();
      return;
    }

    const view = historyRunner ? await historyRunner.getSessionView(session.id) : null;
    socket.send(
      JSON.stringify({
        type: "session.started",
        sessionId: session.id,
        timestamp: new Date().toISOString(),
        data: {
          cwd: view?.cwd ?? session.cwd,
          model: view?.model ?? session.model,
          approvalMode: view?.approvalMode ?? session.approvalMode,
          reasoningEffort: view?.reasoningEffort ?? session.reasoningEffort,
        serviceTier: view?.serviceTier ?? session.serviceTier,
        sandboxMode: view?.sandboxMode ?? session.sandboxMode,
        status: view?.status ?? session.status,
        threadId: view?.threadId ?? session.threadId,
        pendingApproval: view?.pendingApproval ?? null,
        goal: view?.goal ?? null,
        goalCapability: view?.goalCapability ?? "unknown",
      },
    }),
    );
    if (isDraining()) {
      socket.send(JSON.stringify(buildBridgeLifecycleEvent(session.id, "restarting", drainReason, drainGraceMs)));
    }

    const trackedSocket = socket as unknown as SessionSocket;
    const socketSet = sessionSockets.get(session.id) ?? new Set<SessionSocket>();
    socketSet.add(trackedSocket);
    sessionSockets.set(session.id, socketSet);

    const unsubscribe = runner.subscribe(session.id, (event) => {
      socket.send(JSON.stringify(event));
    });

    socket.on("close", () => {
      unsubscribe();
      const currentSet = sessionSockets.get(session.id);
      currentSet?.delete(trackedSocket);
      if (currentSet && currentSet.size === 0) {
        sessionSockets.delete(session.id);
      }
    });
  });

  return app;

  function isDraining(): boolean {
    return drainStartedAt != null;
  }

  function buildLifecycleState(): BridgeLifecycleState {
    return {
      phase: isDraining() ? "restarting" : "running",
      draining: isDraining(),
      reason: drainReason,
      startedAt: bridgeStartedAt,
      drainStartedAt,
      drainGraceMs,
      bridgeVersion,
    };
  }

  function broadcastBridgeLifecycle(
    phase: BridgeLifecycleState["phase"],
    reason: string | null,
    graceMs: number | null,
  ): void {
    for (const [sessionId, sockets] of sessionSockets.entries()) {
      const payload = JSON.stringify(buildBridgeLifecycleEvent(sessionId, phase, reason, graceMs));
      for (const socket of sockets) {
        try {
          socket.send(payload);
        } catch {
          socket.close();
        }
      }
    }
  }

  function buildBridgeLifecycleEvent(
    sessionId: string,
    phase: BridgeLifecycleState["phase"],
    reason: string | null,
    graceMs: number | null,
  ) {
    return {
      type: "bridge.lifecycle",
      sessionId,
      timestamp: new Date().toISOString(),
      data: {
        phase,
        reason,
        graceMs,
        bridgeVersion,
        bridgeStartedAt,
      },
    };
  }
}

async function parseMultipartImageUpload(
  request: FastifyRequest,
  attachmentStore: AttachmentStore,
) {
  void attachmentStore;
  let fileBuffer: Buffer | null = null;
  let displayName = "";
  let mimeType = "";
  let sessionId: string | undefined;

  for await (const part of request.parts()) {
    if (part.type === "file") {
      if (fileBuffer) {
        throw new Error("multiple-image-files-not-supported");
      }
      fileBuffer = await part.toBuffer();
      if (!displayName) {
        displayName = part.filename;
      }
      if (!mimeType) {
        mimeType = part.mimetype;
      }
      continue;
    }

    if (part.fieldname === "displayName") {
      displayName = String(part.value ?? "").trim();
    } else if (part.fieldname === "mimeType") {
      mimeType = String(part.value ?? "").trim();
    } else if (part.fieldname === "sessionId") {
      sessionId = String(part.value ?? "").trim() || undefined;
    }
  }

  if (!fileBuffer || fileBuffer.length === 0) {
    throw new Error("invalid-image-content");
  }

  if (!displayName) {
    displayName = "image";
  }

  if (!mimeType) {
    mimeType = "image/png";
  }

  return {
    kind: "multipart" as const,
    displayName,
    mimeType,
    content: fileBuffer,
    sessionId,
  };
}

async function parseJsonImageUpload(
  body: unknown,
  attachmentStore: AttachmentStore,
) {
  void attachmentStore;
  const parsed = uploadImageSchema.safeParse(body);
  if (!parsed.success) {
    throw new Error("invalid-request");
  }

  return {
    kind: "json" as const,
    ...parsed.data,
  };
}

function resolveBridgeBodyLimitBytes(): number {
  const configuredMb = Number.parseInt(process.env.BRIDGE_BODY_LIMIT_MB ?? "", 10);
  const limitMb = Number.isFinite(configuredMb) && configuredMb > 0 ? configuredMb : 64;
  return limitMb * 1024 * 1024;
}

function buildBridgeRestartingError(action: string) {
  return {
    error: "bridge-restarting",
    action,
    message: "bridge is restarting and temporarily not accepting new mutating requests",
  };
}

function buildImageTooLargeError(limitBytes: number) {
  const maxMegabytes = Math.max(1, Math.round(limitBytes / (1024 * 1024)));
  return {
    error: "image-too-large",
    maxBytes: limitBytes,
    maxMegabytes,
    message: `图片过大，当前上限 ${maxMegabytes} MB。`,
  };
}

async function sendImageFile(
  reply: FastifyReply,
  filePath: string,
  mimeType: string,
) {
  try {
    await access(filePath);
  } catch {
    return reply.status(404).send({ error: "image-not-found" });
  }

  reply.header("Content-Type", mimeType);
  reply.header("Cache-Control", "no-store");
  return reply.send(createReadStream(filePath));
}

function canServeLocalPath(
  candidatePath: string,
  security: BridgeSecurityConfig,
  attachmentStore: AttachmentStore,
): boolean {
  if (attachmentStore.containsPath(candidatePath)) {
    return true;
  }

  if (security.allowedCwds.length === 0) {
    return path.isAbsolute(candidatePath);
  }

  const normalizedCandidate = normalizePathForComparison(candidatePath);
  return security.allowedCwds.some((allowedCwd) => isSameOrChildPath(normalizedCandidate, allowedCwd));
}

function mimeTypeFromPath(filePath: string): string {
  switch (path.extname(filePath).toLowerCase()) {
    case ".txt":
      return "text/plain; charset=utf-8";
    case ".md":
      return "text/markdown; charset=utf-8";
    case ".json":
      return "application/json; charset=utf-8";
    case ".log":
      return "text/plain; charset=utf-8";
    case ".csv":
      return "text/csv; charset=utf-8";
    case ".tsv":
      return "text/tab-separated-values; charset=utf-8";
    case ".pdf":
      return "application/pdf";
    case ".zip":
      return "application/zip";
    case ".tar":
      return "application/x-tar";
    case ".gz":
      return "application/gzip";
    case ".png":
      return "image/png";
    case ".jpeg":
    case ".jpg":
      return "image/jpeg";
    case ".webp":
      return "image/webp";
    case ".gif":
      return "image/gif";
    case ".bmp":
      return "image/bmp";
    default:
      return "application/octet-stream";
  }
}

async function sendDownloadFile(
  reply: FastifyReply,
  filePath: string,
  displayName: string,
  mimeType: string,
) {
  try {
    await access(filePath);
  } catch {
    return reply.status(404).send({ error: "file-not-found" });
  }

  const safeDisplayName = sanitizeDispositionFileName(displayName);
  reply.header("Content-Type", mimeType);
  reply.header("Cache-Control", "no-store");
  reply.header(
    "Content-Disposition",
    `attachment; filename="${safeDisplayName}"; filename*=UTF-8''${encodeURIComponent(displayName)}`,
  );
  return reply.send(createReadStream(filePath));
}

function normalizePathForComparison(value: string): string {
  const resolved = path.normalize(path.resolve(value));
  const root = path.parse(resolved).root;
  const trimmed = resolved === root ? resolved : resolved.replace(/[\\/]+$/, "");
  return process.platform === "win32" ? trimmed.toLowerCase() : trimmed;
}

function sanitizeDispositionFileName(value: string): string {
  const sanitized = value
    .replace(/[\r\n"]/g, "_")
    .trim();
  return sanitized || "download";
}

function normalizeDrainGraceMs(value: number | undefined): number {
  if (!Number.isFinite(value)) {
    return 2_000;
  }

  const normalized = value ?? 2_000;
  return Math.max(0, Math.min(15_000, normalized));
}

function isLoopbackRequest(request: FastifyRequest): boolean {
  const candidates = [
    request.ip,
    request.socket.remoteAddress,
  ];
  return candidates.some((candidate) => isLoopbackAddress(candidate));
}

function isLoopbackAddress(value: string | undefined): boolean {
  if (!value) {
    return false;
  }

  const normalized = value.trim().toLowerCase().replace(/^::ffff:/, "");
  return normalized === "127.0.0.1" || normalized === "::1" || normalized === "localhost";
}

function isSameOrChildPath(candidate: string, allowedRoot: string): boolean {
  if (candidate === allowedRoot) {
    return true;
  }

  const relative = path.relative(allowedRoot, candidate);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}

function resolveSessionImageSaveRoot(cwd: string, security: BridgeSecurityConfig): { cwd: string; targetDir: string } {
  const validated = validateSessionCwd(cwd, security);
  if (!validated.ok || !validated.cwd) {
    throw new Error(validated.message ?? validated.error ?? "invalid-session-cwd");
  }

  const resolvedCwd = path.resolve(validated.cwd);
  return {
    cwd: resolvedCwd,
    targetDir: path.join(resolvedCwd, "mobile_uploads"),
  };
}

async function resolveSessionRecord(
  sessionId: string,
  store: SessionStore,
  historyRunner: HistoryCapableBridgeRunner | null,
) {
  if (historyRunner) {
    const session = await historyRunner.attachSession(sessionId);
    if (session) {
      return session;
    }
  }

  return store.get(sessionId);
}

function sendGoalError(reply: FastifyReply, error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  if (message === "goal-not-supported") {
    return reply.status(501).send({ error: message });
  }
  if (message === "goal-session-unavailable") {
    return reply.status(409).send({ error: message });
  }

  return reply.status(502).send({
    error: "session-goal-failed",
    message,
  });
}

function sendAccountQuotaError(reply: FastifyReply, error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  if (/method not found|unsupported|not supported|unknown method/i.test(message)) {
    return reply.status(501).send({ error: "quota-not-supported" });
  }

  return reply.status(502).send({
    error: "account-quota-failed",
    message,
  });
}

function omitUndefinedFields<T extends Record<string, unknown>>(value: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(value).filter(([, entry]) => entry !== undefined),
  ) as Partial<T>;
}

function isImageUploadTooLargeError(error: unknown): boolean {
  const code = error && typeof error === "object" && "code" in error
    ? String((error as { code?: unknown }).code ?? "")
    : "";
  const message = error instanceof Error ? error.message : String(error ?? "");
  return code === "FST_ERR_CTP_BODY_TOO_LARGE"
    || code === "FST_REQ_FILE_TOO_LARGE"
    || message === "request file too large";
}

function createRunner(store: SessionStore): BridgeRunner {
  const mode = process.env.CODEX_MOBILE_RUNNER ?? "mock";
  return mode === "app-server" ? new AppServerRunner(store) : new MockRunner(store);
}
