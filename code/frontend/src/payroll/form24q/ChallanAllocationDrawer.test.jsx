import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { ChallanAllocationDrawer, changesByRun } from './ChallanAllocationDrawer';
import { form24QService } from './form24QService';

vi.mock('./form24QService', () => ({
  form24QService: { candidates: vi.fn(), allocate: vi.fn() },
}));

const challan = { id: 'c1', bsrCode: '0510304', challanSerialNo: '12', allocatableAmount: '1500.00', filed: false };
const candidates = [
  { payRunId: 'r1', period: '2026-10', employeeId: 'e1', employeeNumber: 'E-1', employeeName: 'Asha', tdsDeducted: '1000.00', allocatedElsewhere: '0', allocatedHere: '0' },
  { payRunId: 'r2', period: '2026-11', employeeId: 'e1', employeeNumber: 'E-1', employeeName: 'Asha', tdsDeducted: '1000.00', allocatedElsewhere: '400.00', allocatedHere: '0' },
];

describe('ChallanAllocationDrawer (W-89)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    form24QService.candidates.mockResolvedValue(candidates);
    form24QService.allocate.mockResolvedValue({});
  });

  it('sends only the changed amounts, lowered ones before raised ones', () => {
    expect(changesByRun(candidates, { 'r1:e1': 1000, 'r2:e1': 0 })).toEqual([['r1', [{ employeeId: 'e1', amount: 1000 }]]]);
    const moved = [
      { ...candidates[0], allocatedHere: '0' },
      { ...candidates[1], allocatedHere: '600.00' },
    ];
    expect(changesByRun(moved, { 'r1:e1': 600, 'r2:e1': 0 })).toEqual([
      ['r2', [{ employeeId: 'e1', amount: 0 }]],
      ['r1', [{ employeeId: 'e1', amount: 600 }]],
    ]);
  });

  it('"Fill remaining" covers open TDS in order up to the challan\'s tax, then saves one call per run', async () => {
    const onSaved = vi.fn();
    const onClose = vi.fn();
    render(<ChallanAllocationDrawer open challan={challan} onClose={onClose} onSaved={onSaved} />);
    await waitFor(() => expect(screen.getByText('2026-11')).toBeDefined());

    fireEvent.click(screen.getByTestId('btn-fill-remaining'));
    // 1000 for October, then 500 of November's 600 open: the challan's 1500 is used up.
    await waitFor(() => expect(screen.getByTestId('allocation-left').textContent).toMatch(/0\.00/));

    fireEvent.click(screen.getByTestId('btn-save-allocation'));
    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(form24QService.allocate).toHaveBeenCalledWith('c1', 'r1', [{ employeeId: 'e1', amount: 1000 }]);
    expect(form24QService.allocate).toHaveBeenCalledWith('c1', 'r2', [{ employeeId: 'e1', amount: 500 }]);
    expect(onClose).toHaveBeenCalled();
  });

  it('shows the server\'s refusal and stays open', async () => {
    form24QService.allocate.mockRejectedValue({ message: 'more than the challan' });
    const onClose = vi.fn();
    render(<ChallanAllocationDrawer open challan={challan} onClose={onClose} />);
    await waitFor(() => expect(screen.getByText('2026-10')).toBeDefined());
    fireEvent.click(screen.getByTestId('btn-fill-remaining'));
    fireEvent.click(screen.getByTestId('btn-save-allocation'));
    await waitFor(() => expect(screen.getByText('more than the challan')).toBeDefined());
    expect(onClose).not.toHaveBeenCalled();
  });
});
