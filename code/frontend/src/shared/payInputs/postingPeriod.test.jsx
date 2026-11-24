import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, renderHook, screen, waitFor } from '@testing-library/react';
import { apiClient } from '@shared/api/client';
import {
  POSTING_STATE,
  isClosed,
  monthName,
  postingNote,
  postingService,
  usePostingPeriod,
  usePostingPeriods,
} from './postingPeriod.js';
import { PostingNote } from './PostingNote.jsx';

vi.mock('@shared/api/client', () => ({ apiClient: { get: vi.fn() } }));

const reply = (requested, posted, state) => ({
  data: { requested_period: requested, posted_period: posted, state },
});

describe('postingPeriod (D-144)', () => {
  beforeEach(() => vi.clearAllMocks());

  it('asks GET /v1/pay-inputs/posting/{period} and returns the bare reply', async () => {
    apiClient.get.mockResolvedValueOnce(reply('2026-10', '2026-10', 'OPEN'));
    await expect(postingService.get('2026-10')).resolves.toEqual({
      requested_period: '2026-10',
      posted_period: '2026-10',
      state: 'OPEN',
    });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/pay-inputs/posting/2026-10');
  });

  it('names a month in full', () => {
    expect(monthName('2026-10')).toBe('October 2026');
  });

  it('words the three states', () => {
    expect(postingNote({ requested_period: '2026-10', posted_period: '2026-10', state: 'OPEN' })).toEqual({
      tone: 'info',
      text: 'Picked up by the October 2026 run',
    });
    expect(postingNote({ requested_period: '2026-10', posted_period: '2026-10', state: 'LOCKED' })).toEqual({
      tone: 'warning',
      text: 'Picked up by the October 2026 run, which must be recomputed',
    });
    expect(postingNote({ requested_period: '2026-10', posted_period: '2026-11', state: 'CLOSED' })).toEqual({
      tone: 'error',
      text: 'October 2026 is closed. Enter this for November 2026',
    });
    expect(postingNote(null)).toBeNull();
    expect(postingNote({ state: 'SOMETHING' })).toBeNull();
  });

  it('only CLOSED refuses saving', () => {
    expect(isClosed({ state: POSTING_STATE.CLOSED })).toBe(true);
    expect(isClosed({ state: POSTING_STATE.LOCKED })).toBe(false);
    expect(isClosed({ state: POSTING_STATE.OPEN })).toBe(false);
    expect(isClosed(null)).toBe(false);
  });

  it('usePostingPeriods asks once per distinct month and ignores blanks', async () => {
    apiClient.get.mockImplementation((url) => {
      const p = url.split('/').pop();
      return Promise.resolve(reply(p, p, 'OPEN'));
    });
    const { result } = renderHook(() =>
      usePostingPeriods(['2026-10', null, '2026-10', '2026-11', ''], { debounceMs: 0 })
    );

    await waitFor(() => expect(Object.keys(result.current).sort()).toEqual(['2026-10', '2026-11']));
    expect(apiClient.get).toHaveBeenCalledTimes(2);
  });

  it('usePostingPeriods remembers an answer: going back to a month does not ask again', async () => {
    apiClient.get.mockImplementation((url) => {
      const p = url.split('/').pop();
      return Promise.resolve(reply(p, p, 'LOCKED'));
    });
    const { result, rerender } = renderHook(({ periods }) => usePostingPeriods(periods, { debounceMs: 0 }), {
      initialProps: { periods: ['2026-10'] },
    });
    await waitFor(() => expect(result.current['2026-10']?.state).toBe('LOCKED'));

    rerender({ periods: ['2026-11'] });
    await waitFor(() => expect(result.current['2026-11']?.state).toBe('LOCKED'));
    rerender({ periods: ['2026-10'] });
    await waitFor(() => expect(result.current['2026-10']?.state).toBe('LOCKED'));

    expect(apiClient.get).toHaveBeenCalledTimes(2);
  });

  it('usePostingPeriods waits for the months to settle before asking', async () => {
    apiClient.get.mockImplementation((url) => {
      const p = url.split('/').pop();
      return Promise.resolve(reply(p, p, 'OPEN'));
    });
    const { rerender } = renderHook(({ periods }) => usePostingPeriods(periods, { debounceMs: 50 }), {
      initialProps: { periods: ['2026-01'] },
    });
    rerender({ periods: ['2026-02'] });
    rerender({ periods: ['2026-03'] });

    await waitFor(() => expect(apiClient.get).toHaveBeenCalledTimes(1));
    expect(apiClient.get).toHaveBeenCalledWith('/v1/pay-inputs/posting/2026-03');
  });

  it('usePostingPeriod leaves a failed lookup out, so the screen says nothing', async () => {
    apiClient.get.mockRejectedValue({ status: 403 });
    const { result } = renderHook(() => usePostingPeriod('2026-10', { debounceMs: 0 }));

    await waitFor(() => expect(apiClient.get).toHaveBeenCalled());
    expect(result.current).toBeNull();
  });

  it('PostingNote renders the line with its state, and nothing without an answer', () => {
    const { rerender } = render(
      <PostingNote
        posting={{ requested_period: '2026-10', posted_period: '2026-11', state: 'CLOSED' }}
        testId="note"
      />
    );
    const note = screen.getByTestId('note');
    expect(note.textContent).toBe('October 2026 is closed. Enter this for November 2026');
    expect(note.getAttribute('data-state')).toBe('CLOSED');
    expect(note.getAttribute('role')).toBe('alert');

    rerender(<PostingNote posting={null} testId="note" />);
    expect(screen.queryByTestId('note')).toBeNull();
  });
});
