import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect } from '@playwright/test';

const E2E_DIR = fileURLToPath(new URL('..', import.meta.url));

/** Where the menu walks drop one screenshot per screen: test-results/screens/<role>/<slug>.png */
export const SCREENS_DIR = path.join(E2E_DIR, 'test-results', 'screens');

export const UUID_RE = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/i;
export const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}/;
/** Two or more ALL_CAPS words joined by underscores: a server code shown raw (D-67..D-70). */
export const CAPS_CODE_RE = /\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/;

/** "Leave allocations" → "leave-allocations"; "/hrms/attendance-log" → "hrms-attendance-log". */
export function slug(text) {
  return String(text)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'root';
}

/** Full-page screenshot into test-results/screens/<role>/<slug>.png. */
export async function screenshotScreen(page, role, name) {
  const dir = path.join(SCREENS_DIR, slug(role));
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${slug(name)}.png`);
  await page.screenshot({ path: file, fullPage: true }).catch(() => {});
  return file;
}

/**
 * Waits until nothing on the page is still loading: no active Skeleton, no spinning Spin, no table
 * in its loading state. Returns false when the deadline passes with something still loading.
 */
export async function waitForSettled(page, timeout = 30_000) {
  const busy = page.locator('.ant-skeleton-active, .ant-spin-spinning, .ant-table .ant-spin-nested-loading > .ant-spin');
  const deadline = Date.now() + timeout;
  // Let the first request go out before counting.
  await page.waitForTimeout(300);
  while (Date.now() < deadline) {
    if ((await busy.count()) === 0) {
      await page.waitForTimeout(200);
      if ((await busy.count()) === 0) return true;
    }
    await page.waitForTimeout(250);
  }
  return false;
}

/** The shell header's user menu button (Header.jsx) - present on every signed-in page. */
const SHELL_HEADER = '[data-testid="user-display"]';

/**
 * Opens `path` in the signed-in app and waits for the shell to render. A cold Vite dev server, or a
 * fresh context's first load, sometimes answers with a blank page: one reload brings it back.
 */
export async function gotoApp(page, path) {
  await page.goto(path, { timeout: 120_000 }).catch(() => {});
  const header = page.locator(SHELL_HEADER);
  if (!(await header.waitFor({ state: 'visible', timeout: 60_000 }).then(() => true, () => false))) {
    await page.reload({ timeout: 120_000 }).catch(() => {});
    await expect(header, `the shell renders at ${path}`).toBeVisible({ timeout: 90_000 });
  }
}

// ---------------------------------------------------------------------------------------------
// Error screens
// ---------------------------------------------------------------------------------------------

/**
 * The shell's and the panels' failure screens, by the words they show (NotFound.jsx, NotEntitled.jsx,
 * AccessProblem.jsx, NoModules.jsx, AppShell.jsx "Navigation Unavailable", PortalLayout.jsx).
 */
const ERROR_TITLES = [
  /^404$/,
  /not found/i,
  /module not subscribed/i,
  /your account is not linked/i,
  /we could not sign you in/i,
  /navigation unavailable/i,
  /no modules available/i,
  /panel not available/i,
  /unable to/i,
  /something went wrong/i,
  /subscription suspended/i,
];

const ERROR_TEXTS = ['Panel component not found', 'Something went wrong', 'Leave request not found.'];

/**
 * A one-line description of the error screen on the page, or null when there is none. Checks every
 * antd Result (status 403/404/500/error/warning or an error title), the bare error texts and an
 * open SweetAlert2 error dialog (msgHelper.js errorMsg).
 */
// D-91: a login with no employee record (tenant admin, platform staff) opening a self-service screen is
// told exactly that. It is the designed answer, not a defect, so it is not reported as an error screen.
const NO_EMPLOYEE_RECORD = /No employee record is linked to your account/i;

export async function findErrorScreen(page, { noEmployeeRecordExpected = false } = {}) {
  // The error dialog first: it carries the server's message, and a screen often shows a generic
  // "not found" text beneath it once the dialog is dismissed.
  const swalError = page.locator('.swal2-popup.swal2-icon-error, .swal2-popup:has(.swal2-icon.swal2-error)').first();
  if (await swalError.isVisible().catch(() => false)) {
    const title = ((await swalError.locator('.swal2-title').innerText().catch(() => '')) || '').trim();
    const body = ((await swalError.locator('.swal2-html-container').innerText().catch(() => '')) || '').trim();
    if (!(noEmployeeRecordExpected && NO_EMPLOYEE_RECORD.test(body))) {
      return `error dialog "${title}"${body ? ` - ${body}` : ''}`;
    }
  }

  const results = page.locator('.ant-result');
  const count = await results.count();
  for (let i = 0; i < count; i += 1) {
    const result = results.nth(i);
    if (!(await result.isVisible().catch(() => false))) continue;
    const title = ((await result.locator('.ant-result-title').innerText().catch(() => '')) || '').trim();
    const subtitle = ((await result.locator('.ant-result-subtitle').innerText().catch(() => '')) || '').trim();
    const className = (await result.getAttribute('class')) || '';
    const badStatus = /ant-result-(403|404|500|error|warning)\b/.test(className);
    if (noEmployeeRecordExpected && NO_EMPLOYEE_RECORD.test(subtitle)) continue;
    if (badStatus || ERROR_TITLES.some((re) => re.test(title))) {
      return `error screen "${title}"${subtitle ? ` - ${subtitle}` : ''}`;
    }
  }

  for (const text of ERROR_TEXTS) {
    const hit = page.getByText(text, { exact: false }).first();
    if (await hit.isVisible().catch(() => false)) return `page text "${text}"`;
  }

  return null;
}

/** Fails the test when the page shows an error screen. `where` names the screen in the message. */
export async function expectNoErrorScreen(page, where = page.url()) {
  const found = await findErrorScreen(page);
  expect(found, `${where} shows ${found}`).toBeNull();
}

// ---------------------------------------------------------------------------------------------
// SweetAlert2 dialogs
// ---------------------------------------------------------------------------------------------

/**
 * Closes an open SweetAlert2 dialog (successMsg / errorMsg) with its OK button and reports it.
 * Resolves `null` when none is open within `timeout`.
 *
 * @returns {Promise<null | { kind: 'success' | 'error' | 'other', title: string, text: string }>}
 */
export async function dismissSwal(page, timeout = 1_000) {
  const popup = page.locator('.swal2-popup');
  try {
    await popup.waitFor({ state: 'visible', timeout });
  } catch {
    return null;
  }
  const className = (await popup.getAttribute('class')) || '';
  const hasErrorIcon = (await popup.locator('.swal2-icon.swal2-error').count()) > 0;
  const hasSuccessIcon = (await popup.locator('.swal2-icon.swal2-success').count()) > 0;
  const kind = hasErrorIcon || /swal2-icon-error/.test(className) ? 'error' : hasSuccessIcon || /swal2-icon-success/.test(className) ? 'success' : 'other';
  const title = ((await popup.locator('.swal2-title').innerText().catch(() => '')) || '').trim();
  const text = ((await popup.locator('.swal2-html-container').innerText().catch(() => '')) || '').trim();
  await popup.locator('.swal2-confirm').click({ timeout: 5_000 }).catch(() => page.keyboard.press('Escape'));
  await popup.waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
  return { kind, title, text };
}

/** Waits for the outcome dialog a save shows and fails the test when it is an error. */
export async function expectSuccessDialog(page, step) {
  const dialog = await dismissSwal(page, 20_000);
  expect(dialog, `${step}: no outcome dialog appeared`).not.toBeNull();
  expect(dialog.kind, `${step}: "${dialog.title}" - ${dialog.text}`).not.toBe('error');
  return dialog;
}

// ---------------------------------------------------------------------------------------------
// Sidebar menu
// ---------------------------------------------------------------------------------------------

const SIDER_MENU = '.ant-layout-sider .ant-menu';

/**
 * Walks the sidebar (AppShell.jsx builds it from the navigation feed): opens every collapsed group
 * and returns one entry per leaf.
 *
 * @returns {Promise<Array<{ label: string, href: string, group: string }>>}
 */
export async function collectMenu(page) {
  const menu = page.locator(SIDER_MENU).first();
  await expect(menu, 'the sidebar menu renders').toBeVisible({ timeout: 30_000 });

  // antd renders a group's children only once it has been opened; open them until none are closed.
  for (let round = 0; round < 10; round += 1) {
    const closed = menu.locator('.ant-menu-submenu:not(.ant-menu-submenu-open) > .ant-menu-submenu-title');
    const count = await closed.count();
    if (count === 0) break;
    for (let i = 0; i < count; i += 1) {
      const title = closed.nth(i);
      if (await title.isVisible().catch(() => false)) {
        await title.click();
        await page.waitForTimeout(150);
      }
    }
    await page.waitForTimeout(250);
  }

  const leaves = await menu.locator('li.ant-menu-item a[href]').evaluateAll((anchors) =>
    anchors.map((a) => {
      const group = a.closest('.ant-menu-submenu');
      const groupTitle = group ? group.querySelector(':scope > .ant-menu-submenu-title') : null;
      return {
        label: (a.textContent || '').trim(),
        href: a.getAttribute('href'),
        group: groupTitle ? (groupTitle.textContent || '').trim() : '',
      };
    }),
  );

  const seen = new Set();
  return leaves.filter((leaf) => {
    if (!leaf.href || seen.has(leaf.href)) return false;
    seen.add(leaf.href);
    return true;
  });
}

/**
 * Opens a menu leaf in-app (the link in the sidebar, opening its group first) and falls back to a
 * full navigation when the link is not reachable.
 */
export async function openMenuLeaf(page, leaf) {
  const menu = page.locator(SIDER_MENU).first();
  const link = menu.locator(`li.ant-menu-item a[href="${leaf.href}"]`).first();
  if (!(await link.isVisible().catch(() => false)) && leaf.group) {
    const title = menu
      .locator('.ant-menu-submenu:not(.ant-menu-submenu-open) > .ant-menu-submenu-title')
      .filter({ hasText: leaf.group })
      .first();
    if (await title.isVisible().catch(() => false)) {
      await title.click();
      await page.waitForTimeout(200);
    }
  }
  if (await link.isVisible().catch(() => false)) {
    await link.click();
    await page.waitForURL((url) => url.pathname === leaf.href || url.pathname.startsWith(`${leaf.href}/`), { timeout: 15_000 }).catch(() => {});
  }
  if (new URL(page.url()).pathname !== leaf.href) {
    await page.goto(leaf.href);
  }
  await waitForSettled(page);
}

/**
 * The first visible heading inside the content area, or '' when none appears within `timeout`:
 * any role=heading (h1-h6, antd Typography.Title), a Card title or a PageHeader title.
 */
export async function pageHeading(page, timeout = 10_000) {
  const content = page.locator('.ant-layout-content');
  const headings = content.locator(
    [
      '[role="heading"]',
      'h1, h2, h3, h4, h5, h6',
      '.ant-typography:is(h1, h2, h3, h4, h5)',
      '.ant-card-head-title',
      '.ant-page-header-heading-title',
    ].join(', '),
  );
  const deadline = Date.now() + timeout;
  do {
    const count = await headings.count();
    for (let i = 0; i < count; i += 1) {
      const heading = headings.nth(i);
      if (await heading.isVisible().catch(() => false)) {
        const text = ((await heading.innerText().catch(() => '')) || '').trim();
        if (text) return text;
      }
    }
    await page.waitForTimeout(250);
  } while (Date.now() < deadline);
  return '';
}

// ---------------------------------------------------------------------------------------------
// Tables
// ---------------------------------------------------------------------------------------------

/**
 * Reads every antd table on the page: header cells and the first data row. Nested tables inside an
 * expanded row are read as tables of their own; the outer table's own thead/tbody are the first
 * ones found under it, so the two do not mix.
 */
async function readTables(page) {
  return page.locator('.ant-table').evaluateAll((tables) =>
    tables.map((table, index) => {
      const card = table.closest('.ant-card');
      const cardTitle = card ? card.querySelector('.ant-card-head-title') : null;
      const thead = table.querySelector('thead.ant-table-thead');
      const tbody = table.querySelector('tbody.ant-table-tbody');
      const headerCells = thead ? Array.from(thead.querySelectorAll(':scope > tr:first-child > th')) : [];
      const headers = headerCells.map((th) => ({
        text: (th.textContent || '').trim(),
        structural:
          th.classList.contains('ant-table-selection-column') ||
          th.classList.contains('ant-table-row-expand-icon-cell') ||
          th.classList.contains('ant-table-cell-scrollbar'),
      }));
      const firstRow = tbody ? tbody.querySelector(':scope > tr.ant-table-row') : null;
      const cells = firstRow ? Array.from(firstRow.children).map((td) => (td.textContent || '').trim()) : null;
      return {
        name: cardTitle ? (cardTitle.textContent || '').trim() : `table ${index + 1}`,
        headers,
        cells,
      };
    }),
  );
}

/**
 * Soft checks on every antd table on the page. Header cells must have words; the first data row
 * must show no raw UUID, no ISO `yyyy-mm-dd` and no ALL_CAPS_CODE. Every offender is collected and
 * reported through one `expect.soft`, so a screen reports all of them at once.
 *
 * @returns {Promise<Array<{ table: string, column: string, kind: string, value: string }>>}
 */
export async function checkTable(page, where = page.url()) {
  const tables = await readTables(page);
  const offenders = [];

  for (const table of tables) {
    table.headers.forEach((header, index) => {
      if (!header.structural && !header.text) {
        offenders.push({ table: table.name, column: `#${index + 1}`, kind: 'empty header', value: '' });
      }
    });
    if (!table.cells) continue;
    table.cells.forEach((value, index) => {
      const header = table.headers[index];
      if (header && header.structural) return;
      const column = header && header.text ? header.text : `#${index + 1}`;
      if (UUID_RE.test(value)) offenders.push({ table: table.name, column, kind: 'raw UUID', value });
      if (ISO_DATE_RE.test(value)) offenders.push({ table: table.name, column, kind: 'ISO date', value });
      const code = value.match(CAPS_CODE_RE);
      if (code) offenders.push({ table: table.name, column, kind: 'raw code', value: code[0] });
    });
  }

  // One line per distinct offender on this screen: a value repeated in several tables is one finding.
  const unique = [...new Map(offenders.map((o) => [`${o.table}|${o.column}|${o.kind}|${o.value}`, o])).values()];
  const report = unique.map((o) => `[${o.table}] column "${o.column}": ${o.kind} "${o.value}"`);
  expect.soft(report, `screen ${where}: ${unique.length} table finding(s)`).toEqual([]);
  return unique;
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/**
 * Labels never clicked blind: they change data, end the session or need a decision. The caller's
 * destructive list plus the plain saves and submits, so a list-screen walk only opens things.
 */
