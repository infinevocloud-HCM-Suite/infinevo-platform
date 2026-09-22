# W-54 — DEPLOYMENT PIPELINE

| Field | Value |
|---|---|
| **Work item** | `W-54` · issue [#74](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/74) · also closes **#138** |
| **Kind** | Infra |
| **Stream / track** | Stream G — Infrastructure · Track I |
| **Wave** | Wave 2 — Data platform |
| **Size / skill** | **L** (was M; #138 folded in by founder decision) · INFRA |
| **Owner** | KarmaveerM |
| **Blocked by** | `W-50` (#70), `W-03` (#4), #124, `W-56` (#78) — merge order in §3 |
| **Blocks** | `W-63` load test |
| **Capabilities** | `PLAT-11` |
| **Decisions** | `D-10` Container Apps · `D-18` India · `D-19` scale · `D-43` no branch protection · `D-45` Flyway schema · `D-46` no `ddl-auto` |
| **Gaps addressed** | `BUG-004`, `DEBT-002`, `DEBT-003` |
| **Status** | **Approved** |
| **Approved by** | sanjib (founder) |
| **Approved on** | 2026-09-21 |

> **Revision 3.** Revision 1 was written without a spec existing at all — the code came
> first, which is `F-1` of the code review. `/review-spec` returned 4 blockers on it.
> Revision 2 fixed all four, one by splitting #138 out. **The founder reversed that split
> on 2026-09-21**: #138 stays in, the ticket is size L, and the verification revision 2
> handed to #138 comes back here. B-2, B-3 and B-4 remain fixed.

---

## 1. Problem

There is no way to get a built image into Azure. Every container in `rg-infinevo-dev`
still runs a placeholder.

An attempt exists and does not work.

| Command | Exit | Output |
|---|---|---|
| `gh api .../actions/runs/35589172208` | 0 | `conclusion=failure`, `jobs=[]` — the workflow never parsed |
| `gh api .../actions/workflows` | 0 | `deploy.yml` registers as its own file path, not `name: Deploy` |
| `az containerapp show -n ca-infinevo-dev-web ... --query "...image"` | 0 | `mcr.microsoft.com/k8se/quickstart:latest` |
| `az containerapp show -n ca-infinevo-dev-app ... --query "...image"` | 0 | `platform-smoke:latest` |
| `az acr repository list -n crinfinevo` | 0 | `migration-runner`, `platform-smoke` — no application images |
| `az containerapp job execution list -n caj-db-migration-dev` | 0 | 5 runs, 4 Failed, 1 Succeeded |

Four properties the architecture requires are absent, and three are structurally absent —
no amount of fixing `deploy.yml` alone reaches them.

| Required | `05-azure-architecture.md` | State today |
|---|---|---|
| Migration runs as `migration_user` before the new revision takes traffic | `:95`, `:127` | No Flyway runs in Azure at all — **#138, below** |
| New revision starts alongside the old | `:128` | All four apps are `activeRevisionsMode: 'Single'` — `containerapps.bicep:86,145,189,248` |
| Traffic shifts once healthy | `:129` | No traffic block exists |
| Rollback shifts traffic back, in seconds | `:130` | Impossible under `Single` |
| prod deploys the same image, never rebuilt | `:122` | The existing branch rebuilds from source per environment |

**#138 — the migration job applies nothing.** `caj-db-migration-dev` runs
`migration-runner-entrypoint.sh`, which runs `provision.sh` and then the six private-path
probes (`infra/docker/migration-runner-entrypoint.sh:70-77`). There is no Flyway in it.
The image cannot run any: `infra/docker/migration-runner.Dockerfile:23` is an Azure CLI
base with no JRE, and `:51-54` copies only `infra/postgres/` and the probe scripts. The
Flyway runner `W-06` built (`code/backend/migration/.../MigrationApplication.java`) has
never executed outside a test. The job also runs as `infinevo_admin`
(`infra/azure/modules/db-migration-job.bicep:74`) where the architecture requires
`migration_user`.

The job reports success because succeeding at nothing is what it does.

## 2. Scope

**In scope**

*The pipeline (`W-54`)*

- One image build per commit, pushed to `crinfinevo` under an immutable `git-<sha>` tag.
  Every environment deploys that same tag — no rebuild, ever.
- `activeRevisionsMode: 'Multiple'` plus a traffic block on the three apps with ingress.
- Traffic shift on health, and rollback by shifting traffic back.
- `actionlint` in `ci.yml`, because nothing in the repository would have caught the parse
  error that stopped run `35589172208`.
- OIDC federated credentials naming the right GitHub organisation, granted
  **`Container Apps Contributor` + `AcrPush` + `Reader` on the shared group, plus a custom
  `Infinevo ACR Build Queue` role scoped to the registry**, never `Contributor`
  (founder decision 3, amended 2026-09-22 — see Decisions).
- **dev is the only environment proved live.** uat and prod paths are written and gated;
  neither resource group exists.

*Flyway in Azure (`#138`)*

- `migration` role in the backend image: a third jar beside `app.jar` and `worker.jar`,
  selected by `INFINEVO_ROLE=migration`.
- `caj-flyway-{env}` — a Container Apps job running that role, as `migration_user`.
- **Its credential wiring.** `W-56-secrets.md:84` defines `psql-migration-pw`, but
  `:218-223` scopes delivery to the four long-running apps and `caj-db-migration-{env}`
  only. `caj-flyway-{env}` is new here and is in neither list, and W-56 is approved and
  will not grow to cover it. This ticket therefore wires it, using the mechanism W-56
  already provisions (`W-56-secrets.md:73`): the job's own user-assigned identity plus
  `Key Vault Secrets User` on `kv-infinevo-shared`. **No new secret is created** — only a
  reader for one W-56 already defines.

**Out of scope**

| Excluded | Owner |
|---|---|
| Setting Container App *application* secrets from the pipeline | **`W-56`** — it owns the Key Vault contract (`W-56-secrets.md:98,192,218-223`). The pipeline sets none |
| Retiring or changing `caj-db-migration-{env}` | `W-51`/#124 — it keeps its provisioning-and-probes job. The new Flyway job is a sibling, not a replacement |
| Redis / cache deployment | `W-53` — gated behind `deployRedis=false` on #124's branch |
| Queue-depth scaling on the worker | `W-52` |
| Index and query standards | `W-55` |
| Load testing the deployed stack | `W-63` |
| Proving W-50/W-51 against the live environment | **#124** |
| Scheduler locking | `W-52` (`DEBT-021`) |

**A prerequisite that is nobody's code.** The Entra app registration and its federated
credentials. `oidc-setup.sh` creates them; **it has never been run**, and must not be run
in its current form (§7).

## 3. What gets built

```
push to any branch
   │
   └─► CI (existing, unchanged behaviour)  ── fails here, nothing else runs
          │
push to main / dispatch
   │
   └─► Deploy
        gate      resolve environment, require CI green for THIS sha
          │
        build     az acr build × 4  →  crinfinevo/<name>:git-<sha>   (once, ever)
          │
        migrate   start caj-flyway-{env}, as migration_user
                  ── fails here, no revision created, no traffic moves
          │
        release   new revision per app, suffix git-<sha>, 0% traffic
          │
        health    poll THE NEW REVISION by name until Healthy
          │
        shift     traffic 100% → new revision
          │
        verify    assert image, revision name, traffic weight, HTTP 200 through
                  Front Door. Any failure → shift traffic back.
```

| File | Change | Task |
|---|---|---|
| `.github/workflows/deploy.yml` | **New.** Does not exist on `main` | T1 |
| `.github/workflows/ci.yml` | Changed — `actionlint` step in the `static` job | T1 |
| `infra/azure/oidc-setup.sh` | **New.** Does not exist on `main` | T2 |
| `infra/azure/modules/containerapps.bicep` | Changed — `Multiple` + traffic block on `app`, `web`, `keycloak`; web liveness probe 80 → 8080 | T2 |
| `infra/azure/modules/flyway-job.bicep` | **New** — `caj-flyway-{env}` | T2 |
| `infra/azure/main.bicep` | Changed — wire the new module, pass the migration identity | T2 |
| `infra/docker/backend.Dockerfile` | Changed — carry `migration.jar` | T3 |
| `infra/docker/backend-entrypoint.sh` | Changed — accept `INFINEVO_ROLE=migration` | T3 |

**Not touched:** `infra/docker/migration-runner*` (that job keeps its purpose),
`infra/postgres/`, `code/backend/**` (the migration module already exists and is
unchanged — `backend.Dockerfile:25` already builds it in the reactor), anything under
`legacy/` or `docs/`, and every `.properties` file.

### Merge order — three unmerged branches touch the same two files

| Order | Branch | Touches |
|---|---|---|
| 1 | #124 `origin/W-51-azure-dev-live` | `main.bicep` |
| 2 | `W-56` `origin/W-56-secrets` | `main.bicep`, `containerapps.bicep` (+88 lines) |
| 3 | **this ticket** | `main.bicep`, `containerapps.bicep` |

This branch is cut from #124. When W-56 lands it must be rebased, and the
`containerapps.bicep` conflict is expected, not a surprise: W-56 adds `secrets` and `env`
blocks, this ticket changes `activeRevisionsMode`, adds a traffic block and moves one
probe port. Different lines, same file. **Stated so the conflict is resolved knowingly.**

### Standing rules

| Rule | Impact |
|---|---|
| `tenant_id` + RLS on every table outside `reference` (`02-data-model.md:15,17`) | **Creates no table, column or schema.** The Flyway job applies scripts that already exist and are already under `ci.yml`'s tenant and RLS gates (`:249`, `:289`) |
| Flyway only, never `ddl-auto` (`D-46`) | This ticket is the first thing that runs Flyway against Azure. Adds no property |
| `Money`/`BigDecimal` | No money handled |
| Index on `tenant_id` (`DEBT-018`) | No index created — `W-55` |
| Expand/contract, previous release must still run (`05-azure-architecture.md:131`) | **Load-bearing.** Traffic-shift rollback returns old code to a new schema. §7 |
| Nothing under `legacy/` or `docs/` edited | Held |

### Implementer tasks

| Task | Area | Order |
|---|---|---|
| T3 | `infra/docker/` | **First** — the job cannot use a role the image lacks |
| T2 | `infra/azure/` | After T3 |
| T1 | `.github/workflows/` | Any time |

No Maven dependency edge is added — `migration.jar` is a Docker `COPY`.

## 4. Proving it

| # | Deliberate break | What must happen |
|---|---|---|
| 1 | Add `secrets.X` to a job-level `if:` | `actionlint` fails in CI, before merge |
| 2 | Ship a revision whose container exits immediately | Health poll fails, **traffic stays on the old revision**, pipeline red |
| 3 | Point the deploy at a tag that is not in the registry | Release fails, no revision created, no traffic moved |
| 4 | Add a migration script that throws | Pipeline stops at `migrate`; no revision created |
| 5 | Add a migration that applies cleanly | `migration.flyway_schema_history` gains a row with `installed_by = migration_user`. **The #138 regression test** |
| 6 | Point `caj-flyway` at `infinevo_admin` | Check 10 fails on `installed_by` |
| 7 | Delete the `AZURE_CLIENT_ID` variable | The workflow fails loudly; it must not silently skip |
| 8 | Shift traffic back to the previous revision | Serving image reverts in under 30 seconds |

Prove on a throwaway branch. Never merge the breaks.

## 5. Verification

Checks 1–9 run on a clean checkout with no Azure access. Checks 10–14 need a completed
deploy run and a logged-in `az`; the verifier runs them after all three tasks are merged
and one pipeline run has finished, and reports NOT RUN with the reason otherwise.

```bash
# ── static: clean checkout, no cloud ───────────────────────────────────────
# 1 — the workflow is a valid workflow
actionlint .github/workflows/deploy.yml .github/workflows/ci.yml

# 2 — the templates still compile
az bicep build --file infra/azure/main.bicep

# 3 — the three apps WITH INGRESS are Multiple, each with a traffic block.
#     Named explicitly: a bare count of 3 passes if worker is flipped and
#     keycloak is not.
for app in app web keycloak; do
  awk "/ca-infinevo-\\\$\{environment\}-${app}'/,/^}/" \
      infra/azure/modules/containerapps.bicep \
    | grep -qE "activeRevisionsMode: 'Multiple'" \
    && echo "$app: Multiple" || echo "$app: FAIL"
done
grep -c 'traffic:' infra/azure/modules/containerapps.bicep

# 4 — exactly one build stage. Counting occurrences is not enough: a reusable
#     build job called once per environment is still one textual occurrence and
#     still rebuilds. Assert the shape.
node -e '
  const y=require("js-yaml").load(
    require("fs").readFileSync(".github/workflows/deploy.yml","utf8"));
  const bad=Object.entries(y.jobs).filter(([n,j])=>n!=="build"&&
    JSON.stringify(j).includes("acr build")).map(([n])=>n);
  console.log(bad.length?"FAIL: acr build in "+bad.join(","):"PASS: build job only");'

# 5 — the pipeline sets no application secrets (W-56 owns them)
grep -c 'containerapp secret set' .github/workflows/deploy.yml

# 6 — no failure swallowed on a write path. Both forms, not only the combined one.
grep -nE '\|\| *true' .github/workflows/deploy.yml

# 7 — the OIDC subject names the real org, and grants no Contributor
grep -n 'GITHUB_ORG=' infra/azure/oidc-setup.sh
grep -c '"Contributor"' infra/azure/oidc-setup.sh
grep -cE 'Container Apps Contributor|AcrPush' infra/azure/oidc-setup.sh

# 8 — traffic is shifted only after the health gate, by job order
node -e '
  const y=require("js-yaml").load(
    require("fs").readFileSync(".github/workflows/deploy.yml","utf8"));
  const s=y.jobs.shift||{}; console.log("shift needs:",(s.needs||[]).join(","));'

# 9 — the migration role exists in the image and is the job's role
grep -c 'migration' infra/docker/backend-entrypoint.sh
grep -n 'INFINEVO_ROLE' infra/azure/modules/flyway-job.bicep

# ── live: after one completed run ──────────────────────────────────────────
# 10 — Flyway APPLIED something, as the right user. The #138 proof.
#      Job status alone is the signal that is already green on a job that
#      applies nothing, so it is not used here.
psql "$MIGRATION_URL" -At -c \
  "select version, success, installed_by
     from migration.flyway_schema_history order by installed_rank"

# 11 — the deployed image is the tag the pipeline pushed, at 100% traffic
az containerapp revision list -g rg-infinevo-dev -n ca-infinevo-dev-app \
  --query "[?properties.active].{rev:name,img:properties.template.containers[0].image,w:properties.trafficWeight}" \
  -o table

# 12 — the same tag on all four apps: one image, never rebuilt per app
for a in app worker web keycloak; do
  az containerapp show -g rg-infinevo-dev -n "ca-infinevo-dev-$a" \
    --query "properties.template.containers[0].image" -o tsv; done \
  | sed 's/.*://' | sort -u

# 13 — the site actually answers through Front Door
FD=$(az afd endpoint show -g rg-infinevo-shared --profile-name afd-infinevo-shared \
       -n ep-infinevo-dev --query hostName -o tsv)
curl -s -o /dev/null -w '%{http_code}\n' "https://${FD}/"

# 14 — rollback by traffic shift, timed
PREV=$(az containerapp revision list -g rg-infinevo-dev -n ca-infinevo-dev-app \
        --query "[1].name" -o tsv)
time az containerapp ingress traffic set -g rg-infinevo-dev -n ca-infinevo-dev-app \
       --revision-weight "${PREV}=100"
```

| Check | Expected | Result |
|---|---|---|
| 1 | exit 0, no findings | |
| 2 | exit 0, no warnings | |
| 3 | `app: Multiple`, `web: Multiple`, `keycloak: Multiple`, then `3` | |
| 4 | `PASS: build job only` | |
| 5 | `0` | |
| 6 | no matches | |
| 7 | `infinevocloud-HCM-Suite`, then `0`, then `2` | |
| 8 | includes the health job | |
| 9 | at least `1`, and `INFINEVO_ROLE` set to `migration` | |
| 10 | `V001\|t\|migration_user` and `V002\|t\|migration_user` at minimum | |
| 11 | one active revision, tag `git-<sha>`, weight `100` | |
| 12 | exactly one line — one tag across all four | |
| 13 | `200` | |
| 14 | exit 0, under 30 seconds | |

## 6. Gap disposition

| Gap | Disposition |
|---|---|
| `BUG-004` uncontrolled schema drift | **Fixed in the environment.** This is where Flyway first runs against Azure, as `migration_user`. `D-46` and `ci.yml:230` already ban `ddl-auto` in code |
| `DEBT-002` no migration framework | **Fixed for deployment.** `W-06` built the runner; this ticket is what invokes it |
| `DEBT-003` no tests | **Partly.** Breaks 1–8 in §4 are the test. No unit test is possible for a workflow file |
| `DEBT-018` no indexes | Deferred to `W-55` |
| `DEBT-021` unlocked schedulers | Deferred to `W-52` |

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| **Traffic-shift rollback returns old code to a new schema.** Code rolls back in seconds; schema does not | **High** — the ticket's own "Watch" note | Expand/contract is mandatory. Rollback is safe only while the last migration was additive; a destructive one makes rollback a forward fix |
| Size L, three areas, one branch. The founder chose this over splitting #138; `/review-spec` B-1 flagged the risk | Medium | T3 → T2 → T1 ordering, and §4 break 5 is a standalone proof of the #138 half that does not need the pipeline. If round 2 ends with findings open, split there |
| The wrong GitHub org in the existing script is claimable, granting rights on both resource groups | Low today, severe if realised | Fixed in T2. The risk is **latent** — the Entra app does not exist. It becomes live the moment anyone runs the script as written |
| `containerapps.bicep` and `main.bicep` are edited by #124, W-56 and this ticket | Certain | Merge order fixed in §3. Conflict expected and located |
| `uat`/`prod` paths written but never executed | Certain | Stated, not hidden |
| Bicep owns app shape, the pipeline owns image and traffic | Medium | The pipeline sets no property Bicep also sets. Checks 3 and 8 are the guards |

## 8. Rollback

Nothing is in production. Reverting the commit restores the previous state of every file.

What a revert does **not** remove:

| Left behind | How to remove |
|---|---|
| Images in `crinfinevo` tagged `git-<sha>` | `az acr repository delete`. Harmless if left |
| Container App revisions created by a run | Deactivate; they hold no data |
| `caj-flyway-{env}` | `az containerapp job delete`, or leave — it costs nothing idle |
| The Entra app registration and its federated credentials | `az ad app delete`. **Must be removed if a wrong-org credential was ever created** |
| The `Key Vault Secrets User` assignment for the Flyway job | `az role assignment delete` |
| Rows in `migration.flyway_schema_history` | **Not removable by a revert.** Forward-only |

## 9. Done when

Each item names the §5 check that proves it.

| # | Done when | Proved by |
|---|---|---|
| 1 | `actionlint` passes on both workflows and runs in CI on every push | 1 |
| 2 | `az acr build` appears only in the build job; no later job rebuilds | 4 |
| 3 | All four apps run the same `git-<sha>` tag after a run | 12 |
| 4 | `caj-flyway-dev` applies migrations, and `installed_by` is `migration_user` | 10 |
| 5 | A failing migration stops the pipeline before any revision is created | §4 break 4 |
| 6 | `app`, `web` and `keycloak` are `Multiple` with a traffic block | 3 |
| 7 | Traffic is shifted only after the health job | 8 |
| 8 | A crash-looping new revision leaves traffic on the old one and turns the run red | §4 break 2 |
| 9 | Rollback by traffic shift returns the previous image in under 30 seconds | 14 |
| 10 | No `containerapp secret set` and no `\|\| true` on a write path | 5, 6 |
| 11 | `oidc-setup.sh` names `infinevocloud-HCM-Suite`, grants no `Contributor`, and grants `Container Apps Contributor`, `AcrPush`, `Reader` on the shared group and the custom `Infinevo ACR Build Queue` role scoped to the registry | 7 |
| 12 | `ca-infinevo-dev-web` serves HTTP 200 through Front Door | 13 |
| 13 | `az bicep build` on `main.bicep` is clean | 2 |

---

## Decisions — answered by the founder, 2026-09-21

**1. Does W-54 also fix #138, or does #138 ship first?**
**Answered: keep them together.** One ticket, size L. `/review-spec` B-1 recommended the
split and the founder chose against it. The consequence is recorded in §7 row 2, and §4
break 5 keeps the #138 half independently provable so the halves can still be separated
if round 2 stalls.

**2. What happens to `origin/feat/W-54-deploy-pipeline`?**
**Answered: close it, branch fresh from #124.** The env-var contracts and the
resource-name mapping carry over — the code review confirmed both correct.

**3. Which Azure role for the pipeline identity?**
**Answered 2026-09-21: built-in `Container Apps Contributor` + `AcrPush`.** Not
`Contributor`, not a custom role.

**Amended 2026-09-22: a custom role is added, because the built-in answer does not work.**
`AcrPush` contains exactly two actions — `registries/pull/read` and
`registries/push/write`. `az acr build` queues a task on the registry and needs five:
`registries/read`, `listBuildSourceUploadUrl/action`, `scheduleRun/action`, `runs/read`
and `runs/listLogSasUrl/action`. The only built-in role granting `scheduleRun` short of
`Contributor` is `Container Registry Tasks Contributor`, which also grants write and
delete on tasks and agent pools — arbitrary build directives on the registry.

So the grants are: `Container Apps Contributor` on the environment resource group,
`AcrPush` on the registry, `Reader` on `rg-infinevo-shared` for the Front Door lookup,
and a custom role `Infinevo ACR Build Queue` scoped to the registry resource with those
five actions and nothing else. Founder approved the amendment on 2026-09-22.
Revisit at `W-57`.
