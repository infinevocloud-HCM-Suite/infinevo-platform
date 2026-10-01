import { describe, it, expect } from 'vitest';
import { resolveConfig } from './config.js';

describe('resolveConfig', () => {
  it('resolves defaults when neither window.__ENV nor import.meta.env has values', () => {
    const config = resolveConfig({}, {});
    expect(config.apiBaseUrl).toBe('/api');
    expect(config.keycloak.url).toBe('http://localhost:8081');
    expect(config.keycloak.realm).toBe('infinevo');
    expect(config.keycloak.clientId).toBe('infinevo-web');
  });

  it('prefers import.meta.env values over defaults when window.__ENV is empty', () => {
    const meta = {
      VITE_API_BASE_URL: 'https://api.infinevocloud.com',
      VITE_KEYCLOAK_URL: 'https://auth.infinevocloud.com',
      VITE_KEYCLOAK_REALM: 'prod-realm',
      VITE_KEYCLOAK_CLIENT_ID: 'prod-web',
    };
    const config = resolveConfig({}, meta);
    expect(config.apiBaseUrl).toBe('https://api.infinevocloud.com');
    expect(config.keycloak.url).toBe('https://auth.infinevocloud.com');
    expect(config.keycloak.realm).toBe('prod-realm');
    expect(config.keycloak.clientId).toBe('prod-web');
  });

  it('prefers window.__ENV over import.meta.env per key', () => {
    const win = {
      API_BASE_URL: 'https://runtime.api.com',
      KEYCLOAK_URL: 'https://runtime.auth.com',
    };
    const meta = {
      VITE_API_BASE_URL: 'https://buildtime.api.com',
      VITE_KEYCLOAK_URL: 'https://buildtime.auth.com',
      VITE_KEYCLOAK_REALM: 'buildtime-realm',
    };
    const config = resolveConfig(win, meta);
    // Overridden by window.__ENV
    expect(config.apiBaseUrl).toBe('https://runtime.api.com');
    expect(config.keycloak.url).toBe('https://runtime.auth.com');
    // Falls back to import.meta.env
    expect(config.keycloak.realm).toBe('buildtime-realm');
    // Falls back to default
    expect(config.keycloak.clientId).toBe('infinevo-web');
  });
});
