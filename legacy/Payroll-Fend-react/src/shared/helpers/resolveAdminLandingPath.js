export function isOrgInactive(org) {
    return (
        org?.isOrgActive === false ||
        String(org?.isOrgActive).toLowerCase() === 'false' ||
        org?.isOrgActive === 0 ||
        String(org?.isOrgActive) === '0'
    );
}

/**
 * Resolves where an admin user should land after login based on their organizations.
 *
 * @param {Array} organizations - List of organizations from GET /api/organizations
 * @returns {string} Path to redirect to
 */
export function resolveAdminLandingPath(organizations) {
    if (!organizations?.length) {
        return '/create-new-organization';
    }

    if (organizations.length === 1) {
        const org = organizations[0];
        if (isOrgInactive(org) && org?.organizationId) {
            return `/setup-new-organization/${org.organizationId}`;
        }
    }

    return '/onboarding-dashboard';
}

/**
 * Normalizes the organizations array from various API response shapes.
 */
export function normalizeOrganizationsResponse(orgRes) {
    if (Array.isArray(orgRes?.data?.data)) {
        return orgRes.data.data;
    }
    if (Array.isArray(orgRes?.data)) {
        return orgRes.data;
    }
    return [];
}
