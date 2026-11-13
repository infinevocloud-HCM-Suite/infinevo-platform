import { describe, it, expect, vi, beforeEach } from 'vitest';
import { lossOfPayService, employeeLabel, manualLopRef } from './lossOfPayService.js';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

const bare = (data) => ({ data });

describe('lossOfPayService (D-140)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists a month of pay inputs from GET /v1/pay-inputs?period=', async () => {
    const body = { rows: [{ id: 'p1' }], quantity_totals_by_kind: {}, amount_totals_by_kind: {} };
    apiClient.get.mockResolvedValueOnce(bare(body));
    await expect(lossOfPayService.forPeriod('2026-10')).resolves.toEqual(body);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/pay-inputs', { params: { period: '2026-10' } });
  });

  it('records LOP_DAYS through POST /v1/pay-inputs with the names PayInputRequest reads', async () => {
    apiClient.post.mockResolvedValueOnce(bare({ id: 'p1', posted_period: '2026-10' }));
    const saved = await lossOfPayService.record({ employeeId: 'e1', period: '2026-10', days: '2.50', reason: 'Absent' });
    expect(saved).toEqual({ id: 'p1', posted_period: '2026-10' });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/pay-inputs', {
      employee_id: 'e1',
      period: '2026-10',
      kind: 'LOP_DAYS',
      quantity: '2.50',
      amount: null,
      source_ref: expect.stringMatching(/^manual-lop:/),
      note: 'Absent',
    });
  });

  it('sends the reason as note and a fresh source_ref each time, so the same reason twice is no duplicate', async () => {
    apiClient.post.mockResolvedValue(bare({ id: 'p1' }));
    const entry = { employeeId: 'e1', period: '2026-10', days: '1', reason: 'Absent' };
    await lossOfPayService.record(entry);
    await lossOfPayService.record(entry);
    const [first, second] = apiClient.post.mock.calls.map(([, body]) => body);
    expect(first.note).toBe('Absent');
    expect(second.note).toBe('Absent');
    expect(first.source_ref).not.toBe(second.source_ref);
    // fits core.pay_input.source_ref VARCHAR(64)
    expect(first.source_ref.length).toBeLessThanOrEqual(64);
  });

  it('builds a manual-lop key even without crypto.randomUUID', () => {
    const original = globalThis.crypto;
    try {
      Object.defineProperty(globalThis, 'crypto', { value: undefined, configurable: true, writable: true });
      const ref = manualLopRef();
      expect(ref).toMatch(/^manual-lop:\S+$/);
      expect(ref.length).toBeLessThanOrEqual(64);
    } finally {
      Object.defineProperty(globalThis, 'crypto', { value: original, configurable: true, writable: true });
    }
  });

  it('reverses through POST /v1/pay-inputs/{id}/reverse with the reason', async () => {
    apiClient.post.mockResolvedValueOnce(bare({ id: 'p2', reverses_id: 'p1' }));
    await lossOfPayService.reverse('p1', 'Entered twice');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/pay-inputs/p1/reverse', { reason: 'Entered twice' });
  });

  it('searches active employees only', async () => {
    apiClient.get.mockResolvedValueOnce(bare({ content: [{ id: 'e1' }] }));
    await expect(lossOfPayService.searchActive('asha')).resolves.toEqual([{ id: 'e1' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees', { params: { status: 'ACTIVE', size: 20, q: 'asha' } });
  });

  it('pages the employee directory until the last page', async () => {
    apiClient.get
      .mockResolvedValueOnce(bare({ content: [{ id: 'e1', employeeNumber: 'E001', firstName: 'Asha', lastName: 'Rao' }], last: false }))
      .mockResolvedValueOnce(bare({ content: [{ id: 'e2', employeeNumber: 'E002', firstName: 'Bilal' }], last: true }));
    await expect(lossOfPayService.directory()).resolves.toEqual({ e1: 'E001 - Asha Rao', e2: 'E002 - Bilal' });
    expect(apiClient.get).toHaveBeenNthCalledWith(2, '/v1/employees', { params: { page: 1, size: 100 } });
  });

  it('labels an employee by number and name', () => {
    expect(employeeLabel({ id: 'x', employeeNumber: 'E9', firstName: 'A', lastName: 'B' })).toBe('E9 - A B');
  });
});
