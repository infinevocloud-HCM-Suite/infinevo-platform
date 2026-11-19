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
    hasLocationConsent: vi.fn(),
    grantLocationConsent: vi.fn(),
  },
}));

describe('MobileAttendanceScreen component (W-98.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default: consent already given
    mobileAttendanceService.hasLocationConsent.mockResolvedValue(true);
    mobileAttendanceService.grantLocationConsent.mockResolvedValue();
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

  it('shows consent notice once before first location request and remembers consent', async () => {
    mobileAttendanceService.hasLocationConsent.mockResolvedValueOnce(false);
    mobileAttendanceService.getToday.mockResolvedValueOnce({
      date: '2026-11-16',
      openSession: null,
      sessions: [],
      workedMinutes: 0,
    });

    render(<MobileAttendanceScreen />);

    // Consent notice is displayed with exact required text
    expect(await screen.findByTestId('location-consent-banner')).toBeTruthy();
    expect(
      screen.getByText(
        'Infinevo records your location only at the moment you clock in or out, so your employer can see where the punch was made. It is not tracked at any other time.'
      )
    ).toBeTruthy();

    // Click "I Understand" to grant consent
    const grantBtn = screen.getByTestId('consent-grant-btn');
    fireEvent.click(grantBtn);

    await waitFor(() => {
      expect(mobileAttendanceService.grantLocationConsent).toHaveBeenCalled();
      expect(screen.queryByTestId('location-consent-banner')).toBeNull();
    });
  });

  it('displays "Location not recorded" when location permission is denied or coordinates null', async () => {
    mobileAttendanceService.getToday.mockResolvedValueOnce({
      date: '2026-11-16',
      openSession: null,
      sessions: [],
      workedMinutes: 0,
    });
    mobileAttendanceService.getCoordinates.mockResolvedValueOnce({
      status: 'denied',
      latitude: null,
      longitude: null,
      accuracyMeters: null,
    });

    render(<MobileAttendanceScreen />);

    expect(await screen.findByTestId('gps-chip')).toBeTruthy();
    expect(screen.getByTestId('gps-chip').textContent).toContain('Location not recorded');
    expect(screen.getByTestId('punch-button').textContent).toContain('Location not recorded');
  });

  it('renders exactly seven day rows in recent activity card', async () => {
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
    expect(rows).toHaveLength(7);
    expect(screen.getAllByText('8:00')).toHaveLength(2);
  });

  it('leaves button in previous state and shows error when punch fails', async () => {
    mobileAttendanceService.getToday.mockResolvedValueOnce({
      date: '2026-11-16',
      openSession: null,
      sessions: [],
      workedMinutes: 0,
    });
    mobileAttendanceService.clockIn.mockRejectedValueOnce(new Error('Network error during punch'));

    render(<MobileAttendanceScreen />);

    const punchBtn = await screen.findByTestId('punch-button');
    expect(punchBtn.getAttribute('aria-label')).toBe('Clock In');

    fireEvent.click(punchBtn);

    await waitFor(() => {
      expect(screen.getByText('Network error during punch')).toBeTruthy();
    });

    // Button remains Clock In (not flipped or left disabled)
    expect(punchBtn.getAttribute('aria-label')).toBe('Clock In');
    expect(punchBtn.textContent).toContain('CLOCK IN');
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
