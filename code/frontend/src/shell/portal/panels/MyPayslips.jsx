import { useEffect, useState } from 'react';
import { Card, Result, Tag, Typography, Space, theme } from 'antd';
import { DollarOutlined, InfoCircleOutlined, ToolOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';

const { Paragraph, Text } = Typography;

export function MyPayslips() {
  const [data, setData] = useState(null);
  const { token } = theme.useToken();

  useEffect(() => {
    portalService
      .getPayslips()
      .then((res) => setData(res))
      .catch((err) => setData({ status: 'error', message: err?.message }));
  }, []);

  return (
    <Card
      title={
        <Space>
          <DollarOutlined style={{ color: token.colorPrimary }} />
          <span>My Payslips</span>
          <Tag color="orange" icon={<ToolOutlined />}>
            Under Development
          </Tag>
        </Space>
      }
      style={{ borderRadius: token.borderRadiusLG }}
      data-testid="panel-payslips"
    >
      <Result
        icon={<DollarOutlined style={{ color: token.colorPrimary, fontSize: 64 }} />}
        title="Payslips Feature Coming in W-36"
        subTitle="The payroll calculation and payslip generation engine is currently in development."
        extra={
          <div style={{ maxWidth: 560, margin: '0 auto', textAlign: 'left' }}>
            <Paragraph type="secondary">
              <InfoCircleOutlined style={{ marginRight: 8, color: token.colorInfo }} />
              Once the payroll engine (W-36) is connected, you will be able to view, download,
              and verify your monthly compensation statements, tax breakdowns, and statutory
              deductions directly from this panel.
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

export default MyPayslips;
