import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';
import { SalaryTab } from './SalaryTab.jsx';
import { salaryService } from './salaryService.js';
import { statutoryProfileService } from './statutoryProfileService.js';
import salaryReducer from './salarySlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./salaryService.js', () => ({
  salaryService: {
    asOf: vi.fn(),
    versions: vi.fn(),
    create: vi.fn(),
    revise: vi.fn(),
    update: vi.fn(),
    cancel: vi.fn(),
  },
}));

vi.mock('./statutoryProfileService.js', () => ({
  statutoryProfileService: {
    get: vi.fn(),
    save: vi.fn(),
  },
}));

vi.mock('./scheduledEarningService.js', () => ({
  scheduledEarningService: {
    list: vi.fn().mockResolvedValue([]),
    create: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
    cancel: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

function renderTab(employeeId = 'emp-1') {
  const store = configureStore({
    reducer: {
      salary: salaryReducer,
    },
    preloadedState: {
      salary: {
        components: {
          earnings: [{ id: 'e1', name: 'Basic', code: 'BASIC' }],
          deductions: [],
          benefits: [],
          reimbursements: [],
        },
        loadedAt: Date.now(),
      },
    },
  });

  vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);

  return render(
    <Provider store={store}>
      <MemoryRouter>
        <SalaryTab employeeId={employeeId} />
      </MemoryRouter>
    </Provider>
  );
}

describe('SalaryTab component (W-47.1a §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    statutoryProfileService.get.mockResolvedValue(null);
  });

  it('no version shows "Create structure" button', async () => {
    salaryService.asOf.mockResolvedValueOnce(null);
    salaryService.versions.mockResolvedValueOnce([]);

    renderTab();

    await waitFor(() => {
      expect(document.getElementById('btn-create-salary-structure')).toBeTruthy();
    });
  });

  it('a version shows "Revise" button', async () => {
    salaryService.asOf.mockResolvedValueOnce({
      id: 'v1',
      effectiveFrom: '2026-10-01',
      annualCtc: '600000',
      monthlyCtc: '50000',
      earnings: [
        {
          componentId: 'e1',
          componentCode: 'BASIC',
          componentName: 'Basic Salary',
          calculationType: 'FLAT',
          value: '50000',
          monthlyAmount: '50000',
          annualAmount: '600000',
          enabled: true,
        },
      ],
    });
    salaryService.versions.mockResolvedValueOnce([
      {
        id: 'v1',
        effectiveFrom: '2026-10-01',
        annualCtc: '600000',
        monthlyCtc: '50000',
        cancelled: false,
      },
    ]);

    renderTab();

    await waitFor(() => {
      expect(document.getElementById('btn-revise-salary-structure')).toBeTruthy();
      expect(screen.getAllByText('600000').length).toBeGreaterThan(0);
      expect(screen.getAllByText('50000').length).toBeGreaterThan(0);
    });
  });

  it('after save the displayed monthly CTC is the mocked server value, not a client sum', async () => {
    salaryService.asOf.mockResolvedValueOnce(null);
    salaryService.versions.mockResolvedValueOnce([]);

    renderTab();

    await waitFor(() => {
      expect(document.getElementById('btn-create-salary-structure')).toBeTruthy();
    });

    // Open create modal
    const createBtn = document.getElementById('btn-create-salary-structure');
    fireEvent.click(createBtn);

    // Fill form
    const ctcInput = document.getElementById('input-annual-ctc');
    expect(ctcInput).toBeTruthy();
    fireEvent.change(ctcInput, { target: { value: '600000' } });

    // Mock create response and subsequent reload response with server-computed monthlyCtc
    salaryService.create.mockResolvedValueOnce({ id: 'v1' });
    salaryService.asOf.mockResolvedValueOnce({
      id: 'v1',
      effectiveFrom: '2026-10-01',
      annualCtc: '600000',
      monthlyCtc: '50000', // server-calculated
      earnings: [],
    });
    salaryService.versions.mockResolvedValueOnce([
      { id: 'v1', effectiveFrom: '2026-10-01', annualCtc: '600000', monthlyCtc: '50000' },
    ]);

    // Submit form modal
    const submitBtn = document.getElementById('btn-salary-version-submit');
    expect(submitBtn).toBeTruthy();
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(salaryService.create).toHaveBeenCalled();
      expect(screen.getAllByText('50000').length).toBeGreaterThan(0);
    });
  });

  it('a 400 error message from SalaryComponentValidator renders', async () => {
    salaryService.asOf.mockResolvedValueOnce(null);
    salaryService.versions.mockResolvedValueOnce([]);

    renderTab();

    await waitFor(() => {
      expect(document.getElementById('btn-create-salary-structure')).toBeTruthy();
    });

    const createBtn = document.getElementById('btn-create-salary-structure');
    fireEvent.click(createBtn);

    const ctcInput = document.getElementById('input-annual-ctc');
    fireEvent.change(ctcInput, { target: { value: '600000' } });

    salaryService.create.mockRejectedValueOnce({
      code: 'VALIDATION_FAILED',
      message: 'Basic salary component is required in CTC structure',
    });

    const submitBtn = document.getElementById('btn-salary-version-submit');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(
        screen.getByText('Basic salary component is required in CTC structure')
      ).toBeDefined();
    });
  });
});
