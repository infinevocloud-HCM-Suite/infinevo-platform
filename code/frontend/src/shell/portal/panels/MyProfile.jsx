import { useEffect, useState } from 'react';
import { Card, Descriptions, Tag, Skeleton, Result, Button, Avatar, Space, Typography, theme } from 'antd';
import { UserOutlined, MailOutlined, PhoneOutlined, CalendarOutlined, IdcardOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';

const { Title, Text } = Typography;

export function MyProfile() {
  const [employee, setEmployee] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { token } = theme.useToken();

  const fetchProfile = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await portalService.getProfile();
      setEmployee(data);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Failed to load profile');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  if (loading) {
    return (
      <Card data-testid="panel-profile-loading">
        <Skeleton active avatar paragraph={{ rows: 6 }} />
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <Result
          status="error"
          title="Unable to Load Profile"
          subTitle={error}
          extra={
            <Button type="primary" onClick={fetchProfile}>
              Retry
            </Button>
          }
        />
      </Card>
    );
  }

  if (!employee) {
    return null;
  }

  const fullName = [employee.firstName, employee.middleName, employee.lastName].filter(Boolean).join(' ');
  const statusColor = employee.status === 'ACTIVE' ? 'success' : 'default';

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }} data-testid="panel-profile">
      <Card
        style={{
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: token.marginMD, marginBottom: token.marginLG }}>
          <Avatar size={72} icon={<UserOutlined />} style={{ backgroundColor: token.colorPrimary }} />
          <div>
            <Space align="center" size="middle">
              <Title level={3} style={{ margin: 0 }}>
                {fullName || 'Employee'}
              </Title>
              <Tag color={statusColor}>{employee.status || 'ACTIVE'}</Tag>
            </Space>
            <div>
              <Text type="secondary">
                <IdcardOutlined style={{ marginRight: 6 }} />
                {employee.employeeNumber}
              </Text>
            </div>
          </div>
        </div>

        <Descriptions
          bordered
          column={{ xs: 1, sm: 2, md: 3 }}
          title="Employee Details"
          size="middle"
        >
          <Descriptions.Item label="Employee #">{employee.employeeNumber || '—'}</Descriptions.Item>
          <Descriptions.Item label="First Name">{employee.firstName || '—'}</Descriptions.Item>
          <Descriptions.Item label="Last Name">{employee.lastName || '—'}</Descriptions.Item>
          <Descriptions.Item label={<Space><MailOutlined /> Work Email</Space>}>
            {employee.workEmail || '—'}
          </Descriptions.Item>
          <Descriptions.Item label={<Space><PhoneOutlined /> Mobile</Space>}>
            {employee.mobile || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Gender">{employee.gender || '—'}</Descriptions.Item>
          <Descriptions.Item label={<Space><CalendarOutlined /> Date of Joining</Space>}>
            {employee.dateOfJoining || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Department">
            {employee.departmentId ? <Text code>{employee.departmentId}</Text> : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Designation">
            {employee.designationId ? <Text code>{employee.designationId}</Text> : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="Work Location">
            {employee.workLocationId ? <Text code>{employee.workLocationId}</Text> : '—'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}

export default MyProfile;
