import { describe, it, expect, vi } from 'vitest';
import { createService } from './createService.js';
import { apiClient } from './client.js';

describe('createService factory', () => {
  const service = createService('/v1/employees');

  it('invokes apiClient.get with params for list and returns data', async () => {
    const spy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: [{ id: 'emp-1' }] });
    const res = await service.list({ status: 'ACTIVE' });
    expect(spy).toHaveBeenCalledWith('/v1/employees', { params: { status: 'ACTIVE' } });
    expect(res).toEqual([{ id: 'emp-1' }]);
  });

  it('invokes apiClient.get with id for get and returns data', async () => {
    const spy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: { id: 'emp-1' } });
    const res = await service.get('emp-1');
    expect(spy).toHaveBeenCalledWith('/v1/employees/emp-1');
    expect(res).toEqual({ id: 'emp-1' });
  });

  it('invokes apiClient.post with body for create and returns data', async () => {
    const body = { firstName: 'Alice' };
    const spy = vi.spyOn(apiClient, 'post').mockResolvedValueOnce({ data: { id: 'emp-1', ...body } });
    const res = await service.create(body);
    expect(spy).toHaveBeenCalledWith('/v1/employees', body);
    expect(res).toEqual({ id: 'emp-1', ...body });
  });

  it('invokes apiClient.put with id and body for update and returns data', async () => {
    const body = { firstName: 'Alice Updated' };
    const spy = vi.spyOn(apiClient, 'put').mockResolvedValueOnce({ data: { id: 'emp-1', ...body } });
    const res = await service.update('emp-1', body);
    expect(spy).toHaveBeenCalledWith('/v1/employees/emp-1', body);
    expect(res).toEqual({ id: 'emp-1', ...body });
  });

  it('invokes apiClient.delete with id for remove and returns data', async () => {
    const spy = vi.spyOn(apiClient, 'delete').mockResolvedValueOnce({ data: null });
    const res = await service.remove('emp-1');
    expect(spy).toHaveBeenCalledWith('/v1/employees/emp-1');
    expect(res).toBeNull();
  });
});
