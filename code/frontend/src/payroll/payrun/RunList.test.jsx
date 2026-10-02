import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RunList } from './RunList.jsx';
import { payrunService } from './payrunService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./payrunService.js', () => ({
  payrunService: {
    list: vi.fn(),
    create: vi.fn(),
  },
}));

describe('RunList component (W-47.2 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
    payrunService.list.mockResolvedValue({
      content: [
        {
          id: 'run-1',
          period: '2026-10',
          pay_date: '2026-10-31',
          run_type: 'REGULAR',
          status: 'COMPUTED',
          included_count: 50,
          skipped_count: 2,
          total_net_pay: 1500000.0,
        },
      ],
      totalElements: 1,
    });
  });

  it('renders runs table and filters send status and runType', async () => {
    render(
      <MemoryRouter>
        <RunList />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('2026-10')).toBeDefined();
      expect(screen.getByText('COMPUTED')).toBeDefined();
    });

    // Initial list call
    expect(payrunService.list).toHaveBeenCalledWith({
      status: undefined,
      runType: undefined,
      page: 0,
      size: 20,
    });

    // Select Off-Cycle in Segmented
    const offCycleRadio = screen.getByRole('radio', { name: 'Off-Cycle' });
    fireEvent.click(offCycleRadio);

    await waitFor(() => {
      expect(payrunService.list).toHaveBeenCalledWith({
        status: undefined,
        runType: 'OFF_CYCLE',
        page: 0,
        size: 20,
      });
    });
  });

  it('"New run" modal opens, picks period and posts {period}', async () => {
    payrunService.create.mockResolvedValueOnce({
      id: 'run-created',
      period: '2026-11',
    });

    render(
      <MemoryRouter>
        <RunList />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /new run/i })).toBeDefined();
    });

    fireEvent.click(screen.getByRole('button', { name: /new run/i }));

    expect(screen.getByText('Create Regular Pay Run')).toBeDefined();

    // Find datepicker and select month
    const input = screen.getByPlaceholderText('Choose Year and Month');
    fireEvent.change(input, { target: { value: '2026-11' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    const okBtn = screen.getByRole('button', { name: /ok/i });
    fireEvent.click(okBtn);

    await waitFor(() => {
      expect(payrunService.create).toHaveBeenCalledWith({ period: '2026-11' });
    });
  });

  it('renders verbatim error message with pay-schedule link on 409 Conflict', async () => {
    payrunService.create.mockRejectedValueOnce({
      status: 409,
      code: 'CONFLICT',
      message: 'No pay schedule exists for this tenant; cannot derive dates',
    });

    render(
      <MemoryRouter>
        <RunList />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /new run/i })).toBeDefined();
    });

    fireEvent.click(screen.getByRole('button', { name: /new run/i }));

    const input = screen.getByPlaceholderText('Choose Year and Month');
    fireEvent.change(input, { target: { value: '2026-12' } });
    fireEvent.keyDown(input, { key: 'Enter' });

    const okBtn = screen.getByRole('button', { name: /ok/i });
    fireEvent.click(okBtn);

    await waitFor(() => {
      // Verbatim message rendered
      expect(
        screen.getByText('No pay schedule exists for this tenant; cannot derive dates')
      ).toBeDefined();
      // Link to /payroll/settings/pay-schedule
      const link = screen.getByRole('link', { name: /configure pay schedule settings/i });
      expect(link).toBeDefined();
      expect(link.getAttribute('href')).toBe('/payroll/settings/pay-schedule');
    });
  });
});
