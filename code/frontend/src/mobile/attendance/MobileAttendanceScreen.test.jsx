import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MobileAttendanceScreen } from './MobileAttendanceScreen.jsx';
import { mobileAttendanceService } from './mobileAttendanceService.js';

vi.mock('./mobileAttendanceService.js', () => ({
  mobileAttendanceService: {
    getCoordinates: vi.fn(),
    getToday: vi.fn(),
    getWeeklyHistory: vi.fn(),
    clockIn: vi.fn(),
    clockOut: vi.fn(),
  },
}));

describe('MobileAttendanceScreen component (W-98.2 §6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mobileAttendanceService.getCoordinates.mockResolvedValue({
      status: 'granted',
      latitude: 12.9716,
      longitude: 77.5946,
      accuracyMeters: 10,
    });
    mobileAttendanceService.getWeeklyHistory.mockResolvedValue([]);
  });

  it('renders Clock In button when employee is not clocked in', async () => {
    mobileAttendanceService.getToday.mockResolvedValueOnce({
      date: '2026-11-16',
      openSession: null,
      sessions: [],
      workedMinutes: 0,
    });

    render(<MobileAttendanceScreen />);

    expect(await screen.findByTestId('mobile-attendance-screen')).toBeTruthy();
    expect(screen.getByText('Ready to Punch In')).toBeTruthy();

    const punchBtn = screen.getByTestId('punch-button');
    expect(punchBtn.getAttribute('aria-label')).toBe('Clock In');
    expect(punchBtn.textContent).toContain('CLOCK IN');
    expect(screen.getByTestId('gps-chip').textContent).toContain('GPS Active');
  });

  it('punches Clock In when employee taps the circular button', async () => {
    mobileAttendanceService.getToday
      .mockResolvedValueOnce({
        date: '2026-11-16',
        openSession: null,
        sessions: [],
        workedMinutes: 0,
      })
      .mockResolvedValueOnce({
        date: '2026-11-16',
        openSession: {
          id: 'sess-new',
          clockInAt: '2026-11-16T09:00:00Z',
          attendanceDate: '2026-11-16',
        },
        sessions: [],
        workedMinutes: 0,
      });

    mobileAttendanceService.clockIn.mockResolvedValueOnce({ id: 'sess-new' });

    render(<MobileAttendanceScreen />);

    const punchBtn = await screen.findByTestId('punch-button');
    fireEvent.click(punchBtn);

    await waitFor(() => {
      expect(mobileAttendanceService.clockIn).toHaveBeenCalled();
    });
  });

  it('renders Clock Out button and active ShiftTimer when employee is clocked in', async () => {
    mobileAttendanceService.getToday.mockResolvedValueOnce({
      date: '2026-11-16',
      openSession: {
        id: 'sess-active',
        clockInAt: new Date(Date.now() - 3600000).toISOString(),
        attendanceDate: '2026-11-16',
      },
      sessions: [
        {
          id: 'sess-active',
          clockInAt: new Date(Date.now() - 3600000).toISOString(),
          clockOutAt: null,
          attendanceDate: '2026-11-16',
        },
      ],
      workedMinutes: 60,
    });

    render(<MobileAttendanceScreen />);

    expect(await screen.findByTestId('shift-timer')).toBeTruthy();
    const punchBtn = screen.getByTestId('punch-button');
    expect(punchBtn.getAttribute('aria-label')).toBe('Clock Out');
    expect(punchBtn.textContent).toContain('CLOCK OUT');
    expect(screen.getByText('You are currently clocked in')).toBeTruthy();
  });

  it('renders recent attendance rows from history', async () => {
    mobileAttendanceService.getToday.mockResolvedValueOnce({
      date: '2026-11-16',
      openSession: null,
      sessions: [],
      workedMinutes: 480,
    });

    mobileAttendanceService.getWeeklyHistory.mockResolvedValueOnce([
      {
        id: 'hist-1',
        attendanceDate: '2026-11-15',
        clockInAt: '2026-11-15T09:00:00Z',
        clockOutAt: '2026-11-15T17:00:00Z',
        workedMinutes: 480,
        dayStatus: 'PRESENT',
      },
    ]);

    render(<MobileAttendanceScreen />);

    const rows = await screen.findAllByTestId('history-row');
    expect(rows).toHaveLength(1);
    expect(screen.getAllByText('8:00')).toHaveLength(2);
  });

  it('displays stale session alert when open session is > 24 hours old', async () => {
    mobileAttendanceService.getToday.mockResolvedValueOnce({
      date: '2026-11-16',
      openSession: {
        id: 'sess-stale',
        clockInAt: '2026-11-14T09:00:00Z',
        attendanceDate: '2026-11-14',
      },
      sessions: [],
      workedMinutes: 0,
    });

    render(<MobileAttendanceScreen />);

    expect(await screen.findByText('Previous Session Expired (>24h)')).toBeTruthy();
  });
});
