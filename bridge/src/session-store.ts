import { randomUUID } from "node:crypto";
import type { CreateSessionInput, SessionRecord } from "./types.js";

export class SessionStore {
  private readonly sessions = new Map<string, SessionRecord>();

  create(input: CreateSessionInput): SessionRecord {
    const now = new Date().toISOString();
    const threadId = `thread_${randomUUID()}`;
    return this.attach({
      id: threadId,
      cwd: input.cwd,
      model: input.model,
      approvalMode: input.approvalMode,
      reasoningEffort: input.reasoningEffort,
      serviceTier: input.serviceTier,
      sandboxMode: input.sandboxMode,
      status: "idle",
      threadId,
      activeTurnId: null,
      lastError: null,
      createdAt: now,
      updatedAt: now,
      lastActivityAt: now,
    });
  }

  attach(session: SessionRecord): SessionRecord {
    this.sessions.set(session.id, session);
    return session;
  }

  get(id: string): SessionRecord | undefined {
    return this.sessions.get(id);
  }

  update(id: string, patch: Partial<Omit<SessionRecord, "id" | "createdAt">>): SessionRecord | undefined {
    const current = this.sessions.get(id);
    if (!current) {
      return undefined;
    }

    const next: SessionRecord = {
      ...current,
      ...patch,
      updatedAt: new Date().toISOString(),
    };

    this.sessions.set(id, next);
    return next;
  }

  delete(id: string): boolean {
    return this.sessions.delete(id);
  }

  findByThreadId(threadId: string): SessionRecord | undefined {
    return [...this.sessions.values()].find((session) => session.threadId === threadId);
  }

  list(): SessionRecord[] {
    return [...this.sessions.values()].sort((a, b) =>
      resolveSessionActivityTimestamp(b).localeCompare(resolveSessionActivityTimestamp(a)),
    );
  }
}

function resolveSessionActivityTimestamp(session: SessionRecord): string {
  return session.lastActivityAt ?? session.updatedAt;
}
