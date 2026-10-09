import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
import {
  Card,
  Table,
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  DatePicker,
  Space,
  Tag,
  Typography,
  Modal,
  Tooltip,
} from 'antd';
import { PlusOutlined, PauseCircleOutlined, PlayCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Formik } from 'formik';
import * as Yup from 'yup';
import dayjs from 'dayjs';
import { useCan } from '@shell/screens';
import { fetchActiveComponents } from './salarySlice.js';
import { scheduledEarningService } from './scheduledEarningService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

const MONTH_FORMAT = 'YYYY-MM';
const MIN_INSTALMENTS = 1;
const MAX_INSTALMENTS = 12;

const STATUS_COLOURS = {
  SCHEDULED: 'blue',
  PAUSED: 'gold',
  CANCELLED: 'default',
  PAID: 'green',
};

/** Only an earning flagged Scheduled in the component drawer can be planned (W-73.6 §2). */
export function scheduledComponents(earnings) {
  return (earnings || []).filter((e) => Boolean(e.scheduledEarning));
}

/**
 * The add form's rules (spec §4): a Scheduled component, a positive amount, a first month that is
 * {@code currentMonth} or later, and 1-12 instalments. {@code currentMonth} is `YYYY-MM`.
 */
export function buildScheduledEarningSchema(currentMonth) {
  return Yup.object().shape({
    componentId: Yup.string().required('Choose a scheduled earning component'),
    amount: Yup.string()
      .required('Amount is required')
      .matches(/^\d+(\.\d{1,2})?$/, 'Must be a valid number with at most 2 decimal places')
      .test('positive', 'Amount must be greater than zero', (v) => Number(v) > 0)
      .test('splittable', 'Amount must be at least 0.01 per instalment', function splittable(v) {
        const n = Number(this.parent.instalments);
        if (!v || !Number.isInteger(n) || n < MIN_INSTALMENTS) return true;
        // Compare in paise so 0.12 over 12 is not lost to floating point.
        return Math.round(Number(v) * 100) >= n;
      }),
    firstPeriod: Yup.string()
      .required('First month is required')
      .matches(/^\d{4}-\d{2}$/, 'First month must be a month')
      .test('not-past', `First month must be ${currentMonth} or later`, (v) => !v || v >= currentMonth),
    instalments: Yup.number()
      .required('Instalments are required')
      .integer('Instalments must be a whole number')
      .min(MIN_INSTALMENTS, `At least ${MIN_INSTALMENTS} instalment`)
      .max(MAX_INSTALMENTS, `At most ${MAX_INSTALMENTS} instalments`),
    reason: Yup.string().max(255, 'Reason cannot exceed 255 characters').nullable(),
  });
}

