---
name: review
description: Review a pull request against its spec's acceptance criteria. Reports numbered findings and fixes nothing. Reads for what verify cannot run.
---

# review

Invoke as `/review <pr-number>`.

Answers a different question from `/verify`. Verify asks *does it work* by running
things. **Review asks *is it right* by reading.**

Both are needed. `W-02` passed 23 of 23 while its Keycloak admin login returned 401 —
the test checked the realm endpoint, which is independent of the admin user existing.
Only reading the diff against the documentation found it.

**This skill fixes nothing.** Findings go to `/develop`.

---

## Steps

1. `gh pr view <pr> --json title,body,files,headRefName` and `gh pr diff <pr>`.
2. Read the ticket's spec. **Its §9 verification table and §13 done-when list are the
   standard.** Not your taste — disagreements about approach belong at spec approval,
   not here.
3. Read the whole diff. Not the summary, the diff.
4. Write the report and post a summary comment on the pull request.

---

## What to look for, in order of how often it bites

### 1. Does the documentation match the code?

The highest-yield check, and the one automation misses. **Every credential, command,
port and URL that a README or spec states, try it.** Documented-but-broken is worse
than undocumented — someone will trust it.

### 2. Does the spec's acceptance table actually hold?

Walk §9 row by row. A row marked ✅ with no evidence beside it is not a pass.

### 3. What did the tests not cover?

- The negative case: unauthenticated, cross-tenant, empty, zero, null
- **Does the new check catch a real violation?** A guard matching nothing reports green
  forever. Look for evidence it was proven, not just written
- Did a test get written to match the code's behaviour rather than the requirement?

### 4. The standing rules

| | |
|---|---|
| `tenant_id` and a policy on every new table outside `reference` | |
| Flyway for every schema change. `ddl-auto` nowhere | |
| `Money` or `BigDecimal` for money, never `double` or `float` | |
| No module referencing another module | |
| New endpoints authenticated, or on the exception list | |
| Nothing under `legacy/` or `docs/` edited, except this ticket's spec | |

### 5. Things that are correct today and wrong later

The ones that cost most, because they surface months on:

- A global default that will be wrong for a case that does not exist yet — a
  `default_schema` in a four-schema design, a hardcoded tenant, a single-region assumption
- Dead configuration: a mount nothing reads, a dependency nothing uses, an environment
  variable nothing consumes
- A dependency that couples startup without need — an API that will not start because a
  cache is down
- Version-specific configuration with no comment saying which version it needs

### 6. Scope

Did the pull request do only what the spec said? Extra work is not a bonus — it is
unreviewed, unspecified change riding on an approval that did not cover it.

---

## The report

Write to `.claude/outputs/<date>-review-pr-<n>.md`, same finding format as `/verify`
so `/develop` and `check-done.mjs` can read both.

```
# Review — PR #<n> — W-nn — <date>

## Verdict
APPROVE / APPROVE WITH FINDINGS / CHANGES REQUIRED

## Spec acceptance
| # | Criterion | Met? | Evidence |

## Findings
| ID | Severity | Finding | Where (path:line) | Status |
| F-1 | High | <one sentence> | <file:line or command output> | OPEN |

## What is good
<Say it. A review that only lists faults teaches nothing about what to repeat.>
```

| Severity | Means |
|---|---|
| **High** | Wrong behaviour, a security hole, or documentation that does not work. **Blocks the merge** |
| **Medium** | A real defect with a workaround, a missing test, a trap for later |
| **Low** | Cosmetic, dead configuration |

Cite `path:line` for every finding. A finding without a location is an opinion.

---

## Reviewing your own work

You will often be reviewing something you wrote. **Reread the diff as though someone
else wrote it, and go looking for what you would have got wrong** — the thing you did
not test, the version you assumed, the documentation you wrote before the code changed.

Doing this on `W-02` found five defects in an hour, one of them a documented login that
had never worked. Self-review is weaker than a second pair of eyes; it is far stronger
than none.

## Finishing

- **CHANGES REQUIRED** or any High finding → `/develop W-nn`
- **APPROVE** → `/merge <pr>`
