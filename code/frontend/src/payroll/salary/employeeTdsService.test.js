import { describe, it, expect, vi, beforeEach } from 'vitest';
import { employeeTdsService } from './employeeTdsService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}));

describe('employeeTdsService (D-145)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('get reads the active record out of the envelope', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: { id: 't1', monthly_share: 10000 } } });

    const res = await employeeTdsService.get('emp-1', '2026-2027');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/employees/emp-1/tds/2026-2027');
    expect(res).toEqual({ id: 't1', monthly_share: 10000 });
  });

  it('history reads the rows, and an empty answer is an empty list', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { status: 200, data: [{ id: 't2' }, { id: 't1' }] } });
    expect(await employeeTdsService.history('emp-1', '2026-2027')).toEqual([{ id: 't2' }, { id: 't1' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/employees/emp-1/tds/2026-2027/history');

    apiClient.get.mockResolvedValueOnce({ data: { status: 200 } });
    expect(await employeeTdsService.history('emp-1', '2026-2027')).toEqual([]);
  });

  it('save puts the body and returns the saved record', async () => {
    const body = { regime: 'NEW', annual_tax: '1000.00', note: 'x' };
    apiClient.put.mockResolvedValueOnce({ data: { status: 200, data: { id: 't3' } } });

    const res = await employeeTdsService.save('emp-1', '2026-2027', body);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/employees/emp-1/tds/2026-2027', body);
    expect(res).toEqual({ id: 't3' });
  });
});
