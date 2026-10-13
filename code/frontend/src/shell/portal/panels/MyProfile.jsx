import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Descriptions, Tag, Skeleton, Result, Button, Avatar, Space, Typography, Tabs, theme } from 'antd';
import {
  UserOutlined,
  MailOutlined,
  PhoneOutlined,
  CalendarOutlined,
  IdcardOutlined,
  ApartmentOutlined,
  EnvironmentOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { portalService } from '../portalService.js';
import { employeeService } from '@core/employee/employeeService.js';
import { EMPTY, formatDate, humanize } from '../../../shared/ui/format.js';

const { Title } = Typography;

export function MyProfile({ actions }) {
  const [employee, setEmployee] = useState(null);
  const [orgNames, setOrgNames] = useState({});
  const [personalSection, setPersonalSection] = useState({});
  const [contactSection, setContactSection] = useState({});
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
        try {
          const [pRes, cRes] = await Promise.allSettled([
            employeeService.section(data.id, 'personal'),
            employeeService.section(data.id, 'contact'),
          ]);
          if (pRes.status === 'fulfilled' && pRes.value) {
            setPersonalSection(pRes.value);
          }
          if (cRes.status === 'fulfilled' && cRes.value) {
            setContactSection(cRes.value);
          }
        } catch {
          // Gracefully continue: basic employee profile is already loaded
        }
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
  const statusColor = employee.status === 'ACTIVE' ? 'success' : 'default';
  const nameOf = (map, id) => (id && map?.[id]) || EMPTY;
  const text = (value) => value || EMPTY;
  const column = { xs: 1, sm: 2 };

  const labelStyle = {
    width: '180px',
    fontWeight: 500,
    color: token.colorTextSecondary,
    backgroundColor: token.colorFillAlter,
    whiteSpace: 'nowrap',
  };

  const contentStyle = {
    fontWeight: 500,
    color: token.colorText,
  };

  const formatAddress = (addr) => {
    if (!addr) return EMPTY;
    const parts = [addr.addressLine1, addr.addressLine2, addr.city, addr.state, addr.zipCode].filter(Boolean);
    return parts.length > 0 ? parts.join(', ') : EMPTY;
  };

  const formatPermanentAddress = (addr) => {
    if (!addr) return EMPTY;
    const parts = [
      addr.permanentAddressLine1,
      addr.permanentAddressLine2,
      addr.permanentCity,
      addr.permanentState,
      addr.permanentZipCode,
    ].filter(Boolean);
    return parts.length > 0 ? parts.join(', ') : EMPTY;
  };

  const formatEmergency = (name, rel, phone) => {
    if (!name && !phone) return EMPTY;
    const items = [name, rel ? `(${rel})` : null, phone ? `: ${phone}` : null].filter(Boolean);
    return items.length > 0 ? items.join(' ') : EMPTY;
  };

  const tabItems = [
    {
      key: 'personal',
      label: (
        <Space size="small">
          <UserOutlined style={{ color: token.colorPrimary }} />
          <span>Personal Details</span>
        </Space>
      ),
      forceRender: true,
      children: (
        <Descriptions
          bordered
          column={column}
          title={
            <Space size="small" style={{ fontSize: 16, fontWeight: 600 }}>
              <UserOutlined style={{ color: token.colorPrimary }} />
              <span>Personal</span>
            </Space>
          }
          size="middle"
          styles={{ label: labelStyle, content: contentStyle }}
        >
          <Descriptions.Item label="First name">{text(employee.firstName)}</Descriptions.Item>
          <Descriptions.Item label="Middle name">{text(employee.middleName)}</Descriptions.Item>
          <Descriptions.Item label="Last name">{text(employee.lastName)}</Descriptions.Item>
          <Descriptions.Item label="Gender">{humanize(employee.gender)}</Descriptions.Item>
          <Descriptions.Item label="Date of birth">{formatDate(personalSection?.dateOfBirth)}</Descriptions.Item>
          <Descriptions.Item label="Marital status">{humanize(personalSection?.maritalStatus)}</Descriptions.Item>
          <Descriptions.Item label="Nationality">{text(personalSection?.nationality)}</Descriptions.Item>
          <Descriptions.Item label="Father's name">{text(personalSection?.fatherName)}</Descriptions.Item>
          <Descriptions.Item label="Differently abled">{text(personalSection?.differentlyAbledType)}</Descriptions.Item>
          <Descriptions.Item label="Full tax exemption">
            {personalSection?.eligibleForFullTaxExemption !== undefined
              ? personalSection.eligibleForFullTaxExemption
                ? 'Yes'
                : 'No'
              : EMPTY}
          </Descriptions.Item>
        </Descriptions>
      ),
    },
    {
      key: 'contact',
      label: (
        <Space size="small">
          <MailOutlined style={{ color: token.colorPrimary }} />
          <span>Contact Details</span>
        </Space>
      ),
      forceRender: true,
      children: (
        <Descriptions
          bordered
          column={column}
          title={
            <Space size="small" style={{ fontSize: 16, fontWeight: 600 }}>
              <MailOutlined style={{ color: token.colorPrimary }} />
              <span>Contact</span>
            </Space>
          }
          size="middle"
          styles={{ label: labelStyle, content: contentStyle }}
        >
          <Descriptions.Item label={<Space><MailOutlined /> Work email</Space>}>
            {text(employee.workEmail)}
          </Descriptions.Item>
          <Descriptions.Item label={<Space><PhoneOutlined /> Mobile</Space>}>
            {text(employee.mobile)}
          </Descriptions.Item>
          <Descriptions.Item label="Personal email">{text(contactSection?.personalEmail)}</Descriptions.Item>
          <Descriptions.Item label="Alternate mobile">{text(contactSection?.alternateMobile)}</Descriptions.Item>
          <Descriptions.Item label="Present address">{formatAddress(contactSection)}</Descriptions.Item>
          <Descriptions.Item label="Permanent address">{formatPermanentAddress(contactSection)}</Descriptions.Item>
          <Descriptions.Item label="Emergency contact">
            {formatEmergency(
              contactSection?.emergencyContactName,
              contactSection?.emergencyContactRelationship,
              contactSection?.emergencyContactNumber
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Secondary emergency">
            {formatEmergency(
              contactSection?.secondaryEmergencyContactName,
              contactSection?.secondaryEmergencyContactRelationship,
              contactSection?.secondaryEmergencyContactNumber
            )}
          </Descriptions.Item>
        </Descriptions>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }} data-testid="panel-profile">
      {/* Profile Summary Hero Card */}
      <Card
        data-testid="panel-profile-summary"
        style={{
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
          border: `1px solid ${token.colorBorderSecondary}`,
          background: `linear-gradient(180deg, ${token.colorFillAlter} 0%, ${token.colorBgContainer} 100%)`,
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: token.marginMD,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: token.marginLG, flexWrap: 'wrap' }}>
            <Avatar
              size={76}
              icon={<UserOutlined />}
              style={{
                backgroundColor: token.colorPrimary,
                boxShadow: `0 4px 14px ${token.colorPrimary}33`,
                border: `3px solid ${token.colorBgContainer}`,
                flexShrink: 0,
              }}
            />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: token.marginSM, flexWrap: 'wrap' }}>
                <Title level={3} style={{ margin: 0, fontWeight: 700 }}>
                  {fullName || 'Employee'}
                </Title>
                <Tag
                  color={statusColor}
                  icon={employee.status === 'ACTIVE' ? <CheckCircleOutlined /> : undefined}
                  style={{
                    borderRadius: 12,
                    padding: '2px 10px',
                    fontWeight: 600,
                  }}
                >
                  {humanize(employee.status)}
                </Tag>
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: token.marginSM,
                }}
              >
                <Tag
                  icon={<IdcardOutlined style={{ color: token.colorPrimary }} />}
                  style={{
                    borderRadius: token.borderRadiusSM,
                    padding: '3px 10px',
                    fontSize: 13,
                    backgroundColor: token.colorBgContainer,
                    border: `1px solid ${token.colorBorderSecondary}`,
                    color: token.colorText,
                  }}
                >
                  Role: {nameOf(orgNames.designations, employee.designationId)}
                </Tag>
                <Tag
                  icon={<ApartmentOutlined style={{ color: token.colorPrimary }} />}
                  style={{
                    borderRadius: token.borderRadiusSM,
                    padding: '3px 10px',
                    fontSize: 13,
                    backgroundColor: token.colorBgContainer,
                    border: `1px solid ${token.colorBorderSecondary}`,
                    color: token.colorText,
                  }}
                >
                  Department: {nameOf(orgNames.departments, employee.departmentId)}
                </Tag>
                <Tag
                  icon={<EnvironmentOutlined style={{ color: token.colorPrimary }} />}
                  style={{
                    borderRadius: token.borderRadiusSM,
                    padding: '3px 10px',
                    fontSize: 13,
                    backgroundColor: token.colorBgContainer,
                    border: `1px solid ${token.colorBorderSecondary}`,
                    color: token.colorText,
                  }}
                >
                  Location: {nameOf(orgNames.workLocations, employee.workLocationId)}
                </Tag>
              </div>
            </div>
          </div>
        </div>
      </Card>

      {/* 2 Tabs at Top: Personal Details & Contact Details (View Mode) */}
      <Card
        data-testid="panel-profile-tabs"
        style={{
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
          border: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Tabs
          defaultActiveKey="personal"
          items={tabItems}
          type="line"
          size="large"
          tabBarStyle={{ marginBottom: token.marginMD }}
        />
      </Card>

      {/* Structured Job Detail Section */}
      <Card
        data-testid="panel-profile-job"
        style={{
          borderRadius: token.borderRadiusLG,
          boxShadow: token.boxShadowTertiary,
          border: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <Descriptions
          bordered
          column={column}
          title={
            <Space size="small" style={{ fontSize: 16, fontWeight: 600 }}>
              <IdcardOutlined style={{ color: token.colorPrimary }} />
              <span>Job</span>
            </Space>
          }
          size="middle"
          styles={{ label: labelStyle, content: contentStyle }}
        >
          <Descriptions.Item label={<Space><IdcardOutlined /> Employee #</Space>}>
            {text(employee.employeeNumber)}
          </Descriptions.Item>
          <Descriptions.Item label="Department">
            {nameOf(orgNames.departments, employee.departmentId)}
          </Descriptions.Item>
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
      </Card>

      {/* Non-edit custom actions if passed as a function or node */}
      {typeof actions === 'function' ? (
        actions({ employee, refresh: fetchProfile })
      ) : actions && !actions?.EditOwnSection ? (
        actions
      ) : null}
    </Space>
  );
}

MyProfile.propTypes = {
  actions: PropTypes.oneOfType([PropTypes.object, PropTypes.node, PropTypes.func]),
};

export default MyProfile;
