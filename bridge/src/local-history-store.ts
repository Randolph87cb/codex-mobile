import { open, readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";
import { buildSessionViewFromThread, type AppServerThread } from "./session-view.js";
import { isInternalSubagentThread } from "./thread-visibility.js";
import type { SessionView } from "./types.js";

interface LocalHistoryStoreOptions {
  codexHome?: string;
  cacheTtlMs?: number;
}

interface RolloutCandidate {
  filePath: string;
  archived: boolean;
}

interface SessionIndexEntry {
  id: string;
  threadName?: string;
  updatedAt?: string;
}

interface RolloutEntry {
  timestamp?: string;
  type?: string;
  payload?: Record<string, unknown>;
}

interface RolloutThread {
  thread: AppServerThread;
  archived: boolean;
}

interface CachedViews {
  expiresAt: number;
  views: SessionView[];
}

type ThreadItems = NonNullable<NonNullable<AppServerThread["turns"]>[number]["items"]>;
type ThreadItem = ThreadItems[number];

const DefaultCacheTtlMs = 15_000;

export class LocalHistoryStore {
  private readonly codexHome: string;
  private readonly cacheTtlMs: number;
  private readonly listCache = new Map<boolean, CachedViews>();

  constructor(options: LocalHistoryStoreOptions = {}) {
    this.codexHome = options.codexHome ?? resolveCodexHome();
    this.cacheTtlMs = options.cacheTtlMs ?? DefaultCacheTtlMs;
  }

  async listSessionViews(archived: boolean): Promise<SessionView[]> {
    const now = Date.now();
    const cached = this.listCache.get(archived);
    if (cached && cached.expiresAt > now) {
      return cached.views;
    }

    const indexById = await this.readSessionIndex();
    const candidates = await this.listCandidates(archived);
    const views: SessionView[] = [];
    for (const candidate of candidates) {
      const parsed = await this.readRolloutThread(candidate, indexById, false);
      if (!parsed) {
        continue;
      }

      views.push(buildSessionViewFromThread(parsed.thread, undefined, null, parsed.archived));
    }

    views.sort((left, right) => right.lastUpdated.localeCompare(left.lastUpdated));
    this.listCache.set(archived, {
      expiresAt: now + this.cacheTtlMs,
      views,
    });
    return views;
  }

  async getSessionView(sessionId: string): Promise<SessionView | null> {
    const indexById = await this.readSessionIndex();
    for (const archived of [false, true]) {
      const candidates = await this.listCandidates(archived);
      const candidate = candidates.find((entry) => entry.filePath.includes(sessionId));
      if (!candidate) {
        continue;
      }

      const parsed = await this.readRolloutThread(candidate, indexById, true);
      if (!parsed) {
        return null;
      }

      return buildSessionViewFromThread(parsed.thread, undefined, null, parsed.archived);
    }

    return null;
  }

  private async listCandidates(archived: boolean): Promise<RolloutCandidate[]> {
    const root = archived
      ? path.join(this.codexHome, "archived_sessions")
      : path.join(this.codexHome, "sessions");
    const files = archived
      ? await listRolloutFiles(root, false)
      : await listRolloutFiles(root, true);
    return files.map((filePath) => ({ filePath, archived }));
  }

  private async readSessionIndex(): Promise<Map<string, SessionIndexEntry>> {
    const entries = new Map<string, SessionIndexEntry>();
    const indexPath = path.join(this.codexHome, "session_index.jsonl");
    let content: string;
    try {
      content = await readFile(indexPath, "utf8");
    } catch {
      return entries;
    }

    for (const line of content.split(/\r?\n/)) {
      if (!line.trim()) {
        continue;
      }

      try {
        const raw = JSON.parse(line) as Record<string, unknown>;
        const id = normalizeText(raw.id);
        if (!id) {
          continue;
        }

        entries.set(id, {
          id,
          threadName: normalizeText(raw.thread_name) ?? undefined,
          updatedAt: normalizeTimestamp(raw.updated_at) ?? undefined,
        });
      } catch {
        continue;
      }
    }

    return entries;
  }

  private async readRolloutThread(
    candidate: RolloutCandidate,
    indexById: Map<string, SessionIndexEntry>,
    includeItems: boolean,
  ): Promise<RolloutThread | null> {
    let content: string;
    let fileUpdatedAt: string | null = null;
    try {
      const stats = await stat(candidate.filePath);
      fileUpdatedAt = stats.mtime.toISOString();
      content = includeItems
        ? await readFile(candidate.filePath, "utf8")
        : await readInitialChunk(candidate.filePath);
    } catch {
      return null;
    }

    let entries: RolloutEntry[];
    try {
      entries = content
        .split(/\r?\n/)
        .filter((line) => line.trim().length > 0)
        .map((line) => JSON.parse(line) as RolloutEntry);
    } catch {
      return null;
    }

    const metaEntry = entries.find((entry) => entry.type === "session_meta");
    const meta = metaEntry?.payload ?? {};
    if (isInternalSubagentThread(meta)) {
      return null;
    }

    const id = normalizeText(meta.id) ?? extractSessionIdFromFileName(candidate.filePath);
    const cwd = normalizeText(meta.cwd) ?? findLatestCwd(entries) ?? "";
    if (!id || !cwd) {
      return null;
    }

    const index = indexById.get(id);
    const createdAt = normalizeTimestamp(meta.timestamp) ?? normalizeTimestamp(metaEntry?.timestamp) ?? fileUpdatedAt;
    const entryUpdatedAt = entries
      .map((entry) => normalizeTimestamp(entry.timestamp))
      .filter((value): value is string => Boolean(value))
      .at(-1);
    const updatedAt = pickLatestTimestamp(
      index?.updatedAt,
      entryUpdatedAt,
      createdAt,
    );
    const items = buildThreadItems(entries);
    return {
      archived: candidate.archived,
      thread: {
        id,
        cwd,
        modelProvider: normalizeText(meta.model_provider) ?? findLatestModel(entries) ?? "openai",
        name: index?.threadName ?? null,
        preview: extractPreviewText(items),
        createdAt,
        updatedAt,
        status: { type: "inactive" },
        turns: [
          {
            id: findLatestTurnId(entries) ?? id,
            status: "completed",
            startedAt: null,
            completedAt: null,
            items,
          },
        ],
      },
    };
  }
}

function resolveCodexHome(): string {
  const configured = process.env.CODEX_HOME;
  if (configured?.trim()) {
    return configured;
  }

  const userHome = process.env.USERPROFILE ?? process.env.HOME ?? "";
  return path.join(userHome, ".codex");
}

async function listRolloutFiles(root: string, recursive: boolean): Promise<string[]> {
  try {
    const entries = await readdir(root, { withFileTypes: true });
    const files: string[] = [];
    for (const entry of entries) {
      const entryPath = path.join(root, entry.name);
      if (entry.isDirectory() && recursive) {
        files.push(...await listRolloutFiles(entryPath, recursive));
        continue;
      }

      if (entry.isFile() && /^rollout-.*\.jsonl$/i.test(entry.name)) {
        files.push(entryPath);
      }
    }

    return files;
  } catch {
    return [];
  }
}

async function readInitialChunk(filePath: string): Promise<string> {
  const handle = await open(filePath, "r");
  try {
    const buffer = Buffer.alloc(256 * 1024);
    const result = await handle.read(buffer, 0, buffer.length, 0);
    if (result.bytesRead === 0) {
      return "";
    }

    const chunk = buffer.subarray(0, result.bytesRead).toString("utf8");
    const lastNewlineIndex = Math.max(chunk.lastIndexOf("\n"), chunk.lastIndexOf("\r"));
    return lastNewlineIndex >= 0 ? chunk.slice(0, lastNewlineIndex) : chunk;
  } finally {
    await handle.close();
  }
}

function buildThreadItems(entries: RolloutEntry[]): ThreadItems {
  const eventItems = entries
    .map((entry) => buildThreadItemFromEvent(entry))
    .filter((item): item is ThreadItem => item !== null);
  if (eventItems.length > 0) {
    return eventItems;
  }

  return entries
    .map((entry) => buildThreadItemFromResponseMessage(entry))
    .filter((item): item is ThreadItem => item !== null);
}

function buildThreadItemFromEvent(entry: RolloutEntry): ThreadItem | null {
  const payload = entry.payload ?? {};
  if (entry.type !== "event_msg") {
    return null;
  }

  if (payload.type === "user_message") {
    const text = normalizeText(payload.message);
    return text
      ? {
          type: "userMessage",
          content: [{ type: "text", text }],
        }
      : null;
  }

  if (payload.type === "agent_message") {
    const text = normalizeText(payload.message);
    return text ? { type: "agentMessage", text } : null;
  }

  return null;
}

function buildThreadItemFromResponseMessage(entry: RolloutEntry): ThreadItem | null {
  const payload = entry.payload ?? {};
  if (entry.type !== "response_item" || payload.type !== "message") {
    return null;
  }

  const role = normalizeText(payload.role);
  if (role !== "user" && role !== "assistant") {
    return null;
  }

  const text = extractMessageContentText(payload.content);
  if (!text) {
    return null;
  }

  return role === "user"
    ? {
        type: "userMessage",
        content: [{ type: "text", text }],
      }
    : { type: "agentMessage", text };
}

function extractMessageContentText(content: unknown): string | null {
  if (typeof content === "string") {
    return normalizeText(content);
  }

  if (!Array.isArray(content)) {
    return null;
  }

  return normalizeText(
    content
      .map((item) => {
        if (!item || typeof item !== "object") {
          return "";
        }
        const record = item as Record<string, unknown>;
        return normalizeText(record.text) ?? "";
      })
      .filter(Boolean)
      .join("\n"),
  );
}

function extractPreviewText(items: ThreadItems): string | undefined {
  for (const item of items) {
    if (item.type !== "userMessage") {
      continue;
    }

    const text = extractMessageContentText(item.content);
    if (text) {
      return text;
    }
  }

  return undefined;
}

function findLatestCwd(entries: RolloutEntry[]): string | null {
  for (const entry of [...entries].reverse()) {
    const cwd = normalizeText(entry.payload?.cwd);
    if (cwd) {
      return cwd;
    }
  }

  return null;
}

function findLatestModel(entries: RolloutEntry[]): string | null {
  for (const entry of [...entries].reverse()) {
    const model = normalizeText(entry.payload?.model);
    if (model) {
      return model;
    }
  }

  return null;
}

function findLatestTurnId(entries: RolloutEntry[]): string | null {
  for (const entry of [...entries].reverse()) {
    const metadata = entry.payload?.metadata;
    const turnId = normalizeText(entry.payload?.turn_id) ??
      (metadata && typeof metadata === "object"
        ? normalizeText((metadata as Record<string, unknown>).turn_id)
        : null);
    if (turnId) {
      return turnId;
    }
  }

  return null;
}

function extractSessionIdFromFileName(filePath: string): string | null {
  const match = path.basename(filePath).match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i);
  return match?.[1] ?? null;
}

function pickLatestTimestamp(...values: Array<string | null | undefined>): string {
  const valid = values.filter((value): value is string => Boolean(value));
  return valid.sort().at(-1) ?? new Date().toISOString();
}

function normalizeTimestamp(value: unknown): string | null {
  if (typeof value !== "string" || !value.trim()) {
    return null;
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }

  return date.toISOString();
}

function normalizeText(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim();
  return normalized ? normalized : null;
}
