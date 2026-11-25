import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '@shared/api/client.js';
import { bankFileService } from './bankFileService.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

describe('bankFileService (W-88.2)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists templates and the field catalogue', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'generic-neft' }] });
    expect(await bankFileService.templates()).toEqual([{ id: 'generic-neft' }]);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/bank-file-templates');

    apiClient.get.mockResolvedValueOnce({ data: [{ code: 'IFSC' }] });
    expect(await bankFileService.fields()).toEqual([{ code: 'IFSC' }]);
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/payroll/bank-file-templates/fields');
  });

  it('creates, updates, copies and deletes a template', async () => {
    apiClient.post.mockResolvedValue({ data: { id: 't1' } });
    apiClient.put.mockResolvedValue({ data: { id: 't1' } });
    await bankFileService.create({ name: 'X' });
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/bank-file-templates', { name: 'X' });
    await bankFileService.update('t1', { name: 'Y' });
    expect(apiClient.put).toHaveBeenCalledWith('/v1/payroll/bank-file-templates/t1', { name: 'Y' });
    await bankFileService.copy('generic-neft');
    expect(apiClient.post).toHaveBeenLastCalledWith('/v1/payroll/bank-file-templates/generic-neft/copy');
    await bankFileService.remove('t1');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/payroll/bank-file-templates/t1');
  });

  it('previews, makes, lists and downloads a run\'s bank files', async () => {
    const body = { templateId: 't1', employeeIds: ['e1'] };
    apiClient.post.mockResolvedValue({ data: { rowCount: 1 } });
    await bankFileService.preview('run-1', body);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/payroll/payruns/run-1/bank-files/preview', body);
    await bankFileService.generate('run-1', body);
    expect(apiClient.post).toHaveBeenLastCalledWith('/v1/payroll/payruns/run-1/bank-files', body);

    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'x1' }] });
    expect(await bankFileService.history('run-1')).toEqual([{ id: 'x1' }]);
    const blob = new Blob(['a']);
    apiClient.get.mockResolvedValueOnce({ data: blob });
    expect(await bankFileService.download('run-1', 'x1')).toBe(blob);
    expect(apiClient.get).toHaveBeenLastCalledWith('/v1/payroll/payruns/run-1/bank-files/x1/download', {
      responseType: 'blob',
    });
  });
});