export const DESTRUCTIVE_LABELS =
  /\b(delete|remove|revoke|cancel|reject|terminate|disable|enable|sign out|stop|mark paid|pay|approve|compute|lock|unlock|retry|submit|save|send|resend|invite|run|generate|withdraw|skip|attach|upload|import|export|download|publish|finali[sz]e|close run|accrue|allocate|record)\b/i;

const CONTENT_BUTTONS = '.ant-layout-content button.ant-btn:not([disabled]):not(.ant-btn-loading):not(.ant-btn-dangerous)';

/** Closes whatever overlay a click opened: drawer, modal, popconfirm, dropdown. */
export async function closeOverlays(page) {
  for (let round = 0; round < 4; round += 1) {
    let closedSomething = false;

    const drawer = page.locator('.ant-drawer-open .ant-drawer-content').last();
    if (await drawer.isVisible().catch(() => false)) {
      const cancel = drawer.getByRole('button', { name: /^(cancel|close|back)$/i }).first();
      if (await cancel.isVisible().catch(() => false)) await cancel.click({ timeout: 3_000 }).catch(() => {});
      else {
        const x = drawer.locator('.ant-drawer-close').first();
        if (await x.isVisible().catch(() => false)) await x.click({ timeout: 3_000 }).catch(() => {});
        else await page.keyboard.press('Escape');
      }
      closedSomething = true;
    }

    const modal = page.locator('.ant-modal-wrap:not([style*="display: none"]) .ant-modal-content').last();
    if (await modal.isVisible().catch(() => false)) {
      const cancel = modal.getByRole('button', { name: /^(cancel|close|no|back|return to portal)$/i }).first();
      if (await cancel.isVisible().catch(() => false)) await cancel.click({ timeout: 3_000 }).catch(() => {});
      else {
        const x = modal.locator('.ant-modal-close').first();
        if (await x.isVisible().catch(() => false)) await x.click({ timeout: 3_000 }).catch(() => {});
        else await page.keyboard.press('Escape');
      }
      closedSomething = true;
    }

    const popconfirm = page.locator('.ant-popconfirm:not(.ant-popover-hidden)').last();
    if (await popconfirm.isVisible().catch(() => false)) {
      const cancel = popconfirm.locator('.ant-popconfirm-buttons button').first();
      if (await cancel.isVisible().catch(() => false)) await cancel.click({ timeout: 3_000 }).catch(() => {});
      else await page.keyboard.press('Escape');
      closedSomething = true;
    }

    const floating = page.locator('.ant-dropdown:not(.ant-dropdown-hidden), .ant-popover:not(.ant-popover-hidden):not(.ant-popconfirm), .ant-select-dropdown:not(.ant-select-dropdown-hidden), .ant-picker-dropdown:not(.ant-picker-dropdown-hidden)').last();
    if (await floating.isVisible().catch(() => false)) {
      await page.keyboard.press('Escape');
      closedSomething = true;
    }

    if (!closedSomething) return;
    await page.waitForTimeout(300);
  }
}

