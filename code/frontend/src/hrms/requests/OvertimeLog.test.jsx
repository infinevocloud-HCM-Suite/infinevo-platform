import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useCan } from '@shell/screens';
import { OvertimeLog } from './OvertimeLog.jsx';
import { requestService } from './requestService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./requestService.js', () => ({
  requestService: { allOvertime: vi.fn() },
}));
vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
  NotEntitled: () => <div>Not entitled</div>,
}));

const T = 60000;
const rows = [
  {
    id: 'r1',
    employeeId: 'e1',
    employeeName: 'Asha Rao',
    overtimeDate: '2026-09-29',
    hours: '2.00',
    amount: '500.00',
    status: 'PENDING',
  },
  {
    id: 'r2',
    employeeId: 'e2',
    employeeName: 'Bilal Khan',
    overtimeDate: '2026-09-30',
    hours: '1.00',
    amount: '250.00',
    status: 'APPROVED',
  },
];

function pickRange(from, to) {
  const inputs = document.querySelectorAll('.ant-picker-range input');
  fireEvent.mouseDown(inputs[0]);
  fireEvent.focus(inputs[0]);
  fireEvent.change(inputs[0], { target: { value: from } });
  fireEvent.keyDown(inputs[0], { key: 'Enter', code: 'Enter' });
  fireEvent.focus(inputs[1]);
  fireEvent.change(inputs[1], { target: { value: to } });
  fireEvent.keyDown(inputs[1], { key: 'Enter', code: 'Enter' });
}

async function choose(index, title) {
  fireEvent.mouseDown(screen.getAllByRole('combobox')[index]);
  const sel = `.ant-select-item-option[title="${title}"]`;
  await waitFor(() => expect(document.querySelector(sel)).toBeTruthy());
  fireEvent.click(document.querySelector(sel));
}

const renderIt = () =>
  render(
    <MemoryRouter>
      <OvertimeLog />
    </MemoryRouter>
  );

describe('OvertimeLog (W-68 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCan.mockReturnValue(true);
    requestService.allOvertime.mockResolvedValue(rows);
  });

  it(
    'requests nothing until a range is picked, then sends the employee filter',
    async () => {
      renderIt();
      expect(await screen.findByText('Pick a range to load requests')).toBeTruthy();
      expect(requestService.allOvertime).not.toHaveBeenCalled();
      pickRange('2026-09-01', '2026-09-30');
      expect(await screen.findByText('Asha Rao')).toBeTruthy();
      expect(requestService.allOvertime).toHaveBeenCalledWith('2026-09-01', '2026-09-30', undefined);
      await choose(0, 'Bilal Khan');
      await waitFor(() =>
        expect(requestService.allOvertime).toHaveBeenLastCalledWith('2026-09-01', '2026-09-30', 'e2')
      );
    },
    T
  );

  it(
    'shows NotEntitled without core.overtime.read',
    async () => {
      useCan.mockReturnValue(false);
      renderIt();
      expect(await screen.findByText('Not entitled')).toBeTruthy();
      expect(useCan).toHaveBeenCalledWith('core.overtime.read');
      expect(requestService.allOvertime).not.toHaveBeenCalled();
    },
    T
  );
});
