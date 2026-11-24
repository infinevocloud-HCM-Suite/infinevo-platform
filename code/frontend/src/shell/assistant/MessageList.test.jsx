import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MessageList, THINKING_TEXT } from './MessageList.jsx';

describe('MessageList', () => {
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
    // W-114.9 seam: a proposed action is plain text for now.
    expect(screen.getByTestId('assistant-helper-message').textContent).toContain('Hello');
    expect(screen.getByText(THINKING_TEXT)).toBeDefined();
  });
});
