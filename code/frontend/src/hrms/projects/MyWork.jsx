import { useEffect, useState } from 'react';
import { Alert, Card, Col, Empty, Progress, Row, Select, Table, Tag, Typography, theme } from 'antd';
import dayjs from 'dayjs';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { COLOR, LABEL, TASK_STATUSES, errorMessage, projectService } from './projectService.js';
import { EMPTY, formatDate, humanize } from '@shared/ui/format.js';

/** A task is overdue when its due date has passed and it is not completed. */
export function isOverdue(task, today = dayjs().format('YYYY-MM-DD')) {
  return Boolean(task.due_date) && task.status !== 'COMPLETED' && task.due_date < today;
}

/** The employee's own projects and tasks (W-48.1 §5); status changes go to `PUT /tasks/{id}/status`. */
export function MyWork() {
  const { token } = theme.useToken();
  const [projects, setProjects] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let live = true;
    Promise.all([projectService.mine(), projectService.myTasks()])
      .then(([p, t]) => {
        if (!live) return;
        setProjects(p || []);
        setTasks(t || []);
      })
      .catch((e) => live && setError(errorMessage(e, 'Could not load your work')))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, []);

  const changeStatus = async (task, status) => {
    setError(null);
    try {
      const updated = await projectService.setTaskStatus(task.id, status);
      setTasks((list) => list.map((t) => (t.id === task.id ? { ...t, ...(updated || { status }) } : t)));
    } catch (e) {
      setError(errorMessage(e, 'Could not change the status'));
    }
  };

  const columns = [
    { title: 'Title', dataIndex: 'title', render: (v) => <Typography.Text strong>{v}</Typography.Text> },
    { title: 'Project', dataIndex: 'project_name', render: (v) => v || EMPTY },
    {
      title: 'Due',
      dataIndex: 'due_date',
      render: (v, r) =>
        isOverdue(r) ? (
          <Typography.Text type="danger" data-testid={`overdue-${r.id}`}>
            {formatDate(v)} (overdue)
          </Typography.Text>
        ) : (
          formatDate(v)
        ),
    },
    { title: 'Priority', dataIndex: 'priority', render: (v) => LABEL[v] || humanize(v) },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (v, r) => (
        <Select
          aria-label={`Status of ${r.title}`}
          value={v}
          onChange={(s) => changeStatus(r, s)}
          style={{ width: 140 }}
          options={TASK_STATUSES.map((s) => ({ value: s, label: LABEL[s] }))}
        />
      ),
    },
  ];

  return (
    <>
      <PageHeader title="My work" subtitle="My assigned projects and tasks" />
      {error && <Alert type="error" message={error} style={{ marginBottom: token.margin }} />}
      <Card variant="borderless" title="My projects" loading={loading} style={{ marginBottom: token.margin }}>
        {projects.length === 0 ? (
          <Empty description="No projects" />
        ) : (
          <Row gutter={[16, 16]}>
            {projects.map((p) => (
              <Col key={p.id} xs={24} sm={12} lg={8}>
                <Card
                  variant="borderless"
                  size="small"
                  title={p.name}
                  extra={<Tag color={COLOR[p.status]}>{LABEL[p.status]}</Tag>}
                >
                  <Progress percent={p.progress ?? 0} size="small" />
                  <div>End date: {formatDate(p.end_date)}</div>
                </Card>
              </Col>
            ))}
          </Row>
        )}
      </Card>
      <Card variant="borderless" title="My tasks" loading={loading}>
        {TASK_STATUSES.map((s) => {
          const group = tasks.filter((t) => t.status === s);
          if (group.length === 0) return null;
          return (
            <div key={s} style={{ marginBottom: token.margin }}>
              <Typography.Title level={5}>
                {LABEL[s]} ({group.length})
              </Typography.Title>
              <Table
                rowKey="id"
                size="small"
                columns={columns}
                dataSource={group}
                pagination={false}
                rowClassName={(r) => (isOverdue(r) ? 'task-overdue' : '')}
              />
            </div>
          );
        })}
        {!loading && tasks.length === 0 && <Empty description="No tasks" />}
      </Card>
    </>
  );
}
