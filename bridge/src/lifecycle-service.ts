import type { FastifyRequest } from "fastify";
import type { BridgeLifecycleController, SessionSocket } from "./app-context.js";
import type { BridgeEvent, BridgeLifecycleState } from "./types.js";

interface CreateBridgeLifecycleControllerOptions {
  bridgeVersion: string;
  bridgeStartedAt: string;
}

export function createBridgeLifecycleController(
  options: CreateBridgeLifecycleControllerOptions,
): BridgeLifecycleController {
  const sessionSockets = new Map<string, Set<SessionSocket>>();
  let drainStartedAt: string | null = null;
  let drainReason: string | null = null;
  let drainGraceMs: number | null = null;

  return {
    isDraining,
    beginDrain,
    cancelDrain,
    buildLifecycleState,
    broadcastLifecycle,
    attachSessionSocket,
    detachSessionSocket,
  };

  function isDraining(): boolean {
    return drainStartedAt != null;
  }

  function beginDrain(reason?: string, graceMs?: number): BridgeLifecycleState {
    drainStartedAt = new Date().toISOString();
    drainReason = reason ?? "bridge restart requested";
    drainGraceMs = normalizeDrainGraceMs(graceMs);
    return buildLifecycleState();
  }

  function cancelDrain(): BridgeLifecycleState {
    drainStartedAt = null;
    drainReason = null;
    drainGraceMs = null;
    return buildLifecycleState();
  }

  function buildLifecycleState(): BridgeLifecycleState {
    return {
      phase: isDraining() ? "restarting" : "running",
      draining: isDraining(),
      reason: drainReason,
      startedAt: options.bridgeStartedAt,
      drainStartedAt,
      drainGraceMs,
      bridgeVersion: options.bridgeVersion,
    };
  }

  function broadcastLifecycle(): void {
    const state = buildLifecycleState();
    for (const [sessionId, sockets] of sessionSockets.entries()) {
      const payload = JSON.stringify(buildBridgeLifecycleEvent(sessionId, state));
      for (const socket of sockets) {
        try {
          socket.send(payload);
        } catch {
          socket.close();
        }
      }
    }
  }

  function attachSessionSocket(sessionId: string, socket: SessionSocket): void {
    const socketSet = sessionSockets.get(sessionId) ?? new Set<SessionSocket>();
    socketSet.add(socket);
    sessionSockets.set(sessionId, socketSet);
  }

  function detachSessionSocket(sessionId: string, socket: SessionSocket): void {
    const socketSet = sessionSockets.get(sessionId);
    socketSet?.delete(socket);
    if (socketSet && socketSet.size === 0) {
      sessionSockets.delete(sessionId);
    }
  }
}

export function buildBridgeLifecycleEvent(
  sessionId: string,
  state: BridgeLifecycleState,
): BridgeEvent {
  return {
    type: "bridge.lifecycle",
    sessionId,
    timestamp: new Date().toISOString(),
    data: {
      phase: state.phase,
      reason: state.reason,
      graceMs: state.drainGraceMs,
      bridgeVersion: state.bridgeVersion,
      bridgeStartedAt: state.startedAt,
    },
  };
}

export function isLoopbackRequest(request: FastifyRequest): boolean {
  const candidates = [
    request.ip,
    request.socket.remoteAddress,
  ];
  return candidates.some((candidate) => isLoopbackAddress(candidate));
}

function normalizeDrainGraceMs(value: number | undefined): number {
  if (!Number.isFinite(value)) {
    return 2_000;
  }

  const normalized = value ?? 2_000;
  return Math.max(0, Math.min(15_000, normalized));
}

function isLoopbackAddress(value: string | undefined): boolean {
  if (!value) {
    return false;
  }

  const normalized = value.trim().toLowerCase().replace(/^::ffff:/, "");
  return normalized === "127.0.0.1" || normalized === "::1" || normalized === "localhost";
}
