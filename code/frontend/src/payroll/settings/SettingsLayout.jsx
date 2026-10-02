import PropTypes from 'prop-types';
import { useLocation, useNavigate, Outlet } from 'react-router-dom';
import { Layout, Menu, Typography, theme } from 'antd';
import {
  CalendarOutlined,
  SafetyCertificateOutlined,
  MedicineBoxOutlined,
  BankOutlined,
  GiftOutlined,
  FileDoneOutlined,
} from '@ant-design/icons';

const { Sider, Content } = Layout;
const { Title, Text } = Typography;

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
];

export function SettingsLayout({ children }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const selectedKey =
    SETTINGS_MENU_ITEMS.find((item) => location.pathname.startsWith(item.key))?.key ||
    SETTINGS_MENU_ITEMS[0].key;

  return (
    <div style={{ width: '100%' }}>
      <div style={{ marginBottom: token.marginLG }}>
        <Title level={2} style={{ margin: 0 }}>
          Payroll Settings
        </Title>
        <Text type="secondary">
          Configure organization-wide payroll schedules, statutory settings, and benefits.
        </Text>
      </div>

      <Layout
        style={{
          background: token.colorBgContainer,
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
          overflow: 'hidden',
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

        <Content style={{ padding: token.paddingLG, minHeight: 520 }}>
          {children || <Outlet />}
        </Content>
      </Layout>
    </div>
  );
}

SettingsLayout.propTypes = {
  children: PropTypes.node,
};
