import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Descriptions, Tag, Skeleton, Result, Button, Avatar, Space, Typography, theme } from 'antd';
import { UserOutlined, MailOutlined, PhoneOutlined, CalendarOutlined, IdcardOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';
import { EMPTY, formatDate, humanize } from '../../../shared/ui/format.js';

const { Title } = Typography;

export function MyProfile({ actions }) {
  const [employee, setEmployee] = useState(null);
  const [orgNames, setOrgNames] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { token } = theme.useToken();

  const fetchProfile = async () => {
    setLoading(true);
    setError(null);
    try {
      // The org lists never reject: a list the caller may not read leaves its names as '—' (D-77).
      const [data, names] = await Promise.all([portalService.getProfile(), portalService.getOrgNames()]);
      setEmployee(data);
      setOrgNames(names);
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
  const nameOf = (map, id) => (id && map?.[id]) || EMPTY;
  const text = (value) => value || EMPTY;
  const column = { xs: 1, sm: 2, md: 3 };

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
          <Space align="center" size="middle">
            <Title level={3} style={{ margin: 0 }}>
              {fullName || 'Employee'}
            </Title>
            <Tag color={statusColor}>{humanize(employee.status)}</Tag>
          </Space>
        </div>

        <Space orientation="vertical" size="large" style={{ width: '100%' }}>
          <Descriptions bordered column={column} title="Personal" size="middle">
            <Descriptions.Item label="First name">{text(employee.firstName)}</Descriptions.Item>
            <Descriptions.Item label="Middle name">{text(employee.middleName)}</Descriptions.Item>
            <Descriptions.Item label="Last name">{text(employee.lastName)}</Descriptions.Item>
            <Descriptions.Item label="Gender">{humanize(employee.gender)}</Descriptions.Item>
          </Descriptions>

          <Descriptions bordered column={column} title="Job" size="middle">
            <Descriptions.Item label={<Space><IdcardOutlined /> Employee #</Space>}>
              {text(employee.employeeNumber)}
            </Descriptions.Item>
            <Descriptions.Item label="Department">{nameOf(orgNames.departments, employee.departmentId)}</Descriptions.Item>
            <Descriptions.Item label="Designation">
              {nameOf(orgNames.designations, employee.designationId)}
            </Descriptions.Item>
            <Descriptions.Item label="Work location">
              {nameOf(orgNames.workLocations, employee.workLocationId)}
            </Descriptions.Item>
            <Descriptions.Item label={<Space><CalendarOutlined /> Date of joining</Space>}>
              {formatDate(employee.dateOfJoining)}
            </Descriptions.Item>
            <Descriptions.Item label="Status">{humanize(employee.status)}</Descriptions.Item>
          </Descriptions>

          <Descriptions bordered column={column} title="Contact" size="middle">
            <Descriptions.Item label={<Space><MailOutlined /> Work email</Space>}>
              {text(employee.workEmail)}
            </Descriptions.Item>
            <Descriptions.Item label={<Space><PhoneOutlined /> Mobile</Space>}>{text(employee.mobile)}</Descriptions.Item>
          </Descriptions>
        </Space>
      </Card>

      {actions?.EditOwnSection ? (
        <actions.EditOwnSection employeeId={employee.id} />
      ) : actions ? (
        <div>{typeof actions === 'function' ? actions({ employee, refresh: fetchProfile }) : actions}</div>
      ) : null}
    </Space>
  );
}

MyProfile.propTypes = {
  actions: PropTypes.oneOfType([PropTypes.object, PropTypes.node, PropTypes.func]),
};

export default MyProfile;
