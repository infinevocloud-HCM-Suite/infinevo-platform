import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { OvertimeDetail } from './OvertimeDetail.jsx';
import { requestService } from './requestService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./requestService.js', () => ({
  requestService: { overtime: vi.fn() },
}));
vi.mock('@shell/screens', () => ({ NotFound: () => <div>Page not found</div> }));

const T = 60000;
const item = {
  id: 'o1',
  overtimeDate: '2026-10-01',
  hours: '2.50',
  amount: '1250.00',
  status: 'APPROVED',
  source: 'REQUEST',
  postedPeriod: '2026-10',
  remarks: 'release',
};

const renderAt = (state) =>
  render(
    <MemoryRouter initialEntries={[{ pathname: '/hrms/overtime-requests/o1', state }]}>
      <Routes>
        <Route path="/hrms/overtime-requests/:id" element={<OvertimeDetail />} />
      </Routes>
    </MemoryRouter>
  );

describe('OvertimeDetail (W-48.5 §7)', () => {
  beforeEach(() => vi.clearAllMocks());

  it(
    'renders hours, amount and status as sent, with a back link to the list',
    async () => {
      requestService.overtime.mockResolvedValueOnce(item);
      renderAt();
      expect(await screen.findByText('2.50')).toBeTruthy();
      expect(screen.getByText('1250.00')).toBeTruthy();
      expect(screen.getByText('APPROVED')).toBeTruthy();
      expect(screen.getByText('2026-10')).toBeTruthy();
      expect(requestService.overtime).toHaveBeenCalledWith('o1');
      expect(screen.getByRole('link', { name: 'Back to list' }).getAttribute('href')).toBe('/hrms/overtime-requests');
    },
    T
  );

  it(
    'links back to approvals when opened from the inbox',
    async () => {
      requestService.overtime.mockResolvedValueOnce(item);
      renderAt({ from: '/approvals' });
      const link = await screen.findByRole('link', { name: 'Back to approvals' });
      expect(link.getAttribute('href')).toBe('/approvals');
    },
    T
  );

  it(
    'a 404 shows NotFound',
    async () => {
      requestService.overtime.mockRejectedValueOnce({ status: 404, message: 'Not found' });
      renderAt();
      expect(await screen.findByText('Page not found')).toBeTruthy();
    },
    T
  );
});
