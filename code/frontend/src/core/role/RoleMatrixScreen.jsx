import { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Card,
  Row,
  Col,
  Space,
  Typography,
  Tag,
  Button,
  Modal,
  Form,
  Input,
  Checkbox,
  Drawer,
  Spin,
  Alert,
  Popconfirm,
  Badge,
  Collapse,
} from 'antd';
import {
  SafetyOutlined,
  PlusOutlined,
  ReloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  LockOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { roleService } from './roleService';
import { useCan } from '@shell/authz/useCan';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { readError } from '../../payroll/tax/apiError';

const { Title, Text, Paragraph } = Typography;

export function RoleMatrixScreen() {
  const [roles, setRoles] = useState([]);
  const [allActions, setAllActions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const canManage = useCan('core.role.manage');

  // Create role modal state
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();

  // View permissions drawer state
  const [viewDrawerVisible, setViewDrawerVisible] = useState(false);
  const [activeRole, setActiveRole] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [rolesList, actionsList] = await Promise.all([
        roleService.list(),
        roleService.listActions(),
      ]);
      setRoles(rolesList || []);
      setAllActions(actionsList || []);
    } catch (err) {
      setError(readError(err, 'Failed to load roles and permissions').message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleCreateRole = async (values) => {
    setCreating(true);
    try {
      const payload = {
        name: values.name.trim(),
        code: values.code ? values.code.trim().toLowerCase() : undefined,
        actionCodes: values.actionCodes || [],
      };
      await roleService.create(payload);
      successMsg('Role Created', `Custom role "${payload.name}" created successfully`);
      setCreateModalVisible(false);
      form.resetFields();
      loadData();
    } catch (err) {
      errorMsg(readError(err, 'Failed to create role'));
    } finally {
      setCreating(false);
    }
  };

  const handleDeleteRole = async (role) => {
    try {
      await roleService.delete(role.id);
      successMsg('Role Deleted', `Role "${role.name}" was deleted`);
      loadData();
    } catch (err) {
      errorMsg(readError(err, 'Failed to delete role'));
    }
  };

  // Group actions by module for the creation checkboxes
  const actionsByModule = allActions.reduce((acc, act) => {
    const mod = act.module || 'core';
    if (!acc[mod]) acc[mod] = [];
    acc[mod].push(act);
    return acc;
  }, {});

  const columns = [
    {
      title: 'Role Name',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => (
        <div>
          <Space>
            {record.system ? <LockOutlined style={{ color: '#1677ff' }} /> : <SafetyOutlined style={{ color: '#722ed1' }} />}
            <Text strong>{text}</Text>
          </Space>
          <br />
          <Text type="secondary" style={{ fontSize: 12, marginLeft: 20 }}>
            <code>{record.code}</code>
          </Text>
        </div>
      ),
    },
    {
      title: 'Classification',
      dataIndex: 'system',
      key: 'system',
      width: 140,
      render: (isSystem) => (
        <Tag color={isSystem ? 'blue' : 'purple'}>
          {isSystem ? 'System Role' : 'Custom Role'}
        </Tag>
      ),
    },
    {
      title: 'Permissions Granted',
      key: 'actionCount',
      width: 180,
      render: (_, record) => {
        const count = record.actionCodes?.length || 0;
        return (
          <Space>
            <Badge count={count} showZero color={count > 0 ? '#52c41a' : '#d9d9d9'} />
            <Text type="secondary">{count === 1 ? '1 Action' : `${count} Actions`}</Text>
          </Space>
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      render: (_, record) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setActiveRole(record);
              setViewDrawerVisible(true);
            }}
            data-testid={`btn-view-role-${record.code}`}
          >
            View
          </Button>

          {!record.system && canManage && (
            <Popconfirm
              title={`Delete role "${record.name}"?`}
              description="This cannot be undone. Any users assigned this role will lose its permissions."
              onConfirm={() => handleDeleteRole(record)}
              okText="Delete"
              cancelText="Cancel"
            >
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                data-testid={`btn-del-role-${record.code}`}
              />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto', padding: '24px 16px' }}>
      <Card
        title={
          <Row justify="space-between" align="middle" wrap gutter={[12, 12]}>
            <Col>
              <Space align="center">
                <SafetyOutlined style={{ fontSize: 22, color: '#1677ff' }} />
                <div>
                  <Title level={4} style={{ margin: 0 }}>
                    Roles & Permissions Management
                  </Title>
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    Configure system access levels and custom roles for your organisation
                  </Text>
                </div>
              </Space>
            </Col>
            <Col>
              <Space>
                <Button icon={<ReloadOutlined />} onClick={loadData} disabled={loading}>
                  Reload
                </Button>
                {canManage && (
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    onClick={() => setCreateModalVisible(true)}
                    data-testid="btn-create-role"
                  >
                    Create Custom Role
                  </Button>
                )}
              </Space>
            </Col>
          </Row>
        }
      >
        {error && (
          <Alert
            message="Error"
            description={error}
            type="error"
            showIcon
            closable
            onClose={() => setError(null)}
            style={{ marginBottom: 16 }}
          />
        )}

        <Table
          dataSource={roles}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: 'No roles found in current tenant.' }}
        />
      </Card>

      {/* Create Custom Role Modal */}
      <Modal
        title={
          <Space>
            <PlusOutlined style={{ color: '#1677ff' }} />
            <span>Create New Custom Role</span>
          </Space>
        }
        open={createModalVisible}
        onCancel={() => setCreateModalVisible(false)}
        onOk={() => form.submit()}
        confirmLoading={creating}
        width={700}
        okText="Create Role"
      >
        <Form form={form} layout="vertical" onFinish={handleCreateRole} style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Role Name"
            rules={[{ required: true, message: 'Please enter role name' }]}
            extra="e.g. Leave Auditor, Payroll Reviewer"
          >
            <Input placeholder="Enter role display name" data-testid="input-role-name" />
          </Form.Item>

          <Form.Item
            name="code"
            label="Role Code (Optional)"
            extra="Leave blank to automatically derive from name (e.g. leave-auditor)"
          >
            <Input placeholder="e.g. leave-auditor" data-testid="input-role-code" />
          </Form.Item>

          <Divider orientation="left">Assign Permission Actions</Divider>

          <Form.Item name="actionCodes" initialValue={[]}>
            <Checkbox.Group style={{ width: '100%' }}>
              <Collapse defaultActiveKey={['core', 'payroll', 'hrms']}>
                {Object.entries(actionsByModule).map(([mod, acts]) => (
                  <Collapse.Panel
                    key={mod}
                    header={
                      <Space>
                        <Tag color="blue">{mod.toUpperCase()}</Tag>
                        <Text strong>{acts.length} Actions</Text>
                      </Space>
                    }
                  >
                    <Row gutter={[12, 12]}>
                      {acts.map((act) => (
                        <Col span={12} key={act.code}>
                          <Checkbox value={act.code} data-testid={`chk-action-${act.code}`}>
                            <Text strong style={{ fontSize: 13 }}>
                              {act.name || act.code}
                            </Text>
                            <br />
                            <Text type="secondary" style={{ fontSize: 11 }}>
                              <code>{act.code}</code>
                            </Text>
                          </Checkbox>
                        </Col>
                      ))}
                    </Row>
                  </Collapse.Panel>
                ))}
              </Collapse>
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </Modal>

      {/* View Role Permissions Drawer */}
      <Drawer
        title={
          <Space>
            <SafetyOutlined style={{ color: '#1677ff' }} />
            <span>Role Permissions: <Text strong>{activeRole?.name}</Text></span>
          </Space>
        }
        open={viewDrawerVisible}
        onClose={() => setViewDrawerVisible(false)}
        width={650}
      >
        {activeRole && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary">Role Code: </Text>
              <code>{activeRole.code}</code>
              <br />
              <Text type="secondary">Classification: </Text>
              <Tag color={activeRole.system ? 'blue' : 'purple'}>
                {activeRole.system ? 'System Role (Immutable)' : 'Custom Role'}
              </Tag>
            </div>

            <Divider orientation="left">
              Active Actions ({activeRole.actionCodes?.length || 0})
            </Divider>

            <div style={{ maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' }}>
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {activeRole.actionCodes?.map((code) => {
                  const details = allActions.find((a) => a.code === code);
                  return (
                    <Card size="small" key={code} style={{ background: '#fafafa' }}>
                      <Row justify="space-between" align="middle">
                        <Col>
                          <Space>
                            <CheckCircleOutlined style={{ color: '#52c41a' }} />
                            <Text strong>{details?.name || code}</Text>
                          </Space>
                          <br />
                          <Text type="secondary" style={{ fontSize: 12, marginLeft: 20 }}>
                            <code>{code}</code>
                          </Text>
                        </Col>
                        <Col>
                          <Tag color="cyan">{details?.module || code.split('.')[0]}</Tag>
                        </Col>
                      </Row>
                      {details?.description && (
                        <Paragraph type="secondary" style={{ fontSize: 12, margin: '6px 0 0 20px' }}>
                          {details.description}
                        </Paragraph>
                      )}
                    </Card>
                  );
                })}
              </Space>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
}

export default RoleMatrixScreen;
