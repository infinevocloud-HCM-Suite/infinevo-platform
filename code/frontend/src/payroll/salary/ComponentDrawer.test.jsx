import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { ComponentDrawer } from './ComponentDrawer.jsx';
import { componentService } from './componentService.js';
import { EARNING_TYPE_NAMES, normaliseInitialValues, buildPayload } from './componentFields.js';
import salaryReducer from './salarySlice.js';

vi.mock('./componentService.js', () => ({
  componentService: {
    create: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

function renderDrawer(props = {}) {
  const store = configureStore({
    reducer: {
      salary: salaryReducer,
    },
  });

  const defaultProps = {
    open: true,
    onClose: vi.fn(),
    onSuccess: vi.fn(),
    kind: 'earnings',
    initialValues: null,
  };

  return render(
    <Provider store={store}>
      <ComponentDrawer {...defaultProps} {...props} />
    </Provider>
  );
}

async function chooseEarningType(name) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Earning type' }));
  fireEvent.click(await screen.findByText(name, { selector: '.ant-select-item-option-content' }));
}

function fillRequired() {
  fireEvent.change(screen.getByPlaceholderText('e.g. BASIC'), { target: { value: 'HRA' } });
  fireEvent.change(screen.getByPlaceholderText('e.g. Basic Salary'), { target: { value: 'HRA' } });
  fireEvent.change(screen.getByPlaceholderText('0.0000'), { target: { value: '12.5' } });
}

function save() {
  fireEvent.click(document.getElementById('btn-save-component'));
}

function openStatutory() {
  fireEvent.click(screen.getByText('Statutory & tax'));
}

const variableBox = () => screen.getByRole('checkbox', { name: 'Variable' });

describe('ComponentDrawer component (W-47.1a §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('percentageOf is hidden when calculationType is FLAT', async () => {
    renderDrawer({
      initialValues: {
        calculationType: 'FLAT',
      },
    });

    expect(screen.queryByText('Percentage Of')).toBeNull();
  });

  it('percentageOf is shown when calculationType is PERCENTAGE', async () => {
    renderDrawer({
      initialValues: {
        calculationType: 'PERCENTAGE',
      },
    });

    expect(screen.getByText('Percentage Of')).toBeDefined();
  });

  it('"12.5" is sent as a string on valid form submission', async () => {
    componentService.create.mockResolvedValueOnce({ id: 'comp-1' });

    renderDrawer({
      initialValues: null,
    });

    fillRequired();
    await chooseEarningType('House Rent Allowance');
    save();

    await waitFor(() => {
      expect(componentService.create).toHaveBeenCalled();
      const callPayload = componentService.create.mock.calls[0][1];
      expect(callPayload.defaultValue).toBe('12.5');
      expect(typeof callPayload.defaultValue).toBe('string');
    });
  });

  it('"1.23456" is blocked by decimal validation (> 4 decimal places)', async () => {
    renderDrawer({
      initialValues: null,
    });

    fireEvent.change(screen.getByPlaceholderText('e.g. BASIC'), { target: { value: 'HRA' } });
    fireEvent.change(screen.getByPlaceholderText('e.g. Basic Salary'), { target: { value: 'HRA' } });
    fireEvent.change(screen.getByPlaceholderText('0.0000'), { target: { value: '1.23456' } });

    save();

    await waitFor(() => {
      expect(
        screen.getByText(/must be a valid number with at most 4 decimal places/i)
      ).toBeDefined();
      expect(componentService.create).not.toHaveBeenCalled();
    });
  });
});

