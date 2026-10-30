import { test, expect } from '@playwright/test';
import { defineRoleSuite } from './fixtures/roleSuite.js';
import { dismissSwal, expectNoErrorScreen, expectSuccessDialog, gotoApp, waitForSettled } from './fixtures/helpers.js';

// payroll-officer: landing, menu walk and button sweep. The role's user and home live in fixtures/roles.js.
defineRoleSuite('payroll-officer');

/**
 * W-75.2: the payroll officer sets up an employee's salary by typing only the CTC.
 *
 * The seed (infra/docker/seed) creates no salary components, so the walk first makes sure Globex has
 * the India catalogue the drawer pre-fills from - Basic 50% of CTC, HRA 50% of Basic and Special
 * Allowance (the balancing earning) - through Payroll > Salary Components. Then it opens GLBX-0005's
 * Salary tab, sets up the salary with CTC 6,00,000 and saves. Idempotent: an existing component or
 * salary structure is reused, not created twice.
 */
const EMPLOYEE_NUMBER = 'GLBX-0005';
const CATALOGUE = [
  { code: 'BASIC', name: 'Basic', earningType: 'Basic', calculation: 'Percentage', value: '50', percentageOf: 'CTC' },
  {
    code: 'HRA',
    name: 'House Rent Allowance',
    earningType: 'House Rent Allowance',
    calculation: 'Percentage',
    value: '50',
    percentageOf: 'Basic',
  },
  { code: 'SPECIAL', name: 'Special Allowance', earningType: 'Custom Allowance', calculation: 'Flat Amount', value: '0' },
];

/** Picks an option in the antd Select inside the form item labelled `label`. */
async function pickInFormItem(page, scope, label, optionText) {
  const item = scope.locator('.ant-form-item').filter({ has: page.locator('label', { hasText: label }) }).first();
  await item.locator('.ant-select-selector').click();
  const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last();
  await dropdown.waitFor({ state: 'visible', timeout: 10_000 });
  const search = item.locator('input.ant-select-selection-search-input');
  if ((await search.getAttribute('readonly')) === null) {
    await search.fill(optionText);
    await page.waitForTimeout(300);
  }
  await dropdown.locator('.ant-select-item-option').filter({ hasText: new RegExp(`^${optionText}$`) }).first().click();
}

async function ensureEarning(page, earning) {
  const row = page.locator('.ant-tabs-tabpane-active .ant-table-row').filter({ hasText: earning.name });
  if ((await row.count()) > 0) {
    test.info().annotations.push({ type: 'reused', description: `earning ${earning.code} already exists` });
    return;
  }
  await page.getByRole('button', { name: 'Add earning' }).click();
  const drawer = page.locator('.ant-drawer-content').filter({ hasText: 'Add earning' });
  await expect(drawer).toBeVisible();
  await drawer.locator('input[name="code"]').fill(earning.code);
  await drawer.locator('input[name="name"]').fill(earning.name);
  await pickInFormItem(page, drawer, 'Earning Type', earning.earningType);
  await pickInFormItem(page, drawer, 'Calculation Type', earning.calculation);
  await drawer.locator('input[name="defaultValue"]').fill(earning.value);
  if (earning.percentageOf) await pickInFormItem(page, drawer, 'Percentage Of', earning.percentageOf);
  await drawer.getByRole('button', { name: 'Create Component' }).click();
  await expectSuccessDialog(page, `create earning ${earning.code}`);
  await waitForSettled(page);
  await expect(row.first(), `the row for ${earning.name}`).toBeVisible();
}

test('W-75.2: payroll officer sets up a salary from the CTC alone', async ({ page }) => {
  test.setTimeout(240_000);

  await gotoApp(page, '/payroll/components');
  await waitForSettled(page, 45_000);
  await expectNoErrorScreen(page, '/payroll/components');
  for (const earning of CATALOGUE) {
    await ensureEarning(page, earning);
  }

  await gotoApp(page, '/employees');
  await waitForSettled(page, 45_000);
  await page.locator('#input-search-employees').fill(EMPLOYEE_NUMBER);
  await page.locator('#input-search-employees').press('Enter');
  const employeeRow = page.locator('.ant-table-row').filter({ hasText: EMPLOYEE_NUMBER }).first();
  await expect(employeeRow).toBeVisible();
  await employeeRow.click();
  await waitForSettled(page, 45_000);
  await page.getByRole('tab', { name: 'Salary' }).click();
  await waitForSettled(page);
  await expectNoErrorScreen(page, `${EMPLOYEE_NUMBER} salary tab`);

  const setUp = page.locator('#btn-create-salary-structure');
  const existing = page.locator('#btn-revise-salary-structure');
  await expect(setUp.or(existing).first()).toBeVisible({ timeout: 30_000 });

  if (await setUp.isVisible()) {
    await setUp.click();
    const drawer = page.locator('.ant-drawer-content').filter({ hasText: 'Set up salary' });
    await expect(drawer).toBeVisible();
    // The catalogue pre-fills Basic, HRA and Special; Special balances to CTC.
    await expect(drawer.getByText('Balances to CTC')).toBeVisible({ timeout: 15_000 });

    await drawer.locator('#input-annual-ctc').fill('600000');
    // The summary comes from the preview (400 ms debounce); Save waits for it.
    await expect(drawer.getByTestId('salary-take-home')).toBeVisible({ timeout: 20_000 });
    await expect(drawer.getByText('before TDS and loss of pay')).toBeVisible();
    const save = page.locator('#btn-salary-version-submit');
    await expect(save).toBeEnabled({ timeout: 20_000 });
    await save.click();

    const outcome = await dismissSwal(page, 20_000);
    expect(outcome, 'save salary: an outcome dialog appears').not.toBeNull();
    expect(outcome.kind, `save salary: "${outcome.title}" - ${outcome.text}`).not.toBe('error');
    await expect(page.getByText(/Difference/)).toHaveCount(0);
    await waitForSettled(page);
  } else {
    test.info().annotations.push({ type: 'reused', description: `${EMPLOYEE_NUMBER} already has a salary structure` });
  }

  // The structure shows: the saved earnings, and the statutory table under them.
  await expect(page.getByText(/Active Structure \(Effective/)).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.ant-table-row').filter({ hasText: 'Special Allowance' }).first()).toBeVisible();
  await expect(page.locator('#table-salary-statutory')).toBeVisible();
  await expect(page.getByText(/Difference/)).toHaveCount(0);
});
