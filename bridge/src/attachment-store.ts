import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { tmpdir } from "node:os";
import type { UploadedImageAttachment } from "./types.js";

interface CreateImageAttachmentInput {
  displayName: string;
  mimeType: string;
  content: Buffer;
}

export class AttachmentStore {
  private readonly attachments = new Map<string, UploadedImageAttachment>();
  private readonly attachmentsByPath = new Map<string, UploadedImageAttachment>();

  constructor(
    private readonly rootDir = join(tmpdir(), "codex-mobile-bridge", "attachments"),
  ) {}

  async createImage(input: CreateImageAttachmentInput): Promise<UploadedImageAttachment> {
    const normalizedMimeType = normalizeImageMimeType(input.mimeType);
    const id = `att_${randomUUID()}`;
    const safeName = sanitizeFileName(input.displayName);
    const extension = resolveImageExtension(normalizedMimeType, safeName);
    const fileName = `${id}${extension}`;
    const filePath = join(this.rootDir, fileName);

    await mkdir(this.rootDir, { recursive: true });
    await writeFile(filePath, input.content);

    const attachment: UploadedImageAttachment = {
      id,
      kind: "image",
      displayName: safeName,
      mimeType: normalizedMimeType,
      path: filePath,
      createdAt: new Date().toISOString(),
    };
    this.attachments.set(id, attachment);
    this.attachmentsByPath.set(normalizePathForComparison(filePath), attachment);
    return attachment;
  }

  async createImageFromBase64(input: Omit<CreateImageAttachmentInput, "content"> & { contentBase64: string }): Promise<UploadedImageAttachment> {
    return this.createImage({
      displayName: input.displayName,
      mimeType: input.mimeType,
      content: decodeBase64(input.contentBase64),
    });
  }

  getImage(id: string): UploadedImageAttachment | undefined {
    return this.attachments.get(id);
  }

  getImageByPath(filePath: string): UploadedImageAttachment | undefined {
    return this.attachmentsByPath.get(normalizePathForComparison(filePath));
  }

  containsPath(filePath: string): boolean {
    const candidate = normalizePathForComparison(filePath);
    const root = normalizePathForComparison(this.rootDir);
    return candidate === root || candidate.startsWith(`${root}\\`) || candidate.startsWith(`${root}/`);
  }
}

function sanitizeFileName(value: string): string {
  const normalized = value.trim().replace(/[\\/:*?"<>|]/g, "_");
  return normalized || "image.jpg";
}

function resolveImageExtension(mimeType: string, displayName: string): string {
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
    default:
      return ".jpg";
  }
}

function decodeBase64(value: string): Buffer {
  const normalized = value.trim();
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(normalized) || normalized.length % 4 !== 0) {
    throw new Error("invalid-image-base64");
  }

  const content = Buffer.from(normalized, "base64");
  if (content.length === 0 || content.toString("base64") !== normalized) {
    throw new Error("invalid-image-base64");
  }

  return content;
}

function normalizeImageMimeType(value: string): string {
  const normalized = value.trim().toLowerCase();
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

function normalizePathForComparison(value: string): string {
  const normalized = normalize(resolve(value));
  return process.platform === "win32" ? normalized.toLowerCase() : normalized;
}
