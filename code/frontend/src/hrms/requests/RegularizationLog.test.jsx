import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useCan } from '@shell/screens';
import { RegularizationLog } from './RegularizationLog.jsx';
import { requestService } from './requestService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./requestService.js', () => ({
  requestService: { allRegularizations: vi.fn() },
}));
vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
  NotEntitled: () => <div>Not entitled</div>,
}));

const T = 60000;
const rows = [
  { id: 'r1', employeeId: 'e1', employeeName: 'Asha Rao', date: '2026-09-29', reason: 'a', status: 'PENDING' },
  { id: 'r2', employeeId: 'e2', employeeName: 'Bilal Khan', date: '2026-09-30', reason: 'b', status: 'APPROVED' },
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
      <RegularizationLog />
    </MemoryRouter>
  );

describe('RegularizationLog (W-48.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCan.mockReturnValue(true);
    requestService.allRegularizations.mockResolvedValue(rows);
  });

  it(
    'sends the status and employee filters',
    async () => {
      renderIt();
      expect(requestService.allRegularizations).not.toHaveBeenCalled();
      pickRange('2026-09-01', '2026-09-30');
      expect(await screen.findByText('Asha Rao')).toBeTruthy();
      expect(requestService.allRegularizations).toHaveBeenCalledWith('2026-09-01', '2026-09-30', undefined, undefined);
      await choose(0, 'REJECTED');
      await waitFor(() =>
        expect(requestService.allRegularizations).toHaveBeenLastCalledWith(
          '2026-09-01',
          '2026-09-30',
          'REJECTED',
          undefined
        )
      );
      await choose(1, 'Bilal Khan');
      await waitFor(() =>
        expect(requestService.allRegularizations).toHaveBeenLastCalledWith('2026-09-01', '2026-09-30', 'REJECTED', 'e2')
      );
    },
    T
  );

  it(
    'shows NotEntitled without core.attendance.read',
    async () => {
      useCan.mockReturnValue(false);
      renderIt();
      expect(await screen.findByText('Not entitled')).toBeTruthy();
      expect(useCan).toHaveBeenCalledWith('core.attendance.read');
      expect(requestService.allRegularizations).not.toHaveBeenCalled();
    },
    T
  );
});
