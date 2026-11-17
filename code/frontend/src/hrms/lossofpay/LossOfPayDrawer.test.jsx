import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { LossOfPayDrawer } from './LossOfPayDrawer.jsx';
import { lossOfPayService } from './lossOfPayService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./lossOfPayService.js', () => ({
  employeeLabel: (e) => `${e.employeeNumber} - ${e.firstName}`,
  lossOfPayService: { searchActive: vi.fn(), record: vi.fn() },
}));
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));

const T = 60000;
const thisMonth = dayjs().format('YYYY-MM');
const nextMonth = dayjs().add(1, 'month').format('YYYY-MM');

async function fill({ days = 2.5, reason = 'Absent 3-5 Oct' } = {}) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Employee' }));
  const sel = '.ant-select-item-option[title="E001 - Asha"]';
  await waitFor(() => expect(document.querySelector(sel)).toBeTruthy());
  fireEvent.click(document.querySelector(sel));
  if (days != null) fireEvent.change(screen.getByLabelText('Days'), { target: { value: String(days) } });
  if (reason != null) fireEvent.change(screen.getByLabelText('Reason'), { target: { value: reason } });
}

const renderIt = (onSaved = vi.fn()) => {
  render(<LossOfPayDrawer open onClose={vi.fn()} onSaved={onSaved} />);
  return onSaved;
};

describe('LossOfPayDrawer (D-140)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    lossOfPayService.searchActive.mockResolvedValue([{ id: 'e1', employeeNumber: 'E001', firstName: 'Asha' }]);
    lossOfPayService.record.mockResolvedValue({ id: 'p1', posted_period: thisMonth });
  });

  it(
    'requires days and a reason before posting',
    async () => {
      renderIt();
      await waitFor(() => expect(lossOfPayService.searchActive).toHaveBeenCalledWith(''));
      await fill({ days: null, reason: null });
      fireEvent.click(screen.getByTestId('lop-save'));
      expect(await screen.findByText('Enter the days')).toBeTruthy();
      expect(await screen.findByText('Enter a reason')).toBeTruthy();
      expect(lossOfPayService.record).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'posts employee, the chosen month, days as text and the reason',
    async () => {
      const onSaved = renderIt();
      await fill();
      fireEvent.click(screen.getByTestId('lop-save'));
      await waitFor(() =>
        expect(lossOfPayService.record).toHaveBeenCalledWith({
          employeeId: 'e1',
          period: thisMonth,
          days: '2.50',
          reason: 'Absent 3-5 Oct',
        })
      );
      await waitFor(() => expect(onSaved).toHaveBeenCalled());
      expect(successMsg).toHaveBeenCalledWith('Loss of pay recorded');
      expect(errorMsg).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'warns when the month was locked and the ledger posted to the next one, and still saves',
    async () => {
      lossOfPayService.record.mockResolvedValue({ id: 'p1', posted_period: nextMonth });
      const onSaved = renderIt();
      await fill();
      fireEvent.click(screen.getByTestId('lop-save'));
      // D-142: a dialog, not a toast - the user must dismiss it.
      expect((await screen.findAllByText(`${dayjs().format('MMM YYYY')} is locked`)).length).toBeGreaterThan(0);
      expect(screen.getAllByText(new RegExp(`posted to ${dayjs().add(1, 'month').format('MMM YYYY')}`)).length).toBeGreaterThan(0);
      expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ posted_period: nextMonth }));
      expect(successMsg).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'reports a server error through errorMsg and does not close',
    async () => {
      const err = { code: 'VALIDATION_FAILED', message: 'Bad' };
      lossOfPayService.record.mockRejectedValue(err);
      const onSaved = renderIt();
      await fill();
      fireEvent.click(screen.getByTestId('lop-save'));
      await waitFor(() => expect(errorMsg).toHaveBeenCalledWith(err));
      expect(onSaved).not.toHaveBeenCalled();
    },
    T
  );
});
