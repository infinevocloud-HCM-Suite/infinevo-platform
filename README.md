# Infinevo HCM Suite — platform

One platform for HRMS and Payroll. A customer buys either, or both, and upgrades
with a switch rather than a re-onboarding.

**New here? Start with [`CONTRIBUTING.md`](CONTRIBUTING.md).** It is about an hour
of reading before you write anything, and it is not optional.

---

## Your first day, in five steps

### 1. Clone

```bash
git clone https://github.com/infinevocloud-HCM-Suite/infinevo-platform.git
cd infinevo-platform
```

That is the only clone you need. The four applications being replaced are already
in [`legacy/`](legacy/) as frozen snapshots.

### 2. Install

| Tool | Version |
|---|---|
| JDK | **21** |
| Maven | 3.9+ (or use `./mvnw`) |
| Node.js | **24 LTS** |
| Docker Desktop | current |

Check: `java -version` · `node -v` · `docker ps`

### 3. Build it, so you know it works before you change anything

```bash
cd code/backend  && ./mvnw clean verify     # 7 modules, 21 tests
cd ../frontend   && npm install && npm run build
```

### 4. Read, in this order — about an hour

| # | Read | Gives you |
|---|---|---|
| 1 | [`docs/target-state/README.md`](docs/target-state/README.md) | Where we are going, and the map of everything else |
| 2 | [`docs/target-state/01-platform-shape.md`](docs/target-state/01-platform-shape.md) | Core vs HRMS vs Payroll, and what a customer gets |
| 3 | [`docs/CONVENTIONS.md`](docs/CONVENTIONS.md) | Money handling, naming hazards, the rules |
| 4 | [`CLAUDE.md`](CLAUDE.md) | The seven hard rules |
| 5 | [`CONTRIBUTING.md`](CONTRIBUTING.md) | How a ticket goes from assigned to merged |

### 5. Take your ticket

You will be assigned one issue. **One at a time** — two open tickets finish neither.

Then, for that ticket:

1. **Write the spec first** into `docs/target-state/features/W-nn-<slug>.md`,
   using `TEMPLATE.md`. Flows, API, schema changes, tests, acceptance criteria
2. **Get it approved.** This is the only gate. No code before it
3. Branch, build with tests as you go
4. Compile, lint and test green locally
5. Open a pull request saying `Closes #<your issue>`, with the real command output

---

## The repository, in four pillars

```
infinevo-platform/
│
├── docs/        THE DESIGN        target-state/ - where we are going
│                                  CONVENTIONS.md - rules new code must follow
│
├── code/        THE CODE          backend/  shared core hrms payroll app worker
│                                  frontend/ src/{shell core hrms payroll shared}
│
├── infra/       HOW IT RUNS       azure/ · docker/ · keycloak/
│
├── legacy/      THE FROZEN SYSTEM the four applications being replaced,
│                                  and docs/ describing how they work
│
└── .claude/     THE HARNESS       hooks · agents · skills
```

**One rule to remember: if a path starts with `legacy/`, it describes what is being
replaced, not what is being built.** Read it, port logic out of it, cite its
`file:line` in your spec — but never edit it. A change there is not deployed
anywhere and will be deleted. The `guard-edit` hook blocks writes to all of it.

---

## The rules that are never negotiable

| # | Rule |
|---|---|
| 1 | Tests for everything you change |
| 2 | `tenant_id` and a row-level security policy on every new table, unless in `reference` |
| 3 | Every new endpoint authenticated, or on the reviewed exception list |
| 4 | Flyway for every schema change. **Never `ddl-auto`** |
| 5 | `Money` or `BigDecimal` for money. Never a floating-point type |
| 6 | No module references another module. Only `core` |
| 7 | Never edit `legacy/`, `docs/`, `*.properties` or `.env*` |

Rules 2, 3 and 6 become build failures as `W-07`, `W-57` and `W-01` land — 6 already
does. Try adding a `payroll` dependency to `hrms` and see what happens.

---

## Where the work is

[**Issues**](../../issues) — 93 tickets, one per work item. [**Milestones**](../../milestones)
group them into nine waves, which are dependency order and not a calendar.

Labels tell you the shape at a glance: `ready` means nothing blocks it;
`blocked` means it is waiting on another ticket; `size-S/M/L` is relative effort;
`stream-*` is which part of the product it belongs to.

## Status

**Two of ninety-three tickets are done.**

`W-01` gave the module skeleton and the boundary the build enforces. `W-02` gave a
runnable platform — nine containers from one command:

```bash
docker compose -f infra/docker/compose.yml up -d
infra/docker/smoke.sh
```

Five tickets are `ready` — `W-03` `W-04` `W-05` `W-49` `W-66`. Everything else waits on
a dependency. **Nothing is in production.**

Two things are owed and written down so they are not forgotten: the two-tenant seed now
inserts both tenants (`W-09`) but **not their different module sets** — that waits on
`W-12` and `core.subscription` — and nobody but the author has yet run the local stack on
their own machine.
