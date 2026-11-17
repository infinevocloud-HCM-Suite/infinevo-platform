import PropTypes from 'prop-types';
import { useLocation, useNavigate } from 'react-router-dom';
import { Badge } from 'antd';
import {
  HomeOutlined,
  ClockCircleOutlined,
  CalendarOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { useCan } from '../shell/navigation/useCan.js';

export function MobileTabBar({ canApproveOverride = undefined, pendingBadgeCount = 0 }) {
  const location = useLocation();
  const navigate = useNavigate();
  const canDecideApproval = useCan('core.approval.decide');
  const showApprovals = canApproveOverride !== undefined ? canApproveOverride : canDecideApproval;

  const currentPath = location.pathname;

  const tabs = [
    {
      key: 'home',
      label: 'Home',
      path: '/me',
      icon: <HomeOutlined className="mobile-tab-icon" />,
      exact: true,
    },
    {
      key: 'attendance',
      label: 'Attendance',
      path: '/me/attendance',
      icon: <ClockCircleOutlined className="mobile-tab-icon" />,
    },
    {
      key: 'leave',
      label: 'Leave',
      path: '/me/leave',
      icon: <CalendarOutlined className="mobile-tab-icon" />,
    },
    {
      key: 'payslips',
      label: 'Payslips',
      path: '/me/payslips',
      icon: <FileTextOutlined className="mobile-tab-icon" />,
    },
  ];

  if (showApprovals) {
    tabs.push({
      key: 'approvals',
      label: 'Approvals',
      path: '/me/approvals',
      icon: (
        <Badge count={pendingBadgeCount} size="small" offset={[4, -2]}>
          <CheckCircleOutlined className="mobile-tab-icon" />
        </Badge>
      ),
    });
  }

  const isTabActive = (tab) => {
    if (tab.exact) {
      return currentPath === tab.path;
    }
    return currentPath === tab.path || currentPath.startsWith(tab.path + '/');
  };

  return (
    <nav className="mobile-tabbar" role="tablist" aria-label="Mobile Navigation">
      {tabs.map((tab) => {
        const active = isTabActive(tab);
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={active}
            aria-label={tab.label}
            className={`mobile-tab-item ${active ? 'active' : ''}`}
            onClick={() => navigate(tab.path)}
          >
            {tab.icon}
            <span className="mobile-tab-label">{tab.label}</span>
          </button>
        );
      })}
    </nav>
  );
}

MobileTabBar.propTypes = {
  canApproveOverride: PropTypes.bool,
  pendingBadgeCount: PropTypes.number,
};
