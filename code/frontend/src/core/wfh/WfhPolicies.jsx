import { useState, useEffect } from 'react';
import { Card, Table, Button, Modal, Form, Input, InputNumber, Switch, DatePicker, Tag, Space, Typography, Row, Col } from 'antd';
import { PlusOutlined, SettingOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { wfhService } from './wfhService.js';
import { formatDate } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

export function WfhPolicies() {
  const canRead = useCan('core.wfh.read');
  const canManage = useCan('core.wfh.manage');

  const [policies, setPolicies] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchPolicies = async () => {
    setLoading(true);
    try {
      const data = await wfhService.getPolicies();
      setPolicies(Array.isArray(data) ? data : []);
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

  const handleCreate = async (values) => {
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

      await wfhService.createPolicy(payload);
      await successMsg('Policy Created', 'Work From Home policy has been created successfully.');
      setModalVisible(false);
      form.resetFields();
      fetchPolicies();
    } catch (err) {
      errorMsg('Creation Failed', err?.response?.data?.message || err?.message);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (code) => <Text strong>{code}</Text>,
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
              onClick={() => setModalVisible(true)}
              data-testid="create-wfh-policy-btn"
            >
              Add WFH Policy
            </Button>
          )}
        </Col>
      </Row>

      <Table
        dataSource={policies}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
        data-testid="wfh-policies-table"
      />

      <Modal
        title="Create Work From Home Policy"
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        footer={null}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            annualDays: 12,
            monthlyLimitDays: 2,
            allowHalfDay: true,
            advanceNoticeDays: 1,
            maxConsecutiveDays: 3,
            requiresManagerApproval: true,
            effectiveFrom: dayjs(),
          }}
          onFinish={handleCreate}
        >
          <Form.Item
            name="code"
            label="Policy Code"
            rules={[{ required: true, message: 'Please enter policy code' }]}
          >
            <Input placeholder="e.g. STANDARD_WFH" maxLength={32} />
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
                Save Policy
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
