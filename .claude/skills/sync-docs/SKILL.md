---
name: sync-docs
description: After a merge, check whether any core document now says something untrue, and bring docs/ back in line. Produces a reviewable diff; only applies it after the founder approves, and only through this skill.
---

# sync-docs

The **only** sanctioned path to change `docs/`. The `guard-edit` hook blocks direct
edits; this skill produces a diff first and applies it only after approval.

**Run it after every merge**, not only when you already suspect drift. Its first job is
to answer one question: *did what we just shipped make any document wrong?* Most of the
time the answer is no, and saying so in one line is a complete run.

## Steps

1. Collect the sources of truth for the change: the merged commit, the `/verify` and
   `/review` reports for the ticket, the `analyze` report's "Doc drift" section, or the
   founder's stated correction.
2. Identify affected docs. **Which side of the line they sit on decides everything:**

   | Doc | Changes when |
   |---|---|
   | `docs/target-state/*.md` | A design decision changes. Add a dated entry to `07-decisions.md`; never rewrite history silently |
   | `docs/target-state/features/W-nn-*.md` | An approved spec is promoted, or the build diverged from it |
   | `docs/CONVENTIONS.md` | A rule for new code changes |
   | `CLAUDE.md`, `CONTRIBUTING.md`, `README.md` | Structure or process changes |
   | `legacy/docs/*` | **Almost never.** These describe a frozen system. They change only if they were wrong about what was frozen |

   `legacy/docs/` is not updated to reflect new work — new work is documented in
   `docs/target-state/`. If you find yourself editing `legacy/docs/ARCHITECTURE.md` to
   describe the new platform, stop: it belongs in `docs/target-state/`.
3. **If nothing is affected, stop and say so in one line.** That is the common case and
   a complete answer. Do not manufacture a change to justify the run.
4. Spawn **explorer** to confirm each fact you intend to write, with `path:line`
   evidence. Never write a path into a doc without confirming the file exists.
5. Write the proposed change as a **unified diff** to
   `.claude/outputs/<date>-docs-diff-<slug>.patch` (create it by editing a copy under
   `.claude/outputs/tmp-docs/` and running `git diff --no-index docs/<file>
   .claude/outputs/tmp-docs/<file>`). Do not touch `docs/` yet.
6. Reply with the summary below. **STOP; wait for "approved".**
7. **On approval, apply the patch.** The guard hook blocks Edit/Write, so apply with
   `git apply .claude/outputs/<patch>` from the repo root via Bash — this is the
   intended bypass; hooks guard the model's editors, not an approved patch. Never delete
   a doc outright: superseded files **move** to `legacy/docs/_archive/`.
8. Verify every markdown link still resolves, and paste the result:

   ```bash
   python -c "
   import io,os,re
   bad=[]
   for b,d,f in os.walk('.'):
       d[:]=[x for x in d if x not in ('.git','node_modules','target','dist')]
       for n in f:
           if not n.endswith('.md') or '-session-' in n: continue   # session logs are untracked
           p=os.path.join(b,n)
           try: s=io.open(p,encoding='utf-8').read()
           except Exception: continue
           for m in re.finditer(r'\]\(([^)#\s]+?)(?:#[^)]*)?\)',s):
               t=m.group(1)
               # Skip what is not a file path on disk:
               #   http/mailto, and GitHub-relative links like ../../issues, which
               #   resolve on github.com and nowhere else. Flagging them trains people
               #   to ignore the check, and then it checks nothing.
               if t.startswith(('http','mailto','../../')): continue
               if not os.path.exists(os.path.normpath(os.path.join(b,t))): bad.append(p+' -> '+t)
   print(len(bad),'broken'); [print(' ',x) for x in bad]"
   ```

9. Update `.claude/work/active-work.md` if direction, ready tickets or in-flight work
   changed. **STOP.**

---

## What the founder reads

Plain English. Name the document, say what it claims today, and what it should claim —
not a diff summary.

```
Docs affected by W-nn: <n>

| Document | Says now | Should say |
|---|---|---|
| docs/target-state/02-data-model.md | Leave balance lives on the employee row | It has its own table, with tenant_id |
| docs/CONVENTIONS.md | nothing about accrual rounding | Round leave days to two places, like money |

Patch: .claude/outputs/<date>-docs-diff-<slug>.patch
Anything I could not confirm: <list, or "none">

Approve and I apply it.
```

When nothing drifted, the whole reply is one line:

```
Docs checked against W-nn — nothing out of date. No change needed.
```

---

## The route to `main`

Applying the patch locally changes nothing for anyone else. `docs/` reaches `main` the
same way code does — through `/merge`.

```bash
git checkout -b docs-<slug>          # no W followed by digits in the name - see below
git add docs/
git commit -m "docs — <what changed> (approved <date>)"
/merge docs-<slug>
```

**Name a docs branch `docs-<slug>`.** The done check reads the branch name to decide
what a branch is allowed to touch: a `W-nn` branch may change only its own ticket spec,
and a `docs-<slug>` branch may change only `docs/`. That is the whole rule, and it
exists so nobody rewrites the design documents while shipping a feature — a docs change
travels on its own, reviewed for what it says rather than waved through with a feature.

The branch test is `/w[-_. ]?(\d{1,4})/i`, unanchored, so `W-04-x`, `w-04-x`,
`feature/W-04-x` and `W04-x` all read as ticket W-04. It over-matches on purpose:
`flow12` contains `w12` and is read as a ticket branch. The misfire is cheap and legible
— the gate names the ticket it thinks you are on, and the fix is to rename the branch —
while the opposite error is a docs edit nobody sees.

The one thing needing no docs branch: a ticket promoting **its own** spec into
`docs/target-state/features/W-nn-<slug>.md` on its own `W-nn` branch. That is the spec
arriving where the plan says it lives, and the spec gate already checked it is approved.
