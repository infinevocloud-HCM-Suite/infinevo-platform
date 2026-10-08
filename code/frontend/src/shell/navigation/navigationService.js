import { apiClient } from '../../shared/api/client.js';

/**
 * Navigation API service (W-45 §5).
 * Retrieves the server-authorized navigation feed and user action codes.
 *
 * The reply: `{ items, actions, modules, tenantName, homePath }`. `homePath` (D-35) is the caller's
 * landing page after login, chosen by the server from their tenant and actions.
 *
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export function fetchNavigation() {
  return apiClient.get('/v1/navigation');
}
