import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { act, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { DashboardPage, POLL_MS } from './DashboardPage.jsx';
import { dashboardService } from './dashboardService.js';
import { dashboardFixture } from './dashboardFixture.js';

vi.mock('./dashboardService.js', () => ({
  dashboardService: { summary: vi.fn(), setupChecklist: vi.fn() },
}));
vi.mock('@shell/screens', () => ({ useCan: () => false }));

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/payroll/dashboard']}>
      <Routes>
        <Route path="/payroll/dashboard" element={<DashboardPage />} />
        <Route path="/payroll/runs" element={<div>runs page</div>} />
      </Routes>
    </MemoryRouter>
  );

const computing = (done) =>
  dashboardFixture({
    current_run: {
      ...dashboardFixture().current_run,
      status: 'COMPUTING',
      progress_done: done,
      progress_total: 40,
    },
  });

describe('DashboardPage (W-47.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Only the interval and the clock are fake: Testing Library's waits still use real timeouts.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    vi.setSystemTime(new Date(2026, 9, 3, 12, 0, 0));
    dashboardService.summary.mockResolvedValue(dashboardFixture());
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders every widget from the W-37 response for the current FY', async () => {
    renderPage();
    expect(await screen.findByText('Current run: 2026-09')).toBeDefined();
    expect(dashboardService.summary).toHaveBeenCalledWith(2026);
    expect(screen.getByText('Active today')).toBeDefined();
    expect(screen.getAllByText('PAID runs, FY 2026-27')).toHaveLength(4);
    expect(screen.getByText('Paid (1 runs)')).toBeDefined();
    expect(screen.getByRole('table', { name: 'Recent runs' })).toBeDefined();
  });

  it('changing the year refetches for that year', async () => {
    renderPage();
    await screen.findByText('Current run: 2026-09');
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Financial year' }));
    fireEvent.click(await screen.findByText('2025-26', { selector: '.ant-select-item-option-content' }));
    await waitFor(() => expect(dashboardService.summary).toHaveBeenLastCalledWith(2025));
  });

  it('shows "No pay run yet" with a link to the runs when there is no run', async () => {
    dashboardService.summary.mockResolvedValue(
      dashboardFixture({
        current_run: null,
        recent_runs: [],
        months: [],
        employees: { active_today: 3, as_at_run: null },
      })
    );
    renderPage();
    expect(await screen.findByText('No pay run yet')).toBeDefined();
    expect(screen.queryByText('Paid (0 runs)')).toBeNull();
    fireEvent.click(screen.getByRole('link', { name: 'Go to pay runs' }));
    expect(screen.getByText('runs page')).toBeDefined();
  });

  it('a failed load shows Retry and no figures; Retry loads again', async () => {
    dashboardService.summary.mockRejectedValueOnce({ status: 500, message: 'Server down' });
    renderPage();
    expect(await screen.findByText('Could not load the payroll dashboard')).toBeDefined();
    expect(screen.getByText('Server down')).toBeDefined();
    expect(screen.queryByText('Active today')).toBeNull();
    expect(screen.queryByText(/\d,\d{3}\.\d{2}/)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Current run: 2026-09')).toBeDefined();
    expect(dashboardService.summary).toHaveBeenCalledTimes(2);
  });

  it('polls every 10 s while COMPUTING and stops once the run leaves it', async () => {
    dashboardService.summary
      .mockResolvedValueOnce(computing(10))
      .mockResolvedValueOnce(computing(30))
      .mockResolvedValue(dashboardFixture());
    renderPage();
    expect(await screen.findByText('10 of 40 employees computed')).toBeDefined();
    expect(dashboardService.summary).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(POLL_MS);
    });
    expect(await screen.findByText('30 of 40 employees computed')).toBeDefined();
    expect(dashboardService.summary).toHaveBeenCalledTimes(2);

    await act(async () => {
      vi.advanceTimersByTime(POLL_MS);
    });
    await waitFor(() => expect(screen.queryByRole('progressbar')).toBeNull());
    expect(dashboardService.summary).toHaveBeenCalledTimes(3);

    await act(async () => {
      vi.advanceTimersByTime(POLL_MS * 3);
    });
    expect(dashboardService.summary).toHaveBeenCalledTimes(3);
  });

  it('stops polling when the page unmounts', async () => {
    dashboardService.summary.mockResolvedValue(computing(10));
    const { unmount } = renderPage();
    await screen.findByText('10 of 40 employees computed');
    unmount();
    await act(async () => {
      vi.advanceTimersByTime(POLL_MS * 3);
    });
    expect(dashboardService.summary).toHaveBeenCalledTimes(1);
  });
});
