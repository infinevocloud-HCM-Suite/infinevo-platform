import { test, expect } from '@playwright/test';
import { defineRoleSuite } from './fixtures/roleSuite.js';
import { ROLES } from './fixtures/roles.js';
import { UUID_RE, checkSqueezedText, expectNoErrorScreen, findErrorScreen, screenshotScreen, waitForSettled } from './fixtures/helpers.js';

const ROLE = 'employee';

defineRoleSuite(ROLE);

test.describe(`${ROLE} portal (${ROLES[ROLE].username})`, () => {
  test('every /me tab opens; My Profile shows no UUID; My Leave offers Apply for Leave', async ({ page }) => {
    test.setTimeout(180_000);
    await page.goto('/me');
    await waitForSettled(page);
    await expectNoErrorScreen(page, '/me');

    const layout = page.locator('[data-testid="portal-layout"]');
    await expect(layout, 'the portal renders its tabs (PortalLayout.jsx)').toBeVisible();

    // The portal's own tab bar only: panels such as My Profile nest their own Tabs (Personal / Contact).
    const tabs = layout.locator('.ant-tabs').first().locator(':scope > .ant-tabs-nav .ant-tabs-tab');
    const count = await tabs.count();
    expect(count, 'the portal offers at least one panel').toBeGreaterThan(0);

    const labels = [];
    for (let i = 0; i < count; i += 1) {
      const tab = tabs.nth(i);
      const label = ((await tab.innerText()) || '').trim();
      labels.push(label);
      await tab.click();
      await waitForSettled(page, 30_000);
      const panelId = new URL(page.url()).pathname.replace(/^\/me\/?/, '') || 'default';

      const error = await findErrorScreen(page);
      expect.soft(error, `tab "${label}" (${page.url()}) shows ${error}`).toBeNull();
      await checkSqueezedText(page, `/me tab "${label}"`);
      await screenshotScreen(page, ROLE, `me-${panelId}`);

      if (panelId === 'profile') {
        const profile = page.locator('[data-testid="panel-profile"]');
        await expect(profile, 'My Profile renders').toBeVisible();
        const text = await profile.innerText();
        const uuid = text.match(UUID_RE);
        expect.soft(uuid, `My Profile shows a raw UUID: ${uuid && uuid[0]}`).toBeNull();
      }

      if (panelId === 'leave') {
        await expect(page.locator('#btn-apply-leave'), 'My Leave offers "Apply for Leave"').toBeVisible();
        await expect(page.locator('#btn-apply-leave')).toHaveText(/apply for leave/i);
      }
    }
    test.info().annotations.push({ type: 'portal tabs', description: labels.join(' | ') });
    expect(labels.some((l) => /profile/i.test(l)), 'a profile tab exists').toBe(true);
    expect(labels.some((l) => /leave/i.test(l)), 'a leave tab exists').toBe(true);
  });
});
