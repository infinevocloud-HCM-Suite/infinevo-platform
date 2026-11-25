import { apiClient } from '@shared/api/client.js';

/**
 * The signed-in tenant's own profile (W-73.1 §4, W-116): name, tagline, logo, locale.
 *
 *   GET /api/v1/tenants/current/profile -> { name, tagline, logoDocumentId, logoUrl, leaveYearStartMonth, countryCode, timezone, countryLocked, leaveYearLocked }
 *   PUT /api/v1/tenants/current/profile    { tagline, logoDocumentId, countryCode, timezone, leaveYearStartMonth } -> the same
 *   GET /api/v1/reference/countries     -> [{ code, name }]
 */
const PATH = '/v1/tenants/current/profile';

export const companyProfileService = {
  async get() {
    const res = await apiClient.get(PATH);
    return res?.data !== undefined ? res.data : res;
  },
  async update(body) {
    const res = await apiClient.put(PATH, body);
    return res?.data !== undefined ? res.data : res;
  },
  async countries() {
    const res = await apiClient.get('/v1/reference/countries');
    return res?.data !== undefined ? res.data : res;
  },
};
