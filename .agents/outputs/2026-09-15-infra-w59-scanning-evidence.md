# W-59 scanning — baseline evidence, 2026-09-15

Measured on `main` at `d3eb07e`, the way the spec on `origin/w-59` proposes to gate:
Trivy `fs --severity HIGH,CRITICAL --ignore-unfixed`. Backend scanned `--offline-scan`
against the local `~/.m2` because Maven Central returned 429 on the first online run.

| Target | Tool | Result |
|---|---|---|
| `code/frontend` | `npm audit` | 4 vulnerabilities (3 moderate, 1 high): esbuild ≤0.24.2 via vite ≤6.4.2; react-router 6.0–7.17 via react-router-dom. Both fixes are **major** bumps (vite 5→8, react-router-dom 6→7) |
| `code/frontend` | Trivy, spec's gate | **0** — dev dependencies suppressed by default, react-router below HIGH in Trivy's data. The spec's gate and its backlog claim use different tools |
| `code/backend` | Trivy, spec's gate | **30 unique fixable CVEs: 6 CRITICAL, 24 HIGH** across all 8 POMs. The spec says "clean" — it ran `dependency:tree`, which reports nothing about CVEs |

## Backend, by package

| Package | Installed | CVEs | Fixed in |
|---|---|---|---|
| `tomcat-embed-core` | 10.1.42 | 17 (CRITICAL/HIGH) | 10.1.58 |
| `spring-webmvc` / `spring-expression` / `spring-core` | 6.1.21 | 4 | 6.2.19 |
| `jackson-databind` / `jackson-core` | 2.17.3 | 3 | 2.18.8 |
| `postgresql` (pgjdbc) | 42.7.7 | 2 | 42.7.12 |
| `spring-boot` / `starter-actuator` / `spring-data-commons` | 3.3.13 | 3 | **3.5.12–3.5.14** |
| `micrometer-core` | 1.13.15 | 1 | 1.15.12 |

Spring Boot 3.3.x receives no further OSS patches; every fix above is in the 3.5 line.
Clearing the backlog means moving the parent to Spring Boot 3.5.x, which reopens `D-39`.

## Operational findings while scanning

- Trivy resolves `pom.xml` dependencies from Maven Central at scan time and was rate-limited
  (`429`, 30-minute block) on the first run. In CI: `mvn dependency:resolve` with a cached
  `~/.m2`, then `--offline-scan`.
- Frontend upgrade cost is small: `code/frontend/src` has 2 files and one `react-router-dom` import.
- Dependabot alerts are **disabled** on the repository (`GET /vulnerability-alerts` → 404). They are free on private repositories and cost no Actions minutes.
