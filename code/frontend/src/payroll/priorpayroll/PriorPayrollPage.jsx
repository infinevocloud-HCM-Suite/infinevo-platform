import { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Button,
  Select,
  Upload,
  Popconfirm,
  Tag,
  Typography,
  Alert,
  Card,
  Space,
  Descriptions,
} from 'antd';
import { UploadOutlined, DownloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan } from '@shell/screens';
import { priorPayrollService } from './priorPayrollService';
import { MidYearBanner } from './MidYearBanner';
import { currentFy, fyOptions } from '../tax/financialYear';

const { Title } = Typography;

const formatCurrency = (val) =>
  val != null
    ? Number(val).toLocaleString('en-IN', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    : '0.00';

const fileKey = (f) => (f ? `${f.name}:${f.size}:${f.lastModified}` : '');

const STATUS_TAG_COLOR = {
  COMPLETED: 'green',
  COMPLETED_WITH_ERRORS: 'orange',
  FAILED: 'red',
  PENDING: 'default',
};

export function PriorPayrollPage() {
  const canExecute = useCan('payroll.run.execute');

  const [fy, setFy] = useState(currentFy());
  const [file, setFile] = useState(null);
  const [checked, setChecked] = useState(null); // { fileKey, documentId, result }
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [bannerKey, setBannerKey] = useState(0);

  const [importsData, setImportsData] = useState({ content: [], totalElements: 0 });
  const [importsPage, setImportsPage] = useState(0);
  const [importsLoading, setImportsLoading] = useState(false);

  const [monthsData, setMonthsData] = useState({ content: [], totalElements: 0 });
  const [monthsPage, setMonthsPage] = useState(0);
  const [monthsLoading, setMonthsLoading] = useState(false);

  const [importResult, setImportResult] = useState(null);

  const loadHistory = useCallback(async (page = 0) => {
    setImportsLoading(true);
    try {
      const data = await priorPayrollService.imports(page, 20);
      setImportsData(data || { content: [], totalElements: 0 });
      setImportsPage(page);
    } catch (err) {
      setError(err.message || 'Failed to load import history');
    } finally {
      setImportsLoading(false);
    }
  }, []);

  const loadMonths = useCallback(async (selectedFy, page = 0) => {
    setMonthsLoading(true);
    try {
      const data = await priorPayrollService.months(selectedFy, page, 50);
      setMonthsData(data || { content: [], totalElements: 0 });
      setMonthsPage(page);
    } catch (err) {
      setError(err.message || 'Failed to load imported months');
    } finally {
      setMonthsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHistory(0);
  }, [loadHistory]);

  useEffect(() => {
    loadMonths(fy, 0);
  }, [fy, loadMonths]);

  const handleFyChange = (newFy) => {
    setFy(newFy);
    setChecked(null);
    setImportResult(null);
    setError(null);
    setMonthsPage(0);
  };

  const handleDownloadTemplate = async () => {
    setError(null);
    try {
      const text = await priorPayrollService.template();
      const url = URL.createObjectURL(new Blob([text], { type: 'text/csv' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = 'prior-payroll-template.csv';
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(err.message || 'Failed to download template');
    }
  };

  const beforeUpload = (f) => {
    if (!f.name || !f.name.toLowerCase().endsWith('.csv')) {
      setError('Choose a .csv file');
      return false;
    }
    setFile(f);
    setChecked(null);
    setImportResult(null);
    setError(null);
    return false;
  };

  const handleCheckFile = async () => {
    if (!file || !canExecute) return;
    setBusy(true);
    setError(null);
    try {
      const documentId = await priorPayrollService.upload(file);
      const result = await priorPayrollService.import({
        documentId,
        financialYear: fy,
        dryRun: true,
      });
      setChecked({
        fileKey: fileKey(file),
        documentId,
        result,
      });
    } catch (err) {
      setError(err.message || 'Check failed');
    } finally {
      setBusy(false);
    }
  };

  const handleDownloadErrors = async () => {
    if (!checked?.result?.error_document_id) return;
    try {
      const url = await priorPayrollService.errorFileLink(checked.result.error_document_id);
      window.open(url, '_blank', 'noopener');
    } catch (err) {
      setError(err.message || 'Failed to download errors');
    }
  };

  const handleImport = async () => {
    if (!checked || checked.fileKey !== fileKey(file) || !canExecute) return;
    setBusy(true);
    setError(null);
    try {
      const res = await priorPayrollService.import({
        documentId: checked.documentId,
        financialYear: fy,
        dryRun: false,
      });
      setImportResult(res);
      setChecked(null);
      setFile(null);
      setBannerKey((k) => k + 1);
      loadHistory(0);
      loadMonths(fy, 0);
    } catch (err) {
      setError(err.message || 'Import failed');
    } finally {
      setBusy(false);
    }
  };

  const handleDeleteMonth = async (id) => {
    if (!canExecute) return;
    setBusy(true);
    setError(null);
    try {
      await priorPayrollService.remove(id);
      setBannerKey((k) => k + 1);
      loadMonths(fy, monthsPage);
    } catch (err) {
      setError(err.message || 'Failed to delete month');
    } finally {
      setBusy(false);
    }
  };

  const importRowsCount = checked?.result
    ? Math.max(0, (checked.result.rows_total || 0) - (checked.result.rows_failed || 0))
    : 0;

  const historyColumns = [
    {
      title: 'Date',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (v) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm') : ''),
    },
    {
      title: 'Dry run',
      dataIndex: 'is_dry_run',
      key: 'is_dry_run',
      render: (isDryRun) => (
        <Tag color={isDryRun ? 'blue' : 'purple'}>{isDryRun ? 'Check' : 'Import'}</Tag>
      ),
    },
    {
      title: 'Rows',
      dataIndex: 'rows_total',
      key: 'rows_total',
    },
    {
      title: 'Imported',
      dataIndex: 'rows_imported',
      key: 'rows_imported',
    },
    {
      title: 'Failed',
      dataIndex: 'rows_failed',
      key: 'rows_failed',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => <Tag color={STATUS_TAG_COLOR[s] || 'default'}>{s}</Tag>,
    },
  ];

  const monthsColumns = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_, r) => `${r.employee_number} - ${r.employee_name}`,
    },
    {
      title: 'Period',
      dataIndex: 'period',
      key: 'period',
    },
    {
      title: 'Gross',
      dataIndex: 'gross_earnings',
      key: 'gross_earnings',
      render: formatCurrency,
    },
    {
      title: 'EPF',
      dataIndex: 'epf_employee',
      key: 'epf_employee',
      render: formatCurrency,
    },
    {
      title: 'ESI',
      dataIndex: 'esi_employee',
      key: 'esi_employee',
      render: formatCurrency,
    },
    {
      title: 'PT',
      dataIndex: 'professional_tax',
      key: 'professional_tax',
      render: formatCurrency,
    },
    {
      title: 'TDS',
      dataIndex: 'tds',
      key: 'tds',
      render: formatCurrency,
    },
    {
      title: 'Net',
      dataIndex: 'net_pay',
      key: 'net_pay',
      render: formatCurrency,
    },
  ];

  if (canExecute) {
    monthsColumns.push({
      title: 'Action',
      key: 'action',
      render: (_, row) => (
        <Popconfirm
          title="Delete this month?"
          onConfirm={() => handleDeleteMonth(row.id)}
          disabled={busy}
        >
          <Button type="link" danger disabled={busy}>
            Delete
          </Button>
        </Popconfirm>
      ),
    });
  }

  const isCheckValid = Boolean(checked && checked.fileKey === fileKey(file));

  return (
    <div style={{ padding: 24 }}>
      <MidYearBanner key={bannerKey} fy={fy} />

      <Space orientation="horizontal" align="center" style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          Prior payroll
        </Title>
        <Select
          value={fy}
          onChange={handleFyChange}
          options={fyOptions(2)}
          style={{ width: 140 }}
        />
        <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
          Download template
        </Button>
      </Space>

      {error && (
        <Alert
          type="error"
          message={error}
          showIcon
          closable
          onClose={() => setError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      {importResult && (
        <Alert
          type={importResult.rows_failed > 0 ? 'warning' : 'success'}
          message={`Import completed: ${importResult.rows_imported} rows imported, ${importResult.rows_failed} failed.`}
          showIcon
          closable
          onClose={() => setImportResult(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Card title="Upload prior payroll" style={{ marginBottom: 24 }}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Upload
            accept=".csv"
            maxCount={1}
            beforeUpload={beforeUpload}
            onRemove={() => {
              setFile(null);
              setChecked(null);
            }}
          >
            <Button icon={<UploadOutlined />}>Choose a .csv file</Button>
          </Upload>

          <Space style={{ marginTop: 8 }}>
            <Button
              onClick={handleCheckFile}
              disabled={!file || busy || !canExecute}
              loading={busy}
            >
              Check file
            </Button>

            <Popconfirm
              title={`Import ${importRowsCount} rows for FY ${fy}?`}
              onConfirm={handleImport}
              disabled={!isCheckValid || busy || !canExecute}
            >
              <Button
                type="primary"
                disabled={!isCheckValid || busy || !canExecute}
                loading={busy}
              >
                Import
              </Button>
            </Popconfirm>
          </Space>

          {checked?.result && (
            <Card
              size="small"
              type="inner"
              title="File check result"
              style={{ marginTop: 16 }}
            >
              <Descriptions size="small" column={3}>
                <Descriptions.Item label="Total rows">
                  {checked.result.rows_total}
                </Descriptions.Item>
                <Descriptions.Item label="Would import">
                  {importRowsCount}
                </Descriptions.Item>
                <Descriptions.Item label="Failed rows">
                  {checked.result.rows_failed}
                </Descriptions.Item>
              </Descriptions>

              {checked.result.rows_failed > 0 && checked.result.error_document_id && (
                <Button
                  danger
                  style={{ marginTop: 8 }}
                  onClick={handleDownloadErrors}
                >
                  Download errors
                </Button>
              )}
            </Card>
          )}
        </Space>
      </Card>

      <Card title="Import history" style={{ marginBottom: 24 }}>
        <Table
          rowKey="id"
          columns={historyColumns}
          dataSource={importsData.content}
          loading={importsLoading}
          pagination={{
            current: importsPage + 1,
            pageSize: 20,
            total: importsData.totalElements,
            onChange: (page) => loadHistory(page - 1),
          }}
        />
      </Card>

      <Card title="Imported months">
        <Table
          rowKey="id"
          columns={monthsColumns}
          dataSource={monthsData.content}
          loading={monthsLoading}
          pagination={{
            current: monthsPage + 1,
            pageSize: 50,
            total: monthsData.totalElements,
            onChange: (page) => loadMonths(fy, page - 1),
          }}
        />
      </Card>
    </div>
  );
}
