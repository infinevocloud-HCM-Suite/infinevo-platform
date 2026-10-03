import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyClaimsPanel } from './MyClaimsPanel.jsx';
import { claimService } from './claimService.js';
import { deductionService } from './deductionService.js';

vi.mock('./claimService.js', () => ({
  claimService: {
    components: vi.fn(),
    submit: vi.fn(),
    listOwn: vi.fn(),
    getOwn: vi.fn(),
  },
}));

vi.mock('./deductionService.js', () => ({
  deductionService: {
    listOwn: vi.fn(),
  },
}));

const CLAIM = {
  id: 'claim-1',
  employee_id: 'emp-1',
  component_name: 'Fuel',
  component_code: 'FUEL',
  requested_amount: 2000,
  approved_amount: 1500,
  bill_date: '2026-09-20',
  status: 'APPROVED',
  posted_period: '2026-10',
  remarks: 'Part approved',
  approval_instance_id: 'appr-1',
};

const DEDUCTIONS = [
  {
    id: 'd-1',
    period: '2026-10',
    deduction_type: 'DAMAGE',
    amount: 500,
    reason: 'Broken laptop screen',
    status: 'POSTED',
  },
  {
    id: 'd-2',
    period: '2026-09',
    deduction_type: 'PENALTY',
    amount: 250.5,
    reason: 'Late return of equipment',
    status: 'REVERSED',
    reversed_at: '2026-09-28T10:00:00Z',
  },
];

const renderPanel = () =>
  render(
    <MemoryRouter>
      <MyClaimsPanel />
    </MemoryRouter>
  );

describe('MyClaimsPanel (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimService.listOwn.mockResolvedValue([CLAIM]);
    claimService.components.mockResolvedValue([]);
    deductionService.listOwn.mockResolvedValue(DEDUCTIONS);
  });

  it('renders the claims tab, then the deductions tab, with a reversed deduction struck through', async () => {
    renderPanel();

    expect(await screen.findByText('Fuel')).toBeDefined();
    expect(screen.getByText('2,000.00')).toBeDefined();
    expect(screen.getByText('1,500.00')).toBeDefined();
    expect(screen.getByText('Approved', { selector: '.ant-tag' })).toBeDefined();
    expect(screen.getByText('Part approved')).toBeDefined();
    expect(screen.getByRole('button', { name: /new claim/i })).toBeDefined();

    fireEvent.click(screen.getByRole('tab', { name: 'Deductions' }));

    expect(await screen.findByText('Broken laptop screen')).toBeDefined();
    const reversedReason = screen.getByText('Late return of equipment');
    expect(reversedReason.closest('del')).not.toBeNull();
    expect(screen.getByText('Broken laptop screen').closest('del')).toBeNull();
    expect(screen.getByText('250.50').closest('del')).not.toBeNull();
    expect(screen.getByTestId('reversed-on').textContent).toContain('2026-09-28');
  });

  it('opens a claim from its row with getOwn', async () => {
    claimService.getOwn.mockResolvedValueOnce(CLAIM);
    renderPanel();

    fireEvent.click(await screen.findByText('Fuel'));
    await waitFor(() => expect(claimService.getOwn).toHaveBeenCalledWith('claim-1'));
    expect(await screen.findByText('Claim id')).toBeDefined();
    // The employee does not get the approval link.
    expect(screen.queryByText('Open the approval')).toBeNull();
  });

  it('a load error shows Retry and no rows, and Retry loads again', async () => {
    claimService.listOwn.mockRejectedValueOnce({ status: 500, message: 'Payroll is down' });
    renderPanel();

    expect(await screen.findByText('Payroll is down')).toBeDefined();
    expect(screen.queryByText('Fuel')).toBeNull();
    expect(document.querySelectorAll('.ant-table-row')).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Fuel')).toBeDefined();
    expect(claimService.listOwn).toHaveBeenCalledTimes(2);
  });

  it('a deductions load error shows Retry on that tab and no rows', async () => {
    deductionService.listOwn.mockRejectedValueOnce({ status: 503, message: 'Deductions unavailable' });
    renderPanel();
    await screen.findByText('Fuel');

    fireEvent.click(screen.getByRole('tab', { name: 'Deductions' }));
    expect(await screen.findByText('Deductions unavailable')).toBeDefined();
    expect(screen.getByRole('button', { name: /retry/i })).toBeDefined();
    expect(screen.queryByText('Broken laptop screen')).toBeNull();
  });
});
