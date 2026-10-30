/**
 * Display names for the statutory component codes the server sends
 * (`StatutoryComponentCode`, plus `PT` on a preview) (W-75.2 §5).
 */
const NAMES = {
  EPF_EMPLOYEE: 'Employee PF',
  EPF_EMPLOYER: 'Employer PF',
  EPS_EMPLOYER: 'Employer pension (EPS)',
  EDLI: 'EDLI',
  EPF_ADMIN: 'PF admin charges',
  ESI_EMPLOYEE: 'Employee ESI',
  ESI_EMPLOYER: 'Employer ESI',
  PT: 'Professional tax',
};

/** The name for a statutory code; an unknown code is read as words. */
export function statutoryName(code) {
  if (!code) return '';
  const known = NAMES[String(code).toUpperCase()];
  if (known) return known;
  const words = String(code).replace(/_/g, ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}
