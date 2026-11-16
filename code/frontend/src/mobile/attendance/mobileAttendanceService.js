import { Geolocation } from '@capacitor/geolocation';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { attendanceService } from '../../hrms/attendance/attendanceService.js';

/**
 * Mobile Attendance Service (W-98.2 §4.2).
 * Wraps Capacitor Geolocation, Haptics, and Attendance API calls for native mobile self-service.
 */
export const mobileAttendanceService = {
  /**
   * Captures device GPS coordinates with a 5-second timeout.
   * If permission is denied or GPS times out, returns null coordinates without blocking the employee.
   */
  async getCoordinates() {
    try {
      const position = await Geolocation.getCurrentPosition({
        enableHighAccuracy: true,
        timeout: 5000,
        maximumAge: 10000,
      });

      if (position?.coords) {
        return {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters: position.coords.accuracy || null,
          status: 'granted',
        };
      }
      return { latitude: null, longitude: null, accuracyMeters: null, status: 'unavailable' };
    } catch (err) {
      const isDenied = err?.message?.toLowerCase().includes('denied') || err?.code === 1;
      const isTimeout = err?.message?.toLowerCase().includes('timeout') || err?.code === 3;
      return {
        latitude: null,
        longitude: null,
        accuracyMeters: null,
        status: isDenied ? 'denied' : isTimeout ? 'timeout' : 'unavailable',
        error: err?.message || 'Location unavailable',
      };
    }
  },

  /**
   * Triggers tactile feedback on button tap.
   */
  async triggerHapticTap() {
    try {
      await Haptics.impact({ style: ImpactStyle.Medium });
    } catch {
      // Non-fatal if device has no haptic motor or running on web
    }
  },

  /**
   * Triggers success notification vibration on punch confirmation.
   */
  async triggerHapticSuccess() {
    try {
      await Haptics.notification({ type: NotificationType.Success });
    } catch {
      // Non-fatal
    }
  },

  /**
   * Fetches today's attendance state.
   */
  async getToday() {
    return attendanceService.today();
  },

  /**
   * Submits Clock In with coordinates.
   */
  async clockIn(coords = null) {
    await this.triggerHapticTap();
    const payload = coords?.latitude != null ? {
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracyMeters: coords.accuracyMeters,
    } : {};
    const res = await attendanceService.clockIn(payload);
    await this.triggerHapticSuccess();
    return res;
  },

  /**
   * Submits Clock Out with coordinates.
   */
  async clockOut(coords = null) {
    await this.triggerHapticTap();
    const payload = coords?.latitude != null ? {
      latitude: coords.latitude,
      longitude: coords.longitude,
      accuracyMeters: coords.accuracyMeters,
    } : {};
    const res = await attendanceService.clockOut(payload);
    await this.triggerHapticSuccess();
    return res;
  },

  /**
   * Retrieves sessions for the last 7 days.
   */
  async getWeeklyHistory(targetDateStr) {
    try {
      const today = targetDateStr ? new Date(targetDateStr) : new Date();
      const from = new Date(today);
      from.setDate(today.getDate() - 6);

      const fmt = (d) => {
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
      };

      const fromStr = fmt(from);
      const toStr = fmt(today);

      return await attendanceService.mySessions(fromStr, toStr);
    } catch {
      return [];
    }
  },
};

