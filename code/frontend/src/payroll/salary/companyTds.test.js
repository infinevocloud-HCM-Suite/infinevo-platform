import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useCompanyTdsOff } from './companyTds.js';
import { statutoryService } from '../settings/statutoryService.js';

vi.mock('../settings/statutoryService.js', () => ({
  statutoryService: {
    get: vi.fn(),
  },
}));

describe('useCompanyTdsOff (D-145)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('is true when the company switch is off', async () => {
    statutoryService.get.mockResolvedValueOnce({ enabled: false, source: 'PERSISTED' });

    const { result } = renderHook(() => useCompanyTdsOff(true));

    await waitFor(() => expect(result.current).toBe(true));
    expect(statutoryService.get).toHaveBeenCalledWith('tds');
  });

  it('is false when the switch is on', async () => {
    statutoryService.get.mockResolvedValueOnce({ enabled: true, source: 'DEFAULT' });

    const { result } = renderHook(() => useCompanyTdsOff(true));

    await waitFor(() => expect(statutoryService.get).toHaveBeenCalled());
    expect(result.current).toBe(false);
  });

  it('a failed read counts as on, so the tick stays usable', async () => {
    // First open: the switch reads off. Reopened: the read fails, and the hook goes back to on.
    statutoryService.get.mockResolvedValueOnce({ enabled: false, source: 'PERSISTED' });
    const { result, rerender } = renderHook(({ active }) => useCompanyTdsOff(active), {
      initialProps: { active: true },
    });
    await waitFor(() => expect(result.current).toBe(true));

    statutoryService.get.mockRejectedValueOnce({ code: 'FORBIDDEN', message: 'No access' });
    rerender({ active: false });
    rerender({ active: true });

    await waitFor(() => expect(statutoryService.get).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(result.current).toBe(false));
  });

  it('a read that throws before it returns a promise also counts as on', async () => {
    statutoryService.get.mockImplementationOnce(() => {
      throw new Error('boom');
    });

    const { result } = renderHook(() => useCompanyTdsOff(true));

    await waitFor(() => expect(statutoryService.get).toHaveBeenCalled());
    expect(result.current).toBe(false);
  });

  it('reads nothing while inactive', () => {
    renderHook(() => useCompanyTdsOff(false));

    expect(statutoryService.get).not.toHaveBeenCalled();
  });
});
