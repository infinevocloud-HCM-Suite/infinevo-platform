import { test, expect } from '@playwright/test';
import { ROLES } from '../fixtures/roles.js';
import { openAs } from '../fixtures/login.js';
import {
  dismissSwal,
  expectNoErrorScreen,
  findErrorScreen,
  expectSuccessDialog,
  gotoApp,
  isoDate,
  nextMonday,
  pickDate,
  selectOption,
  waitForSettled,
} from '../fixtures/helpers.js';

/**
 * One leave request end to end, across four roles (serial: each test builds on the one before):
 *   1. hr.globex       creates the leave type "E2E Casual" with a 12-day policy (LeaveTypes.jsx, PolicyForm.jsx)
 *   2. hr.globex       allocates it to GLBX-0001 for this year (Allocations.jsx)
 *   3. employee.globex applies for one day of it next Monday (ApplyLeave.jsx)
 *   4. manager.globex  views the item from /approvals, then approves it with a comment (Inbox.jsx, DecideModal.jsx)
 *   5. hr.globex       approves the second step - the LEAVE definition is REPORTING_MANAGER then ROLE hr (V089)
 *   6. employee.globex sees it Approved in My Leave (MyLeave.jsx, MyRequests.jsx)
 *
 * Idempotent where the screens allow: an existing type, policy, allocation or request is reused.
 */

const LEAVE_TYPE = { name: 'E2E Casual', code: 'E2ECL', annualDays: 12 };
const EMPLOYEE = ROLES.employee;
const YEAR = new Date().getFullYear();

/**
 * The tenant's leave year runs 1 April - 31 March (the server names it when it refuses an earlier
 * date). The screens used to assume 1 January - 31 December; fixed as D-93 (allocation window from
 * the company profile) and D-55 (policy date defaults on the server). The FINDING tests at the end
 * prove each screen now works without a wire workaround.
 */
const LEAVE_YEAR_START_MONTH = 4;
function leaveYearStart(today = new Date()) {
  const year = today.getMonth() + 1 >= LEAVE_YEAR_START_MONTH ? today.getFullYear() : today.getFullYear() - 1;
  return new Date(year, LEAVE_YEAR_START_MONTH - 1, 1);
}
/** Shared across the serial tests. */
const state = {
  leaveDate: null, // yyyy-mm-dd the request was made for
  alreadyApproved: false,
  viewItem: null, // { url, status, body, problem } from the manager's View Item (test 9 reports it)
};

async function withRole(browser, role, body) {
  const { context, page } = await openAs(browser, role);
  try {
    await body(page);
  } finally {
    await context.close();
  }
}

