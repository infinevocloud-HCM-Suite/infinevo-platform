import { useState, useEffect, useCallback } from 'react';
import { Table, Button, Card, Space, Typography, Tag, Switch, message } from 'antd';
import { FormOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { hiringService } from '../hiringService.js';
import { ScorecardModal } from './ScorecardModal.jsx';

const { Text } = Typography;

export function InterviewList() {
  const canInterview = useCan('hrms.hiring.interview');

  const [loading, setLoading] = useState(true);
  const [interviews, setInterviews] = useState([]);
  const [mineOnly, setMineOnly] = useState(false);
  const [scorecardModalOpen, setScorecardModalOpen] = useState(false);
  const [activeInterviewId, setActiveInterviewId] = useState(null);

  const fetchInterviews = useCallback(async () => {
    setLoading(true);
    try {
      const data = await hiringService.interviews.list(mineOnly);
      setInterviews(data);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load interviews');
    } finally {
      setLoading(false);
    }
  }, [mineOnly]);

  useEffect(() => {
    fetchInterviews();
  }, [fetchInterviews]);

  const columns = [
    {
      title: 'Candidate',
      key: 'candidate',
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Text strong>{row.candidateName || '—'}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{row.jobOpeningTitle || '—'}</Text>
        </Space>
      ),
    },
    {
      title: 'Round',
      dataIndex: 'roundName',
      key: 'roundName',
    },
    {
      title: 'Scheduled Time',
      key: 'scheduledAt',
      render: (_, row) => (
        <Space direction="vertical" size={2}>
          <Text>{new Date(row.scheduledAt).toLocaleString()}</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>{row.durationMinutes} mins</Text>
        </Space>
      ),
    },
    {
      title: 'Interviewer',
      dataIndex: 'interviewerName',
      key: 'interviewerName',
      render: (name) => name || 'Assigned Interviewer',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (st) => (
        <Tag color={st === 'COMPLETED' ? 'success' : st === 'SCHEDULED' ? 'processing' : 'default'}>
          {st}
        </Tag>
      ),
    },
    {
      title: 'Scorecard',
      key: 'scorecard',
      render: (_, row) => (
        row.feedbackCount > 0 ? (
          <Tag color="gold">★ {row.averageRating?.toFixed(1) || 'Rated'}</Tag>
        ) : (
          <Text type="secondary">Pending</Text>
        )
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) => (
        <Space size="small">
          {row.meetingLink && (
            <Button
              size="small"
              icon={<VideoCameraOutlined />}
              href={row.meetingLink}
              target="_blank"
            >
              Join
            </Button>
          )}
          {canInterview && row.status !== 'CANCELLED' && (
            <Button
              size="small"
              type="primary"
              icon={<FormOutlined />}
              onClick={() => {
                setActiveInterviewId(row.id);
                setScorecardModalOpen(true);
              }}
            >
              Scorecard
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <PageHeader
        title="Interviews & Scorecards"
        subtitle="Track candidate interview schedules and evaluation ratings"
      />

      <Card style={{ marginTop: 16 }}>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Text>Show my interviews only:</Text>
            <Switch checked={mineOnly} onChange={setMineOnly} />
          </Space>
          <Text type="secondary">{interviews.length} interview{interviews.length === 1 ? '' : 's'}</Text>
        </div>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={interviews}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <ScorecardModal
        open={scorecardModalOpen}
        interviewId={activeInterviewId}
        onClose={() => setScorecardModalOpen(false)}
        onSuccess={fetchInterviews}
      />
    </div>
  );
}

