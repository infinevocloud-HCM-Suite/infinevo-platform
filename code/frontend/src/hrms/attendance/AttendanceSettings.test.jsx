import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AttendanceSettings } from './AttendanceSettings.jsx';
import { attendanceService } from './attendanceService.js';
import { useCan } from '@shell/screens';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./attendanceService.js', () => ({
  attendanceService: { preferences: vi.fn(), savePreferences: vi.fn() },
}));
vi.mock('@shell/screens', () => ({ useCan: vi.fn(() => true) }));

const T = 60000;
const pref = {
  hoursCalculation: 'EVERY_SESSION',
  fullDayMinimumHours: 9,
  halfDayMinimumHours: 4.5,
  regularizationWindowDays: null,
  maxRegularizationsPerMonth: null,
  allowRegularizationWithoutSession: true,
  isDefault: true,
};

describe('AttendanceSettings (W-48.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCan.mockReturnValue(true);
    attendanceService.preferences.mockResolvedValue(pref);
  });

  it(
    'blocks half day above full day',
    async () => {
      render(<AttendanceSettings />);
      expect(await screen.findByText('Using defaults')).toBeTruthy();
      const half = screen.getByLabelText('Half-day minimum hours');
      fireEvent.change(half, { target: { value: '10' } });
      fireEvent.blur(half);
      fireEvent.click(screen.getByRole('button', { name: /save/i }));
      expect(await screen.findByText('Half day must be below full day')).toBeTruthy();
      expect(attendanceService.savePreferences).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'hides Save without core.attendance.manage',
    async () => {
      useCan.mockReturnValue(false);
      render(<AttendanceSettings />);
      expect(await screen.findByText('Using defaults')).toBeTruthy();
      expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
      expect(useCan).toHaveBeenCalledWith('core.attendance.manage');
    },
    T
  );

  it(
    'a failed load shows the error with Retry and no Save; Retry loads the form (D-84)',
    async () => {
      attendanceService.preferences.mockRejectedValueOnce(new Error('Server down'));
      render(<AttendanceSettings />);
      expect(await screen.findByText('Server down')).toBeTruthy();
      expect(screen.queryByRole('button', { name: /save/i })).toBeNull();
      expect(screen.queryByLabelText('Full-day minimum hours')).toBeNull();
      fireEvent.click(screen.getByRole('button', { name: /retry/i }));
      expect(await screen.findByRole('button', { name: /save/i })).toBeTruthy();
      expect(attendanceService.preferences).toHaveBeenCalledTimes(2);
      expect(attendanceService.savePreferences).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'renders grace period duration and sends it on save',
    async () => {
      attendanceService.savePreferences.mockResolvedValueOnce({
        ...pref,
        gracePeriodMinutes: 10,
        isDefault: false,
      });
      render(<AttendanceSettings />);
      expect(await screen.findByText('Using defaults')).toBeTruthy();
      const grace = screen.getByLabelText('Grace period duration');
      expect(grace).toBeTruthy();
      fireEvent.change(grace, { target: { value: '10' } });
      fireEvent.blur(grace);
      fireEvent.click(screen.getByRole('button', { name: /save/i }));
      await waitFor(() => {
        expect(attendanceService.savePreferences).toHaveBeenCalledWith(
          expect.objectContaining({
            gracePeriodMinutes: 10,
          })
        );
      });
    },
    T
  );
});
