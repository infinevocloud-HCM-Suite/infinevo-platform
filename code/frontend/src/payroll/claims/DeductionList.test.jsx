import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DeductionList } from './DeductionList.jsx';
import { deductionService } from './deductionService.js';
import { claimService } from './claimService.js';
import { useCan } from '@shell/screens';

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(),
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

vi.mock('./deductionService.js', () => ({
  deductionService: {
    enter: vi.fn(),
    list: vi.fn(),
    get: vi.fn(),
    reverse: vi.fn(),
    listOwn: vi.fn(),
  },
}));

vi.mock('./claimService.js', () => ({
  claimService: {
    searchEmployees: vi.fn(),
  },
}));

const ROW = {
  id: 'd-1',
  employee_id: 'emp-1',
  employee_name: 'Asha Rao',
  period: '2026-10',
  deduction_type: 'DAMAGE',
  amount: 500,
  reason: 'Broken screen',
  status: 'POSTED',
  posted_period: '2026-10',
};

describe('DeductionList (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimService.searchEmployees.mockResolvedValue([]);
    deductionService.list.mockResolvedValue({ content: [ROW], totalElements: 1 });
  });

  it('hides Enter deductions and Reverse without manage', async () => {
    useCan.mockReturnValue(false);
    render(<DeductionList />);

    expect(await screen.findByText('Asha Rao')).toBeDefined();
    expect(useCan).toHaveBeenCalledWith('payroll.employee_deduction.manage');
    expect(screen.queryByRole('button', { name: /enter deductions/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /^reverse$/i })).toBeNull();
  });

  it('shows both with manage; Reverse needs a reason and sends it', async () => {
    useCan.mockReturnValue(true);
    deductionService.reverse.mockResolvedValueOnce({ ...ROW, status: 'REVERSED', reversed_at: '2026-10-03T09:00:00Z' });
    render(<DeductionList />);

    expect(await screen.findByRole('button', { name: /enter deductions/i })).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: /^reverse$/i }));

    const dialog = await screen.findByRole('dialog');
    const ok = () => Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent === 'Reverse');
    expect(ok().disabled).toBe(true);

    const reason = screen.getByLabelText('Reason for reversal');
    fireEvent.change(reason, { target: { value: '   ' } });
    expect(ok().disabled).toBe(true);

    fireEvent.change(reason, { target: { value: 'Entered twice' } });
    expect(ok().disabled).toBe(false);
    fireEvent.click(ok());

    await waitFor(() => expect(deductionService.reverse).toHaveBeenCalledWith('d-1', 'Entered twice'));
    await waitFor(() => expect(screen.getByText('Reversed')).toBeDefined());
  });

  it('a 409 on reverse reloads that row', async () => {
    useCan.mockReturnValue(true);
    deductionService.reverse.mockRejectedValueOnce({ status: 409, code: 'CONFLICT', message: 'Already reversed' });
    deductionService.get.mockResolvedValueOnce({ ...ROW, status: 'REVERSED', reversed_at: '2026-10-02T09:00:00Z' });
    render(<DeductionList />);

    fireEvent.click(await screen.findByRole('button', { name: /^reverse$/i }));
    fireEvent.change(await screen.findByLabelText('Reason for reversal'), { target: { value: 'Duplicate' } });
    const dialog = screen.getByRole('dialog');
    fireEvent.click(Array.from(dialog.querySelectorAll('button')).find((b) => b.textContent === 'Reverse'));

    await waitFor(() => expect(deductionService.get).toHaveBeenCalledWith('d-1'));
    await waitFor(() => expect(screen.getByText('Reversed')).toBeDefined());
  });

  it('filters become query params', async () => {
    useCan.mockReturnValue(false);
    render(<DeductionList />);
    await screen.findByText('Asha Rao');

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Status filter' }));
    fireEvent.click(await screen.findByText('Posted', { selector: '.ant-select-item-option-content' }));
    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Type filter' }));
    fireEvent.click(await screen.findByText('Penalty', { selector: '.ant-select-item-option-content' }));

    await waitFor(() =>
      expect(deductionService.list).toHaveBeenLastCalledWith({
        employeeId: undefined,
        period: undefined,
        status: 'POSTED',
        deductionType: 'PENALTY',
        page: 0,
        size: 25,
      })
    );
  });

  it('a load error shows Retry and no rows', async () => {
    useCan.mockReturnValue(true);
    deductionService.list.mockRejectedValueOnce({ status: 500, message: 'Down' });
    render(<DeductionList />);

    expect(await screen.findByText('Down')).toBeDefined();
    expect(screen.queryByText('Asha Rao')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Asha Rao')).toBeDefined();
  });
});
