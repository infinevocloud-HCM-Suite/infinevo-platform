import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { FnfStatementPage } from './FnfStatementPage.jsx';
import { fnfService } from './fnfService.js';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('./fnfService.js', () => ({
  fnfService: {
    getSettlement: vi.fn(),
    approveSettlement: vi.fn(),
    paySettlement: vi.fn(),
  },
}));

describe('FnfStatementPage (W-90.4)', () => {
  const sampleDraftSettlement = {
    id: 'settlement-101',
    employeeId: 'EMP-1001',
    resignationDate: '2026-10-15',
    exitDate: '2026-11-30',
    exitReason: 'RESIGNATION',
    workingDaysInExitMonth: 22,
    exitMonthSalary: 55000,
    leaveEncashmentAmount: 18000,
    gratuityAmount: 72000,
    noticePayAmount: 0,
    otherEarnings: 0,
    grossEarnings: 145000,
    noticeRecoveryAmount: 5000,
    epfDeduction: 1800,
    esiDeduction: 0,
    ptDeduction: 200,
    tdsDeduction: 8000,
    otherRecoveries: 0,
    grossDeductions: 15000,
    netPayable: 130000,
    status: 'DRAFT',
  };

  const sampleApprovedSettlement = {
    ...sampleDraftSettlement,
    status: 'APPROVED',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders statement with earnings, deductions, and net payable', async () => {
    fnfService.getSettlement.mockResolvedValueOnce(sampleDraftSettlement);

    render(
      <MemoryRouter initialEntries={['/payroll/settlements/settlement-101']}>
        <Routes>
          <Route path="/payroll/settlements/:id" element={<FnfStatementPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Full & Final Settlement Statement')).toBeTruthy();
      expect(screen.getByText('EMP-1001')).toBeTruthy();
      expect(screen.getByText('Gross Earnings (+)')).toBeTruthy();
      expect(screen.getByText('Gross Deductions & Recoveries (-)')).toBeTruthy();
      expect(screen.getByText('Net Payable Amount (Earnings - Deductions)')).toBeTruthy();
    });

    expect(screen.getByRole('button', { name: /Approve Settlement/i })).toBeTruthy();
  });

  it('handles approve settlement action', async () => {
    fnfService.getSettlement.mockResolvedValueOnce(sampleDraftSettlement);
    fnfService.approveSettlement.mockResolvedValueOnce({
      ...sampleDraftSettlement,
      status: 'APPROVED',
    });

    render(
      <MemoryRouter initialEntries={['/payroll/settlements/settlement-101']}>
        <Routes>
          <Route path="/payroll/settlements/:id" element={<FnfStatementPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Approve Settlement/i })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /Approve Settlement/i }));

    await waitFor(() => {
      expect(fnfService.approveSettlement).toHaveBeenCalledWith('settlement-101');
    });
  });

  it('handles pay settlement action when approved', async () => {
    fnfService.getSettlement.mockResolvedValueOnce(sampleApprovedSettlement);
    fnfService.paySettlement.mockResolvedValueOnce({
      ...sampleApprovedSettlement,
      status: 'PAID',
    });

    render(
      <MemoryRouter initialEntries={['/payroll/settlements/settlement-101']}>
        <Routes>
          <Route path="/payroll/settlements/:id" element={<FnfStatementPage />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Mark as Paid/i })).toBeTruthy();
    });

    fireEvent.click(screen.getByRole('button', { name: /Mark as Paid/i }));

    await waitFor(() => {
      expect(fnfService.paySettlement).toHaveBeenCalledWith('settlement-101');
    });
  });
});
