import { useContext, useSyncExternalStore } from 'react';
import { ReactReduxContext } from 'react-redux';

const noStore = () => () => {};
const noSession = () => null;

/**
 * The live impersonation session from the store, or null (W-65.3 §5). Read through the store by
 * slice name, not by importing the slice from `@core` (§11). Safe without a Redux Provider: a
 * shell rendered alone has no session.
 *
 * @param {Object} [store] the Redux store; the one in context when omitted
 */
export function useImpersonationSession(store) {
  const contextStore = useContext(ReactReduxContext)?.store ?? null;
  const source = store === undefined ? contextStore : store;
  return useSyncExternalStore(
    source ? source.subscribe : noStore,
    source ? () => source.getState().impersonation?.session ?? null : noSession,
  );
}
