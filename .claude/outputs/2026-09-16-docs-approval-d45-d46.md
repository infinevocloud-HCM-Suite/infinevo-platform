# Docs change approval — d45-d46 — 2026-09-16

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-16-docs-diff-d45-d46.patch` |
| **Status** | **Approved 2026-09-16** by the founder |
| **Why** | `W-59`'s scanning baseline found `CVE-2026-40973` in `spring-boot` itself at 3.3.13, fixed only in 3.5.14 with no 3.3.x patch — so `D-39` had to be reopened. `D-45` records the move to Spring Boot 3.5.x, `D-46` records enabling Dependabot, and `D-39` is struck through in the house style already used for `D-41` |

## Paths covered

- `docs/target-state/07-decisions.md` @ `a05504caf8828c1a30983517f5f986b240196ae8`

Approved alongside the `W-59` spec (`docs/target-state/features/W-59-scanning.md`), which
reached `main` in `fc99b7a` under gate 5's own-spec exception and is therefore not listed
here. Evidence for every figure written into `D-45`:
`.claude/outputs/2026-09-15-infra-w59-scan-raw.md`.
