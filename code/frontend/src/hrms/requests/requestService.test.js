import { describe, it, expect, vi, beforeEach } from 'vitest';
import { requestService } from './requestService.js';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const bare = (data) => ({ data });
const REG = '/v1/hrms/attendance/regularizations';
const OT = '/v1/hrms/overtime-requests';
const wire = {
  id: 'o1',
  employee_id: 'e1',
  overtime_date: '2026-10-01',
  hours: '2.50',
  amount: '1250.00',
  status: 'PENDING',
  source: 'REQUEST',
  remarks: 'release',
  pay_input_id: null,
  posted_period: '2026-10',
  created_at: '2026-10-01T10:00:00Z',
};
const mapped = {
  id: 'o1',
  employeeId: 'e1',
  overtimeDate: '2026-10-01',
  hours: '2.50',
  amount: '1250.00',
  status: 'PENDING',
  source: 'REQUEST',
  remarks: 'release',
  payInputId: null,
  postedPeriod: '2026-10',
  createdAt: '2026-10-01T10:00:00Z',
};

describe('requestService (W-48.5 §7)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('myRegularizations hits /mine with the range and returns the bare body', async () => {
    const body = [{ id: 'r1' }];
    apiClient.get.mockResolvedValueOnce(bare(body));
    expect(await requestService.myRegularizations('2026-09-01', '2026-09-30')).toEqual(body);
    expect(apiClient.get).toHaveBeenCalledWith(`${REG}/mine`, {
      params: { from: '2026-09-01', to: '2026-09-30' },
    });
  });

  it('allRegularizations sends status and employeeId only when set', async () => {
    apiClient.get.mockResolvedValue(bare([]));
    await requestService.allRegularizations('a', 'b', 'PENDING', 'e1');
    expect(apiClient.get).toHaveBeenLastCalledWith(REG, {
      params: { from: 'a', to: 'b', status: 'PENDING', employeeId: 'e1' },
    });
    await requestService.allRegularizations('a', 'b');
    expect(apiClient.get).toHaveBeenLastCalledWith(REG, {
      params: { from: 'a', to: 'b' },
    });
  });

  it('regularization(id) and submitRegularization return the bare body', async () => {
    apiClient.get.mockResolvedValueOnce(bare({ id: 'r1' }));
    expect(await requestService.regularization('r1')).toEqual({ id: 'r1' });
    expect(apiClient.get).toHaveBeenCalledWith(`${REG}/r1`);
    const body = { date: '2026-10-01', inAt: 'x', outAt: 'y', reason: 'z' };
    apiClient.post.mockResolvedValueOnce(bare({ id: 'r2' }));
    expect(await requestService.submitRegularization(body)).toEqual({
      id: 'r2',
    });
    expect(apiClient.post).toHaveBeenCalledWith(REG, body);
  });

  it('overtime replies are mapped to camelCase', async () => {
    apiClient.get.mockResolvedValueOnce(bare([wire]));
    expect(await requestService.myOvertime('a', 'b')).toEqual([mapped]);
    expect(apiClient.get).toHaveBeenCalledWith(`${OT}/mine`, {
      params: { from: 'a', to: 'b' },
    });

    apiClient.get.mockResolvedValueOnce(bare(wire));
    expect(await requestService.overtime('o1')).toEqual(mapped);
    expect(apiClient.get).toHaveBeenLastCalledWith(`${OT}/o1`);

    const body = { overtime_date: '2026-10-01', hours: '2.50', remarks: null };
    apiClient.post.mockResolvedValueOnce(bare(wire));
    expect(await requestService.submitOvertime(body)).toEqual(mapped);
    expect(apiClient.post).toHaveBeenCalledWith(OT, body);
  });

  it('allOvertime hits /v1/overtime with its params and maps employee_name', async () => {
    apiClient.get.mockResolvedValueOnce(bare([{ ...wire, employee_name: 'Asha Rao' }]));
    expect(await requestService.allOvertime('a', 'b', 'e1')).toEqual([{ ...mapped, employeeName: 'Asha Rao' }]);
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/overtime', {
      params: { from: 'a', to: 'b', employeeId: 'e1' },
    });
    apiClient.get.mockResolvedValueOnce(bare([]));
    expect(await requestService.allOvertime('a', 'b')).toEqual([]);
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/overtime', {
      params: { from: 'a', to: 'b' },
    });
  });
});
