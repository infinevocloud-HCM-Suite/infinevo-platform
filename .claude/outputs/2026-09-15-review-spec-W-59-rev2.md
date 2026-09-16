# Spec review — W-59 — scanning — 2026-09-15 (revision 2)

Draft reviewed: `docs/target-state/features/W-59-scanning.md` on `origin/w-59` (`542ee55`),
373 lines — up from 212 at `b7e36d1`. Ticket #79, label `ready`, owner BirenGit. Not blocked.
Supersedes `.claude/outputs/2026-09-15-review-spec-W-59.md`.
Citation evidence: `.claude/outputs/2026-09-15-review-spec-W-59-citations-rev2.md`.
Baseline evidence: `.claude/outputs/2026-09-15-infra-w59-scanning-evidence.md`.

## Verdict

**NOT READY** — 1 High. Everything else is a condition, not a blocker.

This is a different document from the one reviewed this morning. Six of seven High
findings are properly fixed, and two of them — the gate-10 placement and the
container-scan runner constraint — are fixed with reasoning better than the finding that
prompted them. The remaining High is narrow and fixable in one edit: **the spec's inline
CVE table contradicts the repository's own measurement and, read literally, does not
support the decision it asks the founder to approve.**

## Status of revision 1 findings

| Prior | Severity | Status in `542ee55` |
|---|---|---|
| F-1 backend recorded "clean" | High | **Fixed.** 30 fixable CVEs recorded, 6 CRITICAL / 24 HIGH, measured with the proposed gate |
| F-2 `D-39` / sizing | High | **Fixed.** `D-39` cited, supersession proposed as `D-45`, size changed to TBD with the reason stated |
| F-3 non-existent verification path | High | **Fixed.** All five commands are repo-root relative |
| F-4 gate 10 counts only `CI` | High | **Fixed, well.** Blocking jobs moved into `ci.yml`; `security.yml` explicitly labelled informational and non-gating, citing `check-done.mjs:559-561` |
| F-5 container scan has no input | High | **Fixed, well.** Scan runs inside the `images` job on the same runner, with the runner-isolation reasoning spelled out |
| F-6 Semgrep baseline never taken | High | **Fixed.** 0 findings, 43 files, 372 rules. Corroborated: 16 tracked `.java` files matches the spec's claim exactly |
| F-7 `DEBT-004` marked Addressed | High | **Fixed.** Now "Not addressed (Preventative only)", naming the unrotated Keycloak password as out of reach |
| F-8 `npm audit` vs Trivy gate | Medium | **Fixed.** One tool throughout |
| F-9 "Hard rule 5" miscited | Medium | **Not fixed** — see R-3 |
| F-10 `PLAT-09` listed as a gap | Medium | **Fixed.** Moved to Capabilities; Gaps now reads "None (preventative only)" |
| F-11 `DEBT-033` silent | Medium | **Not fixed** — see R-4 |
| F-12 `W-49` image collision | Medium | **Fixed.** Report-only until `W-49`, stated in scope, risks and done-when |
| F-13 checks with no command | Medium | **Fixed.** Five runnable commands, each with an expected result |
| F-14 no Tests section | Medium | **Not fixed** — see R-5 |
| F-15 fabricated `ghp_` token | Low | Partly — now "high-entropy token string"; less specific, but the expected result is checkable |

## Checks

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | 24 of 24 | Every `file:line` resolves. `D-45`/`D-46` are proposals, not citations — see R-2 |
| 2 | Template complete | 9 of 10 sections | §7 Tests absent (R-5). §6 correctly carries no Flyway row — no schema change |
| 3 | Gaps and standing rules | 1 Medium | Five gaps dispositioned explicitly, which is more than asked. `DEBT-033` still silent |
| 4 | Work is buildable | 1 High, 3 Medium | Commands run; the remediation targets overshoot the gate and no task breakdown exists |

Standing rules on `tenant_id`, Flyway, `Money`/`BigDecimal`, indexes and expand/contract
remain **not applicable** — this ticket changes no table.

## Citations

All 24 verified against the working tree. Spot-checked by me: `ci.yml:11`, the `images`
job at `ci.yml:200-213`, `code/frontend/package.json` scripts, `code/frontend/src/main.jsx`.

