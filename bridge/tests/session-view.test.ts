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
});
