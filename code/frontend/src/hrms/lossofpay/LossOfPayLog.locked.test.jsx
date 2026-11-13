import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import dayjs from 'dayjs';
import { LossOfPayLog } from './LossOfPayLog.jsx';
import { lossOfPayService } from './lossOfPayService.js';

configure({ asyncUtilTimeout: 20000 });

const MONTH = 'YYYY-MM';
const nextMonth = dayjs().add(1, 'month').format(MONTH);

vi.mock('./lossOfPayService.js', () => ({
  LOP_KIND: 'LOP_DAYS',
  MANUAL_LOP_REF_PREFIX: 'manual-lop:',
  employeeLabel: (e) => e.id,
  lossOfPayService: { forPeriod: vi.fn(), directory: vi.fn(), reverse: vi.fn(), searchActive: vi.fn(), record: vi.fn() },
}));
vi.mock('@shell/screens', () => ({ useCan: vi.fn(() => true), NotEntitled: () => <div>Not entitled</div> }));
vi.mock('@shared/ui/msgHelper.js', () => ({ successMsg: vi.fn(), errorMsg: vi.fn() }));
// The drawer is tested on its own; here it only reports what the ledger answered.
vi.mock('./LossOfPayDrawer.jsx', () => ({
  // eslint-disable-next-line react/prop-types
  LossOfPayDrawer: ({ open, onSaved }) =>
    open ? (
      <button type="button" onClick={() => onSaved({ id: 'p9', posted_period: nextMonth })}>
        fake-save
      </button>
    ) : null,
}));

describe('LossOfPayLog locked month (D-142)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    lossOfPayService.forPeriod.mockResolvedValue({ rows: [] });
    lossOfPayService.directory.mockResolvedValue({});
  });

  it(
    'after an entry lands in a later month the list shows that month',
    async () => {
      render(
        <MemoryRouter>
          <LossOfPayLog />
        </MemoryRouter>
      );
      await waitFor(() => expect(lossOfPayService.forPeriod).toHaveBeenCalledWith(dayjs().format(MONTH)));
      fireEvent.click(screen.getByText('Add loss of pay'));
      fireEvent.click(await screen.findByText('fake-save'));
      await waitFor(() => expect(lossOfPayService.forPeriod).toHaveBeenCalledWith(nextMonth));
      expect(document.getElementById('lop-filter-month').value).toBe(dayjs(nextMonth, MONTH).format('MMM YYYY'));
    },
    60000
  );
});
