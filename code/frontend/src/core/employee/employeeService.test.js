import { describe, it, expect, vi, beforeEach } from 'vitest';
import { employeeService } from './employeeService.js';
import { apiClient } from '../../shared/api/client.js';

vi.mock('../../shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('employeeService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('passes every param to list endpoint', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { content: [], totalElements: 0 } });

    await employeeService.list({
      q: 'Asha',
      status: 'ACTIVE',
      page: 0,
      size: 20,
      sort: 'lastName,asc',
      includeDeleted: true,
    });

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees', {
      params: {
        q: 'Asha',
        status: 'ACTIVE',
        page: 0,
        size: 20,
        sort: 'lastName,asc',
        includeDeleted: true,
      },
    });
  });

  it('calls section GET with correct path', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { dateOfBirth: '1990-01-01' } });

    const res = await employeeService.section('emp-123', 'personal');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-123/personal');
    expect(res).toEqual({ dateOfBirth: '1990-01-01' });
  });

  it('calls saveSection PUT with correct path and body', async () => {
    const payload = { personalEmail: 'asha@work.com' };
    apiClient.put.mockResolvedValueOnce({ data: payload });

    const res = await employeeService.saveSection('emp-123', 'contact', payload);

    expect(apiClient.put).toHaveBeenCalledWith('/v1/employees/emp-123/contact', payload);
    expect(res).toEqual(payload);
  });

  it('calls getAccess GET on the access path', async () => {
    const access = { state: 'NONE', invitationId: null, expiresAt: null, roles: [] };
    apiClient.get.mockResolvedValueOnce({ data: access });

    const res = await employeeService.getAccess('emp-123');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/employees/emp-123/access');
    expect(res).toEqual(access);
  });
});
