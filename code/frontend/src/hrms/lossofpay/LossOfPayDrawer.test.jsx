import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { LossOfPayDrawer } from './LossOfPayDrawer.jsx';
import { lossOfPayService } from './lossOfPayService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { apiClient } from '@shared/api/client';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./lossOfPayService.js', () => ({
  employeeLabel: (e) => `${e.employeeNumber} - ${e.firstName}`,
  lossOfPayService: { searchActive: vi.fn(), record: vi.fn() },
}));
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));
// D-144: the posting lookup goes through the shared client; answer it per test.
vi.mock('@shared/api/client', () => ({ apiClient: { get: vi.fn() } }));

const T = 60000;
const thisMonth = dayjs().format('YYYY-MM');
const nextMonth = dayjs().add(1, 'month').format('YYYY-MM');
const thisName = dayjs().format('MMMM YYYY');
const nextName = dayjs().add(1, 'month').format('MMMM YYYY');

const answer = (state, posted = thisMonth) =>
  apiClient.get.mockResolvedValue({ data: { requested_period: thisMonth, posted_period: posted, state } });

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
    answer('OPEN');
  });

  it(
    'says before saving which run picks the entry up: an open month',
    async () => {
      renderIt();
      const note = await screen.findByTestId('lop-posting');
      expect(note.textContent).toBe(`Picked up by the ${thisName} run`);
      expect(note.getAttribute('data-state')).toBe('OPEN');
      expect(apiClient.get).toHaveBeenCalledWith(`/v1/pay-inputs/posting/${thisMonth}`);
      expect(screen.getByTestId('lop-save').disabled).toBe(false);
    },
    T
  );

  it(
    'a locked month still saves, and says the run must be recomputed',
    async () => {
      answer('LOCKED');
      renderIt();
      const note = await screen.findByTestId('lop-posting');
      expect(note.textContent).toBe(`Picked up by the ${thisName} run, which must be recomputed`);
      expect(note.getAttribute('data-state')).toBe('LOCKED');
      await fill();
      fireEvent.click(screen.getByTestId('lop-save'));
      await waitFor(() => expect(lossOfPayService.record).toHaveBeenCalled());
    },
    T
  );

  it(
    'a closed month names the month to use and disables Save',
    async () => {
      answer('CLOSED', nextMonth);
      renderIt();
      const note = await screen.findByTestId('lop-posting');
      expect(note.textContent).toBe(`${thisName} is closed. Enter this for ${nextName}`);
      expect(note.getAttribute('data-state')).toBe('CLOSED');
      await waitFor(() => expect(screen.getByTestId('lop-save').disabled).toBe(true));
      await fill();
      fireEvent.click(screen.getByTestId('lop-save'));
      expect(lossOfPayService.record).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'says nothing under the month when the lookup fails, and still lets the server decide',
    async () => {
      apiClient.get.mockRejectedValue({ status: 403 });
      const onSaved = renderIt();
      await waitFor(() => expect(apiClient.get).toHaveBeenCalled());
      expect(screen.queryByTestId('lop-posting')).toBeNull();
      await fill();
      fireEvent.click(screen.getByTestId('lop-save'));
      await waitFor(() => expect(onSaved).toHaveBeenCalled());
    },
    T
  );

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
    'reports a 409 for a month closed between the lookup and the save, and does not save',
    async () => {
      const err = { status: 409, code: 'CONFLICT', message: `${thisName} is closed (run approved). Enter this for ${nextName}.` };
      lossOfPayService.record.mockRejectedValue(err);
      const onSaved = renderIt();
      await fill();
      fireEvent.click(screen.getByTestId('lop-save'));
      await waitFor(() => expect(errorMsg).toHaveBeenCalledWith(err));
      expect(onSaved).not.toHaveBeenCalled();
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
