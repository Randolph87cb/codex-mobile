import { afterEach, describe, expect, test, vi } from "vitest";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { tmpdir } from "node:os";
import { buildBridgeApp } from "../src/app.js";
import type { HistoryCapableBridgeRunner } from "../src/bridge-runner.js";
import { LocalHistoryStore } from "../src/local-history-store.js";
import { SessionStore } from "../src/session-store.js";
import type {
  AccountQuotaSnapshot,
  ResolvedSessionInput,
  SessionApprovalInput,
  SessionApprovalResult,
  SessionGoalState,
  SessionGoalUpdateInput,
  SessionView,
} from "../src/types.js";

class FallbackTestRunner implements HistoryCapableBridgeRunner {
  readonly mode = "app-server" as const;
  readonly createSession = vi.fn();
  readonly submitInput = vi.fn(async (_sessionId: string, _input: ResolvedSessionInput) => undefined);
  readonly approve = vi.fn(async (_sessionId: string, input: SessionApprovalInput): Promise<SessionApprovalResult> => ({
    requestId: input.requestId ?? 1,
    decision: input.decision ?? "approve",
    method: "item/commandExecution/requestApproval",
    status: "running",
  }));
  readonly interrupt = vi.fn(async () => undefined);
  readonly renameSessionTitle = vi.fn();
  readonly archiveSession = vi.fn(async () => undefined);
  readonly unarchiveSession = vi.fn(async () => undefined);
  readonly getAccountQuota = vi.fn(async (): Promise<AccountQuotaSnapshot> => ({
    limitId: "codex",
    planType: "prolite",
    rateLimitReachedType: null,
    fiveHours: null,
    oneWeek: null,
    credits: null,
  }));
  readonly getSessionGoal = vi.fn(async (): Promise<SessionGoalState> => ({
    capability: "unsupported",
    goal: null,
  }));
  readonly updateSessionGoal = vi.fn(async (_sessionId: string, _input: SessionGoalUpdateInput): Promise<SessionGoalState> => ({
    capability: "unsupported",
    goal: null,
  }));
  readonly clearSessionGoal = vi.fn(async () => ({
    capability: "unsupported" as const,
    cleared: false,
  }));
  readonly attachSession = vi.fn(async () => null);
  readonly getSessionView = vi.fn(async (sessionId: string): Promise<SessionView | null> => {
    if (sessionId === "local-throw") {
      throw new Error("thread/read failed");
    }
    return null;
  });

  subscribe(): () => void {
    return () => undefined;
  }

  async listSessionViews(archived = false): Promise<SessionView[]> {
    if (archived) {
      return [];
    }

    return [
      buildHistoryView("remote-thread", "app-server 线程", "2026-06-22T00:00:00.000Z"),
      buildHistoryView("local-duplicate", "app-server 优先", "2026-06-21T23:00:00.000Z"),
    ];
  }
}

