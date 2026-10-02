import { useEffect, useState } from 'react';
import { Card, Result, Tag, Typography, Space, theme } from 'antd';
import { ClockCircleOutlined, InfoCircleOutlined, ToolOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';

const { Paragraph, Text } = Typography;

export function MyTimesheet() {
  const [data, setData] = useState(null);
  const { token } = theme.useToken();

  useEffect(() => {
    portalService
      .getTimesheet()
      .then((res) => setData(res))
      .catch((err) => setData({ status: 'error', message: err?.message }));
  }, []);

  return (
    <Card
      title={
        <Space>
          <ClockCircleOutlined style={{ color: token.colorPrimary }} />
          <span>My Timesheet</span>
          <Tag color="orange" icon={<ToolOutlined />}>
            Under Development
          </Tag>
        </Space>
      }
      style={{ borderRadius: token.borderRadiusLG }}
      data-testid="panel-timesheet"
    >
      <Result
        icon={<ClockCircleOutlined style={{ color: token.colorPrimary, fontSize: 64 }} />}
        title="Timesheet Feature Coming Soon"
        subTitle="The HRMS project timesheet tracking and entry module is currently in development."
        extra={
          <div style={{ maxWidth: 560, margin: '0 auto', textAlign: 'left' }}>
            <Paragraph type="secondary">
              <InfoCircleOutlined style={{ marginRight: 8, color: token.colorInfo }} />
              Once the HRMS timesheet module is enabled, you will be able to log daily hours,
              assign work across project billing codes, and submit weekly timesheets for manager approval.
            </Paragraph>
            {data?.message && (
              <Paragraph style={{ textAlign: 'center' }}>
                <Text code>Backend status: {data.message}</Text>
              </Paragraph>
            )}
          </div>
        }
      />
    </Card>
  );
}

export default MyTimesheet;
