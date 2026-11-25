import { useState, useEffect, useCallback } from 'react';
import { Table, Button, Card, Input, Space, Typography, Tag, message } from 'antd';
import { UserAddOutlined, SearchOutlined } from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { hiringService } from '../hiringService.js';
import { CandidateDrawer } from './CandidateDrawer.jsx';

const { Text } = Typography;

export function CandidateList() {
  const canManage = useCan('hrms.hiring.manage');

  const [loading, setLoading] = useState(true);
  const [candidates, setCandidates] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);

  const fetchCandidates = useCallback(async () => {
    setLoading(true);
    try {
      const data = await hiringService.candidates.list(searchQuery || undefined);
      setCandidates(data);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load candidates');
    } finally {
      setLoading(false);
    }
  }, [searchQuery]);

  useEffect(() => {
    fetchCandidates();
  }, [fetchCandidates]);

  const columns = [
    {
      title: 'Name',
      key: 'name',
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Text strong>{row.firstName} {row.lastName}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{row.email}</Text>
        </Space>
      ),
    },
    {
      title: 'Phone',
      dataIndex: 'phone',
      key: 'phone',
      render: (phone) => phone || '—',
    },
    {
      title: 'Current Role',
      key: 'role',
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Text>{row.currentTitle || '—'}</Text>
          {row.currentCompany && <Text type="secondary" style={{ fontSize: 12 }}>{row.currentCompany}</Text>}
        </Space>
      ),
    },
    {
      title: 'Experience',
      dataIndex: 'experienceYears',
      key: 'experienceYears',
      render: (exp) => (exp != null ? `${exp} yrs` : '—'),
    },
    {
      title: 'Notice Period',
      dataIndex: 'noticePeriodDays',
      key: 'noticePeriodDays',
      render: (days) => (days != null ? `${days} days` : '—'),
    },
    {
      title: 'Resume',
      dataIndex: 'resumeDocumentId',
      key: 'resume',
      render: (docId) => (
        docId ? <Tag color="blue">Available</Tag> : <Text type="secondary">None</Text>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Candidate Directory"
        subtitle="Manage talent pool and prospective applicant profiles"
        actions={
          canManage && (
            <Button
              type="primary"
              icon={<UserAddOutlined />}
              onClick={() => setDrawerOpen(true)}
            >
              Add Candidate
            </Button>
          )
        }
      />

      <Card style={{ marginTop: 16 }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Input.Search
            placeholder="Search candidate by name, email, company, or role"
            allowClear
            style={{ width: 360 }}
            prefix={<SearchOutlined />}
            onSearch={setSearchQuery}
          />
          <Text type="secondary">{candidates.length} candidate{candidates.length === 1 ? '' : 's'}</Text>
        </div>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={candidates}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <CandidateDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onSuccess={fetchCandidates}
      />
    </div>
  );
}

