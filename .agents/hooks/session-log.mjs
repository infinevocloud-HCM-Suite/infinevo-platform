#!/usr/bin/env node
// session-log.mjs — Antigravity Stop hook.
// Records session summary and files modified to .agents/outputs/.

import { readFileSync, appendFileSync, existsSync, mkdirSync } from "node:fs";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");
const OUTPUTS_DIR = join(ROOT, ".agents", "outputs");

let input = {};
try {
  const raw = readFileSync(0, "utf8");
  if (raw && raw.trim()) {
    input = JSON.parse(raw);
  }
} catch {
  console.log(JSON.stringify({}));
  process.exit(0);
}

const transcriptPath = input?.transcriptPath;
const sessionId = input?.conversationId ?? Date.now();
const dateStr = new Date().toISOString().slice(0, 10);

if (!existsSync(OUTPUTS_DIR)) {
  mkdirSync(OUTPUTS_DIR, { recursive: true });
}

const touchedFiles = new Set();

if (transcriptPath && existsSync(transcriptPath)) {
  try {
    const lines = readFileSync(transcriptPath, "utf8").split("\n");
    for (const line of lines) {
      if (!line.trim()) continue;
      try {
        const item = JSON.parse(line);
        const calls = item?.tool_calls ?? [];
        for (const tc of calls) {
          const fn = tc?.function?.name ?? tc?.name;
          const a = typeof tc?.function?.arguments === "string" ? JSON.parse(tc.function.arguments) : (tc?.args ?? tc?.tool_input ?? {});
          if (fn === "replace_file_content" || fn === "write_to_file" || fn === "multi_replace_file_content") {
            const f = a.TargetFile ?? a.filePath ?? a.path;
            if (f) touchedFiles.add(f);
          }
        }
      } catch {}
    }
  } catch {}
}

const logFile = join(OUTPUTS_DIR, `${dateStr}-session-active.md`);
const entry = `
### Session ${sessionId} — ${new Date().toISOString()}
- **Touched Files (${touchedFiles.size})**:
${Array.from(touchedFiles).map(f => `  - \`${f}\``).join("\n") || "  - None"}
`;

try {
  appendFileSync(logFile, entry, "utf8");
} catch {}

console.log(JSON.stringify({}));
process.exit(0);
