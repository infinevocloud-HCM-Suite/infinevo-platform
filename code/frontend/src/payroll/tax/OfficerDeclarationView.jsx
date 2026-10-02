import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useParams, Link } from 'react-router-dom';
import {
  Card,
  Descriptions,
  Tag,
  Alert,
  Spin,
  Space,
  Typography,
  Row,
  Col,
  Button,
  Empty,
  Select,
} from 'antd';
import {
  EyeOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { declarationService } from './declarationService';
import { currentFy, fyOptions, formatFyDisplay } from './financialYear';
import { readError } from './apiError';

const { Title, Text } = Typography;

// The employee comes from the route (`/payroll/tax-declarations/:employeeId/:fy`); the officer
// endpoint takes a UUID and answers anything else with 400, so nothing else is sent.
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isEmployeeUuid(value) {
  return typeof value === 'string' && UUID_PATTERN.test(value.trim());
}

export function OfficerDeclarationView({ employeeId: propEmployeeId, fy: propFy }) {
  const params = useParams();
  const canRead = useCan('payroll.tax_declaration.read');

  const empId = (propEmployeeId || params?.employeeId || '').trim();
  const validEmpId = isEmployeeUuid(empId);
  const routeFy = propFy || params?.fy;

  const [selectedFy, setSelectedFy] = useState(routeFy || currentFy());
  const [header, setHeader] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchDeclaration = useCallback(
    async (id, fy) => {
      if (!canRead || !isEmployeeUuid(id)) {
        setHeader(null);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const data = await declarationService.headerOf(id, fy);
        setHeader(data);
      } catch (err) {
        const info = readError(err, 'Failed to fetch employee declaration');
        setHeader(null);
        setError(
          info.status === 404
            ? `No tax declaration found for this employee in FY ${formatFyDisplay(fy)}.`
            : info.message,
        );
      } finally {
        setLoading(false);
      }
    },
    [canRead],
  );

  useEffect(() => {
    if (routeFy) setSelectedFy(routeFy);
  }, [routeFy]);

  useEffect(() => {
    fetchDeclaration(empId, selectedFy);
  }, [empId, selectedFy, fetchDeclaration]);

  if (!canRead) {
    return <NotEntitled />;
  }

  const renderStatusTag = (status) => {
    switch (status?.toUpperCase()) {
      case 'SUBMITTED':
        return <Tag color="blue">SUBMITTED</Tag>;
      case 'VERIFIED':
      case 'APPROVED':
        return <Tag color="green">VERIFIED</Tag>;
      case 'REJECTED':
        return <Tag color="red">REJECTED</Tag>;
      case 'DRAFT':
      default:
        return <Tag color="orange">{status || 'DRAFT'}</Tag>;
    }
  };

  const renderBooleanTag = (val) => {
    return val ? (
      <Tag icon={<CheckCircleOutlined />} color="success">
        Yes
      </Tag>
    ) : (
      <Tag icon={<CloseCircleOutlined />} color="default">
        No
      </Tag>
    );
  };

  const regime = header?.tax_regime || 'NEW';

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '24px 16px' }}>
      <Card
        title={
          <Row justify="space-between" align="middle" wrap>
            <Col>
              <Space align="center">
                <EyeOutlined style={{ fontSize: 20, color: '#1677ff' }} />
                <Title level={4} style={{ margin: 0 }}>
                  Officer Declaration Review
                </Title>
              </Space>
            </Col>
            <Col>
              <Tag color="purple" style={{ fontSize: 13, padding: '4px 10px' }}>
                Officer Mode (Read-Only)
              </Tag>
            </Col>
          </Row>
        }
        extra={
          <Button
            icon={<ReloadOutlined />}
            onClick={() => fetchDeclaration(empId, selectedFy)}
            disabled={!validEmpId || loading}
          >
            Refresh
          </Button>
        }
      >
        <Alert
          message="Payroll Officer Review Mode"
          description="You are reviewing employee declaration records in read-only mode. Edits and submissions are disabled."
          type="info"
          showIcon
          style={{ marginBottom: 24 }}
        />

        <Row gutter={[16, 16]} align="bottom" style={{ marginBottom: 24 }}>
          <Col xs={24} sm={12} md={10}>
            {validEmpId && (
              <Link to={`/employees/${empId}`} data-testid="officer-employee-link">
                <ArrowLeftOutlined /> Back to the employee
              </Link>
            )}
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Text strong>Financial Year:</Text>
            <Select
              value={selectedFy}
              onChange={setSelectedFy}
              options={fyOptions().map((opt) => ({
                value: opt.value,
                label: opt.label,
              }))}
              style={{ width: '100%', marginTop: 4 }}
              data-testid="officer-fy-select"
            />
          </Col>
        </Row>

        {!validEmpId && (
          <Alert
            message="No employee selected"
            description="Open this page from an employee's page. The address does not name a valid employee."
            type="warning"
            showIcon
            style={{ marginBottom: 20 }}
            data-testid="officer-invalid-employee"
          />
        )}

        {error && (
          <Alert
            message="Notice"
            description={error}
            type={error.includes('No tax declaration') ? 'warning' : 'error'}
            showIcon
            style={{ marginBottom: 20 }}
          />
        )}

        <Spin spinning={loading}>
          {!validEmpId ? null : !header ? (
            !loading && !error && <Empty description="No declaration found." />
          ) : (
            <Descriptions
              title={`Declaration Header — FY ${formatFyDisplay(selectedFy)}`}
              bordered
              column={{ xxl: 2, xl: 2, lg: 2, md: 1, sm: 1, xs: 1 }}
              size="middle"
            >
              <Descriptions.Item label="Employee ID">
                <Text strong>{header.employee_id || empId}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Tax Regime">
                <Tag color={regime === 'NEW' ? 'cyan' : 'magenta'}>
                  {regime === 'NEW' ? 'New Regime (Sec 115BAC)' : 'Old Regime'}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                {renderStatusTag(header.status)}
              </Descriptions.Item>
              <Descriptions.Item label="Window Status">
                <Space>
                  <Tag color={header.window_open ? 'green' : 'default'}>
                    {header.window_open ? 'Window Open' : 'Window Closed'}
                  </Tag>
                  <Tag color={header.editable ? 'blue' : 'default'}>
                    {header.editable ? 'Editable' : 'Locked'}
                  </Tag>
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="Staying in Rented House">
                {renderBooleanTag(header.is_staying_in_rented_house)}
              </Descriptions.Item>
              <Descriptions.Item label="Repaying Self-Occupied Home Loan">
                {renderBooleanTag(header.is_repaying_self_occupied_loan)}
              </Descriptions.Item>
              <Descriptions.Item label="Has Let-Out Property">
                {renderBooleanTag(header.has_let_out_property)}
              </Descriptions.Item>
              <Descriptions.Item label="Submitted At">
                {header.submitted_at
                  ? dayjs(header.submitted_at).format('YYYY-MM-DD HH:mm')
                  : 'Not yet submitted'}
              </Descriptions.Item>
              <Descriptions.Item label="Last Updated">
                {header.updated_at
                  ? dayjs(header.updated_at).format('YYYY-MM-DD HH:mm')
                  : 'N/A'}
              </Descriptions.Item>
            </Descriptions>
          )}
        </Spin>
      </Card>
    </div>
  );
}

OfficerDeclarationView.propTypes = {
  employeeId: PropTypes.string,
  fy: PropTypes.string,
};

export default OfficerDeclarationView;
