import { EventEmitter } from "node:events";
import { PassThrough } from "node:stream";
import { afterEach, describe, expect, test, vi } from "vitest";
import {
  createBridgeRestartScheduler,
  type RestartSchedulerChildProcess,
  type RestartSchedulerSpawn,
} from "../src/restart-scheduler.js";

class FakeRestartLauncher extends EventEmitter implements RestartSchedulerChildProcess {
  readonly stdout = new PassThrough();
  readonly stderr = new PassThrough();
  readonly kill = vi.fn(() => true);
}

describe("createBridgeRestartScheduler", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
    vi.useRealTimers();
  });

  test("starts a PowerShell launcher that confirms the background restart helper", async () => {
    vi.stubEnv("HOST", "127.0.0.1");
    vi.stubEnv("PORT", "9999");
    vi.stubEnv("CODEX_MOBILE_RUNNER", "mock-runner");

    const launcher = new FakeRestartLauncher();
    const spawnProcess = vi.fn<RestartSchedulerSpawn>(() => launcher);
    const scheduler = createBridgeRestartScheduler({ spawn: spawnProcess });

    const scheduled = scheduler.scheduleRestart({
      reason: "mobile settings restart",
      graceMs: 1200,
    });

    expect(spawnProcess).toHaveBeenCalledTimes(1);
    const [command, args, options] = spawnProcess.mock.calls[0];
    expect(command).toBe("powershell.exe");
    expect(args).toEqual([
      "-NoProfile",
      "-ExecutionPolicy",
      "Bypass",
      "-Command",
      expect.any(String),
    ]);
    expect(options).toMatchObject({
      stdio: ["ignore", "pipe", "pipe"],
      windowsHide: true,
    });

    const launcherCommand = String(args[4]);
    expect(launcherCommand).toContain("Start-Process");
    expect(launcherCommand).toContain("restart-helper.log");
    expect(launcherCommand).toContain("HELPER_STARTED");
    expect(launcherCommand).toContain("mobile settings restart");

    const encodedCommand = extractEncodedCommand(launcherCommand);
    const helperCommand = Buffer.from(encodedCommand, "base64").toString("utf16le");
    expect(helperCommand).toContain("Start-Sleep -Milliseconds 1000");
    expect(helperCommand).toContain("restart-bridge-background.ps1");
    expect(helperCommand).toContain("-HostAddress '127.0.0.1'");
    expect(helperCommand).toContain("-Port 9999");
    expect(helperCommand).toContain("-Runner 'mock-runner'");
    expect(helperCommand).toContain("$scriptOutput = &");
    expect(helperCommand).toContain("foreach ($line in $scriptOutput)");
    expect(helperCommand).not.toContain("-SkipBuild");

    launcher.stdout.write("HELPER_STARTED pid=1234 log=D:\\workspace\\codex-mobile\\.logs\\bridge\\restart-helper.log\r\n");
    launcher.emit("exit", 0, null);

    await expect(scheduled).resolves.toMatchObject({
      phase: "scheduled",
      message: expect.stringContaining("mobile settings restart"),
    });
  });

  test("rejects when PowerShell spawn fails", async () => {
    const launcher = new FakeRestartLauncher();
    const spawnProcess = vi.fn<RestartSchedulerSpawn>(() => launcher);
    const scheduler = createBridgeRestartScheduler({ spawn: spawnProcess });

    const scheduled = scheduler.scheduleRestart({
      reason: "mobile settings restart",
      graceMs: 1200,
    });

    launcher.emit("error", new Error("spawn powershell.exe ENOENT"));

    await expect(scheduled).rejects.toThrow("failed to launch restart helper: spawn powershell.exe ENOENT");
  });

  test("rejects when the launcher exits non-zero", async () => {
    const launcher = new FakeRestartLauncher();
    const spawnProcess = vi.fn<RestartSchedulerSpawn>(() => launcher);
    const scheduler = createBridgeRestartScheduler({ spawn: spawnProcess });

    const scheduled = scheduler.scheduleRestart({
      reason: "mobile settings restart",
      graceMs: 1200,
    });

    launcher.stderr.write("Start-Process failed");
    launcher.emit("exit", 1, null);

    await expect(scheduled).rejects.toThrow("restart helper launcher exited with code 1");
    await expect(scheduled).rejects.toThrow("stderr: Start-Process failed");
  });

  test("rejects when the launcher exits without helper confirmation", async () => {
    const launcher = new FakeRestartLauncher();
    const spawnProcess = vi.fn<RestartSchedulerSpawn>(() => launcher);
    const scheduler = createBridgeRestartScheduler({ spawn: spawnProcess });

    const scheduled = scheduler.scheduleRestart({
      reason: "mobile settings restart",
      graceMs: 1200,
    });

    launcher.stdout.write("launcher exited without starting helper");
    launcher.emit("exit", 0, null);

    await expect(scheduled).rejects.toThrow("restart helper launch did not confirm Start-Process success");
  });

  test("rejects and kills the launcher when helper confirmation times out", async () => {
    vi.useFakeTimers();

    const launcher = new FakeRestartLauncher();
    const spawnProcess = vi.fn<RestartSchedulerSpawn>(() => launcher);
    const scheduler = createBridgeRestartScheduler({ spawn: spawnProcess });

    const scheduled = scheduler.scheduleRestart({
      reason: "mobile settings restart",
      graceMs: 1200,
    });
    const rejection = expect(scheduled).rejects.toThrow("restart helper launcher timed out after 10000 ms");

    await vi.advanceTimersByTimeAsync(10_000);

    await rejection;
    expect(launcher.kill).toHaveBeenCalledTimes(1);
  });
});

function extractEncodedCommand(command: string): string {
  const match = /'-EncodedCommand', '([^']+)'/.exec(command);
  if (!match) {
    throw new Error(`EncodedCommand not found in command: ${command}`);
  }

  return match[1];
}
