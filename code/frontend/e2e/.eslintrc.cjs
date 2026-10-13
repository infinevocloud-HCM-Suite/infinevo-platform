// The e2e suite is Node + Playwright code, not application code: the shell/module import rules and
// the browser-storage bans in ../.eslintrc.cjs do not apply here. `root: true` stops ESLint from
// merging the two; `npm run lint` lints `src/` only and never reaches this directory.
module.exports = {
  root: true,
  env: { node: true, browser: true, es2022: true },
  extends: ['eslint:recommended'],
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  rules: {
    'no-empty': ['error', { allowEmptyCatch: true }],
  },
};
