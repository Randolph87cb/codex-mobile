import { randomUUID } from "node:crypto";
import { access, copyFile, mkdir, writeFile } from "node:fs/promises";
import { extname, join, normalize, parse, relative, resolve } from "node:path";
import { tmpdir } from "node:os";
import type { UploadedImageAttachment, UploadedVideoAttachment } from "./types.js";

type UploadedAttachment = UploadedImageAttachment | UploadedVideoAttachment;
type AttachmentKind = UploadedAttachment["kind"];

interface CreateAttachmentInput {
  displayName: string;
  mimeType: string;
  content: Buffer;
}

export class AttachmentStore {
  private readonly attachments = new Map<string, UploadedAttachment>();
  private readonly attachmentsByPath = new Map<string, UploadedAttachment>();

  constructor(
    private readonly rootDir = join(tmpdir(), "codex-mobile-bridge", "attachments"),
  ) {}

  async createImage(input: CreateAttachmentInput): Promise<UploadedImageAttachment> {
    return this.createAttachment("image", input) as Promise<UploadedImageAttachment>;
  }

  async createImageFromBase64(input: Omit<CreateAttachmentInput, "content"> & { contentBase64: string }): Promise<UploadedImageAttachment> {
    return this.createImage({
      displayName: input.displayName,
      mimeType: input.mimeType,
      content: decodeBase64(input.contentBase64, "image"),
    });
  }

  async createVideo(input: CreateAttachmentInput): Promise<UploadedVideoAttachment> {
    return this.createAttachment("video", input) as Promise<UploadedVideoAttachment>;
  }

  async createVideoFromBase64(input: Omit<CreateAttachmentInput, "content"> & { contentBase64: string }): Promise<UploadedVideoAttachment> {
    return this.createVideo({
      displayName: input.displayName,
      mimeType: input.mimeType,
      content: decodeBase64(input.contentBase64, "video"),
    });
  }

  getAttachment(id: string): UploadedAttachment | undefined {
    return this.attachments.get(id);
  }

  getAttachmentByPath(filePath: string): UploadedAttachment | undefined {
    return this.attachmentsByPath.get(normalizePathForComparison(filePath));
  }

  getImage(id: string): UploadedImageAttachment | undefined {
    const attachment = this.attachments.get(id);
    return attachment?.kind === "image" ? attachment : undefined;
  }

  getImageByPath(filePath: string): UploadedImageAttachment | undefined {
    const attachment = this.attachmentsByPath.get(normalizePathForComparison(filePath));
    return attachment?.kind === "image" ? attachment : undefined;
  }

  async saveAttachmentToDirectory<T extends UploadedAttachment>(
    attachment: T,
    targetDir: string,
    relativeRoot?: string,
  ): Promise<T> {
    await mkdir(targetDir, { recursive: true });

    const stagedPath = attachment.savedPath ?? attachment.path;
    const targetPath = await createUniqueTargetPath(
      targetDir,
      attachment.displayName,
      attachment.kind,
      attachment.mimeType,
    );
    await copyFile(stagedPath, targetPath);

    const nextAttachment = {
      ...attachment,
      savedPath: targetPath,
      savedRelativePath: relativeRoot
        ? normalizeRelativePath(relativeRoot, targetPath)
        : undefined,
    } as T;

    this.attachments.set(attachment.id, nextAttachment);
    this.attachmentsByPath.set(normalizePathForComparison(attachment.path), nextAttachment);
    this.attachmentsByPath.set(normalizePathForComparison(targetPath), nextAttachment);
    return nextAttachment;
  }

  async saveImageToDirectory(
    attachment: UploadedImageAttachment,
    targetDir: string,
    relativeRoot?: string,
  ): Promise<UploadedImageAttachment> {
    return this.saveAttachmentToDirectory(attachment, targetDir, relativeRoot);
  }

  async saveVideoToDirectory(
    attachment: UploadedVideoAttachment,
    targetDir: string,
    relativeRoot?: string,
  ): Promise<UploadedVideoAttachment> {
    return this.saveAttachmentToDirectory(attachment, targetDir, relativeRoot);
  }

  containsPath(filePath: string): boolean {
    const candidate = normalizePathForComparison(filePath);
    const root = normalizePathForComparison(this.rootDir);
    return candidate === root || candidate.startsWith(`${root}\\`) || candidate.startsWith(`${root}/`);
  }

