import { apiClient } from '../../shared/api/client.js';

const IMPORT = '/v1/employees/import';
const INVITE_ALL = '/v1/employee-invitations/invite-all';

function form(file) {
  const body = new FormData();
  body.append('file', file);
  return body;
}

// apiClient defaults to application/json, which would serialise the form to JSON.
const MULTIPART = { headers: { 'Content-Type': 'multipart/form-data' } };

/**
 * Bulk employee import and "Invite all without access" (W-73.7 §5). Replies are bare (no envelope).
 */
export const employeeImportService = {
  /** The CSV template as text. */
  async template() {
    const res = await apiClient.get(`${IMPORT}/template`, { responseType: 'text' });
    return res.data;
  },

  /** Every row checked, nothing written: [{row, employeeNumber, status: OK|ERROR, message}]. */
  async dryRun(file) {
    const res = await apiClient.post(`${IMPORT}/dry-run`, form(file), MULTIPART);
    return res.data;
  },

  /** Queues the import; `validOnly` skips rows in error. Returns the job id. */
  async importFile(file, validOnly) {
    const res = await apiClient.post(IMPORT, form(file), {
      ...MULTIPART,
      params: { validOnly: Boolean(validOnly) },
    });
    return res.data.jobId;
  },

  /** Recent import and invite-all jobs, newest first. */
  async jobs() {
    const res = await apiClient.get(`${IMPORT}/jobs`);
    return res.data;
  },

  /** The result CSV of a finished job, as text. */
  async resultFile(jobId) {
    const res = await apiClient.get(`${IMPORT}/jobs/${jobId}/result`, { responseType: 'text' });
    return res.data;
  },

  /** How many employees "Invite all without access" would invite now. */
  async inviteAllCount() {
    const res = await apiClient.get(`${INVITE_ALL}/count`);
    return res.data.count;
  },

  /** Queues "Invite all without access". Returns the job id. */
  async inviteAll() {
    const res = await apiClient.post(INVITE_ALL);
    return res.data.jobId;
  },
};

/** Saves text as a CSV download in the browser. */
export function saveCsv(text, fileName) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
}
