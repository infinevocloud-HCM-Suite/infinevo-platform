import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mobileApprovalService } from './mobileApprovalService.js';
import { apiClient } from '../../shared/api/client.js';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('mobileApprovalService (W-98.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getPendingApprovals', () => {
    it('uses 20 per page by default and maps server totalElements to counts', async () => {
      const mockPending = [
        {
          id: 'step-1',
          instanceId: 'inst-1',
          flowType: 'LEAVE',
          summary: 'Casual Leave (2 days)',
          subjectEmployeeName: 'Priya Sharma',
          stepIndex: 0,
          totalSteps: 2,
          createdAt: '2026-11-15T10:00:00Z',
          reason: 'Personal work',
          days: 2,
        },
        {
          id: 'step-2',
          instanceId: 'inst-2',
          flowType: 'WORK_FROM_HOME',
          summary: 'WFH Request (1 day)',
          subjectEmployeeName: 'Rahul Verma',
          stepIndex: 0,
          totalSteps: 1,
          createdAt: '2026-11-16T08:30:00Z',
          reason: 'Plumber visit',
          days: 1,
        },
      ];

      apiClient.get.mockResolvedValueOnce({
        data: { content: mockPending, totalElements: 45, totalPages: 3 },
      });

      const res = await mobileApprovalService.getPendingApprovals();

      expect(apiClient.get).toHaveBeenCalledWith('/v1/approvals/pending', {
        params: { page: 0, size: 20 },
      });
      expect(res.rows).toHaveLength(2);
      expect(res.total).toBe(2);
      // Counts use server totalElements
      expect(res.totalElements).toBe(45);
      expect(res.countsByType.ALL).toBe(45);
      expect(res.countsByType.LEAVE).toBe(1);
      expect(res.countsByType.WORK_FROM_HOME).toBe(1);
      expect(res.hasMore).toBe(true);
      expect(res.rows[0].subjectEmployeeName).toBe('Priya Sharma');
      expect(res.rows[0].daysCount).toBe(2);
    });

    it('passes explicit paging parameters', async () => {
      apiClient.get.mockResolvedValueOnce({
        data: { content: [], totalElements: 30, totalPages: 3 },
      });

      const res = await mobileApprovalService.getPendingApprovals({ page: 2, size: 10 });

      expect(apiClient.get).toHaveBeenCalledWith('/v1/approvals/pending', {
        params: { page: 2, size: 10 },
      });
      expect(res.hasMore).toBe(false);
    });

    it('filters items by flowType when specified, including OTHER for unmapped types', async () => {
      const mockPending = [
        {
          id: 'step-1',
          instanceId: 'inst-1',
          flowType: 'LEAVE',
          summary: 'Leave',
        },
        {
          id: 'step-2',
          instanceId: 'inst-2',
          flowType: 'REIMBURSEMENT',
          summary: 'Expense Claim',
        },
      ];

      apiClient.get.mockResolvedValueOnce({
        data: { content: mockPending, totalElements: 2 },
      });

      const resOther = await mobileApprovalService.getPendingApprovals({
        flowType: 'OTHER',
      });

      expect(resOther.rows).toHaveLength(1);
      expect(resOther.rows[0].flowType).toBe('REIMBURSEMENT');
    });
  });

  describe('getInstanceDetail', () => {
    it('fetches instance metadata and multi-step approval history', async () => {
      apiClient.get
        .mockResolvedValueOnce({
          data: { id: 'inst-1', flowType: 'LEAVE', status: 'PENDING' },
        })
        .mockResolvedValueOnce({
          data: [
            {
              stepIndex: 0,
              status: 'APPROVED',
              approverName: 'Manager Dave',
              comment: 'Approved for coverage',
            },
          ],
        });

      const res = await mobileApprovalService.getInstanceDetail('inst-1');

      expect(apiClient.get).toHaveBeenCalledWith('/v1/approvals/inst-1');
      expect(apiClient.get).toHaveBeenCalledWith('/v1/approvals/inst-1/history');
      expect(res.instance.id).toBe('inst-1');
      expect(res.history).toHaveLength(1);
      expect(res.history[0].approverName).toBe('Manager Dave');
    });

    it('throws error when instanceId is missing', async () => {
      await expect(mobileApprovalService.getInstanceDetail('')).rejects.toThrow(
        'Instance ID is required'
      );
    });
  });

  describe('decide', () => {
    it('submits APPROVED decision with stepId in URL and decision in body', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { success: true } });

      const res = await mobileApprovalService.decide({
        stepId: 'step-xyz-123',
        decision: 'APPROVED',
        comment: 'Approved as discussed',
      });

      expect(apiClient.post).toHaveBeenCalledWith(
        '/v1/approvals/steps/step-xyz-123/decide',
        {
          decision: 'APPROVED',
          comment: 'Approved as discussed',
        }
      );
      expect(res.success).toBe(true);
    });

    it('submits REJECTED decision with stepId in URL and required reason', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { success: true } });

      const res = await mobileApprovalService.decide({
        stepId: 'step-xyz-456',
        decision: 'REJECTED',
        comment: 'Sprint deadline conflict',
      });

      expect(apiClient.post).toHaveBeenCalledWith(
        '/v1/approvals/steps/step-xyz-456/decide',
        {
          decision: 'REJECTED',
          comment: 'Sprint deadline conflict',
        }
      );
      expect(res.success).toBe(true);
    });

    it('throws error when rejecting without a reason', async () => {
      await expect(
        mobileApprovalService.decide({
          stepId: 'step-1',
          decision: 'REJECTED',
          comment: '   ',
        })
      ).rejects.toThrow('Rejection reason is required');
    });

    it('throws error when decision is neither APPROVED nor REJECTED', async () => {
      await expect(
        mobileApprovalService.decide({
          stepId: 'step-1',
          decision: 'MAYBE',
        })
      ).rejects.toThrow('Decision must be either APPROVED or REJECTED');
    });

    it('throws error when stepId is empty', async () => {
      await expect(
        mobileApprovalService.decide({
          stepId: '',
          decision: 'APPROVED',
        })
      ).rejects.toThrow('Step ID is required');
    });
  });
});