describe('ComponentDrawer earning type and variable (D-37)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('the earning type list is the 32 legacy names', () => {
    expect(EARNING_TYPE_NAMES).toHaveLength(32);
    expect(EARNING_TYPE_NAMES).toContain('Basic');
    expect(EARNING_TYPE_NAMES).toContain('Telephone And Internet Allowance');
    EARNING_TYPE_NAMES.forEach((name) => expect(name.length).toBeLessThanOrEqual(32));
  });

  it('renders the named list and filters it by search', async () => {
    renderDrawer();
    const combo = screen.getByRole('combobox', { name: 'Earning type' });
    fireEvent.mouseDown(combo);
    expect(
      await screen.findByText('Dearness Allowance', { selector: '.ant-select-item-option-content' })
    ).toBeDefined();

    fireEvent.change(combo, { target: { value: 'rent' } });
    await waitFor(() => {
      const shown = Array.from(document.querySelectorAll('.ant-select-item-option-content')).map(
        (n) => n.textContent
      );
      expect(shown).toEqual(['House Rent Allowance']);
    });
  });

  it('a new earning cannot be saved without a named type', async () => {
    renderDrawer();
    fillRequired();
    save();
    await waitFor(() => expect(screen.getByText('Earning type is required')).toBeDefined());
    expect(componentService.create).not.toHaveBeenCalled();
  });

  it('frequency shows only when Variable is ticked, and is sent with variable=true', async () => {
    componentService.create.mockResolvedValueOnce({ id: 'comp-1' });
    renderDrawer();

    expect(screen.queryByRole('combobox', { name: 'Frequency' })).toBeNull();
    fireEvent.click(variableBox());
    expect(screen.getByRole('combobox', { name: 'Frequency' })).toBeDefined();

    fillRequired();
    await chooseEarningType('Bonus');
    save();

    await waitFor(() => expect(componentService.create).toHaveBeenCalled());
    const payload = componentService.create.mock.calls[0][1];
    expect(payload.earningType).toBe('Bonus');
    expect(payload.variable).toBe(true);
    expect(payload.earningFrequency).toBe('MONTHLY');
  });

  it('unticking Variable hides frequency again', () => {
    renderDrawer();
    fireEvent.click(variableBox());
    fireEvent.click(variableBox());
    expect(screen.queryByRole('combobox', { name: 'Frequency' })).toBeNull();
  });

  it('an old FIXED row opens with Variable unticked and no type chosen', () => {
    renderDrawer({
      initialData: { id: 'e1', code: 'BASIC', name: 'Basic', earningType: 'FIXED', variable: false },
    });
    expect(variableBox().checked).toBe(false);
    expect(screen.queryByRole('combobox', { name: 'Frequency' })).toBeNull();
    expect(screen.getByText('Search earning types')).toBeDefined();
  });

  it('an old VARIABLE row opens with Variable ticked and its frequency', () => {
    renderDrawer({
      initialData: {
        id: 'e2',
        code: 'PERF',
        name: 'Performance',
        earningType: 'VARIABLE',
        earningFrequency: 'QUARTERLY',
      },
    });
    expect(variableBox().checked).toBe(true);
    expect(screen.getByRole('combobox', { name: 'Frequency' })).toBeDefined();
    expect(screen.getByText('Quarterly')).toBeDefined();
  });

  it('an old ONE_TIME row opens ticked and one-time', () => {
    renderDrawer({
      initialData: { id: 'e3', code: 'JOIN', name: 'Joining', earningType: 'ONE_TIME' },
    });
    expect(variableBox().checked).toBe(true);
    expect(screen.getByRole('checkbox', { name: 'One-time payment' }).checked).toBe(true);
  });

  it('a named-type row keeps its type and flags on read', () => {
    const v = normaliseInitialValues('earnings', {
      earningType: 'Basic',
      variable: false,
      includedInEpf: true,
      epfInclusionType: 'Conditional',
    });
    expect(v.earningType).toBe('Basic');
    expect(v.variable).toBe(false);
    expect(v.epfInclusionType).toBe('WHEN_PF_WAGE_BELOW_15000');
  });

  it('saving an old row sends the picked named type to update', async () => {
    componentService.update.mockResolvedValueOnce({ id: 'e1' });
    renderDrawer({
      initialData: {
        id: 'e1',
        code: 'BASIC',
        name: 'Basic',
        earningType: 'FIXED',
        defaultValue: 50,
        parentEarningId: 'p-1',
        active: true,
      },
    });
    await chooseEarningType('Basic');
    save();
    await waitFor(() => expect(componentService.update).toHaveBeenCalled());
    const [kind, id, payload] = componentService.update.mock.calls[0];
    expect(kind).toBe('earnings');
    expect(id).toBe('e1');
    expect(payload.earningType).toBe('Basic');
    expect(payload.variable).toBe(false);
    expect(payload.parentEarningId).toBe('p-1');
    expect(payload).not.toHaveProperty('active');
    expect(payload).not.toHaveProperty('id');
  });
});

