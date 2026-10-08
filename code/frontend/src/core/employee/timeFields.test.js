import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  FALLBACK_TIME_ZONES,
  listTimeZones,
  defaultTimeZone,
  timeZoneOptions,
  parseHHmm,
  formatHHmm,
} from './timeFields.js';

describe('timeFields (D-41)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('lists the browser IANA zones when Intl can, with the modern names ICU lacks', () => {
    const zones = listTimeZones();
    expect(zones.length).toBeGreaterThan(FALLBACK_TIME_ZONES.length);
    for (const zone of Intl.supportedValuesOf('timeZone')) {
      expect(zones).toContain(zone);
    }
    // ICU says Asia/Calcutta; the merge makes Asia/Kolkata findable too.
    expect(zones).toContain('Asia/Kolkata');
    expect(zones).toContain('Europe/London');
    expect(new Set(zones).size).toBe(zones.length);
  });

  it('falls back to a fixed list when Intl.supportedValuesOf is unavailable', () => {
    const original = Intl.supportedValuesOf;
    try {
      Intl.supportedValuesOf = undefined;
      expect(listTimeZones()).toBe(FALLBACK_TIME_ZONES);
    } finally {
      Intl.supportedValuesOf = original;
    }
  });

  it('falls back when Intl.supportedValuesOf throws', () => {
    vi.spyOn(Intl, 'supportedValuesOf').mockImplementation(() => {
      throw new RangeError('unsupported');
    });
    expect(listTimeZones()).toBe(FALLBACK_TIME_ZONES);
  });

  it('defaults to the browser zone', () => {
    expect(defaultTimeZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
  });

  it('keeps a stored zone the list does not have', () => {
    const options = timeZoneOptions('Etc/GMT+5');
    expect(options[0]).toEqual({ value: 'Etc/GMT+5', label: 'Etc/GMT+5' });
    expect(timeZoneOptions('Asia/Kolkata').filter((o) => o.value === 'Asia/Kolkata')).toHaveLength(1);
  });

  it('parses HH:mm and HH:mm:ss, and rejects anything else', () => {
    expect(parseHHmm('09:30').format('HH:mm')).toBe('09:30');
    expect(parseHHmm('18:00:00').format('HH:mm')).toBe('18:00');
    expect(parseHHmm('9:00 AM')).toBeNull();
    expect(parseHHmm('25:00')).toBeNull();
    expect(parseHHmm('')).toBeNull();
    expect(parseHHmm(null)).toBeNull();
  });

  it('formats a picked time as HH:mm, and a cleared one as null', () => {
    expect(formatHHmm(parseHHmm('07:05'))).toBe('07:05');
    expect(formatHHmm(null)).toBeNull();
  });
});
