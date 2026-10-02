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

describe('FbpDeclarationTab (W-47.1b §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fbpService.declaration.mockResolvedValue({
      pool_annual: 100000,
      declared_annual: 40000,
      unallocated_annual: 60000,
      lines: [
        {
          component_id: 'comp-fuel-1',
          kind: 'EARNING',
          name: 'Fuel Allowance',
          code: 'FUEL_ALLOW',
          max_limit: 50000,
          annual_amount: 30000,
        },
        {
          component_id: 'comp-tele-1',
          kind: 'REIMBURSEMENT',
          name: 'Telephone Reimbursement',
          code: 'TELE_REIMB',
          max_limit: 20000,
          annual_amount: 10000,
        },
      ],
    });
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

  it('save PUTs lines[] of {kind, component_id, annual_amount}', async () => {
    fbpService.setDeclaration.mockResolvedValue({
      pool_annual: 100000,
      declared_annual: 45000,
      unallocated_annual: 55000,
      lines: [],
    });

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
      component_id: 'comp-fuel-1',
      annual_amount: 30000,
    });
    expect(payload.lines[1]).toEqual({
      kind: 'REIMBURSEMENT',
      component_id: 'comp-tele-1',
      annual_amount: 10000,
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
