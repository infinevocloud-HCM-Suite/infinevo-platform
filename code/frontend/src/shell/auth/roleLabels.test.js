/**
 * A chip shows words, never a raw role code: every code the backend seeds has a label, and an
 * unknown code still falls back to words.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
import { ROLE_LABELS, roleLabel } from './roleLabels.js';

const CORE_MIGRATIONS = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../../../../backend/migration/src/main/resources/db/migration/core',
);

/**
 * The script that defines `core.seed_system_roles` last - the highest version wins in Flyway - so a
 * later rewrite of the seed is the one checked here.
 */
function latestSeedScript() {
  const scripts = readdirSync(CORE_MIGRATIONS)
    .map((name) => ({ name, version: Number(/^V(\d+)__/.exec(name)?.[1]) }))
    .filter(({ version }) => Number.isFinite(version))
    .sort((a, b) => b.version - a.version);
  for (const { name } of scripts) {
    const source = readFileSync(resolve(CORE_MIGRATIONS, name), 'utf8');
    if (/CREATE OR REPLACE FUNCTION core\.seed_system_roles\s*\(/.test(source)) return source;
  }
  throw new Error(`no script in ${CORE_MIGRATIONS} defines core.seed_system_roles`);
}

/** The codes `core.seed_system_roles` inserts: the rows of its `INSERT INTO core.role` VALUES list. */
function seededRoleCodes() {
  const source = latestSeedScript();
  const codes = new Set();
  for (const match of source.matchAll(/\(p_tenant_id, '([a-z-]+)', '[^']+', true\)/g)) codes.add(match[1]);
  return [...codes].sort();
}

describe('roleLabels', () => {
  it('has a label for every role code the backend seeds', () => {
    const codes = seededRoleCodes();
    expect(codes.length).toBeGreaterThan(0);
    expect(codes.filter((c) => !ROLE_LABELS[c])).toEqual([]);
  });

  it('returns the mapped label for a known code', () => {
    expect(roleLabel('hr')).toBe('HR');
    expect(roleLabel('payroll-officer')).toBe('Payroll officer');
  });

  it('turns an unknown code into words instead of showing it raw', () => {
    expect(roleLabel('shift_lead')).toBe('Shift lead');
    expect(roleLabel('regionalManager')).toBe('Regional manager');
  });

  it('returns an empty string for a missing code', () => {
    expect(roleLabel(undefined)).toBe('');
    expect(roleLabel('')).toBe('');
  });
});
