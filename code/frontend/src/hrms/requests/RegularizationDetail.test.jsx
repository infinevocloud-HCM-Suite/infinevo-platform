import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { RegularizationDetail } from './RegularizationDetail.jsx';
import { requestService } from './requestService.js';
import { approvalService } from '../../core/approvals/approvalService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./requestService.js', () => ({
  requestService: { regularization: vi.fn() },
}));

vi.mock('react-redux', () => ({
  useDispatch: () => vi.fn(),
  useSelector: vi.fn(),
}));

vi.mock('@shell/screens', () => ({
  NotFound: () => <div>Page not found</div>,
  useCan: () => true,
}));

vi.mock('../../core/approvals/approvalService.js', () => ({
  approvalService: {
    pending: vi.fn().mockResolvedValue({ content: [] }),
    decide: vi.fn().mockResolvedValue({}),
  },
}));

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

const renderAt = (state) =>
  render(
    <MemoryRouter initialEntries={[{ pathname: '/hrms/regularizations/r1', state }]}>
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

  it('renders inline Approve and Reject buttons when pending step exists and allows decision (D-115)', async () => {
    requestService.regularization.mockResolvedValueOnce({
      ...item,
      status: 'PENDING',
    });
    approvalService.pending.mockResolvedValueOnce({
      content: [
        {
          id: 'step-reg-1',
          itemId: 'r1',
          flowType: 'REGULARIZATION',
          itemRef: 'Regularization Asha Rao 2026-10-14',
          decision: null,
          stepIndex: 0,
        },
      ],
    });

    renderAt();

    await waitFor(() => expect(screen.getByRole('button', { name: /approve/i })).toBeTruthy());
    expect(screen.getByRole('button', { name: /reject/i })).toBeTruthy();

    // Click Approve
    fireEvent.click(screen.getByRole('button', { name: /approve/i }));
    expect(await screen.findByText('Approve Request')).toBeTruthy();

    // Confirm decision
    fireEvent.click(document.getElementById('btn-confirm-decide'));
    await waitFor(() => {
      expect(approvalService.decide).toHaveBeenCalledWith('step-reg-1', {
        decision: 'APPROVED',
        comment: '',
      });
    });
  }, T);
});

