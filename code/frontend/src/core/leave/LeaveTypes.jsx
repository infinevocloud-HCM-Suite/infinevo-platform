import { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Button,
  Space,
  Typography,
  Card,
  Drawer,
  Form,
  Input,
  Select,
  Switch,
  DatePicker,
  Tag,
  Row,
  Col,
} from 'antd';
import { PlusOutlined, EditOutlined, SettingOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { leaveTypeService } from './leaveTypeService.js';
import { PolicyForm } from './PolicyForm.jsx';

const { Title } = Typography;

export function LeaveTypes() {
  const canRead = useCan('core.leave.read');
  const canManage = useCan('core.leave_type.manage');

  const [loading, setLoading] = useState(false);
  const [types, setTypes] = useState([]);

  // Create / Edit Leave Type Drawer
  const [typeDrawerOpen, setTypeDrawerOpen] = useState(false);
  const [editingType, setEditingType] = useState(null);
  const [typeForm] = Form.useForm();
  const [typeSaving, setTypeSaving] = useState(false);

  // Policy Drawer
  const [policyDrawerOpen, setPolicyDrawerOpen] = useState(false);
  const [policyLeaveType, setPolicyLeaveType] = useState(null);

  const loadLeaveTypes = useCallback(async () => {
    setLoading(true);
    try {
      const data = await leaveTypeService.list();
      setTypes(Array.isArray(data) ? data : []);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canRead) {
      loadLeaveTypes();
    }
  }, [canRead, loadLeaveTypes]);

  const handleOpenCreate = () => {
    setEditingType(null);
    typeForm.resetFields();
    typeForm.setFieldsValue({
      isPaid: true,
      unit: 'DAYS',
      allowHalfDay: true,
      isActive: true,
    });
    setTypeDrawerOpen(true);
  };

  const handleOpenEdit = (record) => {
    setEditingType(record);
    typeForm.setFieldsValue({
      name: record.name,
      code: record.code,
      isPaid: record.isPaid ?? true,
      unit: record.unit || 'DAYS',
      allowHalfDay: record.allowHalfDay ?? true,
      isActive: record.isActive ?? true,
      validFrom: record.validFrom ? dayjs(record.validFrom) : null,
      validTo: record.validTo ? dayjs(record.validTo) : null,
    });
    setTypeDrawerOpen(true);
  };

  const handleSaveType = async (values) => {
    setTypeSaving(true);
    try {
      const payload = {
        name: values.name,
        code: values.code,
        isPaid: Boolean(values.isPaid),
        unit: values.unit,
        allowHalfDay: Boolean(values.allowHalfDay),
        isActive: Boolean(values.isActive),
        validFrom: values.validFrom ? values.validFrom.format('YYYY-MM-DD') : null,
        validTo: values.validTo ? values.validTo.format('YYYY-MM-DD') : null,
      };

      if (editingType) {
        await leaveTypeService.update(editingType.id, payload);
        await successMsg('Leave type updated');
      } else {
        await leaveTypeService.create(payload);
        await successMsg('Leave type created');
      }
      setTypeDrawerOpen(false);
      loadLeaveTypes();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setTypeSaving(false);
    }
  };

  const handleOpenPolicy = (record) => {
    setPolicyLeaveType(record);
    setPolicyDrawerOpen(true);
  };

  if (!canRead) {
    return <NotEntitled />;
  }

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{text}</Typography.Text>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {record.code}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Paid',
      dataIndex: 'isPaid',
      key: 'isPaid',
      render: (paid) =>
        paid ? <Tag color="green">Paid</Tag> : <Tag color="orange">Unpaid</Tag>,
    },
    {
      title: 'Unit',
      dataIndex: 'unit',
      key: 'unit',
      render: (unit) => <Tag>{unit || 'DAYS'}</Tag>,
    },
    {
      title: 'Half-Day',
      dataIndex: 'allowHalfDay',
      key: 'allowHalfDay',
      render: (allowed) => (allowed ? 'Allowed' : 'Not Allowed'),
    },
    {
      title: 'Validity',
      key: 'validity',
      render: (_, record) => {
        if (!record.validFrom && !record.validTo) return 'Permanent';
        return `${record.validFrom || ''} to ${record.validTo || 'Present'}`;
      },
    },
    {
      title: 'Policy Summary',
      key: 'policy',
      render: (_, record) => {
        if (!record.policy) {
          return <Typography.Text type="secondary">Not configured</Typography.Text>;
        }
        const p = record.policy;
        return (
          <Space orientation="vertical" size={2}>
            <span>{p.annualDays} days/yr</span>
            <Tag color={p.exceedBalanceMode === 'MARK_AS_LOP' ? 'red' : 'blue'}>
              {p.exceedBalanceMode || 'NO_LIMIT'}
            </Tag>
          </Space>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'isActive',
      key: 'isActive',
      render: (active) =>
        active !== false ? <Tag color="success">Active</Tag> : <Tag color="default">Inactive</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          {canManage && (
            <>
              <Button
                type="link"
                icon={<EditOutlined />}
                onClick={() => handleOpenEdit(record)}
              >
                Edit
              </Button>
              <Button
                type="link"
                icon={<SettingOutlined />}
                onClick={() => handleOpenPolicy(record)}
              >
                Policy
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card style={{ margin: 24 }}>
      <Row justify="space-between" align="middle" style={{ marginBottom: 16 }}>
        <Col>
          <Title level={4} style={{ margin: 0 }}>
            Leave Types
          </Title>
        </Col>
        <Col>
          {canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenCreate}
            >
              Add Leave Type
            </Button>
          )}
        </Col>
      </Row>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={types}
        loading={loading}
        pagination={{ pageSize: 15 }}
      />

      {/* Leave Type Create/Edit Drawer */}
      <Drawer
        title={editingType ? 'Edit Leave Type' : 'Add Leave Type'}
        width={480}
        open={typeDrawerOpen}
        onClose={() => setTypeDrawerOpen(false)}
        destroyOnClose
      >
        <Form form={typeForm} layout="vertical" onFinish={handleSaveType}>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Please enter leave type name' }]}
          >
            <Input placeholder="e.g. Annual Leave, Sick Leave" />
          </Form.Item>

          <Form.Item
            name="code"
            label="Code"
            rules={[{ required: true, message: 'Please enter unique code' }]}
          >
            <Input
              placeholder="e.g. AL, SL, CL"
              disabled={Boolean(editingType)}
            />
          </Form.Item>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="unit" label="Unit" rules={[{ required: true }]}>
                <Select
                  options={[
                    { value: 'DAYS', label: 'Days' },
                    { value: 'HOURS', label: 'Hours' },
                  ]}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="isPaid" label="Paid Leave" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="allowHalfDay" label="Allow Half-Day" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="isActive" label="Active" valuePropName="checked">
                <Switch />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="validFrom" label="Valid From">
                <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="validTo" label="Valid To">
                <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item style={{ marginTop: 24 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={typeSaving}>
                Save
              </Button>
              <Button onClick={() => setTypeDrawerOpen(false)}>Cancel</Button>
            </Space>
          </Form.Item>
        </Form>
      </Drawer>

      {/* Policy Drawer */}
      <Drawer
        title={policyLeaveType ? `Configure Policy - ${policyLeaveType.name}` : 'Configure Policy'}
        width={640}
        open={policyDrawerOpen}
        onClose={() => setPolicyDrawerOpen(false)}
        destroyOnClose
      >
        {policyLeaveType && (
          <PolicyForm
            leaveType={policyLeaveType}
            onSuccess={() => {
              setPolicyDrawerOpen(false);
              loadLeaveTypes();
            }}
            onCancel={() => setPolicyDrawerOpen(false)}
          />
        )}
      </Drawer>
    </Card>
  );
}
