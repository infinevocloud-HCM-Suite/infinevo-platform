# Feature: Frontend shell — service layer, tokens, shell errors

| Field | Value |
|---|---|
| **Feature ID** | `W-45` · from ticket #57 · `PLAT-01` |
| **Spec file** | `docs/target-state/features/W-45-frontend-shell.md` |
| **Owner** | unassigned |
| **Apps touched** | `code/frontend` only. No backend, no migration |
| **Related gaps** | DEBT-012 (closed by the target choice), BUG-006 (deferred), DEBT-008 (already closed by the envelope) |
| **Status** | **Ready** |
| **Written by** | founder, 2026-09-27 |
| **Blocked by** | nothing — `W-12` is on `main` (`a4f31ae`) |
| **Size** | **M** (scoping said L; four of the six work-plan items shipped under `W-12.3`) |

## Size cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | **none** | 1 |
| Flyway migration | **none** | 1 |
| Externally testable behaviour | a screen built on the shell reaches the API, shows errors and is styled without importing `axios`, reading `localStorage` or writing a colour literal — and the build fails if it tries | 1 |
| Frontend area | `src/shell` + `src/shared` (the frame; no screen) | 1 |

Within cap.

---

## 1. Problem

The work plan lists six shell items (`08-work-plan.md:120`). Four are already on `main`,
built under `W-12.3`. What is left is the part that stops the frozen Payroll habits coming
back with the 158 screens in `W-46`–`W-48`.

| Work-plan item | State on `main` | Evidence |
|---|---|---|
| Layout | built | `code/frontend/src/shell/AppShell.jsx:20-100` |
| Navigation driven by entitlement | built, tested | `AppShell.jsx:26-28`, `shell/routes.js:24-29`, `shell/navigation/useNavigation.js` |
| Keycloak adapter | built, tested | `shell/auth/keycloak.js:45-56`, `keycloak.test.js` |
| Runtime configuration | built, duplicated in two files | `shell/auth/keycloak.js:26-32`, `shared/api/client.js:34-37`, `infra/docker/frontend-entrypoint.sh:35` |
| API service layer | **client only, no pattern** | `shared/api/client.js:33-40`; no `services/` folder anywhere under `src/` |
| Design tokens | **three tokens** | `shared/theme.js:4-11`; `AppShell.jsx:44,47,81,85,89` hard-code colours inline |

What is missing, and why it matters:

- **No service-layer pattern.** `client.js` exists but nothing shows a screen how to use it,
  and nothing stops a screen importing `axios` itself. The frozen Payroll frontend does
  exactly that in most of its screens, reading `organizationId` and the token `__t` from
  `localStorage` inline (`legacy/docs/FEATURE_MAP.md:440`, `CONVENTIONS.md` §4). Only a
  handful of legacy services are abstracted (`legacy/Payroll-Fend-react/src/components/service/`).
- **Tokens are too thin to style a screen from.** `theme.js` sets primary colour, radius and
  font. The shell itself already bypasses it with literal hex values (`AppShell.jsx:47,81,85,89`).
  Every ported screen will do the same unless the tokens exist first.
- **The shell does not act on the error envelope.** `client.js:53-85` maps the backend's
  `ApiErrorResponse` into `code` and four booleans, and its comment says what the shell
  "should" do for each. Nothing does it. An `UNAUTHORIZED`, a `TENANT_SUSPENDED` and an
  unknown path all render an empty content area today.
- **Modules have no registration contract.** `routes.js:10-14` holds three empty arrays and
  `store.js:6` an empty reducer map. Nothing says how `W-46` adds a route or a slice.
- **The header is empty** (`AppShell.jsx:76-88`): no user name, no logout, although
  `keycloak.js:78` already exports `logout()`.

## 2. Scope

**In scope**

- `src/shared/config.js`: one read of `window.__ENV` with `import.meta.env.VITE_*` fallback;
  `client.js` and `keycloak.js` read from it instead of each other's copy.
- The service-layer pattern: `src/<module>/services/<thing>Service.js` wrapping `apiClient`.
  One real service to prove it, `src/shell/navigation/navigationService.js`, used by
  `useNavigation.js`.
