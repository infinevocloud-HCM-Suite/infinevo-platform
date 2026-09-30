import { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Card,
  Tabs,
  Table,
  Button,
  Space,
  Typography,
  Select,
  InputNumber,
  Switch,
  theme,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ArrowUpOutlined,
  ArrowDownOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { useCan, useHasModule, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { definitionService } from './definitionService.js';
import { roleService } from './roleService.js';
import { useEmployeeSearch } from './useEmployeeSearch.js';
import { flowTypesFor } from './flowTypes.js';

const { Title, Text } = Typography;

// What a definition is saved with when the tenant has none yet (W-15.1 §4 defaults).
const DEFAULT_ROUTING = { stepOrdering: 'SEQUENTIAL', commentScope: 'PER_STEP' };

const APPROVER_KINDS = [
  { label: 'Reporting Manager', value: 'REPORTING_MANAGER' },
  { label: 'Indirect Manager', value: 'INDIRECT_MANAGER' },
  { label: 'Approver Level 1', value: 'APPROVER_LEVEL_1' },
  { label: 'Approver Level 2', value: 'APPROVER_LEVEL_2' },
  { label: 'Approver Level 3', value: 'APPROVER_LEVEL_3' },
  { label: 'Role', value: 'ROLE' },
  { label: 'Named Employee', value: 'NAMED_EMPLOYEE' },
  { label: 'Project Manager', value: 'PROJECT_MANAGER' },
];

export function Definitions() {
  const { token } = theme.useToken();
  const canManage = useCan('core.approval_definition.manage');
  const hasHrms = useHasModule('HRMS');
  const hasPayroll = useHasModule('PAYROLL');

  // A Payroll-only tenant sees only its two flows (D-36, D-37, W-46.4 §8)
  const flowTypes = useMemo(() => flowTypesFor({ hasHrms, hasPayroll }), [hasHrms, hasPayroll]);

  const [activeFlow, setActiveFlow] = useState(flowTypes[0]?.key || 'LEAVE');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [steps, setSteps] = useState([]);
  // The loaded definition's routing, sent back unchanged: this screen edits the steps only, and
  // must not turn an any-order or shared-comment flow into the defaults by saving it.
  const [routing, setRouting] = useState(DEFAULT_ROUTING);
  const [roles, setRoles] = useState([]);

  useEffect(() => {
    if (flowTypes.length > 0 && !flowTypes.some((f) => f.key === activeFlow)) {
      setActiveFlow(flowTypes[0].key);
    }
  }, [flowTypes, activeFlow]);

  useEffect(() => {
    roleService
      .list()
      .then((data) => {
        setRoles(Array.isArray(data) ? data : []);
      })
      .catch(() => {});
  }, []);

  // Employee options for NAMED_EMPLOYEE
  const {
    options: employeeOptions,
    loading: searchLoading,
    error: searchError,
    search: searchEmployees,
  } = useEmployeeSearch();

  const loadDefinition = useCallback(
    async (flowType) => {
      if (!canManage) return;
      setLoading(true);
      try {
        const todayStr = new Date().toISOString().split('T')[0];
        const res = await definitionService.list(flowType, todayStr);
        const def = Array.isArray(res)
          ? res.find((d) => d.isActive !== false) || res[0]
          : res;
        setRouting({
          stepOrdering: def?.stepOrdering || DEFAULT_ROUTING.stepOrdering,
          commentScope: def?.commentScope || DEFAULT_ROUTING.commentScope,
        });
        const rawSteps = def?.steps || [];
        const normalizedSteps = rawSteps.map((s, i) => {
          const kind = s.kind || s.approverKind || 'REPORTING_MANAGER';
          const assignee = s.assignee || '';
          return {
            stepIndex: i,
            approverKind: kind,
            assigneeRole:
              kind === 'ROLE' ? assignee || s.assigneeRole || '' : s.assigneeRole || '',
            assigneeEmployeeId:
              kind === 'NAMED_EMPLOYEE'
                ? assignee || s.assigneeEmployeeId || ''
                : s.assigneeEmployeeId || '',
            escalateAfterDays: s.escalate_after_days ?? s.escalateAfterDays ?? 3,
            perItem: s.per_item ?? s.perItem ?? false,
          };
        });
        setSteps(normalizedSteps);
      } catch (err) {
        await errorMsg(err);
        setSteps([]);
        setRouting(DEFAULT_ROUTING);
      } finally {
        setLoading(false);
      }
    },
    [canManage]
  );

  useEffect(() => {
    if (canManage) {
      loadDefinition(activeFlow);
    }
  }, [activeFlow, loadDefinition, canManage]);

  if (!canManage) {
    return <NotEntitled />;
  }

  const handleAddStep = () => {
    const nextIndex = steps.length;
    setSteps([
      ...steps,
      {
        stepIndex: nextIndex,
        approverKind: 'REPORTING_MANAGER',
        assigneeRole: '',
        assigneeEmployeeId: '',
        escalateAfterDays: 3,
        perItem: false,
      },
    ]);
  };

  const handleRemoveStep = (index) => {
    const updated = steps
      .filter((_, i) => i !== index)
      .map((s, i) => ({ ...s, stepIndex: i }));
    setSteps(updated);
  };

  const handleMoveStep = (index, direction) => {
    const target = index + direction;
    if (target < 0 || target >= steps.length) return;
    const copy = [...steps];
    const temp = copy[index];
    copy[index] = copy[target];
    copy[target] = temp;
    setSteps(copy.map((s, i) => ({ ...s, stepIndex: i })));
  };

  const handleUpdateStep = (index, field, value) => {
    const copy = [...steps];
    copy[index] = { ...copy[index], [field]: value };
    setSteps(copy);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = {
        stepOrdering: routing.stepOrdering,
        commentScope: routing.commentScope,
        effectiveFrom: new Date().toISOString().split('T')[0],
        isActive: true,
        steps: steps.map((s) => ({
          kind: s.approverKind,
          assignee:
            s.approverKind === 'ROLE'
              ? s.assigneeRole
              : s.approverKind === 'NAMED_EMPLOYEE'
              ? s.assigneeEmployeeId
              : null,
          escalate_after_days: s.escalateAfterDays ?? 3,
          per_item: Boolean(s.perItem),
        })),
      };
      await definitionService.save(activeFlow, payload);
      await successMsg('Definition Saved', `Approval definition for ${activeFlow} updated.`);
      loadDefinition(activeFlow);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: 'Step',
      dataIndex: 'stepIndex',
      key: 'stepIndex',
      width: 70,
      render: (index) => `Step ${index + 1}`,
    },
    {
      title: 'Approver Kind',
      dataIndex: 'approverKind',
      key: 'approverKind',
      width: 220,
      render: (kind, _, index) => (
        <Select
          value={kind}
          style={{ width: '100%' }}
          disabled={!canManage}
          options={APPROVER_KINDS}
          onChange={(val) => handleUpdateStep(index, 'approverKind', val)}
          id={`select-kind-${index}`}
        />
      ),
    },
    {
      title: 'Assignee (Role / Employee)',
      key: 'assignee',
      render: (_, record, index) => {
        if (record.approverKind === 'ROLE') {
          return (
            <Select
              id={`select-role-${index}`}
              style={{ width: '100%' }}
              placeholder="Select role"
              value={record.assigneeRole || undefined}
              disabled={!canManage}
              options={roles.map((r) => ({
                label: r.name ? `${r.name} (${r.code})` : r.code,
                value: r.code,
              }))}
              onChange={(val) => handleUpdateStep(index, 'assigneeRole', val)}
            />
          );
        }
        if (record.approverKind === 'NAMED_EMPLOYEE') {
          return (
            <Select
              id={`input-employee-${index}`}
              style={{ width: '100%' }}
              showSearch
              filterOption={false}
              placeholder="Search employee by name or ID"
              value={record.assigneeEmployeeId || undefined}
              disabled={!canManage}
              loading={searchLoading}
              onSearch={searchEmployees}
              onFocus={() => { if (employeeOptions.length === 0) searchEmployees(''); }}
              onChange={(val) => handleUpdateStep(index, 'assigneeEmployeeId', val)}
              options={employeeOptions}
              notFoundContent={searchError || undefined}
            />
          );
        }
        return <Text type="secondary">Determined at runtime</Text>;
      },
    },
    {
      title: 'Escalate After (Days)',
      dataIndex: 'escalateAfterDays',
      key: 'escalateAfterDays',
      width: 170,
      render: (days, _, index) => (
        <InputNumber
          id={`input-escalate-${index}`}
          min={0}
          max={90}
          value={days}
          disabled={!canManage}
          onChange={(val) => handleUpdateStep(index, 'escalateAfterDays', val)}
        />
      ),
    },
    {
      title: 'Per Item',
      dataIndex: 'perItem',
      key: 'perItem',
      width: 100,
      render: (perItem, _, index) => (
        <Switch
          id={`switch-per-item-${index}`}
          checked={perItem}
          disabled={!canManage}
          onChange={(checked) => handleUpdateStep(index, 'perItem', checked)}
        />
      ),
    },
  ];

  if (canManage) {
    columns.push({
      title: 'Order / Delete',
      key: 'actions',
      width: 140,
      render: (_, record, index) => (
        <Space size="small">
          <Button
            size="small"
            icon={<ArrowUpOutlined />}
            disabled={index === 0}
            onClick={() => handleMoveStep(index, -1)}
            id={`btn-move-up-${index}`}
          />
          <Button
            size="small"
            icon={<ArrowDownOutlined />}
            disabled={index === steps.length - 1}
            onClick={() => handleMoveStep(index, 1)}
            id={`btn-move-down-${index}`}
          />
          <Button
            danger
            size="small"
            type="text"
            icon={<DeleteOutlined />}
            onClick={() => handleRemoveStep(index)}
            id={`btn-remove-step-${index}`}
          />
        </Space>
      ),
    });
  }

  return (
    <Card style={{ margin: token.marginLG }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: token.marginLG,
        }}
      >
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Approval Definitions
          </Title>
          <Text type="secondary">
            Configure multi-step approval workflows across organization flow types.
          </Text>
        </div>

        {canManage && (
          <Space>
            <Button
              icon={<PlusOutlined />}
              onClick={handleAddStep}
              id="btn-add-step"
            >
              Add Step
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSave}
              loading={saving}
              id="btn-save-definition"
            >
              Save Definition
            </Button>
          </Space>
        )}
      </div>

      <Tabs
        activeKey={activeFlow}
        onChange={(key) => setActiveFlow(key)}
        items={flowTypes.map((f) => ({ key: f.key, label: f.label }))}
      />

      <Table
        dataSource={steps}
        columns={columns}
        rowKey="stepIndex"
        pagination={false}
        loading={loading}
      />
    </Card>
  );
}
