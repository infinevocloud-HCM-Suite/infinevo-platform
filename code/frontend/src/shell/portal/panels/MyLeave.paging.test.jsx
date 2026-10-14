import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MyLeave, REQUESTS_PAGE_SIZE } from './MyLeave.jsx';
import { portalService } from '../portalService.js';

// D-80: My Leave read the first 25 requests and nothing past them. The server pages them; the panel
// asks for page 0 and offers "Load more" until every request is on screen.
function request(n) {
  return {
    id: `req-${n}`,
    leaveTypeId: 'type-cl',
    leaveTypeName: 'Casual Leave',
    fromDate: '2026-10-05',
    toDate: '2026-10-05',
    workingDays: 1,
    reason: `Reason ${n}`,
    status: 'APPROVED',
  };
}

describe('MyLeave pages its requests on the server (D-80)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(portalService, 'getLeaveBalances').mockResolvedValue([]);
  });

  it('asks for the first page, then the next on Load more, until all are loaded', async () => {
    const first = Array.from({ length: REQUESTS_PAGE_SIZE }, (_, i) => request(i + 1));
    const second = [request(REQUESTS_PAGE_SIZE + 1), request(REQUESTS_PAGE_SIZE + 2)];
    const total = first.length + second.length;
    const spy = vi
      .spyOn(portalService, 'getLeaveRequests')
      .mockResolvedValueOnce({ content: first, totalElements: total })
      .mockResolvedValueOnce({ content: second, totalElements: total });

    render(<MyLeave />);

    const more = await screen.findByRole('button', { name: `Load more (${REQUESTS_PAGE_SIZE} of ${total})` });
    expect(spy).toHaveBeenCalledWith({ page: 0, size: REQUESTS_PAGE_SIZE });

    fireEvent.click(more);

    await waitFor(() => {
      expect(spy).toHaveBeenCalledWith({ page: 1, size: REQUESTS_PAGE_SIZE });
    });
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /Load more/ })).toBeNull();
    });
  });

  it('offers no Load more when the first page holds everything', async () => {
    vi.spyOn(portalService, 'getLeaveRequests').mockResolvedValue({ content: [request(1)], totalElements: 1 });

    render(<MyLeave />);

    await waitFor(() => {
      expect(screen.getByText('Reason 1')).toBeDefined();
    });
    expect(screen.queryByRole('button', { name: /Load more/ })).toBeNull();
  });
});
