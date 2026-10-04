import PropTypes from 'prop-types';
import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import { useFormik } from 'formik';
import * as Yup from 'yup';
import dayjs from 'dayjs';
import { COLOR, LABEL, PRIORITIES, TASK_STATUSES, errorMessage, projectService } from './projectService.js';

const taskSchema = Yup.object({
  title: Yup.string().trim().required('Title is required'),
  priority: Yup.string().required('Priority is required'),
});

/** The project's team as assignee options — a task can go only to someone on the team (W-48.1 §5). */
export function teamOptions(team) {
  return (team || []).map((m) => ({
    value: m.employee_id,
    label: m.name || m.employee_id,
  }));
}

function TaskForm({ initial, team, onSubmit, onCancel, submitting }) {
  const f = useFormik({
    initialValues: {
      title: initial?.title ?? '',
      description: initial?.description ?? '',
      assignee_employee_id: initial?.assignee_employee_id ?? null,
      due_date: initial?.due_date ?? null,
      priority: initial?.priority ?? 'MEDIUM',
      status: initial?.status ?? 'TODO',
      estimated_hours: initial?.estimated_hours ?? null,
    },
    validationSchema: taskSchema,
    onSubmit: (v) =>
      onSubmit({
        ...v,
        title: v.title.trim(),
        description: v.description || null,
      }),
  });
  const err = (k) => (f.touched[k] || f.submitCount > 0) && f.errors[k];
  return (
    <Form layout="vertical" onFinish={f.handleSubmit}>
      <Form.Item label="Title" required validateStatus={err('title') ? 'error' : ''} help={err('title')}>
        <Input aria-label="Title" name="title" value={f.values.title} onChange={f.handleChange} />
      </Form.Item>
      <Form.Item label="Description">
        <Input.TextArea name="description" value={f.values.description} onChange={f.handleChange} rows={3} />
      </Form.Item>
      <Form.Item label="Assignee">
        <Select
          aria-label="Assignee"
          allowClear
          value={f.values.assignee_employee_id ?? undefined}
          onChange={(v) => f.setFieldValue('assignee_employee_id', v ?? null)}
          options={teamOptions(team)}
        />
      </Form.Item>
      <Form.Item label="Due date">
        <DatePicker
          value={f.values.due_date ? dayjs(f.values.due_date) : null}
          onChange={(d) => f.setFieldValue('due_date', d ? d.format('YYYY-MM-DD') : null)}
          style={{ width: '100%' }}
        />
      </Form.Item>
      <Form.Item label="Priority" required>
        <Select
          aria-label="Priority"
          value={f.values.priority}
          onChange={(v) => f.setFieldValue('priority', v)}
          options={PRIORITIES.map((p) => ({ value: p, label: LABEL[p] }))}
        />
      </Form.Item>
      <Form.Item label="Status">
        <Select
          value={f.values.status}
          onChange={(v) => f.setFieldValue('status', v)}
          options={TASK_STATUSES.map((s) => ({ value: s, label: LABEL[s] }))}
        />
      </Form.Item>
      <Form.Item label="Estimate (hours)">
        <InputNumber
          min={0}
          precision={0}
          value={f.values.estimated_hours}
          onChange={(v) => f.setFieldValue('estimated_hours', v ?? null)}
          style={{ width: '100%' }}
        />
      </Form.Item>
      <Space>
        <Button type="primary" htmlType="submit" loading={submitting}>
          Save
        </Button>
        <Button onClick={onCancel}>Cancel</Button>
      </Space>
    </Form>
  );
}

/** Project tasks (W-48.1 §5): table with a status filter; create and edit in a Drawer when `canManage`. */
export function TasksTab({ projectId, team, canManage }) {
  const [status, setStatus] = useState(undefined);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null); // null closed, {} new, task to edit
  const [saving, setSaving] = useState(false);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let live = true;
    setLoading(true);
    projectService
      .tasks(projectId, status)
      .then((d) => live && setRows(d || []))
      .catch((e) => live && setError(errorMessage(e, 'Could not load tasks')))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, [projectId, status, reload]);

  const save = async (body) => {
    setSaving(true);
    setError(null);
    try {
      if (editing?.id) await projectService.updateTask(editing.id, body);
      else await projectService.createTask(projectId, body);
      setEditing(null);
      setReload((n) => n + 1);
    } catch (e) {
      setError(errorMessage(e, 'Could not save the task'));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id) => {
    setError(null);
    try {
      await projectService.removeTask(id);
      setReload((n) => n + 1);
    } catch (e) {
      setError(errorMessage(e, 'Could not delete the task'));
    }
  };

  const columns = [
    { title: 'Title', dataIndex: 'title' },
    { title: 'Assignee', dataIndex: 'assignee_name', render: (v) => v || '-' },
    { title: 'Due', dataIndex: 'due_date', render: (v) => v || '-' },
    { title: 'Priority', dataIndex: 'priority', render: (v) => LABEL[v] || v },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (v) => <Tag color={COLOR[v]}>{LABEL[v] || v}</Tag>,
    },
    {
      title: 'Estimate',
      dataIndex: 'estimated_hours',
      render: (v) => (v == null ? '-' : `${v} h`),
    },
  ];
  if (canManage) {
    columns.push({
      title: '',
      key: 'actions',
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => setEditing(r)}>
            Edit
          </Button>
          <Popconfirm title="Delete this task?" onConfirm={() => remove(r.id)}>
            <Button size="small" danger>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    });
  }

  return (
    <>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select
          aria-label="Task status"
          placeholder="Status"
          allowClear
          value={status}
          onChange={setStatus}
          style={{ width: 160 }}
          options={TASK_STATUSES.map((s) => ({ value: s, label: LABEL[s] }))}
        />
        {canManage && (
          <Button type="primary" onClick={() => setEditing({})}>
            New task
          </Button>
        )}
      </Space>
      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20 }}
      />
      <Drawer
        title={editing?.id ? 'Edit task' : 'New task'}
        open={editing !== null}
        onClose={() => setEditing(null)}
        width={480}
        destroyOnClose
      >
        {editing !== null && (
          <TaskForm
            initial={editing}
            team={team}
            onSubmit={save}
            onCancel={() => setEditing(null)}
            submitting={saving}
          />
        )}
      </Drawer>
    </>
  );
}

TaskForm.propTypes = {
  initial: PropTypes.shape({
    title: PropTypes.string,
    description: PropTypes.string,
    assignee_employee_id: PropTypes.string,
    due_date: PropTypes.string,
    priority: PropTypes.string,
    status: PropTypes.string,
    estimated_hours: PropTypes.number,
  }),
  team: PropTypes.arrayOf(PropTypes.shape({ employee_id: PropTypes.string, name: PropTypes.string })),
  onSubmit: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
  submitting: PropTypes.bool,
};

TasksTab.propTypes = {
  projectId: PropTypes.string.isRequired,
  team: PropTypes.arrayOf(PropTypes.shape({ employee_id: PropTypes.string, name: PropTypes.string })),
  canManage: PropTypes.bool,
};