test.describe.serial('leave request flow', () => {
test('1. HR creates the leave type "E2E Casual" with a 12-day policy', async ({ browser }) => {
  test.setTimeout(180_000);
  await withRole(browser, 'hr', async (page) => {
    await gotoApp(page, '/leave/types');
    await waitForSettled(page, 45_000);
    await expectNoErrorScreen(page, '/leave/types');
    await expect(page.getByRole('heading', { name: 'Leave Types' })).toBeVisible();

    const row = page.locator('.ant-table-row').filter({ hasText: LEAVE_TYPE.name });

    if ((await row.count()) === 0) {
      await page.getByRole('button', { name: 'Add Leave Type' }).click();
      const drawer = page.getByRole('dialog').filter({ hasText: 'Add Leave Type' });
      await expect(drawer).toBeVisible();
      await drawer.getByLabel('Name').fill(LEAVE_TYPE.name);
      await drawer.getByLabel('Code').fill(LEAVE_TYPE.code);
      // Unit defaults to Days and Paid Leave to on (LeaveTypes.jsx handleOpenCreate); assert, do not toggle.
      await expect(drawer.locator('.ant-select-selection-item')).toHaveText('Days');
      await expect(drawer.getByLabel('Paid Leave')).toBeChecked();
      // Valid From is required on the form since D-90; HR fills today, as they would.
      await pickDate(page, drawer.getByLabel('Valid From'), isoDate(new Date()));
      await drawer.getByRole('button', { name: 'Save' }).click();
      await expectSuccessDialog(page, 'create leave type');
      await waitForSettled(page);
    } else {
      test.info().annotations.push({ type: 'reused', description: `leave type ${LEAVE_TYPE.name} already exists` });
    }
    await expect(row.first(), `the row for ${LEAVE_TYPE.name}`).toBeVisible();

    // The policy is (re)saved every run: saving it again is idempotent, and the allocation below needs
    // it effective from the leave year's start.
    await row.first().getByRole('button', { name: 'Policy' }).click();
    const policyForm = page.locator('[data-testid="policy-form"]');
    await expect(policyForm).toBeVisible();
    await policyForm.getByLabel('Annual Days').fill(String(LEAVE_TYPE.annualDays));
    // No accrual: the switch starts off for a new policy; turn it off if an earlier run left it on.
    const accrual = policyForm.getByLabel('Accrual Enabled');
    if (await accrual.isChecked()) await accrual.click();
    // Exceed balance mode: "No Limit" is the default (PolicyForm.jsx); pick it explicitly.
    await selectOption(page, policyForm.getByLabel('Exceed Balance Mode'), 'No Limit');
    await policyForm.getByRole('button', { name: 'Save Policy' }).click();

    let outcome = await dismissSwal(page, 20_000);
    expect(outcome, 'save policy: an outcome dialog appears').not.toBeNull();
    if (outcome.kind === 'error' && /effectiveFrom/.test(outcome.text)) {
      // SCREEN DEFECT (test 7 below reports it; a failure here would stop the serial chain): PolicyForm.jsx
      // offers no "effective from" field, yet the server requires `effectiveFrom`, so HR cannot save any
      // policy from the screen. The save is retried with today's date added on the wire so the rest of
      // the flow (allocation, apply, two approvals) still runs.
      test.info().annotations.push({ type: 'workaround', description: `effectiveFrom added to PUT /leave-types/:id/policy: ${outcome.text}` });
      await page.route('**/leave-types/*/policy', async (route) => {
        const request = route.request();
        if (request.method() !== 'PUT') return route.continue();
        const body = JSON.parse(request.postData() || '{}');
        if (!body.effectiveFrom) body.effectiveFrom = isoDate(leaveYearStart());
        return route.continue({ postData: JSON.stringify(body) });
      });
      await policyForm.getByRole('button', { name: 'Save Policy' }).click();
      outcome = await dismissSwal(page, 20_000);
      expect(outcome, 'save policy (retry): an outcome dialog appears').not.toBeNull();
    }
    expect(outcome.kind, `save policy: "${outcome.title}" - ${outcome.text}`).not.toBe('error');
    await waitForSettled(page);
    await expect(row.first()).toContainText('12 days/yr');
  });
});

test('2. HR allocates "E2E Casual" to GLBX-0001 for the current year', async ({ browser }) => {
  test.setTimeout(240_000);
  await withRole(browser, 'hr', async (page) => {
    await gotoApp(page, '/leave/allocations');
    // The screen reads every employee's balances before it settles.
    await waitForSettled(page, 120_000);
    await expectNoErrorScreen(page, '/leave/allocations');
    await expect(page.getByRole('heading', { name: 'Leave Allocations' })).toBeVisible();

    const table = page.locator('.ant-table').first();
    const headers = (await table.locator('.ant-table-thead th').allInnerTexts()).map((h) => h.trim());
    const typeColumn = headers.indexOf(LEAVE_TYPE.name);
    const employeeRow = table.locator('.ant-table-row').filter({ hasText: EMPLOYEE.employeeNumber }).first();

    if (typeColumn >= 0 && (await employeeRow.count()) > 0) {
      const current = ((await employeeRow.locator('td').nth(typeColumn).innerText()) || '').trim();
      if (current && current !== '-') {
        test.info().annotations.push({ type: 'reused', description: `allocation exists: ${current} days remaining` });
        return;
      }
    }

    // D-93: the screen builds the leave-year window from the company profile (1 April for this tenant),
    // so the dates go to the server as typed - no rewrite on the wire any more.

    await page.getByRole('button', { name: 'Allocate' }).click();
    const modal = page.getByRole('dialog').filter({ hasText: 'Manual Leave Allocation' });
    await expect(modal).toBeVisible();
    await selectOption(page, modal.getByLabel('Employee', { exact: true }), EMPLOYEE.employeeNumber);
    await selectOption(page, modal.getByLabel('Leave Type', { exact: true }), `${LEAVE_TYPE.name} (${LEAVE_TYPE.code})`);
    await modal.getByLabel('Leave Year').fill(String(YEAR));
    await modal.getByLabel('Opening Days').fill(String(LEAVE_TYPE.annualDays));
    await modal.getByRole('button', { name: 'Save Allocation' }).click();

    const outcome = await dismissSwal(page, 30_000);
    expect(outcome, 'an outcome dialog appears after Save Allocation').not.toBeNull();
    if (outcome.kind === 'error' && /conflict|already/i.test(`${outcome.title} ${outcome.text}`)) {
      test.info().annotations.push({ type: 'reused', description: `allocation already recorded: ${outcome.text}` });
      await page.keyboard.press('Escape');
    } else {
      expect(outcome.kind, `allocate: "${outcome.title}" - ${outcome.text}`).not.toBe('error');
    }
    await waitForSettled(page, 120_000);
  });
});

test('3. The employee applies for one day of "E2E Casual" next Monday', async ({ browser }) => {
  test.setTimeout(180_000);
  await withRole(browser, 'employee', async (page) => {
    // Reuse a request from an earlier run on the same date rather than make a second one.
    await gotoApp(page, '/me/leave');
    await waitForSettled(page, 45_000);
    await expectNoErrorScreen(page, '/me/leave');
    // The first Monday (up to eight weeks out) that has no request yet, so every run goes through
    // the approvals; a run on a fully booked calendar reuses the last one.
    const rows = page.locator('[data-testid="panel-leave"] .ant-table-row');
    const listed = (await rows.allInnerTexts()).join('\n');
    let firstFree = -1;
    for (let week = 0; week < 8 && firstFree < 0; week += 1) {
      if (!listed.includes(isoDate(nextMonday(7 * week)))) firstFree = week;
    }
    if (firstFree < 0) {
      const target = isoDate(nextMonday(49));
      const existing = rows.filter({ hasText: target }).first();
      const status = ((await existing.innerText()) || '').trim();
      state.leaveDate = target;
      state.alreadyApproved = /approved/i.test(status);
      test.info().annotations.push({ type: 'reused', description: `request for ${target} exists (${status.replace(/\s+/g, ' ')})` });
      return;
    }
    const weekOffset = 7 * firstFree;

    await gotoApp(page, '/me/leave/apply');
    await waitForSettled(page, 45_000);
    await expectNoErrorScreen(page, '/me/leave/apply');
    const form = page.locator('[data-testid="apply-leave-page"]');
    await expect(form.getByRole('heading', { name: 'Apply for Leave' })).toBeVisible();

    // A seeded holiday on the chosen Monday is refused by the server: move one working day on and retry.
    let submitted = null;
    for (let attempt = 0; attempt < 4 && !submitted; attempt += 1) {
      const date = isoDate(nextMonday(weekOffset + attempt));
      await selectOption(page, form.getByLabel('Leave Type'), LEAVE_TYPE.name);
      await pickDate(page, form.getByLabel('From Date'), date);
      await pickDate(page, form.getByLabel('To Date'), date);
      await form.getByLabel('Reason').fill(`E2E leave flow ${date}`);
      await page.locator('#btn-submit-leave').click();

      const outcome = await dismissSwal(page, 30_000);
      expect(outcome, 'an outcome dialog appears after Submit').not.toBeNull();
      if (outcome.kind !== 'error') {
        submitted = date;
      } else {
        test.info().annotations.push({ type: `attempt ${attempt + 1} refused`, description: `${date}: ${outcome.title} - ${outcome.text}` });
        if (!/holiday|weekend|working day|non-working/i.test(`${outcome.title} ${outcome.text}`)) {
          throw new Error(`apply refused: ${outcome.title} - ${outcome.text}`);
        }
      }
    }
    expect(submitted, 'the request was submitted').not.toBeNull();
    state.leaveDate = submitted;

    await page.waitForURL((url) => url.pathname === '/me/leave', { timeout: 30_000 });
    await waitForSettled(page, 45_000);
    const row = page.locator('[data-testid="panel-leave"] .ant-table-row').filter({ hasText: submitted }).first();
    await expect(row, 'the new request is listed under My Leave').toBeVisible();
    await expect(row).toContainText(/submitted|pending/i);
  });
});

/**
 * Opens /approvals as `role`, finds the LEAVE step for the employee, and approves it with a comment.
 * `viewItem` first opens the item from the inbox (/leave/requests/:id, itemRoutes.js) and comes back.
 */
async function approveAs(browser, role, { viewItem }) {
  await withRole(browser, role, async (page) => {
    await gotoApp(page, '/approvals');
    await waitForSettled(page, 45_000);
    await expectNoErrorScreen(page, '/approvals');
    await expect(page.getByRole('heading', { name: 'Pending Approvals' })).toBeVisible();

    const row = page
      .locator('.ant-table-row')
      .filter({ hasText: 'LEAVE' })
      .filter({ hasText: EMPLOYEE.displayName.split(' ')[0] })
      .first();
    await expect(row, `a LEAVE step for ${EMPLOYEE.displayName} waits in ${role}'s inbox`).toBeVisible();

    if (viewItem) {
      // View Item → /leave/requests/:id must open the detail, not Not Found (D-72). The server's
      // answer to the detail call is recorded so a refusal names its reason.
      const detailCall = page.waitForResponse((r) => /\/api\/v1\/leave-requests\/[0-9a-f-]{36}$/.test(r.url()) && r.request().method() === 'GET', { timeout: 45_000 }).catch(() => null);
      await row.getByRole('button', { name: 'View Item' }).click();
      await page.waitForURL(/\/leave\/requests\/[0-9a-f-]{36}$/, { timeout: 30_000 });
      const detail = await detailCall;
      const detailBody = detail ? (await detail.text().catch(() => '')).slice(0, 300) : '(no call seen)';
      await waitForSettled(page, 45_000);
      // Recorded, not asserted here: a failure would stop the serial chain. Test 9 reports it.
      state.viewItem = { url: page.url(), status: detail ? detail.status() : null, body: detailBody, problem: await findErrorScreen(page) };
      test.info().annotations.push({ type: 'View Item', description: `${state.viewItem.status} ${detailBody}${state.viewItem.problem ? ` → ${state.viewItem.problem}` : ''}` });
      await dismissSwal(page, 500);

      await page.goBack();
      await page.waitForURL((url) => url.pathname === '/approvals', { timeout: 30_000 });
      await waitForSettled(page, 45_000);
    }

    await row.getByRole('button', { name: 'Approve' }).click();
    const modal = page.getByRole('dialog').filter({ hasText: 'Approve Request' });
    await expect(modal).toBeVisible();
    await modal.locator('#input-decide-comment').fill(`Approved by the e2e suite (${role})`);
    await modal.locator('#btn-confirm-decide').click();
    const outcome = await expectSuccessDialog(page, `${role} approve`);
    expect(outcome.title).toMatch(/decision recorded/i);
  });
}

test('4. The manager views the item from the inbox and approves it with a comment', async ({ browser }) => {
  test.setTimeout(180_000);
  test.skip(state.alreadyApproved, 'the request was approved on an earlier run');
  expect(state.leaveDate, 'step 3 recorded the leave date').not.toBeNull();
  await approveAs(browser, 'manager', { viewItem: true });
});

test('5. HR approves the second step (LEAVE flow: reporting manager, then the hr role - V089)', async ({ browser }) => {
  test.setTimeout(180_000);
  test.skip(state.alreadyApproved, 'the request was approved on an earlier run');
  expect(state.leaveDate, 'step 3 recorded the leave date').not.toBeNull();
  await approveAs(browser, 'hr', { viewItem: false });
});

test('6. The employee sees the request Approved in My Leave', async ({ browser }) => {
  test.setTimeout(120_000);
  expect(state.leaveDate, 'step 3 recorded the leave date').not.toBeNull();

  await withRole(browser, 'employee', async (page) => {
    await gotoApp(page, '/me/leave');
    await waitForSettled(page, 45_000);
    await expectNoErrorScreen(page, '/me/leave');
    const row = page.locator('[data-testid="panel-leave"] .ant-table-row').filter({ hasText: state.leaveDate }).first();
    await expect(row, `the request for ${state.leaveDate} is listed`).toBeVisible();
    await expect(row, 'its status is Approved').toContainText(/approved/i);
  });
});
});

