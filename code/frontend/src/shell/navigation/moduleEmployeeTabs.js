import PropTypes from 'prop-types';
import { createContext, createElement, useContext, useMemo } from 'react';

/**
 * Employee page tabs the modules contribute (D-66, W-47.1 §5). Core owns EmployeePage but may not
 * import a module, so the shell composes the modules' `employeeTabs` (shell/moduleTabs.js) and
 * hands them down here, as PortalLayout does with the modules' portal panels.
 *
 * Each tab is `{ key, label, module, action?, render }`. The provider keeps only the tabs whose
 * module the tenant holds and whose action the user holds - the feed's `modules` and `actions`,
 * passed in by AppShell, which already reads the feed (a second useNavigation here would add a
 * second refetch on every tenant change). A hint for the screen, not a boundary: the endpoint
 * behind the tab still refuses on its own.
 */
const ModuleEmployeeTabsContext = createContext([]);

export function ModuleEmployeeTabsProvider({ tabs, modules, actions, children }) {
  const visible = useMemo(
    () =>
      (tabs || []).filter((tab) => {
        if (typeof tab.module !== 'string' || !Array.isArray(modules)) return false;
        if (!modules.includes(tab.module.toUpperCase())) return false;
        if (!tab.action) return true;
        return Array.isArray(actions) ? actions.includes(tab.action) : actions instanceof Set && actions.has(tab.action);
      }),
    [tabs, modules, actions],
  );
  return createElement(ModuleEmployeeTabsContext.Provider, { value: visible }, children);
}

ModuleEmployeeTabsProvider.propTypes = {
  tabs: PropTypes.arrayOf(
    PropTypes.shape({
      key: PropTypes.string.isRequired,
      label: PropTypes.node.isRequired,
      module: PropTypes.string.isRequired,
      action: PropTypes.string,
      render: PropTypes.func.isRequired,
    }),
  ),
  modules: PropTypes.arrayOf(PropTypes.string),
  actions: PropTypes.oneOfType([PropTypes.arrayOf(PropTypes.string), PropTypes.instanceOf(Set)]),
  children: PropTypes.node,
};

/** The module tabs this tenant and user may see on the employee page, after core's own. */
export function useModuleEmployeeTabs() {
  return useContext(ModuleEmployeeTabsContext);
}
