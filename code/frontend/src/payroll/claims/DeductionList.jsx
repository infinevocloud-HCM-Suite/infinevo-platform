import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Input,
  Modal,
  Result,
  Row,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { deductionService } from './deductionService.js';
import { EmployeeSelect } from './EmployeeSelect.jsx';
import { DeductionGrid } from './DeductionGrid.jsx';
import {
  DEDUCTION_STATUS_OPTIONS,
  DEDUCTION_TYPE_OPTIONS,
  deductionStatus,
  deductionTypeLabel,
  formatAmount,
  formatDate,
} from './claimLabels.js';
import { readError } from '../tax/apiError.js';

const { Title, Text } = Typography;
const REASON_MAX = 255;

/**
 * `/payroll/deductions` (W-47.4 §5): the officer's deduction list. "Enter deductions" and
 * "Reverse" are shown only with `payroll.employee_deduction.manage`; the endpoints refuse on
 * their own regardless.
 */
export function DeductionList() {
  const canManage = useCan('payroll.employee_deduction.manage');
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(25);
  const [employeeId, setEmployeeId] = useState(undefined);
  const [period, setPeriod] = useState(null);
  const [status, setStatus] = useState(undefined);
  const [deductionType, setDeductionType] = useState(undefined);
  const [gridOpen, setGridOpen] = useState(false);

  const [reversing, setReversing] = useState(null);
  const [reason, setReason] = useState('');
  const [reverseBusy, setReverseBusy] = useState(false);
  const [reverseError, setReverseError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await deductionService.list({
        employeeId,
        period: period ? period.format('YYYY-MM') : undefined,
        status,
        deductionType,
        page: page - 1,
        size: pageSize,
      });
      setRows(data?.content ?? []);
      setTotal(data?.totalElements ?? 0);
    } catch (err) {
      setRows([]);
      setTotal(0);
      setError(readError(err, 'Could not load the deductions').message);
    } finally {
      setLoading(false);
    }
  }, [employeeId, period, status, deductionType, page, pageSize]);

  useEffect(() => {
    load();
  }, [load]);

  const replaceRow = (row) => setRows((prev) => prev.map((r) => (r.id === row.id ? row : r)));

  const closeReverse = () => {
    setReversing(null);
    setReason('');
    setReverseError(null);
  };

  const confirmReverse = async () => {
    const text = reason.trim();
    if (!reversing || text.length === 0 || text.length > REASON_MAX || reverseBusy) return;
    setReverseBusy(true);
    setReverseError(null);
    try {
      replaceRow(await deductionService.reverse(reversing.id, text));
      successMsg('Deduction reversed');
      closeReverse();
    } catch (err) {
      const e = readError(err, 'The deduction could not be reversed');
      if (e.status === 409) {
        // Someone else reversed it, or the period moved on: show the row as it is now.
        try {
          replaceRow(await deductionService.get(reversing.id));
        } catch {
          load();
        }
        errorMsg(err);
        closeReverse();
      } else {
        setReverseError(e.message);
      }
    } finally {
      setReverseBusy(false);
    }
  };

  const columns = [
    { title: 'Employee', dataIndex: 'employee_name', key: 'employee_name', render: (v) => v || '-' },
    { title: 'Period', dataIndex: 'period', key: 'period' },
    { title: 'Type', dataIndex: 'deduction_type', key: 'deduction_type', render: deductionTypeLabel },
    { title: 'Amount', dataIndex: 'amount', key: 'amount', align: 'right', render: formatAmount },
    { title: 'Reason', dataIndex: 'reason', key: 'reason', render: (v) => v || '-' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s, row) => {
        const tag = deductionStatus(s);
        return (
          <Space direction="vertical" size={0}>
            <Tag color={tag.color}>{tag.label}</Tag>
            {s === 'REVERSED' && <Text type="secondary">{formatDate(row.reversed_at)}</Text>}
          </Space>
        );
      },
    },
    { title: 'Posted period', dataIndex: 'posted_period', key: 'posted_period', render: (v) => v || '-' },
  ];

  if (canManage) {
    columns.push({
      title: '',
      key: 'actions',
      render: (_, row) =>
        row.status === 'POSTED' ? (
          <Button size="small" danger onClick={() => setReversing(row)}>
            Reverse
          </Button>
        ) : null,
    });
  }

  const reasonText = reason.trim();

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          Salary deductions
        </Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>
            Refresh
          </Button>
          {canManage && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setGridOpen(true)}>
              Enter deductions
            </Button>
          )}
        </Space>
      </Space>

      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Text type="secondary">Employee</Text>
            <EmployeeSelect
              ariaLabel="Employee filter"
              value={employeeId}
              onChange={(v) => {
                setEmployeeId(v);
                setPage(1);
              }}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={24} md={5}>
            <Text type="secondary">Period</Text>
            <DatePicker
              picker="month"
              value={period}
              onChange={(v) => {
                setPeriod(v);
                setPage(1);
              }}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={24} md={5}>
            <Text type="secondary">Status</Text>
            <Select
              aria-label="Status filter"
              allowClear
              placeholder="All statuses"
              options={DEDUCTION_STATUS_OPTIONS}
              value={status}
              onChange={(v) => {
                setStatus(v);
                setPage(1);
              }}
              style={{ width: '100%' }}
            />
          </Col>
          <Col xs={24} md={6}>
            <Text type="secondary">Type</Text>
            <Select
              aria-label="Type filter"
              allowClear
              placeholder="All types"
              options={DEDUCTION_TYPE_OPTIONS}
              value={deductionType}
              onChange={(v) => {
                setDeductionType(v);
                setPage(1);
              }}
              style={{ width: '100%' }}
            />
          </Col>
        </Row>
      </Card>

      {error ? (
        <Result
          status="error"
          title="Could not load the deductions"
          subTitle={error}
          extra={
            <Button type="primary" onClick={load}>
              Retry
            </Button>
          }
        />
      ) : (
        <Table
          rowKey="id"
          dataSource={rows}
          columns={columns}
          loading={loading}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setPage(p);
              setPageSize(ps);
            },
          }}
        />
      )}

      <Modal
        title="Reverse this deduction?"
        open={Boolean(reversing)}
        onCancel={closeReverse}
        onOk={confirmReverse}
        okText="Reverse"
        okButtonProps={{ danger: true, disabled: reasonText.length === 0 || reasonText.length > REASON_MAX }}
        confirmLoading={reverseBusy}
        destroyOnClose
      >
        {reversing && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text>
              {reversing.employee_name || reversing.employee_id}, {reversing.period},{' '}
              {deductionTypeLabel(reversing.deduction_type)}, {formatAmount(reversing.amount)}
            </Text>
            {reverseError && <Alert type="error" showIcon message={reverseError} />}
            <Input.TextArea
              aria-label="Reason for reversal"
              rows={3}
              maxLength={REASON_MAX}
              showCount
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Why is this deduction reversed?"
            />
          </Space>
        )}
      </Modal>

      {canManage && (
        <DeductionGrid
          open={gridOpen}
          onClose={() => setGridOpen(false)}
          onPosted={(result) => {
            successMsg('Deductions posted', `${result?.count ?? 0} deductions posted`);
            load();
          }}
        />
      )}
    </div>
  );
}
