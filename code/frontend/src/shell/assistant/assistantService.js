import { apiClient } from '../../shared/api/client.js';

/** The longest chat title the server keeps (`AiSession.MAX_TITLE`, W-114.3). */
export const MAX_TITLE = 200;

/** Past chats older than this are not listed (W-114.5 Decisions); W-114.10 deletes them. */
export const PAST_CHAT_DAYS = 30;

/** The most chats one list call asks for (`AiSessionController.MAX_PAGE_SIZE`). */
const SESSION_PAGE_SIZE = 50;

/** The error code the server sends with `429` and a `Retry-After` header (W-114.4). */
export const AI_RATE_LIMITED = 'AI_RATE_LIMITED';

function bodyOf(res) {
  return res?.data !== undefined ? res.data : res;
}

/** Whole seconds from a `Retry-After` header, or null when it is missing or not a number. */
export function retryAfterSeconds(headers) {
  const raw =
    typeof headers?.get === 'function'
      ? headers.get('retry-after')
      : headers?.['retry-after'] ?? headers?.['Retry-After'];
  const seconds = Number.parseInt(raw, 10);
  return Number.isFinite(seconds) && seconds > 0 ? seconds : null;
}

/** The chat title from the first question: trimmed and cut to what the server keeps. */
export function titleFrom(question) {
  const text = (question || '').trim().replace(/\s+/g, ' ');
  return text.length > MAX_TITLE ? text.slice(0, MAX_TITLE) : text;
}

/**
 * The AI helper's calls (W-114.5). Every one goes through `apiClient`; the tenant and the user are the
 * server's, from the token, never sent from here.
 */
export const assistantService = {
  /** `GET /api/v1/ai/status` → `{ enabled, monthlyRequestCap, usedThisMonth, month }` (W-114.1). */
  async getStatus() {
    return bodyOf(await apiClient.get('/v1/ai/status'));
  },

  /** `POST /api/v1/ai/sessions` → `{ id, title, createdAt, lastMessageAt }` (W-114.3). */
  async createSession(title) {
    const clean = titleFrom(title);
    return bodyOf(await apiClient.post('/v1/ai/sessions', clean ? { title: clean } : {}));
  },

  /** The caller's chats from the last 30 days, newest activity first (the server's order). */
  async listSessions(now = Date.now()) {
    const page = bodyOf(await apiClient.get('/v1/ai/sessions', { params: { page: 0, size: SESSION_PAGE_SIZE } }));
    const list = Array.isArray(page?.content) ? page.content : Array.isArray(page) ? page : [];
    const cutoff = now - PAST_CHAT_DAYS * 24 * 60 * 60 * 1000;
    return list.filter((s) => {
      const at = Date.parse(s?.lastMessageAt || s?.createdAt);
      return Number.isNaN(at) || at >= cutoff;
    });
  },

  /** `GET /api/v1/ai/sessions/{id}/messages` → the user and helper messages, oldest first. */
  async listMessages(sessionId) {
    const body = bodyOf(await apiClient.get(`/v1/ai/sessions/${encodeURIComponent(sessionId)}/messages`));
    return Array.isArray(body) ? body : [];
  },

  /** `DELETE /api/v1/ai/sessions/{id}`. */
  async deleteSession(sessionId) {
    await apiClient.delete(`/v1/ai/sessions/${encodeURIComponent(sessionId)}`);
  },

  /**
   * `POST /api/v1/ai/sessions/{id}/messages` with `{ content }` → `{ message, proposedAction }` (W-114.4).
   *
   * `context` - the screen the user is on - is the W-114.13 seam: it is accepted here and not sent yet.
   *
   * The shared client keeps no response headers on an error, so this one call lets a `429` through
   * as a response and rejects it itself in the client's error shape, adding `retryAfterSeconds` from
   * the `Retry-After` header that `AI_RATE_LIMITED` carries.
   */
  async sendMessage(sessionId, content, context) {
    void context; // W-114.13 sends it.
    const res = await apiClient.post(
      `/v1/ai/sessions/${encodeURIComponent(sessionId)}/messages`,
      { content },
      { validateStatus: (status) => (status >= 200 && status < 300) || status === 429 },
    );
    if (res?.status === 429) {
      const body = res.data || {};
      throw {
        code: body.code ?? 'INTERNAL',
        message: body.message ?? 'Something went wrong',
        fieldErrors: body.fieldErrors ?? {},
        traceId: body.traceId,
        status: 429,
        retryAfterSeconds: retryAfterSeconds(res.headers),
      };
    }
    return bodyOf(res);
  },
};
