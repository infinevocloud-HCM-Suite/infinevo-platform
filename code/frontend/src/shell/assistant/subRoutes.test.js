/**
 * W-114.13 — every entry of the backend's fixed sub-route list (`help/sub-routes.json`) is a page the
 * frontend really mounts, so a link the helper offers never opens a page that is gone. The route tables
 * are read as text: a route matches when it is the same path, or the same path with `:param` segments.
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const SUB_ROUTES = resolve(HERE, '../../../../backend/core/src/main/resources/help/sub-routes.json');
const TABLES = ['../routes.js', '../../core/index.js', '../../hrms/index.js', '../../payroll/index.js'];

function routePaths() {
  return TABLES.flatMap((file) => {
    const source = readFileSync(resolve(HERE, file), 'utf8');
    return [...source.matchAll(/path:\s*'([^']+)'/g)].map((m) => m[1]);
  });
}

function matches(route, path) {
  const a = route.split('/');
  const b = path.split('/');
  return a.length === b.length && a.every((seg, i) => seg.startsWith(':') || seg === b[i]);
}

describe('help/sub-routes.json', () => {
  const entries = JSON.parse(readFileSync(SUB_ROUTES, 'utf8'));
  const routes = routePaths();

  it('is read, and the route tables are read', () => {
    expect(entries.length).toBeGreaterThan(0);
    expect(routes).toContain('/employees/new');
  });

  it('every path is a route the frontend mounts', () => {
    const missing = entries.map((e) => e.path).filter((path) => !routes.some((route) => matches(route, path)));
    expect(missing).toEqual([]);
  });

  it('every path sits under its parent', () => {
    expect(entries.filter((e) => !e.path.startsWith(`${e.parent}/`))).toEqual([]);
  });
});
