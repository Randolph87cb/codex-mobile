import path from "node:path";
import type { GoalCapability, PendingApprovalView, SessionRecord, SessionStatus, SessionView } from "./types.js";

interface AppServerThreadStatus {
  type?: string;
  activeFlags?: string[];
}

interface AppServerUserInput {
  type?: string;
  text?: string;
  path?: string;
  imageUrl?: string;
}

interface AppServerThreadItem {
  id?: string;
  type?: string;
  text?: string;
  content?: AppServerUserInput[];
  summary?: string[];
  command?: string;
  cwd?: string;
  status?: string;
  aggregatedOutput?: string | null;
  exitCode?: number | null;
  durationMs?: number | null;
  changes?: Array<{
    path?: string;
    kind?: string;
    diff?: string;
  }>;
  server?: string;
  tool?: string;
  arguments?: unknown;
  result?: unknown;
  error?: {
    message?: string;
  } | string | null;
  namespace?: string | null;
  contentItems?: unknown[] | null;
  success?: boolean | null;
  receiverThreadIds?: string[];
  prompt?: string | null;
  model?: string | null;
  query?: string;
  review?: string;
  path?: string;
  savedPath?: string;
  revisedPrompt?: string | null;
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

export function buildSessionViewFromRecord(
  session: SessionRecord,
  pendingApproval: PendingApprovalView | null = null,
): SessionView {
  return {
    id: session.id,
    title: "新会话",
    subtitle: `${session.model} • ${formatStatusLabel(session.status)} • ${session.cwd}`,
    lastUpdated: session.updatedAt,
    transcriptPreview: buildLocalTranscriptPreview(session),
    archived: false,
    source: "local",
    cwd: session.cwd,
    model: session.model,
    approvalMode: session.approvalMode,
    reasoningEffort: session.reasoningEffort,
    serviceTier: session.serviceTier,
    sandboxMode: session.sandboxMode,
    status: session.status,
    threadId: session.threadId,
    activeTurnId: session.activeTurnId,
    lastError: session.lastError,
    pendingApproval,
    goal: null,
    goalCapability: deriveGoalCapability(session.threadId),
    createdAt: session.createdAt,
    updatedAt: session.updatedAt,
  };
}

export function buildSessionViewFromThread(
  thread: AppServerThread,
  session?: SessionRecord,
  pendingApproval: PendingApprovalView | null = null,
  archived = false,
): SessionView {
  const fallbackStatus = mapThreadStatus(thread.status);
  const status = resolveThreadBackedStatus(thread, session, fallbackStatus);
  const createdAt = pickLatestTimestamp(toIsoString(thread.createdAt), session?.createdAt, false);
  const updatedAt = pickLatestTimestamp(toIsoString(thread.updatedAt), session?.updatedAt, true);
  const transcriptPreview = buildThreadTranscriptPreview(thread);
  const model = session?.model ?? normalizeText(thread.modelProvider, "openai") ?? "openai";
  const reasoningEffort = session?.reasoningEffort ?? "medium";
  const serviceTier = session?.serviceTier ?? "default";
  const sandboxMode = session?.sandboxMode ?? "workspace-write";
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
    archived,
    source: session ? "local" : "history",
    cwd: thread.cwd,
    model,
    approvalMode: session?.approvalMode ?? "manual",
    reasoningEffort,
    serviceTier,
    sandboxMode,
    status,
    threadId: session?.threadId ?? thread.id,
    activeTurnId: session?.activeTurnId ?? null,
    lastError,
    pendingApproval,
    goal: null,
    goalCapability: deriveGoalCapability(session?.threadId ?? thread.id),
    createdAt,
    updatedAt,
  };
}

function deriveGoalCapability(threadId: string | null | undefined): GoalCapability {
  return threadId ? "unknown" : "unsupported";
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

  return lines.join("\n\n");
}

function collectTranscriptLines(thread: AppServerThread): string[] {
  const turns = [...(thread.turns ?? [])].sort(compareTurns);
  const lines: string[] = [];

  for (const turn of turns) {
    for (const item of turn.items ?? []) {
      if (item.type === "userMessage") {
        const content = extractUserMessageContent(item);
        if (content) {
          lines.push(`你：${content}`);
        }
        continue;
      }

      if (item.type === "agentMessage") {
        const text = normalizeText(item.text);
        if (text) {
          lines.push(`Codex：${text}`);
        }
        continue;
      }

      const block = formatThreadItemAsTranscriptBlock(item);
      if (block) {
        lines.push(block);
      }
    }

    if (turn.status === "failed" && turn.error?.message) {
      lines.push(`系统：${turn.error.message}`);
    }
  }

  return lines;
}

