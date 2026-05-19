import { describe, expect, test } from "vitest";
import { MockRunner } from "../src/mock-runner.js";
import { SessionStore } from "../src/session-store.js";
import type { BridgeEvent } from "../src/types.js";

describe("MockRunner", () => {
  test("emits delta and completion events", async () => {
    const store = new SessionStore();
    const session = store.create({
      cwd: "D:\\workspace\\codex-mobile",
      model: "gpt-5.5",
      approvalMode: "manual",
      reasoningEffort: "medium",
      serviceTier: "fast",
    });
    const runner = new MockRunner(store);
    const events: BridgeEvent[] = [];

    const unsubscribe = runner.subscribe(session.id, (event) => {
      events.push(event);
    });

    await runner.submitInput(session.id, {
      text: "hello",
      attachments: [],
    });
    unsubscribe();

    expect(events.some((event) => event.type === "assistant.delta")).toBe(true);
    expect(events.some((event) => event.type === "assistant.done")).toBe(true);
    expect(store.get(session.id)?.status).toBe("idle");
  });
});
