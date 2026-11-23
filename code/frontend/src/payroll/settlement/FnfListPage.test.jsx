import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { FnfListPage } from './FnfListPage.jsx';
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
    listSettlements: vi.fn(),
    initiateSettlement: vi.fn(),
  },
}));

describe('FnfListPage (W-90.4)', () => {
  const sampleSettlements = [
    {
      id: 'settlement-1',
      employeeId: 'EMP-001',
      exitDate: '2026-11-30',
      exitReason: 'RESIGNATION',
      grossEarnings: 150000,
      grossDeductions: 25000,
      netPayable: 125000,
      status: 'DRAFT',
    },
    {
      id: 'settlement-2',
      employeeId: 'EMP-002',
      exitDate: '2026-11-15',
      exitReason: 'TERMINATION',
      grossEarnings: 80000,
      grossDeductions: 10000,
      netPayable: 70000,
      status: 'PAID',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders table with settlement records and handles navigation', async () => {
    fnfService.listSettlements.mockResolvedValueOnce(sampleSettlements);

    render(
      <MemoryRouter>
        <FnfListPage />
      </MemoryRouter>
    );

    expect(screen.getByText('Full & Final (F&F) Exit Settlements')).toBeTruthy();
    expect(screen.getByText('Initiate Settlement')).toBeTruthy();

    await waitFor(() => {
      expect(screen.getByText('EMP-001')).toBeTruthy();
      expect(screen.getByText('EMP-002')).toBeTruthy();
    });

    // View Statement button navigates to detail
    const viewButtons = screen.getAllByRole('button', { name: /View Statement/i });
    expect(viewButtons.length).toBe(2);
    fireEvent.click(viewButtons[0]);
    expect(mockNavigate).toHaveBeenCalledWith('/payroll/settlements/settlement-1');
  });

  it('filters settlements based on search input', async () => {
    fnfService.listSettlements.mockResolvedValueOnce(sampleSettlements);

    render(
      <MemoryRouter>
        <FnfListPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('EMP-001')).toBeTruthy();
      expect(screen.getByText('EMP-002')).toBeTruthy();
    });

    const searchInput = screen.getByPlaceholderText('Search by Employee ID...');
    fireEvent.change(searchInput, { target: { value: 'EMP-001' } });

    expect(screen.getByText('EMP-001')).toBeTruthy();
    expect(screen.queryByText('EMP-002')).toBeNull();
  });

  it('opens Initiate Settlement modal when clicking initiate button', async () => {
    fnfService.listSettlements.mockResolvedValueOnce([]);

    render(
      <MemoryRouter>
        <FnfListPage />
      </MemoryRouter>
    );

    const initiateBtn = screen.getByRole('button', { name: /Initiate Settlement/i });
    fireEvent.click(initiateBtn);

    await waitFor(() => {
      expect(screen.getByText(/Initiate Full & Final \(F&F\) Settlement/i)).toBeTruthy();
    });
  });
});
