import { useEffect, useState } from 'react';
import { Card, Form, Input, Select, Button, Alert, Spin, Space, Typography, Divider, Row, Col, message, theme } from 'antd';
import { AuditOutlined, SaveOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { form24QService } from '../form24q/form24QService.js';

const { Text } = Typography;

/** Protean's deductor categories (Form 138 batch header field 32). */
export const DEDUCTOR_TYPES = [
  { value: 'K', label: 'Company' },
  { value: 'M', label: 'Branch / division of a company' },
  { value: 'F', label: 'Firm' },
  { value: 'P', label: 'Association of persons (AOP)' },
  { value: 'T', label: 'Association of persons (trust)' },
  { value: 'B', label: 'Body of individuals' },
  { value: 'Q', label: 'Individual / HUF' },
  { value: 'J', label: 'Artificial juridical person' },
  { value: 'A', label: 'Central Government' },
  { value: 'S', label: 'State Government' },
  { value: 'D', label: 'Statutory body (Central Govt.)' },
  { value: 'E', label: 'Statutory body (State Govt.)' },
  { value: 'G', label: 'Autonomous body (Central Govt.)' },
  { value: 'H', label: 'Autonomous body (State Govt.)' },
  { value: 'L', label: 'Local authority (Central Govt.)' },
  { value: 'N', label: 'Local authority (State Govt.)' },
];

const FIELDS = [
  'tan',
  'pan',
  'tds_circle',
  'signatory_name',
  'signatory_parent_name',
  'signatory_designation',
  'deductor_type',
  'email',
  'phone',
  'gstin',
  'responsible_pan',
  'responsible_email',
  'responsible_phone',
];

const upper = (e) => e?.target?.value?.toUpperCase();

/** The request body, from the form's values: trimmed, empty as null, signatory employee kept. */
export function toDeductorRequest(values, current) {
  const body = { signatory_employee_id: current?.signatory_employee_id ?? null };
  FIELDS.forEach((f) => {
    const v = typeof values[f] === 'string' ? values[f].trim() : values[f];
    body[f] = v === '' || v === undefined ? null : v;
  });
  return body;
}

/**
 * Payroll › Settings › TDS deductor (W-89): the TAN, PAN and signatory that Form 16 prints, and the contact
 * details the quarterly TDS return file needs. The address is the work location marked as the filing address.
 */
export function TdsDeductorSettings() {
  const { token } = theme.useToken();
  const canManage = useCan('payroll.settings.manage');
  const [form] = Form.useForm();
  const [current, setCurrent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const found = await form24QService.getDeductor();
        if (cancelled) return;
        setCurrent(found);
        if (found) form.setFieldsValue(Object.fromEntries(FIELDS.map((f) => [f, found[f] ?? undefined])));
      } catch (err) {
        if (!cancelled) setError(err?.message || 'Failed to load the deductor details');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [form]);

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    setError(null);
    try {
      const saved = await form24QService.saveDeductor(toDeductorRequest(values, current));
      setCurrent(saved);
      message.success('Deductor details saved');
    } catch (err) {
      setError(err?.message || 'Failed to save the deductor details');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <Spin />;

  return (
    <Card
      variant="borderless"
      title={
        <Space>
          <AuditOutlined style={{ color: token.colorPrimary }} />
          <span>TDS deductor</span>
        </Space>
      }
      extra={
        canManage && (
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={save} data-testid="btn-save-deductor">
            Save
          </Button>
        )
      }
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Form 16 prints the TAN, PAN and signatory. The quarterly TDS return also needs the contact details below; the
        address comes from the work location marked as the filing address.
      </Text>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      <Form form={form} layout="vertical" disabled={!canManage}>
        <Row gutter={16}>
          <Col xs={24} md={8}>
            <Form.Item
              name="tan"
              label="TAN"
              getValueFromEvent={upper}
              rules={[{ required: true, pattern: /^[A-Z]{4}[0-9]{5}[A-Z]$/, message: 'TAN looks like ABCD12345E' }]}
            >
              <Input aria-label="TAN" maxLength={10} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="pan"
              label="PAN of the company"
              getValueFromEvent={upper}
              rules={[{ required: true, pattern: /^[A-Z]{5}[0-9]{4}[A-Z]$/, message: 'PAN looks like ABCDE1234F' }]}
            >
              <Input aria-label="PAN of the company" maxLength={10} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="tds_circle" label="TDS circle">
              <Input aria-label="TDS circle" maxLength={13} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="deductor_type" label="Deductor type">
              <Select aria-label="Deductor type" allowClear options={DEDUCTOR_TYPES} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="email" label="Company email" rules={[{ type: 'email', message: 'Enter a valid email' }]}>
              <Input aria-label="Company email" maxLength={75} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="phone" label="Company phone" rules={[{ pattern: /^[0-9]{6,10}$/, message: '6 to 10 digits' }]}>
              <Input aria-label="Company phone" maxLength={10} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="gstin"
              label="GSTIN (optional)"
              getValueFromEvent={upper}
              rules={[{ pattern: /^[0-9A-Z]{15}$/, message: '15 letters and digits' }]}
            >
              <Input aria-label="GSTIN" maxLength={15} />
            </Form.Item>
          </Col>
        </Row>
        <Divider orientation="left">Person responsible (signatory)</Divider>
        <Row gutter={16}>
          <Col xs={24} md={8}>
            <Form.Item name="signatory_name" label="Name" rules={[{ required: true, message: 'Required' }]}>
              <Input aria-label="Signatory name" maxLength={120} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="signatory_parent_name" label="Father's / parent's name">
              <Input aria-label="Signatory parent name" maxLength={120} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="signatory_designation" label="Designation" rules={[{ required: true, message: 'Required' }]}>
              <Input aria-label="Signatory designation" maxLength={120} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="responsible_pan"
              label="PAN"
              getValueFromEvent={upper}
              rules={[{ pattern: /^[A-Z]{5}[0-9]{4}[A-Z]$/, message: 'PAN looks like ABCDE1234F' }]}
            >
              <Input aria-label="Signatory PAN" maxLength={10} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item name="responsible_email" label="Email" rules={[{ type: 'email', message: 'Enter a valid email' }]}>
              <Input aria-label="Signatory email" maxLength={75} />
            </Form.Item>
          </Col>
          <Col xs={24} md={8}>
            <Form.Item
              name="responsible_phone"
              label="Mobile"
              rules={[{ pattern: /^[0-9]{6,10}$/, message: '6 to 10 digits' }]}
            >
              <Input aria-label="Signatory mobile" maxLength={10} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Card>
  );
}
