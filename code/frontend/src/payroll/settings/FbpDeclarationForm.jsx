import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Table,
  InputNumber,
  Button,
  DatePicker,
  Space,
  Row,
  Col,
  Tag,
  Typography,
  Alert,
  Spin,
  Statistic,
  message,
  theme,
} from 'antd';
import { SaveOutlined, CalendarOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { fbpService } from './fbpService.js';

const { Text } = Typography;

export function FbpDeclarationForm({
  employeeId,
  asOf: initialAsOf,
  canManage = true,
  onSaved,
}) {
  const { token } = theme.useToken();

  const [asOf, setAsOf] = useState(initialAsOf || dayjs().format('YYYY-MM-DD'));
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error404, setError404] = useState(null);
  const [errorOther, setErrorOther] = useState(null);

  const [declaration, setDeclaration] = useState(null);
  const [lines, setLines] = useState([]);

  const loadDeclaration = useCallback(async (date) => {
    if (!employeeId) return;
    setLoading(true);
    setError404(null);
    setErrorOther(null);
    try {
      const data = await fbpService.declaration(employeeId, date);
      setDeclaration(data);

      const rawLines = data?.lines || [];
      const formattedLines = rawLines.map((l) => ({
        component_id: l.component_id || l.id,
        kind: l.kind,
        name: l.name || l.component_name || l.code,
        code: l.code,
        max_limit: l.max_limit,
        annual_amount: l.annual_amount != null ? Number(l.annual_amount) : 0,
      }));
      setLines(formattedLines);
    } catch (err) {
      setDeclaration(null);
      setLines([]);
      if (err.response?.status === 404 || err.status === 404) {
        setError404(err.response?.data?.message || 'No salary version found in force for this date');
      } else {
        setErrorOther(err.response?.data?.message || err.message || 'Failed to load FBP declaration');
      }
    } finally {
      setLoading(false);
    }
  }, [employeeId]);

  useEffect(() => {
    loadDeclaration(asOf);
  }, [asOf, loadDeclaration]);

  const handleAmountChange = (index, value) => {
    setLines((prev) => {
      const updated = [...prev];
      updated[index] = {
        ...updated[index],
        annual_amount: value != null ? Number(value) : 0,
      };
      return updated;
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setError404(null);
    try {
      const payloadLines = lines.map((l) => ({
        kind: l.kind,
        component_id: l.component_id,
        annual_amount: Number(l.annual_amount || 0),
      }));

      const payload = { lines: payloadLines };
      const res = await fbpService.setDeclaration(employeeId, payload);
      message.success('FBP declaration saved successfully');
      setDeclaration(res);
      if (onSaved) onSaved(res);
    } catch (err) {
      if (err.response?.status === 404 || err.status === 404) {
        const msg = err.response?.data?.message || 'No salary version found in force for this date';
        setError404(msg);
        message.error(msg);
      } else {
        message.error(err.response?.data?.message || err.message || 'Failed to save declaration');
      }
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      width: 120,
      render: (k) => (
        <Tag color={k === 'EARNING' ? 'cyan' : 'orange'}>{k}</Tag>
      ),
    },
    {
      title: 'Component',
      dataIndex: 'name',
      key: 'name',
      render: (name, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          {r.code && <Text type="secondary" code>{r.code}</Text>}
        </Space>
      ),
    },
    {
      title: 'Max Limit (₹)',
      dataIndex: 'max_limit',
      key: 'max_limit',
      width: 160,
      render: (val) => (val != null ? `₹${val}` : 'No limit'),
    },
    {
      title: 'Declared Annual Amount (₹)',
      dataIndex: 'annual_amount',
      key: 'annual_amount',
      width: 220,
      render: (val, record, idx) => (
        <InputNumber
          min={0}
          max={record.max_limit != null ? Number(record.max_limit) : undefined}
          style={{ width: '100%' }}
          value={val}
          onChange={(newVal) => handleAmountChange(idx, newVal)}
          disabled={!canManage}
          data-testid={`fbp-line-amount-${record.component_id || idx}`}
        />
      ),
    },
  ];

  return (
    <Card
      title={
        <Row justify="space-between" align="middle" style={{ width: '100%' }}>
          <Col>
            <Space>
              <CalendarOutlined style={{ color: token.colorPrimary }} />
              <span>Flexible Benefit Plan Declaration</span>
            </Space>
          </Col>
          <Col>
            <Space align="center">
              <Text type="secondary">As Of Date:</Text>
              <DatePicker
                value={asOf ? dayjs(asOf) : null}
                onChange={(date) => {
                  if (date) setAsOf(date.format('YYYY-MM-DD'));
                }}
                data-testid="fbp-asof-picker"
              />
            </Space>
          </Col>
        </Row>
      }
    >
      {error404 && (
        <Alert
          type="error"
          showIcon
          message="No Salary Version Found"
          description={error404}
          style={{ marginBottom: 16 }}
          data-testid="fbp-404-error"
        />
      )}

      {errorOther && (
        <Alert
          type="error"
          showIcon
          message={errorOther}
          style={{ marginBottom: 16 }}
        />
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <Spin size="large" />
        </div>
      ) : (
        <>
          {declaration && (
            <Row gutter={24} style={{ marginBottom: 24 }}>
              <Col xs={24} sm={8}>
                <Card size="small" style={{ background: token.colorBgLayout }}>
                  <Statistic
                    title="FBP Annual Pool"
                    value={declaration.pool_annual ?? declaration.fbp?.pool_annual ?? 0}
                    prefix="₹"
                    precision={2}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={8}>
                <Card size="small" style={{ background: token.colorBgLayout }}>
                  <Statistic
                    title="Total Declared"
                    value={declaration.declared_annual ?? declaration.fbp?.declared_annual ?? 0}
                    prefix="₹"
                    precision={2}
                    valueStyle={{ color: token.colorSuccess }}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={8}>
                <Card size="small" style={{ background: token.colorBgLayout }}>
                  <Statistic
                    title="Unallocated Amount"
                    value={declaration.unallocated_annual ?? declaration.fbp?.unallocated_annual ?? 0}
                    prefix="₹"
                    precision={2}
                    valueStyle={{
                      color:
                        (declaration.unallocated_annual ?? declaration.fbp?.unallocated_annual ?? 0) < 0
                          ? token.colorError
                          : token.colorWarning,
                    }}
                  />
                </Card>
              </Col>
            </Row>
          )}

          <Table
            dataSource={lines}
            columns={columns}
            rowKey={(r) => r.component_id}
            pagination={false}
            size="middle"
            bordered
            data-testid="fbp-declaration-table"
          />

          {canManage && lines.length > 0 && (
            <div style={{ marginTop: 24, textAlign: 'right' }}>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving}
                onClick={handleSave}
                data-testid="save-fbp-declaration-button"
              >
                Save FBP Declaration
              </Button>
            </div>
          )}
        </>
      )}
    </Card>
  );
}

FbpDeclarationForm.propTypes = {
  employeeId: PropTypes.string.isRequired,
  asOf: PropTypes.string,
  canManage: PropTypes.bool,
  onSaved: PropTypes.func,
};

export default FbpDeclarationForm;
