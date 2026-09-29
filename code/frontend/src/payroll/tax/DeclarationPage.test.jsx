import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DeclarationPage } from './DeclarationPage';
import { declarationService } from './declarationService';

vi.mock('./declarationService', () => ({
  declarationService: {
    header: vi.fn(),
    saveHeader: vi.fn(),
    submit: vi.fn(),
    reopen: vi.fn(),
  },
}));

describe('DeclarationPage', () => {
  const sampleHeader = {
    employee_id: 'EMP-123',
    financial_year: '2026-27',
    regime: 'NEW',
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
  });

  it('renders header details and tabs on mount', async () => {
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

  it('allows changing tax regime via modal', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    declarationService.saveHeader.mockResolvedValueOnce({ success: true });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('change-regime-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('change-regime-btn'));

    // Modal opens
    await waitFor(() => {
      expect(screen.getByText(/Select the tax regime for Financial Year/i)).toBeTruthy();
    });

    // Select OLD regime radio
    const oldRadio = screen.getByText('Old Tax Regime');
    fireEvent.click(oldRadio);

    // Click confirm button
    const applyBtn = screen.getByRole('button', { name: /apply regime change/i });
    fireEvent.click(applyBtn);

    await waitFor(() => {
      expect(declarationService.saveHeader).toHaveBeenCalledWith('2026-27', expect.objectContaining({
        regime: 'OLD',
      }));
    });
  });

  it('updates housing flag when switch is toggled', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    declarationService.saveHeader.mockResolvedValueOnce({ success: true });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('switch-rented-house')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('switch-rented-house'));

    await waitFor(() => {
      expect(declarationService.saveHeader).toHaveBeenCalledWith('2026-27', expect.objectContaining({
        is_staying_in_rented_house: true,
      }));
    });
  });

  it('submits tax declaration and updates status to SUBMITTED', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);
    declarationService.submit.mockResolvedValueOnce({ status: 'SUBMITTED', submitted_at: '2026-04-15T10:00:00Z' });

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('submit-declaration-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('submit-declaration-btn'));

    // Confirmation popconfirm ok button
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /yes, submit/i })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /yes, submit/i }));

    await waitFor(() => {
      expect(declarationService.submit).toHaveBeenCalledWith('2026-27');
    });
  });

  it('reopens tax declaration when submitted', async () => {
    declarationService.header.mockResolvedValueOnce({
      ...sampleHeader,
      status: 'SUBMITTED',
      editable: false,
    });
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
    });
  });

  it('switches between tabs', async () => {
    declarationService.header.mockResolvedValueOnce(sampleHeader);

    render(<DeclarationPage initialFy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByTestId('housing-tab-content')).toBeTruthy();
    });

    // Click on Deductions tab
    const deductionsTab = screen.getByText(/Deductions & 80C/i);
    fireEvent.click(deductionsTab);

    await waitFor(() => {
      expect(screen.getByTestId('deductions-tab-content')).toBeTruthy();
    });
  });
});
