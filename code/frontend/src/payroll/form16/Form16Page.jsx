import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Typography,
  Select,
  Button,
  Tabs,
  Upload,
  Table,
  Tag,
  Alert,
  Spin,
  Empty,
  Space,
  theme,
} from 'antd';
import {
  FileTextOutlined,
  UploadOutlined,
  DownloadOutlined,
  InboxOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { currentFy, fyOptions } from '../tax/financialYear';
import { form16Service } from './form16Service';
import { Form16StatementView } from './Form16StatementView';
import { EmployeeSelect } from '../claims/EmployeeSelect';
import { formatDate } from '../claims/claimLabels';

const { Title, Text, Paragraph } = Typography;
const { Dragger } = Upload;

export function Form16Page() {
  const { token } = theme.useToken();
  const [selectedFy, setSelectedFy] = useState(currentFy());
  const [activeTab, setActiveTab] = useState('statements');

  // Tab 1: Form 16 Statements state
  const [selectedEmployeeId, setSelectedEmployeeId] = useState(null);
  const [statementLoading, setStatementLoading] = useState(false);
  const [statement, setStatement] = useState(null);
  const [statementError, setStatementError] = useState(null);

  // Tab 2: TRACES Part A state
  const [fileList, setFileList] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [uploadError, setUploadError] = useState(null);
  const [certificates, setCertificates] = useState([]);
  const [certificatesLoading, setCertificatesLoading] = useState(false);
  const [downloadingDocId, setDownloadingDocId] = useState(null);

  // Load statement
  const handleGenerateStatement = async () => {
    if (!selectedEmployeeId) return;
    setStatementLoading(true);
    setStatementError(null);
    setStatement(null);

    try {
      const data = await form16Service.getStatement(selectedEmployeeId, selectedFy);
      setStatement(data);
    } catch (err) {
      const status = err?.response?.status;
      const apiCode = err?.response?.data?.code || err?.response?.data?.message;
      if (status === 409 || apiCode === 'DEDUCTOR_NOT_SET') {
        setStatementError({
          type: '409',
          message: 'Tax Deductor Details Incomplete',
          description:
            'Employer TAN, PAN, or TDS Circle is not configured. Please complete Deductor Settings in Payroll Settings before generating statements.',
        });
      } else if (status === 404) {
        setStatementError({
          type: '404',
          message: 'No Active TDS Record Found',
          description: `No active tax or TDS records were found for this employee in Financial Year ${selectedFy}.`,
        });
      } else {
        setStatementError({
          type: 'generic',
          message: 'Failed to Generate Form 16',
          description: err?.response?.data?.message || err.message || 'An error occurred while fetching the statement.',
        });
      }
    } finally {
      setStatementLoading(false);
    }
  };

  // Load certificates list for Tab 2
  const loadCertificates = useCallback(async (fy) => {
    setCertificatesLoading(true);
    try {
      const rows = await form16Service.listPartA(fy);
      setCertificates(rows || []);
    } catch {
      setCertificates([]);
    } finally {
      setCertificatesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (activeTab === 'traces') {
      loadCertificates(selectedFy);
    }
  }, [activeTab, selectedFy, loadCertificates]);

  // Handle FY change
  const handleFyChange = (fy) => {
    setSelectedFy(fy);
    setStatement(null);
    setStatementError(null);
    setUploadResult(null);
    setUploadError(null);
  };

  // Upload TRACES ZIP
  const handleUploadZip = async () => {
    if (fileList.length === 0) return;
    const file = fileList[0];

    // Client-side validations
    if (!file.name.toLowerCase().endsWith('.zip')) {
      setUploadError('Only valid .zip archive files are accepted.');
      return;
    }
    if (file.size > 50 * 1024 * 1024) {
      setUploadError('The ZIP file exceeds the maximum allowed size of 50 MB.');
      return;
    }

    setUploading(true);
    setUploadError(null);
    setUploadResult(null);

    try {
      const result = await form16Service.uploadPartA(selectedFy, file);
      setUploadResult(result);
      setFileList([]);
      loadCertificates(selectedFy);
    } catch (err) {
      setUploadError(
        err?.response?.data?.message || err.message || 'Failed to process the TRACES ZIP file.'
      );
    } finally {
      setUploading(false);
    }
  };

  // Download certificate
  const handleDownloadDoc = async (documentId) => {
    setDownloadingDocId(documentId);
    try {
      const res = await form16Service.getDocumentDownloadLink(documentId);
      const url = res?.url || res?.link;
      if (url) {
        window.open(url, '_blank', 'noopener,noreferrer');
      }
    } catch {
      // error handling
    } finally {
      setDownloadingDocId(null);
    }
  };

  const certificateColumns = [
    {
      title: 'Employee ID',
      dataIndex: 'employee_id',
      key: 'employee_id',
      render: (id) => <Text code>{id ? String(id).slice(0, 8) + '...' : '-'}</Text>,
    },
    {
      title: 'Source File Name',
      dataIndex: 'source_file_name',
      key: 'source_file_name',
      render: (name) => <Text strong>{name}</Text>,
    },
    {
      title: 'Uploaded At',
      dataIndex: 'uploaded_at',
      key: 'uploaded_at',
      render: (dt) => formatDate(dt),
    },
    {
      title: 'Action',
      key: 'action',
      align: 'right',
      render: (_, record) => (
        <Button
          icon={<DownloadOutlined />}
          size="small"
          loading={downloadingDocId === record.document_id}
          onClick={() => handleDownloadDoc(record.document_id)}
        >
          Download PDF
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }} data-testid="form16-page">
      {/* Header Bar */}
      <div style={{ marginBottom: token.marginLG }}>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col xs={24} md={16}>
            <Title level={2} style={{ margin: 0 }}>
              Form 16 Tax Management
            </Title>
            <Text type="secondary">
              Generate employee annual tax deduction statements (Part B) and manage TRACES certificate archives (Part A).
            </Text>
          </Col>
          <Col xs={24} md={8} style={{ textAlign: 'right' }}>
            <Space>
              <Text strong>Financial Year:</Text>
              <Select
                value={selectedFy}
                onChange={handleFyChange}
                options={fyOptions()}
                style={{ width: 140 }}
                aria-label="Select Financial Year"
              />
            </Space>
          </Col>
        </Row>
      </div>

      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        type="card"
        items={[
          {
            key: 'statements',
            label: (
              <Space>
                <FileTextOutlined />
                <span>Part B Statements</span>
              </Space>
            ),
            children: (
              <div>
                <Card style={{ marginBottom: token.marginMD, borderRadius: token.borderRadiusLG }}>
                  <Row gutter={[16, 16]} align="middle">
                    <Col xs={24} sm={16} md={18}>
                      <Space orientation="vertical" size="small" style={{ width: '100%' }}>
                        <Text strong>Select Employee:</Text>
                        <EmployeeSelect
                          value={selectedEmployeeId}
                          onChange={(val) => {
                            setSelectedEmployeeId(val);
                            setStatement(null);
                            setStatementError(null);
                          }}
                          placeholder="Search employee by name or number..."
                          style={{ width: '100%' }}
                        />
                      </Space>
                    </Col>
                    <Col xs={24} sm={8} md={6} style={{ alignSelf: 'flex-end' }}>
                      <Button
                        type="primary"
                        icon={<SearchOutlined />}
                        onClick={handleGenerateStatement}
                        loading={statementLoading}
                        disabled={!selectedEmployeeId}
                        style={{ width: '100%' }}
                      >
                        Generate Form 16
                      </Button>
                    </Col>
                  </Row>
                </Card>

                {statementLoading && (
                  <Card style={{ textAlign: 'center', padding: token.paddingXL, borderRadius: token.borderRadiusLG }}>
                    <Spin size="large" tip="Generating Form 16 statement..." />
                  </Card>
                )}

                {statementError && (
                  <Alert
                    type={statementError.type === '409' ? 'warning' : statementError.type === '404' ? 'info' : 'error'}
                    showIcon
                    message={statementError.message}
                    description={statementError.description}
                    style={{ marginBottom: token.marginMD }}
                  />
                )}

                {statement && !statementLoading && (
                  <Form16StatementView statement={statement} />
                )}

                {!statement && !statementLoading && !statementError && (
                  <Card style={{ textAlign: 'center', padding: token.paddingXL, borderRadius: token.borderRadiusLG }}>
                    <Empty
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                      description="Select an employee and click 'Generate Form 16' to view the annual tax certificate."
                    />
                  </Card>
                )}
              </div>
            ),
          },
          {
            key: 'traces',
            label: (
              <Space>
                <UploadOutlined />
                <span>TRACES Part A Upload</span>
              </Space>
            ),
            children: (
              <div>
                {/* Upload Card */}
                <Card title="Upload TRACES ZIP Archive" style={{ marginBottom: token.marginMD, borderRadius: token.borderRadiusLG }}>
                  <Paragraph type="secondary">
                    Upload the official TRACES Form 16 Part A zip archive downloaded from the Income Tax portal for FY {selectedFy}.
                    The archive will be unpacked, validated, and mapped directly to active employees by PAN.
                  </Paragraph>

                  <Dragger
                    accept=".zip,application/zip,application/x-zip-compressed"
                    multiple={false}
                    fileList={fileList}
                    beforeUpload={(file) => {
                      setFileList([file]);
                      return false;
                    }}
                    onRemove={() => setFileList([])}
                    style={{ padding: token.paddingMD }}
                  >
                    <p className="ant-upload-drag-icon">
                      <InboxOutlined style={{ fontSize: 48, color: token.colorPrimary }} />
                    </p>
                    <p className="ant-upload-text">Click or drag TRACES ZIP archive to this area</p>
                    <p className="ant-upload-hint">Supported file: .zip archive up to 50 MB</p>
                  </Dragger>

                  {uploadError && (
                    <Alert
                      type="error"
                      showIcon
                      message="Upload Failed"
                      description={uploadError}
                      style={{ marginTop: token.marginMD }}
                    />
                  )}

                  {uploadResult && (
                    <Alert
                      type="success"
                      showIcon
                      message="Processing Complete"
                      description={
                        <Space size="middle" style={{ marginTop: token.marginXS }}>
                          <Tag color="success" icon={<CheckCircleOutlined />}>
                            {uploadResult.matched} Matched
                          </Tag>
                          <Tag color="warning" icon={<ExclamationCircleOutlined />}>
                            {uploadResult.unmatched?.length || 0} Unmatched
                          </Tag>
                          <Tag color="default">
                            {uploadResult.skipped?.length || 0} Skipped
                          </Tag>
                        </Space>
                      }
                      style={{ marginTop: token.marginMD }}
                    />
                  )}

                  <div style={{ marginTop: token.marginMD, textAlign: 'right' }}>
                    <Button
                      type="primary"
                      icon={<UploadOutlined />}
                      onClick={handleUploadZip}
                      loading={uploading}
                      disabled={fileList.length === 0}
                    >
                      Upload & Process ZIP
                    </Button>
                  </div>
                </Card>

                {/* Certificates Table */}
                <Card
                  title={`Uploaded Part A Certificates — FY ${selectedFy}`}
                  style={{ borderRadius: token.borderRadiusLG }}
                >
                  <Table
                    columns={certificateColumns}
                    dataSource={certificates.map((row, idx) => ({ ...row, key: row.document_id || idx }))}
                    loading={certificatesLoading}
                    pagination={{ pageSize: 10 }}
                    size="middle"
                    locale={{
                      emptyText: (
                        <Empty
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description={`No Part A certificates found for FY ${selectedFy}. Upload a TRACES ZIP archive above.`}
                        />
                      ),
                    }}
                  />
                </Card>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
