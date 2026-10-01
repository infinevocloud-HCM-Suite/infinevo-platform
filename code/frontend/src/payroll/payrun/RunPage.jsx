import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import {
  Card,
  Row,
  Col,
  Statistic,
  Tag,
  Button,
  Space,
  Progress,
  Typography,
  Alert,
  Popconfirm,
  Spin,
  Descriptions,
  Breadcrumb,
} from 'antd';
import {
  LockOutlined,
  PlayCircleOutlined,
  CloseCircleOutlined,
  ArrowLeftOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { payrunService } from './payrunService.js';
import { startPolling, stopPolling, setRun } from './payrunSlice.js';
import { RunEmployees } from './RunEmployees.jsx';

const { Title, Text } = Typography;

const STATUS_COLOR_MAP = {
  DRAFT: 'default',
  LOCKED: 'processing',
  COMPUTING: 'cyan',
  COMPUTED: 'blue',
  FAILED: 'error',
  APPROVED: 'orange',
  PAID: 'success',
  CANCELLED: 'default',
};

export function isStaleComputing(run) {
  if (!run || run.status !== 'COMPUTING') return false;
  const startedAt = run.compute_started_at || run.computeStartedAt;
  if (!startedAt) return false;
  const started = new Date(startedAt).getTime();
  return Date.now() - started > 15 * 60 * 1000;
}

export function RunPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const dispatch = useDispatch();

  const canExecute = useCan('payroll.run.execute');

  const runFromStore = useSelector((state) => state.payrun?.byId?.[id]);
  const [localRun, setLocalRun] = useState(null);
  const [loading, setLoading] = useState(!runFromStore);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);

  const run = runFromStore || localRun;

  const fetchRun = useCallback(async () => {
    if (!id) return;
    try {
      const data = await payrunService.get(id);
      dispatch(setRun(data));
      setLocalRun(data);
      return data;
    } catch (err) {
      setActionError(err?.message || 'Failed to load pay run');
    } finally {
      setLoading(false);
    }
  }, [id, dispatch]);

  useEffect(() => {
    fetchRun();
  }, [fetchRun]);

  // Handle polling when COMPUTING
  useEffect(() => {
    if (run?.status === 'COMPUTING') {
      dispatch(startPolling(id));
    } else {
      dispatch(stopPolling());
    }
    return () => {
      dispatch(stopPolling());
    };
  }, [id, run?.status, dispatch]);

  const handleCompute = async () => {
    setActionLoading(true);
    setActionError(null);
    try {
      await payrunService.compute(id);
      await fetchRun();
    } catch (err) {
      if (err?.status === 409 || err?.code === 'CONFLICT') {
        await fetchRun();
      }
      setActionError(err?.message || 'Compute failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleLock = async () => {
    setActionLoading(true);
    setActionError(null);
    try {
      await payrunService.lock(id);
      await fetchRun();
    } catch (err) {
      if (err?.status === 409 || err?.code === 'CONFLICT') {
        await fetchRun();
      }
      setActionError(err?.message || 'Lock failed');
    } finally {
      setActionLoading(false);
    }
  };

  const handleCancel = async () => {
    setActionLoading(true);
    setActionError(null);
    try {
      await payrunService.cancel(id);
      await fetchRun();
    } catch (err) {
      if (err?.status === 409 || err?.code === 'CONFLICT') {
        await fetchRun();
      }
      setActionError(err?.message || 'Cancel failed');
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!run) {
    return (
      <div style={{ padding: 24 }}>
        <Alert
          message="Pay run not found"
          description={actionError || 'The requested pay run does not exist.'}
          type="error"
          showIcon
          action={
            <Button onClick={() => navigate('/payroll/runs')}>Back to runs</Button>
          }
        />
      </div>
    );
  }

  const status = run.status;
  const isComputing = status === 'COMPUTING';
  const staleComputing = isStaleComputing(run);

  const canCompute =
    canExecute &&
    (status === 'LOCKED' ||
      status === 'COMPUTED' ||
      status === 'FAILED' ||
      (isComputing && staleComputing));

  const canLock = canExecute && status === 'DRAFT';
  const canCancel = canExecute && (status === 'DRAFT' || status === 'LOCKED');

  const progressDone = run.progress_done !== undefined ? run.progress_done : run.progressDone || 0;
  const progressTotal = run.progress_total !== undefined ? run.progress_total : run.progressTotal || 0;
  const percent = progressTotal > 0 ? Math.round((progressDone / progressTotal) * 100) : 0;
  const computeAttempt = run.compute_attempt !== undefined ? run.compute_attempt : run.computeAttempt || 1;

  const totalGross = run.total_gross !== undefined ? run.total_gross : run.totalGross;
  const totalDeductions = run.total_deductions !== undefined ? run.total_deductions : run.totalDeductions;
  const totalNet = run.total_net_pay !== undefined ? run.total_net_pay : run.totalNetPay;
  const isComputed = status === 'COMPUTED' || status === 'APPROVED' || status === 'PAID';

  return (
    <div style={{ padding: '24px', maxWidth: 1200, margin: '0 auto' }}>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/payroll/runs')}>Pay Runs</a> },
          { title: `${run.period} (${run.run_type || run.runType || 'REGULAR'})` },
        ]}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          flexWrap: 'wrap',
          gap: 16,
        }}
      >
        <Space align="center">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/payroll/runs')}>
            Runs
          </Button>
          <Title level={3} style={{ margin: 0 }}>
            Pay Run — {run.period}
          </Title>
          <Tag color={STATUS_COLOR_MAP[status] || 'default'} style={{ fontSize: 14, padding: '4px 10px' }}>
            {status}
          </Tag>
          <Tag color={run.run_type === 'OFF_CYCLE' || run.runType === 'OFF_CYCLE' ? 'purple' : 'blue'}>
            {run.run_type || run.runType || 'REGULAR'}
          </Tag>
        </Space>

        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchRun} loading={actionLoading}>
            Refresh
          </Button>

          <Popconfirm
            title="Lock this pay run?"
            description="Locking freezes pay inputs and prepares the run for computation."
            onConfirm={handleLock}
            okText="Lock"
            cancelText="Cancel"
            disabled={!canLock}
          >
            <Button
              icon={<LockOutlined />}
              disabled={!canLock}
              loading={actionLoading}
            >
              Lock
            </Button>
          </Popconfirm>

          <Popconfirm
            title="Compute pay run?"
            description={
              staleComputing
                ? 'The previous compute attempt timed out. Recompute now?'
                : 'Run async computation for all included employees?'
            }
            onConfirm={handleCompute}
            okText="Compute"
            cancelText="Cancel"
            disabled={!canCompute}
          >
            <Button
              type="primary"
              icon={<PlayCircleOutlined />}
              disabled={!canCompute}
              loading={actionLoading}
            >
              Compute
            </Button>
          </Popconfirm>

          <Popconfirm
            title="Cancel this pay run?"
            description="Are you sure you want to cancel this pay run? This action cannot be undone."
            onConfirm={handleCancel}
            okText="Yes, Cancel"
            cancelText="No"
            okButtonProps={{ danger: true }}
            disabled={!canCancel}
          >
            <Button
              danger
              icon={<CloseCircleOutlined />}
              disabled={!canCancel}
              loading={actionLoading}
            >
              Cancel
            </Button>
          </Popconfirm>
        </Space>
      </div>

      {actionError && (
        <Alert
          message="Action Failed"
          description={actionError}
          type="error"
          showIcon
          closable
          onClose={() => setActionError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      {isComputing && (
        <Card style={{ marginBottom: 24, background: '#f6ffed', borderColor: '#b7eb8f' }}>
          <Space direction="vertical" orientation="left" style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text strong>
                Computation in progress (Attempt {computeAttempt})
              </Text>
              <Text strong>
                {progressDone} / {progressTotal}
              </Text>
            </div>
            <Progress
              percent={percent}
              status="active"
              format={() => `${progressDone} / ${progressTotal}`}
            />
          </Space>
        </Card>
      )}

      <Card style={{ marginBottom: 24 }}>
        <Descriptions column={{ xs: 1, sm: 2, md: 4 }} bordered size="small">
          <Descriptions.Item label="Period">{run.period}</Descriptions.Item>
          <Descriptions.Item label="Pay Date">
            {run.pay_date || run.payDate || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="Included Employees">
            <Text strong style={{ color: '#52c41a' }}>
              {run.included_count !== undefined ? run.included_count : run.includedCount || 0}
            </Text>
          </Descriptions.Item>
          <Descriptions.Item label="Skipped Employees">
            <Text strong style={{ color: '#fa8c16' }}>
              {run.skipped_count !== undefined ? run.skipped_count : run.skippedCount || 0}
            </Text>
          </Descriptions.Item>
          {run.notes && (
            <Descriptions.Item label="Notes" span={4}>
              {run.notes}
            </Descriptions.Item>
          )}
        </Descriptions>

        {isComputed && (
          <Row gutter={16} style={{ marginTop: 20 }}>
            <Col xs={24} sm={8}>
              <Card type="inner" style={{ textAlign: 'center' }}>
                <Statistic
                  title="Total Gross"
                  value={totalGross !== undefined && totalGross !== null ? Number(totalGross) : 0}
                  precision={2}
                  prefix="₹"
                  valueStyle={{ color: '#1677ff' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card type="inner" style={{ textAlign: 'center' }}>
                <Statistic
                  title="Total Deductions"
                  value={totalDeductions !== undefined && totalDeductions !== null ? Number(totalDeductions) : 0}
                  precision={2}
                  prefix="₹"
                  valueStyle={{ color: '#ff4d4f' }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card type="inner" style={{ textAlign: 'center' }}>
                <Statistic
                  title="Total Net Pay"
                  value={totalNet !== undefined && totalNet !== null ? Number(totalNet) : 0}
                  precision={2}
                  prefix="₹"
                  valueStyle={{ color: '#52c41a', fontWeight: 'bold' }}
                />
              </Card>
            </Col>
          </Row>
        )}
      </Card>

      <RunEmployees payrunId={id} />
    </div>
  );
}
