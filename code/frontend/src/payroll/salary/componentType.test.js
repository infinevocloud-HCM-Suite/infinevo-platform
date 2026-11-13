import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { componentType, componentTypeTag } from './componentType.js';

const NO_FLAGS = {
  fbpComponent: false,
  scheduledEarning: false,
  oneTime: false,
  variable: false,
  earningFrequency: 'MONTHLY',
};

function earning(overrides = {}) {
  return { ...NO_FLAGS, ...overrides };
}

describe('componentType (W-117)', () => {
  it('FBP is purple', () => {
    expect(componentType(earning({ fbpComponent: true }))).toEqual({ label: 'FBP', color: 'purple' });
  });

  it('Scheduled is gold', () => {
    expect(componentType(earning({ scheduledEarning: true }))).toEqual({ label: 'Scheduled', color: 'gold' });
  });

  it('One-time is gold', () => {
    expect(componentType(earning({ oneTime: true }))).toEqual({ label: 'One-time', color: 'gold' });
  });

  it.each([
    ['MONTHLY', 'Variable · Monthly'],
    ['QUARTERLY', 'Variable · Quarterly'],
    ['HALF_YEARLY', 'Variable · Half-yearly'],
    ['YEARLY', 'Variable · Yearly'],
  ])('Variable %s reads "%s" in blue', (frequency, label) => {
    expect(componentType(earning({ variable: true, earningFrequency: frequency }))).toEqual({ label, color: 'blue' });
  });

  it('Variable with no frequency reads just "Variable"', () => {
    expect(componentType(earning({ variable: true, earningFrequency: null })).label).toBe('Variable');
  });

  it('Fixed, in the default colour, when every flag is false', () => {
    expect(componentType(earning())).toEqual({ label: 'Fixed', color: 'default' });
  });

  it('Fixed for a missing earning', () => {
    expect(componentType(undefined)).toEqual({ label: 'Fixed', color: 'default' });
  });

  it('FBP wins over Variable', () => {
    expect(componentType(earning({ fbpComponent: true, variable: true, earningFrequency: 'YEARLY' })).label).toBe('FBP');
  });

  it('Scheduled wins over Variable', () => {
    expect(componentType(earning({ scheduledEarning: true, variable: true, earningFrequency: 'QUARTERLY' })).label).toBe(
      'Scheduled'
    );
  });

  it('Scheduled wins over One-time', () => {
    expect(componentType(earning({ scheduledEarning: true, oneTime: true })).label).toBe('Scheduled');
  });

  it('One-time wins over Variable', () => {
    expect(componentType(earning({ oneTime: true, variable: true })).label).toBe('One-time');
  });

  it('a benefit reads Benefit, a reimbursement reads Reimbursement, FBP first for both', () => {
    expect(componentType({ fbpComponent: false }, 'benefits')).toEqual({ label: 'Benefit', color: 'default' });
    expect(componentType({ fbpComponent: false }, 'reimbursements')).toEqual({
      label: 'Reimbursement',
      color: 'default',
    });
    expect(componentType({ fbpComponent: true }, 'benefits').label).toBe('FBP');
    expect(componentType({ fbpComponent: true }, 'reimbursements').label).toBe('FBP');
  });

  it('componentTypeTag renders the label in a Tag with data-testid component-type', () => {
    render(componentTypeTag(earning({ variable: true, earningFrequency: 'QUARTERLY' })));
    const tag = screen.getByTestId('component-type');
    expect(tag.textContent).toBe('Variable · Quarterly');
    expect(tag.className).toContain('ant-tag-blue');
  });
});
