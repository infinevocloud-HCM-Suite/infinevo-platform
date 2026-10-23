import { useState, useEffect } from 'react';
import { Card, Upload, Button, Switch, Input, Table, Alert, Space, Typography, Tag, Row, Col, theme } from 'antd';
import { UploadOutlined, DownloadOutlined, InboxOutlined, FileTextOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { wfhService } from './wfhService.js';
import { formatDate } from '@shared/ui/format.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;
const { Dragger } = Upload;

export function WfhImport() {
  const { token } = theme.useToken();
  const canManage = useCan('core.wfh_balance.manage');

  const [fileList, setFileList] = useState([]);
  const [dryRun, setDryRun] = useState(false);
  const [wfhYear, setWfhYear] = useState(String(new Date().getFullYear()));
  const [uploading, setUploading] = useState(false);
  const [lastResult, setLastResult] = useState(null);
  const [logs, setLogs] = useState([]);
  const [loadingLogs, setLoadingLogs] = useState(false);

  const fetchLogs = async () => {
    setLoadingLogs(true);
    try {
      const data = await wfhService.getImportLogs();
      setLogs(Array.isArray(data) ? data : []);
    } catch (err) {
      console.warn('Failed to fetch import logs', err);
    } finally {
      setLoadingLogs(false);
    }
  };

  useEffect(() => {
    if (canManage) {
      fetchLogs();
    }
  }, [canManage]);

  if (!canManage) {
    return <NotEntitled action="core.wfh_balance.manage" />;
  }

  const handleDownloadTemplate = async () => {
    try {
      const blob = await wfhService.downloadTemplate();
      const url = window.URL.createObjectURL(new Blob([blob], { type: 'text/csv' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = 'wfh_allocations_template.csv';
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      errorMsg('Download Failed', err?.message || 'Could not download template');
    }
  };

  const handleUpload = async () => {
    if (fileList.length === 0) {
      errorMsg('Validation', 'Please select a CSV file to upload');
      return;
    }

    setUploading(true);
    setLastResult(null);

    try {
      const formData = new FormData();
      formData.append('file', fileList[0].originFileObj || fileList[0]);
      formData.append('wfhYear', wfhYear);
      formData.append('dryRun', String(dryRun));

      const res = await wfhService.uploadImport(formData);
      setLastResult(res);

      if (res.status === 'COMPLETED' || res.status === 'PARTIAL') {
        await successMsg(
          dryRun ? 'Validation Complete' : 'Import Complete',
          `Successfully processed ${res.rowsImported} of ${res.rowsTotal} rows.`
        );
      } else {
        await errorMsg('Import Failed', `All ${res.rowsFailed} rows failed.`);
      }

      setFileList([]);
      fetchLogs();
    } catch (err) {
      errorMsg('Upload Failed', err?.response?.data?.message || err?.message);
    } finally {
      setUploading(false);
    }
  };

  const logColumns = [
    {
      title: 'File Name',
      dataIndex: 'fileName',
      key: 'fileName',
      render: (f) => <Text strong>{f}</Text>,
    },
    {
      title: 'Year',
      dataIndex: 'wfhYear',
      key: 'wfhYear',
      render: (y) => <Tag color="blue">{y}</Tag>,
    },
    {
      title: 'Mode',
      dataIndex: 'dryRun',
      key: 'dryRun',
      render: (d) => (d ? <Tag color="orange">Dry Run</Tag> : <Tag color="green">Committed</Tag>),
    },
    {
      title: 'Total Rows',
      dataIndex: 'rowsTotal',
      key: 'rowsTotal',
    },
    {
      title: 'Imported',
      dataIndex: 'rowsImported',
      key: 'rowsImported',
      render: (v) => <Text type="success">{v}</Text>,
    },
    {
      title: 'Failed',
      dataIndex: 'rowsFailed',
      key: 'rowsFailed',
      render: (v) => (v > 0 ? <Text type="danger">{v}</Text> : '0'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => {
        if (s === 'COMPLETED') return <Tag color="success">Completed</Tag>;
        if (s === 'PARTIAL') return <Tag color="warning">Partial</Tag>;
        if (s === 'FAILED') return <Tag color="error">Failed</Tag>;
        return <Tag color="processing">{s}</Tag>;
      },
    },
    {
      title: 'Date',
      dataIndex: 'startedAt',
      key: 'startedAt',
      render: (d) => formatDate(d),
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }}>
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Title level={3} style={{ margin: 0 }}>
              <FileTextOutlined style={{ marginRight: 8 }} />
              Bulk WFH Quota Import
            </Title>
            <Text type="secondary">Upload annual quota allocations for multiple employees via CSV</Text>
          </div>

          <Button
            icon={<DownloadOutlined />}
            onClick={handleDownloadTemplate}
            data-testid="download-wfh-template-btn"
          >
            Download CSV Template
          </Button>
        </div>

        <Card title="Upload Allocations CSV" style={{ borderRadius: token.borderRadiusLG }}>
          <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
            <Row gutter={16} align="middle">
              <Col xs={24} sm={8}>
                <Text strong>Target Allocation Year: </Text>
                <Input
                  value={wfhYear}
                  onChange={(e) => setWfhYear(e.target.value)}
                  style={{ width: 120, marginLeft: 8 }}
                  maxLength={4}
                />
              </Col>
              <Col xs={24} sm={8}>
                <Space>
                  <Switch checked={dryRun} onChange={setDryRun} />
                  <Text>Dry Run (Validate without saving)</Text>
                </Space>
              </Col>
              <Col xs={24} sm={8} style={{ textAlign: 'right' }}>
                <Button
                  type="primary"
                  icon={<UploadOutlined />}
                  onClick={handleUpload}
                  loading={uploading}
                  disabled={fileList.length === 0}
                  data-testid="upload-wfh-csv-btn"
                >
                  {dryRun ? 'Validate File' : 'Import Allocations'}
                </Button>
              </Col>
            </Row>

            <Dragger
              fileList={fileList}
              beforeUpload={(file) => {
                setFileList([file]);
                return false;
              }}
              onRemove={() => setFileList([])}
              accept=".csv"
              maxCount={1}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined style={{ fontSize: 48, color: token.colorPrimary }} />
              </p>
              <p className="ant-upload-text">Click or drag CSV file to this area to upload</p>
              <p className="ant-upload-hint">
                Header columns required: <code>employee_code,year,allocated_days</code>
              </p>
            </Dragger>

            {lastResult && (
              <Alert
                type={lastResult.rowsFailed === 0 ? 'success' : (lastResult.rowsImported > 0 ? 'warning' : 'error')}
                showIcon
                message={
                  <span>
                    Import Status: <strong>{lastResult.status}</strong> —{' '}
                    Imported: {lastResult.rowsImported} / Total: {lastResult.rowsTotal}
                    {lastResult.rowsFailed > 0 && ` (Failed: ${lastResult.rowsFailed})`}
                  </span>
                }
                description={
                  lastResult.errors?.length > 0 && (
                    <ul style={{ margin: '8px 0 0 0', paddingLeft: 20 }}>
                      {lastResult.errors.slice(0, 10).map((err, i) => (
                        <li key={i}>{err}</li>
                      ))}
                      {lastResult.errors.length > 10 && (
                        <li>...and {lastResult.errors.length - 10} more errors</li>
                      )}
                    </ul>
                  )
                }
              />
            )}
          </Space>
        </Card>

        <Card title="Past Import History" style={{ borderRadius: token.borderRadiusLG }}>
          <Table
            dataSource={logs}
            columns={logColumns}
            rowKey="id"
            loading={loadingLogs}
            pagination={{ pageSize: 10 }}
            data-testid="wfh-import-logs-table"
          />
        </Card>
      </Space>
    </div>
  );
}
