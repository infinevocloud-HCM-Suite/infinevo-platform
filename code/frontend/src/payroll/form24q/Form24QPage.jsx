import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import dayjs from 'dayjs';
import {
  Card,
  Row,
  Col,
  Select,
  Radio,
  Button,
  Statistic,
  Alert,
  Space,
  Table,
  Tag,
  Popconfirm,
  Modal,
  Input,
  DatePicker,
  Typography,
  message,
  theme,
} from 'antd';
import {
  PlusOutlined,
  DownloadOutlined,
  CheckCircleOutlined,
  EditOutlined,
  DeleteOutlined,
  ApartmentOutlined,
  LockOutlined,
  UnlockOutlined,
} from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { formatMoney } from '@shared/ui/format.js';
import { currentFy, fyOptions, fyForApi } from '../tax/financialYear';
import { form24QService, saveFile } from './form24QService';
import { ChallanModal } from './ChallanModal';
import { ChallanAllocationDrawer } from './ChallanAllocationDrawer';

const { Text } = Typography;

const QUARTERS = [
  { value: 'Q1', label: 'Q1 Apr–Jun' },
  { value: 'Q2', label: 'Q2 Jul–Sep' },
  { value: 'Q3', label: 'Q3 Oct–Dec' },
  { value: 'Q4', label: 'Q4 Jan–Mar' },
];

const STATUS = {
  FULL: { color: 'green', text: 'Fully allocated' },
  PARTIAL: { color: 'gold', text: 'Partly allocated' },
  UNALLOCATED: { color: 'red', text: 'Not allocated' },
};

/** The quarter today falls in, so the page opens on the one being worked on. */
export function quarterOf(date = dayjs()) {
  const m = date.month() + 1;
  if (m >= 4 && m <= 6) return 'Q1';
  if (m >= 7 && m <= 9) return 'Q2';
  if (m >= 10) return 'Q3';
  return 'Q4';
}

/**
 * Payroll › Quarterly TDS return (W-89): record the quarter's TDS challans, allocate each to the employees whose TDS
 * it paid, check the statement, download it as CSV or as the Form 138 file for the portal, and mark the quarter
 * filed with its token number. In Q4 the year's salary statement (Annexure II) is a CSV too.
 */
