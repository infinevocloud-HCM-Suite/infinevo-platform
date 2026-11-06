import PropTypes from 'prop-types';
import { useLocation, useNavigate, Outlet } from 'react-router-dom';
import { Layout, Menu, theme } from 'antd';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import {
  CalendarOutlined,
  SafetyCertificateOutlined,
  MedicineBoxOutlined,
  BankOutlined,
  GiftOutlined,
  FileDoneOutlined,
  FileTextOutlined,
} from '@ant-design/icons';

const { Sider, Content } = Layout;

export const SETTINGS_MENU_ITEMS = [
  {
    key: '/payroll/settings/pay-schedule',
    icon: <CalendarOutlined />,
    label: 'Pay Schedule',
  },
  {
    key: '/payroll/settings/epf',
    icon: <SafetyCertificateOutlined />,
    label: 'EPF',
  },
  {
    key: '/payroll/settings/esi',
    icon: <MedicineBoxOutlined />,
    label: 'ESI',
  },
  {
    key: '/payroll/settings/professional-tax',
    icon: <BankOutlined />,
    label: 'Professional Tax',
  },
  {
    key: '/payroll/settings/fbp',
    icon: <GiftOutlined />,
    label: 'Flexible Benefit Plan',
  },
  {
    key: '/payroll/settings/tax-declaration',
    icon: <FileDoneOutlined />,
    label: 'Tax declaration',
  },
  {
    key: '/payroll/settings/form-16',
    icon: <FileTextOutlined />,
    label: 'Form 16',
  },
];

export function SettingsLayout({ children }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const selectedKey =
    SETTINGS_MENU_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ||
    SETTINGS_MENU_ITEMS[0].key;

  return (
    <div className="saas-page-container fadeSlideUp" style={{ width: '100%' }}>
      <PageHeader
        title="Payroll Settings"
        subtitle="Configure organization-wide payroll schedules, statutory settings, and benefits."
        breadcrumbs={[
          { label: 'Payroll', to: '/payroll' },
          { label: 'Settings' },
        ]}
      />

      <Layout
        className="saas-card"
        style={{
          background: token.colorBgContainer,
          borderRadius: 14,
          border: `1px solid ${token.colorBorderSecondary}`,
          overflow: 'hidden',
          boxShadow: '0 4px 20px -2px rgba(15, 23, 42, 0.05)',
        }}
      >
        <Sider
          width={240}
          style={{
            background: token.colorBgContainer,
            borderRight: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={SETTINGS_MENU_ITEMS}
            onClick={({ key }) => navigate(key)}
            style={{ height: '100%', borderRight: 0, paddingTop: token.paddingXS }}
          />
        </Sider>

        <Content style={{ padding: token.paddingLG, minHeight: 520, background: token.colorBgContainer }}>
          {children || <Outlet />}
        </Content>
      </Layout>
    </div>
  );
}

SettingsLayout.propTypes = {
  children: PropTypes.node,
};
