import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { InstanceDetail } from './InstanceDetail.jsx';
import { approvalService } from './approvalService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./approvalService.js', () => ({
  approvalService: {
    get: vi.fn(),
    history: vi.fn(),
    reassign: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

describe('InstanceDetail component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders instance and history trail with delegatedFrom and escalatedFrom information', async () => {
    approvalService.get.mockResolvedValueOnce({
      id: 'inst-1',
      flowType: 'LEAVE',
      status: 'PENDING',
      currentStepIndex: 1,
      itemRef: 'LEAVE req-123',
    });

    approvalService.history.mockResolvedValueOnce({
      steps: [
        {
          stepIndex: 0,
          decision: 'APPROVED',
          comment: 'Approved by delegate',
          decidedAt: '2026-10-01T10:00:00Z',
          delegatedFromEmployeeId: 'emp-mgr-1',
          delegatedFromEmployeeName: 'Vikram Shah',
          assigneeEmployeeId: 'emp-delegate-2',
          assigneeName: 'Meera Iyer',
        },
        {
          stepIndex: 1,
          eventType: 'ESCALATED',
          reason: 'Timed out, escalated',
          occurredAt: '2026-10-02T10:00:00Z',
          escalatedFromEmployeeId: 'emp-mgr-1',
          escalatedFromEmployeeName: 'Vikram Shah',
          assigneeEmployeeId: 'emp-director-3',
          assigneeName: 'Ravi Kumar',
        },
      ],
    });

    render(
      <MemoryRouter>
        <InstanceDetail instanceId="inst-1" open={true} onClose={vi.fn()} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Approval Instance Details')).toBeDefined();
      expect(screen.getByText('inst-1')).toBeDefined();
      expect(screen.getByText(/Delegated from: Vikram Shah/)).toBeDefined();
      expect(screen.getByText(/Escalated from: Vikram Shah/)).toBeDefined();
      expect(screen.getByText(/Assignee: Meera Iyer/)).toBeDefined();
    });
    expect(screen.queryByText(/emp-mgr-1/)).toBeNull();
    expect(screen.queryByText(/emp-delegate-2/)).toBeNull();
  });

  it('names the requester and each step assignee, never their ids (D-79)', async () => {
    approvalService.get.mockResolvedValueOnce({
      id: 'inst-3',
      flowType: 'LEAVE',
      status: 'APPROVED',
      subjectEmployeeId: 'emp-asha',
      subjectEmployeeName: 'Asha Rao',
      steps: [
        {
          id: 'step-1',
          stepIndex: 0,
          approverKind: 'REPORTING_MANAGER',
          assigneeEmployeeId: 'emp-vikram',
          assigneeEmployeeName: 'Vikram Shah',
          decision: 'APPROVED',
          approvedAmount: '1500.00',
          decidedAt: '2026-10-01T10:00:00Z',
        },
      ],
    });
    approvalService.history.mockResolvedValueOnce({ steps: [] });

    render(
      <MemoryRouter>
        <InstanceDetail instanceId="inst-3" />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Asha Rao')).toBeDefined();
      expect(screen.getByText('Vikram Shah')).toBeDefined();
    });
    expect(screen.getByText('1 Oct 2026')).toBeDefined();
    expect(screen.getByText(/1,500.00/)).toBeDefined();
    expect(screen.queryByText('emp-asha')).toBeNull();
    expect(screen.queryByText('emp-vikram')).toBeNull();
  });

  it('loads get and history independently so instance still renders if history fails', async () => {
    approvalService.get.mockResolvedValueOnce({
      id: 'inst-2',
      flowType: 'REIMBURSEMENT',
      status: 'PENDING',
      currentStepIndex: 0,
    });
    approvalService.history.mockRejectedValueOnce(new Error('Forbidden to view history'));

    render(
      <MemoryRouter>
        <InstanceDetail instanceId="inst-2" open={true} onClose={vi.fn()} />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('Approval Instance Details')).toBeDefined();
      expect(screen.getByText('inst-2')).toBeDefined();
      expect(screen.getByText('No history events recorded.')).toBeDefined();
    });
  });
});
