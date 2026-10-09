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
 *
 * Live mode (D-88): `--live <keycloak-base-url> --app <app-root-url>` also asks the deployed realm's
 * sign-in page for its "Forgot password?" link. Keycloak imports the realm file only once, so a realm
 * that existed before a setting was added never picks it up from the file; the page is the proof.
 * No secret is needed: the sign-in page is public. `infra/azure/verify-live.sh` runs this.
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

// 2b. Password policy and brute-force lockout (D-36). Every rule the tracker names must be present.
const REQUIRED_POLICY = ['length(10)', 'upperCase(1)', 'lowerCase(1)', 'digits(1)', 'specialChars(1)', 'notUsername', 'notEmail', 'passwordHistory(5)'];
const policy = typeof realm.passwordPolicy === 'string' ? realm.passwordPolicy : '';
for (const rule of REQUIRED_POLICY) {
  if (!policy.split(' and ').map((r) => r.trim()).includes(rule)) {
    errors.push(`'passwordPolicy' must include '${rule}', got '${policy}'.`);
  }
}
if (realm.bruteForceProtected !== true) {
  errors.push(`'bruteForceProtected' must be true, got '${realm.bruteForceProtected}'.`);
}
if (!(Number.isInteger(realm.failureFactor) && realm.failureFactor >= 3 && realm.failureFactor <= 10)) {
  errors.push(`'failureFactor' must be an integer between 3 and 10, got '${realm.failureFactor}'.`);
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

// 5. Live realm (D-88): the deployed sign-in page must offer "Forgot password?". Without it, an invitee
// who loses their password has no way back: nothing in the app resets a password.
const args = process.argv.slice(2);
const argValue = (name) => {
  const i = args.indexOf(name);
  return i >= 0 && i + 1 < args.length ? args[i + 1] : null;
};
const liveBase = argValue('--live');
if (args.includes('--live') && !liveBase) {
  errors.push("'--live' needs the Keycloak base URL after it, e.g. --live https://host/auth --app https://host/");
}
if (liveBase) {
  const appRoot = argValue('--app') ?? '';
  const base = liveBase.replace(/\/$/, '');
  const query = new URLSearchParams({
    client_id: 'infinevo-web',
    response_type: 'code',
    scope: 'openid',
    redirect_uri: appRoot,
  });
  const loginUrl = `${base}/realms/${realm.realm}/protocol/openid-connect/auth?${query}`;
  try {
    const response = await fetch(loginUrl, { redirect: 'follow' });
    const html = await response.text();
    if (response.status !== 200) {
      errors.push(`Live sign-in page ${loginUrl} answered HTTP ${response.status}; expected 200.`);
    } else if (!/login-actions\/reset-credentials/.test(html)) {
      errors.push(
        `Live realm has no "Forgot password?" link on its sign-in page (${base}): set resetPasswordAllowed=true on the deployed realm (infra/keycloak/README.md §2).`,
      );
    } else {
      console.log(`PASS: live realm at ${base} offers "Forgot password?" on its sign-in page`);
    }
  } catch (err) {
    errors.push(`Live sign-in page ${loginUrl} could not be fetched: ${err.message}`);
  }
}

if (errors.length > 0) {
  console.error(`::error::check-realm.mjs failed with ${errors.length} error(s):`);
  for (const err of errors) {
    console.error(`  - ${err}`);
  }
  // Not process.exit(): after a fetch, Node on Windows aborts in libuv teardown (UV_HANDLE_CLOSING) and the
  // exit code is lost. Setting exitCode lets the loop drain and still fails the run.
  process.exitCode = 1;
} else {
  console.log('OK - infra/keycloak/infinevo-realm.json passed all security and configuration checks.');
}
