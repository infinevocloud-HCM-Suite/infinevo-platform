# Antigravity IDE Development Harness — Specification & Migration Prompt

> **Prompt Purpose:**  
> Use this document as an executable specification and AI prompt to generate, configure, and install the complete development harness for **Antigravity IDE** in the `infinevo-platform` repository. It provides a direct 1:1 mapping from the existing Claude Code harness (`CLAUDE.md`, `.claude/`) to the Antigravity IDE customization architecture (`GEMINI.md`, `.agents/`).

---

## Part 1: Architecture & Mental Model Mapping

The `infinevo-platform` uses a strict, machine-enforced development harness to manage a multi-module enterprise HCM rewrite replacing four frozen legacy systems. Below is the mapping between the Claude Code harness and the Antigravity IDE harness:

| Claude Code Construct | Antigravity IDE Equivalent | Purpose & Behavior in Antigravity |
| :--- | :--- | :--- |
| `CLAUDE.md` (Repository Root) | `GEMINI.md` / `AGENTS.md` | Root hierarchical workspace instructions automatically loaded on every turn. Contains project context, 4 pillars, stack facts, hard rules, and working agreements. |
| `.claude/` | `.agents/` | Primary project customization root recognized automatically by Antigravity IDE. |
| `.claude/settings.json` (hooks) | `.agents/hooks.json` | JSON configuration defining lifecycle hooks (`PreToolUse`, `PostToolUse`, `PreInvocation`, `PostInvocation`, `Stop`). |
| Hook Tool: `Edit\|Write` | `replace_file_content\|write_to_file\|multi_replace_file_content` | Antigravity tool names for modifying or creating files. |
| Hook Tool: `Bash` | `run_command` | Antigravity tool name for executing shell commands. |
| Hook Exit Code (e.g. Exit 2) | JSON stdout: `{"decision": "deny", "reason": "..."}` | Antigravity hook protocol: hooks inspect JSON on `stdin` and output JSON on `stdout`. |
| `.claude/hooks/guard-edit.mjs` | `.agents/hooks/guard-edit.mjs` | `PreToolUse` hook blocking writes to `legacy/`, `docs/` (except current branch's ticket spec), `application*.properties`, and `.env*`. |
| `.claude/hooks/guard-merge.mjs` | `.agents/hooks/guard-merge.mjs` | `PreToolUse` hook blocking `git push` to `main` without a verified PASS receipt from `check-done.mjs`. |
| `.claude/hooks/verify-app.mjs` | `.agents/hooks/verify-app.mjs` | `PostToolUse` hook running debounced background compilation (Maven) or lint (npm) after code edits. |
| `.claude/hooks/session-log.mjs` | `.agents/hooks/session-log.mjs` | `Stop` hook appending session summary and touched files to `.agents/outputs/` using `transcriptPath`. |
| `.claude/agents/*.md` | `.agents/skills/` & `.agents/plugins/` subagent personas | Subagents (`explorer`, `implementer`, `reviewer`, `verifier`) defined with strict boundaries, progressive disclosure, and designated toolsets. |
| `.claude/skills/<name>/SKILL.md` | `.agents/skills/<name>/SKILL.md` | Workspace skills with YAML frontmatter (`name`, `description`). Invoked via slash commands (e.g., `/develop`, `/merge`) or triggered autonomously. |
| `.claude/scripts/check-done.mjs` | `.agents/scripts/check-done.mjs` | 8-gate definition-of-done validator writing signed receipts to `.agents/outputs/.merge-receipts/`. |
| `.claude/work/active-work.md` | `.agents/work/active-work.md` | Source-of-truth status tracking for in-flight tickets, next-up queue, and blockers. |
| `.claude/outputs/` | `.agents/outputs/` | Receipts, review findings, and verification logs. |

---

## Part 2: File Structure to Generate

Create the following file tree within the workspace root:

```text
infinevo-platform/
├── GEMINI.md                                    # Antigravity project root instructions
├── AGENTS.md                                    # Symlink or mirror to GEMINI.md
└── .agents/
    ├── hooks.json                               # Lifecycle hook declarations
    ├── hooks/
    │   ├── guard-edit.mjs                       # PreToolUse: Protect docs, legacy, env, props
    │   ├── guard-merge.mjs                      # PreToolUse: Protect main from unverified push
    │   ├── verify-app.mjs                       # PostToolUse: Debounced compile/lint feedback
    │   └── session-log.mjs                      # Stop: Session audit logging
    ├── scripts/
    │   ├── check-done.mjs                       # 8-gate definition of done & receipt issuer
    │   └── prune-outputs.mjs                    # Sweeper for orphaned outputs
    ├── work/
    │   ├── active-work.md                       # Active tickets & work queue
    │   └── active-work.template.md              # Template for active-work
    ├── outputs/
    │   ├── .gitkeep
    │   ├── .merge-receipts/
    │   └── .verify-cache/
    └── skills/
        ├── develop/SKILL.md                     # Autonomous 3-round build/test/verify/review loop
        ├── merge/SKILL.md                       # Definition-of-done check and squash merge to main
        ├── plan-feature/SKILL.md                # Spec generation from TEMPLATE.md, stops for approval
        ├── infra-task/SKILL.md                  # Infra/Data/Sec spec generation
        ├── review-spec/SKILL.md                 # Spec gatekeeper before founder review
        ├── review/SKILL.md                      # Read-only diff review against spec acceptance criteria
        ├── verify/SKILL.md                      # Independent build, test, lint, and boundary verifier
        ├── test/SKILL.md                        # Targeted test authoring for changed modules
        ├── analyze/SKILL.md                     # Deep investigation of frozen vs target architecture
        └── sync-docs/SKILL.md                   # Controlled documentation sync with approved diffs
```

---

## Part 3: Detailed Specifications for Core Files

### 1. `GEMINI.md` (Project Root Rule File)

Place `GEMINI.md` at the repository root `d:\Infinevoclouds\GEMINI.md`.

```markdown
# Infinevo Cloud

This is `infinevo-platform` — the repository where the unified HCM platform is built.
It holds the target-state design, the Antigravity agent harness, and the new codebase.

The four applications it replaces — two Spring Boot backends, two React frontends,
two databases, two auth systems — are **frozen snapshots in `legacy/`**. Read them,
port logic out of them, never edit them. See `legacy/README.md`.

## The repository, in four pillars

```
infinevo-platform/
│
├── docs/        THE DESIGN        target-state/ (where we are going, 12 documents)
│                                  target-state/features/ (one spec per ticket)
│                                  CONVENTIONS.md (rules new code must follow)
│
├── code/        THE CODE          backend/  shared core hrms payroll app worker migration
│                                  frontend/ src/{shell core hrms payroll shared}
│
├── infra/       HOW IT RUNS       azure/ (Bicep) · docker/ · keycloak/
│                                  .github/workflows/ - GitHub Actions workflows
│
├── legacy/      THE FROZEN SYSTEM the four applications being replaced
│                                  docs/ - how they work, read before porting
│
└── .agents/     THE HARNESS       hooks · skills · scripts · work/ · outputs/
```

**Each pillar is self-contained.**
- `docs/` and `code/` describe only the target state.
- Everything about the frozen system lives under `legacy/`.
- Rule: **If the path starts with `legacy/`, it describes what is being replaced, not what is being built.** The `guard-edit` hook blocks writes to all of it.
- `infra/` is new capability (Docker, Keycloak, Azure Bicep).
- `.github/workflows/` contains CI/CD pipelines.

| If you are | Read |
|---|---|
| New here | `CONTRIBUTING.md` |
| Building something | `docs/target-state/README.md`, then your ticket's spec |
| Asking how it works today | `legacy/docs/FEATURE_MAP.md`, then `legacy/` |
| Deploying or containerising | `infra/README.md` |

## Stack Facts

| App | Language / build | Auth | Database | Port |
|---|---|---|---|---|
| `HRMS_Backend` | Java **21** · Spring Boot 3.2.4 · Maven | custom JWT (`jjwt`) | MySQL `hrmstestdb` | 1010 |
| `HRMS_Frontend` | React 18 · **Vite** · MUI v6 | JWT via React Context | — | 5173 |
| `Payroll-Bend-SBoot` | Java **17** · Spring Boot 3.2.5 · Maven | **Keycloak** OAuth2 | MySQL `payrollDB` | 3032 dev / 3029 prod |
| `Payroll-Fend-react` | React 18 · **CRA** · Ant Design 5 · Redux | Keycloak SSO | — | 3000 |

Keycloak realm `HRMS`, client `react-app`. Active branches: `main`, `main`, `taxation`, `employee` (**not** `main` on the two Payroll repos).

## Hard Rules

1. **Founder approval before any implementation.** Write the plan, stop, wait.
2. **Read `.agents/work/active-work.md` before starting** any task.
3. **Never edit `docs/` or `*.properties` during feature work.** Docs change only through `sync-docs` with an approved diff. Enforced by the `guard-edit` hook.
4. **Flyway for migrations — never `ddl-auto`.** Both legacy backends ran `ddl-auto=update`; that is a known defect, not a pattern to copy.
5. **Upstream remotes are read-only.** The four origin repos are production source. Never push to them. Changes flow one way: upstream → this repo.
6. **Repo state beats chat memory.** If conversation and repo disagree, repo is right. Verify before asserting.
7. **`tenant_id` is mandatory on every new entity and query** once the tenant column lands. No exceptions, including lookup tables.

## Where Things Are

### Target State — build against this
| Need | Read |
|---|---|
| Where we are going: decisions, work plan, build order | `docs/target-state/README.md` |
| Coding rules, `BigDecimal`, naming hazards | `docs/CONVENTIONS.md` |
| New feature spec | `docs/target-state/features/TEMPLATE.md` |
| Current direction, in-flight, frozen | `.agents/work/active-work.md` |
| Setup and the developer loop | `CONTRIBUTING.md` |

### Legacy — read for reference only
| Need | Read |
|---|---|
| System shape, ports, cross-service flow | `legacy/docs/ARCHITECTURE.md` |
| Tables and columns (all 131, old schema) | `legacy/docs/DB_SCHEMA.md` |
| Which files implement a feature in the 4 apps | `legacy/docs/FEATURE_MAP.md` |
| Known defects and debt in the frozen system | `legacy/docs/GAP_INVENTORY.md` |
| The frozen source itself | `legacy/` — see `legacy/README.md` |

## Working Agreements

- Cite `file:line` for any claim about the code.
- No tests exist in legacy (DEBT-003). Add tests for what you change; verify them with evidence.
- Typos in legacy package names are real and load-bearing (`timeshhet/`, `leaveAndAttedance/`, `EmployyePortalContoller.java`). Do not silently rename them in `legacy/`. New target code uses correct spellings.
- **Answer briefly, and lead with a table — in chat and in every file you write.**
- A report file is an answer. Lead with one plain sentence per finding and `file:line` citations beneath it.
```

---

### 2. `.agents/hooks.json` (Hook Configuration)

Antigravity IDE executes hooks declared in `.agents/hooks.json`. Configure it to enforce the development harness guardrails:

```json
{
  "guard-edit": {
    "PreToolUse": [
      {
        "matcher": "replace_file_content|write_to_file|multi_replace_file_content",
        "hooks": [
          {
            "type": "command",
            "command": "node .agents/hooks/guard-edit.mjs",
            "timeout": 10
          }
        ]
      }
    ]
  },
  "guard-merge": {
    "PreToolUse": [
      {
        "matcher": "run_command",
        "hooks": [
          {
            "type": "command",
            "command": "node .agents/hooks/guard-merge.mjs",
            "timeout": 15
          }
        ]
      }
    ]
  },
  "verify-app": {
    "PostToolUse": [
      {
        "matcher": "replace_file_content|write_to_file|multi_replace_file_content",
        "hooks": [
          {
            "type": "command",
            "command": "node .agents/hooks/verify-app.mjs",
            "timeout": 300
          }
        ]
      }
    ]
  },
  "session-log": {
    "Stop": [
      {
        "type": "command",
        "command": "node .agents/hooks/session-log.mjs",
        "timeout": 15
      }
    ]
  }
}
```

---

### 3. Adapted Hook Implementations

#### A. `.agents/hooks/guard-edit.mjs`
Protects `docs/`, `legacy/`, `application*.properties`, and `.env*` files.
- Reads `stdin` JSON containing Antigravity's `toolCall` object:
  ```json
  {
    "toolCall": {
      "name": "replace_file_content",
      "args": {
        "TargetFile": "d:\\Infinevoclouds\\docs\\CONVENTIONS.md"
      }
    },
    "workspacePaths": ["d:\\Infinevoclouds"]
  }
  ```
- If an edit targets a protected path, outputs:
  ```json
  {
    "decision": "deny",
    "reason": "guard-edit: BLOCKED docs/CONVENTIONS.md - docs/ is read-only during feature work..."
  }
  ```
- If allowed, outputs:
  ```json
  {
    "decision": "allow"
  }
  ```
- **Special Exemption:** Permitted write for the branch's *own* approved feature spec: `docs/target-state/features/W-nn-*.md` where `W-nn` matches the active git branch name `W-nn-<slug>`.

#### B. `.agents/hooks/guard-merge.mjs`
Guards against pushes to `main`.
- Reads `stdin` JSON for `toolCall.name === "run_command"` and `toolCall.args.CommandLine`.
- Strips heredocs/quotes to prevent false positives.
- If `git push` targets `main` and unpushed commits contain changes in `code/` or `docs/`:
  - Inspects `.agents/outputs/.merge-receipts/`.
  - Checks if a valid receipt exists matching `HEAD^{tree}` with status `"PASS"`.
  - If no matching passing receipt exists, outputs:
    ```json
    {
      "decision": "deny",
      "reason": "guard-merge: BLOCKED push to main without passing check-done receipt..."
    }
    ```
  - Otherwise outputs `{"decision": "allow"}`.

#### C. `.agents/hooks/verify-app.mjs`
Provides continuous background feedback on code edits.
- Fires on `PostToolUse` for edit tools.
- Resolves edited path:
  - If inside `code/backend`, runs `mvn -q compile` (or `mvnw`).
  - If inside `code/frontend`, runs `npm run lint --silent`.
- Uses a 20-second debounce cache stored in `.agents/outputs/.verify-cache/`.
- Never blocks; writes `{}` to `stdout` and logs build/lint status to stdout/stderr.

#### D. `.agents/hooks/session-log.mjs`
Provides an audit log of agent sessions.
- Fires on the `Stop` event.
- Reads `transcriptPath` from stdin.
- Parses JSONL events, collects all files touched via `replace_file_content`, `write_to_file`, `multi_replace_file_content`, and appends an entry to `.agents/outputs/YYYY-MM-DD-session-<id>.md`.
- Outputs `{}`.

---

### 4. Definition of Done Script: `.agents/scripts/check-done.mjs`

Ported from `.claude/scripts/check-done.mjs` to work with `.agents/`.
Validates all 8 gates before any code can be merged to `main`:
1. **Branch names a ticket:** Must match `W-nn-<slug>` or `docs-<slug>`.
2. **Approved spec exists:** `docs/target-state/features/W-nn-<slug>.md` must have `Status: Approved`.
3. **No open High findings:** Scans `.agents/outputs/` for review/verify reports with unresolved `High` findings.
4. **`legacy/` untouched:** Verifies `git diff origin/main..HEAD` contains no files in `legacy/`.
5. **`docs/` changed only by a recognised route:** A `W-nn` branch touches only its own `features/W-nn-*` spec; a `docs-*` branch touches only `docs/`.
6. **`ddl-auto` set nowhere:** Greps for active `ddl-auto` properties across `code/backend/`.
7. **No floating-point money:** Checks that money/salary/amount fields use `BigDecimal` or `Money`.
8. **CI green for this commit:** Queries GitHub Actions for a green run on the HEAD commit.

When all 8 gates pass, writes a signed receipt:
`.agents/outputs/.merge-receipts/<branch-sanitized>.json`:
```json
{
  "branch": "W-06-flyway",
  "commit": "a1b2c3d...",
  "tree": "e5f6g7h...",
  "status": "PASS",
  "timestamp": "2026-09-21T12:00:00Z",
  "gates": 8
}
```

---

### 5. Antigravity Skills Specification (`.agents/skills/`)

Create 10 skills in `.agents/skills/<name>/SKILL.md`:

#### 1. `develop` (`.agents/skills/develop/SKILL.md`)
- **Description:** Build an approved ticket end to end — writes the code, runs test, verify, and review, fixes what they find, and stops after three rounds. Refuses to start without an approved spec.
- **Trigger:** `/develop W-nn`
- **Workflow:**
  1. Check `.agents/work/active-work.md`. Stop if ticket is not Approved.
  2. Round 1: Branch `W-nn-<slug>`. Implement one module at a time (`code/backend/<module>` or `code/frontend/src/<area>`). Cite `legacy/` `file:line` for ported logic.
  3. Invoke `/test W-nn`, `/verify W-nn`, `/review W-nn`.
  4. If findings exist: fix defects (`FIXED`), defer unrelated ones (`DEFERRED`), ask founder if disputed (`DISPUTED`).
  5. Repeat loop up to 3 rounds maximum. Round 4 never runs.
  6. On clean exit: report table of changed files and rounds used. Remind dev to run `/merge W-nn`.

#### 2. `merge` (`.agents/skills/merge/SKILL.md`)
- **Description:** Put a finished branch onto main. Runs the machine-checked definition of done first and refuses if any gate fails.
- **Trigger:** `/merge W-nn`
- **Workflow:**
  1. Run `node .agents/scripts/check-done.mjs`. If any gate fails, stop and report.
  2. Squash-merge branch onto `main` locally: `git merge --squash W-nn-<slug>`.
  3. Commit with structured summary (closes issue).
  4. Push to origin main (allowed by `guard-merge` due to passing receipt).
  5. Post-merge: verify main builds, confirm GitHub issue closed, update `.agents/work/active-work.md`, run `/sync-docs`, and prune outputs via `node .agents/scripts/prune-outputs.mjs`.

#### 3. `plan-feature` (`.agents/skills/plan-feature/SKILL.md`)
- **Description:** Turn a product ticket into a founder-reviewable spec using docs/target-state/features/TEMPLATE.md. Stops before any code is written.
- **Trigger:** `/plan-feature W-nn`
- **Workflow:**
  1. Read `.agents/work/active-work.md` and the ticket issue. Stop if ticket is blocked.
  2. Read `docs/target-state/09-build-order.md` §3 and `legacy/docs/GAP_INVENTORY.md`.
  3. Investigate legacy behavior (acting as explorer): trace controllers, services, queries, and table names from `legacy/docs/DB_SCHEMA.md`.
  4. Draft spec in `.agents/outputs/<date>-plan-<slug>.md` (NOT in `docs/`).
  5. Cite `legacy/` `file:line` for all ported logic.
  6. Run `/review-spec <draft-path>` to catch spec defects.
  7. Present plain English summary to founder with key decisions needed.
  8. **STOP and wait for founder approval.**

#### 4. `infra-task` (`.agents/skills/infra-task/SKILL.md`)
- **Description:** Spec and plan platform infrastructure, database, security, and CI/CD tasks (`skill-INFRA`, `skill-DATA`, `skill-SEC`). Stops before code is written.
- **Trigger:** `/infra-task W-nn`

#### 5. `review-spec` (`.agents/skills/review-spec/SKILL.md`)
- **Description:** Independent checklist inspection of a drafted spec before the founder sees it.
- **Trigger:** `/review-spec <spec-path>`
- **Checks:** Valid citations, complete acceptance table, Flyway migration names, tenant isolation, zero `ddl-auto`, no cross-module dependencies.

#### 6. `review` (`.agents/skills/review/SKILL.md`)
- **Description:** Independent code review of a ticket branch against its spec acceptance criteria. Read-only; reports numbered findings (F-1, F-2).
- **Trigger:** `/review W-nn`
- **Rules:** Never fixes code. Reads whole diff (`git diff main...HEAD`). Verifies documentation matches code. Verifies standing rules (`tenant_id`, Flyway, `BigDecimal`, module boundaries). Categorizes findings into High, Medium, Low.

#### 7. `verify` (`.agents/skills/verify/SKILL.md`)
- **Description:** Independent verification running build, lint, tests, and module boundaries. Read-only; captures actual exit codes and command outputs.
- **Trigger:** `/verify W-nn`
- **Commands:**
  - Backend: `cd code/backend && ./mvnw -B clean verify`
  - Frontend: `cd code/frontend && npm run lint && npm run build`
  - Flyway check: `grep -rn "ddl-auto" code/backend/` (must be empty)
  - Module enforcer: `mvn dependency:tree` verifying `hrms` and `payroll` never depend on each other.
- Writes report to `.agents/outputs/<date>-verify-W-nn.md`.

#### 8. `test` (`.agents/skills/test/SKILL.md`)
- **Description:** Author targeted unit and integration tests for code changed in a ticket.
- **Trigger:** `/test W-nn`
- **Rules:** JUnit 5 mirroring target package in `code/backend/<module>/src/test/java`. Vitest/RTL in `code/frontend/`. Ensures negative cases (unauthenticated, wrong tenant, null/empty) are covered.

#### 9. `analyze` (`.agents/skills/analyze/SKILL.md`)
- **Description:** Read-only architectural investigation to answer "where/how does X work" in legacy or target state.
- **Trigger:** `/analyze <query>`
- **Output:** Evidence table citing exact `file:line` references.

#### 10. `sync-docs` (`.agents/skills/sync-docs/SKILL.md`)
- **Description:** Safe, controlled updates to `docs/` after code changes or architectural decisions.
- **Trigger:** `/sync-docs`
- **Workflow:** Detects drift between `docs/` and `code/`. Proposes exact markdown diff. Requests founder approval before writing to `docs/`.

---

### 6. Subagent Personas (Execution Rules)

When executing tasks, the Antigravity agent adopts specialized personas with strict tool restrictions:

| Persona | Allowed Antigravity Tools | Constraints |
|---|---|---|
| **Explorer** | `view_file`, `grep_search`, `list_dir`, `write_to_file` (only under `.agents/outputs/`) | Read-only investigator. Never edits code or docs. Every claim must cite `path:line`. |
| **Implementer** | `view_file`, `grep_search`, `list_dir`, `replace_file_content`, `write_to_file`, `multi_replace_file_content`, `run_command` | Operates only on **one module at a time**. Adds tests for everything changed. Only works after founder approval. |
| **Reviewer** | `view_file`, `grep_search`, `list_dir`, `run_command` (read-only git/grep commands) | **No edit tools.** Never fixes defects. Inspects diff against spec. Reports High/Medium/Low findings. |
| **Verifier** | `view_file`, `grep_search`, `list_dir`, `run_command` (compilers, test runners, linters) | **No edit tools.** Executes verification commands, captures real stdout/stderr and exit codes. |

---

## Part 4: Step-by-Step Installation Prompt for the Agent

When running this prompt to instantiate the harness:

1. **Create Directory Skeleton:**
   Ensure `.agents/hooks`, `.agents/scripts`, `.agents/skills`, `.agents/work`, and `.agents/outputs/.merge-receipts` exist.
2. **Generate `GEMINI.md`:**
   Write the complete root workspace rules file. Create `AGENTS.md` referencing it.
3. **Install `.agents/hooks.json`:**
   Configure all lifecycle hooks with exact tool matchers.
4. **Implement Hook Scripts in `.agents/hooks/`:**
   Port `guard-edit.mjs`, `guard-merge.mjs`, `verify-app.mjs`, and `session-log.mjs`, adapting them to the Antigravity stdin/stdout JSON contract.
5. **Implement Utility Scripts in `.agents/scripts/`:**
   Port `check-done.mjs` and `prune-outputs.mjs`, ensuring paths point to `.agents/`.
6. **Populate Skills in `.agents/skills/`:**
   Create all 10 `SKILL.md` files with YAML frontmatter.
7. **Initialize Work Tracking:**
   Create `.agents/work/active-work.md` and `.agents/work/active-work.template.md`.
8. **Verify the Installation:**
   - Run `node .agents/scripts/check-done.mjs` to test script execution.
   - Run a dry-run test of `guard-edit.mjs` passing a simulated edit to `docs/` and asserting `decision: "deny"`.
   - Run a dry-run test of `guard-merge.mjs` passing a simulated `git push origin main` and asserting `decision: "deny"`.
9. **Decommission & Remove Claude Harness:**
   Once the Antigravity harness is verified, delete the old `.claude` directory and root `CLAUDE.md`:
   - **PowerShell:**
     ```powershell
     Remove-Item -Recurse -Force .claude
     Remove-Item -Force CLAUDE.md
     ```
   - **Bash / Git Bash:**
     ```bash
     rm -rf .claude
     rm -f CLAUDE.md
     ```
   *(Note: Retain the frozen `legacy/**/CLAUDE.md` files as those document the legacy snapshots).*

