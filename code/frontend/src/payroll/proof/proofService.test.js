import { describe, it, expect, vi, beforeEach } from 'vitest';
import { proofService, readPage } from './proofService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: { get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));

const ok = (data) => ({ data: { data } });

describe('proofService (D-152)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('reads the own proof with the API financial year and unwraps the envelope', async () => {
    apiClient.get.mockResolvedValueOnce(ok({ id: 'p1' }));
    expect(await proofService.own('2026-27')).toEqual({ id: 'p1' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/me/proof-of-investment/2026-2027');
  });

  it('saves a claim on one item', async () => {
    apiClient.put.mockResolvedValueOnce(ok({ id: 'i1' }));
    await proofService.updateItem('2026-27', 'i1', { claimed_amount: '100.00', employee_note: null });
    expect(apiClient.put).toHaveBeenCalledWith('/v1/me/proof-of-investment/2026-2027/items/i1', {
      claimed_amount: '100.00',
      employee_note: null,
    });
  });

  it('attaches a receipt as multipart with a file part', async () => {
    apiClient.post.mockResolvedValueOnce(ok({ document_id: 'd1' }));
    const file = new File(['%PDF-'], 'rent.pdf', { type: 'application/pdf' });
    await proofService.attach('2026-27', 'i1', file);
    const [url, body, config] = apiClient.post.mock.calls[0];
    expect(url).toBe('/v1/me/proof-of-investment/2026-2027/items/i1/documents');
    expect(body.get('file')).toBe(file);
    expect(config.headers['Content-Type']).toBe('multipart/form-data');
  });

  it('removes a receipt and submits the proof', async () => {
    apiClient.delete.mockResolvedValueOnce({ status: 204 });
    await proofService.detach('2026-27', 'i1', 'd1');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/me/proof-of-investment/2026-2027/items/i1/documents/d1');
    apiClient.post.mockResolvedValueOnce(ok({ status: 'SUBMITTED' }));
    expect(await proofService.submit('2026-27')).toEqual({ status: 'SUBMITTED' });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/me/proof-of-investment/2026-2027/submit');
  });

  it('fetches receipts as blobs through the authenticated client, own and officer', async () => {
    const blob = new Blob(['x']);
    apiClient.get.mockResolvedValue({ data: blob });
    expect(await proofService.ownReceipt('2026-27', 'i1', 'd1')).toBe(blob);
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/me/proof-of-investment/2026-2027/items/i1/documents/d1', {
      responseType: 'blob',
    });
    expect(await proofService.receiptOf('e1', '2026-27', 'i1', 'd1')).toBe(blob);
    expect(apiClient.get).toHaveBeenLastCalledWith(
      '/v1/payroll/employees/e1/proof-of-investment/2026-2027/items/i1/documents/d1',
      { responseType: 'blob' },
    );
  });

  it('reviews and decides through the W-34.2 paths', async () => {
    apiClient.get.mockResolvedValueOnce(ok({ id: 'p1' }));
    await proofService.review('p1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/proof-of-investment/p1/review');
    apiClient.post.mockResolvedValue(ok({}));
    await proofService.decideItem('p1', 'i1', { action: 'APPROVE', approved_amount: '5', comment: null });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/proof-of-investment/p1/items/i1/decide', {
      action: 'APPROVE',
      approved_amount: '5',
      comment: null,
    });
    await proofService.decideFinal('p1', { action: 'RETURN', comment: 'x' });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/proof-of-investment/p1/final', {
      action: 'RETURN',
      comment: 'x',
    });
  });

  it('lists with only the filters given, and reads the summary', async () => {
    apiClient.get.mockResolvedValue(ok({ content: [] }));
    await proofService.list('2026-27', { status: 'SUBMITTED', search: '  asha ' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/proof-of-investment', {
      params: { fy: '2026-2027', page: 0, size: 25, status: 'SUBMITTED', search: 'asha' },
    });
    await proofService.list('2026-27');
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/payroll/proof-of-investment', {
      params: { fy: '2026-2027', page: 0, size: 25 },
    });
    await proofService.summary('2026-27');
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/payroll/proof-of-investment/summary', {
      params: { fy: '2026-2027' },
    });
  });

  it('reads and creates the POI reminder rule, keeping only POI_PENDING / POI_DUE_DATE rules', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: [
        { id: 'r1', audience: 'POI_PENDING', anchor: 'POI_DUE_DATE' },
        { id: 'r2', audience: 'SUBJECT', anchor: 'FIXED' },
      ],
    });
    expect((await proofService.reminderRules()).map((r) => r.id)).toEqual(['r1']);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/reminder-rules', { params: { event: 'POI_REMINDER', isActive: true } });

    apiClient.post.mockResolvedValueOnce({ data: { id: 'r3' } });
    await proofService.createReminderRule({ offsetDays: 7, sendAt: '10:00', repeatEveryDays: 2 });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/reminder-rules', {
      event: 'POI_REMINDER',
      audience: 'POI_PENDING',
      anchor: 'POI_DUE_DATE',
      offset_days: 7,
      send_at_local_time: '10:00',
      repeat_every_days: 2,
    });
  });

  it('readPage reads both Spring page shapes', () => {
    expect(readPage({ content: [1], totalElements: 9 })).toEqual({ content: [1], total: 9 });
    expect(readPage({ content: [1], page: { totalElements: 4 } })).toEqual({ content: [1], total: 4 });
    expect(readPage(null)).toEqual({ content: [], total: 0 });
  });
});
