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
  InputNumber,
  Tag,
  Statistic,
  Alert,
} from 'antd';
import {
  InboxOutlined,
  PlayCircleOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { leaveImportService } from './leaveImportService.js';
import { documentService } from '../document/documentService.js';

const { Title, Text } = Typography;
const { Dragger } = Upload;

const STATUS_TAGS = {
  PENDING: { color: 'processing', label: 'Processing' },
  COMPLETED: { color: 'success', label: 'Completed' },
  COMPLETED_WITH_ERRORS: { color: 'warning', label: 'Completed with Errors' },
  FAILED: { color: 'error', label: 'Failed' },
};

export function LeaveImport() {
  const canManage = useCan('core.leave_balance.manage');

  const [leaveYear, setLeaveYear] = useState(dayjs().year());
  const [dryRun, setDryRun] = useState(true);
  const [documentId, setDocumentId] = useState(null);
  const [fileName, setFileName] = useState('');
  const [uploading, setUploading] = useState(false);

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

  const handleCustomUpload = async ({ file, onSuccess, onError }) => {
    setUploading(true);
    try {
      const doc = await documentService.upload(file, 'LEAVE_IMPORT');
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
        leaveYear: String(leaveYear),
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

  const historyColumns = [
    {
      title: 'Run ID',
      dataIndex: 'id',
      key: 'id',
      render: (id) => <Text code>{id ? id.substring(0, 8) : '-'}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => {
        const conf = STATUS_TAGS[status] || { color: 'default', label: status };
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
      render: (dt) => (dt ? dayjs(dt).format('YYYY-MM-DD HH:mm:ss') : '-'),
    },
  ];

  return (
    <Card style={{ margin: 24 }}>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <Title level={4} style={{ margin: 0 }}>
          Bulk Leave Import
        </Title>

        <Text type="secondary">
          Upload a spreadsheet (.xlsx or .csv) to bulk allocate initial leave balances or annual
          allotments across employees. Dry run validates records without writing to the database.
        </Text>

        {/* Upload & Configuration Card */}
        <Card type="inner" title="Import Configuration">
          <Row gutter={24} align="middle">
            <Col xs={24} sm={12} md={6}>
              <Text strong>Leave Year:</Text>
              <div style={{ marginTop: 8 }}>
                <InputNumber
                  value={leaveYear}
                  onChange={setLeaveYear}
                  style={{ width: '100%' }}
                />
              </div>
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
                Supports single .xlsx or .csv balance sheets.
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
                  valueStyle={{ color: '#3f8600' }}
                />
              </Col>
              <Col span={8}>
                <Statistic
                  title="Rows Failed"
                  value={currentImport.rowsFailed ?? 0}
                  valueStyle={{ color: currentImport.rowsFailed > 0 ? '#cf1322' : undefined }}
                />
              </Col>
            </Row>

            {currentImport.rowsFailed > 0 && (
              <Alert
                type="warning"
                showIcon
                message="Errors Encountered"
                description={`Some rows had validation errors. ${
                  currentImport.errorDocumentId
                    ? `Download error report document: ${currentImport.errorDocumentId}`
                    : 'Check row constraints, employee numbers, and active leave types.'
                }`}
                style={{ marginTop: 16 }}
              />
            )}
          </Card>
        )}

        {/* Execution History */}
        <Card type="inner" title="Import History" icon={<HistoryOutlined />}>
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
  );
}
