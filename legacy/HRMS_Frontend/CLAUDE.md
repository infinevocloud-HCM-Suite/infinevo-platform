# HRMS_Frontend

React 18 · Vite · MUI v6 + Bootstrap 5 · JWT via React Context (no Redux). Port 5173.
Branch `main`. See root `@CLAUDE.md`.

| Task | Command |
|---|---|
| Dev | `npm run dev` |
| Build | `npm run build` |
| Lint | `npm run lint` (eslint flat config, `eslint.config.js`) |
| Test | none configured — no test script, no tests (DEBT-003) |

`node_modules` is installed locally (2026-09-11) and gitignored; run `npm install` after a fresh clone.

**Structure:** `src/App.jsx` holds all routes (role-prefixed `/:role/*`).
`components/context/` has the auth context plus `ProtectedRoute` (role) and
`ActionProtectedRoute` (per-action permission). `components/service/` is the axios layer —
unlike Payroll FE, this app **does** have one; use it. `axiosInterceptor.js` attaches the JWT.

**Known issues** (`@docs/GAP_INVENTORY.md`): no Redux, all state in Context (DEBT-012) ·
app is effectively frozen — last commit 2025-12-17, `HRMS_FRONTEND_DEV` stale since 2025-06-30.

**Note:** this app uses MUI and Vite; Payroll FE uses Ant Design and CRA. Nothing is shared.
