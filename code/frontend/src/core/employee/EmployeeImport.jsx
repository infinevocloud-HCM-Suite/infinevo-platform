import { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Table,
  Button,
  Space,
  Typography,
  Upload,
  Checkbox,
  Tag,
  Alert,
  Progress,
  theme,
} from 'antd';
import {
  DownloadOutlined,
  UploadOutlined,
  ExperimentOutlined,
  CloudUploadOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg } from '@shared/ui/msgHelper.js';
import { employeeImportService, saveCsv } from './employeeImportService.js';

const { Title, Text } = Typography;

/** How often the history refreshes while a job is queued or running. */
export const POLL_MS = 3000;

/**
 * A queued or running job with no progress for this long is shown as stalled and no longer polled. A
 * worker that dies mid-run leaves its job RUNNING (nothing sweeps it yet), and the screen would
 * otherwise poll forever.
 */
export const STALL_MS = 10 * 60 * 1000;

/** True for a queued or running job untouched for {@link STALL_MS}. */
export function isStalled(job, now = Date.now()) {
  if (job.status !== 'QUEUED' && job.status !== 'RUNNING') return false;
  const last = Date.parse(job.updatedAt || job.startedAt || '');
  return Number.isFinite(last) && now - last > STALL_MS;
}

const JOB_STATUS = {
  QUEUED: { color: 'default', label: 'Queued' },
  RUNNING: { color: 'processing', label: 'Running' },
  COMPLETED: { color: 'success', label: 'Completed' },
  FAILED: { color: 'error', label: 'Failed' },
};

const KIND_LABEL = { IMPORT: 'Import', INVITE_ALL: 'Invite all' };

/** Error rows first, then by row number — the order a reader fixes a file in (W-73.7 §5). */
export function errorsFirst(results) {
  return [...results].sort((a, b) => {
    const ea = a.status === 'ERROR' ? 0 : 1;
    const eb = b.status === 'ERROR' ? 0 : 1;
    return ea - eb || a.row - b.row;
  });
}

/**
 * Bulk employee import (W-73.7 §5): template, upload, dry run, import, and the history of import and
 * invite-all jobs with their result files. Import stays disabled until a dry run of this file is clean,
 * or "Import valid rows only" is ticked.
 */
