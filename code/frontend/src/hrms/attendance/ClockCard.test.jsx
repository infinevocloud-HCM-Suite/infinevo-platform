import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent } from '@testing-library/react';
import { ClockCard } from './ClockCard.jsx';
import { attendanceService } from './attendanceService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./attendanceService.js', () => ({
  attendanceService: { today: vi.fn(), clockIn: vi.fn(), clockOut: vi.fn() },
}));

const T = 60000;
const empty = {
  date: '2026-10-04',
  openSession: null,
  sessions: [],
  workedMinutes: 0,
};

describe('ClockCard (W-48.4 §7)', () => {
  beforeEach(() => vi.clearAllMocks());

  it(
    'shows Clock in with no open session',
    async () => {
      attendanceService.today.mockResolvedValue(empty);
      render(<ClockCard />);
      expect(await screen.findByRole('button', { name: /clock in/i })).toBeTruthy();
      expect(screen.getByText(/4 Oct 2026/)).toBeTruthy();
    },
    T
  );

  it(
    'shows Clock out and running time with an open session',
    async () => {
      const clockInAt = new Date(Date.now() - 125 * 60000).toISOString();
      const open = {
        id: 's1',
        clockInAt,
        clockOutAt: null,
        workedMinutes: null,
      };
      attendanceService.today.mockResolvedValue({
        ...empty,
        openSession: open,
        sessions: [open],
      });
      render(<ClockCard />);
      expect(await screen.findByRole('button', { name: /clock out/i })).toBeTruthy();
      expect(screen.getByText(/running 2:05/)).toBeTruthy();
    },
    T
  );

  it(
    'shows the server message on 409',
    async () => {
      attendanceService.today.mockResolvedValue(empty);
      attendanceService.clockIn.mockRejectedValue({
        status: 409,
        message: 'Already clocked in',
      });
      render(<ClockCard />);
      fireEvent.click(await screen.findByRole('button', { name: /clock in/i }));
      expect(await screen.findByText('Already clocked in')).toBeTruthy();
    },
    T
  );

  it(
    'shows Clock in and warning alert when open session is > 24 hours old',
    async () => {
      const clockInAt = new Date(Date.now() - 25 * 3600 * 1000).toISOString();
      const open = {
        id: 's-old',
        clockInAt,
        clockOutAt: null,
        workedMinutes: null,
      };
      attendanceService.today.mockResolvedValue({
        ...empty,
        openSession: open,
        sessions: [open],
      });
      attendanceService.clockIn.mockResolvedValue({ id: 's-new', clockInAt: new Date().toISOString() });
      render(<ClockCard />);

      expect(await screen.findByRole('button', { name: /clock in/i })).toBeTruthy();
      expect(screen.queryByRole('button', { name: /clock out/i })).toBeNull();
      expect(screen.getByText(/exceeded 24 hours/i)).toBeTruthy();

      fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
      expect(attendanceService.clockIn).toHaveBeenCalledTimes(1);
      expect(attendanceService.clockOut).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'shows Clock in when open session is from an earlier date',
    async () => {
      const clockInAt = '2026-10-03T09:00:00Z';
      const open = {
        id: 's-yesterday',
        attendanceDate: '2026-10-03',
        clockInAt,
        clockOutAt: null,
      };
      attendanceService.today.mockResolvedValue({
        ...empty,
        date: '2026-10-04',
        openSession: open,
        sessions: [],
      });
      render(<ClockCard />);

      expect(await screen.findByRole('button', { name: /clock in/i })).toBeTruthy();
      expect(screen.queryByRole('button', { name: /clock out/i })).toBeNull();
    },
    T
  );
});

