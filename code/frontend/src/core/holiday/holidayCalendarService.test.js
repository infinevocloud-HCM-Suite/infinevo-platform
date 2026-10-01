import { describe, it, expect, vi, beforeEach } from 'vitest';
import { holidayCalendarService } from './holidayCalendarService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('holidayCalendarService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('calls list on /v1/holiday-calendars', async () => {
    apiClient.get.mockResolvedValueOnce({ data: [{ id: 'cal-1', name: 'Bengaluru 2026' }] });
    const res = await holidayCalendarService.list();
    expect(apiClient.get).toHaveBeenCalledWith('/v1/holiday-calendars', { params: undefined });
    expect(res).toEqual([{ id: 'cal-1', name: 'Bengaluru 2026' }]);
  });

  it('calls get by id', async () => {
    apiClient.get.mockResolvedValueOnce({ data: { id: 'cal-1', name: 'Bengaluru 2026' } });
    const res = await holidayCalendarService.get('cal-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/holiday-calendars/cal-1');
    expect(res).toEqual({ id: 'cal-1', name: 'Bengaluru 2026' });
  });

  it('calls create with payload', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'cal-1' } });
    const payload = { name: 'Bengaluru 2026', isDefault: true, workLocationIds: ['loc-1'] };
    const res = await holidayCalendarService.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/holiday-calendars', payload);
    expect(res).toEqual({ id: 'cal-1' });
  });

  it('calls update with id and payload', async () => {
    apiClient.put.mockResolvedValueOnce({ data: { id: 'cal-1' } });
    const payload = { name: 'Updated Bengaluru', isDefault: false, workLocationIds: ['loc-2'] };
    const res = await holidayCalendarService.update('cal-1', payload);
    expect(apiClient.put).toHaveBeenCalledWith('/v1/holiday-calendars/cal-1', payload);
    expect(res).toEqual({ id: 'cal-1' });
  });

  it('calls addHoliday on /{id}/holidays', async () => {
    apiClient.post.mockResolvedValueOnce({ data: { id: 'hol-1', name: 'Diwali' } });
    const payload = {
      name: 'Diwali',
      from: '2026-11-08',
      to: '2026-11-08',
      isRestricted: false,
      description: 'Festival of Lights',
    };
    const res = await holidayCalendarService.addHoliday('cal-1', payload);
    expect(apiClient.post).toHaveBeenCalledWith('/v1/holiday-calendars/cal-1/holidays', payload);
    expect(res).toEqual({ id: 'hol-1', name: 'Diwali' });
  });

  it('calls removeHoliday on /{id}/holidays/{holidayId}', async () => {
    apiClient.delete.mockResolvedValueOnce({ data: null });
    await holidayCalendarService.removeHoliday('cal-1', 'hol-1');
    expect(apiClient.delete).toHaveBeenCalledWith('/v1/holiday-calendars/cal-1/holidays/hol-1');
  });
});
