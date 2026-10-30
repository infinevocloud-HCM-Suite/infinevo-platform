import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter } from 'react-router-dom';
import { SalarySetupDrawer } from './SalarySetupDrawer.jsx';
import { salaryService } from './salaryService.js';
import { statutoryProfileService } from './statutoryProfileService.js';
import { statutoryService } from '../settings/statutoryService.js';
import salaryReducer from './salarySlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./salaryService.js', () => ({
  salaryService: {
    preview: vi.fn(),
    create: vi.fn(),
    revise: vi.fn(),
    update: vi.fn(),
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
    get: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

function earning(code, name, overrides = {}) {
  return {
    id: code.toLowerCase(),
    code,
    name,
    calculationType: 'FLAT',
    defaultValue: '0',
    percentageOf: null,
    includedInCtc: true,
    variable: false,
    fbpComponent: false,
    active: true,
    ...overrides,
  };
}

// The India catalogue: Basic 50% of CTC, HRA 50% of Basic, Special Allowance.
const CATALOGUE = [
  earning('BASIC', 'Basic', { calculationType: 'PERCENTAGE', defaultValue: '50.0000', percentageOf: 'CTC' }),
  earning('HRA', 'House Rent Allowance', { calculationType: 'PERCENTAGE', defaultValue: '50.0000', percentageOf: 'BASIC' }),
  earning('SPECIAL', 'Special Allowance'),
];

const PREVIEW = {
  employeeId: 'emp-1',
  annualCtc: '600000.0000',
  monthlyCtc: '50000.0000',
  earnings: [
    { componentId: 'basic', componentName: 'Basic', monthlyAmount: '25000.0000', annualAmount: '300000.0000', includedInCtc: true },
    { componentId: 'hra', componentName: 'House Rent Allowance', monthlyAmount: '12500.0000', annualAmount: '150000.0000', includedInCtc: true },
    { componentId: 'special', componentName: 'Special Allowance', monthlyAmount: '12500.0000', annualAmount: '150000.0000', includedInCtc: true },
  ],
  benefits: [],
  reimbursements: [],
  statutory: [
    { componentCode: 'EPF_EMPLOYEE', share: 'EMPLOYEE', wageBase: '15000.0000', rate: '12.0000', monthlyAmount: '1800.0000', annualAmount: '21600.0000', includedInCtc: false },
    { componentCode: 'EPF_EMPLOYER', share: 'EMPLOYER', wageBase: '15000.0000', rate: '3.6700', monthlyAmount: '550.5000', annualAmount: '6606.0000', includedInCtc: false },
  ],
  grossMonthly: '50000.0000',
  employeeDeductionsMonthly: [
    { componentCode: 'EPF_EMPLOYEE', monthlyAmount: '1800.0000', includedInCtc: false },
    { componentCode: 'PT', monthlyAmount: '200.0000', includedInCtc: false },
  ],
  employerContributionsMonthly: [{ componentCode: 'EPF_EMPLOYER', monthlyAmount: '550.5000', includedInCtc: false }],
  takeHomeMonthlyEstimate: '48000.0000',
  ptEstimated: true,
  balancingComponentId: 'special',
};

function renderDrawer(props = {}, { canManage = true } = {}) {
  vi.spyOn(useCanModule, 'useCan').mockReturnValue(canManage);
  const store = configureStore({
    reducer: { salary: salaryReducer },
    preloadedState: {
      salary: {
        components: { earnings: CATALOGUE, deductions: [], benefits: [], reimbursements: [] },
        loadedAt: Date.now(),
        loading: false,
        error: null,
      },
    },
  });
  const onClose = vi.fn();
  const onSuccess = vi.fn();
  const utils = render(
    <Provider store={store}>
      <MemoryRouter>
        <SalarySetupDrawer open employeeId="emp-1" onClose={onClose} onSuccess={onSuccess} {...props} />
      </MemoryRouter>
    </Provider>
  );
  return { ...utils, onClose, onSuccess };
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
// The debounce alone is 400 ms; under a loaded full-suite run the default 1 s waitFor is too tight.
const SLOW = { timeout: 5000 };

describe('SalarySetupDrawer (W-75.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    statutoryProfileService.get.mockResolvedValue({
      eligibleForPf: true,
      eligibleForPt: true,
      eligibleForEsi: false,
      eligibleForEps: true,
      eligibleForLwf: false,
      director: false,
      uan: '',
      pfAccountNumber: '',
      esiNumber: '',
    });
    statutoryProfileService.save.mockResolvedValue({ id: 'p1' });
    statutoryService.get.mockImplementation((kind) => Promise.resolve({ is_enabled: true, kind }));
    salaryService.preview.mockResolvedValue(PREVIEW);
    salaryService.create.mockResolvedValue({ id: 'v1' });
  });

  it('pre-fills Basic, HRA and Special from the catalogue, Special marked balancing', async () => {
    renderDrawer();

    await waitFor(() => {
      expect(screen.getByTestId('earning-row-2')).toBeDefined();
    }, SLOW);
    expect(within(screen.getByTestId('earning-row-0')).getByText('Basic (BASIC)')).toBeDefined();
    expect(within(screen.getByTestId('earning-row-0')).getByDisplayValue('50.0000')).toBeDefined();
    expect(within(screen.getByTestId('earning-row-1')).getByText('House Rent Allowance (HRA)')).toBeDefined();
    const special = within(screen.getByTestId('earning-row-2'));
    expect(special.getByText('Special Allowance (SPECIAL)')).toBeDefined();
    expect(special.getByText('Balances to CTC')).toBeDefined();
    expect(screen.queryByTestId('earning-row-3')).toBeNull();
  });

  it('typing the CTC calls preview once, after the debounce, and the summary shows the take-home', async () => {
    renderDrawer();
    await waitFor(() => expect(screen.getByTestId('earning-row-2')).toBeDefined(), SLOW);

    const ctc = document.getElementById('input-annual-ctc');
    fireEvent.change(ctc, { target: { value: '6' } });
    fireEvent.change(ctc, { target: { value: '6000' } });
    fireEvent.change(ctc, { target: { value: '600000' } });
    expect(salaryService.preview).not.toHaveBeenCalled();

    await waitFor(() => expect(salaryService.preview).toHaveBeenCalledTimes(1), SLOW);
    await sleep(600);
    expect(salaryService.preview).toHaveBeenCalledTimes(1);

    const [employeeId, body, options] = salaryService.preview.mock.calls[0];
    expect(employeeId).toBe('emp-1');
    expect(body.annualCtc).toBe('600000');
    expect(body.statutoryProfile).toMatchObject({ eligibleForPf: true, eligibleForPt: true });
    expect(options.signal).toBeDefined();

    await waitFor(() => {
      expect(screen.getByTestId('salary-take-home').textContent).toBe('₹ 48,000.00');
    }, SLOW);
    expect(screen.getByText('before TDS and loss of pay')).toBeDefined();
    expect(within(screen.getByTestId('balancing-amount')).getByText('₹ 12,500.00 / month')).toBeDefined();
    expect(document.getElementById('table-salary-preview-statutory')).toBeTruthy();
    expect(screen.getByText('Professional tax')).toBeDefined();
  });

  it('Save sends balancing: true on Special and saves the statutory profile first', async () => {
    const { onSuccess, onClose } = renderDrawer();
    await waitFor(() => expect(screen.getByTestId('earning-row-2')).toBeDefined(), SLOW);

    fireEvent.change(document.getElementById('input-annual-ctc'), { target: { value: '600000' } });
    const save = document.getElementById('btn-salary-version-submit');
    expect(save.disabled).toBe(true);
    await waitFor(() => expect(screen.getByTestId('salary-take-home')).toBeDefined(), SLOW);
    await waitFor(() => expect(document.getElementById('btn-salary-version-submit').disabled).toBe(false), SLOW);

    fireEvent.click(document.getElementById('btn-salary-version-submit'));

    await waitFor(() => expect(salaryService.create).toHaveBeenCalledTimes(1), SLOW);
    expect(statutoryProfileService.save).toHaveBeenCalledTimes(1);
    expect(statutoryProfileService.save.mock.invocationCallOrder[0]).toBeLessThan(
      salaryService.create.mock.invocationCallOrder[0]
    );

    const [, payload] = salaryService.create.mock.calls[0];
    expect(payload.annualCtc).toBe('600000');
    expect(payload.earnings).toHaveLength(3);
    expect(payload.earnings[2]).toMatchObject({ componentId: 'special', calculationType: 'FLAT', balancing: true });
    expect(payload.earnings[0]).toMatchObject({
      componentId: 'basic',
      calculationType: 'PERCENTAGE',
      value: '50.0000',
      percentageOf: 'CTC',
      balancing: false,
    });
    expect(payload.earnings[1]).toMatchObject({ componentId: 'hra', percentageOf: 'BASIC', balancing: false });
    expect(payload).not.toHaveProperty('statutoryProfile');

    const [, profile] = statutoryProfileService.save.mock.calls[0];
    expect(Object.keys(profile)).toHaveLength(10);
    expect(profile).toMatchObject({ eligibleForPf: true, eligibleForEps: true, eligibleForPt: true });

    await waitFor(() => expect(onSuccess).toHaveBeenCalled(), SLOW);
    expect(onClose).toHaveBeenCalled();
  });

  it('keeps the drawer open with the server message when the salary save fails after the profile saved', async () => {
    salaryService.create.mockRejectedValueOnce({
      code: 'VALIDATION_FAILED',
      message: 'Basic salary component is required in CTC structure',
      fieldErrors: {},
    });
    const { onClose } = renderDrawer();
    await waitFor(() => expect(screen.getByTestId('earning-row-2')).toBeDefined(), SLOW);
    fireEvent.change(document.getElementById('input-annual-ctc'), { target: { value: '600000' } });
    await waitFor(() => expect(document.getElementById('btn-salary-version-submit').disabled).toBe(false), SLOW);

    fireEvent.click(document.getElementById('btn-salary-version-submit'));

    await waitFor(() => {
      expect(screen.getByText('Basic salary component is required in CTC structure')).toBeDefined();
    }, SLOW);
    expect(statutoryProfileService.save).toHaveBeenCalledTimes(1);
    expect(onClose).not.toHaveBeenCalled();
  });

  it('shows a negative-balance preview error under CTC and disables Save', async () => {
    salaryService.preview.mockRejectedValue({
      code: 'VALIDATION_FAILED',
      message: 'Validation failed',
      fieldErrors: { annualCtc: 'Components and employer contributions exceed the CTC by 1200.00' },
    });
    renderDrawer();
    await waitFor(() => expect(screen.getByTestId('earning-row-2')).toBeDefined(), SLOW);

    fireEvent.change(document.getElementById('input-annual-ctc'), { target: { value: '100000' } });

    await waitFor(() => {
      expect(screen.getByText('Components and employer contributions exceed the CTC by 1200.00')).toBeDefined();
    }, SLOW);
    expect(document.getElementById('btn-salary-version-submit').disabled).toBe(true);
  });

  it('refuses an 11-digit UAN under the field', async () => {
    renderDrawer();
    await waitFor(() => expect(screen.getByTestId('earning-row-2')).toBeDefined(), SLOW);

    fireEvent.change(document.getElementById('input-uan'), { target: { value: '10012345678' } });

    await waitFor(() => expect(screen.getByText('UAN must be exactly 12 digits')).toBeDefined(), SLOW);
    expect(document.getElementById('input-uan').getAttribute('maxlength')).toBe('16');
  });

  it('alerts when EPF is switched off for the company', async () => {
    statutoryService.get.mockImplementation((kind) => Promise.resolve({ is_enabled: kind !== 'epf' }));
    renderDrawer();

    await waitFor(() => expect(screen.getByText('EPF is switched off for the company')).toBeDefined(), SLOW);
    expect(screen.queryByText('ESI is switched off for the company')).toBeNull();
    expect(screen.getByRole('link', { name: /Settings/ }).getAttribute('href')).toBe('/payroll/settings/epf');
  });

  it('finance (no payroll.salary.manage) sees no Save and calls no preview', async () => {
    renderDrawer({}, { canManage: false });
    await waitFor(() => expect(screen.getByTestId('earning-row-2')).toBeDefined(), SLOW);

    expect(document.getElementById('btn-salary-version-submit')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
    await sleep(500);
    expect(salaryService.preview).not.toHaveBeenCalled();
  });
});
