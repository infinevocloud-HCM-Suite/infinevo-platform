import { Navigate, Outlet } from 'react-router-dom';
import { useSelector } from 'react-redux';

/**
 * AuthGuard
 *
 * Protects routes based on both authentication (token) and the portal the user
 * logged in through (`userType` in localStorage: "admin" | "employee").
 *
 * @param {"admin"|"employee"} [allowedRole]
 *   - When provided, the guard enforces that the logged-in user's portal matches.
 *   - When omitted, it falls back to the legacy token-only behaviour.
 *
 * Redirect rules:
 *   - No token            -> the correct login page for the route.
 *   - Wrong portal        -> the user's own home (admin -> /dashboard, employee -> /home).
 *   - Token but no userType (e.g. a session created before this change) -> allowed through,
 *     so existing sessions are not forcibly broken.
 */
const AuthGuard = ({ allowedRole }) => {
    const authSelector = useSelector(state => state.authReducer);

    // Redux is the primary source, but on a hard refresh it is empty until it is
    // rehydrated from localStorage, so we fall back to the persisted token.
    const token = authSelector.token || localStorage.getItem('__t');
    const userType = localStorage.getItem('userType');

    // 1. Not authenticated -> send to the appropriate login page.
    if (!token) {
        return <Navigate to={allowedRole === 'employee' ? '/employeePortalLogin' : '/login'} replace />;
    }

    // 2. Legacy behaviour: no role constraint on this route.
    if (!allowedRole) {
        return <Outlet />;
    }

    // 3. Authenticated but on the wrong portal -> bounce to the user's own home.
    if (userType && userType !== allowedRole) {
        return <Navigate to={userType === 'admin' ? '/dashboard' : '/home'} replace />;
    }

    // 4. Authenticated for this portal (or a pre-existing session with no userType).
    return <Outlet />;
};

export default AuthGuard;
