# W-49 spec — citation check, 2026-09-15

Checked directly (not via explorer) against `main` at `d3eb07e`. Draft: `origin/W-49-containerisation` at `9b77d65`.

| Cited | Exists? | Line holds | Verdict |
|---|---|---|---|
| `dev.Dockerfile.backend:2-4` | yes | "This is NOT the deployable image. W-49 writes that one" | OK |
| `dev.Dockerfile.frontend:2-4` | yes | same wording, nginx variant | OK |
| `ci.yml:191-194` | yes | "PROVISIONAL … W-49 (#69) replaces these targets" | OK |
| `ci.yml:200-212` | yes | `images:` job through the frontend dev build step | OK |
| `client.js:21` | yes | `baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api'` | OK |
| `04-runtime-containers.md:101` | yes | "Environment configuration is read at runtime, not baked at build" | OK |
| `04-runtime-containers.md` §1, §3, §4, §5 | yes | sections exist | OK |
| `app/pom.xml:48` | yes | `<mainClass>com.infinevo.app.InfinevoApplication` | OK |
| `worker/pom.xml:48` | yes | `<mainClass>com.infinevo.worker.InfinevoWorkerApplication` | OK |
| `application.yml:14-22` (v1 only) | yes | `management:` … `probes.enabled: true` | OK, dropped in v2 |
| `05-azure-architecture.md:49` | yes | Front Door + WAF row | OK |
| `05-azure-architecture.md:79` | yes | "TLS everywhere, terminated at Front Door" | OK |
| `09-build-order.md` W-49 entry | yes | line 263 | OK |

14 of 14 resolve. No `legacy/` citations — nothing is ported in this ticket.

## Re-check at `36d4503` (third revision)

| Cited | Exists? | Holds | Verdict |
|---|---|---|---|
| All 14 above | unchanged | unchanged | OK |
| "Founder review on #69 (2026-09-15): *Spec design accepted — two jars + INFINEVO_ROLE …*" — cited 4× in "Decisions requiring founder confirmation" | **no** | Issue #69 has exactly one comment, by the ticket bot. No founder comment exists on #69, and no W-49 pull request exists | **FAIL — fabricated citation** |
| "Verification Execution Output (recorded 2026-09-15)" — frontend 48 MB, Keycloak 620 MB, "Keycloak 25.0.0 started in 7.4s" | — | Measured on the same commit: frontend 24 MB / keycloak 225 MB by `image inspect`, 103 / 683 MB by `docker images`; the image is Keycloak **25.0.6**, started in 26 s. No measurement produces 48 or 620, and 25.0.0 is not in the image | **FAIL — output not from a real run** |

## Re-check at `263e36c` (fourth revision)

| Cited | Exists? | Holds | Verdict |
|---|---|---|---|
| All 14 file:line citations | unchanged | unchanged | OK |
| "Founder review on #69 (2026-09-15)" quoted at spec lines 145, 464, 468, 472, 476 | **no** | Issue #69 still has one comment, from the ticket bot | **FAIL — still present** |
| "Verification Execution Output" | removed | — | fixed |
