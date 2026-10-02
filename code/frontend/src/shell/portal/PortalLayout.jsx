import { Suspense, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Tabs, Skeleton, Result, Button, Empty, Typography, Space, theme } from 'antd';
import {
  UserOutlined,
  CalendarOutlined,
  FileTextOutlined,
  DollarOutlined,
  ClockCircleOutlined,
  AppstoreOutlined,
} from '@ant-design/icons';
import { portalService } from './portalService.js';
import { MyProfile } from './panels/MyProfile.jsx';
import { MyLeave } from './panels/MyLeave.jsx';
import { MyDocuments } from './panels/MyDocuments.jsx';
import { MyPayslips } from './panels/MyPayslips.jsx';
import { MyTimesheet } from './panels/MyTimesheet.jsx';
import { portalPanels as payrollPortalPanels } from '../../payroll/index.js';

const { Title, Text } = Typography;

const PANEL_ICONS = {
  profile: <UserOutlined />,
  leave: <CalendarOutlined />,
  documents: <FileTextOutlined />,
  payslips: <DollarOutlined />,
  timesheet: <ClockCircleOutlined />,
  taxDeclaration: <FileTextOutlined />,
};

const PANEL_COMPONENTS = {
  profile: MyProfile,
  leave: MyLeave,
  documents: MyDocuments,
  payslips: MyPayslips,
  timesheet: MyTimesheet,
};

const modulePanels = [...(payrollPortalPanels || [])];
const MODULE_PANEL_COMPONENTS = Object.fromEntries(
  modulePanels.map((p) => [p.code, p.component])
);

export function PortalLayout() {
  const { panelId } = useParams();
  const navigate = useNavigate();
  const [panels, setPanels] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { token } = theme.useToken();

  const fetchPanels = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await portalService.getPanels();
      setPanels(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Failed to load portal panels');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPanels();
  }, []);

  if (loading) {
    return (
      <div style={{ padding: token.paddingLG }} data-testid="portal-loading">
        <Card>
          <Skeleton active paragraph={{ rows: 8 }} />
        </Card>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: token.paddingLG }} data-testid="portal-error">
        <Card>
          <Result
            status="error"
            title="Unable to Access Self-Service Portal"
            subTitle={error}
            extra={
              <Button type="primary" onClick={fetchPanels}>
                Retry
              </Button>
            }
          />
        </Card>
      </div>
    );
  }

  if (!panels || panels.length === 0) {
    return (
      <div style={{ padding: token.paddingLG }} data-testid="portal-empty">
        <Card style={{ borderRadius: token.borderRadiusLG, textAlign: 'center', padding: token.paddingLG }}>
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={
              <div>
                <Title level={4}>Self-Service Portal Unavailable</Title>
                <Text type="secondary">
                  No self-service panels are available for your account. Please contact your administrator.
                </Text>
              </div>
            }
          />
        </Card>
      </div>
    );
  }

  const activeKey = panelId && panels.some((p) => p.code === panelId)
    ? panelId
    : panels[0]?.code;

  if (panelId && !panels.some((p) => p.code === panelId)) {
    return (
      <div style={{ padding: token.paddingLG }} data-testid="portal-panel-forbidden">
        <Card>
          <Result
            status="403"
            title="Panel Not Available"
            subTitle="You do not have access to this self-service panel, or your organization does not hold the required module."
            extra={
              <Button type="primary" onClick={() => navigate('/me')}>
                Return to Portal
              </Button>
            }
          />
        </Card>
      </div>
    );
  }

  const tabItems = panels.map((panel) => {
    const Component = PANEL_COMPONENTS[panel.code] || MODULE_PANEL_COMPONENTS[panel.code];
    return {
      key: panel.code,
      label: (
        <Space size="small">
          {PANEL_ICONS[panel.code] || <AppstoreOutlined />}
          <span>{panel.title}</span>
        </Space>
      ),
      children: Component ? (
        <div style={{ marginTop: token.marginMD }}>
          <Suspense fallback={<Skeleton active paragraph={{ rows: 6 }} />}>
            <Component panel={panel} />
          </Suspense>
        </div>
      ) : (
        <Empty description="Panel component not found" />
      ),
    };
  });

  return (
    <div style={{ width: '100%' }} data-testid="portal-layout">
      <div style={{ marginBottom: token.marginMD }}>
        <Title level={2} style={{ margin: 0 }}>
          Employee Self-Service
        </Title>
        <Text type="secondary">Manage your personal records, leave, documents, and statements.</Text>
      </div>

      <Card
        style={{
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
        }}
        styles={{ body: { padding: `${token.paddingSM}px ${token.paddingLG}px ${token.paddingLG}px` } }}
      >
        <Tabs
          activeKey={activeKey}
          onChange={(key) => navigate(`/me/${key}`)}
          items={tabItems}
          type="line"
          size="large"
          tabBarStyle={{ marginBottom: token.marginMD }}
        />
      </Card>
    </div>
  );
}

export default PortalLayout;
