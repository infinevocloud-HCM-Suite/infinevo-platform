import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { OtherIncomeSection } from './OtherIncomeSection';
import { declarationService } from './declarationService';
import taxReducer from './taxSlice';

vi.mock('./declarationService', () => ({
  declarationService: {
    otherIncome: vi.fn(),
    saveOtherIncome: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

function renderSection(store = configureStore({ reducer: { tax: taxReducer } })) {
  const utils = render(
    <Provider store={store}>
      <OtherIncomeSection fy="2026-27" editable={true} />
    </Provider>,
  );
  return { ...utils, store };
}

describe('OtherIncomeSection', () => {
  const sampleOtherIncome = [
    { id: 'oi-1', kind: 'SAVINGS_INTEREST', description: 'HDFC Savings Account', amount: 6500 },
    { id: 'oi-2', kind: 'FD_INTEREST', description: 'SBI 1-year FD', amount: 14000 },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders existing rows without computing client-side totals', async () => {
    declarationService.otherIncome.mockResolvedValueOnce(sampleOtherIncome);

    renderSection();

    expect(declarationService.otherIncome).toHaveBeenCalledWith('2026-27');
    await waitFor(() => {
      expect(screen.getByDisplayValue('HDFC Savings Account')).toBeTruthy();
      expect(screen.getByDisplayValue('SBI 1-year FD')).toBeTruthy();
    });
    expect(screen.queryByText('Total Other Income Declared:')).toBeNull();
  });

  it('adds a row, saves it, and replaces the section with the response', async () => {
    declarationService.otherIncome.mockResolvedValueOnce([]);
    const saved = [{ id: 'oi-9', kind: 'SAVINGS_INTEREST', description: 'Dividend from Tata Motors', amount: 1200 }];
    declarationService.saveOtherIncome.mockResolvedValueOnce(saved);

    const { store } = renderSection();
    await waitFor(() => expect(declarationService.otherIncome).toHaveBeenCalled());

    fireEvent.click(screen.getByTestId('add-other-income-btn'));
    fireEvent.change(screen.getByTestId('input-other-income-desc-0'), { target: { value: 'Dividend from Tata Motors' } });
    fireEvent.change(screen.getByTestId('input-other-income-amount-0'), { target: { value: '1200' } });
    fireEvent.click(screen.getByTestId('save-other-income-btn'));

    await waitFor(() => {
      expect(declarationService.saveOtherIncome).toHaveBeenCalledWith('2026-27', [
        { kind: 'SAVINGS_INTEREST', description: 'Dividend from Tata Motors', amount: 1200 },
      ]);
      expect(store.getState().tax.sections.otherIncome).toEqual(saved);
    });
  });

  it('marks the row a 400 names', async () => {
    declarationService.otherIncome.mockResolvedValueOnce(sampleOtherIncome);
    declarationService.saveOtherIncome.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Row 2: amount must be non-negative',
    });

    renderSection();
    await waitFor(() => expect(screen.getByDisplayValue('SBI 1-year FD')).toBeTruthy());
    fireEvent.click(screen.getByTestId('save-other-income-btn'));

    await waitFor(() => expect(screen.getByTestId('other-income-row-error-1')).toBeTruthy());
    expect(screen.queryByTestId('other-income-row-error-0')).toBeNull();
  });

  it('removes a row', async () => {
    declarationService.otherIncome.mockResolvedValueOnce(sampleOtherIncome);

    renderSection();
    await waitFor(() => expect(screen.getByTestId('delete-other-income-row-0')).toBeTruthy());
    fireEvent.click(screen.getByTestId('delete-other-income-row-0'));

    await waitFor(() => {
      expect(screen.queryByDisplayValue('HDFC Savings Account')).toBeNull();
      expect(screen.getByDisplayValue('SBI 1-year FD')).toBeTruthy();
    });
  });
});
