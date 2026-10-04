import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AttendanceLog } from './AttendanceLog.jsx';
import { attendanceService } from './attendanceService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./attendanceService.js', () => ({
  attendanceService: { allSessions: vi.fn() },
}));

const T = 60000;
const rows = [
  {
    id: 's1',
    employeeId: 'e1',
    employeeName: 'Asha Rao',
    attendanceDate: '2026-09-29',
    clockInAt: '2026-09-29T04:00:00Z',
    clockOutAt: '2026-09-29T12:00:00Z',
    workedMinutes: 480,
    origin: 'CLOCK',
  },
  {
    id: 's2',
    employeeId: 'e2',
    employeeName: 'Bilal Khan',
    attendanceDate: '2026-09-29',
    clockInAt: '2026-09-29T05:00:00Z',
    clockOutAt: null,
    workedMinutes: null,
    origin: 'CLOCK',
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

describe('AttendanceLog (W-48.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    attendanceService.allSessions.mockResolvedValue(rows);
  });

  it(
    'sends nothing until a range is picked (§3: no browser-derived today)',
    async () => {
      render(<AttendanceLog />);
      expect(await screen.findByText(/pick a range to load sessions/i)).toBeTruthy();
      expect(attendanceService.allSessions).not.toHaveBeenCalled();
      pickRange('2026-09-28', '2026-10-04');
      expect(await screen.findAllByText('Asha Rao')).toBeTruthy();
      expect(attendanceService.allSessions).toHaveBeenCalledWith('2026-09-28', '2026-10-04', undefined);
    },
    T
  );

  it(
    'blocks a range over 31 days',
    async () => {
      render(<AttendanceLog />);
      pickRange('2026-09-28', '2026-10-04');
      expect(await screen.findAllByText('Asha Rao')).toBeTruthy();
      const calls = attendanceService.allSessions.mock.calls.length;
      pickRange('2026-01-01', '2026-03-01');
      expect(await screen.findByText(/at most 31 days/i)).toBeTruthy();
      expect(attendanceService.allSessions.mock.calls.length).toBe(calls);
    },
    T
  );

  it(
    'employee filter sends employeeId',
    async () => {
      render(<AttendanceLog />);
      pickRange('2026-09-28', '2026-10-04');
      expect(await screen.findAllByText('Bilal Khan')).toBeTruthy();
      fireEvent.mouseDown(screen.getByRole('combobox'));
      await waitFor(() => expect(document.querySelector('.ant-select-item-option[title="Bilal Khan"]')).toBeTruthy());
      fireEvent.click(document.querySelector('.ant-select-item-option[title="Bilal Khan"]'));
      await waitFor(() =>
        expect(attendanceService.allSessions).toHaveBeenLastCalledWith(expect.any(String), expect.any(String), 'e2')
      );
    },
    T
  );
});
