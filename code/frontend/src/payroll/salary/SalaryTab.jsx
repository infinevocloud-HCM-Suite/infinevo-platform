import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useParams } from 'react-router-dom';
import {
  Card,
  Descriptions,
  Table,
  Button,
  DatePicker,
  Space,
  Typography,
  Tag,
  Modal,
  Spin,
  Empty,
  Divider,
} from 'antd';
import { PlusOutlined, EditOutlined, CloseCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { salaryService } from './salaryService.js';
import { StatutoryProfileCard } from './StatutoryProfileCard.jsx';
import { ScheduledEarningsPanel } from './ScheduledEarningsPanel.jsx';
import { SalaryVersionForm } from './SalaryVersionForm.jsx';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

export function SalaryTab({ employeeId: propEmployeeId, employee }) {
  const params = useParams();
  const employeeId = propEmployeeId || employee?.id || params?.id;

  const canRead = useCan('payroll.salary.read');
  const canManage = useCan('payroll.salary.manage');

  const [asOfDate, setAsOfDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [currentVersion, setCurrentVersion] = useState(null);
  const [versions, setVersions] = useState([]);
  const [loading, setLoading] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [formMode, setFormMode] = useState('create');
  const [selectedVersion, setSelectedVersion] = useState(null);

  const loadData = useCallback(async () => {
    if (!employeeId || !canRead) return;
    setLoading(true);
    try {
      const [versionsData, asOfData] = await Promise.allSettled([
        salaryService.versions(employeeId),
        salaryService.asOf(employeeId, asOfDate),
      ]);

      if (versionsData.status === 'fulfilled') {
        setVersions(versionsData.value || []);
      } else {
        setVersions([]);
      }

      if (asOfData.status === 'fulfilled') {
        setCurrentVersion(asOfData.value || null);
      } else {
        setCurrentVersion(null);
      }
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [employeeId, asOfDate, canRead]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  if (!canRead) {
    return <NotEntitled action="payroll.salary.read" />;
  }

  const handleCancelVersion = (record) => {
    Modal.confirm({
      title: 'Cancel Salary Version',
      content: `Are you sure you want to cancel the salary version effective from ${record.effectiveFrom}?`,
      okText: 'Cancel Version',
      okType: 'danger',
      onOk: async () => {
        try {
          await salaryService.cancel(employeeId, record.id);
          await successMsg('Version Cancelled', 'Salary version has been cancelled.');
          loadData();
        } catch (err) {
          errorMsg(err);
        }
      },
    });
  };

  const componentColumns = [
    { title: 'Code', dataIndex: 'componentCode', key: 'componentCode', width: 120 },
    { title: 'Name', dataIndex: 'componentName', key: 'componentName' },
    {
      title: 'Type',
      dataIndex: 'calculationType',
      key: 'calculationType',
      width: 120,
      render: (t) => <Tag>{t}</Tag>,
    },
    {
      title: 'Value / %',
      dataIndex: 'value',
      key: 'value',
      width: 120,
      render: (val, r) => (r.calculationType === 'PERCENTAGE' ? `${val}% of ${r.percentageOf || 'Basic'}` : val),
    },
    {
      title: 'Monthly Amount',
      dataIndex: 'monthlyAmount',
      key: 'monthlyAmount',
      width: 140,
      render: (amt) => (amt != null ? String(amt) : '-'),
    },
    {
      title: 'Annual Amount',
      dataIndex: 'annualAmount',
      key: 'annualAmount',
      width: 140,
      render: (amt) => (amt != null ? String(amt) : '-'),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 100,
      render: (enabled) => (
        <Tag color={enabled ? 'green' : 'default'}>{enabled ? 'Active' : 'Disabled'}</Tag>
      ),
    },
  ];

  const versionColumns = [
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
    },
    {
      title: 'Annual CTC',
      dataIndex: 'annualCtc',
      key: 'annualCtc',
      render: (val) => (val != null ? String(val) : '-'),
    },
    {
      title: 'Monthly CTC',
      dataIndex: 'monthlyCtc',
      key: 'monthlyCtc',
      render: (val) => (val != null ? String(val) : '-'),
    },
    {
      title: 'Change',
      dataIndex: 'changeInPercent',
      key: 'changeInPercent',
      render: (val) => (val != null ? `${val}%` : '-'),
    },
    {
      title: 'Notes',
      dataIndex: 'notes',
      key: 'notes',
      render: (notes) => notes || '-',
    },
    {
      title: 'Status',
      dataIndex: 'cancelled',
      key: 'cancelled',
      render: (cancelled) => (
        <Tag color={cancelled ? 'default' : 'success'}>
          {cancelled ? 'Cancelled' : 'Active'}
        </Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            disabled={record.cancelled || !canManage}
            onClick={() => {
              setSelectedVersion(record);
              setFormMode('edit');
              setFormOpen(true);
            }}
          >
            Edit
          </Button>
          <Button
            type="text"
            danger
            size="small"
            icon={<CloseCircleOutlined />}
            disabled={record.cancelled || !canManage}
            onClick={() => handleCancelVersion(record)}
          >
            Cancel
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Spin spinning={loading}>
        {/* Top Control Bar */}
        <Card style={{ marginBottom: 24 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 16,
            }}
          >
            <Space align="center">
              <Text strong>As of Date:</Text>
              <DatePicker
                value={dayjs(asOfDate)}
                format="YYYY-MM-DD"
                allowClear={false}
                onChange={(d) => {
                  if (d) setAsOfDate(d.format('YYYY-MM-DD'));
                }}
              />
            </Space>

            <Space>
              {!currentVersion && versions.length === 0 && canManage && (
                <Button
                  id="btn-create-salary-top"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setSelectedVersion(null);
                    setFormMode('create');
                    setFormOpen(true);
                  }}
                >
                  Create structure
                </Button>
              )}

              {(currentVersion || versions.length > 0) && canManage && (
                <Button
                  id="btn-revise-salary-structure"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setSelectedVersion(currentVersion || versions[0]);
                    setFormMode('revise');
                    setFormOpen(true);
                  }}
                >
                  Revise
                </Button>
              )}
            </Space>
          </div>
        </Card>

        {/* Current In-Force Version */}
        {currentVersion ? (
          <Card
            title={
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Title level={5} style={{ margin: 0 }}>
                  Active Structure (Effective {currentVersion.effectiveFrom})
                </Title>
                {currentVersion.changeInPercent != null && (
                  <Tag color="blue">Change: {String(currentVersion.changeInPercent)}%</Tag>
                )}
              </div>
            }
            style={{ marginBottom: 24 }}
          >
            <Descriptions bordered size="small" style={{ marginBottom: 24 }}>
              <Descriptions.Item label="Annual CTC">
                <Text strong>{String(currentVersion.annualCtc)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Monthly CTC">
                <Text strong>{String(currentVersion.monthlyCtc)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Effective From">
                {currentVersion.effectiveFrom}
              </Descriptions.Item>
              <Descriptions.Item label="Change (%)">
                {currentVersion.changeInPercent != null
                  ? `${currentVersion.changeInPercent}%`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Notes" span={2}>
                {currentVersion.notes || '-'}
              </Descriptions.Item>
            </Descriptions>

            {/* Earnings Table */}
            <Title level={5}>Earnings</Title>
            <Table
              dataSource={currentVersion.earnings || []}
              columns={componentColumns}
              rowKey={(r) => r.componentId || r.id}
              pagination={false}
              size="small"
              style={{ marginBottom: 24 }}
            />

            {/* Benefits Table */}
            {(currentVersion.benefits?.length > 0 || true) && (
              <>
                <Title level={5}>Benefits</Title>
                <Table
                  dataSource={currentVersion.benefits || []}
                  columns={componentColumns}
                  rowKey={(r) => r.componentId || r.id}
                  pagination={false}
                  size="small"
                  style={{ marginBottom: 24 }}
                />
              </>
            )}

            {/* Reimbursements Table */}
            {(currentVersion.reimbursements?.length > 0 || true) && (
              <>
                <Title level={5}>Reimbursements</Title>
                <Table
                  dataSource={currentVersion.reimbursements || []}
                  columns={componentColumns}
                  rowKey={(r) => r.componentId || r.id}
                  pagination={false}
                  size="small"
                  style={{ marginBottom: 24 }}
                />
              </>
            )}
          </Card>
        ) : (
          <Card style={{ marginBottom: 24, textAlign: 'center', padding: '32px 0' }}>
            <Empty
              description="No salary structure active as of this date"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            >
              {canManage && (
                <Button
                  id="btn-create-salary-structure"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => {
                    setSelectedVersion(null);
                    setFormMode('create');
                    setFormOpen(true);
                  }}
                >
                  Create structure
                </Button>
              )}
            </Empty>
          </Card>
        )}

        {/* Statutory Profile Section */}
        {employeeId && <StatutoryProfileCard employeeId={employeeId} />}

        {/* Scheduled earnings (W-73.6) */}
        {employeeId && <ScheduledEarningsPanel employeeId={employeeId} />}

        <Divider style={{ margin: '32px 0' }} />

        {/* All Versions History Table */}
        <Card
          title={<Title level={5} style={{ margin: 0 }}>Salary Versions History</Title>}
          style={{ marginBottom: 24 }}
        >
          <Table
            dataSource={versions}
            columns={versionColumns}
            rowKey="id"
            pagination={{ pageSize: 10 }}
            size="small"
            rowClassName={(record) => (record.cancelled ? 'cancelled-version-row' : '')}
            onRow={(record) =>
              record.cancelled
                ? {
                    style: {
                      textDecoration: 'line-through',
                      opacity: 0.65,
                    },
                  }
                : {}
            }
          />
        </Card>
      </Spin>

      {/* Salary Version Form Modal */}
      {formOpen && (
        <SalaryVersionForm
          open={formOpen}
          onClose={() => {
            setFormOpen(false);
            setSelectedVersion(null);
          }}
          onSuccess={() => {
            loadData();
          }}
          employeeId={employeeId}
          mode={formMode}
          initialValues={selectedVersion}
        />
      )}
    </div>
  );
}

SalaryTab.propTypes = {
  employeeId: PropTypes.string,
  employee: PropTypes.shape({
    id: PropTypes.string,
  }),
};