- ESLint rules that make the pattern the only option (§5, "Build-time check").
- Module registration contract: each `src/<module>/index.js` exports `routes` and
  `reducers`; `routes.js` and `store.js` compose from them.
- `theme.js` expanded to a full token set; `AppShell.jsx` reads tokens, no literals.
- `src/shared/ui/msgHelper.js` with `successMsg` / `errorMsg` (`CONVENTIONS.md` §4).
- Shell responses to the envelope: re-login, suspension screen, not-entitled screen,
  not-found screen, empty-feed screen.
- Header: display name from the token, logout.

**Out of scope**

- Any screen (`W-46`, `W-47`, `W-48`). No employee list, no dashboards.
- Any backend change. The navigation endpoint and the envelope already exist.
- Code splitting and lazy routes (BUG-006). Deferred to the first screen ticket that makes
  the bundle measurably large; the module contract below does not prevent it.
- Tenant switching UI. The tenant rides in the token (`keycloak.js:84-88`); an admin
  console switching it is `W-65`.
- Internationalisation. The feed carries `labelKey` (`AppShell.jsx:112,118`); a translation
  layer is a later ticket. The shell renders the key as today.
- `frontend-entrypoint.sh` and the nginx image (`W-49`). Unchanged; the contract of
  `window.__ENV` is kept exactly.

## 3. Flow

```
browser ──> index.html ──> /env.js (window.__ENV, written at container start)
        ──> main.jsx ──> initAuth() [Keycloak, login-required]
        ──> <AppShell>
              useNavigation ──> navigationService.fetch() ──> apiClient ──> GET /api/v1/navigation
              routesFromFeed(items, routeGroups composed from core/hrms/payroll index.js)
              <Header> name · logout        <Content> matched route | NotFound | NotEntitled | Suspended

any screen ──> <module>/services/xService ──> apiClient
                                                 │ 401 / UNAUTHORIZED ──> keycloak.login()
                                                 │ TENANT_SUSPENDED   ──> shell shows <Suspended/> full page
                                                 │ MODULE_NOT_ENTITLED ──> screen or route shows <NotEntitled/>
                                                 │ FORBIDDEN, validation ──> screen calls errorMsg(...)
```

## 4. Backend changes

None. No controller, service, entity, repository, DTO or migration is touched.

## 5. Frontend changes