  private async createAttachment(
    kind: AttachmentKind,
    input: CreateAttachmentInput,
  ): Promise<UploadedAttachment> {
    const normalizedMimeType = normalizeMimeType(kind, input.mimeType);
    const id = `att_${randomUUID()}`;
    const safeName = sanitizeFileName(input.displayName, kind);
    const extension = resolveAttachmentExtension(kind, normalizedMimeType, safeName);
    const fileName = `${id}${extension}`;
    const filePath = join(this.rootDir, fileName);

    await mkdir(this.rootDir, { recursive: true });
    await writeFile(filePath, input.content);

    const attachment: UploadedAttachment = {
      id,
      kind,
      displayName: safeName,
      mimeType: normalizedMimeType,
      path: filePath,
      createdAt: new Date().toISOString(),
    };
    this.attachments.set(id, attachment);
    this.attachmentsByPath.set(normalizePathForComparison(filePath), attachment);
    return attachment;
  }
}

function sanitizeFileName(value: string, kind: AttachmentKind): string {
  const normalized = value.trim().replace(/[\\/:*?"<>|]/g, "_");
  if (normalized) {
    return normalized;
  }

  return kind === "video" ? "video.mp4" : "image.jpg";
}

function resolveAttachmentExtension(kind: AttachmentKind, mimeType: string, displayName: string): string {
  const existingExtension = extname(displayName).trim();
  if (existingExtension) {
    return existingExtension;
  }

  switch (mimeType.toLowerCase()) {
    case "image/gif":
      return ".gif";
    case "image/bmp":
      return ".bmp";
    case "image/png":
      return ".png";
    case "image/webp":
      return ".webp";
    case "image/jpeg":
      return ".jpg";
    case "video/webm":
      return ".webm";
    case "video/quicktime":
      return ".mov";
    case "video/mp4":
    default:
      return kind === "video" ? ".mp4" : ".jpg";
  }
}

function decodeBase64(value: string, kind: AttachmentKind): Buffer {
  const normalized = value.trim();
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(normalized) || normalized.length % 4 !== 0) {
    throw new Error(kind === "video" ? "invalid-video-base64" : "invalid-image-base64");
  }

  const content = Buffer.from(normalized, "base64");
  if (content.length === 0 || content.toString("base64") !== normalized) {
    throw new Error(kind === "video" ? "invalid-video-base64" : "invalid-image-base64");
  }

  return content;
}

function normalizeMimeType(kind: AttachmentKind, value: string): string {
  const normalized = value.trim().toLowerCase();
  if (kind === "image") {
    switch (normalized) {
      case "image/jpeg":
      case "image/png":
      case "image/webp":
      case "image/gif":
      case "image/bmp":
        return normalized;
      default:
        throw new Error("unsupported-image-mime-type");
    }
  }

  switch (normalized) {
    case "video/mp4":
    case "video/webm":
    case "video/quicktime":
      return normalized;
    default:
      throw new Error("unsupported-video-mime-type");
  }
}

function normalizePathForComparison(value: string): string {
  const normalized = normalize(resolve(value));
  return process.platform === "win32" ? normalized.toLowerCase() : normalized;
}

async function createUniqueTargetPath(
  targetDir: string,
  displayName: string,
  kind: AttachmentKind,
  mimeType: string,
): Promise<string> {
  const safeName = sanitizeFileName(displayName, kind);
  const parsed = parse(safeName);
  const baseName = parsed.name || (kind === "video" ? "video" : "image");
  const extension = parsed.ext || resolveAttachmentExtension(kind, mimeType, safeName);

  let candidate = join(targetDir, `${baseName}${extension}`);
  let counter = 2;
  while (await pathExists(candidate)) {
    candidate = join(targetDir, `${baseName} (${counter})${extension}`);
    counter += 1;
  }

  return candidate;
}

async function pathExists(candidate: string): Promise<boolean> {
  try {
    await access(candidate);
    return true;
  } catch {
    return false;
  }
}

function normalizeRelativePath(relativeRoot: string, targetPath: string): string {
  return relative(resolve(relativeRoot), resolve(targetPath)).replace(/\\/g, "/");
}
