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

  it('W-114.28: each set-up step opens the list that holds what was made', () => {
    const cases = [
      ['core.org.department.create', { id: 'd-1', code: 'FIN' }, '/org/departments', 'Open departments'],
      ['core.org.designation.create', { id: 'g-1', code: 'MGR' }, '/org/designations', 'Open designations'],
      ['core.org.location.create', { id: 'l-1', code: 'PUN' }, '/org/work-locations', 'Open work locations'],
      ['core.holiday.add', { calendarId: 'c 1', holidayId: 'h-1' }, '/holidays/c%201', 'Open the holiday calendar'],
      ['core.user.invite', { email: 'a@b.in' }, '/users', 'Open users and invitations'],
      [
        'payroll.bank_file_template.create',
        { templateId: 't-1' },
        '/payroll/settings/bank-files',
        'Open bank file templates',
      ],
    ];
    for (const [code, result, path, label] of cases) {
      expect(linkFor(code, result)).toBe(path);
      expect(linkFor(code, {})).toBeNull();
      expect(linkLabelFor(code)).toBe(label);
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
