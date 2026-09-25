# W-59 Spec Draft — Citation Verification — 2026-09-15

## Evidence Table

| # | Claim (as drafted) | Citation target | File exists? | Line exists? | Actual content (verbatim quote) | Verdict |
|---|---|---|---|---|---|---|
| 1 | "Watch: fix the initial backlog once, or the noise gets ignored permanently." | `docs/target-state/09-build-order.md:285` | ✓ | ✓ | Line 285: `**`W-59` Scanning** · Build: dependency, code and container scanning. Done when: it runs on every merge. Watch: fix the initial backlog once, or the noise gets ignored permanently.` | OK |
| 2 | W-64 Penetration test is defined at | `docs/target-state/08-work-plan.md:158` | ✓ | ✓ | Line 158: `\| `W-64` \| Penetration test \| External engagement · Remediation \| `PLAT-09` \| `W-57`, `W-58` \| Infra \|` | OK |
| 3 | W-58 Tenant isolation policy tests is defined at | `docs/target-state/08-work-plan.md:152` | ✓ | ✓ | Line 152: `\| `W-58` \| Tenant isolation tests \| Cross-tenant read tests · Row-level security verification · Pipeline integration \| `PLAT-09` \| `W-08` \| New \|` | OK |
| 4a | `.github/workflows/ci.yml` exists | `.github/workflows/ci.yml` | ✓ | — | File is 213 lines | OK |
| 4b | Job names in ci.yml | `.github/workflows/ci.yml` | ✓ | Various | Jobs: `backend` (line 29), `frontend` (line 61), `static` (line 111), `images` (line 200) | OK |
| 4c | Job triggers | `.github/workflows/ci.yml:13-16` | ✓ | ✓ | Lines 13-16: `on: pull_request` and `push: branches: [main]` | OK |
| 5a | `code/frontend/package.json` exists | `code/frontend/package.json` | ✓ | — | File is 33 lines | OK |
| 5b | vite version and location | `code/frontend/package.json:31` | ✓ | ✓ | Line 31: `"vite": "^5.4.10"` in `devDependencies` | OK |
| 5c | react-router-dom version and location | `code/frontend/package.json:24` | ✓ | ✓ | Line 24: `"react-router-dom": "^6.28.0"` in `dependencies` | OK |
| 5d | scripts block | `code/frontend/package.json:10-14` | ✓ | ✓ | Lines 10-14: `"dev": "vite"`, `"build": "vite build"`, `"preview": "vite preview"`, `"lint": "eslint src --ext .js,.jsx --max-warnings 0"` | OK |
| 6 | `code/frontend/package-lock.json` exists | `code/frontend/package-lock.json` | ✓ | — | File exists, lockfileVersion 3 | OK |
| 7a | `code/backend/pom.xml` exists | `code/backend/pom.xml` | ✓ | — | File is 230 lines | OK |
| 7b | Parent spring-boot version | `code/backend/pom.xml:9-10` | ✓ | ✓ | Line 10: `<version>3.3.13</version>` (parent: spring-boot-starter-parent) | OK |
| 7c | Module entries | `code/backend/pom.xml:34-41` | ✓ | ✓ | Lines 34-41: modules `shared`, `core`, `hrms`, `payroll`, `app`, `worker`, `migration` (7 modules listed) | OK |
| 8a | Maven wrapper at `code/backend/` | `code/backend/mvnw` | ✓ | — | File exists, is executable shell script | OK |
| 8b | Maven wrapper cmd at `code/backend/` | `code/backend/mvnw.cmd` | ✓ | — | File exists | OK |
| 9a | Image tag `infinevo-backend:dev` in ci.yml | `.github/workflows/ci.yml:209` | ✓ | ✓ | Line 209: `docker build -f infra/docker/dev.Dockerfile.backend -t infinevo-backend:dev .` | OK |
| 9b | Image tag `infinevo-frontend:dev` in ci.yml | `.github/workflows/ci.yml:212` | ✓ | ✓ | Line 212: `docker build -f infra/docker/dev.Dockerfile.frontend -t infinevo-frontend:dev .` | OK |
| 10a | DEBT-004 exists in GAP_INVENTORY | `legacy/docs/GAP_INVENTORY.md:42` | ✓ | ✓ | Line 42: `\| **DEBT-004** \| Both backends \| Secrets hardcoded in `.properties` — Keycloak secret, Cloudinary keys, Brevo key, `fed.secret`, DB passwords \| Credentials in git history; assume compromised \|` | OK |
| 10b | DEBT-004 title exact | `legacy/docs/GAP_INVENTORY.md:42` | ✓ | ✓ | Title: "Secrets hardcoded in `.properties`" — Description: "Keycloak secret, Cloudinary keys, Brevo key, `fed.secret`, DB passwords" | OK |
| 11a | Is PLAT-09 in GAP_INVENTORY.md? | `legacy/docs/GAP_INVENTORY.md` | ✓ | — | PLAT-09 does **NOT** appear in GAP_INVENTORY.md (searched entire file) | MISSING |
| 11b | PLAT-09 type and definition | `docs/target-state/01-platform-shape.md:161` | ✓ | ✓ | Line 161: `\| `PLAT-09` \| Security hardening \| Secrets, isolation tests, encryption, endpoint audit, scanning \|` — **PLAT-09 is a CAPABILITY ID, not a gap ID** | OK (but DRIFTED) |
| 12a | D-38 Java version | `docs/target-state/07-decisions.md:50` | ✓ | ✓ | Line 50: `\| `D-38` \| 2026-09-13 \| **Java 21** for the whole backend \| `HRMS_Backend` is already on 21, Payroll on 17. 21 is LTS with support to 2031, and nothing in the frozen Payroll code blocks the move. One version across seven modules, chosen before any code exists \| `03`, `W-01` \|` | OK |
| 12b | D-42 Node version | `docs/target-state/07-decisions.md:54` | ✓ | ✓ | Line 54: `\| `D-42` \| 2026-09-13 \| **Node 24 LTS** for the frontend and the harness \| The current active LTS, maintained to 2028. **Supersedes `D-41`**, which named a runtime that was already end-of-life on the day it was approved \| `03`, `W-01` \|` | OK |
| 12c | D-43 Branch protection | `docs/target-state/07-decisions.md:55` | ✓ | ✓ | Line 55: `\| `D-43` \| 2026-09-13 \| **Branch protection is convention, not enforcement, until the second developer joins** \| GitHub refuses branch protection on private repositories on the Free plan. The options were to make the repository public, pay for Team, or rely on convention. A multi-tenant platform holding other companies' payroll data cannot be public, and paying per seat before there are seats is premature. Revisit when the team grows \| `W-01` \|` | OK |
| 13 | W-56 is Azure Key Vault integration | `docs/target-state/08-work-plan.md:150` | ✓ | ✓ | Line 150: `\| `W-56` \| Secrets \| Key Vault setup · Managed identity wiring · Rotation process \| `PLAT-09` \| `W-51` \| Infra \|` | OK (W-56 is "Secrets", which encompasses Key Vault) |
| 14 | W-49 containerisation | `docs/target-state/08-work-plan.md:133` | ✓ | ✓ | Line 133: `\| `W-49` \| Containerisation \| Backend Dockerfile · Frontend Dockerfile · Keycloak image · **Web and worker profiles from one image** \| — \| `W-01` \| Infra \|` | OK |
| 15 | W-64 penetration test | `docs/target-state/08-work-plan.md:158` | ✓ | ✓ | Line 158: `\| `W-64` \| Penetration test \| External engagement · Remediation \| `PLAT-09` \| `W-57`, `W-58` \| Infra \|` | OK |
| 16a | CONVENTIONS.md has "rule 5" | `docs/CONVENTIONS.md:19` | ✓ | ✓ | Line 19: Rule 5 is "**Upstream remotes are read-only.** Never push to the four origin repos — they are production source. Changes flow one way, upstream → new repo" | OK |
| 16b | Root CLAUDE.md has "rule 5" | `D:\Infinevoclouds\CLAUDE.md:7` | ✓ | ✓ | Root CLAUDE.md line 7: Hard rule 5 is "**Upstream remotes are read-only.** The four origin repos are production source. Never push to them. Changes flow one way: upstream → this repo." | OK (same meaning, different wording) |
| 16c | Draft claim about "legacy stays frozen" | — | ✗ | — | Neither CONVENTIONS.md nor CLAUDE.md rule 5 says "legacy stays frozen". That concept appears in rule 3 (Never edit docs/ or *.properties) and in CLAUDE.md §1 ("frozen snapshots"). Rule 5 is about upstream remotes | DRIFTED |
| 17 | code/frontend/src file count | — | ✓ | — | 9 files found by glob: `hrms/index.js`, `payroll/index.js`, `shared/api/client.js`, `shared/theme.js`, `shell/AppShell.jsx`, `shell/routes.js`, `shell/store.js`, `core/index.js`, `main.jsx` | OK (9 files) |
| 17b | react-router-dom imports in src/ | `code/frontend/src/main.jsx:5` | ✓ | ✓ | Line 5 of main.jsx: `import { BrowserRouter } from 'react-router-dom';` — This is the only import found in the scanned files | OK |

