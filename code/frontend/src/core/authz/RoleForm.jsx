import { useEffect, useState, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Card,
  Form,
  Input,
  Button,
  Typography,
  Space,
  Checkbox,
  Row,
  Col,
  Divider,
  Spin,
  Alert,
  theme,
} from 'antd';
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { roleService } from './roleService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text, Paragraph } = Typography;

export function RoleForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const canManage = useCan('core.role.manage');

  const isNew = !id || id === 'new';
  const [form] = Form.useForm();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [actions, setActions] = useState([]);
  const [selectedActions, setSelectedActions] = useState([]);
  const [existingRole, setExistingRole] = useState(null);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const actionList = await roleService.listActions();
        setActions(Array.isArray(actionList) ? actionList : []);

        if (!isNew) {
          const roleData = await roleService.get(id);
          setExistingRole(roleData);
          form.setFieldsValue({
            name: roleData.name,
            code: roleData.code,
          });
          setSelectedActions(roleData.actionCodes || []);
        }
      } catch (err) {
        errorMsg(err);
      } finally {
        setLoading(false);
      }
    }

    if (canManage) {
      loadData();
    }
  }, [id, isNew, canManage, form]);

  if (!canManage) {
    return <NotEntitled action="core.role.manage" />;
  }

  // Group actions by module
  const actionsByModule = useMemo(() => {
    const groups = {};
    for (const act of actions) {
      const mod = act.module || 'other';
      if (!groups[mod]) {
        groups[mod] = [];
      }
      groups[mod].push(act);
    }
    return groups;
  }, [actions]);

  const handleToggleAction = (actionCode) => {
    setSelectedActions((prev) =>
      prev.includes(actionCode)
        ? prev.filter((c) => c !== actionCode)
        : [...prev, actionCode],
    );
  };

  const handleToggleModuleAll = (moduleActions) => {
    const codes = moduleActions.map((a) => a.code);
    const allSelected = codes.every((c) => selectedActions.includes(c));
    if (allSelected) {
      setSelectedActions((prev) => prev.filter((c) => !codes.includes(c)));
    } else {
      setSelectedActions((prev) => Array.from(new Set([...prev, ...codes])));
    }
  };

  const handleSubmit = async (values) => {
    setSaving(true);
    try {
      const payload = {
        name: values.name.trim(),
        actionCodes: selectedActions,
      };

      if (isNew) {
        if (values.code?.trim()) {
          payload.code = values.code.trim();
        }
        await roleService.create(payload);
        await successMsg('Role Created', `Custom role "${payload.name}" has been created.`);
      } else {
        await roleService.update(id, payload);
        await successMsg('Role Updated', `Custom role "${payload.name}" has been updated.`);
      }
      navigate('/roles');
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ padding: '0 0 24px 0', maxWidth: 960, margin: '0 auto' }}>
      <Card variant="borderless" style={{ borderRadius: token.borderRadiusLG, marginBottom: token.marginLG }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <Space>
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/roles')}
            />
            <div>
              <Title level={4} style={{ margin: 0 }}>
                {isNew ? 'Create New Role' : `Edit Role: ${existingRole?.name || ''}`}
              </Title>
              <Paragraph type="secondary" style={{ margin: 0, marginTop: 4 }}>
                {isNew
                  ? 'Define a new role and choose the permissions granted to members.'
                  : 'Update permissions and name for this role.'}
              </Paragraph>
            </div>
          </Space>
          <Space>
            <Button onClick={() => navigate('/roles')}>Cancel</Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={saving}
              onClick={() => form.submit()}
              id="btn-save-role"
            >
              Save Role
            </Button>
          </Space>
        </div>
      </Card>

      {existingRole?.system && (
        <Alert
          type="warning"
          showIcon
          message="System Role"
          description="This is a seeded system role. Modifications to system roles are restricted by security policy."
          style={{ marginBottom: token.marginLG }}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        disabled={existingRole?.system}
      >
        <Card title="Role Information" variant="borderless" style={{ borderRadius: token.borderRadiusLG, marginBottom: token.marginLG }}>
          <Row gutter={16}>
            <Col xs={24} md={12}>
              <Form.Item
                name="name"
                label="Role Name"
                rules={[{ required: true, message: 'Please enter a role name' }]}
              >
                <Input placeholder="e.g. Payroll Reviewer, HR Operations" maxLength={100} id="input-role-name" />
              </Form.Item>
            </Col>
            <Col xs={24} md={12}>
              <Form.Item
                name="code"
                label="Role Code"
                tooltip={isNew ? 'Leave blank to generate automatically from name' : 'Role codes cannot be changed once created.'}
              >
                <Input
                  placeholder={isNew ? 'e.g. payroll-reviewer (optional)' : ''}
                  disabled={!isNew}
                  maxLength={64}
                  id="input-role-code"
                />
              </Form.Item>
            </Col>
          </Row>
        </Card>

        <Card
          title={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Permissions ({selectedActions.length} selected)</span>
              <Button
                size="small"
                onClick={() => {
                  const allCodes = actions.map((a) => a.code);
                  setSelectedActions(selectedActions.length === allCodes.length ? [] : allCodes);
                }}
              >
                {selectedActions.length === actions.length ? 'Deselect All' : 'Select All'}
              </Button>
            </div>
          }
          variant="borderless"
          style={{ borderRadius: token.borderRadiusLG }}
        >
          {Object.entries(actionsByModule).map(([modName, modActions]) => {
            const allModSelected = modActions.every((a) => selectedActions.includes(a.code));
            const someModSelected = modActions.some((a) => selectedActions.includes(a.code));

            return (
              <div key={modName} style={{ marginBottom: 20 }}>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: 12 }}>
                  <Checkbox
                    checked={allModSelected}
                    indeterminate={someModSelected && !allModSelected}
                    onChange={() => handleToggleModuleAll(modActions)}
                  >
                    <Text strong style={{ textTransform: 'uppercase', fontSize: 13, letterSpacing: '0.5px' }}>
                      {modName} Module ({modActions.length})
                    </Text>
                  </Checkbox>
                </div>

                <Row gutter={[12, 12]} style={{ paddingLeft: 24 }}>
                  {modActions.map((act) => (
                    <Col xs={24} sm={12} lg={8} key={act.code}>
                      <Checkbox
                        checked={selectedActions.includes(act.code)}
                        onChange={() => handleToggleAction(act.code)}
                      >
                        <Space orientation="vertical" size={0}>
                          <Text style={{ fontSize: 13 }}>{act.name || act.code}</Text>
                          <Text type="secondary" style={{ fontSize: 11, fontFamily: 'monospace' }}>
                            {act.code}
                          </Text>
                        </Space>
                      </Checkbox>
                    </Col>
                  ))}
                </Row>
                <Divider style={{ margin: '16px 0' }} />
              </div>
            );
          })}
        </Card>
      </Form>
    </div>
  );
}
export default RoleForm;
