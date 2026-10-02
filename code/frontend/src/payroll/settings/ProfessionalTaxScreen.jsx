import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Collapse,
  Table,
  Tag,
  Button,
  Space,
  Drawer,
  Form,
  Input,
  InputNumber,
  DatePicker,
  Popconfirm,
  Typography,
  Spin,
  Alert,
  Checkbox,
  Row,
  Col,
  Divider,
  message,
  theme,
} from 'antd';
import {
  BankOutlined,
  EditOutlined,
  DeleteOutlined,
  HistoryOutlined,
  PlusOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { ptService } from './ptService.js';

const { Text } = Typography;

const MONTH_OPTIONS = [
  { label: 'Jan', value: 1 },
  { label: 'Feb', value: 2 },
  { label: 'Mar', value: 3 },
  { label: 'Apr', value: 4 },
  { label: 'May', value: 5 },
  { label: 'Jun', value: 6 },
  { label: 'Jul', value: 7 },
  { label: 'Aug', value: 8 },
  { label: 'Sep', value: 9 },
  { label: 'Oct', value: 10 },
  { label: 'Nov', value: 11 },
  { label: 'Dec', value: 12 },
];

export function ProfessionalTaxScreen() {
  const { token } = theme.useToken();
  const [form] = Form.useForm();

  const [loading, setLoading] = useState(true);
  const [states, setStates] = useState([]);
  const [error, setError] = useState(null);

  // Override Drawer state
  const [overrideDrawerOpen, setOverrideDrawerOpen] = useState(false);
  const [selectedState, setSelectedState] = useState(null);
  const [overrideSaving, setOverrideSaving] = useState(false);

  // History Drawer state
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [historyState, setHistoryState] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyData, setHistoryData] = useState([]);

  const loadStates = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await ptService.list();
      const list = Array.isArray(res) ? res : res?.items || [];
      setStates(list);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load professional tax settings');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadStates();
  }, [loadStates]);

  const openOverrideDrawer = (stateRecord) => {
    setSelectedState(stateRecord);
    setOverrideDrawerOpen(true);

    const initialSlabs = (stateRecord.slabs && stateRecord.slabs.length > 0)
      ? stateRecord.slabs.map((s) => ({
          from_amount: s.from_amount,
          to_amount: s.to_amount,
          amount: s.amount,
          is_female_exempt: !!s.is_female_exempt,
          deduction_months: s.deduction_months || [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
        }))
      : [
          {
            from_amount: 0,
            to_amount: null,
            amount: 0,
            is_female_exempt: false,
            deduction_months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
          },
        ];

    form.setFieldsValue({
      registration_number: stateRecord.registration_number || '',
      effective_from: stateRecord.effective_from ? dayjs(stateRecord.effective_from) : dayjs(),
      slabs: initialSlabs,
    });
  };

  const closeOverrideDrawer = () => {
    setOverrideDrawerOpen(false);
    setSelectedState(null);
    form.resetFields();
  };

  const handleSaveOverride = async (values) => {
    if (!selectedState) return;
    setOverrideSaving(true);
    try {
      const payload = {
        registration_number: values.registration_number ? values.registration_number.trim() : null,
        effective_from: values.effective_from
          ? (typeof values.effective_from === 'string' ? values.effective_from : values.effective_from.format('YYYY-MM-DD'))
          : dayjs().format('YYYY-MM-DD'),
        slabs: (values.slabs || []).map((s) => ({
          from_amount: Number(s.from_amount),
          to_amount: s.to_amount != null && s.to_amount !== '' ? Number(s.to_amount) : null,
          amount: Number(s.amount),
          is_female_exempt: !!s.is_female_exempt,
          deduction_months: s.deduction_months || [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
        })),
      };

      await ptService.override(selectedState.state_code, payload);
      message.success(`Override saved for ${selectedState.state_name || selectedState.state_code}`);
      closeOverrideDrawer();
      loadStates();
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to save override');
    } finally {
      setOverrideSaving(false);
    }
  };

  const handleRemoveOverride = async (stateCode, stateName) => {
    try {
      await ptService.removeOverride(stateCode);
      message.success(`Override removed for ${stateName || stateCode}`);
      loadStates();
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to remove override');
    }
  };

  const openHistoryDrawer = async (stateRecord) => {
    setHistoryState(stateRecord);
    setHistoryDrawerOpen(true);
    setHistoryLoading(true);
    try {
      const res = await ptService.history(stateRecord.state_code);
      const items = Array.isArray(res) ? res : res?.items || [];
      setHistoryData(items);
    } catch (err) {
      message.error(err.response?.data?.message || err.message || 'Failed to load history');
      setHistoryData([]);
    } finally {
      setHistoryLoading(false);
    }
  };

  const closeHistoryDrawer = () => {
    setHistoryDrawerOpen(false);
    setHistoryState(null);
    setHistoryData([]);
  };

  const slabColumns = [
    {
      title: 'From Amount (₹)',
      dataIndex: 'from_amount',
      key: 'from_amount',
      render: (val) => (val != null ? `₹${val}` : '₹0'),
    },
    {
      title: 'To Amount (₹)',
      dataIndex: 'to_amount',
      key: 'to_amount',
      render: (val) => (val != null ? `₹${val}` : 'And above'),
    },
    {
      title: 'Tax Amount (₹)',
      dataIndex: 'amount',
      key: 'amount',
      render: (val) => <Text strong>{val != null ? `₹${val}` : '₹0'}</Text>,
    },
    {
      title: 'Female Exempt',
      dataIndex: 'is_female_exempt',
      key: 'is_female_exempt',
      render: (exempt) => (
        <Tag color={exempt ? 'green' : 'default'}>{exempt ? 'Yes' : 'No'}</Tag>
      ),
    },
    {
      title: 'Deduction Months',
      dataIndex: 'deduction_months',
      key: 'deduction_months',
      render: (months) => {
        if (!months || months.length === 12) return 'All Months (1-12)';
        return months.map((m) => MONTH_OPTIONS.find((opt) => opt.value === m)?.label || m).join(', ');
      },
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  const collapseItems = states.map((st) => ({
    key: st.state_code,
    label: (
      <Row justify="space-between" align="middle" style={{ width: '100%', paddingRight: 16 }}>
        <Col>
          <Space align="center" size="middle">
            <Text strong style={{ fontSize: 16 }}>
              {st.state_name || st.state_code} ({st.state_code})
            </Text>
            <Tag
              color={st.source === 'OVERRIDE' ? 'purple' : 'blue'}
              data-testid={`pt-source-tag-${st.state_code}`}
            >
              {st.source || 'REFERENCE'}
            </Tag>
            {st.registration_number && (
              <Text type="secondary" style={{ fontSize: 13 }}>
                Reg: {st.registration_number}
              </Text>
            )}
          </Space>
        </Col>
        <Col>
          <Space onClick={(e) => e.stopPropagation()}>
            <Button
              size="small"
              icon={<EditOutlined />}
              onClick={() => openOverrideDrawer(st)}
              data-testid={`pt-override-btn-${st.state_code}`}
            >
              Override
            </Button>

            {st.source === 'OVERRIDE' && (
              <Popconfirm
                title="Remove custom override?"
                description="This state will revert to standard statutory reference slabs."
                onConfirm={() => handleRemoveOverride(st.state_code, st.state_name)}
                okText="Yes, Remove"
                cancelText="Cancel"
                data-testid={`pt-remove-confirm-${st.state_code}`}
              >
                <Button
                  size="small"
                  danger
                  icon={<DeleteOutlined />}
                  data-testid={`pt-remove-btn-${st.state_code}`}
                >
                  Remove override
                </Button>
              </Popconfirm>
            )}

            <Button
              size="small"
              icon={<HistoryOutlined />}
              onClick={() => openHistoryDrawer(st)}
              data-testid={`pt-history-btn-${st.state_code}`}
            >
              History
            </Button>
          </Space>
        </Col>
      </Row>
    ),
    children: (
      <>
        <div style={{ marginBottom: 12 }}>
          <Space size="large">
            <Text type="secondary">
              Effective From: <Text strong>{st.effective_from || 'Always'}</Text>
            </Text>
            <Text type="secondary">
              Total Slabs: <Text strong>{st.slabs ? st.slabs.length : 0}</Text>
            </Text>
          </Space>
        </div>

        <Table
          dataSource={st.slabs || []}
          columns={slabColumns}
          rowKey={(r) => r.id || `${r.from_amount}-${r.to_amount}-${r.amount}`}
          pagination={false}
          size="small"
          bordered
          data-testid={`pt-slabs-table-${st.state_code}`}
        />
      </>
    ),
  }));

  return (
    <Card
      title={
        <Space>
          <BankOutlined style={{ color: token.colorPrimary }} />
          <span>Professional Tax Settings</span>
        </Space>
      }
    >
      {error && (
        <Alert
          type="error"
          showIcon
          message={error}
          style={{ marginBottom: 16 }}
        />
      )}

      {states.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="No active work locations found"
          description="Professional tax is configured for states matching your tenant's active work locations."
        />
      ) : (
        <Collapse
          defaultActiveKey={states.map((s) => s.state_code)}
          items={collapseItems}
          data-testid="pt-collapse"
        />
      )}

      {/* Override Drawer */}
      <Drawer
        title={`Custom Professional Tax Override: ${selectedState?.state_name || selectedState?.state_code}`}
        width={720}
        open={overrideDrawerOpen}
        onClose={closeOverrideDrawer}
        data-testid="pt-override-drawer"
        extra={
          <Space>
            <Button onClick={closeOverrideDrawer}>Cancel</Button>
            <Button
              type="primary"
              onClick={() => form.submit()}
              loading={overrideSaving}
              data-testid="save-override-button"
            >
              Save Override
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" onFinish={handleSaveOverride}>
          <Row gutter={24}>
            <Col span={12}>
              <Form.Item
                name="registration_number"
                label="Registration Number"
                extra="State PT registration certificate number"
              >
                <Input placeholder="e.g. PT-123456" data-testid="override-reg-input" />
              </Form.Item>
            </Col>

            <Col span={12}>
              <Form.Item
                name="effective_from"
                label="Effective From"
                rules={[{ required: true, message: 'Effective date is required' }]}
              >
                <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" data-testid="override-effective-picker" />
              </Form.Item>
            </Col>
          </Row>

          <Divider orientation="left">Tax Slabs</Divider>

          <Form.List
            name="slabs"
            rules={[
              {
                validator: async (_, slabs) => {
                  if (!slabs || slabs.length < 1) {
                    return Promise.reject(new Error('At least one slab is required'));
                  }
                },
              },
            ]}
          >
            {(fields, { add, remove }) => (
              <Space direction="vertical" style={{ width: '100%' }} size="middle">
                {fields.map(({ key, name, ...restField }, idx) => (
                  <Card
                    key={key}
                    size="small"
                    title={`Slab #${idx + 1}`}
                    extra={
                      fields.length > 1 ? (
                        <MinusCircleOutlined
                          style={{ color: '#ff4d4f', cursor: 'pointer' }}
                          onClick={() => remove(name)}
                          data-testid={`remove-slab-btn-${idx}`}
                        />
                      ) : null
                    }
                  >
                    <Row gutter={16}>
                      <Col span={8}>
                        <Form.Item
                          {...restField}
                          name={[name, 'from_amount']}
                          label="From Amount (₹)"
                          rules={[{ required: true, message: 'Required' }]}
                        >
                          <InputNumber
                            min={0}
                            style={{ width: '100%' }}
                            data-testid={`slab-from-input-${idx}`}
                          />
                        </Form.Item>
                      </Col>

                      <Col span={8}>
                        <Form.Item
                          {...restField}
                          name={[name, 'to_amount']}
                          label="To Amount (₹)"
                          extra="Leave empty for uppermost ceiling"
                        >
                          <InputNumber
                            min={0}
                            style={{ width: '100%' }}
                            placeholder="Unlimited"
                            data-testid={`slab-to-input-${idx}`}
                          />
                        </Form.Item>
                      </Col>

                      <Col span={8}>
                        <Form.Item
                          {...restField}
                          name={[name, 'amount']}
                          label="Monthly Tax (₹)"
                          rules={[{ required: true, message: 'Required' }]}
                        >
                          <InputNumber
                            min={0}
                            style={{ width: '100%' }}
                            data-testid={`slab-amount-input-${idx}`}
                          />
                        </Form.Item>
                      </Col>
                    </Row>

                    <Row gutter={16} align="middle">
                      <Col span={8}>
                        <Form.Item
                          {...restField}
                          name={[name, 'is_female_exempt']}
                          valuePropName="checked"
                        >
                          <Checkbox data-testid={`slab-female-exempt-${idx}`}>
                            Female Exempt
                          </Checkbox>
                        </Form.Item>
                      </Col>

                      <Col span={16}>
                        <Form.Item
                          {...restField}
                          name={[name, 'deduction_months']}
                          label="Applicable Months"
                          extra="Default: all 12 months"
                        >
                          <Checkbox.Group
                            options={MONTH_OPTIONS}
                            data-testid={`slab-months-${idx}`}
                          />
                        </Form.Item>
                      </Col>
                    </Row>
                  </Card>
                ))}

                <Button
                  type="dashed"
                  onClick={() =>
                    add({
                      from_amount: 0,
                      to_amount: null,
                      amount: 0,
                      is_female_exempt: false,
                      deduction_months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
                    })
                  }
                  block
                  icon={<PlusOutlined />}
                  data-testid="add-slab-button"
                >
                  Add Slab
                </Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Drawer>

      {/* History Drawer */}
      <Drawer
        title={`Override History: ${historyState?.state_name || historyState?.state_code}`}
        width={600}
        open={historyDrawerOpen}
        onClose={closeHistoryDrawer}
        data-testid="pt-history-drawer"
      >
        {historyLoading ? (
          <div style={{ textAlign: 'center', padding: '40px 0' }}>
            <Spin />
          </div>
        ) : historyData.length === 0 ? (
          <Alert message="No history records found for this state" type="info" showIcon />
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {historyData.map((h, idx) => (
              <Card key={h.id || idx} size="small">
                <Row justify="space-between">
                  <Col>
                    <Text strong>Effective: {h.effective_from}</Text>
                  </Col>
                  <Col>
                    <Text type="secondary">{h.created_at || h.changed_at}</Text>
                  </Col>
                </Row>
                {h.registration_number && (
                  <div>
                    <Text type="secondary">Reg: {h.registration_number}</Text>
                  </div>
                )}
                {h.slabs && (
                  <div style={{ marginTop: 8 }}>
                    <Text type="secondary">{h.slabs.length} slab(s) configured</Text>
                  </div>
                )}
              </Card>
            ))}
          </Space>
        )}
      </Drawer>
    </Card>
  );
}

export default ProfessionalTaxScreen;
