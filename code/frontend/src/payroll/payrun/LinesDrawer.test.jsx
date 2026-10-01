import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { LinesDrawer } from './LinesDrawer.jsx';
import { payrunService } from './payrunService.js';

vi.mock('./payrunService.js', () => ({
  payrunService: {
    lines: vi.fn(),
  },
}));

describe('LinesDrawer component (W-47.2 §7)', () => {
  const mockLines = [
    {
      id: 'l1',
      line_kind: 'EARNING',
      component_code: 'BASIC',
      component_name: 'Basic Pay',
      amount: 50000.0,
      sort_order: 10,
    },
    {
      id: 'l2',
      line_kind: 'EARNING',
      component_code: 'HRA',
      component_name: 'House Rent Allowance',
      amount: 20000.0,
      sort_order: 20,
    },
    {
      id: 'l3',
      line_kind: 'DEDUCTION',
      component_code: 'PF_EMP',
      component_name: 'Provident Fund (Employee)',
      amount: 1800.0,
      sort_order: 30,
    },
    {
      id: 'l4',
      line_kind: 'BENEFIT',
      component_code: 'PF_EMPR',
      component_name: 'Provident Fund (Employer)',
      amount: 1800.0,
      sort_order: 40,
    },
    {
      id: 'l5',
      line_kind: 'REIMBURSEMENT',
      component_code: 'MED_REIMB',
      component_name: 'Medical Reimbursement',
      amount: 2500.0,
      sort_order: 50,
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders all four sections by line_kind', async () => {
    payrunService.lines.mockResolvedValueOnce({
      employee_id: 'emp-1',
      computation_error: null,
      lines: mockLines,
    });

    render(
      <LinesDrawer
        open={true}
        onClose={vi.fn()}
        payrunId="run-1"
        employee={{ employee_id: 'emp-1', employee_number: 'EMP001', employee_name: 'Asha Sharma' }}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Earnings')).toBeDefined();
      expect(screen.getByText('Deductions')).toBeDefined();
      expect(screen.getByText('Benefits')).toBeDefined();
      expect(screen.getByText('Reimbursements')).toBeDefined();
    });

    // Verify individual line details
    expect(screen.getByText('BASIC')).toBeDefined();
    expect(screen.getByText('Basic Pay')).toBeDefined();
    expect(screen.getByText('50000.00')).toBeDefined();

    expect(screen.getByText('HRA')).toBeDefined();
    expect(screen.getByText('PF_EMP')).toBeDefined();
    expect(screen.getByText('PF_EMPR')).toBeDefined();
    expect(screen.getByText('MED_REIMB')).toBeDefined();
  });

  it('renders computation_error Alert at the top when present', async () => {
    payrunService.lines.mockResolvedValueOnce({
      employee_id: 'emp-2',
      computation_error: 'Loss of pay policy lookup failed for location BLR',
      lines: [],
    });

    render(
      <LinesDrawer
        open={true}
        onClose={vi.fn()}
        payrunId="run-1"
        employee={{ employee_id: 'emp-2', employee_number: 'EMP002' }}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('Computation Error')).toBeDefined();
      expect(screen.getByText('Loss of pay policy lookup failed for location BLR')).toBeDefined();
    });
  });

  it('does NOT sum totals client-side', async () => {
    payrunService.lines.mockResolvedValueOnce({
      employee_id: 'emp-1',
      computation_error: null,
      lines: mockLines,
    });

    render(
      <LinesDrawer
        open={true}
        onClose={vi.fn()}
        payrunId="run-1"
        employee={{ employee_id: 'emp-1', employee_number: 'EMP001' }}
      />
    );

    await waitFor(() => {
      expect(screen.getByText('BASIC')).toBeDefined();
    });

    // 50000 + 20000 = 70000: client must not compute or display client-side sum "70000"
    expect(screen.queryByText('70000.00')).toBeNull();
    expect(screen.queryByText('Total Earnings')).toBeNull();
  });
});
