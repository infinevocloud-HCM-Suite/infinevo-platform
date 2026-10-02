import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { NavigationProvider } from '@shell/navigation/useNavigation';
import { OfficerDeclarationView, isEmployeeUuid } from './OfficerDeclarationView';
import { declarationService } from './declarationService';

vi.mock('./declarationService', () => ({
  declarationService: {
    headerOf: vi.fn(),
  },
}));

const EMPLOYEE_ID = '3f2b9c1e-8a4d-4f6b-9c2e-1a2b3c4d5e6f';

function renderAt(route, { actions = ['payroll.tax_declaration.read'] } = {}) {
  return render(
    <NavigationProvider value={{ items: [], actions, loading: false, error: null }}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/employees/:employeeId/tax-declaration/:fy" element={<OfficerDeclarationView />} />
          <Route path="/payroll/tax-declarations/:employeeId/:fy" element={<OfficerDeclarationView />} />
        </Routes>
      </MemoryRouter>
    </NavigationProvider>,
  );
}

describe('OfficerDeclarationView', () => {
  const sampleHeader = {
    employee_id: EMPLOYEE_ID,
    financial_year: '2026-2027',
    tax_regime: 'NEW',
    status: 'SUBMITTED',
    window_open: true,
    editable: false,
    is_staying_in_rented_house: true,
    is_repaying_self_occupied_loan: false,
    has_let_out_property: false,
    submitted_at: '2026-04-10T12:00:00Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders NotEntitled without payroll.tax_declaration.read, and reads nothing', () => {
    renderAt(`/payroll/tax-declarations/${EMPLOYEE_ID}/2026-27`, { actions: [] });

    expect(screen.getByText('Module Not Subscribed')).toBeTruthy();
    expect(screen.queryByText('Officer Declaration Review')).toBeNull();
    expect(declarationService.headerOf).not.toHaveBeenCalled();
  });

  it('reads :employeeId and :fy from the route and links back to the employee page', async () => {
    declarationService.headerOf.mockResolvedValueOnce(sampleHeader);

    renderAt(`/payroll/tax-declarations/${EMPLOYEE_ID}/2026-27`);

    expect(declarationService.headerOf).toHaveBeenCalledWith(EMPLOYEE_ID, '2026-27');
    await waitFor(() => {
      expect(screen.getByText(EMPLOYEE_ID)).toBeTruthy();
      expect(screen.getByText('SUBMITTED')).toBeTruthy();
      expect(screen.getByText('New Regime (Sec 115BAC)')).toBeTruthy();
      expect(screen.getByText('Locked')).toBeTruthy();
    });
    expect(screen.getByTestId('officer-employee-link').getAttribute('href')).toBe(`/employees/${EMPLOYEE_ID}`);
  });

  it('has no free-text employee box and never sends an id that is not a UUID', () => {
    renderAt('/payroll/tax-declarations/EMP-1001/2026-27');

    expect(screen.queryByTestId('officer-employee-input')).toBeNull();
    expect(screen.getByTestId('officer-invalid-employee')).toBeTruthy();
    expect(declarationService.headerOf).not.toHaveBeenCalled();
  });

  it('says so when the employee has no declaration (404)', async () => {
    declarationService.headerOf.mockRejectedValueOnce({ status: 404, code: 'NOT_FOUND', message: 'Not found' });

    renderAt(`/payroll/tax-declarations/${EMPLOYEE_ID}/2026-27`);

    await waitFor(() => {
      expect(screen.getByText(/No tax declaration found for this employee in FY 2026-27/i)).toBeTruthy();
    });
  });

  it('recognises a UUID', () => {
    expect(isEmployeeUuid(EMPLOYEE_ID)).toBe(true);
    expect(isEmployeeUuid('EMP-1001')).toBe(false);
    expect(isEmployeeUuid('')).toBe(false);
  });
});
