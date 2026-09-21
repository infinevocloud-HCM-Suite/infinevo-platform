# Active Work

> Live project state. **Read this before starting any task** (root `GEMINI.md` rule 2).
> Last refreshed: **2026-09-21**, active branch `W-08-tenant-binding-filter`.
> Tracked, not gitignored — it is how everyone sees where the project stands.

## Where the project is

**Design finished. Build started. Nothing in production.**

| | |
|---|---|
| Repository | `infinevocloud-HCM-Suite/infinevo-platform`, private |
| Tickets | **100** — GitHub is authoritative, this file is the summary |
| Waves | 9. **Wave 1 active** |
| Merged | `W-01` skeleton · `W-02` local stack · `W-03` build pipeline · `W-04` test foundation · `W-05` Postgres & schemas · `W-06` Flyway · `W-07` Tenant schema · `W-08` Tenant binding filter (in progress) |
| Team | `developers`, Write access. |

---

## Current direction

**One platform replacing four applications, then Azure.** A customer buys HRMS,
Payroll, or both, and upgrades with a switch rather than a re-onboarding.

One repository · one backend with three enforced modules (`core` / `hrms` / `payroll`)
· one React frontend · **one Postgres database with four schemas** · one login
(Keycloak, single realm) · **Azure Container Apps**.

Design: `docs/target-state/` — 12 documents, **49 decisions (`D-01`–`D-49`), zero open
questions.** Start at `docs/target-state/README.md`.

---

## Frozen — `legacy/`

All four applications are frozen as of 2026-09-13 and live in `legacy/`. Read them,
port logic out of them, cite their `file:line`. **Never edit them** — `guard-edit`
blocks it, and a change there is not deployed anywhere.

| Folder | Branch taken | Commit |
|---|---|---|
| `legacy/HRMS_Backend` | `main` | `d984c64` · 2026-06-16 |
| `legacy/HRMS_Frontend` | `main` | `c72116c` · 2025-12-17 |
| `legacy/Payroll-Bend-SBoot` | **`taxation`** | `39b37d6` · 2026-09-09 |
| `legacy/Payroll-Fend-react` | **`employee`** | `053ca62` · 2026-09-10 |

How they work: `legacy/docs/` — `ARCHITECTURE.md`, `DB_SCHEMA.md`, `FEATURE_MAP.md`,
`GAP_INVENTORY.md`.

---

## Related

`docs/target-state/README.md` · `CONTRIBUTING.md` · `GEMINI.md` · `.agents/outputs/`
