export function isInternalSubagentThread(value: unknown): boolean {
  const record = asRecord(value);
  if (!record) {
    return false;
  }

  const threadSource = normalizeText(record.thread_source) ?? normalizeText(record.threadSource);
  if (threadSource === "subagent") {
    return true;
  }

  const source = asRecord(record.source);
  return source ? Object.hasOwn(source, "subagent") : false;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null ? value as Record<string, unknown> : null;
}

function normalizeText(value: unknown): string | null {
  return typeof value === "string" ? value.trim() : null;
}
