import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyRegularizations } from './MyRegularizations.jsx';
import { requestService } from './requestService.js';

configure({ asyncUtilTimeout: 20000 });

vi.mock('./requestService.js', () => ({
  requestService: { myRegularizations: vi.fn(), submitRegularization: vi.fn() },
}));
vi.mock('@shell/screens', () => ({ useCan: vi.fn(() => true) }));

const T = 60000;
const rows = [
  {
    id: 'r1',
    date: '2026-09-29',
    inAt: '2026-09-29T04:00:00Z',
    outAt: '2026-09-29T12:00:00Z',
    reason: 'Forgot to clock in',
    status: 'PENDING',
    createdAt: '2026-09-30T00:00:00Z',
  },
];

function pickRange(from, to) {
  const inputs = document.querySelectorAll('.ant-picker-range input');
  fireEvent.mouseDown(inputs[0]);
  fireEvent.focus(inputs[0]);
  fireEvent.change(inputs[0], { target: { value: from } });
  fireEvent.keyDown(inputs[0], { key: 'Enter', code: 'Enter' });
  fireEvent.focus(inputs[1]);
  fireEvent.change(inputs[1], { target: { value: to } });
  fireEvent.keyDown(inputs[1], { key: 'Enter', code: 'Enter' });
}

const renderIt = () =>
  render(
    <MemoryRouter>
      <MyRegularizations />
    </MemoryRouter>
  );

async function openForm() {
  fireEvent.click(screen.getByRole('button', { name: /new request/i }));
  await screen.findByLabelText('Reason');
}

function fill({ date, inTime, outTime, reason }) {
  fireEvent.change(screen.getByLabelText('Date'), { target: { value: date } });
  fireEvent.change(screen.getByLabelText('In'), { target: { value: inTime } });
  fireEvent.change(screen.getByLabelText('Out'), { target: { value: outTime } });
  fireEvent.change(screen.getByLabelText('Reason'), { target: { value: reason } });
}

describe('MyRegularizations (W-48.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    requestService.myRegularizations.mockResolvedValue(rows);
  });

  it(
    'requests nothing until a range is picked',
    async () => {
      renderIt();
      expect(await screen.findByText(/pick a range to load requests/i)).toBeTruthy();
      expect(requestService.myRegularizations).not.toHaveBeenCalled();
      pickRange('2026-09-01', '2026-09-30');
      expect(await screen.findByText('Forgot to clock in')).toBeTruthy();
      expect(requestService.myRegularizations).toHaveBeenCalledWith('2026-09-01', '2026-09-30');
    },
    T
  );

  it(
    'the form blocks out before in',
    async () => {
      renderIt();
      await openForm();
      fill({ date: '2026-09-29', inTime: '18:00', outTime: '09:00', reason: 'late' });
      fireEvent.click(screen.getByRole('button', { name: /submit/i }));
      expect(await screen.findByText('Out must be after in')).toBeTruthy();
      expect(requestService.submitRegularization).not.toHaveBeenCalled();
    },
    T
  );

  it(
    'a 409 shows the server message',
    async () => {
      requestService.submitRegularization.mockRejectedValueOnce({
        status: 409,
        message: 'A request for this date is already pending',
      });
      renderIt();
      await openForm();
      fill({ date: '2026-09-29', inTime: '09:00', outTime: '18:00', reason: 'Forgot' });
      fireEvent.click(screen.getByRole('button', { name: /submit/i }));
      expect(await screen.findByText('A request for this date is already pending')).toBeTruthy();
      await waitFor(() => expect(requestService.submitRegularization).toHaveBeenCalledTimes(1));
      const body = requestService.submitRegularization.mock.calls[0][0];
      expect(body.date).toBe('2026-09-29');
      expect(body.reason).toBe('Forgot');
      expect(body.inAt).toMatch(/^2026-09-29T09:00:00[+-]\d{2}:\d{2}$/);
      expect(body.outAt).toMatch(/^2026-09-29T18:00:00[+-]\d{2}:\d{2}$/);
    },
    T
  );
});
