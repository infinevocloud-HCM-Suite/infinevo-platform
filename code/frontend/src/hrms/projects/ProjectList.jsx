import { useEffect, useState } from 'react';
import { Alert, Button, Card, Drawer, Input, Progress, Select, Space, Table, Tag } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useCan } from '@shell/screens';
import { ProjectForm } from './ProjectForm.jsx';
import { COLOR, LABEL, PROJECT_STATUSES, errorMessage, projectService } from './projectService.js';

/**
 * Project list (W-48.1 §5). A caller without `hrms.project.read` (a manager) sees only the projects they manage,
 * so the list asks with `managed=true`; "New project" needs `hrms.project.manage`.
 */
export function ProjectList() {
  const canReadAll = useCan('hrms.project.read');
  const canManage = useCan('hrms.project.manage');
  const navigate = useNavigate();

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState(undefined);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [creating, setCreating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let live = true;
    setLoading(true);
    setError(null);
    projectService
      .list({
        status,
        search: search.trim() || undefined,
        managed: canReadAll ? undefined : true,
      })
      .then((data) => live && setRows(data || []))
      .catch((e) => live && setError(errorMessage(e, 'Could not load projects')))
      .finally(() => live && setLoading(false));
    return () => {
      live = false;
    };
  }, [status, search, canReadAll, reload]);

  const create = async (body) => {
    setSaving(true);
    setSaveError(null);
    try {
      const p = await projectService.create(body);
      setCreating(false);
      if (p?.id) navigate(`/hrms/projects/${p.id}`);
      else setReload((n) => n + 1);
    } catch (e) {
      setSaveError(errorMessage(e, 'Could not create the project'));
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      render: (v, r) => <Link to={`/hrms/projects/${r.id}`}>{v}</Link>,
    },
    { title: 'Manager', dataIndex: 'manager_name', render: (v) => v || '-' },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (v) => <Tag color={COLOR[v]}>{LABEL[v] || v}</Tag>,
    },
    {
      title: 'Progress',
      dataIndex: 'progress',
      render: (v) => <Progress percent={v ?? 0} size="small" />,
    },
    { title: 'End date', dataIndex: 'end_date', render: (v) => v || '-' },
    {
      title: 'Team',
      key: 'team',
      render: (_, r) => (r.team || r.team_member_ids || []).length,
    },
  ];

  return (
    <Card
      title="Projects"
      extra={
        canManage && (
          <Button type="primary" onClick={() => setCreating(true)}>
            New project
          </Button>
        )
      }
    >
      <Space style={{ marginBottom: 16 }} wrap>
        <Input.Search
          aria-label="Search"
          placeholder="Search projects"
          allowClear
          onSearch={setSearch}
          style={{ width: 240 }}
        />
        <Select
          aria-label="Status"
          placeholder="Status"
          allowClear
          value={status}
          onChange={setStatus}
          style={{ width: 160 }}
          options={PROJECT_STATUSES.map((s) => ({ value: s, label: LABEL[s] }))}
        />
      </Space>
      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{ pageSize: 20 }}
      />
      {canManage && (
        <Drawer
          title="New project"
          open={creating}
          onClose={() => setCreating(false)}
          width={480}
          destroyOnClose
        >
          {saveError && <Alert type="error" message={saveError} style={{ marginBottom: 16 }} />}
          <ProjectForm onSubmit={create} onCancel={() => setCreating(false)} submitting={saving} />
        </Drawer>
      )}
    </Card>
  );
}