/**
 * Clicks every visible, enabled antd button in the content area whose label is not destructive,
 * one at a time: after each click the page must show no error screen; an opened drawer, modal or
 * dropdown is closed again; a navigation is undone with Back. Stops after `maxButtons` clicks or
 * `timeBudgetMs`, whichever comes first. Problems are soft failures collected per button, so one
 * screen reports all of them.
 *
 * @returns {Promise<{ clicked: string[], problems: string[], exhausted: boolean }>}
 */
export async function clickEveryButton(
  page,
  { maxButtons = 25, timeBudgetMs = 45_000, where = page.url(), noEmployeeRecordExpected = false } = {}
) {
  const started = Date.now();
  const clicked = new Set();
  const problems = [];
  const startUrl = page.url();

  while (clicked.size < maxButtons && Date.now() - started < timeBudgetMs) {
    const buttons = page.locator(CONTENT_BUTTONS);
    const count = await buttons.count();
    let target = null;
    let key = null;

    for (let i = 0; i < count; i += 1) {
      const button = buttons.nth(i);
      if (!(await button.isVisible().catch(() => false))) continue;
      const text = ((await button.innerText().catch(() => '')) || '').trim().replace(/\s+/g, ' ');
      const aria = (await button.getAttribute('aria-label').catch(() => '')) || '';
      const label = text || aria;
      const candidate = label || `icon-button#${i}`;
      if (clicked.has(candidate)) continue;
      if (label && DESTRUCTIVE_LABELS.test(label)) {
        clicked.add(candidate); // remembered, never clicked
        continue;
      }
      target = button;
      key = candidate;
      break;
    }
    if (!target) break;

    clicked.add(key);
    const before = page.url();
    try {
      await target.click({ timeout: 5_000 });
    } catch (err) {
      problems.push(`"${key}": could not click (${String(err.message).split('\n')[0]})`);
      continue;
    }
    await page.waitForTimeout(500);
    await waitForSettled(page, 15_000);

    const dialog = await dismissSwal(page, 500);
    if (dialog && dialog.kind === 'error') problems.push(`"${key}": error dialog "${dialog.title}" - ${dialog.text}`);

    const error = await findErrorScreen(page, { noEmployeeRecordExpected });
    if (error) problems.push(`"${key}": ${error}`);

    await closeOverlays(page);

    if (page.url() !== before) {
      await page.goBack({ waitUntil: 'commit' }).catch(() => {});
      await waitForSettled(page, 15_000);
      if (new URL(page.url()).pathname !== new URL(startUrl).pathname) {
        await page.goto(startUrl);
        await waitForSettled(page, 15_000);
      }
    }
  }

  const clickedList = [...clicked];
  expect.soft(problems, `${where} buttons failed:\n  ${problems.join('\n  ')}`).toEqual([]);
  return { clicked: clickedList, problems, exhausted: clicked.size >= maxButtons || Date.now() - started >= timeBudgetMs };
}

