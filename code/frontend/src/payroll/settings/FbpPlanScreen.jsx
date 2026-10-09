import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
  Card,
  Form,
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
  Tag,
  Table,
  Divider,
  message,
  theme,
} from 'antd';
import {
  GiftOutlined,
  SaveOutlined,
  LockOutlined,
  UnlockOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useDispatch } from 'react-redux';
import { fbpService } from './fbpService.js';
import { setFbpPlan } from './settingsSlice.js';

const { Text } = Typography;

export function FbpPlanScreen() {
  const dispatch = useDispatch();
  const { token } = theme.useToken();
  const [form] = Form.useForm();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [locking, setLocking] = useState(false);
  const [plan, setPlan] = useState(null);
  const [components, setComponents] = useState([]);
  const [isEnabled, setIsEnabled] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [planRes, compRes] = await Promise.allSettled([
        fbpService.plan(),
        fbpService.components(),
      ]);

      if (planRes.status === 'fulfilled' && planRes.value) {
        const p = planRes.value;
        setPlan(p);
        dispatch(setFbpPlan(p));
        // FbpPlanResponse field names (D-67).
        setIsEnabled(!!p.isEnabled);
        form.setFieldsValue({
          isEnabled: !!p.isEnabled,
          windowOpensOn: p.windowOpensOn ? dayjs(p.windowOpensOn) : null,
          windowClosesOn: p.windowClosesOn ? dayjs(p.windowClosesOn) : null,
          notifyOnRelease: !!p.notifyOnRelease,
          notifyOnLock: !!p.notifyOnLock,
          reminderDaysBeforeClose: p.reminderDaysBeforeClose
            ? p.reminderDaysBeforeClose.map(String)
            : ['15', '7', '3', '1'],
        });
      } else {
        form.setFieldsValue({
          isEnabled: false,
          reminderDaysBeforeClose: ['15', '7', '3', '1'],
        });
      }

      if (compRes.status === 'fulfilled' && compRes.value) {
        const items = Array.isArray(compRes.value) ? compRes.value : compRes.value?.items || [];
        setComponents(items);
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
      const reminderDays = Array.from(
        new Set(
          (values.reminderDaysBeforeClose || [])
            .map((v) => parseInt(v, 10))
            .filter((n) => !isNaN(n) && n >= 1 && n <= 60)
        )
      ).sort((a, b) => b - a);

      // FbpPlanRequest field names (D-67).
      const payload = {
        isEnabled: !!values.isEnabled,
        windowOpensOn: values.windowOpensOn
          ? (typeof values.windowOpensOn === 'string' ? values.windowOpensOn : values.windowOpensOn.format('YYYY-MM-DD'))
          : null,
        windowClosesOn: values.windowClosesOn
          ? (typeof values.windowClosesOn === 'string' ? values.windowClosesOn : values.windowClosesOn.format('YYYY-MM-DD'))
          : null,
        notifyOnRelease: !!values.notifyOnRelease,
        notifyOnLock: !!values.notifyOnLock,
        reminderDaysBeforeClose: reminderDays,
      };

      const updated = await fbpService.savePlan(payload);
      setPlan(updated);
      dispatch(setFbpPlan(updated));
      message.success('FBP plan configuration saved');
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to save FBP plan');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleLock = async () => {
    setLocking(true);
    try {
      if (plan?.isLocked) {
        const updated = await fbpService.unlock();
        setPlan((prev) => ({ ...prev, ...updated, isLocked: false, lockedAt: null }));
        message.success('FBP plan unlocked');
      } else {
        const updated = await fbpService.lock();
        setPlan((prev) => ({ ...prev, ...updated, isLocked: true }));
        message.success('FBP plan locked');
      }
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Lock operation failed');
    } finally {
      setLocking(false);
    }
  };

  const componentColumns = [
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      render: (k) => (
        <Tag color={k === 'EARNING' ? 'cyan' : 'orange'}>{k}</Tag>
      ),
    },
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (c) => <Text code>{c}</Text>,
    },
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (n) => <Text strong>{n}</Text>,
    },
    {
      title: 'Max Limit (₹)',
      dataIndex: 'maxLimit',
      key: 'maxLimit',
      render: (limit) => (limit != null ? `₹${limit}` : 'No limit'),
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        title={
          <Space>
            <GiftOutlined style={{ color: token.colorPrimary }} />
            <span>Flexible Benefit Plan (FBP) Configuration</span>
          </Space>
        }
        extra={
          plan && (
            <Space>
              <Tag
                color={plan.isLocked ? 'error' : plan.isEnabled ? 'success' : 'default'}
                data-testid="fbp-status-tag"
              >
                {plan.isLocked ? 'Locked' : plan.isEnabled ? 'Enabled' : 'Disabled'}
              </Tag>
              {plan.lockedAt && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Locked at: {dayjs(plan.lockedAt).format('D MMM YYYY, HH:mm')}
                </Text>
              )}
              <Button
                size="small"
                icon={plan.isLocked ? <UnlockOutlined /> : <LockOutlined />}
                danger={!plan.isLocked}
                loading={locking}
                onClick={handleToggleLock}
                data-testid="lock-fbp-button"
              >
                {plan.isLocked ? 'Unlock Plan' : 'Lock Plan'}
              </Button>
            </Space>
          )
        }
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleFinish}
          onValuesChange={(changed) => {
            if ('isEnabled' in changed) {
              setIsEnabled(!!changed.isEnabled);
            }
          }}
        >
          <Row gutter={24} align="middle">
            <Col xs={24} md={8}>
              <Form.Item
                name="isEnabled"
                label="Enable Flexible Benefit Plan"
                valuePropName="checked"
                extra="Allow employees to submit declarations during open window"
              >
                <Switch data-testid="fbp-enabled-switch" />
              </Form.Item>
            </Col>

            <Col xs={24} md={8}>
              <Form.Item
                name="windowOpensOn"
                label="Window Opens On"
                rules={[
                  {
                    validator: (_, val) => {
                      if (isEnabled && !val) {
                        return Promise.reject(new Error('Window start date is required when enabled'));
                      }
                      return Promise.resolve();
                    },
                  },
                ]}
              >
                <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" data-testid="fbp-opens-picker" />
              </Form.Item>
            </Col>

            <Col xs={24} md={8}>
              <Form.Item
                name="windowClosesOn"
                label="Window Closes On"
                rules={[
                  {
                    validator: (_, val) => {
                      if (isEnabled && !val) {
                        return Promise.reject(new Error('Window close date is required when enabled'));
                      }
                      return Promise.resolve();
                    },
                  },
                ]}
              >
                <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" data-testid="fbp-closes-picker" />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">Notifications & Reminders</Divider>

          <Row gutter={24}>
            <Col xs={24} md={12}>
              <Form.Item
                name="notifyOnRelease"
                label="Notify Employees on Window Release"
                valuePropName="checked"
                extra="Send email notification when FBP window opens"
              >
                <Switch />
              </Form.Item>
            </Col>

            <Col xs={24} md={12}>
              <Form.Item
                name="notifyOnLock"
                label="Notify Employees on Lock"
                valuePropName="checked"
                extra="Send email notification when FBP window closes or locks"
              >
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={24}>
            <Col xs={24}>
              <Form.Item
                name="reminderDaysBeforeClose"
                label="Reminder Days Before Window Closes"
                extra="Integer days (1-60). Press Enter to add tags."
                rules={[
                  {
                    validator: async (_, values) => {
                      if (values && values.length > 0) {
                        for (const v of values) {
                          const num = Number(v);
                          if (isNaN(num) || num < 1 || num > 60) {
                            return Promise.reject(new Error('Every reminder day must be an integer between 1 and 60'));
                          }
                        }
                      }
                    },
                  },
                ]}
              >
                <Select
                  mode="tags"
                  style={{ width: '100%' }}
                  placeholder="e.g. 15, 7, 3, 1"
                  tokenSeparators={[',', ' ']}
                  data-testid="fbp-reminders-select"
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginTop: 16 }}>
            <Button
              type="primary"
              htmlType="submit"
              icon={<SaveOutlined />}
              loading={saving}
              data-testid="save-fbp-plan-button"
            >
              Save FBP Plan
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* Read-only Table of FBP Components */}
      <Card
        title={
          <Row justify="space-between" align="middle" style={{ width: '100%' }}>
            <Col>
              <span>FBP Eligible Components ({components.length})</span>
            </Col>
            <Col>
              <Link to="/payroll/components">
                <Button size="small" type="link" icon={<LinkOutlined />}>
                  Manage in Salary Components
                </Button>
              </Link>
            </Col>
          </Row>
        }
      >
        {components.length === 0 ? (
          <Alert
            message="No FBP Components Configured"
            description="Components must be flagged as FBP components in Salary Components to appear here."
            type="info"
            showIcon
          />
        ) : (
          <Table
            dataSource={components}
            columns={componentColumns}
            rowKey={(r) => r.id || r.code}
            pagination={false}
            size="small"
            bordered
            data-testid="fbp-components-table"
          />
        )}
      </Card>
    </Space>
  );
}

export default FbpPlanScreen;
