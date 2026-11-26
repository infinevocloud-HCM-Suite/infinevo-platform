import { useState, useEffect, useCallback } from 'react';
import { Table, Tag, Button, Space, Card, Select, Typography, message, theme } from 'antd';
import {
  PlusOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  StopOutlined,
  LockOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { surveyService } from './surveyService.js';
import { SurveyBuilderDrawer } from './SurveyBuilderDrawer.jsx';

const { Text } = Typography;

export function SurveyList() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const canManage = useCan('hrms.survey.manage');

  const [loading, setLoading] = useState(true);
  const [surveys, setSurveys] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);

  const fetchSurveys = useCallback(async () => {
    setLoading(true);
    try {
      const data = await surveyService.list(statusFilter || undefined);
      setSurveys(Array.isArray(data) ? data : []);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load surveys');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    fetchSurveys();
  }, [fetchSurveys]);

  const handleStatusChange = async (id, newStatus) => {
    try {
      await surveyService.updateStatus(id, newStatus);
      message.success(`Survey status changed to ${newStatus}`);
      fetchSurveys();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to update survey status');
    }
  };

  const statusColors = {
    DRAFT: 'default',
    PUBLISHED: 'processing',
    CLOSED: 'magenta',
  };

  const categoryLabels = {
    PULSE_POLL: 'Pulse Poll',
    SATISFACTION: 'Job Satisfaction',
    ONBOARDING_FEEDBACK: 'Onboarding',
    GENERAL: 'General',
  };

  const columns = [
    {
      title: 'Survey Title',
      key: 'title',
      render: (_, row) => (
        <div>
          <Text strong style={{ fontSize: token.fontSize }}>
            {row.title}
          </Text>
          <div style={{ marginTop: 2 }}>
            <Tag color="blue">{categoryLabels[row.category] || row.category}</Tag>
            {row.isAnonymous && (
              <Tag icon={<LockOutlined />} color="purple">
                Anonymous
              </Tag>
            )}
          </div>
        </div>
      ),
    },
    {
      title: 'Audience',
      key: 'target',
      render: (_, row) => {
        if (row.targetType === 'ALL_EMPLOYEES') return <Text>All Employees</Text>;
        if (row.targetType === 'DEPARTMENT') return <Text>Dept: {row.targetName || 'Specific'}</Text>;
        if (row.targetType === 'WORK_LOCATION') return <Text>Loc: {row.targetName || 'Specific'}</Text>;
        return <Text>All</Text>;
      },
    },
    {
      title: 'Active Schedule',
      key: 'schedule',
      render: (_, row) => (
        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
          {row.startDate} ~ {row.endDate}
        </Text>
      ),
    },
    {
      title: 'Responses',
      dataIndex: 'totalResponses',
      key: 'totalResponses',
      render: (count) => <Text strong>{count || 0}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color={statusColors[status] || 'default'}>
          {status}
        </Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) => (
        <Space size="small">
          <Button
            size="small"
            icon={<BarChartOutlined />}
            onClick={() => navigate(`/hrms/surveys/${row.id}/results`)}
          >
            Analytics
          </Button>
          {canManage && row.status === 'DRAFT' && (
            <Button
              size="small"
              type="primary"
              icon={<CheckCircleOutlined />}
              onClick={() => handleStatusChange(row.id, 'PUBLISHED')}
            >
              Publish
            </Button>
          )}
          {canManage && row.status === 'PUBLISHED' && (
            <Button
              size="small"
              danger
              icon={<StopOutlined />}
              onClick={() => handleStatusChange(row.id, 'CLOSED')}
            >
              Close
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }}>
      <PageHeader
        title="Surveys & Polls"
        subtitle="Design employee feedback surveys, pulse checks, and view real-time participation analytics"
        actions={
          canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setDrawerOpen(true)}
            >
              Create Survey
            </Button>
          )
        }
      />

      <Card style={{ marginTop: token.marginMD, borderColor: token.colorBorderSecondary }}>
        <div style={{ marginBottom: token.marginMD, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Text type="secondary">Filter Status:</Text>
            <Select
              style={{ width: 160 }}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: '', label: 'All Surveys' },
                { value: 'DRAFT', label: 'Draft' },
                { value: 'PUBLISHED', label: 'Published' },
                { value: 'CLOSED', label: 'Closed' },
              ]}
            />
          </Space>
          <Text type="secondary">{surveys.length} survey{surveys.length === 1 ? '' : 's'}</Text>
        </div>

        <Table
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={surveys}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <SurveyBuilderDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onSuccess={fetchSurveys}
      />
    </div>
  );
}

