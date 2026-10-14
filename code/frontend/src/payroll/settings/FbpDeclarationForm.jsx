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
import { formatMoney, humanize } from '@shared/ui/format.js';

const { Text } = Typography;

export function FbpDeclarationForm({
  employeeId,
  asOf: initialAsOf,
  canManage = true,
  onSaved,
  isOwn = !employeeId,
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
    if (!isOwn && !employeeId) return;
    setLoading(true);
    setError404(null);
    setErrorOther(null);
    try {
      const data = isOwn
        ? await fbpService.readOwn()
        : await fbpService.declaration(employeeId, date);
      setDeclaration(data);

      // FbpDeclarationLineResponse field names (D-67). The line's own annual amount is its ceiling.
      const rawLines = data?.lines || [];
      const formattedLines = rawLines.map((l) => ({
        componentId: l.componentId,
        kind: l.kind,
        componentName: l.componentName || l.componentCode,
        componentCode: l.componentCode,
        lineAnnualAmount: l.lineAnnualAmount,
        annualAmount: l.declaredAnnualAmount != null ? Number(l.declaredAnnualAmount) : 0,
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
  }, [employeeId, isOwn]);

  useEffect(() => {
    loadDeclaration(asOf);
  }, [asOf, loadDeclaration]);

  const handleAmountChange = (index, value) => {
    setLines((prev) => {
      const updated = [...prev];
      updated[index] = {
        ...updated[index],
        annualAmount: value != null ? Number(value) : 0,
      };
      return updated;
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setError404(null);
    try {
      // FbpDeclarationLineRequest field names (D-67).
      const payloadLines = lines.map((l) => ({
        kind: l.kind,
        componentId: l.componentId,
        annualAmount: Number(l.annualAmount || 0),
      }));

      const payload = { lines: payloadLines };
      const res = isOwn
        ? await fbpService.declareOwn(payload)
        : await fbpService.setDeclaration(employeeId, payload);
      message.success('FBP declaration saved successfully');
      setDeclaration(res);
      if (onSaved) onSaved(res);
    } catch (err) {
      if (err.response?.status === 404 || err.status === 404) {
        const msg = err.response?.data?.message || 'No salary version found in force for this date';
        setError404(msg);
        message.error(msg);
      } else if (err.response?.status === 409 || err.status === 409) {
        const msg = err.response?.data?.message || 'Declaration window is closed';
        message.error(msg);
      } else {
        message.error(err.response?.data?.message || err.message || 'Failed to save declaration');
      }
    } finally {
      setSaving(false);
    }
  };

  const isWindowClosed = isOwn && declaration && declaration.windowOpen === false;
  const isEditable = canManage && !isWindowClosed;

  const columns = [
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      width: 120,
      render: (k) => (
        <Tag color={k === 'EARNING' ? 'cyan' : 'orange'}>{humanize(k)}</Tag>
      ),
    },
    {
      title: 'Component',
      dataIndex: 'componentName',
      key: 'componentName',
      render: (name, r) => (
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          {r.componentCode && <Text type="secondary" code>{r.componentCode}</Text>}
        </Space>
      ),
    },
    {
      title: 'Max Limit',
      dataIndex: 'lineAnnualAmount',
      key: 'lineAnnualAmount',
      width: 160,
      render: (val) => (val != null ? formatMoney(val) : 'No limit'),
    },
    {
      title: 'Declared Annual Amount (₹)',
      dataIndex: 'annualAmount',
      key: 'annualAmount',
      width: 220,
      render: (val, record, idx) => (
        <Space direction="vertical" style={{ width: '100%' }} size={2}>
          <InputNumber
            min={0}
            max={record.lineAnnualAmount != null ? Number(record.lineAnnualAmount) : undefined}
            style={{ width: '100%' }}
            value={val}
            onChange={(newVal) => handleAmountChange(idx, newVal)}
            disabled={!isEditable}
            data-testid={`fbp-line-amount-${record.componentId || idx}`}
          />
          <Text type="secondary" style={{ fontSize: 12 }}>
            ≈ {formatMoney((val || 0) / 12)} / mo
          </Text>
        </Space>
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
          {!isOwn && (
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
          )}
        </Row>
      }
    >
      {isWindowClosed && (
        <Alert
          type="warning"
          showIcon
          message="Declaration Window Closed"
          description="The Flexible Benefit Plan declaration window is currently closed. You can view your allocated amounts, but changes cannot be submitted."
          style={{ marginBottom: 16 }}
          data-testid="fbp-window-closed-alert"
        />
      )}

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
                    value={declaration.summary?.poolAnnual ?? 0}
                    prefix="₹"
                    precision={2}
                  />
                </Card>
              </Col>
              <Col xs={24} sm={8}>
                <Card size="small" style={{ background: token.colorBgLayout }}>
                  <Statistic
                    title="Total Declared"
                    value={declaration.summary?.declaredAnnual ?? 0}
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
                    value={declaration.summary?.unallocatedAnnual ?? 0}
                    prefix="₹"
                    precision={2}
                    valueStyle={{
                      color:
                        (declaration.summary?.unallocatedAnnual ?? 0) < 0
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
            rowKey={(r) => r.componentId}
            pagination={false}
            size="middle"
            bordered
            data-testid="fbp-declaration-table"
          />

          {isEditable && lines.length > 0 && (
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
  employeeId: PropTypes.string,
  asOf: PropTypes.string,
  canManage: PropTypes.bool,
  onSaved: PropTypes.func,
  isOwn: PropTypes.bool,
};

export default FbpDeclarationForm;