function AddScheduledEarningDrawer({ open, onClose, onSaved, employeeId, components, currentMonth }) {
  const schema = buildScheduledEarningSchema(currentMonth);
  const options = components.map((c) => ({
    value: c.id,
    label: `${c.name || c.code}${c.name && c.code ? ` (${c.code})` : ''}`,
  }));

  const handleSubmit = async (values, { setSubmitting }) => {
    try {
      await scheduledEarningService.create(employeeId, {
        componentId: values.componentId,
        amount: values.amount,
        firstPeriod: values.firstPeriod,
        instalments: values.instalments,
        reason: values.reason || null,
      });
      await successMsg('Scheduled', 'The earning has been scheduled.');
      onSaved?.();
      onClose?.();
    } catch (err) {
      errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer title="Schedule an earning" width={480} open={open} onClose={onClose} destroyOnClose>
      <Formik
        initialValues={{
          componentId: components.length === 1 ? components[0].id : undefined,
          amount: '',
          firstPeriod: dayjs().add(1, 'month').format(MONTH_FORMAT),
          instalments: 1,
          reason: '',
        }}
        validationSchema={schema}
        onSubmit={handleSubmit}
      >
        {({
          values,
          errors,
          touched,
          handleChange,
          handleBlur,
          handleSubmit: formikSubmit,
          isSubmitting,
          setFieldValue,
          setFieldTouched,
        }) => {
          const fieldError = (name) => (touched[name] && errors[name] ? errors[name] : undefined);
          return (
            <Form layout="vertical" onFinish={formikSubmit}>
              <Form.Item
                label="Component"
                required
                validateStatus={fieldError('componentId') ? 'error' : ''}
                help={fieldError('componentId')}
              >
                <Select
                  aria-label="Component"
                  placeholder={options.length ? 'Choose a scheduled component' : 'No component is flagged Scheduled'}
                  value={values.componentId}
                  options={options}
                  disabled={!options.length}
                  onChange={(val) => setFieldValue('componentId', val)}
                  onBlur={() => setFieldTouched('componentId', true)}
                />
              </Form.Item>
              <Form.Item
                label="Amount"
                required
                validateStatus={fieldError('amount') ? 'error' : ''}
                help={fieldError('amount')}
              >
                <Input
                  name="amount"
                  aria-label="Amount"
                  value={values.amount}
                  placeholder="Total amount, split across the instalments"
                  onChange={handleChange}
                  onBlur={handleBlur}
                />
              </Form.Item>
              <Form.Item
                label="First month"
                required
                validateStatus={fieldError('firstPeriod') ? 'error' : ''}
                help={fieldError('firstPeriod')}
              >
                <DatePicker
                  aria-label="First month"
                  picker="month"
                  format={MONTH_FORMAT}
                  allowClear={false}
                  value={values.firstPeriod ? dayjs(values.firstPeriod, MONTH_FORMAT) : null}
                  disabledDate={(d) => d && d.format(MONTH_FORMAT) < currentMonth}
                  onChange={(d) => {
                    setFieldValue('firstPeriod', d ? d.format(MONTH_FORMAT) : '');
                    setFieldTouched('firstPeriod', true, false);
                  }}
                />
              </Form.Item>
              <Form.Item
                label="Instalments"
                required
                validateStatus={fieldError('instalments') ? 'error' : ''}
                help={fieldError('instalments')}
              >
                <InputNumber
                  aria-label="Instalments"
                  min={MIN_INSTALMENTS}
                  max={MAX_INSTALMENTS}
                  precision={0}
                  value={values.instalments}
                  onChange={(val) => setFieldValue('instalments', val)}
                  onBlur={() => setFieldTouched('instalments', true)}
                />
                <Text type="secondary" style={{ marginLeft: 12 }}>
                  One pay-input line per month; the last instalment takes the rounding.
                </Text>
              </Form.Item>
              <Form.Item
                label="Reason"
                validateStatus={fieldError('reason') ? 'error' : ''}
                help={fieldError('reason')}
              >
                <Input.TextArea
                  name="reason"
                  aria-label="Reason"
                  rows={2}
                  value={values.reason}
                  onChange={handleChange}
                  onBlur={handleBlur}
                />
              </Form.Item>
              <div style={{ textAlign: 'right' }}>
                <Space>
                  <Button onClick={onClose} disabled={isSubmitting}>
                    Cancel
                  </Button>
                  <Button
                    id="btn-save-scheduled-earning"
                    type="primary"
                    htmlType="submit"
                    loading={isSubmitting}
                    disabled={!options.length}
                  >
                    Schedule
                  </Button>
                </Space>
              </div>
            </Form>
          );
        }}
      </Formik>
    </Drawer>
  );
}

AddScheduledEarningDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSaved: PropTypes.func,
  employeeId: PropTypes.string.isRequired,
  components: PropTypes.array.isRequired,
  currentMonth: PropTypes.string.isRequired,
};

/**
 * The employee Salary tab's Scheduled earnings panel (W-73.6 §5): the schedules with the pay input
 * each paid instalment became, an add drawer, and pause / resume / cancel.
 */
