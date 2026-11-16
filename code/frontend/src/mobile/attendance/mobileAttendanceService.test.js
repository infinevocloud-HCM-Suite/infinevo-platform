import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mobileAttendanceService } from './mobileAttendanceService.js';
import { Geolocation } from '@capacitor/geolocation';
import { Haptics } from '@capacitor/haptics';
import { attendanceService } from '../../hrms/attendance/attendanceService.js';

vi.mock('@capacitor/geolocation', () => ({
  Geolocation: {
    getCurrentPosition: vi.fn(),
  },
}));

vi.mock('@capacitor/haptics', () => ({
  Haptics: {
    impact: vi.fn().mockResolvedValue(),
    notification: vi.fn().mockResolvedValue(),
  },
  ImpactStyle: { Medium: 'MEDIUM' },
  NotificationType: { Success: 'SUCCESS' },
}));

vi.mock('../../hrms/attendance/attendanceService.js', () => ({
  attendanceService: {
    today: vi.fn(),
    clockIn: vi.fn(),
    clockOut: vi.fn(),
    mySessions: vi.fn(),
  },
}));

describe('mobileAttendanceService (W-98.2 §6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getCoordinates', () => {
    it('returns captured GPS coordinates when permission is granted', async () => {
      Geolocation.getCurrentPosition.mockResolvedValueOnce({
        coords: {
          latitude: 12.9716,
          longitude: 77.5946,
          accuracy: 15,
        },
      });

      const res = await mobileAttendanceService.getCoordinates();
      expect(res.status).toBe('granted');
      expect(res.latitude).toBe(12.9716);
      expect(res.longitude).toBe(77.5946);
      expect(res.accuracyMeters).toBe(15);
    });

    it('returns denied status without throwing when user denies location permission', async () => {
      Geolocation.getCurrentPosition.mockRejectedValueOnce(new Error('User denied Geolocation'));

      const res = await mobileAttendanceService.getCoordinates();
      expect(res.status).toBe('denied');
      expect(res.latitude).toBeNull();
      expect(res.longitude).toBeNull();
    });

    it('returns timeout status when GPS request exceeds timeout', async () => {
      Geolocation.getCurrentPosition.mockRejectedValueOnce({ code: 3, message: 'Timeout expired' });

      const res = await mobileAttendanceService.getCoordinates();
      expect(res.status).toBe('timeout');
      expect(res.latitude).toBeNull();
    });
  });

  describe('clockIn & clockOut', () => {
    it('triggers haptics and forwards coordinates to attendanceService.clockIn', async () => {
      attendanceService.clockIn.mockResolvedValueOnce({ id: 'sess-1' });

      const res = await mobileAttendanceService.clockIn({
        latitude: 12.9716,
        longitude: 77.5946,
        accuracyMeters: 10,
      });

      expect(Haptics.impact).toHaveBeenCalled();
      expect(attendanceService.clockIn).toHaveBeenCalledWith({
        latitude: 12.9716,
        longitude: 77.5946,
        accuracyMeters: 10,
      });
      expect(Haptics.notification).toHaveBeenCalled();
      expect(res).toEqual({ id: 'sess-1' });
    });

    it('sends empty payload when coordinates are null', async () => {
      attendanceService.clockOut.mockResolvedValueOnce({ status: 'PRESENT' });

      const res = await mobileAttendanceService.clockOut(null);

      expect(attendanceService.clockOut).toHaveBeenCalledWith({});
      expect(res).toEqual({ status: 'PRESENT' });
    });
  });

  describe('getWeeklyHistory', () => {
    it('queries sessions for the 7-day range ending on target date', async () => {
      attendanceService.mySessions.mockResolvedValueOnce([
        { id: 's1', attendanceDate: '2026-11-16', workedMinutes: 480 },
      ]);

      const res = await mobileAttendanceService.getWeeklyHistory('2026-11-16');

      expect(attendanceService.mySessions).toHaveBeenCalledWith('2026-11-10', '2026-11-16');
      expect(res).toHaveLength(1);
    });
  });
});

