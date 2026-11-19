import { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Button,
  Space,
  Typography,
  Card,
  Modal,
  Form,
  Input,
  Select,
  Switch,
  InputNumber,
  Tag,
  Alert,
  Spin,
  Popconfirm,
  Tooltip,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  StopOutlined,
  CheckOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { onboardingService } from './onboardingService.js';
import { DOCUMENT_LABELS } from '../document/documentService.js';

const { Text } = Typography;

const TASK_TYPE_LABELS = {
  DOCUMENT_UPLOAD: 'Document Upload',
  PROFILE_UPDATE: 'Profile Update',
  POLICY_ACKNOWLEDGEMENT: 'Policy Acknowledgement',
  MANUAL_TASK: 'Manual Task',
};

const TASK_TYPE_COLORS = {
  DOCUMENT_UPLOAD: 'blue',
  PROFILE_UPDATE: 'purple',
  POLICY_ACKNOWLEDGEMENT: 'cyan',
  MANUAL_TASK: 'gold',
};

const ASSIGNED_ROLES = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'HR', label: 'HR' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'IT', label: 'IT' },
];

export function OnboardingTemplateSettings() {
  const canManage = useCan('core.onboarding.manage');

  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [templates, setTemplates] = useState([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);

  const selectedTaskType = Form.useWatch('taskType', form);

  const loadTemplates = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await onboardingService.getTemplates();
      const list = Array.isArray(data) ? [...data] : [];
      list.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
      setTemplates(list);
    } catch (err) {
      setLoadError(err?.message || 'Failed to load onboarding templates');
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canManage) {
      loadTemplates();
    }
  }, [canManage, loadTemplates]);

  if (!canManage) {
    return <NotEntitled />;
  }

  const handleOpenAdd = () => {
    setEditingTemplate(null);
    setFormError(null);
    form.resetFields();
    form.setFieldsValue({
      assignedRole: 'EMPLOYEE',
      taskType: 'MANUAL_TASK',
      isMandatory: true,
      dueDaysAfterJoining: 7,
      sortOrder: templates.length,
    });
    setModalOpen(true);
  };

  const handleOpenEdit = (record) => {
    setEditingTemplate(record);
    setFormError(null);
    form.resetFields();
    form.setFieldsValue({
      title: record.title,
      description: record.description,
      taskType: record.taskType,
      documentLabel: record.documentLabel,
      assignedRole: record.assignedRole || 'EMPLOYEE',
      isMandatory: record.isMandatory ?? true,
      dueDaysAfterJoining: record.dueDaysAfterJoining ?? 7,
      sortOrder: record.sortOrder ?? 0,
      isActive: record.isActive ?? true,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      setFormError(null);

      const payload = {
        title: values.title?.trim(),
        description: values.description?.trim() || null,
        taskType: values.taskType,
        documentLabel: values.taskType === 'DOCUMENT_UPLOAD' ? (values.documentLabel || null) : null,
        assignedRole: values.assignedRole || 'EMPLOYEE',
        isMandatory: values.isMandatory ?? true,
        dueDaysAfterJoining: values.dueDaysAfterJoining ?? 0,
        sortOrder: values.sortOrder ?? 0,
      };

      if (editingTemplate) {
        payload.isActive = values.isActive ?? true;
        await onboardingService.updateTemplate(editingTemplate.id, payload);
        await successMsg('Template Updated', `"${payload.title}" has been updated.`);
      } else {
        await onboardingService.createTemplate(payload);
        await successMsg('Template Created', `"${payload.title}" has been created.`);
      }

      setModalOpen(false);
      loadTemplates();
    } catch (err) {
      if (err?.errorFields) {
        return; // Client validation failed
      }
      const msg = err?.response?.data?.message || err?.message || 'Failed to save template';
      setFormError(msg);

      // Attach field errors if message matches known fields
      const lower = msg.toLowerCase();
      if (lower.includes('title')) {
        form.setFields([{ name: 'title', errors: [msg] }]);
      } else if (lower.includes('duedaysafterjoining') || lower.includes('due days')) {
        form.setFields([{ name: 'dueDaysAfterJoining', errors: [msg] }]);
      } else if (lower.includes('documentlabel')) {
        form.setFields([{ name: 'documentLabel', errors: [msg] }]);
      } else if (lower.includes('tasktype') || lower.includes('task type')) {
        form.setFields([{ name: 'taskType', errors: [msg] }]);
      }
      await errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  const handleToggleActive = async (record) => {
    try {
      const nextActive = !record.isActive;
      await onboardingService.updateTemplate(record.id, { isActive: nextActive });
      await successMsg(
        nextActive ? 'Template Activated' : 'Template Deactivated',
        `"${record.title}" is now ${nextActive ? 'active' : 'inactive'}.`
      );
      loadTemplates();
    } catch (err) {
      await errorMsg(err);
    }
  };

  const handleMove = async (index, direction) => {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= templates.length) return;

    const current = templates[index];
    const target = templates[targetIndex];

    try {
      setLoading(true);
      const currentOrder = current.sortOrder ?? index;
      const targetOrder = target.sortOrder ?? targetIndex;

      // Swap sortOrders
      await onboardingService.updateTemplate(current.id, { sortOrder: targetOrder });
      await onboardingService.updateTemplate(target.id, { sortOrder: currentOrder });
      loadTemplates();
    } catch (err) {
      await errorMsg(err);
      setLoading(false);
    }
  };

  if (loadError) {
    return (
      <div style={{ padding: 24 }}>
        <Alert
          message="Failed to load onboarding templates"
          description={loadError}
          type="error"
          showIcon
          action={<Button icon={<ReloadOutlined />} onClick={loadTemplates}>Retry</Button>}
        />
      </div>
    );
  }

  const columns = [
    {
      title: 'Order',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 80,
      render: (val) => <Text code>{val}</Text>,
    },
    {
      title: 'Title',
      dataIndex: 'title',
      key: 'title',
      render: (title, record) => (
        <div>
          <Text strong style={{ textDecoration: record.isActive === false ? 'line-through' : 'none' }}>
            {title}
          </Text>
          {record.isMandatory && (
            <Tag color="red" style={{ marginLeft: 8 }}>
              Mandatory
            </Tag>
          )}
          {record.description && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {record.description}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'taskType',
      key: 'taskType',
      width: 160,
      render: (type, record) => (
        <Space direction="vertical" size={2}>
          <Tag color={TASK_TYPE_COLORS[type] || 'default'}>
            {TASK_TYPE_LABELS[type] || type}
          </Tag>
          {type === 'DOCUMENT_UPLOAD' && record.documentLabel && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Doc: {record.documentLabel}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Assigned Role',
      dataIndex: 'assignedRole',
      key: 'assignedRole',
      width: 120,
      render: (role) => <Tag>{role || 'EMPLOYEE'}</Tag>,
    },
    {
      title: 'Due Days',
      dataIndex: 'dueDaysAfterJoining',
      key: 'dueDaysAfterJoining',
      width: 110,
      render: (days) => `${days ?? 0} days`,
    },
    {
      title: 'Status',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 100,
      render: (isActive) =>
        isActive !== false ? <Tag color="success">Active</Tag> : <Tag color="default">Inactive</Tag>,
    },
    {
      title: 'Reorder',
      key: 'reorder',
      width: 100,
      render: (_, __, index) => (
        <Space size="small">
          <Tooltip title="Move up">
            <Button
              size="small"
              icon={<ArrowUpOutlined />}
              disabled={index === 0}
              onClick={() => handleMove(index, -1)}
            />
          </Tooltip>
          <Tooltip title="Move down">
            <Button
              size="small"
              icon={<ArrowDownOutlined />}
              disabled={index === templates.length - 1}
              onClick={() => handleMove(index, 1)}
            />
          </Tooltip>
        </Space>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() => handleOpenEdit(record)}
          >
            Edit
          </Button>
          <Popconfirm
            title={record.isActive !== false ? 'Deactivate this template?' : 'Activate this template?'}
            description={
              record.isActive !== false
                ? 'Inactive templates will not be assigned to new joiners.'
                : 'Active templates will be assigned to new joiners.'
            }
            onConfirm={() => handleToggleActive(record)}
            okText={record.isActive !== false ? 'Deactivate' : 'Activate'}
          >
            <Button
              size="small"
              danger={record.isActive !== false}
              icon={record.isActive !== false ? <StopOutlined /> : <CheckOutlined />}
            >
              {record.isActive !== false ? 'Deactivate' : 'Activate'}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Onboarding Templates"
        subtitle="Manage checklist tasks automatically assigned to newly hired employees"
        actions={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleOpenAdd}>
            Add Template
          </Button>
        }
      />

      <Card variant="borderless" style={{ marginTop: 16 }}>
        {loading && !templates.length ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin size="large" />
          </div>
        ) : (
          <Table
            rowKey="id"
            dataSource={templates}
            columns={columns}
            pagination={false}
            loading={loading}
          />
        )}
      </Card>

      <Modal
        title={editingTemplate ? 'Edit Onboarding Template' : 'Add Onboarding Template'}
        open={modalOpen}
        onOk={handleSave}
        onCancel={() => setModalOpen(false)}
        confirmLoading={saving}
        destroyOnHidden
      >
        {formError && (
          <Alert message={formError} type="error" showIcon style={{ marginBottom: 16 }} />
        )}
        <Form form={form} layout="vertical">
          <Form.Item
            name="title"
            label="Title"
            rules={[
              { required: true, message: 'Title is required' },
              { max: 200, message: 'Title must not exceed 200 characters' },
            ]}
          >
            <Input placeholder="e.g. Submit Identity Proof" maxLength={200} />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} placeholder="Optional instructions or guidance" />
          </Form.Item>

          <Form.Item
            name="taskType"
            label="Task Type"
            rules={[{ required: true, message: 'Task type is required' }]}
          >
            <Select options={Object.entries(TASK_TYPE_LABELS).map(([k, v]) => ({ value: k, label: v }))} />
          </Form.Item>

          {selectedTaskType === 'DOCUMENT_UPLOAD' && (
            <Form.Item
              name="documentLabel"
              label="Document Label"
              rules={[{ required: true, message: 'Document label is required for document upload tasks' }]}
            >
              <Select
                placeholder="Select or specify document label"
                options={DOCUMENT_LABELS.map((d) => ({ value: d.value, label: d.label }))}
              />
            </Form.Item>
          )}

          <Form.Item
            name="assignedRole"
            label="Assigned Role"
            rules={[{ required: true, message: 'Assigned role is required' }]}
          >
            <Select options={ASSIGNED_ROLES} />
          </Form.Item>

          <Form.Item
            name="dueDaysAfterJoining"
            label="Due Days After Joining"
            rules={[
              { required: true, message: 'Due days is required' },
              { type: 'number', min: 0, message: 'dueDaysAfterJoining must be 0 or more' },
            ]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="sortOrder" label="Sort Order">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="isMandatory" label="Mandatory Task" valuePropName="checked">
            <Switch checkedChildren="Yes" unCheckedChildren="No" />
          </Form.Item>

          {editingTemplate && (
            <Form.Item name="isActive" label="Active" valuePropName="checked">
              <Switch checkedChildren="Active" unCheckedChildren="Inactive" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  );
}
