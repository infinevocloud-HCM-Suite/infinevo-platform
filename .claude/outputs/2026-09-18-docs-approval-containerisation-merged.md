# Docs change approval — containerisation-merged — 2026-09-18

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-17-docs-diff-w49-merged.patch` |
| **Status** | **Approved 2026-09-18** by the founder |
| **Why** | `W-49` merged to `main` as `c0a8643` on 2026-09-17. The design documents still said the deployable images did not exist, described one fat jar toggled by a Spring profile where two jars are selected by `INFINEVO_ROLE`, and said the Keycloak image ships with a realm pre-built — which is the exact thing the ticket removed. |

## Paths covered

- `docs/target-state/04-runtime-containers.md` @ `aad30747256bcd5cba53b4ec2ca01d879c969e48`
- `docs/target-state/07-decisions.md` @ `6c42ef5bf8e1a4d5c8528213e3103c184a129662`
- `docs/target-state/08-work-plan.md` @ `b264cc773eff18f21640d7e663bb020790b0611d`
- `docs/target-state/09-build-order.md` @ `a6d0e8e652d9b4084b82e5337514e18dff0b4a64`
- `docs/target-state/10-scoping.md` @ `185c32f1b26872691e00421c2b499d11ce9985aa`
- `docs/target-state/features/W-02-local-stack.md` @ `7dca1abd314c89f22f5696de420aeb8c26c97c1b`
- `docs/target-state/features/W-03-build-test-pipeline.md` @ `5852234be1cf4ae05c81d47a534f4fe41e6200a9`
- `docs/target-state/features/W-59-scanning.md` @ `0919105c16fdb3ffc9f7c8e17baea787ed60d921`

## What changed, per document

| Doc | Change |
|---|---|
| `04-runtime-containers.md` | Images exist as of `c0a8643`. Two jars, not one; role selected by `INFINEVO_ROLE`, not a Spring profile. No realm or theme baked into the Keycloak image. Stage 3 copies both jars and exposes 8080 and 8082. Runtime environment injection marked implemented, with the escaping requirement stated |
| `07-decisions.md` | Three appended: `D-47` image size measured as content size with the containerd store explicitly enabled on CI; `D-48` two jars selected by `INFINEVO_ROLE`, superseding the `Spring profile` row of `04` §3; `D-49` unprivileged nginx on 8080. All three were confirmed by the founder on issue #69 on 2026-09-15 but had never been recorded here |
| `08-work-plan.md` · `09-build-order.md` · `10-scoping.md` | `W-49` marked merged 2026-09-17 (#116); `W-50` shown unblocked and ready; "profiles" wording corrected to roles |
| `W-02-local-stack.md` | Out-of-scope row marked delivered; the risk "the dev Dockerfiles drift into being the production build" marked Closed — `W-49` wrote the real ones and changed neither dev file |
| `W-03-build-test-pipeline.md` | Flow diagram updated to five builds plus gates; the `(provisional)` risk closed; Q3 gains an "As built" note correcting "the single web/worker image" |
| `W-59-scanning.md` | The three production images added to the baseline with their measured sizes. **The "report-only until `W-49`" premise has expired**, so whether the container scan blocks is now W-59's decision rather than an inherited deferral. The Keycloak primary-gid-0 question is written down: `keycloak.Dockerfile:58-63` hands it to `W-59`, and until now W-59 said nothing back |

## Not included, deliberately

| Left out | Why |
|---|---|
| `README.md` ticket counts ("Two of ninety-three tickets are done"; `W-49` listed as ready) | Real drift — actual is 12 closed, 89 open — but it predates `W-49` and is broader than this change. Its own pass |
| `11-ways-of-working.md:161`, Dev-1 assigned to `W-49` | Historical "first week, concretely" framing, defensible as history |
| `CONTRIBUTING.md` production-image build instructions | New content rather than drift correction |
