import { useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Alert, Button, DatePicker, Drawer, Input, InputNumber, Select, Space, Table, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { deductionService } from './deductionService.js';
import { EmployeeSelect } from './EmployeeSelect.jsx';
import { DEDUCTION_TYPE_OPTIONS, toAmountString } from './claimLabels.js';
import { readError } from '../tax/apiError.js';

const { Text } = Typography;

/** The API takes 1-500 lines per batch (W-35.2 §4). */
export const MAX_LINES = 500;
const PAGE_SIZE = 50;
const REASON_MAX = 255;

let nextKey = 0;
export function blankLine() {
  nextKey += 1;
  return {
    key: `line-${nextKey}`,
    employee_id: undefined,
    period: null,
    deduction_type: undefined,
    amount: null,
    reason: '',
    remarks: '',
  };
}

/** Appends a blank line; at `MAX_LINES` the lines come back unchanged. */
export function addLine(lines) {
  return lines.length >= MAX_LINES ? lines : [...lines, blankLine()];
}

/** No later than the end of next month. */
const tooFarAhead = (day) => Boolean(day) && day.isAfter(dayjs().add(1, 'month').endOf('month'));

/**
 * The server's message without its own leading "Line n:" - that n is the zero-based index, so
 * next to the screen's one-based "Row n+1" it would name a different number for the same row.
 */
export function withoutLineNumber(message) {
  if (typeof message !== 'string') return message;
  const stripped = message.replace(/^\s*line\s+\d+\s*[:.-]?\s*/i, '');
  return stripped || message;
}

/** A grid line as the API reads it. The amount stays text; nothing is computed here. */
export function toRequestLine(line) {
  return {
    employee_id: line.employee_id ?? null,
    period: line.period ? line.period.format('YYYY-MM') : null,
    deduction_type: line.deduction_type ?? null,
    amount: toAmountString(line.amount) ?? (line.amount === null || line.amount === '' ? null : String(line.amount)),
    reason: line.reason?.trim() || null,
    remarks: line.remarks?.trim() || null,
    document_id: null,
  };
}

/**
 * "Enter deductions" drawer (W-47.4 §5): one editable row per line, posted as one all-or-nothing
 * batch. A `400` names the failing line's zero-based index in `fieldErrors.line`; that row is
 * marked and nothing is posted. Post is disabled in flight and a double click posts once.
 */
export function DeductionGrid({ open, onClose, onPosted }) {
  const [lines, setLines] = useState(() => [blankLine()]);
  const [posting, setPosting] = useState(false);
  const [error, setError] = useState(null);
  const [errorLine, setErrorLine] = useState(null);
  const [page, setPage] = useState(1);
  const inFlight = useRef(false);

  useEffect(() => {
    if (open) {
      setLines([blankLine()]);
      setError(null);
      setErrorLine(null);
      setPage(1);
    }
  }, [open]);

  const update = (key, field, value) => {
    setLines((prev) => prev.map((l) => (l.key === key ? { ...l, [field]: value } : l)));
  };

  const remove = (key) => {
    setLines((prev) => prev.filter((l) => l.key !== key));
    setErrorLine(null);
  };

  const post = async () => {
    if (inFlight.current || lines.length === 0) return;
    inFlight.current = true;
    setPosting(true);
    setError(null);
    setErrorLine(null);
    try {
      const result = await deductionService.enter(lines.map(toRequestLine));
      onPosted?.(result);
      onClose?.();
    } catch (err) {
      const { message, fieldErrors } = readError(err, 'The deductions could not be posted');
      const index = Number.parseInt(fieldErrors?.line, 10);
      if (Number.isInteger(index) && index >= 0 && index < lines.length) {
        setErrorLine(index);
        setPage(Math.floor(index / PAGE_SIZE) + 1);
      }
      setError(message);
    } finally {
      inFlight.current = false;
      setPosting(false);
    }
  };

  const rowStatus = (index) => (index === errorLine ? 'error' : undefined);

  const columns = [
    {
      title: '#',
      key: 'n',
      width: 50,
      render: (_, __, i) => (page - 1) * PAGE_SIZE + i + 1,
    },
    {
      title: 'Employee',
      key: 'employee_id',
      width: 240,
      render: (_, line, i) => (
        <EmployeeSelect
          ariaLabel={`Employee, line ${(page - 1) * PAGE_SIZE + i + 1}`}
          value={line.employee_id}
          onChange={(v) => update(line.key, 'employee_id', v)}
          status={rowStatus((page - 1) * PAGE_SIZE + i)}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'Period',
      key: 'period',
      width: 140,
      render: (_, line, i) => (
        <DatePicker
          picker="month"
          value={line.period}
          disabledDate={tooFarAhead}
          onChange={(v) => update(line.key, 'period', v)}
          status={rowStatus((page - 1) * PAGE_SIZE + i)}
          placeholder="Month"
        />
      ),
    },
    {
      title: 'Type',
      key: 'deduction_type',
      width: 180,
      render: (_, line, i) => (
        <Select
          aria-label="Type"
          value={line.deduction_type}
          options={DEDUCTION_TYPE_OPTIONS}
          onChange={(v) => update(line.key, 'deduction_type', v)}
          status={rowStatus((page - 1) * PAGE_SIZE + i)}
          placeholder="Type"
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'Amount',
      key: 'amount',
      width: 140,
      render: (_, line, i) => (
        <InputNumber
          aria-label="Amount"
          stringMode
          min="0.01"
          precision={2}
          value={line.amount}
          onChange={(v) => update(line.key, 'amount', v)}
          status={rowStatus((page - 1) * PAGE_SIZE + i)}
          placeholder="0.00"
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'Reason',
      key: 'reason',
      render: (_, line, i) => (
        <Input
          aria-label="Reason"
          value={line.reason}
          maxLength={REASON_MAX}
          onChange={(e) => update(line.key, 'reason', e.target.value)}
          status={rowStatus((page - 1) * PAGE_SIZE + i)}
        />
      ),
    },
    {
      title: 'Remarks',
      key: 'remarks',
      render: (_, line) => (
        <Input
          aria-label="Remarks"
          value={line.remarks}
          maxLength={REASON_MAX}
          onChange={(e) => update(line.key, 'remarks', e.target.value)}
        />
      ),
    },
    {
      title: '',
      key: 'remove',
      width: 50,
      render: (_, line) => (
        <Button
          aria-label="Remove line"
          type="text"
          danger
          icon={<DeleteOutlined />}
          disabled={posting}
          onClick={() => remove(line.key)}
        />
      ),
    },
  ];

  return (
    <Drawer
      title="Enter deductions"
      open={open}
      onClose={onClose}
      width="90%"
      extra={
        <Space>
          <Text type="secondary" data-testid="line-count">
            {lines.length} / {MAX_LINES} lines
          </Text>
          <Button onClick={onClose} disabled={posting}>
            Cancel
          </Button>
          <Button type="primary" onClick={post} loading={posting} disabled={posting || lines.length === 0}>
            Post
          </Button>
        </Space>
      }
    >
      {error && (
        <Alert
          type="error"
          showIcon
          message={errorLine === null ? error : `Row ${errorLine + 1}: nothing was posted`}
          description={errorLine === null ? null : withoutLineNumber(error)}
          style={{ marginBottom: 16 }}
        />
      )}
      <Table
        rowKey="key"
        size="small"
        dataSource={lines}
        columns={columns}
        rowClassName={(_, i) =>
          (page - 1) * PAGE_SIZE + i === errorLine ? 'deduction-line-error' : ''
        }
        onRow={(_, i) => ({ 'aria-invalid': (page - 1) * PAGE_SIZE + i === errorLine ? 'true' : undefined })}
        pagination={
          lines.length > PAGE_SIZE
            ? { current: page, pageSize: PAGE_SIZE, onChange: setPage, showSizeChanger: false }
            : false
        }
      />
      <Button
        icon={<PlusOutlined />}
        style={{ marginTop: 12 }}
        disabled={posting || lines.length >= MAX_LINES}
        onClick={() => setLines((prev) => addLine(prev))}
      >
        Add line
      </Button>
    </Drawer>
  );
}

DeductionGrid.propTypes = {
  open: PropTypes.bool,
  onClose: PropTypes.func,
  onPosted: PropTypes.func,
};
