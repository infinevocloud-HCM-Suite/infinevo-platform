import { createService } from '@shared/api/createService.js';
import { apiClient } from '@shared/api/client.js';

const baseService = createService('/v1/holiday-calendars');

export const holidayCalendarService = {
  ...baseService,
  async addHoliday(id, body) {
    const res = await apiClient.post(`${baseService.basePath}/${id}/holidays`, body);
    return res?.data !== undefined ? res.data : res;
  },
  async removeHoliday(id, holidayId) {
    const res = await apiClient.delete(`${baseService.basePath}/${id}/holidays/${holidayId}`);
    return res?.data !== undefined ? res.data : res;
  },
};
