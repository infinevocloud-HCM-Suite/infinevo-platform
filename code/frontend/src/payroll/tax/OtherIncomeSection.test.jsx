import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OtherIncomeSection } from './OtherIncomeSection';
import { declarationService } from './declarationService';

vi.mock('./declarationService', () => ({
  declarationService: {
    otherIncome: vi.fn(),
    saveOtherIncome: vi.fn(),
  },
}));

describe('OtherIncomeSection', () => {
  const sampleOtherIncome = [
    {
      id: 'oi-1',
      kind: 'SAVINGS_INTEREST',
      description: 'HDFC Savings Account',
      amount: 6500,
    },
    {
      id: 'oi-2',
      kind: 'FD_INTEREST',
      description: 'SBI 1-year FD',
      amount: 14000,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders existing other income rows without computing client-side totals', async () => {
    declarationService.otherIncome.mockResolvedValueOnce(sampleOtherIncome);

    render(<OtherIncomeSection fy="2026-27" editable={true} />);

    expect(declarationService.otherIncome).toHaveBeenCalledWith('2026-27');

    await waitFor(() => {
      expect(screen.getByDisplayValue('HDFC Savings Account')).toBeTruthy();
      expect(screen.getByDisplayValue('SBI 1-year FD')).toBeTruthy();
    });
    expect(screen.queryByText('Total Other Income Declared:')).toBeNull();
  });

  it('adds and saves a new other income row', async () => {
    declarationService.otherIncome.mockResolvedValueOnce([]);
    declarationService.saveOtherIncome.mockResolvedValueOnce({ success: true });

    render(<OtherIncomeSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByTestId('add-other-income-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('add-other-income-btn'));

    await waitFor(() => {
      expect(screen.getByTestId('input-other-income-desc-0')).toBeTruthy();
    });

    const descInput = screen.getByTestId('input-other-income-desc-0');
    fireEvent.change(descInput, { target: { value: 'Dividend from Tata Motors' } });

    const saveBtn = screen.getByTestId('save-other-income-btn');
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(declarationService.saveOtherIncome).toHaveBeenCalledWith('2026-27', expect.any(Array));
    });
  });

  it('removes an other income row', async () => {
    declarationService.otherIncome.mockResolvedValueOnce(sampleOtherIncome);

    render(<OtherIncomeSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByTestId('delete-other-income-row-0')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('delete-other-income-row-0'));

    await waitFor(() => {
      expect(screen.queryByDisplayValue('HDFC Savings Account')).toBeNull();
      expect(screen.getByDisplayValue('SBI 1-year FD')).toBeTruthy();
    });
  });
});
