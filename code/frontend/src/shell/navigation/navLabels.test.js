/**
 * The menu shows words, never a raw `nav.*` key: every key the backend catalogues send has an
 * English label, and an unknown key still falls back to words.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { NAV_LABELS, navLabel } from './navLabels.js';

const BACKEND = resolve(dirname(fileURLToPath(import.meta.url)), '../../../../backend');
const CATALOGUES = [
  'core/src/main/java/com/infinevo/core/navigation/NavigationCatalogue.java',
  'hrms/src/main/java/com/infinevo/hrms/navigation/HrmsNavigation.java',
  'payroll/src/main/java/com/infinevo/payroll/navigation/PayrollNavigation.java',
];

function backendLabelKeys() {
  const keys = new Set();
  for (const file of CATALOGUES) {
    const source = readFileSync(resolve(BACKEND, file), 'utf8');
    for (const match of source.matchAll(/"(nav\.[A-Za-z0-9_.]+)"/g)) keys.add(match[1]);
  }
  return [...keys].sort();
}

describe('navLabels', () => {
  it('has a label for every key the backend navigation catalogues send', () => {
    const keys = backendLabelKeys();
    expect(keys.length).toBeGreaterThan(0);
    expect(keys.filter((k) => !NAV_LABELS[k])).toEqual([]);
  });

  it('returns the mapped label for a known key', () => {
    expect(navLabel('nav.setup')).toBe('Setup');
    expect(navLabel('nav.payroll.runs')).toBe('Payroll runs');
  });

  it('turns an unknown key into words instead of showing it raw', () => {
    expect(navLabel('nav.hrms.shift_roster')).toBe('Shift roster');
    expect(navLabel('nav.payGroups')).toBe('Pay groups');
  });

  it('turns a key that names only a module into a word', () => {
    expect(navLabel('nav.hrms')).toBe('Hrms');
  });

  it('returns a value with no dot unchanged', () => {
    expect(navLabel('Core Menu Item')).toBe('Core Menu Item');
  });

  it('returns an empty string for a missing key', () => {
    expect(navLabel(undefined)).toBe('');
  });
});
