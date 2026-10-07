import { apiClient } from '@shared/api/client.js';

/**
 * The settings responses are plain camelCase records (`EpfSettingResponse`, `EsiSettingResponse`),
 * while every settings screen reads snake_case keys and the requests accept both spellings
 * (`@JsonAlias`). Returning both spellings here lets the screens load what the server sends
 * without each screen knowing the server's casing.
 */
export function withSnakeCaseKeys(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return data;
  const out = { ...data };
  for (const [key, value] of Object.entries(data)) {
    const snake = key.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase();
    if (snake !== key && !(snake in out)) out[snake] = value;
  }
  return out;
}

export const statutoryService = {
  async get(kind) {
    const res = await apiClient.get(`/v1/payroll/settings/${kind}`);
    return withSnakeCaseKeys(res.data?.data ?? res.data);
  },

  async save(kind, payload) {
    const res = await apiClient.put(`/v1/payroll/settings/${kind}`, payload);
    return withSnakeCaseKeys(res.data?.data ?? res.data);
  },
};
