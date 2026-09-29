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
      code: '80C_LIC',
      name: 'Life Insurance Premium',
      group_code: '80C',
      max_limit: 150000,
      description: 'Life insurance premiums paid for self/family',
    },
    {
      id: 'item-80d-med',
      code: '80D_MED',
      name: 'Medical Insurance',
      group_code: '80D',
      max_limit: 50000,
      description: 'Health insurance policies for self/parents',
    },
  ];

  const sampleDeductions = {
    section6a: [
      {
        section6a_item_id: 'item-80c-lic',
        item_code: '80C_LIC',
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
      },
    ],
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders catalogue items and existing 6A declarations', async () => {
    declarationService.items.mockResolvedValueOnce(sampleItems);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);

    render(<DeductionsSection fy="2026-27" editable={true} />);

    expect(declarationService.items).toHaveBeenCalledWith('2026-27');
    expect(declarationService.deductions).toHaveBeenCalledWith('2026-27');

    await waitFor(() => {
      expect(screen.getByText('Group 80C')).toBeTruthy();
      expect(screen.getByText('Group 80D')).toBeTruthy();
      expect(screen.getByText('Life Insurance Premium')).toBeTruthy();
    });
  });

  it('saves 6A deduction declaration on clicking save', async () => {
    declarationService.items.mockResolvedValueOnce(sampleItems);
    declarationService.deductions.mockResolvedValueOnce(sampleDeductions);
    declarationService.save6a.mockResolvedValueOnce({ success: true });

    render(<DeductionsSection fy="2026-27" editable={true} />);

    await waitFor(() => {
      expect(screen.getByTestId('save-6a-btn')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('save-6a-btn'));

    await waitFor(() => {
      expect(declarationService.save6a).toHaveBeenCalledWith('2026-27', expect.any(Array));
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

  it('switches to Previous Employment tab and saves details', async () => {
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
      expect(screen.getByDisplayValue('Previous Tech Corp')).toBeTruthy();
    });

    fireEvent.click(screen.getByTestId('save-prevemp-btn'));

    await waitFor(() => {
      expect(declarationService.savePrevEmployment).toHaveBeenCalledWith('2026-27', expect.any(Array));
    });
  });
});
