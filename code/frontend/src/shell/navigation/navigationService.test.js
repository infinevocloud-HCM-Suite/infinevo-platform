import { describe, it, expect, vi } from 'vitest';
import { fetchNavigation } from './navigationService.js';
import { apiClient } from '../../shared/api/client.js';

describe('navigationService', () => {
  it('calls /v1/navigation on apiClient', async () => {
    const mockData = { items: [{ key: 'core', labelKey: 'Core' }], actions: ['core.employee.read'] };
    const spy = vi.spyOn(apiClient, 'get').mockResolvedValueOnce({ data: mockData });

    const result = await fetchNavigation();
    expect(spy).toHaveBeenCalledWith('/v1/navigation');
    expect(result.data).toEqual(mockData);
  });
});