describe("local history fallback", () => {
  const tempDirs: string[] = [];

  afterEach(async () => {
    vi.restoreAllMocks();
    while (tempDirs.length > 0) {
      const dir = tempDirs.pop();
      if (dir) {
        await rm(dir, { recursive: true, force: true });
      }
    }
  });

  test("fills missing app-server sessions from local rollout JSONL without overriding app-server results", async () => {
    const codexHome = await mkdtemp(path.join(tmpdir(), "codex-mobile-local-history-"));
    tempDirs.push(codexHome);
    await writeSessionIndex(codexHome, [
      ["local-missing", "本地漏项", "2026-06-21T15:45:00.000Z"],
      ["local-duplicate", "本地重复项", "2026-06-21T15:46:00.000Z"],
      ["local-archived", "本地归档项", "2026-06-21T15:47:00.000Z"],
      ["local-throw", "本地读失败兜底", "2026-06-21T15:48:00.000Z"],
      ["local-subagent", "内部 subagent", "2026-06-21T15:49:00.000Z"],
      ["local-subagent-archived", "内部归档 subagent", "2026-06-21T15:50:00.000Z"],
    ]);
    await writeRollout(codexHome, {
      id: "local-missing",
      cwd: "D:\\workspace\\missing",
      titleUserText: "列表漏掉的本地线程",
      assistantText: "这里是本地回复。",
      updatedAt: "2026-06-21T15:45:00.000Z",
    });
    await writeRollout(codexHome, {
      id: "local-duplicate",
      cwd: "D:\\workspace\\duplicate",
      titleUserText: "不应覆盖 app-server",
      assistantText: "本地重复回复。",
      updatedAt: "2026-06-21T15:46:00.000Z",
    });
    await writeRollout(codexHome, {
      id: "local-archived",
      cwd: "D:\\workspace\\archived",
      titleUserText: "归档线程",
      assistantText: "本地归档回复。",
      updatedAt: "2026-06-21T15:47:00.000Z",
      archived: true,
    });
    await writeRollout(codexHome, {
      id: "local-throw",
      cwd: "D:\\workspace\\throw",
      titleUserText: "读失败兜底线程",
      assistantText: "fallback detail",
      updatedAt: "2026-06-21T15:48:00.000Z",
    });
    await writeRollout(codexHome, {
      id: "local-subagent",
      cwd: "D:\\workspace\\subagent",
      titleUserText: "不应显示的内部线程",
      assistantText: "internal detail",
      updatedAt: "2026-06-21T15:49:00.000Z",
      subagent: true,
    });
    await writeRollout(codexHome, {
      id: "local-subagent-archived",
      cwd: "D:\\workspace\\subagent-archived",
      titleUserText: "不应显示的归档内部线程",
      assistantText: "internal archived detail",
      updatedAt: "2026-06-21T15:50:00.000Z",
      archived: true,
      subagent: true,
    });
    await writeBadRollout(codexHome);

    const runner = new FallbackTestRunner();
    const app = await buildBridgeApp({
      store: new SessionStore(),
      runner,
      localHistoryStore: new LocalHistoryStore({ codexHome, cacheTtlMs: 0 }),
    });

    const active = await app.inject({ method: "GET", url: "/api/sessions?archived=false" });
    expect(active.statusCode).toBe(200);
    const activeItems = active.json<{ items: SessionView[] }>().items;
    expect(activeItems.map((item) => item.id)).toEqual([
      "remote-thread",
      "local-duplicate",
      "local-throw",
      "local-missing",
    ]);
    expect(activeItems.filter((item) => item.id === "local-duplicate")).toHaveLength(1);
    expect(activeItems.find((item) => item.id === "local-duplicate")?.title).toBe("app-server 优先");
    expect(activeItems.some((item) => item.id === "local-archived")).toBe(false);
    expect(activeItems.some((item) => item.id === "local-subagent")).toBe(false);

    const archived = await app.inject({ method: "GET", url: "/api/sessions?archived=true" });
    expect(archived.statusCode).toBe(200);
    expect(archived.json<{ items: SessionView[] }>().items).toEqual([
      expect.objectContaining({
        id: "local-archived",
        archived: true,
        transcriptPreview: expect.stringContaining("你：归档线程"),
      }),
    ]);
    expect(archived.json<{ items: SessionView[] }>().items.some((item) => item.id === "local-subagent-archived")).toBe(false);

    const missingDetail = await app.inject({ method: "GET", url: "/api/session/local-missing" });
    expect(missingDetail.statusCode).toBe(200);
    expect(missingDetail.json()).toMatchObject({
      id: "local-missing",
      title: "本地漏项",
      cwd: "D:\\workspace\\missing",
      source: "history",
      transcriptPreview: expect.stringContaining("你：列表漏掉的本地线程"),
    });

    const throwDetail = await app.inject({ method: "GET", url: "/api/session/local-throw" });
    expect(throwDetail.statusCode).toBe(200);
    expect(throwDetail.json()).toMatchObject({
      id: "local-throw",
      title: "本地读失败兜底",
      transcriptPreview: expect.stringContaining("Codex：fallback detail"),
    });

    const subagentDetail = await app.inject({ method: "GET", url: "/api/session/local-subagent" });
    expect(subagentDetail.statusCode).toBe(404);

    await app.listen({ port: 0, host: "127.0.0.1" });
    const address = app.server.address();
    if (!address || typeof address === "string") {
      throw new Error("unexpected-server-address");
    }

    const wsPayload = await new Promise<string>((resolve, reject) => {
      const socket = new WebSocket(`ws://127.0.0.1:${address.port}/api/session/local-missing/ws`);
      socket.addEventListener("message", (event) => {
        resolve(String(event.data));
        socket.close();
      });
      socket.addEventListener("error", () => {
        reject(new Error("websocket-connect-failed"));
      });
    });

    expect(JSON.parse(wsPayload)).toMatchObject({
      type: "session.started",
      sessionId: "local-missing",
      data: {
        status: "idle",
        threadId: "local-missing",
      },
    });

    await app.close();
  });
});

