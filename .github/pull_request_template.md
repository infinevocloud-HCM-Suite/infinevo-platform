<!--
  One pull request per ticket. It is reviewed against the spec's acceptance criteria,
  not against personal taste — disagreements about approach belong at spec approval.
-->

Closes #

**Spec:** `docs/target-state/features/W-nn-<slug>.md`

## What changed

<!-- Two or three lines. What a reviewer needs before reading the diff. -->

## Logic ported from `legacy/`

<!--
  Cite file:line for anything carried over, so the reviewer can check it was ported
  rather than reinvented. Write "none" if this is new work.
-->

## Verification

<!-- Paste the real output. "It works" is not evidence. -->

```
$ cd backend && ./mvnw -q verify

$ cd frontend && npm run lint && npm run build
```

## Definition of done

- [ ] Pipeline green — compile, lint, tests
- [ ] Tests written for what changed
- [ ] New tables carry `tenant_id` and a row-level security policy, unless in `reference`
- [ ] New endpoints authenticated, or added to the reviewed exception list
- [ ] Indexes added for the queries introduced
- [ ] Schema changes are Flyway scripts — no `ddl-auto`
- [ ] Money is `Money` or `BigDecimal` — no floating-point type
- [ ] No module references another module — only `core`
- [ ] Nothing under `legacy/` or `docs/` was edited
- [ ] The spec updated to match what was actually built

<!--
  The last box matters more than it looks. A spec that drifts from the code is worse
  than no spec, because the next person trusts it.
-->
