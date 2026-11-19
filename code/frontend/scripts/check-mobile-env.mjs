#!/usr/bin/env node

const apiUrl = process.env.VITE_API_BASE_URL;
const keycloakUrl = process.env.VITE_KEYCLOAK_URL;

if (!apiUrl || !apiUrl.startsWith('https://')) {
  console.error('Error: VITE_API_BASE_URL must be set and must be an absolute https:// address');
  process.exit(1);
}

if (!keycloakUrl || !keycloakUrl.startsWith('https://')) {
  console.error('Error: VITE_KEYCLOAK_URL must be set and must be an absolute https:// address');
  process.exit(1);
}

