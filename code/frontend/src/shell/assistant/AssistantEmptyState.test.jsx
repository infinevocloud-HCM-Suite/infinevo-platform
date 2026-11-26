import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { assistantService } from './assistantService.js';
import { AssistantEmptyState, EMPTY_STATE_TEXT, TASKS_HEADING } from './AssistantEmptyState.jsx';

const STARTERS = [
  { id: 'leave-apply', label: 'Apply leave for me', prompt: 'Apply leave for me', kind: 'TASK' },
  { id: 'leave-left', label: 'How many leave days do I have left?', prompt: 'How many leave days?', kind: 'QUESTION' },
  { id: 'holidays-next', label: 'Which holidays are coming up?', prompt: 'Which holidays?', kind: 'QUESTION' },
];

describe('AssistantEmptyState (W-114.22)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the chips from the service: questions first, then tasks under "Do it for me"', async () => {
    vi.spyOn(assistantService, 'listStarters').mockResolvedValue(STARTERS);
    render(<AssistantEmptyState onPick={vi.fn()} />);

    const questions = await screen.findByTestId('assistant-starters-questions');
    expect(within(questions).getAllByRole('button').map((b) => b.getAttribute('aria-label'))).toEqual([
      'How many leave days do I have left?',
      'Which holidays are coming up?',
    ]);
    const tasks = screen.getByTestId('assistant-starters-tasks');
    expect(within(tasks).getAllByRole('button').map((b) => b.textContent)).toEqual(['Apply leave for me']);
    expect(screen.getByText(TASKS_HEADING)).toBeDefined();
    expect(screen.getByText(EMPTY_STATE_TEXT)).toBeDefined();
  });

  it('a click sends the starter prompt, not its label', async () => {
    vi.spyOn(assistantService, 'listStarters').mockResolvedValue(STARTERS);
    const onPick = vi.fn();
    render(<AssistantEmptyState onPick={onPick} />);

    fireEvent.click(await screen.findByRole('button', { name: 'How many leave days do I have left?' }));
    expect(onPick).toHaveBeenCalledWith('How many leave days?');
  });

  it('an empty list or a failed call shows the one line and no chips, no error', async () => {
    const list = vi.spyOn(assistantService, 'listStarters').mockResolvedValue([]);
    const { unmount } = render(<AssistantEmptyState onPick={vi.fn()} />);
    await waitFor(() => expect(list).toHaveBeenCalled());
    expect(screen.getByText(EMPTY_STATE_TEXT)).toBeDefined();
    expect(screen.queryAllByRole('button')).toEqual([]);
    unmount();

    list.mockRejectedValue(new Error('500'));
    render(<AssistantEmptyState onPick={vi.fn()} />);
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
    expect(screen.getByText(EMPTY_STATE_TEXT)).toBeDefined();
    expect(screen.queryAllByRole('button')).toEqual([]);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('with no onPick (helper busy or off) the chips are shown but disabled', async () => {
    vi.spyOn(assistantService, 'listStarters').mockResolvedValue(STARTERS);
    render(<AssistantEmptyState />);
    const chip = await screen.findByRole('button', { name: 'Apply leave for me' });
    expect(chip.disabled).toBe(true);
  });
});
