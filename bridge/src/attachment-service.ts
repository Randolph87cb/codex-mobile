import { createReadStream } from "node:fs";
import { access } from "node:fs/promises";
import path from "node:path";
import type { FastifyReply, FastifyRequest } from "fastify";
import { z } from "zod";
import { AttachmentStore } from "./attachment-store.js";
import { BridgeServiceError } from "./app-context.js";
import type {
  BridgeSecurityConfig,
  ResolvedSessionInputAttachment,
  SessionInputAttachmentRef,
  SessionRecord,
} from "./types.js";
import { validateSessionCwd } from "./security.js";

const uploadImageSchema = z.object({
  displayName: z.string().trim().min(1),
  mimeType: z.string().trim().min(1),
  contentBase64: z.string().trim().min(1),
  sessionId: z.string().trim().min(1).optional(),
});

const uploadVideoSchema = z.object({
  displayName: z.string().trim().min(1),
  mimeType: z.string().trim().min(1),
  contentBase64: z.string().trim().min(1),
  sessionId: z.string().trim().min(1).optional(),
});

type SessionResolver = (sessionId: string) => Promise<SessionRecord | null>;

interface CreateAttachmentServiceOptions {
  attachmentStore: AttachmentStore;
  security: BridgeSecurityConfig;
  resolveSessionRecord: SessionResolver;
}

export interface BridgeAttachmentService {
  uploadImage(request: FastifyRequest): Promise<AttachmentResponse>;
  uploadVideo(request: FastifyRequest): Promise<AttachmentResponse>;
  resolveSessionInputAttachments(refs: SessionInputAttachmentRef[]): ResolvedSessionInputAttachment[];
  readImageContent(id: string): Promise<{ filePath: string; mimeType: string } | null>;
  resolveDownloadableFile(
    rawPath: string,
    deniedErrorCode?: "image-path-not-allowed" | "file-path-not-allowed",
  ): Promise<{ filePath: string; displayName: string; mimeType: string }>;
}

interface AttachmentResponse {
  id: string;
  path: string;
  kind: "image" | "video";
  displayName: string;
  mimeType: string;
  savedPath?: string;
  savedRelativePath?: string;
  createdAt: string;
}

export function createAttachmentService(options: CreateAttachmentServiceOptions): BridgeAttachmentService {
  return {
    uploadImage,
    uploadVideo,
    resolveSessionInputAttachments,
    readImageContent,
    resolveDownloadableFile,
  };

  async function uploadImage(request: FastifyRequest): Promise<AttachmentResponse> {
    const upload = request.isMultipart()
      ? await parseMultipartImageUpload(request)
      : await parseJsonImageUpload(request.body);
    return createUploadedAttachment("image", upload);
  }

  async function uploadVideo(request: FastifyRequest): Promise<AttachmentResponse> {
    const upload = request.isMultipart()
      ? await parseMultipartVideoUpload(request)
      : await parseJsonVideoUpload(request.body);
    return createUploadedAttachment("video", upload);
  }

  function resolveSessionInputAttachments(refs: SessionInputAttachmentRef[]): ResolvedSessionInputAttachment[] {
    return refs.map((attachment) => {
      const uploaded = attachment.path
        ? options.attachmentStore.getAttachmentByPath(path.resolve(attachment.path))
        : attachment.id
          ? options.attachmentStore.getAttachment(attachment.id)
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
        savedPath: uploaded.savedPath,
      };
    });
  }

  async function readImageContent(id: string): Promise<{ filePath: string; mimeType: string } | null> {
    const attachment = options.attachmentStore.getImage(id);
    if (!attachment) {
      return null;
    }

    return {
      filePath: attachment.path,
      mimeType: attachment.mimeType,
    };
  }

  async function resolveDownloadableFile(
    rawPath: string,
    deniedErrorCode: "image-path-not-allowed" | "file-path-not-allowed" = "file-path-not-allowed",
  ): Promise<{ filePath: string; displayName: string; mimeType: string }> {
    const candidatePath = path.resolve(rawPath);
    if (!canServeLocalPath(candidatePath, options.security, options.attachmentStore)) {
      throw new BridgeServiceError(403, {
        error: deniedErrorCode,
      });
    }

    return {
      filePath: candidatePath,
      displayName: path.basename(candidatePath),
      mimeType: mimeTypeFromPath(candidatePath),
    };
  }

  async function createUploadedAttachment(
    kind: "image" | "video",
    upload: ImageUploadInput | VideoUploadInput,
  ): Promise<AttachmentResponse> {
    const session = upload.sessionId
      ? await options.resolveSessionRecord(upload.sessionId)
      : null;
    if (upload.sessionId && !session) {
      throw new BridgeServiceError(404, { error: "session-not-found" });
    }

    let attachment = kind === "image"
      ? upload.kind === "multipart"
        ? await options.attachmentStore.createImage({
          displayName: upload.displayName,
          mimeType: upload.mimeType,
          content: upload.content,
        })
        : await options.attachmentStore.createImageFromBase64({
          displayName: upload.displayName,
          mimeType: upload.mimeType,
          contentBase64: upload.contentBase64,
        })
      : upload.kind === "multipart"
        ? await options.attachmentStore.createVideo({
          displayName: upload.displayName,
          mimeType: upload.mimeType,
          content: upload.content,
        })
        : await options.attachmentStore.createVideoFromBase64({
          displayName: upload.displayName,
          mimeType: upload.mimeType,
          contentBase64: upload.contentBase64,
        });

    if (session) {
      const saveRoot = resolveSessionAttachmentSaveRoot(session.cwd, options.security);
      if (kind === "image" && attachment.kind === "image") {
        attachment = await options.attachmentStore.saveImageToDirectory(
          attachment,
          saveRoot.targetDir,
          saveRoot.cwd,
        );
      } else if (kind === "video" && attachment.kind === "video") {
        attachment = await options.attachmentStore.saveVideoToDirectory(
          attachment,
          saveRoot.targetDir,
          saveRoot.cwd,
        );
      }
    }

    return {
      id: attachment.id,
      path: attachment.path,
      kind: attachment.kind,
      displayName: attachment.displayName,
      mimeType: attachment.mimeType,
      savedPath: attachment.savedPath,
      savedRelativePath: attachment.savedRelativePath,
      createdAt: attachment.createdAt,
    };
  }
}

