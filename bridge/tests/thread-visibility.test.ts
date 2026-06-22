import { describe, expect, test } from "vitest";
import { isInternalSubagentThread } from "../src/thread-visibility.js";

describe("isInternalSubagentThread", () => {
  test("recognizes stable subagent markers", () => {
    expect(isInternalSubagentThread({ thread_source: "subagent" })).toBe(true);
    expect(isInternalSubagentThread({ threadSource: "subagent" })).toBe(true);
    expect(isInternalSubagentThread({ source: { subagent: { thread_spawn: {} } } })).toBe(true);
  });

  test("does not hide parent-only threads", () => {
    expect(isInternalSubagentThread({ parent_thread_id: "parent-thread" })).toBe(false);
    expect(isInternalSubagentThread({ parentThreadId: "parent-thread" })).toBe(false);
  });
});
