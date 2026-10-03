import { describe, it, expect, vi, beforeEach } from 'vitest';
import { leaveImportService } from './leaveImportService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('leaveImportService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls start on /v1/leave-imports with body', async () => {
    apiClient.post.mockResolvedValueOnce({
      data: { id: 'import-1', status: 'PENDING', isDryRun: true },
    });
    const payload = { documentId: 'doc-1', leaveYear: '2026', dryRun: true };
    const res = await leaveImportService.start(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/leave-imports', payload);
    expect(res).toEqual({ id: 'import-1', status: 'PENDING', isDryRun: true });
  });

  it('calls get on /v1/leave-imports/{id}', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: { id: 'import-1', status: 'COMPLETED' },
    });
    const res = await leaveImportService.get('import-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-imports/import-1');
    expect(res).toEqual({ id: 'import-1', status: 'COMPLETED' });
  });

  it('calls history on /v1/leave-imports with pagination params', async () => {
    apiClient.get.mockResolvedValueOnce({
      data: { content: [{ id: 'import-1' }], totalElements: 1 },
    });
    const res = await leaveImportService.history(0, 10);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/leave-imports', {
      params: { page: 0, size: 10 },
    });
    expect(res).toEqual({ content: [{ id: 'import-1' }], totalElements: 1 });
  });
});
