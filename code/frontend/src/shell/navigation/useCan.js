import { useNavigation } from './useNavigation.js';

/**
 * Hook to check if the current user holds a specific action permission
 * according to the navigation feed actions set (W-12.3 §5).
 *
 * <p>The one way a screen decides to show or hide a button.
 *
 * @param {string} actionCode action code, e.g. 'core.employee.delete'
 * @returns {boolean} true if caller holds the action code, false otherwise
 */
export function useCan(actionCode) {
  // Hook must be called unconditionally — rules-of-hooks.
  // The early-return for invalid input comes AFTER the hook call.
  const { actions } = useNavigation();

  if (!actionCode || typeof actionCode !== 'string') {
    return false;
  }
  if (!actions) {
    return false;
  }
  if (Array.isArray(actions)) {
    return actions.includes(actionCode);
  }
  if (actions instanceof Set) {
    return actions.has(actionCode);
  }
  return false;
}
