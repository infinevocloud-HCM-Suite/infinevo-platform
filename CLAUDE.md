# Infinevo Cloud

This is `infinevo-platform` — the repository the unified HCM platform is built in.
It holds the target-state design, the agent harness, and the new codebase as it
grows.

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
│                                  .github/workflows/ - must stay there, GitHub's rule
│
├── legacy/      THE FROZEN SYSTEM the four applications being replaced
│                                  docs/ - how they work, read before porting
│
└── .claude/     THE HARNESS       hooks · agents · skills · work/ · outputs/
```

**Each pillar is self-contained.** `docs/` and `code/` describe only the target
state; everything about the frozen system — its code *and* its documentation —
lives under `legacy/`. So there is exactly one rule to remember: **if the path
starts with `legacy/`, it describes what is being replaced, not what is being
built.** The `guard-edit` hook blocks writes to all of it.

`infra/` has no frozen counterpart — the four frozen applications contain one
deployment file between them, so everything there is new capability rather than
a port.

**`.github/workflows/` is the one thing that cannot live in `infra/`** — GitHub
Actions reads workflows only from that path.

| If you are | Read |
|---|---|
| New here | `CONTRIBUTING.md` |
| Building something | `docs/target-state/README.md`, then your ticket's spec |
| Asking how it works today | `legacy/docs/FEATURE_MAP.md`, then `legacy/` |
| Deploying or containerising | `infra/README.md` |

## Stack facts

| App | Language / build | Auth | Database | Port |
|---|---|---|---|---|
| `HRMS_Backend` | Java **21** · Spring Boot 3.2.4 · Maven | custom JWT (`jjwt`) | MySQL `hrmstestdb` | 1010 |
| `HRMS_Frontend` | React 18 · **Vite** · MUI v6 | JWT via React Context | — | 5173 |
| `Payroll-Bend-SBoot` | Java **17** · Spring Boot 3.2.5 · Maven | **Keycloak** OAuth2 | MySQL `payrollDB` | 3032 dev / 3029 prod |
| `Payroll-Fend-react` | React 18 · **CRA** · Ant Design 5 · Redux | Keycloak SSO | — | 3000 |

Keycloak realm `HRMS`, client `react-app`. Active branches: `main`, `main`,
`taxation`, `employee` — **not** `main` on the two Payroll repos.

## How a ticket runs

Four commands, the same for a feature and for platform work. **Two stops: you approve
the spec, you merge the branch.** Nothing else waits for you.

| | |
|---|---|
| `/plan-feature W-nn` | Writes the spec |
| **founder approves** | The one gate before code |
| `/develop W-nn` | Builds it, runs its own checks, fixes what it finds, pushes the branch |
| `/merge W-nn` | Gates, one independent read, then **founder merges** |

There are no review rounds. A defect found is fixed in the commit that caused it — it
does not become a numbered finding, a report file or a ticket. `/analyze` and
`/sync-docs` exist for questions and doc drift, and neither waits for approval.

## The rules a machine checks

These are enforced. They are listed so you know what will reject you, not as prose to
comply with by hand.

| Rule | Enforced by |
|---|---|
| `tenant_id` and an RLS policy on every new table outside `reference` | CI, `check-done.mjs` |
| Flyway for every schema change; `ddl-auto` set nowhere | CI, `check-done.mjs` |
| `Money` or `BigDecimal` for money, never `double` or `float` | CI, `check-done.mjs` |
| No module references another module — only `core` | `maven-enforcer` |
| No write to `legacy/`, `docs/` (except the branch's own spec), `*.properties`, `.env*` | `guard-edit` hook |
| No push to `main` carrying `code/` outside `/merge` | `guard-merge` hook |

## The rules nothing can check

1. **Upstream remotes are read-only.** The four origin repos are production source.
   Never push to them. Changes flow one way: upstream → this repo.
2. **Repo state beats chat memory.** If a conversation and the repo disagree, the
   repo is right. Verify before asserting.
3. **Read `@.claude/work/active-work.md` before starting** any task.

## Where things are

**`docs/target-state/` is where we are going. `legacy/docs/` is how the frozen
applications work today.** Never mix them up: a statement from `legacy/docs/`
describes code being replaced, not a rule for new code.

### Target state — build against this

| Need | Read |
|---|---|
| Where we are going: decisions, work plan, build order | `@docs/target-state/README.md` |
| Coding rules, `BigDecimal`, naming hazards | `@docs/CONVENTIONS.md` |
| New feature spec | `@docs/target-state/features/TEMPLATE.md` |
| Current direction, in-flight, frozen | `@.claude/work/active-work.md` |
| Setup and the developer loop | `@CONTRIBUTING.md` |

### Legacy — read for reference only

| Need | Read |
|---|---|
| System shape, ports, cross-service flow — **as frozen** | `@legacy/docs/ARCHITECTURE.md` |
| Tables and columns (all 131, **the old schema**) | `@legacy/docs/DB_SCHEMA.md` |
| Which files implement a feature in the 4 frozen apps | `@legacy/docs/FEATURE_MAP.md` |
| Known defects and debt in the frozen system | `@legacy/docs/GAP_INVENTORY.md` |
| Superseded docs | `@legacy/docs/_archive/` |
| The frozen source itself | `legacy/` — see `legacy/README.md` |

Per-app conventions and build commands live in each frozen app's own `CLAUDE.md`
under `legacy/`.

## Working agreements

- Cite `file:line` for any claim about the code.
- No tests exist today (DEBT-003). The `implementer` agent adds tests for what it
  changes and `/develop` runs them. Do not claim something works without evidence.
- Typos in package names are real and load-bearing: `timeshhet/`,
  `leaveAndAttedance/`, `EmployyePortalContoller.java`. Do not silently rename.
- **Answer briefly, and lead with a table — in chat and in every file you write.**
  Any summary, audit, status report or comparison opens with a compact table —
  findings, file lists, gate results, drift checks. Prose only for what a table
  cannot hold: a causal explanation, a recommendation, a trade-off. No preamble,
  no restating the question.
- **A report file is an answer.** Anything written to `.claude/outputs/` follows the
  same rule as a reply: one plain sentence per point, `file:line` citations listed
  beneath it, never packed inside it. A long report is the failure mode, not the
  thorough one.
