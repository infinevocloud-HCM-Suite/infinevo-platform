import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { RunPage, inputsSinceComputeNotice } from './RunPage.jsx';
import payrunReducer from './payrunSlice.js';
import { payrunService } from './payrunService.js';
import * as useCanModule from '@shell/screens';

vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));
vi.mock('./payrunService.js', () => ({
  payrunService: {
    get: vi.fn(),
    compute: vi.fn(),
    refreshScheduledEarnings: vi.fn(),
    lock: vi.fn(),
    cancel: vi.fn(),
    approve: vi.fn(),
    pay: vi.fn(),
    export: vi.fn(),
    employees: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
    employeeNames: vi.fn().mockResolvedValue(new Map()),
    lines: vi.fn().mockResolvedValue({ lines: [] }),
  },
}));

const T = 60000;
const MESSAGE = '3 new pay inputs since this run was computed. Compute again before approving.';

const computedRun = (extra = {}) => ({
  id: 'run-1',
  period: '2026-10',
  run_type: 'REGULAR',
  status: 'COMPUTED',
  included_count: 1,
  skipped_count: 0,
  total_gross: '1000.00',
  total_deductions: '0.00',
  total_net_pay: '1000.00',
  ...extra,
});

describe('RunPage - entries since the run was computed (D-144)', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({ reducer: { payrun: payrunReducer } });
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    payrunService.employees.mockResolvedValue({ content: [], totalElements: 0 });
  });

  const renderIt = () =>
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/payroll/runs/run-1']}>
          <Routes>
            <Route path="/payroll/runs/:id" element={<RunPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

  it('words the notice as the server words its 409, and only while the count is above zero', () => {
    expect(inputsSinceComputeNotice(computedRun({ inputs_since_compute: 3 }))).toBe(MESSAGE);
    expect(inputsSinceComputeNotice(computedRun({ inputs_since_compute: 1 }))).toBe(
      '1 new pay input since this run was computed. Compute again before approving.'
    );
    expect(inputsSinceComputeNotice(computedRun({ inputs_since_compute: 0 }))).toBeNull();
    expect(inputsSinceComputeNotice(computedRun())).toBeNull();
    expect(inputsSinceComputeNotice(computedRun({ status: 'PAID', inputs_since_compute: 3 }))).toBeNull();
    expect(inputsSinceComputeNotice(null)).toBeNull();
  });

  it(
    'shows a warning banner on a computed run with new entries',
    async () => {
      payrunService.get.mockResolvedValue(computedRun({ inputs_since_compute: 3 }));
      renderIt();
      const banner = await screen.findByTestId('inputs-since-compute');
      expect(banner.textContent).toContain(MESSAGE);
      expect(banner.className).toContain('ant-alert-warning');
    },
    T
  );

  it(
    'shows no banner when nothing changed since the compute',
    async () => {
      payrunService.get.mockResolvedValue(computedRun({ inputs_since_compute: 0 }));
      renderIt();
      await waitFor(() => expect(payrunService.get).toHaveBeenCalled());
      await waitFor(() => expect(screen.getByRole('button', { name: /approve/i })).toBeTruthy());
      expect(screen.queryByTestId('inputs-since-compute')).toBeNull();
    },
    T
  );

  it(
    'a 409 from approve shows the server message and re-reads the run',
    async () => {
      payrunService.get.mockResolvedValue(computedRun({ inputs_since_compute: 3 }));
      payrunService.approve.mockRejectedValue({ status: 409, code: 'CONFLICT', message: MESSAGE });
      renderIt();
      await screen.findByTestId('inputs-since-compute');
      fireEvent.click(screen.getByRole('button', { name: /approve/i }));
      fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));
      await waitFor(() => expect(payrunService.approve).toHaveBeenCalledWith('run-1'));
      await waitFor(() => expect(payrunService.get).toHaveBeenCalledTimes(2));
      expect((await screen.findAllByText(MESSAGE)).length).toBeGreaterThan(1);
    },
    T
  );
});
