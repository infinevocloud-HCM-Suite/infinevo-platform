import { useEffect, useState, useCallback } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import {
  Table,
  Breadcrumb,
  Button,
  Modal,
  Select,
  DatePicker,
  Tag,
  Typography,
  Space,
  Card,
  theme,
} from 'antd';
import { UserOutlined, PlusOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { reportingLineService } from '../reportingLineService.js';
import { employeeService } from '../employeeService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

export function ReportingLineTab({ employeeId }) {
  const { token } = theme.useToken();
  const canManage = useCan('core.reporting_line.manage');

  const [loading, setLoading] = useState(false);
  const [lines, setLines] = useState([]);
  const [chain, setChain] = useState([]);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [managerOptions, setManagerOptions] = useState([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [selectedManagerId, setSelectedManagerId] = useState(null);
  const [selectedKind, setSelectedKind] = useState('PRIMARY');
  const [effectiveDate, setEffectiveDate] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const loadReportingData = useCallback(async () => {
    setLoading(true);
    try {
      const [linesData, chainData] = await Promise.all([
        reportingLineService.lines(employeeId),
        reportingLineService.managerChain(employeeId),
      ]);
      setLines(linesData || []);
      setChain(chainData || []);
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [employeeId]);

  useEffect(() => {
    loadReportingData();
  }, [loadReportingData]);

  const searchEmployees = async (query) => {
    setSearchLoading(true);
    try {
      const res = await employeeService.list({ q: query, size: 10 });
      const options = (res.content || []).map((e) => ({
        value: e.id,
        label: `${e.employeeNumber} - ${[e.firstName, e.lastName].filter(Boolean).join(' ')}`,
      }));
      setManagerOptions(options);
    } catch {
      setManagerOptions([]);
    } finally {
      setSearchLoading(false);
    }
  };

  const handleOpenModal = () => {
    setSelectedManagerId(null);
    setSelectedKind('PRIMARY');
    setEffectiveDate('');
    searchEmployees('');
    setModalOpen(true);
  };

  const handleSetManager = async () => {
    if (!selectedManagerId || !effectiveDate) {
      return;
    }
    setSubmitting(true);
    try {
      await reportingLineService.set(employeeId, {
        managerId: selectedManagerId,
        kind: selectedKind,
        effectiveFrom: effectiveDate,
      });
      await successMsg('Reporting Line Set', 'Primary reporting manager assigned successfully.');
      setModalOpen(false);
      loadReportingData();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: 'Manager',
      dataIndex: 'managerName',
      key: 'managerName',
      render: (name) => (
        <Space size="small">
          <UserOutlined style={{ color: token.colorPrimary }} />
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      render: (kind) => <Tag color="blue">{kind}</Tag>,
    },
    {
      title: 'Effective From',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      render: (d) => d || '—',
    },
    {
      title: 'Effective To',
      dataIndex: 'effectiveTo',
      key: 'effectiveTo',
      render: (d) => d || 'Ongoing',
    },
  ];

  return (
    <Card variant="borderless" style={{ padding: 0 }}>
      {/* Manager chain */}
      <div style={{ marginBottom: token.marginLG }}>
        <Title level={5} style={{ marginBottom: token.marginSM }}>Manager Chain</Title>
        {chain.length > 0 ? (
          <Breadcrumb
            id="breadcrumb-manager-chain"
            items={chain.map((c) => ({
              title: c.managerName,
            }))}
          />
        ) : (
          <Text type="secondary">No reporting chain recorded.</Text>
        )}
      </div>

      {/* Reporting lines table */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: token.marginSM }}>
        <Title level={5} style={{ margin: 0 }}>Reporting Lines</Title>
        {canManage && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleOpenModal}
            id="btn-set-primary-manager"
          >
            Set Primary Manager
          </Button>
        )}
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={lines}
        loading={loading}
        pagination={false}
      />

      <Modal
        title="Set Reporting Manager"
        open={modalOpen}
        onOk={handleSetManager}
        onCancel={() => setModalOpen(false)}
        confirmLoading={submitting}
        okButtonProps={{ disabled: !selectedManagerId || !effectiveDate, id: 'btn-confirm-set-manager' }}
        destroyOnHidden={true}
      >
        <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
          <div>
            <label htmlFor="select-manager" style={{ display: 'block', marginBottom: 4 }}>
              <Text strong>Select Manager *</Text>
            </label>
            <Select
              id="select-manager"
              style={{ width: '100%' }}
              showSearch
              filterOption={false}
              placeholder="Search employee by name or ID"
              loading={searchLoading}
              onSearch={searchEmployees}
              value={selectedManagerId}
              onChange={(val) => setSelectedManagerId(val)}
              options={managerOptions}
            />
          </div>

          <div>
            <label htmlFor="select-kind" style={{ display: 'block', marginBottom: 4 }}>
              <Text strong>Reporting Kind</Text>
            </label>
            <Select
              id="select-kind"
              style={{ width: '100%' }}
              value={selectedKind}
              onChange={(val) => setSelectedKind(val)}
              options={[
                { value: 'PRIMARY', label: 'Primary' },
                { value: 'INDIRECT', label: 'Indirect' },
                { value: 'APPROVER_L1', label: 'Approver Level 1' },
                { value: 'APPROVER_L2', label: 'Approver Level 2' },
                { value: 'APPROVER_L3', label: 'Approver Level 3' },
              ]}
            />
          </div>

          <div>
            <label htmlFor="picker-effectiveDate" style={{ display: 'block', marginBottom: 4 }}>
              <Text strong>Effective Date *</Text>
            </label>
            <DatePicker
              id="picker-effectiveDate"
              style={{ width: '100%' }}
              value={effectiveDate ? dayjs(effectiveDate) : null}
              onChange={(_, dateStr) => setEffectiveDate(dateStr)}
            />
          </div>
        </Space>
      </Modal>
    </Card>
  );
}

ReportingLineTab.propTypes = {
  employeeId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
};
