import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import dayjs from 'dayjs';
import { useCan } from '@shell/screens';
import { LossOfPayLog, formatDays, lopReason } from './LossOfPayLog.jsx';
import { lossOfPayService } from './lossOfPayService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./lossOfPayService.js', () => ({
  LOP_KIND: 'LOP_DAYS',
  MANUAL_LOP_REF_PREFIX: 'manual-lop:',
  employeeLabel: (e) => e.id,
  lossOfPayService: {
    forPeriod: vi.fn(),
    directory: vi.fn(),
    reverse: vi.fn(),
    searchActive: vi.fn(() => Promise.resolve([])),
    record: vi.fn(),
  },
}));
vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
  NotEntitled: () => <div>Not entitled</div>,
}));
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));

const T = 60000;
const rows = [
  {
    id: 'p1',
    employee_id: 'e1',
    posted_period: '2026-10',
    kind: 'LOP_DAYS',
    quantity: '2.50',
    source_ref: 'manual-lop:2b1c',
    note: 'Absent 3-5 Oct',
    reverses_id: null,
    created_at: '2026-10-20T10:00:00Z',
    created_by: 'hr.asha',
  },
  {
    id: 'p2',
    employee_id: 'e2',
    posted_period: '2026-10',
    kind: 'LOP_DAYS',
    quantity: '3.00',
    source_ref: 'manual-lop:9f8e',
    note: 'No show',
    reverses_id: null,
    created_at: '2026-10-21T10:00:00Z',
  },
  {
    id: 'p3',
    employee_id: 'e2',
    posted_period: '2026-10',
    kind: 'LOP_DAYS',
    quantity: '3.00',
    source_ref: 'manual-lop:9f8e',
    note: 'Wrong employee',
    reverses_id: 'p2',
    created_at: '2026-10-22T10:00:00Z',
  },
  { id: 'o1', employee_id: 'e1', posted_period: '2026-10', kind: 'OVERTIME', quantity: '4.00', reverses_id: null },
];

const can = (granted) => useCan.mockImplementation((code) => granted.includes(code));

const renderIt = () =>
  render(
    <MemoryRouter>
      <LossOfPayLog />
    </MemoryRouter>
  );

describe('LossOfPayLog (D-140)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    can(['core.pay_input.read', 'core.pay_input.write']);
    lossOfPayService.forPeriod.mockResolvedValue({ rows });
    lossOfPayService.directory.mockResolvedValue({ e1: 'E001 - Asha Rao', e2: 'E002 - Bilal Khan' });
    lossOfPayService.reverse.mockResolvedValue({ id: 'p4', reverses_id: 'p1' });
  });

  it(
    'shows NotEntitled without core.pay_input.read and loads nothing',
    async () => {
      can([]);
      renderIt();
      expect(await screen.findByText('Not entitled')).toBeTruthy();
      expect(useCan).toHaveBeenCalledWith('core.pay_input.read');
      expect(lossOfPayService.forPeriod).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'loads the current month and renders LOP rows with name, days, month and status',
    async () => {
      renderIt();
      expect(await screen.findByText('E001 - Asha Rao')).toBeTruthy();
      expect(lossOfPayService.forPeriod).toHaveBeenCalledWith(dayjs().format('YYYY-MM'));
      expect(screen.getAllByText('E002 - Bilal Khan').length).toBe(2);
      expect(screen.getByTestId('lop-days-p1').textContent).toBe('2.5');
      expect(screen.getAllByText('Oct 2026').length).toBe(3);
      expect(screen.getByTestId('lop-reason-p1').textContent).toBe('Absent 3-5 Oct');
      // a reversal row shows its own reason
      expect(screen.getByTestId('lop-reason-p3').textContent).toBe('Wrong employee');
      // the random key is never shown as a reason
      expect(screen.queryByText(/manual-lop:/)).toBeNull();
      expect(screen.getByText('hr.asha')).toBeTruthy();
      expect(screen.getByTestId('lop-status-p1').textContent).toBe('Posted');
      expect(screen.getByTestId('lop-status-p2').textContent).toBe('Reversed');
      expect(screen.getByTestId('lop-status-p3').textContent).toBe('Reversal of 3');
      // the overtime row of the same month is not loss of pay
      expect(screen.queryByTestId('lop-days-o1')).toBeNull();
      // only an original row not yet reversed can be reversed
      expect(screen.getByTestId('lop-reverse-p1')).toBeTruthy();
      expect(screen.queryByTestId('lop-reverse-p2')).toBeNull();
      expect(screen.queryByTestId('lop-reverse-p3')).toBeNull();
      expect(screen.getByText('Add loss of pay')).toBeTruthy();
    },
    T
  );

  it(
    'hides Add and Reverse without core.pay_input.write',
    async () => {
      can(['core.pay_input.read']);
      renderIt();
      expect(await screen.findByText('E001 - Asha Rao')).toBeTruthy();
      expect(screen.queryByText('Add loss of pay')).toBeNull();
      expect(screen.queryByTestId('lop-reverse-p1')).toBeNull();
    },
    T
  );

  it(
    'Reverse asks for a reason, posts the reversal and reloads the month',
    async () => {
      renderIt();
      fireEvent.click(await screen.findByTestId('lop-reverse-p1'));
      const dialog = await screen.findByRole('dialog');
      const ok = within(dialog).getByRole('button', { name: 'Reverse' });
      expect(ok.disabled).toBe(true);
      fireEvent.change(within(dialog).getByLabelText('Reason for reversal'), { target: { value: 'Entered twice' } });
      await waitFor(() => expect(ok.disabled).toBe(false));
      fireEvent.click(ok);
      await waitFor(() => expect(lossOfPayService.reverse).toHaveBeenCalledWith('p1', 'Entered twice'));
      await waitFor(() => expect(lossOfPayService.forPeriod).toHaveBeenCalledTimes(2));
    },
    T
  );

  it('reads the reason from note, and from source_ref only for a caller reference', () => {
    expect(lopReason({ note: 'Absent', source_ref: 'manual-lop:1' })).toBe('Absent');
    expect(lopReason({ note: null, source_ref: 'manual-lop:1' })).toBe('—');
    expect(lopReason({ note: '', source_ref: 'leave:42' })).toBe('leave:42');
    expect(lopReason({})).toBe('—');
  });

  it('trims day figures as text', () => {
    expect(formatDays('2.50')).toBe('2.5');
    expect(formatDays('3.00')).toBe('3');
    expect(formatDays('10')).toBe('10');
    expect(formatDays(null)).toBe('—');
  });
});
