import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Table, Input, AutoComplete, Select, Switch, Button, Tag, Space, Typography, Card, Alert, Modal, theme, Avatar } from 'antd';
import { PlusOutlined, UploadOutlined, MailOutlined, DownloadOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { StatusDot } from '@shared/ui/StatusDot.jsx';
import { theme as appTheme } from '@shared/theme.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { employeeService } from './employeeService.js';
import { limitBadgeText, limitReached } from './limitText.js';
import { employeeImportService } from './employeeImportService.js';
import { orgMasterService } from './orgMasterService.js';
import { setMasters } from './employeeSlice.js';
import { formatDate, statusLabel } from '@shared/ui/format.js';

const { Text } = Typography;

const statusColorMap = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  TERMINATED: 'error',
};

const STATUSES = ['ACTIVE', 'SUSPENDED', 'TERMINATED'];
const DEFAULT_PAGE_SIZE = 25;

/**
 * D-139: the list's view lives in the URL query - `page`, `size`, `q`, `status`, `deleted` and
 * `focus` (the row last opened) - so the back arrow from an employee, a reload and a shared link
 * all land on the same page with the same filters. A missing or malformed value is the default.
 */
export function readListView(params) {
  const page = Number.parseInt(params.get('page') || '', 10);
  const size = Number.parseInt(params.get('size') || '', 10);
  const status = params.get('status');
  return {
    page: Number.isInteger(page) && page > 0 ? page : 1,
    pageSize: Number.isInteger(size) && size > 0 ? size : DEFAULT_PAGE_SIZE,
    searchQuery: params.get('q') || '',
    statusFilter: STATUSES.includes(status) ? status : undefined,
    includeDeleted: params.get('deleted') === '1',
    focusId: params.get('focus') || null,
  };
}

/** The query for a view, defaults left out so the plain `/employees` stays the first page. */
export function writeListView(view) {
  const next = {};
  if (view.page > 1) next.page = String(view.page);
  if (view.pageSize !== DEFAULT_PAGE_SIZE) next.size = String(view.pageSize);
  if (view.searchQuery) next.q = view.searchQuery;
  if (view.statusFilter) next.status = view.statusFilter;
  if (view.includeDeleted) next.deleted = '1';
  if (view.focusId) next.focus = view.focusId;
  return next;
}

