import { spawn, type SpawnOptions } from "node:child_process";
import path from "node:path";
import type { Readable } from "node:stream";
import { fileURLToPath } from "node:url";
import type {
  BridgeRestartScheduleInput,
  BridgeRestartScheduleResult,
  BridgeRestartScheduler,
} from "./app-context.js";

const DefaultRestartDelayMs = 1_000;
const OuterLaunchTimeoutMs = 10_000;
const HelperStartedMarker = "HELPER_STARTED";

export interface RestartSchedulerChildProcess {
  stdout?: Readable | null;
  stderr?: Readable | null;
  kill?(signal?: NodeJS.Signals | number): boolean;
  once(event: "error", listener: (error: Error) => void): this;
  once(event: "exit", listener: (code: number | null, signal: NodeJS.Signals | null) => void): this;
}

export type RestartSchedulerSpawn = (
  command: string,
  args: readonly string[],
  options: SpawnOptions,
) => RestartSchedulerChildProcess;

export interface BridgeRestartSchedulerOptions {
  spawn?: RestartSchedulerSpawn;
}

export function createBridgeRestartScheduler(options: BridgeRestartSchedulerOptions = {}): BridgeRestartScheduler {
  const spawnProcess = options.spawn ?? spawn;

  return {
    async scheduleRestart(input: BridgeRestartScheduleInput): Promise<BridgeRestartScheduleResult> {
      const repoRoot = resolveRepoRoot();
      const scriptPath = path.join(repoRoot, "scripts", "restart-bridge-background.ps1");
      const logsDir = path.join(repoRoot, ".logs", "bridge");
      const helperLogPath = path.join(logsDir, "restart-helper.log");
      const host = process.env.HOST?.trim() || "0.0.0.0";
      const port = normalizePort(process.env.PORT);
      const runner = process.env.CODEX_MOBILE_RUNNER?.trim() || "app-server";
      const helperCommand = buildOuterHelperCommand({
        helperLogPath,
        host,
        logsDir,
        port,
        reason: input.reason,
        runner,
        scriptPath,
      });

      const child = spawnProcess(
        "powershell.exe",
        [
          "-NoProfile",
          "-ExecutionPolicy",
          "Bypass",
          "-Command",
          helperCommand,
        ],
        {
          stdio: ["ignore", "pipe", "pipe"],
          windowsHide: true,
          env: process.env,
        },
      );

      const launch = await waitForHelperLaunch(child);
      if (!launch.stdout.includes(HelperStartedMarker)) {
        throw new Error(
          [
            "restart helper launch did not confirm Start-Process success",
            formatProcessOutput("stdout", launch.stdout),
            formatProcessOutput("stderr", launch.stderr),
          ].join("\n"),
        );
      }

      return {
        phase: "scheduled",
        message: `bridge 重启已调度：${input.reason}，将在约 ${DefaultRestartDelayMs} ms 后执行。`,
      };
    },
  };
}

interface RestartHelperCommandInput {
  helperLogPath: string;
  host: string;
  logsDir: string;
  port: number;
  reason: string;
  runner: string;
  scriptPath: string;
}

function buildOuterHelperCommand(input: RestartHelperCommandInput): string {
  const innerCommand = buildInnerHelperCommand(input);
  const encodedInnerCommand = Buffer.from(innerCommand, "utf16le").toString("base64");
  const launchSummary = `launch requested: reason=${input.reason}, host=${input.host}, port=${input.port}, runner=${input.runner}`;
  const startedSummary = `started restart helper: script=${input.scriptPath}, host=${input.host}, port=${input.port}, runner=${input.runner}`;

  return [
    `$ErrorActionPreference = 'Stop'`,
    `$logsDir = ${quotePowerShell(input.logsDir)}`,
    `$helperLog = ${quotePowerShell(input.helperLogPath)}`,
    `New-Item -ItemType Directory -Path $logsDir -Force | Out-Null`,
    `function Write-HelperLog {`,
    `    param([string]$Message)`,
    `    $timestamp = (Get-Date).ToString("o")`,
    `    Add-Content -Path $helperLog -Value "$timestamp [launcher] $Message" -Encoding UTF8`,
    `}`,
    `Write-HelperLog ${quotePowerShell(launchSummary)}`,
    `$powerShellExe = Join-Path $PSHOME 'powershell.exe'`,
    `if (-not (Test-Path $powerShellExe)) { $powerShellExe = 'powershell.exe' }`,
    `$process = Start-Process -FilePath $powerShellExe -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', ${quotePowerShell(encodedInnerCommand)}) -WindowStyle Hidden -PassThru`,
    `Write-HelperLog (${quotePowerShell(`${startedSummary}; pid=`)} + $process.Id)`,
    `Write-Output "${HelperStartedMarker} pid=$($process.Id) log=$helperLog"`,
  ].join("\r\n");
}

