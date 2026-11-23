import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Card,
  Tabs,
  Input,
  InputNumber,
  Switch,
  Button,
  Space,
  Typography,
  Alert,
  Spin,
  Empty,
  Tag,
  theme,
} from 'antd';
import { SearchOutlined, UndoOutlined, SaveOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { optionService } from './optionService.js';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

const CATEGORIES = [
  { key: 'GENERAL', label: 'General' },
  { key: 'SECURITY', label: 'Security' },
  { key: 'ATTENDANCE', label: 'Attendance' },
  { key: 'LEAVE', label: 'Leave' },
  { key: 'PAYROLL', label: 'Payroll' },
  { key: 'PORTAL', label: 'Portal' },
];

export function TenantOptionsScreen() {
  const { token } = theme.useToken();
  const canManage = useCan('core.tenant_option.manage');

  const [options, setOptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState(null);
  const [search, setSearch] = useState('');
  const [draftValues, setDraftValues] = useState({});
  const [busyKeys, setBusyKeys] = useState({});

  const loadOptions = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await optionService.list();
      const list = Array.isArray(data) ? data : [];
      setOptions(list);

      const drafts = {};
      for (const opt of list) {
        drafts[opt.key] = opt.value;
      }
      setDraftValues(drafts);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || 'Failed to load tenant options');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOptions();
  }, [loadOptions]);

  const handleUpdate = async (key, newValue) => {
    setBusyKeys((prev) => ({ ...prev, [key]: true }));
    try {
      const updated = await optionService.update(key, newValue);
      setOptions((prev) => prev.map((o) => (o.key === key ? updated : o)));
      setDraftValues((prev) => ({ ...prev, [key]: updated.value }));
      successMsg(`Option "${key}" updated`);
    } catch (err) {
      errorMsg(err?.response?.data?.message || err?.message || 'Failed to update option');
    } finally {
      setBusyKeys((prev) => ({ ...prev, [key]: false }));
    }
  };

  const handleReset = async (key) => {
    setBusyKeys((prev) => ({ ...prev, [key]: true }));
    try {
      const updated = await optionService.reset(key);
      setOptions((prev) => prev.map((o) => (o.key === key ? updated : o)));
      setDraftValues((prev) => ({ ...prev, [key]: updated.value }));
      successMsg(`Option "${key}" reset to default`);
    } catch (err) {
      errorMsg(err?.response?.data?.message || err?.message || 'Failed to reset option');
    } finally {
      setBusyKeys((prev) => ({ ...prev, [key]: false }));
    }
  };

  // Filter options by search
  const filteredOptions = useMemo(() => {
    if (!search.trim()) return options;
    const term = search.trim().toLowerCase();
    return options.filter(
      (o) =>
        (o.label && o.label.toLowerCase().includes(term)) ||
        (o.key && o.key.toLowerCase().includes(term)) ||
        (o.description && o.description.toLowerCase().includes(term))
    );
  }, [options, search]);

  // Group by category, omitting empty categories
  const availableTabs = useMemo(() => {
    const list = [];
    for (const cat of CATEGORIES) {
      const catOptions = filteredOptions.filter((o) => o.category === cat.key);
      if (catOptions.length > 0) {
        list.push({ ...cat, options: catOptions });
      }
    }
    return list;
  }, [filteredOptions]);

  // Set default active tab if current activeTab is not in availableTabs
  useEffect(() => {
    if (availableTabs.length > 0) {
      if (!activeTab || !availableTabs.some((t) => t.key === activeTab)) {
        setActiveTab(availableTabs[0].key);
      }
    } else {
      setActiveTab(null);
    }
  }, [availableTabs, activeTab]);

  return (
    <div className="tenant-options-screen" data-testid="tenant-options-screen">
      <PageHeader title="Options" subtitle="Configure organization-wide tenant switches and defaults" />

      <Space orientation="vertical" size="middle" style={{ width: '100%', marginTop: 16 }}>
        {error && <Alert type="error" message={error} showIcon />}

        <Card variant="borderless" className="saas-card">
          <Input
            prefix={<SearchOutlined style={{ color: token.colorTextTertiary }} />}
            placeholder="Search options by label or description..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            allowClear
            style={{ maxWidth: 400, marginBottom: 16 }}
            data-testid="options-search-input"
          />

          {loading ? (
            <div style={{ padding: 48, textAlign: 'center' }} data-testid="options-loading">
              <Spin size="large" />
            </div>
          ) : availableTabs.length === 0 ? (
            <Empty
              description="No options are available yet."
              data-testid="options-empty"
              style={{ padding: 40 }}
            />
          ) : (
            <Tabs
              activeKey={activeTab}
              onChange={setActiveTab}
              items={availableTabs.map((tab) => ({
                key: tab.key,
                label: tab.label,
                children: (
                  <div className="options-list" data-testid={`options-tab-${tab.key.toLowerCase()}`}>
                    {tab.options.map((opt) => {
                      const isBusy = Boolean(busyKeys[opt.key]);
                      const draft = draftValues[opt.key] !== undefined ? draftValues[opt.key] : opt.value;
                      const hasChanged = draft !== opt.value;

                      return (
                        <Card
                          key={opt.key}
                          size="small"
                          variant="outlined"
                          style={{
                            marginBottom: 12,
                            borderRadius: token.borderRadiusLG,
                            border: opt.overridden ? `1px solid ${token.colorPrimaryBorder}` : undefined,
                          }}
                          data-testid={`option-row-${opt.key}`}
                        >
                          <div
                            style={{
                              display: 'flex',
                              alignItems: 'flex-start',
                              justifyContent: 'space-between',
                              gap: 16,
                              flexWrap: 'wrap',
                            }}
                          >
                            <div style={{ flex: 1, minWidth: 260 }}>
                              <Space align="center" size="small">
                                <Text strong style={{ fontSize: 15 }}>
                                  {opt.label || opt.key}
                                </Text>
                                {opt.overridden ? (
                                  <Tag color="blue">Overridden</Tag>
                                ) : (
                                  <Tag color="default">Default</Tag>
                                )}
                              </Space>
                              <Paragraph
                                type="secondary"
                                style={{ margin: '4px 0 0 0', fontSize: 13 }}
                              >
                                {opt.description}
                              </Paragraph>
                            </div>

                            {/* Controls */}
                            <div style={{ minWidth: 200, display: 'flex', alignItems: 'center', gap: 12 }}>
                              {canManage ? (
                                <Space align="center" wrap data-testid={`option-controls-${opt.key}`}>
                                  {opt.valueType === 'BOOLEAN' && (
                                    <Switch
                                      checked={draft === 'true'}
                                      loading={isBusy}
                                      onChange={(checked) => handleUpdate(opt.key, checked ? 'true' : 'false')}
                                      data-testid={`switch-${opt.key}`}
                                    />
                                  )}

                                  {opt.valueType === 'INTEGER' && (
                                    <Space size="small">
                                      <InputNumber
                                        value={draft ? Number(draft) : undefined}
                                        disabled={isBusy}
                                        onChange={(val) =>
                                          setDraftValues((prev) => ({ ...prev, [opt.key]: String(val) }))
                                        }
                                        data-testid={`number-${opt.key}`}
                                      />
                                      {hasChanged && (
                                        <Button
                                          type="primary"
                                          size="small"
                                          icon={<SaveOutlined />}
                                          loading={isBusy}
                                          onClick={() => handleUpdate(opt.key, draft)}
                                          data-testid={`save-${opt.key}`}
                                        >
                                          Save
                                        </Button>
                                      )}
                                    </Space>
                                  )}

                                  {opt.valueType === 'STRING' && (
                                    <Space size="small">
                                      <Input
                                        value={draft || ''}
                                        disabled={isBusy}
                                        onChange={(e) =>
                                          setDraftValues((prev) => ({ ...prev, [opt.key]: e.target.value }))
                                        }
                                        data-testid={`input-${opt.key}`}
                                      />
                                      {hasChanged && (
                                        <Button
                                          type="primary"
                                          size="small"
                                          icon={<SaveOutlined />}
                                          loading={isBusy}
                                          onClick={() => handleUpdate(opt.key, draft)}
                                          data-testid={`save-${opt.key}`}
                                        >
                                          Save
                                        </Button>
                                      )}
                                    </Space>
                                  )}

                                  {opt.valueType === 'JSON' && (
                                    <Space direction="vertical" size="small" style={{ width: 280 }}>
                                      <TextArea
                                        rows={3}
                                        value={draft || ''}
                                        disabled={isBusy}
                                        onChange={(e) =>
                                          setDraftValues((prev) => ({ ...prev, [opt.key]: e.target.value }))
                                        }
                                        data-testid={`json-${opt.key}`}
                                      />
                                      {hasChanged && (
                                        <Button
                                          type="primary"
                                          size="small"
                                          icon={<SaveOutlined />}
                                          loading={isBusy}
                                          onClick={() => handleUpdate(opt.key, draft)}
                                          data-testid={`save-${opt.key}`}
                                        >
                                          Save
                                        </Button>
                                      )}
                                    </Space>
                                  )}

                                  <Button
                                    size="small"
                                    icon={<UndoOutlined />}
                                    disabled={!opt.overridden || isBusy}
                                    onClick={() => handleReset(opt.key)}
                                    data-testid={`reset-${opt.key}`}
                                  >
                                    Reset to default
                                  </Button>
                                </Space>
                              ) : (
                                <Text style={{ fontFamily: 'monospace' }} data-testid={`readonly-${opt.key}`}>
                                  {opt.value}
                                </Text>
                              )}
                            </div>
                          </div>
                        </Card>
                      );
                    })}
                  </div>
                ),
              }))}
            />
          )}
        </Card>
      </Space>
    </div>
  );
}
