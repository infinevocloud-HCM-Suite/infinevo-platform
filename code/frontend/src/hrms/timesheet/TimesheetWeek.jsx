import { useCallback, useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Input,
  InputNumber,
  Popconfirm,
  Popover,
  Result,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import { DeleteOutlined, LeftOutlined, MessageOutlined, PlusOutlined, RightOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { NotEntitled, useCan } from '@shell/screens';
import { successMsg } from '@shared/ui/msgHelper.js';
import { timesheetService } from './timesheetService.js';
import {
  DAY_LABELS,
  DAY_LIMIT_HUNDREDTHS,
  STATUS_COLOR,
  STATUS_LABEL,
  addWeeks,
  dayTotals,
  emptyRow,
  formatHours,
  formatHundredths,
  gridTotal,
  isIsoDate,
  isMonday,
  mondayOf,
  projectStatuses,
  rowKey,
  rowTotal,
  rowsFromResponse,
  toRequestBody,
  validate,
  weekDates,
} from './weekGrid.js';

const { Title, Text } = Typography;

const weekPath = (weekStart) => `/hrms/timesheets/week/${weekStart}`;

/** A rejected call as `{ message, fieldErrors }`, from the flattened envelope `apiClient` rejects with. */
function readError(err, fallback) {
  return {
    message: err?.message || fallback,
    fieldErrors: err?.fieldErrors || {},
    isModuleNotEntitled: Boolean(err?.isModuleNotEntitled),
  };
}

/** The note on one cell: an editable text in a popover, or the text alone when the cell is locked. */
function NoteCell({ value, editable, label, onChange }) {
  if (!editable) {
    if (!value) return null;
    return (
      <Popover content={<Text style={{ whiteSpace: 'pre-wrap' }}>{value}</Text>} title="Note">
        <MessageOutlined aria-label={`Note ${label}`} />
      </Popover>
    );
  }
  return (
    <Popover
      trigger="click"
      title="Note"
      content={
        <Input.TextArea
          aria-label={`Note text ${label}`}
          value={value}
          maxLength={500}
          rows={3}
          style={{ width: 240 }}
          onChange={(e) => onChange(e.target.value)}
        />
      }
    >
      <Button
        size="small"
        type={value ? 'link' : 'text'}
        icon={<MessageOutlined />}
        aria-label={`Note ${label}`}
      />
    </Popover>
  );
}

NoteCell.propTypes = {
  value: PropTypes.string,
  editable: PropTypes.bool.isRequired,
  label: PropTypes.string.isRequired,
  onChange: PropTypes.func,
};

/**
 * One week of the caller's timesheet as a grid (W-48.2 §5): rows are project → task, columns Monday to Sunday.
 * Create, edit and view are this one screen (§13 decision 1). A non-Monday date in the URL goes to its Monday.
 */
export function TimesheetWeek() {
  const { weekStart } = useParams();
  if (!isIsoDate(weekStart)) {
    return <Navigate to={weekPath(mondayOf(dayjs()))} replace />;
  }
  if (!isMonday(weekStart)) {
    return <Navigate to={weekPath(mondayOf(weekStart))} replace />;
  }
  return <WeekGrid key={weekStart} weekStart={weekStart} />;
}

function WeekGrid({ weekStart }) {
  const navigate = useNavigate();
  const canSubmit = useCan('hrms.timesheet.submit');
  const dates = useMemo(() => weekDates(weekStart), [weekStart]);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [timesheet, setTimesheet] = useState(null);
  const [rows, setRows] = useState([]);
  const [projects, setProjects] = useState([]);
  const [tasksByProject, setTasksByProject] = useState({});
  const [busy, setBusy] = useState(false);
  const [problems, setProblems] = useState(null); // { message, list: string[] }
  const [addProject, setAddProject] = useState(null);
  const [addTask, setAddTask] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [week, mine] = await Promise.all([
        timesheetService.week(weekStart),
        timesheetService.myProjects(),
      ]);
      const projectList = Array.isArray(mine) ? mine : [];
      const ids = [
        ...new Set([
          ...projectList.map((p) => p.id),
          ...((week && week.projects) || []).map((p) => p.project_id),
        ]),
      ];
      const lists = await Promise.all(
        ids.map((id) => timesheetService.tasks(id).catch(() => []))
      );
      setProjects(projectList);
      setTasksByProject(Object.fromEntries(ids.map((id, i) => [id, Array.isArray(lists[i]) ? lists[i] : []])));
      setTimesheet(week);
      setRows(rowsFromResponse(week));
    } catch (err) {
      setLoadError(readError(err, 'Could not load this week'));
    } finally {
      setLoading(false);
    }
  }, [weekStart]);

  useEffect(() => {
    load();
  }, [load]);

  const statuses = useMemo(() => projectStatuses(timesheet), [timesheet]);
  const weekStatus = timesheet?.status || 'NOT_STARTED';
  const mode = !timesheet
    ? 'new'
    : timesheet.status === 'DRAFT'
      ? 'draft'
      : timesheet.status === 'REJECTED'
        ? 'rejected'
        : 'locked';
  const rejectedIds = Object.keys(statuses).filter((id) => statuses[id].status === 'REJECTED');

  const isEditable = (projectId) => {
    if (!canSubmit) return false;
    if (mode === 'new' || mode === 'draft') return true;
    if (mode === 'rejected') return statuses[projectId]?.status === 'REJECTED';
    return false;
  };
  const anyEditable = canSubmit && (mode === 'new' || mode === 'draft' || mode === 'rejected');

  // A task's own project_name (W-48.1) when the server sends it, else the name from /projects/mine.
  const projectName = (projectId) => {
    const fromTask = (tasksByProject[projectId] || []).find((t) => t.project_name)?.project_name;
    return fromTask || projects.find((p) => p.id === projectId)?.name || 'Unknown project';
  };
  const taskName = (projectId, taskId) =>
    (tasksByProject[projectId] || []).find((t) => t.id === taskId)?.title || 'Unknown task';

  const live = validate(weekStart, rows);
  const totals = dayTotals(rows, dates);

  const apply = (saved) => {
    setTimesheet(saved);
    setRows(rowsFromResponse(saved));
  };

  const updateCell = (key, date, patch) => {
    setRows((current) =>
      current.map((row) =>
        row.key === key
          ? {
              ...row,
              cells: {
                ...row.cells,
                [date]: { hours: '', description: '', ...row.cells[date], ...patch },
              },
            }
          : row
      )
    );
  };

  const check = (onlyEditable) => {
    const result = validate(weekStart, rows, {
      assignedProjectIds: onlyEditable ? undefined : projects.map((p) => p.id),
    });
    if (!result.ok) {
      setProblems({ message: 'Fix these before sending', list: result.errors });
      return false;
    }
    return true;
  };

  const fail = (err, fallback) => {
    const { message, fieldErrors } = readError(err, fallback);
    setProblems({
      message,
      list: Object.entries(fieldErrors).map(([field, reason]) => `${field}: ${reason}`),
    });
  };

  /** Saves the grid: POST on the first save, PUT on a draft. Returns the saved week, or null. */
  const persist = async () => {
    if (!check(false)) return null;
    const body = toRequestBody(weekStart, rows);
    const saved = timesheet
      ? await timesheetService.replace(timesheet.id, body)
      : await timesheetService.create(body);
    apply(saved);
    return saved;
  };

  const run = async (action, fallback) => {
    setBusy(true);
    setProblems(null);
    try {
      await action();
    } catch (err) {
      fail(err, fallback);
    } finally {
      setBusy(false);
    }
  };

  const handleSave = () =>
    run(async () => {
      const saved = await persist();
      if (saved) successMsg('Draft saved');
    }, 'Could not save the week');

  // Submit always saves first, so an unsaved edit is never left behind (§9).
  const handleSubmit = () =>
    run(async () => {
      const saved = await persist();
      if (!saved) return;
      const submitted = await timesheetService.submit(saved.id);
      apply(submitted);
      successMsg('Week submitted for approval');
    }, 'Could not submit the week');

  // A resubmit sends the rejected projects only (W-42-3-timesheet-submit-approve.md §4).
  const handleResubmit = () =>
    run(async () => {
      if (!check(true)) return;
      const body = toRequestBody(weekStart, rows, { onlyProjects: rejectedIds });
      if (body.projects.length === 0) {
        setProblems({ message: 'Enter hours on the rejected projects before resubmitting', list: [] });
        return;
      }
      const saved = await timesheetService.replace(timesheet.id, body);
      apply(saved);
      successMsg('Rejected projects resubmitted');
    }, 'Could not resubmit the week');

  const handleDelete = () =>
    run(async () => {
      await timesheetService.remove(timesheet.id);
      setTimesheet(null);
      setRows([]);
      successMsg('Draft deleted');
    }, 'Could not delete the draft');

  const handleAddRow = () => {
    if (!addProject || !addTask) return;
    const key = rowKey(addProject, addTask);
    if (!rows.some((r) => r.key === key)) {
      setRows((current) => [...current, emptyRow(addProject, addTask)]);
    }
    setAddTask(null);
  };

  if (loading) {
    return (
      <Card data-testid="timesheet-week-loading">
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }
  if (loadError) {
    if (loadError.isModuleNotEntitled) return <NotEntitled />;
    return (
      <Result
        status="error"
        title="Could not load this week"
        subTitle={loadError.message}
        extra={
          <Button type="primary" onClick={load}>
            Retry
          </Button>
        }
      />
    );
  }

  const addableProjects = (mode === 'rejected'
    ? rejectedIds.map((id) => ({ id, name: projectName(id) }))
    : projects
  ).map((p) => ({ value: p.id, label: p.name || projectName(p.id) }));
  const addableTasks = (tasksByProject[addProject] || [])
    .filter((t) => !rows.some((r) => r.key === rowKey(addProject, t.id)))
    .map((t) => ({ value: t.id, label: t.title }));

  const dayColumns = dates.map((date, i) => ({
    title: (
      <span>
        {DAY_LABELS[i]} <Text type="secondary">{dayjs(date).format('D MMM')}</Text>
      </span>
    ),
    key: date,
    width: 120,
    render: (_, row) => {
      const cell = row.cells[date] || { hours: '', description: '' };
      const editable = isEditable(row.projectId);
      const label = `${projectName(row.projectId)} ${taskName(row.projectId, row.taskId)} ${date}`;
      const invalid =
        Boolean(live.cellErrors[`${row.key}|${date}`]) || totals[date] > DAY_LIMIT_HUNDREDTHS;
      return (
        <Space size={2}>
          {editable ? (
            <InputNumber
              aria-label={`Hours ${label}`}
              stringMode
              min="0"
              max="24"
              step="0.25"
              precision={2}
              value={cell.hours === '' ? null : cell.hours}
              status={invalid ? 'error' : undefined}
              style={{ width: 76 }}
              onChange={(value) => updateCell(row.key, date, { hours: value ?? '' })}
            />
          ) : (
            <Text type={invalid ? 'danger' : undefined}>{formatHours(cell.hours)}</Text>
          )}
          <NoteCell
            value={cell.description || ''}
            editable={editable}
            label={label}
            onChange={(description) => updateCell(row.key, date, { description })}
          />
        </Space>
      );
    },
  }));

  const columns = [
    {
      title: 'Project',
      key: 'project',
      render: (_, row) => (
        <Space size={4} wrap>
          <span>{projectName(row.projectId)}</span>
          {statuses[row.projectId] && (
            <Tag color={STATUS_COLOR[statuses[row.projectId].status]}>
              {STATUS_LABEL[statuses[row.projectId].status] || statuses[row.projectId].status}
            </Tag>
          )}
        </Space>
      ),
    },
    { title: 'Task', key: 'task', render: (_, row) => taskName(row.projectId, row.taskId) },
    ...dayColumns,
    {
      title: 'Total',
      key: 'total',
      render: (_, row) => <Text strong>{formatHundredths(rowTotal(row))}</Text>,
    },
  ];
  if (anyEditable) {
    columns.push({
      title: '',
      key: 'remove',
      render: (_, row) =>
        isEditable(row.projectId) ? (
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            aria-label={`Remove ${projectName(row.projectId)} ${taskName(row.projectId, row.taskId)}`}
            onClick={() => setRows((current) => current.filter((r) => r.key !== row.key))}
          />
        ) : null,
    });
  }

  const summary = () => (
    <Table.Summary.Row>
      <Table.Summary.Cell index={0} colSpan={2}>
        <Text strong>Day total</Text>
      </Table.Summary.Cell>
      {dates.map((date, i) => (
        <Table.Summary.Cell index={i + 2} key={date}>
          <Text
            strong
            type={totals[date] > DAY_LIMIT_HUNDREDTHS ? 'danger' : undefined}
            data-testid={`day-total-${date}`}
          >
            {formatHundredths(totals[date])}
          </Text>
        </Table.Summary.Cell>
      ))}
      <Table.Summary.Cell index={9}>
        <Text strong data-testid="week-total">
          {formatHundredths(gridTotal(rows))}
        </Text>
      </Table.Summary.Cell>
      {anyEditable && <Table.Summary.Cell index={10} />}
    </Table.Summary.Row>
  );

  return (
    <div style={{ padding: 24 }} data-testid="timesheet-week">
      <Space align="center" wrap style={{ marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          Timesheet
        </Title>
        <Tag color={STATUS_COLOR[weekStatus]} data-testid="week-status">
          {STATUS_LABEL[weekStatus] || weekStatus}
        </Tag>
        <Link to="/hrms/timesheets">All weeks</Link>
      </Space>

      <Space align="center" wrap style={{ marginBottom: 16, display: 'flex' }}>
        <Button
          icon={<LeftOutlined />}
          aria-label="Previous week"
          onClick={() => navigate(weekPath(addWeeks(weekStart, -1)))}
        />
        <DatePicker
          value={dayjs(weekStart)}
          allowClear={false}
          format="[Week of] D MMM YYYY"
          disabledDate={(d) => d.day() !== 1}
          onChange={(d) => d && navigate(weekPath(mondayOf(d)))}
        />
        <Button
          icon={<RightOutlined />}
          aria-label="Next week"
          onClick={() => navigate(weekPath(addWeeks(weekStart, 1)))}
        />
      </Space>

      {mode === 'locked' && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={
            weekStatus === 'APPROVED'
              ? 'This week is approved and can no longer be changed.'
              : 'This week has been submitted for approval and is read-only.'
          }
        />
      )}

      {rejectedIds.map((id) => (
        <Alert
          key={id}
          type="warning"
          showIcon
          style={{ marginBottom: 8 }}
          message={`${projectName(id)} was rejected`}
          description={statuses[id].rejectionReason || 'No reason was given.'}
          data-testid={`rejected-${id}`}
        />
      ))}

      {problems && (
        <Alert
          type="error"
          showIcon
          closable
          onClose={() => setProblems(null)}
          style={{ marginBottom: 16 }}
          message={problems.message}
          description={
            problems.list.length > 0 ? (
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {problems.list.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            ) : undefined
          }
          data-testid="timesheet-problems"
        />
      )}

      <Card>
        <Table
          rowKey="key"
          size="small"
          columns={columns}
          dataSource={rows}
          pagination={false}
          scroll={{ x: true }}
          summary={summary}
          locale={{ emptyText: anyEditable ? 'No rows yet. Add a project and task below.' : 'No hours this week.' }}
        />

        {anyEditable && (
          <Space wrap style={{ marginTop: 16 }}>
            <Select
              aria-label="Project"
              placeholder="Project"
              style={{ width: 220 }}
              value={addProject}
              options={addableProjects}
              onChange={(value) => {
                setAddProject(value);
                setAddTask(null);
              }}
            />
            <Select
              aria-label="Task"
              placeholder="Task"
              style={{ width: 220 }}
              value={addTask}
              options={addableTasks}
              disabled={!addProject}
              onChange={setAddTask}
            />
            <Button icon={<PlusOutlined />} onClick={handleAddRow} disabled={!addProject || !addTask}>
              Add row
            </Button>
          </Space>
        )}

        {anyEditable && (
          <Space wrap style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
            {mode === 'draft' && (
              <Popconfirm
                title="Delete this draft week?"
                okText="Yes"
                cancelText="No"
                onConfirm={handleDelete}
                disabled={busy}
              >
                <Button danger disabled={busy}>
                  Delete draft
                </Button>
              </Popconfirm>
            )}
            {mode !== 'rejected' && (
              <Button onClick={handleSave} loading={busy}>
                Save draft
              </Button>
            )}
            {mode !== 'rejected' && (
              <Popconfirm
                title="Submit this week for approval? It cannot be changed afterwards."
                okText="Yes"
                cancelText="No"
                onConfirm={handleSubmit}
                disabled={busy}
              >
                <Button type="primary" disabled={busy}>
                  Submit
                </Button>
              </Popconfirm>
            )}
            {mode === 'rejected' && (
              <Popconfirm
                title="Resubmit the rejected projects for approval?"
                okText="Yes"
                cancelText="No"
                onConfirm={handleResubmit}
                disabled={busy}
              >
                <Button type="primary" disabled={busy}>
                  Resubmit
                </Button>
              </Popconfirm>
            )}
          </Space>
        )}
        {live.errors.length > 0 && Object.keys(live.dayErrors).length > 0 && (
          <Text type="danger" style={{ display: 'block', marginTop: 8 }}>
            {Object.values(live.dayErrors).join(' ')}
          </Text>
        )}
      </Card>
    </div>
  );
}

WeekGrid.propTypes = {
  weekStart: PropTypes.string.isRequired,
};

export default TimesheetWeek;
