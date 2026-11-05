import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import {
  Card, Descriptions, Switch, Select, Form, Input, Button, Tabs, Tag, Typography, Space, Skeleton,
  Modal, Alert, InputNumber, theme,
} from 'antd';
import { NotFound, useCan } from '@shell/screens';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { LoadError } from '@shared/ui/LoadError.jsx';
import { tenantService } from './tenantService.js';
import { impersonationService } from './impersonationService.js';
import { started } from './impersonationSlice.js';
import { AuditTab, AUDIT_HINT } from './AuditTab.jsx';
import {
  isPlatformTenant, MODULES, STATUSES, STATUS_COLORS, PLANS, PLAN_LABELS, SUPPORT_PLANS,
  SUPPORT_PLAN_LABELS, employeesText,
} from './platform.js';
import { applyResultText } from './countryTemplates.js';
import { formatDate, formatDateTime, statusLabel } from '@shared/ui/format.js';

/** The user invitation form: the Invitations tab of Users & access (`core.users`, W-73.4). */
export const INVITATION_FORM_PATH = '/users?tab=invitations';

/** Statuses that lock a tenant out of its modules, so they ask first. */
const LOCKING_STATUSES = ['SUSPENDED', 'CANCELLED'];

/**
 * One tenant, for platform staff (W-65.3 §5): overview, module switches, plan and employee limit
 * (W-84.1), status, "act as", audit.
 *
 * Reads `GET /api/v1/tenants/{id}` (`TenantOverview`, snake_case, bare). Revoking a module and
 * suspending ask first: the tenant's data in a revoked module goes read-only (W-12-1 §13 row 1)
 * and a suspended tenant is locked out of every module endpoint. A limit below the current headcount
 * asks first too: nobody is removed, but adding or reactivating employees is blocked (W-84.2).
 *
 * "Act as" asks for an email, resolved server-side, because a customer's users cannot be listed
 * before a session exists (§5 note, §13 decision 2). A tenant with no user accounts gets a
 * bootstrap session instead, after which staff send the first admin's invitation.
 */
