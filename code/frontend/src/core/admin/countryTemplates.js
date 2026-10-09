/**
 * Country templates (W-73.9): what a new tenant of a country starts with.
 *
 * Section names are `reference.country_template.section` on the server
 * (`TenantTemplateService.SECTION_ORDER`), listed here in the order the server applies them.
 */
export const SECTION_LABELS = {
  holidays: 'holidays',
  leave_types: 'leave types',
  pay_schedule: 'pay schedule',
  salary_components: 'salary components',
  statutory: 'EPF and ESI',
};

const ORDER = Object.keys(SECTION_LABELS);

/** Section names as words, in the server's order: `['statutory', 'holidays']` -> `'holidays, EPF and ESI'`. */
export function sectionList(sections) {
  return [...(sections || [])]
    .sort((a, b) => {
      const ia = ORDER.indexOf(a);
      const ib = ORDER.indexOf(b);
      return (ia < 0 ? ORDER.length : ia) - (ib < 0 ? ORDER.length : ib);
    })
    .map((s) => SECTION_LABELS[s] || s.replace(/_/g, ' '))
    .join(', ');
}

/**
 * The line under Create Tenant's Country field. `templates` is the reply of
 * `GET /v1/reference/country-templates` (`[{ countryCode, sections, version }]`), or null while it
 * has not loaded or could not be read - then there is no line at all.
 */
export function startsWithLine(templates, countryCode) {
  if (!Array.isArray(templates)) return undefined;
  const code = (countryCode || '').trim().toUpperCase();
  if (code.length !== 2) return undefined;
  const template = templates.find((t) => t.countryCode === code);
  return template
    ? `Starts with: ${sectionList(template.sections)}`
    : 'No template — the admin sets everything up';
}

/** What `POST /v1/tenants/{id}/apply-template` did (`{ countryCode, applied, skipped }`), in a sentence. */
export function applyResultText(result) {
  const applied = result?.applied || [];
  const skipped = result?.skipped || [];
  if (!applied.length && !skipped.length) {
    return `There is no template for ${result?.countryCode || 'this country'}.`;
  }
  if (!applied.length) {
    return `Nothing to add: the tenant already has its own ${sectionList(skipped)}.`;
  }
  const added = `Added ${sectionList(applied)}.`;
  return skipped.length ? `${added} Left alone, already set up or module not held: ${sectionList(skipped)}.` : added;
}
