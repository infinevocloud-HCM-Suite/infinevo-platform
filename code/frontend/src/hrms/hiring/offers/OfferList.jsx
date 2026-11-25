import { useState, useEffect, useCallback } from 'react';
import { Table, Button, Card, Space, Typography, Tag, Select, message } from 'antd';
import { SendOutlined, CheckOutlined, CloseOutlined, UserAddOutlined } from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { hiringService } from '../hiringService.js';
import { ConvertEmployeeModal } from './ConvertEmployeeModal.jsx';

const { Text } = Typography;

export function OfferList() {
  const canManage = useCan('hrms.hiring.manage');

  const [loading, setLoading] = useState(true);
  const [offers, setOffers] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [convertModalOpen, setConvertModalOpen] = useState(false);
  const [activeOffer, setActiveOffer] = useState(null);

  const fetchOffers = useCallback(async () => {
    setLoading(true);
    try {
      const data = await hiringService.offers.list(statusFilter || undefined);
      setOffers(data);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load offers');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchOffers();
  }, [fetchOffers]);

  const handleSend = async (id) => {
    try {
      await hiringService.offers.send(id);
      message.success('Offer sent to candidate');
      fetchOffers();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Send failed');
    }
  };

  const handleAccept = async (id) => {
    try {
      await hiringService.offers.accept(id);
      message.success('Offer marked as accepted');
      fetchOffers();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Acceptance update failed');
    }
  };

  const handleDecline = async (id) => {
    try {
      await hiringService.offers.decline(id, 'Declined by candidate');
      message.success('Offer marked as declined');
      fetchOffers();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Decline update failed');
    }
  };

  const statusColors = {
    DRAFT: 'default',
    SENT: 'processing',
    ACCEPTED: 'success',
    DECLINED: 'error',
    EXPIRED: 'warning',
  };

  const columns = [
    {
      title: 'Candidate',
      key: 'candidate',
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Text strong>{row.candidateName || '—'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{row.candidateEmail || '—'}</Text>
        </Space>
      ),
    },
    {
      title: 'Job Opening',
      dataIndex: 'jobOpeningTitle',
      key: 'jobOpeningTitle',
      render: (title) => title || '—',
    },
    {
      title: 'Offered CTC',
      dataIndex: 'offeredCtc',
      key: 'offeredCtc',
      render: (ctc) => (ctc != null ? `₹${Number(ctc).toLocaleString()}` : '—'),
    },
    {
      title: 'Join Date',
      dataIndex: 'targetJoinDate',
      key: 'targetJoinDate',
    },
    {
      title: 'Valid Until',
      dataIndex: 'offerValidUntil',
      key: 'offerValidUntil',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (st, row) => (
        <Space size={4}>
          <Tag color={statusColors[st] || 'default'}>{st}</Tag>
          {row.convertedEmployeeId && <Tag color="green">Converted</Tag>}
        </Space>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) => (
        <Space size="small">
          {canManage && row.status === 'DRAFT' && (
            <Button
              size="small"
              type="primary"
              icon={<SendOutlined />}
              onClick={() => handleSend(row.id)}
            >
              Send
            </Button>
          )}
          {canManage && row.status === 'SENT' && (
            <>
              <Button
                size="small"
                type="primary"
                icon={<CheckOutlined />}
                onClick={() => handleAccept(row.id)}
              >
                Accept
              </Button>
              <Button
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => handleDecline(row.id)}
              >
                Decline
              </Button>
            </>
          )}
          {canManage && row.status === 'ACCEPTED' && !row.convertedEmployeeId && (
            <Button
              size="small"
              type="primary"
              icon={<UserAddOutlined />}
              onClick={() => {
                setActiveOffer(row);
                setConvertModalOpen(true);
              }}
            >
              Convert to Employee
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Job Offers"
        subtitle="Manage candidate job offers and one-click employee onboarding"
      />

      <Card style={{ marginTop: 16 }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Text type="secondary">Status:</Text>
            <Select
              style={{ width: 160 }}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: '', label: 'All Offers' },
                { value: 'DRAFT', label: 'Draft' },
                { value: 'SENT', label: 'Sent' },
                { value: 'ACCEPTED', label: 'Accepted' },
                { value: 'DECLINED', label: 'Declined' },
                { value: 'EXPIRED', label: 'Expired' },
              ]}
            />
          </Space>
          <Text type="secondary">{offers.length} offer{offers.length === 1 ? '' : 's'}</Text>
        </div>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={offers}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <ConvertEmployeeModal
        open={convertModalOpen}
        offer={activeOffer}
        onClose={() => setConvertModalOpen(false)}
        onSuccess={fetchOffers}
      />
    </div>
  );
}