function buildInnerHelperCommand(input: RestartHelperCommandInput): string {
  return [
    `$ErrorActionPreference = 'Stop'`,
    `$helperLog = ${quotePowerShell(input.helperLogPath)}`,
    `function Write-HelperLog {`,
    `    param([string]$Message)`,
    `    $timestamp = (Get-Date).ToString("o")`,
    `    Add-Content -Path $helperLog -Value "$timestamp [helper] $Message" -Encoding UTF8`,
    `}`,
    `Write-HelperLog "waiting ${DefaultRestartDelayMs} ms before running restart script"`,
    `Start-Sleep -Milliseconds ${DefaultRestartDelayMs}`,
    `Write-HelperLog ${quotePowerShell(`running restart script: ${input.scriptPath}`)}`,
    `try {`,
    `    $scriptOutput = & ${quotePowerShell(input.scriptPath)} -HostAddress ${quotePowerShell(input.host)} -Port ${input.port} -Runner ${quotePowerShell(input.runner)} 2>&1`,
    `    $exitCode = if ($null -ne $global:LASTEXITCODE) { [int]$global:LASTEXITCODE } else { 0 }`,
    `    foreach ($line in $scriptOutput) { Write-HelperLog ("script: " + [string]$line) }`,
    `    Write-HelperLog "restart script exited with code $exitCode"`,
    `    exit $exitCode`,
    `} catch {`,
    `    Write-HelperLog "restart script failed: $($_.Exception.Message)"`,
    `    throw`,
    `}`,
  ].join("\r\n");
}

interface HelperLaunchResult {
  stdout: string;
  stderr: string;
}

function waitForHelperLaunch(child: RestartSchedulerChildProcess): Promise<HelperLaunchResult> {
  return new Promise((resolve, reject) => {
    const stdout: string[] = [];
    const stderr: string[] = [];
    let settled = false;
    const timeout = setTimeout(() => {
      child.kill?.();
      rejectOnce(new Error(`restart helper launcher timed out after ${OuterLaunchTimeoutMs} ms`));
    }, OuterLaunchTimeoutMs);

    const rejectOnce = (error: Error) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      reject(error);
    };

    const resolveOnce = (output: HelperLaunchResult) => {
      if (settled) {
        return;
      }
      settled = true;
      clearTimeout(timeout);
      resolve(output);
    };

    child.stdout?.on("data", (chunk) => stdout.push(String(chunk)));
    child.stderr?.on("data", (chunk) => stderr.push(String(chunk)));

    child.once("error", (error) => {
      rejectOnce(new Error(`failed to launch restart helper: ${error.message}`));
    });

    child.once("exit", (code, signal) => {
      const output = {
        stdout: stdout.join(""),
        stderr: stderr.join(""),
      };

      if (code !== 0) {
        rejectOnce(
          new Error(
            [
              `restart helper launcher exited with code ${code ?? "null"}${signal ? ` signal ${signal}` : ""}`,
              formatProcessOutput("stdout", output.stdout),
              formatProcessOutput("stderr", output.stderr),
            ].join("\n"),
          ),
        );
        return;
      }

      resolveOnce(output);
    });
  });
}

function resolveRepoRoot(): string {
  const moduleDir = path.dirname(fileURLToPath(import.meta.url));
  return path.resolve(moduleDir, "..", "..");
}

function normalizePort(value: string | undefined): number {
  const port = Number.parseInt(value ?? "", 10);
  return Number.isFinite(port) && port > 0 ? port : 8787;
}

function formatProcessOutput(label: string, output: string): string {
  const trimmed = output.trim();
  return `${label}: ${trimmed.length > 0 ? trimmed : "<empty>"}`;
}

function quotePowerShell(value: string): string {
  return `'${value.replace(/'/g, "''")}'`;
}
