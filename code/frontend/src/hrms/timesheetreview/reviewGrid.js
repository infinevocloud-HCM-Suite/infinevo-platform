import React from 'react';
import { Typography } from 'antd';
import { DAY_LABELS, formatHundredths, toHundredths, weekDates } from '../timesheet/weekGrid.js';

/** Rows of { key, project, task, cells{date: hundredths}, descriptions{date: string}, total } for a list of project lines (W-48.3 §5). */
export function taskRows(projects) {
  const rows = [];
  const seen = new Map(); // a task may repeat on a project (V185): each row keeps its own key
  for (const p of projects || []) {
    for (const t of p.tasks || []) {
      const cells = {};
      const descriptions = {};
      let total = 0;
      for (const d of t.days || []) {
        const h = toHundredths(d.hours) || 0;
        cells[d.date] = (cells[d.date] || 0) + h;
        total += h;
        const desc = d.description ? String(d.description).trim() : '';
        if (desc) {
          descriptions[d.date] = descriptions[d.date] ? `${descriptions[d.date]}\n${desc}` : desc;
        }
      }
      const base = `${p.project_id}:${t.task_id}`;
      const n = seen.get(base) || 0;
      seen.set(base, n + 1);
      rows.push({
        key: n > 0 ? `${base}#${n}` : base,
        project: p.project_name || p.project_id,
        task: t.task_title || t.task_id,
        cells,
        descriptions,
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
      render: (_, row) => {
        const h = row.cells[date];
        const desc = row.descriptions?.[date];
        if (!h && !desc) return '';
        if (!desc) return formatHundredths(h);
        return React.createElement(
          'div',
          { style: { textAlign: 'right' } },
          React.createElement('div', null, h ? formatHundredths(h) : ''),
          React.createElement(
            Typography.Text,
            {
              type: 'secondary',
              style: {
                fontSize: 12,
                display: 'block',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
                marginTop: 2,
                textAlign: 'right',
              },
              title: desc,
            },
            desc
          )
        );
      },
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

/** Flatten all day descriptions into an ordered list of { date, project, task, hours, description }. */
export function collectDescriptions(rows, dates) {
  const list = [];
  for (const r of rows || []) {
    const dayKeys = dates && dates.length > 0 ? dates : Object.keys(r.descriptions || {}).sort();
    for (const d of dayKeys) {
      if (r.descriptions?.[d]) {
        list.push({
          date: d,
          project: r.project,
          task: r.task,
          hours: r.cells?.[d] || 0,
          description: r.descriptions[d],
        });
      }
    }
  }
  return list;
}
