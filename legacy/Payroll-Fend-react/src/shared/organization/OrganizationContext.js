import { createContext, useContext } from "react";
import useOrganizationResolver from "./useOrganizationResolver";

const OrganizationContext = createContext(null);

// Wrap a layout in this once; every page rendered through that layout's <Outlet />
// shares the same resolved organizationId instead of resolving it independently.
export function OrganizationProvider({ mode = "admin", children }) {
    const resolver = useOrganizationResolver(mode);

    return (
        <OrganizationContext.Provider value={resolver}>
            {children}
        </OrganizationContext.Provider>
    );
}

export function useOrganization() {
    const ctx = useContext(OrganizationContext);
    if (!ctx) {
        throw new Error("useOrganization must be used within an OrganizationProvider");
    }
    return ctx;
}