export function ScheduledEarningsPanel({ employeeId }) {
  const dispatch = useDispatch();
  const canRead = useCan('payroll.structure.read');
  const canManage = useCan('payroll.structure.manage');
  const earnings = useSelector((state) => state.salary?.components?.earnings) || [];
  const loadedAt = useSelector((state) => state.salary?.loadedAt);
  const components = scheduledComponents(earnings);
  const currentMonth = dayjs().format(MONTH_FORMAT);

  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [addOpen, setAddOpen] = useState(false);

  const load = useCallback(async () => {
    if (!employeeId || !canRead) return;
    setLoading(true);
    try {
      const data = await scheduledEarningService.list(employeeId);
      setRows(Array.isArray(data) ? data : []);
    } catch (err) {
      errorMsg(err);
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [employeeId, canRead]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (canRead && !loadedAt) dispatch(fetchActiveComponents());
  }, [canRead, loadedAt, dispatch]);

  if (!canRead) return null;

  const act = (label, fn) => async () => {
    try {
      await fn();
      await successMsg(label, `The scheduled earning has been ${label.toLowerCase()}.`);
      load();
    } catch (err) {
      errorMsg(err);
    }
  };

  const confirmCancel = (record) => {
    Modal.confirm({
      title: 'Cancel scheduled earning',
      content: `Stop the remaining ${record.instalments - record.paidInstalments} instalment(s) of ${record.componentName || record.componentCode}? Instalments already written stay as pay inputs.`,
      okText: 'Cancel schedule',
      okType: 'danger',
      onOk: act('Cancelled', () => scheduledEarningService.cancel(record.id)),
    });
  };

  const columns = [
    {
      title: 'Component',
      key: 'component',
      render: (_, r) => (
        <span>
          {r.componentName || r.componentCode}
          {r.componentCode && r.componentName ? <Text type="secondary"> ({r.componentCode})</Text> : null}
        </span>
      ),
    },
    { title: 'Amount', dataIndex: 'amount', key: 'amount', width: 120, render: (v) => (v != null ? String(v) : '-') },
    { title: 'First month', dataIndex: 'firstPeriod', key: 'firstPeriod', width: 110 },
    {
      title: 'Instalments',
      key: 'instalments',
      width: 110,
      render: (_, r) => `${r.paidInstalments} / ${r.instalments}`,
    },
    { title: 'Next month', dataIndex: 'nextPeriod', key: 'nextPeriod', width: 110, render: (v) => v || '-' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s, r) => (
        <Tooltip title={r.statusReason || undefined}>
          <Tag color={STATUS_COLOURS[s] || 'default'}>{s}</Tag>
        </Tooltip>
      ),
    },
    { title: 'Reason', dataIndex: 'reason', key: 'reason', render: (v) => v || '-' },
    {
      title: 'Pay inputs',
      dataIndex: 'payInputIds',
      key: 'payInputIds',
      render: (ids) =>
        ids && ids.length ? (
          <Space direction="vertical" size={0}>
            {ids.map((id) => (
              <Text key={id} code style={{ fontSize: 11 }}>
                {id}
              </Text>
            ))}
          </Space>
        ) : (
          '-'
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 230,
      render: (_, r) => (
        <Space>
          {r.status === 'SCHEDULED' && (
            <Button
              type="text"
              size="small"
              icon={<PauseCircleOutlined />}
              disabled={!canManage}
              onClick={act('Paused', () => scheduledEarningService.pause(r.id))}
            >
              Pause
            </Button>
          )}
          {r.status === 'PAUSED' && (
            <Button
              type="text"
              size="small"
              icon={<PlayCircleOutlined />}
              disabled={!canManage}
              onClick={act('Resumed', () => scheduledEarningService.resume(r.id))}
            >
              Resume
            </Button>
          )}
          {(r.status === 'SCHEDULED' || r.status === 'PAUSED') && (
            <Button
              type="text"
              danger
              size="small"
              icon={<CloseCircleOutlined />}
              disabled={!canManage}
              onClick={() => confirmCancel(r)}
            >
              Cancel
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={<Title level={5} style={{ margin: 0 }}>Scheduled earnings</Title>}
      extra={
        canManage && (
          <Button
            id="btn-add-scheduled-earning"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setAddOpen(true)}
          >
            Schedule earning
          </Button>
        )
      }
      style={{ marginBottom: 24 }}
    >
      <Table
        dataSource={rows}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={false}
        size="small"
        locale={{ emptyText: 'No earning is scheduled for this employee' }}
      />
      {addOpen && (
        <AddScheduledEarningDrawer
          open={addOpen}
          onClose={() => setAddOpen(false)}
          onSaved={load}
          employeeId={employeeId}
          components={components}
          currentMonth={currentMonth}
        />
      )}
    </Card>
  );
}

ScheduledEarningsPanel.propTypes = {
  employeeId: PropTypes.string.isRequired,
};
