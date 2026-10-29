import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MyWfh } from './MyWfh.jsx';
import { portalService } from '../portalService.js';
import { approvalService } from '../../../core/approvals/approvalService.js';

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
    expect(screen.getAllByText('Pending Approval').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId('apply-wfh-btn')).toBeDefined();
    expect(screen.getByTestId('withdraw-wfh-wfh-req-1')).toBeDefined();
  });

  it('opens view details modal and renders multi-stage approval trail for employee (D-126)', async () => {
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
        approvalInstanceId: 'inst-wfh-123',
        createdAt: '2026-10-23T10:00:00Z',
      },
    ]);
    vi.spyOn(portalService, 'getWfhBalance').mockResolvedValue({
      employeeId: 'emp-1',
      availableDays: 7.0,
    });
    const historySpy = vi.spyOn(approvalService, 'history').mockResolvedValue({
      steps: [
        {
          id: 'step-1',
          stepIndex: 0,
          decision: 'APPROVED',
          assigneeName: 'Karmaveer',
          decidedAt: '2026-10-24T14:30:00Z',
          comment: 'Approved for focus sprint',
        },
        {
          id: 'step-2',
          stepIndex: 1,
          decision: null,
          assigneeName: null,
          role: 'HR',
        },
      ],
    });

    render(<MyWfh />);

    await waitFor(() => {
      expect(screen.getByTestId('view-wfh-wfh-req-1')).toBeDefined();
    });

    fireEvent.click(screen.getByTestId('view-wfh-wfh-req-1'));

    await waitFor(() => {
      expect(historySpy).toHaveBeenCalledWith('inst-wfh-123');
    });

    await waitFor(() => {
      expect(screen.getByText('Approval Trail')).toBeDefined();
      expect(screen.getByText('Step 1 — Approved')).toBeDefined();
      expect(screen.getByText(/by Karmaveer/)).toBeDefined();
      expect(screen.getByText(/Approved for focus sprint/)).toBeDefined();
      expect(screen.getByText('Step 2 — Pending Approval')).toBeDefined();
    });
  });
});