| File | Change |
|---|---|
| `src/shared/config.js` | **new.** Exports `config = { apiBaseUrl, keycloak: { url, realm, clientId } }`. Resolution order per key: `window.__ENV.<KEY>` → `import.meta.env.VITE_<KEY>` → the default already in code. Defaults stay: `/api` (`client.js:37`), `http://localhost:8081` / `infinevo` / `infinevo-web` (`keycloak.js:29-31`). The only file allowed to read `window.__ENV` or `import.meta.env` |
| `src/shared/api/client.js` | `baseURL: config.apiBaseUrl`. Response interceptor: on `isUnauthorized`, call the registered `onUnauthorized` handler (set by the shell, like `setTokenProvider` at `:29`) before rejecting. On `isTenantSuspended`, call `onTenantSuspended`. Rest unchanged |
| `src/shared/api/createService.js` | **new.** `createService(basePath)` returning `{ list(params), get(id), create(body), update(id, body), remove(id) }` over `apiClient`. A service file spreads it and adds its own calls. Small on purpose |
| `src/shell/navigation/navigationService.js` | **new.** `fetchNavigation()` → `apiClient.get('/v1/navigation')`. The proof of the pattern |
| `src/shell/navigation/useNavigation.js` | `fetchNavigationFeed` (`:36-40`) calls `fetchNavigation()` instead of `apiClient` directly. The `/api` suffix guess at `:39` goes: `config.apiBaseUrl` is authoritative and the service path is always `/v1/navigation` |
| `src/shared/ui/msgHelper.js` | **new.** `successMsg(title, text)` and `errorMsg(title, text)` on SweetAlert2, as `CONVENTIONS.md` §4 requires. Signature reduced from the legacy four-argument form (`legacy/Payroll-Fend-react/src/shared/helpers/msgHelper.js:10,27`): no `ERROR_CONST` lookup, the envelope already carries the message. `errorMsg(err)` also accepts a rejected client error and uses `err.message`, appending `traceId` when present |
| `src/shared/theme.js` | Full token set (§5a). Also `components.Layout` and `components.Menu` tokens for sider and header colours so `AppShell` has none inline |
| `src/shell/AppShell.jsx` | Read colours and spacing through `theme.useToken()`; delete every literal at `:44,47,81,85,86,89`. Add `<Header>` content and the fallback routes (`*` → `NotFound`, empty feed → `NoModules`). Wrap `<Content>` in `ShellBoundary` |
| `src/shell/Header.jsx` | **new.** Right-aligned: `keycloak.tokenParsed.name` (fallback `preferred_username`), a logout item calling `logout()` from `keycloak.js:78` |
| `src/shell/ShellBoundary.jsx` | **new.** Holds `suspended` state set by `onTenantSuspended`; when set, renders `Suspended` in place of the whole content area. Registers `onUnauthorized = () => keycloak.login()` |
| `src/shell/screens/Suspended.jsx`, `NotEntitled.jsx`, `NotFound.jsx`, `NoModules.jsx` | **new.** Ant Design `Result` pages, one sentence each. `NotEntitled` is exported from `@shell/screens` so a screen can render it on `err.isModuleNotEntitled` |
| `src/shell/routes.js` | `routeGroups` built from `core.routes`, `hrms.routes`, `payroll.routes` imported from the module index files. `routesFromFeed` unchanged |
| `src/shell/store.js` | `reducer: { ...core.reducers, ...hrms.reducers, ...payroll.reducers }` |
| `src/core/index.js`, `src/hrms/index.js`, `src/payroll/index.js` | Export `routes = []` and `reducers = {}` in place of `export default {}` |
| `.eslintrc.cjs` | The rules in §5b, as `overrides` per folder |
| `package.json` | Add `sweetalert2`. Nothing else new |
| `src/shared/README.md` | **new, one page.** How to add a service, a slice, a route; the three forbidden things and the rule that catches each |

**Routes added**

| Path | Component | Guard / layout |
|---|---|---|
| `*` | `NotFound` | inside `AppShell`; rendered only when no feed route matched |
| — | `NoModules` | rendered in `Content` when the feed returns zero items and loading is over |
| — | `Suspended` | replaces `Content` when the client reports `TENANT_SUSPENDED` |

### 5a. Tokens

`theme.js` exports `theme = { token, components }`. Every value below is one line; the
implementer picks values consistent with Ant Design defaults, this is the *list*, not the
colours.

| Group | Tokens |
|---|---|
| Colour | `colorPrimary`, `colorSuccess`, `colorWarning`, `colorError`, `colorInfo`, `colorBgLayout`, `colorBgContainer`, `colorBorderSecondary`, `colorTextSecondary` |
| Type | `fontFamily`, `fontSize`, `fontSizeHeading1`…`fontSizeHeading4`, `lineHeight` |
| Space | `sizeUnit`, `sizeStep`, `padding`, `paddingLG`, `margin`, `marginLG` |
| Shape | `borderRadius`, `borderRadiusLG`, `controlHeight`, `boxShadowSecondary` |
| Components | `Layout.headerBg`, `Layout.headerHeight`, `Layout.siderBg`, `Layout.bodyBg`, `Menu.darkItemBg`, `Menu.darkItemSelectedBg` |

Rule for screens, written in `src/shared/README.md`: a colour, size or radius comes from
`theme.useToken()` or from an Ant Design component prop. A hex literal or a pixel literal in
a screen is a review comment.

### 5b. Build-time check

The behaviour this ticket is tested on. ESLint, run by `npm run lint` with
`--max-warnings 0` (`package.json:14`), so CI already fails on it (`.github/workflows/ci.yml`
frontend step).

