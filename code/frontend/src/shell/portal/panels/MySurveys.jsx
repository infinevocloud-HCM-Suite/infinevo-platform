import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Tag, Button, Empty, Typography, message, theme } from 'antd';
import { FormOutlined, CheckCircleOutlined, LockOutlined } from '@ant-design/icons';
import { surveyService } from '../../../hrms/surveys/surveyService.js';
import { SurveyModal } from './SurveyModal.jsx';

const { Title, Text } = Typography;

export function MySurveys() {
  const { token } = theme.useToken();
  const [loading, setLoading] = useState(true);
  const [surveys, setSurveys] = useState([]);
  const [activeSurveyId, setActiveSurveyId] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);

  const fetchSurveys = useCallback(async () => {
    setLoading(true);
    try {
      const data = await surveyService.listActive();
      setSurveys(Array.isArray(data) ? data : []);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load employee surveys');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSurveys();
  }, [fetchSurveys]);

  const handleOpenSurvey = (id) => {
    setActiveSurveyId(id);
    setModalOpen(true);
  };

  const columns = [
    {
      title: 'Survey',
      key: 'title',
      render: (_, row) => (
        <div>
          <Text strong style={{ fontSize: token.fontSize }}>
            {row.title}
          </Text>
          {row.description && (
            <div style={{ marginTop: 2 }}>
              <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                {row.description}
              </Text>
            </div>
          )}
          <div style={{ marginTop: 4 }}>
            {row.isAnonymous && (
              <Tag icon={<LockOutlined />} color="purple">
                Anonymous
              </Tag>
            )}
            <Tag color="blue">{row.category}</Tag>
          </div>
        </div>
      ),
    },
    {
      title: 'Valid Through',
      dataIndex: 'endDate',
      key: 'endDate',
      render: (date) => (
        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
          {date}
        </Text>
      ),
    },
    {
      title: 'Questions',
      dataIndex: 'questionCount',
      key: 'questionCount',
      render: (count) => <Text>{count} questions</Text>,
    },
    {
      title: 'Status',
      key: 'status',
      render: (_, row) =>
        row.completed ? (
          <Tag color="success" icon={<CheckCircleOutlined />}>
            Completed
          </Tag>
        ) : (
          <Tag color="processing">Pending</Tag>
        ),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_, row) => (
        <Button
          type={row.completed ? 'default' : 'primary'}
          icon={<FormOutlined />}
          disabled={row.completed}
          onClick={() => handleOpenSurvey(row.id)}
        >
          {row.completed ? 'Submitted' : 'Take Survey'}
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: token.marginMD }}>
        <Title level={4} style={{ margin: 0 }}>
          Assigned Surveys & Feedback
        </Title>
        <Text type="secondary">
          Share your voice on active pulse checks, satisfaction surveys, and company feedback
        </Text>
      </div>

      <Card style={{ borderColor: token.colorBorderSecondary }}>
        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={surveys}
          pagination={false}
          locale={{ emptyText: <Empty description="No pending or active surveys assigned to you" /> }}
        />
      </Card>

      <SurveyModal
        open={modalOpen}
        surveyId={activeSurveyId}
        onClose={() => setModalOpen(false)}
        onSuccess={fetchSurveys}
      />
    </div>
  );
}

