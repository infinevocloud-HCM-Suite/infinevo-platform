import { apiClient } from '../../shared/api/client.js';

function toMap(items = []) {
  return items.reduce((acc, item) => {
    acc[item.id] = item.name;
    return acc;
  }, {});
}

export const orgMasterService = {
  /**
   * Fetches active departments, designations, and work locations concurrently.
   * Returns id -> name lookup maps as well as raw lists for dropdown selections.
   */
  async all() {
    const [depRes, desigRes, locRes] = await Promise.all([
      apiClient.get('/v1/departments', { params: { activeOnly: true } }),
      apiClient.get('/v1/designations', { params: { activeOnly: true } }),
      apiClient.get('/v1/work-locations', { params: { activeOnly: true } }),
    ]);

    const departmentsList = depRes.data || [];
    const designationsList = desigRes.data || [];
    const workLocationsList = locRes.data || [];

    return {
      departments: toMap(departmentsList),
      designations: toMap(designationsList),
      workLocations: toMap(workLocationsList),
      raw: {
        departments: departmentsList,
        designations: designationsList,
        workLocations: workLocationsList,
      },
    };
  },
};
