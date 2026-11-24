import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Table, Space, Select, Input, Button, Tag, Alert } from 'antd';
import { EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import { formatMoney, formatDate, humanize, EMPTY } from '@shared/ui/format.js';
import { readError } from '../tax/apiError';
import { proofService, readPage } from './proofService';
import { proofStatus, PROOF_STATUS_OPTIONS } from './proofLabels';

const PAGE_SIZE = 25;

/**
 * The officer's list of employees and their proof for a year (W-34.3 list): one table for the
 * verification queue and the chase list, each with its own status choices.
 */
export function ProofTable({ fy, statuses, defaultStatus, onOpen, openLabel = 'Open', reloadKey }) {
  const [status, setStatus] = useState(defaultStatus);
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await proofService.list(fy, { status, search: query, page, size: PAGE_SIZE });
      const { content, total: count } = readPage(data);
      setRows(content);
      setTotal(count);
    } catch (err) {
      setRows([]);
      setTotal(0);
      setError(readError(err, 'Failed to load proofs of investment').message);
    } finally {
      setLoading(false);
    }
  }, [fy, status, query, page]);

  useEffect(() => {
    load();
  }, [load, reloadKey]);

  const options = PROOF_STATUS_OPTIONS.filter((o) => !statuses || statuses.includes(o.value));

  const columns = [
    { title: 'Employee no.', dataIndex: 'number', key: 'number', render: (v) => v || EMPTY },
    { title: 'Name', dataIndex: 'name', key: 'name', render: (v) => v || EMPTY },
    { title: 'Regime', dataIndex: 'tax_regime', key: 'tax_regime', render: (v) => humanize(v) },
    {
      title: 'Proof',
      dataIndex: 'proof_status',
      key: 'proof_status',
      render: (code) => {
        const s = proofStatus(code);
        return <Tag color={s.color}>{s.label}</Tag>;
      },
    },
    { title: 'Submitted', dataIndex: 'submitted_at', key: 'submitted_at', render: (v) => formatDate(v) },
    { title: 'Claimed', dataIndex: 'claimed_total', key: 'claimed_total', align: 'right', render: (v) => formatMoney(v) },
    { title: 'Verified', dataIndex: 'approved_total', key: 'approved_total', align: 'right', render: (v) => formatMoney(v) },
    {
      title: 'Action',
      key: 'action',
      render: (_, row) =>
        row.proof_id ? (
          <Button size="small" icon={<EyeOutlined />} onClick={() => onOpen(row)}>
            {openLabel}
          </Button>
        ) : (
          EMPTY
        ),
    },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          value={status ?? ''}
          onChange={(v) => {
            setStatus(v || undefined);
            setPage(0);
          }}
          options={statuses ? options : [{ value: '', label: 'Any status' }, ...options]}
          style={{ width: 170 }}
          aria-label="Proof status"
          data-testid="proof-status-filter"
        />
        <Input.Search
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onSearch={(v) => {
            setQuery(v);
            setPage(0);
          }}
          allowClear
          placeholder="Name or employee number"
          style={{ width: 260 }}
          aria-label="Search employees"
        />
        <Button icon={<ReloadOutlined />} onClick={load}>
          Refresh
        </Button>
      </Space>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
      <Table
        className="saas-table"
        rowKey="employee_id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        size="middle"
        scroll={{ x: true }}
        locale={{ emptyText: 'No employees match.' }}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total,
          showSizeChanger: false,
          hideOnSinglePage: true,
          onChange: (p) => setPage(p - 1),
        }}
      />
    </>
  );
}

ProofTable.propTypes = {
  fy: PropTypes.string.isRequired,
  statuses: PropTypes.arrayOf(PropTypes.string),
  defaultStatus: PropTypes.string,
  onOpen: PropTypes.func.isRequired,
  openLabel: PropTypes.string,
  reloadKey: PropTypes.number,
};

export default ProofTable;