export function EmployeeImport() {
  const canCreate = useCan('core.employee.create');
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const [file, setFile] = useState(null);
  const [results, setResults] = useState(null);
  const [validOnly, setValidOnly] = useState(false);
  const [checking, setChecking] = useState(false);
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState(null);

  const [jobs, setJobs] = useState([]);
  const [jobsLoading, setJobsLoading] = useState(false);

  const loadJobs = useCallback(async () => {
    setJobsLoading(true);
    try {
      setJobs((await employeeImportService.jobs()) || []);
    } catch {
      setJobs([]);
    } finally {
      setJobsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canCreate) loadJobs();
  }, [canCreate, loadJobs]);

  const active = jobs.some((j) => (j.status === 'QUEUED' || j.status === 'RUNNING') && !isStalled(j));
  useEffect(() => {
    if (!active) return undefined;
    const timer = setInterval(loadJobs, POLL_MS);
    return () => clearInterval(timer);
  }, [active, loadJobs]);

  const counts = useMemo(() => {
    if (!results) return { ok: 0, errors: 0 };
    const errors = results.filter((r) => r.status === 'ERROR').length;
    return { ok: results.length - errors, errors };
  }, [results]);

  if (!canCreate) {
    return <NotEntitled />;
  }

  const canImport =
    results !== null && counts.ok > 0 && (counts.errors === 0 || validOnly) && !importing;

  const beforeUpload = (f) => {
    setError(null);
    if (!f.name || !f.name.toLowerCase().endsWith('.csv')) {
      setError('Choose a .csv file');
      return false;
    }
    setFile(f);
    setResults(null);
    setValidOnly(false);
    return false;
  };

  const handleTemplate = async () => {
    setError(null);
    try {
      saveCsv(await employeeImportService.template(), 'employee-import-template.csv');
    } catch (err) {
      setError(err?.message || 'The template could not be downloaded');
    }
  };

  const handleDryRun = async () => {
    setError(null);
    setChecking(true);
    try {
      setResults(await employeeImportService.dryRun(file));
    } catch (err) {
      setResults(null);
      setError(err?.message || 'The file could not be checked');
    } finally {
      setChecking(false);
    }
  };

  const handleImport = async () => {
    setError(null);
    setImporting(true);
    try {
      await employeeImportService.importFile(file, validOnly);
      await successMsg('Import started', 'The employees are being created. Progress shows below.');
      setFile(null);
      setResults(null);
      setValidOnly(false);
      await loadJobs();
    } catch (err) {
      setError(err?.message || 'The import could not be started');
    } finally {
      setImporting(false);
    }
  };

  const handleResult = async (job) => {
    setError(null);
    try {
      const text = await employeeImportService.resultFile(job.jobId);
      saveCsv(text, `employee-${job.kind === 'INVITE_ALL' ? 'invite-all' : 'import'}-${job.jobId}.csv`);
    } catch (err) {
      setError(err?.message || 'The result file could not be downloaded');
    }
  };

  const resultColumns = [
    { title: 'Row', dataIndex: 'row', key: 'row', width: 70 },
    { title: 'Employee number', dataIndex: 'employeeNumber', key: 'employeeNumber', render: (v) => v || '—' },
    {
      title: 'Result',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s) => <Tag color={s === 'ERROR' ? 'error' : 'success'}>{s === 'ERROR' ? 'Error' : 'OK'}</Tag>,
    },
    { title: 'Message', dataIndex: 'message', key: 'message' },
  ];

  const jobColumns = [
    {
      title: 'Started',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (v) => (v ? new Date(v).toLocaleString() : '—'),
    },
    { title: 'Kind', dataIndex: 'kind', key: 'kind', render: (k) => KIND_LABEL[k] || k || '—' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s, job) => {
        if (isStalled(job)) {
          return (
            <Space direction="vertical" size={0}>
              <Tag color="warning">Stalled</Tag>
              <Text type="secondary">No progress for 10 minutes. Try the import again, or contact support.</Text>
            </Space>
          );
        }
        const tag = JOB_STATUS[s] || { color: 'default', label: s };
        return (
          <Space direction="vertical" size={0}>
            <Tag color={tag.color}>{tag.label}</Tag>
            {s === 'RUNNING' && <Progress percent={job.progressPercentage || 0} size="small" />}
            {s === 'FAILED' && job.errorMessage && <Text type="danger">{job.errorMessage}</Text>}
          </Space>
        );
      },
    },
    { title: 'Created', dataIndex: 'createdCount', key: 'createdCount', render: (v) => v ?? '—' },
    { title: 'Invited', dataIndex: 'invitedCount', key: 'invitedCount', render: (v) => v ?? '—' },
    { title: 'Failed', dataIndex: 'failedCount', key: 'failedCount', render: (v) => v ?? '—' },
    {
      title: 'Result file',
      key: 'result',
      render: (_, job) =>
        job.resultDocumentId ? (
          <Button
            size="small"
            icon={<DownloadOutlined />}
            onClick={() => handleResult(job)}
            data-testid={`btn-result-${job.jobId}`}
          >
            Download
          </Button>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card variant="borderless" style={{ borderRadius: token.borderRadiusLG }}>
        <Space style={{ marginBottom: token.marginLG }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/employees')} id="btn-back-employees">
            Employees
          </Button>
          <Title level={4} style={{ margin: 0 }}>
            Import Employees
          </Title>
        </Space>

        <Space wrap style={{ marginBottom: token.marginLG }}>
          <Button icon={<DownloadOutlined />} onClick={handleTemplate} id="btn-import-template">
            Download template
          </Button>
          <Upload accept=".csv" maxCount={1} showUploadList={false} beforeUpload={beforeUpload}>
            <Button icon={<UploadOutlined />} id="btn-import-choose">
              Choose CSV
            </Button>
          </Upload>
          {file && <Text data-testid="import-file-name">{file.name}</Text>}
          <Button
            icon={<ExperimentOutlined />}
            onClick={handleDryRun}
            disabled={!file}
            loading={checking}
            id="btn-import-dry-run"
          >
            Dry run
          </Button>
          <Button
            type="primary"
            icon={<CloudUploadOutlined />}
            onClick={handleImport}
            disabled={!canImport}
            loading={importing}
            id="btn-import-start"
          >
            Import
          </Button>
        </Space>

        <Text type="secondary" style={{ display: 'block', marginBottom: token.margin }}>
          Up to 1,000 rows. Rows with give_access Y are invited by email; roles (for example hr;manager) need the
          Assign roles permission.
        </Text>

        {error && (
          <Alert type="error" showIcon message={error} style={{ marginBottom: token.margin }} id="alert-import-error" />
        )}

        {results && (
          <>
            <Space style={{ marginBottom: token.margin }} wrap>
              <Tag color="success">{counts.ok} OK</Tag>
              <Tag color={counts.errors ? 'error' : 'default'}>{counts.errors} with errors</Tag>
              {counts.errors > 0 && counts.ok > 0 && (
                <Checkbox
                  checked={validOnly}
                  onChange={(e) => setValidOnly(e.target.checked)}
                  id="chk-import-valid-only"
                >
                  Import valid rows only
                </Checkbox>
              )}
            </Space>
            <Table
              rowKey="row"
              size="small"
              columns={resultColumns}
              dataSource={errorsFirst(results)}
              pagination={{ pageSize: 50 }}
              data-testid="import-dry-run-table"
            />
          </>
        )}
      </Card>

      <Card
        variant="borderless"
        title="History"
        style={{ borderRadius: token.borderRadiusLG }}
        extra={
          <Button size="small" onClick={loadJobs} id="btn-import-history-refresh">
            Refresh
          </Button>
        }
      >
        <Table
          rowKey="jobId"
          size="small"
          columns={jobColumns}
          dataSource={jobs}
          loading={jobsLoading}
          pagination={false}
          data-testid="import-history-table"
        />
      </Card>
    </Space>
  );
}
