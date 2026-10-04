import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import dayjs from 'dayjs';
import { MyTimesheetPanel } from './MyTimesheetPanel.jsx';
import { mondayOf } from './weekGrid.js';
import { apiClient } from '@shared/api/client.js';

// Ant Design screens render slowly under a loaded run; give async queries room.
configure({ asyncUtilTimeout: 10000 });

vi.mock('@shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

const envelope = (data) => ({ data: { status: 'success', message: 'ok', data } });

function mockApi(week) {
  apiClient.get.mockImplementation((url) => {
    if (url === '/v1/me/timesheet') return Promise.resolve(envelope(week));
    if (url === '/v1/hrms/projects/mine') return Promise.resolve(envelope([{ id: 'p-1', name: 'Apollo' }]));
    return Promise.reject(new Error(`unexpected ${url}`));
  });
}

function renderPanel() {
  return render(
    <MemoryRouter>
      <MyTimesheetPanel />
    </MemoryRouter>
  );
}

describe('MyTimesheetPanel (W-48.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('data: null shows "Not started" and the link to this week', async () => {
    mockApi(null);
    renderPanel();

    expect(await screen.findByText('Not started')).toBeDefined();
    const monday = mondayOf(dayjs());
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me/timesheet', { params: { weekStart: monday } });
    const link = screen.getByRole('link', { name: /open week/i });
    expect(link.getAttribute('href')).toBe(`/hrms/timesheets/week/${monday}`);
    expect(screen.getByText('0.00')).toBeDefined();
  }, 60000);

  it('shows the week status, total hours and each project status', async () => {
    mockApi({
      id: 'ts-1',
      status: 'SUBMITTED',
      projects: [
        {
          project_id: 'p-1',
          status: 'SUBMITTED',
          tasks: [
            {
              task_id: 't-1',
              days: [
                { date: '2026-09-28', hours: 7.25 },
                { date: '2026-09-29', hours: 0.75 },
              ],
            },
          ],
        },
      ],
    });
    renderPanel();

    await waitFor(() => expect(screen.getByText('8.00')).toBeDefined());
    expect(screen.getByText('Apollo: Submitted')).toBeDefined();
  }, 60000);
});
