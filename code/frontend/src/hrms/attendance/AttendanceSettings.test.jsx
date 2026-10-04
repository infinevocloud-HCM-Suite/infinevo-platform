import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent } from '@testing-library/react';
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
});
