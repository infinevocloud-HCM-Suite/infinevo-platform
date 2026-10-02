#!/usr/bin/env node
/**
 * check-realm.mjs — Static validation for production Keycloak realm export (W-10.1).
 *
 * Enforces security constraints on infra/keycloak/infinevo-realm.json:
 * - No `users` key (production realm never seeds user accounts)
 * - No `credentials` or `secret` key anywhere in the tree
 * - No `localhost` string anywhere in the file
 * - `sslRequired` must be "external"
 * - `resetPasswordAllowed` must be true
 */

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const realmPath = join(__dirname, 'infinevo-realm.json');

let rawContent;
try {
  rawContent = readFileSync(realmPath, 'utf8');
} catch (err) {
  console.error(`::error::Failed to read realm file at ${realmPath}: ${err.message}`);
  process.exit(1);
}

const errors = [];

// 1. Raw string checks
if (/localhost/i.test(rawContent)) {
  errors.push("Forbidden string 'localhost' found in realm file.");
}

let realm;
try {
  realm = JSON.parse(rawContent);
} catch (err) {
  console.error(`::error::Invalid JSON in ${realmPath}: ${err.message}`);
  process.exit(1);
}

// 2. Root properties
if ('users' in realm) {
  errors.push("Forbidden key 'users' found in realm definition. Production realm must contain no user accounts.");
}

if (realm.sslRequired !== 'external') {
  errors.push(`'sslRequired' must be 'external', got '${realm.sslRequired}'.`);
}

if (realm.resetPasswordAllowed !== true) {
  errors.push(`'resetPasswordAllowed' must be true, got '${realm.resetPasswordAllowed}'.`);
}

if (realm.realm !== 'infinevo') {
  errors.push(`'realm' must be 'infinevo', got '${realm.realm}'.`);
}

// 3. Recursive check for 'credentials' and 'secret' keys anywhere in the JSON structure
function scanObject(obj, path = '') {
  if (!obj || typeof obj !== 'object') return;
  for (const [key, value] of Object.entries(obj)) {
    const currentPath = path ? `${path}.${key}` : key;
    if (key.toLowerCase() === 'credentials') {
      errors.push(`Forbidden key 'credentials' found at ${currentPath}.`);
    }
    if (key.toLowerCase() === 'secret') {
      errors.push(`Forbidden key 'secret' found at ${currentPath}.`);
    }
    if (typeof value === 'object' && value !== null) {
      scanObject(value, currentPath);
    }
  }
}

scanObject(realm);

// 3b. Every smtpServer credential and address is an env placeholder, never a literal:
// the SMTP password is the secret this realm carries (from Key Vault at run time).
for (const field of ['host', 'port', 'from', 'user', 'password']) {
  const value = realm.smtpServer?.[field];
  if (typeof value !== 'string' || !/^\$\{[A-Z0-9_]+\}$/.test(value)) {
    errors.push(`'smtpServer.${field}' must be a \${ENV_VAR} placeholder, got '${field === 'password' ? '<redacted>' : value}'.`);
  }
}

// 4. Client check
const webClient = realm.clients?.find(c => c.clientId === 'infinevo-web');
if (!webClient) {
  errors.push("Missing required client 'infinevo-web'.");
} else {
  if (webClient.publicClient !== true) {
    errors.push("'infinevo-web' client must be publicClient: true.");
  }
}

if (errors.length > 0) {
  console.error(`::error::check-realm.mjs failed with ${errors.length} error(s):`);
  for (const err of errors) {
    console.error(`  - ${err}`);
  }
  process.exit(1);
}

console.log('OK - infra/keycloak/infinevo-realm.json passed all security and configuration checks.');
process.exit(0);
