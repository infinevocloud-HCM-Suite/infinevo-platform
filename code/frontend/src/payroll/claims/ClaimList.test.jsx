import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { ClaimList } from './ClaimList.jsx';
import { claimService } from './claimService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./claimService.js', () => ({
  claimService: {
    list: vi.fn(),
    searchEmployees: vi.fn(),
  },
}));

const ROW = {
  id: 'claim-1',
  employee_id: 'emp-1',
  employee_name: 'Asha Rao',
  component_name: 'Fuel',
  bill_date: '2026-09-20',
  requested_amount: 2000,
  approved_amount: 1500,
  status: 'APPROVED',
  posted_period: '2026-10',
};

const renderList = () =>
  render(
    <MemoryRouter initialEntries={['/payroll/claims']}>
      <Routes>
        <Route path="/payroll/claims" element={<ClaimList />} />
        <Route path="/payroll/claims/:id" element={<div>detail page</div>} />
      </Routes>
    </MemoryRouter>
  );

describe('ClaimList (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimService.list.mockResolvedValue({ content: [ROW], totalElements: 1 });
    claimService.searchEmployees.mockResolvedValue([
      { id: 'emp-1', employeeNumber: 'E01', firstName: 'Asha', lastName: 'Rao' },
    ]);
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('hides the employee filter from a role that cannot read the directory (D-82, finance)', async () => {
    useCanModule.useCan.mockImplementation((a) => a !== 'core.employee.read');
    renderList();
    expect(await screen.findByText('Asha Rao')).toBeDefined();
    expect(screen.queryAllByLabelText('Employee filter')).toHaveLength(0);
    expect(claimService.searchEmployees).not.toHaveBeenCalled();
    expect(screen.getAllByLabelText('Status filter').length).toBeGreaterThan(0);
    expect(useCanModule.useCan).toHaveBeenCalledWith('core.employee.read');
  });

  it('shows the employee filter to a role that can read the directory', async () => {
    renderList();
    await screen.findByText('Asha Rao');
    expect(screen.getAllByLabelText('Employee filter').length).toBeGreaterThan(0);
    await waitFor(() => expect(claimService.searchEmployees).toHaveBeenCalled());
  });

  it('shows the employee name, not an id, and amounts as sent', async () => {
    renderList();
    expect(await screen.findByText('Asha Rao')).toBeDefined();
    expect(screen.queryByText('emp-1')).toBeNull();
    expect(screen.getByText('₹ 2,000.00')).toBeDefined();
    expect(screen.getByText('₹ 1,500.00')).toBeDefined();
    expect(screen.getByText('Oct 2026')).toBeDefined();
  });

  it('filters become query params', async () => {
    renderList();
    await screen.findByText('Asha Rao');
    expect(claimService.list).toHaveBeenCalledWith({
      employeeId: undefined,
      status: undefined,
      from: undefined,
      to: undefined,
      page: 0,
      size: 25,
    });

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Status filter' }));
    fireEvent.click(await screen.findByText('Submitted', { selector: '.ant-select-item-option-content' }));
    await waitFor(() =>
      expect(claimService.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'SUBMITTED', page: 0 }))
    );

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Employee filter' }));
    fireEvent.click(await screen.findByText('E01 — Asha Rao', { selector: '.ant-select-item-option-content' }));
    await waitFor(() =>
      expect(claimService.list).toHaveBeenLastCalledWith(
        expect.objectContaining({ employeeId: 'emp-1', status: 'SUBMITTED' })
      )
    );

    const [start, end] = screen.getAllByRole('textbox').filter((el) => el.closest('.ant-picker-range'));
    fireEvent.mouseDown(start);
    fireEvent.focus(start);
    fireEvent.change(start, { target: { value: '2026-09-01' } });
    fireEvent.keyDown(start, { key: 'Enter', code: 'Enter' });
    fireEvent.mouseDown(end);
    fireEvent.focus(end);
    fireEvent.change(end, { target: { value: '2026-09-30' } });
    fireEvent.keyDown(end, { key: 'Enter', code: 'Enter' });
    await waitFor(() =>
      expect(claimService.list).toHaveBeenLastCalledWith({
        employeeId: 'emp-1',
        status: 'SUBMITTED',
        from: '2026-09-01',
        to: '2026-09-30',
        page: 0,
        size: 25,
      })
    );
  });

  it('a row opens the detail', async () => {
    renderList();
    fireEvent.click(await screen.findByText('Asha Rao'));
    expect(await screen.findByText('detail page')).toBeDefined();
  });

  it('a load error shows Retry and no rows (no mock data)', async () => {
    claimService.list.mockRejectedValueOnce({ status: 503, message: 'Backend unreachable' });
    renderList();

    expect(await screen.findByText('Backend unreachable')).toBeDefined();
    expect(screen.queryByText('Asha Rao')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Asha Rao')).toBeDefined();
  });
});
