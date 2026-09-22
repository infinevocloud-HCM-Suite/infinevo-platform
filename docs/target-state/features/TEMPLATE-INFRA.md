# <W-nn> — <NAME>

> Copy to `docs/target-state/features/W-nn-<slug>.md` and fill in.
> **For infra tickets** — anything labelled `skill-INFRA`, `skill-DATA` or `skill-SEC`,
> whose subject is an environment rather than a screen. For customer-facing work use
> `TEMPLATE.md` instead.
>
> The folder is `features/` for both kinds, and that is not cosmetic:
> `check-done.mjs:116` finds the approved spec there, and `:384` allows a ticket's pull
> request to change `docs/` only under `features/W-nn-`. A spec filed anywhere else
> fails two gates. A `docs/` file that is **not** a ticket spec travels its own pull
> request with an approval file — `sync-docs`, and issue #100.
>
> An infra ticket has no actor, no route, and usually no table, so this template drops
> Flow, Frontend changes, and the API contract rather than carrying three sections that
> only ever read "not applicable". If your ticket **does** change the schema, add a
> Database changes section from `TEMPLATE.md` — Flyway only, never `ddl-auto`.

| Field | Value |
|---|---|
| **Work item** | `W-nn` · issue [#n](https://github.com/infinevocloud-HCM-Suite/infinevo-platform/issues/n) |
| **Kind** | Infra / Data / Security |
| **Stream / track** | |
| **Wave** | |
| **Size / skill** | S · M · L — INFRA / DATA / SEC |
| **Owner** | |
| **Blocked by** | |
| **Blocks** | |
| **Capabilities** | `PLAT-nn` |
| **Decisions** | `D-nn` — every decision this ticket is bound by |
| **Gaps addressed** | `DEBT-nnn`, `BUG-nnn` — each fixed, deferred, or discounted in §6 |
| **Status** | **Draft — not approved** |
| **Approved by** | |
| **Approved on** | |

> Hard rule 1: no code is written until this spec is approved.

---

## 1. Problem

What is wrong or missing in the environment today. Cite `file:line`, a gap ID, or a
command and its output. Not a solution.

**Baseline** — measure the current state before changing it, and record it here. A gate,
a threshold, or a migration is only meaningful against a number someone can re-measure.

| Command | Exit | Output |
|---|---|---|
| | | |

## 2. Scope

**In scope**

-

**Out of scope**

- *Name the ticket that owns each excluded thing. "Out of scope" without an owner is
  work that quietly disappears.*

## 3. What gets built

The shape of the thing — a diagram, a job graph, a container set, a schema. Then the
exact files:

| File | Change |
|---|---|
| | |

State plainly what is **not** touched. For infra that list is usually longer than the
list of changes, and it is what makes the blast radius reviewable.

## 4. Proving it

An infra deliverable is usually a gate, a guarantee, or a constraint — so the test is
that it **fails when it should**. A gate only ever observed passing has not been tested.

| # | Deliberate break | What must happen |
|---|---|---|
| | | |

Prove these on a throwaway branch, paste the evidence into the merge commit message,
never merge the breaks.

## 5. Verification

Exact commands `/develop` can run on a clean checkout, with expected output. For a Bicep
ticket that means `az bicep build` and `az bicep lint` — deploying it is the founder's
step, not a check the harness runs.
No "check that it works".

```bash
```

| Check | Expected | Result |
|---|---|---|
| | | |

## 6. Gap disposition

Every gap ID in the header, accounted for. `infra-task` requires each be fixed,
explicitly deferred, or discounted — a gap that is silently dropped is a gap that
returns.

| Gap | Disposition |
|---|---|
| `DEBT-nnn` | Fixed / Deferred to `W-nn` / Discounted because … |

## 7. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| | | |

## 8. Rollback

What to do if it goes wrong. Nothing is in production yet, so say so if rollback is just
a revert — but say what *else* was created that a revert does not remove: an image, a
registry entry, a database role, a cloud resource, a secret.

## 9. Done when

A numbered list. Each item checkable by someone who did not build it.

1.
2.

---

## Decisions needed before implementation

Numbered questions, each with the options and a recommendation. The founder answers
these before any code is written; the answers get recorded back into this file.

**1. <question>**
**(a)** … **(b)** … **Recommend (x)** because …
