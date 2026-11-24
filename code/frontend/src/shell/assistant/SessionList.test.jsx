import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { SessionList, UNTITLED } from './SessionList.jsx';

const sessions = [
  { id: 's2', title: 'Which holidays are coming up?', lastMessageAt: '2026-11-24T10:00:00Z' },
  { id: 's1', title: null, createdAt: '2026-11-20T10:00:00Z', lastMessageAt: null },
];

describe('SessionList', () => {
  it('lists chats by first question and date, in the order given', () => {
    render(<SessionList sessions={sessions} onOpen={vi.fn()} onDelete={vi.fn()} />);
    const items = screen.getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Which holidays are coming up?');
    expect(items[0].textContent).toContain('2026');
    expect(items[1].textContent).toContain(UNTITLED);
  });

  it('opens a chat when it is picked', () => {
    const onOpen = vi.fn();
    render(<SessionList sessions={sessions} onOpen={onOpen} onDelete={vi.fn()} />);
    fireEvent.click(screen.getByText('Which holidays are coming up?'));
    expect(onOpen).toHaveBeenCalledWith(sessions[0]);
  });

  it('asks before deleting, then calls onDelete', async () => {
    const onDelete = vi.fn();
    render(<SessionList sessions={sessions} onOpen={vi.fn()} onDelete={onDelete} />);
    fireEvent.click(screen.getByRole('button', { name: 'Delete chat Which holidays are coming up?' }));
    expect(onDelete).not.toHaveBeenCalled();
    expect(await screen.findByText('Delete this chat?')).toBeDefined();
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(onDelete).toHaveBeenCalledWith(sessions[0]));
  });

  it('says so when there are no earlier chats', () => {
    render(<SessionList sessions={[]} onOpen={vi.fn()} onDelete={vi.fn()} />);
    expect(screen.getByText('No earlier chats.')).toBeDefined();
  });
});
