import { EventEmitter } from "node:events";
import { existsSync } from "node:fs";
import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import { createInterface, type Interface } from "node:readline";

import type { JsonRpcRequestId } from "./types.js";

export interface JsonRpcResponse<TResult> {
  id: number;
  result?: TResult;
  error?: {
    code: number;
    message: string;
    data?: unknown;
  };
}

export interface JsonRpcNotification {
  method: string;
  params?: unknown;
}

export interface JsonRpcServerRequest {
  id: JsonRpcRequestId;
  method: string;
  params?: unknown;
}

export interface LineTransport {
  start(): void;
  sendLine(line: string): void;
  close(): void;
  onLine(listener: (line: string) => void): void;
  onStderrLine(listener: (line: string) => void): void;
  onExit(listener: (code: number | null) => void): void;
}

export class ChildProcessLineTransport implements LineTransport {
  private child?: ChildProcessWithoutNullStreams;
  private rl?: Interface;
  private stderrRl?: Interface;
  private readonly lineListeners = new Set<(line: string) => void>();
  private readonly stderrLineListeners = new Set<(line: string) => void>();
  private readonly exitListeners = new Set<(code: number | null) => void>();

  constructor(
    private readonly command: string,
    private readonly args: string[],
    private readonly cwd?: string,
  ) {}

  start(): void {
    if (this.child) {
      return;
    }

    this.child = spawn(this.command, this.args, {
      cwd: this.cwd,
      stdio: ["pipe", "pipe", "pipe"],
      windowsHide: true,
    });

    this.rl = createInterface({ input: this.child.stdout });
    this.rl.on("line", (line) => {
      for (const listener of this.lineListeners) {
        listener(line);
      }
    });
    this.stderrRl = createInterface({ input: this.child.stderr });
    this.stderrRl.on("line", (line) => {
      for (const listener of this.stderrLineListeners) {
        listener(line);
      }
    });

    this.child.on("exit", (code) => {
      for (const listener of this.exitListeners) {
        listener(code);
      }
    });
  }

  sendLine(line: string): void {
    if (!this.child) {
      throw new Error("transport-not-started");
    }

    this.child.stdin.write(`${line}\n`);
  }

  close(): void {
    this.rl?.close();
    this.stderrRl?.close();
    this.child?.kill();
    this.rl = undefined;
    this.stderrRl = undefined;
    this.child = undefined;
  }

  onLine(listener: (line: string) => void): void {
    this.lineListeners.add(listener);
  }

  onStderrLine(listener: (line: string) => void): void {
    this.stderrLineListeners.add(listener);
  }

  onExit(listener: (code: number | null) => void): void {
    this.exitListeners.add(listener);
  }
}

interface AppServerClientOptions {
  cwd?: string;
  clientInfo: {
    name: string;
    version: string;
  };
  transport?: LineTransport;
  logger?: AppServerClientLogger;
}

export interface AppServerClientLogger {
  warn(message: string): void;
}

export class AppServerClient {
  private readonly transport: LineTransport;
  private readonly logger: AppServerClientLogger;
  private readonly notifications = new Set<(message: JsonRpcNotification) => void>();
  private readonly serverRequests = new Set<(message: JsonRpcServerRequest) => void>();
  private readonly pending = new Map<number, { resolve: (value: unknown) => void; reject: (error: Error) => void }>();
  private nextId = 1;
  private startPromise?: Promise<void>;

  constructor(private readonly options: AppServerClientOptions) {
    this.transport =
      options.transport ??
      new ChildProcessLineTransport(resolveCodexExecutable(), ["app-server"], options.cwd);
    this.logger = options.logger ?? {
      warn: (message: string) => {
        console.warn(message);
      },
    };
  }

