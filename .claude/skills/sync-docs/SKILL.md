---
name: sync-docs
description: Bring docs/ back in line with the code after a change or when analyze found drift. Produces a reviewable diff; only applies it after the founder approves, and only through this skill.
---

# sync-docs

The **only** sanctioned path to change `docs/`. The `guard-edit` hook blocks direct edits;
this skill produces a diff first and applies it only after approval.

## Steps
1. Collect the sources of truth for the change: the verifier report / merged diff, the
   `analyze` report's "Doc drift" section, or the founder's stated correction.
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
3. Spawn **explorer** to confirm each fact you intend to write, with `path:line` evidence.
   Never write a path into a doc without confirming the file exists.
4. Write the proposed change as a **unified diff** to `.claude/outputs/<date>-docs-diff-<slug>.patch`
   (create it by editing a copy under `.claude/outputs/tmp-docs/` and running `git diff
   --no-index docs/<file> .claude/outputs/tmp-docs/<file>`). Do not touch `docs/` yet.
5. Reply with the patch path, a table `| Doc | Lines | What changes | Evidence |`, and any
   entry you could not verify (marked so). **STOP; wait for "approved".**
6. **On approval, apply the patch first** — the approval file has to name a digest of
   each file's approved content, so that content must exist before it can be written.
   The guard hook blocks Edit/Write, so apply with `git apply .claude/outputs/<patch>`
   from the repo root via Bash (this is the intended bypass — hooks guard the model's
   editors, not an approved patch). Never delete a doc outright: superseded files **move**
   to `legacy/docs/_archive/`. That move is still a deletion from `docs/`, and it is
   approved by writing `` `deleted` `` where the sha would go — see step 8.
7. **Stage the docs, then take a blob sha for each one.** `git rev-parse :<path>` reads
   the staged content, which is exactly what the commit will carry:

   ```bash
   git add -A docs/
   git diff --cached --name-status -- docs/ | while read -r st f rest; do
     case "$st" in
       D*) echo "- \`$f\` @ \`deleted\`" ;;                       # archived / removed
       R*) echo "- \`$rest\` @ \`$(git rev-parse ":$rest")\`"
           echo "- \`$f\` @ \`deleted\`" ;;                       # moved: both halves
       *)  echo "- \`$f\` @ \`$(git rev-parse ":$f")\`" ;;
     esac
   done
   ```

   A **deletion is approved by the word `deleted`**, not by a sha — there is no content
   left to hash, and before this the archive move documented in step 6 could not be
   merged at all. The gate checks it exactly as it checks a sha: the file must really be
   gone at `HEAD`, and an approval reading `deleted` for a file that is still there is
   refused.

8. **Write the approval file**, `.claude/outputs/<date>-docs-approval-<slug>.md`, using
   those bullet lines verbatim. This is what gate 5 of the done check reads, and a
   `docs/` change with no such file **newly added** in its pull request cannot reach
   `main`.

   ```markdown
   # Docs change approval — <slug> — <date>

   | Field | Value |
   |---|---|
   | **Patch** | `.claude/outputs/<date>-docs-diff-<slug>.patch` |
   | **Status** | **Approved <date>** by the founder |
   | **Why** | one line: what changed in the world that these documents must now say |

   ## Paths covered

   - `docs/target-state/features/TEMPLATE-INFRA.md` @ `<40 hex blob sha>`
   - `docs/CONVENTIONS.md` @ `<40 hex blob sha>`
   ```

   Five things the gate insists on, each of them because it was got round once:

   | Rule | Why |
   |---|---|
   | The path is matched **exactly** and must be a plain path under `docs/` | A directory (`docs/target-state/`) authorises nothing — list each file, because the point is that a human read each file. `..` segments, backslashes and quotes are refused outright |
   | Backticks round the path, then `@`, then a full **40-hex blob sha**, in backticks too | The backticks are what let a path containing a space be approved at all. An abbreviated sha is refused |
   | Only bullets under the `## Paths covered` heading count, at the **left margin** (up to three spaces), and text that markdown hides — a fenced block, an HTML comment, anything indented four spaces or a tab — asserts nothing | Otherwise the template printed above authorises `CONVENTIONS.md` the moment somebody pastes it unedited; a path under a heading reading "Explicitly NOT approved" is approved anyway; and `<!-- **Approved** -->`, invisible in every rendered view, marks the file approved |
   | An **indented line anywhere under `## Paths covered` is refused outright** — the whole approval, not just that line | Four spaces is a code block after a blank line and a list continuation inside a list, and the gate does not try to tell them apart. It refuses and names the line; unindent it, or move it out of the section |
   | The approval must be **added** by the pull request, never merely edited | Touching an approval that already reached `main` would re-open every path it lists, for brand-new content |

   If the docs change after you take the shas — a review comment, a typo fix — **take
   them again.** The gate compares each recorded sha against the file at `HEAD` and
   refuses on any difference. That is the point: the approval is for those bytes, and
   a fresh sha means a fresh look.
