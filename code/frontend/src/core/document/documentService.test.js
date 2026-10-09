import { describe, it, expect, vi, beforeEach } from 'vitest';
import { documentService, labelText } from '../document/documentService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
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

  it('uploads an employee document with kind, employeeId and label in the query', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'doc-9', label: 'ID_PROOF' } });
    const file = new File(['pdf'], 'passport.pdf', { type: 'application/pdf' });

    const res = await documentService.uploadForEmployee(file, 'emp-1', 'ID_PROOF');

    expect(apiClient.post).toHaveBeenCalledWith(
      '/v1/documents?kind=EMPLOYEE_DOCUMENT&employeeId=emp-1&label=ID_PROOF',
      expect.any(FormData),
      expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } })
    );
    expect(apiClient.post.mock.calls[0][1].get('file')).toBe(file);
    expect(res).toEqual({ id: 'doc-9', label: 'ID_PROOF' });
  });

  it("lists an employee's documents on /v1/employees/{id}/documents", async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'doc-1' }] });
    const res = await documentService.listForEmployee('emp-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-1/documents');
    expect(res).toEqual([{ id: 'doc-1' }]);
  });

  it('fetches a signed link on /v1/documents/{id}/link', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { url: 'https://blob/x', expiresAt: '2026-10-08T10:15:00Z' } });
    const res = await documentService.link('doc-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/documents/doc-1/link');
    expect(res.url).toBe('https://blob/x');
  });

  it('deletes on /v1/documents/{id}', async () => {
    apiClient.delete.mockResolvedValueOnce({ data: undefined });
    await documentService.remove('doc-1');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/documents/doc-1');
  });

  it('maps a label code to its display text', () => {
    expect(labelText('OFFER_LETTER')).toBe('Offer letter');
    expect(labelText('UNKNOWN')).toBe('UNKNOWN');
    expect(labelText(null)).toBe('—');
  });
});