describe('ComponentDrawer Statutory & tax group (D-38)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('is collapsed by default', () => {
    renderDrawer();
    expect(screen.getByText('Statutory & tax')).toBeDefined();
    expect(screen.queryByRole('radio', { name: 'Always' })).toBeNull();
  });

  it('earnings: shows the three EPF options', () => {
    renderDrawer();
    openStatutory();
    expect(screen.getByRole('radio', { name: 'Always' })).toBeDefined();
    expect(screen.getByRole('radio', { name: 'Only when PF wage is below 15,000' })).toBeDefined();
    expect(screen.getByRole('radio', { name: 'Never' }).checked).toBe(true);
  });

  it.each([
    ['Always', 'ALWAYS', true],
    ['Only when PF wage is below 15,000', 'WHEN_PF_WAGE_BELOW_15000', true],
    ['Never', 'NEVER', false],
  ])('earnings: EPF radio "%s" sends %s', async (label, value, included) => {
    componentService.create.mockResolvedValueOnce({ id: 'comp-1' });
    renderDrawer();
    fillRequired();
    await chooseEarningType('Basic');
    openStatutory();
    if (value !== 'NEVER') fireEvent.click(screen.getByRole('radio', { name: 'Always' }));
    fireEvent.click(screen.getByRole('radio', { name: label }));
    save();
    await waitFor(() => expect(componentService.create).toHaveBeenCalled());
    const payload = componentService.create.mock.calls[0][1];
    expect(payload.epfInclusionType).toBe(value);
    expect(payload.includedInEpf).toBe(included);
  });

  it('deductions: renders EMI type and interest rates', () => {
    renderDrawer({ kind: 'deductions' });
    expect(screen.queryByText('EMI Type')).toBeNull();
    openStatutory();
    expect(screen.getByText('EMI Type')).toBeDefined();
    expect(screen.getByText('Perquisite Interest Rate (%)')).toBeDefined();
    expect(screen.getByText('EMI Interest Rate (%)')).toBeDefined();
  });

  it('deductions: perquisite rate is sent', async () => {
    componentService.create.mockResolvedValueOnce({ id: 'd1' });
    renderDrawer({ kind: 'deductions' });
    fillRequired();
    openStatutory();
    fireEvent.change(screen.getByPlaceholderText('e.g. REDUCING_BALANCE'), { target: { value: 'FLAT' } });
    fireEvent.change(screen.getByPlaceholderText('Perquisite rate'), { target: { value: '8.5' } });
    save();
    await waitFor(() => expect(componentService.create).toHaveBeenCalled());
    const payload = componentService.create.mock.calls[0][1];
    expect(payload.emiType).toBe('FLAT');
    expect(payload.perquisiteInterestRate).toBe('8.5');
    expect(payload.emiInterestRate).toBeNull();
  });

  it('benefits: renders superannuation, contributions and tax exemption', async () => {
    componentService.create.mockResolvedValueOnce({ id: 'b1' });
    renderDrawer({ kind: 'benefits' });
    fillRequired();
    openStatutory();
    fireEvent.click(screen.getByRole('checkbox', { name: 'Consider this a superannuation fund' }));
    expect(screen.getByRole('checkbox', { name: 'One-time benefit' })).toBeDefined();
    expect(screen.getByRole('checkbox', { name: 'Allows employer contribution' })).toBeDefined();
    expect(screen.getByRole('checkbox', { name: 'Allows employee contribution' })).toBeDefined();
    fireEvent.change(screen.getByPlaceholderText('e.g. 80C'), { target: { value: '80CCD' } });
    save();
    await waitFor(() => expect(componentService.create).toHaveBeenCalled());
    const payload = componentService.create.mock.calls[0][1];
    expect(payload.superannuation).toBe(true);
    expect(payload.taxExemptSection).toBe('80CCD');
    expect(payload.taxExemptionSubType).toBeNull();
  });

  it('reimbursements: renders carry-forward and opt-in', async () => {
    componentService.create.mockResolvedValueOnce({ id: 'r1' });
    renderDrawer({ kind: 'reimbursements' });
    fillRequired();
    openStatutory();
    fireEvent.click(screen.getByRole('radio', { name: 'Carry forward to next period' }));
    fireEvent.click(screen.getByRole('checkbox', { name: 'Employees opt in to this reimbursement' }));
    save();
    await waitFor(() => expect(componentService.create).toHaveBeenCalled());
    const payload = componentService.create.mock.calls[0][1];
    expect(payload.carryForwardOption).toBe('CARRY_FORWARD');
    expect(payload.optIn).toBe(true);
  });

  it('a failed submit opens the group when one of its fields is in error', async () => {
    renderDrawer({ kind: 'deductions', initialValues: { perquisiteInterestRate: '1.23456' } });
    fillRequired();
    save();
    await waitFor(() => expect(screen.getByText('Perquisite Interest Rate (%)')).toBeDefined());
    expect(componentService.create).not.toHaveBeenCalled();
  });
});

