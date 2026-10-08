import { describe, it, expect, vi, beforeEach } from 'vitest';
import { employeeImportService } from './employeeImportService.js';
import { apiClient } from '../../shared/api/client.js';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));

describe('employeeImportService', () => {
  beforeEach(() => vi.clearAllMocks());

  it('posts the file as multipart for the dry run', async () => {
    apiClient.post.mockResolvedValue({ data: [{ row: 1 }] });
    const file = new File(['x'], 'a.csv');

    const rows = await employeeImportService.dryRun(file);

    expect(rows).toEqual([{ row: 1 }]);
    const [url, body, options] = apiClient.post.mock.calls[0];
    expect(url).toBe('/v1/employees/import/dry-run');
    expect(body.get('file')).toBe(file);
    expect(options.headers['Content-Type']).toBe('multipart/form-data');
  });

  it('sends validOnly with the import and returns the job id', async () => {
    apiClient.post.mockResolvedValue({ data: { jobId: 'job-1' } });

    const jobId = await employeeImportService.importFile(new File(['x'], 'a.csv'), true);

    expect(jobId).toBe('job-1');
    expect(apiClient.post.mock.calls[0][0]).toBe('/v1/employees/import');
    expect(apiClient.post.mock.calls[0][2].params).toEqual({ validOnly: true });
  });

  it('reads the invite-all count and queues invite-all', async () => {
    apiClient.get.mockResolvedValue({ data: { count: 7 } });
    apiClient.post.mockResolvedValue({ data: { jobId: 'job-2' } });

    expect(await employeeImportService.inviteAllCount()).toBe(7);
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employee-invitations/invite-all/count');
    expect(await employeeImportService.inviteAll()).toBe('job-2');
    expect(apiClient.post).toHaveBeenCalledWith('/v1/employee-invitations/invite-all');
  });

  it('fetches the template and a result file as text', async () => {
    apiClient.get.mockResolvedValue({ data: 'a,b\n' });

    await employeeImportService.template();
    await employeeImportService.resultFile('job-3');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/import/template', { responseType: 'text' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/import/jobs/job-3/result', { responseType: 'text' });
  });
});