---

## Summary of Issues

### DRIFTED (citations exist but differ from draft claim)

| Item | Issue | Details |
|---|---|---|
| **11** | PLAT-09 type | Draft appears to list PLAT-09 under "Gaps addressed" but PLAT-09 is a **capability ID in platform-shape.md**, not a gap ID in GAP_INVENTORY.md. It does not appear in GAP_INVENTORY.md at all. |
| **16c** | Rule 5 misdescription | Draft claims "Hard rule 5" means "legacy stays frozen", but rule 5 is actually "Upstream remotes are read-only". The "legacy stays frozen" concept is rule 3 (never edit docs) and part of the CLAUDE.md architectural statement, not rule 5. |

### MISSING (cannot be verified)

| Item | Issue | Details |
|---|---|---|
| **11a** | PLAT-09 in GAP_INVENTORY | PLAT-09 does not appear anywhere in `legacy/docs/GAP_INVENTORY.md`. It is only found in `docs/target-state/01-platform-shape.md:161` as a capability. |

### ALL OTHER CITATIONS: OK

All other 15 citations verified as accurate:
- Lines 285, 158, 152 in target-state docs exist and quote correctly
- ci.yml jobs and triggers verified
- package.json versions and structure verified
- pom.xml parent version and modules verified
- Maven wrappers exist
- Image tags in ci.yml verified (lines 209, 212)
- DEBT-004 in GAP_INVENTORY verified
- D-38, D-42, D-43 in decisions verified
- W-56, W-49, W-64 in work-plan verified
- frontend src files and react-router-dom import verified

---

## Related Documentation

- `docs/target-state/01-platform-shape.md` — PLAT-09 definition (capability)
- `legacy/docs/GAP_INVENTORY.md` — DEBT-004 and all gap IDs
- `docs/target-state/07-decisions.md` — D-38, D-42, D-43
- `docs/target-state/08-work-plan.md` — W-56, W-49, W-64
- `docs/target-state/09-build-order.md` — W-59 Watch note
- `docs/CONVENTIONS.md` — Rules 1-7
- `CLAUDE.md` (root) — Hard rules 1-7
