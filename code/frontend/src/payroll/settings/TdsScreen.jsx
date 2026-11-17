import { useState, useEffect, useCallback } from 'react';
import { Card, Button, Switch, Alert, Spin, Space, Typography, message, theme } from 'antd';
import { AuditOutlined, SaveOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { statutoryService } from './statutoryService.js';

const { Text } = Typography;

/**
 * The company TDS switch (D-141). Off: the pay run writes no TDS line for any employee of the
 * company; employee TDS records and declarations are left as they are.
 */
export function TdsScreen() {
  const { token } = theme.useToken();
  const canManage = useCan('payroll.settings.manage');

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [source, setSource] = useState(null);
  const [enabled, setEnabled] = useState(true);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await statutoryService.get('tds');
      if (data) {
        setSource(data.source || null);
        setEnabled(data.enabled !== false);
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
        <Text type="secondary">
          Off: no TDS line is written in any pay run for this company. Employee TDS records and declarations stay as
          they are.
        </Text>
      </div>

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
