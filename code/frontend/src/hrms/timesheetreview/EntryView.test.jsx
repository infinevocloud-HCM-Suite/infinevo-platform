import { describe, it, expect, vi } from 'vitest';
import { configure, render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { EntryView } from './EntryView.jsx';
import { reviewService } from './reviewService.js';

configure({ asyncUtilTimeout: 30000 });

vi.mock('./reviewService.js', () => ({
  reviewService: { entry: vi.fn() },
}));

describe('EntryView (W-48.3 §7)', () => {
  it('renders tasks by day, totals and the back link', async () => {
    reviewService.entry.mockResolvedValue({
      id: 'pe-1',
      employee_id: 'e-1',
      employee_name: 'Asha Rao',
      week_start_date: '2026-09-28',
      week_end_date: '2026-10-04',
      project_id: 'p-1',
      project_name: 'Apollo',
      status: 'SUBMITTED',
      tasks: [
        {
          id: 'te-1',
          task_id: 't-1',
          task_title: 'Build',
          days: [
            { date: '2026-09-28', hours: 7.25 },
            { date: '2026-09-29', hours: 0.75 },
          ],
        },
        { id: 'te-2', task_id: 't-2', days: [{ date: '2026-09-30', hours: 1 }] },
      ],
    });
    render(
      <MemoryRouter initialEntries={['/hrms/timesheet-review/entries/pe-1']}>
        <Routes>
          <Route path="/hrms/timesheet-review/entries/:entryId" element={<EntryView />} />
        </Routes>
      </MemoryRouter>
    );
    expect(await screen.findByText('Build')).toBeTruthy();
    expect(reviewService.entry).toHaveBeenCalledWith('pe-1');
    expect(screen.getByText('Apollo')).toBeTruthy();
    expect(screen.getByText('Asha Rao')).toBeTruthy();
    expect(screen.getByText('t-2')).toBeTruthy();
    expect(screen.getAllByText('7.25').length).toBeGreaterThan(0);
    expect(screen.getByText('8.00')).toBeTruthy();
    expect(screen.getByText('9.00')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Back to approvals' }).getAttribute('href')).toBe('/approvals');
  }, 60000);
});
