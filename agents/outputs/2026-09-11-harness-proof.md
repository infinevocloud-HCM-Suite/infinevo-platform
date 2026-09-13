# Harness hook proof — 2026-09-11

Task 15 of the harness build: show the hooks working **live** in a Claude Code session,
not just in a dry run. All output below is pasted verbatim from the session.

## Wiring (`.claude/settings.json`)

| Event | Matcher | Script | Behaviour |
|---|---|---|---|
| PreToolUse | `Edit\|Write` | `.claude/hooks/guard-edit.mjs` | exit 2 + reason on `docs/**`, `application*.properties`, `.env*`; else allow |
| PostToolUse | `Edit\|Write` | `.claude/hooks/verify-app.mjs` | maps file → app, runs only that app's compile/lint; PASS/FAIL/SKIP back to the model; never blocks; 20 s debounce per app |
| Stop | — | `.claude/hooks/session-log.mjs` | appends files-touched + last message to `agents/outputs/YYYY-MM-DD-session-<id>.md` |

## (a) Edit on `docs/ARCHITECTURE.md` → denied

```
PreToolUse:Edit hook error: [node "$CLAUDE_PROJECT_DIR/.claude/hooks/guard-edit.mjs"]: guard-edit: BLOCKED docs/ARCHITECTURE.md
  - docs/ is read-only during feature work; propose changes via the sync-docs skill and get founder approval
```
File afterwards: first line still `# ARCHITECTURE — Infinevo Cloud HRMS + Payroll Suite` — unchanged.

## (b) Write `HRMS_Backend/scratch.txt` → allowed, HRMS compile ran

```
PostToolUse:Write hook additional context: verify-app: PASS HRMS_Backend compile (22.5s) — clean
```
Only HRMS_Backend was compiled; no other app was touched (verify-app is scoped by the first
path segment).

## (c) Cleanup

`rm -f HRMS_Backend/scratch.txt` → removed. `git -C HRMS_Backend status` shows only the
pre-existing wrapper changes (`mvnw`, `mvnw.cmd`, `.mvn/`, `CLAUDE.md`, and the tracked
`target/` artefacts — see build-baseline side effect 2).

## Dry-run matrix (before wiring, JSON piped on stdin)

| Hook | Input | Result |
|---|---|---|
| guard-edit | `docs/ARCHITECTURE.md` (absolute) | exit 2, BLOCKED |
| guard-edit | `docs/features/TEMPLATE.md` | exit 2, BLOCKED |
| guard-edit | `Payroll-Bend-SBoot/src/main/resources/application-prod.properties` | exit 2, BLOCKED |
| guard-edit | `Payroll-Fend-react/.env.production` | exit 2, BLOCKED |
| guard-edit | `HRMS_Backend/scratch.txt`, `agents/active-work.md`, `.claude/settings.json` | exit 0, allowed |
| guard-edit | malformed JSON | exit 0 (fails open — never blocks on its own bug) |
| verify-app | `HRMS_Backend/scratch.txt` | PASS HRMS_Backend compile (6.2s) |
| verify-app | `HRMS_Backend/pom.xml` 2 s later | SKIP debounced |
| verify-app | `Payroll-Bend-SBoot/pom.xml` | PASS compile (31.5s) via system Maven |
| verify-app | same, `MAVEN_HOME`/`JAVA_HOME` unset | PASS (auto-detected `C:\Tools\apache-maven-3.9.11`, JDK 21) |
| verify-app | `Payroll-Fend-react/src/App.js` | PASS lint — ✖ 833 problems (0 errors, 833 warnings) |
| verify-app | `HRMS_Frontend/src/App.jsx` | FAIL lint (exit 1) — pre-existing 350 errors, tail shown |
| verify-app | `agents/active-work.md` | SKIP not inside an app folder |
| session-log | live transcript | entry written with 4 files + first assistant line |

## Findings surfaced while building (not fixed — proposed GAP entries)

| Proposed ID | Finding | Evidence |
|---|---|---|
| DEBT-034 | `HRMS_Backend/target/` (134 files) is tracked in git; every compile dirties 125 tracked files | `git -C HRMS_Backend ls-files target \| wc -l` → 134 |
| DEBT-035 | `Payroll-Bend-SBoot/mvnw.cmd` fails when `%USERPROFILE%` contains a space: `'C:\Users\S' is not recognized … Cannot start maven from wrapper`. Bash `mvnw` works. verify-app sidesteps it by preferring system Maven | dry-run output above |
| DEBT-036 | HRMS_Frontend lint has 350 errors (314 `no-unused-vars`), so verify-app reports FAIL on every HRMS_Frontend edit until cleared | build-baseline |

These need `sync-docs` (with approval) to land in `docs/GAP_INVENTORY.md` — the guard hook
now blocks direct edits, by design.

## Not yet proven
- **Stop hook live**: fires only when a turn ends after wiring. Check
  `agents/outputs/2026-09-11-session-0bf71874.md` gains a new entry after this reply.