function buildHistoryView(id: string, title: string, lastUpdated: string): SessionView {
  return {
    id,
    title,
    subtitle: `openai • 空闲 • D:\\workspace\\${id}`,
    lastUpdated,
    transcriptPreview: "你：app-server",
    archived: false,
    source: "history",
    cwd: `D:\\workspace\\${id}`,
    model: "openai",
    approvalMode: "manual",
    reasoningEffort: "medium",
    serviceTier: "default",
    sandboxMode: "workspace-write",
    status: "idle",
    threadId: id,
    activeTurnId: null,
    lastError: null,
    goal: null,
    goalCapability: "unsupported",
    createdAt: lastUpdated,
    updatedAt: lastUpdated,
  };
}

async function writeSessionIndex(
  codexHome: string,
  rows: Array<[id: string, title: string, updatedAt: string]>,
): Promise<void> {
  await writeFile(
    path.join(codexHome, "session_index.jsonl"),
    rows.map(([id, thread_name, updated_at]) => JSON.stringify({ id, thread_name, updated_at })).join("\n"),
  );
}

async function writeRollout(
  codexHome: string,
  options: {
    id: string;
    cwd: string;
    titleUserText: string;
    assistantText: string;
    updatedAt: string;
    archived?: boolean;
    subagent?: boolean;
  },
): Promise<void> {
  const dir = options.archived
    ? path.join(codexHome, "archived_sessions")
    : path.join(codexHome, "sessions", "2026", "06", "21");
  await mkdir(dir, { recursive: true });
  const filePath = path.join(dir, `rollout-2026-06-21T23-43-48-${options.id}.jsonl`);
  const lines = [
    {
      timestamp: "2026-06-21T15:43:51.587Z",
      type: "session_meta",
      payload: {
        id: options.id,
        timestamp: "2026-06-21T15:43:48.384Z",
        cwd: options.cwd,
        model_provider: "openai",
        ...(options.subagent
          ? {
              thread_source: "subagent",
              source: {
                subagent: {
                  thread_spawn: {},
                },
              },
            }
          : {}),
      },
    },
    {
      timestamp: "2026-06-21T15:43:53.557Z",
      type: "event_msg",
      payload: {
        type: "user_message",
        message: options.titleUserText,
      },
    },
    {
      timestamp: options.updatedAt,
      type: "event_msg",
      payload: {
        type: "agent_message",
        message: options.assistantText,
      },
    },
  ];
  await writeFile(filePath, lines.map((line) => JSON.stringify(line)).join("\n"));
}

async function writeBadRollout(codexHome: string): Promise<void> {
  const dir = path.join(codexHome, "sessions", "2026", "06", "22");
  await mkdir(dir, { recursive: true });
  await writeFile(
    path.join(dir, "rollout-2026-06-22T00-00-00-bad-jsonl.jsonl"),
    "{\"timestamp\":\"2026-06-22T00:00:00.000Z\"}\n{bad json",
  );
}
