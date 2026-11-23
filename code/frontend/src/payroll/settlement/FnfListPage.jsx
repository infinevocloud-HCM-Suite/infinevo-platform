import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Tag, Button, Space, Input, Select, Typography, message, theme } from 'antd';
import { PlusOutlined, EyeOutlined, SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { fnfService } from './fnfService.js';
import { InitiateFnfModal } from './InitiateFnfModal.jsx';

const { Title } = Typography;

export function FnfListPage() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const [settlements, setSettlements] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [modalOpen, setModalOpen] = useState(false);

  const fetchSettlements = useCallback(async () => {
    try {
      setLoading(true);
      const data = await fnfService.listSettlements();
      setSettlements(Array.isArray(data) ? data : []);
    } catch (err) {
      message.error('Failed to load F&F settlements');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSettlements();
  }, [fetchSettlements]);

  const filteredData = settlements.filter((item) => {
    const matchesSearch =
      !searchText ||
      (item.employeeId && item.employeeId.toLowerCase().includes(searchText.toLowerCase())) ||
      (item.exitReason && item.exitReason.toLowerCase().includes(searchText.toLowerCase()));
    const matchesStatus = statusFilter === 'ALL' || item.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  const getStatusTag = (status) => {
    switch (status) {
      case 'PAID':
        return <Tag color="green">PAID</Tag>;
      case 'APPROVED':
        return <Tag color="cyan">APPROVED</Tag>;
      case 'UNDER_REVIEW':
        return <Tag color="orange">UNDER REVIEW</Tag>;
      case 'CANCELLED':
        return <Tag color="red">CANCELLED</Tag>;
      case 'DRAFT':
      default:
        return <Tag color="blue">DRAFT</Tag>;
    }
  };

  const columns = [
    {
      title: 'Employee ID',
      dataIndex: 'employeeId',
      key: 'employeeId',
      render: (id) => <span style={{ fontFamily: 'monospace' }}>{id}</span>,
    },
    {
      title: 'Exit Date',
      dataIndex: 'exitDate',
      key: 'exitDate',
      sorter: (a, b) => new Date(a.exitDate) - new Date(b.exitDate),
    },
    {
      title: 'Exit Reason',
      dataIndex: 'exitReason',
      key: 'exitReason',
    },
    {
      title: 'Gross Earnings',
      dataIndex: 'grossEarnings',
      key: 'grossEarnings',
      align: 'right',
      render: (val) => `₹${Number(val || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`,
    },
    {
      title: 'Gross Deductions',
      dataIndex: 'grossDeductions',
      key: 'grossDeductions',
      align: 'right',
      render: (val) => `₹${Number(val || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`,
    },
    {
      title: 'Net Payable',
      dataIndex: 'netPayable',
      key: 'netPayable',
      align: 'right',
      render: (val) => (
        <strong style={{ color: Number(val) >= 0 ? token.colorSuccess : token.colorError }}>
          ₹{Number(val || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
        </strong>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => getStatusTag(status),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() => navigate(`/payroll/settlements/${record.id}`)}
        >
          View Statement
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <Title level={3} style={{ margin: 0 }}>
            Full & Final (F&F) Exit Settlements
          </Title>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchSettlements}>
              Refresh
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setModalOpen(true)}
            >
              Initiate Settlement
            </Button>
          </Space>
        </div>

        <div style={{ display: 'flex', gap: 16, marginBottom: 16 }}>
          <Input
            placeholder="Search by Employee ID..."
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 300 }}
            allowClear
          />
          <Select
            value={statusFilter}
            onChange={(val) => setStatusFilter(val)}
            style={{ width: 160 }}
            options={[
              { label: 'All Statuses', value: 'ALL' },
              { label: 'Draft', value: 'DRAFT' },
              { label: 'Under Review', value: 'UNDER_REVIEW' },
              { label: 'Approved', value: 'APPROVED' },
              { label: 'Paid', value: 'PAID' },
              { label: 'Cancelled', value: 'CANCELLED' },
            ]}
          />
        </div>

        <Table
          columns={columns}
          dataSource={filteredData}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: true }}
        />
      </Card>

      <InitiateFnfModal
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onSuccess={(created) => {
          setModalOpen(false);
          fetchSettlements();
          if (created?.id) {
            navigate(`/payroll/settlements/${created.id}`);
          }
        }}
      />
    </div>
  );
}
