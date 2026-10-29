import { useState, useEffect } from 'react';
import { Card, Table, Button, Modal, Form, Input, InputNumber, Switch, DatePicker, Tag, Space, Typography, Row, Col } from 'antd';
import { PlusOutlined, SettingOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { wfhService } from './wfhService.js';
import { formatDate, humanize, EMPTY } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

export function WfhPolicies() {
  const canRead = useCan('core.wfh.read');
  const canManage = useCan('core.wfh.manage');

  const [policies, setPolicies] = useState([]);
  const [defaultPolicy, setDefaultPolicy] = useState(null);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchPolicies = async () => {
    setLoading(true);
    try {
      const promises = [wfhService.getPolicies()];
      if (typeof wfhService.getDefaultPolicy === 'function') {
        promises.push(wfhService.getDefaultPolicy());
      }
      const results = await Promise.allSettled(promises);
      const policiesRes = results[0];
      const defaultRes = results.length > 1 ? results[1] : null;

      if (policiesRes.status === 'fulfilled') {
        const raw = policiesRes.value?.data !== undefined ? policiesRes.value.data : policiesRes.value;
        setPolicies(Array.isArray(raw) ? raw : (raw?.content || raw?.items || []));
      }
      if (defaultRes && defaultRes.status === 'fulfilled') {
        const rawDef = defaultRes.value?.data !== undefined ? defaultRes.value.data : defaultRes.value;
        setDefaultPolicy(rawDef);
      }
    } catch (err) {
      errorMsg('Error', err?.message || 'Failed to fetch WFH policies');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (canRead) {
      fetchPolicies();
    }
  }, [canRead]);

  if (!canRead) {
    return <NotEntitled action="core.wfh.read" />;
  }

  const handleOpenCreate = () => {
    setEditingPolicy(null);
    form.resetFields();
    form.setFieldsValue({
      annualDays: 12,
      monthlyLimitDays: 2,
      allowHalfDay: true,
      advanceNoticeDays: 1,
      maxConsecutiveDays: 3,
      requiresManagerApproval: true,
      effectiveFrom: dayjs(),
    });
    setModalVisible(true);
  };

  const handleOpenEdit = (record) => {
    setEditingPolicy(record);
    form.resetFields();
    form.setFieldsValue({
      code: record.code,
      name: record.name,
      annualDays: Number(record.annualDays || 12),
      monthlyLimitDays: Number(record.monthlyLimitDays || 2),
      allowHalfDay: record.allowHalfDay !== false,
      advanceNoticeDays: record.advanceNoticeDays || 1,
      maxConsecutiveDays: record.maxConsecutiveDays || 3,
      requiresManagerApproval: record.requiresManagerApproval !== false,
      requiresHrApproval: !!record.requiresHrApproval,
      effectiveFrom: record.effectiveFrom ? dayjs(record.effectiveFrom) : dayjs(),
    });
    setModalVisible(true);
  };

  const handleSave = async (values) => {
    setSubmitting(true);
    try {
      const payload = {
        code: values.code.trim().toUpperCase(),
        name: values.name.trim(),
        annualDays: values.annualDays,
        monthlyLimitDays: values.monthlyLimitDays,
        allowHalfDay: values.allowHalfDay !== false,
        advanceNoticeDays: values.advanceNoticeDays || 1,
        maxConsecutiveDays: values.maxConsecutiveDays || 3,
        requiresManagerApproval: values.requiresManagerApproval !== false,
        requiresHrApproval: !!values.requiresHrApproval,
        effectiveFrom: values.effectiveFrom ? dayjs(values.effectiveFrom).format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD'),
        active: true,
      };

      if (editingPolicy) {
        await wfhService.updatePolicy(editingPolicy.id, payload);
        await successMsg('Policy Updated', 'Work From Home policy rules have been updated.');
      } else {
        await wfhService.createPolicy(payload);
        await successMsg('Policy Created', 'Work From Home policy has been created successfully.');
      }
      setModalVisible(false);
      form.resetFields();
      fetchPolicies();
    } catch (err) {
      errorMsg('Save Failed', err?.response?.data?.message || err?.message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (code) => <Text strong>{code ? humanize(code) : EMPTY}</Text>,
    },
    {
      title: 'Policy Name',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Annual Quota',
      dataIndex: 'annualDays',
      key: 'annualDays',
      render: (d) => `${d} days/yr`,
    },
    {
      title: 'Monthly Limit',
      dataIndex: 'monthlyLimitDays',
      key: 'monthlyLimitDays',
      render: (d) => (d ? `${d} days/mo` : 'No limit'),
    },
    {
      title: 'Half Day Allowed',
      dataIndex: 'allowHalfDay',
      key: 'allowHalfDay',
      render: (v) => (v ? <Tag color="green">Yes</Tag> : <Tag color="default">No</Tag>),
    },
    {
      title: 'Max Consecutive',
      dataIndex: 'maxConsecutiveDays',
      key: 'maxConsecutiveDays',
      render: (d) => `${d || 3} days`,
    },
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      render: (d) => formatDate(d),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      key: 'active',
      render: (v) => (v ? <Tag color="success">Active</Tag> : <Tag color="error">Inactive</Tag>),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, r) => canManage && (
        <Button
          type="link"
          size="small"
          onClick={() => handleOpenEdit(r)}
          data-testid={`edit-policy-${r.id || r.code}`}
        >
          Edit Policy
        </Button>
      ),
    },
  ];

  return (
    <Card style={{ margin: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>
            <SettingOutlined style={{ marginRight: 8 }} />
            WFH Policies
          </Title>
          <Text type="secondary">Define annual quotas, monthly pacing, and remote work rules</Text>
        </Col>

        <Col>
          {canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenCreate}
              data-testid="create-wfh-policy-btn"
            >
              Add WFH Policy
            </Button>
          )}
        </Col>
      </Row>

      {defaultPolicy && (
        <Card
          type="inner"
          style={{
            marginBottom: 20,
            background: 'rgba(99, 102, 241, 0.04)',
            borderColor: 'rgba(99, 102, 241, 0.3)',
            borderRadius: 8,
          }}
          data-testid="wfh-default-policy-card"
        >
          <Row justify="space-between" align="middle">
            <Col>
              <Space direction="vertical" size={2}>
                <Space>
                  <Tag color="geekblue">{defaultPolicy.code || 'DEFAULT_WFH'}</Tag>
                  <Text strong>{defaultPolicy.name || 'Default Remote Work Policy'}</Text>
                  <Tag color="cyan">Tenant Fallback</Tag>
                </Space>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  Active system fallback policy governing all employees without an explicit custom policy
                </Text>
              </Space>
            </Col>
            <Col>
              <Space size="large">
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Annual Quota</Text>
                  <div><Text strong style={{ color: '#6366f1' }}>{defaultPolicy.annualDays || 12} days/yr</Text></div>
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Monthly Cap</Text>
                  <div><Text strong>{defaultPolicy.monthlyLimitDays || 2} days/mo</Text></div>
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Half-Day</Text>
                  <div><Tag color={defaultPolicy.allowHalfDay ? 'success' : 'default'}>{defaultPolicy.allowHalfDay ? 'Allowed' : 'Disabled'}</Tag></div>
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>Advance Notice</Text>
                  <div><Text strong>{defaultPolicy.advanceNoticeDays || 1} day</Text></div>
                </div>
              </Space>
            </Col>
          </Row>
        </Card>
      )}

      <Table
        dataSource={policies}
        columns={columns}
        rowKey={(r) => r.id || r.code}
        loading={loading}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: 'No custom WFH policies configured' }}
        data-testid="wfh-policies-table"
      />

      <Modal
        title={editingPolicy ? 'Edit Work From Home Policy' : 'Create Work From Home Policy'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSave}
        >
          <Form.Item
            name="code"
            label="Policy Code"
            rules={[{ required: true, message: 'Please enter policy code' }]}
          >
            <Input placeholder="e.g. STANDARD_WFH" maxLength={32} disabled={!!editingPolicy} />
          </Form.Item>

          <Form.Item
            name="name"
            label="Policy Name"
            rules={[{ required: true, message: 'Please enter policy name' }]}
          >
            <Input placeholder="e.g. Standard Remote Work Policy" maxLength={128} />
          </Form.Item>

          <Space size="large" style={{ display: 'flex', width: '100%' }}>
            <Form.Item
              name="annualDays"
              label="Annual Quota (Days)"
              rules={[{ required: true }]}
            >
              <InputNumber min={0} max={365} step={1} precision={2} />
            </Form.Item>

            <Form.Item
              name="monthlyLimitDays"
              label="Monthly Velocity Limit (Days)"
            >
              <InputNumber min={0} max={31} step={1} precision={2} />
            </Form.Item>
          </Space>

          <Space size="large" style={{ display: 'flex', width: '100%' }}>
            <Form.Item
              name="advanceNoticeDays"
              label="Advance Notice (Days)"
            >
              <InputNumber min={0} max={60} step={1} />
            </Form.Item>

            <Form.Item
              name="maxConsecutiveDays"
              label="Max Consecutive Days"
            >
              <InputNumber min={1} max={30} step={1} />
            </Form.Item>
          </Space>

          <Form.Item
            name="allowHalfDay"
            label="Allow Half Day WFH"
            valuePropName="checked"
          >
            <Switch checkedChildren="Yes" unCheckedChildren="No" />
          </Form.Item>

          <Form.Item
            name="effectiveFrom"
            label="Effective From"
            rules={[{ required: true }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => setModalVisible(false)}>Cancel</Button>
              <Button type="primary" htmlType="submit" loading={submitting}>
                {editingPolicy ? 'Update Policy' : 'Save Policy'}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