export function Form24QPage() {
  const { token: themeToken } = theme.useToken();
  const canManage = useCan('payroll.tax.manage');
  const [fy, setFy] = useState(currentFy());
  const [quarter, setQuarter] = useState(quarterOf());
  const [challans, setChallans] = useState([]);
  const [preview, setPreview] = useState(null);
  const [filed, setFiled] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [allocating, setAllocating] = useState(null);
  const [fileOpen, setFileOpen] = useState(false);
  const [token, setToken] = useState('');
  const [filedOn, setFiledOn] = useState(dayjs());
  const [busy, setBusy] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [list, p, returns] = await Promise.all([
        form24QService.listChallans(fy, quarter),
        form24QService.getAnnexure1Preview(fy, quarter),
        form24QService.returns(fy),
      ]);
      setChallans(list);
      setPreview(p);
      setFiled(returns.find((r) => r.quarter === quarter) || null);
    } catch (err) {
      setError(err?.message || 'Failed to load the quarter');
      setPreview(null);
    } finally {
      setLoading(false);
    }
  }, [fy, quarter]);

  useEffect(() => {
    load();
  }, [load]);

  const download = async (what) => {
    setBusy(what);
    try {
      const base = `TDS_${fyForApi(fy)}_${quarter}`;
      if (what === 'csv') saveFile(await form24QService.exportAnnexure1(fy, quarter, 'CSV'), `${base}_statement.csv`);
      if (what === 'txt') saveFile(await form24QService.exportAnnexure1(fy, quarter, 'TXT'), `${base}_Form138.txt`);
      if (what === 'a2') saveFile(await form24QService.exportAnnexure2(fy), `TDS_${fyForApi(fy)}_salary_statement.csv`);
    } catch (err) {
      message.error(err?.message || 'Failed to download');
    } finally {
      setBusy(null);
    }
  };

  const markFiled = async () => {
    setBusy('file');
    try {
      await form24QService.markFiled(fy, quarter, token.trim(), filedOn?.format('YYYY-MM-DD'));
      message.success(`${quarter} marked filed`);
      setFileOpen(false);
      setToken('');
      load();
    } catch (err) {
      message.error(err?.message || 'Failed to mark the quarter filed');
    } finally {
      setBusy(null);
    }
  };

  const reopen = async () => {
    try {
      await form24QService.reopen(fy, quarter);
      message.success(`${quarter} reopened`);
      load();
    } catch (err) {
      message.error(err?.message || 'Failed to reopen the quarter');
    }
  };

  const remove = async (c) => {
    try {
      await form24QService.deleteChallan(c.id);
      message.success('Challan deleted');
      load();
    } catch (err) {
      message.error(err?.message || 'Failed to delete the challan');
    }
  };

  const problems = preview?.portalFileProblems || [];
  const locked = !!filed;

  return (
    <div style={{ padding: '0 24px 24px' }}>
      <PageHeader
        title="Quarterly TDS return"
        subtitle="TDS on salary: challans, who they paid for, and the Form 138 file for the income-tax portal"
        actions={
          canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={locked}
              onClick={() => {
                setEditing(null);
                setModalOpen(true);
              }}
              data-testid="btn-add-challan"
            >
              Record challan
            </Button>
          )
        }
      />

      <Card variant="borderless" style={{ marginBottom: 16 }}>
        <Space wrap size={16}>
          <Select aria-label="Financial year" value={fy} onChange={setFy} options={fyOptions()} style={{ width: 140 }} />
          <Radio.Group
            value={quarter}
            onChange={(e) => setQuarter(e.target.value)}
            optionType="button"
            options={QUARTERS}
          />
          {filed ? (
            <Space>
              <Tag icon={<LockOutlined />} color="green" data-testid="quarter-filed">
                Filed {dayjs(filed.filedOn).format('DD MMM YYYY')} · token {filed.tokenNumber}
              </Tag>
              {canManage && (
                <Popconfirm
                  title="Reopen this quarter?"
                  description="Its challans and allocations can be changed again, for a correction."
                  onConfirm={reopen}
                >
                  <Button size="small" icon={<UnlockOutlined />} data-testid="btn-reopen">
                    Reopen
                  </Button>
                </Popconfirm>
              )}
            </Space>
          ) : (
            canManage && (
              <Button icon={<CheckCircleOutlined />} onClick={() => setFileOpen(true)} data-testid="btn-mark-filed">
                Mark filed
              </Button>
            )
          )}
        </Space>
      </Card>

      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}

      {preview && (
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col xs={12} md={6}>
            <Card variant="borderless">
              <Statistic title="TDS deducted" value={formatMoney(preview.totalTdsDeducted)} />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card variant="borderless">
              <Statistic title="Covered by challans" value={formatMoney(preview.totalTdsDeposited)} />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card variant="borderless">
              <Statistic
                title="Not yet covered"
                value={formatMoney(preview.unallocatedTds)}
                valueStyle={Number(preview.unallocatedTds) > 0 ? { color: themeToken.colorError } : undefined}
              />
            </Card>
          </Col>
          <Col xs={12} md={6}>
            <Card variant="borderless">
              <Statistic title="Employees without PAN" value={preview.missingPanCount ?? 0} />
            </Card>
          </Col>
        </Row>
      )}

      <Card variant="borderless" title="Challans" style={{ marginBottom: 16 }}>
        <Table
          rowKey="id"
          loading={loading}
          dataSource={challans}
          pagination={false}
          locale={{ emptyText: 'No challan recorded for this quarter' }}
          columns={[
            { title: 'Date', dataIndex: 'tenderDate', key: 'date', width: 120, render: (d) => dayjs(d).format('DD MMM YYYY') },
            { title: 'BSR / serial', key: 'bsr', render: (_, c) => `${c.bsrCode} / ${c.challanSerialNo}` },
            { title: 'Tax', key: 'tax', align: 'right', render: (_, c) => formatMoney(c.allocatableAmount) },
            { title: 'Total paid', key: 'total', align: 'right', render: (_, c) => formatMoney(c.totalAmount) },
            { title: 'Allocated', key: 'alloc', align: 'right', render: (_, c) => formatMoney(c.allocatedAmount) },
            {
              title: 'Status',
              key: 'status',
              render: (_, c) => {
                const s = STATUS[c.allocationStatus] || STATUS.UNALLOCATED;
                return (
                  <Tag color={s.color} data-testid={`status-${c.id}`}>
                    {s.text}
                  </Tag>
                );
              },
            },
            canManage && {
              title: 'Actions',
              key: 'actions',
              width: 260,
              render: (_, c) => (
                  <Space size="small">
                    <Button
                      size="small"
                      icon={<ApartmentOutlined />}
                      onClick={() => setAllocating(c)}
                      data-testid={`btn-allocate-${c.id}`}
                    >
                      Allocate
                    </Button>
                    <Button
                      size="small"
                      icon={<EditOutlined />}
                      disabled={locked}
                      onClick={() => {
                        setEditing(c);
                        setModalOpen(true);
                      }}
                    >
                      Edit
                    </Button>
                    <Popconfirm title="Delete this challan and its allocations?" onConfirm={() => remove(c)} disabled={locked}>
                      <Button size="small" danger icon={<DeleteOutlined />} disabled={locked} />
                    </Popconfirm>
                  </Space>
                ),
            },
          ].filter(Boolean)}
        />
      </Card>

      <Card variant="borderless" title="Download">
        {problems.length > 0 && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Before the portal file can be made"
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }} data-testid="portal-problems">
                {problems.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            }
            action={
              canManage && (
                <Link to="/payroll/settings/tds-deductor">
                  <Button size="small">Deductor details</Button>
                </Link>
              )
            }
          />
        )}
        <Space wrap>
          <Button icon={<DownloadOutlined />} loading={busy === 'csv'} onClick={() => download('csv')} data-testid="btn-csv">
            Statement (CSV)
          </Button>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            loading={busy === 'txt'}
            disabled={problems.length > 0}
            onClick={() => download('txt')}
            data-testid="btn-txt"
          >
            Form 138 file for the portal (.txt)
          </Button>
          {quarter === 'Q4' && (
            <Button icon={<DownloadOutlined />} loading={busy === 'a2'} onClick={() => download('a2')} data-testid="btn-a2">
              Year&apos;s salary statement (CSV)
            </Button>
          )}
        </Space>
        <Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
          Check the .txt file with Protean&apos;s File Validation Utility (FVU 1.2) before uploading it.
        </Text>
      </Card>

      <ChallanModal
        open={modalOpen}
        challan={editing}
        financialYear={fy}
        quarter={quarter}
        onClose={() => setModalOpen(false)}
        onSuccess={load}
      />
      <ChallanAllocationDrawer
        open={!!allocating}
        challan={allocating}
        onClose={() => setAllocating(null)}
        onSaved={load}
      />
      <Modal
        title={`Mark ${quarter} ${fy} filed`}
        open={fileOpen}
        onCancel={() => setFileOpen(false)}
        onOk={markFiled}
        okText="Mark filed"
        okButtonProps={{ disabled: !/^[0-9]{15}$/.test(token.trim()), 'data-testid': 'btn-confirm-filed' }}
        confirmLoading={busy === 'file'}
        destroyOnHidden
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>The 15-digit token number on the filing acknowledgement. The next quarter&apos;s file quotes it.</Text>
          <Input
            aria-label="Token number"
            value={token}
            maxLength={15}
            onChange={(e) => setToken(e.target.value.replace(/\D/g, ''))}
            placeholder="15 digits"
          />
          <DatePicker
            aria-label="Filed on"
            value={filedOn}
            onChange={setFiledOn}
            disabledDate={(d) => d.isAfter(dayjs(), 'day')}
            format="DD MMM YYYY"
          />
        </Space>
      </Modal>
    </div>
  );
}
