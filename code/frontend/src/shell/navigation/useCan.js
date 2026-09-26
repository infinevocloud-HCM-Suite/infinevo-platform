import { useNavigation } from './useNavigation.js';

/**
 * `useCan('core.employee.delete')` - does the signed-in user hold this action code, according
 * to the navigation feed (W-12.3 §5)? The one way a screen decides whether to show a button.
 *
 * It is a hint for the screen, not a boundary: the endpoint behind the button still refuses
 * on its own (W-12.2). Absent feed, absent code, or anything but a string: false.
 */
export function useCan(actionCode) {
  const { actions } = useNavigation();
  if (typeof actionCode !== 'string' || actionCode === '') {
    return false;
  }
  if (Array.isArray(actions)) {
    return actions.includes(actionCode);
  }
  return actions instanceof Set && actions.has(actionCode);
}