/**
 * Screen defects the flow had to work around. Outside the serial chain: a failure here must not
 * rerun the flow, and these are deterministic, so they do not retry.
 */
test.describe('screen findings', () => {
  test.describe.configure({ retries: 0 });

test('7. FINDING: HR can save a leave policy from the screen (no network workaround)', async ({ browser }) => {
  test.setTimeout(120_000);
  await withRole(browser, 'hr', async (page) => {
    await gotoApp(page, '/leave/types');
    await waitForSettled(page, 45_000);
    const row = page.locator('.ant-table-row').filter({ hasText: LEAVE_TYPE.name }).first();
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: 'Policy' }).click();
    const policyForm = page.locator('[data-testid="policy-form"]');
    await expect(policyForm).toBeVisible();
    await policyForm.getByLabel('Annual Days').fill(String(LEAVE_TYPE.annualDays));
    await policyForm.getByRole('button', { name: 'Save Policy' }).click();
    // Fails while PolicyForm.jsx sends no effectiveFrom: "Validation Error - effectiveFrom must not be null".
    await expectSuccessDialog(page, 'screen /leave/types > Policy > Save Policy');
  });
});

test('8. FINDING: HR can allocate a leave type from the screen (no network workaround)', async ({ browser }) => {
  test.setTimeout(240_000);
  // GLBX-0002 (HR's own record), not GLBX-0001: step 2 already allocated to GLBX-0001 on the wire, and the
  // server reports that conflict before it looks the policy up, which would hide the defect.
  const OTHER_EMPLOYEE = 'GLBX-0002';
  await withRole(browser, 'hr', async (page) => {
    await gotoApp(page, '/leave/allocations');
    await waitForSettled(page, 120_000);
    await page.getByRole('button', { name: 'Allocate' }).click();
    const modal = page.getByRole('dialog').filter({ hasText: 'Manual Leave Allocation' });
    await expect(modal).toBeVisible();
    await selectOption(page, modal.getByLabel('Employee', { exact: true }), OTHER_EMPLOYEE);
    await selectOption(page, modal.getByLabel('Leave Type', { exact: true }), `${LEAVE_TYPE.name} (${LEAVE_TYPE.code})`);
    await modal.getByLabel('Leave Year').fill(String(YEAR));
    await modal.getByLabel('Opening Days').fill(String(LEAVE_TYPE.annualDays));
    await modal.getByRole('button', { name: 'Save Allocation' }).click();
    // Failed before D-93 with "No effective policy found for leave type <uuid>" (the screen sent 1 January as the
    // year start). A Conflict means the row exists already from an earlier run.
    const outcome = await dismissSwal(page, 30_000);
    expect(outcome, 'an outcome dialog appears').not.toBeNull();
    if (outcome.kind === 'error' && /conflict|already/i.test(`${outcome.title} ${outcome.text}`)) return;
    expect(outcome.kind, `screen /leave/allocations > Allocate: "${outcome.title}" - ${outcome.text}`).not.toBe('error');
  });
});

