/**
 * The shell's public surface for the modules (W-45 §5b). `@shell/screens` is the one shell path
 * core, hrms and payroll may import, so everything exported here is reachable from every screen.
 *
 * It holds the error screens and the two feed hints a screen needs to decide what to show -
 * nothing else. The feed itself, the Keycloak adapter, the store and the router stay inside the
 * shell: `index.test.js` pins this list, so adding to it is a decision, not a drive-by.
 */
export { Suspended } from './Suspended.jsx';
export { NotEntitled } from './NotEntitled.jsx';
export { NotFound } from './NotFound.jsx';
export { NoModules } from './NoModules.jsx';
export { useCan } from '../navigation/useCan.js';
export { useHasModule } from '../navigation/useHasModule.js';
