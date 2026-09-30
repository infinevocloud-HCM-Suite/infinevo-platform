import { useNavigation } from './useNavigation.js';

/**
 * `useHasModule('PAYROLL')` - does the tenant hold this module, according to the navigation
 * feed's `modules` (W-46.4 §8)? For a core screen that serves both modules and must show only
 * the half the tenant bought.
 *
 * It reads what the tenant holds, not what this user may see: the menu items are filtered by
 * permission, so they cannot answer this. A hint for the screen, not a boundary - the endpoint
 * still refuses on its own (W-12.2). Absent feed, absent module, or anything but a string: false.
 */
export function useHasModule(moduleCode) {
  const { modules } = useNavigation();
  if (typeof moduleCode !== 'string' || moduleCode === '') {
    return false;
  }
  return Array.isArray(modules) && modules.includes(moduleCode.toUpperCase());
}
