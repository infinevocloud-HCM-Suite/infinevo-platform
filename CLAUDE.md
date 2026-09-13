# Infinevo Cloud

Four applications. Two Spring Boot backends, two React frontends, two databases,
two auth systems. Root is a git repo holding docs + harness only; the four app
folders are separate repos and are gitignored until the subtree migration.

## Stack facts

| App | Language / build | Auth | Database | Port |
|---|---|---|---|---|
| `HRMS_Backend` | Java **21** · Spring Boot 3.2.4 · Maven | custom JWT (`jjwt`) | MySQL `hrmstestdb` | 1010 |
| `HRMS_Frontend` | React 18 · **Vite** · MUI v6 | JWT via React Context | — | 5173 |
| `Payroll-Bend-SBoot` | Java **17** · Spring Boot 3.2.5 · Maven | **Keycloak** OAuth2 | MySQL `payrollDB` | 3032 dev / 3029 prod |
| `Payroll-Fend-react` | React 18 · **CRA** · Ant Design 5 · Redux | Keycloak SSO | — | 3000 |

Keycloak realm `HRMS`, client `react-app`. Active branches: `main`, `main`,
`taxation`, `employee` — **not** `main` on the two Payroll repos.

## Hard rules

1. **Founder approval before any implementation.** Write the plan, stop, wait.
2. **Read `@agents/active-work.md` before starting** any task.
3. **Never edit `docs/` or `*.properties` during feature work.** Docs change only
   through `sync-docs` with an approved diff. Enforced by the `guard-edit` hook.
4. **Flyway for migrations — never `ddl-auto`.** Both backends currently run
   `ddl-auto=update`; that is a known defect, not a pattern to copy.
5. **Upstream remotes are read-only.** The four origin repos are production source.
   Never push to them. Changes flow one way: upstream → this repo.
6. **Repo state beats chat memory.** If a conversation and the repo disagree, the
   repo is right. Verify before asserting.
7. **`tenant_id` is mandatory on every new entity and query** once the tenant column
   lands. No exceptions, including lookup tables.

## Where things are

| Need | Read |
|---|---|
| **Where we are going** (target state, decisions, open questions) | `@docs/target-state/README.md` |
| System shape, ports, cross-service flow — **today** | `@docs/ARCHITECTURE.md` |
| Tables and columns (all 131) | `@docs/DB_SCHEMA.md` |
| Which files implement a feature (all 4 apps) | `@docs/FEATURE_MAP.md` |
| Known defects and debt | `@docs/GAP_INVENTORY.md` |
| Coding rules, `BigDecimal`, naming hazards | `@docs/CONVENTIONS.md` |
| Current direction, in-flight, frozen | `@agents/active-work.md` |
| New feature spec | `@docs/features/TEMPLATE.md` |
| Superseded docs | `@docs/_archive/` |

Per-app conventions and build commands live in each app's own `CLAUDE.md`.

## Working agreements

- Cite `file:line` for any claim about the code.
- No tests exist today (DEBT-003). The `implementer` agent adds tests for what it
  changes; `verifier` runs them. Do not claim something works without evidence.
- Typos in package names are real and load-bearing: `timeshhet/`,
  `leaveAndAttedance/`, `EmployyePortalContoller.java`. Do not silently rename.