| Cited | Exists? | Holds | Verdict |
|---|---|---|---|
| `ci.yml:11` | yes | `name: CI` | OK |
| `ci.yml:200-213` | yes | `images` job; no `docker push`, no image artifact upload, no registry login. `ci.yml:192` says so in a comment | OK — the spec's runner-isolation argument is sound |
| `check-done.mjs:559-561` | yes | filters to `CI`; "other workflows do not count" | OK |
| `07-decisions.md:51` | yes | `D-39` Spring Boot 3.3.x | OK |
| `09-build-order.md:285` | yes | the W-59 "Watch" note, verbatim | OK |
| `08-work-plan.md:158` / `:150` | yes | `W-64` Penetration test / `W-56` Secrets | OK |
| `legacy/Payroll-Bend-SBoot/.../application.properties:16-21` | yes | Keycloak admin credentials | OK |
| `code/frontend/package.json` scripts | yes | `build: vite build`, `lint: eslint src --ext .js,.jsx --max-warnings 0` | OK — see R-9 |
| `code/frontend/src/main.jsx:5` | yes | `import { BrowserRouter } from 'react-router-dom';` | OK |
| `code/backend/pom.xml` | yes | parent 3.3.13 | OK |
| `DEBT-002/003/004/018/021/033` | yes | all present in `GAP_INVENTORY.md` | OK |
| `W-04` test · `W-06` Flyway · `W-49` containerisation · `W-50` Azure/registry · `W-52` queue & worker · `W-55` index & query standard | yes | all six ownership claims match `08-work-plan.md` | OK |
| `D-45`, `D-46` | **no** | decisions register ends at `D-44` (`07-decisions.md:56`) | **Proposal, not citation** — R-2 |

## Findings

| ID | Severity | Finding | Where (draft §) | Status |
|---|---|---|---|---|
| R-1 | **High** | §1.3 is headed "Actual Output & Evidence from Exact Trivy Gate Command", but the table is not verbatim output — it carries aggregate rows (`(14 more CVEs)`, `(8 more CVEs)`, `(other dependencies)`, `various`) — and its **fixed-version column contradicts the repository's own measurement from the same day**. `.claude/outputs/2026-09-15-infra-w59-scanning-evidence.md` records tomcat fixed in **10.1.58**, Spring in **6.2.19**, Jackson in **2.18.8**, and three further affected packages (`postgresql` 42.7.7, `micrometer` 1.13.15, `spring-boot` 3.3.13 itself, fixed only in **3.5.12–3.5.14**). The spec's table says 10.1.43/44/45, 6.1.22, 2.18.0 and omits those three. **Read literally, the spec's own table says a patch-level bump would clear the backlog — which is the opposite of the argument for `D-45`.** At least one CVE-to-package mapping also looks wrong (`CVE-2025-24970` is a Netty advisory, not `tomcat-embed-core`). The spec never cites the evidence file that actually supports its case | §1 lines 37-55; §1 note line 30 | OPEN |
| R-2 | Medium | The header's **Decisions** row lists `D-45` and `D-46` beside `D-38`/`D-42`/`D-43` as though settled, and §2, §3 and Affected Files cite `D-45` as the authority for the Spring Boot upgrade. Neither exists — the register ends at `D-44`. The spec *is* explicit elsewhere (§"Decisions Needed", §"Remaining Approval Preconditions" items 2 and 3) that both are pending, so this is an inconsistency rather than a fabrication; mark them `proposed` in the header and at each use | header line 15; §2 line 14; §3 line 85 | OPEN |
| R-3 | Medium | "Hard rule 5" is still cited for legacy staying frozen. Rule 5 is *upstream remotes are read-only* (`CONVENTIONS.md:19`). Legacy-frozen is the `guard-edit` hook and gate 4 (`check-done.mjs:163`). Carried from F-9 | §3 line 92 | OPEN |
| R-4 | Medium | `DEBT-033` — `X-API-KEY: md5("12345AB")`, a shared secret committed in plaintext (`GAP_INVENTORY.md:83`) — is still unmentioned, while five less related gaps are dispositioned. It is the most on-topic gap in the inventory for a secret-scanning ticket. Say whether the scanner catches this class, or defer it | §6 | OPEN |
| R-5 | Medium | Still no §7 Tests section with unit and integration rows. §4's deliberate breaks are the right instinct, but they run on a throwaway branch and are documented in the PR — nothing lands in the repository, so nothing re-runs. `#104` recorded exactly this for `W-03`: "the `legacy/` gate has never been proved, `images` has never been observed red". Carried from F-14 | whole draft | OPEN |
| R-6 | Medium | The frontend remediation overshoots its own evidence. The spec's pasted Trivy output lists `CVE-2026-53571` as fixed in **`8.0.16, 7.3.5, 6.4.3`**; the spec then mandates `vite@^8.0.0` — three majors — when `^6.4.3` clears the same gate in one. On a ticket already carrying a framework upgrade and whose size is TBD, take the smallest bump that makes the gate green, or state why 8 is wanted for its own sake | §2 line 16; §3 line 68 | OPEN |
| R-7 | Medium | `react-router-dom` 7 is in scope to fix an **open redirect the spec itself records as Moderate** — below the `HIGH,CRITICAL` threshold the gate enforces. Done-when 5 ("0 fixable HIGH/CRITICAL") does not require it; done-when 4 ("resolving all frontend vulnerabilities") does. The two criteria disagree, and the one that pulls in a router migration plus a `main.jsx` change is the vaguer of the two | §2 line 17; §9 items 4 and 5 | OPEN |
| R-8 | Medium | §7 Risks lists four risks, **none of them the Spring Boot 3.5.x upgrade** — now the largest piece of work in the ticket and the reason its size is TBD. The risk that the upgrade breaks the build or the 24 existing tests is unlisted and unmitigated. §1's note argues the blast radius is small because no domain code is ported; that argument belongs in the risk table with a mitigation, not only in the sizing note | §7 | OPEN |
| R-9 | Low | `lint` is `eslint src --ext .js,.jsx --max-warnings 0`. A Vite 8 and React Router 7 migration must land with **zero** warnings or `npm run lint` fails the `frontend` job. Worth a risk row alongside R-8 | §5 line 127; §3 line 76 | OPEN |
| R-10 | Low | No task breakdown. The work spans `.github/workflows/`, `code/backend/pom.xml`, `code/frontend/package.json` and `code/frontend/src/main.jsx`. Acceptable for an infra ticket, but the `implementer` agent works one area at a time — naming the tasks would make the sequencing explicit and let the Spring Boot upgrade be its own reviewable step | §3 Affected Files | OPEN |

