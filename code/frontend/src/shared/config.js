/**
 * Unified runtime configuration (W-45 §5).
 *
 * This is the ONLY file in src/ allowed to read window.__ENV or import.meta.env.
 * Resolution precedence:
 *   1. window.__ENV.<KEY>       (injected at container startup)
 *   2. import.meta.env.VITE_<KEY> (compiled Vite fallback)
 *   3. Default value in code
 */

export function resolveConfig(win = typeof window !== 'undefined' ? window.__ENV : {}, meta = typeof import.meta !== 'undefined' ? import.meta.env : {}) {
  const winEnv = win || {};
  const metaEnv = meta || {};

  return {
    apiBaseUrl: winEnv.API_BASE_URL || metaEnv.VITE_API_BASE_URL || '/api',
    keycloak: {
      url: winEnv.KEYCLOAK_URL || metaEnv.VITE_KEYCLOAK_URL || 'http://localhost:8081',
      realm: winEnv.KEYCLOAK_REALM || metaEnv.VITE_KEYCLOAK_REALM || 'infinevo',
      clientId: winEnv.KEYCLOAK_CLIENT_ID || metaEnv.VITE_KEYCLOAK_CLIENT_ID || 'infinevo-web',
    },
  };
}

export const config = resolveConfig();
