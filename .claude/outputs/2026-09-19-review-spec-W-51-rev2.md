# Spec review — W-51 — networking-and-identity — rev 2 — 2026-09-19

Draft: `.claude/outputs/2026-09-19-plan-W-51-networking-rev2.md` (606 lines, `Status: Draft — not approved`).
Ticket #71, `stream-G, size-M, skill-INFRA, ready` — not `blocked`.

**Re-review.** The first pass is `.claude/outputs/2026-09-19-review-spec-W-51.md`. Per the
re-review rule this reports blockers only and does not re-sweep; the five Notes from pass 1
stay closed whether or not they were acted on.

**Disclosure that affects how much this pass is worth:** rev 2 was written in the same
session that is now reviewing it. An independent pass in a clean session would be worth
more than this one, and pass 1 already demonstrated why — explorer graded 30 citations OK
and was wrong on two of them, both caught only because its own answers disagreed.

## Verdict

**APPROVE WITH CONDITIONS** — one blocker, and it narrows an existing mechanism rather
than requiring a redesign. Approval is sound once F-6 is closed.

## Checks

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | Re-checked only the two that drifted in pass 1 | `05-azure-architecture.md:75` and `:159` now hold what the draft claims. No `legacy/` citations, so no sweep |
| 2 | Template complete | Unchanged from pass 1 — 9 of 9 + Decisions | Still creates no database object, so no Database changes section is owed |
| 3 | Gaps and standing rules | Pass | §6 now states explicitly that no table, column, index or migration is created, so `tenant_id`, RLS, Flyway and precision rules do not apply. `SEC-001` removed and the reasoning given |
| 4 | Work is buildable | **1 blocker** | Four of the five pass-1 worries are genuinely answered. The fifth is narrower than it was but still open |

## Pass-1 findings — the worry, not the patch

| Was | The worry, restated | Now |
|---|---|---|
| F-1 | *Does any command prove Front Door routes correctly and the WAF actually blocks?* | **Closed.** §5 4c sends `?wafcanary=block` and requires `403` — detach the security policy and it returns `200` and fails. 4d requires ≥1 block in a 150-request burst. 4b reads `X-Infinevo-Origin` **and** cross-checks the origin group against each app's live ingress FQDN, so a misroute is visible from outside |
| F-2 | *Can the origin protection be built where the spec says it lives?* | **Closed.** `ipSecurityRestrictions` is in the ACA ingress schema and implementable in `containerapps.bicep`; §5 step 8 proves it from the verifier's own IP. `X-Azure-FDID` deferred to `W-57` with a founder question rather than left hanging |
| F-3 | *Can the deployment still write secrets once the vault is locked?* | **Closed.** `networkAcls.defaultAction: Deny` + transient `/32`, and §5 step 2 fails if any `ipRule` survives. `deploy.sh:8` is `set -euo pipefail`, so a failed write at `:120` cannot pass silently |
| F-4 | *Does anything prove the private endpoints and the role assignments actually work?* | **Still open — see F-6.** Six probes now exist where rev 1 had none, and the gap is much narrower, but the evidence is self-reported |
| F-5 | *Is the destructive environment recreation planned and confirmed?* | **Closed.** §8a steps 3–6 order the two recreations and name why the job cannot exist before step 4; §5 step 3 asserts `infrastructureSubnetId` ends `/snet-cae` |

## Blockers

| ID | Blocker | Where (draft §) | What closes it |
|---|---|---|---|
| **F-6** | **The private-path evidence is a string the code under test prints about itself.** §5 step 6 gates on `grep -Fq "${probe}: OK"` against the job's own log (lines 418-421). A probe script that echoes `PROBE-BLOB: OK` without opening a socket passes, and so does one whose blob write silently fell back to a public endpoint. This is the strongest check in the spec and the only one not observed from outside the thing it measures — 4b, 4c and 8 are all externally verified, which is why they are sound. The failure is the same shape as W-50's: the private path stays unproven until `W-52`/`W-53`, and nothing here goes red | §5 step 6, lines 393-423; §3e "Runs" row; §9 item 7 | Three changes, all small: (1) each probe exits non-zero on failure so the job's own `Succeeded` is the gate, and delete the log grep — an exit code cannot be echoed by mistake; (2) each probe prints the resolved address and §5 asserts it matches `^10\.`, so a public-endpoint fallback fails; (3) cross-check one probe from outside the job — after the run, `az servicebus queue show -g $RG_ENV --namespace-name sb-infinevo-$ENV -n <probe-queue> --query countDetails.activeMessageCount -o tsv` observes the Service Bus send over the **control** plane, which the job cannot fake. One independent observation is enough to make the rest credible |

## Notes

- §5 line 394 still reads "then four probes"; there are six. Wording only, no effect on what is built.

## What is good

- §2.3 is the best thing in the revision. Rather than applying `publicNetworkAccess: 'Disabled'` uniformly and hoping, it gives a four-row table saying which resource gets which setting **and why Key Vault differs**, then sends the deviation to the founder as question 2 instead of deciding it quietly. That is the right handling for a documented rule (`05-azure-architecture.md:75`) the work genuinely cannot satisfy as written.
- §3f's "Exercised by" column, with "control plane only" spelled out for six of the sixteen assignments and a paragraph bounding why. Naming what is *not* proven is worth more than a table that silently implies everything is.
- §4 breaks 4, 5 and 8 are deliberate falsification tests aimed at the checks themselves — remove the WAF association, misroute an origin, revoke a role, and verification must go red. Rev 1 could not fail in any of those three ways. Break 8 is also what makes F-6 a tightening rather than a rewrite.
- §8a step 8 — "Validate, then retire. Only after it passes end to end is the old server deleted." The old server stays up through the entire procedure, so the rollback is to stop, not to restore.
