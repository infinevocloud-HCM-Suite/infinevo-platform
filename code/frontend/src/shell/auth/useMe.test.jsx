import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { useMe, fetchMe, resetMe, markWelcomeSeen } from './useMe.js';
import { apiClient } from '../../shared/api/client.js';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), put: vi.fn() },
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

  it('W-73.8: welcomeSeen is false only when the server says false', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { displayName: 'New', roles: [], welcomeSeen: false } });
    const { result } = renderHook(() => useMe());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.welcomeSeen).toBe(false);

    apiClient.get.mockResolvedValueOnce({ data: { displayName: 'Old', roles: [] } });
    await act(async () => {
      await fetchMe();
    });
    expect(result.current.welcomeSeen).toBe(true);

    apiClient.get.mockRejectedValueOnce({ status: 500 });
    await act(async () => {
      await fetchMe().catch(() => {});
    });
    expect(result.current.welcomeSeen).toBe(true);
  });

  it('W-73.8: markWelcomeSeen() PUTs and every hook sees welcomeSeen at once', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { displayName: 'New', roles: [], welcomeSeen: false } });
    apiClient.put.mockResolvedValueOnce({ status: 204 });
    const { result } = renderHook(() => useMe());
    await waitFor(() => expect(result.current.welcomeSeen).toBe(false));

    await act(async () => {
      await markWelcomeSeen();
    });
    expect(apiClient.put).toHaveBeenCalledWith('/v1/me/welcome-seen');
    expect(result.current.welcomeSeen).toBe(true);
  });
});
