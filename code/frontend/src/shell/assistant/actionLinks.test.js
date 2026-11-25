import { describe, it, expect } from 'vitest';
import { linkFor, linkLabelFor } from './actionLinks.js';

describe('actionLinks', () => {
  it('builds the leave request link from leaveRequestId', () => {
    expect(linkFor('core.leave.apply', { leaveRequestId: 'lr-1', status: 'PENDING' })).toBe('/me/leave/requests/lr-1');
    expect(linkLabelFor('core.leave.apply')).toBe('Open the leave request');
  });

  it('returns nothing for an unknown code, or a result without the id', () => {
    expect(linkFor('core.made.up', { leaveRequestId: 'lr-1' })).toBeNull();
    expect(linkFor(undefined, {})).toBeNull();
    expect(linkFor('toString', {})).toBeNull();
    expect(linkFor('core.leave.apply', null)).toBeNull();
    expect(linkFor('core.leave.apply', {})).toBeNull();
  });
});
