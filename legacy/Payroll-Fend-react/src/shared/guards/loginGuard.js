import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useSelector } from 'react-redux';
import axios from 'axios';

import Loader from '../components/loaders/fullPageLoader';
import { GlobalConst } from '../appConfig/globalConst';
import {
    normalizeOrganizationsResponse,
    resolveAdminLandingPath,
} from '../helpers/resolveAdminLandingPath';

/**
 * LoginGuard
 *
 * Guards the auth/login routes. If the user is already authenticated, they are
 * redirected to their own portal home instead of being allowed to see a login
 * page again:
 *   - employee -> /home
 *   - admin -> org-aware landing path (create org, setup org, or onboarding dashboard)
 */
const LoginGuard = () => {
    const authSelector = useSelector(state => state.authReducer);
    const [redirectPath, setRedirectPath] = useState(null);
    const [resolved, setResolved] = useState(false);

    const token = authSelector.token || localStorage.getItem('__t');
    const userType = localStorage.getItem('userType');

    useEffect(() => {
        if (!token) {
            setResolved(true);
            return;
        }

        if (userType === 'employee') {
            setRedirectPath('/home');
            setResolved(true);
            return;
        }

        axios
            .get(`${GlobalConst.API_URL}/api/organizations`, {
                headers: { Authorization: `Bearer ${token}` },
            })
            .then((orgRes) => {
                const organizations = normalizeOrganizationsResponse(orgRes);
                setRedirectPath(resolveAdminLandingPath(organizations));
            })
            .catch(() => {
                setRedirectPath('/create-new-organization');
            })
            .finally(() => {
                setResolved(true);
            });
    }, [token, userType]);

    if (!token) {
        return <Outlet />;
    }

    if (!resolved) {
        return <Loader />;
    }

    if (redirectPath) {
        return <Navigate to={redirectPath} replace />;
    }

    return <Navigate to="/onboarding-dashboard" replace />;
};

export default LoginGuard;
