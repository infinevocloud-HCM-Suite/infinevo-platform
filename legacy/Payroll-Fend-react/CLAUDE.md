# Payroll-Fend-react

React 18 · Create React App · Ant Design 5 · Redux Toolkit · Keycloak SSO · ECharts ·
Formik/Yup. Port 3000. Branch **`employee`** (not `main` — that is 13 months stale).
See root `@CLAUDE.md`.

| Task | Command |
|---|---|
| Dev | `npm start` (Windows) / `npm run linuxstart` |
| Build | `npm run build` (Windows) / `npm run linuxbuild` |
| Lint | **no `lint` script** — use `npx eslint src --ext .js,.jsx` (CRA `eslintConfig` is embedded in `package.json`) |
| Test | `npm test` — configured but no tests exist (DEBT-003) |

`node_modules` is installed locally (2026-09-11) and gitignored; run `npm install` after a fresh clone.

**Structure:** all 158 routes in `src/pages/pageLayouts/router.js`. Four layouts:
`sidebarLayout` (admin), `settingsLayout`, `employeeLayout` (portal), `authLayout`.
Guards: `authGuard`, `loginGuard`, `organizationGuard`.

**Screen anatomy** — repeats in ~150 files: read `organizationId` and token `__t` from
`localStorage` → `fetchXxx()` in `useEffect` calling `GlobalConst.API_URL` → Formik/Yup schema →
`handleSubmit` → AntD `columns[]` at the bottom. **There is no API service layer** for most
modules; only `authService`, `invitationService`, `reimbursementService`, `leaveStore` are
abstracted. Prefer adding to `shared/services/` for new work.

**Known issues** (`@docs/GAP_INVENTORY.md`): ~12k duplicated lines across six admin/employee
POI file pairs (DEBT-026) · CTC engine copy-pasted in 3 files (DEBT-027) · dashboard tiles are
hard-coded demo data (DEBT-028) · `addNewEarning.js` posts to a non-existent endpoint (DEBT-029) ·
route aliases (DEBT-030) · dead files `dashboardcopy.js`, `addEmpOld.js`, `header-ols.js`,
empty `editEmployee.js` (DEBT-031).

**Naming hazards:** `attendence.js`, `benifits.js`, `salaryRevisonDetails.js`, `leaveAttendence/`.
Notifications use SweetAlert2 via `msgHelper.js`, not AntD `message`.
