import { useCallback, useEffect, useState } from "react";
import axios from "axios";
import { GlobalConst } from "../appConfig/globalConst";

// The only place in the app that resolves and persists `organizationId`.
// Admin pages used to each read `localStorage.getItem("organizationId") || "default-org-id"`
// on mount, racing this same resolution and sometimes reading it before it was ever written.
// Routing every page through this hook (via OrganizationProvider) removes that race entirely.
export default function useOrganizationResolver(mode = "admin") {
    const [organizations, setOrganizations] = useState([]);
    const [selectedOrgId, setSelectedOrgId] = useState(null);
    const [ready, setReady] = useState(false);

    const fetchRoleName = useCallback(async (token, organizationId) => {
        try {
            const res = await axios.get(
                `${GlobalConst.API_URL}/api/organization-user-role-mapping/my-role`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        organizationId,
                    },
                }
            );
            if (res.data?.status === 200 && res.data?.data) {
                localStorage.setItem("roleName", res.data.data.roleName);
            }
        } catch (e) {
            console.error("Failed to fetch role for organization:", e);
        }
    }, []);

    const resolve = useCallback(async () => {
        setReady(false);
        const token = localStorage.getItem("__t");
        const stored = localStorage.getItem("organizationId");

        try {
            if (mode === "employee") {
                // Employee portal access is scoped by isEmployeePortalEnable, not org membership.
                const res = await axios.get(
                    `${GlobalConst.API_URL}/api/organization-user-role-mapping/my-organizations`,
                    { headers: { Authorization: `Bearer ${token}` } }
                );
                const orgIds = Array.isArray(res.data?.data) ? res.data.data : [];
                setOrganizations(orgIds.map((id) => ({ organizationId: id })));

                const resolvedId = orgIds.find((id) => String(id) === String(stored)) ?? orgIds[0] ?? null;
                if (resolvedId && String(resolvedId) !== String(stored)) {
                    localStorage.setItem("organizationId", resolvedId);
                }
                setSelectedOrgId(resolvedId);
            } else {
                // Admin portal: only active orgs the user actually belongs to are valid choices.
                const res = await axios.get(
                    `${GlobalConst.API_URL}/api/organizations/active-organizations`,
                    { headers: { Authorization: `Bearer ${token}` } }
                );
                const orgs = Array.isArray(res.data?.data) ? res.data.data : [];
                setOrganizations(orgs);

                const storedOrg = orgs.find((org) => String(org.organizationId) === String(stored));
                const resolvedOrg = storedOrg || orgs[0] || null;
                const resolvedId = resolvedOrg ? resolvedOrg.organizationId : null;

                if (resolvedId && String(resolvedId) !== String(stored)) {
                    localStorage.setItem("organizationId", resolvedId);
                }
                setSelectedOrgId(resolvedId);

                if (resolvedId) {
                    await fetchRoleName(token, resolvedId);
                }
            }
        } catch (e) {
            console.error("Failed to resolve organization:", e);
        } finally {
            setReady(true);
        }
    }, [mode, fetchRoleName]);

    useEffect(() => {
        resolve();
    }, [resolve]);

    const selectOrganization = useCallback((organizationId) => {
        localStorage.setItem("organizationId", organizationId);
        setSelectedOrgId(organizationId);
    }, []);

    return { ready, organizations, selectedOrgId, selectOrganization, refresh: resolve };
}
