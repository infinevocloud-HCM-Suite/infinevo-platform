import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
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
    preview: vi.fn(),
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

vi.mock('../settings/statutoryService.js', () => ({
  statutoryService: {
    get: vi.fn().mockResolvedValue({ is_enabled: true }),
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

const DEFAULT_EARNINGS = [{ id: 'e1', name: 'Basic', code: 'BASIC' }];

function renderTab(
  employeeId = 'emp-1',
  { canManage = true, earnings = DEFAULT_EARNINGS, benefits = [], reimbursements = [] } = {}
) {
  const store = configureStore({
    reducer: {
      salary: salaryReducer,
    },
    preloadedState: {
      salary: {
        components: {
          earnings,
          deductions: [],
          benefits,
          reimbursements,
        },
        loadedAt: Date.now(),
      },
    },
  });

  vi.spyOn(useCanModule, 'useCan').mockImplementation(
    (action) => action === 'payroll.salary.read' || canManage
  );

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
    // The server answers a default profile when none is stored (EmployeeStatutoryProfileServiceImpl.get).
    statutoryProfileService.get.mockResolvedValue({
      eligibleForPf: false,
      eligibleForEps: false,
      contributesEpsOnHigherWages: false,
      eligibleForPt: false,
      eligibleForEsi: false,
      eligibleForLwf: false,
      director: false,
      pfAccountNumber: null,
      uan: null,
      esiNumber: null,
    });
    statutoryProfileService.save.mockResolvedValue({ id: 'p1' });
    salaryService.preview.mockResolvedValue({
      earnings: [],
      benefits: [],
      reimbursements: [],
      statutory: [],
      grossMonthly: '0.0000',
      employeeDeductionsMonthly: [],
      takeHomeMonthlyEstimate: '0.0000',
      ptEstimated: true,
    });
  });

  it('no version shows the "Set up salary" button', async () => {
    salaryService.asOf.mockResolvedValueOnce(null);
    salaryService.versions.mockResolvedValueOnce([]);

    renderTab();

    await waitFor(() => {
      expect(document.getElementById('btn-create-salary-structure')).toBeTruthy();
    });
    expect(document.getElementById('btn-create-salary-structure').textContent).toContain('Set up salary');
  });

  it('finance (read only) sees no "Set up salary" or "Revise" button', async () => {
    salaryService.asOf.mockResolvedValueOnce(null);
    salaryService.versions.mockResolvedValueOnce([]);

    renderTab('emp-1', { canManage: false });

    await waitFor(() => {
      expect(screen.getByText('No salary structure active as of this date')).toBeDefined();
    });
    expect(document.getElementById('btn-create-salary-structure')).toBeNull();
    expect(document.getElementById('btn-create-salary-top')).toBeNull();
    expect(document.getElementById('btn-revise-salary-structure')).toBeNull();
  });

  it('the active version shows its statutory contributions table (W-75.2, D-130)', async () => {
    salaryService.asOf.mockResolvedValueOnce({
      id: 'v1',
      effectiveFrom: '2026-10-01',
      annualCtc: '600000',
      monthlyCtc: '50000',
      earnings: [],
      statutory: [
        {
          id: 's1',
          componentCode: 'EPF_EMPLOYER',
          share: 'EMPLOYER',
          wageBase: '15000.0000',
          rate: '3.6700',
          monthlyAmount: '550.5000',
          annualAmount: '6606.0000',
          includedInCtc: true,
        },
      ],
    });
    salaryService.versions.mockResolvedValueOnce([{ id: 'v1', effectiveFrom: '2026-10-01', cancelled: false }]);

    renderTab();

    await waitFor(() => {
      expect(document.getElementById('table-salary-statutory')).toBeTruthy();
    });
    expect(screen.getByText('Employer PF')).toBeDefined();
    expect(screen.getByText('3.67% of ₹ 15,000.00')).toBeDefined();
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

  it('the structure list shows the component type from the catalogue, "—" for an unknown component (W-117)', async () => {
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
        {
          componentId: 'e2',
          componentCode: 'RETENTION',
          componentName: 'Retention Bonus',
          calculationType: 'FLAT',
          value: '1000',
          monthlyAmount: '1000',
          annualAmount: '12000',
          enabled: true,
        },
        {
          componentId: 'gone',
          componentCode: 'OLD_ALLOW',
          componentName: 'Old Allowance',
          calculationType: 'FLAT',
          value: '500',
          monthlyAmount: '500',
          annualAmount: '6000',
          enabled: true,
        },
      ],
      benefits: [
        {
          componentId: 'b1',
          componentCode: 'GRATUITY',
          componentName: 'Gratuity',
          calculationType: 'FLAT',
          value: '2000',
          monthlyAmount: '2000',
          annualAmount: '24000',
          enabled: true,
        },
      ],
      reimbursements: [
        {
          componentId: 'r1',
          componentCode: 'FUEL',
          componentName: 'Fuel',
          calculationType: 'FLAT',
          value: '1500',
          monthlyAmount: '1500',
          annualAmount: '18000',
          enabled: true,
        },
      ],
    });
    salaryService.versions.mockResolvedValueOnce([{ id: 'v1', effectiveFrom: '2026-10-01', cancelled: false }]);

    renderTab('emp-1', {
      earnings: [
        { id: 'e1', name: 'Basic', code: 'BASIC' },
        { id: 'e2', name: 'Retention Bonus', code: 'RETENTION', scheduledEarning: true },
      ],
      benefits: [{ id: 'b1', name: 'Gratuity', code: 'GRATUITY' }],
      reimbursements: [{ id: 'r1', name: 'Fuel', code: 'FUEL', fbpComponent: true }],
    });

    await waitFor(() => {
      expect(screen.getByText('Retention Bonus')).toBeDefined();
    });
    expect(screen.getAllByRole('columnheader', { name: 'Component type' }).length).toBeGreaterThan(0);
    expect(screen.getAllByRole('columnheader', { name: 'Calculation' }).length).toBeGreaterThan(0);
    expect(screen.queryByRole('columnheader', { name: 'Code' })).toBeNull();

    const rowOf = (name) => screen.getByText(name).closest('tr');
    expect(within(rowOf('Basic Salary')).getByTestId('component-type').textContent).toBe('Fixed');
    expect(within(rowOf('Retention Bonus')).getByTestId('component-type').textContent).toBe('Scheduled');
    const unknown = rowOf('Old Allowance');
    expect(within(unknown).queryByTestId('component-type')).toBeNull();
    expect(unknown.querySelector('td').textContent).toBe('—');
    expect(screen.queryByText('OLD_ALLOW')).toBeNull();
    // A benefit or reimbursement is looked up in its own catalogue, not the earnings one.
    expect(within(rowOf('Gratuity')).getByTestId('component-type').textContent).toBe('Benefit');
    expect(within(rowOf('Fuel')).getByTestId('component-type').textContent).toBe('FBP');
  });

  it('after save the displayed monthly CTC is the mocked server value, not a client sum', async () => {
    salaryService.asOf.mockResolvedValueOnce(null);
    salaryService.versions.mockResolvedValueOnce([]);

    renderTab();

    await waitFor(() => {
      expect(document.getElementById('btn-create-salary-structure')).toBeTruthy();
    });

    // Open the setup drawer
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

    // Save once the preview has settled
    // The drawer's preview is debounced 400 ms; allow for a loaded full-suite run.
    await waitFor(
      () => {
        expect(document.getElementById('btn-salary-version-submit').disabled).toBe(false);
      },
      { timeout: 5000 }
    );
    fireEvent.click(document.getElementById('btn-salary-version-submit'));

    await waitFor(() => {
      expect(statutoryProfileService.save).toHaveBeenCalled();
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

    // The drawer's preview is debounced 400 ms; allow for a loaded full-suite run.
    await waitFor(
      () => {
        expect(document.getElementById('btn-salary-version-submit').disabled).toBe(false);
      },
      { timeout: 5000 }
    );
    fireEvent.click(document.getElementById('btn-salary-version-submit'));

    await waitFor(() => {
      expect(
        screen.getByText('Basic salary component is required in CTC structure')
      ).toBeDefined();
    });
  });
});
