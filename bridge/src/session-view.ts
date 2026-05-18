import type { SessionRecord, SessionStatus, SessionView } from "./types.js";

interface AppServerThreadStatus {
  type?: string;
  activeFlags?: string[];
}

interface AppServerUserInput {
  type?: string;
  text?: string;
}

interface AppServerThreadItem {
  type?: string;
  text?: string;
  content?: AppServerUserInput[];
}

interface AppServerTurnError {
  message?: string;
}

interface AppServerTurn {
  id: string;
  status?: string;
  error?: AppServerTurnError | null;
  startedAt?: number | null;
  completedAt?: number | null;
  items?: AppServerThreadItem[];
}

export interface AppServerThread {
  id: string;
  cwd: string;
  modelProvider?: string;
  name?: string | null;
  preview?: string;
  createdAt?: number | string | null;
  updatedAt?: number | string | null;
  status?: AppServerThreadStatus;
  turns?: AppServerTurn[];
}

export function buildSessionViewFromRecord(session: SessionRecord): SessionView {
  return {
    id: session.id,
    title: "新会话",
    subtitle: `${session.model} • ${formatStatusLabel(session.status)} • ${session.cwd}`,
    lastUpdated: session.updatedAt,
    transcriptPreview: buildLocalTranscriptPreview(session),
    source: "local",
    cwd: session.cwd,
    model: session.model,
    approvalMode: session.approvalMode,
    reasoningEffort: session.reasoningEffort,
    serviceTier: session.serviceTier,
    status: session.status,
    threadId: session.threadId,
    activeTurnId: session.activeTurnId,
    lastError: session.lastError,
    createdAt: session.createdAt,
    updatedAt: session.updatedAt,
  };
}

export function buildSessionViewFromThread(
  thread: AppServerThread,
  session?: SessionRecord,
): SessionView {
  const fallbackStatus = mapThreadStatus(thread.status);
  const status = resolveThreadBackedStatus(thread, session, fallbackStatus);
  const createdAt = pickLatestTimestamp(toIsoString(thread.createdAt), session?.createdAt, false);
  const updatedAt = pickLatestTimestamp(toIsoString(thread.updatedAt), session?.updatedAt, true);
  const transcriptPreview = buildThreadTranscriptPreview(thread);
  const model = session?.model ?? normalizeText(thread.modelProvider, "openai") ?? "openai";
  const reasoningEffort = session?.reasoningEffort ?? "medium";
  const serviceTier = session?.serviceTier ?? "fast";
  const title = firstNonEmpty(
    normalizeText(thread.name),
    extractFirstUserMessage(thread),
    normalizeText(thread.preview),
    session?.threadId ? `线程 ${session.threadId}` : `线程 ${thread.id}`,
  );
  const lastError = firstNonEmptyOrNull(session?.lastError, extractLatestTurnError(thread));

  return {
    id: session?.id ?? thread.id,
    title,
    subtitle: `${model} • ${formatStatusLabel(status)} • ${thread.cwd}`,
    lastUpdated: updatedAt,
    transcriptPreview,
    source: session ? "local" : "history",
    cwd: thread.cwd,
    model,
    approvalMode: session?.approvalMode ?? "manual",
    reasoningEffort,
    serviceTier,
    status,
    threadId: session?.threadId ?? thread.id,
    activeTurnId: session?.activeTurnId ?? null,
    lastError,
    createdAt,
    updatedAt,
  };
}

export function mapThreadStatus(status: AppServerThreadStatus | undefined): SessionStatus {
  if (!status?.type) {
    return "idle";
  }

  if (status.type === "active" && status.activeFlags?.includes("waitingOnApproval")) {
    return "awaiting_approval";
  }

  if (status.type === "active") {
    return "running";
  }

  if (status.type === "systemError") {
    return "error";
  }

  return "idle";
}

function buildLocalTranscriptPreview(session: SessionRecord): string {
  return [
    `工作目录：${session.cwd}`,
    `线程 ID：${session.threadId ?? "尚未分配"}`,
    `当前轮次：${session.activeTurnId ?? "空闲"}`,
    `最近错误：${session.lastError ?? "无"}`,
  ].join("\n");
}

