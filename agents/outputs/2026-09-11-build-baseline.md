# Build & lint baseline — 2026-09-11

Purpose: record how each app builds **before** the harness hooks go live, so any
later regression can be told apart from pre-existing failures. Nothing was fixed.

## Toolchain (installed 2026-09-11)

| Tool | Version | Path |
|---|---|---|
| JDK | Microsoft OpenJDK 21.0.12.1 LTS | `C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot` |
| Maven | Apache Maven 3.9.11 | `C:\Tools\apache-maven-3.9.11` |
| Node / npm | v24.14.0 / 11.9.0 | pre-existing |

`JAVA_HOME`, `MAVEN_HOME` and PATH are user-level env vars. Terminals opened before
the install must be restarted.

## Results

| App | Branch @ SHA | Command | Result | Detail |
|---|---|---|---|---|
| `HRMS_Backend` | `main` @ d984c64 | `./mvnw -q compile` | **PASS** | exit 0, no warnings printed |
| `Payroll-Bend-SBoot` | `taxation` @ 39b37d6 | `./mvnw -q compile` | **PASS** | exit 0 (Java 17 target compiled on JDK 21) |
| `HRMS_Frontend` | `main` @ c72116c | `npm run lint` | **FAIL — code errors** | 376 problems: 350 errors, 26 warnings, 67 files |
| `Payroll-Fend-react` | `employee` @ 053ca62 | `npx eslint src --ext .js,.jsx` | **PASS (warnings)** | 833 problems: 0 errors, 833 warnings, 130 files |

Gate verdict: every app either builds, or fails on **code** errors, not toolchain
errors → harness sections E–H may proceed.

### HRMS_Frontend — error breakdown

| Count | Rule | Severity |
|---|---|---|
| 314 | `no-unused-vars` | error |
| 25 | `react-hooks/exhaustive-deps` | warn |
| 15 | `react/no-unescaped-entities` | error |
| 12 | `react/prop-types` | error |
| 4 | `no-useless-catch` | error |
| 3 | `no-undef` (incl. `vite.config.js:10` `__dirname`) | error |
| 2 | `no-constant-binary-expression` | error |
| 1 | `react-refresh/only-export-components` | warn |

Implication for the `verify-app` hook: HRMS_Frontend lint will report FAIL on every
edit until this backlog is cleared or the rule set is relaxed. The hook is
informational (never blocks), so this is noise, not a stopper. Candidate future
task: `npx eslint . --fix` clears most `no-unused-vars` mechanically — needs
founder approval as it touches ~67 source files.

### Payroll-Fend-react — top warnings

| Count | Rule |
|---|---|
| 581 | `no-unused-vars` |
| 152 | `jsx-a11y/anchor-is-valid` |
| 82 | `react-hooks/exhaustive-deps` |
| 10 | `react/jsx-no-target-blank` |
| 2 | `no-dupe-keys` (real bug smell — duplicate object keys) |
| 2 | `no-mixed-operators` |
| 1 | `eqeqeq` |

## Side effects of this baseline run

1. **`HRMS_Backend/.mvn/wrapper/maven-wrapper.properties` added** (Maven 3.9.11)
   via `mvn -N wrapper:wrapper`. The plugin also **regenerated the tracked
   `mvnw` and `mvnw.cmd`** scripts (469 / 346 line diffs). The old scripts were
   broken anyway (no wrapper dir). Untracked in the origin repo; these live only
   in the local working copy until the new consolidated repo is created.
2. **`HRMS_Backend/target/` is committed to git** (134 tracked files, the
   `.gitignore` only ignores `*.class` by pattern yet the files were added
   before it). Compiling therefore modifies 125 tracked build artifacts. Harmless
   locally (origin is read-only, never pushed) but every `verify-app` run will
   leave the HRMS_Backend working tree dirty. → new GAP entry **DEBT-034**
   proposed; Payroll-Bend-SBoot tracks 0 files under `target/`.
3. `node_modules/` installed in both frontends (1,592 and 1,726 packages). Both
   `package.json` files are unchanged; lockfiles were honoured.
4. `CLAUDE.md` present as untracked in each of the 4 app folders (harness task 4).

## Not done (by design)

- No lint errors fixed, no `--fix` run.
- No tests run — none exist except `LeaveAllocationImportTest.java` (DEBT-003).
- `target/` in HRMS_Backend not reverted; decision left to founder.