9. Verify every markdown link still resolves, and paste the result:

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

10. Update `.claude/work/active-work.md` if direction, ready tickets or in-flight work
    changed. **STOP.**

---

## The route to `main`

Applying the patch locally changes nothing for anyone else — `docs/` reaches `main`
the same way code does, through a pull request and `/merge`.

```bash
git checkout -b docs-<slug>          # no W followed by digits in the name - see below
git add docs/ .claude/outputs/<date>-docs-approval-<slug>.md
git commit -m "docs — <what changed> (approved <date>)"
gh pr create --title "docs — <what changed>" --body "...

Closes #<issue>"
```

**The approval file must be ADDED by the pull request's own diff.** An approval sitting
in `.claude/outputs/` from a previous change authorises nothing, and touching it does not
revive it — otherwise the first one ever written would authorise every docs change after
it.

**Which half gate 5 reads from where.** The *filename* comes from the pull request's diff
(`gh pr view --json files`, which also says whether the PR added it). The *body* is read
from the working tree, and the approved content is checked against `git rev-parse
HEAD:<path>` in that same checkout. So the check has to run **on the pull request's head
commit** — which is what `/merge` does. The gate checks that for itself before it reads
any digest: it compares `git rev-parse HEAD` against the PR's `headRefOid` and, if they
differ, names both shas and tells you to `gh pr checkout` rather than reporting the
content as drifted. That distinction matters — "content changed since approval" invites
you to re-approve, and re-approving from a stale tree approves the wrong bytes.

**No `W` followed by digits anywhere in the branch name.** The exact test is
`/w[-_. ]?(\d{1,4})/i`, unanchored and case-insensitive, so every one of `W-04-x`,
`w-04-x`, `feature/W-04-x`, `fix/W-04-x`, `myW-04-x` and `W04-x` is read as ticket W-04.
Gate 5 then stays strict and allows only that ticket's own
`docs/target-state/features/W-nn-*.md` — an approval file does **not** widen it. That is
deliberate: the gate exists so nobody rewrites the design documents while shipping a
feature. A docs change travels as its own pull request, reviewed for
what it says rather than waved through with a feature.

That pattern deliberately over-matches, and there is no boundary in front of the `W`:
`show-04-fix` contains `w-04` and `flow12` contains `w12`, so both are read as ticket
branches. The misfire is cheap and legible — the gate says "branch … is ticket W-04" and
the fix is to rename the branch — while the opposite error is a docs edit nobody sees.
**Name a docs branch `docs-<slug>`** and none of this arises.

A pull request with no branch name at all — which only happens when `gh pr view` failed —
is refused outright rather than handed the permissive route, and so is a name that opens
`W-` with no number behind it (`W-`, `w-tenant`): it is ticket-shaped, names no ticket,
and the permissive route is not the answer to that ambiguity.

The one exception needs no approval file: a ticket promoting **its own** spec into
`docs/target-state/features/W-nn-<slug>.md` on its own `W-nn` branch. That is the spec
arriving where the plan says it lives, and gate 2 already checked it is approved.
