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
      expect(screen.getByText(/2026-10-04/)).toBeTruthy();
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
});
