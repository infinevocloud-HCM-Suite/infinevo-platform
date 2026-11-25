import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Table,
  Tag,
  Switch,
  Input,
  Button,
  Typography,
  Space,
  Alert,
  Spin,
  message,
  theme,
  Empty,
} from 'antd';
import { TeamOutlined, SaveOutlined } from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { lwfService } from './lwfService.js';

const { Text } = Typography;

export function LwfSetting() {
  const { token } = theme.useToken();
  const canManage = useCan('payroll.lwf.manage');

  const [loading, setLoading] = useState(true);
  const [states, setStates] = useState([]);
  const [error, setError] = useState(null);
  const [editingReg, setEditingReg] = useState({});
  const [savingState, setSavingState] = useState(null);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await lwfService.list();
      setStates(Array.isArray(data) ? data : []);
      const regs = {};
      (Array.isArray(data) ? data : []).forEach((s) => {
        regs[s.stateCode] = s.registrationNumber || '';
      });
      setEditingReg(regs);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load LWF settings');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  const handleToggleEnable = async (record, checked) => {
    setSavingState(record.stateCode);
    try {
      await lwfService.update(record.stateCode, {
        isEnabled: checked,
        registrationNumber: editingReg[record.stateCode],
      });
      message.success(`LWF for ${record.stateName} ${checked ? 'enabled' : 'disabled'}`);
      setStates((prev) =>
        prev.map((s) => (s.stateCode === record.stateCode ? { ...s, isEnabled: checked } : s))
      );
    } catch (err) {
      message.error(err?.response?.data?.message || 'Failed to update LWF state');
    } finally {
      setSavingState(null);
    }
  };

  const handleSaveRegistration = async (stateCode) => {
    setSavingState(stateCode);
    try {
      const reg = editingReg[stateCode];
      await lwfService.update(stateCode, {
        registrationNumber: reg,
      });
      message.success(`Registration number saved for ${stateCode}`);
      setStates((prev) =>
        prev.map((s) => (s.stateCode === stateCode ? { ...s, registrationNumber: reg } : s))
      );
    } catch (err) {
      message.error(err?.response?.data?.message || 'Failed to save registration number');
    } finally {
      setSavingState(null);
    }
  };

  const formatCycle = (cycle) => {
    switch (cycle) {
      case 'MONTHLY':
        return 'Monthly';
      case 'HALF_YEARLY_JUNE_DECEMBER':
        return 'Half-Yearly (Jun & Dec)';
      case 'ANNUAL_DECEMBER':
        return 'Annual (Dec)';
      default:
        return cycle || 'None';
    }
  };

  const formatRule = (record) => {
    if (!record.leviesLwf) {
      return <Text type="secondary">State does not levy Labour Welfare Fund</Text>;
    }
    if (record.calcMode === 'PERCENTAGE_WITH_CAP') {
      return (
        <span>
          <Text strong>0.20%</Text> of Gross (EE Cap: ₹{record.employeeCap || 25} / ER Cap: ₹{record.employerCap || 50})
        </span>
      );
    }
    const ee = record.employeeContribution != null ? Number(record.employeeContribution).toFixed(2) : '0.00';
    const er = record.employerContribution != null ? Number(record.employerContribution).toFixed(2) : '0.00';
    return (
      <span>
        Employee: <Text strong>₹{ee}</Text> | Employer: <Text strong>₹{er}</Text>
      </span>
    );
  };

  const columns = [
    {
      title: 'State',
      dataIndex: 'stateName',
      key: 'stateName',
      render: (name, record) => (
        <Space direction="vertical" size={2}>
          <Space>
            <Text strong>{name}</Text>
            <Tag>{record.stateCode}</Tag>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {record.activeLocationsCount} active work location{record.activeLocationsCount !== 1 ? 's' : ''}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Deduction Schedule',
      dataIndex: 'deductionCycle',
      key: 'deductionCycle',
      render: (cycle, record) => (
        record.leviesLwf ? (
          <Tag color="blue">{formatCycle(cycle)}</Tag>
        ) : (
          <Tag color="default">Exempt</Tag>
        )
      ),
    },
    {
      title: 'Statutory Contribution Rules',
      key: 'rules',
      render: (_, record) => formatRule(record),
    },
    {
      title: 'Registration / Establishment Code',
      key: 'registrationNumber',
      render: (_, record) => (
        <Space>
          <Input
            placeholder="e.g. MH-LWF-12345"
            value={editingReg[record.stateCode] ?? ''}
            onChange={(e) =>
              setEditingReg((prev) => ({ ...prev, [record.stateCode]: e.target.value }))
            }
            disabled={!canManage || !record.leviesLwf}
            style={{ width: 180 }}
            data-testid={`reg-input-${record.stateCode}`}
          />
          {canManage && record.leviesLwf && (
            <Button
              icon={<SaveOutlined />}
              size="small"
              onClick={() => handleSaveRegistration(record.stateCode)}
              loading={savingState === record.stateCode}
              data-testid={`save-reg-btn-${record.stateCode}`}
            />
          )}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'isEnabled',
      key: 'isEnabled',
      render: (isEnabled, record) => (
        <Space>
          <Switch
            checked={isEnabled}
            disabled={!canManage || !record.leviesLwf}
            loading={savingState === record.stateCode}
            onChange={(checked) => handleToggleEnable(record, checked)}
            data-testid={`enable-switch-${record.stateCode}`}
          />
          <Text type={isEnabled ? undefined : 'secondary'}>
            {isEnabled ? 'Enabled' : 'Disabled'}
          </Text>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Labour Welfare Fund (LWF)"
        subtitle="Manage state Labour Welfare Fund registrations, statutory schedules, and compliance"
        icon={<TeamOutlined />}
      />

      {error && (
        <Alert
          message="Error"
          description={error}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      <Card
        style={{
          borderRadius: 8,
          border: `1px solid ${token.colorBorderSecondary}`,
        }}
        styles={{ body: { padding: 0 } }}
      >
        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin size="large" />
          </div>
        ) : states.length === 0 ? (
          <div style={{ padding: 48, textAlign: 'center' }}>
            <Empty
              description="No active work locations found. Set up work locations to configure state LWF."
            />
          </div>
        ) : (
          <Table
            dataSource={states}
            columns={columns}
            rowKey="stateCode"
            pagination={false}
          />
        )}
      </Card>
    </div>
  );
}

