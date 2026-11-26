import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { assistantService } from './assistantService.js';
import { AssistantLauncher } from './AssistantLauncher.jsx';
import PropTypes from 'prop-types';
import { MemoryRouter } from 'react-router-dom';
import { NavigationProvider } from '../navigation/useNavigation.js';

const FEED = { items: [{ key: 'core.leave.types', labelKey: 'nav.leave.types', path: '/leave/types' }], actions: [] };

/** The drawer reads the route and the menu (W-114.13). */
function Shell({ children }) {
  return (
    <MemoryRouter initialEntries={['/leave/types']}>
      <NavigationProvider value={FEED}>{children}</NavigationProvider>
    </MemoryRouter>
  );
}

Shell.propTypes = { children: PropTypes.node };

const on = { enabled: true, monthlyRequestCap: 500, usedThisMonth: 0, month: '2026-11' };

describe('AssistantLauncher', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(assistantService, 'linkTargets').mockResolvedValue([]);
  });

  it('renders no button when the helper is off', async () => {
    const getStatus = vi.spyOn(assistantService, 'getStatus').mockResolvedValue({ ...on, enabled: false });
    const { container } = render(<AssistantLauncher />, { wrapper: Shell });
    await waitFor(() => expect(getStatus).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole('button', { name: 'Ask the AI helper' })).toBeNull();
    expect(container.innerHTML).toBe('');
  });

  it('renders no button when the status call fails', async () => {
    const getStatus = vi.spyOn(assistantService, 'getStatus').mockRejectedValue({ code: 'INTERNAL', status: 500 });
    const { container } = render(<AssistantLauncher />, { wrapper: Shell });
    await waitFor(() => expect(getStatus).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole('button', { name: 'Ask the AI helper' })).toBeNull();
    expect(container.innerHTML).toBe('');
  });

  it('renders the button when the helper is on, and it opens the drawer', async () => {
    vi.spyOn(assistantService, 'getStatus').mockResolvedValue(on);
    render(<AssistantLauncher />, { wrapper: Shell });
    const button = await screen.findByRole('button', { name: 'Ask the AI helper' });
    expect(document.getElementById('btn-ai-helper')).not.toBeNull();
    expect(screen.queryByText('AI helper')).toBeNull();

    fireEvent.click(button);
    expect(await screen.findByText('AI helper')).toBeDefined();
    expect(screen.getByTestId('assistant-usage').textContent).toBe('0 of 500 this month');
  });

  it('moves focus to the input on open and back to the button on close', async () => {
    vi.spyOn(assistantService, 'getStatus').mockResolvedValue(on);
    render(<AssistantLauncher />, { wrapper: Shell });
    const button = await screen.findByRole('button', { name: 'Ask the AI helper' });

    fireEvent.click(button);
    const input = await screen.findByLabelText('Your question');
    await waitFor(() => expect(document.activeElement).toBe(input));

    fireEvent.keyDown(input, { key: 'Escape', code: 'Escape', keyCode: 27 });
    await waitFor(() => expect(document.activeElement).toBe(document.getElementById('btn-ai-helper')));
  });

  it('refreshes the usage line after an answer', async () => {
    const getStatus = vi
      .spyOn(assistantService, 'getStatus')
      .mockResolvedValueOnce(on)
      .mockResolvedValueOnce({ ...on, usedThisMonth: 1 });
    vi.spyOn(assistantService, 'createSession').mockResolvedValue({ id: 's1' });
    vi.spyOn(assistantService, 'sendMessage').mockResolvedValue({
      message: { id: 'a1', role: 'ASSISTANT', content: 'Answer' },
      proposedAction: null,
    });
    render(<AssistantLauncher />, { wrapper: Shell });
    fireEvent.click(await screen.findByRole('button', { name: 'Ask the AI helper' }));

    const input = screen.getByLabelText('Your question');
    fireEvent.change(input, { target: { value: 'Hello' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByText('Answer')).toBeDefined();
    await waitFor(() => expect(screen.getByTestId('assistant-usage').textContent).toBe('1 of 500 this month'));
    expect(getStatus).toHaveBeenCalledTimes(2);
  });
});
