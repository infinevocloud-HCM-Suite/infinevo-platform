import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { DeductionsSection } from './DeductionsSection';
import { declarationService } from './declarationService';

vi.mock('./declarationService', () => ({
  declarationService: {
    items: vi.fn(),
    deductions: vi.fn(),
    save6a: vi.fn(),
    savePreTax: vi.fn(),
    savePrevEmployment: vi.fn(),
  },
}));

describe('DeductionsSection', () => {
  const sampleItems = [
    {
      id: 'item-80c-lic',
      section_code: '80C_LIC',
      name: 'Life Insurance Premium',
      category_group_code: '80C',
      max_limit: 150000,
      description: 'Life insurance premiums paid for self/family',
    },
    {
      id: 'item-80d-med',
      section_code: '80D_MED',
      name: 'Medical Insurance',
      category_group_code: '80D',
      max_limit: 25000,
      description: 'Health insurance policies for self/parents',
    },
    {
      id: 'item-80d-parents',
      section_code: '80D_PARENTS_SR',
      name: 'Medical Insurance - Senior Citizen Parents',
      category_group_code: '80D',
      max_limit: 50000,
      description: 'Health insurance for senior citizen parents',
    },
  ];

  const sampleDeductions = {
    section6a: [
      {
        section6a_item_id: 'item-80c-lic',
        section_code: '80C_LIC',
        amount: 50000,
        description: 'LIC Policy 1',
      },
    ],
    pre_tax_deductions: [
      {
        kind: 'VPF',
        amount: 10000,
      },
    ],
    previous_employment: [
      {
        kind: 'INCOME',
        amount: 250000,
        employer_name: 'Previous Tech Corp',
        employer_tan: 'PUNE12345A',
        entered_by: 'EMPLOYEE',
      },
      {
        kind: 'INCOME_TAX_DEDUCTED',
        amount: 20000,
        employer_name: 'Previous Tech Corp',
        employer_tan: 'PUNE12345A',
        entered_by: 'OFFICER',
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('groups catalogue items by category_group_code and renders per-item max_limit', async () => {
    declarationService.items.mockResolvedValueOnce(sampleItems);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);

    render(<DeductionsSection fy="2026-27" editable={true} />);

    expect(declarationService.items).toHaveBeenCalledWith('2026-27');
    expect(declarationService.deductions).toHaveBeenCalledWith('2026-27');

    await waitFor(() => {
      expect(screen.getByText('Group 80C')).toBeTruthy();
      expect(screen.getByText('Group 80D')).toBeTruthy();
      expect(screen.getByText('Life Insurance Premium')).toBeTruthy();
      expect(screen.getByTestId('item-cap-80D_MED').textContent).toContain('25,000');
      expect(screen.getByTestId('item-cap-80D_PARENTS_SR').textContent).toContain('50,000');
    });
  });

  it('saves 6A deduction declaration and surfaces row-level 400 field errors', async () => {
    declarationService.items.mockResolvedValueOnce(sampleItems);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    const err400 = new Error('Validation failed');
    err400.response = {
      status: 400,
      data: {
        message: 'Validation failed',
        errors: { 'item-80c-lic': 'Amount exceeds item limit' },
      },
    };
    declarationService.save6a.mockRejectedValueOnce(err400);

    render(<DeductionsSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByTestId('save-6a-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('save-6a-btn'));

    await waitFor(() => {
      expect(declarationService.save6a).toHaveBeenCalledWith('2026-27', expect.any(Array));
      expect(screen.getByTestId('field-error-item-80c-lic')).toBeTruthy();
      expect(screen.getAllByText(/Amount exceeds item limit/i).length).toBeGreaterThanOrEqual(1);
    });
  });

  it('switches to Pre-Tax tab and saves pre-tax deduction', async () => {
    declarationService.items.mockResolvedValueOnce(sampleItems);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    declarationService.savePreTax.mockResolvedValueOnce({ success: true });

    render(<DeductionsSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByText(/Pre-Tax Deductions/i)).toBeTruthy();
    });

    fireEvent.click(screen.getByText(/Pre-Tax Deductions/i));

    await waitFor(() => {
      expect(screen.getByTestId('save-pretax-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('save-pretax-btn'));

    await waitFor(() => {
      expect(declarationService.savePreTax).toHaveBeenCalledWith('2026-27', expect.any(Array));
    });
  });

  it('renders OFFICER entered previous employment rows as read-only and excludes them on save', async () => {
    declarationService.items.mockResolvedValueOnce(sampleItems);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    declarationService.savePrevEmployment.mockResolvedValueOnce({ success: true });

    render(<DeductionsSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByText(/Previous Employment/i)).toBeTruthy();
    });

    fireEvent.click(screen.getByText(/Previous Employment/i));

    await waitFor(() => {
      expect(screen.getByTestId('save-prevemp-btn')).toBeTruthy();
      expect(screen.getByTestId('officer-badge-INCOME_TAX_DEDUCTED')).toBeTruthy();
      const officerInput = screen.getByTestId('input-prevemp-INCOME_TAX_DEDUCTED');
      expect(officerInput.hasAttribute('disabled')).toBe(true);
    });

    fireEvent.click(screen.getByTestId('save-prevemp-btn'));

    await waitFor(() => {
      expect(declarationService.savePrevEmployment).toHaveBeenCalledWith('2026-27', [
        expect.objectContaining({
          kind: 'INCOME',
          amount: 250000,
        }),
      ]);
    });
  });
});
