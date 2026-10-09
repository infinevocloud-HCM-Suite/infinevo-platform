import { describe, it, expect } from 'vitest';
import { sectionList, startsWithLine, applyResultText } from './countryTemplates.js';

const TEMPLATES = [{ countryCode: 'IN', sections: ['statutory', 'holidays'], version: 1 }];

describe('countryTemplates', () => {
  it('lists sections as words in the order the server applies them', () => {
    expect(sectionList(['statutory', 'holidays', 'pay_schedule'])).toBe('holidays, pay schedule, EPF and ESI');
    expect(sectionList(['some_new_section'])).toBe('some new section');
  });

  it('says what a country starts with, "No template" without one, and nothing before a full code', () => {
    expect(startsWithLine(TEMPLATES, ' in ')).toBe('Starts with: holidays, EPF and ESI');
    expect(startsWithLine(TEMPLATES, 'AE')).toBe('No template — the admin sets everything up');
    expect(startsWithLine(TEMPLATES, 'I')).toBeUndefined();
    expect(startsWithLine(null, 'IN')).toBeUndefined();
  });

  it('describes an apply result', () => {
    expect(applyResultText({ countryCode: 'AE', applied: [], skipped: [] })).toBe('There is no template for AE.');
    expect(applyResultText({ countryCode: 'IN', applied: [], skipped: ['holidays'] })).toBe(
      'Nothing to add: the tenant already has its own holidays.',
    );
    expect(applyResultText({ countryCode: 'IN', applied: ['holidays'], skipped: [] })).toBe('Added holidays.');
  });
});
