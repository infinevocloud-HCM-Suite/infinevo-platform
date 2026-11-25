import { apiClient } from '@shared/api/client';
import { fyForApi } from '../tax/financialYear';

/**
 * Proof of investment (W-34.1 employee, W-34.2 review, W-34.3 chase list) and the reminder rule the
 * chase list sets (W-20.2). Paths start '/v1/' because apiClient's baseURL is '/api'; every financial
 * year goes through fyForApi, as the backend reads 'YYYY-YYYY' only.
 *
 * Receipts come back as a Blob through the authenticated client (D-152): the download endpoints need
 * the bearer token, which a plain link never carries.
 */
const fyPath = (fy) => encodeURIComponent(fyForApi(fy));
const id = (value) => encodeURIComponent(value);
const data = (res) => res.data.data;

const MY = '/v1/me/proof-of-investment';
const OFFICER = '/v1/payroll/proof-of-investment';

export const POI_REMINDER_RULE = {
  event: 'POI_REMINDER',
  audience: 'POI_PENDING',
  anchor: 'POI_DUE_DATE',
};

export const proofService = {
  // The employee's own proof (payroll.proof.read_own, payroll.proof.submit_own)
  async own(fy) {
    return data(await apiClient.get(`${MY}/${fyPath(fy)}`));
  },

  async updateItem(fy, itemId, body) {
    return data(await apiClient.put(`${MY}/${fyPath(fy)}/items/${id(itemId)}`, body));
  },

  async attach(fy, itemId, file) {
    const form = new FormData();
    form.append('file', file);
    return data(
      await apiClient.post(`${MY}/${fyPath(fy)}/items/${id(itemId)}/documents`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      }),
    );
  },

  async detach(fy, itemId, documentId) {
    await apiClient.delete(`${MY}/${fyPath(fy)}/items/${id(itemId)}/documents/${id(documentId)}`);
  },

  async ownReceipt(fy, itemId, documentId) {
    const res = await apiClient.get(
      `${MY}/${fyPath(fy)}/items/${id(itemId)}/documents/${id(documentId)}`,
      { responseType: 'blob' },
    );
    return res.data;
  },

  async submit(fy) {
    return data(await apiClient.post(`${MY}/${fyPath(fy)}/submit`));
  },

  // An officer's read of one employee's proof (payroll.proof.read)
  async of(employeeId, fy) {
    return data(
      await apiClient.get(`/v1/payroll/employees/${id(employeeId)}/proof-of-investment/${fyPath(fy)}`),
    );
  },

  async receiptOf(employeeId, fy, itemId, documentId) {
    const res = await apiClient.get(
      `/v1/payroll/employees/${id(employeeId)}/proof-of-investment/${fyPath(fy)}/items/${id(itemId)}/documents/${id(documentId)}`,
      { responseType: 'blob' },
    );
    return res.data;
  },

  // Review (payroll.proof.review)
  async review(proofId) {
    return data(await apiClient.get(`${OFFICER}/${id(proofId)}/review`));
  },

  async decideItem(proofId, itemId, body) {
    return data(await apiClient.post(`${OFFICER}/${id(proofId)}/items/${id(itemId)}/decide`, body));
  },

  async decideFinal(proofId, body) {
    return data(await apiClient.post(`${OFFICER}/${id(proofId)}/final`, body));
  },

  // Chase list and summary (payroll.proof.read)
  async list(fy, { status, search, page = 0, size = 25 } = {}) {
    const params = { fy: fyForApi(fy), page, size };
    if (status) params.status = status;
    if (search && search.trim()) params.search = search.trim();
    return data(await apiClient.get(OFFICER, { params }));
  },

  async summary(fy) {
    return data(await apiClient.get(`${OFFICER}/summary`, { params: { fy: fyForApi(fy) } }));
  },

  // The POI reminder rule (core.reminder_rule.manage). These endpoints answer bare bodies, no envelope.
  // Active rules only: delete is a soft deactivate, and a deleted rule must not block setting a new one.
  async reminderRules() {
    const res = await apiClient.get('/v1/reminder-rules', { params: { event: POI_REMINDER_RULE.event, isActive: true } });
    const rules = Array.isArray(res.data) ? res.data : [];
    return rules.filter(
      (r) => r.audience === POI_REMINDER_RULE.audience && r.anchor === POI_REMINDER_RULE.anchor,
    );
  },

  async createReminderRule({ offsetDays, sendAt, repeatEveryDays }) {
    const res = await apiClient.post('/v1/reminder-rules', {
      ...POI_REMINDER_RULE,
      offset_days: offsetDays,
      send_at_local_time: sendAt,
      repeat_every_days: repeatEveryDays || null,
    });
    return res.data;
  },
};

/** A Spring page in either shape: bare `{ content, totalElements }` or `{ content, page: { totalElements } }`. */
export function readPage(page) {
  const content = Array.isArray(page?.content) ? page.content : [];
  const total = page?.totalElements ?? page?.page?.totalElements ?? content.length;
  return { content, total };
}
