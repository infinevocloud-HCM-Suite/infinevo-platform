import { describe, it, expect } from 'vitest';
import { linkFor, linkLabelFor, viewLinkFor } from './actionLinks.js';

describe('actionLinks', () => {
  it('builds the leave request link from leaveRequestId', () => {
    expect(linkFor('core.leave.apply', { leaveRequestId: 'lr-1', status: 'PENDING' })).toBe('/me/leave/requests/lr-1');
    expect(linkLabelFor('core.leave.apply')).toBe('Open the leave request');
  });

  it('links a decided approval to its request page (W-114.18)', () => {
    expect(linkFor('core.approval.decide', { instanceId: 'ai-1', decision: 'APPROVED' })).toBe('/approvals/ai-1');
    expect(linkFor('core.approval.decide', {})).toBeNull();
    expect(linkLabelFor('core.approval.decide')).toBe('Open the approval');
  });

  it('viewLinkFor accepts a plain app path only', () => {
    expect(viewLinkFor('/approvals/abc-1')).toBe('/approvals/abc-1');
    expect(viewLinkFor('//evil.example')).toBeNull();
    expect(viewLinkFor('https://evil.example')).toBeNull();
    expect(viewLinkFor('/a?b=1')).toBeNull();
    expect(viewLinkFor(null)).toBeNull();
  });

  it('links an added employee to their page (W-114.20)', () => {
    expect(linkFor('core.employee.create', { employeeId: 'e-1', employeeNumber: 'E-207' })).toBe('/employees/e-1');
    expect(linkFor('core.employee.create', {})).toBeNull();
  });

  it('W-114.17: withdraw and cancel of leave open the request; every WFH action opens the WFH tab', () => {
    for (const code of ['core.leave.withdraw', 'core.leave.cancel']) {
      expect(linkFor(code, { leaveRequestId: 'lr 2', status: 'WITHDRAWN' })).toBe('/me/leave/requests/lr%202');
      expect(linkLabelFor(code)).toBe('Open the leave request');
    }
    for (const code of ['core.wfh.apply', 'core.wfh.withdraw', 'core.wfh.cancel']) {
      expect(linkFor(code, { wfhRequestId: 'w-1', status: 'PENDING' })).toBe('/me/wfh');
      expect(linkFor(code, {})).toBeNull();
      expect(linkLabelFor(code)).toBe('Open work from home');
    }
  });

  it('returns nothing for an unknown code, or a result without the id', () => {
    expect(linkFor('core.made.up', { leaveRequestId: 'lr-1' })).toBeNull();
    expect(linkFor(undefined, {})).toBeNull();
    expect(linkFor('toString', {})).toBeNull();
    expect(linkFor('core.leave.apply', null)).toBeNull();
    expect(linkFor('core.leave.apply', {})).toBeNull();
  });
});
