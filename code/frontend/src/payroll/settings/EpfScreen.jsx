import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Form,
  Input,
  InputNumber,
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
import { SafetyCertificateOutlined, SaveOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useDispatch } from 'react-redux';
import { statutoryService } from './statutoryService.js';
import { setEpf } from './settingsSlice.js';


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

export function EpfScreen() {
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
      const data = await statutoryService.get('epf');
      if (data) {
        dispatch(setEpf(data));
        setSource(data.source || null);
        setIsEnabled(!!data.is_enabled);
        form.setFieldsValue({
          is_enabled: !!data.is_enabled,
          registration_number: data.registration_number || '',
          registration_date: data.registration_date ? dayjs(data.registration_date) : null,
          deduction_cycle: data.deduction_cycle || 'MONTHLY',
          employee_rate: data.employee_rate != null ? String(data.employee_rate) : '',
          employer_rate: data.employer_rate != null ? String(data.employer_rate) : '',
          eps_rate: data.eps_rate != null ? String(data.eps_rate) : '',
          edli_rate: data.edli_rate != null ? String(data.edli_rate) : '',
          admin_charge_rate: data.admin_charge_rate != null ? String(data.admin_charge_rate) : '',
          wage_ceiling: data.wage_ceiling != null ? String(data.wage_ceiling) : '',
          restrict_employee_to_ceiling: !!data.restrict_employee_to_ceiling,
          restrict_employer_to_ceiling: !!data.restrict_employer_to_ceiling,
          prorate_restricted_wage: !!data.prorate_restricted_wage,
          consider_earned_wage: data.consider_earned_wage ?? true,
          eps_senior_age: data.eps_senior_age != null ? Number(data.eps_senior_age) : 58,
          include_employer_in_ctc: !!data.include_employer_in_ctc,
          include_edli_in_ctc: !!data.include_edli_in_ctc,
          include_admin_in_ctc: !!data.include_admin_in_ctc,
          include_employer_in_structure: !!data.include_employer_in_structure,
          include_edli_in_structure: !!data.include_edli_in_structure,
          include_admin_in_structure: !!data.include_admin_in_structure,
          abry_scheme: !!data.abry_scheme,
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
        eps_rate: String(values.eps_rate),
        edli_rate: String(values.edli_rate),
        admin_charge_rate: String(values.admin_charge_rate),
        wage_ceiling: String(values.wage_ceiling),
        restrict_employee_to_ceiling: !!values.restrict_employee_to_ceiling,
        restrict_employer_to_ceiling: !!values.restrict_employer_to_ceiling,
        prorate_restricted_wage: !!values.prorate_restricted_wage,
        consider_earned_wage: !!values.consider_earned_wage,
        eps_senior_age: Number(values.eps_senior_age),
        include_employer_in_ctc: !!values.include_employer_in_ctc,
        include_edli_in_ctc: !!values.include_edli_in_ctc,
        include_admin_in_ctc: !!values.include_admin_in_ctc,
        include_employer_in_structure: !!values.include_employer_in_structure,
        include_edli_in_structure: !!values.include_edli_in_structure,
        include_admin_in_structure: !!values.include_admin_in_structure,
        abry_scheme: !!values.abry_scheme,
      };

      const updated = await statutoryService.save('epf', payload);
      dispatch(setEpf(updated));
      setSource(updated.source || null);
      message.success('EPF settings saved successfully');
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to save EPF settings');
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
          <SafetyCertificateOutlined style={{ color: token.colorPrimary }} />
          <span>Employees&apos; Provident Fund (EPF) Settings</span>
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
              label="Enable EPF"
              valuePropName="checked"
            >
              <Switch data-testid="epf-enabled-switch" />
            </Form.Item>
          </Col>

          <Col xs={24} md={8}>
            <Form.Item
              name="registration_number"
              label="EPF Registration Number"
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
              <Input placeholder="e.g. MH/BAN/0012345/000" data-testid="epf-reg-number-input" />
            </Form.Item>
          </Col>

          <Col xs={24} md={8}>
            <Form.Item name="registration_date" label="Registration Date">
              <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" placeholder="YYYY-MM-DD" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={24}>
          <Col xs={24} md={8}>
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

          <Col xs={24} md={8}>
            <Form.Item
              name="wage_ceiling"
              label="EPF Wage Ceiling (₹)"
              rules={[{ validator: validatePositiveMoney }]}
              extra="Statutory gross limit for mandatory contribution"
            >
              <Input placeholder="Ceiling amount" data-testid="epf-wage-ceiling-input" />
            </Form.Item>
          </Col>

          <Col xs={24} md={8}>
            <Form.Item
              name="eps_senior_age"
              label="EPS Senior Category Age"
              rules={[
                { required: true, message: 'Senior age is required' },
                {
                  type: 'number',
                  min: 50,
                  max: 70,
                  message: 'Senior age must be between 50 and 70',
                },
              ]}
              extra="Age above which EPS contribution stops"
            >
              <InputNumber min={50} max={70} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left">Contribution Rates (%)</Divider>

        <Row gutter={24}>
          <Col xs={24} md={8}>
            <Form.Item
              name="employee_rate"
              label="Employee Contribution Rate (%)"
              rules={[{ validator: validateRate4Decimals }]}
            >
              <Input placeholder="e.g. 12.0000" data-testid="epf-employee-rate-input" />
            </Form.Item>
          </Col>

          <Col xs={24} md={8}>
            <Form.Item
              name="employer_rate"
              label="Employer Total Contribution Rate (%)"
              rules={[{ validator: validateRate4Decimals }]}
            >
              <Input placeholder="e.g. 12.0000" data-testid="epf-employer-rate-input" />
            </Form.Item>
          </Col>

          <Col xs={24} md={8}>
            <Form.Item
              name="eps_rate"
              label="EPS Share of Employer Rate (%)"
              rules={[
                { validator: validateRate4Decimals },
                ({ getFieldValue }) => ({
                  validator(_, val) {
                    const employer = parseFloat(getFieldValue('employer_rate'));
                    const eps = parseFloat(val);
                    if (!isNaN(employer) && !isNaN(eps) && eps > employer) {
                      return Promise.reject(new Error('EPS rate cannot exceed Employer rate'));
                    }
                    return Promise.resolve();
                  },
                }),
              ]}
              extra="Portion routed to Pension Scheme"
            >
              <Input placeholder="e.g. 5.0000" data-testid="epf-eps-rate-input" />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={24}>
          <Col xs={24} md={12}>
            <Form.Item
              name="edli_rate"
              label="EDLI Contribution Rate (%)"
              rules={[{ validator: validateRate4Decimals }]}
            >
              <Input placeholder="e.g. 0.5000" data-testid="epf-edli-rate-input" />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="admin_charge_rate"
              label="EPF Admin Charges Rate (%)"
              rules={[{ validator: validateRate4Decimals }]}
            >
              <Input placeholder="e.g. 0.5000" data-testid="epf-admin-rate-input" />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left">Restriction & Wage Rules</Divider>

        <Row gutter={24}>
          <Col xs={24} md={12}>
            <Form.Item
              name="restrict_employee_to_ceiling"
              label="Restrict Employee Share to Wage Ceiling"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="restrict_employer_to_ceiling"
              label="Restrict Employer Share to Wage Ceiling"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="prorate_restricted_wage"
              label="Prorate Restricted Wage on LOP"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="consider_earned_wage"
              label="Consider Earned Salary for EPF Calculation"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left">CTC & Structure Inclusion</Divider>

        <Row gutter={24}>
          <Col xs={24} md={12}>
            <Form.Item
              name="include_employer_in_ctc"
              label="Include Employer EPF Share in CTC"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="include_edli_in_ctc"
              label="Include EDLI contribution in CTC"
              valuePropName="checked"
            >
              <Switch data-testid="epf-include-edli-in-ctc" />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="include_admin_in_ctc"
              label="Include EPF admin charges in CTC"
              valuePropName="checked"
            >
              <Switch data-testid="epf-include-admin-in-ctc" />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="include_employer_in_structure"
              label="Include Employer Share in Salary Structure"
              valuePropName="checked"
            >
              <Switch />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="include_edli_in_structure"
              label="Include EDLI contribution in salary structure"
              valuePropName="checked"
            >
              <Switch data-testid="epf-include-edli-in-structure" />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="include_admin_in_structure"
              label="Include EPF admin charges in salary structure"
              valuePropName="checked"
            >
              <Switch data-testid="epf-include-admin-in-structure" />
            </Form.Item>
          </Col>

          <Col xs={24} md={12}>
            <Form.Item
              name="abry_scheme"
              label="Eligible for ABRY Scheme"
              valuePropName="checked"
              extra="Atmanirbhar Bharat Rojgar Yojana subsidy"
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
            data-testid="save-epf-button"
          >
            Save EPF Settings
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
}

export default EpfScreen;
