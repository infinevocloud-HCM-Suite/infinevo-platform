import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Typography,
  Row,
  Col,
  Upload,
  Switch,
  Select,
  Tag,
  Statistic,
  Alert,
  theme,
} from 'antd';
import {
  InboxOutlined,
  PlayCircleOutlined,
  HistoryOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { leaveImportService } from './leaveImportService.js';
import { documentService } from '../document/documentService.js';
import { companyProfileService } from '../settings/companyProfileService.js';
import { EMPTY, formatDateTime, statusTag } from '@shared/ui/format.js';

const { Text } = Typography;
const { Dragger } = Upload;

/** The server's default for core.tenant.leave_year_start_month (V033): April. */
const DEFAULT_LEAVE_YEAR_START_MONTH = 4;

/** The year a leave year starts in, for the leave year that holds `today`. */
export function currentLeaveYearStart(today, startMonth) {
  return today.month() + 1 >= startMonth ? today.year() : today.year() - 1;
}

/**
 * The leave year as the server stores it (LeaveDateUtils.canonicalizeLeaveYear): '2026' when the company's
 * year starts in January, '2026-27' for any other start month (D-49).
 */
export function leaveYearValue(startYear, startMonth) {
  if (startMonth === 1) return String(startYear);
  return `${startYear}-${String((startYear + 1) % 100).padStart(2, '0')}`;
}

/** '2026-27 (Apr 2026 to Mar 2027)': what HR picks, so nobody has to know the format. */
export function leaveYearLabel(startYear, startMonth) {
  const start = dayjs(new Date(startYear, startMonth - 1, 1));
  const end = start.add(1, 'year').subtract(1, 'day');
  return `${leaveYearValue(startYear, startMonth)} (${start.format('MMM YYYY')} to ${end.format('MMM YYYY')})`;
}

const STATUS_TAGS = {
  PENDING: { color: 'processing', label: 'Processing' },
  COMPLETED: { color: 'success', label: 'Completed' },
  COMPLETED_WITH_ERRORS: { color: 'warning', label: 'Completed with Errors' },
  FAILED: { color: 'error', label: 'Failed' },
};

export function LeaveImport() {
  const canManage = useCan('core.leave_balance.manage');
  const { token } = theme.useToken();

  // D-49: the leave year follows the company's start month, which the platform admin sets. Until the
  // company profile answers, assume April - the server's own default.
  const [leaveYearStartMonth, setLeaveYearStartMonth] = useState(DEFAULT_LEAVE_YEAR_START_MONTH);
  const [leaveYearFromTenant, setLeaveYearFromTenant] = useState(true);
  const [leaveYearStart, setLeaveYearStart] = useState(() =>
    currentLeaveYearStart(dayjs(), DEFAULT_LEAVE_YEAR_START_MONTH)
  );
  const [openingReport, setOpeningReport] = useState(false);
  const [dryRun, setDryRun] = useState(true);
  const [documentId, setDocumentId] = useState(null);
  const [fileName, setFileName] = useState('');
  const [uploading, setUploading] = useState(false);
  const [downloadingTemplate, setDownloadingTemplate] = useState(false);

  // Active / Last Import Run
  const [currentImport, setCurrentImport] = useState(null);
  const [importing, setImporting] = useState(false);
  const pollTimerRef = useRef(null);

  // History
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyList, setHistoryList] = useState([]);
  const [historyPagination, setHistoryPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const loadHistory = useCallback(async (page = 1, pageSize = 10) => {
    setHistoryLoading(true);
    try {
      const data = await leaveImportService.history(page - 1, pageSize);
      const list = Array.isArray(data) ? data : data?.content || [];
      const total = data?.totalElements ?? list.length;
      setHistoryList(list);
      setHistoryPagination({ current: page, pageSize, total });
    } catch {
      setHistoryList([]);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canManage) {
      loadHistory();
    }
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [canManage, loadHistory]);

  useEffect(() => {
    if (!canManage) return undefined;
    let live = true;
    companyProfileService
      .get()
      .catch(() => null)
      .then((profile) => {
        if (!live) return;
        const month = Number(profile?.leaveYearStartMonth);
        const known = month >= 1 && month <= 12;
        const startMonth = known ? month : DEFAULT_LEAVE_YEAR_START_MONTH;
        setLeaveYearStartMonth(startMonth);
        setLeaveYearFromTenant(known);
        setLeaveYearStart(currentLeaveYearStart(dayjs(), startMonth));
      });
    return () => {
      live = false;
    };
  }, [canManage]);

  // D-51: the error report is a stored document; open it through its signed link.
  const handleOpenErrorReport = async (id) => {
    setOpeningReport(true);
    try {
      const { url } = await documentService.link(id);
      window.open(url, '_blank', 'noopener');
    } catch (err) {
      await errorMsg(err);
    } finally {
      setOpeningReport(false);
    }
  };

  const handleCustomUpload = async ({ file, onSuccess, onError }) => {
    setUploading(true);
    try {
      const doc = await documentService.upload(file, 'LEAVE_ATTACHMENT');
      setDocumentId(doc.id || doc.documentId);
      setFileName(file.name);
      onSuccess(doc);
      await successMsg(`Uploaded ${file.name}`);
    } catch (err) {
      onError(err);
      await errorMsg(err);
    } finally {
      setUploading(false);
    }
  };

  const handleDownloadSample = async () => {
    setDownloadingTemplate(true);
    try {
      const text = await leaveImportService.downloadTemplate();
      const blob = new Blob([text], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'leave_import_sample_template.csv';
      a.click();
      URL.revokeObjectURL(url);
      await successMsg('Sample template downloaded successfully');
    } catch (err) {
      await errorMsg(err);
    } finally {
      setDownloadingTemplate(false);
    }
  };

  const startPolling = (importId) => {
    if (pollTimerRef.current) clearInterval(pollTimerRef.current);

    pollTimerRef.current = setInterval(async () => {
      try {
        const latest = await leaveImportService.get(importId);
        setCurrentImport(latest);

        if (latest.status && latest.status !== 'PENDING') {
          clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
          setImporting(false);
          loadHistory();
          successMsg(`Import finished with status: ${latest.status}`);
        }
      } catch {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
        setImporting(false);
      }
    }, 2000);
  };

  const handleStartImport = async () => {
    if (!documentId) {
      await errorMsg(new Error('Please upload a file before running import'));
      return;
    }

    setImporting(true);
    try {
      const payload = {
        documentId,
        leaveYear: leaveYearValue(leaveYearStart, leaveYearStartMonth),
        dryRun,
      };

      const result = await leaveImportService.start(payload);
      setCurrentImport(result);

      if (result.status === 'PENDING') {
        startPolling(result.id);
      } else {
        setImporting(false);
        loadHistory();
        await successMsg(
          dryRun
            ? `Dry run finished: ${result.rowsImported ?? 0} valid, ${result.rowsFailed ?? 0} failed`
            : `Import complete: ${result.rowsImported ?? 0} imported, ${result.rowsFailed ?? 0} failed`
        );
      }
    } catch (err) {
      setImporting(false);
      await errorMsg(err);
    }
  };

  if (!canManage) {
    return <NotEntitled />;
  }

  // Last, current and next leave year around the one in view.
  const thisLeaveYear = currentLeaveYearStart(dayjs(), leaveYearStartMonth);
  const leaveYearOptions = [...new Set([thisLeaveYear - 1, thisLeaveYear, thisLeaveYear + 1, leaveYearStart])]
    .sort((a, b) => a - b)
    .map((y) => ({ value: y, label: leaveYearLabel(y, leaveYearStartMonth) }));

  const historyColumns = [
    {
      title: 'Run ID',
      dataIndex: 'id',
      key: 'id',
      render: (id) => (id ? <Text code>{id.substring(0, 8)}</Text> : EMPTY),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        const conf = STATUS_TAGS[status] || statusTag(status);
        return <Tag color={conf.color}>{conf.label}</Tag>;
      },
    },
    {
      title: 'Type',
      dataIndex: 'isDryRun',
      key: 'isDryRun',
      render: (isDry) => (isDry ? <Tag color="orange">Dry Run</Tag> : <Tag color="blue">Live Import</Tag>),
    },
    { title: 'Year', dataIndex: 'leaveYear', key: 'leaveYear' },
    { title: 'Total Rows', dataIndex: 'rowsTotal', key: 'rowsTotal' },
    {
      title: 'Imported',
      dataIndex: 'rowsImported',
      key: 'rowsImported',
      render: (c) => <Text type="success">{c}</Text>,
    },
    {
      title: 'Failed',
      dataIndex: 'rowsFailed',
      key: 'rowsFailed',
      render: (c) => (c > 0 ? <Text type="danger">{c}</Text> : '0'),
    },
    {
      title: 'Started At',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (dt) => formatDateTime(dt),
    },
  ];

  return (
    <div className="saas-page-container fadeSlideUp">
      <Card className="saas-card" variant="borderless">
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <PageHeader
            title="Bulk Leave Import"
            subtitle="Upload a spreadsheet (.xlsx or .csv) to bulk allocate initial leave balances or annual allotments across employees. Dry run validates records without writing to the database."
          />

          {/* Upload & Configuration Card */}
          <Card
            type="inner"
            variant="borderless"
            title="Import Configuration"
            style={{
              background: token.colorFillAlter,
              border: `1px solid ${token.colorBorderSecondary}`,
              borderRadius: 12,
            }}
            extra={
              <Button
                className="btn-spring"
                aria-label="Download Sample Template"
                icon={<DownloadOutlined aria-hidden="true" />}
                onClick={handleDownloadSample}
                loading={downloadingTemplate}
              >
                Download Sample Template
              </Button>
            }
          >
          <Row gutter={24} align="middle">
            <Col xs={24} sm={12} md={6}>
              <Text strong>Leave Year:</Text>
              <div style={{ marginTop: 8 }}>
                <Select
                  aria-label="Leave Year"
                  value={leaveYearStart}
                  onChange={setLeaveYearStart}
                  options={leaveYearOptions}
                  style={{ width: '100%' }}
                />
              </div>
              {!leaveYearFromTenant && (
                <Text type="secondary">
                  Company settings could not be read; assuming the leave year starts in April.
                </Text>
              )}
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text strong>Dry Run (No database writes):</Text>
              <div style={{ marginTop: 8 }}>
                <Switch
                  checked={dryRun}
                  onChange={setDryRun}
                  checkedChildren="Dry Run"
                  unCheckedChildren="Live"
                />
              </div>
            </Col>
            <Col xs={24} sm={24} md={12}>
              <div style={{ textAlign: 'right', marginTop: 24 }}>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={handleStartImport}
                  loading={importing}
                  disabled={!documentId || uploading}
                  size="large"
                >
                  {dryRun ? 'Start Dry Run' : 'Execute Live Import'}
                </Button>
              </div>
            </Col>
          </Row>

          <div style={{ marginTop: 20 }}>
            <Dragger
              name="file"
              multiple={false}
              customRequest={handleCustomUpload}
              showUploadList={false}
              disabled={uploading || importing}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">
                {fileName ? `Selected: ${fileName}` : 'Click or drag file to this area to upload'}
              </p>
              <p className="ant-upload-hint">
                Supports single .xlsx or .csv balance sheets. Use the sample template above for the required format.
              </p>
            </Dragger>
          </div>
        </Card>

        {/* Result Panel */}
        {currentImport && (
          <Card
            type="inner"
            title="Import Run Results"
            extra={
              <Tag
                color={
                  STATUS_TAGS[currentImport.status]?.color ||
                  (currentImport.isDryRun ? 'orange' : 'green')
                }
              >
                {STATUS_TAGS[currentImport.status]?.label || currentImport.status}
              </Tag>
            }
          >
            <Row gutter={24} style={{ marginBottom: 16 }}>
              <Col span={8}>
                <Statistic title="Total Rows" value={currentImport.rowsTotal ?? 0} />
              </Col>
              <Col span={8}>
                <Statistic
                  title="Rows Imported (Valid)"
                  value={currentImport.rowsImported ?? 0}
                  valueStyle={{ color: token.colorSuccess }}
                />
              </Col>
              <Col span={8}>
                <Statistic
                  title="Rows Failed"
                  value={currentImport.rowsFailed ?? 0}
                  valueStyle={{ color: currentImport.rowsFailed > 0 ? token.colorError : undefined }}
                />
              </Col>
            </Row>

            {currentImport.rowsFailed > 0 && (
              <Alert
                type="warning"
                showIcon
                message="Errors Encountered"
                description={
                  currentImport.errorDocumentId
                    ? 'Some rows had validation errors. The error report lists each failed row and the reason.'
                    : 'Some rows had validation errors. Check row constraints, employee numbers, and active leave types.'
                }
                action={
                  currentImport.errorDocumentId ? (
                    <Button
                      icon={<DownloadOutlined aria-hidden="true" />}
                      loading={openingReport}
                      onClick={() => handleOpenErrorReport(currentImport.errorDocumentId)}
                    >
                      Download error report
                    </Button>
                  ) : null
                }
                style={{ marginTop: 16 }}
              />
            )}
          </Card>
        )}

        {/* Execution History */}
        <Card type="inner" variant="borderless" title="Import History" icon={<HistoryOutlined />}>
          <Table
            rowKey="id"
            columns={historyColumns}
            dataSource={historyList}
            loading={historyLoading}
            pagination={historyPagination}
            onChange={(p) => loadHistory(p.current, p.pageSize)}
            size="small"
          />
        </Card>
      </Space>
    </Card>
    </div>
  );
}
