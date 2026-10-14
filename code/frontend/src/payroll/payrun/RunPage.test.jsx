import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import dayjs from 'dayjs';
import { RunPage, isPaidOnAllowed } from './RunPage.jsx';
import payrunReducer from './payrunSlice.js';
import { payrunService } from './payrunService.js';
import * as useCanModule from '@shell/screens';
import { formatMoney } from '@shared/ui/format.js';

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

vi.mock('./payrunService.js', () => ({
  payrunService: {
    get: vi.fn(),
    compute: vi.fn(),
    lock: vi.fn(),
    cancel: vi.fn(),
    approve: vi.fn(),
    pay: vi.fn(),
    employees: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
    employeeNames: vi.fn().mockResolvedValue(new Map()),
    lines: vi.fn().mockResolvedValue({ lines: [] }),
  },
}));

describe('RunPage component (W-47.2 §7)', () => {
  let store;

  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({
      reducer: {
        payrun: payrunReducer,
      },
    });
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  const renderRunPage = (runId = 'run-1') => {
    return render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[`/payroll/runs/${runId}`]}>
          <Routes>
            <Route path="/payroll/runs/:id" element={<RunPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );
  };

  it('verifies button enablement per status across all eight statuses', async () => {
    // Mirrors PayRunStatus.ALLOWED on the server; PAID has no way out (W-36.2 §13 decision 9).
    const statuses = [
      { status: 'DRAFT', canLock: true, canCompute: false, canApprove: false, canPay: false, canCancel: true },
      { status: 'LOCKED', canLock: false, canCompute: true, canApprove: false, canPay: false, canCancel: true },
      { status: 'COMPUTING', canLock: false, canCompute: false, canApprove: false, canPay: false, canCancel: false },
      { status: 'COMPUTED', canLock: false, canCompute: true, canApprove: true, canPay: false, canCancel: false },
      { status: 'FAILED', canLock: false, canCompute: true, canApprove: false, canPay: false, canCancel: false },
      { status: 'APPROVED', canLock: false, canCompute: false, canApprove: false, canPay: true, canCancel: true },
      { status: 'PAID', canLock: false, canCompute: false, canApprove: false, canPay: false, canCancel: false },
      { status: 'CANCELLED', canLock: false, canCompute: false, canApprove: false, canPay: false, canCancel: false },
    ];

    for (const s of statuses) {
      // Reset employees mock per iteration to avoid stale promise state
      payrunService.employees.mockResolvedValue({ content: [], totalElements: 0 });

      payrunService.get.mockResolvedValueOnce({
        id: `run-${s.status}`,
        period: '2026-10',
        status: s.status,
        compute_started_at: new Date().toISOString(), // fresh computing
        included_count: 5,
        skipped_count: 0,
      });

      const { unmount } = renderRunPage(`run-${s.status}`);

      await waitFor(() => {
        expect(screen.getByText(`Pay Run — Oct 2026`)).toBeDefined();
      });

      const lockBtn = screen.getByRole('button', { name: /lock/i });
      const computeBtn = screen.getByRole('button', { name: /compute/i });
      const approveBtn = screen.getByRole('button', { name: /approve/i });
      const payBtn = screen.getByRole('button', { name: /pay$/i });
      const cancelBtn = screen.getByRole('button', { name: /cancel/i });

      expect(lockBtn.hasAttribute('disabled')).toBe(!s.canLock);
      expect(computeBtn.hasAttribute('disabled')).toBe(!s.canCompute);
      expect(approveBtn.hasAttribute('disabled')).toBe(!s.canApprove);
      expect(payBtn.hasAttribute('disabled')).toBe(!s.canPay);
      expect(cancelBtn.hasAttribute('disabled')).toBe(!s.canCancel);

      unmount();
    }
  }, 40000);

  it('renders progress bar showing 3 / 10 when status is COMPUTING', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-comp',
      period: '2026-10',
      status: 'COMPUTING',
      progress_done: 3,
      progress_total: 10,
      compute_attempt: 1,
      compute_started_at: new Date().toISOString(),
    });

    renderRunPage('run-comp');

    await waitFor(() => {
      // Progress text shows 3 / 10
      expect(screen.getAllByText('3 / 10').length).toBeGreaterThan(0);
      expect(screen.getByText(/Computation in progress \(Attempt 1\)/i)).toBeDefined();
    });
  });

  it('enables Compute button during COMPUTING when stale (> 15 mins)', async () => {
    const twentyMinsAgo = new Date(Date.now() - 20 * 60 * 1000).toISOString();
    payrunService.get.mockResolvedValueOnce({
      id: 'run-stale',
      period: '2026-10',
      status: 'COMPUTING',
      progress_done: 2,
      progress_total: 10,
      compute_started_at: twentyMinsAgo,
    });

    renderRunPage('run-stale');

    await waitFor(() => {
      expect(screen.getByText('Pay Run — Oct 2026')).toBeDefined();
    });

    const computeBtn = screen.getByRole('button', { name: /compute/i });
    expect(computeBtn.hasAttribute('disabled')).toBe(false);
  });

  it('re-fetches pay run when a 409 conflict occurs on Lock', async () => {
    payrunService.get
      .mockResolvedValueOnce({
        id: 'run-lock-409',
        period: '2026-10',
        status: 'DRAFT',
      })
      .mockResolvedValueOnce({
        id: 'run-lock-409',
        period: '2026-10',
        status: 'LOCKED',
      });

    payrunService.lock.mockRejectedValueOnce({
      status: 409,
      code: 'CONFLICT',
      message: 'Pay run has already been locked by another session',
    });

    renderRunPage('run-lock-409');

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /lock/i })).toBeDefined();
    });

    const lockBtn = screen.getByRole('button', { name: /lock/i });
    fireEvent.click(lockBtn);

    // Confirm popconfirm
    const okBtn = await screen.findByRole('button', { name: 'Lock' });
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(payrunService.lock).toHaveBeenCalledWith('run-lock-409');
      // Re-fetched after 409
      expect(payrunService.get).toHaveBeenCalledTimes(2);
      expect(
        screen.getByText('Pay run has already been locked by another session')
      ).toBeDefined();
    });
  });

  it('approves a COMPUTED run and re-reads it', async () => {
    payrunService.get
      .mockResolvedValueOnce({ id: 'run-ap', period: '2026-10', status: 'COMPUTED' })
      .mockResolvedValueOnce({ id: 'run-ap', period: '2026-10', status: 'APPROVED' });
    payrunService.approve.mockResolvedValueOnce({ id: 'run-ap', status: 'APPROVED' });

    renderRunPage('run-ap');

    fireEvent.click(await screen.findByRole('button', { name: /approve/i }));
    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }));

    await waitFor(() => {
      expect(payrunService.approve).toHaveBeenCalledWith('run-ap');
      expect(payrunService.get).toHaveBeenCalledTimes(2);
    });
  });

  it('pays an APPROVED run with today as paid_on and reports the notifications', async () => {
    const today = dayjs().format('YYYY-MM-DD');
    payrunService.get
      .mockResolvedValueOnce({ id: 'run-pay', period: '2026-10', status: 'APPROVED', period_start: '2000-01-01' })
      .mockResolvedValueOnce({ id: 'run-pay', period: '2026-10', status: 'PAID', paid_on: today });
    payrunService.pay.mockResolvedValueOnce({ id: 'run-pay', status: 'PAID', paid_on: today, notified: 2 });

    renderRunPage('run-pay');

    fireEvent.click(await screen.findByRole('button', { name: /pay$/i }));
    fireEvent.click(await screen.findByRole('button', { name: 'Mark paid' }));

    await waitFor(() => {
      expect(payrunService.pay).toHaveBeenCalledWith('run-pay', today);
      expect(screen.getByText(`Paid on ${today}. 2 payslip notification(s) sent.`)).toBeDefined();
    });
  });

  it('keeps Approve and Pay disabled without their own actions, even with payroll.run.execute', async () => {
    useCanModule.useCan.mockImplementation((code) => code === 'payroll.run.execute');

    for (const status of ['COMPUTED', 'APPROVED']) {
      payrunService.get.mockResolvedValueOnce({ id: `run-perm-${status}`, period: '2026-10', status });
      const { unmount } = renderRunPage(`run-perm-${status}`);
      await screen.findByText('Pay Run — Oct 2026');

      expect(screen.getByRole('button', { name: /approve/i }).hasAttribute('disabled')).toBe(true);
      expect(screen.getByRole('button', { name: /pay$/i }).hasAttribute('disabled')).toBe(true);
      unmount();
    }
  });
});

