import { test, expect } from '@playwright/test';
import { ROLES, isDashboard } from './roles.js';
import { landHome } from './login.js';
import {
  checkTable,
  clickEveryButton,
  collectMenu,
  expectNoErrorScreen,
  findErrorScreen,
  gotoApp,
  openMenuLeaf,
  pageHeading,
  screenshotScreen,
  waitForSettled,
} from './helpers.js';

const WALK_TIMEOUT = 300_000;
const BUTTON_BUDGET_PER_SCREEN = 40_000;
const BUTTON_BUDGET_TOTAL = 240_000;

/**
 * The checks every role gets (the per-role spec files call this):
 *   (a) landing  - sign-in lands on the role's home with no error screen; dashboards show a figure;
 *   (b) menu walk - every sidebar leaf opens with no error screen, has a heading, its tables show no
 *                   raw ids/dates/codes; one screenshot per screen in test-results/screens/<role>/;
 *   (d) buttons  - every safe button on each list screen (one with a table) is clicked once.
 * Role-specific extras (the employee's portal tabs) live in the spec file.
 */
export function defineRoleSuite(roleKey) {
  const role = ROLES[roleKey];
  if (!role) throw new Error(`Unknown role ${roleKey}; see fixtures/roles.js`);

  // Only a login with no employee record on purpose (tenant admin, platform staff) may see the
  // "No employee record is linked" screen; for everyone else it is a seed or linking defect.
  const noEmployeeRecordExpected = !role.employeeNumber;

  test.describe(`${roleKey} (${role.username})`, () => {
    test('landing: the home page opens with no error screen', async ({ page }) => {
      await gotoApp(page, '/');
      const { landedAt } = await landHome(page);
      expect(role.homes, `home for ${roleKey}`).toContain(landedAt);
      await waitForSettled(page);
      await expectNoErrorScreen(page, `${roleKey} home ${landedAt}`);

      if (isDashboard(landedAt)) {
        const figures = page.locator('.ant-layout-content .ant-statistic, .ant-layout-content .ant-card-head-title');
        await expect(figures.first(), 'a dashboard shows at least one figure or card').toBeVisible();
      } else {
        expect(await pageHeading(page), `${landedAt} has a heading`).not.toBe('');
      }
      await screenshotScreen(page, roleKey, `home-${landedAt}`);
    });

    test('menu walk: every sidebar screen opens cleanly', async ({ page }) => {
      test.setTimeout(WALK_TIMEOUT);
      await gotoApp(page, '/');
      await landHome(page);
      await waitForSettled(page);

      const leaves = await collectMenu(page);
      test.info().annotations.push({ type: 'menu leaves', description: leaves.map((l) => l.href).join(' ') });
      expect(leaves.length, 'the sidebar names at least one screen').toBeGreaterThan(0);

      for (const leaf of leaves) {
        const where = `${roleKey} ${leaf.group ? `${leaf.group} > ` : ''}${leaf.label} (${leaf.href})`;
        await openMenuLeaf(page, leaf);
        const settled = await waitForSettled(page, 45_000);
        expect.soft(settled, `${where} is still loading after 45s`).toBe(true);

        const error = await findErrorScreen(page, { noEmployeeRecordExpected });
        expect.soft(error, `${where} shows ${error}`).toBeNull();
        expect.soft(await pageHeading(page), `${where} has a heading`).not.toBe('');
        await checkTable(page, where);
        await screenshotScreen(page, roleKey, leaf.href);
      }
    });

    test('buttons: every safe button on each list screen works', async ({ page }) => {
      test.setTimeout(WALK_TIMEOUT);
      const started = Date.now();
      await gotoApp(page, '/');
      await landHome(page);
      await waitForSettled(page);

      const leaves = await collectMenu(page);
      const skipped = [];
      for (const leaf of leaves) {
        if (Date.now() - started > BUTTON_BUDGET_TOTAL) {
          skipped.push(leaf.href);
          continue;
        }
        await openMenuLeaf(page, leaf);
        await waitForSettled(page, 30_000);
        if ((await page.locator('.ant-layout-content .ant-table').count()) === 0) continue;

        const where = `${roleKey} ${leaf.label} (${leaf.href})`;
        const { clicked } = await clickEveryButton(page, { noEmployeeRecordExpected, 
          maxButtons: 25,
          timeBudgetMs: BUTTON_BUDGET_PER_SCREEN,
          where,
        });
        test.info().annotations.push({ type: `buttons ${leaf.href}`, description: clicked.join(' | ') || '(none)' });
      }
      if (skipped.length) {
        test.info().annotations.push({ type: 'skipped (time budget)', description: skipped.join(' ') });
      }
    });
  });
}
