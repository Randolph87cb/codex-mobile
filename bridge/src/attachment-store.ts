import { randomUUID } from "node:crypto";
import { access, copyFile, mkdir, writeFile } from "node:fs/promises";
import { extname, join, normalize, parse, relative, resolve } from "node:path";
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

  async saveImageToDirectory(
    attachment: UploadedImageAttachment,
    targetDir: string,
    relativeRoot?: string,
  ): Promise<UploadedImageAttachment> {
    await mkdir(targetDir, { recursive: true });

    const stagedPath = attachment.savedPath ?? attachment.path;
    const targetPath = await createUniqueTargetPath(targetDir, attachment.displayName);
    await copyFile(stagedPath, targetPath);

    const nextAttachment: UploadedImageAttachment = {
      ...attachment,
      savedPath: targetPath,
      savedRelativePath: relativeRoot
        ? normalizeRelativePath(relativeRoot, targetPath)
        : undefined,
    };

    this.attachments.set(attachment.id, nextAttachment);
    this.attachmentsByPath.set(normalizePathForComparison(attachment.path), nextAttachment);
    this.attachmentsByPath.set(normalizePathForComparison(targetPath), nextAttachment);
    return nextAttachment;
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

async function createUniqueTargetPath(targetDir: string, displayName: string): Promise<string> {
  const safeName = sanitizeFileName(displayName);
  const parsed = parse(safeName);
  const baseName = parsed.name || "image";
  const extension = parsed.ext || ".jpg";

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