describe('RunPage load errors and failure reason (D-83)', () => {
  let store;
  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({ reducer: { payrun: payrunReducer } });
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    payrunService.employees.mockResolvedValue({ content: [], totalElements: 0 });
  });

  const renderAt = (runId) =>
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[`/payroll/runs/${runId}`]}>
          <Routes>
            <Route path="/payroll/runs/:id" element={<RunPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

  it('shows the failure reason of a FAILED run', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-f',
      period: '2026-10',
      status: 'FAILED',
      failure_reason: 'No pay schedule for 2026-10',
    });
    renderAt('run-f');
    expect(await screen.findByText('Computation failed')).toBeDefined();
    expect(screen.getByText('No pay schedule for 2026-10')).toBeDefined();
  });

  it('reads failureReason in camel case too, and says so when there is none', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-f2',
      period: '2026-10',
      status: 'FAILED',
      failureReason: 'Worker timed out',
    });
    const { unmount } = renderAt('run-f2');
    expect(await screen.findByText('Worker timed out')).toBeDefined();
    unmount();

    payrunService.get.mockResolvedValueOnce({ id: 'run-f3', period: '2026-10', status: 'FAILED' });
    renderAt('run-f3');
    expect(await screen.findByText('The server did not record a reason.')).toBeDefined();
  });

  it('shows no failure banner for a run that has not failed', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-ok',
      period: '2026-10',
      status: 'COMPUTED',
      failure_reason: 'stale text from an earlier attempt',
    });
    renderAt('run-ok');
    await screen.findByText('Pay Run — Oct 2026');
    expect(screen.queryByText('Computation failed')).toBeNull();
    expect(screen.queryByText('stale text from an earlier attempt')).toBeNull();
  });

  it('shows run totals as exact rupee amounts', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-t',
      period: '2026-10',
      status: 'COMPUTED',
      total_gross: '12345678901234567.89',
      total_deductions: '1000.5',
      total_net_pay: null,
    });
    const { container } = renderAt('run-t');
    await screen.findByText('Total Gross');
    const values = [...container.querySelectorAll('.ant-statistic-content')].map((n) => n.textContent);
    expect(values).toEqual([formatMoney('12345678901234567.89'), '₹ 1,000.50', '—']);
    // The amount is the decimal the server sent, not a float that lost digits.
    expect(values[0]).toContain('67.89');
  });

  it('a 404 reads "Pay run not found" with no Retry', async () => {
    payrunService.get.mockRejectedValueOnce({ status: 404, code: 'NOT_FOUND', message: 'Pay run x not found' });
    renderAt('run-404');
    expect(await screen.findByText('Pay run not found')).toBeDefined();
    expect(screen.queryByRole('button', { name: /retry/i })).toBeNull();
  });

  it.each([
    ['a 500', { status: 500, code: 'INTERNAL', message: 'Database unavailable' }],
    ['a 403', { status: 403, code: 'FORBIDDEN', message: 'You do not have access to pay runs' }],
    ['a network error', { code: 'INTERNAL', message: 'Something went wrong' }],
  ])('%s shows the server message and a Retry that reloads', async (_n, err) => {
    payrunService.get
      .mockRejectedValueOnce(err)
      .mockResolvedValueOnce({ id: 'run-e', period: '2026-10', status: 'DRAFT' });
    renderAt('run-e');
    expect(await screen.findByText('Could not load the pay run')).toBeDefined();
    expect(screen.queryByText('Pay run not found')).toBeNull();
    expect(screen.getByText(err.message)).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByText('Pay Run — Oct 2026')).toBeDefined();
    expect(payrunService.get).toHaveBeenCalledTimes(2);
  });
});