  async start(): Promise<void> {
    if (this.startPromise) {
      return this.startPromise;
    }

    this.transport.onLine((line) => {
      this.handleLine(line);
    });
    this.transport.onStderrLine((line) => {
      const trimmedLine = line.trim();
      if (!trimmedLine) {
        return;
      }
      this.logger.warn(`app-server stderr: ${truncateTransportLine(trimmedLine)}`);
    });
    this.transport.onExit((code) => {
      const error = new Error(`app-server exited with code ${code ?? "unknown"}`);
      for (const pending of this.pending.values()) {
        pending.reject(error);
      }
      this.pending.clear();
    });

    this.transport.start();
    this.startPromise = this.requestInternal("initialize", {
      clientInfo: this.options.clientInfo,
      capabilities: {
        experimentalApi: true,
      },
    }).then(() => undefined);
    return this.startPromise;
  }

  async request<TResult>(method: string, params: unknown): Promise<TResult> {
    await this.start();
    return this.requestInternal<TResult>(method, params);
  }

  onNotification(listener: (message: JsonRpcNotification) => void): () => void {
    this.notifications.add(listener);
    return () => {
      this.notifications.delete(listener);
    };
  }

  onServerRequest(listener: (message: JsonRpcServerRequest) => void): () => void {
    this.serverRequests.add(listener);
    return () => {
      this.serverRequests.delete(listener);
    };
  }

  sendResponse(id: JsonRpcRequestId, result: unknown): void {
    this.transport.sendLine(JSON.stringify({ id, result }));
  }

  sendError(id: JsonRpcRequestId, code: number, message: string, data?: unknown): void {
    this.transport.sendLine(JSON.stringify({ id, error: { code, message, data } }));
  }

  async close(): Promise<void> {
    this.transport.close();
  }

  private requestInternal<TResult>(method: string, params: unknown): Promise<TResult> {
    const id = this.nextId++;

    return new Promise<TResult>((resolve, reject) => {
      this.pending.set(id, {
        resolve: (value) => resolve(value as TResult),
        reject,
      });

      this.transport.sendLine(
        JSON.stringify({
          jsonrpc: "2.0",
          id,
          method,
          params,
        }),
      );
    });
  }

  private handleLine(line: string): void {
    const trimmedLine = line.trim();
    if (!trimmedLine) {
      return;
    }

    if (!trimmedLine.startsWith("{")) {
      this.logger.warn(`app-server stdout non-json line ignored: ${truncateTransportLine(trimmedLine)}`);
      return;
    }

    let parsed: JsonRpcResponse<unknown> | JsonRpcNotification | JsonRpcServerRequest;
    try {
      parsed = JSON.parse(trimmedLine) as
        | JsonRpcResponse<unknown>
        | JsonRpcNotification
        | JsonRpcServerRequest;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      this.logger.warn(
        `app-server stdout malformed json ignored: ${truncateTransportLine(trimmedLine)} (${message})`,
      );
      return;
    }

    if ("id" in parsed && "method" in parsed) {
      for (const listener of this.serverRequests) {
        listener(parsed);
      }
      return;
    }

    if ("id" in parsed) {
      const pending = this.pending.get(parsed.id);
      if (!pending) {
        return;
      }

      this.pending.delete(parsed.id);
      if (parsed.error) {
        pending.reject(new Error(parsed.error.message));
        return;
      }

      pending.resolve(parsed.result);
      return;
    }

    for (const listener of this.notifications) {
      listener(parsed);
    }
  }
}

function resolveCodexExecutable(): string {
  if (process.env.CODEX_EXECUTABLE && existsSync(process.env.CODEX_EXECUTABLE)) {
    return process.env.CODEX_EXECUTABLE;
  }

  const appData = process.env.APPDATA;
  if (appData) {
    const npmVendor = `${appData}\\npm\\node_modules\\@openai\\codex\\node_modules\\@openai\\codex-win32-x64\\vendor\\x86_64-pc-windows-msvc\\codex\\codex.exe`;
    if (existsSync(npmVendor)) {
      return npmVendor;
    }
  }

  return "codex.exe";
}

function truncateTransportLine(line: string, maxLength = 400): string {
  return line.length <= maxLength ? line : `${line.slice(0, maxLength)}...`;
}

