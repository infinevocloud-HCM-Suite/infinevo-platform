---
name: test
description: Write or extend tests for what a ticket changed, and nothing else. Adds tests; never changes behaviour to make one pass.
---

# test

Invoke as `/test W-nn`.

Writes the tests for what this ticket changed. It is separate from `/develop` so that
tests are a deliverable rather than the thing that gets dropped when time is short, and
separate from `/verify` because writing a test and running one are different jobs.

**The hard limit: this skill adds tests. It does not change behaviour.** If a test you
write fails, that is a finding — report it. Editing the code to make your own test pass
is how a suite ends up asserting whatever the code happens to do.

---

## Steps

1. Read the ticket's spec, particularly **§7 Tests** and **§9 Verification**. The spec
   already says what should be covered; start there rather than inventing a list.
2. `git diff --name-only origin/main..HEAD` — the scope is exactly this, nothing wider.
   Untested code elsewhere is real, but it is `W-04`'s problem or its own ticket.
3. Write the tests. Mirror the package under `src/test/java`.
4. Run them. Report honestly:
   - **All pass** → say so, hand over to `/verify`
   - **One fails** → report it as a finding. **Do not fix the code.** `/develop` fixes
   - **Cannot be tested** → say why, plainly. "No test runner for this" is an honest
     answer; a test that asserts nothing is not

---

## What to test

| Change | Test |
|---|---|
| A service method | Unit test, mocked repository. Every branch that matters, and the failure path |
| A repository query | **Integration test against real Postgres.** Never an in-memory database — row-level security is never exercised otherwise |
| A new table | That `tenant_id` is present, and that a query from tenant A cannot see tenant B's row |
| Money arithmetic | Rounding at the boundary, scale, and `0.00` comparing equal to `0`. See `MoneyTest` |
| An endpoint | Authenticated and unauthenticated. The unauthenticated case matters more |
| Infrastructure | A script that proves the property, like `infra/docker/smoke.sh` |

## Two tests worth more than the rest

**The negative one.** That the endpoint refuses an unauthenticated caller. That tenant A
cannot read tenant B. That `app_user` cannot run DDL. These are the ones that catch the
defects that matter, and they are the ones people forget.

**The one that proves the test itself works.** A check that passes because it matches
nothing is worse than no check — it reports green forever. When you write a guard,
break the thing deliberately once and watch it fail, then put it back. `smoke.sh`'s
`ddl-auto` check passed on a comment for an hour before anyone noticed.

---

## Conventions

- JUnit 5, mirroring the package. AssertJ for assertions
- `@Nested` classes to group. **Keep every test inside a nested class if any are** —
  Surefire reports an outer class's own methods under the first nested class, which
  makes the counts read wrongly
- Name the behaviour, not the method: `refusesAnUnauthenticatedCaller`, not `testGet`
- `@DisplayName` when the method name cannot carry it
- No `Thread.sleep`. No test that depends on another test's order

## Finishing

```bash
cd code/backend && ./mvnw -B test
```

Report the count, and whether any test is new. Then `/verify W-nn`.
