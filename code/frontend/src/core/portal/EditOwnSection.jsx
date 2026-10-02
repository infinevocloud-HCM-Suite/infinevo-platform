import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Card, Tabs, Spin, Typography, theme } from 'antd';
import { UserOutlined, ContactsOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { portalService } from '@shell/portal/portalService.js';
import { SectionTab } from '../employee/tabs/SectionTab.jsx';

const { Title, Text } = Typography;

export function EditOwnSection({ employeeId: propEmployeeId }) {
  const { token } = theme.useToken();
  const canUpdateOwn = useCan('core.employee.update_own');

  const [employeeId, setEmployeeId] = useState(propEmployeeId || null);
  const [loading, setLoading] = useState(!propEmployeeId);

  useEffect(() => {
    let active = true;

    if (!propEmployeeId) {
      setLoading(true);
      portalService
        .getProfile()
        .then((profile) => {
          if (active && profile?.id) {
            setEmployeeId(profile.id);
          }
        })
        .catch(() => {})
        .finally(() => {
          if (active) setLoading(false);
        });
    } else {
      setEmployeeId(propEmployeeId);
      setLoading(false);
    }

    return () => {
      active = false;
    };
  }, [propEmployeeId]);

  // Spec §5 / §7: Hidden without core.employee.update_own
  if (!canUpdateOwn) {
    return null;
  }

  if (loading) {
    return (
      <Card style={{ borderRadius: token.borderRadiusLG, marginTop: token.marginLG }}>
        <Spin spinning />
      </Card>
    );
  }

  if (!employeeId) {
    return null;
  }

  const tabItems = [
    {
      key: 'personal',
      label: (
        <span>
          <UserOutlined style={{ marginRight: 8 }} />
          Personal Details
        </span>
      ),
      children: (
        <SectionTab
          employeeId={employeeId}
          sectionName="personal"
          permissionOverride="core.employee.update_own"
        />
      ),
    },
    {
      key: 'contact',
      label: (
        <span>
          <ContactsOutlined style={{ marginRight: 8 }} />
          Contact Details
        </span>
      ),
      children: (
        <SectionTab
          employeeId={employeeId}
          sectionName="contact"
          permissionOverride="core.employee.update_own"
        />
      ),
    },
  ];

  return (
    <Card
      style={{
        borderRadius: token.borderRadiusLG,
        boxShadow: token.boxShadowTertiary,
        marginTop: token.marginLG,
      }}
      data-testid="edit-own-section"
    >
      <div style={{ marginBottom: token.marginMD }}>
        <Title level={4} style={{ margin: 0 }}>
          Edit Profile Information
        </Title>
        <Text type="secondary">Update your own personal and contact details.</Text>
      </div>

      <Tabs items={tabItems} type="card" />
    </Card>
  );
}

EditOwnSection.propTypes = {
  employeeId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
};

export default EditOwnSection;
