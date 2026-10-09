import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Link, useNavigate } from 'react-router-dom';
import { Avatar, Button, Card, Col, List, Progress, Row, Space, Typography, theme } from 'antd';
import { apiClient } from '../../shared/api/client.js';
import { portalService } from '../portal/portalService.js';
import { useNavigation } from '../navigation/useNavigation.js';
import { useMe, markWelcomeSeen } from '../auth/useMe.js';
import { initialsOf } from '../Header.jsx';

const { Title, Text, Paragraph } = Typography;

/** The roles that get the "Where things are" card (spec §2). */
const STAFF_ROLES = ['hr', 'manager', 'payroll-officer'];

/**
 * The admin's three steps, each opening its row on the setup checklist. `/setup` names a row
 * `setup-step-row-<code>` (SetupChecklist.jsx), and the step codes are SetupStepCatalogue's.
 */
export const ADMIN_STEPS = [
  { key: 'people', title: 'Add work location and employees', to: '/setup#setup-step-row-work-location' },
  { key: 'pay', title: 'Pay schedule and salary components', to: '/setup#setup-step-row-pay-schedule' },
  { key: 'statutory', title: 'Statutory settings', to: '/setup#setup-step-row-epf' },
];

function collectPaths(items, into = new Set()) {
  for (const item of items || []) {
    if (item.path) into.add(item.path);
    if (item.children) collectPaths(item.children, into);
  }
  return into;
}

/** The tenant admin's card: three steps and the checklist's live progress. */
function AdminCard() {
  const [checklist, setChecklist] = useState(null);
  useEffect(() => {
    let live = true;
    apiClient
      .get('/v1/setup-checklist')
      .then((response) => {
        if (live) setChecklist(response?.data || null);
      })
      // No progress is better than an error on a welcome page; the steps still link to /setup.
      .catch(() => {});
    return () => {
      live = false;
    };
  }, []);

  const done = Number(checklist?.completedCount) || 0;
  const total = Number(checklist?.totalCount) || 0;

  return (
    <Card title="Three steps to your first payroll" data-testid="welcome-card-admin">
      {checklist && total > 0 ? (
        <div data-testid="welcome-setup-progress">
          <Text>{`${done}/${total} setup steps done`}</Text>
          <Progress percent={Math.round((done / total) * 100)} showInfo={false} />
        </div>
      ) : null}
      <List
        dataSource={ADMIN_STEPS}
        renderItem={(step, index) => (
          <List.Item key={step.key}>
            <Link to={step.to}>{`${index + 1}. ${step.title}`}</Link>
          </List.Item>
        )}
      />
    </Card>
  );
}

/** HR, managers and payroll officers: where their screens are. Links the feed does not name are left out. */
function StaffCard({ homePath, feedPaths }) {
  const links = [
    homePath ? { key: 'home', label: 'Your home page', to: homePath } : null,
    feedPaths.has('/approvals') ? { key: 'approvals', label: 'Approvals waiting for you', to: '/approvals' } : null,
    feedPaths.has('/employees') ? { key: 'people', label: 'The people list', to: '/employees' } : null,
  ].filter(Boolean);
  return (
    <Card title="Where things are" data-testid="welcome-card-staff">
      <List
        dataSource={links}
        renderItem={(link) => (
          <List.Item key={link.key}>
            <Link to={link.to}>{link.label}</Link>
          </List.Item>
        )}
      />
    </Card>
  );
}

StaffCard.propTypes = {
  homePath: PropTypes.string,
  feedPaths: PropTypes.instanceOf(Set).isRequired,
};

/**
 * Employees: their own portal at /me. The payslips link shows only when the portal offers that
 * panel - a tenant without Payroll answers it "Panel Not Available".
 */
