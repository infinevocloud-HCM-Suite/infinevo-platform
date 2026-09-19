# Spec review — W-51 — networking-and-identity — 2026-09-19

Draft: `docs/target-state/features/W-51-networking-and-identity.md` on branch `origin/w-51-net`
(502 lines, `Status: Draft — not approved`). Ticket #71, labels `stream-G, size-M, skill-INFRA, ready`.
First pass — no earlier `review-spec` output for W-51 exists.

## Verdict

**NOT READY**

## Checks

| # | Check | Result | Notes |
|---|---|---|---|
| 1 | Citations resolve | 5 of 6 | No `legacy/` citations, so no explorer sweep. `05-azure-architecture.md:28`, `:63-75`, `D-18`, `D-19`, `D-22`, `D-48` all resolve. `SEC-001` does not exist |
| 2 | Template complete | 9 of 9 sections + Decisions | `TEMPLATE-INFRA.md` is the right template (`skill-INFRA`). §2 out-of-scope is a real bounded list. No database objects created, so no Database changes section is owed (`TEMPLATE-INFRA.md:14-15`) |
| 3 | Gaps and standing rules | Pass | No new entities, tables or money fields. Flyway only, no `ddl-auto`. `DEBT-004` disposed; `SEC-001` is not a tracked ID |
| 4 | Work is buildable | **5 blockers** | Every §5 criterion is Bash-runnable with expected output — no human-eyeball checks. The failure is elsewhere: four of the things §1 names as the problem are asserted, never attempted |

## Citations

| Cited | Exists? | Line holds | Verdict |
|---|---|---|---|
| `05-azure-architecture.md:28` | yes | `\| rg-infinevo-shared \| Container registry, Key Vault, Log Analytics workspace, DNS zone, Front Door \| Permanent, one only \|` | OK |
| `05-azure-architecture.md:63-75` | yes | Front Door + WAF topology diagram and the "Only Front Door is public" rule table | OK |
| `D-18` India region | yes | `07-decisions.md:30` | OK |
| `D-19` 10 × 100 scale | yes | `07-decisions.md:31` | OK |
| `D-22` deny-by-default | yes | `07-decisions.md:34`, `05-azure-architecture.md:149` §7a | OK |
| `D-48` unified backend image | yes | `07-decisions.md:59` | OK |
| `SEC-001` | **no** | `GAP_INVENTORY.md` holds `BUG-001`–`007` and `DEBT-001`–`033` only | Note |

## Blockers

