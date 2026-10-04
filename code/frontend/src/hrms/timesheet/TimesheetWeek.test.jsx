import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { TimesheetWeek } from './TimesheetWeek.jsx';
import { timesheetService } from './timesheetService.js';

// Ant Design screens render slowly under a loaded run; give async queries room.
configure({ asyncUtilTimeout: 10000 });

vi.mock('./timesheetService.js', () => ({
  timesheetService: {
    mine: vi.fn(),
    week: vi.fn(),
    create: vi.fn(),
    replace: vi.fn(),
    submit: vi.fn(),
    remove: vi.fn(),
    myProjects: vi.fn(),
    tasks: vi.fn(),
  },
}));

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
  NotEntitled: () => <div>Not entitled</div>,
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

const WEEK = '2026-09-28';
const P1 = 'p-1';
const P2 = 'p-2';
const T1 = 't-1';
const T2 = 't-2';

const projects = [
  { id: P1, name: 'Apollo' },
  { id: P2, name: 'Zephyr' },
];
const tasks = {
  [P1]: [{ id: T1, project_id: P1, title: 'Build' }],
  [P2]: [{ id: T2, project_id: P2, title: 'Test' }],
};

function week(status, projectStatus = {}) {
  return {
    id: 'ts-1',
    week_start_date: WEEK,
    week_end_date: '2026-10-04',
    status,
    projects: [
      {
        id: 'pe-1',
        project_id: P1,
        status: projectStatus[P1] || status,
        rejection_reason: projectStatus[P1] === 'REJECTED' ? 'Wrong task on Monday' : null,
        tasks: [{ id: 'te-1', task_id: T1, days: [{ id: 'd1', date: WEEK, hours: 7.25, description: null }] }],
      },
      {
        id: 'pe-2',
        project_id: P2,
        status: projectStatus[P2] || status,
        rejection_reason: null,
        tasks: [{ id: 'te-2', task_id: T2, days: [{ id: 'd2', date: WEEK, hours: 0.75, description: null }] }],
      },
    ],
  };
}

function renderAt(weekStart) {
  return render(
    <MemoryRouter initialEntries={[`/hrms/timesheets/week/${weekStart}`]}>
      <Routes>
        <Route path="/hrms/timesheets/week/:weekStart" element={<TimesheetWeek />} />
      </Routes>
    </MemoryRouter>
  );
}

async function confirm() {
  const ok = await screen.findByRole('button', { name: /^yes$/i });
  fireEvent.click(ok);
}

function chooseOption(label, optionText) {
  const select = screen.getByRole('combobox', { name: label });
  fireEvent.mouseDown(select);
  const option = screen
    .getAllByTitle(optionText)
    .find((el) => el.classList.contains('ant-select-item-option'));
  fireEvent.click(option);
}

