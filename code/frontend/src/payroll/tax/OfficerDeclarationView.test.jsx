import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OfficerDeclarationView } from './OfficerDeclarationView';
import { declarationService } from './declarationService';

vi.mock('./declarationService', () => ({
  declarationService: {
    headerOf: vi.fn(),
  },
}));

describe('OfficerDeclarationView', () => {
  const sampleHeader = {
    employee_id: 'EMP-1001',
    financial_year: '2026-27',
    regime: 'NEW',
    status: 'SUBMITTED',
    window_open: true,
    editable: false,
    is_staying_in_rented_house: true,
    is_repaying_self_occupied_loan: false,
    has_let_out_property: false,
    submitted_at: '2026-04-10T12:00:00Z',
    updated_at: '2026-04-10T12:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders prompt when no employee ID is provided initially', () => {
    render(<OfficerDeclarationView />);

    expect(screen.getByText('Officer Declaration Review')).toBeTruthy();
    expect(screen.getByText(/Payroll Officer Review Mode/i)).toBeTruthy();
    expect(screen.getByText(/Officer Mode \(Read-Only\)/i)).toBeTruthy();
    expect(screen.getByText(/Enter an Employee ID above to review/i)).toBeTruthy();
  });

  it('fetches and displays header for provided employee ID prop', async () => {
    declarationService.headerOf.mockResolvedValueOnce(sampleHeader);

    render(<OfficerDeclarationView employeeId="EMP-1001" fy="2026-27" />);

    expect(declarationService.headerOf).toHaveBeenCalledWith('EMP-1001', '2026-27');

    await waitFor(() => {
      expect(screen.getByText('EMP-1001')).toBeTruthy();
      expect(screen.getByText('SUBMITTED')).toBeTruthy();
      expect(screen.getByText('New Regime (Sec 115BAC)')).toBeTruthy();
      expect(screen.getByText('Window Open')).toBeTruthy();
      expect(screen.getByText('Locked')).toBeTruthy();
    });
  });

  it('handles search input submission to inspect an employee', async () => {
    declarationService.headerOf.mockResolvedValueOnce(sampleHeader);

    render(<OfficerDeclarationView fy="2026-27" />);

    const input = screen.getByTestId('officer-employee-input');
    fireEvent.change(input, { target: { value: 'EMP-1001' } });

    const searchBtn = screen.getByRole('button', { name: /search/i });
    fireEvent.click(searchBtn);

    await waitFor(() => {
      expect(declarationService.headerOf).toHaveBeenCalledWith('EMP-1001', '2026-27');
      expect(screen.getByText('EMP-1001')).toBeTruthy();
    });
  });

  it('shows warning alert when employee declaration is not found (404)', async () => {
    const error404 = new Error('Not found');
    error404.response = { status: 404 };
    declarationService.headerOf.mockRejectedValueOnce(error404);

    render(<OfficerDeclarationView employeeId="EMP-UNKNOWN" fy="2026-27" />);

    await waitFor(() => {
      expect(screen.getByText(/No tax declaration found for employee "EMP-UNKNOWN"/i)).toBeTruthy();
    });
  });
});
