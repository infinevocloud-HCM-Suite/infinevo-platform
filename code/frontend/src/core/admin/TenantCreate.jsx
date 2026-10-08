import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Select, Checkbox, Button, Card, Space, Typography } from 'antd';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { tenantService } from './tenantService.js';
import { MODULES } from './platform.js';

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/** True when the browser knows the IANA zone; the server checks it again with `ZoneId.of`. */
export function isTimeZone(value) {
  try {
    new Intl.DateTimeFormat('en', { timeZone: value });
    return true;
  } catch {
    return false;
  }
}

/**
 * Provision a tenant without `psql` (W-65.3 §5, `09-build-order.md:297`).
 *
 * Field rules mirror `W-12-1` §4 and `TenantServiceImpl.provisionTenant`: name required;
 * country an ISO 3166-1 alpha-2 code; timezone an IANA zone name, at most 64 characters;
 * leave year start month 1-12; modules any of HRMS and PAYROLL.
 *
 * Administrator email is optional (D-42). When given, the server invites that address into the
 * new tenant as its `tenant-admin`, so nobody has to act as the tenant to invite its first admin.
 * Sent as `admin_email`, snake_case like the other `TenantRequest` fields.
 */
export function TenantCreate() {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  const onFinish = async (values) => {
    setSaving(true);
    const adminEmail = (values.admin_email || '').trim();
    const body = {
      name: values.name.trim(),
      country_code: values.country_code.trim().toUpperCase(),
      timezone: values.timezone.trim(),
      leave_year_start_month: values.leave_year_start_month,
      modules: values.modules || [],
    };
    if (adminEmail) body.admin_email = adminEmail;
    try {
      await tenantService.create(body);
      await successMsg(
        'Tenant created',
        adminEmail
          ? `${body.name} is ready. An invitation was sent to ${adminEmail}.`
          : `${body.name} is ready.`,
      );
      navigate('/admin/tenants');
    } catch (err) {
      errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <Typography.Title level={3}>New tenant</Typography.Title>
      <Form
        form={form}
        layout="vertical"
        style={{ maxWidth: 480 }}
        initialValues={{
          country_code: 'IN',
          timezone: 'Asia/Kolkata',
          leave_year_start_month: 4,
          modules: [],
        }}
        onFinish={onFinish}
      >
        <Form.Item
          label="Name"
          name="name"
          rules={[{ required: true, whitespace: true, message: 'Name is required' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          label="Country"
          name="country_code"
          rules={[
            { required: true, message: 'Country is required' },
            { pattern: /^\s*[A-Za-z]{2}\s*$/, message: 'Two-letter ISO 3166-1 code, for example IN' },
          ]}
        >
          <Input maxLength={2} />
        </Form.Item>
        <Form.Item
          label="Timezone"
          name="timezone"
          rules={[
            { required: true, message: 'Timezone is required' },
            { max: 64, message: 'At most 64 characters' },
            {
              validator: (_, value) =>
                !value || isTimeZone(value.trim())
                  ? Promise.resolve()
                  : Promise.reject(new Error('Not a known IANA timezone, for example Asia/Kolkata')),
            },
          ]}
        >
          <Input />
        </Form.Item>
        <Form.Item
          label="Leave year starts in"
          name="leave_year_start_month"
          rules={[{ required: true, message: 'Pick a month' }]}
        >
          <Select options={MONTHS.map((label, i) => ({ value: i + 1, label }))} />
        </Form.Item>
        <Form.Item
          label="Administrator email"
          name="admin_email"
          extra="Optional. This person is invited as the tenant's administrator."
          rules={[
            { type: 'email', transform: (v) => (v || '').trim(), message: 'Enter a valid email address' },
            { max: 255, message: 'At most 255 characters' },
          ]}
        >
          <Input type="email" autoComplete="off" />
        </Form.Item>
        <Form.Item label="Modules" name="modules">
          <Checkbox.Group options={MODULES.map((m) => ({ value: m, label: m }))} />
        </Form.Item>
        <Space>
          <Button type="primary" htmlType="submit" loading={saving}>
            Create tenant
          </Button>
          <Button onClick={() => navigate('/admin/tenants')}>Cancel</Button>
        </Space>
      </Form>
    </Card>
  );
}
