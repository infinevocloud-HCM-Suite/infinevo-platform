import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { HrmsDashboardPage } from './HrmsDashboardPage.jsx';
import { hrmsDashboardService } from './hrmsDashboardService.js';
import { employeeReply, managerReply } from './dashboardFixture.js';

vi.mock('./hrmsDashboardService.js', () => ({
  hrmsDashboardService: { summary: vi.fn() },
}));
vi.mock('@shell/screens', () => ({
  NotEntitled: () => <div>not entitled</div>,
}));

const WAIT = { timeout: 30000 };
const ALL = ['Today', 'Timesheets', 'My projects', 'My tasks', 'Projects I manage', 'Waiting for me', 'My team today'];

const renderPage = () =>
  render(
    <MemoryRouter>
      <HrmsDashboardPage />
    </MemoryRouter>,
  );

describe('HrmsDashboardPage (W-48.6 §7)', { timeout: 60000 }, () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('employee reply (team null): four cards, no Team section; as_of shown', async () => {
    hrmsDashboardService.summary.mockResolvedValue(employeeReply());
    renderPage();
    expect(await screen.findByText('As of 2026-10-04', {}, WAIT)).toBeDefined();
    for (const t of ALL.slice(0, 4)) expect(screen.getByText(t, { selector: '.ant-card-head-title' })).toBeDefined();
    for (const t of ALL.slice(4)) expect(screen.queryByText(t)).toBeNull();
    expect(screen.queryByText('Team')).toBeNull();
  });

  it('manager reply: all seven cards and the Team section', async () => {
    hrmsDashboardService.summary.mockResolvedValue(managerReply());
    renderPage();
    await screen.findByText('As of 2026-10-04', {}, WAIT);
    for (const t of ALL) expect(screen.getByText(t, { selector: '.ant-card-head-title' })).toBeDefined();
    expect(screen.getByText('Team', { selector: '.ant-divider-inner-text' })).toBeDefined();
  });

  it('a null me.today hides that card only', async () => {
    const reply = managerReply();
    reply.me.today = null;
    hrmsDashboardService.summary.mockResolvedValue(reply);
    renderPage();
    await screen.findByText('As of 2026-10-04', {}, WAIT);
    expect(screen.queryByText('Today', { selector: '.ant-card-head-title' })).toBeNull();
    for (const t of ALL.slice(1)) expect(screen.getByText(t, { selector: '.ant-card-head-title' })).toBeDefined();
  });

  it('a failed load shows Retry and no figures; Retry loads again', async () => {
    hrmsDashboardService.summary.mockRejectedValueOnce({
      status: 500,
      message: 'boom',
    });
    hrmsDashboardService.summary.mockResolvedValueOnce(employeeReply());
    renderPage();
    const retry = await screen.findByRole('button', { name: 'Retry' }, WAIT);
    expect(screen.getByText('boom')).toBeDefined();
    expect(screen.queryByText('My tasks')).toBeNull();
    fireEvent.click(retry);
    expect(await screen.findByText('As of 2026-10-04', {}, WAIT)).toBeDefined();
    await waitFor(() => expect(hrmsDashboardService.summary).toHaveBeenCalledTimes(2), WAIT);
  });

  it('a 403 shows NotEntitled', async () => {
    hrmsDashboardService.summary.mockRejectedValueOnce({
      status: 403,
      message: 'no',
      isForbidden: true,
    });
    renderPage();
    expect(await screen.findByText('not entitled', {}, WAIT)).toBeDefined();
  });
});
