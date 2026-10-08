/**
 * English text for the role codes `GET /api/v1/me` returns (W-73.1 §5).
 *
 * The seeded codes are `core.seed_system_roles`'s (V158); `roleLabels.test.js` fails the build
 * when the seed names a code that is missing here. An unknown code still reads as words, never as
 * a raw code - the same rule as `navLabels.js`.
 */
export const ROLE_LABELS = {
  'platform-admin': 'Platform admin',
  'tenant-admin': 'Tenant admin',
  hr: 'HR',
  manager: 'Manager',
  'payroll-officer': 'Payroll officer',
  finance: 'Finance',
  employee: 'Employee',
};

/** The words for a role code: `payroll-officer` → "Payroll officer", `shift_lead` → "Shift lead". */
export function roleLabel(code) {
  if (!code) return '';
  const known = ROLE_LABELS[code];
  if (known) return known;
  const words = String(code)
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_.-]+/g, ' ')
    .trim()
    .toLowerCase();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : String(code);
}
