import path from "node:path";
import type { BridgeSecurityConfig, BridgeSecurityState } from "./types.js";

const ALLOWED_CWDS_ENV = "CODEX_MOBILE_ALLOWED_CWDS";

export interface AuthorizationResult {
  ok: boolean;
  message?: string;
}

export interface CwdValidationResult {
  ok: boolean;
  cwd?: string;
  error?: "invalid-cwd" | "cwd-not-allowed";
  message?: string;
}

export function resolveBridgeSecurityConfig(env: NodeJS.ProcessEnv = process.env): BridgeSecurityConfig {
  const token = env.CODEX_MOBILE_AUTH_TOKEN?.trim() || null;
  const allowedCwds = parseAllowedCwds(env[ALLOWED_CWDS_ENV]);
  return { token, allowedCwds };
}

export function buildBridgeSecurityState(config: BridgeSecurityConfig): BridgeSecurityState {
  return {
    tokenAuthEnabled: config.token !== null,
    cwdWhitelistEnabled: config.allowedCwds.length > 0,
  };
}

export function authorizeApiRequest(
  authorizationHeader: string | string[] | undefined,
  config: BridgeSecurityConfig,
): AuthorizationResult {
  if (config.token === null) {
    return { ok: true };
  }

  if (!authorizationHeader) {
    return { ok: false, message: "missing bearer token" };
  }

  const headerValue = Array.isArray(authorizationHeader) ? authorizationHeader[0] : authorizationHeader;
  const [scheme, token] = headerValue.split(/\s+/, 2);
  if (scheme !== "Bearer" || !token) {
    return { ok: false, message: "invalid bearer token" };
  }

  return token === config.token ? { ok: true } : { ok: false, message: "invalid bearer token" };
}

export function validateSessionCwd(rawCwd: string, config: BridgeSecurityConfig): CwdValidationResult {
  if (config.allowedCwds.length === 0) {
    return { ok: true, cwd: rawCwd };
  }

  const cwd = rawCwd.trim();
  if (!path.isAbsolute(cwd)) {
    return {
      ok: false,
      error: "invalid-cwd",
      message: "cwd must be an absolute path when CODEX_MOBILE_ALLOWED_CWDS is configured",
    };
  }

  const normalizedCwd = normalizePathForComparison(cwd);
  const allowed = config.allowedCwds.some((allowedCwd) => isSameOrChildPath(normalizedCwd, allowedCwd));
  if (!allowed) {
    return {
      ok: false,
      error: "cwd-not-allowed",
      message: "cwd is outside the allowed directories",
    };
  }

  return { ok: true, cwd: path.normalize(cwd) };
}

function parseAllowedCwds(rawValue: string | undefined): string[] {
  if (!rawValue?.trim()) {
    return [];
  }

  return rawValue
    .split(";")
    .map((value) => value.trim())
    .filter(Boolean)
    .map((value) => {
      if (!path.isAbsolute(value)) {
        throw new Error(`${ALLOWED_CWDS_ENV} contains a non-absolute path: ${value}`);
      }

      return normalizePathForComparison(value);
    });
}

function normalizePathForComparison(value: string): string {
  const resolved = path.normalize(path.resolve(value));
  const root = path.parse(resolved).root;
  const trimmed = resolved === root ? resolved : resolved.replace(/[\\/]+$/, "");
  return process.platform === "win32" ? trimmed.toLowerCase() : trimmed;
}

function isSameOrChildPath(candidate: string, allowedRoot: string): boolean {
  if (candidate === allowedRoot) {
    return true;
  }

  const relative = path.relative(allowedRoot, candidate);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}
