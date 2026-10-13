import fs from 'node:fs';
import path from 'node:path';
import { test as setup, expect } from '@playwright/test';
import { ROLES, authFile } from './fixtures/roles.js';
import { login } from './fixtures/login.js';

/**
 * Signs every role in once and saves its browser state (Keycloak's SSO cookie included) to
 * e2e/.auth/<role>.json. The role projects replay that state, so each spec starts signed in.
 *
 * Clicking "Go to my home" here marks the welcome page seen (W-73.8), so the role specs land on
 * the home path directly. The first sign-in after a fresh seed is the one that sees the page.
 */
for (const [role, user] of Object.entries(ROLES)) {
  setup(`sign in ${role} (${user.username})`, async ({ page }) => {
    setup.setTimeout(180_000);
    const { welcomeShown, landedAt } = await login(page, user.username);
    setup.info().annotations.push({ type: 'welcome page shown', description: String(welcomeShown) });
    setup.info().annotations.push({ type: 'landed at', description: landedAt });
    expect(user.homes, `${role} landed at ${landedAt}`).toContain(landedAt);

    fs.mkdirSync(path.dirname(authFile(role)), { recursive: true });
    await page.context().storageState({ path: authFile(role) });
  });
}