function buildThreadTranscriptPreview(thread: AppServerThread): string {
  const lines = collectTranscriptLines(thread);
  if (lines.length === 0) {
    return [
      `工作目录：${thread.cwd}`,
      `线程 ID：${thread.id}`,
      `预览：${normalizeText(thread.preview, "暂无消息")}`,
    ].join("\n");
  }

  const joined = lines.slice(-12).join("\n\n");
  return joined.length <= 4000 ? joined : joined.slice(joined.length - 4000);
}

function collectTranscriptLines(thread: AppServerThread): string[] {
  const turns = [...(thread.turns ?? [])].sort(compareTurns);
  const lines: string[] = [];

  for (const turn of turns) {
    for (const item of turn.items ?? []) {
      if (item.type === "userMessage") {
        const text = normalizeText(
          (item.content ?? [])
            .filter((contentItem) => contentItem.type === "text")
            .map((contentItem) => contentItem.text ?? "")
            .join("\n"),
        );
        if (text) {
          lines.push(`你：${text}`);
        }
        continue;
      }

      if (item.type === "agentMessage") {
        const text = normalizeText(item.text);
        if (text) {
          lines.push(`Codex：${text}`);
        }
      }
    }

    if (turn.status === "failed" && turn.error?.message) {
      lines.push(`系统：${turn.error.message}`);
    }
  }

  return lines;
}

function compareTurns(left: AppServerTurn, right: AppServerTurn): number {
  const leftTimestamp = left.startedAt ?? left.completedAt ?? 0;
  const rightTimestamp = right.startedAt ?? right.completedAt ?? 0;
  return leftTimestamp - rightTimestamp;
}

function extractFirstUserMessage(thread: AppServerThread): string | null {
  for (const turn of thread.turns ?? []) {
    for (const item of turn.items ?? []) {
      if (item.type !== "userMessage") {
        continue;
      }

      const text = normalizeText(
        (item.content ?? [])
          .filter((contentItem) => contentItem.type === "text")
          .map((contentItem) => contentItem.text ?? "")
          .join("\n"),
      );
      if (text) {
        return text;
      }
    }
  }

  return normalizeText(thread.preview);
}

function extractLatestTurnError(thread: AppServerThread): string | null {
  const turns = [...(thread.turns ?? [])].reverse();
  for (const turn of turns) {
    const message = normalizeText(turn.error?.message);
    if (message) {
      return message;
    }
  }

  return null;
}

function pickLatestTimestamp(
  left: string | null,
  right: string | undefined,
  preferLatest: boolean,
): string {
  const normalizedRight = right ?? null;
  if (!left) {
    return normalizedRight ?? new Date().toISOString();
  }
  if (!normalizedRight) {
    return left;
  }

  if (!preferLatest) {
    return left < normalizedRight ? left : normalizedRight;
  }

  return left > normalizedRight ? left : normalizedRight;
}

function toIsoString(value: number | string | null | undefined): string | null {
  if (typeof value === "number") {
    return new Date(value * 1000).toISOString();
  }
  if (typeof value === "string" && value.trim()) {
    return value;
  }
  return null;
}

function resolveThreadBackedStatus(
  thread: AppServerThread,
  session: SessionRecord | undefined,
  fallbackStatus: SessionStatus,
): SessionStatus {
  if (!session || session.status === "idle") {
    return fallbackStatus;
  }

  const threadUpdatedAt = toIsoString(thread.updatedAt);
  if (threadUpdatedAt && threadUpdatedAt > session.updatedAt) {
    return fallbackStatus;
  }

  return session.status;
}

function formatStatusLabel(status: SessionStatus): string {
  switch (status) {
    case "running":
      return "进行中";
    case "awaiting_approval":
      return "等待批准";
    case "error":
      return "出错";
    default:
      return "空闲";
  }
}

function normalizeText(value: string | null | undefined, fallback?: string): string | null {
  const normalized = value?.trim();
  if (normalized) {
    return normalized;
  }
  return fallback ?? null;
}

function firstNonEmpty(...values: Array<string | null | undefined>): string {
  for (const value of values) {
    const normalized = normalizeText(value);
    if (normalized) {
      return normalized;
    }
  }

  return "未命名会话";
}

function firstNonEmptyOrNull(...values: Array<string | null | undefined>): string | null {
  for (const value of values) {
    const normalized = normalizeText(value);
    if (normalized) {
      return normalized;
    }
  }

  return null;
}