test('9. FINDING: the manager can open a leave request from the inbox (View Item → /leave/requests/:id)', async ({ browser }) => {
  test.setTimeout(180_000);
  let detailPath = state.viewItem ? new URL(state.viewItem.url).pathname : null;
  if (!detailPath) {
    // Step 4 was skipped: take any of the employee's requests (My Leave's view button carries its id).
    await withRole(browser, 'employee', async (page) => {
      await gotoApp(page, '/me/leave');
      await waitForSettled(page, 45_000);
      const view = page.locator('[data-testid="panel-leave"] [id^="btn-view-"]').first();
      await expect(view, 'the employee has at least one leave request').toBeVisible();
      detailPath = `/leave/requests/${(await view.getAttribute('id')).replace('btn-view-', '')}`;
    });
  }
  const seen = state.viewItem || { status: '-', body: '(step 4 skipped)' };
  // The same screen as the manager reaches it from the inbox: the request is decided by now, but the detail call is the same.
  await withRole(browser, 'manager', async (page) => {
    const detailCall = page.waitForResponse((r) => /\/api\/v1\/leave-requests\/[0-9a-f-]{36}$/.test(r.url()) && r.request().method() === 'GET', { timeout: 45_000 }).catch(() => null);
    await gotoApp(page, detailPath);
    const detail = await detailCall;
    const body = detail ? (await detail.text().catch(() => '')).slice(0, 300) : '(no call seen)';
    await waitForSettled(page, 45_000);
    const problem = await findErrorScreen(page);
    expect(problem, `screen ${detailPath} as manager: ${problem}; GET answered ${detail ? detail.status() : '-'} ${body} (from the inbox: ${seen.status} ${seen.body})`).toBeNull();
    await expect(page.getByRole('heading', { name: 'Leave Request Details' })).toBeVisible();
  });
});
});
