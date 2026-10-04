import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ReviewPage, REVIEW_STATUSES } from './ReviewPage.jsx';
import { reviewService } from './reviewService.js';
import { useCan } from '@shell/screens';

// Ant Design screens render slowly under a loaded run; give async queries room.
configure({ asyncUtilTimeout: 30000 });

vi.mock('./reviewService.js', () => ({
  reviewService: { managed: vi.fn(), team: vi.fn(), all: vi.fn(), get: vi.fn(), entry: vi.fn() },
}));

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => false),
  NotEntitled: () => <div>Not entitled</div>,
}));

const rows = Array.from({ length: 20 }, (_, i) => ({
  id: `ts-${i}`,
  employee_id: `e-${i}`,
  employee_name: `Person ${i}`,
  week_start_date: '2026-09-28',
  status: 'SUBMITTED',
  projects: [{ id: `pe-${i}`, project_id: 'p-1', project_name: 'Apollo', status: 'SUBMITTED', tasks: [] }],
}));
const pageOf = { content: rows, page: 0, size: 20, total_elements: 45, total_pages: 3 };

function grant(...actions) {
  useCan.mockImplementation((a) => actions.includes(a));
}

function renderPage() {
  return render(
    <MemoryRouter>
      <ReviewPage />
    </MemoryRouter>
  );
}

describe('ReviewPage (W-48.3 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    for (const fn of ['managed', 'team', 'all']) reviewService[fn].mockResolvedValue(pageOf);
  });

  it('a manager sees My projects and My team, defaulting to SUBMITTED', async () => {
    grant('hrms.timesheet.approve', 'hrms.timesheet.read_team');
    renderPage();
    expect(await screen.findByRole('tab', { name: 'My projects' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'My team' })).toBeTruthy();
    expect(screen.queryByRole('tab', { name: 'All' })).toBeNull();
    await waitFor(() => expect(reviewService.managed).toHaveBeenCalled());
    const f = reviewService.managed.mock.calls[0][0];
    expect(f.status).toBe('SUBMITTED');
    expect(f.page).toBe(0);
    expect(f.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  }, 60000);

  it('HR sees My projects and All', async () => {
    grant('hrms.timesheet.approve', 'hrms.timesheet.read');
    renderPage();
    expect(await screen.findByRole('tab', { name: 'All' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'My projects' })).toBeTruthy();
    expect(screen.queryByRole('tab', { name: 'My team' })).toBeNull();
  }, 60000);

  it('no action gives NotEntitled', () => {
    grant();
    renderPage();
    expect(screen.getByText('Not entitled')).toBeTruthy();
  }, 60000);

  it('status never offers DRAFT', async () => {
    expect(REVIEW_STATUSES).toEqual(['SUBMITTED', 'APPROVED', 'REJECTED']);
    grant('hrms.timesheet.read');
    renderPage();
    const select = await screen.findByRole('combobox', { name: 'Status' });
    fireEvent.mouseDown(select);
    expect(await screen.findByRole('option', { name: 'Approved' })).toBeTruthy();
    expect(screen.queryByRole('option', { name: 'Draft' })).toBeNull();
  }, 60000);

  it('paging sends page', async () => {
    grant('hrms.timesheet.read');
    renderPage();
    expect(await screen.findByText('Person 0')).toBeTruthy();
    fireEvent.click(screen.getByTitle('2'));
    await waitFor(() =>
      expect(reviewService.all.mock.calls.some(([f]) => f.page === 1 && f.size === 20)).toBe(true)
    );
  }, 60000);
});