| ID | Blocker | Where (draft §) | What closes it |
|---|---|---|---|
| **F-1** | **Front Door and the WAF are declared, never exercised.** §1 problem 2 is "no WAF, rate limiting or edge TLS". §5 step 4 checks the profile SKU string, the WAF `provisioningState`, and that a security policy references the WAF id. Not one command in the whole script sends a request *through* Front Door. A profile with no route, a route pointed at the wrong origin group, or a WAF whose custom rules never match all pass step 4 while ingress is broken or unprotected | §5 step 4, lines 327-339; §9 item 6, line 482 | A `curl` against the default endpoint `afd-infinevo-shared.azurefd.net` (needs no DNS decision) asserting `/`, `/api/*` and `/auth/*` each reach the intended origin, plus one request that a custom WAF rule must reject — exceed the rate-limit threshold or trip the size rule — asserting `403` |
| **F-2** | **Origin protection has no implementation site inside the declared scope.** `X-Azure-FDID` validation is the only thing standing between the internet and the Container Apps (§2.4, §3d, §9.7). Container Apps ingress supports `ipSecurityRestrictions`, not header matching — so the check has to live in the application or a proxy, and §3c puts `code/backend/` and `infra/docker/` explicitly out of scope. All four apps still run the public starter image (`infra/azure/modules/containerapps.bicep:66,118,165,223`), which will answer `/health` with something other than `403`. §5 step 8 therefore cannot pass as written, and the implementer is sent into scope the spec forbids | §2.4 line 100; §3b line 204; §3d line 227; §5 step 8 lines 402-407 | Name where the header check executes and bring that file into §2 and §3b — or drop to `ipSecurityRestrictions` on the Front Door backend ranges and rewrite §9.7 and §5 step 8 to match what is actually enforced |
| **F-3** | **Disabling Key Vault public access breaks the deployment that depends on it.** §2.3 and §9.4 set `publicNetworkAccess: 'Disabled'` on `kv-infinevo-shared`, whose only private endpoint sits inside `vnet-infinevo-{env}`. But `deploy.sh:89` and `:120` and `post-deploy-db.sh:58,106,112` are Key Vault **data-plane** calls issued from CI or a laptop outside the VNet, and they run before the migration job can exist. No trusted-services bypass, firewall exception, self-hosted runner or ordering is named anywhere, so §9 item 2 — "`deploy.sh --env dev` provisions … in a single command" — cannot hold. §5 reads Key Vault only over the control plane, which keeps working, so verification stays green | §2.3 line 87; §9 items 2 and 4, lines 478-480 | Name the bypass mechanism in §2, and add a §5 step that performs one data-plane secret write from the same runner the pipeline uses, after lockdown |
| **F-4** | **The private data path for Redis, Service Bus and Blob is declared, never exercised — and so are three of the role assignments.** §5 step 2 reads control-plane properties; step 3 checks a DNS zone object exists and has a link. A private endpoint bound to the wrong subresource, or a zone carrying no A record, passes both while every consumer is cut off — the defect first surfaces at `W-52`/`W-53`. Postgres and Key Vault escape this because the migration job (step 6) genuinely uses them; Redis, Service Bus and Storage have no equivalent. Step 5 has the same shape for `Storage Blob Data Contributor` and the two Service Bus roles: the assignment is listed, never used | §5 steps 2, 3 and 5, lines 294-363; §9 items 4 and 8, lines 480-484 | One in-VNet command per service — from the migration job — that resolves the private FQDN to a `10.x` address and completes a data-plane operation with the UAMI token: a blob write, a queue send, a Redis `PING` |
| **F-5** | **VNet-injecting the Container Apps environment is destructive and unsequenced.** `containerapp-env.bicep` has no `vnetConfiguration` today, and `infrastructureSubnetId` is immutable — `cae-infinevo-{env}` must be deleted and recreated, taking all four Container Apps with it. §7 and §8a confront exactly this for PostgreSQL and say nothing about the environment, and the ordering is circular: the migration job that bootstraps the private database lives inside the environment being replaced. Nothing in §5 asserts the environment ended up VNet-injected at all | §3b line 203; §7 lines 438-443; §8a lines 449-462 | An §8a step ordering environment recreation against the Postgres recreation and the migration job's own creation, plus a §5 assertion that `cae-infinevo-dev` reports a non-empty `properties.vnetConfiguration.infrastructureSubnetId` |

## Notes

- `SEC-001` (header line 18, §6 line 432) is not a tracked gap — `legacy/docs/GAP_INVENTORY.md` holds `BUG-001`–`007` and `DEBT-001`–`033`. Either cite the real ID for the exposed database or drop the row; `DEBT-004` carries the weight either way.
- §5 step 6 polls 30 × 5s = 150s, while §3e sets the job timeout to 900s. A legitimately slow first migration fails verification.
- §5 step 5's `az role assignment list --assignee` omits resource-scope assignments unless `--all` is passed; every role in §3f is resource-scoped.
- `05-azure-architecture.md:155` records health and readiness as "not routed through Front Door". §5 step 8 uses `/health` as its bypass probe — worth choosing a different path so the two documents do not disagree.
- The migration job re-runs `provision.sh` on every verification run; §3e does not say it is idempotent.

## What is good

- The §1 baseline table is measured, not asserted — seven commands with exit codes and real output. That is the right way to open an infra spec and it made this review faster.
- §8a faces the PostgreSQL Flexible Server networking immutability directly, with a backup-restore-validate-retire sequence. Most specs discover that constraint during implementation. The gap is that the same reasoning was not extended to the Container Apps environment (F-5), not that the reasoning is missing.
- The Front Door SKU is argued rather than assumed — Standard vs Premium with the cost delta, the two capabilities given up (managed rule sets, Private Link origins), and the consequence of each named in §7. The trade-off is the founder's to take, and it is presented so they can.
- §2's out-of-scope table names a ticket for every exclusion, and §3f is an explicit per-identity, per-resource role matrix rather than a prose summary.
