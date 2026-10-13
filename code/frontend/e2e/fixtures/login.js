import { expect } from '@playwright/test';
import { BASE_URL, PASSWORD, ROLES, authFile } from './roles.js';

/** The shell header's user menu button (Header.jsx) - visible on every signed-in page, the welcome page included. */
export const USER_MENU = '[data-testid="user-display"]';

const KEYCLOAK_LOGIN_URL = /\/realms\/[^/]+\/protocol\/openid-connect\/auth/;

/**
 * Signs `username` in through Keycloak's login page and lands on the role's home.
 *
 * `page.goto('/')` makes keycloak-js (`onLoad: 'login-required'`, keycloak.js) send the browser to
 * Keycloak. The form there is Keycloak's own: `#username`, `#password`, `#kc-login`. After the
 * redirect back the shell shows either the first-sign-in Welcome page (W-73.8) or the home path.
 *
 * @returns {Promise<{ welcomeShown: boolean, landedAt: string }>}
 */
export async function login(page, username, password = PASSWORD) {
  const userMenu = page.locator(USER_MENU);

  // A cold Vite dev server answers the very first load with a blank page while it pre-bundles the
  // dependencies, then asks the browser to reload. Try the first navigation a few times.
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await page.goto('/', { timeout: 120_000 }).catch(() => {});
    // Already signed in (replayed storage state) → no Keycloak form appears.
    await Promise.race([
      page.waitForURL(KEYCLOAK_LOGIN_URL, { timeout: 60_000 }).catch(() => {}),
      userMenu.waitFor({ state: 'visible', timeout: 60_000 }).catch(() => {}),
    ]);
    if (KEYCLOAK_LOGIN_URL.test(page.url()) || (await userMenu.isVisible().catch(() => false))) break;
  }

  if (KEYCLOAK_LOGIN_URL.test(page.url())) {
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await page.locator('#kc-login').click();

    const loginError = page.locator('#input-error, .kc-feedback-text, #input-error-username');
    await Promise.race([
      userMenu.waitFor({ state: 'visible', timeout: 90_000 }).catch(() => {}),
      loginError.first().waitFor({ state: 'visible', timeout: 90_000 }).then(async () => {
        throw new Error(`Keycloak refused ${username}: ${(await loginError.first().innerText()).trim()}`);
      }, () => {}),
    ]);
  }

  // Keycloak's session cookie is set by now; one reload brings a blank first load back silently.
  if (!(await userMenu.isVisible().catch(() => false))) {
    await page.reload({ timeout: 120_000 }).catch(() => {});
  }

  await expect(userMenu, 'the shell header renders after sign-in').toBeVisible({ timeout: 90_000 });
  return landHome(page);
}

/**
 * From `/` (or `/welcome`) to the home path. Records whether the Welcome page appeared and clicks its
 * "Go to my home" button (`#btn-welcome-go-home`, Welcome.jsx). Roles with no welcome card are sent
 * home by the page itself.
 */
export async function landHome(page) {
  const goHome = page.locator('#btn-welcome-go-home');
  let welcomeShown = false;

  // The shell header renders once Keycloak has handed the browser back; nothing below it exists before.
  // A blank first load (cold Vite server, fresh context) comes back after one reload.
  const header = page.locator(USER_MENU);
  if (!(await header.waitFor({ state: 'visible', timeout: 60_000 }).then(() => true, () => false))) {
    await page.reload({ timeout: 120_000 }).catch(() => {});
  }
  await expect(header, 'the shell header renders').toBeVisible({ timeout: 90_000 });

  // The root redirect waits for /me; give the page a moment to decide between home and welcome.
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    const { pathname } = new URL(page.url());
    if (await goHome.isVisible().catch(() => false)) {
      welcomeShown = true;
      await goHome.click();
      await page.waitForURL((url) => !['/', '/welcome'].includes(url.pathname), { timeout: 30_000 });
      break;
    }
    if (pathname !== '/' && pathname !== '/welcome') break;
    await page.waitForTimeout(250);
  }

  await expect
    .poll(() => new URL(page.url()).pathname, { message: 'left / and /welcome', timeout: 90_000 })
    .not.toMatch(/^\/(welcome)?$/);

  return { welcomeShown, landedAt: new URL(page.url()).pathname };
}

/**
 * A fresh context and page signed in as `role`, from its saved storage state. For the multi-role
 * flows, which cannot use the per-project `storageState`. The caller closes the context.
 */
export async function openAs(browser, role) {
  if (!ROLES[role]) throw new Error(`Unknown role ${role}; see fixtures/roles.js`);
  const context = await browser.newContext({
    storageState: authFile(role),
    baseURL: BASE_URL,
    viewport: { width: 1440, height: 900 },
  });
  const page = await context.newPage();
  return { context, page };
}
