import { defineConfig, devices } from '@playwright/test';
import { ROLES, BASE_URL, authFile } from './fixtures/roles.js';

/**
 * Playwright end-to-end suite for the Infinevo frontend.
 *
 * One `setup` project signs every seeded role in once (Keycloak login form) and saves the browser
 * state to `e2e/.auth/<role>.json`. One project per role then replays that state - its spec is
 * `<role>.spec.js`. The `flows` project holds multi-role scenarios that open their own contexts.
 *
 * Run from `code/frontend` with the local stack up: `npm run e2e`. See `e2e/README.md`.
 */
const roleProjects = Object.keys(ROLES).map((role) => ({
  name: role,
  testMatch: `**/${role}.spec.js`,
  dependencies: ['setup'],
  use: { storageState: authFile(role) },
}));

export default defineConfig({
  testDir: '.',
  outputDir: './test-results',
  // One worker: the Vite dev server in Docker resets connections when several browsers hit it at once
  // (ERR_CONNECTION_RESET), and the flow files share state across their tests.
  fullyParallel: false,
  workers: 1,
  retries: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { outputFolder: './playwright-report', open: 'never' }]],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: BASE_URL,
    headless: true,
    viewport: { width: 1440, height: 900 },
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 60_000,
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.js$/ },
    ...roleProjects,
    {
      name: 'flows',
      testMatch: /[\\/]flows[\\/].*\.spec\.js$/,
      dependencies: ['setup'],
    },
  ],
});
