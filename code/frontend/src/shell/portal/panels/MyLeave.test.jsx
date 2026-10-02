import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MyLeave } from './MyLeave.jsx';
import { MyDocuments } from './MyDocuments.jsx';
import { portalService } from '../portalService.js';

// The payloads below are the server's own shapes: a Spring page for leave requests
// (LeaveRequestResponse rows under `content`), a plain list of LeaveBalanceResponse, and a
// plain list of DocumentResponse. A panel that reads any other field name shows an empty
// table and zero balances while looking finished.
describe('Portal panels read the fields the server sends (W-25)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('MyLeave shows the requests in a page and the remaining days of each balance', async () => {
    vi.spyOn(portalService, 'getLeaveRequests').mockResolvedValue({
      content: [
        {
          id: 'req-1',
          leaveTypeId: 'type-cl',
          fromDate: '2026-10-05',
          toDate: '2026-10-06',
          workingDays: 2,
          reason: 'Family function',
          status: 'APPROVED',
        },
      ],
      totalElements: 1,
    });
    vi.spyOn(portalService, 'getLeaveBalances').mockResolvedValue([
      {
        leaveTypeId: 'type-cl',
        leaveTypeCode: 'CL',
        leaveTypeName: 'Casual Leave',
        entitlementDays: 12,
        consumedDays: 2,
        remainingDays: 10,
      },
    ]);

    render(<MyLeave />);

    await waitFor(() => {
      expect(screen.getByText('Family function')).toBeDefined();
    });
    expect(screen.getByText('2 day(s)')).toBeDefined();
    expect(screen.getByText('APPROVED')).toBeDefined();
    // The balance card and the request row both name the type, from the balance.
    expect(screen.getAllByText('Casual Leave').length).toBe(2);
    expect(screen.getByText('10')).toBeDefined();
    expect(screen.getByText('Used: 2 days')).toBeDefined();
  });

  it('MyDocuments shows the file name and kind', async () => {
    vi.spyOn(portalService, 'getDocuments').mockResolvedValue([
      { id: 'doc-1', kind: 'EMPLOYEE_DOCUMENT', fileName: 'offer-letter.pdf', createdAt: '2026-09-01T10:00:00Z' },
    ]);

    render(<MyDocuments />);

    await waitFor(() => {
      expect(screen.getByText('offer-letter.pdf')).toBeDefined();
    });
    expect(screen.getByText('EMPLOYEE_DOCUMENT')).toBeDefined();
    expect(screen.queryByText('VERIFIED')).toBeNull();
  });
});
