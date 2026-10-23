import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MyWfh } from './MyWfh.jsx';
import { portalService } from '../portalService.js';

describe('MyWfh Panel (W-74)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders WFH balance meter, requests table, and zero LOP badge', async () => {
    vi.spyOn(portalService, 'getWfhRequests').mockResolvedValue([
      {
        id: 'wfh-req-1',
        employeeId: 'emp-1',
        employeeName: 'Krushna Sonalkar',
        employeeNumber: 'EMP001',
        fromDate: '2026-11-02',
        toDate: '2026-11-03',
        workingDays: 2.0,
        halfDay: false,
        reason: 'Focus sprint on backend engine',
        status: 'PENDING',
        createdAt: '2026-10-23T10:00:00Z',
      },
    ]);

    vi.spyOn(portalService, 'getWfhBalance').mockResolvedValue({
      employeeId: 'emp-1',
      wfhYear: '2026',
      entitledDays: 12.0,
      consumedDays: 3.0,
      pendingDays: 2.0,
      availableDays: 7.0,
      monthlyLimit: 2.0,
      monthConsumed: 1.0,
    });

    render(<MyWfh />);

    await waitFor(() => {
      expect(screen.getByText('Work From Home (WFH)')).toBeDefined();
    });

    // Check Balance Card displays
    expect(screen.getByTestId('wfh-balance-card')).toBeDefined();
    expect(screen.getByText('Zero LOP Guarantee')).toBeDefined();
    expect(screen.getByText('7')).toBeDefined(); // 7 available days
    expect(screen.getByText('3')).toBeDefined(); // 3 consumed days
    expect(screen.getByText('2')).toBeDefined(); // 2 pending days

    // Check Requests table
    expect(screen.getByText('Focus sprint on backend engine')).toBeDefined();
    expect(screen.getByText('Pending Approval')).toBeDefined();
    expect(screen.getByTestId('apply-wfh-btn')).toBeDefined();
    expect(screen.getByTestId('withdraw-wfh-wfh-req-1')).toBeDefined();
  });
});
