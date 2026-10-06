import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Form,
  InputNumber,
  Select,
  Switch,
  DatePicker,
  Button,
  Space,
  Row,
  Col,
  Divider,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { leaveTypeService } from './leaveTypeService.js';
import { departmentService } from '../org/departmentService.js';
import { designationService } from '../org/designationService.js';
import { workLocationService } from '../org/workLocationService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Text } = Typography;

export function PolicyForm({ leaveType, onSuccess, onCancel }) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [exceedMode, setExceedMode] = useState(leaveType?.policy?.exceedBalanceMode || 'NO_LIMIT');
  const [accrualOn, setAccrualOn] = useState(Boolean(leaveType?.policy?.accrualEnabled));
  const [carryForwardOn, setCarryForwardOn] = useState(Boolean(leaveType?.policy?.carryForwardEnabled));

  const [departments, setDepartments] = useState([]);
  const [designations, setDesignations] = useState([]);
  const [locations, setLocations] = useState([]);

  useEffect(() => {
    departmentService.list(false).then((data) => setDepartments(Array.isArray(data) ? data : [])).catch(() => {});
    designationService.list(false).then((data) => setDesignations(Array.isArray(data) ? data : [])).catch(() => {});
    workLocationService.list(false).then((data) => setLocations(Array.isArray(data) ? data : [])).catch(() => {});
  }, []);

  useEffect(() => {
    if (leaveType?.policy) {
      const p = leaveType.policy;
      const deptIds = (p.eligibility || [])
        .filter((e) => (e.dimension || '').toLowerCase() === 'department')
        .map((e) => e.valueId);
      const desigIds = (p.eligibility || [])
        .filter((e) => (e.dimension || '').toLowerCase() === 'designation')
        .map((e) => e.valueId);
      const locIds = (p.eligibility || [])
        .filter((e) => ['location', 'work_location'].includes((e.dimension || '').toLowerCase()))
        .map((e) => e.valueId);

      setExceedMode(p.exceedBalanceMode || 'NO_LIMIT');
      setAccrualOn(Boolean(p.accrualEnabled));
      setCarryForwardOn(Boolean(p.carryForwardEnabled));

      form.setFieldsValue({
        annualDays: p.annualDays,
        effectiveFrom: p.effectiveFrom ? dayjs(p.effectiveFrom) : dayjs().startOf('year'),
        accrualEnabled: p.accrualEnabled,
        accrualFrequency: p.accrualFrequency || 'MONTHLY',
        accrualUnits: p.accrualUnits,
        resetEnabled: p.resetEnabled,
        resetFrequency: p.resetFrequency || 'YEARLY',
        carryForwardEnabled: p.carryForwardEnabled,
        carryForwardCap: p.carryForwardCap,
        carryForwardExpiresAfterMonths: p.carryForwardExpiresAfterMonths,
        exceedBalanceMode: p.exceedBalanceMode || 'NO_LIMIT',
        exceedBalanceLimitDays: p.exceedBalanceLimitDays,
        gender: p.gender || 'ALL',
        departmentIds: deptIds,
        designationIds: desigIds,
        locationIds: locIds,
      });
    } else {
      setExceedMode('NO_LIMIT');
      form.setFieldsValue({
        annualDays: 12,
        effectiveFrom: dayjs().startOf('year'),
        accrualEnabled: false,
        accrualFrequency: 'MONTHLY',
        resetEnabled: true,
        resetFrequency: 'YEARLY',
        carryForwardEnabled: false,
        exceedBalanceMode: 'NO_LIMIT',
        gender: 'ALL',
        departmentIds: [],
        designationIds: [],
        locationIds: [],
      });
    }
  }, [leaveType, form]);

  const handleSubmit = useCallback(
    async (values) => {
      setSaving(true);
      try {
        const eligibility = [
          ...(values.departmentIds || []).map((id) => ({ dimension: 'department', valueId: id })),
          ...(values.designationIds || []).map((id) => ({ dimension: 'designation', valueId: id })),
          ...(values.locationIds || []).map((id) => ({ dimension: 'work_location', valueId: id })),
        ];

        const payload = {
          annualDays: values.annualDays,
          effectiveFrom: values.effectiveFrom ? values.effectiveFrom.format('YYYY-MM-DD') : null,
          accrualEnabled: Boolean(values.accrualEnabled),
          accrualFrequency: values.accrualEnabled ? values.accrualFrequency : null,
          accrualUnits: values.accrualUnits || null,
          resetEnabled: Boolean(values.resetEnabled),
          resetFrequency: values.resetEnabled ? values.resetFrequency : null,
          carryForwardEnabled: Boolean(values.carryForwardEnabled),
          carryForwardCap: values.carryForwardEnabled ? values.carryForwardCap : null,
          carryForwardExpiresAfterMonths: values.carryForwardEnabled
            ? values.carryForwardExpiresAfterMonths
            : null,
          exceedBalanceMode: values.exceedBalanceMode,
          exceedBalanceLimitDays:
            values.exceedBalanceMode === 'YEAR_END_LIMIT' ? values.exceedBalanceLimitDays : null,
          gender: values.gender && values.gender !== 'ALL' ? values.gender : null,
          eligibility,
        };

        await leaveTypeService.savePolicy(leaveType.id, payload);
        await successMsg('Policy saved successfully');
        if (onSuccess) onSuccess();
      } catch (err) {
        await errorMsg(err);
      } finally {
        setSaving(false);
      }
    },
    [leaveType, onSuccess]
  );

  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={handleSubmit}
      data-testid="policy-form"
    >
      <Text strong>Entitlement & Accrual</Text>
      <Row gutter={16} style={{ marginTop: 8 }}>
        <Col span={8}>
          <Form.Item
            name="annualDays"
            label="Annual Days"
            rules={[{ required: true, message: 'Please enter annual entitlement' }]}
          >
            <InputNumber min={0} max={365} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name="effectiveFrom"
            label="Effective From"
            rules={[{ required: true, message: 'Please select effective date' }]}
          >
            <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" placeholder="YYYY-MM-DD" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name="accrualEnabled" label="Accrual Enabled" valuePropName="checked">
            <Switch onChange={setAccrualOn} />
          </Form.Item>
        </Col>
      </Row>

      {accrualOn && (
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="accrualFrequency" label="Accrual Frequency">
              <Select
                options={[
                  { value: 'MONTHLY', label: 'Monthly' },
                  { value: 'YEARLY', label: 'Yearly' },
                ]}
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="accrualUnits" label="Accrual Units per Period">
              <InputNumber min={0} max={30} step={0.25} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
        </Row>
      )}

      <Divider />

      <Text strong>Reset & Carry Forward</Text>
      <Row gutter={16} style={{ marginTop: 8 }}>
        <Col span={8}>
          <Form.Item name="resetEnabled" label="Reset Enabled" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Col>
        <Col span={16}>
          <Form.Item name="resetFrequency" label="Reset Frequency">
            <Select
              options={[
                { value: 'YEARLY', label: 'Yearly' },
                { value: 'HALF_YEARLY', label: 'Half-Yearly' },
                { value: 'QUARTERLY', label: 'Quarterly' },
                { value: 'MONTHLY', label: 'Monthly' },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name="carryForwardEnabled" label="Carry Forward" valuePropName="checked">
            <Switch onChange={setCarryForwardOn} />
          </Form.Item>
        </Col>
        {carryForwardOn && (
          <>
            <Col span={8}>
              <Form.Item name="carryForwardCap" label="Max Carry Forward (Days)">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="carryForwardExpiresAfterMonths" label="Expiry (Months)">
                <InputNumber min={1} max={12} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
          </>
        )}
      </Row>

      <Divider />

      <Text strong>Balance & Overdraw Rules</Text>
      <Row gutter={16} style={{ marginTop: 8 }}>
        <Col span={12}>
          <Form.Item
            name="exceedBalanceMode"
            label="Exceed Balance Mode"
            rules={[{ required: true }]}
          >
            <Select
              data-testid="exceed-balance-select"
              onChange={(val) => setExceedMode(val)}
              options={[
                { value: 'NO_LIMIT', label: 'No Limit (Unlimited Negative)' },
                { value: 'YEAR_END_LIMIT', label: 'Year End Limit (Capped Negative)' },
                { value: 'MARK_AS_LOP', label: 'Mark as LOP (Loss of Pay)' },
              ]}
            />
          </Form.Item>
        </Col>
        {exceedMode === 'YEAR_END_LIMIT' && (
          <Col span={12}>
            <Form.Item
              name="exceedBalanceLimitDays"
              label="Negative Balance Limit (Days)"
              rules={[{ required: true, message: 'Please enter negative balance limit' }]}
            >
              <InputNumber
                data-testid="negative-balance-limit"
                min={0}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Col>
        )}
      </Row>

      <Divider />

      <Text strong>Eligibility Matrix</Text>
      <Row gutter={16} style={{ marginTop: 8 }}>
        <Col span={12}>
          <Form.Item name="departmentIds" label="Departments (Empty for All)">
            <Select
              mode="multiple"
              allowClear
              placeholder="All departments"
              options={departments.map((d) => ({ value: d.id, label: d.name }))}
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="designationIds" label="Designations (Empty for All)">
            <Select
              mode="multiple"
              allowClear
              placeholder="All designations"
              options={designations.map((d) => ({ value: d.id, label: d.name }))}
            />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item name="locationIds" label="Work Locations (Empty for All)">
            <Select
              mode="multiple"
              allowClear
              placeholder="All locations"
              options={locations.map((l) => ({ value: l.id, label: l.name }))}
            />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item name="gender" label="Gender Eligibility">
            <Select
              options={[
                { value: 'ALL', label: 'All Genders' },
                { value: 'MALE', label: 'Male' },
                { value: 'FEMALE', label: 'Female' },
                { value: 'OTHER', label: 'Other' },
              ]}
            />
          </Form.Item>
        </Col>
      </Row>

      <Form.Item style={{ marginTop: 16 }}>
        <Space>
          <Button type="primary" htmlType="submit" loading={saving}>
            Save Policy
          </Button>
          {onCancel && (
            <Button onClick={onCancel} disabled={saving}>
              Cancel
            </Button>
          )}
        </Space>
      </Form.Item>
    </Form>
  );
}

PolicyForm.propTypes = {
  leaveType: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string,
    code: PropTypes.string,
    policy: PropTypes.object,
  }).isRequired,
  onSuccess: PropTypes.func,
  onCancel: PropTypes.func,
};