export function EmployeeList() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { token } = theme.useToken();

  const canCreate = useCan('core.employee.create');
  const canDelete = useCan('core.employee.delete');
  const canExport = useCan('core.employee.export');

  const masters = useSelector((state) => state.employee?.masters || {});
  const mastersLoadedAt = useSelector((state) => state.employee?.loadedAt);

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [params, setParams] = useSearchParams();
  const view = readListView(params);
  const { page, pageSize, searchQuery, statusFilter, includeDeleted, focusId } = view;
  // One setter for the view: a filter change goes back to page 1 and drops the focused row.
  const setView = useCallback(
    (changes) => setParams(writeListView({ ...readListView(params), ...changes }), { replace: true }),
    [params, setParams]
  );
  const focusedRowRef = useRef(null);
  // D-139: suggestions under the search box, fetched after a short pause in typing; picking one
  // opens that employee, Enter still filters the list on the typed text.
  const [suggestions, setSuggestions] = useState([]);
  const suggestTimer = useRef(null);
  const suggestSeq = useRef(0);
  const [exporting, setExporting] = useState(false);
  const [loadError, setLoadError] = useState(null);
  // W-84.2: the plan's limit and the places in use, {limit, used}; null until loaded or when the call fails.
  const [usage, setUsage] = useState(null);

  const openEmployee = useCallback(
    (id) => {
      const query = new URLSearchParams(writeListView({ ...view, focusId: id })).toString();
      const from = query ? `/employees?${query}` : '/employees';
      navigate(`/employees/${id}`, { state: { from } });
    },
    [navigate, view]
  );

  const suggest = useCallback(
    (text) => {
      clearTimeout(suggestTimer.current);
      const q = (text || '').trim();
      if (q.length < 2) {
        setSuggestions([]);
        return;
      }
      suggestTimer.current = setTimeout(async () => {
        const seq = ++suggestSeq.current;
        try {
          const res = await employeeService.list({
            q,
            status: statusFilter || undefined,
            includeDeleted: canDelete ? includeDeleted : false,
            page: 0,
            size: 8,
            sort: 'employeeNumber,asc',
          });
          if (seq !== suggestSeq.current) return; // a later keystroke already answered
          setSuggestions(
            (res.content || []).map((r) => {
              const name = [r.firstName, r.middleName, r.lastName].filter(Boolean).join(' ');
              return { value: `${r.employeeNumber} · ${name}`, id: r.id, name };
            })
          );
        } catch {
          setSuggestions([]);
        }
      }, 300);
    },
    [statusFilter, includeDeleted, canDelete]
  );
  useEffect(() => () => clearTimeout(suggestTimer.current), []);

  // A failure leaves the badge on the list total; the limit is advisory here, the server enforces it.
  const fetchUsage = useCallback(async () => {
    try {
      const res = await employeeService.limit();
      if (res && typeof res.used === 'number') {
        setUsage({ limit: typeof res.limit === 'number' ? res.limit : null, used: res.used });
      }
    } catch {
      // ignored on purpose
    }
  }, []);

  // Load masters once per session
  useEffect(() => {
    if (!mastersLoadedAt) {
      orgMasterService.all().then((res) => {
        dispatch(setMasters(res));
      }).catch(() => {});
    }
  }, [dispatch, mastersLoadedAt]);

  const fetchEmployees = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const res = await employeeService.list({
        q: searchQuery || undefined,
        status: statusFilter || undefined,
        includeDeleted: canDelete ? includeDeleted : false,
        page: page - 1,
        size: pageSize,
        sort: 'employeeNumber,asc',
      });
      setData(res.content || []);
      setTotal(res.totalElements || 0);
      fetchUsage();
    } catch (err) {
      // A failed load must not look like "no employees": say so, and offer a retry.
      setData([]);
      setTotal(0);
      setLoadError(err?.message || 'Something went wrong');
    } finally {
      setLoading(false);
    }
  }, [searchQuery, statusFilter, includeDeleted, page, pageSize, canDelete, fetchUsage]);

  useEffect(() => {
    fetchEmployees();
  }, [fetchEmployees]);

  // Back from an employee: bring the row that was opened into view once the page has loaded.
  useEffect(() => {
    if (!loading && focusId && focusedRowRef.current && typeof focusedRowRef.current.scrollIntoView === 'function') {
      focusedRowRef.current.scrollIntoView({ block: 'center' });
    }
  }, [loading, focusId, data]);

  // W-73.7: one job inviting every active employee with a work email and no account or live invitation,
  // with the employee role only. The count comes first so the confirm says how many will get an email.
  const handleInviteAll = async () => {
    let count;
    try {
      count = await employeeImportService.inviteAllCount();
    } catch (err) {
      await errorMsg(err);
      return;
    }
    if (!count) {
      await successMsg('Nobody to invite', 'Every employee with a work email already has access or an invitation.');
      return;
    }
    Modal.confirm({
      title: 'Invite all without access',
      content: `Send a portal invitation to ${count} employee${count === 1 ? '' : 's'} with a work email and no access yet? They get the Employee role only.`,
      okText: `Invite ${count}`,
      onOk: async () => {
        try {
          await employeeImportService.inviteAll();
          await successMsg('Invitations queued', 'Progress and the result file are on the Import page.');
          navigate('/employees/import');
        } catch (err) {
          await errorMsg(err);
        }
      },
    });
  };

  const handleExport = async () => {
    setExporting(true);
    try {
      const res = await employeeService.export({
        ...(statusFilter ? { status: statusFilter } : {}),
      });
      if (res?.url) {
        employeeService.downloadFile(res.url, res.fileName || 'employees.xlsx');
        await successMsg('Export downloaded', `Exported ${res.rowCount || 0} employees.`);
      } else {
        throw new Error('Export link not returned by server');
      }
    } catch (err) {
      await errorMsg(err);
    } finally {
      setExporting(false);
    }
  };

  const columns = [
    {
      title: 'Emp ID',
      width: 110,
      dataIndex: 'employeeNumber',
      key: 'employeeNumber',
      render: (num) => (
        <Text type="secondary" style={{ fontFamily: 'monospace' }}>
          {num}
        </Text>
      ),
    },
    {
      title: 'Name',
      key: 'name',
      width: 200,
      ellipsis: true,
      render: (_, r) => {
        const fullName = [r.firstName, r.middleName, r.lastName].filter(Boolean).join(' ');
        const initials = [r.firstName?.[0], r.lastName?.[0]].filter(Boolean).join('').toUpperCase() || 'U';
        return (
          <Space size="middle">
            <Avatar
              size="small"
              style={{
                backgroundColor: token.colorInfoBg,
                color: token.colorInfoText,
                fontSize: 12,
              }}
            >
              {initials}
            </Avatar>
            <Text strong ellipsis={{ tooltip: fullName }}>{fullName}</Text>
          </Space>
        );
      },
    },
    {
      title: 'Work Email',
      dataIndex: 'workEmail',
      key: 'workEmail',
      width: 200,
      ellipsis: true,
      render: (email) => (email ? <span title={email}>{email}</span> : <span style={{ color: token.colorTextSecondary }}>—</span>),
    },
    {
      title: 'Department',
      width: 130,
      dataIndex: 'departmentId',
      key: 'department',
      render: (id) => (id && masters.departments?.[id]) || '—',
    },
    {
      title: 'Designation',
      width: 140,
      dataIndex: 'designationId',
      key: 'designation',
      render: (id) => (id && masters.designations?.[id]) || '—',
    },
    {
      title: 'Location',
      width: 120,
      dataIndex: 'workLocationId',
      key: 'workLocation',
      render: (id) => (id && masters.workLocations?.[id]) || '—',
    },
    {
      title: 'Status',
      width: 100,
      dataIndex: 'status',
      key: 'status',
      render: (status, r) => (
        <Space size="small">
          <StatusDot tone={statusColorMap[status] || 'default'}>{statusLabel(status)}</StatusDot>
          {r.isDeleted && <Tag color="error">Deleted</Tag>}
        </Space>
      ),
    },
    {
      title: 'Joined',
      width: 100,
      dataIndex: 'dateOfJoining',
      key: 'dateOfJoining',
      render: (date) => formatDate(date),
    },
  ];

  const headerActions = (
    <Space wrap>
      {canExport && (
        <Button
          icon={<DownloadOutlined />}
          onClick={handleExport}
          loading={exporting}
          id="btn-export-employees"
          className="btn-spring"
        >
          Export
        </Button>
      )}
      {canCreate && (
        <>
          <Button
            icon={<MailOutlined />}
            onClick={handleInviteAll}
            id="btn-invite-all"
            className="btn-spring"
          >
            Invite all without access
          </Button>
          <Button
            icon={<UploadOutlined />}
            onClick={() => navigate('/employees/import')}
            id="btn-import-employees"
            className="btn-spring"
          >
            Import
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/employees/new')}
            id="btn-new-employee"
            className="btn-spring"
            style={{
              boxShadow: '0 2px 8px rgba(37, 99, 235, 0.25)',
              fontWeight: 600,
            }}
          >
            New Employee
          </Button>
        </>
      )}
    </Space>
  );

  return (
    <Card variant="borderless" className="saas-card saas-page-container">
      <PageHeader
        title="Employees"
        count={usage ? limitBadgeText(usage.used, usage.limit) : `${total} employees`}
        actions={headerActions}
      />

      {usage && limitReached(usage.used, usage.limit) && (
        <Alert
          type="warning"
          showIcon
          id="alert-employee-limit"
          message={`Your plan allows ${usage.limit} employees and ${usage.used} are in use. Contact Infinevo to raise the limit.`}
          style={{ marginBottom: token.marginLG }}
        />
      )}

      <div style={{ display: 'flex', gap: token.margin, flexWrap: 'wrap', marginBottom: token.marginLG, alignItems: 'center' }}>
        <AutoComplete
          key={searchQuery}
          defaultValue={searchQuery}
          options={suggestions}
          onSearch={suggest}
          onSelect={(_, option) => openEmployee(option.id)}
          style={{ width: 280 }}
          id="input-search-employees"
          data-testid="employee-search-suggest"
        >
          <Input.Search
            placeholder="Search employees..."
            allowClear
            onSearch={(val) => {
              setSuggestions([]);
              setView({ searchQuery: val, page: 1, focusId: null });
            }}
          />
        </AutoComplete>

        <Select
          placeholder="Filter by Status"
          allowClear
          value={statusFilter}
          onChange={(val) => setView({ statusFilter: val, page: 1, focusId: null })}
          style={{ width: 160 }}
          options={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'SUSPENDED', label: 'Suspended' },
            { value: 'TERMINATED', label: 'Terminated' },
          ]}
          id="select-status-filter"
        />

        {canDelete && (
          <Space style={{ marginLeft: 'auto' }} id="wrapper-include-deleted">
            <Text type="secondary">Include deleted:</Text>
            <Switch
              checked={includeDeleted}
              onChange={(checked) => setView({ includeDeleted: checked, page: 1, focusId: null })}
              id="switch-include-deleted"
            />
          </Space>
        )}
      </div>

      {loadError && (
        <Alert
          type="error"
          showIcon
          message="Employees could not be loaded"
          description={loadError}
          action={
            <Button size="small" onClick={fetchEmployees} id="btn-retry-employees">
              Retry
            </Button>
          }
          style={{ marginBottom: token.marginLG, borderRadius: 8 }}
          id="alert-employees-load-error"
        />
      )}

      <Table
        rowKey="id"
        scroll={{ x: 960 }}
        tableLayout="fixed"
        // W-83.7: the header stays below the app's 64 px top bar while rows scroll.
        sticky={{ offsetHeader: appTheme.components.Layout.headerHeight }}
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (p, ps) => setView({ page: ps === pageSize ? p : 1, pageSize: ps, focusId: null }),
        }}
        // D-139: the row click records itself in the list URL and hands that URL to the employee
        // page, whose back arrow returns here. The row is highlighted until the view changes.
        rowClassName={(record) => (record.id === focusId ? 'employee-row-focused' : '')}
        onRow={(record) => ({
          onClick: () => openEmployee(record.id),
          style: { cursor: 'pointer' },
          'data-testid': `employee-row-${record.id}`,
          ref: record.id === focusId ? focusedRowRef : undefined,
        })}
      />
    </Card>
  );
}
