import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Typography,
  Select,
  Button,
  Tag,
  Alert,
  Spin,
  Space,
  Statistic,
  Empty,
  theme,
} from 'antd';
import {
  FileTextOutlined,
  DownloadOutlined,
  SafetyCertificateOutlined,
  CheckCircleOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { currentFy, fyOptions } from '../tax/financialYear';
import { form16Service } from './form16Service';
import { Form16StatementView } from './Form16StatementView';
import { formatAmount } from '../claims/claimLabels';

const { Title, Text } = Typography;

export function EmployeeForm16Panel() {
  const { token } = theme.useToken();
  const [selectedFy, setSelectedFy] = useState(currentFy());
  const [loading, setLoading] = useState(false);
  const [statement, setStatement] = useState(null);
  const [statementError, setStatementError] = useState(null);
  const [partA, setPartA] = useState(null);
  const [partALoading, setPartALoading] = useState(false);

  const loadData = useCallback(async (fy) => {
    setLoading(true);
    setStatementError(null);
    setPartALoading(true);

    try {
      const stmt = await form16Service.getOwnStatement(fy);
      setStatement(stmt);
    } catch (err) {
      const status = err?.status || err?.response?.status;
      const code = err?.code || err?.response?.data?.code;
      if (status === 404 || code === 'NOT_FOUND') {
        setStatementError('No active TDS records or Form 16 statements are available for this financial year.');
      } else if (status === 409 || code === 'CONFLICT') {
        setStatementError('Company tax deductor details are not configured yet. Please contact your payroll administrator.');
      } else {
        setStatementError(
          err?.message && err.message !== 'Something went wrong'
            ? err.message
            : 'No active TDS records or Form 16 statements are available for this financial year.'
        );
      }
      setStatement(null);
    } finally {
      setLoading(false);
    }

    try {
      const cert = await form16Service.getOwnPartA(fy);
      setPartA(cert);
    } catch {
      setPartA(null);
    } finally {
      setPartALoading(false);
    }
  }, []);

  useEffect(() => {
    loadData(selectedFy);
  }, [selectedFy, loadData]);

  return (
    <div data-testid="employee-form16-panel">
      {/* Header with FY Picker */}
      <Card style={{ marginBottom: token.marginMD, borderRadius: token.borderRadiusLG }}>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col xs={24} sm={16}>
            <Title level={4} style={{ margin: 0 }}>
              Form 16 & Tax Deductions
            </Title>
            <Text type="secondary">
              View your annual TDS summary, download Form 16 Part B, and access TRACES certified Part A.
            </Text>
          </Col>
          <Col xs={24} sm={8} style={{ textAlign: 'right' }}>
            <Space>
              <Text strong>FY:</Text>
              <Select
                value={selectedFy}
                onChange={setSelectedFy}
                options={fyOptions()}
                style={{ width: 140 }}
                aria-label="Select Financial Year"
              />
            </Space>
          </Col>
        </Row>
      </Card>

      {/* Overview Cards (TDS to Date & Part A Certificate) */}
      <Row gutter={[16, 16]} style={{ marginBottom: token.marginMD }}>
        <Col xs={24} md={12}>
          <Card
            title={
              <Space>
                <FileTextOutlined />
                <span>TDS Deducted to Date (FY {selectedFy})</span>
              </Space>
            }
            bordered
            style={{ height: '100%', borderRadius: token.borderRadiusLG }}
          >
            {loading ? (
              <Spin />
            ) : (
              <div>
                <Statistic
                  title="Total Tax Deducted by Employer"
                  value={`₹${formatAmount(statement?.totalDeducted || 0)}`}
                  styles={{ content: { color: token.colorPrimary, fontWeight: 600 } }}
                />
                <div style={{ marginTop: token.marginSM }}>
                  <Text type="secondary">Assessment Year: </Text>
                  <Tag color="purple">{statement?.assessmentYear || '-'}</Tag>
                  <Text type="secondary" style={{ marginLeft: token.marginSM }}>Regime: </Text>
                  <Tag color="blue">{statement?.regime || 'NEW'}</Tag>
                </div>
              </div>
            )}
          </Card>
        </Col>

        <Col xs={24} md={12}>
          <Card
            title={
              <Space>
                <SafetyCertificateOutlined />
                <span>TRACES Certificate (Part A)</span>
              </Space>
            }
            bordered
            style={{ height: '100%', borderRadius: token.borderRadiusLG }}
          >
            {partALoading ? (
              <Spin />
            ) : partA?.link ? (
              <div>
                <Alert
                  type="success"
                  showIcon
                  icon={<CheckCircleOutlined />}
                  message="Certified Part A Available"
                  description="Your digitally signed Form 16 Part A certificate from TRACES is available for download."
                  style={{ marginBottom: token.marginSM }}
                />
                <Button
                  type="primary"
                  icon={<DownloadOutlined />}
                  href={partA.link}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Download Part A (PDF)
                </Button>
              </div>
            ) : (
              <div>
                <Alert
                  type="info"
                  showIcon
                  icon={<InfoCircleOutlined />}
                  message="Certificate Pending"
                  description={`TRACES Part A certificate for FY ${selectedFy} has not been published yet. Please check back after quarterly TDS filing.`}
                />
              </div>
            )}
          </Card>
        </Col>
      </Row>

      {/* Part B Statement */}
      {loading && (
        <Card style={{ textAlign: 'center', padding: token.paddingXL, borderRadius: token.borderRadiusLG }}>
          <Spin size="large" tip="Loading annual Form 16 statement..." />
        </Card>
      )}

      {statementError && !loading && (
        <Alert
          type="info"
          showIcon
          icon={<InfoCircleOutlined />}
          message="No Form 16 Statement"
          description={statementError}
          style={{ marginBottom: token.marginMD }}
        />
      )}

      {statement && !loading && (
        <Form16StatementView statement={statement} />
      )}

      {!statement && !loading && !statementError && (
        <Card style={{ textAlign: 'center', padding: token.paddingXL, borderRadius: token.borderRadiusLG }}>
          <Empty description="No Form 16 statement available." />
        </Card>
      )}
    </div>
  );
}
