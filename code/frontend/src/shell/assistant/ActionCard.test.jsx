import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { assistantService } from './assistantService.js';
import { ActionCard, ACTION_TEXT, RECHECK_MS, formatRemaining } from './ActionCard.jsx';

const IN_15_MIN = () => new Date(Date.now() + 15 * 60 * 1000).toISOString();

function leaveAction(over = {}) {
  return {
    intentId: 'i1',
    actionCode: 'core.leave.apply',
    summary: 'Apply for 5 days of Casual Leave from 1 Dec',
    fields: [
      { label: 'Leave type', value: 'Casual Leave' },
      { label: 'From', value: '2026-12-01' },
      { label: 'To', value: '2026-12-05' },
      { label: 'Days', value: '5' },
      { label: 'Reason', value: 'Family trip' },
    ],
    status: 'PROPOSED',
    expiresAt: IN_15_MIN(),
    result: null,
    ...over,
  };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function renderCard(intentId = 'i1') {
  return render(
    <MemoryRouter>
      <ActionCard intentId={intentId} />
    </MemoryRouter>,
  );
}

const confirmButton = () => screen.queryByRole('button', { name: /Confirm/ });
const cancelButton = () => screen.queryByRole('button', { name: /Cancel/ });

describe('ActionCard', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('draws the summary and every field in the server order, with Confirm, Cancel and a countdown', async () => {
    const get = vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    renderCard();

    const card = await screen.findByRole('group', { name: 'Apply for 5 days of Casual Leave from 1 Dec' });
    expect(get).toHaveBeenCalledWith('i1');
    const labels = [...card.querySelectorAll('dt')].map((n) => n.textContent);
    const values = [...card.querySelectorAll('dd')].map((n) => n.textContent);
    expect(labels).toEqual(['Leave type', 'From', 'To', 'Days', 'Reason']);
    expect(values).toEqual(['Casual Leave', '2026-12-01', '2026-12-05', '5', 'Family trip']);
    expect(confirmButton().id).toBe('btn-ai-confirm');
    expect(cancelButton()).not.toBeNull();
    const countdown = screen.getByTestId('assistant-action-countdown');
    expect(countdown.textContent).toMatch(/^Expires in 1[45]:\d\d$/);
    // Not read out every second by the message list's live region.
    expect(countdown.getAttribute('aria-live')).toBe('off');
  });

  it('formats the countdown as m:ss', () => {
    expect(formatRemaining(872)).toBe('14:32');
    expect(formatRemaining(5)).toBe('0:05');
    expect(formatRemaining(-3)).toBe('0:00');
  });

  it('Confirm calls confirmAction once with the id only and shows Done with the link', async () => {
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    const confirm = vi.spyOn(assistantService, 'confirmAction').mockResolvedValue({
      intentId: 'i1',
      actionCode: 'core.leave.apply',
      status: 'CONFIRMED',
      result: { leaveRequestId: 'lr-9', status: 'PENDING', traceId: 't' },
    });
    renderCard();
    await screen.findByRole('group');

    fireEvent.click(confirmButton());

    expect(await screen.findByText(ACTION_TEXT.done)).toBeDefined();
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(confirm.mock.calls[0]).toEqual(['i1']);
    const link = screen.getByRole('link', { name: 'Open the leave request' });
    expect(link.getAttribute('href')).toBe('/me/leave/requests/lr-9');
    expect(confirmButton()).toBeNull();
    expect(cancelButton()).toBeNull();
    // The fields stay, so the user still sees what was done.
    expect(screen.getByText('Casual Leave')).toBeDefined();
  });

  it('two fast clicks on Confirm call confirmAction once and show Done once', async () => {
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    const pending = deferred();
    const confirm = vi.spyOn(assistantService, 'confirmAction').mockReturnValue(pending.promise);
    renderCard();
    await screen.findByRole('group');

    const button = confirmButton();
    fireEvent.click(button);
    fireEvent.click(button);
    expect(confirm).toHaveBeenCalledTimes(1);

    // While confirming: a spinner on Confirm and both buttons disabled.
    await waitFor(() => expect(button.className).toContain('ant-btn-loading'));
    expect(button.disabled).toBe(true);
    expect(cancelButton().disabled).toBe(true);

    await act(async () => {
      pending.resolve({ intentId: 'i1', actionCode: 'core.leave.apply', status: 'CONFIRMED', result: { leaveRequestId: 'lr-1' } });
    });
    expect(screen.getAllByText(ACTION_TEXT.done)).toHaveLength(1);
    expect(confirm).toHaveBeenCalledTimes(1);
  });

  it('Cancel calls cancelAction with the id only, shows Cancelled and sends nothing else', async () => {
    const get = vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    const confirm = vi.spyOn(assistantService, 'confirmAction');
    const cancel = vi.spyOn(assistantService, 'cancelAction').mockResolvedValue({
      intentId: 'i1',
      actionCode: 'core.leave.apply',
      status: 'CANCELLED',
      result: null,
    });
    renderCard();
    await screen.findByRole('group');

    fireEvent.click(cancelButton());

    expect(await screen.findByText(ACTION_TEXT.cancelled)).toBeDefined();
    expect(cancel.mock.calls).toEqual([['i1']]);
    expect(confirm).not.toHaveBeenCalled();
    expect(get).toHaveBeenCalledTimes(1);
    expect(confirmButton()).toBeNull();
    expect(cancelButton()).toBeNull();
  });

  it('when the countdown reaches 0 it asks the server and shows Expired', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] });
    const get = vi
      .spyOn(assistantService, 'getAction')
      .mockResolvedValueOnce(leaveAction({ expiresAt: new Date(Date.now() + 3000).toISOString() }))
      .mockResolvedValueOnce(leaveAction({ status: 'EXPIRED' }));
    renderCard();
    await act(async () => {
      await Promise.resolve();
    });
    expect(screen.getByTestId('assistant-action-countdown').textContent).toBe('Expires in 0:03');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(screen.getByTestId('assistant-action-countdown').textContent).toBe('Expires in 0:02');
    expect(get).toHaveBeenCalledTimes(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2500);
    });
    expect(get).toHaveBeenCalledTimes(2);
    expect(screen.getByText(ACTION_TEXT.expired)).toBeDefined();
    expect(confirmButton()).toBeNull();
  });

  it('a 410 on Confirm asks the server and shows Expired', async () => {
    const get = vi
      .spyOn(assistantService, 'getAction')
      .mockResolvedValueOnce(leaveAction())
      .mockResolvedValueOnce(leaveAction({ status: 'EXPIRED' }));
    vi.spyOn(assistantService, 'confirmAction').mockRejectedValue({ code: 'AI_ACTION_EXPIRED', status: 410, message: 'x' });
    renderCard();
    await screen.findByRole('group');

    fireEvent.click(confirmButton());

    expect(await screen.findByText(ACTION_TEXT.expired)).toBeDefined();
    expect(get).toHaveBeenCalledTimes(2);
    expect(confirmButton()).toBeNull();
  });

  it('FAILED shows the server reason, whole, even at 500 characters', async () => {
    const reason = 'R'.repeat(250) + ' ' + 'S'.repeat(249);
    expect(reason).toHaveLength(500);
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction({ status: 'FAILED', result: { reason } }));
    renderCard();

    expect(await screen.findByText(ACTION_TEXT.failed)).toBeDefined();
    expect(screen.getByTestId('assistant-action-reason').textContent).toBe(reason);
    expect(document.body.textContent).toContain(reason);
    expect(confirmButton()).toBeNull();
  });

  it('a 409 AI_ACTION_FAILED on Confirm reads the reason back from the server', async () => {
    vi.spyOn(assistantService, 'getAction')
      .mockResolvedValueOnce(leaveAction())
      .mockResolvedValueOnce(leaveAction({ status: 'FAILED', result: { reason: 'Not enough Casual Leave balance' } }));
    vi.spyOn(assistantService, 'confirmAction').mockRejectedValue({ code: 'AI_ACTION_FAILED', status: 409, message: 'm' });
    renderCard();
    await screen.findByRole('group');

    fireEvent.click(confirmButton());

    expect(await screen.findByText('Not enough Casual Leave balance')).toBeDefined();
    expect(screen.getByText(ACTION_TEXT.failed)).toBeDefined();
    expect(confirmButton()).toBeNull();
  });

  it('a 403 on Confirm says the permission is gone; AI_DISABLED says the helper is off', async () => {
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    const confirm = vi
      .spyOn(assistantService, 'confirmAction')
      .mockRejectedValueOnce({ code: 'FORBIDDEN', status: 403, message: 'Forbidden' });
    const first = renderCard();
    await screen.findByRole('group');
    fireEvent.click(confirmButton());
    expect(await screen.findByText(ACTION_TEXT.forbidden)).toBeDefined();
    expect(confirmButton()).toBeNull();
    expect(cancelButton()).toBeNull();
    first.unmount();

    confirm.mockRejectedValueOnce({ code: 'AI_DISABLED', status: 403, message: 'off' });
    renderCard();
    await screen.findByRole('group');
    fireEvent.click(confirmButton());
    expect(await screen.findByText(ACTION_TEXT.disabled)).toBeDefined();
    expect(confirmButton()).toBeNull();
  });

  it('a network error on Confirm brings the buttons back with a message, and Confirm works again', async () => {
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    const confirm = vi
      .spyOn(assistantService, 'confirmAction')
      .mockRejectedValueOnce({ code: 'INTERNAL', message: 'Network Error', status: undefined })
      .mockResolvedValueOnce({ intentId: 'i1', actionCode: 'core.leave.apply', status: 'CONFIRMED', result: { leaveRequestId: 'lr-2' } });
    renderCard();
    await screen.findByRole('group');

    fireEvent.click(confirmButton());
    expect(await screen.findByText(ACTION_TEXT.network)).toBeDefined();
    await waitFor(() => expect(confirmButton().disabled).toBe(false));
    expect(cancelButton().disabled).toBe(false);

    fireEvent.click(confirmButton());
    expect(await screen.findByText(ACTION_TEXT.done)).toBeDefined();
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(confirm.mock.calls).toEqual([['i1'], ['i1']]);
  });

  it('shows a field value with markup as text and creates no element from it', async () => {
    const attack = '<img src=x onerror="window.__aiCardPwned=1"><b>bold</b>';
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(
      leaveAction({ summary: '<i>Leave</i>', fields: [{ label: '<u>Reason</u>', value: attack }] }),
    );
    const { container } = renderCard();
    const card = await screen.findByRole('group');

    expect(card.querySelector('img')).toBeNull();
    expect(card.querySelector('b')).toBeNull();
    expect(card.querySelector('i')).toBeNull();
    expect(card.querySelector('u')).toBeNull();
    expect(container.querySelector('[onerror]')).toBeNull();
    expect(card.querySelector('dd').textContent).toBe(attack);
    expect(card.textContent).toContain('<i>Leave</i>');
    expect(window.__aiCardPwned).toBeUndefined();
  });

  it('a made-up actionCode with three fields draws the same card and no link', async () => {
    vi.spyOn(assistantService, 'getAction').mockResolvedValue({
      intentId: 'x1',
      actionCode: 'core.made.up',
      summary: 'Do a made-up thing',
      fields: [
        { label: 'One', value: 'a' },
        { label: 'Two', value: 'b' },
        { label: 'Three', value: 'c' },
      ],
      status: 'PROPOSED',
      expiresAt: IN_15_MIN(),
      result: null,
    });
    vi.spyOn(assistantService, 'confirmAction').mockResolvedValue({
      intentId: 'x1',
      actionCode: 'core.made.up',
      status: 'CONFIRMED',
      result: { thingId: 't1' },
    });
    renderCard('x1');
    const card = await screen.findByRole('group', { name: 'Do a made-up thing' });
    expect(card.querySelectorAll('dt')).toHaveLength(3);
    expect(confirmButton()).not.toBeNull();

    fireEvent.click(confirmButton());
    expect(await screen.findByText(ACTION_TEXT.done)).toBeDefined();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('rule 1: the card shows what getAction returns, nothing from the message', async () => {
    // The helper's message said "2 days from 4 Dec"; the stored action is 5 days from 1 Dec.
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    renderCard();
    const card = await screen.findByRole('group');
    expect(card.textContent).toContain('5 days of Casual Leave from 1 Dec');
    expect(card.textContent).toContain('2026-12-01');
    expect(card.textContent).not.toContain('2 days');
    expect(card.textContent).not.toContain('4 Dec');
  });

  it('rule 2: no Confirm before getAction answers; on failure "Could not load" with Retry, still no Confirm', async () => {
    const pending = deferred();
    const get = vi.spyOn(assistantService, 'getAction').mockReturnValueOnce(pending.promise);
    renderCard();

    expect(screen.getByTestId('assistant-action-loading')).toBeDefined();
    expect(confirmButton()).toBeNull();

    await act(async () => {
      pending.reject({ code: 'INTERNAL', message: 'Network Error' });
    });
    expect(screen.getByText(ACTION_TEXT.loadFailed)).toBeDefined();
    expect(confirmButton()).toBeNull();

    get.mockResolvedValueOnce(leaveAction());
    fireEvent.click(screen.getByRole('button', { name: ACTION_TEXT.retry }));
    expect(await screen.findByRole('group')).toBeDefined();
    expect(confirmButton()).not.toBeNull();
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('a reloaded card draws the end state the server holds', async () => {
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(
      leaveAction({ status: 'CONFIRMED', result: { leaveRequestId: 'lr-7' } }),
    );
    renderCard();
    expect(await screen.findByText(ACTION_TEXT.done)).toBeDefined();
    expect(screen.getByRole('link').getAttribute('href')).toBe('/me/leave/requests/lr-7');
    expect(screen.queryByTestId('assistant-action-countdown')).toBeNull();
  });

  it('when the check at 0:00 fails, it asks the server again after RECHECK_MS', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] });
    const get = vi
      .spyOn(assistantService, 'getAction')
      .mockResolvedValueOnce(leaveAction({ expiresAt: new Date(Date.now() + 1000).toISOString() }))
      .mockRejectedValueOnce({ code: 'INTERNAL', message: 'Network Error' })
      .mockResolvedValueOnce(leaveAction({ status: 'EXPIRED' }));
    renderCard();
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1500);
    });
    expect(get).toHaveBeenCalledTimes(2);
    expect(screen.getByTestId('assistant-action-countdown').textContent).toBe('Expires in 0:00');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(RECHECK_MS + 1500);
    });
    expect(get).toHaveBeenCalledTimes(3);
    expect(screen.getByText(ACTION_TEXT.expired)).toBeDefined();
    expect(confirmButton()).toBeNull();
  });

  it('a 404 on Confirm (the chat was deleted) shows that the action no longer exists, with no buttons', async () => {
    vi.spyOn(assistantService, 'getAction').mockResolvedValue(leaveAction());
    vi.spyOn(assistantService, 'confirmAction').mockRejectedValue({ code: 'NOT_FOUND', status: 404, message: 'Not found' });
    renderCard();
    await screen.findByRole('group');

    fireEvent.click(confirmButton());

    expect(await screen.findByText(ACTION_TEXT.gone)).toBeDefined();
    expect(confirmButton()).toBeNull();
    expect(cancelButton()).toBeNull();
    expect(screen.queryByText(ACTION_TEXT.network)).toBeNull();
  });
});
