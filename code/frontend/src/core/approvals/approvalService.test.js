import { describe, it, expect, vi, beforeEach } from 'vitest';
import { approvalService } from './approvalService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('approvalService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls pending with pagination parameters and unwraps data', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { content: [], totalElements: 0 } });
    const res = await approvalService.pending(1, 10);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/approvals/pending', {
      params: { page: 1, size: 10 },
    });
    expect(res).toEqual({ content: [], totalElements: 0 });
  });

  it('calls get and history with instanceId and unwraps data', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { id: 'inst-1' } });
    const inst = await approvalService.get('inst-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/approvals/inst-1');
    expect(inst).toEqual({ id: 'inst-1' });

    apiClient.get.mockResolvedValueOnce({ data: { events: [] } });
    const hist = await approvalService.history('inst-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/approvals/inst-1/history');
    expect(hist).toEqual({ events: [] });
  });

  it('calls decide with stepId and payload', async () => {
    apiClient.post.mockResolvedValueOnce({ data: {} });
    await approvalService.decide('step-1', {
      decision: 'APPROVED',
      comment: 'Looks good',
      approvedAmount: '1500.00',
    });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/approvals/steps/step-1/decide', {
      decision: 'APPROVED',
      comment: 'Looks good',
      approvedAmount: '1500.00',
    });
  });

  it('calls reassign with instanceId and payload', async () => {
    apiClient.post.mockResolvedValueOnce({ data: {} });
    await approvalService.reassign('inst-1', {
      employeeId: 'emp-2',
      reason: 'On leave',
    });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/approvals/inst-1/reassign', {
      employeeId: 'emp-2',
      reason: 'On leave',
    });
  });
});
