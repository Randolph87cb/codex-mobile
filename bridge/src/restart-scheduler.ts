import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import type {
  BridgeRestartScheduleInput,
  BridgeRestartScheduleResult,
  BridgeRestartScheduler,
} from "./app-context.js";

const DefaultRestartDelayMs = 1_000;

export function createBridgeRestartScheduler(): BridgeRestartScheduler {
  return {
    async scheduleRestart(input: BridgeRestartScheduleInput): Promise<BridgeRestartScheduleResult> {
      const scriptPath = resolveRestartScriptPath();
      const host = process.env.HOST?.trim() || "0.0.0.0";
      const port = normalizePort(process.env.PORT);
      const runner = process.env.CODEX_MOBILE_RUNNER?.trim() || "app-server";
      const helperCommand = [
        `Start-Sleep -Milliseconds ${DefaultRestartDelayMs}`,
        `& ${quotePowerShell(scriptPath)} -HostAddress ${quotePowerShell(host)} -Port ${port} -Runner ${quotePowerShell(runner)}`,
      ].join("; ");

      const child = spawn(
        "powershell.exe",
        [
          "-NoProfile",
          "-ExecutionPolicy",
          "Bypass",
          "-Command",
          helperCommand,
        ],
        {
          detached: true,
          stdio: "ignore",
          windowsHide: true,
          env: process.env,
        },
      );

      child.unref();

      return {
        phase: "scheduled",
        message: `bridge 重启已调度：${input.reason}，将在约 ${DefaultRestartDelayMs} ms 后执行。`,
      };
    },
  };
}

function resolveRestartScriptPath(): string {
  const moduleDir = path.dirname(fileURLToPath(import.meta.url));
  const repoRoot = path.resolve(moduleDir, "..", "..");
  return path.join(repoRoot, "scripts", "restart-bridge-background.ps1");
}

function normalizePort(value: string | undefined): number {
  const port = Number.parseInt(value ?? "", 10);
  return Number.isFinite(port) && port > 0 ? port : 8787;
}

function quotePowerShell(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}
