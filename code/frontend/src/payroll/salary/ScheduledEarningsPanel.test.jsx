import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import dayjs from 'dayjs';
import {
  ScheduledEarningsPanel,
  scheduledComponents,
  buildScheduledEarningSchema,
} from './ScheduledEarningsPanel.jsx';
import { scheduledEarningService } from './scheduledEarningService.js';
import salaryReducer from './salarySlice.js';
import * as useCanModule from '@shell/screens';

vi.mock('./scheduledEarningService.js', () => ({
  scheduledEarningService: {
    list: vi.fn(),
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

const EARNINGS = [
  { id: 'e-basic', code: 'BASIC', name: 'Basic', scheduledEarning: false },
  { id: 'e-bonus', code: 'BONUS', name: 'Bonus', scheduledEarning: true },
  { id: 'e-hra', code: 'HRA', name: 'House Rent Allowance' },
];

function renderPanel({ earnings = EARNINGS, can = () => true } = {}) {
  const store = configureStore({
    reducer: { salary: salaryReducer },
    preloadedState: {
      salary: {
        components: { earnings, deductions: [], benefits: [], reimbursements: [] },
        loadedAt: Date.now(),
        loading: false,
        error: null,
      },
    },
  });
  vi.spyOn(useCanModule, 'useCan').mockImplementation(can);
  return render(
    <Provider store={store}>
      <ScheduledEarningsPanel employeeId="emp-1" />
    </Provider>
  );
}

describe('ScheduledEarningsPanel (W-73.6 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    scheduledEarningService.list.mockResolvedValue([]);
  });

  it('offers only components flagged Scheduled', async () => {
    expect(scheduledComponents(EARNINGS).map((c) => c.code)).toEqual(['BONUS']);

    renderPanel();
    await waitFor(() => expect(scheduledEarningService.list).toHaveBeenCalledWith('emp-1'));
    fireEvent.click(document.getElementById('btn-add-scheduled-earning'));

    fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'Component' }));
    await screen.findByText('Bonus (BONUS)', { selector: '.ant-select-item-option-content' });
    expect(screen.queryByText('Basic (BASIC)', { selector: '.ant-select-item-option-content' })).toBeNull();
    expect(
      screen.queryByText('House Rent Allowance (HRA)', { selector: '.ant-select-item-option-content' })
    ).toBeNull();
  });

  it('refuses a first month before the current one, and accepts this month or later', async () => {
    const current = dayjs().format('YYYY-MM');
    const schema = buildScheduledEarningSchema(current);
    const base = { componentId: 'e-bonus', amount: '30000', instalments: 1, reason: '' };

    await expect(
      schema.validate({ ...base, firstPeriod: dayjs().subtract(1, 'month').format('YYYY-MM') })
    ).rejects.toThrow(`First month must be ${current} or later`);
    await expect(schema.validate({ ...base, firstPeriod: current })).resolves.toBeTruthy();
    await expect(
      schema.validate({ ...base, firstPeriod: dayjs().add(1, 'month').format('YYYY-MM') })
    ).resolves.toBeTruthy();
  });

  it('refuses a zero amount and more than twelve instalments', async () => {
    const schema = buildScheduledEarningSchema(dayjs().format('YYYY-MM'));
    const next = dayjs().add(1, 'month').format('YYYY-MM');
    await expect(
      schema.validate({ componentId: 'e-bonus', amount: '0', firstPeriod: next, instalments: 1 })
    ).rejects.toThrow('Amount must be greater than zero');
    await expect(
      schema.validate({ componentId: 'e-bonus', amount: '100', firstPeriod: next, instalments: 13 })
    ).rejects.toThrow('At most 12 instalments');
  });

  it('refuses more than 2 decimals and an amount under 0.01 per instalment (F-3, F-4)', async () => {
    const schema = buildScheduledEarningSchema(dayjs().format('YYYY-MM'));
    const next = dayjs().add(1, 'month').format('YYYY-MM');
    const base = { componentId: 'e-bonus', firstPeriod: next };
    await expect(schema.validate({ ...base, amount: '100.005', instalments: 1 })).rejects.toThrow(
      'at most 2 decimal places'
    );
    await expect(schema.validate({ ...base, amount: '0.05', instalments: 12 })).rejects.toThrow(
      'Amount must be at least 0.01 per instalment'
    );
    await expect(schema.validate({ ...base, amount: '0.12', instalments: 12 })).resolves.toBeTruthy();
  });

  it('sends the schedule and reloads the list', async () => {
    scheduledEarningService.create.mockResolvedValue({ id: 's-1' });
    renderPanel({ earnings: [EARNINGS[1]] });
    await waitFor(() => expect(scheduledEarningService.list).toHaveBeenCalledTimes(1));
    fireEvent.click(document.getElementById('btn-add-scheduled-earning'));

    fireEvent.change(await screen.findByLabelText('Amount'), { target: { value: '30000' } });
    fireEvent.click(document.getElementById('btn-save-scheduled-earning'));

    await waitFor(() => expect(scheduledEarningService.create).toHaveBeenCalledTimes(1));
    const [employeeId, body] = scheduledEarningService.create.mock.calls[0];
    expect(employeeId).toBe('emp-1');
    expect(body).toMatchObject({
      componentId: 'e-bonus',
      amount: '30000',
      firstPeriod: dayjs().add(1, 'month').format('YYYY-MM'),
      instalments: 1,
    });
    await waitFor(() => expect(scheduledEarningService.list).toHaveBeenCalledTimes(2));
  });

  it('shows pause for a scheduled row, resume for a paused one, and the pay inputs written', async () => {
    scheduledEarningService.list.mockResolvedValue([
      {
        id: 's-1',
        componentCode: 'BONUS',
        componentName: 'Bonus',
        amount: '10000.0000',
        firstPeriod: '2026-11',
        instalments: 3,
        paidInstalments: 1,
        nextPeriod: '2026-12',
        status: 'SCHEDULED',
        payInputIds: ['pi-1'],
      },
      {
        id: 's-2',
        componentCode: 'BONUS',
        componentName: 'Bonus',
        amount: '5000.0000',
        firstPeriod: '2027-01',
        instalments: 1,
        paidInstalments: 0,
        nextPeriod: '2027-01',
        status: 'PAUSED',
        payInputIds: [],
      },
    ]);
    scheduledEarningService.pause.mockResolvedValue({});
    renderPanel();

    expect(await screen.findByText('pi-1')).toBeTruthy();
    expect(screen.getByText('1 / 3')).toBeTruthy();
    expect(screen.getAllByText('Pause')).toHaveLength(1);
    expect(screen.getAllByText('Resume')).toHaveLength(1);

    fireEvent.click(screen.getByText('Pause'));
    await waitFor(() => expect(scheduledEarningService.pause).toHaveBeenCalledWith('s-1'));
  });

  it('hides the add button and disables actions without payroll.structure.manage', async () => {
    scheduledEarningService.list.mockResolvedValue([
      {
        id: 's-1',
        componentCode: 'BONUS',
        amount: '1',
        firstPeriod: '2026-11',
        instalments: 1,
        paidInstalments: 0,
        status: 'SCHEDULED',
        payInputIds: [],
      },
    ]);
    renderPanel({ can: (action) => action === 'payroll.structure.read' });
    await screen.findByText('BONUS');
    expect(document.getElementById('btn-add-scheduled-earning')).toBeNull();
    expect(screen.getByText('Pause').closest('button').disabled).toBe(true);
  });
});
