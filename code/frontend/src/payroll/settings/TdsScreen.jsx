import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Card, Button, Switch, Alert, Spin, Space, Typography, message, theme } from 'antd';
import { AuditOutlined, SaveOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { statutoryService } from './statutoryService.js';

const { Text } = Typography;

/** D-145: what off means, under the switch. */
const TDS_PAUSE_NOTE =
  'Off pauses TDS for everyone. Employee settings are kept and apply again when it is turned on.';

function count(value) {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/**
 * The company TDS switch (D-141). Off: the pay run writes no TDS line for any employee of the
 * company; employee TDS records and declarations are left as they are. D-145: the screen says how many
 * employees TDS applies to, and that off is a pause that keeps each employee's setting.
 */
export function TdsScreen() {
  const { token } = theme.useToken();
  const canManage = useCan('payroll.settings.manage');

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [source, setSource] = useState(null);
  const [enabled, setEnabled] = useState(true);
  const [counts, setCounts] = useState({ applicable: null, employees: null });

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await statutoryService.get('tds');
      if (data) {
        setSource(data.source || null);
        setEnabled(data.enabled !== false);
        setCounts({ applicable: count(data.applicable_count), employees: count(data.employee_count) });
      }
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load TDS settings');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const updated = await statutoryService.save('tds', { enabled });
      if (updated) {
        setSource(updated.source || null);
        setEnabled(updated.enabled !== false);
      }
      message.success('TDS settings saved successfully');
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to save TDS settings');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <Card
      variant="borderless"
      title={
        <Space>
          <AuditOutlined style={{ color: token.colorPrimary }} />
          <span>TDS</span>
        </Space>
      }
    >
      {source === 'DEFAULT' && (
        <Alert
          type="info"
          showIcon
          message="Not set yet, TDS is deducted by default"
          style={{ marginBottom: 24 }}
          data-testid="tds-default-source-banner"
        />
      )}

      <Space align="center" style={{ marginBottom: 8 }}>
        <Switch
          id="switch-tds-enabled"
          checked={enabled}
          disabled={!canManage}
          onChange={(checked) => setEnabled(checked)}
        />
        <label htmlFor="switch-tds-enabled">Deduct TDS in pay runs</label>
      </Space>
      <div>
        <Text type="secondary" data-testid="tds-pause-note">
          {TDS_PAUSE_NOTE}
        </Text>
      </div>
      {counts.employees !== null && counts.applicable !== null && (
        <div style={{ marginTop: 12 }}>
          <Link to="/employees" id="link-tds-applicable-count" data-testid="tds-applicable-count">
            TDS applicable for {counts.applicable} of {counts.employees} employees
          </Link>
        </div>
      )}

      {canManage && (
        <Button
          id="btn-save-tds"
          type="primary"
          className="btn-spring"
          icon={<SaveOutlined />}
          loading={saving}
          onClick={handleSave}
          style={{ marginTop: 24 }}
        >
          Save
        </Button>
      )}
    </Card>
  );
}

export default TdsScreen;
