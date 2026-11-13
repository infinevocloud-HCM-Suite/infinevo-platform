import { createElement } from 'react';
import { Tag } from 'antd';

/**
 * The type of an earning, read from the flags the API sends on every `EarningResponse`
 * (code/backend/payroll/src/main/java/com/infinevo/payroll/component/EarningResponse.java:21-30) (W-117).
 *
 * First match wins: FBP, Scheduled, One-time, Variable with its frequency, else Fixed.
 */

/*
 * `humanize` from @shared/ui/format.js reads HALF_YEARLY as "Half yearly"; the spec asks for
 * "Half-yearly", so the four frequencies are spelled here.
 */
const FREQUENCY_LABELS = {
  MONTHLY: 'Monthly',
  QUARTERLY: 'Quarterly',
  HALF_YEARLY: 'Half-yearly',
  YEARLY: 'Yearly',
};

function frequencyLabel(code) {
  if (code === null || code === undefined) return null;
  const key = String(code).trim().toUpperCase();
  return FREQUENCY_LABELS[key] || null;
}

/** The label a benefit or reimbursement gets when it is not FBP: the kind itself. */
const KIND_LABELS = {
  benefits: 'Benefit',
  reimbursements: 'Reimbursement',
};

/**
 * @param {object|null|undefined} component a component from the catalogue
 * @param {'earnings'|'benefits'|'reimbursements'} [kind='earnings'] which catalogue it came from;
 *   a benefit or reimbursement has no variable, scheduled or one-time flag, so it reads FBP or its kind
 * @returns {{ label: string, color: string }}
 */
export function componentType(component, kind = 'earnings') {
  if (component?.fbpComponent) return { label: 'FBP', color: 'purple' };
  if (KIND_LABELS[kind]) return { label: KIND_LABELS[kind], color: 'default' };
  if (component?.scheduledEarning) return { label: 'Scheduled', color: 'gold' };
  if (component?.oneTime) return { label: 'One-time', color: 'gold' };
  if (component?.variable) {
    const frequency = frequencyLabel(component.earningFrequency);
    return { label: frequency ? `Variable · ${frequency}` : 'Variable', color: 'blue' };
  }
  return { label: 'Fixed', color: 'default' };
}

/**
 * The type as an antd Tag, for a table cell.
 *
 * @param {object|null|undefined} component
 * @param {'earnings'|'benefits'|'reimbursements'} [kind='earnings']
 */
export function componentTypeTag(component, kind = 'earnings') {
  const { label, color } = componentType(component, kind);
  // createElement, not JSX: the spec names this file .js, and Vite parses JSX only in .jsx.
  return createElement(Tag, { color, 'data-testid': 'component-type' }, label);
}