function EmployeeCard() {
  const [hasPayslips, setHasPayslips] = useState(false);
  useEffect(() => {
    let live = true;
    portalService
      .getPanels()
      .then((panels) => {
        if (live) setHasPayslips(Array.isArray(panels) && panels.some((panel) => panel?.code === 'payslips'));
      })
      .catch(() => {});
    return () => {
      live = false;
    };
  }, []);
  const links = [
    { key: 'profile', label: 'Check your personal and contact details', to: '/me/profile' },
    hasPayslips ? { key: 'payslips', label: 'Your payslips appear here', to: '/me/payslips' } : null,
    { key: 'leave', label: 'Apply for leave here', to: '/me/leave/apply' },
  ].filter(Boolean);
  return (
    <Card title="Complete your profile" data-testid="welcome-card-employee">
      <List
        dataSource={links}
        renderItem={(link) => (
          <List.Item key={link.key}>
            <Link to={link.to}>{link.label}</Link>
          </List.Item>
        )}
      />
    </Card>
  );
}

/**
 * The first-sign-in page (W-73.8 §5): the tenant's branding, one card per kind of role the user
 * holds, and Go to my home. The shell sends `/` here while `/me` says `welcomeSeen: false`; the
 * user menu's "Getting started" opens it later, and then Go to my home writes nothing.
 */
export function Welcome({ homePath }) {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const { items, tenantName, tenantLogoUrl, tagline } = useNavigation();
  const me = useMe();
  const [leaving, setLeaving] = useState(false);

  const roles = Array.isArray(me.roles) ? me.roles : [];
  const isAdmin = roles.includes('tenant-admin');
  const isStaff = roles.some((role) => STAFF_ROLES.includes(role));
  const isEmployee = roles.includes('employee');
  const feedPaths = collectPaths(items);
  const home = homePath || '/me';

  const goHome = async () => {
    if (leaving) return;
    setLeaving(true);
    if (!me.welcomeSeen) {
      // A failed write shows the page again next sign-in; it does not keep the user here now.
      await markWelcomeSeen().catch(() => {});
    }
    navigate(home, { replace: true });
  };

  const cards = [
    isAdmin ? <AdminCard key="admin" /> : null,
    isStaff ? <StaffCard key="staff" homePath={homePath} feedPaths={feedPaths} /> : null,
    isEmployee ? <EmployeeCard key="employee" /> : null,
  ].filter(Boolean);

  // Roles with no card of their own (platform staff, custom roles) have nothing to read here: the
  // page is marked seen and they go straight home.
  const nothingToShow = !me.loading && cards.length === 0;
  useEffect(() => {
    if (nothingToShow) goHome();
    // goHome is rebuilt every render; running once when the page turns out empty is the intent.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nothingToShow]);
  if (nothingToShow) return null;

  return (
    <div data-testid="welcome-screen" style={{ maxWidth: 960, margin: '0 auto' }}>
      <Space size="middle" align="center" style={{ marginBottom: token.marginLG }}>
        {tenantLogoUrl ? (
          <img
            src={tenantLogoUrl}
            alt={tenantName ? `${tenantName} logo` : 'Company logo'}
            style={{ height: token.controlHeightLG * 1.5, maxWidth: 200, objectFit: 'contain', display: 'block' }}
          />
        ) : tenantName ? (
          <Avatar size={64} style={{ background: token.colorPrimary, color: token.colorBgContainer, fontWeight: 600 }}>
            {initialsOf(tenantName)}
          </Avatar>
        ) : null}
        <div>
          <Title level={2} style={{ margin: 0 }} id="heading-welcome">
            {tenantName ? `Welcome to ${tenantName}` : 'Welcome'}
          </Title>
          {tagline ? <Text type="secondary">{tagline}</Text> : null}
        </div>
      </Space>
      <Paragraph>
        {me.displayName ? `Hello ${me.displayName}. ` : ''}
        Here is where to start. You can open this page again from your user menu, under Getting started.
      </Paragraph>
      <Row gutter={[token.margin, token.margin]}>
        {cards.map((card) => (
          <Col key={card.key} xs={24} md={cards.length > 1 ? 12 : 24}>
            {card}
          </Col>
        ))}
      </Row>
      <div style={{ marginTop: token.marginLG }}>
        <Button type="primary" size="large" onClick={goHome} loading={leaving} id="btn-welcome-go-home">
          Go to my home
        </Button>
      </div>
    </div>
  );
}

Welcome.propTypes = {
  homePath: PropTypes.string,
};
