import { describe, expect, test } from "vitest";
import { buildSessionViewFromThread } from "../src/session-view.js";

describe("session view mapping", () => {
  test("builds transcript preview from thread turns", () => {
    const view = buildSessionViewFromThread({
      id: "thread-1",
      cwd: "D:\\workspace\\codex-mobile",
      modelProvider: "openai",
      preview: "你是谁",
      createdAt: 1_779_117_160,
      updatedAt: 1_779_117_260,
      status: {
        type: "idle",
      },
      turns: [
        {
          id: "turn-1",
          status: "completed",
          startedAt: 1_779_117_160,
          completedAt: 1_779_117_260,
          items: [
            {
              type: "userMessage",
              content: [
                {
                  type: "text",
                  text: "你是谁",
                },
              ],
            },
            {
              type: "agentMessage",
              text: "我是连接到 Windows 上 Codex 的移动客户端。",
            },
          ],
        },
      ],
    });

    expect(view.title).toBe("你是谁");
    expect(view.transcriptPreview).toContain("你：你是谁");
    expect(view.transcriptPreview).toContain("Codex：我是连接到 Windows 上 Codex 的移动客户端。");
    expect(view.threadId).toBe("thread-1");
  });

  test("renders historical image items as bridge image markdown", () => {
    const localImagePath = "D:\\workspace\\codex-mobile\\screenshots\\preview image.png";
    const view = buildSessionViewFromThread({
      id: "thread-image",
      cwd: "D:\\workspace\\codex-mobile",
      modelProvider: "openai",
      preview: "看图",
      createdAt: 1_779_117_160,
      updatedAt: 1_779_117_260,
      status: {
        type: "idle",
      },
      turns: [
        {
          id: "turn-image",
          status: "completed",
          startedAt: 1_779_117_160,
          completedAt: 1_779_117_260,
          items: [
            {
              type: "userMessage",
              content: [
                {
                  type: "text",
                  text: "帮我看下这两张图",
                },
                {
                  type: "localImage",
                  path: localImagePath,
                },
                {
                  type: "image",
                  text: "远程图片",
                  imageUrl: "https://example.com/remote.png",
                },
              ],
            },
          ],
        },
      ],
    });

    expect(view.transcriptPreview).toContain("你：帮我看下这两张图");
    expect(view.transcriptPreview).toContain(
      `![preview image.png](/api/image/file?path=${encodeURIComponent(localImagePath)})`,
    );
    expect(view.transcriptPreview).toContain("![远程图片](https://example.com/remote.png)");
  });

  test("keeps earlier transcript blocks instead of trimming to the latest tail", () => {
    const turns = Array.from({ length: 7 }, (_, index) => ({
      id: `turn-${index + 1}`,
      status: "completed",
      startedAt: 1_779_117_160 + index,
      completedAt: 1_779_117_260 + index,
      items: [
        {
          type: "userMessage",
          content: [
            {
              type: "text",
              text: `第 ${index + 1} 条用户消息`,
            },
          ],
        },
        {
          type: "agentMessage",
          text: `第 ${index + 1} 条助手回复`,
        },
      ],
    }));

    const view = buildSessionViewFromThread({
      id: "thread-history",
      cwd: "D:\\workspace\\codex-mobile",
      modelProvider: "openai",
      preview: "历史消息",
      createdAt: 1_779_117_160,
      updatedAt: 1_779_117_360,
      status: {
        type: "idle",
      },
      turns,
    });

    expect(view.transcriptPreview).toContain("你：第 1 条用户消息");
    expect(view.transcriptPreview).toContain("Codex：第 1 条助手回复");
    expect(view.transcriptPreview).toContain("你：第 7 条用户消息");
    expect(view.transcriptPreview).toContain("Codex：第 7 条助手回复");
  });

  test("includes operation items in transcript preview", () => {
    const view = buildSessionViewFromThread({
      id: "thread-ops",
      cwd: "D:\\workspace\\codex-mobile",
      modelProvider: "openai",
      preview: "操作消息",
      createdAt: 1_779_117_160,
      updatedAt: 1_779_117_360,
      status: {
        type: "active",
      },
      turns: [
        {
          id: "turn-ops",
          status: "inProgress",
          startedAt: 1_779_117_260,
          items: [
            {
              type: "commandExecution",
              status: "completed",
              command: "npm test",
              cwd: "D:\\workspace\\codex-mobile\\bridge",
              exitCode: 0,
              aggregatedOutput: "all tests passed",
            },
            {
              type: "fileChange",
              status: "completed",
              changes: [
                {
                  path: "bridge/src/app-server-runner.ts",
                  kind: "update",
                  diff: "@@ -1 +1 @@\n-old\n+new",
                },
              ],
            },
          ],
        },
      ],
    });

    expect(view.transcriptPreview).toContain("系统：命令执行");
    expect(view.transcriptPreview).toContain("命令：npm test");
    expect(view.transcriptPreview).toContain("系统：文件修改");
    expect(view.transcriptPreview).toContain("修改：bridge/src/app-server-runner.ts");
  });

  test("does not crash when historical item fields are not strings", () => {
    const view = buildSessionViewFromThread({
      id: "thread-weird",
      cwd: "D:\\workspace\\codex-mobile",
      modelProvider: "openai",
      preview: "异常字段",
      createdAt: 1_779_117_160,
      updatedAt: 1_779_117_360,
      status: {
        type: "idle",
      },
      turns: [
        {
          id: "turn-weird",
          status: "completed",
          startedAt: 1_779_117_260,
          items: [
            {
              type: "enteredReviewMode",
              review: { mode: "strict" } as never,
            },
            {
              type: "imageGeneration",
              result: { path: "artifact.png", size: 1234 } as never,
            },
          ],
        },
      ],
    });

    expect(view.title).toBe("异常字段");
    expect(view.transcriptPreview).toContain("系统：进入审查模式");
    expect(view.transcriptPreview).toContain("系统：图片生成");
  });

  test("uses saved image path instead of dumping base64 image payload", () => {
    const savedPath = "D:\\workspace\\临时目录\\pets\\image.png";
    const base64Payload = "A".repeat(2_048);
    const view = buildSessionViewFromThread({
      id: "thread-image-generation",
      cwd: "D:\\workspace\\临时目录",
      modelProvider: "openai",
      preview: "图片生成",
      createdAt: 1_779_117_160,
      updatedAt: 1_779_117_360,
      status: {
        type: "idle",
      },
      turns: [
        {
          id: "turn-image-generation",
          status: "completed",
          startedAt: 1_779_117_260,
          items: [
            {
              type: "imageGeneration",
              result: base64Payload,
              savedPath,
            },
          ],
        },
      ],
    });

    expect(view.transcriptPreview).toContain("系统：图片生成");
    expect(view.transcriptPreview).toContain("图片内容已生成。");
    expect(view.transcriptPreview).toContain(
      `已保存：[image.png](bridge-file://${encodeURIComponent(savedPath)})`,
    );
    expect(view.transcriptPreview).toContain(
      `![image.png](/api/image/file?path=${encodeURIComponent(savedPath)})`,
    );
    expect(view.transcriptPreview).not.toContain(base64Payload.slice(0, 128));
  });

  test("prefers newer thread status over stale local running status", () => {
    const view = buildSessionViewFromThread(
      {
        id: "thread-2",
        cwd: "D:\\workspace\\codex-mobile",
        modelProvider: "openai",
        preview: "旧线程",
        createdAt: "2026-05-19T03:00:00.000Z",
        updatedAt: "2026-05-19T03:10:00.000Z",
        status: {
          type: "inactive",
        },
        turns: [],
      },
      {
        id: "thread-2",
        cwd: "D:\\workspace\\codex-mobile",
        model: "gpt-5.5",
        approvalMode: "manual",
        reasoningEffort: "medium",
        serviceTier: "fast",
        sandboxMode: "danger-full-access",
        status: "running",
        threadId: "thread-2",
        activeTurnId: "turn-old",
        lastError: null,
        createdAt: "2026-05-19T03:00:00.000Z",
        updatedAt: "2026-05-19T03:05:00.000Z",
      },
    );

    expect(view.status).toBe("idle");
    expect(view.subtitle).toContain("空闲");
    expect(view.sandboxMode).toBe("danger-full-access");
  });

  test("uses last activity time instead of local refresh time for thread-backed sessions", () => {
    const view = buildSessionViewFromThread(
      {
        id: "thread-3",
        cwd: "D:\\workspace\\codex-mobile",
        modelProvider: "openai",
        preview: "最后回复时间",
        createdAt: "2026-05-19T03:00:00.000Z",
        updatedAt: "2026-05-19T03:10:00.000Z",
        status: {
          type: "inactive",
        },
        turns: [
          {
            id: "turn-3",
            status: "completed",
            startedAt: 1_747_633_000,
            completedAt: 1_747_633_060,
            items: [],
          },
        ],
      },
      {
        id: "thread-3",
        cwd: "D:\\workspace\\codex-mobile",
        model: "gpt-5.5",
        approvalMode: "manual",
        reasoningEffort: "medium",
        serviceTier: "default",
        sandboxMode: "workspace-write",
        status: "idle",
        threadId: "thread-3",
        activeTurnId: null,
        lastError: null,
        createdAt: "2026-05-19T03:00:00.000Z",
        updatedAt: "2026-05-19T03:20:00.000Z",
        lastActivityAt: "2026-05-19T03:08:00.000Z",
      },
    );

    expect(view.updatedAt).toBe("2026-05-19T03:20:00.000Z");
    expect(view.lastUpdated).toBe("2026-05-19T03:10:00.000Z");
  });
});
