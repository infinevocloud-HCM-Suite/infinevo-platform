import { describe, it, expect, vi, beforeEach } from 'vitest';
import { projectService, errorMessage } from './projectService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

const env = (data) => ({ data: { status: 'success', message: 'ok', data } });

describe('projectService (W-48.1 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    for (const m of ['get', 'post', 'put']) apiClient[m].mockResolvedValue(env({ id: 'x' }));
    apiClient.delete.mockResolvedValue({ status: 204 });
  });

  const P = '/v1/hrms/projects';
  const T = '/v1/hrms/tasks';
  const cases = [
    [
      'list',
      () => projectService.list({ status: 'STARTED', search: '', managed: true }),
      'get',
      P,
      { params: { status: 'STARTED', managed: true } },
    ],
    ['mine', () => projectService.mine(), 'get', `${P}/mine`],
    ['get', () => projectService.get('p1'), 'get', `${P}/p1`],
    ['create', () => projectService.create({ name: 'A' }), 'post', P, { name: 'A' }],
    ['update', () => projectService.update('p1', { name: 'B' }), 'put', `${P}/p1`, { name: 'B' }],
    [
      'setStatus',
      () => projectService.setStatus('p1', 'COMPLETED'),
      'put',
      `${P}/p1/status`,
      { status: 'COMPLETED' },
    ],
    ['setProgress', () => projectService.setProgress('p1', 40), 'put', `${P}/p1/progress`, { progress: 40 }],
    ['assignments', () => projectService.assignments('p1'), 'get', `${P}/p1/assignments`],
    [
      'assign',
      () => projectService.assign('p1', { employee_id: 'e1' }),
      'post',
      `${P}/p1/assignments`,
      { employee_id: 'e1' },
    ],
    [
      'tasks',
      () => projectService.tasks('p1', 'TODO'),
      'get',
      `${P}/p1/tasks`,
      { params: { status: 'TODO' } },
    ],
    [
      'createTask',
      () => projectService.createTask('p1', { title: 't' }),
      'post',
      `${P}/p1/tasks`,
      { title: 't' },
    ],
    ['myTasks', () => projectService.myTasks(), 'get', `${T}/mine`],
    ['updateTask', () => projectService.updateTask('t1', { title: 'u' }), 'put', `${T}/t1`, { title: 'u' }],
    [
      'setTaskStatus',
      () => projectService.setTaskStatus('t1', 'IN_PROGRESS'),
      'put',
      `${T}/t1/status`,
      { status: 'IN_PROGRESS' },
    ],
    [
      'assignable',
      () => projectService.assignable('an'),
      'get',
      '/v1/hrms/employees/assignable',
      { params: { q: 'an' } },
    ],
  ];

  it.each(cases)('%s hits its path and unwraps data', async (_n, call, method, path, arg) => {
    expect(await call()).toEqual({ id: 'x' });
    if (arg === undefined) expect(apiClient[method]).toHaveBeenCalledWith(path);
    else expect(apiClient[method]).toHaveBeenCalledWith(path, arg);
  });

  it('deletes hit their paths', async () => {
    await projectService.remove('p1');
    await projectService.unassign('p1', 'e1');
    await projectService.removeTask('t1');
    expect(apiClient.delete.mock.calls.map((c) => c[0])).toEqual([
      `${P}/p1`,
      `${P}/p1/assignments/e1`,
      `${T}/t1`,
    ]);
  });

  it('errorMessage reads the message the shared client flattened (D-81)', () => {
    // The shape `client.js` rejects with: the server's ApiErrorResponse.message is `message`.
    expect(
      errorMessage({ code: 'CONFLICT', message: 'Project name already in use', status: 409 }, 'fb')
    ).toBe('Project name already in use');
    expect(errorMessage({ code: 'FORBIDDEN', message: 'Not your project', status: 403 })).toBe(
      'Not your project'
    );
  });

  it('errorMessage falls back when the server sent no message', () => {
    // No body: the client's own generic text is replaced by the caller's specific fallback.
    expect(
      errorMessage({ code: 'INTERNAL', message: 'Something went wrong', status: 500 }, 'Could not load')
    ).toBe('Could not load');
    expect(errorMessage({}, 'fallback')).toBe('fallback');
    expect(errorMessage(undefined)).toBe('Something went wrong');
  });
});
