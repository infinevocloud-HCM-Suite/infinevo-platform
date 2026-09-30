import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DeclarationPage } from './DeclarationPage';
import { declarationService } from './declarationService';
import { store } from '@shell/store';
import { resetTaxState } from './taxSlice';

vi.mock('./declarationService', () => ({
  declarationService: {
    header: vi.fn(),
    saveHeader: vi.fn(),
    submit: vi.fn(),
    reopen: vi.fn(),
    housing: vi.fn().mockResolvedValue({ house_rent: [], home_loans: [], let_out_properties: [] }),
    items: vi.fn().mockResolvedValue([]),
    deductions: vi.fn().mockResolvedValue({ section6a: [], pre_tax_deductions: [], previous_employment: [] }),
    otherIncome: vi.fn().mockResolvedValue([]),
    summary: vi.fn().mockResolvedValue({ declared: {}, computed: {} }),
  },
}));

describe('DeclarationPage', () => {
  const sampleHeader = {
    employee_id: 'EMP-123',
    financial_year: '2026-27',
    tax_regime: 'NEW',
    status: 'DRAFT',
    window_open: true,
    editable: true,
    can_change_tax_regime: true,
    reopenable: true,
    is_staying_in_rented_house: false,
    is_repaying_self_occupied_loan: false,
    has_let_out_property: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    store.dispatch(resetTaxState());
  });

  it('renders header details and tabs on mount without writing to localStorage', async () => {
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    declarationService.header.mockResolvedValueOnce(sampleHeader);

    render(<DeclarationPage initialFy="2026-27" />);

    expect(screen.getByText('Tax Declaration (Investment Declarations)')).toBeTruthy();
    expect(declarationService.header).toHaveBeenCalledWith('2026-27');

    await waitFor(() => {
      expect(screen.getByText('New Regime (115BAC)')).toBeTruthy();
      expect(screen.getByText('DRAFT')).toBeTruthy();
      expect(screen.getByText('Window Open')).toBeTruthy();
      expect(screen.getByText('Editable')).toBeTruthy();
      expect(screen.getByTestId('housing-tab-content')).toBeTruthy();
    });

    expect(setItemSpy).not.toHaveBeenCalled();
    expect(store.getState().tax.header).toEqual(sampleHeader);
    setItemSpy.mockRestore();
  });

  it('displays warning alert when window is closed and disables edits', async () => {
    declarationService.header.mockResolvedValueOnce({
      ...sampleHeader,
      window_open: false,
      editable: false,
    });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByText('Declaration Window is Currently Closed')).toBeTruthy();
      expect(screen.queryByTestId('submit-declaration-btn')).toBeNull();
    });
  });

  it('allows changing tax_regime via modal', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    declarationService.saveHeader.mockResolvedValueOnce({
      ...sampleHeader,
      tax_regime: 'OLD',
    });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('change-regime-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('change-regime-btn'));

    await waitFor(() => {
      expect(screen.getByText(/Select the tax regime for Financial Year/i)).toBeTruthy();
    });

    const oldRadio = screen.getByText('Old Tax Regime');
    fireEvent.click(oldRadio);

    const applyBtn = screen.getByRole('button', { name: /apply regime change/i });
    fireEvent.click(applyBtn);

    await waitFor(() => {
      expect(declarationService.saveHeader).toHaveBeenCalledWith(
        '2026-27',
        expect.objectContaining({
          tax_regime: 'OLD',
        }),
      );
    });
  });

  it('updates housing flag when switch is toggled', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    declarationService.saveHeader.mockResolvedValueOnce({
      ...sampleHeader,
      is_staying_in_rented_house: true,
    });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('switch-rented-house')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('switch-rented-house'));

    await waitFor(() => {
      expect(declarationService.saveHeader).toHaveBeenCalledWith(
        '2026-27',
        expect.objectContaining({
          tax_regime: 'NEW',
          is_staying_in_rented_house: true,
        }),
      );
    });
  });

  it('submits tax declaration and re-fetches header', async () => {
    declarationService.header
      .mockResolvedValueOnce(sampleHeader)
      .mockResolvedValueOnce({ ...sampleHeader, status: 'SUBMITTED', editable: false });
    declarationService.submit.mockResolvedValueOnce({ status: 'SUBMITTED', submitted_at: '2026-04-15T10:00:00Z' });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('submit-declaration-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('submit-declaration-btn'));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /yes, submit/i })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /yes, submit/i }));

    await waitFor(() => {
      expect(declarationService.submit).toHaveBeenCalledWith('2026-27');
      expect(declarationService.header).toHaveBeenCalledTimes(2);
    });
  });

  it('re-fetches header on 409 ALREADY_SUBMITTED error during submit', async () => {
    declarationService.header
      .mockResolvedValueOnce(sampleHeader)
      .mockResolvedValueOnce({ ...sampleHeader, status: 'SUBMITTED', editable: false });
    const conflictErr = new Error('Already submitted');
    conflictErr.response = { status: 409, data: { code: 'ALREADY_SUBMITTED', message: 'Declaration already submitted' } };
    declarationService.submit.mockRejectedValueOnce(conflictErr);

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('submit-declaration-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('submit-declaration-btn'));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /yes, submit/i })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /yes, submit/i }));

    await waitFor(() => {
      expect(declarationService.submit).toHaveBeenCalledWith('2026-27');
      expect(declarationService.header).toHaveBeenCalledTimes(2);
      expect(screen.getByText('SUBMITTED')).toBeTruthy();
    });
  });

  it('reopens tax declaration when submitted and re-fetches header', async () => {
    declarationService.header
      .mockResolvedValueOnce({
        ...sampleHeader,
        status: 'SUBMITTED',
        editable: false,
      })
      .mockResolvedValueOnce(sampleHeader);
    declarationService.reopen.mockResolvedValueOnce({ status: 'DRAFT' });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('reopen-declaration-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('reopen-declaration-btn'));

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /yes, reopen/i })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /yes, reopen/i }));

    await waitFor(() => {
      expect(declarationService.reopen).toHaveBeenCalledWith('2026-27');
      expect(declarationService.header).toHaveBeenCalledTimes(2);
    });
  });

  it('switches between tabs', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('housing-tab-content')).toBeTruthy();
    });

    const deductionsTab = screen.getByText(/Deductions & 80C/i);
    fireEvent.click(deductionsTab);

    await waitFor(() => {
      expect(screen.getByTestId('deductions-tab-content')).toBeTruthy();
    });
  });
});
