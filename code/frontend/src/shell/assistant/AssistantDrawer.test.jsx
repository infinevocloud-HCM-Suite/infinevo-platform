import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { assistantService } from './assistantService.js';
import { AssistantDrawer, TEXT, usageLine } from './AssistantDrawer.jsx';
import { EMPTY_STATE_TEXT } from './AssistantEmptyState.jsx';
import { THINKING_TEXT } from './MessageList.jsx';

const status = { enabled: true, monthlyRequestCap: 500, usedThisMonth: 1, month: '2026-11' };

function answer(content, id = 'a1') {
  return { message: { id, role: 'ASSISTANT', content, createdAt: '2026-11-24T10:00:00Z' }, proposedAction: null };
}

function renderDrawer(props = {}) {
  const onClose = vi.fn();
  const onAnswered = vi.fn();
  const utils = render(
    <AssistantDrawer open onClose={onClose} status={status} onAnswered={onAnswered} {...props} />,
  );
  return { ...utils, onClose, onAnswered };
}

function ask(text) {
  const input = screen.getByLabelText('Your question');
  fireEvent.change(input, { target: { value: text } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
}

/** A promise the test settles by hand, to hold an answer pending. */
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('AssistantDrawer', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the empty state and the usage line', () => {
    renderDrawer();
    expect(screen.getByText(EMPTY_STATE_TEXT)).toBeDefined();
    expect(screen.getByTestId('assistant-usage').textContent).toBe('1 of 500 this month');
    expect(screen.getByText('AI helper')).toBeDefined();
  });

  it('the first question creates a session, then sends; the answer shows and usage is refreshed', async () => {
    const create = vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    const send = vi.spyOn(assistantService, 'sendMessage').mockResolvedValue(answer('Diwali is on 1 Nov.'));
    const { onAnswered } = renderDrawer();

    ask('Which holidays are coming up?');

    expect(await screen.findByText('Diwali is on 1 Nov.')).toBeDefined();
    expect(create).toHaveBeenCalledWith('Which holidays are coming up?');
    expect(send).toHaveBeenCalledWith('s1', 'Which holidays are coming up?');
    expect(create.mock.invocationCallOrder[0]).toBeLessThan(send.mock.invocationCallOrder[0]);
    expect(screen.getByText('Which holidays are coming up?')).toBeDefined();
    expect(onAnswered).toHaveBeenCalledTimes(1);

    // The second question reuses the chat.
    send.mockResolvedValue(answer('You have 4 days left.', 'a2'));
    ask('How much leave do I have?');
    expect(await screen.findByText('You have 4 days left.')).toBeDefined();
    expect(create).toHaveBeenCalledTimes(1);
    expect(send).toHaveBeenLastCalledWith('s1', 'How much leave do I have?');
  });

  it('Shift+Enter does not send', () => {
    const create = vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    renderDrawer();
    const input = screen.getByLabelText('Your question');
    fireEvent.change(input, { target: { value: 'Line one' } });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', shiftKey: true });
    expect(create).not.toHaveBeenCalled();
    expect(input.getAttribute('maxlength')).toBe('2000');
  });

  it('shows Thinking and disables the input while the answer is pending', async () => {
    vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    const pending = deferred();
    vi.spyOn(assistantService, 'sendMessage').mockReturnValue(pending.promise);
    renderDrawer();

    ask('Hello');

    expect(await screen.findByText(THINKING_TEXT)).toBeDefined();
    expect(screen.getByLabelText('Your question').disabled).toBe(true);
    expect(screen.getByText('Hello')).toBeDefined();

    await act(async () => pending.resolve(answer('Hi there')));
    expect(await screen.findByText('Hi there')).toBeDefined();
    expect(screen.queryByText(THINKING_TEXT)).toBeNull();
    expect(screen.getByLabelText('Your question').disabled).toBe(false);
  });

  it('Escape closes the drawer', () => {
    const { onClose } = renderDrawer();
    fireEvent.keyDown(screen.getByLabelText('Your question'), { key: 'Escape', code: 'Escape', keyCode: 27 });
    expect(onClose).toHaveBeenCalled();
  });

  it('403 AI_DISABLED says the helper is off and disables the input', async () => {
    vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    vi.spyOn(assistantService, 'sendMessage').mockRejectedValue({ code: 'AI_DISABLED', status: 403 });
    renderDrawer();

    ask('Hello');

    expect(await screen.findByText(TEXT.disabled)).toBeDefined();
    expect(screen.getByLabelText('Your question').disabled).toBe(true);
  });

  it('429 AI_CAP_REACHED says the limit is used and disables the input; past chats still open', async () => {
    vi.spyOn(assistantService, 'createSession').mockRejectedValue({ code: 'AI_CAP_REACHED', status: 429 });
    vi.spyOn(assistantService, 'listSessions').mockResolvedValue([{ id: 's9', title: 'Old chat' }]);
    vi.spyOn(assistantService, 'listMessages').mockResolvedValue([
      { id: 'u1', role: 'USER', content: 'Old question' },
      { id: 'a1', role: 'ASSISTANT', content: 'Old answer' },
    ]);
    renderDrawer();

    ask('Hello');

    expect(await screen.findByText(TEXT.capReached)).toBeDefined();
    expect(screen.getByLabelText('Your question').disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: /Past chats/ }));
    fireEvent.click(await screen.findByText('Old chat'));
    expect(await screen.findByText('Old answer')).toBeDefined();
  });

  it('429 AI_RATE_LIMITED says how many seconds to wait', async () => {
    vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    vi.spyOn(assistantService, 'sendMessage').mockRejectedValue({
      code: 'AI_RATE_LIMITED',
      status: 429,
      retryAfterSeconds: 12,
      message: 'You are sending messages too quickly. Try again in a moment.',
    });
    renderDrawer();

    ask('Hello');

    expect(await screen.findByText(TEXT.rateLimited(12))).toBeDefined();
    expect(screen.getByLabelText('Your question').disabled).toBe(false);
  });

  it('a long rate-limit wait shows the server words instead of a count of seconds', async () => {
    vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    vi.spyOn(assistantService, 'sendMessage').mockRejectedValue({
      code: 'AI_RATE_LIMITED',
      status: 429,
      retryAfterSeconds: 40000,
      message: "You have reached today's limit for the AI helper. Try again tomorrow.",
    });
    renderDrawer();

    ask('Hello');

    expect(await screen.findByText("You have reached today's limit for the AI helper. Try again tomorrow.")).toBeDefined();
  });

  it('any other error offers Retry, which resends the same question; the question stays in the list', async () => {
    vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    const send = vi
      .spyOn(assistantService, 'sendMessage')
      .mockRejectedValueOnce({ code: 'INTERNAL', status: 502, message: 'The helper could not answer just now.' })
      .mockResolvedValueOnce(answer('Second time lucky'));
    renderDrawer();

    ask('Which holidays are coming up?');

    expect(await screen.findByText(TEXT.failed)).toBeDefined();
    expect(screen.getAllByText('Which holidays are coming up?')).toHaveLength(1);

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

    expect(await screen.findByText('Second time lucky')).toBeDefined();
    expect(send).toHaveBeenCalledTimes(2);
    expect(send.mock.calls[1]).toEqual(['s1', 'Which holidays are coming up?']);
    expect(screen.getAllByText('Which holidays are coming up?')).toHaveLength(1);
    expect(screen.queryByText(TEXT.failed)).toBeNull();
  });

  it('New chat starts over and keeps the old chat on the server', async () => {
    vi.spyOn(assistantService, 'createSession').mockResolvedValueOnce({ id: 's1' }).mockResolvedValueOnce({ id: 's2' });
    const send = vi.spyOn(assistantService, 'sendMessage').mockResolvedValue(answer('Answer'));
    renderDrawer();
    ask('First');
    expect(await screen.findByText('Answer')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /New chat/ }));
    expect(screen.getByText(EMPTY_STATE_TEXT)).toBeDefined();

    ask('Second');
    await waitFor(() => expect(send).toHaveBeenLastCalledWith('s2', 'Second'));
  });

  it('deleting the open chat from Past chats clears it', async () => {
    vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    vi.spyOn(assistantService, 'sendMessage').mockResolvedValue(answer('Answer'));
    vi.spyOn(assistantService, 'listSessions').mockResolvedValue([{ id: 's1', title: 'First' }]);
    const del = vi.spyOn(assistantService, 'deleteSession').mockResolvedValue();
    renderDrawer();
    ask('First');
    expect(await screen.findByText('Answer')).toBeDefined();

    fireEvent.click(screen.getByRole('button', { name: /Past chats/ }));
    fireEvent.click(await screen.findByRole('button', { name: 'Delete chat First' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(await screen.findByText('No earlier chats.')).toBeDefined();
    expect(del).toHaveBeenCalledWith('s1');
    fireEvent.click(screen.getByRole('button', { name: /Past chats/ }));
    expect(screen.getByText(EMPTY_STATE_TEXT)).toBeDefined();
  });

  it('the drawer is full width on a phone', () => {
    renderDrawer({ phone: true });
    const wrapper = document.querySelector('.assistant-drawer .ant-drawer-content-wrapper');
    expect(wrapper.style.width).toBe('100%');
  });

  it('usageLine reads "N of M this month", or "N this month" with no cap', () => {
    expect(usageLine({ usedThisMonth: 3, monthlyRequestCap: 500 })).toBe('3 of 500 this month');
    expect(usageLine({ usedThisMonth: 3, monthlyRequestCap: null })).toBe('3 this month');
    expect(usageLine(null)).toBeNull();
  });
});
