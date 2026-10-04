import { apiClient } from '@shared/api/client';

const PROJECTS = '/v1/hrms/projects';
const TASKS = '/v1/hrms/tasks';

function clean(filters = {}) {
  const out = {};
  for (const [k, v] of Object.entries(filters)) {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  }
  return out;
}

const unwrap = (res) => res.data.data;

/**
 * Projects, assignments, tasks and the assignable-employee picker (W-48.1 §3; `ProjectController.java`,
 * `AssignmentController.java`, `TaskController.java`, `AssignableEmployeeController.java`).
 * Every reply is the {status, message, data} envelope; deletes return 204 with no body.
 */
export const projectService = {
  async list(filters) {
    return unwrap(await apiClient.get(PROJECTS, { params: clean(filters) }));
  },
  async mine() {
    return unwrap(await apiClient.get(`${PROJECTS}/mine`));
  },
  async get(id) {
    return unwrap(await apiClient.get(`${PROJECTS}/${id}`));
  },
  async create(body) {
    return unwrap(await apiClient.post(PROJECTS, body));
  },
  async update(id, body) {
    return unwrap(await apiClient.put(`${PROJECTS}/${id}`, body));
  },
  async setStatus(id, status) {
    return unwrap(await apiClient.put(`${PROJECTS}/${id}/status`, { status }));
  },
  async setProgress(id, progress) {
    return unwrap(await apiClient.put(`${PROJECTS}/${id}/progress`, { progress }));
  },
  async remove(id) {
    await apiClient.delete(`${PROJECTS}/${id}`);
  },
  async assignments(projectId) {
    return unwrap(await apiClient.get(`${PROJECTS}/${projectId}/assignments`));
  },
  async assign(projectId, body) {
    return unwrap(await apiClient.post(`${PROJECTS}/${projectId}/assignments`, body));
  },
  async unassign(projectId, employeeId) {
    await apiClient.delete(`${PROJECTS}/${projectId}/assignments/${employeeId}`);
  },
  async tasks(projectId, status) {
    return unwrap(
      await apiClient.get(`${PROJECTS}/${projectId}/tasks`, {
        params: clean({ status }),
      })
    );
  },
  async createTask(projectId, body) {
    return unwrap(await apiClient.post(`${PROJECTS}/${projectId}/tasks`, body));
  },
  async myTasks() {
    return unwrap(await apiClient.get(`${TASKS}/mine`));
  },
  async updateTask(id, body) {
    return unwrap(await apiClient.put(`${TASKS}/${id}`, body));
  },
  async setTaskStatus(id, status) {
    return unwrap(await apiClient.put(`${TASKS}/${id}/status`, { status }));
  },
  async removeTask(id) {
    await apiClient.delete(`${TASKS}/${id}`);
  },
  async assignable(q) {
    return unwrap(await apiClient.get('/v1/hrms/employees/assignable', { params: { q } }));
  },
};

/** The server's reason for a failed call (`ApiErrorResponse.message`), else a fallback. */
export function errorMessage(err, fallback = 'Something went wrong') {
  return err?.response?.data?.message || fallback;
}

export const PRIORITIES = ['LOW', 'MEDIUM', 'HIGH'];
export const PROJECT_STATUSES = ['STARTED', 'COMPLETED'];
export const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED'];
export const LABEL = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  STARTED: 'Started',
  COMPLETED: 'Completed',
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  IN_REVIEW: 'In review',
};
export const COLOR = {
  STARTED: 'blue',
  COMPLETED: 'green',
  TODO: 'default',
  IN_PROGRESS: 'blue',
  IN_REVIEW: 'gold',
};
