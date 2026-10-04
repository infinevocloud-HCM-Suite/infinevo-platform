import dayjs from 'dayjs';

/**
 * The week grid's pure half (W-48.2 §5): a timesheet response to grid rows and back to the request body
 * (`TimesheetRequest.java`), totals, and the checks the server makes (`W-42-1-timesheet-entry.md` §4).
 *
 * Hours are `BigDecimal` on the server. Here a cell holds them as a string and every sum is taken in
 * hundredths, as integers, so `7.25 + 0.75` is `8.00` and never `7.999…`.
 */

export const DAY_LIMIT_HUNDREDTHS = 2400;
export const DATE_FORMAT = 'YYYY-MM-DD';
export const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export const STATUS_COLOR = {
  NOT_STARTED: 'default',
  DRAFT: 'gold',
  SUBMITTED: 'blue',
  APPROVED: 'green',
  REJECTED: 'red',
  CANCELLED: 'default',
};

export const STATUS_LABEL = {
  NOT_STARTED: 'Not started',
  DRAFT: 'Draft',
  SUBMITTED: 'Submitted',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

/** The Monday on or before a date, as `YYYY-MM-DD`. Sunday belongs to the week that began six days earlier. */
export function mondayOf(date) {
  const d = dayjs(date);
  return d.subtract((d.day() + 6) % 7, 'day').format(DATE_FORMAT);
}

/** True when the value is a real `YYYY-MM-DD` date. */
export function isIsoDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const d = dayjs(value);
  return d.isValid() && d.format(DATE_FORMAT) === value;
}

export function isMonday(value) {
  return isIsoDate(value) && dayjs(value).day() === 1;
}

/** The seven dates of the week, Monday first. */
export function weekDates(weekStart) {
  const start = dayjs(weekStart);
  return Array.from({ length: 7 }, (_, i) => start.add(i, 'day').format(DATE_FORMAT));
}

export function addWeeks(weekStart, weeks) {
  return dayjs(weekStart).add(weeks * 7, 'day').format(DATE_FORMAT);
}

/**
 * Hours to whole hundredths. Empty is 0. More than two decimals, a negative or a non-number is `null`: the
 * caller reports it rather than rounding it away.
 */
export function toHundredths(value) {
  if (value === null || value === undefined) return 0;
  const text = String(value).trim();
  if (text === '') return 0;
  const match = /^(\d+)(?:\.(\d*))?$/.exec(text);
  if (!match) return null;
  const decimals = (match[2] || '').replace(/0+$/, '');
  if (decimals.length > 2) return null;
  return Number(match[1]) * 100 + Number(decimals.padEnd(2, '0'));
}

/** Whole hundredths to `h.hh`. */
export function formatHundredths(hundredths) {
  const n = Math.max(0, Math.trunc(hundredths || 0));
  const cents = String(n % 100).padStart(2, '0');
  return `${Math.floor(n / 100)}.${cents}`;
}

/** Hours as the grid shows them: two decimals, or empty for none. */
export function formatHours(value) {
  const h = toHundredths(value);
  if (h === null) return String(value);
  return h === 0 ? '' : formatHundredths(h);
}

export function rowKey(projectId, taskId) {
  return `${projectId}:${taskId}`;
}

/** An empty row for a project and task. */
export function emptyRow(projectId, taskId) {
  return { key: rowKey(projectId, taskId), projectId, taskId, cells: {} };
}

/** Grid rows from a timesheet response, one per project and task, in the response's order. */
export function rowsFromResponse(timesheet) {
  const rows = [];
  for (const project of timesheet?.projects || []) {
    for (const task of project.tasks || []) {
      const row = emptyRow(project.project_id, task.task_id);
      for (const day of task.days || []) {
        row.cells[day.date] = {
          hours: formatHours(day.hours),
          description: day.description || '',
        };
      }
      rows.push(row);
    }
  }
  return rows;
}

/** Each project's own status and rejection reason, by project id. */
export function projectStatuses(timesheet) {
  const out = {};
  for (const project of timesheet?.projects || []) {
    out[project.project_id] = {
      status: project.status,
      rejectionReason: project.rejection_reason || null,
    };
  }
  return out;
}

