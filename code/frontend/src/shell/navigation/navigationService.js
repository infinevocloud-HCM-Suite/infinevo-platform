import { apiClient } from '../../shared/api/client.js';

/**
 * Navigation API service (W-45 §5).
 * Retrieves the server-authorized navigation feed and user action codes.
 *
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function fetchNavigation() {
  return apiClient.get('/v1/navigation');
}
