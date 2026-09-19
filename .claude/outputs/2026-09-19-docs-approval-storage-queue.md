# Docs change approval — storage-queue — 2026-09-19

| Field | Value |
|---|---|
| **Patch** | `.claude/outputs/2026-09-19-docs-diff-storage-queue.patch` |
| **Status** | **Approved 2026-09-19** by the founder |
| **Why** | Azure Storage Queue replaces Azure Service Bus for the job queue: a private endpoint on Service Bus is Premium-tier only, roughly ten times Standard, which `D-19` scale does not justify. The design documents named Service Bus in three places and would otherwise send the next reader to build `W-52` against a product no longer provisioned. |

## Paths covered

- `docs/target-state/04-runtime-containers.md` @ `7dd1ac84a0e819ca1f51e5be9428abf44f6cc481`
- `docs/target-state/05-azure-architecture.md` @ `8a692fe420965cae827f6944d074ee094eac9ecc`
- `docs/target-state/07-decisions.md` @ `5aa4ccad92574a7562a4a8538bd2513110119e2b`

## Not covered by this approval

`docs/target-state/features/W-51-networking-and-identity.md` is the ticket's own spec and
travels on the `W-51` branch under gate 5's own-spec exception, not through this approval.

`08-work-plan.md:134` and `09-build-order.md:265` still name Service Bus in **`W-50`'s**
rows. That is deliberate — `W-50` did build a Service Bus namespace, and those rows record
what was built. `D-50` is what records the supersession.
