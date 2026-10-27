import { describe, it, expect, vi } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { EntryView } from './EntryView.jsx';
import { reviewService } from './reviewService.js';
import { approvalService } from '../../core/approvals/approvalService.js';

configure({ asyncUtilTimeout: 30000 });

vi.mock('./reviewService.js', () => ({
  reviewService: { entry: vi.fn() },
}));

vi.mock('react-redux', () => ({
  useDispatch: () => vi.fn(),
  useSelector: vi.fn(),
}));

vi.mock('@shell/screens', () => ({
  useCan: () => true,
}));

vi.mock('../../core/approvals/approvalService.js', () => ({
  approvalService: {
    pending: vi.fn().mockResolvedValue({ content: [] }),
    decide: vi.fn().mockResolvedValue({}),
  },
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

  it('renders employee daily descriptions in cells and work descriptions section', async () => {
    reviewService.entry.mockResolvedValue({
      id: 'pe-2',
      employee_id: 'e-2',
      employee_name: 'Vikram Mehta',
      week_start_date: '2026-10-05',
      week_end_date: '2026-10-11',
      project_id: 'p-1',
      project_name: 'Apollo',
      status: 'SUBMITTED',
      tasks: [
        {
          id: 'te-1',
          task_id: 't-1',
          task_title: 'Backend API',
          days: [
            { date: '2026-10-05', hours: 8, description: 'Implemented JWT token refresh logic' },
            { date: '2026-10-06', hours: 7.5, description: 'Added integration test coverage' },
          ],
        },
      ],
    });
    render(
      <MemoryRouter initialEntries={['/hrms/timesheet-review/entries/pe-2']}>
        <Routes>
          <Route path="/hrms/timesheet-review/entries/:entryId" element={<EntryView />} />
        </Routes>
      </MemoryRouter>
    );
    expect(await screen.findByText('Backend API')).toBeTruthy();
    expect(screen.getAllByText('Implemented JWT token refresh logic').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Added integration test coverage').length).toBeGreaterThan(0);
    expect(screen.getByText('Work descriptions (2)')).toBeTruthy();
  }, 60000);

  it('renders inline Approve and Reject buttons when pending step exists and allows decision (D-113)', async () => {
    reviewService.entry.mockResolvedValue({
      id: 'pe-3',
      employee_id: 'e-3',
      employee_name: 'David Miller',
      week_start_date: '2026-10-12',
      week_end_date: '2026-10-18',
      project_id: 'p-1',
      project_name: 'Apollo',
      status: 'SUBMITTED',
      tasks: [],
    });
    approvalService.pending.mockResolvedValue({
      content: [
        {
          id: 'step-ts-1',
          itemId: 'pe-3',
          flowType: 'TIMESHEET',
          itemRef: 'Apollo Week 2026-10-12',
          decision: null,
          stepIndex: 0,
        },
      ],
    });

    render(
      <MemoryRouter initialEntries={['/hrms/timesheet-review/entries/pe-3']}>
        <Routes>
          <Route path="/hrms/timesheet-review/entries/:entryId" element={<EntryView />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => expect(screen.getByRole('button', { name: /approve/i })).toBeTruthy());
    expect(screen.getByRole('button', { name: /reject/i })).toBeTruthy();

    // Click Approve
    fireEvent.click(screen.getByRole('button', { name: /approve/i }));
    expect(await screen.findByText('Approve Request')).toBeTruthy();

    // Confirm decision
    fireEvent.click(document.getElementById('btn-confirm-decide'));
    await waitFor(() => {
      expect(approvalService.decide).toHaveBeenCalledWith('step-ts-1', {
        decision: 'APPROVED',
        comment: '',
      });
    });
  }, 60000);
});