describe('RunPage for a finance login (D-82)', () => {
  let store;
  beforeEach(() => {
    vi.clearAllMocks();
    store = configureStore({ reducer: { payrun: payrunReducer } });
    // Finance: payroll.run.read and payroll.run.approve, no payroll.run.execute, no core.employee.read.
    vi.spyOn(useCanModule, 'useCan').mockImplementation(
      (a) => a === 'payroll.run.read' || a === 'payroll.run.approve'
    );
  });

  it('names each employee from the run reply, without asking core', async () => {
    payrunService.get.mockResolvedValueOnce({
      id: 'run-fin',
      period: '2026-10',
      status: 'APPROVED',
      approved_at: '2026-10-28T10:00:00Z',
      approved_by: 'a1b2',
      approved_by_name: 'Fiona Finance',
    });
    payrunService.employees.mockResolvedValue({
      content: [
        { id: 'r1', employee_id: 'e1', employee_number: 'E01', employee_name: 'Alice Smith', inclusion_status: 'INCLUDED' },
        { id: 'r2', employee_id: 'e2', employee_number: 'E02', employee_name: 'Bob Jones', inclusion_status: 'INCLUDED' },
      ],
      totalElements: 2,
    });
    payrunService.employeeNames.mockRejectedValue({ status: 403, code: 'FORBIDDEN' });

    renderAt('run-fin');
    expect(await screen.findByText('Alice Smith')).toBeDefined();
    expect(screen.getByText('Bob Jones')).toBeDefined();
    expect(screen.getByText(/by Fiona Finance/)).toBeDefined();
    expect(payrunService.employeeNames).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /lock/i }).hasAttribute('disabled')).toBe(true);
    expect(screen.getByRole('button', { name: /cancel/i }).hasAttribute('disabled')).toBe(true);
  });

  const renderAt = (runId) =>
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={[`/payroll/runs/${runId}`]}>
          <Routes>
            <Route path="/payroll/runs/:id" element={<RunPage />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    );
});

describe('isPaidOnAllowed (W-36.2 §4)', () => {
  const run = { period_start: '2026-10-01' };

  it('allows today and any day from the period start', () => {
    expect(isPaidOnAllowed(dayjs(), { period_start: '2000-01-01' })).toBe(true);
    expect(isPaidOnAllowed(dayjs('2026-10-01'), run)).toBe(true);
  });

  it('refuses a future day, a day before the period, and no day', () => {
    expect(isPaidOnAllowed(dayjs().add(1, 'day'), { period_start: '2000-01-01' })).toBe(false);
    expect(isPaidOnAllowed(dayjs('2026-09-30'), run)).toBe(false);
    expect(isPaidOnAllowed(null, run)).toBe(false);
  });
});
