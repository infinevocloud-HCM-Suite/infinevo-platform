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
    myProfile: vi.fn(),
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
        tasks: [{ id: 'te-1', task_id: T1, days: [{ id: 'd1', date: WEEK, hours: 7.25, description: 'Build work' }] }],
      },
      {
        id: 'pe-2',
        project_id: P2,
        status: projectStatus[P2] || status,
        rejection_reason: null,
        tasks: [{ id: 'te-2', task_id: T2, days: [{ id: 'd2', date: WEEK, hours: 0.75, description: 'Test work' }] }],
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
    timesheetService.myProfile.mockResolvedValue({ id: 'emp-1', employeeNumber: 'P074' });
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
    const desc = screen.getByRole('textbox', { name: `Description Apollo Build ${WEEK}` });
    fireEvent.change(desc, { target: { value: 'Apollo Build work' } });
    await waitFor(() => expect(screen.getByTestId('week-total').textContent).toBe('7.50'));

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }));

    await waitFor(() =>
      expect(timesheetService.create).toHaveBeenCalledWith({
        week_start_date: WEEK,
        projects: [{ project_id: P1, tasks: [{ task_id: T1, days: [{ date: WEEK, hours: 7.5, description: 'Apollo Build work' }] }] }],
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
      projects: [{ project_id: P1, tasks: [{ task_id: T1, days: [{ date: WEEK, hours: 6, description: 'Build work' }] }] }],
    });
    expect(timesheetService.submit).not.toHaveBeenCalled();
  }, 60000);

  it('blocks saving if hours are entered without a description', async () => {
    timesheetService.week.mockResolvedValue(null);
    renderAt(WEEK);

    expect(await screen.findByTestId('week-status')).toHaveProperty('textContent', 'Not started');

    chooseOption('Project', 'Apollo');
    chooseOption('Task', 'Build');
    fireEvent.click(screen.getByRole('button', { name: /add row/i }));

    const cell = await screen.findByRole('spinbutton', { name: `Hours Apollo Build ${WEEK}` });
    fireEvent.change(cell, { target: { value: '4' } });

    fireEvent.click(screen.getByRole('button', { name: /save draft/i }));
    expect(await screen.findByTestId('timesheet-problems')).toBeDefined();
    expect(screen.getByText('Description is required when hours are entered.')).toBeDefined();
    expect(timesheetService.create).not.toHaveBeenCalled();
  }, 60000);

  it('a non-Monday date goes to its Monday', async () => {
    timesheetService.week.mockResolvedValue(null);
    renderAt('2026-10-04');

    await waitFor(() => expect(timesheetService.week).toHaveBeenCalledWith(WEEK));
    expect(timesheetService.week).not.toHaveBeenCalledWith('2026-10-04');
  }, 60000);

  it('filters task dropdown by logged-in employee assignee (D-136)', async () => {
    timesheetService.week.mockResolvedValue(null);
    timesheetService.myProfile.mockResolvedValue({ id: 'emp-1', employeeNumber: 'P074' });
    timesheetService.tasks.mockImplementation((id) => {
      if (id === P1) {
        return Promise.resolve([
          { id: 'task-mine-1', title: 'My Feature Task', assignee_employee_id: 'emp-1' },
          { id: 'task-other-1', title: 'Colleague Bugfix Task', assignee_employee_id: 'emp-2' },
          { id: 'task-unassigned', title: 'General Unassigned Task', assignee_employee_id: null },
        ]);
      }
      return Promise.resolve([]);
    });

    renderAt(WEEK);

    // Verify Employee ID is rendered in header
    expect(await screen.findByDisplayValue('P074')).toBeDefined();

    // Select project P1
    chooseOption('Project', 'Apollo');

    // Open task select
    const taskSelect = screen.getByRole('combobox', { name: 'Task' });
    fireEvent.mouseDown(taskSelect);

    // "My Feature Task" and "General Unassigned Task" should be visible
    const myOption = screen.getAllByTitle('My Feature Task').find((el) => el.classList.contains('ant-select-item-option'));
    expect(myOption).toBeDefined();

    const generalOption = screen.getAllByTitle('General Unassigned Task').find((el) => el.classList.contains('ant-select-item-option'));
    expect(generalOption).toBeDefined();

    // "Colleague Bugfix Task" assigned to emp-2 MUST NOT be present
    const colleagueOption = screen.queryByTitle('Colleague Bugfix Task');
    expect(colleagueOption).toBeNull();
  }, 60000);

  it('renders weekend headers and boxes with neutral grey styling, not red/pink (W-81.1)', async () => {
    const overlimitWeek = {
      ...week('DRAFT'),
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
              days: [{ id: 'd-sat1', date: '2026-10-03', hours: 15, description: 'Sat 1' }],
            },
            {
              id: 'te-2',
              task_id: T2,
              days: [{ id: 'd-sat2', date: '2026-10-03', hours: 10, description: 'Sat 2' }],
            },
          ],
        },
      ],
    };
    timesheetService.week.mockResolvedValue(overlimitWeek);
    renderAt(WEEK);

    // Wait for screen to finish loading
    expect(await screen.findByTestId('week-status')).toHaveProperty('textContent', 'Draft');

    // Week is 2026-09-28 (Monday) to 2026-10-04 (Sunday)
    // Saturday: 2026-10-03, Sunday: 2026-10-04
    // Ant Design scroll.x tables clone headers, so getAllByText returns all header instances
    const satHeaders = screen.getAllByText('Sat 03/10');
    expect(satHeaders.length).toBeGreaterThan(0);
    expect(satHeaders[0].style.color).not.toBe('rgb(207, 19, 34)');
    expect(satHeaders[0].style.fontWeight).toBe('600');

    const sunHeaders = screen.getAllByText('Sun 04/10');
    expect(sunHeaders.length).toBeGreaterThan(0);
    expect(sunHeaders[0].style.color).not.toBe('rgb(207, 19, 34)');
    expect(sunHeaders[0].style.fontWeight).toBe('600');

    // Monday header is not red
    const monHeaders = screen.getAllByText('Mon 28/09');
    expect(monHeaders.length).toBeGreaterThan(0);
    expect(monHeaders[0].style.color).toBe('');

    // Weekday box (Monday 2026-09-28)
    const monBoxes = await screen.findAllByTestId('day-box-2026-09-28');
    const monBox = monBoxes[0];

    // Weekend box (Saturday 2026-10-03 and Sunday 2026-10-04) shows no red/pink and differs from weekday
    const satBox = screen.getAllByTestId('day-box-2026-10-03')[0];
    expect(satBox.style.backgroundColor).not.toBe('rgb(255, 241, 240)');
    expect(satBox.style.borderColor).not.toBe('rgb(255, 163, 158)');

    const sunBox = screen.getAllByTestId('day-box-2026-10-04')[0];
    expect(sunBox.style.backgroundColor).not.toBe('rgb(255, 241, 240)');
    expect(sunBox.style.borderColor).not.toBe('rgb(255, 163, 158)');

    expect(satBox.style.backgroundColor).not.toBe(monBox.style.backgroundColor);
    expect(satBox.style.borderColor).not.toBe(monBox.style.borderColor);

    // Over-limit weekend total (15 + 10 = 25.00 hours > 24) still shows as danger
    const satTotal = screen.getByTestId('day-total-2026-10-03');
    expect(satTotal.textContent).toBe('25.00');
    expect(satTotal.className).toContain('ant-typography-danger');

    // Cancel button is present
    expect(screen.getByRole('button', { name: /cancel/i })).toBeDefined();
  }, 60000);
});

