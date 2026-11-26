import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within, act } from '@testing-library/react';
import { assistantService } from './assistantService.js';
import { AssistantEmptyState, EMPTY_STATE_TEXT, QUESTIONS_GROUP, TASKS_HEADING } from './AssistantEmptyState.jsx';

const tenantListeners = vi.hoisted(() => new Set());
vi.mock('../auth/keycloak.js', async (importOriginal) => ({
  ...(await importOriginal()),
  onTenantChange: (listener) => {
    tenantListeners.add(listener);
    return () => tenantListeners.delete(listener);
  },
}));

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
    expect(screen.getByRole('group', { name: QUESTIONS_GROUP })).toBe(questions);
    expect(screen.getByRole('group', { name: TASKS_HEADING })).toBe(tasks);
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

  it('a tenant change asks for the new tenant\'s starters', async () => {
    const list = vi
      .spyOn(assistantService, 'listStarters')
      .mockResolvedValueOnce(STARTERS)
      .mockResolvedValueOnce([]);
    render(<AssistantEmptyState onPick={vi.fn()} />);
    await screen.findByRole('button', { name: 'Apply leave for me' });

    act(() => tenantListeners.forEach((listener) => listener('t2', 't1')));

    await waitFor(() => expect(list).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(screen.queryAllByRole('button')).toEqual([]));
  });

  it('with no onPick (helper busy or off) the chips are shown but disabled', async () => {
    vi.spyOn(assistantService, 'listStarters').mockResolvedValue(STARTERS);
    render(<AssistantEmptyState />);
    const chip = await screen.findByRole('button', { name: 'Apply leave for me' });
    expect(chip.disabled).toBe(true);
  });
});
