import { useState, useEffect, useCallback } from 'react';
import { Table, Button, Select, Typography, Card, Alert, Space, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { taxRecalcFailureService } from './taxRecalcFailureService.js';
import { fyOptions, fyForApi } from './financialYear.js';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { EMPTY, formatDateTime, humanize } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Text } = Typography;

const ALL_YEARS = '';

/**
 * `/payroll/tax/recalc-failures` (D-151): the tax recalculations that failed and have not succeeded since
 * (D-147). Each row says who, which year, why and when; an officer with `payroll.tax_declaration.verify`
 * retries it. A retry that succeeds closes the failure; one that fails again stays listed with the new reason.
 */
export function TaxRecalcFailuresPage() {
  const canRead = useCan('payroll.tax_declaration.read');
  const canRetry = useCan('payroll.tax_declaration.verify');
  const [fy, setFy] = useState(ALL_YEARS);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [retrying, setRetrying] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      setRows(await taxRecalcFailureService.open(fy ? fyForApi(fy) : undefined));
    } catch (err) {
      setLoadError(err?.message || 'Could not load the failed recalculations');
    } finally {
      setLoading(false);
    }
  }, [fy]);

  useEffect(() => {
    if (canRead) load();
  }, [canRead, load]);

  const retry = async (row) => {
    setRetrying(row.id);
    try {
      const result = await taxRecalcFailureService.retry(row.id);
      setRetrying(null);
      await successMsg(
        'Tax recalculated',
        `${row.employee_name || 'The employee'}, FY ${row.financial_year}: annual tax ${result?.annual_tax ?? EMPTY}`
      );
    } catch (err) {
      setRetrying(null);
      await errorMsg(err);
    }
    load();
  };

  if (!canRead) {
    return <Alert type="warning" showIcon message="You do not have access to tax recalculation failures" />;
  }

  const columns = [
    {
      title: 'Employee',
      dataIndex: 'employee_name',
      key: 'employee_name',
      render: (name, row) => name || <Text type="secondary">{row.employee_id}</Text>,
    },
    { title: 'Financial year', dataIndex: 'financial_year', key: 'financial_year' },
    { title: 'Trigger', dataIndex: 'trigger', key: 'trigger', render: (v) => humanize(v) },
    {
      title: 'Reason',
      dataIndex: 'error_message',
      key: 'error_message',
      render: (v) => <Text style={{ wordBreak: 'break-word' }}>{v || EMPTY}</Text>,
    },
    { title: 'Attempts', dataIndex: 'attempts', key: 'attempts', render: (v) => <Tag>{v}</Tag> },
    { title: 'First failed', dataIndex: 'first_failed_at', key: 'first_failed_at', render: (v) => formatDateTime(v) },
    { title: 'Last failed', dataIndex: 'last_failed_at', key: 'last_failed_at', render: (v) => formatDateTime(v) },
  ];
  if (canRetry) {
    columns.push({
      title: 'Action',
      key: 'actions',
      render: (_, row) => (
        <Button
          id={`btn-retry-${row.id}`}
          data-testid="tax-recalc-retry"
          size="small"
          type="primary"
          loading={retrying === row.id}
          disabled={retrying !== null && retrying !== row.id}
          onClick={() => retry(row)}
        >
          Retry
        </Button>
      ),
    });
  }

  return (
    <div data-testid="tax-recalc-failures-page">
      <PageHeader
        title="Tax recalculation failures"
        subtitle="Recalculations that failed after a declaration, proof or salary change"
        count={rows.length}
        actions={
          <Space>
            <Select
              id="select-recalc-failure-fy"
              data-testid="tax-recalc-fy"
              value={fy}
              onChange={setFy}
              style={{ width: 140 }}
              aria-label="Financial year"
              options={[{ label: 'All years', value: ALL_YEARS }, ...fyOptions(3)]}
            />
            <Button id="btn-reload-recalc-failures" icon={<ReloadOutlined />} onClick={load} disabled={loading}>
              Refresh
            </Button>
          </Space>
        }
      />
      {loadError && (
        <Alert type="error" showIcon style={{ marginBottom: 16 }} data-testid="tax-recalc-load-error" message={loadError} />
      )}
      <Card variant="borderless">
        <Table
          data-testid="tax-recalc-failures"
          rowKey="id"
          loading={loading}
          dataSource={rows}
          columns={columns}
          pagination={false}
          scroll={{ x: true }}
          locale={{ emptyText: 'No failed tax recalculations' }}
        />
      </Card>
    </div>
  );
}
