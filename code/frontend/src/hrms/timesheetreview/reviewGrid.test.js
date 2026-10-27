import { describe, it, expect } from 'vitest';
import { taskRows, gridColumns, dayTotals, sumRows, collectDescriptions } from './reviewGrid.js';

describe('reviewGrid helpers (W-48.3 §5)', () => {
  const projectsFixture = [
    {
      project_id: 'p-1',
      project_name: 'Alpha',
      tasks: [
        {
          task_id: 't-1',
          task_title: 'Development',
          days: [
            { date: '2026-10-12', hours: 7.5, description: 'Built REST endpoints' },
            { date: '2026-10-13', hours: 8, description: 'Added auth filters' },
          ],
        },
      ],
    },
  ];

  it('extracts cells, descriptions, and total from projects', () => {
    const rows = taskRows(projectsFixture);
    expect(rows).toHaveLength(1);
    expect(rows[0].project).toBe('Alpha');
    expect(rows[0].task).toBe('Development');
    expect(rows[0].cells['2026-10-12']).toBe(750);
    expect(rows[0].cells['2026-10-13']).toBe(800);
    expect(rows[0].descriptions['2026-10-12']).toBe('Built REST endpoints');
    expect(rows[0].descriptions['2026-10-13']).toBe('Added auth filters');
    expect(rows[0].total).toBe(1550);
  });

  it('calculates day totals and row sum', () => {
    const rows = taskRows(projectsFixture);
    const dates = ['2026-10-12', '2026-10-13', '2026-10-14'];
    const totals = dayTotals(rows, dates);
    expect(totals).toEqual([750, 800, 0]);
    expect(sumRows(rows)).toBe(1550);
  });

  it('collects all day descriptions into an ordered list', () => {
    const rows = taskRows(projectsFixture);
    const list = collectDescriptions(rows, ['2026-10-12', '2026-10-13', '2026-10-14']);
    expect(list).toHaveLength(2);
    expect(list[0]).toEqual({
      date: '2026-10-12',
      project: 'Alpha',
      task: 'Development',
      hours: 750,
      description: 'Built REST endpoints',
    });
    expect(list[1]).toEqual({
      date: '2026-10-13',
      project: 'Alpha',
      task: 'Development',
      hours: 800,
      description: 'Added auth filters',
    });
  });

  it('builds grid columns with project and daily dates', () => {
    const { cols, dates } = gridColumns('2026-10-12');
    expect(dates).toHaveLength(7);
    expect(cols[0].title).toBe('Project');
    expect(cols[1].title).toBe('Task');
    expect(cols[cols.length - 1].title).toBe('Total');
  });

  it('builds grid columns without project when withProject is false', () => {
    const { cols } = gridColumns('2026-10-12', { withProject: false });
    expect(cols[0].title).toBe('Task');
  });
});