| Rule | Scope | Forbids | Allowed in |
|---|---|---|---|
| `no-restricted-imports` | `src/**` | `axios` | `src/shared/api/client.js` only |
| `no-restricted-globals` + `no-restricted-syntax` | `src/**` | `localStorage`, `sessionStorage`, `window.__ENV`, `import.meta.env` | `src/shared/config.js` only |
| `no-restricted-imports` | `src/hrms/**` | `@payroll/*`, `@shell/*` (except `@shell/screens`) | — |
| `no-restricted-imports` | `src/payroll/**` | `@hrms/*`, `@shell/*` (except `@shell/screens`) | — |
| `no-restricted-imports` | `src/core/**` | `@hrms/*`, `@payroll/*`, `@shell/*` (except `@shell/screens`) | — |
| `no-restricted-imports` | `src/shared/**` | `@shell/*`, `@core/*`, `@hrms/*`, `@payroll/*` | — |

This mirrors the backend rule that no module references another, only `core`
(`maven-enforcer`). `shared` depends on nothing; the shell composes the rest (`client.js:23-25`
already states this intent).

## 6. Database changes

None. No table, no migration, no `tenant_id`, no money column. The frontend never sends a
tenant (`client.js:16-18,49`).

## 7. Tests

All under vitest + jsdom, the setup `W-12.3` left (`vite.config.js:21-25`).

| Type | File | Covers |
|---|---|---|
| Unit | `src/shared/config.test.js` | `window.__ENV` wins over `VITE_*`, which wins over the default, per key |
| Unit | `src/shared/api/client.test.js` | **new** — no test covers `client.js` today. Envelope → `code` + booleans; `onUnauthorized` and `onTenantSuspended` handlers called exactly once each |
| Unit | `src/shared/api/createService.test.js` | the five verbs hit the right path and method on `apiClient` (mocked adapter) |
| Unit | `src/shell/navigation/navigationService.test.js` | calls `/v1/navigation`; `useNavigation.test.jsx` keeps passing through it |
| Unit | `src/shared/ui/msgHelper.test.js` | `errorMsg(err)` shows `err.message` and the `traceId` |
| Component | `src/shell/Header.test.jsx` | shows `name`, falls back to `preferred_username`, logout calls `logout()` |
| Component | `src/shell/ShellBoundary.test.jsx` | `TENANT_SUSPENDED` replaces content with `Suspended`; `UNAUTHORIZED` calls `keycloak.login()` |
| Component | `src/shell/AppShell.test.jsx` | empty feed renders `NoModules`; unknown path renders `NotFound`; a route from a module `index.js` mounts when the feed names it; **no hex literal in the rendered shell's inline styles** |
| Unit | `src/shell/store.test.js` | a reducer exported by `core/index.js` appears in the store |
| Lint | `src/test/lint-rules.test.js` | runs ESLint programmatically on four fixture strings: a screen importing `axios`, one reading `localStorage`, `hrms` importing `@payroll/x`, `shared` importing `@shell/x`. Each must report exactly one error |

## 8. Verification

```bash
cd code/frontend
npm ci
npm run lint          # 0 errors, 0 warnings
npm test              # all suites pass; the lint-rules suite lists 4 fixtures, 4 errors
npm run build         # dist/ produced, no warning about chunk size > 500 kB
```

The forbidden things fail the build:

```bash
printf "import axios from 'axios';\nexport const x = axios;\n" > src/core/tmp-bad.js
npm run lint          # exit 1: "'axios' import is restricted from being used"
rm src/core/tmp-bad.js
```

Runtime config still reaches the browser through the container (`W-49` image, unchanged):

```bash
docker compose -f infra/docker/compose.yml up -d frontend
curl -s http://localhost:8080/env.js   # window.__ENV = { API_BASE_URL: ..., KEYCLOAK_URL: ..., KEYCLOAK_REALM: ..., KEYCLOAK_CLIENT_ID: ... }
```

