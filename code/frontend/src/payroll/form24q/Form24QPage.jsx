import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Typography,
  Select,
  Radio,
  Button,
  Statistic,
  Alert,
  Space,
  Tooltip,
  Tag,
  theme,
  message,
} from 'antd';
import {
  PlusOutlined,
  DownloadOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  FileTextOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import { currentFy, fyOptions, fyForApi } from '../tax/financialYear';
import { form24QService } from './form24QService';
import { ChallanList } from './ChallanList';
import { ChallanModal } from './ChallanModal';
import { PageHeader } from '@shared/ui/PageHeader.jsx';

const { Text, Paragraph } = Typography;

export function Form24QPage() {
  const { token } = theme.useToken();
  const [selectedFy, setSelectedFy] = useState(currentFy());
  const [selectedQuarter, setSelectedQuarter] = useState('Q1');

  // Challans state
  const [challans, setChallans] = useState([]);
  const [challansLoading, setChallansLoading] = useState(false);

  // Reconciliation preview state
  const [preview, setPreview] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState(null);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);

  // Export loading states
  const [exportingA1Csv, setExportingA1Csv] = useState(false);
  const [exportingA1Txt, setExportingA1Txt] = useState(false);
  const [exportingA2Csv, setExportingA2Csv] = useState(false);
  const [exportingA2Txt, setExportingA2Txt] = useState(false);

  const loadData = useCallback(async () => {
    setChallansLoading(true);
    setPreviewLoading(true);
    setPreviewError(null);

    try {
      const [challanList, previewData] = await Promise.all([
        form24QService.listChallans(selectedFy, selectedQuarter),
        form24QService.getAnnexure1Preview(selectedFy, selectedQuarter).catch((err) => {
          setPreviewError(err?.response?.data?.message || err.message);
          return null;
        }),
      ]);
      setChallans(challanList);
      setPreview(previewData);
    } catch (err) {
      message.error(err?.message || 'Failed to load Form 24Q data');
    } finally {
      setChallansLoading(false);
      setPreviewLoading(false);
    }
  }, [selectedFy, selectedQuarter]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const triggerDownload = (blob, filename) => {
    const url = window.URL.createObjectURL(new Blob([blob]));
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.parentNode?.removeChild(link);
    window.URL.revokeObjectURL(url);
  };

  const handleExportA1 = async (format) => {
    const setLoader = format === 'CSV' ? setExportingA1Csv : setExportingA1Txt;
    try {
      setLoader(true);
      const ext = format === 'CSV' ? 'csv' : 'txt';
      const filename = `Form24Q_${fyForApi(selectedFy)}_${selectedQuarter}_Annexure1.${ext}`;
      const blob = await form24QService.exportAnnexure1(selectedFy, selectedQuarter, format);
      triggerDownload(blob, filename);
    } catch (err) {
      message.error(err?.response?.data?.message || `Failed to export Annexure I (${format})`);
    } finally {
      setLoader(false);
    }
  };

  const handleExportA2 = async (format) => {
    if (selectedQuarter !== 'Q4') return;
    const setLoader = format === 'CSV' ? setExportingA2Csv : setExportingA2Txt;
    try {
      setLoader(true);
      const ext = format === 'CSV' ? 'csv' : 'txt';
      const filename = `Form24Q_${fyForApi(selectedFy)}_Annexure2.${ext}`;
      const blob = await form24QService.exportAnnexure2(selectedFy, format);
      triggerDownload(blob, filename);
    } catch (err) {
      message.error(err?.response?.data?.message || `Failed to export Annexure II (${format})`);
    } finally {
      setLoader(false);
    }
  };

  const formatInr = (amount) =>
    amount != null
      ? `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
      : '₹0.00';

  const isQ4 = selectedQuarter === 'Q4';

  return (
    <div style={{ padding: '0 24px 24px' }}>
      <PageHeader
        title="Form 24Q (TDS) Compliance"
        subtitle="Manage government ITNS 281 tax challans, quarterly deductee reconciliation, and generate NSDL e-TDS returns"
        actions={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalOpen(true)}
            data-testid="record-challan-btn"
          >
            Record Challan
          </Button>
        }
      />

      {/* Control Bar: FY and Quarter */}
      <Card style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]} align="middle" justify="space-between">
          <Col xs={24} sm={10} md={8}>
            <Space align="center">
              <Text strong>Financial Year:</Text>
              <Select
                value={selectedFy}
                onChange={setSelectedFy}
                options={fyOptions()}
                style={{ width: 140 }}
                data-testid="fy-selector"
              />
            </Space>
          </Col>
          <Col xs={24} sm={14} md={16} style={{ textAlign: 'right' }}>
            <Radio.Group
              value={selectedQuarter}
              onChange={(e) => setSelectedQuarter(e.target.value)}
              buttonStyle="solid"
              data-testid="quarter-selector"
            >
              <Radio.Button value="Q1">Q1 (Apr – Jun)</Radio.Button>
              <Radio.Button value="Q2">Q2 (Jul – Sep)</Radio.Button>
              <Radio.Button value="Q3">Q3 (Oct – Dec)</Radio.Button>
              <Radio.Button value="Q4">Q4 (Jan – Mar)</Radio.Button>
            </Radio.Group>
          </Col>
        </Row>
      </Card>

      {/* Section 1: Pre-flight & Reconciliation Summary */}
      <Card
        title={
          <Space>
            <AuditOutlined style={{ color: token.colorPrimary }} />
            <span>Quarterly Pre-flight & Reconciliation Status ({selectedQuarter})</span>
          </Space>
        }
        loading={previewLoading}
        style={{ marginBottom: 16 }}
      >
        {previewError && (
          <Alert
            type="warning"
            showIcon
            message="Notice"
            description={previewError}
            style={{ marginBottom: 16 }}
          />
        )}

        {preview && (
          <>
            {preview.hasMissingPans && (
              <Alert
                type="warning"
                showIcon
                icon={<WarningOutlined />}
                message="Missing PAN Detected"
                description="One or more employees do not have a valid PAN recorded. Tax is deducted at higher rates under Sec 206AA (PANNOTAVBL) with Reason Code Y."
                style={{ marginBottom: 16 }}
                data-testid="missing-pan-alert"
              />
            )}

            {preview.shortfallAmount > 0 && (
              <Alert
                type="error"
                showIcon
                icon={<WarningOutlined />}
                message="Deposit Shortfall Detected"
                description={`Total deposited TDS is less than payroll deductions by ${formatInr(preview.shortfallAmount)}. Please record and allocate additional challans before filing.`}
                style={{ marginBottom: 16 }}
                data-testid="shortfall-alert"
              />
            )}

            {preview.isReconciled && !preview.hasMissingPans && (
              <Alert
                type="success"
                showIcon
                icon={<CheckCircleOutlined />}
                message="Return Ready for Filing"
                description="Total TDS deductions match deposited challans and all deductees have valid PANs."
                style={{ marginBottom: 16 }}
                data-testid="reconciled-alert"
              />
            )}

            <Row gutter={[16, 16]}>
              <Col xs={12} sm={8} md={4}>
                <Statistic
                  title="Deductees Count"
                  value={preview.totalDeducteesCount ?? 0}
                  data-testid="stat-deductees-count"
                />
              </Col>
              <Col xs={12} sm={8} md={5}>
                <Statistic
                  title="Total Gross Salary"
                  value={formatInr(preview.totalGrossSalaryPaid)}
                  data-testid="stat-gross-salary"
                />
              </Col>
              <Col xs={12} sm={8} md={5}>
                <Statistic
                  title="Total TDS Deducted"
                  value={formatInr(preview.totalTdsDeducted)}
                  valueStyle={{ color: token.colorError }}
                  data-testid="stat-tds-deducted"
                />
              </Col>
              <Col xs={12} sm={8} md={5}>
                <Statistic
                  title="Total TDS Deposited"
                  value={formatInr(preview.totalTdsDeposited)}
                  valueStyle={{ color: token.colorSuccess }}
                  data-testid="stat-tds-deposited"
                />
              </Col>
              <Col xs={12} sm={8} md={5}>
                <Statistic
                  title="Net Difference"
                  value={formatInr(preview.shortfallAmount)}
                  valueStyle={{
                    color: preview.shortfallAmount > 0 ? token.colorError : token.colorSuccess,
                  }}
                  data-testid="stat-net-difference"
                />
              </Col>
            </Row>
          </>
        )}
      </Card>

      {/* Section 2: Challans List */}
      <Card
        title={
          <Row justify="space-between" align="middle">
            <Col>
              <Space>
                <FileTextOutlined style={{ color: token.colorPrimary }} />
                <span>Recorded ITNS 281 Challans ({selectedQuarter})</span>
              </Space>
            </Col>
            <Col>
              <Button
                type="dashed"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => setModalOpen(true)}
              >
                Add Challan
              </Button>
            </Col>
          </Row>
        }
        style={{ marginBottom: 16 }}
      >
        <ChallanList
          challans={challans}
          loading={challansLoading}
          onRecordClick={() => setModalOpen(true)}
        />
      </Card>

      {/* Section 3: Statutory Exports */}
      <Card
        title={
          <Space>
            <DownloadOutlined style={{ color: token.colorPrimary }} />
            <span>Form 24Q Statutory Return Exports</span>
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          message="Notice on NSDL e-TDS FVU Format"
          description="The exported e-TDS text files (.txt) are formatted according to NSDL Form 24Q specifications. Ensure the file is validated via the official Income Tax Department NSDL FVU utility and reviewed by your Chartered Accountant (CA) prior to filing."
          style={{ marginBottom: 16 }}
        />
        <Row gutter={[24, 24]}>
          <Col xs={24} md={12}>
            <Card
              type="inner"
              title="Annexure I — Quarterly Deductee Statement"
              extra={<Tag color="blue">{selectedQuarter}</Tag>}
            >
              <Paragraph type="secondary">
                Contains quarterly challan headers (CD) and deductee transaction records (DD) mapped from
                settled pay runs in {selectedQuarter}.
              </Paragraph>
              <Space wrap orientation="horizontal">
                <Button
                  icon={<DownloadOutlined />}
                  onClick={() => handleExportA1('CSV')}
                  loading={exportingA1Csv}
                  data-testid="export-a1-csv-btn"
                >
                  Download Annexure I (CSV)
                </Button>
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  onClick={() => handleExportA1('TXT')}
                  loading={exportingA1Txt}
                  data-testid="export-a1-txt-btn"
                >
                  Download Annexure I (e-TDS TXT)
                </Button>
              </Space>
            </Card>
          </Col>

          <Col xs={24} md={12}>
            <Card
              type="inner"
              title="Annexure II — Annual Salary & Tax Statement"
              extra={
                <Tag color={isQ4 ? 'green' : 'default'}>
                  {isQ4 ? 'Q4 (Annual Return)' : 'Q4 Only'}
                </Tag>
              }
            >
              <Paragraph type="secondary">
                Contains annual salary details (SD), Chapter VI-A deductions, rebate, and net tax computed
                for all employees active or terminated during FY {selectedFy}. Filed with Quarter 4 return only.
              </Paragraph>
              <Space orientation="horizontal" wrap>
                <Tooltip
                  title={!isQ4 ? 'Annexure II is filed with Q4 return only' : ''}
                >
                  <span>
                    <Button
                      icon={<DownloadOutlined />}
                      onClick={() => handleExportA2('CSV')}
                      loading={exportingA2Csv}
                      disabled={!isQ4}
                      data-testid="export-a2-csv-btn"
                    >
                      Download Annexure II (CSV)
                    </Button>
                  </span>
                </Tooltip>
                <Tooltip
                  title={!isQ4 ? 'Annexure II is filed with Q4 return only' : ''}
                >
                  <span>
                    <Button
                      type="primary"
                      icon={<DownloadOutlined />}
                      onClick={() => handleExportA2('TXT')}
                      loading={exportingA2Txt}
                      disabled={!isQ4}
                      data-testid="export-a2-txt-btn"
                    >
                      Download Annexure II (e-TDS TXT)
                    </Button>
                  </span>
                </Tooltip>
              </Space>
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Record Challan Modal */}
      <ChallanModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onSuccess={loadData}
        financialYear={selectedFy}
        quarter={selectedQuarter}
      />
    </div>
  );
}
