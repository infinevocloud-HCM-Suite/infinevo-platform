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
6. **On approval, record the approval before touching anything.** Write
   `.claude/outputs/<date>-docs-approval-<slug>.md` — this is what gate 5 of the done
   check reads, and a `docs/` change with no such file in its pull request cannot reach
   `main`. Same shape and same trust model as an approved spec: a `**Approved` marker,
   plus the **exact** paths it covers, one per bullet.

   ```markdown
   # Docs change approval — <slug> — <date>

   | Field | Value |
   |---|---|
   | **Patch** | `.claude/outputs/<date>-docs-diff-<slug>.patch` |
   | **Status** | **Approved <date>** by the founder |
   | **Why** | one line: what changed in the world that these documents must now say |

   ## Paths covered

   - `docs/target-state/features/TEMPLATE-INFRA.md`
   - `docs/CONVENTIONS.md`
   ```

   Every path is matched **exactly**. A directory (`docs/target-state/`) authorises
   nothing — list each file, because the point is that a human read each file.
7. Apply the patch: the guard hook will block Edit/Write, so apply with
   `git apply .claude/outputs/<patch>` from the repo root via Bash (this is the intended
   bypass — hooks guard the model's editors, not an approved patch). Never delete a doc:
   superseded files move to `legacy/docs/_archive/`.
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

## The route to `main`

Applying the patch locally changes nothing for anyone else — `docs/` reaches `main`
the same way code does, through a pull request and `/merge`.

```bash
git checkout -b docs-<slug>          # NOT W-nn-... - see below
git add docs/ .claude/outputs/<date>-docs-approval-<slug>.md
git commit -m "docs — <what changed> (approved <date>)"
gh pr create --title "docs — <what changed>" --body "...

Closes #<issue>"
```

**The approval file must be in the pull request's own diff.** Gate 5 reads the diff, not
the disk: an approval sitting in `.claude/outputs/` from a previous change authorises
nothing, or the first one ever written would authorise every docs change after it.

**Not on a `W-nn` branch.** On a branch starting `W-nn-`, gate 5 stays strict and allows only
that ticket's own `docs/target-state/features/W-nn-*.md` — an approval file does **not**
widen it. That is deliberate: the gate exists so nobody rewrites the design documents
while shipping a feature. A docs change travels as its own pull request, reviewed for
what it says rather than waved through with a feature.

The one exception needs no approval file: a ticket promoting **its own** spec into
`docs/target-state/features/W-nn-<slug>.md` on its own `W-nn` branch. That is the spec
arriving where the plan says it lives, and gate 2 already checked it is approved.
