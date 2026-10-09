import { useCallback, useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import {
  Card, Descriptions, Switch, Select, Form, Input, Button, Tabs, Tag, Typography, Space, Skeleton,
  Modal, Alert, theme,
} from 'antd';
import { NotFound, useCan } from '@shell/screens';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { tenantService } from './tenantService.js';
import { impersonationService } from './impersonationService.js';
import { started } from './impersonationSlice.js';
import { AuditTab, AUDIT_HINT } from './AuditTab.jsx';
import { isPlatformTenant, MODULES, STATUSES, STATUS_COLORS } from './platform.js';
import { applyResultText } from './countryTemplates.js';

/** The user invitation form: the Invitations tab of Users & access (`core.users`, W-73.4). */
export const INVITATION_FORM_PATH = '/users?tab=invitations';

/** Statuses that lock a tenant out of its modules, so they ask first. */
const LOCKING_STATUSES = ['SUSPENDED', 'CANCELLED'];

/**
 * One tenant, for platform staff (W-65.3 §5): overview, module switches, status, "act as", audit.
 *
 * Reads `GET /api/v1/tenants/{id}` (`TenantOverview`, snake_case, bare). Revoking a module and
 * suspending ask first: the tenant's data in a revoked module goes read-only (W-12-1 §13 row 1)
 * and a suspended tenant is locked out of every module endpoint.
 *
 * "Act as" asks for an email, resolved server-side, because a customer's users cannot be listed
 * before a session exists (§5 note, §13 decision 2). A tenant with no user accounts gets a
 * bootstrap session instead, after which staff send the first admin's invitation.
 */
export function TenantDetail() {
  const { id } = useParams();
  const dispatch = useDispatch();
  const { token } = theme.useToken();
  const session = useSelector((state) => state.impersonation?.session ?? null);
  const canImpersonate = useCan('core.tenant.impersonate');

  const [tenant, setTenant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [opening, setOpening] = useState(false);
  const [bootstrapped, setBootstrapped] = useState(false);
  const [applyingTemplate, setApplyingTemplate] = useState(false);
  const [templateResult, setTemplateResult] = useState(null);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    try {
      const res = await tenantService.get(id);
      setTenant(res);
      setMissing(false);
    } catch (err) {
      if (err?.status === 404) setMissing(true);
      else errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <Skeleton active />;
  if (missing || !tenant) return <NotFound />;

  const platform = isPlatformTenant(tenant.tenant_id);
  const modules = tenant.modules || [];
  const noUsers = Number(tenant.user_count) === 0;
  const sessionHere = !!session && session.tenantId === tenant.tenant_id;
  // A session here while the tenant has no user accounts can only be a bootstrap one: the server
  // opens a user session only for an existing user. So the invitation link survives a remount.
  const bootstrapHere = sessionHere && (bootstrapped || noUsers);

  const applyModules = async (next) => {
    setBusy(true);
    try {
      await tenantService.setModules(tenant.tenant_id, next);
      await load();
    } catch (err) {
      errorMsg(err);
    } finally {
      setBusy(false);
    }
  };

  const onModuleSwitch = (module, checked) => {
    if (checked) {
      applyModules([...new Set([...modules, module])]);
      return;
    }
    Modal.confirm({
      title: `Revoke ${module} from ${tenant.name}?`,
      content: `${tenant.name}'s ${module} data stays, but goes read-only and the ${module} menu disappears at next sign-in.`,
      okText: 'Revoke',
      okType: 'danger',
      onOk: () => applyModules(modules.filter((m) => m !== module)),
    });
  };

  const applyStatus = async (status) => {
    setBusy(true);
    try {
      await tenantService.setStatus(tenant.tenant_id, status);
      await load();
    } catch (err) {
      errorMsg(err);
    } finally {
      setBusy(false);
    }
  };

  const onStatusChange = (status) => {
    if (status === tenant.status) return;
    if (!LOCKING_STATUSES.includes(status)) {
      applyStatus(status);
      return;
    }
    Modal.confirm({
      title: `Set ${tenant.name} to ${status}?`,
      content: `Every user of ${tenant.name} is locked out of every module until it is reactivated.`,
      okText: status === 'SUSPENDED' ? 'Suspend' : 'Cancel subscription',
      okType: 'danger',
      onOk: () => applyStatus(status),
    });
  };

  const onActAs = async (values) => {
    setOpening(true);
    try {
      const target = noUsers ? null : { email: values.email };
      const opened = await impersonationService.open(tenant.tenant_id, target, values.reason.trim());
      dispatch(
        started({
          sessionId: opened.sessionId,
          tenantId: opened.tenantId || tenant.tenant_id,
          tenantName: tenant.name,
          userLabel: opened.userEmail || 'setup admin (no user yet)',
          expiresAt: opened.expiresAt,
        }),
      );
      form.resetFields();
      if (!opened.userAccountId) setBootstrapped(true);
    } catch (err) {
      errorMsg(err);
    } finally {
      setOpening(false);
    }
  };

  // W-73.9: a tenant made before country templates gets the sections it has nothing of its own for.
  const onApplyTemplate = async () => {
    setApplyingTemplate(true);
    try {
      setTemplateResult(await tenantService.applyTemplate(tenant.tenant_id));
    } catch (err) {
      errorMsg(err);
    } finally {
      setApplyingTemplate(false);
    }
  };

  const overview = (
    <Space direction="vertical" style={{ width: '100%' }} size={token.marginLG}>
      <Descriptions bordered column={1} size="small">
        <Descriptions.Item label="Name">
          <Space size="small">
            {tenant.name}
            {platform && <Tag color="purple">platform</Tag>}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label="Tenant id">{tenant.tenant_id}</Descriptions.Item>
        <Descriptions.Item label="Country">{tenant.country_code}</Descriptions.Item>
        <Descriptions.Item label="Timezone">{tenant.timezone}</Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLORS[tenant.status] || 'default'}>{tenant.status}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Created">
          {tenant.created_at ? new Date(tenant.created_at).toLocaleString() : ''}
        </Descriptions.Item>
        <Descriptions.Item label="Current period ends">{tenant.current_period_end || '-'}</Descriptions.Item>
        <Descriptions.Item label="User accounts">{tenant.user_count}</Descriptions.Item>
      </Descriptions>

      {!platform && (
        <Card size="small" title="Modules">
          <Space direction="vertical">
            {MODULES.map((m) => (
              <Space key={m}>
                <Switch
                  aria-label={`Module ${m}`}
                  checked={modules.includes(m)}
                  disabled={busy}
                  onChange={(checked) => onModuleSwitch(m, checked)}
                />
                <span>{m}</span>
              </Space>
            ))}
          </Space>
        </Card>
      )}

      {!platform && (
        <Card size="small" title="Status">
          <Select
            aria-label="Subscription status"
            style={{ width: 200 }}
            value={tenant.status}
            disabled={busy}
            onChange={onStatusChange}
            options={STATUSES.map((s) => ({ value: s, label: s }))}
          />
        </Card>
      )}

      {!platform && (
        <Card size="small" title="Country template">
          <Space direction="vertical" style={{ width: '100%' }}>
            <Typography.Text type="secondary">
              Adds the country&apos;s holidays, leave types, salary components, EPF and ESI settings and pay
              schedule where this tenant has none of its own. Nothing it already has is changed.
            </Typography.Text>
            <Button onClick={onApplyTemplate} loading={applyingTemplate}>
              Apply country template
            </Button>
            {templateResult && <Alert type="info" showIcon message={applyResultText(templateResult)} />}
          </Space>
        </Card>
      )}

      {!platform && (canImpersonate || sessionHere) && (
        <Card size="small" title="Act as">
          {sessionHere && (
            <Alert
              type="info"
              showIcon
              message={`You are acting as ${session.userLabel} in ${tenant.name}. Stop from the banner at the top.`}
            />
          )}
          {/* Only starting a session needs the permission. Once one is live the feed is the
              target's, which never carries core.tenant.impersonate, so what follows a start is
              keyed on the store's session instead. */}
          {!sessionHere && canImpersonate && (
            <Form form={form} layout="vertical" style={{ maxWidth: 480 }} onFinish={onActAs}>
              {!noUsers && (
                <Form.Item
                  label="User's email"
                  name="email"
                  rules={[
                    { required: true, message: 'Enter the email of a user in this tenant' },
                    { type: 'email', message: 'Not an email address' },
                  ]}
                >
                  <Input />
                </Form.Item>
              )}
              <Form.Item
                label="Reason"
                name="reason"
                rules={[
                  { required: true, whitespace: true, message: 'Say why, for example the support ticket' },
                  { max: 200, message: 'At most 200 characters' },
                ]}
              >
                <Input.TextArea rows={2} maxLength={200} />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={opening} disabled={!!session}>
                {noUsers ? 'Set up as admin' : 'Act as'}
              </Button>
              {session && !sessionHere && (
                <Typography.Paragraph type="secondary" style={{ marginTop: token.marginXS }}>
                  Stop the session in {session.tenantName} first.
                </Typography.Paragraph>
              )}
            </Form>
          )}
          {bootstrapHere && (
            <Alert
              style={{ marginTop: token.marginSM }}
              type="success"
              showIcon
              message={
                <span>
                  Setup session open. <Link to={INVITATION_FORM_PATH}>Send the first admin&apos;s invitation</Link>.
                </span>
              }
            />
          )}
        </Card>
      )}
    </Space>
  );

  return (
    <Card>
      <Typography.Title level={3}>{tenant.name}</Typography.Title>
      <Tabs
        defaultActiveKey="overview"
        tabBarExtraContent={
          sessionHere ? null : <Typography.Text type="secondary">{AUDIT_HINT}</Typography.Text>
        }
        items={[
          { key: 'overview', label: 'Overview', children: overview },
          {
            key: 'audit',
            label: 'Audit',
            disabled: !sessionHere,
            children: <AuditTab enabled={sessionHere} />,
          },
        ]}
      />
    </Card>
  );
}
