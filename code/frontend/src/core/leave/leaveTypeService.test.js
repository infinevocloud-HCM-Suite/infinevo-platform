import { describe, it, expect, vi, beforeEach } from 'vitest';
import { leaveTypeService } from './leaveTypeService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('leaveTypeService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list on /v1/leave-types', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'type-1', name: 'Annual Leave' }] });
    const res = await leaveTypeService.list();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-types', { params: undefined });
    expect(res).toEqual([{ id: 'type-1', name: 'Annual Leave' }]);
  });

  it('calls get by id', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { id: 'type-1', name: 'Annual Leave' } });
    const res = await leaveTypeService.get('type-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-types/type-1');
    expect(res).toEqual({ id: 'type-1', name: 'Annual Leave' });
  });

  it('calls create on /v1/leave-types', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'type-1' } });
    const payload = { name: 'Sick Leave', code: 'SL', unit: 'DAYS' };
    const res = await leaveTypeService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-types', payload);
    expect(res).toEqual({ id: 'type-1' });
  });

  it('calls update on /v1/leave-types/{id}', async () => {
    apiClient.put.mockResolvedValueOnce({ data: { id: 'type-1' } });
    const payload = { name: 'Updated Leave' };
    const res = await leaveTypeService.update('type-1', payload);
    expect(apiClient.put).toHaveBeenCalledWith('/v1/leave-types/type-1', payload);
    expect(res).toEqual({ id: 'type-1' });
  });

  it('calls savePolicy on /v1/leave-types/{id}/policy', async () => {
    apiClient.put.mockResolvedValueOnce({ data: { annualDays: 15 } });
    const policyPayload = { annualDays: 15, exceedBalanceMode: 'noLimit' };
    const res = await leaveTypeService.savePolicy('type-1', policyPayload);
    expect(apiClient.put).toHaveBeenCalledWith('/v1/leave-types/type-1/policy', policyPayload);
    expect(res).toEqual({ annualDays: 15 });
  });

  it('calls previewPolicy on /v1/leave-types/{id}/policy/preview', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [] });
    const params = { annualDays: 12 };
    const res = await leaveTypeService.previewPolicy('type-1', params);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-types/type-1/policy/preview', { params });
    expect(res).toEqual([]);
  });

  it('calls eligible on /v1/leave-types/eligible with employeeId and asOf', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'type-1' }] });
    const res = await leaveTypeService.eligible('emp-1', '2026-10-01');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-types/eligible', {
      params: { employeeId: 'emp-1', asOf: '2026-10-01' },
    });
    expect(res).toEqual([{ id: 'type-1' }]);
  });
});
