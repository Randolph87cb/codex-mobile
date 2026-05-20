import { describe, expect, test, vi } from "vitest";
import {
  AppServerClient,
  type JsonRpcServerRequest,
  type LineTransport,
} from "../src/app-server-client.js";

class FakeLineTransport implements LineTransport {
  readonly sent: string[] = [];
  private lineListener?: (line: string) => void;
  private stderrLineListener?: (line: string) => void;
  private exitListener?: (code: number | null) => void;

  start(): void {
    return;
  }

  sendLine(line: string): void {
    this.sent.push(line);
  }

  close(): void {
    this.exitListener?.(0);
  }

  onLine(listener: (line: string) => void): void {
    this.lineListener = listener;
  }

  onStderrLine(listener: (line: string) => void): void {
    this.stderrLineListener = listener;
  }

  onExit(listener: (code: number | null) => void): void {
    this.exitListener = listener;
  }

  emitLine(line: string): void {
    this.lineListener?.(line);
  }

  emitStderrLine(line: string): void {
    this.stderrLineListener?.(line);
  }
}

describe("AppServerClient", () => {
  test("initializes and resolves requests over line transport", async () => {
    const transport = new FakeLineTransport();
    const client = new AppServerClient({
      clientInfo: { name: "test", version: "0.1.0" },
      transport,
    });

    const startPromise = client.start();
    expect(JSON.parse(transport.sent[0]).method).toBe("initialize");

    transport.emitLine(
      JSON.stringify({
        id: 1,
        result: {
          userAgent: "ua",
          codexHome: "C:\\Users\\Administrator\\.codex",
          platformFamily: "windows",
          platformOs: "windows",
        },
      }),
    );
    await startPromise;

    const requestPromise = client.request<{ thread: { id: string } }>("thread/start", { cwd: "D:\\workspace" });
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(JSON.parse(transport.sent[1]!).method).toBe("thread/start");

    transport.emitLine(
      JSON.stringify({
        id: 2,
        result: {
          thread: { id: "thread-1" },
        },
      }),
    );

    await expect(requestPromise).resolves.toEqual({
      thread: { id: "thread-1" },
    });
  });

  test("dispatches notifications and server requests", async () => {
    const transport = new FakeLineTransport();
    const client = new AppServerClient({
      clientInfo: { name: "test", version: "0.1.0" },
      transport,
    });

    const notificationSpy = vi.fn();
    const requestSpy = vi.fn<(request: JsonRpcServerRequest) => void>();
    client.onNotification(notificationSpy);
    client.onServerRequest(requestSpy);

    const startPromise = client.start();
    transport.emitLine(
      JSON.stringify({
        id: 1,
        result: {
          userAgent: "ua",
          codexHome: "C:\\Users\\Administrator\\.codex",
          platformFamily: "windows",
          platformOs: "windows",
        },
      }),
    );
    await startPromise;

    transport.emitLine(JSON.stringify({ method: "thread/started", params: { thread: { id: "t1" } } }));
    transport.emitLine(JSON.stringify({ id: 99, method: "request_user_input", params: { threadId: "t1" } }));

    expect(notificationSpy).toHaveBeenCalledWith({
      method: "thread/started",
      params: { thread: { id: "t1" } },
    });
    expect(requestSpy).toHaveBeenCalledWith({
      id: 99,
      method: "request_user_input",
      params: { threadId: "t1" },
    });
  });

  test("ignores non-json stdout lines and keeps processing later json-rpc messages", async () => {
    const transport = new FakeLineTransport();
    const logger = { warn: vi.fn() };
    const client = new AppServerClient({
      clientInfo: { name: "test", version: "0.1.0" },
      transport,
      logger,
    });

    const startPromise = client.start();
    transport.emitLine("成功: 已终止 PID 1996 (父进程 PID 23768 子进程)的进程。");
    transport.emitLine(
      JSON.stringify({
        id: 1,
        result: {
          userAgent: "ua",
          codexHome: "C:\\Users\\Administrator\\.codex",
          platformFamily: "windows",
          platformOs: "windows",
        },
      }),
    );
    await startPromise;

    const requestPromise = client.request<{ ok: boolean }>("thread/list", {});
    await new Promise((resolve) => setTimeout(resolve, 0));
    transport.emitLine(JSON.stringify({ id: 2, result: { ok: true } }));

    await expect(requestPromise).resolves.toEqual({ ok: true });
    expect(logger.warn).toHaveBeenCalledWith(
      expect.stringContaining("app-server stdout non-json line ignored"),
    );
  });

  test("logs app-server stderr lines for later diagnosis", async () => {
    const transport = new FakeLineTransport();
    const logger = { warn: vi.fn() };
    const client = new AppServerClient({
      clientInfo: { name: "test", version: "0.1.0" },
      transport,
      logger,
    });

    const startPromise = client.start();
    transport.emitStderrLine("unexpected stderr line");
    transport.emitLine(
      JSON.stringify({
        id: 1,
        result: {
          userAgent: "ua",
          codexHome: "C:\\Users\\Administrator\\.codex",
          platformFamily: "windows",
          platformOs: "windows",
        },
      }),
    );
    await startPromise;

    expect(logger.warn).toHaveBeenCalledWith("app-server stderr: unexpected stderr line");
  });
});