/**
 * The request body for these rows. Cells of 0 or empty hours are dropped, then tasks with no day and projects
 * with no task. `onlyProjects`, when given, keeps those projects alone: a resubmit sends the rejected ones only.
 */
export function toRequestBody(weekStart, rows, { onlyProjects } = {}) {
  const keep = onlyProjects ? new Set(onlyProjects) : null;
  const projects = [];
  const byProject = new Map();
  for (const row of rows) {
    if (keep && !keep.has(row.projectId)) continue;
    const days = Object.keys(row.cells)
      .sort()
      .map((date) => ({ date, cell: row.cells[date] }))
      .filter(({ cell }) => {
        const h = toHundredths(cell.hours);
        return h !== null && h > 0;
      })
      .map(({ date, cell }) => ({
        date,
        hours: Number(formatHundredths(toHundredths(cell.hours))),
        description: cell.description ? cell.description : null,
      }));
    if (days.length === 0) continue;
    let project = byProject.get(row.projectId);
    if (!project) {
      project = { project_id: row.projectId, tasks: [] };
      byProject.set(row.projectId, project);
      projects.push(project);
    }
    project.tasks.push({ task_id: row.taskId, days });
  }
  return { week_start_date: weekStart, projects };
}

/** A row's total in hundredths; invalid cells count as 0. */
export function rowTotal(row) {
  return Object.values(row.cells).reduce((sum, cell) => sum + (toHundredths(cell.hours) || 0), 0);
}

/** Each date's total in hundredths across all rows. */
export function dayTotals(rows, dates) {
  const totals = Object.fromEntries(dates.map((d) => [d, 0]));
  for (const row of rows) {
    for (const date of dates) {
      totals[date] += toHundredths(row.cells[date]?.hours) || 0;
    }
  }
  return totals;
}

export function gridTotal(rows) {
  return rows.reduce((sum, row) => sum + rowTotal(row), 0);
}

/** A response's total hours in hundredths. */
export function responseTotal(timesheet) {
  return gridTotal(rowsFromResponse(timesheet));
}

/**
 * The server's rules, checked before sending (`W-42-1-timesheet-entry.md` §4): the week starts on a Monday;
 * every project is one the caller is assigned to (when `assignedProjectIds` is given); hours above 0 and at
 * most 24, two decimals; a day's total at most 24; at least one entry.
 *
 * @returns {{ ok: boolean, errors: string[], cellErrors: Object, dayErrors: Object }}
 */
export function validate(weekStart, rows, { assignedProjectIds } = {}) {
  const errors = [];
  const cellErrors = {};
  const dayErrors = {};

  if (!isMonday(weekStart)) {
    errors.push('A week starts on a Monday.');
  }
  const dates = isIsoDate(weekStart) ? weekDates(weekStart) : [];

  if (assignedProjectIds) {
    const assigned = new Set(assignedProjectIds);
    const unassigned = [...new Set(rows.map((r) => r.projectId))].filter((id) => !assigned.has(id));
    if (unassigned.length > 0) {
      errors.push('You can only log hours on projects you are assigned to.');
    }
  }

  let entries = 0;
  for (const row of rows) {
    for (const [date, cell] of Object.entries(row.cells)) {
      const h = toHundredths(cell.hours);
      if (h === null || h > DAY_LIMIT_HUNDREDTHS) {
        cellErrors[`${row.key}|${date}`] = 'Hours are above 0 and at most 24, with two decimals.';
      } else if (h > 0) {
        entries += 1;
        if (dates.length > 0 && !dates.includes(date)) {
          cellErrors[`${row.key}|${date}`] = 'The date is outside the week.';
        }
      }
    }
  }
  if (Object.keys(cellErrors).length > 0) {
    errors.push('Hours are above 0 and at most 24, with two decimals.');
  }

  const totals = dayTotals(rows, dates);
  for (const date of dates) {
    if (totals[date] > DAY_LIMIT_HUNDREDTHS) {
      dayErrors[date] = `${formatHundredths(totals[date])} hours on ${date}: a day's total is at most 24.`;
    }
  }
  errors.push(...Object.values(dayErrors));

  if (entries === 0) {
    errors.push('Enter hours on at least one day.');
  }

  return { ok: errors.length === 0, errors, cellErrors, dayErrors };
}