type ImageUploadInput =
  | {
    kind: "multipart";
    displayName: string;
    mimeType: string;
    content: Buffer;
    sessionId?: string;
  }
  | {
    kind: "json";
    displayName: string;
    mimeType: string;
    contentBase64: string;
    sessionId?: string;
  };

type VideoUploadInput = ImageUploadInput;

export function buildAttachmentTooLargeError(limitBytes: number, kind: "image" | "video") {
  const maxMegabytes = Math.max(1, Math.round(limitBytes / (1024 * 1024)));
  return {
    error: kind === "video" ? "video-too-large" : "image-too-large",
    maxBytes: limitBytes,
    maxMegabytes,
    message: kind === "video"
      ? `视频过大，当前上限 ${maxMegabytes} MB。`
      : `图片过大，当前上限 ${maxMegabytes} MB。`,
  };
}

export function isAttachmentUploadTooLargeError(error: unknown): boolean {
  const code = error && typeof error === "object" && "code" in error
    ? String((error as { code?: unknown }).code ?? "")
    : "";
  const message = error instanceof Error ? error.message : String(error ?? "");
  return code === "FST_ERR_CTP_BODY_TOO_LARGE"
    || code === "FST_REQ_FILE_TOO_LARGE"
    || message === "request file too large";
}

export async function sendImageFile(
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

export async function sendDownloadFile(
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

async function parseMultipartImageUpload(request: FastifyRequest): Promise<ImageUploadInput> {
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

  return {
    kind: "multipart",
    displayName: displayName || "image",
    mimeType: mimeType || "image/png",
    content: fileBuffer,
    sessionId,
  };
}

async function parseJsonImageUpload(body: unknown): Promise<ImageUploadInput> {
  const parsed = uploadImageSchema.safeParse(body);
  if (!parsed.success) {
    throw new Error("invalid-request");
  }

  return {
    kind: "json",
    ...parsed.data,
  };
}

async function parseMultipartVideoUpload(request: FastifyRequest): Promise<VideoUploadInput> {
  let fileBuffer: Buffer | null = null;
  let displayName = "";
  let mimeType = "";
  let sessionId: string | undefined;

  for await (const part of request.parts()) {
    if (part.type === "file") {
      if (fileBuffer) {
        throw new Error("multiple-video-files-not-supported");
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
    throw new Error("invalid-video-content");
  }

  return {
    kind: "multipart",
    displayName: displayName || "video",
    mimeType: mimeType || "video/mp4",
    content: fileBuffer,
    sessionId,
  };
}

async function parseJsonVideoUpload(body: unknown): Promise<VideoUploadInput> {
  const parsed = uploadVideoSchema.safeParse(body);
  if (!parsed.success) {
    throw new Error("invalid-request");
  }

  return {
    kind: "json",
    ...parsed.data,
  };
}

function resolveSessionAttachmentSaveRoot(cwd: string, security: BridgeSecurityConfig): { cwd: string; targetDir: string } {
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
    case ".mp4":
      return "video/mp4";
    case ".webm":
      return "video/webm";
    case ".mov":
      return "video/quicktime";
    default:
      return "application/octet-stream";
  }
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
  const parsed = path.parse(sanitized);
  const asciiBaseName = parsed.name
    .normalize("NFKD")
    .replace(/[^\x20-\x7e]/g, "")
    .replace(/[\\/:*?<>|;,=]/g, "_")
    .trim();
  const asciiExtension = parsed.ext
    .normalize("NFKD")
    .replace(/[^\x20-\x7e]/g, "")
    .replace(/[^.A-Za-z0-9]/g, "");
  const fallbackBaseName = /[A-Za-z0-9]/.test(asciiBaseName) ? asciiBaseName : "download";
  return `${fallbackBaseName}${asciiExtension}`;
}

function isSameOrChildPath(candidate: string, allowedRoot: string): boolean {
  if (candidate === allowedRoot) {
    return true;
  }

  const relative = path.relative(allowedRoot, candidate);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}
