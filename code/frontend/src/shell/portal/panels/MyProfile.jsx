import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Descriptions, Skeleton, Result, Button, Avatar, Space, Row, Col, Typography, theme } from 'antd';
import { UserOutlined, MailOutlined, PhoneOutlined, CalendarOutlined, IdcardOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';
import { EMPTY, formatDate, humanize, statusLabel } from '../../../shared/ui/format.js';
import { StatusTag } from '../../../shared/ui/StatusTag.jsx';

const { Title, Text } = Typography;

export function MyProfile({ actions }) {
  const [employee, setEmployee] = useState(null);
  const [orgNames, setOrgNames] = useState({});
  const [managerName, setManagerName] = useState(null);
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
      if (data?.id) {
        const mgr = await portalService.getReportingManager(data.id);
        setManagerName(mgr);
      }
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
  const nameOf = (map, id) => (id && map?.[id]) || EMPTY;
  const text = (value) => value || EMPTY;
  // One group per Descriptions, one or two pairs a row, so a value never wraps a letter a line (D-58).
  const column = { xs: 1, sm: 1, md: 2 };
  const descriptionProps = {
    bordered: true,
    column,
    size: 'middle',
    styles: { label: { width: 180 } },
  };

  return (
    // Antd 5's Space reads `direction`; the old `orientation` was ignored, which laid the groups and
    // the edit panel side by side and squeezed every value into a one-letter column (D-56, D-58).
    <Row gutter={[token.marginLG, token.marginLG]} data-testid="panel-profile">
      <Col span={24}>
        <Card
          style={{
            borderRadius: token.borderRadiusLG,
            boxShadow: token.boxShadowTertiary,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: token.marginMD,
              marginBottom: token.marginLG,
            }}
          >
            <Avatar size={64} icon={<UserOutlined />} style={{ backgroundColor: token.colorPrimary, flexShrink: 0 }} />
            <Space align="center" size="middle" wrap>
              <Title level={3} style={{ margin: 0 }}>
                {fullName || 'Employee'}
              </Title>
              <StatusTag status={employee.status} />
            </Space>
          </div>

          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Descriptions {...descriptionProps} title="Personal">
              <Descriptions.Item label="First name">{text(employee.firstName)}</Descriptions.Item>
              <Descriptions.Item label="Middle name">{text(employee.middleName)}</Descriptions.Item>
              <Descriptions.Item label="Last name">{text(employee.lastName)}</Descriptions.Item>
              <Descriptions.Item label="Gender">{humanize(employee.gender)}</Descriptions.Item>
            </Descriptions>

            <Descriptions {...descriptionProps} title="Job">
              <Descriptions.Item label={<Space><IdcardOutlined /> Employee #</Space>}>
                {text(employee.employeeNumber)}
              </Descriptions.Item>
              <Descriptions.Item label="Department">{nameOf(orgNames.departments, employee.departmentId)}</Descriptions.Item>
              <Descriptions.Item label="Designation">
                {nameOf(orgNames.designations, employee.designationId)}
              </Descriptions.Item>
              <Descriptions.Item label="Reporting manager">{text(managerName)}</Descriptions.Item>
              <Descriptions.Item label="Work location">
                {nameOf(orgNames.workLocations, employee.workLocationId)}
              </Descriptions.Item>
              <Descriptions.Item label={<Space><CalendarOutlined /> Date of joining</Space>}>
                {formatDate(employee.dateOfJoining)}
              </Descriptions.Item>
              <Descriptions.Item label="Status">{statusLabel(employee.status)}</Descriptions.Item>
            </Descriptions>

            <Descriptions {...descriptionProps} title="Contact">
              <Descriptions.Item label={<Space><MailOutlined /> Work email</Space>}>
                <Text style={{ wordBreak: 'break-word' }}>{text(employee.workEmail)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label={<Space><PhoneOutlined /> Mobile</Space>}>{text(employee.mobile)}</Descriptions.Item>
            </Descriptions>
          </Space>
        </Card>
      </Col>

      {actions ? (
        <Col span={24} data-testid="panel-profile-actions">
          {actions.EditOwnSection ? (
            <actions.EditOwnSection employeeId={employee.id} />
          ) : typeof actions === 'function' ? (
            actions({ employee, refresh: fetchProfile })
          ) : (
            actions
          )}
        </Col>
      ) : null}
    </Row>
  );
}

MyProfile.propTypes = {
  actions: PropTypes.oneOfType([PropTypes.object, PropTypes.node, PropTypes.func]),
};

export default MyProfile;
