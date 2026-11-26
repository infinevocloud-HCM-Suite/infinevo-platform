import { describe, it, expect } from 'vitest';
import { feedPaths, splitAnswer } from './answerLinks.js';

const FEED = new Set(['/leave/types', '/employees', '/payroll/runs']);

describe('splitAnswer (W-114.13)', () => {
  it('a link to a feed path becomes a link part; the text around it stays text', () => {
    expect(splitAnswer('Open [Leave types](app:/leave/types) now.', FEED)).toEqual([
      { text: 'Open ' },
      { label: 'Leave types', path: '/leave/types' },
      { text: ' now.' },
    ]);
  });

  it('a path not in the feed stays text, brackets and all', () => {
    const text = 'See [Users](app:/users).';
    expect(splitAnswer(text, FEED)).toEqual([{ text }]);
  });

  it('other schemes, a host, and raw HTML all stay text', () => {
    [
      '[x](https://evil.example)',
      '[x](javascript:alert(1))',
      '[x](app://evil.example)',
      '[x](app:/leave/types?next=https://evil.example)',
      '<a href="/leave/types">x</a>',
      '[x](/leave/types)',
    ].forEach((text) => {
      expect(splitAnswer(text, FEED)).toEqual([{ text }]);
    });
  });

  it('two links in one answer', () => {
    const parts = splitAnswer('[A](app:/leave/types) and [B](app:/employees)', FEED);
    expect(parts.filter((p) => p.path).map((p) => p.path)).toEqual(['/leave/types', '/employees']);
  });

  it('a label with a line break, or over 80 characters, stays text', () => {
    expect(splitAnswer('[Leave\ntypes](app:/leave/types)', FEED)).toEqual([{ text: '[Leave\ntypes](app:/leave/types)' }]);
    const long = `[${'x'.repeat(81)}](app:/leave/types)`;
    expect(splitAnswer(long, FEED)).toEqual([{ text: long }]);
  });

  it('a sub-route links only when it is in the allowed list; a path with an id never does', () => {
    const withTarget = new Set([...FEED, '/employees/new']);
    expect(splitAnswer('[New](app:/employees/new)', withTarget)).toEqual([{ label: 'New', path: '/employees/new' }]);
    expect(splitAnswer('[New](app:/employees/new)', FEED)).toEqual([{ text: '[New](app:/employees/new)' }]);
    expect(splitAnswer('[Run](app:/payroll/runs/123)', withTarget)).toEqual([{ text: '[Run](app:/payroll/runs/123)' }]);
  });

  it('feedPaths collects every path, children included', () => {
    const items = [
      { key: 'core.leave', path: '/leave/types', children: [{ key: 'core.leave.requests', path: '/leave/requests' }] },
      { key: 'core.me', path: '/me' },
    ];
    expect([...feedPaths(items)].sort()).toEqual(['/leave/requests', '/leave/types', '/me']);
    expect(feedPaths(null).size).toBe(0);
  });
});
