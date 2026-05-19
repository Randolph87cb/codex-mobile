import { randomUUID } from "node:crypto";
import { mkdir, writeFile } from "node:fs/promises";
import { extname, join } from "node:path";
import { tmpdir } from "node:os";
import type { UploadedImageAttachment } from "./types.js";

interface CreateImageAttachmentInput {
  displayName: string;
  mimeType: string;
  contentBase64: string;
}

export class AttachmentStore {
  private readonly attachments = new Map<string, UploadedImageAttachment>();

  constructor(
    private readonly rootDir = join(tmpdir(), "codex-mobile-bridge", "attachments"),
  ) {}

  async createImage(input: CreateImageAttachmentInput): Promise<UploadedImageAttachment> {
    const id = `att_${randomUUID()}`;
    const safeName = sanitizeFileName(input.displayName);
    const extension = resolveImageExtension(input.mimeType, safeName);
    const fileName = `${id}${extension}`;
    const filePath = join(this.rootDir, fileName);
    const content = decodeBase64(input.contentBase64);

    await mkdir(this.rootDir, { recursive: true });
    await writeFile(filePath, content);

    const attachment: UploadedImageAttachment = {
      id,
      kind: "image",
      displayName: safeName,
      mimeType: input.mimeType,
      path: filePath,
      createdAt: new Date().toISOString(),
    };
    this.attachments.set(id, attachment);
    return attachment;
  }

  getImage(id: string): UploadedImageAttachment | undefined {
    return this.attachments.get(id);
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
    case "image/png":
      return ".png";
    case "image/webp":
      return ".webp";
    default:
      return ".jpg";
  }
}

function decodeBase64(value: string): Buffer {
  try {
    const content = Buffer.from(value, "base64");
    if (content.length === 0) {
      throw new Error("empty");
    }
    return content;
  } catch {
    throw new Error("invalid-image-base64");
  }
}
