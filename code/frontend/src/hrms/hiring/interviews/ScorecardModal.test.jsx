import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ScorecardModal } from './ScorecardModal.jsx';
import { hiringService } from '../hiringService.js';

vi.mock('../hiringService.js', () => ({
  hiringService: {
    interviews: {
      submitFeedback: vi.fn(),
    },
  },
}));

describe('ScorecardModal component (W-112)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders rating, recommendation options, and text areas', () => {
    render(
      <ScorecardModal
        open={true}
        interviewId="intvw-1"
        onClose={vi.fn()}
        onSuccess={vi.fn()}
      />
    );

    expect(screen.getByText('Submit Interview Scorecard')).toBeTruthy();
    expect(screen.getByText('Overall Candidate Rating')).toBeTruthy();
    expect(screen.getByText('Hiring Recommendation')).toBeTruthy();
    expect(screen.getByText('Strong Yes')).toBeTruthy();
    expect(screen.getByText('Key Strengths')).toBeTruthy();
  });

  it('submits scorecard when required fields are filled', async () => {
    hiringService.interviews.submitFeedback.mockResolvedValue({ id: 'fb-1' });
    const handleSuccess = vi.fn();
    const handleClose = vi.fn();

    render(
      <ScorecardModal
        open={true}
        interviewId="intvw-1"
        onClose={handleClose}
        onSuccess={handleSuccess}
      />
    );

    // Select Strong Yes radio
    fireEvent.click(screen.getByText('Strong Yes'));

    // Submit
    const submitBtn = screen.getByRole('button', { name: 'Submit Scorecard' });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(hiringService.interviews.submitFeedback).toHaveBeenCalledWith(
        'intvw-1',
        expect.objectContaining({
          recommendation: 'STRONG_YES',
        })
      );
    });
  });
});

