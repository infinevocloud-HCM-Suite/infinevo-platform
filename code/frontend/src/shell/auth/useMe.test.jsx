import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useMe, fetchMe, resetMe } from './useMe.js';
import { apiClient } from '../../shared/api/client.js';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: { get: vi.fn() },
}));

vi.mock('./keycloak.js', () => ({
  onTenantChange: vi.fn(() => () => {}),
}));

describe('useMe (W-73.1)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetMe();
  });

  it('fetches /v1/me once and exposes displayName and roles', async () => {
    apiClient.get.mockResolvedValue({
      data: { displayName: 'Gita Rao', roles: ['hr', 'employee'], email: 'gita@acme.local' },
    });

    const { result } = renderHook(() => useMe());
    expect(result.current.loading).toBe(true);

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(apiClient.get).toHaveBeenCalledTimes(1);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me');
    expect(result.current.displayName).toBe('Gita Rao');
    expect(result.current.roles).toEqual(['hr', 'employee']);
    expect(result.current.email).toBe('gita@acme.local');

    // A second hook reads the store; it does not fetch again.
    renderHook(() => useMe());
    expect(apiClient.get).toHaveBeenCalledTimes(1);
  });

  it('a failed call leaves an empty name and no roles, and reports the error', async () => {
    apiClient.get.mockRejectedValue({ status: 500, message: 'boom' });

    const { result } = renderHook(() => useMe());
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.displayName).toBe('');
    expect(result.current.roles).toEqual([]);
    expect(result.current.error).toMatchObject({ status: 500 });
  });

  it('fetchMe() republishes to every mounted hook', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { displayName: 'Before', roles: [] } });
    const { result } = renderHook(() => useMe());
    await waitFor(() => expect(result.current.displayName).toBe('Before'));

    apiClient.get.mockResolvedValueOnce({ data: { displayName: 'After', roles: ['manager'] } });
    await act(async () => {
      await fetchMe();
    });
    expect(result.current.displayName).toBe('After');
    expect(result.current.roles).toEqual(['manager']);
  });

  it('ignores a malformed reply', async () => {
    apiClient.get.mockResolvedValue({ data: { displayName: 42, roles: 'hr' } });
    const { result } = renderHook(() => useMe());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.displayName).toBe('');
    expect(result.current.roles).toEqual([]);
  });
});