export function TenantDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { token } = theme.useToken();
  const session = useSelector((state) => state.impersonation?.session ?? null);
  const canImpersonate = useCan('core.tenant.impersonate');

  const [tenant, setTenant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  // Any failure but a 404 is kept whole and shown with Retry, never as "not found" (D-85).
  const [loadError, setLoadError] = useState(null);
  const shown = useRef(false);
  const [busy, setBusy] = useState(false);
  const [opening, setOpening] = useState(false);
  const [bootstrapped, setBootstrapped] = useState(false);
  const [applyingTemplate, setApplyingTemplate] = useState(false);
  const [templateResult, setTemplateResult] = useState(null);
  const [form] = Form.useForm();
  // The Plan card's draft, reset from the overview on every load (W-84.1).
  const [planDraft, setPlanDraft] = useState({ plan: null, supportPlan: null, employeeLimit: null });

  const load = useCallback(async () => {
    try {
      const res = await tenantService.get(id);
      setTenant(res);
      setPlanDraft({
        plan: res?.plan ?? null,
        supportPlan: res?.support_plan ?? null,
        employeeLimit: res?.employee_limit ?? null,
      });
      shown.current = true;
      setMissing(false);
      setLoadError(null);
    } catch (err) {
      if (err?.status === 404 || err?.code === 'NOT_FOUND') setMissing(true);
      else if (shown.current) errorMsg(err);
      else setLoadError(err || new Error('Could not load the tenant'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const retry = () => {
    setLoading(true);
    setLoadError(null);
    load();
  };

  if (loading) return <Skeleton active />;
  if (missing) return <NotFound />;
  if (!tenant) return <LoadError title="Could not load the tenant" error={loadError} onRetry={retry} />;

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

  const employeeCount = Number(tenant.employee_count ?? 0);
  const hasLimit = typeof tenant.employee_limit === 'number';
  const overSavedLimit = hasLimit && employeeCount > tenant.employee_limit;

  const applyPlan = async ({ plan, supportPlan, employeeLimit }) => {
    setBusy(true);
    try {
      await tenantService.setPlan(tenant.tenant_id, {
        plan: plan || null,
        supportPlan: supportPlan || null,
        employeeLimit: employeeLimit ?? null,
      });
      await load();
    } catch (err) {
      errorMsg(err);
    } finally {
      setBusy(false);
    }
  };

  const onSavePlan = () => {
    const draft = planDraft;
    const limit = draft.employeeLimit;
    if (typeof limit === 'number' && limit < employeeCount) {
      Modal.confirm({
        title: 'Set the limit below current headcount?',
        content: `${tenant.name} has ${employeeCount} employees. Nobody is removed; adding or reactivating an employee is blocked until the count falls below ${limit}.`,
        okText: 'Set limit',
        onOk: () => applyPlan(draft),
      });
      return;
    }
    applyPlan(draft);
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
          <Tag color={STATUS_COLORS[tenant.status] || 'default'}>{statusLabel(tenant.status)}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Created">
          {formatDateTime(tenant.created_at)}
        </Descriptions.Item>
        <Descriptions.Item label="Current period ends">{formatDate(tenant.current_period_end)}</Descriptions.Item>
        <Descriptions.Item label="User accounts">{tenant.user_count}</Descriptions.Item>
        <Descriptions.Item label="Employees">
          {employeesText(tenant.employee_count, tenant.employee_limit)}
        </Descriptions.Item>
      </Descriptions>

      {!platform && (
        <Card size="small" variant="borderless" title="Modules">
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
        <Card size="small" variant="borderless" title="Plan">
          <Form layout="vertical" style={{ maxWidth: 480 }}>
            <Form.Item label="Plan">
              <Select
                aria-label="Plan"
                allowClear
                placeholder="No plan"
                value={planDraft.plan ?? undefined}
                disabled={busy}
                onChange={(v) => setPlanDraft((d) => ({ ...d, plan: v ?? null }))}
                options={PLANS.map((p) => ({ value: p, label: PLAN_LABELS[p] }))}
              />
            </Form.Item>
            <Form.Item label="Support plan">
              <Select
                aria-label="Support plan"
                allowClear
                placeholder="No support plan"
                value={planDraft.supportPlan ?? undefined}
                disabled={busy}
                onChange={(v) => setPlanDraft((d) => ({ ...d, supportPlan: v ?? null }))}
                options={SUPPORT_PLANS.map((p) => ({ value: p, label: SUPPORT_PLAN_LABELS[p] }))}
              />
            </Form.Item>
            <Form.Item label="Employee limit">
              <InputNumber
                aria-label="Employee limit"
                min={1}
                precision={0}
                placeholder="No limit"
                style={{ width: '100%' }}
                value={planDraft.employeeLimit}
                disabled={busy}
                onChange={(v) =>
                  setPlanDraft((d) => ({ ...d, employeeLimit: typeof v === 'number' ? v : null }))
                }
              />
            </Form.Item>
            <Space direction="vertical">
              <Typography.Text type={overSavedLimit ? 'danger' : undefined}>
                {hasLimit
                  ? `Employees: ${employeeCount} of ${tenant.employee_limit}`
                  : `Employees: ${employeeCount}, no limit`}
              </Typography.Text>
              <Button type="primary" id="btn-save-plan" disabled={busy} onClick={onSavePlan}>
                Save plan
              </Button>
            </Space>
          </Form>
        </Card>
      )}

      {!platform && (
        <Card size="small" variant="borderless" title="Status">
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
        <Card size="small" variant="borderless" title="Country template">
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
        <Card size="small" variant="borderless" title="Act as">
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
    <Card variant="borderless">
      <PageHeader
        title={tenant.name}
        onBack={() => navigate('/admin/tenants')}
        backLabel="Tenants"
      />
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
