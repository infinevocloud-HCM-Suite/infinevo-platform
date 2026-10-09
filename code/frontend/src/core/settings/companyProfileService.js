import { apiClient } from '@shared/api/client.js';

/**
 * The signed-in tenant's own profile (W-73.1 §4): name, tagline, logo.
 *
 *   GET /api/v1/tenants/current/profile -> { name, tagline, logoDocumentId, logoUrl }
 *   PUT /api/v1/tenants/current/profile    { tagline, logoDocumentId } -> the same
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
};
