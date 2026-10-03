import { describe, it, expect, vi, beforeEach } from 'vitest';
import { documentService } from '../document/documentService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('documentService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls upload on /v1/documents with multipart FormData and kind query param', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'doc-123' } });
    const dummyFile = new File(['content'], 'sample.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });

    const res = await documentService.upload(dummyFile, 'LEAVE_ATTACHMENT');
    expect(apiClient.post).toHaveBeenCalledWith(
      '/v1/documents?kind=LEAVE_ATTACHMENT',
      expect.any(FormData),
      expect.objectContaining({
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    );
    expect(res).toEqual({ id: 'doc-123' });
  });

  it('calls get on /v1/documents/{id}', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { id: 'doc-123' } });
    const res = await documentService.get('doc-123');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/documents/doc-123');
    expect(res).toEqual({ id: 'doc-123' });
  });
});
