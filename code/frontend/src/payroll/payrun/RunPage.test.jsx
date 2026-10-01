import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { RunPage } from './RunPage.jsx';
import payrunReducer from './payrunSlice.js';
import { payrunService } from './payrunService.js';
import * as useCanModule from '@shell/screens';

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
    employees: vi.fn().mockResolvedValue({ content: [], totalElements: 0 }),
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
    const statuses = [
      { status: 'DRAFT', canLock: true, canCompute: false, canCancel: true },
      { status: 'LOCKED', canLock: false, canCompute: true, canCancel: true },
      { status: 'COMPUTING', canLock: false, canCompute: false, canCancel: false },
      { status: 'COMPUTED', canLock: false, canCompute: true, canCancel: false },
      { status: 'FAILED', canLock: false, canCompute: true, canCancel: false },
      { status: 'APPROVED', canLock: false, canCompute: false, canCancel: false },
      { status: 'PAID', canLock: false, canCompute: false, canCancel: false },
      { status: 'CANCELLED', canLock: false, canCompute: false, canCancel: false },
    ];

    for (const s of statuses) {
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
        expect(screen.getByText(`Pay Run — 2026-10`)).toBeDefined();
      });

      const lockBtn = screen.getByRole('button', { name: /lock/i });
      const computeBtn = screen.getByRole('button', { name: /compute/i });
      const cancelBtn = screen.getByRole('button', { name: /cancel/i });

      expect(lockBtn.hasAttribute('disabled')).toBe(!s.canLock);
      expect(computeBtn.hasAttribute('disabled')).toBe(!s.canCompute);
      expect(cancelBtn.hasAttribute('disabled')).toBe(!s.canCancel);

      unmount();
    }
  });

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
      expect(screen.getByText('Pay Run — 2026-10')).toBeDefined();
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
});
