import { useEffect } from 'react';
import PropTypes from 'prop-types';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { Avatar, Dropdown, Button } from 'antd';
import { LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { useMe } from '../shell/auth/useMe.js';
import { logout } from '../shell/auth/keycloak.js';
import { MobileTabBar } from './MobileTabBar.jsx';
import { MobileAttendanceScreen } from './attendance/MobileAttendanceScreen.jsx';
import { MobileLeaveScreen } from './leave/MobileLeaveScreen.jsx';
import { MobilePayslipsScreen } from './payslips/MobilePayslipsScreen.jsx';
import { MobileApprovalsScreen } from './approvals/MobileApprovalsScreen.jsx';
import { PortalLayout } from '../shell/portal/PortalLayout.jsx';
import { ApplyLeave } from '../core/portal/ApplyLeave.jsx';
import { ApplyWfh } from '../core/portal/ApplyWfh.jsx';
import { LeaveBalanceDetail } from '../core/portal/LeaveBalanceDetail.jsx';
import { PayslipPage } from '../shell/portal/PayslipPage.jsx';
import './mobile.css';

export function MobileShell({ children = null }) {
  const me = useMe();
  const navigate = useNavigate();

  useEffect(() => {
    document.body.classList.add('mobile-native');
    return () => {
      document.body.classList.remove('mobile-native');
    };
  }, []);

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
      onClick: () => logout(),
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
      <MobileTabBar />
    </div>
  );
}

MobileShell.propTypes = {
  children: PropTypes.node,
};
