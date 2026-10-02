import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Form,
  Checkbox,
  Radio,
  InputNumber,
  DatePicker,
  Button,
  Switch,
  Select,
  Typography,
  Alert,
  Spin,
  Space,
  Row,
  Col,
  Descriptions,
  message,
  theme,
} from 'antd';
import { CalendarOutlined, SaveOutlined, EyeOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useDispatch } from 'react-redux';
import { payScheduleService } from './payScheduleService.js';
import { lopPolicyService } from './lopPolicyService.js';
import { setPaySchedule, setLopPolicy } from './settingsSlice.js';

const { Text } = Typography;

const DAY_OPTIONS = [
  { label: 'Mon', value: 1 },
  { label: 'Tue', value: 2 },
  { label: 'Wed', value: 3 },
  { label: 'Thu', value: 4 },
  { label: 'Fri', value: 5 },
  { label: 'Sat', value: 6 },
  { label: 'Sun', value: 7 },
];

function getFirstOfNextMonth() {
  const now = new Date();
  const nextMonth = new Date(now.getFullYear(), now.getMonth() + 1, 1);
  return dayjs(nextMonth).format('YYYY-MM-DD');
}

export function PayScheduleScreen() {
  const dispatch = useDispatch();
  const { token } = theme.useToken();

  const [scheduleForm] = Form.useForm();
  const [lopForm] = Form.useForm();

  const [loading, setLoading] = useState(true);
  const [scheduleSaving, setScheduleSaving] = useState(false);
  const [lopSaving, setLopSaving] = useState(false);

  const [payDayRule, setPayDayRule] = useState('LAST_DAY_OF_PERIOD');
  const [workingDayBasis, setWorkingDayBasis] = useState('ACTUAL_DAYS');

  // Preview state
  const [previewMonth, setPreviewMonth] = useState(dayjs().format('YYYY-MM'));
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewData, setPreviewData] = useState(null);
  const [previewError, setPreviewError] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [scheduleRes, lopRes] = await Promise.allSettled([
        payScheduleService.get(),
        lopPolicyService.get(),
      ]);

      if (scheduleRes.status === 'fulfilled' && scheduleRes.value) {
        const sched = scheduleRes.value;
        dispatch(setPaySchedule(sched));
        const rule = sched.pay_day_rule || 'LAST_DAY_OF_PERIOD';
        setPayDayRule(rule);
        scheduleForm.setFieldsValue({
          working_days: sched.working_days || [1, 2, 3, 4, 5],
          pay_day_rule: rule,
          pay_day_of_month: sched.pay_day_of_month,
          input_cutoff_day: sched.input_cutoff_day ?? 25,
          first_period_start: sched.first_period_start ? dayjs(sched.first_period_start) : null,
        });
      } else {
        scheduleForm.setFieldsValue({
          working_days: [1, 2, 3, 4, 5],
          pay_day_rule: 'LAST_DAY_OF_PERIOD',
          input_cutoff_day: 25,
        });
      }

      if (lopRes.status === 'fulfilled' && lopRes.value) {
        const lop = lopRes.value;
        dispatch(setLopPolicy(lop));
        const basis = lop.working_day_basis || 'ACTUAL_DAYS';
        setWorkingDayBasis(basis);
        lopForm.setFieldsValue({
          working_day_basis: basis,
          configured_days_per_month: lop.configured_days_per_month,
          weekends_payable: lop.weekends_payable ?? true,
          holidays_payable: lop.holidays_payable ?? true,
          lop_rounding: lop.lop_rounding || 'HALF_UP_2',
          effective_from: lop.effective_from ? dayjs(lop.effective_from) : dayjs(getFirstOfNextMonth()),
        });
      } else {
        lopForm.setFieldsValue({
          working_day_basis: 'ACTUAL_DAYS',
          weekends_payable: true,
          holidays_payable: true,
          lop_rounding: 'HALF_UP_2',
          effective_from: dayjs(getFirstOfNextMonth()),
        });
      }
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, [dispatch, scheduleForm, lopForm]);

  const loadPreview = useCallback(async (periodStr) => {
    if (!periodStr) return;
    setPreviewLoading(true);
    setPreviewError(null);
    try {
      const data = await payScheduleService.period(periodStr);
      setPreviewData(data);
      setPreviewError(null);
    } catch (err) {
      setPreviewData(null);
      if (err.response?.status === 409 || err.status === 409) {
        setPreviewError('Save the schedule first');
      } else {
        setPreviewError(err.response?.data?.message || err.message || 'Failed to preview period');
      }
    } finally {
      setPreviewLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (previewMonth) {
      loadPreview(previewMonth);
    }
  }, [previewMonth, loadPreview]);

  const handleSaveSchedule = async (values) => {
    setScheduleSaving(true);
    try {
      const payload = {
        working_days: (values.working_days || []).map(Number),
        pay_day_rule: values.pay_day_rule,
        pay_day_of_month: values.pay_day_rule === 'SPECIFIC_DAY' ? values.pay_day_of_month : undefined,
        input_cutoff_day: values.input_cutoff_day,
        first_period_start: values.first_period_start
          ? (typeof values.first_period_start === 'string' ? values.first_period_start : values.first_period_start.format('YYYY-MM-DD'))
          : undefined,
      };
      const updated = await payScheduleService.save(payload);
      dispatch(setPaySchedule(updated));
      message.success('Pay schedule saved successfully');
      if (previewMonth) {
        loadPreview(previewMonth);
      }
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to save pay schedule');
    } finally {
      setScheduleSaving(false);
    }
  };

  const handleSaveLop = async (values) => {
    setLopSaving(true);
    try {
      const payload = {
        working_day_basis: values.working_day_basis,
        configured_days_per_month:
          values.working_day_basis === 'ORG_DAYS' ? values.configured_days_per_month : null,
        weekends_payable: !!values.weekends_payable,
        holidays_payable: !!values.holidays_payable,
        lop_rounding: values.lop_rounding || 'HALF_UP_2',
        effective_from: values.effective_from
          ? (typeof values.effective_from === 'string' ? values.effective_from : values.effective_from.format('YYYY-MM-DD'))
          : getFirstOfNextMonth(),
      };
      const updated = await lopPolicyService.save(payload);
      dispatch(setLopPolicy(updated));
      message.success('Loss of pay policy saved successfully');
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to save LOP policy');
    } finally {
      setLopSaving(false);
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
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* Card 1: Pay Schedule */}
      <Card
        title={
          <Space>
            <CalendarOutlined style={{ color: token.colorPrimary }} />
            <span>Pay Schedule</span>
          </Space>
        }
      >
        <Form
          form={scheduleForm}
          layout="vertical"
          onFinish={handleSaveSchedule}
          onValuesChange={(changed) => {
            if (changed.pay_day_rule) {
              setPayDayRule(changed.pay_day_rule);
            }
          }}
        >
          <Form.Item
            name="working_days"
            label="Work-Week Days"
            rules={[{ required: true, message: 'Select at least one working day' }]}
            extra="ISO weekday numbers (1=Monday to 7=Sunday)"
          >
            <Checkbox.Group options={DAY_OPTIONS} data-testid="working-days-group" />
          </Form.Item>

          <Row gutter={24}>
            <Col xs={24} md={12}>
              <Form.Item
                name="pay_day_rule"
                label="Pay Day Rule"
                rules={[{ required: true, message: 'Please select a pay day rule' }]}
              >
                <Radio.Group data-testid="pay-day-rule-group">
                  <Space direction="vertical">
                    <Radio value="LAST_DAY_OF_PERIOD">Last Day of Period</Radio>
                    <Radio value="LAST_WORKING_DAY">Last Working Day</Radio>
                    <Radio value="SPECIFIC_DAY">Specific Day of Month</Radio>
                  </Space>
                </Radio.Group>
              </Form.Item>
            </Col>

            {payDayRule === 'SPECIFIC_DAY' && (
              <Col xs={24} md={12}>
                <Form.Item
                  name="pay_day_of_month"
                  label="Pay Day of Month (Following Month)"
                  rules={[
                    { required: true, message: 'Enter pay day of month (1-28)' },
                    { type: 'number', min: 1, max: 28, message: 'Must be between 1 and 28' },
                  ]}
                >
                  <InputNumber
                    min={1}
                    max={28}
                    style={{ width: '100%' }}
                    placeholder="1 to 28"
                    data-testid="pay-day-of-month-input"
                  />
                </Form.Item>
              </Col>
            )}
          </Row>

          <Row gutter={24}>
            <Col xs={24} md={12}>
              <Form.Item
                name="input_cutoff_day"
                label="Input Cut-Off Day"
                rules={[
                  { required: true, message: 'Enter cutoff day (1-28)' },
                  { type: 'number', min: 1, max: 28, message: 'Must be between 1 and 28' },
                ]}
                extra="Day of the month for locking attendance and input entries"
              >
                <InputNumber
                  min={1}
                  max={28}
                  style={{ width: '100%' }}
                  placeholder="e.g. 25"
                  data-testid="cutoff-day-input"
                />
              </Form.Item>
            </Col>

            <Col xs={24} md={12}>
              <Form.Item
                name="first_period_start"
                label="First Period Start Date"
                rules={[{ required: true, message: 'Select first period start date' }]}
                extra="Must be the first day of a month"
              >
                <DatePicker
                  style={{ width: '100%' }}
                  format="YYYY-MM-DD"
                  placeholder="YYYY-MM-DD"
                  data-testid="first-period-picker"
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              icon={<SaveOutlined />}
              loading={scheduleSaving}
              data-testid="save-schedule-button"
            >
              Save Schedule
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* Card 2: Loss of Pay (LOP) Basis */}
      <Card
        title={
          <Space>
            <CalendarOutlined style={{ color: token.colorWarning }} />
            <span>Loss of Pay (LOP) Basis</span>
          </Space>
        }
      >
        <Form
          form={lopForm}
          layout="vertical"
          onFinish={handleSaveLop}
          onValuesChange={(changed) => {
            if (changed.working_day_basis) {
              setWorkingDayBasis(changed.working_day_basis);
            }
          }}
        >
          <Row gutter={24}>
            <Col xs={24} md={12}>
              <Form.Item
                name="working_day_basis"
                label="Working-Day Basis"
                rules={[{ required: true, message: 'Select a working day basis' }]}
              >
                <Select
                  data-testid="lop-basis-select"
                  options={[
                    { label: 'Actual Calendar Days (ACTUAL_DAYS)', value: 'ACTUAL_DAYS' },
                    { label: 'Organisation Working Days (ORG_DAYS)', value: 'ORG_DAYS' },
                    { label: 'Fixed 30 Days (FIXED_30)', value: 'FIXED_30' },
                  ]}
                />
              </Form.Item>
            </Col>

            {workingDayBasis === 'ORG_DAYS' && (
              <Col xs={24} md={12}>
                <Form.Item
                  name="configured_days_per_month"
                  label="Configured Days per Month (Optional)"
                  extra="Fixed days count per month if not using calendar working days"
                >
                  <InputNumber
                    min={1}
                    max={31}
                    step={0.5}
                    style={{ width: '100%' }}
                    data-testid="lop-configured-days-input"
                  />
                </Form.Item>
              </Col>
            )}

            <Col xs={24} md={12}>
              <Form.Item
                name="effective_from"
                label="Effective From"
                rules={[{ required: true, message: 'Effective date is required' }]}
                extra="Policy is versioned; a save creates a new version"
              >
                <DatePicker
                  style={{ width: '100%' }}
                  format="YYYY-MM-DD"
                  placeholder="YYYY-MM-DD"
                  data-testid="lop-effective-from-picker"
                />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={24}>
            <Col xs={24} md={8}>
              <Form.Item
                name="weekends_payable"
                label="Weekends Payable"
                valuePropName="checked"
                extra="Whether weekly off days are considered payable"
              >
                <Switch data-testid="lop-weekends-switch" />
              </Form.Item>
            </Col>

            <Col xs={24} md={8}>
              <Form.Item
                name="holidays_payable"
                label="Holidays Payable"
                valuePropName="checked"
                extra="Whether organization holidays are considered payable"
              >
                <Switch data-testid="lop-holidays-switch" />
              </Form.Item>
            </Col>

            <Col xs={24} md={8}>
              <Form.Item
                name="lop_rounding"
                label="LOP Rounding Rule"
                rules={[{ required: true, message: 'Select rounding rule' }]}
              >
                <Select
                  data-testid="lop-rounding-select"
                  options={[
                    { label: 'Half Up (2 decimals)', value: 'HALF_UP_2' },
                    { label: 'Half Up (Integer)', value: 'HALF_UP_0' },
                    { label: 'Truncate (2 decimals)', value: 'TRUNCATE_2' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginBottom: 0 }}>
            <Button
              type="primary"
              htmlType="submit"
              icon={<SaveOutlined />}
              loading={lopSaving}
              data-testid="save-lop-button"
            >
              Save Basis Policy
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* Card 3: Period Preview */}
      <Card
        title={
          <Space>
            <EyeOutlined style={{ color: token.colorInfo }} />
            <span>Period Preview</span>
          </Space>
        }
        extra={
          <Space>
            <Text type="secondary">Month:</Text>
            <DatePicker
              picker="month"
              value={previewMonth ? dayjs(previewMonth) : null}
              onChange={(date) => {
                const str = date ? date.format('YYYY-MM') : '';
                setPreviewMonth(str);
              }}
              data-testid="preview-month-picker"
            />
          </Space>
        }
      >
        {previewLoading && (
          <div style={{ textAlign: 'center', padding: '24px 0' }}>
            <Spin />
            <div style={{ marginTop: 8 }}><Text type="secondary">Loading preview...</Text></div>
          </div>
        )}

        {!previewLoading && previewError && (
          <Alert
            type="warning"
            showIcon
            message={previewError}
            data-testid="preview-error"
          />
        )}

        {!previewLoading && !previewError && previewData && (
          <div data-testid="preview-descriptions">
            <Descriptions
              bordered
              size="middle"
              column={{ xs: 1, sm: 2, md: 4 }}
            >
              <Descriptions.Item label="Period Start">
                <Text strong>{previewData.start}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Period End">
                <Text strong>{previewData.end}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Input Cut-Off Date">
                <Text type="warning">{previewData.cutoff_date}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Pay Date">
                <Text type="success">{previewData.pay_date}</Text>
              </Descriptions.Item>
            </Descriptions>
          </div>
        )}
      </Card>
    </Space>
  );
}

export default PayScheduleScreen;
