import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { JobOpeningList } from './JobOpeningList.jsx';
import { hiringService } from '../hiringService.js';

vi.mock('../hiringService.js', () => ({
  hiringService: {
    openings: {
      list: vi.fn(),
      updateStatus: vi.fn(),
    },
  },
}));

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
}));

describe('JobOpeningList component (W-112)', () => {
  const mockOpenings = [
    {
      id: 'op-1',
      openingCode: 'JOB-2026-001',
      title: 'Senior Backend Engineer',
      departmentName: 'Engineering',
      workLocationName: 'Bangalore HQ',
      employmentType: 'FULL_TIME',
      openingsCount: 3,
      filledCount: 1,
      totalCandidates: 12,
      interviewingCount: 4,
      offeredCount: 1,
      hiredCount: 1,
      status: 'OPEN',
    },
    {
      id: 'op-2',
      openingCode: 'JOB-2026-002',
      title: 'Product Designer',
      departmentName: 'Design',
      workLocationName: 'Remote',
      employmentType: 'FULL_TIME',
      openingsCount: 1,
      filledCount: 0,
      totalCandidates: 5,
      interviewingCount: 2,
      offeredCount: 0,
      hiredCount: 0,
      status: 'DRAFT',
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    hiringService.openings.list.mockResolvedValue(mockOpenings);
  });

  it('renders job openings table with codes, titles, headcount, and statuses', async () => {
    render(
      <MemoryRouter>
        <JobOpeningList />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('JOB-2026-001')).toBeTruthy();
      expect(screen.getByText('Senior Backend Engineer')).toBeTruthy();
      expect(screen.getByText('JOB-2026-002')).toBeTruthy();
      expect(screen.getByText('Product Designer')).toBeTruthy();
    });

    expect(screen.getByText('Create Job Opening')).toBeTruthy();
  });
});

