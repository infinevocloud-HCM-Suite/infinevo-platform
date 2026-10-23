import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { useNavigate } from 'react-router-dom';
import { Card, Form, DatePicker, Switch, Radio, Input, Button, Space, Typography, Alert, Row, Col, theme } from 'antd';
import { ArrowLeftOutlined, SendOutlined, CheckCircleOutlined, InfoCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { wfhService } from '../wfh/wfhService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { RangePicker } = DatePicker;

function countWorkingDays(startDate, endDate) {
  if (!startDate || !endDate) return 0;
  let count = 0;
  let cur = dayjs(startDate);
  const end = dayjs(endDate);
  while (!cur.isAfter(end)) {
    const day = cur.day();
    if (day !== 0 && day !== 6) {
      count++;
    }
    cur = cur.add(1, 'day');
  }
  return count;
}

export function ApplyWfh({ onSuccess }) {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const canApply = useCan('core.wfh.apply');

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [balance, setBalance] = useState(null);
  const [isHalfDay, setIsHalfDay] = useState(false);
  const [estimatedDays, setEstimatedDays] = useState(0);

  useEffect(() => {
    async function fetchBalance() {
      try {
        const bal = await wfhService.getMyBalance();
        setBalance(bal);
      } catch (err) {
        console.warn('Could not load WFH balance', err);
      }
    }
    fetchBalance();
  }, []);

  if (!canApply) {
    return <NotEntitled action="core.wfh.apply" />;
  }

  const handleValuesChange = (_, allValues) => {
    if (allValues.isHalfDay) {
      setEstimatedDays(allValues.singleDate ? 0.5 : 0);
    } else if (allValues.dateRange && allValues.dateRange[0] && allValues.dateRange[1]) {
      const days = countWorkingDays(allValues.dateRange[0], allValues.dateRange[1]);
      setEstimatedDays(days);
    } else {
      setEstimatedDays(0);
    }
  };

  const handleFinish = async (values) => {
    setLoading(true);
    try {
      let fromDate;
      let toDate;

      if (values.isHalfDay) {
        const d = values.singleDate ? dayjs(values.singleDate).format('YYYY-MM-DD') : null;
        fromDate = d;
        toDate = d;
      } else {
        fromDate = values.dateRange?.[0] ? dayjs(values.dateRange[0]).format('YYYY-MM-DD') : null;
        toDate = values.dateRange?.[1] ? dayjs(values.dateRange[1]).format('YYYY-MM-DD') : null;
      }

      if (!fromDate || !toDate) {
        throw new Error('Please select valid WFH dates');
      }

      const payload = {
        fromDate,
        toDate,
        halfDay: !!values.isHalfDay,
        halfDayPeriod: values.isHalfDay ? values.halfDayPeriod || 'first' : null,
        reason: values.reason ? values.reason.trim() : '',
      };

      await wfhService.apply(payload);
      await successMsg(
        'WFH Request Submitted',
        'Your Work From Home application has been submitted for approval.'
      );

      if (onSuccess) {
        onSuccess();
      } else {
        navigate('/me/wfh');
      }
    } catch (err) {
      await errorMsg(
        'Application Failed',
        err?.response?.data?.message || err?.message || 'Failed to submit WFH request'
      );
    } finally {
      setLoading(false);
    }
  };

  const availableDays = balance?.availableDays !== undefined ? Number(balance.availableDays) : 12;
  const isOverBalance = estimatedDays > availableDays;

  return (
    <div style={{ maxWidth: 760, margin: '0 auto', padding: token.paddingMD }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/me/wfh')}
            data-testid="back-to-wfh-btn"
          >
            Back to WFH
          </Button>
          <Title level={3} style={{ margin: 0 }}>Apply for Work From Home</Title>
          <div style={{ width: 80 }} />
        </div>

        {balance && (
          <Alert
            message={
              <Row justify="space-between" align="middle">
                <Col>
                  <Space>
                    <InfoCircleOutlined style={{ color: token.colorPrimary }} />
                    <Text strong>Current Available Quota:</Text>
                    <Text style={{ fontSize: token.fontSizeLG, fontWeight: 700, color: token.colorPrimary }}>
                      {availableDays} days
                    </Text>
                  </Space>
                </Col>
                <Col>
                  <Text type="secondary">
                    Monthly Pacing: max {balance.monthlyLimit || 2} days/mo (Used: {balance.monthConsumed || 0}d)
                  </Text>
                </Col>
              </Row>
            }
            type="info"
            showIcon={false}
            style={{ borderRadius: token.borderRadiusLG }}
          />
        )}

        <Card
          data-testid="apply-wfh-card"
          style={{
            borderRadius: token.borderRadiusLG,
            boxShadow: '0 4px 16px rgba(0,0,0,0.06)',
          }}
        >
          <Form
            form={form}
            layout="vertical"
            initialValues={{
              isHalfDay: false,
              halfDayPeriod: 'first',
            }}
            onValuesChange={handleValuesChange}
            onFinish={handleFinish}
          >
            <Form.Item
              name="isHalfDay"
              label="Half Day Application"
              valuePropName="checked"
            >
              <Switch
                checkedChildren="Half Day"
                unCheckedChildren="Full Day"
                data-testid="half-day-switch"
                onChange={(checked) => {
                  setIsHalfDay(checked);
                  form.setFieldsValue({
                    singleDate: null,
                    dateRange: null,
                  });
                  setEstimatedDays(0);
                }}
              />
            </Form.Item>

            {isHalfDay ? (
              <>
                <Form.Item
                  name="singleDate"
                  label="Select Date"
                  rules={[{ required: true, message: 'Please select the WFH date' }]}
                >
                  <DatePicker
                    style={{ width: '100%' }}
                    data-testid="wfh-single-date"
                    disabledDate={(current) => current && current.day() === 0 || current.day() === 6}
                  />
                </Form.Item>

                <Form.Item
                  name="halfDayPeriod"
                  label="Half Day Period"
                  rules={[{ required: true }]}
                >
                  <Radio.Group data-testid="half-day-period-group">
                    <Radio value="first">1st Half (Morning)</Radio>
                    <Radio value="second">2nd Half (Afternoon)</Radio>
                  </Radio.Group>
                </Form.Item>
              </>
            ) : (
              <Form.Item
                name="dateRange"
                label="Select Date Range"
                rules={[{ required: true, message: 'Please select from and to dates' }]}
              >
                <RangePicker
                  style={{ width: '100%' }}
                  data-testid="wfh-date-range"
                  disabledDate={(current) => current && (current.day() === 0 || current.day() === 6)}
                />
              </Form.Item>
            )}

            {estimatedDays > 0 && (
              <div style={{ marginBottom: token.marginMD }}>
                <Alert
                  type={isOverBalance ? 'error' : 'success'}
                  showIcon
                  icon={isOverBalance ? undefined : <CheckCircleOutlined />}
                  message={
                    <span>
                      Estimated working days: <strong>{estimatedDays} day{estimatedDays !== 1 ? 's' : ''}</strong>
                      {isOverBalance && ` (Exceeds available balance of ${availableDays} days!)`}
                    </span>
                  }
                />
              </div>
            )}

            <Form.Item
              name="reason"
              label="Reason for Remote Work"
              rules={[
                { required: true, message: 'Please provide a reason for WFH' },
                { min: 5, message: 'Please provide at least 5 characters' },
              ]}
            >
              <TextArea
                rows={4}
                placeholder="Describe your remote work focus, tasks, or circumstances..."
                maxLength={500}
                showCount
                data-testid="wfh-reason-input"
              />
            </Form.Item>

            <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
              <Space>
                <Button onClick={() => navigate('/me/wfh')}>Cancel</Button>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<SendOutlined />}
                  loading={loading}
                  disabled={isOverBalance}
                  data-testid="submit-wfh-btn"
                >
                  Submit Application
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      </Space>
    </div>
  );
}

ApplyWfh.propTypes = {
  onSuccess: PropTypes.func,
};
