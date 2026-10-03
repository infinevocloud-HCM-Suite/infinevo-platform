import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ClaimDetail } from './ClaimDetail.jsx';
import { claimService } from './claimService.js';

vi.mock('@shell/screens', () => ({
  NotFound: () => <div data-testid="not-found">not found</div>,
}));

vi.mock('./claimService.js', () => ({
  claimService: {
    get: vi.fn(),
  },
}));

const CLAIM = {
  id: 'claim-1',
  employee_id: 'emp-1',
  employee_name: 'Asha Rao',
  reimbursement_id: 'c-fuel',
  component_code: 'FUEL',
  component_name: 'Fuel',
  max_limit: 2000,
  requested_amount: 2000,
  approved_amount: null,
  bill_date: '2026-09-20',
  description: 'Client visit',
  document_id: null,
  status: 'SUBMITTED',
  remarks: null,
  approval_instance_id: 'appr-7',
  pay_input_id: null,
  posted_period: null,
  approved_by: null,
  approved_at: null,
  created_at: '2026-09-21T10:00:00Z',
  updated_at: '2026-09-21T10:00:00Z',
};

const renderDetail = () =>
  render(
    <MemoryRouter initialEntries={['/payroll/claims/claim-1']}>
      <Routes>
        <Route path="/payroll/claims/:id" element={<ClaimDetail />} />
      </Routes>
    </MemoryRouter>
  );

describe('ClaimDetail (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('describes the claim and links a SUBMITTED claim to its approval', async () => {
    claimService.get.mockResolvedValueOnce(CLAIM);
    renderDetail();

    expect(await screen.findByText('Asha Rao')).toBeDefined();
    expect(claimService.get).toHaveBeenCalledWith('claim-1');
    expect(screen.getByText('Client visit')).toBeDefined();
    expect(screen.getAllByText('2,000.00')).toHaveLength(2);
    const link = screen.getByRole('link', { name: 'Open the approval' });
    expect(link.getAttribute('href')).toBe('/approvals/appr-7');
  });

  it('no approval link once the claim is decided', async () => {
    claimService.get.mockResolvedValueOnce({
      ...CLAIM,
      status: 'APPROVED',
      approved_amount: 1500,
      posted_period: '2026-10',
    });
    renderDetail();

    expect(await screen.findByText('1,500.00')).toBeDefined();
    expect(screen.getByText('2026-10')).toBeDefined();
    expect(screen.queryByRole('link', { name: 'Open the approval' })).toBeNull();
  });

  it('a 404 shows the shell not-found screen', async () => {
    claimService.get.mockRejectedValueOnce({ status: 404, code: 'NOT_FOUND', message: 'No such claim' });
    renderDetail();
    expect(await screen.findByTestId('not-found')).toBeDefined();
  });

  it('another failure shows Retry', async () => {
    claimService.get.mockRejectedValueOnce({ status: 500, message: 'Boom' });
    renderDetail();

    expect(await screen.findByText('Boom')).toBeDefined();
    claimService.get.mockResolvedValueOnce(CLAIM);
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Asha Rao')).toBeDefined();
  });
});
