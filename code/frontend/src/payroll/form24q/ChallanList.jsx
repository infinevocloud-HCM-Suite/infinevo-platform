import PropTypes from 'prop-types';
import { Table, Tag, Typography, Empty, Button, theme } from 'antd';
import { PlusOutlined } from '@ant-design/icons';

const { Text } = Typography;

export function ChallanList({ challans = [], loading = false, onRecordClick }) {
  const { token } = theme.useToken();
  const formatInr = (amount) =>
    amount != null
      ? `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
      : '₹0.00';

  const columns = [
    {
      title: 'BSR Code',
      dataIndex: 'bsrCode',
      key: 'bsrCode',
      render: (code) => <Text strong>{code}</Text>,
    },
    {
      title: 'Challan No',
      dataIndex: 'challanSerialNo',
      key: 'challanSerialNo',
    },
    {
      title: 'Tender Date',
      dataIndex: 'tenderDate',
      key: 'tenderDate',
    },
    {
      title: 'Head',
      dataIndex: 'minorHead',
      key: 'minorHead',
      render: (head) => (
        <Tag color={head === '200' ? 'blue' : 'purple'}>
          {head === '200' ? '200 (Taxpayer)' : '400 (Assessment)'}
        </Tag>
      ),
    },
    {
      title: 'Tax',
      dataIndex: 'taxAmount',
      key: 'taxAmount',
      align: 'right',
      render: formatInr,
    },
    {
      title: 'Cess',
      dataIndex: 'cessAmount',
      key: 'cessAmount',
      align: 'right',
      render: formatInr,
    },
    {
      title: 'Total Deposited',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      align: 'right',
      render: (val) => <Text strong style={{ color: token.colorPrimary }}>{formatInr(val)}</Text>,
    },
    {
      title: 'Allocated',
      dataIndex: 'allocatedAmount',
      key: 'allocatedAmount',
      align: 'right',
      render: formatInr,
    },
    {
      title: 'Allocation Status',
      key: 'allocationStatus',
      render: (_, record) => {
        const status = record.allocationStatus;
        if (status === 'FULLY_ALLOCATED') {
          return <Tag color="success">Fully Allocated</Tag>;
        }
        if (status === 'PARTIALLY_ALLOCATED') {
          return (
            <Tag color="warning">
              Unallocated {formatInr(record.unallocatedAmount)}
            </Tag>
          );
        }
        if (status === 'OVERALLOCATED') {
          return <Tag color="error">Overallocated</Tag>;
        }
        return <Tag color="default">Unallocated</Tag>;
      },
    },
    {
      title: 'Filing',
      dataIndex: 'isFiled',
      key: 'isFiled',
      render: (filed) => (
        <Tag color={filed ? 'cyan' : 'default'}>{filed ? 'Filed' : 'Pending'}</Tag>
      ),
    },
  ];

  return (
    <div>
      <Table
        dataSource={challans}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 5 }}
        locale={{
          emptyText: (
            <Empty
              description="No TDS challans recorded for this quarter"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            >
              {onRecordClick && (
                <Button type="primary" icon={<PlusOutlined />} onClick={onRecordClick}>
                  Record Challan
                </Button>
              )}
            </Empty>
          ),
        }}
      />
    </div>
  );
}

ChallanList.propTypes = {
  challans: PropTypes.arrayOf(PropTypes.object),
  loading: PropTypes.bool,
  onRecordClick: PropTypes.func,
};
