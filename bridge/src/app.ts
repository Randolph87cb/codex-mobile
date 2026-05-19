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
import type { BridgeSecurityConfig } from "./types.js";

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
});

const imageFileQuerySchema = z.object({
  path: z.string().trim().min(1),
});

const approveSchema = z.object({
  requestId: z.union([z.string().min(1), z.number()]).optional(),
  decision: z.enum(["approve", "approve_for_session", "reject", "reject_and_interrupt"]).default("approve"),
});

interface BuildBridgeAppOptions {
  runner?: BridgeRunner;
  store?: SessionStore;
  security?: BridgeSecurityConfig;
}

export async function buildBridgeApp(options: BuildBridgeAppOptions = {}): Promise<FastifyInstance> {
  const app = Fastify({
    logger: true,
    bodyLimit: resolveBridgeBodyLimitBytes(),
  });
  const store = options.store ?? new SessionStore();
  const attachmentStore = new AttachmentStore();
  const runner = options.runner ?? createRunner(store);
  const historyRunner = isHistoryCapableRunner(runner) ? runner : null;
  const security = options.security ?? resolveBridgeSecurityConfig();
  const securityState = buildBridgeSecurityState(security);

  await app.register(websocket);
  await app.register(multipart, {
    limits: {
      fileSize: resolveBridgeBodyLimitBytes(),
      files: 1,
    },
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
    security: securityState,
  }));

  app.get("/api/sessions", async () => ({
    items: historyRunner ? await historyRunner.listSessionViews() : store.list(),
  }));

  app.post("/api/attachment/image", async (request, reply) => {
    try {
      const attachment = request.isMultipart()
        ? await parseMultipartImageUpload(request, attachmentStore)
        : await parseJsonImageUpload(request.body, attachmentStore);
      return reply.status(201).send({
        id: attachment.id,
        path: attachment.path,
        kind: attachment.kind,
        displayName: attachment.displayName,
        mimeType: attachment.mimeType,
        createdAt: attachment.createdAt,
      });
    } catch (error) {
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
    if (!canServeImagePath(candidatePath, security, attachmentStore)) {
      return reply.status(403).send({
        error: "image-path-not-allowed",
      });
    }

    return sendImageFile(reply, candidatePath, mimeTypeFromPath(candidatePath));
  });

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

  app.patch("/api/session/:id/config", async (request, reply) => {
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
          path: uploaded.path,
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

  app.post("/api/session/:id/interrupt", async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const session = await resolveSessionRecord(params.id, store, historyRunner);
    if (!session) {
      return reply.status(404).send({ error: "session-not-found" });
    }

    await runner.interrupt(session.id);
    return { ok: true };
  });

  app.post("/api/session/:id/approve", async (request, reply) => {
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

async function parseMultipartImageUpload(
  request: FastifyRequest,
  attachmentStore: AttachmentStore,
) {
  let fileBuffer: Buffer | null = null;
  let displayName = "";
  let mimeType = "";

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

  return attachmentStore.createImage({
    displayName,
    mimeType,
    content: fileBuffer,
  });
}

async function parseJsonImageUpload(
  body: unknown,
  attachmentStore: AttachmentStore,
) {
  const parsed = uploadImageSchema.safeParse(body);
  if (!parsed.success) {
    throw new Error("invalid-request");
  }

  return attachmentStore.createImageFromBase64(parsed.data);
}

function resolveBridgeBodyLimitBytes(): number {
  const configuredMb = Number.parseInt(process.env.BRIDGE_BODY_LIMIT_MB ?? "", 10);
  const limitMb = Number.isFinite(configuredMb) && configuredMb > 0 ? configuredMb : 32;
  return limitMb * 1024 * 1024;
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

function canServeImagePath(
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
      return "image/jpeg";
  }
}

function normalizePathForComparison(value: string): string {
  const resolved = path.normalize(path.resolve(value));
  const root = path.parse(resolved).root;
  const trimmed = resolved === root ? resolved : resolved.replace(/[\\/]+$/, "");
  return process.platform === "win32" ? trimmed.toLowerCase() : trimmed;
}

function isSameOrChildPath(candidate: string, allowedRoot: string): boolean {
  if (candidate === allowedRoot) {
    return true;
  }

  const relative = path.relative(allowedRoot, candidate);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
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

function omitUndefinedFields<T extends Record<string, unknown>>(value: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(value).filter(([, entry]) => entry !== undefined),
  ) as Partial<T>;
}

function createRunner(store: SessionStore): BridgeRunner {
  const mode = process.env.CODEX_MOBILE_RUNNER ?? "mock";
  return mode === "app-server" ? new AppServerRunner(store) : new MockRunner(store);
}