describe('buildPayload', () => {
  it('sends only the fields each request record accepts', () => {
    const payload = buildPayload('reimbursements', {
      id: 'x',
      code: 'TRV',
      name: 'Travel',
      reimbursementType: 'TRAVEL',
      calculationType: 'FLAT',
      defaultValue: '1',
      maxLimit: '',
      variable: true,
      epfInclusionType: 'ALWAYS',
    });
    expect(payload).not.toHaveProperty('id');
    expect(payload).not.toHaveProperty('variable');
    expect(payload).not.toHaveProperty('epfInclusionType');
    expect(payload.maxLimit).toBeNull();
  });

  it('a non-variable earning is sent monthly and not one-time', () => {
    const payload = buildPayload('earnings', {
      earningType: 'Basic',
      calculationType: 'FLAT',
      variable: false,
      oneTime: true,
      earningFrequency: 'YEARLY',
      epfInclusionType: 'ALWAYS',
    });
    expect(payload.oneTime).toBe(false);
    expect(payload.earningFrequency).toBe('MONTHLY');
    expect(payload.includedInEpf).toBe(true);
  });

  it('W-73.6: the Scheduled checkbox sits under Variable and is sent as scheduledEarning', async () => {
    componentService.create.mockResolvedValue({ id: 'new-id' });
    renderDrawer();

    const scheduledBox = screen.getByRole('checkbox', { name: 'Scheduled' });
    expect(scheduledBox.checked).toBe(false);
    fireEvent.click(scheduledBox);
    await chooseEarningType('Bonus');
    fillRequired();
    save();

    await waitFor(() => expect(componentService.create).toHaveBeenCalledTimes(1));
    const body = componentService.create.mock.calls[0][1];
    expect(body.scheduledEarning).toBe(true);
    expect(body.variable).toBe(false);
    expect(normaliseInitialValues('earnings', { earningType: 'Bonus', scheduledEarning: true }).scheduledEarning).toBe(
      true
    );
    expect(normaliseInitialValues('earnings', { earningType: 'Bonus' }).scheduledEarning).toBe(false);
  });
});