| Check | Expected | Result |
|---|---|---|
| `npm run lint` on a clean tree | exit 0 | |
| `npm run lint` with the `axios` fixture | exit 1, one restricted-import error | |
| `npm test` | green; lint-rules suite 4/4 | |
| `grep -rn "#[0-9a-fA-F]\{3,6\}" src/shell src/shared --include=*.jsx` | only `theme.js` matches | |
| `grep -rn "__ENV\|import.meta.env" src` | only `src/shared/config.js` | |
| Log in as the `acme` seed tenant (`W-02`), open the app | header shows the user's name; sidebar shows the `W-12.3` items; logout returns to Keycloak | |
| Log in as a tenant with a suspended subscription (`W-12.1` seed, if present; otherwise flip `status` in `core.subscription` locally) | full-page `Suspended`, no menu items clickable | |
| Type `/does-not-exist` | `NotFound` inside the shell, menu still visible | |

## 9. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The lint rules are written too loosely and a screen slips `axios` past them | medium | the lint-rules test in §7 asserts each fixture errors; a rule change that stops catching one fails CI |
| Tokens chosen now do not suit the Payroll screens ported in `W-47` | medium | tokens are one file; `W-47` changes values, not screens. That is the point of `theme.js:2` |
| `ShellBoundary` swallows a `TENANT_SUSPENDED` from one stale request after re-activation | low | the notice offers "Reload"; the next feed fetch clears it |
| Moving `window.__ENV` reads into `config.js` breaks the `frontend-entrypoint.sh` contract | low | keys are unchanged; the `curl env.js` check in §8 and `config.test.js` prove the read |
| SweetAlert2 and Ant Design `message` both end up in use | medium | `no-restricted-imports` on `antd/es/message` is *not* added — Ant Design's `message` is legitimate for inline hints. `msgHelper` is for outcomes; `README.md` says which is which |

## 10. Rollback

Revert the merge commit. Nothing outside `code/frontend` changes; no data, no migration, no
image contract. `W-12.3`'s behaviour is preserved because its tests keep running unchanged.

## 11. Standing rules

| Rule | Impact |
|---|---|
| `tenant_id` + RLS on every new table | **creates no table** |
| Flyway for every schema change | **no schema change** |
| `Money` / `BigDecimal` | no money handled |
| Index on `tenant_id` + lookup columns | not applicable |
| Expand / contract | not applicable |
| No module references another, only `core` | **enforced on the frontend by §5b**, the first time it is |
| No write to `legacy/` | read only, for `msgHelper` and the FEATURE_MAP citations |

## 12. Gap inventory

| ID | Overlap | Decision |
|---|---|---|
| DEBT-012 (HRMS: no Redux, all Context) | state management | **closed by the target** — Redux Toolkit from Payroll (`03-*.md` §5, `store.js:3`) |
| BUG-006 (no code splitting, one CRA bundle) | bundle size | **deferred** — the module `routes` export can carry `React.lazy` later; measure first |
| DEBT-008 (inconsistent error envelope) | error handling | **already closed** by `ApiErrorResponse`; `client.js:58` reads it |
| `CONVENTIONS.md` §4 "~100 files read `__t` inline" | token handling | **prevented** by the `localStorage` lint rule; the token only ever comes from the interceptor (`client.js:42-51`) |

## 13. Implementer tasks

One area per task, in order.

| # | Area | Task |
|---|---|---|
| 1 | `src/shared` | `config.js`; `client.js` reads it and gains the two handlers; `createService.js`; `msgHelper.js`; `theme.js` full set; tests |
| 2 | `src/shell` | `navigationService.js`; `Header.jsx`; `ShellBoundary.jsx`; four screens; `AppShell.jsx` reads tokens and adds fallbacks; `routes.js` and `store.js` compose from modules; tests |
| 3 | `src/core`, `src/hrms`, `src/payroll` | `index.js` exports `routes` and `reducers` |
| 4 | repo root of `code/frontend` | ESLint rules; `lint-rules.test.js`; `src/shared/README.md`; `sweetalert2` dependency |

## 14. Open questions

None that block. One choice made here the founder may reverse: outcome dialogs use
SweetAlert2 because `CONVENTIONS.md` §4 says so. Ant Design's `notification` would remove a
dependency; if preferred, `msgHelper.js` changes and nothing else does.