// ---------------------------------------------------------------------------------------------
// antd form helpers for the flows
// ---------------------------------------------------------------------------------------------

/** Picks `optionText` in the antd Select whose search input is `input` (the element a form label points at). */
export async function selectOption(page, input, optionText) {
  // The chosen value's <span> sits over the search input and intercepts clicks; open on the selector box.
  const selector = input.locator('xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " ant-select-selector ")][1]');
  await selector.click();
  const dropdown = page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').last();
  await dropdown.waitFor({ state: 'visible', timeout: 10_000 });
  // A searchable select narrows on typing; a plain one ignores it (the input is read-only).
  if ((await input.getAttribute('readonly')) === null) {
    await input.fill(optionText);
    await page.waitForTimeout(300);
  }
  const option = dropdown.locator('.ant-select-item-option').filter({ hasText: optionText }).first();
  await expect(option, `option "${optionText}" is offered`).toBeVisible({ timeout: 10_000 });
  await option.click();
}

/** Types `yyyy-mm-dd` into an antd DatePicker input and confirms it. */
export async function pickDate(page, input, isoDate) {
  await input.click();
  await input.fill(isoDate);
  await input.press('Enter');
  await page.locator('.ant-picker-dropdown:not(.ant-picker-dropdown-hidden)').waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {});
}

/** yyyy-mm-dd in local time. */
export function isoDate(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** The next Monday strictly after today, plus `offsetDays`. */
export function nextMonday(offsetDays = 0) {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  const daysAhead = (8 - date.getDay()) % 7 || 7;
  date.setDate(date.getDate() + daysAhead + offsetDays);
  return date;
}
