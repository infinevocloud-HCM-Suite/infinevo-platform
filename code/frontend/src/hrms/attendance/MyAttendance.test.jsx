import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { MyAttendance } from './MyAttendance.jsx';
import { attendanceService } from './attendanceService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./attendanceService.js', () => ({
  attendanceService: { today: vi.fn(), clockIn: vi.fn(), clockOut: vi.fn(), mySessions: vi.fn() },
}));

const T = 60000;

describe('MyAttendance (D-84)', () => {
  beforeEach(() => vi.clearAllMocks());

  it(
    'loads the current month on its own when /today fails',
    async () => {
      const start = dayjs().startOf('month');
      const date = start.format('YYYY-MM-DD');
      attendanceService.today.mockRejectedValue(new Error('today down'));
      attendanceService.mySessions.mockResolvedValue([
        { id: 's1', attendanceDate: date, clockInAt: `${date}T09:00:00Z`, clockOutAt: null, workedMinutes: 0 },
      ]);
      render(<MyAttendance />);
      await waitFor(() =>
        expect(attendanceService.mySessions).toHaveBeenCalledWith(date, start.endOf('month').format('YYYY-MM-DD'))
      );
      expect(await screen.findByText('My days')).toBeTruthy();
      // The one day's row renders: its Sessions cell reads 1.
      expect(await screen.findByRole('cell', { name: '1' })).toBeTruthy();
    },
    T
  );

  it(
    'switches to the server month when /today answers with another month',
    async () => {
      attendanceService.today.mockResolvedValue({ date: '2020-02-10', openSession: null, sessions: [], workedMinutes: 0 });
      attendanceService.mySessions.mockResolvedValue([]);
      render(<MyAttendance />);
      await waitFor(() => expect(attendanceService.mySessions).toHaveBeenCalledWith('2020-02-01', '2020-02-29'));
    },
    T
  );
});
