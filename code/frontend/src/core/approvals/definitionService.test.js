import { describe, it, expect, vi, beforeEach } from 'vitest';
import { definitionService } from './definitionService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('definitionService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list with optional flowType and asOf and unwraps data', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'def-1', flowType: 'LEAVE' }] });
    const res = await definitionService.list('LEAVE', '2026-09-29');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/approval-definitions', {
      params: { flowType: 'LEAVE', asOf: '2026-09-29' },
    });
    expect(res).toEqual([{ id: 'def-1', flowType: 'LEAVE' }]);

    apiClient.get.mockResolvedValueOnce({ data: [] });
    await definitionService.list();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/approval-definitions', {
      params: {},
    });
  });

  it('calls save with flowType and body and unwraps data', async () => {
    apiClient.put.mockResolvedValueOnce({ data: { flowType: 'LEAVE', steps: [] } });
    const res = await definitionService.save('LEAVE', {
      steps: [{ stepIndex: 0, approverKind: 'REPORTING_MANAGER' }],
    });
    expect(apiClient.put).toHaveBeenCalledWith('/v1/approval-definitions/LEAVE', {
      steps: [{ stepIndex: 0, approverKind: 'REPORTING_MANAGER' }],
    });
    expect(res).toEqual({ flowType: 'LEAVE', steps: [] });
  });
});
