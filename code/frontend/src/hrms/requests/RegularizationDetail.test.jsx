import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RegularizationDetail } from './RegularizationDetail.jsx';
import { requestService } from './requestService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./requestService.js', () => ({
  requestService: { regularization: vi.fn() },
}));
vi.mock('@shell/screens', () => ({ NotFound: () => <div>Page not found</div> }));

const T = 60000;
const item = {
  id: 'r1',
  employeeId: 'emp-asha',
  employeeName: 'Asha Rao',
  date: '2026-10-14',
  inAt: '2026-10-14T03:30:00Z',
  outAt: '2026-10-14T12:30:00Z',
  reason: 'Forgot to clock in',
  status: 'APPROVED',
  decidedAt: '2026-10-15T05:00:00Z',
  decidedBy: 'emp-vikram',
  decidedByName: 'Vikram Shah',
  decisionComment: 'ok',
};

const renderAt = () =>
  render(
    <MemoryRouter initialEntries={['/hrms/regularizations/r1']}>
      <Routes>
        <Route path="/hrms/regularizations/:id" element={<RegularizationDetail />} />
      </Routes>
    </MemoryRouter>
  );

describe('RegularizationDetail (D-79)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('names the employee and the decider, never their ids', async () => {
    requestService.regularization.mockResolvedValueOnce(item);
    renderAt();

    expect(await screen.findByText('Vikram Shah')).toBeDefined();
    expect(screen.getByText('Asha Rao')).toBeDefined();
    expect(screen.getByText('14 Oct 2026')).toBeDefined();
    expect(screen.queryByText('emp-vikram')).toBeNull();
    expect(screen.queryByText('emp-asha')).toBeNull();
  }, T);

  it('an undecided request shows a dash for the decider', async () => {
    requestService.regularization.mockResolvedValueOnce({
      ...item,
      status: 'PENDING',
      decidedAt: null,
      decidedBy: null,
      decidedByName: null,
      decisionComment: null,
    });
    renderAt();

    expect(await screen.findByText('Asha Rao')).toBeDefined();
    expect(screen.getAllByText('—').length).toBeGreaterThan(0);
  }, T);
});
