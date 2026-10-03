import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Form,
  Input,
  DatePicker,
  Button,
  Switch,
  Select,
  Alert,
  Spin,
  Space,
  Row,
  Col,
  Divider,
  message,
  theme,
} from 'antd';
import { MedicineBoxOutlined, SaveOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useDispatch } from 'react-redux';
import { statutoryService } from './statutoryService.js';
import { setEsi } from './settingsSlice.js';

const validateRate4Decimals = (_, value) => {
  if (value === undefined || value === null || value === '') {
    return Promise.reject(new Error('Rate is required'));
  }
  const str = String(value).trim();
  const match = str.match(/^\d+(\.\d+)?$/);
  if (!match) {
    return Promise.reject(new Error('Must be a valid decimal number'));
  }
  const parts = str.split('.');
  if (parts.length > 1 && parts[1].length > 4) {
    return Promise.reject(new Error('Rate can have at most 4 decimal places'));
  }
  const num = parseFloat(str);
  if (num < 0 || num > 100) {
    return Promise.reject(new Error('Rate must be between 0 and 100'));
  }
  return Promise.resolve();
};

const validatePositiveMoney = (_, value) => {
  if (value === undefined || value === null || value === '') {
    return Promise.reject(new Error('Wage ceiling is required'));
  }
  const str = String(value).trim();
  const match = str.match(/^\d+(\.\d+)?$/);
  if (!match) {
    return Promise.reject(new Error('Must be a valid positive amount'));
  }
  const parts = str.split('.');
  if (parts.length > 1 && parts[1].length > 4) {
    return Promise.reject(new Error('Ceiling can have at most 4 decimal places'));
  }
  if (parseFloat(str) <= 0) {
    return Promise.reject(new Error('Wage ceiling must be greater than 0'));
  }
  return Promise.resolve();
};

export function EsiScreen() {
  const dispatch = useDispatch();
  const { token } = theme.useToken();
  const [form] = Form.useForm();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [source, setSource] = useState(null);
  const [isEnabled, setIsEnabled] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await statutoryService.get('esi');
      if (data) {
        dispatch(setEsi(data));
        setSource(data.source || null);
        setIsEnabled(!!data.is_enabled);
        form.setFieldsValue({
          is_enabled: !!data.is_enabled,
          registration_number: data.registration_number || '',
          registration_date: data.registration_date ? dayjs(data.registration_date) : null,
          deduction_cycle: data.deduction_cycle || 'MONTHLY',
          employee_rate: data.employee_rate != null ? String(data.employee_rate) : '',
          employer_rate: data.employer_rate != null ? String(data.employer_rate) : '',
          wage_ceiling: data.wage_ceiling != null ? String(data.wage_ceiling) : '',
          include_employer_in_ctc: !!data.include_employer_in_ctc,
          include_in_structure: !!data.include_in_structure,
        });
      }
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, [dispatch, form]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleFinish = async (values) => {
    setSaving(true);
    try {
      const payload = {
        is_enabled: !!values.is_enabled,
        registration_number: values.registration_number ? values.registration_number.trim() : null,
        registration_date: values.registration_date
          ? (typeof values.registration_date === 'string' ? values.registration_date : values.registration_date.format('YYYY-MM-DD'))
          : null,
        deduction_cycle: values.deduction_cycle || 'MONTHLY',
        employee_rate: String(values.employee_rate),
        employer_rate: String(values.employer_rate),
        wage_ceiling: String(values.wage_ceiling),
        include_employer_in_ctc: !!values.include_employer_in_ctc,
        include_in_structure: !!values.include_in_structure,
      };

      const updated = await statutoryService.save('esi', payload);
      dispatch(setEsi(updated));
      setSource(updated.source || null);
      message.success('ESI settings saved successfully');
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to save ESI settings');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <Card
      title={
        <Space>
          <MedicineBoxOutlined style={{ color: token.colorPrimary }} />
          <span>Employees&apos; State Insurance (ESI) Settings</span>
        </Space>
      }
    >
      {source === 'DEFAULT' && (
        <Alert
          type="warning"
          showIcon
          message="defaults, not yet saved"
          style={{ marginBottom: 24 }}
          data-testid="default-source-banner"
        />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        onValuesChange={(changed) => {
          if ('is_enabled' in changed) {
            setIsEnabled(!!changed.is_enabled);
          }
        }}
      >
        <Row gutter={24} align="middle">
          <Col xs={24} md={8}>
            <Form.Item
              name="is_enabled"
              label="Enable ESI"
              valuePropName="checked"
            >
              <Switch data-testid="esi-enabled-switch" />
            </Form.Item>
          </Col>

          <Col xs={24} md={8}>
            <Form.Item
              name="registration_number"
              label="ESI Registration Number"
              rules={[
                {
                  validator: (_, val) => {
                    if (isEnabled && (!val || !val.trim())) {
                      return Promise.reject(new Error('Registration number is required when enabled'));
                    }
                    if (val && val.length > 32) {
                      return Promise.reject(new Error('Max 32 characters'));
                    }
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <Input placeholder="e.g. 31000123450000001" data-testid="esi-reg-number-input" />
            </Form.Item>
          </Col>

          <Col xs={24} md={8}>
            <Form.Item name="registration_date" label="Registration Date">
              <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" placeholder="YYYY-MM-DD" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={24}>
          <Col xs={24} md={12}>
            <Form.Item
              name="deduction_cycle"
              label="Deduction Cycle"
              rules={[{ required: true, message: 'Deduction cycle is required' }]}
            >
              <Select
                options={[{ label: 'Monthly', value: 'MONTHLY' }]}
                disabled
              />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="wage_ceiling"
              label="ESI Wage Ceiling (₹)"
              rules={[{ validator: validatePositiveMoney }]}
              extra="Gross wage limit for ESI coverage"
            >
              <Input placeholder="Ceiling amount" data-testid="esi-wage-ceiling-input" />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left">Contribution Rates (%)</Divider>

        <Row gutter={24}>
          <Col xs={24} md={12}>
            <Form.Item
              name="employee_rate"
              label="Employee Contribution Rate (%)"
              rules={[{ validator: validateRate4Decimals }]}
            >
              <Input placeholder="e.g. 0.7500" data-testid="esi-employee-rate-input" />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="employer_rate"
              label="Employer Contribution Rate (%)"
              rules={[{ validator: validateRate4Decimals }]}
            >
              <Input placeholder="e.g. 3.2500" data-testid="esi-employer-rate-input" />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left">CTC & Structure Inclusion</Divider>

        <Row gutter={24}>
          <Col xs={24} md={12}>
            <Form.Item
              name="include_employer_in_ctc"
              label="Include Employer ESI Share in CTC"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="include_in_structure"
              label="Include Employer Share in Salary Structure"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item style={{ marginTop: 16 }}>
          <Button
            type="primary"
            htmlType="submit"
            icon={<SaveOutlined />}
            loading={saving}
            data-testid="save-esi-button"
          >
            Save ESI Settings
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
}

export default EsiScreen;
