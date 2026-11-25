/**
 * Country code to standard / commonly used IANA timezones (W-116).
 */
export const COUNTRY_TIMEZONES = {
  IN: ['Asia/Kolkata'],
  US: [
    'America/New_York',
    'America/Chicago',
    'America/Denver',
    'America/Los_Angeles',
    'America/Anchorage',
    'Pacific/Honolulu',
    'America/Phoenix',
  ],
  GB: ['Europe/London'],
  AE: ['Asia/Dubai'],
  SG: ['Asia/Singapore'],
  AU: [
    'Australia/Sydney',
    'Australia/Melbourne',
    'Australia/Brisbane',
    'Australia/Perth',
    'Australia/Adelaide',
  ],
  CA: [
    'America/Toronto',
    'America/Vancouver',
    'America/Edmonton',
    'America/Winnipeg',
    'America/Halifax',
  ],
  DE: ['Europe/Berlin'],
  FR: ['Europe/Paris'],
  JP: ['Asia/Tokyo'],
  SA: ['Asia/Riyadh'],
  QA: ['Asia/Qatar'],
  OM: ['Asia/Muscat'],
  KW: ['Asia/Kuwait'],
  BH: ['Asia/Bahrain'],
  MY: ['Asia/Kuala_Lumpur'],
  NZ: ['Pacific/Auckland'],
  IE: ['Europe/Dublin'],
  NL: ['Europe/Amsterdam'],
  CH: ['Europe/Zurich'],
  SE: ['Europe/Stockholm'],
  ES: ['Europe/Madrid'],
  IT: ['Europe/Rome'],
  PH: ['Asia/Manila'],
  ID: ['Asia/Jakarta'],
  TH: ['Asia/Bangkok'],
  VN: ['Asia/Ho_Chi_Minh'],
  ZA: ['Africa/Johannesburg'],
};

/**
 * Returns the full list of supported IANA timezones.
 */
export function getAllTimezones() {
  if (typeof Intl !== 'undefined' && typeof Intl.supportedValuesOf === 'function') {
    try {
      return Intl.supportedValuesOf('timeZone');
    } catch {
      // Fallback if unsupported
    }
  }
  return [
    'Africa/Cairo',
    'Africa/Johannesburg',
    'Africa/Lagos',
    'America/Anchorage',
    'America/Bogota',
    'America/Buenos_Aires',
    'America/Chicago',
    'America/Denver',
    'America/Halifax',
    'America/Los_Angeles',
    'America/Mexico_City',
    'America/New_York',
    'America/Phoenix',
    'America/Santiago',
    'America/Sao_Paulo',
    'America/Toronto',
    'America/Vancouver',
    'Asia/Bangkok',
    'Asia/Dubai',
    'Asia/Hong_Kong',
    'Asia/Jakarta',
    'Asia/Jerusalem',
    'Asia/Kolkata',
    'Asia/Kuala_Lumpur',
    'Asia/Manila',
    'Asia/Qatar',
    'Asia/Riyadh',
    'Asia/Seoul',
    'Asia/Shanghai',
    'Asia/Singapore',
    'Asia/Tokyo',
    'Australia/Adelaide',
    'Australia/Brisbane',
    'Australia/Melbourne',
    'Australia/Perth',
    'Australia/Sydney',
    'Europe/Amsterdam',
    'Europe/Berlin',
    'Europe/Dublin',
    'Europe/London',
    'Europe/Madrid',
    'Europe/Paris',
    'Europe/Rome',
    'Europe/Zurich',
    'Pacific/Auckland',
    'Pacific/Honolulu',
    'UTC',
  ];
}

/**
 * Returns the common timezones for a given country code, or empty list if none known.
 */
export function getTimezonesForCountry(countryCode) {
  if (!countryCode) return [];
  const code = countryCode.toUpperCase();
  return COUNTRY_TIMEZONES[code] || [];
}
