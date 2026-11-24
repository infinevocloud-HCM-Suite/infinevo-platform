import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { DeductionGrid } from './DeductionGrid.jsx';
import { deductionService } from './deductionService.js';
import { claimService } from './claimService.js';
import { apiClient } from '@shared/api/client';

configure({ asyncUtilTimeout: 10000 });

vi.mock('./deductionService.js', () => ({ deductionService: { enter: vi.fn() } }));
vi.mock('./claimService.js', () => ({ claimService: { searchEmployees: vi.fn() } }));
vi.mock('@shared/api/client', () => ({ apiClient: { get: vi.fn() } }));

const T = 60000;
const thisMonth = dayjs().format('YYYY-MM');
const nextMonth = dayjs().add(1, 'month').format('YYYY-MM');
const thisName = dayjs().format('MMMM YYYY');
const nextName = dayjs().add(1, 'month').format('MMMM YYYY');

const postButton = () => screen.getByRole('button', { name: /post$/i });
const addButton = () => screen.getByRole('button', { name: /add line/i });
const dataRows = () => Array.from(document.querySelectorAll('tr.ant-table-row'));

/** Opens row `n`'s (zero-based) month picker and clicks the month `period` (YYYY-MM). */
async function pickMonth(n, period) {
  const input = dataRows()[n].querySelector('.ant-picker input');
  fireEvent.mouseDown(input);
  fireEvent.click(input);
  // Each picker renders its own dropdown into the body, in the order opened: the last is this row's.
  const cellSelector = `.ant-picker-dropdown td[title="${period}"]`;
  await waitFor(() => expect(document.querySelector(cellSelector)).toBeTruthy());
  const cells = document.querySelectorAll(cellSelector);
  fireEvent.click(cells[cells.length - 1]);
}

/** Answers every posting lookup with `state` (CLOSED names the next month). */
function answer(state) {
  apiClient.get.mockImplementation((url) => {
    const period = url.split('/').pop();
    return Promise.resolve({
      data: {
        requested_period: period,
        posted_period: state === 'CLOSED' ? dayjs(`${period}-01`).add(1, 'month').format('YYYY-MM') : period,
        state,
      },
    });
  });
}

describe('DeductionGrid - which run picks a line up (D-144)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimService.searchEmployees.mockResolvedValue([]);
    answer('OPEN');
  });

  it(
    'shows the run under each row month, asking once per distinct month',
    async () => {
      render(<DeductionGrid open onClose={vi.fn()} />);
      fireEvent.click(addButton());
      await pickMonth(0, thisMonth);
      await pickMonth(1, thisMonth);

      const note = await screen.findByTestId('posting-line-1');
      expect(note.textContent).toBe(`Picked up by the ${thisName} run`);
      expect((await screen.findByTestId('posting-line-2')).textContent).toBe(`Picked up by the ${thisName} run`);
      expect(apiClient.get).toHaveBeenCalledTimes(1);
      expect(apiClient.get).toHaveBeenCalledWith(`/v1/pay-inputs/posting/${thisMonth}`);
      expect(postButton().disabled).toBe(false);
    },
    T
  );

  it(
    'a locked month says the run must be recomputed and does not block Post',
    async () => {
      answer('LOCKED');
      render(<DeductionGrid open onClose={vi.fn()} />);
      await pickMonth(0, thisMonth);

      const note = await screen.findByTestId('posting-line-1');
      expect(note.textContent).toBe(`Picked up by the ${thisName} run, which must be recomputed`);
      expect(note.getAttribute('data-state')).toBe('LOCKED');
      expect(postButton().disabled).toBe(false);
      expect(dataRows()[0].getAttribute('aria-invalid')).toBeNull();
    },
    T
  );

  it(
    'a closed month marks the row, names the month to use and refuses Post',
    async () => {
      answer('CLOSED');
      render(<DeductionGrid open onClose={vi.fn()} />);
      fireEvent.click(addButton());
      await pickMonth(1, thisMonth);

      const note = await screen.findByTestId('posting-line-2');
      expect(note.textContent).toBe(`${thisName} is closed. Enter this for ${nextName}`);
      await waitFor(() => expect(postButton().disabled).toBe(true));
      expect(screen.getByTestId('closed-rows').textContent).toBe('Row 2 names a closed month');
      expect(dataRows()[1].getAttribute('aria-invalid')).toBe('true');
      expect(dataRows()[1].className).toContain('deduction-line-error');
      expect(dataRows()[0].getAttribute('aria-invalid')).toBeNull();

      fireEvent.click(postButton());
      expect(deductionService.enter).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'moving the row to an open month clears the mark and Post comes back',
    async () => {
      apiClient.get.mockImplementation((url) => {
        const period = url.split('/').pop();
        const closed = period === thisMonth;
        return Promise.resolve({
          data: { requested_period: period, posted_period: closed ? nextMonth : period, state: closed ? 'CLOSED' : 'OPEN' },
        });
      });
      render(<DeductionGrid open onClose={vi.fn()} />);
      await pickMonth(0, thisMonth);
      await waitFor(() => expect(postButton().disabled).toBe(true));

      await pickMonth(0, nextMonth);
      await waitFor(() =>
        expect(screen.getByTestId('posting-line-1').textContent).toBe(`Picked up by the ${nextName} run`)
      );
      expect(postButton().disabled).toBe(false);
      expect(dataRows()[0].getAttribute('aria-invalid')).toBeNull();
    },
    T
  );

  it(
    'a 409 for a closed month from the server marks the row it names',
    async () => {
      deductionService.enter.mockRejectedValueOnce({
        status: 409,
        code: 'CONFLICT',
        message: `Line 0: ${thisName} is closed (run approved). Enter this for ${nextName}.`,
        fieldErrors: { line: '0' },
      });
      render(<DeductionGrid open onClose={vi.fn()} />);
      fireEvent.click(postButton());

      await waitFor(() => expect(dataRows()[0].getAttribute('aria-invalid')).toBe('true'));
      expect(screen.getByText('Row 1: nothing was posted')).toBeDefined();
      expect(screen.getByText(`${thisName} is closed (run approved). Enter this for ${nextName}.`)).toBeDefined();
    },
    T
  );
});
