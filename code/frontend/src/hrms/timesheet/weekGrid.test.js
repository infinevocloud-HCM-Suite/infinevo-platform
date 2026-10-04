import { describe, it, expect } from 'vitest';
import {
  dayTotals,
  formatHundredths,
  gridTotal,
  isMonday,
  mondayOf,
  rowsFromResponse,
  toHundredths,
  toRequestBody,
  validate,
  weekDates,
} from './weekGrid.js';

const P1 = '11111111-1111-1111-1111-111111111111';
const P2 = '22222222-2222-2222-2222-222222222222';
const T1 = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const T2 = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const WEEK = '2026-09-28';

const response = {
  id: 'ts-1',
  week_start_date: WEEK,
  week_end_date: '2026-10-04',
  status: 'DRAFT',
  projects: [
    {
      id: 'pe-1',
      project_id: P1,
      status: 'DRAFT',
      rejection_reason: null,
      tasks: [
        {
          id: 'te-1',
          task_id: T1,
          days: [
            { id: 'd1', date: '2026-09-28', hours: 7.25, description: 'design' },
            { id: 'd2', date: '2026-09-29', hours: 8, description: null },
          ],
        },
      ],
    },
    {
      id: 'pe-2',
      project_id: P2,
      status: 'DRAFT',
      rejection_reason: null,
      tasks: [
        { id: 'te-2', task_id: T2, days: [{ id: 'd3', date: '2026-09-28', hours: 0.75, description: null }] },
      ],
    },
  ],
};

const row = (projectId, taskId, cells) => ({ key: `${projectId}:${taskId}`, projectId, taskId, cells });

describe('weekGrid (W-48.2 §7)', () => {
  it('round trips a response through the grid to the request body', () => {
    const rows = rowsFromResponse(response);
    expect(rows).toHaveLength(2);
    expect(rows[0].cells['2026-09-28']).toEqual({ hours: '7.25', description: 'design' });
    expect(rows[0].cells['2026-09-29']).toEqual({ hours: '8.00', description: '' });

    expect(toRequestBody(WEEK, rows)).toEqual({
      week_start_date: WEEK,
      projects: [
        {
          project_id: P1,
          tasks: [
            {
              task_id: T1,
              days: [
                { date: '2026-09-28', hours: 7.25, description: 'design' },
                { date: '2026-09-29', hours: 8, description: null },
              ],
            },
          ],
        },
        {
          project_id: P2,
          tasks: [{ task_id: T2, days: [{ date: '2026-09-28', hours: 0.75, description: null }] }],
        },
      ],
    });
  });

  it('sums in hundredths, so 7.25 + 0.75 is 8.00', () => {
    const rows = rowsFromResponse(response);
    const totals = dayTotals(rows, weekDates(WEEK));
    expect(totals['2026-09-28']).toBe(800);
    expect(formatHundredths(totals['2026-09-28'])).toBe('8.00');
    expect(formatHundredths(gridTotal(rows))).toBe('16.00');
    // 0.1 + 0.2 in floating point is 0.30000000000000004; in hundredths it is 0.30
    expect(formatHundredths(toHundredths('0.1') + toHundredths('0.2'))).toBe('0.30');
  });

  it('fails a day of 24.25 across tasks', () => {
    const rows = [
      row(P1, T1, { '2026-09-30': { hours: '20', description: '' } }),
      row(P2, T2, { '2026-09-30': { hours: '4.25', description: '' } }),
    ];
    const result = validate(WEEK, rows);
    expect(result.ok).toBe(false);
    expect(result.dayErrors['2026-09-30']).toMatch(/24\.25/);

    rows[1].cells['2026-09-30'].hours = '4';
    expect(validate(WEEK, rows).ok).toBe(true);
  });

  it('refuses more than two decimals, over 24 in a cell, a non-Monday week and an unassigned project', () => {
    const one = (hours) => [row(P1, T1, { '2026-09-28': { hours, description: '' } })];
    expect(validate(WEEK, one('1.125')).ok).toBe(false);
    expect(validate(WEEK, one('24.5')).ok).toBe(false);
    expect(validate('2026-09-29', one('1')).errors).toContain('A week starts on a Monday.');
    expect(validate(WEEK, one('1'), { assignedProjectIds: [P2] }).ok).toBe(false);
    expect(validate(WEEK, one('1'), { assignedProjectIds: [P1] }).ok).toBe(true);
    expect(validate(WEEK, one('0')).errors).toContain('Enter hours on at least one day.');
  });

  it('drops hours of 0 from the body, then empty tasks and projects', () => {
    const rows = [
      row(P1, T1, {
        '2026-09-28': { hours: '0', description: 'x' },
        '2026-09-29': { hours: '2.5', description: '' },
      }),
      row(P2, T2, { '2026-09-28': { hours: '', description: '' } }),
    ];
    const body = toRequestBody(WEEK, rows);
    expect(body.projects).toHaveLength(1);
    expect(body.projects[0].tasks[0].days).toEqual([{ date: '2026-09-29', hours: 2.5, description: null }]);
  });

  it('keeps only the given projects for a resubmit', () => {
    const body = toRequestBody(WEEK, rowsFromResponse(response), { onlyProjects: [P2] });
    expect(body.projects.map((p) => p.project_id)).toEqual([P2]);
  });

  it('takes the Monday of a Sunday to be the previous Monday', () => {
    expect(mondayOf('2026-10-04')).toBe('2026-09-28');
    expect(mondayOf('2026-09-28')).toBe('2026-09-28');
    expect(mondayOf('2026-10-01')).toBe('2026-09-28');
    expect(isMonday('2026-09-28')).toBe(true);
    expect(isMonday('2026-10-04')).toBe(false);
    expect(weekDates(WEEK)).toEqual([
      '2026-09-28',
      '2026-09-29',
      '2026-09-30',
      '2026-10-01',
      '2026-10-02',
      '2026-10-03',
      '2026-10-04',
    ]);
  });
});
