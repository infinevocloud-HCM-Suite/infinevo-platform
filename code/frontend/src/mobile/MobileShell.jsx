import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { useSelector, useDispatch } from 'react-redux';
import { Avatar, Dropdown, Button } from 'antd';
import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Capacitor } from '@capacitor/core';
import { SplashScreen } from '@capacitor/splash-screen';
import { StatusBar, Style } from '@capacitor/status-bar';
import { useMe } from '../shell/auth/useMe.js';
import { logout } from '../shell/auth/keycloak.js';
import { useCan } from '../shell/navigation/useCan.js';
import { setUnauthorizedHandler } from '../shared/api/client.js';
import { isMobileAuthenticated, startMobileLogin, clearStoredTokens, mobileLogout } from './mobileAuth.js';
import { MobileTabBar } from './MobileTabBar.jsx';
import { MobileAttendanceScreen } from './attendance/MobileAttendanceScreen.jsx';
import { MobileLeaveScreen } from './leave/MobileLeaveScreen.jsx';
import { MobilePayslipsScreen } from './payslips/MobilePayslipsScreen.jsx';
import { MobileApprovalsScreen } from './approvals/MobileApprovalsScreen.jsx';
import { mobileApprovalService } from './approvals/mobileApprovalService.js';
import { mobilePayslipService } from './payslips/mobilePayslipService.js';
import { setPendingCount } from '../core/approvals/approvalSlice.js';
import { PortalLayout } from '../shell/portal/PortalLayout.jsx';
import { ApplyLeave } from '../core/portal/ApplyLeave.jsx';
import { ApplyWfh } from '../core/portal/ApplyWfh.jsx';
import { LeaveBalanceDetail } from '../core/portal/LeaveBalanceDetail.jsx';
import { PayslipPage } from '../shell/portal/PayslipPage.jsx';
import './mobile.css';

export function MobileShell({ children = null, initialAuthenticated = null }) {
  const me = useMe();
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const canDecideApproval = useCan('core.approval.decide');
  const pendingBadgeCount = useSelector((state) => state.approvals?.pendingCount ?? 0);

  const [authenticated, setAuthenticated] = useState(() => {
    if (initialAuthenticated !== null) return initialAuthenticated;
    return typeof isMobileAuthenticated === 'function' ? isMobileAuthenticated() : true;
  });
  const [loggingIn, setLoggingIn] = useState(false);
  const [authError, setAuthError] = useState(null);

  useEffect(() => {
    document.body.classList.add('mobile-native');
    if (Capacitor.isNativePlatform()) {
      SplashScreen.hide().catch(() => {});
      StatusBar.setStyle({ style: Style.Light }).catch(() => {});
    }
    // Cleanup cached payslips on app mount (W-98.4 §2)
    mobilePayslipService.cleanup().catch(() => {});
    return () => {
      document.body.classList.remove('mobile-native');
    };
  }, []);

  // Load pending approvals count on mount and when permission changes (W-98.5 §2)
  useEffect(() => {
    if (canDecideApproval) {
      mobileApprovalService
        .getPendingApprovals({ page: 0, size: 1 })
        .then((res) => {
          const total = res.totalElements ?? res.unfilteredTotal ?? res.rows?.length ?? 0;
          dispatch(setPendingCount(total));
        })
        .catch(() => {});
    }
  }, [canDecideApproval, dispatch]);

  useEffect(() => {
    setUnauthorizedHandler(async () => {
      await clearStoredTokens();
      setAuthenticated(false);
      try {
        await startMobileLogin();
      } catch (err) {
        setAuthError(err?.message || 'Authentication required');
      }
    });
  }, []);

  const handleSignIn = async () => {
    setLoggingIn(true);
    setAuthError(null);
    try {
      await startMobileLogin();
      setAuthenticated(isMobileAuthenticated());
    } catch (err) {
      setAuthError(err?.message || 'Login was cancelled');
    } finally {
      setLoggingIn(false);
    }
  };

  if (!authenticated) {
    return (
      <div
        className="mobile-shell-container"
        data-testid="mobile-signin-screen"
        style={{
          padding: 32,
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '80vh',
        }}
      >
        <div
          className="mobile-header-logo"
          style={{ width: 64, height: 64, fontSize: 32, margin: '0 auto 16px', lineHeight: '64px' }}
          aria-hidden="true"
        >
          I
        </div>
        <h2>Sign In to Infinevo</h2>
        <p style={{ color: '#666', marginBottom: 24, maxWidth: 300 }}>
          {authError
            ? 'Login was cancelled or interrupted. Please try again.'
            : 'Please sign in to access your attendance, leaves, payslips, and approvals.'}
        </p>
        <Button type="primary" size="large" onClick={handleSignIn} loading={loggingIn}>
          Sign In
        </Button>
      </div>
    );
  }

  const userMenuItems = [
    {
      key: 'profile',
      label: 'My Profile',
      icon: <UserOutlined />,
      onClick: () => navigate('/me/profile'),
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      label: 'Sign Out',
      icon: <LogoutOutlined />,
      danger: true,
      onClick: () => {
        if (Capacitor.isNativePlatform()) {
          mobileLogout();
        } else {
          logout();
        }
      },
    },
  ];

  return (
    <div className="mobile-shell-container" data-testid="mobile-shell">
      {/* Top Header */}
      <header className="mobile-header" role="banner">
        <div className="mobile-header-brand">
          <div className="mobile-header-logo" aria-hidden="true">I</div>
          <div>
            <div className="mobile-header-title">Infinevo</div>
            <div className="mobile-header-tenant">{me.displayName || 'Mobile Portal'}</div>
          </div>
        </div>

        <div className="mobile-header-actions">
          <Dropdown menu={{ items: userMenuItems }} trigger={['click']} placement="bottomRight">
            <Button
              type="text"
              shape="circle"
              aria-label="User Menu"
              icon={
                <Avatar size={32} style={{ backgroundColor: '#1890ff', cursor: 'pointer' }}>
                  {me.displayName ? me.displayName.charAt(0).toUpperCase() : <UserOutlined />}
                </Avatar>
              }
            />
          </Dropdown>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="mobile-content" role="main">
        {children || (
          <Routes>
            <Route path="/me" element={<PortalLayout />} />
            <Route path="/me/profile" element={<PortalLayout />} />
            <Route path="/me/:panelId" element={<PortalLayout />} />
            <Route path="/me/attendance" element={<MobileAttendanceScreen />} />
            <Route path="/me/leave" element={<MobileLeaveScreen />} />
            <Route path="/me/leave/apply" element={<ApplyLeave />} />
            <Route path="/me/leave-balance" element={<LeaveBalanceDetail />} />
            <Route path="/me/wfh/apply" element={<ApplyWfh />} />
            <Route path="/me/payslips" element={<MobilePayslipsScreen />} />
            <Route path="/me/payslips/:payrunId" element={<PayslipPage />} />
            <Route path="/me/approvals" element={<MobileApprovalsScreen />} />
            <Route path="*" element={<Navigate to="/me" replace />} />
          </Routes>
        )}
      </main>

      {/* Persistent Bottom Tab Bar */}
      <MobileTabBar pendingBadgeCount={pendingBadgeCount} />
    </div>
  );
}

MobileShell.propTypes = {
  children: PropTypes.node,
  initialAuthenticated: PropTypes.bool,
};
