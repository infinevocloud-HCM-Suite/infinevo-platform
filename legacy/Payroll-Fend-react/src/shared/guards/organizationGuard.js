import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import axios from 'axios';

import Loader from '../components/loaders/fullPageLoader';
import { GlobalConst } from '../appConfig/globalConst';
import {
    isOrgInactive,
    normalizeOrganizationsResponse,
    resolveAdminLandingPath,
} from '../helpers/resolveAdminLandingPath';

/**
 * OrganizationGuard
 *
 * Blocks access to main admin app pages until the user has at least one active
 * organization. Users with no org are sent to create one; users with only
 * inactive orgs (legacy registrations) are sent to complete setup.
 */
const OrganizationGuard = () => {
    const [redirectPath, setRedirectPath] = useState(null);
    const [allowed, setAllowed] = useState(false);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const token = localStorage.getItem('__t');
        if (!token) {
            setLoading(false);
            return;
        }

        axios
            .get(`${GlobalConst.API_URL}/api/organizations`, {
                headers: { Authorization: `Bearer ${token}` },
            })
            .then((orgRes) => {
                const organizations = normalizeOrganizationsResponse(orgRes);

                if (!organizations.length) {
                    setRedirectPath('/create-new-organization');
                    return;
                }

                const hasActiveOrg = organizations.some((org) => !isOrgInactive(org));
                if (!hasActiveOrg) {
                    const inactiveOrg = organizations.find((org) => org?.organizationId);
                    if (inactiveOrg?.organizationId) {
                        setRedirectPath(
                            `/setup-new-organization/${inactiveOrg.organizationId}`
                        );
                    } else {
                        setRedirectPath('/create-new-organization');
                    }
                    return;
                }

                setAllowed(true);
            })
            .catch(() => {
                setRedirectPath('/create-new-organization');
            })
            .finally(() => {
                setLoading(false);
            });
    }, []);

    if (loading) {
        return <Loader />;
    }

    if (redirectPath) {
        return <Navigate to={redirectPath} replace />;
    }

    if (allowed) {
        return <Outlet />;
    }

    return <Navigate to={resolveAdminLandingPath([])} replace />;
};

export default OrganizationGuard;
