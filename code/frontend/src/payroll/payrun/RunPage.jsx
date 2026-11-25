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
  Modal,
  DatePicker,
  Form,
  Dropdown,
  theme,
} from 'antd';
import { successMsg } from '@shared/ui/msgHelper.js';
import { BankFileDrawer } from './BankFileDrawer.jsx';
import { BankFileHistory } from './BankFileHistory.jsx';
import {
  LockOutlined,
  PlayCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  DollarOutlined,
  DownloadOutlined,
  DownOutlined,
  ScheduleOutlined,
  ExclamationCircleOutlined,
  BankOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan } from '@shell/screens';
import { formatDate, formatMoney, formatMonth } from '@shared/ui/format.js';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { payrunService } from './payrunService.js';
import { startPolling, stopPolling, setRun } from './payrunSlice.js';
import { RunEmployees } from './RunEmployees.jsx';

const { Text } = Typography;

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

// The day a run may be marked paid (W-36.2 §4): not in the future, not before the period starts.
export function isPaidOnAllowed(day, run) {
  if (!day) return false;
  if (day.isAfter(dayjs(), 'day')) return false;
  const periodStart = run?.period_start;
  return !(periodStart && day.isBefore(dayjs(periodStart), 'day'));
}

// A load that failed because the run is not there, as against one that failed for any other reason
// (network, 403, 500) - only the first is "not found" (D-83). The shared client sets `status`/`code`.
export function isNotFound(err) {
  return err?.status === 404 || err?.code === 'NOT_FOUND';
}

// Why the worker gave up on a FAILED run (`pay_run.failure_reason`, W-29.2 §3), in either key style.
export function failureReasonOf(run) {
  return run?.failure_reason || run?.failureReason || null;
}

// D-144: entries for the month written after the run was computed (`inputs_since_compute`, run
// detail only). A locked or computed month still takes entries; the run must be computed again,
// and the server refuses approval until it is. Same words as that 409.
export function inputsSinceComputeNotice(run) {
  if (!run || ['APPROVED', 'PAID', 'CANCELLED'].includes(run.status)) return null;
  const n = Number(run.inputs_since_compute ?? run.inputsSinceCompute ?? 0);
  if (!Number.isFinite(n) || n <= 0) return null;
  return `${n} new pay input${n === 1 ? '' : 's'} since this run was computed. Compute again before approving.`;
}

// Who approved: the name the reply carries. Never the raw login id (D-79).
function approverOf(run) {
  return run?.approved_by_name || run?.approvedByName || null;
}

