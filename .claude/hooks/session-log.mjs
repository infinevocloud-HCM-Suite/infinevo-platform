#!/usr/bin/env node
// session-log.mjs — Stop hook.
// When the model finishes a turn, append one entry to agents/outputs/YYYY-MM-DD-session-<id>.md:
// timestamp, files edited/written this turn (from the transcript), and the first lines of the
// final assistant message. Gives a durable, greppable trail of what each session touched.
// Never blocks: always exits 0.

import { readFileSync, appendFileSync, existsSync, mkdirSync } from "node:fs";
import { resolve, relative, sep, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(fileURLToPath(import.meta.url), "..", "..", "..");

let input = {};
try { input = JSON.parse(readFileSync(0, "utf8") || "{}"); } catch { process.exit(0); }
if (input.stop_hook_active) process.exit(0); // avoid loops

const sid = String(input.session_id ?? "unknown").slice(0, 8);
const now = new Date();
const day = now.toISOString().slice(0, 10);
const outDir = join(ROOT, "agents", "outputs");
const file = join(outDir, `${day}-session-${sid}.md`);

const edited = new Set();
let lastText = "";
let lastUserIdx = -1;
const lines = [];
try {
  if (input.transcript_path && existsSync(input.transcript_path))
    lines.push(...readFileSync(input.transcript_path, "utf8").split("\n").filter(Boolean));
} catch { /* transcript unavailable */ }

const msgs = [];
for (const l of lines) { try { msgs.push(JSON.parse(l)); } catch { /* skip */ } }
const isHumanTurn = (m) => {
  if (m.type !== "user") return false;
  const c = m.message?.content;
  if (typeof c === "string") return true;
  return Array.isArray(c) && c.some((x) => x.type === "text") && !c.some((x) => x.type === "tool_result");
};
msgs.forEach((m, i) => { if (isHumanTurn(m)) lastUserIdx = i; });

for (const m of msgs.slice(lastUserIdx + 1)) {
  if (m.type !== "assistant") continue;
  const content = m.message?.content ?? [];
  for (const c of Array.isArray(content) ? content : []) {
    if (c.type === "tool_use" && /^(Edit|Write|MultiEdit)$/.test(c.name) && c.input?.file_path)
      edited.add(relative(ROOT, resolve(c.input.file_path)).split(sep).join("/"));
    if (c.type === "text" && c.text?.trim()) lastText = c.text.trim();
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
process.exit(0);
