import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Button, Drawer, Result, Skeleton, Space, Table, Tabs, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { claimService } from './claimService.js';
import { deductionService } from './deductionService.js';
import { ClaimForm } from './ClaimForm.jsx';
import { ClaimDescriptions } from './ClaimDetail.jsx';
import {
  claimStatus,
  deductionStatus,
  deductionTypeLabel,
  formatAmount,
  formatDate,
} from './claimLabels.js';
import { readError } from '../tax/apiError.js';

const { Text } = Typography;

/** Loads a list once and on demand; on failure the rows are cleared, never filled in (DEBT-031). */
function useList(fetch, fallback) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetch();
      setRows(Array.isArray(data) ? data : []);
    } catch (err) {
      setRows([]);
      setError(readError(err, fallback).message);
    } finally {
      setLoading(false);
    }
  }, [fetch, fallback]);

  useEffect(() => {
    load();
  }, [load]);

  return { rows, loading, error, load };
}

function LoadError({ title, error, onRetry }) {
  return (
    <Result
      status="error"
      title={title}
      subTitle={error}
      extra={
        <Button type="primary" onClick={onRetry}>
          Retry
        </Button>
      }
    />
  );
}

LoadError.propTypes = {
  title: PropTypes.string.isRequired,
  error: PropTypes.string,
  onRetry: PropTypes.func.isRequired,
};

const fetchClaims = () => claimService.listOwn();
const fetchDeductions = () => deductionService.listOwn();

/**
 * The `/me` panel "Claims and deductions" (W-47.4 §5, § 13 decision 4): the employee's claims,
 * with detail and "New claim", and their deductions, read only.
 */
export function MyClaimsPanel() {
  const claims = useList(fetchClaims, 'Could not load your claims');
  const deductions = useList(fetchDeductions, 'Could not load your deductions');
  const [formOpen, setFormOpen] = useState(false);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState(null);
  const [detailId, setDetailId] = useState(null);

  const openDetail = useCallback(async (id) => {
    setDetailId(id);
    setDetail(null);
    setDetailError(null);
    setDetailLoading(true);
    try {
      setDetail(await claimService.getOwn(id));
    } catch (err) {
      setDetailError(readError(err, 'Could not load the claim').message);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const closeDetail = () => {
    setDetailId(null);
    setDetail(null);
    setDetailError(null);
  };

  const claimColumns = [
    { title: 'Bill date', dataIndex: 'bill_date', key: 'bill_date', render: formatDate },
    { title: 'Component', dataIndex: 'component_name', key: 'component_name', render: (v) => v || '-' },
    {
      title: 'Requested',
      dataIndex: 'requested_amount',
      key: 'requested_amount',
      align: 'right',
      render: formatAmount,
    },
    {
      title: 'Approved',
      dataIndex: 'approved_amount',
      key: 'approved_amount',
      align: 'right',
      render: formatAmount,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => {
        const tag = claimStatus(s);
        return <Tag color={tag.color}>{tag.label}</Tag>;
      },
    },
    { title: 'Posted period', dataIndex: 'posted_period', key: 'posted_period', render: (v) => v || '-' },
    { title: 'Remarks', dataIndex: 'remarks', key: 'remarks', render: (v) => v || '-' },
  ];

  // A reversed deduction is struck through and carries its reversal date.
  const struck = (row, content) =>
    row.status === 'REVERSED' ? <Text delete>{content}</Text> : content;

  const deductionColumns = [
    { title: 'Period', dataIndex: 'period', key: 'period', render: (v, row) => struck(row, v) },
    {
      title: 'Type',
      dataIndex: 'deduction_type',
      key: 'deduction_type',
      render: (v, row) => struck(row, deductionTypeLabel(v)),
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (v, row) => struck(row, formatAmount(v)),
    },
    { title: 'Reason', dataIndex: 'reason', key: 'reason', render: (v, row) => struck(row, v || '-') },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s, row) => {
        const tag = deductionStatus(s);
        return (
          <Space size={4}>
            <Tag color={tag.color}>{tag.label}</Tag>
            {s === 'REVERSED' && (
              <Text type="secondary" data-testid="reversed-on">
                on {formatDate(row.reversed_at)}
              </Text>
            )}
          </Space>
        );
      },
    },
  ];

  const items = [
    {
      key: 'claims',
      label: 'Claims',
      children: claims.error ? (
        <LoadError title="Could not load your claims" error={claims.error} onRetry={claims.load} />
      ) : (
        <>
          <Space style={{ marginBottom: 16 }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setFormOpen(true)}>
              New claim
            </Button>
          </Space>
          <Table
            rowKey="id"
            size="middle"
            dataSource={claims.rows}
            columns={claimColumns}
            loading={claims.loading}
            onRow={(record) => ({ onClick: () => openDetail(record.id), style: { cursor: 'pointer' } })}
            pagination={{ pageSize: 10, hideOnSinglePage: true }}
          />
        </>
      ),
    },
    {
      key: 'deductions',
      label: 'Deductions',
      children: deductions.error ? (
        <LoadError title="Could not load your deductions" error={deductions.error} onRetry={deductions.load} />
      ) : (
        <Table
          rowKey="id"
          size="middle"
          dataSource={deductions.rows}
          columns={deductionColumns}
          loading={deductions.loading}
          pagination={{ pageSize: 10, hideOnSinglePage: true }}
        />
      ),
    },
  ];

  return (
    <div>
      <Tabs items={items} />
      <ClaimForm open={formOpen} onClose={() => setFormOpen(false)} onSubmitted={() => claims.load()} />
      <Drawer title="Claim" open={Boolean(detailId)} onClose={closeDetail} width={560}>
        {detailLoading && <Skeleton active paragraph={{ rows: 6 }} />}
        {detailError && (
          <LoadError title="Could not load the claim" error={detailError} onRetry={() => openDetail(detailId)} />
        )}
        {detail && <ClaimDescriptions claim={detail} showEmployee={false} />}
      </Drawer>
    </div>
  );
}
