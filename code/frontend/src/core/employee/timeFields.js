import dayjs from 'dayjs';

/**
 * Helpers for the Employment section's time zone and shift fields (D-41).
 */

/**
 * Used when the browser cannot list its zones (Intl.supportedValuesOf is missing on older
 * engines). Covers the zones the platform's tenants are most likely to sit in; a stored value
 * outside it is still shown, because timeZoneOptions adds the current value.
 */
export const FALLBACK_TIME_ZONES = [
  'UTC',
  'Asia/Kolkata',
  'Asia/Dubai',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Kathmandu',
  'Asia/Dhaka',
  'Asia/Colombo',
  'Australia/Sydney',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Paris',
  'Africa/Johannesburg',
  'America/New_York',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Sao_Paulo',
];

/**
 * Every IANA zone the browser knows, merged with the fallback list; the fallback list alone when
 * the browser cannot list them.
 *
 * The merge is not decoration: ICU lists some zones under their old canonical names —
 * Intl.supportedValuesOf gives "Asia/Calcutta", not "Asia/Kolkata" — so without it a user
 * searching for Kolkata finds nothing. Both spellings are valid to java.time.ZoneId.of.
 */
export function listTimeZones() {
  try {
    if (typeof Intl !== 'undefined' && typeof Intl.supportedValuesOf === 'function') {
      const zones = Intl.supportedValuesOf('timeZone');
      if (Array.isArray(zones) && zones.length > 0) {
        return Array.from(new Set([...zones, ...FALLBACK_TIME_ZONES])).sort();
      }
    }
  } catch {
    // fall through to the fallback list
  }
  return FALLBACK_TIME_ZONES;
}

/**
 * The zone a blank Time Zone field starts on.
 *
 * Nothing the frontend loads carries the tenant's zone today (the navigation feed and /me have
 * no timezone), so this is the browser's zone, then UTC.
 */
export function defaultTimeZone() {
  try {
    const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (zone) return zone;
  } catch {
    // fall through
  }
  return 'UTC';
}

/** Select options for the zone list, with the current value added if the list lacks it. */
export function timeZoneOptions(current) {
  const zones = listTimeZones();
  const all = current && !zones.includes(current) ? [current, ...zones] : zones;
  return all.map((zone) => ({ value: zone, label: zone }));
}

const HH_MM = /^(\d{1,2}):(\d{2})(?::\d{2}(?:\.\d+)?)?$/;

/**
 * "09:30" (or "09:30:00", as a LocalTime may come back) to a dayjs for TimePicker, or null.
 */
export function parseHHmm(value) {
  if (!value || typeof value !== 'string') return null;
  const match = HH_MM.exec(value.trim());
  if (!match) return null;
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour > 23 || minute > 59) return null;
  return dayjs().hour(hour).minute(minute).second(0).millisecond(0);
}

/** A TimePicker value back to "HH:mm", or null when cleared. */
export function formatHHmm(value) {
  return value ? dayjs(value).format('HH:mm') : null;
}
