import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiClient } from '../../shared/api/client.js';
import { assistantService, retryAfterSeconds, titleFrom, MAX_TITLE } from './assistantService.js';

describe('assistantService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('getStatus reads GET /v1/ai/status', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { enabled: true, usedThisMonth: 3 } });
    await expect(assistantService.getStatus()).resolves.toEqual({ enabled: true, usedThisMonth: 3 });
    expect(get).toHaveBeenCalledWith('/v1/ai/status');
  });

  it('createSession titles the chat with the first question, cut to the server limit', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { id: 's1' } });
    await assistantService.createSession('  Which   holidays\nare coming up?  ');
    expect(post).toHaveBeenCalledWith('/v1/ai/sessions', { title: 'Which holidays are coming up?' });

    await assistantService.createSession('x'.repeat(MAX_TITLE + 50));
    expect(post.mock.calls[1][1].title).toHaveLength(MAX_TITLE);
    expect(titleFrom('')).toBe('');
  });

  it('listSessions asks for one page and keeps the last 30 days, in the server order', async () => {
    const now = Date.parse('2026-11-24T12:00:00Z');
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: {
        content: [
          { id: 'new', lastMessageAt: '2026-11-24T10:00:00Z' },
          { id: 'old', lastMessageAt: '2026-10-01T10:00:00Z' },
          { id: 'mid', lastMessageAt: null, createdAt: '2026-11-01T10:00:00Z' },
        ],
      },
    });
    const list = await assistantService.listSessions(now);
    expect(get).toHaveBeenCalledWith('/v1/ai/sessions', { params: { page: 0, size: 50 } });
    expect(list.map((s) => s.id)).toEqual(['new', 'mid']);
  });

  it('listMessages and deleteSession call the session paths', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: [{ id: 'm1' }] });
    const del = vi.spyOn(apiClient, 'delete').mockResolvedValue({ status: 204 });
    await expect(assistantService.listMessages('s1')).resolves.toEqual([{ id: 'm1' }]);
    expect(get).toHaveBeenCalledWith('/v1/ai/sessions/s1/messages');
    await assistantService.deleteSession('s1');
    expect(del).toHaveBeenCalledWith('/v1/ai/sessions/s1');
  });

  it('sendMessage posts only the content; the context is not sent yet (W-114.13)', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      status: 200,
      data: { message: { id: 'a1', role: 'ASSISTANT', content: 'Hi' }, proposedAction: null },
    });
    const res = await assistantService.sendMessage('s1', 'Hello', { route: '/me' });
    expect(res.message.content).toBe('Hi');
    expect(post.mock.calls[0][0]).toBe('/v1/ai/sessions/s1/messages');
    expect(post.mock.calls[0][1]).toEqual({ content: 'Hello' });
  });

  it('sendMessage turns a 429 into the client error shape with the Retry-After seconds', async () => {
    vi.spyOn(apiClient, 'post').mockResolvedValue({
      status: 429,
      headers: { 'retry-after': '17' },
      data: { code: 'AI_RATE_LIMITED', message: 'You are sending messages too quickly. Try again in a moment.' },
    });
    await expect(assistantService.sendMessage('s1', 'Hello')).rejects.toMatchObject({
      code: 'AI_RATE_LIMITED',
      status: 429,
      retryAfterSeconds: 17,
    });
  });

  it('sendMessage lets only a 429 through as a response; other statuses still reject in the client', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ status: 200, data: {} });
    await assistantService.sendMessage('s1', 'Hello');
    const { validateStatus } = post.mock.calls[0][2];
    expect(validateStatus(200)).toBe(true);
    expect(validateStatus(429)).toBe(true);
    expect(validateStatus(403)).toBe(false);
    expect(validateStatus(502)).toBe(false);
  });

  it('retryAfterSeconds reads plain and axios-style headers, and refuses junk', () => {
    expect(retryAfterSeconds({ 'retry-after': '5' })).toBe(5);
    expect(retryAfterSeconds({ get: (name) => (name === 'retry-after' ? '9' : null) })).toBe(9);
    expect(retryAfterSeconds({ 'retry-after': 'soon' })).toBeNull();
    expect(retryAfterSeconds(undefined)).toBeNull();
  });
});
