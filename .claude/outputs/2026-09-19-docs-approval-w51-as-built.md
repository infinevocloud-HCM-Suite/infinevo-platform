# Docs change approval — w51-as-built — 2026-09-19

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-19-docs-diff-w51-as-built.patch` |
| **Status** | **Approved 2026-09-19** by the founder |
| **Why** | `W-51` (#128, `7e6e58e`) built the virtual network and made four architecture decisions that lived only in the ticket spec. One of them contradicts the design document as written: `05-azure-architecture.md` said nothing but Front Door has a public endpoint, while `keyvault.bicep:42` sets `publicNetworkAccess: 'Enabled'`. Someone reading the design document alone was misled. |

## Paths covered

- `docs/target-state/05-azure-architecture.md` @ `0d61d675c20e7f5b6f2f78107aaf5b9acd4052a0`
- `docs/target-state/07-decisions.md` @ `c56d5947094a5e6e5c1113ed8388ca187c68f553`

## What changed

`05-azure-architecture.md` §3 gains a row for the virtual network and its five private
endpoints, and the Front Door row now names the Standard SKU and records that no custom
domains exist yet. §4's rules name the Key Vault exception and note that Postgres is
reached by private endpoint rather than delegated-subnet integration.

`07-decisions.md` gains four dated entries, all made during `W-51`:

| ID | Decision |
|---|---|
| `D-51` | Front Door **Standard**, not Premium — no managed WAF rule sets, no Private Link origins |
| `D-52` | Postgres by **private endpoint**, not delegated subnet. Delegation is a one-way door |
| `D-53` | Key Vault is the single documented exception to "only Front Door is public" |
| `D-54` | Origin protection is `ipSecurityRestrictions`; `X-Azure-FDID` deferred to `W-57`, so the boundary is IP-based, not authentication, until then |

## Not covered by this approval

`docs/target-state/features/W-51-networking-and-identity.md` — merged with `W-51` itself
under gate 5's own-spec exception.
