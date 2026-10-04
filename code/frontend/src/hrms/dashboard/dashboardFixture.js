/** A manager's W-44 reply (HrmsDashboardResponse.java), for the W-48.6 tests. */
export const managerReply = () => ({
  as_of: '2026-10-04',
  me: {
    today: {
      clocked_in: true,
      clocked_in_at: '2026-10-04T03:30:00Z',
      worked_minutes: 125,
    },
    timesheets: {
      this_week: {
        week_start: '2026-09-28',
        timesheet_id: null,
        status: null,
        hours: '0.00',
      },
      last_week: {
        week_start: '2026-09-21',
        timesheet_id: 't-1',
        status: 'SUBMITTED',
        hours: '40.00',
      },
    },
    projects: {
      active: 7,
      items: [
        {
          project_id: 'p-1',
          name: 'Apollo',
          status: 'STARTED',
          progress: 40,
          end_date: '2026-12-31',
        },
      ],
    },
    tasks: {
      open: 9,
      by_status: { TODO: 4, IN_PROGRESS: 3, IN_REVIEW: 2 },
      overdue: 3,
      due_this_week: 2,
      next: [
        {
          task_id: 'k-1',
          project_id: 'p-1',
          project_name: 'Apollo',
          title: 'Late task',
          status: 'TODO',
          priority: 'HIGH',
          due_date: '2026-10-03',
        },
        {
          task_id: 'k-2',
          project_id: 'p-1',
          project_name: 'Apollo',
          title: 'Future task',
          status: 'TODO',
          priority: 'LOW',
          due_date: '2026-10-05',
        },
      ],
    },
  },
  team: {
    projects: {
      managed: 4,
      by_status: { STARTED: 3, ON_HOLD: 1 },
      items: [
        {
          project_id: 'p-2',
          name: 'Zeus',
          progress: 10,
          end_date: '2027-01-31',
          team_size: 5,
          open_tasks: 12,
          overdue_tasks: 2,
        },
      ],
    },
    approvals: {
      waiting: 11,
      oldest: [
        {
          timesheet_id: 't-9',
          project_entry_id: 'e-1',
          employee_id: 'u-1',
          employee_name: 'Asha Rao',
          project_name: 'Zeus',
          week_start: '2026-09-21',
          submitted_at: '2026-09-27T10:00:00Z',
        },
        {
          timesheet_id: 't-8',
          project_entry_id: 'e-2',
          employee_id: 'u-2',
          employee_name: 'Ravi Sen',
          project_name: 'Zeus',
          week_start: '2026-09-28',
          submitted_at: '2026-10-03T10:00:00Z',
        },
      ],
    },
    reports: {
      reports: 6,
      clocked_in_today: 4,
      late_last_week: 1,
      late: [{ employee_id: 'u-3', name: 'Mina Das' }],
    },
  },
});

export const employeeReply = () => ({ ...managerReply(), team: null });
