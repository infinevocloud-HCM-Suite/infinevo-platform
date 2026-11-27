import { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import {
  Table,
  Button,
  Space,
  Typography,
  Card,
  Tag,
  Select,
  Input,
  theme,
} from 'antd';
import { CheckOutlined, CloseOutlined, EyeOutlined } from '@ant-design/icons';
import { useCan, useHasModule, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { approvalService } from './approvalService.js';
import { setPendingCount, decrementPendingCount } from './approvalSlice.js';
import { DecideModal } from './DecideModal.jsx';
import { getItemRoute } from './itemRoutes.js';
import { flowTypesFor } from './flowTypes.js';
import { formatDate, humanize } from '@shared/ui/format.js';
import { PageHeader } from '@shared/ui/PageHeader.jsx';

const { Text } = Typography;

const FLOW_TYPE_COLORS = {
  LEAVE: 'blue',
  REGULARIZATION: 'purple',
  OVERTIME: 'orange',
  REIMBURSEMENT: 'green',
  PROOF_OF_INVESTMENT: 'cyan',
  PAY_RUN: 'geekblue',
  TIMESHEET: 'magenta',
  WORK_FROM_HOME: 'cyan',
};

const formatSummary = (str) => {
  if (!str) return str;
  return str.replace('WORK_FROM_HOME Request', 'Work From Home Request');
};

/** The server's largest page (ApprovalController caps size at 100). */
const FILTER_LOAD_SIZE = 100;

export function Inbox() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const canDecide = useCan('core.approval.decide');
  const hasHrms = useHasModule('HRMS');
  const hasPayroll = useHasModule('PAYROLL');

  // The modules narrow the filter's choices only. The rows are whatever the server assigned to
  // this user: a step waiting on them is never hidden on the strength of a guess about modules.
  const filterOptions = useMemo(
    () => flowTypesFor({ hasHrms, hasPayroll }).map((flow) => ({ label: flow.label, value: flow.key })),
    [hasHrms, hasPayroll],
  );

  const [loading, setLoading] = useState(false);
  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);
  const [flowTypeFilter, setFlowTypeFilter] = useState(null);
  // Founder 2026-11-27: find a person's requests by name. While a filter is on, the inbox loads the
  // server's largest page (100) and filters it here, so a match is not lost on a page not loaded.
  const [nameFilter, setNameFilter] = useState('');
  const filtering = Boolean(flowTypeFilter) || nameFilter.trim() !== '';

  // Decide modal state
  const [decideModal, setDecideModal] = useState({
    open: false,
    decision: 'APPROVED',
    step: null,
  });

  const loadPending = useCallback(async () => {
    if (!canDecide) return;
    setLoading(true);
    try {
      const res = filtering
        ? await approvalService.pending(0, FILTER_LOAD_SIZE)
        : await approvalService.pending(page - 1, pageSize);
      const items = res?.content || (Array.isArray(res) ? res : []);
      const count = res?.totalElements ?? items.length;
      setData(items);
      setTotal(count);
      dispatch(setPendingCount(count));
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [canDecide, page, pageSize, dispatch, filtering]);

  useEffect(() => {
    loadPending();
  }, [loadPending]);

  const handleOpenDecide = (step, decision) => {
    setDecideModal({
      open: true,
      decision,
      step,
    });
  };

  const handleDecideSuccess = async () => {
    const decision = decideModal.decision;
    setDecideModal({ open: false, decision: 'APPROVED', step: null });
    dispatch(decrementPendingCount());
    await successMsg(
      'Decision Recorded',
      `Request has been ${decision.toLowerCase()} successfully.`
    );
    loadPending();
  };

  // Filter by the selected flow type and by the employee's name (or employee ID), case-insensitive
  const filteredData = useMemo(() => {
    const needle = nameFilter.trim().toLowerCase();
    return data.filter(
      (item) =>
        (!flowTypeFilter || item.flowType === flowTypeFilter) &&
        (!needle ||
          String(item.subjectEmployeeName || '').toLowerCase().includes(needle) ||
          String(item.subjectEmployeeId || '').toLowerCase().includes(needle)),
    );
  }, [data, flowTypeFilter, nameFilter]);

  const columns = [
    {
      title: 'Flow Type',
      dataIndex: 'flowType',
      key: 'flowType',
      width: 160,
      render: (flowType) => {
        const type = flowType || 'GENERAL';
        return <Tag color={FLOW_TYPE_COLORS[type] || 'default'}>{humanize(type)}</Tag>;
      },
    },
    {
      title: 'Subject / Ref',
      key: 'itemRef',
      render: (_, record) => (
        <Space direction="vertical" size={2}>
          <Link to={`/approvals/${record.instanceId}`} id={`link-instance-${record.id}`}>
            <Text strong>{formatSummary(record.summary || record.itemRef) || `Instance #${record.instanceId?.substring(0, 8)}`}</Text>
          </Link>
          {record.subjectEmployeeId && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.subjectEmployeeName
                ? `Employee: ${record.subjectEmployeeName}`
                : `Employee ID: ${record.subjectEmployeeId}`}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Step',
      key: 'stepIndex',
      width: 130,
      render: (_, record) => {
        const stepNum = (record.stepIndex ?? 0) + 1;
        return record.totalSteps ? `Step ${stepNum} of ${record.totalSteps}` : `Step ${stepNum}`;
      },
    },
    {
      title: 'Waiting Since',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (date) => formatDate(date),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 220,
      render: (_, record) => {
        const type = record.flowType;
        const itemRoute = record.itemId ? getItemRoute(type, record.itemId) : null;

        return (
          <Space size="small">
            {canDecide && (
              <>
                <Button
                  type="primary"
                  size="small"
                  icon={<CheckOutlined />}
                  onClick={() => handleOpenDecide(record, 'APPROVED')}
                  id={`btn-approve-${record.id}`}
                  className="btn-spring"
                  style={{
                    background: token.colorSuccess,
                    borderColor: token.colorSuccess,
                    borderRadius: 6,
                    fontWeight: 600,
                  }}
                >
                  Approve
                </Button>
                <Button
                  danger
                  size="small"
                  icon={<CloseOutlined />}
                  onClick={() => handleOpenDecide(record, 'REJECTED')}
                  id={`btn-reject-${record.id}`}
                  className="btn-spring"
                  style={{ borderRadius: 6, fontWeight: 600 }}
                >
                  Reject
                </Button>
              </>
            )}
            {itemRoute && (
              <Button
                type="link"
                size="small"
                icon={<EyeOutlined />}
                onClick={() => navigate(itemRoute, { state: { from: '/approvals' } })}
                id={`btn-view-item-${record.id}`}
              >
                View Item
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  if (!canDecide) {
    return <NotEntitled />;
  }

  return (
    <Card variant="borderless" className="saas-card saas-page-container">
      <PageHeader
        title="Pending Approvals"
        subtitle="Review and decide requests waiting on your approval."
        count={`${total} pending`}
        actions={
          <Space wrap>
          <Input.Search
            id="input-employee-filter"
            allowClear
            placeholder="Search employee name"
            style={{ width: 220 }}
            value={nameFilter}
            onChange={(e) => setNameFilter(e.target.value)}
          />
          <Select
            id="select-flow-type-filter"
            allowClear
            placeholder="Filter by Flow Type"
            style={{ width: 220 }}
            value={flowTypeFilter}
            onChange={(val) => setFlowTypeFilter(val)}
            options={filterOptions}
          />
          </Space>
        }
      />

      <Table
        dataSource={filteredData}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={
          filtering
            ? { pageSize, onChange: (_, s) => setPageSize(s) }
            : {
                current: page,
                pageSize,
                total,
                onChange: (p, s) => {
                  setPage(p);
                  setPageSize(s);
                },
              }
        }
      />

      {decideModal.step && (
        <DecideModal
          open={decideModal.open}
          decision={decideModal.decision}
          step={decideModal.step}
          onCancel={() => setDecideModal({ open: false, decision: 'APPROVED', step: null })}
          onSuccess={handleDecideSuccess}
        />
      )}
    </Card>
  );
}
