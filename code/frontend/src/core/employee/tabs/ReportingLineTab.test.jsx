import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ReportingLineTab } from './ReportingLineTab.jsx';
import { reportingLineService } from '../reportingLineService.js';
import { employeeService } from '../employeeService.js';
import * as useCanModule from '@shell/screens';

vi.mock('../reportingLineService.js', () => ({
  reportingLineService: {
    lines: vi.fn(),
    managerChain: vi.fn(),
    set: vi.fn(),
  },
}));

vi.mock('../employeeService.js', () => ({
  employeeService: {
    list: vi.fn(),
  },
}));

import { errorMsg } from '../../../shared/ui/msgHelper.js';

vi.mock('../../../shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('ReportingLineTab component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    reportingLineService.lines.mockResolvedValue([
      {
        id: 'line-1',
        managerId: 'mgr-1',
        managerName: 'Vikram Mehta',
        kind: 'PRIMARY',
        effectiveFrom: '2026-04-01',
      },
    ]);
    reportingLineService.managerChain.mockResolvedValue([
      { id: 'chain-1', managerName: 'Vikram Mehta' },
    ]);
    employeeService.list.mockResolvedValue({
      content: [{ id: 'mgr-2', employeeNumber: 'E-002', firstName: 'Priya', lastName: 'Nair' }],
    });
  });

  it('renders manager chain and reporting lines table', async () => {
    render(<ReportingLineTab employeeId="emp-1" />);

    await waitFor(() => {
      expect(screen.getAllByText('Vikram Mehta').length).toBeGreaterThan(0);
      expect(screen.getByText('PRIMARY')).toBeDefined();
    });
  });

  it('shows error envelope when set primary manager returns 409 cycle conflict', async () => {
    const cycleError = {
      code: 'CONFLICT',
      message: 'Cycle detected: an employee cannot report to themselves or their reports',
      status: 409,
    };
    reportingLineService.set.mockRejectedValueOnce(cycleError);

    render(<ReportingLineTab employeeId="emp-1" />);

    // Open modal
    const setBtn = await screen.findByRole('button', { name: /set primary manager/i });
    fireEvent.click(setBtn);

    await waitFor(() => {
      expect(screen.getByText('Set Reporting Manager')).toBeDefined();
    });

    // Select manager from dropdown
    const selectManager = document.querySelector('#select-manager .ant-select-selector') || document.getElementById('select-manager');
    fireEvent.mouseDown(selectManager);

    await waitFor(() => {
      const option = screen.getByText('E-002 - Priya Nair');
      expect(option).toBeDefined();
      fireEvent.click(option);
    });

    // Set effective date
    const dateInput = screen.getByPlaceholderText('Select date');
    fireEvent.change(dateInput, { target: { value: '2026-05-01' } });
    fireEvent.keyDown(dateInput, { key: 'Enter', code: 'Enter' });

    // Submit
    const confirmBtn = document.getElementById('btn-confirm-set-manager');
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(reportingLineService.set).toHaveBeenCalledWith(
        'emp-1',
        expect.objectContaining({
          managerId: 'mgr-2',
          kind: 'PRIMARY',
          effectiveFrom: '2026-05-01',
        })
      );
      expect(errorMsg).toHaveBeenCalledWith(cycleError);
    });
  });
});
