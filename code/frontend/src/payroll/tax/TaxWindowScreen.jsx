import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Form,
  DatePicker,
  Select,
  Switch,
  InputNumber,
  Button,
  Typography,
  Alert,
  Spin,
  Space,
  Row,
  Col,
  Tag,
  message,
  Divider,
} from 'antd';
import { SaveOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { currentFy, fyOptions, formatFyDisplay } from './financialYear';
import { taxSettingsService } from './taxSettingsService';

const { Title, Text } = Typography;

export function TaxWindowScreen({ initialFy }) {
  const [selectedFy, setSelectedFy] = useState(initialFy || currentFy());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [settings, setSettings] = useState(null);
  const [form] = Form.useForm();

  const loadSettings = useCallback(async (fy) => {
    setLoading(true);
    setError(null);
    try {
      const data = await taxSettingsService.get(fy);
      setSettings(data);
      form.setFieldsValue({
        window_opens_on: data?.window_opens_on ? dayjs(data.window_opens_on) : null,
        window_closes_on: data?.window_closes_on ? dayjs(data.window_closes_on) : null,
        is_locked: Boolean(data?.is_locked),
        default_tax_regime: data?.default_tax_regime || 'NEW',
        can_change_tax_regime: data?.can_change_tax_regime !== false,
        pan_required_for_rent_over_threshold: data?.pan_required_for_rent_over_threshold ?? 100000,
      });
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load declaration settings');
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    loadSettings(selectedFy);
  }, [selectedFy, loadSettings]);

  const handleFyChange = (value) => {
    setSelectedFy(value);
  };

  const handleFinish = async (values) => {
    setSaving(true);
    setError(null);
    try {
      const payload = {
        window_opens_on: values.window_opens_on ? values.window_opens_on.format('YYYY-MM-DD') : null,
        window_closes_on: values.window_closes_on ? values.window_closes_on.format('YYYY-MM-DD') : null,
        is_locked: Boolean(values.is_locked),
        default_tax_regime: values.default_tax_regime,
        can_change_tax_regime: Boolean(values.can_change_tax_regime),
        pan_required_for_rent_over_threshold: Number(values.pan_required_for_rent_over_threshold),
      };
      const updated = await taxSettingsService.save(selectedFy, payload);
      setSettings(updated);
      message.success(`Tax declaration window settings saved for FY ${formatFyDisplay(selectedFy)}`);
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to save settings';
      setError(msg);
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  const isWindowActive = () => {
    if (!settings || settings.is_locked) return false;
    const today = dayjs();
    const opens = settings.window_opens_on ? dayjs(settings.window_opens_on) : null;
    const closes = settings.window_closes_on ? dayjs(settings.window_closes_on) : null;
    if (opens && today.isBefore(opens, 'day')) return false;
    if (closes && today.isAfter(closes, 'day')) return false;
    return true;
  };

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }}>
      <Card
        title={
          <Row justify="space-between" align="middle" wrap>
            <Col>
              <Space align="center">
                <SettingOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                <Title level={4} style={{ margin: 0 }}>
                  Tax Declaration Window Configuration
                </Title>
              </Space>
            </Col>
            <Col>
              <Space align="center">
                <Text strong>Financial Year:</Text>
                <Select
                  value={selectedFy}
                  onChange={handleFyChange}
                  options={fyOptions().map((opt) => ({
                    value: opt.value,
                    label: opt.label,
                  }))}
                  style={{ width: 140 }}
                  data-testid="fy-select"
                />
              </Space>
            </Col>
          </Row>
        }
        extra={
          settings && (
            <Tag color={settings.is_locked ? 'error' : isWindowActive() ? 'success' : 'warning'}>
              {settings.is_locked ? 'Locked' : isWindowActive() ? 'Window Open' : 'Window Inactive'}
            </Tag>
          )
        }
      >
        {error && (
          <Alert
            message="Error"
            description={error}
            type="error"
            showIcon
            closable
            onClose={() => setError(null)}
            style={{ marginBottom: 20 }}
          />
        )}

        <Spin spinning={loading}>
          <Form
            form={form}
            layout="vertical"
            onFinish={handleFinish}
            initialValues={{
              is_locked: false,
              default_tax_regime: 'NEW',
              can_change_tax_regime: true,
              pan_required_for_rent_over_threshold: 100000,
            }}
          >
            <Row gutter={[24, 0]}>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="window_opens_on"
                  label="Window Opens On"
                  extra="Employees can start submitting declarations from this date"
                >
                  <DatePicker
                    style={{ width: '100%' }}
                    format="YYYY-MM-DD"
                    placeholder="YYYY-MM-DD"
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="window_closes_on"
                  label="Window Closes On"
                  extra="Declaration window closes for employees after this date"
                >
                  <DatePicker
                    style={{ width: '100%' }}
                    format="YYYY-MM-DD"
                    placeholder="YYYY-MM-DD"
                  />
                </Form.Item>
              </Col>
            </Row>

            <Divider orientation="left">Policy & Default Regimes</Divider>

            <Row gutter={[24, 16]}>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="default_tax_regime"
                  label="Default Tax Regime"
                  rules={[{ required: true, message: 'Please select default tax regime' }]}
                  extra="Default regime assigned to employees who do not declare"
                >
                  <Select
                    options={[
                      { value: 'NEW', label: 'New Regime (Section 115BAC)' },
                      { value: 'OLD', label: 'Old Regime (with Deductions/Exemptions)' },
                    ]}
                  />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="pan_required_for_rent_over_threshold"
                  label="Landlord PAN Threshold (₹ / year)"
                  rules={[{ required: true, message: 'Please enter landlord PAN threshold' }]}
                  extra="Landlord PAN is mandatory if annual rent exceeds this limit"
                >
                  <InputNumber
                    min={0}
                    step={10000}
                    formatter={(val) => `₹ ${val}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                    parser={(val) => val.replace(/₹\s?|(,*)/g, '')}
                    style={{ width: '100%' }}
                  />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={[24, 16]}>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="can_change_tax_regime"
                  label="Allow Employees to Change Regime"
                  valuePropName="checked"
                  extra="Permit employees to choose between Old and New tax regimes"
                >
                  <Switch checkedChildren="Yes" unCheckedChildren="No" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  name="is_locked"
                  label="Lock Window Manually"
                  valuePropName="checked"
                  extra="When locked, no declarations can be edited or submitted regardless of dates"
                >
                  <Switch checkedChildren="Locked" unCheckedChildren="Unlocked" />
                </Form.Item>
              </Col>
            </Row>

            {settings?.updated_at && (
              <div style={{ marginTop: 8, marginBottom: 16 }}>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Last updated:{' '}
                  {dayjs(settings.updated_at).isValid()
                    ? dayjs(settings.updated_at).format('YYYY-MM-DD HH:mm')
                    : settings.updated_at}
                  {settings.updated_by ? ` by ${settings.updated_by}` : ''}
                </Text>
              </div>
            )}

            <Divider />

            <Row justify="end">
              <Space>
                <Button
                  icon={<ReloadOutlined />}
                  onClick={() => loadSettings(selectedFy)}
                  disabled={loading || saving}
                >
                  Reload
                </Button>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<SaveOutlined />}
                  loading={saving}
                  disabled={loading}
                >
                  Save Settings
                </Button>
              </Space>
            </Row>
          </Form>
        </Spin>
      </Card>
    </div>
  );
}

TaxWindowScreen.propTypes = {
  initialFy: PropTypes.string,
};

export default TaxWindowScreen;
