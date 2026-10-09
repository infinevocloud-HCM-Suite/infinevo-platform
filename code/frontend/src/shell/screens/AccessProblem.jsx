import PropTypes from 'prop-types';
import { Result, Button } from 'antd';
import { logout } from '../auth/keycloak.js';

/**
 * Why the app stopped instead of sending the browser back to sign-in (D-63). Signing in again cannot
 * fix either case — Keycloak still holds a live session and would hand the same user straight back —
 * so the way out is Sign out, which ends that session.
 */
const PROBLEMS = {
  noTenant: {
    title: 'Your account is not linked to one company',
    text:
      'You are signed in, but Infinevo cannot tell which company to open for this account. ' +
      'Ask your administrator to check your access, or sign out and sign in with another account.',
  },
  authLoop: {
    title: 'We could not sign you in',
    text:
      'Signing in did not work twice in a row. This usually happens when this browser is still signed in ' +
      'as someone else. Sign out, then sign in again — or use a private window.',
  },
};

export function AccessProblem({ kind }) {
  const problem = PROBLEMS[kind] ?? PROBLEMS.authLoop;
  return (
    <Result
      status="warning"
      title={problem.title}
      subTitle={problem.text}
      extra={
        <Button type="primary" id="btn-access-problem-logout" onClick={() => logout()}>
          Sign out
        </Button>
      }
    />
  );
}

AccessProblem.propTypes = {
  kind: PropTypes.oneOf(['noTenant', 'authLoop']).isRequired,
};
