#!/usr/bin/env node
// session-log.mjs — Stop hook for Antigravity IDE.
// Appends session summary and touched files to .agents/outputs/YYYY-MM-DD-session-<id>.md.
// Always exits 0, outputs {} to stdout.

import { readFileSync, appendFileSync, existsSync, mkdirSync } from "node:fs";
import { resolve, relative, sep, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");

let input = {};
try { input = JSON.parse(readFileSync(0, "utf8") || "{}"); } catch {
  process.stdout.write("{}\n");
  process.exit(0);
}

const sid = String(input.session_id ?? input.sessionId ?? "unknown").slice(0, 8);
const now = new Date();
const day = now.toISOString().slice(0, 10);
const outDir = join(ROOT, ".agents", "outputs");
const file = join(outDir, `${day}-session-${sid}.md`);

const edited = new Set();
let lastText = "";
let lastUserIdx = -1;
const lines = [];
const transcript = input.transcript_path ?? input.transcriptPath;
try {
  if (transcript && existsSync(transcript))
    lines.push(...readFileSync(transcript, "utf8").split("\n").filter(Boolean));
} catch { /* transcript unavailable */ }

const msgs = [];
for (const l of lines) { try { msgs.push(JSON.parse(l)); } catch { /* skip */ } }
const isHumanTurn = (m) => {
  if (m.type !== "user" && m.source !== "USER_EXPLICIT") return false;
  const c = m.content ?? m.message?.content;
  if (typeof c === "string") return true;
  return Array.isArray(c) && c.some((x) => x.type === "text") && !c.some((x) => x.type === "tool_result");
};
msgs.forEach((m, i) => { if (isHumanTurn(m)) lastUserIdx = i; });

for (const m of msgs.slice(lastUserIdx + 1)) {
  if (m.type !== "assistant" && m.type !== "PLANNER_RESPONSE") continue;
  const toolCalls = m.tool_calls ?? [];
  for (const tc of toolCalls) {
    if (/^(replace_file_content|write_to_file|multi_replace_file_content|Edit|Write)$/.test(tc.name ?? tc.function?.name)) {
      const args = tc.args ?? tc.function?.arguments ?? {};
      const target = args.TargetFile ?? args.targetFile ?? args.file_path ?? args.path;
      if (target) edited.add(relative(ROOT, resolve(target)).split(sep).join("/"));
    }
  }
  const content = m.content ?? m.message?.content ?? [];
  if (typeof content === "string") lastText = content;
  else if (Array.isArray(content)) {
    for (const c of content) {
      if (c.type === "text" && c.text?.trim()) lastText = c.text.trim();
    }
  }
}

const summary = lastText.split("\n").filter(Boolean).slice(0, 3).join(" ").slice(0, 300);
const entry = [
  `## ${now.toISOString().replace("T", " ").slice(0, 19)} UTC`,
  edited.size ? `- files: ${[...edited].map((f) => `\`${f}\``).join(", ")}` : "- files: (none)",
  summary ? `- said: ${summary}` : "- said: (no text)",
  "",
].join("\n");

try {
  mkdirSync(outDir, { recursive: true });
  if (!existsSync(file)) appendFileSync(file, `# Session ${sid} — ${day}\n\n`);
  appendFileSync(file, entry);
} catch { /* logging is best-effort */ }

process.stdout.write("{}\n");
process.exit(0);
