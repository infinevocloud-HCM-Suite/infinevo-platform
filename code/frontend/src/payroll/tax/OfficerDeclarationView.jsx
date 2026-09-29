import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
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
  Input,
  Select,
} from 'antd';
import {
  EyeOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { declarationService } from './declarationService';
import { currentFy, fyOptions, formatFyDisplay } from './financialYear';

const { Title, Text } = Typography;

export function OfficerDeclarationView({ employeeId: propEmployeeId, fy: propFy }) {
  const [empId, setEmpId] = useState(propEmployeeId || '');
  const [inputEmpId, setInputEmpId] = useState(propEmployeeId || '');
  const [selectedFy, setSelectedFy] = useState(propFy || currentFy());
  const [header, setHeader] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const fetchDeclaration = useCallback(async (id, fy) => {
    if (!id || !id.trim()) {
      setHeader(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const data = await declarationService.headerOf(id.trim(), fy);
      setHeader(data);
    } catch (err) {
      if (err?.response?.status === 404) {
        setHeader(null);
        setError(`No tax declaration found for employee "${id}" in FY ${formatFyDisplay(fy)}.`);
      } else {
        setError(err?.response?.data?.message || err?.message || 'Failed to fetch employee declaration');
        setHeader(null);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (propEmployeeId) {
      setEmpId(propEmployeeId);
      setInputEmpId(propEmployeeId);
    }
  }, [propEmployeeId]);

  useEffect(() => {
    if (propFy) {
      setSelectedFy(propFy);
    }
  }, [propFy]);

  useEffect(() => {
    if (empId) {
      fetchDeclaration(empId, selectedFy);
    }
  }, [empId, selectedFy, fetchDeclaration]);

  const handleSearch = () => {
    setEmpId(inputEmpId.trim());
  };

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
            disabled={!empId || loading}
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
            <Text strong>Employee ID:</Text>
            <Input.Search
              placeholder="Enter Employee ID (e.g. EMP-001)"
              value={inputEmpId}
              onChange={(e) => setInputEmpId(e.target.value)}
              onSearch={handleSearch}
              enterButton={<SearchOutlined />}
              style={{ marginTop: 4 }}
              data-testid="officer-employee-input"
            />
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
          {!empId ? (
            <Empty description="Enter an Employee ID above to review their tax declaration header." />
          ) : !header ? (
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
                <Tag color={header.regime === 'NEW' ? 'cyan' : 'magenta'}>
                  {header.regime === 'NEW' ? 'New Regime (Sec 115BAC)' : 'Old Regime'}
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
