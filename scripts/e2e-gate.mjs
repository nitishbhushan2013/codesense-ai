#!/usr/bin/env node
/**
 * E2E enforcement gate (Claude Code PreToolUse hook).
 *
 * Fires before a `git commit`. Ensures the Playwright E2E suite is green before a
 * commit (i.e. before a story is marked done) is allowed through. If the app
 * servers are down it starts them, waits for readiness, then runs the suite.
 *
 * Wiring (in .claude/settings.json):
 *   "hooks": { "PreToolUse": [ { "matcher": "Bash|PowerShell",
 *     "hooks": [ { "type": "command", "command": "node scripts/e2e-gate.mjs" } ] } ] }
 *
 * Exit codes (PreToolUse contract):
 *   0  -> allow the tool call (not a commit, or suite passed)
 *   2  -> BLOCK the tool call; stderr is shown to Claude as the reason
 */

import net from "node:net";
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawn, spawnSync } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(__dirname, "..");
const BACKEND = path.join(REPO, "backend", "codesense-backend");
const FRONTEND = path.join(REPO, "frontend", "codesense-frontend");
const LOG_DIR = path.join(REPO, ".e2e-gate");
const isWin = process.platform === "win32";

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const block = (msg) => {
  process.stderr.write(`\n[e2e-gate] BLOCKED: ${msg}\n`);
  process.exit(2);
};
const allow = (msg) => {
  if (msg) process.stderr.write(`[e2e-gate] ${msg}\n`);
  process.exit(0);
};

function portOpen(port, host = "127.0.0.1", timeout = 1000) {
  return new Promise((res) => {
    const sock = new net.Socket();
    let done = false;
    const finish = (v) => {
      if (!done) {
        done = true;
        sock.destroy();
        res(v);
      }
    };
    sock.setTimeout(timeout);
    sock.once("connect", () => finish(true));
    sock.once("timeout", () => finish(false));
    sock.once("error", () => finish(false));
    sock.connect(port, host);
  });
}

function httpStatus(url) {
  return new Promise((res) => {
    const req = http.get(url, (r) => {
      r.resume();
      res(r.statusCode || 0);
    });
    req.on("error", () => res(0));
    req.setTimeout(2500, () => {
      req.destroy();
      res(0);
    });
  });
}

async function waitFor(check, { timeoutMs, intervalMs = 2000, label }) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await check()) return true;
    await sleep(intervalMs);
  }
  process.stderr.write(`[e2e-gate] timed out waiting for ${label}\n`);
  return false;
}

function startDetached(command, cwd, logName) {
  fs.mkdirSync(LOG_DIR, { recursive: true });
  const out = fs.openSync(path.join(LOG_DIR, logName), "a");
  const child = spawn(command, {
    cwd,
    detached: true,
    stdio: ["ignore", out, out],
    shell: true,
    windowsHide: true,
  });
  child.unref();
}

// --- read the hook payload from stdin -------------------------------------
function readStdin() {
  return new Promise((res) => {
    let data = "";
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", (c) => (data += c));
    process.stdin.on("end", () => res(data));
    // If nothing is piped, don't hang.
    setTimeout(() => res(data), 800);
  });
}

function isCommit(payload) {
  try {
    const json = JSON.parse(payload);
    const tool = json.tool_name || json.toolName;
    if (tool !== "Bash" && tool !== "PowerShell") return false;
    const cmd = (json.tool_input || json.toolInput || {}).command || "";
    // a real commit, not `git commit -h`/`--help`
    return /\bgit\b[^\n]*\bcommit\b/.test(cmd) && !/--help|\s-h(\s|$)/.test(cmd);
  } catch {
    return false;
  }
}

async function main() {
  const payload = await readStdin();
  if (!isCommit(payload)) allow(); // not a commit -> allow silently

  process.stderr.write("[e2e-gate] git commit detected — running E2E gate...\n");

  // 1) Postgres must be up (we can't reliably auto-start a DB service).
  if (!(await portOpen(5432))) {
    block(
      "PostgreSQL is not reachable on localhost:5432. Start the database, then commit again.",
    );
  }

  // 2) Backend on 8080 — start if down.
  if (!(await portOpen(8080))) {
    process.stderr.write("[e2e-gate] backend down — starting Spring Boot...\n");
    startDetached(
      isWin ? "mvnw.cmd spring-boot:run" : "./mvnw spring-boot:run",
      BACKEND,
      "backend.log",
    );
    const ready = await waitFor(
      async () => (await httpStatus("http://localhost:8080/api/auth/me")) > 0,
      { timeoutMs: 150000, label: "backend :8080" },
    );
    if (!ready)
      block(
        "Backend failed to come up on :8080 within 150s. See .e2e-gate/backend.log",
      );
  }

  // 3) Frontend on 3000 — start if down.
  if (!(await portOpen(3000))) {
    process.stderr.write("[e2e-gate] frontend down — starting Next.js...\n");
    startDetached(isWin ? "npm.cmd run dev" : "npm run dev", FRONTEND, "frontend.log");
    const ready = await waitFor(
      async () => (await httpStatus("http://localhost:3000/")) === 200,
      { timeoutMs: 90000, label: "frontend :3000" },
    );
    if (!ready)
      block(
        "Frontend failed to come up on :3000 within 90s. See .e2e-gate/frontend.log",
      );
  }

  // 4) Run the suite.
  process.stderr.write("[e2e-gate] servers up — running `npm run test:e2e`...\n");
  const res = spawnSync(isWin ? "npm.cmd" : "npm", ["run", "test:e2e"], {
    cwd: FRONTEND,
    stdio: ["ignore", "inherit", "inherit"],
    shell: true,
  });

  if (res.status === 0) {
    allow("E2E suite passed — commit allowed.");
  }
  block(
    "Playwright E2E suite failed. Fix the failing scenarios (or add missing ones) before committing. Run `npm run test:e2e` in frontend/codesense-frontend to reproduce.",
  );
}

main().catch((e) => block(`gate crashed: ${e?.message || e}`));