export function formatThreadItemAsTranscriptBlock(item: AppServerThreadItem): string | null {
  switch (item.type) {
    case "plan":
      return buildSystemBlock("计划更新", [normalizeText(item.text)]);
    case "reasoning":
      {
        const reasoningLines = [
          item.summary?.map((part) => part.trim()).filter(Boolean).join("\n"),
          item.text,
        ].map((part) => normalizeText(part)).filter((part): part is string => part !== null);
        return reasoningLines.length > 0 ? buildSystemBlock("推理摘要", reasoningLines) : null;
      }
    case "commandExecution":
      return buildCommandExecutionBlock(item);
    case "fileChange":
      return buildFileChangeBlock(item);
    case "mcpToolCall":
      return buildMcpToolCallBlock(item);
    case "dynamicToolCall":
      return buildDynamicToolCallBlock(item);
    case "collabAgentToolCall":
      return buildCollabToolCallBlock(item);
    case "webSearch":
      return buildSystemBlock("网页搜索", [normalizeText(item.query)]);
    case "imageView":
      return buildSystemBlock("查看图片", [
        normalizeText(String(item.path ?? "")),
        buildBridgeFileMarkdown("下载原图", normalizeText(String(item.path ?? ""))),
        buildBridgeFileImageMarkdown(normalizeText(String(item.path ?? ""))),
      ]);
    case "imageGeneration":
      return buildSystemBlock("图片生成", [
        summarizeImageGenerationResult(item.result),
        buildSavedFileLine(normalizeText(String(item.savedPath ?? item.path ?? ""))),
        buildBridgeFileImageMarkdown(normalizeText(String(item.savedPath ?? item.path ?? ""))),
      ]);
    case "enteredReviewMode":
      return buildSystemBlock("进入审查模式", [normalizeText(item.review)]);
    case "exitedReviewMode":
      return buildSystemBlock("退出审查模式", [normalizeText(item.review)]);
    case "contextCompaction":
      return buildSystemBlock("上下文压缩", ["Codex 对当前线程做了上下文压缩。"]);
    default:
      return null;
  }
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

      const text = extractUserMessageText(item);
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

function normalizeText(value: unknown, fallback?: string): string | null {
  if (typeof value === "string") {
    const normalized = value.trim();
    if (normalized) {
      return normalized;
    }
  } else if (typeof value === "number" || typeof value === "boolean" || typeof value === "bigint") {
    const normalized = String(value).trim();
    if (normalized) {
      return normalized;
    }
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

function extractUserMessageText(item: AppServerThreadItem): string | null {
  return normalizeText(
    (item.content ?? [])
      .filter((contentItem) => contentItem.type === "text")
      .map((contentItem) => contentItem.text ?? "")
      .join("\n"),
  );
}

function extractUserMessageContent(item: AppServerThreadItem): string | null {
  const contentItems = item.content ?? [];
  const text = extractUserMessageText(item);
  const imageMarkdown = contentItems
    .filter((contentItem) => contentItem.type === "localImage" || contentItem.type === "image")
    .map((contentItem) => {
      if (contentItem.type === "localImage") {
        return buildBridgeFileImageMarkdown(contentItem.path);
      }
      return buildImageMarkdown(
        normalizeText(contentItem.text, "图片"),
        normalizeText(contentItem.imageUrl),
      );
    })
    .filter((value): value is string => Boolean(value));

  const parts = [
    text,
    ...imageMarkdown,
  ].filter((value): value is string => Boolean(value));
  return parts.length > 0 ? parts.join("\n") : null;
}

function buildBridgeFileImageMarkdown(rawPath: string | null | undefined): string | null {
  const normalizedPath = normalizeText(rawPath);
  if (!normalizedPath) {
    return null;
  }

  return buildImageMarkdown(
    path.basename(normalizedPath),
    `/api/image/file?path=${encodeURIComponent(normalizedPath)}`,
  );
}

function buildBridgeFileMarkdown(label: string, rawPath: string | null | undefined): string | null {
  const normalizedPath = normalizeText(rawPath);
  if (!normalizedPath) {
    return null;
  }

  return `[${label}](bridge-file://${encodeURIComponent(normalizedPath)})`;
}

function buildSavedFileLine(rawPath: string | null | undefined): string | null {
  const normalizedPath = normalizeText(rawPath);
  if (!normalizedPath) {
    return null;
  }

  const link = buildBridgeFileMarkdown(path.basename(normalizedPath), normalizedPath);
  return link ? `已保存：${link}` : `已保存：${normalizedPath}`;
}

function buildImageMarkdown(alt: string | null | undefined, source: string | null | undefined): string | null {
  const normalizedSource = normalizeText(source);
  if (!normalizedSource) {
    return null;
  }

  return `![${normalizeText(alt, "图片") ?? "图片"}](${normalizedSource})`;
}

function summarizeImageGenerationResult(result: unknown): string | null {
  if (typeof result !== "string") {
    return normalizeText(prettyJson(result));
  }

  const normalized = normalizeText(result);
  if (!normalized) {
    return null;
  }

  if (looksLikeBase64ImagePayload(normalized)) {
    return "图片内容已生成。";
  }

  return truncateText(normalized, 400);
}

function looksLikeBase64ImagePayload(value: string): boolean {
  if (value.length < 512) {
    return false;
  }

  return /^[A-Za-z0-9+/=\r\n]+$/.test(value);
}

function buildCommandExecutionBlock(item: AppServerThreadItem): string {
  const lines = [
    `状态：${formatItemStatus(item.status)}`,
    normalizeText(item.command) ? `命令：${normalizeText(item.command)}` : null,
    normalizeText(item.cwd) ? `目录：${normalizeText(item.cwd)}` : null,
    item.exitCode === null || item.exitCode === undefined ? null : `退出码：${item.exitCode}`,
    item.durationMs === null || item.durationMs === undefined ? null : `耗时：${item.durationMs} ms`,
  ];
  const outputText = normalizeText(item.aggregatedOutput);
  const output = outputText ? `输出：\n\`\`\`text\n${truncateText(outputText, 2000)}\n\`\`\`` : null;
  return buildSystemBlock("命令执行", [...lines, output]);
}

function buildFileChangeBlock(item: AppServerThreadItem): string {
  const changeLines = (item.changes ?? [])
    .slice(0, 6)
    .map((change) => {
      const path = normalizeText(change.path, "未知路径");
      return `${formatFileChangeKind(change.kind)}：${path}`;
    });
  const diffBlocks = (item.changes ?? [])
    .slice(0, 2)
    .map((change) => {
      const diff = normalizeText(change.diff);
      if (!diff) {
        return null;
      }
      return `${normalizeText(change.path, "未知路径")}\n\`\`\`diff\n${truncateText(diff, 1600)}\n\`\`\``;
    })
    .filter((value): value is string => value !== null);

  return buildSystemBlock(
    "文件修改",
    [
      `状态：${formatItemStatus(item.status)}`,
      ...changeLines,
      ...diffBlocks,
    ],
  );
}

function buildMcpToolCallBlock(item: AppServerThreadItem): string {
  return buildSystemBlock(
    `工具调用 ${firstNonEmpty(normalizeText(item.server), "mcp")}/${firstNonEmpty(normalizeText(item.tool), "unknown")}`,
    [
      `状态：${formatItemStatus(item.status)}`,
      normalizeText(prettyJson(item.arguments)),
      normalizeText(prettyJson(item.result)),
      normalizeText(extractErrorText(item.error)),
    ],
  );
}

function buildDynamicToolCallBlock(item: AppServerThreadItem): string {
  const toolName = item.namespace
    ? `${item.namespace}/${firstNonEmpty(normalizeText(item.tool), "unknown")}`
    : firstNonEmpty(normalizeText(item.tool), "unknown");
  return buildSystemBlock(
    `动态工具 ${toolName}`,
    [
      `状态：${formatItemStatus(item.status)}`,
      normalizeText(prettyJson(item.arguments)),
      item.success === null || item.success === undefined ? null : `成功：${item.success}`,
    ],
  );
}

function buildCollabToolCallBlock(item: AppServerThreadItem): string {
  return buildSystemBlock(
    `协作调用 ${firstNonEmpty(normalizeText(item.tool), "unknown")}`,
    [
      `状态：${formatItemStatus(item.status)}`,
      item.receiverThreadIds?.length ? `目标线程：${item.receiverThreadIds.join(", ")}` : null,
      normalizeText(item.prompt) ? `提示：${truncateText(normalizeText(item.prompt) ?? "", 400)}` : null,
      normalizeText(item.model) ? `模型：${normalizeText(item.model)}` : null,
    ],
  );
}

function buildSystemBlock(title: string, parts: Array<string | null | undefined>): string {
  const lines = parts
    .map((part) => normalizeText(part))
    .filter((part): part is string => part !== null);
  return [`系统：${title}`, ...lines].join("\n");
}

function truncateText(value: string, maxLength: number): string {
  return value.length <= maxLength ? value : `${value.slice(0, maxLength)}\n...`;
}

function prettyJson(value: unknown): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  if (typeof value === "string") {
    return normalizeText(value);
  }

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function extractErrorText(value: AppServerThreadItem["error"]): string | null {
  if (!value) {
    return null;
  }
  if (typeof value === "string") {
    return value;
  }
  return normalizeText(value.message);
}

function formatItemStatus(status: string | null | undefined): string {
  switch (status) {
    case "inProgress":
      return "进行中";
    case "completed":
      return "已完成";
    case "failed":
      return "失败";
    case "declined":
      return "已拒绝";
    default:
      return firstNonEmpty(normalizeText(status), "未知");
  }
}

function formatFileChangeKind(kind: string | null | undefined): string {
  switch (kind) {
    case "add":
      return "新增";
    case "delete":
      return "删除";
    case "update":
      return "修改";
    default:
      return firstNonEmpty(normalizeText(kind), "变更");
  }
}
