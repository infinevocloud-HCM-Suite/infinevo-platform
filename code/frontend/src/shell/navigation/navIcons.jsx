import {
  AppstoreOutlined,
  AuditOutlined,
  BankOutlined,
  CalendarOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
  HomeOutlined,
  IdcardOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';

/**
 * One line icon per top-level menu item (W-83.7). Keyed by the feed's label key, so a module
 * that adds a top-level entry gets `AppstoreOutlined` until it is named here.
 */
const ICONS = {
  'nav.admin.home': DashboardOutlined,
  'nav.approvals': CheckSquareOutlined,
  'nav.audit': AuditOutlined,
  'nav.employees': IdcardOutlined,
  'nav.holidays': CalendarOutlined,
  'nav.hrms': ClockCircleOutlined,
  'nav.hrms.dashboard': DashboardOutlined,
  'nav.leave': CalendarOutlined,
  'nav.me': UserOutlined,
  'nav.organisation': BankOutlined,
  'nav.payroll': WalletOutlined,
  'nav.payroll.dashboard': DashboardOutlined,
  'nav.people': TeamOutlined,
  'nav.roles': SafetyCertificateOutlined,
  'nav.settings': SettingOutlined,
  'nav.setup': ToolOutlined,
  'nav.tenants': BankOutlined,
  'nav.users': UserOutlined,
  'nav.wfh': HomeOutlined,
};

export function navIcon(labelKey) {
  const Icon = ICONS[labelKey] || AppstoreOutlined;
  return <Icon data-testid="nav-icon" />;
}
