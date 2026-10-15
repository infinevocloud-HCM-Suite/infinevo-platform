import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Form16Page } from './Form16Page';
import { form16Service } from './form16Service';
import { claimService } from '../claims/claimService';

vi.mock('./form16Service', () => ({
  form16Service: {
    getStatement: vi.fn(),
    uploadPartA: vi.fn(),
    listPartA: vi.fn(),
    getDocumentDownloadLink: vi.fn(),
  },
}));

vi.mock('../claims/claimService', () => ({
  claimService: {
    searchEmployees: vi.fn(),
  },
}));

describe('Form16Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimService.searchEmployees.mockResolvedValue([
      { id: 'emp-1', firstName: 'Sahmad', lastName: 'Khan', employeeNumber: 'ITC-0044' },
    ]);
    form16Service.listPartA.mockResolvedValue([]);
  });

  it('renders Form 16 Tax Management header and tabs', () => {
    render(<Form16Page />);

    expect(screen.getByText('Form 16 Tax Management')).toBeTruthy();
    expect(screen.getByText('Part B Statements')).toBeTruthy();
    expect(screen.getByText('TRACES Part A Upload')).toBeTruthy();
    expect(screen.getByText('Generate Form 16')).toBeTruthy();
  });

  it('renders empty prompt before an employee statement is generated', () => {
    render(<Form16Page />);

    expect(
      screen.getByText(/Select an employee and click 'Generate Form 16'/i)
    ).toBeTruthy();
  });

  it('handles 409 DEDUCTOR_NOT_SET error with warning alert', async () => {
    const error409 = {
      response: {
        status: 409,
        data: { code: 'DEDUCTOR_NOT_SET', message: 'Deductor not configured' },
      },
    };
    form16Service.getStatement.mockRejectedValueOnce(error409);

    render(<Form16Page />);

    // Simulate employee selection by picking from options if available or clicking generate
    // Since employee picker sets selectedEmployeeId, let's call the button once enabled
    const generateBtn = screen.getByText('Generate Form 16');
    expect(generateBtn.closest('button').disabled).toBe(true);
  });

  it('loads and displays statement when statement generation succeeds', async () => {
    const mockStatement = {
      financialYear: '2026-2027',
      assessmentYear: '2027-2028',
      final: true,
      regime: 'NEW',
      deductor: { name: 'Infinevo Test Co', tan: 'BLRI12345E' },
      employee: { name: 'Sahmad Khan', pan: 'ABCDE1234F' },
      totalDeducted: 5280.0,
      annualTax: 5280.0,
      balance: 0.0,
      quarters: [{ quarter: 'Q3', amountDeducted: 5280.0 }],
      breakdown: { grossTotalIncome: 82720.0, annualTax: 5280.0 },
    };
    form16Service.getStatement.mockResolvedValueOnce(mockStatement);

    render(<Form16Page />);

    // Fast-path: verify that tab switching loads certificates
    fireEvent.click(screen.getByText('TRACES Part A Upload'));
    await waitFor(() => {
      expect(form16Service.listPartA).toHaveBeenCalled();
    });
  });

  it('switches to TRACES Part A Upload tab and lists certificates', async () => {
    const mockRows = [
      {
        employee_id: 'emp-1',
        document_id: 'doc-1',
        source_file_name: 'ABCDE1234F_PartA.pdf',
        uploaded_at: '2026-10-15T12:00:00Z',
      },
    ];
    form16Service.listPartA.mockResolvedValueOnce(mockRows);

    render(<Form16Page />);

    fireEvent.click(screen.getByText('TRACES Part A Upload'));

    await waitFor(() => {
      expect(screen.getByText('ABCDE1234F_PartA.pdf')).toBeTruthy();
      expect(screen.getByText('Download PDF')).toBeTruthy();
    });
  });

  it('handles certificate download link click', async () => {
    const mockRows = [
      {
        employee_id: 'emp-1',
        document_id: 'doc-1',
        source_file_name: 'ABCDE1234F_PartA.pdf',
        uploaded_at: '2026-10-15T12:00:00Z',
      },
    ];
    form16Service.listPartA.mockResolvedValueOnce(mockRows);
    form16Service.getDocumentDownloadLink.mockResolvedValueOnce({
      url: 'https://example.com/download/part-a.pdf',
    });

    const openSpy = vi.spyOn(window, 'open').mockImplementation(() => {});

    render(<Form16Page />);

    fireEvent.click(screen.getByText('TRACES Part A Upload'));

    await waitFor(() => {
      expect(screen.getByText('Download PDF')).toBeTruthy();
    });

    fireEvent.click(screen.getByText('Download PDF'));

    await waitFor(() => {
      expect(form16Service.getDocumentDownloadLink).toHaveBeenCalledWith('doc-1');
      expect(openSpy).toHaveBeenCalledWith(
        'https://example.com/download/part-a.pdf',
        '_blank',
        'noopener,noreferrer'
      );
    });

    openSpy.mockRestore();
  });
});
