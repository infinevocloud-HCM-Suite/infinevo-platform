import { describe, it, expect, vi } from 'vitest';
import { configure, render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { WeekView } from './WeekView.jsx';
import { reviewService } from './reviewService.js';

configure({ asyncUtilTimeout: 30000 });

vi.mock('./reviewService.js', () => ({
  reviewService: { get: vi.fn() },
}));

describe('WeekView (W-48.3 §7)', () => {
  it('renders full week timesheet with project rows, daily hours and descriptions', async () => {
    reviewService.get.mockResolvedValue({
      id: 'ts-1',
      employee_id: 'e-1',
      employee_name: 'Priya Sharma',
      week_start_date: '2026-10-12',
      week_end_date: '2026-10-18',
      status: 'SUBMITTED',
      projects: [
        {
          id: 'p-entry-1',
          project_id: 'p-1',
          project_name: 'Mobile App',
          status: 'SUBMITTED',
          tasks: [
            {
              id: 'te-1',
              task_id: 't-1',
              task_title: 'UI Design',
              days: [
                { date: '2026-10-12', hours: 8, description: 'Designed Figma components for onboarding' },
                { date: '2026-10-13', hours: 4, description: 'Design review with product team' },
              ],
            },
          ],
        },
      ],
    });

    render(
      <MemoryRouter initialEntries={['/hrms/timesheet-review/ts-1']}>
        <Routes>
          <Route path="/hrms/timesheet-review/:id" element={<WeekView />} />
        </Routes>
      </MemoryRouter>
    );

    expect(await screen.findByText('Mobile App')).toBeTruthy();
    expect(reviewService.get).toHaveBeenCalledWith('ts-1');
    expect(screen.getByText('Priya Sharma')).toBeTruthy();
    expect(screen.getByText('UI Design')).toBeTruthy();
    expect(screen.getAllByText('8.00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('4.00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('12.00').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Designed Figma components for onboarding').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Design review with product team').length).toBeGreaterThan(0);
    expect(screen.getByText('Work descriptions (2)')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Back to review' }).getAttribute('href')).toBe('/hrms/timesheet-review');
  }, 60000);

  it('renders error alert when loading timesheet fails', async () => {
    reviewService.get.mockRejectedValue(new Error('Network error'));
    render(
      <MemoryRouter initialEntries={['/hrms/timesheet-review/err-id']}>
        <Routes>
          <Route path="/hrms/timesheet-review/:id" element={<WeekView />} />
        </Routes>
      </MemoryRouter>
    );
    expect(await screen.findByText('Could not load the timesheet.')).toBeTruthy();
  }, 60000);
});
