import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MessageList, THINKING_TEXT } from './MessageList.jsx';
import { assistantService } from './assistantService.js';
import { ActionCard, ACTION_TEXT } from './ActionCard.jsx';

// The real card, wrapped so a test can read the props MessageList gave it.
vi.mock('./ActionCard.jsx', async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, ActionCard: vi.fn(actual.ActionCard) };
});

describe('MessageList', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    ActionCard.mockClear();
  });

  it('shows an answer carrying markup as text and creates no element from it', () => {
    const attack = '<img src=x onerror="window.__aiPwned=1"><script>window.__aiPwned=2</script><b>bold</b>';
    const { container } = render(
      <MessageList messages={[{ id: 'a1', role: 'ASSISTANT', content: attack }]} />,
    );

    const log = screen.getByRole('log');
    expect(log.querySelector('img')).toBeNull();
    expect(log.querySelector('script')).toBeNull();
    expect(log.querySelector('b')).toBeNull();
    expect(container.querySelector('[onerror]')).toBeNull();
    expect(screen.getByTestId('assistant-helper-message').textContent).toContain(attack);
    expect(window.__aiPwned).toBeUndefined();
  });

  it('keeps line breaks in the text', () => {
    render(<MessageList messages={[{ id: 'a1', role: 'ASSISTANT', content: 'Line one\nLine two' }]} />);
    const bubble = screen.getByTestId('assistant-helper-message').querySelector('.assistant-bubble');
    expect(bubble.textContent).toBe('Line one\nLine two');
    expect(bubble.className).toContain('assistant-bubble');
  });

  it('is a polite live log with user and helper rows, and a Thinking row while pending', () => {
    vi.spyOn(assistantService, 'getAction').mockReturnValue(new Promise(() => {}));
    render(
      <MessageList
        messages={[
          { id: 'u1', role: 'USER', content: 'Hi' },
          { id: 'a1', role: 'ASSISTANT', content: 'Hello', proposedAction: { intentId: 'i1' } },
        ]}
        pending
      />,
    );
    const log = screen.getByRole('log');
    expect(log.getAttribute('aria-live')).toBe('polite');
    expect(screen.getByTestId('assistant-user-message').textContent).toContain('Hi');
    expect(screen.getByTestId('assistant-helper-message').textContent).toContain('Hello');
    expect(screen.getByText(THINKING_TEXT)).toBeDefined();
  });

  it('a new answer with proposedAction renders ActionCard with intentId as its only prop', () => {
    vi.spyOn(assistantService, 'getAction').mockReturnValue(new Promise(() => {}));
    render(
      <MessageList
        messages={[
          { id: 'u1', role: 'USER', content: 'apply casual leave for 4 Dec' },
          {
            id: 'a1',
            role: 'ASSISTANT',
            content: 'I can apply 2 days from 4 Dec.',
            proposedAction: { intentId: 'i1', summary: '2 days from 4 Dec', fields: [{ label: 'Days', value: '2' }] },
          },
          { id: 'a2', role: 'ASSISTANT', content: 'No action here.', proposedAction: null },
        ]}
      />,
    );
    expect(ActionCard).toHaveBeenCalled();
    for (const [props] of ActionCard.mock.calls) {
      expect(props).toEqual({ intentId: 'i1' });
    }
    expect(screen.getAllByTestId('assistant-action-card')).toHaveLength(1);
    // The card under the message is loading from the server; nothing of the message text is in it.
    const card = screen.getByTestId('assistant-action-card');
    expect(card.textContent).not.toContain('4 Dec');
    expect(within(card).queryByRole('button', { name: /Confirm/ })).toBeNull();
  });

  it('a loaded chat with one confirmed and one expired action draws each in its true state', async () => {
    const get = vi.spyOn(assistantService, 'getAction').mockImplementation(async (intentId) =>
      intentId === 'done-1'
        ? {
            intentId,
            actionCode: 'core.leave.apply',
            summary: 'Apply for 1 day of Casual Leave',
            fields: [{ label: 'Days', value: '1' }],
            status: 'CONFIRMED',
            expiresAt: '2026-11-24T10:15:00Z',
            result: { leaveRequestId: 'lr-1', status: 'PENDING' },
          }
        : {
            intentId,
            actionCode: 'core.leave.apply',
            summary: 'Apply for 2 days of Sick Leave',
            fields: [{ label: 'Days', value: '2' }],
            status: 'EXPIRED',
            expiresAt: '2026-11-24T11:15:00Z',
            result: null,
          },
    );
    render(
      <MessageList
        messages={[
          { id: 'a1', role: 'ASSISTANT', content: 'Shall I apply?', proposedAction: { intentId: 'done-1' } },
          { id: 'a2', role: 'ASSISTANT', content: 'Shall I apply this one?', proposedAction: { intentId: 'old-2' } },
        ]}
      />,
    );

    const done = await screen.findByRole('group', { name: 'Apply for 1 day of Casual Leave' });
    const expired = await screen.findByRole('group', { name: 'Apply for 2 days of Sick Leave' });
    expect(within(done).getByText(ACTION_TEXT.done)).toBeDefined();
    expect(within(expired).getByText(ACTION_TEXT.expired)).toBeDefined();
    expect(screen.queryByRole('button', { name: /Confirm/ })).toBeNull();
    expect(get.mock.calls.map((c) => c[0]).sort()).toEqual(['done-1', 'old-2']);
  });

  it('W-114.13: an allowed app: link renders as an anchor to that path; the markup case still creates no element', () => {
    const attack = '<img src=x onerror="window.__aiPwned=3">';
    render(
      <MemoryRouter>
        <MessageList
          allowedPaths={new Set(['/leave/types'])}
          messages={[
            { id: 'a1', role: 'ASSISTANT', content: `Open [Leave types](app:/leave/types). ${attack}` },
            { id: 'u1', role: 'USER', content: 'Is [Leave types](app:/leave/types) right?' },
          ]}
        />
      </MemoryRouter>,
    );

    const links = screen.getAllByTestId('assistant-answer-link');
    expect(links).toHaveLength(1);
    expect(links[0].tagName).toBe('A');
    expect(links[0].getAttribute('href')).toBe('/leave/types');
    expect(links[0].textContent).toBe('Leave types');
    const log = screen.getByRole('log');
    expect(log.querySelector('img')).toBeNull();
    expect(window.__aiPwned).toBeUndefined();
    expect(screen.getByTestId('assistant-user-message').textContent).toContain('[Leave types](app:/leave/types)');
  });
});
