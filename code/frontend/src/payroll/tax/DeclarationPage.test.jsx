import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { DeclarationPage } from './DeclarationPage';
import { declarationService } from './declarationService';
import taxReducer from './taxSlice';

vi.mock('./declarationService', () => ({
  declarationService: {
    header: vi.fn(),
    saveHeader: vi.fn(),
    submit: vi.fn(),
    reopen: vi.fn(),
    housing: vi.fn(),
    items: vi.fn(),
    deductions: vi.fn(),
    otherIncome: vi.fn(),
    summary: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

const SAVE_BUTTONS = [
  'save-rent-btn',
  'save-loan-btn',
  'save-letout-btn',
  'save-6a-btn',
  'save-pretax-btn',
  'save-prevemp-btn',
  'save-other-income-btn',
];

describe('DeclarationPage', () => {
  const sampleHeader = {
    employee_id: 'EMP-123',
    financial_year: '2026-2027',
    tax_regime: 'NEW',
    status: 'DRAFT',
    window_open: true,
    editable: true,
    is_staying_in_rented_house: false,
    is_repaying_self_occupied_loan: false,
    has_let_out_property: false,
  };

  let store;

  function renderPage() {
    return render(
      <Provider store={store}>
        <DeclarationPage initialFy="2026-27" />
      </Provider>,
    );
  }

  async function expandAll() {
    fireEvent.click(screen.getByTestId('panel-deductions'));
    fireEvent.click(screen.getByTestId('panel-otherIncome'));
    fireEvent.click(screen.getByTestId('panel-summary'));
    await waitFor(() => {
      expect(screen.getByTestId('deductions-tab-content')).toBeTruthy();
      expect(screen.getByTestId('other-income-tab-content')).toBeTruthy();
      expect(screen.getByTestId('summary-tab-content')).toBeTruthy();
    });
  }

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({ reducer: { tax: taxReducer } });
    declarationService.housing.mockResolvedValue({ house_rent: [], home_loans: [], let_out_properties: [] });
    declarationService.items.mockResolvedValue([]);
    declarationService.deductions.mockResolvedValue({ section6a: [], pre_tax_deductions: [], previous_employment: [] });
    declarationService.otherIncome.mockResolvedValue([]);
    declarationService.summary.mockResolvedValue({ declared: {}, computed: null });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the header and keeps it in the slice', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);

    renderPage();

    expect(screen.getByText('Tax Declaration (Investment Declarations)')).toBeTruthy();
    await waitFor(() => {
      expect(screen.getByText('New Regime (115BAC)')).toBeTruthy();
      expect(screen.getByText('DRAFT')).toBeTruthy();
      expect(screen.getByText('Window Open')).toBeTruthy();
      expect(screen.getByTestId('housing-tab-content')).toBeTruthy();
    });
    expect(declarationService.header).toHaveBeenCalledWith('2026-27');
    expect(store.getState().tax.fy).toBe('2026-27');
    expect(store.getState().tax.header).toEqual(sampleHeader);
  });

  it('writes nothing to localStorage while the user types', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    renderPage();
    await waitFor(() => expect(screen.getByTestId('add-rent-row-btn')).toBeTruthy());

    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');
    fireEvent.click(screen.getByTestId('add-rent-row-btn'));
    fireEvent.change(screen.getByPlaceholderText('Landlord Name'), { target: { value: 'Typed landlord' } });
    fireEvent.change(screen.getByPlaceholderText('Rental premises address'), { target: { value: 'Typed address' } });

    expect(screen.getByDisplayValue('Typed landlord')).toBeTruthy();
    expect(setItemSpy).not.toHaveBeenCalled();
  });

  it('disables every save button and Submit when editable is false', async () => {
    declarationService.header.mockResolvedValueOnce({ ...sampleHeader, window_open: true, editable: false });

    renderPage();
    await waitFor(() => expect(screen.getByText('Declaration is Locked')).toBeTruthy());
    await expandAll();

    await waitFor(() => SAVE_BUTTONS.forEach((id) => expect(screen.getByTestId(id)).toBeTruthy()));
    SAVE_BUTTONS.forEach((id) => {
      expect(screen.getByTestId(id).hasAttribute('disabled'), id).toBe(true);
    });
    expect(screen.getByTestId('submit-declaration-btn').hasAttribute('disabled')).toBe(true);
    expect(screen.getByTestId('switch-rented-house').hasAttribute('disabled')).toBe(true);
  });

  it('shows the closed-window banner and disables Submit when the window is closed', async () => {
    declarationService.header.mockResolvedValueOnce({ ...sampleHeader, window_open: false, editable: false });

    renderPage();

    await waitFor(() => expect(screen.getByText('Declaration Window is Currently Closed')).toBeTruthy());
    expect(screen.getByTestId('submit-declaration-btn').hasAttribute('disabled')).toBe(true);
    expect(screen.getByTestId('save-rent-btn').hasAttribute('disabled')).toBe(true);
  });

  it('enables every save button when editable', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    renderPage();
    await waitFor(() => expect(screen.getByTestId('housing-tab-content')).toBeTruthy());
    await expandAll();

    await waitFor(() => SAVE_BUTTONS.forEach((id) => expect(screen.getByTestId(id)).toBeTruthy()));
    SAVE_BUTTONS.forEach((id) => expect(screen.getByTestId(id).hasAttribute('disabled'), id).toBe(false));
    expect(screen.getByTestId('submit-declaration-btn').hasAttribute('disabled')).toBe(false);
  });

  it('changes tax_regime via the modal', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    declarationService.saveHeader.mockResolvedValueOnce({ ...sampleHeader, tax_regime: 'OLD' });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('change-regime-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('change-regime-btn'));
    await waitFor(() => expect(screen.getByText(/Select the tax regime for Financial Year/i)).toBeTruthy());
    fireEvent.click(screen.getByText('Old Tax Regime'));
    fireEvent.click(screen.getByRole('button', { name: /apply regime change/i }));

    await waitFor(() => {
      expect(declarationService.saveHeader).toHaveBeenCalledWith('2026-27', expect.objectContaining({ tax_regime: 'OLD' }));
      expect(store.getState().tax.header.tax_regime).toBe('OLD');
    });
  });

  it('updates a housing flag when its switch is toggled', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    declarationService.saveHeader.mockResolvedValueOnce({ ...sampleHeader, is_staying_in_rented_house: true });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('switch-rented-house')).toBeTruthy());
    fireEvent.click(screen.getByTestId('switch-rented-house'));

    await waitFor(() => {
      expect(declarationService.saveHeader).toHaveBeenCalledWith(
        '2026-27',
        expect.objectContaining({ tax_regime: 'NEW', is_staying_in_rented_house: true }),
      );
    });
  });

  it('submits and re-reads the header', async () => {
    declarationService.header
      .mockResolvedValueOnce(sampleHeader)
      .mockResolvedValueOnce({ ...sampleHeader, status: 'SUBMITTED', editable: false });
    declarationService.submit.mockResolvedValueOnce({ status: 'SUBMITTED' });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('submit-declaration-btn')).toBeTruthy());
    fireEvent.click(screen.getByTestId('submit-declaration-btn'));
    await waitFor(() => expect(screen.getByRole('button', { name: /yes, submit/i })).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /yes, submit/i }));

    await waitFor(() => {
      expect(declarationService.submit).toHaveBeenCalledWith('2026-27');
      expect(declarationService.header).toHaveBeenCalledTimes(2);
    });
  });

  it('re-reads the header on 409 ALREADY_SUBMITTED from Submit', async () => {
    declarationService.header
      .mockResolvedValueOnce(sampleHeader)
      .mockResolvedValueOnce({ ...sampleHeader, status: 'SUBMITTED', editable: false });
    declarationService.submit.mockRejectedValueOnce({
      status: 409,
      code: 'ALREADY_SUBMITTED',
      message: 'Declaration already submitted',
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('submit-declaration-btn')).toBeTruthy());
    fireEvent.click(screen.getByTestId('submit-declaration-btn'));
    await waitFor(() => expect(screen.getByRole('button', { name: /yes, submit/i })).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /yes, submit/i }));

    await waitFor(() => {
      expect(declarationService.header).toHaveBeenCalledTimes(2);
      expect(screen.getAllByText('SUBMITTED').length).toBeGreaterThan(0);
    });
  });

  it('enables Reopen when SUBMITTED and the window is open, and re-reads the header', async () => {
    declarationService.header
      .mockResolvedValueOnce({ ...sampleHeader, status: 'SUBMITTED', editable: false })
      .mockResolvedValueOnce(sampleHeader);
    declarationService.reopen.mockResolvedValueOnce({ status: 'DRAFT' });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('reopen-declaration-btn')).toBeTruthy());
    expect(screen.getByTestId('reopen-declaration-btn').hasAttribute('disabled')).toBe(false);

    fireEvent.click(screen.getByTestId('reopen-declaration-btn'));
    await waitFor(() => expect(screen.getByRole('button', { name: /yes, reopen/i })).toBeTruthy());
    fireEvent.click(screen.getByRole('button', { name: /yes, reopen/i }));

    await waitFor(() => {
      expect(declarationService.reopen).toHaveBeenCalledWith('2026-27');
      expect(declarationService.header).toHaveBeenCalledTimes(2);
    });
  });

  it('disables Reopen when SUBMITTED and the window is closed', async () => {
    declarationService.header.mockResolvedValueOnce({
      ...sampleHeader,
      status: 'SUBMITTED',
      editable: false,
      window_open: false,
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('reopen-declaration-btn')).toBeTruthy());
    expect(screen.getByTestId('reopen-declaration-btn').hasAttribute('disabled')).toBe(true);
  });

  it('loads a section on its first expand, once', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    renderPage();
    await waitFor(() => expect(screen.getByTestId('housing-tab-content')).toBeTruthy());

    expect(declarationService.otherIncome).not.toHaveBeenCalled();
    fireEvent.click(screen.getByTestId('panel-otherIncome'));
    await waitFor(() => expect(declarationService.otherIncome).toHaveBeenCalledTimes(1));

    // Collapse and expand again: read from the slice, not the server.
    fireEvent.click(screen.getByTestId('panel-otherIncome'));
    fireEvent.click(screen.getByTestId('panel-otherIncome'));
    expect(declarationService.otherIncome).toHaveBeenCalledTimes(1);
  });
});
