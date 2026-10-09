import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { FbpDeclarationTab } from './FbpDeclarationTab.jsx';
import { fbpService } from './fbpService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./fbpService.js', () => ({
  fbpService: {
    declaration: vi.fn(),
    setDeclaration: vi.fn(),
  },
}));

function inputValue(testId) {
  const el = screen.getByTestId(testId);
  return (el.tagName === 'INPUT' ? el : el.querySelector('input')).value;
}

function renderTab(employeeId = 'emp-123', canRead = true, canManage = true) {
  vi.spyOn(useCanModule, 'useCan').mockImplementation((action) => {
    if (action === 'payroll.fbp.read') return canRead;
    if (action === 'payroll.salary.manage') return canManage;
    return false;
  });

  return render(
    <MemoryRouter initialEntries={[`/employees/${employeeId}`]}>
      <Routes>
        <Route path="/employees/:id" element={<FbpDeclarationTab />} />
      </Routes>
    </MemoryRouter>
  );
}

// Shaped as FbpDeclarationResponse (payroll/fbp) serialises it: camelCase, the pool under `summary`.
const DECLARATION = {
  ctcStructureId: 'ctc-1',
  employeeId: 'emp-123',
  windowOpen: true,
  declaredAt: '2026-04-10T09:30:00Z',
  declaredBy: 'EMPLOYEE',
  summary: { poolAnnual: 70000, declaredAnnual: 40000, unallocatedAnnual: 30000 },
  lines: [
    {
      kind: 'EARNING',
      componentId: 'comp-fuel-1',
      componentCode: 'FUEL_ALLOW',
      componentName: 'Fuel Allowance',
      lineAnnualAmount: 50000,
      declaredAnnualAmount: 30000,
      declaredMonthlyAmount: 2500,
    },
    {
      kind: 'REIMBURSEMENT',
      componentId: 'comp-tele-1',
      componentCode: 'TELE_REIMB',
      componentName: 'Telephone Reimbursement',
      lineAnnualAmount: 20000,
      declaredAnnualAmount: 10000,
      declaredMonthlyAmount: 833.33,
    },
  ],
};

describe('FbpDeclarationTab (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fbpService.declaration.mockResolvedValue(DECLARATION);
  });

  it('renders NotEntitled when user lacks payroll.fbp.read', () => {
    renderTab('emp-123', false, false);
    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
  });

  it('renders declaration lines, pool summary, and inputs', async () => {
    renderTab('emp-123', true, true);

    await waitFor(() => {
      expect(screen.getByText('Flexible Benefit Plan Declaration')).toBeDefined();
      expect(screen.getByText('Fuel Allowance')).toBeDefined();
      expect(screen.getByText('Telephone Reimbursement')).toBeDefined();
      expect(screen.getByTestId('save-fbp-declaration-button')).toBeDefined();
    });
  });

  it('D-67: shows the component names, the pool summary, each line ceiling and the declared amounts', async () => {
    renderTab('emp-123', true, true);

    await waitFor(() => {
      expect(screen.getByText('Fuel Allowance')).toBeDefined();
    });
    expect(screen.getByText('FUEL_ALLOW')).toBeDefined();
    expect(screen.getByText('₹50000')).toBeDefined();
    expect(screen.getByText('₹20000')).toBeDefined();
    // Pool, declared and unallocated come from `summary`.
    expect(screen.getByText('70,000')).toBeDefined();
    expect(screen.getByText('40,000')).toBeDefined();
    expect(screen.getByText('30,000')).toBeDefined();
    expect(inputValue('fbp-line-amount-comp-fuel-1')).toBe('30000');
    expect(inputValue('fbp-line-amount-comp-tele-1')).toBe('10000');
  });

  it('D-67: save PUTs lines[] of FbpDeclarationLineRequest {kind, componentId, annualAmount}', async () => {
    fbpService.setDeclaration.mockResolvedValue(DECLARATION);

    renderTab('emp-123', true, true);

    await waitFor(() => {
      expect(screen.getByTestId('save-fbp-declaration-button')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('save-fbp-declaration-button'));

    await waitFor(() => {
      expect(fbpService.setDeclaration).toHaveBeenCalledTimes(1);
    });

    const [empId, payload] = fbpService.setDeclaration.mock.calls[0];
    expect(empId).toBe('emp-123');
    expect(payload.lines).toBeDefined();
    expect(payload.lines.length).toBe(2);

    expect(payload.lines[0]).toEqual({
      kind: 'EARNING',
      componentId: 'comp-fuel-1',
      annualAmount: 30000,
    });
    expect(payload.lines[1]).toEqual({
      kind: 'REIMBURSEMENT',
      componentId: 'comp-tele-1',
      annualAmount: 10000,
    });
  });

  it('renders 404 message when employee has no salary version in force', async () => {
    const err404 = new Error('No salary version found in force for this date');
    err404.response = {
      status: 404,
      data: { message: 'No salary version found in force for this date' },
    };
    fbpService.declaration.mockRejectedValue(err404);

    renderTab('emp-123', true, true);

    await waitFor(() => {
      expect(screen.getByTestId('fbp-404-error')).toBeDefined();
      expect(screen.getByText('No salary version found in force for this date')).toBeDefined();
    });
  });
});
