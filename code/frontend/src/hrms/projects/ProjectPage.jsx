import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Popconfirm,
  Progress,
  Select,
  Slider,
  Space,
  Spin,
  Tabs,
  Tag,
} from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { useCan } from '@shell/screens';
import { ProjectForm } from './ProjectForm.jsx';
import { TeamTab } from './TeamTab.jsx';
import { TasksTab } from './TasksTab.jsx';
import { COLOR, LABEL, PROJECT_STATUSES, errorMessage, projectService } from './projectService.js';

/** One project (W-48.1 §5): header with status, progress and delete; Details, Team and Tasks tabs. */
export function ProjectPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const canManage = useCan('hrms.project.manage');

  const [project, setProject] = useState(null);
  const [assignments, setAssignments] = useState([]);
  const [error, setError] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [progress, setProgress] = useState(0);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const [p, a] = await Promise.all([projectService.get(id), projectService.assignments(id)]);
      setProject(p);
      setProgress(p?.progress ?? 0);
      setAssignments(a || []);
    } catch (e) {
      setLoadError(errorMessage(e, 'Could not load the project'));
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (fn, fallback) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(errorMessage(e, fallback));
    }
  };

  const changeStatus = (s) =>
    act(async () => setProject(await projectService.setStatus(id, s)), 'Could not change the status');
  const saveProgress = (v) =>
    act(async () => setProject(await projectService.setProgress(id, v)), 'Could not save progress');
  const remove = () =>
    act(async () => {
      await projectService.remove(id);
      navigate('/hrms/projects');
    }, 'Could not delete the project');
  const save = async (body) => {
    setSaving(true);
    await act(async () => {
      setProject(await projectService.update(id, body));
      setEditing(false);
    }, 'Could not save the project');
    setSaving(false);
  };

  if (loadError) return <Alert type="error" message={loadError} />;
  if (!project) return <Spin />;

  const team =
    project.team ||
    assignments.map((a) => ({
      employee_id: a.employee_id,
      name: a.employee_name,
    }));

  const details = (
    <Descriptions column={1} bordered size="small">
      <Descriptions.Item label="Manager">{project.manager_name || '-'}</Descriptions.Item>
      <Descriptions.Item label="Category">{project.category || '-'}</Descriptions.Item>
      <Descriptions.Item label="Priority">{LABEL[project.priority] || project.priority}</Descriptions.Item>
      <Descriptions.Item label="Start date">{project.start_date || '-'}</Descriptions.Item>
      <Descriptions.Item label="End date">{project.end_date || '-'}</Descriptions.Item>
      <Descriptions.Item label="Budget">{project.budget ?? '-'}</Descriptions.Item>
      <Descriptions.Item label="Description">{project.description || '-'}</Descriptions.Item>
    </Descriptions>
  );

  return (
    <Card
      title={
        <Space>
          {project.name}
          <Tag color={COLOR[project.status]}>{LABEL[project.status] || project.status}</Tag>
        </Space>
      }
      extra={
        canManage && (
          <Space>
            <Select
              aria-label="Project status"
              value={project.status}
              onChange={changeStatus}
              style={{ width: 140 }}
              options={PROJECT_STATUSES.map((s) => ({
                value: s,
                label: LABEL[s],
              }))}
            />
            <Button onClick={() => setEditing(true)}>Edit</Button>
            <Popconfirm title="Delete this project?" onConfirm={remove} okText="Delete">
              <Button danger>Delete</Button>
            </Popconfirm>
          </Space>
        )
      }
    >
      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
      <div style={{ maxWidth: 400, marginBottom: 16 }}>
        {canManage ? (
          <Slider
            aria-label="Progress"
            min={0}
            max={100}
            value={progress}
            onChange={setProgress}
            onChangeComplete={saveProgress}
          />
        ) : (
          <Progress percent={project.progress ?? 0} />
        )}
      </div>
      <Tabs
        items={[
          { key: 'details', label: 'Details', children: details },
          {
            key: 'team',
            label: 'Team',
            children: (
              <TeamTab projectId={id} assignments={assignments} canManage={canManage} onChange={load} />
            ),
          },
          {
            key: 'tasks',
            label: 'Tasks',
            children: <TasksTab projectId={id} team={team} canManage={canManage} />,
          },
        ]}
      />
      {canManage && (
        <Drawer
          title="Edit project"
          open={editing}
          onClose={() => setEditing(false)}
          width={480}
          destroyOnClose
        >
          <ProjectForm
            initial={project}
            onSubmit={save}
            onCancel={() => setEditing(false)}
            submitting={saving}
          />
        </Drawer>
      )}
    </Card>
  );
}
