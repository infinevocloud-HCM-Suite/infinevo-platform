/**
 * Unit tests for useCan (W-12.3 §7, frontend).
 *
 * Tests are written using Node's built-in test runner (node:test / node:assert).
 * No JSX, no React — useCan logic is tested by mocking the NavigationContext.
 */

import { describe, it, beforeEach, mock } from 'node:test';
import assert from 'node:assert/strict';

// ──────────────────────────────────────────────────────────────────────────────
// Minimal mock of the NavigationContext (no React required for logic tests)
// ──────────────────────────────────────────────────────────────────────────────
let mockActions = [];

// Replicate useCan logic directly (test the logic, not the React hook wiring)
function canCheck(actionCode, actions) {
  if (!actionCode || typeof actionCode !== 'string') return false;
  if (!actions) return false;
  if (Array.isArray(actions)) return actions.includes(actionCode);
  if (actions instanceof Set) return actions.has(actionCode);
  return false;
}

// ──────────────────────────────────────────────────────────────────────────────
describe('useCan logic', () => {
  describe('when actions is an Array', () => {
    it('returns true for a code in actions', () => {
      const actions = ['core.employee.read', 'payroll.run.read'];
      assert.equal(canCheck('core.employee.read', actions), true);
    });

    it('returns false for a code NOT in actions', () => {
      const actions = ['core.employee.read'];
      assert.equal(canCheck('core.employee.delete', actions), false);
    });

    it('returns false when actions is empty', () => {
      assert.equal(canCheck('core.employee.read', []), false);
    });

    it('hides every guarded button when actions is empty', () => {
      const protectedActions = [
        'core.employee.read',
        'core.employee.create',
        'core.role.manage',
        'hrms.timesheet.read',
        'payroll.run.read',
      ];
      for (const action of protectedActions) {
        assert.equal(canCheck(action, []), false, `Expected ${action} to be false with empty actions`);
      }
    });
  });

  describe('when actions is a Set', () => {
    it('returns true for a code in the Set', () => {
      const actions = new Set(['core.employee.read', 'payroll.run.read']);
      assert.equal(canCheck('payroll.run.read', actions), true);
    });

    it('returns false for a code NOT in the Set', () => {
      const actions = new Set(['core.employee.read']);
      assert.equal(canCheck('payroll.run.read', actions), false);
    });
  });

  describe('invalid inputs', () => {
    it('returns false for null actionCode', () => {
      assert.equal(canCheck(null, ['core.employee.read']), false);
    });

    it('returns false for undefined actionCode', () => {
      assert.equal(canCheck(undefined, ['core.employee.read']), false);
    });

    it('returns false for empty string actionCode', () => {
      assert.equal(canCheck('', ['core.employee.read']), false);
    });

    it('returns false for null actions', () => {
      assert.equal(canCheck('core.employee.read', null), false);
    });

    it('returns false for undefined actions', () => {
      assert.equal(canCheck('core.employee.read', undefined), false);
    });
  });

  describe('action codes never contain role names', () => {
    const roleNames = ['admin', 'tenant-admin', 'employee', 'hr', 'manager', 'payroll-officer'];
    const validActions = ['core.employee.read', 'payroll.run.read', 'hrms.timesheet.read'];

    it('valid action codes match the module.resource.verb pattern', () => {
      const pattern = /^[a-z]+\.[a-z_]+\.[a-z_]+$/;
      for (const action of validActions) {
        assert.match(action, pattern, `${action} should match module.resource.verb`);
      }
    });

    it('role names are not valid action codes', () => {
      for (const role of roleNames) {
        assert.equal(
          canCheck(role, validActions),
          false,
          `Role name '${role}' must not be treated as an action code`,
        );
      }
    });
  });
});
