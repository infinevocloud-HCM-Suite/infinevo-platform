# Legacy documentation — reference only

**Everything in this folder describes the four frozen applications.** It is not a
specification for anything being built.

If you are implementing a ticket, the documents you build against are in
[`../target-state/`](../target-state/) and [`../CONVENTIONS.md`](../CONVENTIONS.md).
You come here to answer *"how does it work today"* before porting logic out of
[`legacy/`](../../legacy/).

---

## The distinction that matters

| | |
|---|---|
| [`../target-state/`](../target-state/) | **Where we are going.** Build against this |
| `docs/legacy/` (here) | **How the frozen apps work.** Read, understand, port from. Never treat as a rule |

A concrete example of why this matters: `DB_SCHEMA.md` here documents 131 tables
managed by `ddl-auto=update` across two MySQL databases. The target is 130 tables
across four Postgres schemas under Flyway. Reading the wrong one produces a
correct-looking design for the wrong system.

---

## What is here

| Document | Describes |
|---|---|
| `ARCHITECTURE.md` | System shape, ports, and cross-service flow of the four apps |
| `DB_SCHEMA.md` | The old schema — 131 tables across `hrmstestdb` and `payrollDB` |
| `FEATURE_MAP.md` | Which files implement which feature, per app. **The fastest way into `legacy/`** |
| `GAP_INVENTORY.md` | Known defects and debt in the frozen system |
| `_archive/` | Superseded documents, kept for provenance. Nothing here is current |

---

## Where it is used

Start at `FEATURE_MAP.md` when you need to find how something works today, then
read the code under [`legacy/`](../../legacy/). Grepping blind is slower.

Three of these documents also carry warnings that still apply when reading the
frozen code — the load-bearing package-name typos, the entities that look
duplicated but are not, and the endpoints with no authentication. Those are
summarised in [`../target-state/06-current-to-target.md`](../target-state/06-current-to-target.md) §5.

---

## Lifetime

This folder is deleted with `legacy/` at cutover. Until then it stays accurate to
the freeze, and is never updated to describe new work — new work is documented in
`docs/features/` and `docs/target-state/`.