describe('TimesheetWeek (W-48.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    timesheetService.myProjects.mockResolvedValue(projects);
    timesheetService.tasks.mockImplementation((id) => Promise.resolve(tasks[id] || []));
  });

  it('with no timesheet, Save sends POST with the rows entered', async () => {
    timesheetService.week.mockResolvedValue(null);
    timesheetService.create.mockImplementation(() =>
      Promise.resolve({ ...week('DRAFT'), projects: [] })
    );
    renderAt(WEEK);

    expect(await screen.findByTestId('week-status')).toHaveProperty('textContent', 'Not started');
    expect(screen.queryByRole('button', { name: /delete draft/i })).toBeNull();

    chooseOption('Project', 'Apollo');
    chooseOption('Task', 'Build');
    fireEvent.click(screen.getByRole('button', { name: /add row/i }));

    const cell = await screen.findByRole('spinbutton', { name: `Hours Apollo Build ${WEEK}` });
    fireEvent.change(cell, { target: { value: '7.5' } });
    await waitFor(() => expect(screen.getByTestId('week-total').textContent).toBe('7.50'));

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }));

    await waitFor(() =>
      expect(timesheetService.create).toHaveBeenCalledWith({
        week_start_date: WEEK,
        projects: [{ project_id: P1, tasks: [{ task_id: T1, days: [{ date: WEEK, hours: 7.5, description: null }] }] }],
      })
    );
    expect(timesheetService.replace).not.toHaveBeenCalled();
  }, 60000);

  it('on a draft, Save sends PUT to the timesheet', async () => {
    timesheetService.week.mockResolvedValue(week('DRAFT'));
    timesheetService.replace.mockResolvedValue(week('DRAFT'));
    renderAt(WEEK);

    await waitFor(() => expect(screen.getByTestId('day-total-2026-09-28').textContent).toBe('8.00'));
    fireEvent.click(screen.getByRole('button', { name: /save draft/i }));

    await waitFor(() => expect(timesheetService.replace).toHaveBeenCalledTimes(1));
    const [id, body] = timesheetService.replace.mock.calls[0];
    expect(id).toBe('ts-1');
    expect(body.projects.map((p) => p.project_id)).toEqual([P1, P2]);
    expect(timesheetService.create).not.toHaveBeenCalled();
  }, 60000);

  it('Submit saves first, then submits', async () => {
    timesheetService.week.mockResolvedValue(week('DRAFT'));
    timesheetService.replace.mockResolvedValue(week('DRAFT'));
    timesheetService.submit.mockResolvedValue(week('SUBMITTED'));
    renderAt(WEEK);

    fireEvent.click(await screen.findByRole('button', { name: /^submit$/i }));
    await confirm();

    await waitFor(() => expect(timesheetService.submit).toHaveBeenCalledWith('ts-1'));
    expect(timesheetService.replace).toHaveBeenCalledTimes(1);
    expect(timesheetService.replace.mock.invocationCallOrder[0]).toBeLessThan(
      timesheetService.submit.mock.invocationCallOrder[0]
    );
    await waitFor(() => expect(screen.getByTestId('week-status').textContent).toBe('Submitted'));
    expect(screen.queryAllByRole('spinbutton')).toHaveLength(0);
  }, 60000);

  it('blocks a day over 24 before sending', async () => {
    timesheetService.week.mockResolvedValue(week('DRAFT'));
    renderAt(WEEK);

    const cell = await screen.findByRole('spinbutton', { name: `Hours Apollo Build ${WEEK}` });
    fireEvent.change(cell, { target: { value: '23.5' } });
    await waitFor(() => expect(screen.getByTestId(`day-total-${WEEK}`).textContent).toBe('24.25'));

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }));
    expect(await screen.findByTestId('timesheet-problems')).toBeDefined();
    expect(timesheetService.replace).not.toHaveBeenCalled();
  }, 60000);

  it('a submitted week has no inputs and no actions', async () => {
    timesheetService.week.mockResolvedValue(week('SUBMITTED'));
    renderAt(WEEK);

    await waitFor(() => expect(screen.getByTestId('week-status').textContent).toBe('Submitted'));
    expect(screen.queryAllByRole('spinbutton')).toHaveLength(0);
    expect(screen.queryByRole('button', { name: /save draft/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /^submit$/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /resubmit/i })).toBeNull();
  }, 60000);

  it('on a rejected week only rejected projects are editable and Resubmit sends only them', async () => {
    timesheetService.week.mockResolvedValue(week('REJECTED', { [P1]: 'REJECTED', [P2]: 'APPROVED' }));
    timesheetService.replace.mockResolvedValue(week('SUBMITTED', { [P1]: 'SUBMITTED', [P2]: 'APPROVED' }));
    renderAt(WEEK);

    const alert = await screen.findByTestId(`rejected-${P1}`);
    expect(within(alert).getByText('Wrong task on Monday')).toBeDefined();

    const inputs = screen.getAllByRole('spinbutton');
    expect(inputs).toHaveLength(7);
    inputs.forEach((input) => expect(input.getAttribute('aria-label')).toMatch(/^Hours Apollo Build /));
    expect(screen.queryByRole('spinbutton', { name: /Zephyr/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /save draft/i })).toBeNull();

    fireEvent.change(screen.getByRole('spinbutton', { name: `Hours Apollo Build ${WEEK}` }), {
      target: { value: '6' },
    });
    fireEvent.click(screen.getByRole('button', { name: /resubmit/i }));
    await confirm();

    await waitFor(() => expect(timesheetService.replace).toHaveBeenCalledTimes(1));
    const [id, body] = timesheetService.replace.mock.calls[0];
    expect(id).toBe('ts-1');
    expect(body).toEqual({
      week_start_date: WEEK,
      projects: [{ project_id: P1, tasks: [{ task_id: T1, days: [{ date: WEEK, hours: 6, description: null }] }] }],
    });
    expect(timesheetService.submit).not.toHaveBeenCalled();
  }, 60000);

  it('a non-Monday date goes to its Monday', async () => {
    timesheetService.week.mockResolvedValue(null);
    renderAt('2026-10-04');

    await waitFor(() => expect(timesheetService.week).toHaveBeenCalledWith(WEEK));
    expect(timesheetService.week).not.toHaveBeenCalledWith('2026-10-04');
  }, 60000);
});
