import { DAY_LABELS, formatHundredths, toHundredths, weekDates } from '../timesheet/weekGrid.js';

/** Rows of { key, project, task, cells{date: hundredths}, total } for a list of project lines (W-48.3 §5). */
export function taskRows(projects) {
  const rows = [];
  for (const p of projects || []) {
    for (const t of p.tasks || []) {
      const cells = {};
      let total = 0;
      for (const d of t.days || []) {
        const h = toHundredths(d.hours) || 0;
        cells[d.date] = (cells[d.date] || 0) + h;
        total += h;
      }
      rows.push({
        key: `${p.project_id}:${t.task_id}`,
        project: p.project_name || p.project_id,
        task: t.task_title || t.task_id,
        cells,
        total,
      });
    }
  }
  return rows;
}

/** Table columns: project, task, Mon to Sun, total. Hours shown with two decimals; sums in hundredths. */
export function gridColumns(weekStart, { withProject = true } = {}) {
  const dates = weekStart ? weekDates(weekStart) : [];
  const cols = [];
  if (withProject) cols.push({ title: 'Project', dataIndex: 'project', key: 'project' });
  cols.push({ title: 'Task', dataIndex: 'task', key: 'task' });
  dates.forEach((date, i) => {
    cols.push({
      title: `${DAY_LABELS[i]} ${date.slice(5)}`,
      key: date,
      align: 'right',
      render: (_, row) => (row.cells[date] ? formatHundredths(row.cells[date]) : ''),
    });
  });
  cols.push({ title: 'Total', key: 'total', align: 'right', render: (_, row) => formatHundredths(row.total) });
  return { cols, dates };
}

export function dayTotals(rows, dates) {
  return dates.map((d) => rows.reduce((s, r) => s + (r.cells[d] || 0), 0));
}

export function sumRows(rows) {
  return rows.reduce((s, r) => s + r.total, 0);
}