export function RunPage() {
  const { token } = theme.useToken();
  const { id } = useParams();
  const navigate = useNavigate();
  const dispatch = useDispatch();

  const canExecute = useCan('payroll.run.execute');
  const canApproveAction = useCan('payroll.run.approve');
  const canPayAction = useCan('payroll.payslip.publish');
  const canReadStatutory = useCan('payroll.statutory_report.read');
  const canExportBankFile = useCan('payroll.bank_file.export');

  const runFromStore = useSelector((state) => state.payrun?.byId?.[id]);
  const [localRun, setLocalRun] = useState(null);
  const [loading, setLoading] = useState(!runFromStore);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [refreshNotice, setRefreshNotice] = useState(null);
  const [payModalOpen, setPayModalOpen] = useState(false);
  const [paidOn, setPaidOn] = useState(null);
  const [payNotice, setPayNotice] = useState(null);
  const [exportLoading, setExportLoading] = useState(false);
  const [statutoryLoading, setStatutoryLoading] = useState(false);
  const [downloadingBankFile, setDownloadingBankFile] = useState(false);
  const [bankDrawerOpen, setBankDrawerOpen] = useState(false);
  const [bankFilesMade, setBankFilesMade] = useState(0);
  // The error of the last read, kept whole so a 404 can be told from every other failure (D-83).
  const [loadError, setLoadError] = useState(null);

  const run = runFromStore || localRun;

  const fetchRun = useCallback(async () => {
    if (!id) return;
    try {
      const data = await payrunService.get(id);
      dispatch(setRun(data));
      setLocalRun(data);
      setLoadError(null);
      return data;
    } catch (err) {
      setLoadError(err || {});
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

  // One shape for every transition: a 409 means the run moved under us, so re-read it and show
  // the server's message (W-47.2 §5).
  const runAction = async (call, failureText) => {
    setActionLoading(true);
    setActionError(null);
    try {
      const result = await call();
      await fetchRun();
      return result;
    } catch (err) {
      if (err?.status === 409 || err?.code === 'CONFLICT') {
        await fetchRun();
      }
      setActionError(err?.message || failureText);
      return null;
    } finally {
      setActionLoading(false);
    }
  };

  const handleExport = async () => {
    try {
      setExportLoading(true);
      const blob = await payrunService.export(id);
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `payrun-${run?.period || 'export'}.xlsx`);
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setActionError(err?.message || 'Failed to export pay run');
    } finally {
      setExportLoading(false);
    }
  };

  const handleStatutoryDownload = async (type, format) => {
    try {
      setStatutoryLoading(true);
      const isEsi = type === 'ESI';
      const blob = isEsi
        ? await payrunService.downloadEsiReturn(id, format)
        : await payrunService.downloadPtReturn(id, format);
      const ext = format.toLowerCase();
      const filename = `${isEsi ? 'esi' : 'pt'}-return-${run?.period || 'monthly'}.${ext}`;
      const url = window.URL.createObjectURL(new Blob([blob]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.parentNode.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      setActionError(err?.message || 'Failed to download statutory return');
    } finally {
      setStatutoryLoading(false);
    }
  };

  const statutoryMenuItems = [
    {
      key: 'esi-xlsx',
      label: 'ESI Monthly Return (.xlsx)',
      onClick: () => handleStatutoryDownload('ESI', 'XLSX'),
    },
    {
      key: 'esi-csv',
      label: 'ESI Monthly Return (.csv)',
      onClick: () => handleStatutoryDownload('ESI', 'CSV'),
    },
    {
      key: 'pt-xlsx',
      label: 'Professional Tax Return (.xlsx)',
      onClick: () => handleStatutoryDownload('PT', 'XLSX'),
    },
    {
      key: 'pt-csv',
      label: 'Professional Tax Return (.csv)',
      onClick: () => handleStatutoryDownload('PT', 'CSV'),
    },
  ];

  const handleCompute = () => runAction(() => payrunService.compute(id), 'Compute failed');
  // W-73.6 follow-up: a schedule added after the run was created, or dated into this month, lands
  // on the ledger only through this; the first compute then reads it.
  const handleRefreshScheduled = async () => {
    setRefreshNotice(null);
    const result = await runAction(
      () => payrunService.refreshScheduledEarnings(id),
      'Refresh scheduled earnings failed'
    );
    if (result) {
      const written = result.written ?? 0;
      setRefreshNotice(
        written === 0
          ? 'No scheduled earnings were due for this month.'
          : `${written} scheduled earning${written === 1 ? '' : 's'} written to the ledger. Lock and compute the run to include ${written === 1 ? 'it' : 'them'}.`
      );
    }
  };
  const handleLock = () => runAction(() => payrunService.lock(id), 'Lock failed');
  const handleCancel = () => runAction(() => payrunService.cancel(id), 'Cancel failed');
  const handleApprove = () => runAction(() => payrunService.approve(id), 'Approve failed');

  const openPayModal = () => {
    setPaidOn(dayjs());
    setPayNotice(null);
    setPayModalOpen(true);
  };

  const handlePay = async () => {
    if (!isPaidOnAllowed(paidOn, run)) return;
    setPayModalOpen(false);
    const paid = await runAction(() => payrunService.pay(id, paidOn.format('YYYY-MM-DD')), 'Pay failed');
    if (paid) {
      setPayNotice(`Paid on ${paid.paid_on}. ${paid.notified ?? 0} payslip notification(s) sent.`);
    }
  };

  const triggerBankFileDownload = (blob) => {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `bank_salary_file_${run.period}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  };

  const handleDownloadBankFile = async () => {
    setDownloadingBankFile(true);
    setActionError(null);
    try {
      const blob = await payrunService.exportBankFile(id);
      const omitted = blob?.omittedEmployees || 0;
      const fileTotal = blob?.fileTotal != null ? Number(blob.fileTotal) : null;
      const runNetPay = blob?.runNetPay != null ? Number(blob.runNetPay) : null;
      const isShortfall = fileTotal != null && runNetPay != null && fileTotal < runNetPay;

      if (omitted > 0 || isShortfall) {
        Modal.confirm({
          title: 'Bank File Payout Shortfall Warning',
          icon: <ExclamationCircleOutlined style={{ color: token.colorWarning }} />,
          content: (
            <div>
              <p>
                {omitted > 0
                  ? `${omitted} employee(s) are not in the bank file: no bank details, paid by cash or cheque, a blank account number or IFSC, or no net pay.`
                  : 'The total payout in the bank file is less than the pay run net pay.'}
              </p>
              {blob?.omittedEmployeeNumbers?.length > 0 && (
                <p data-testid="bank-file-omitted-numbers">
                  <strong>Left out:</strong> {blob.omittedEmployeeNumbers.join(', ')}
                </p>
              )}
              {fileTotal != null && runNetPay != null && (
                <p>
                  <strong>File Total:</strong> ₹{fileTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  <br />
                  <strong>Run Net Pay:</strong> ₹{runNetPay.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  <br />
                  <span style={{ color: token.colorError }}>
                    Shortfall: ₹{(runNetPay - fileTotal).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </span>
                </p>
              )}
              <p>Do you want to proceed with downloading this bank file?</p>
            </div>
          ),
          okText: 'Download Anyway',
          okType: 'primary',
          cancelText: 'Cancel',
          onOk() {
            triggerBankFileDownload(blob);
          },
        });
      } else {
        triggerBankFileDownload(blob);
        successMsg('Downloaded', 'Bank salary payment file downloaded.');
      }
    } catch (err) {
      setActionError(err?.message || 'Failed to download bank file');
    } finally {
      setDownloadingBankFile(false);
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
    const notFound = !loadError || isNotFound(loadError);
    const retry = () => {
      setLoading(true);
      setActionError(null);
      fetchRun();
    };
    return (
      <div style={{ padding: 24 }}>
        <Alert
          message={notFound ? 'Pay run not found' : 'Could not load the pay run'}
          description={
            notFound
              ? 'The requested pay run does not exist.'
              : loadError?.message || 'Something went wrong'
          }
          type="error"
          showIcon
          action={
            <Space>
              {!notFound && (
                <Button type="primary" icon={<ReloadOutlined />} onClick={retry}>
                  Retry
                </Button>
              )}
              <Button onClick={() => navigate('/payroll/runs')}>Back to runs</Button>
            </Space>
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
  const isRegular = (run.run_type || run.runType || 'REGULAR') === 'REGULAR';
  // DRAFT only: Lock closes the month's inputs, so a later refresh could not land in this run.
  const canRefreshScheduled = canExecute && isRegular && status === 'DRAFT';
  // APPROVED can still be cancelled; PAID never can (W-36.2 §13 decision 9).
  const canCancel = canExecute && (status === 'DRAFT' || status === 'LOCKED' || status === 'APPROVED');
  const canApprove = canApproveAction && status === 'COMPUTED';
  const canPay = canPayAction && status === 'APPROVED';
  const canDownloadBankFile = canExportBankFile && (status === 'APPROVED' || status === 'PAID');

  const progressDone = run.progress_done !== undefined ? run.progress_done : run.progressDone || 0;
  const progressTotal = run.progress_total !== undefined ? run.progress_total : run.progressTotal || 0;
  const percent = progressTotal > 0 ? Math.round((progressDone / progressTotal) * 100) : 0;
  const computeAttempt = run.compute_attempt !== undefined ? run.compute_attempt : run.computeAttempt || 1;

  const totalGross = run.total_gross !== undefined ? run.total_gross : run.totalGross;
  const totalDeductions = run.total_deductions !== undefined ? run.total_deductions : run.totalDeductions;
  const totalNet = run.total_net_pay !== undefined ? run.total_net_pay : run.totalNetPay;
  const isComputed = status === 'COMPUTED' || status === 'APPROVED' || status === 'PAID';
  const canDownloadStatutory = canReadStatutory && isComputed && isRegular;
  const failureReason = status === 'FAILED' ? failureReasonOf(run) : null;
  const approver = approverOf(run);

  return (
    <div className="saas-page-container" style={{ padding: '24px', maxWidth: 1200, margin: '0 auto' }}>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/payroll/runs')}>Pay Runs</a> },
          { title: `${run.period} (${run.run_type || run.runType || 'REGULAR'})` },
        ]}
      />

      <PageHeader
        title={`Pay Run — ${formatMonth(run.period)}`}
        onBack={() => navigate('/payroll/runs')}
        backLabel="Runs"
        actions={
          <Space wrap>
            <Tag color={STATUS_COLOR_MAP[status] || 'default'} style={{ fontSize: 14, padding: '4px 10px' }}>
              {status}
            </Tag>
            <Tag color={run.run_type === 'OFF_CYCLE' || run.runType === 'OFF_CYCLE' ? 'purple' : 'blue'}>
              {run.run_type || run.runType || 'REGULAR'}
            </Tag>
            <Button icon={<ReloadOutlined />} onClick={fetchRun} loading={actionLoading}>
              Refresh
            </Button>

            {isRegular && (
              <Button
                id="btn-refresh-scheduled-earnings"
                icon={<ScheduleOutlined />}
                onClick={handleRefreshScheduled}
                disabled={!canRefreshScheduled}
                loading={actionLoading}
              >
                Refresh scheduled earnings
              </Button>
            )}

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
              title="Approve this pay run?"
              description="Approving freezes the computed figures. The run can no longer be recomputed."
              onConfirm={handleApprove}
              okText="Approve"
              cancelText="Back"
              disabled={!canApprove}
            >
              <Button
                icon={<CheckCircleOutlined />}
                disabled={!canApprove}
                loading={actionLoading}
              >
                Approve
              </Button>
            </Popconfirm>

            <Button
              type="primary"
              icon={<DollarOutlined />}
              disabled={!canPay}
              loading={actionLoading}
              onClick={openPayModal}
            >
              Pay
            </Button>

            {canDownloadBankFile && (
              <Button
                icon={<DownloadOutlined />}
                onClick={handleDownloadBankFile}
                loading={downloadingBankFile}
                data-testid="download-bank-file-btn"
              >
                Download Bank File
              </Button>
            )}
            {canDownloadBankFile && (
              <Button icon={<BankOutlined />} onClick={() => setBankDrawerOpen(true)} data-testid="open-bank-file-drawer">
                Bank file…
              </Button>
            )}

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

            <Button
              icon={<DownloadOutlined />}
              loading={exportLoading}
              onClick={handleExport}
              data-testid="export-payrun-button"
            >
              Export to Excel
            </Button>

            <Dropdown
              menu={{ items: statutoryMenuItems }}
              disabled={!canDownloadStatutory}
              trigger={['click']}
            >
              <Button
                icon={<DownloadOutlined />}
                loading={statutoryLoading}
                disabled={!canDownloadStatutory}
                data-testid="statutory-returns-dropdown"
              >
                Statutory Returns <DownOutlined />
              </Button>
            </Dropdown>
          </Space>
        }
      />

      {status === 'FAILED' && (
        <Alert
          message="Computation failed"
          description={failureReason || 'The server did not record a reason.'}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
        />
      )}

      {inputsSinceComputeNotice(run) && (
        <Alert
          message={inputsSinceComputeNotice(run)}
          type="warning"
          showIcon
          data-testid="inputs-since-compute"
          style={{ marginBottom: 16 }}
        />
      )}

      {refreshNotice && (
        <Alert
          message="Scheduled earnings"
          description={refreshNotice}
          type="info"
          showIcon
          closable
          onClose={() => setRefreshNotice(null)}
          style={{ marginBottom: 16 }}
        />
      )}

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

      {payNotice && (
        <Alert
          message={payNotice}
          type="success"
          showIcon
          closable
          onClose={() => setPayNotice(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Modal
        title="Mark pay run as paid"
        open={payModalOpen}
        onOk={handlePay}
        okText="Mark paid"
        cancelText="Back"
        okButtonProps={{ disabled: !isPaidOnAllowed(paidOn, run) }}
        onCancel={() => setPayModalOpen(false)}
        destroyOnClose
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Paying releases payslips to every included employee and cannot be undone. A paid run can never be cancelled."
        />
        <Form layout="vertical">
          <Form.Item label="Date paid" required>
            <DatePicker
              value={paidOn}
              onChange={setPaidOn}
              disabledDate={(day) => !isPaidOnAllowed(day, run)}
              style={{ width: '100%' }}
            />
          </Form.Item>
        </Form>
      </Modal>

      {isComputing && (
        <Card variant="borderless" style={{ marginBottom: 24, background: token.colorSuccessBg, borderColor: token.colorSuccessBorder }}>
          <Space direction="vertical" style={{ width: '100%' }}>
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

      <Card variant="borderless" style={{ marginBottom: 24 }}>
        <Descriptions column={{ xs: 1, sm: 2, md: 4 }} bordered size="small">
          <Descriptions.Item label="Period">{formatMonth(run.period)}</Descriptions.Item>
          <Descriptions.Item label="Pay Date">
            {formatDate(run.pay_date || run.payDate)}
          </Descriptions.Item>
          <Descriptions.Item label="Included Employees">
            <Text strong style={{ color: token.colorSuccess }}>
              {run.included_count !== undefined ? run.included_count : run.includedCount || 0}
            </Text>
          </Descriptions.Item>
          <Descriptions.Item label="Skipped Employees">
            <Text strong style={{ color: token.colorWarning }}>
              {run.skipped_count !== undefined ? run.skipped_count : run.skippedCount || 0}
            </Text>
          </Descriptions.Item>
          {run.approved_at && (
            <Descriptions.Item label="Approved">
              {formatDate(run.approved_at)}
              {approver ? ` by ${approver}` : ''}
            </Descriptions.Item>
          )}
          {run.paid_on && <Descriptions.Item label="Paid On">{formatDate(run.paid_on)}</Descriptions.Item>}
          {run.notes && (
            <Descriptions.Item label="Notes" span={4}>
              {run.notes}
            </Descriptions.Item>
          )}
        </Descriptions>

        {isComputed && (
          <Row gutter={16} style={{ marginTop: 20 }}>
            <Col xs={24} sm={8}>
              <Card type="inner" variant="borderless" style={{ textAlign: 'center' }}>
                <Statistic
                  title="Total Gross"
                  value={formatMoney(totalGross)}
                  valueStyle={{ color: token.colorPrimary }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card type="inner" variant="borderless" style={{ textAlign: 'center' }}>
                <Statistic
                  title="Total Deductions"
                  value={formatMoney(totalDeductions)}
                  valueStyle={{ color: token.colorError }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card type="inner" variant="borderless" style={{ textAlign: 'center' }}>
                <Statistic
                  title="Total Net Pay"
                  value={formatMoney(totalNet)}
                  valueStyle={{ color: token.colorSuccess }}
                />
              </Card>
            </Col>
          </Row>
        )}
      </Card>

      {canDownloadBankFile && <BankFileHistory payrunId={id} refreshKey={bankFilesMade} />}

      <RunEmployees payrunId={id} />

      {canDownloadBankFile && (
        <BankFileDrawer
          open={bankDrawerOpen}
          payrunId={id}
          onClose={() => setBankDrawerOpen(false)}
          onGenerated={() => setBankFilesMade((n) => n + 1)}
        />
      )}
    </div>
  );
}