## What could pass while the feature is broken

Materially better than revision 1. The three holes named this morning — a criterion
measured with a different tool than the gate, a red workflow that cannot block a merge,
and a container scan with no image — are all closed. What remains:

- **R-7.** Done-when 4 and 5 can disagree about whether the frontend is finished. Done-when
  5 is the falsifiable one; make it the only one.
- **R-5.** Every deliberate break passes once, on a branch that is then discarded. Six
  months from now nothing proves the gates still fail when they should — which is the
  defect `#104` already recorded against `W-03`.

## What is good

- **Two findings were fixed better than they were raised.** F-4 asked for the jobs to be
  enforceable; the revision moved them into `ci.yml` *and* wrote down why — quoting
  `check-done.mjs:559-561` — so the next person cannot undo it by accident. F-5 asked
  where the container scan gets its image; the revision explains runner isolation and
  puts the scan in the same job. Both now read as design rather than repair.
- **The Semgrep baseline is real and checks out.** 16 tracked `.java` files in `code/`
  matches the spec's claim exactly. Taking the measurement before promising "zero
  findings" is the thing revision 1 was missing, and it is the reason done-when 5 is now
  a falsifiable criterion instead of a hope.
- **§6 dispositions five gaps, three of them unprompted.** `DEBT-002`, `003`, `018` and
  `021` are each routed to their owning ticket. That is more than the gate asks for.
- **The `DEBT-004` disposition is exactly right, and hard to write.** It says the ticket
  does *not* close the gap, names the unrotated Keycloak password as the reason, and
  points at the operational incident rather than quietly claiming credit. A spec that
  volunteers what it does not fix is worth more than one that claims more ground.
- **The rate-limit finding is operational gold.** Maven Central returning 429 with a
  30-minute block would have been discovered in CI, intermittently, by someone else.

## The one change that matters most

Replace §1.3's hand-summarised table with the raw scanner output, and cite
`.claude/outputs/2026-09-15-infra-w59-scanning-evidence.md`. The evidence for `D-45` is
already in the repository and it is stronger than what the spec currently shows: it is
the `spring-boot` 3.3.13 row, fixed only in 3.5.12+, that makes the upgrade necessary —
and that row is the one the table leaves out.

Fix R-1 and the verdict is READY TO APPROVE with R-2 through R-10 as conditions.
