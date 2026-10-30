import { useState, useEffect, useRef, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
import { Link } from 'react-router-dom';
import {
  Drawer,
  Form,
  Input,
  DatePicker,
  Select,
  Button,
  Row,
  Col,
  Switch,
  Checkbox,
  Alert,
  Typography,
  Card,
  Descriptions,
  Table,
  Tag,
  Space,
  Spin,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan } from '@shell/screens';
import { fetchActiveComponents } from './salarySlice.js';
import { salaryService } from './salaryService.js';
import { statutoryProfileService } from './statutoryProfileService.js';
import { statutoryService } from '../settings/statutoryService.js';
import { buildDefaultRows, canBalance, pickBalancingEarning, rowFor } from './salaryDefaults.js';
import {
  statutoryFieldRules,
  esiCeilingWarning,
  STATUTORY_FIELD_KEYS,
  UAN_MAX_LENGTH,
  PF_ACCOUNT_MAX_LENGTH,
  ESI_NUMBER_MAX_LENGTH,
} from './statutoryRules.js';
import { statutoryName } from './statutoryLabels.js';
import { isDecimal, isPositiveDecimal } from './decimal.js';
import { StatutoryContributionsTable } from './StatutoryContributionsTable.jsx';
import { successMsg } from '@shared/ui/msgHelper.js';
import { EMPTY, formatMoney } from '@shared/ui/format.js';

const { TextArea } = Input;
const { Text, Title } = Typography;

const DECIMAL_REGEX = /^\d+(\.\d{1,4})?$/;
const PREVIEW_DEBOUNCE_MS = 400;

const NO_ROWS = [];
const NO_COMPONENTS = { earnings: NO_ROWS, benefits: NO_ROWS, reimbursements: NO_ROWS };

const EMPTY_PROFILE = {
  eligibleForPf: false,
  eligibleForEps: false,
  contributesEpsOnHigherWages: false,
  eligibleForPt: false,
  eligibleForEsi: false,
  pfAccountNumber: '',
  uan: '',
  esiNumber: '',
};

function versionRow(item) {
  return {
    componentId: item.componentId,
    calculationType: item.calculationType || 'FLAT',
    value: item.value != null ? String(item.value) : '',
    percentageOf: item.percentageOf || null,
    enabled: item.enabled ?? true,
  };
}

function itemPayload(item) {
  return {
    componentId: item.componentId,
    calculationType: item.calculationType,
    value: item.value,
    percentageOf: item.calculationType === 'PERCENTAGE' ? item.percentageOf : null,
    enabled: item.enabled ?? true,
  };
}

/** The index of the one balancing row: the first earning row carrying the balancing earning. */
function balancingIndex(earnings, balancingId) {
  if (!balancingId) return -1;
  return (earnings || []).findIndex((row) => row?.componentId === balancingId);
}

/** `SalaryVersionRequest` from the form, with `balancing` on the one balancing earning (W-75.1). */
function salaryPayload(values, balancingId) {
  const index = balancingIndex(values.earnings, balancingId);
  return {
    annualCtc: values.annualCtc,
    effectiveFrom: values.effectiveFrom ? dayjs(values.effectiveFrom).format('YYYY-MM-DD') : null,
    notes: values.notes || null,
    earnings: (values.earnings || []).map((item, i) =>
      i === index
        ? { ...itemPayload({ ...item, calculationType: 'FLAT', value: item.value || '0' }), balancing: true }
        : { ...itemPayload(item), balancing: false }
    ),
    benefits: (values.benefits || []).map(itemPayload),
    reimbursements: (values.reimbursements || []).map(itemPayload),
  };
}

/** `StatutoryProfileRequest`: the drawer's ticks and numbers, LWF and director kept as loaded. */
function profilePayload(statutory = {}, kept = {}) {
  const trimmed = (value) => (value && String(value).trim() ? String(value).trim() : null);
  return {
    eligibleForPf: Boolean(statutory.eligibleForPf),
    eligibleForPt: Boolean(statutory.eligibleForPt),
    eligibleForLwf: Boolean(kept.eligibleForLwf),
    eligibleForEsi: Boolean(statutory.eligibleForEsi),
    eligibleForEps: Boolean(statutory.eligibleForEps),
    contributesEpsOnHigherWages: Boolean(statutory.contributesEpsOnHigherWages),
    director: Boolean(kept.director),
    pfAccountNumber: trimmed(statutory.pfAccountNumber),
    uan: trimmed(statutory.uan),
    esiNumber: trimmed(statutory.esiNumber),
  };
}

/** True when every row is complete enough for the server to resolve it. */
function previewReady(values, balancingId) {
  if (!isPositiveDecimal(values.annualCtc) || !values.effectiveFrom) return false;
  const index = balancingIndex(values.earnings, balancingId);
  const complete = (item, isBalancing) =>
    item &&
    item.componentId &&
    (isBalancing ||
      (isDecimal(item.value) && (item.calculationType !== 'PERCENTAGE' || Boolean(item.percentageOf))));
  return (
    (values.earnings || []).every((item, i) => complete(item, i === index)) &&
    (values.benefits || []).every((item) => complete(item, false)) &&
    (values.reimbursements || []).every((item) => complete(item, false))
  );
}

/** The form path a server field key belongs under, or null when it names no field on the drawer. */
function fieldPath(key, values) {
  if (STATUTORY_FIELD_KEYS.includes(key)) return ['statutory', key];
  if (key === 'annualCtc' || key === 'effectiveFrom' || key === 'notes') return [key];
  const match = /^(earnings|benefits|reimbursements)\[(\d+)\]\.(\w+)$/.exec(key);
  if (!match) return null;
  const [, list, rawIndex, field] = match;
  const index = Number(rawIndex);
  const row = values?.[list]?.[index];
  if (!row) return null;
  if (field === 'componentId') return [list, index, 'componentId'];
  if (field === 'percentageOf' && row.calculationType === 'PERCENTAGE') return [list, index, 'percentageOf'];
  return [list, index, 'value'];
}

/** The balancing row's value cell: read-only, the server's figure from the preview. */
function BalancingAmount({ amount }) {
  return (
    <div data-testid="balancing-amount" style={{ lineHeight: '32px' }}>
      <Tag color="purple">Balances to CTC</Tag>
      <Text>{amount != null ? `${formatMoney(amount)} / month` : EMPTY}</Text>
    </div>
  );
}

BalancingAmount.propTypes = {
  amount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
};

/**
 * Set up, revise or edit an employee's salary in one drawer: CTC and date, earnings pre-filled from
 * the company catalogue with one balancing earning the server computes, the statutory ticks and
 * numbers, and a live summary from `POST …/salary/preview` (W-75.2).
 *
 * Ports the legacy salary form's live statutory figures (legacy/Payroll-Fend-react/src/pages/mainPages/
 * employee/salaryDetails.js:241-290) and its balancing allowance (salaryDetails.js:392-396), but every
 * amount here is the server's: the drawer sums nothing.
 */
export function SalarySetupDrawer({
  open,
  onClose,
  onSuccess,
  onProfileSaved,
  employeeId,
  mode = 'create',
  initialValues = null,
  dateOfJoining = null,
}) {
  const dispatch = useDispatch();
  const canManage = useCan('payroll.salary.manage');
  const [form] = Form.useForm();

  const [submitting, setSubmitting] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [balancingId, setBalancingIdState] = useState(null);
  const [keptProfile, setKeptProfile] = useState({ eligibleForLwf: false, director: false });
  const [companyEpf, setCompanyEpf] = useState(null);
  const [companyEsi, setCompanyEsi] = useState(null);
  // 'loading' | 'loaded' | 'error'. Save waits for 'loaded' so it can never PUT an empty profile over a real one.
  const [profileState, setProfileState] = useState('loading');

  const [preview, setPreview] = useState(null);
  const [previewError, setPreviewError] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewPending, setPreviewPending] = useState(false);

  const balancingRef = useRef(null);
  const keptRef = useRef(keptProfile);
  const initialisedRef = useRef(false);
  const timerRef = useRef(null);
  const abortRef = useRef(null);
  const seqRef = useRef(0);
  const serverErrorPathsRef = useRef([]);
  const runPreviewRef = useRef(null);

  const components = useSelector((state) => state.salary?.components);
  const componentsLoadedAt = useSelector((state) => state.salary?.loadedAt);
  const componentsError = useSelector((state) => state.salary?.error);
  const catalogue = components || NO_COMPONENTS;
  const earningCatalogue = catalogue.earnings || NO_ROWS;

  const earningsValue = Form.useWatch('earnings', form);
  const epfTicked = Form.useWatch(['statutory', 'eligibleForPf'], form);
  const epsTicked = Form.useWatch(['statutory', 'eligibleForEps'], form);
  const esiTicked = Form.useWatch(['statutory', 'eligibleForEsi'], form);

  const setBalancingId = useCallback((id) => {
    balancingRef.current = id;
    setBalancingIdState(id);
  }, []);

  const clearServerErrors = useCallback(() => {
    const paths = serverErrorPathsRef.current;
    serverErrorPathsRef.current = [];
    if (paths.length) form.setFields(paths.map((name) => ({ name, errors: [] })));
  }, [form]);

  /** Puts each server field error under its field; returns the messages that name no field. */
  const applyServerErrors = useCallback(
    (err) => {
      clearServerErrors();
      const values = form.getFieldsValue(true);
      const unplaced = [];
      const placed = [];
      Object.entries(err?.fieldErrors || {}).forEach(([key, message]) => {
        const name = fieldPath(key, values);
        if (name) {
          placed.push({ name, errors: [message] });
        } else {
          unplaced.push(message);
        }
      });
      if (placed.length) {
        form.setFields(placed);
        serverErrorPathsRef.current = placed.map((p) => p.name);
      }
      return unplaced;
    },
    [form, clearServerErrors]
  );

  const runPreview = useCallback(async () => {
    setPreviewPending(false);
    abortRef.current?.abort();
    const seq = ++seqRef.current;
    const values = form.getFieldsValue(true);
    const balancing = balancingRef.current;

    if (!previewReady(values, balancing)) {
      clearServerErrors();
      setPreview(null);
      setPreviewError(null);
      setPreviewLoading(false);
      return;
    }

    const controller = new AbortController();
    abortRef.current = controller;
    setPreviewLoading(true);
    try {
      const body = {
        ...salaryPayload(values, balancing),
        statutoryProfile: profilePayload(values.statutory, keptRef.current),
      };
      const data = await salaryService.preview(employeeId, body, { signal: controller.signal });
      if (seq !== seqRef.current) return;
      clearServerErrors();
      setPreview(data);
      setPreviewError(null);
    } catch (err) {
      if (seq !== seqRef.current) return;
      setPreview(null);
      setPreviewError({ ...err, unplaced: applyServerErrors(err) });
    } finally {
      if (seq === seqRef.current) setPreviewLoading(false);
    }
  }, [employeeId, form, clearServerErrors, applyServerErrors]);

  runPreviewRef.current = runPreview;

  /** Debounces a preview: 400 ms after the last change; the call before it is cancelled. */
  const schedulePreview = useCallback(() => {
    if (!canManage) return;
    clearTimeout(timerRef.current);
    setPreviewPending(true);
    timerRef.current = setTimeout(() => runPreviewRef.current?.(), PREVIEW_DEBOUNCE_MS);
  }, [canManage]);

  useEffect(
    () => () => {
      clearTimeout(timerRef.current);
      abortRef.current?.abort();
    },
    []
  );

  useEffect(() => {
    if (open) dispatch(fetchActiveComponents());
  }, [open, dispatch]);

  // Basics first, so the CTC can be typed while the catalogue loads.
  useEffect(() => {
    if (!open) {
      initialisedRef.current = false;
      return;
    }
    setSaveError(null);
    let effectiveFrom = dayjs();
    if (mode === 'edit' && initialValues?.effectiveFrom) effectiveFrom = dayjs(initialValues.effectiveFrom);
    if (mode === 'create' && dateOfJoining) effectiveFrom = dayjs(dateOfJoining);
    form.setFieldsValue({
      annualCtc: initialValues?.annualCtc != null ? String(initialValues.annualCtc) : '',
      effectiveFrom,
      notes: initialValues?.notes || '',
    });
  }, [open, mode, initialValues, dateOfJoining, form]);

  // Rows, once the catalogue is in.
  useEffect(() => {
    if (!open || initialisedRef.current) return;
    if (!componentsLoadedAt && !componentsError) return;
    initialisedRef.current = true;

    let earnings;
    let balancing;
    if (initialValues) {
      earnings = (initialValues.earnings || []).map(versionRow);
      const pick = pickBalancingEarning(earningCatalogue);
      balancing = pick ? pick.id : null;
      if (pick) {
        const index = earnings.findIndex((row) => row.componentId === pick.id);
        if (index >= 0) {
          earnings[index] = { ...rowFor(pick, true), enabled: earnings[index].enabled };
        } else {
          earnings.push(rowFor(pick, true));
        }
      }
    } else {
      const defaults = buildDefaultRows(earningCatalogue);
      earnings = defaults.rows;
      balancing = defaults.balancingId;
    }

    form.setFieldsValue({
      earnings,
      benefits: (initialValues?.benefits || []).map(versionRow),
      reimbursements: (initialValues?.reimbursements || []).map(versionRow),
    });
    setBalancingId(balancing);
    schedulePreview();
  }, [
    open,
    componentsLoadedAt,
    componentsError,
    initialValues,
    earningCatalogue,
    form,
    setBalancingId,
    schedulePreview,
  ]);

  // The employee's statutory profile, and the company's EPF and ESI switches.
  useEffect(() => {
    if (!open || !employeeId) return undefined;
    let live = true;
    setProfileState('loading');
    statutoryProfileService
      .get(employeeId)
      .then((data) => {
        if (!live) return;
        // The server answers a default profile when none is stored, so no data is a failed read.
        if (!data) {
          setProfileState('error');
          return;
        }
        const kept = { eligibleForLwf: Boolean(data.eligibleForLwf), director: Boolean(data.director) };
        keptRef.current = kept;
        setKeptProfile(kept);
        form.setFieldsValue({
          statutory: {
            eligibleForPf: Boolean(data.eligibleForPf),
            eligibleForEps: Boolean(data.eligibleForEps),
            contributesEpsOnHigherWages: Boolean(data.contributesEpsOnHigherWages),
            eligibleForPt: Boolean(data.eligibleForPt),
            eligibleForEsi: Boolean(data.eligibleForEsi),
            pfAccountNumber: data.pfAccountNumber || '',
            uan: data.uan || '',
            esiNumber: data.esiNumber || '',
          },
        });
        setProfileState('loaded');
        schedulePreview();
      })
      .catch(() => {
        if (live) setProfileState('error');
      });

    Promise.allSettled([statutoryService.get('epf'), statutoryService.get('esi')]).then(([epf, esi]) => {
      if (!live) return;
      setCompanyEpf(epf.status === 'fulfilled' ? epf.value : null);
      setCompanyEsi(esi.status === 'fulfilled' ? esi.value : null);
    });
    return () => {
      live = false;
    };
  }, [open, employeeId, form, schedulePreview]);

  const handleBalancingChange = (id) => {
    const nextId = id || null;
    if (nextId) {
      const earnings = [...(form.getFieldValue('earnings') || [])];
      const earning = earningCatalogue.find((e) => e.id === nextId);
      const index = earnings.findIndex((row) => row?.componentId === nextId);
      if (index >= 0) {
        earnings[index] = { ...earnings[index], calculationType: 'FLAT', value: earnings[index].value || '0' };
      } else if (earning) {
        earnings.push(rowFor(earning, true));
      }
      form.setFieldsValue({ earnings });
    }
    setBalancingId(nextId);
    schedulePreview();
  };

  const handleSave = async () => {
    try {
      await form.validateFields();
    } catch {
      return;
    }
    const values = form.getFieldsValue(true);
    setSubmitting(true);
    setSaveError(null);

    // Statutory profile first: the salary's statutory lines are derived from it.
    try {
      await statutoryProfileService.save(employeeId, profilePayload(values.statutory, keptRef.current));
      if (onProfileSaved) onProfileSaved();
    } catch (err) {
      setSaveError({ message: err?.message || 'Failed to save statutory details', unplaced: applyServerErrors(err) });
      setSubmitting(false);
      return;
    }

    // The profile is valid on its own: it stays saved if the salary is refused, and the drawer stays open.
    try {
      const payload = salaryPayload(values, balancingRef.current);
      let result;
      if (mode === 'revise') {
        result = await salaryService.revise(employeeId, payload);
      } else if (mode === 'edit') {
        result = await salaryService.update(employeeId, initialValues.id, payload);
      } else {
        result = await salaryService.create(employeeId, payload);
      }
      setSubmitting(false);
      await successMsg(
        'Success',
        mode === 'revise'
          ? 'Salary revision created'
          : mode === 'edit'
            ? 'Salary version updated'
            : 'Salary structure created'
      );
      if (onSuccess) onSuccess(result);
      onClose();
    } catch (err) {
      setSaveError({ message: err?.message || 'Failed to save salary structure', unplaced: applyServerErrors(err) });
      setSubmitting(false);
    }
  };

  const fieldRules = statutoryFieldRules((name) => form.getFieldValue(['statutory', name]));
  const balancingRow = balancingIndex(earningsValue, balancingId);
  const balancingMonthly =
    preview?.earnings?.find((e) => e.componentId === balancingId)?.monthlyAmount ?? null;
  const esiWarning = esiCeilingWarning(preview?.grossMonthly, esiTicked, companyEsi?.wage_ceiling);
  const saveBlocked = previewPending || previewLoading || Boolean(previewError) || profileState !== 'loaded';

  const renderEarnings = () => (
    <Card
      size="small"
      title="Earnings"
      style={{ marginBottom: 16 }}
      extra={
        <Space>
          <Text type="secondary">Balancing earning</Text>
          <Select
            id="select-balancing-earning"
            aria-label="Balancing earning"
            style={{ width: 220 }}
            allowClear
            placeholder="Pick the earning that balances to CTC"
            value={balancingId || undefined}
            onChange={handleBalancingChange}
            options={earningCatalogue.filter(canBalance).map((e) => ({ value: e.id, label: e.name }))}
          />
        </Space>
      }
    >
      {!balancingId && earningCatalogue.length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="No Special or Fixed allowance is active: pick the earning that balances to CTC, or enter every amount."
        />
      )}
      <Form.List name="earnings">
        {(fields, { remove }) => (
          <div>
            {fields.length === 0 && (
              <Text type="secondary" style={{ fontStyle: 'italic' }}>
                No earnings allocated.
              </Text>
            )}
            {fields.map(({ key, name, ...restField }) => {
              const row = earningsValue?.[name] || {};
              const isBalancing = name === balancingRow;
              const isPercentage = !isBalancing && row.calculationType === 'PERCENTAGE';
              return (
                <Row key={key} gutter={8} align="top" style={{ marginBottom: 12 }} data-testid={`earning-row-${name}`}>
                  <Col span={7}>
                    <Form.Item
                      {...restField}
                      name={[name, 'componentId']}
                      rules={[{ required: true, message: 'Select component' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select placeholder="Component" disabled={isBalancing}>
                        {earningCatalogue.map((comp) => (
                          <Select.Option key={comp.id} value={comp.id}>
                            {comp.name} ({comp.code})
                          </Select.Option>
                        ))}
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item
                      {...restField}
                      name={[name, 'calculationType']}
                      rules={[{ required: true, message: 'Type' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select placeholder="Type" disabled={isBalancing}>
                        <Select.Option value="FLAT">Flat</Select.Option>
                        <Select.Option value="PERCENTAGE">%</Select.Option>
                      </Select>
                    </Form.Item>
                  </Col>
                  {isBalancing ? (
                    <Col span={9}>
                      <Form.Item {...restField} name={[name, 'value']} style={{ marginBottom: 0 }}>
                        <BalancingAmount amount={balancingMonthly} />
                      </Form.Item>
                    </Col>
                  ) : (
                    <>
                      <Col span={5}>
                        <Form.Item
                          {...restField}
                          name={[name, 'value']}
                          rules={[
                            { required: true, message: 'Value' },
                            { pattern: DECIMAL_REGEX, message: 'Up to 4 decimals' },
                          ]}
                          style={{ marginBottom: 0 }}
                        >
                          <Input placeholder={isPercentage ? '%' : 'Monthly amount'} />
                        </Form.Item>
                      </Col>
                      <Col span={4}>
                        {isPercentage ? (
                          <Form.Item
                            {...restField}
                            name={[name, 'percentageOf']}
                            rules={[{ required: true, message: 'Base' }]}
                            style={{ marginBottom: 0 }}
                          >
                            <Select placeholder="% of">
                              <Select.Option value="BASIC">Basic</Select.Option>
                              <Select.Option value="CTC">CTC</Select.Option>
                              <Select.Option value="GROSS">Gross</Select.Option>
                            </Select>
                          </Form.Item>
                        ) : (
                          <div />
                        )}
                      </Col>
                    </>
                  )}
                  <Col span={2}>
                    <Form.Item
                      {...restField}
                      name={[name, 'enabled']}
                      valuePropName="checked"
                      style={{ marginBottom: 0 }}
                    >
                      <Switch size="small" />
                    </Form.Item>
                  </Col>
                  <Col span={2}>
                    <Button
                      type="text"
                      danger
                      aria-label="Remove earning"
                      icon={<DeleteOutlined />}
                      onClick={() => {
                        if (isBalancing) setBalancingId(null);
                        remove(name);
                        schedulePreview();
                      }}
                    />
                  </Col>
                </Row>
              );
            })}
          </div>
        )}
      </Form.List>
      <Button
        type="dashed"
        size="small"
        icon={<PlusOutlined />}
        onClick={() => {
          const current = form.getFieldValue('earnings') || [];
          form.setFieldsValue({
            earnings: [
              ...current,
              { componentId: undefined, calculationType: 'FLAT', value: '', percentageOf: null, enabled: true },
            ],
          });
        }}
      >
        Add earning
      </Button>
    </Card>
  );

  const renderOtherList = (fieldName, title, availableItems) => (
    <Card
      size="small"
      title={title}
      style={{ marginBottom: 16 }}
      extra={
        <Button
          type="dashed"
          size="small"
          icon={<PlusOutlined />}
          onClick={() => {
            const current = form.getFieldValue(fieldName) || [];
            form.setFieldsValue({
              [fieldName]: [
                ...current,
                {
                  componentId: availableItems[0]?.id || undefined,
                  calculationType: 'FLAT',
                  value: '',
                  percentageOf: null,
                  enabled: true,
                },
              ],
            });
          }}
        >
          Add {title.slice(0, -1).toLowerCase()}
        </Button>
      }
    >
      <Form.List name={fieldName}>
        {(fields, { remove }) => (
          <div>
            {fields.length === 0 && (
              <Text type="secondary" style={{ fontStyle: 'italic' }}>
                No {title.toLowerCase()} allocated.
              </Text>
            )}
            {fields.map(({ key, name, ...restField }) => {
              const rowValues = form.getFieldValue([fieldName, name]) || {};
              const isPercentage = rowValues.calculationType === 'PERCENTAGE';
              return (
                <Row key={key} gutter={8} align="top" style={{ marginBottom: 12 }}>
                  <Col span={7}>
                    <Form.Item
                      {...restField}
                      name={[name, 'componentId']}
                      rules={[{ required: true, message: 'Select component' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select placeholder="Component">
                        {availableItems.map((comp) => (
                          <Select.Option key={comp.id} value={comp.id}>
                            {comp.name} ({comp.code})
                          </Select.Option>
                        ))}
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    <Form.Item
                      {...restField}
                      name={[name, 'calculationType']}
                      rules={[{ required: true, message: 'Type' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select placeholder="Type">
                        <Select.Option value="FLAT">Flat</Select.Option>
                        <Select.Option value="PERCENTAGE">%</Select.Option>
                      </Select>
                    </Form.Item>
                  </Col>
                  <Col span={5}>
                    <Form.Item
                      {...restField}
                      name={[name, 'value']}
                      rules={[
                        { required: true, message: 'Value' },
                        { pattern: DECIMAL_REGEX, message: 'Up to 4 decimals' },
                      ]}
                      style={{ marginBottom: 0 }}
                    >
                      <Input placeholder="Value / %" />
                    </Form.Item>
                  </Col>
                  <Col span={4}>
                    {isPercentage ? (
                      <Form.Item
                        {...restField}
                        name={[name, 'percentageOf']}
                        rules={[{ required: true, message: 'Base' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <Select placeholder="% of">
                          <Select.Option value="BASIC">Basic</Select.Option>
                          <Select.Option value="CTC">CTC</Select.Option>
                          <Select.Option value="GROSS">Gross</Select.Option>
                        </Select>
                      </Form.Item>
                    ) : (
                      <div />
                    )}
                  </Col>
                  <Col span={2}>
                    <Form.Item
                      {...restField}
                      name={[name, 'enabled']}
                      valuePropName="checked"
                      style={{ marginBottom: 0 }}
                    >
                      <Switch size="small" />
                    </Form.Item>
                  </Col>
                  <Col span={2}>
                    <Button
                      type="text"
                      danger
                      aria-label={`Remove ${title.slice(0, -1).toLowerCase()}`}
                      icon={<DeleteOutlined />}
                      onClick={() => {
                        remove(name);
                        schedulePreview();
                      }}
                    />
                  </Col>
                </Row>
              );
            })}
          </div>
        )}
      </Form.List>
    </Card>
  );

  const renderStatutory = () => (
    <Card size="small" title="Statutory" style={{ marginBottom: 16 }}>
      {profileState === 'error' && (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 8 }}
          data-testid="statutory-profile-load-error"
          message="Could not load the employee's statutory details. Close and reopen to try again; saving is off until they load."
        />
      )}
      {companyEpf && !companyEpf.is_enabled && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 8 }}
          message="EPF is switched off for the company"
          description={<Link to="/payroll/settings/epf">Payroll → Settings → EPF</Link>}
        />
      )}
      {companyEsi && !companyEsi.is_enabled && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 8 }}
          message="ESI is switched off for the company"
          description={<Link to="/payroll/settings/esi">Payroll → Settings → ESI</Link>}
        />
      )}
      <Row gutter={16}>
        <Col span={8}>
          <Form.Item name={['statutory', 'eligibleForPf']} valuePropName="checked" style={{ marginBottom: 4 }}>
            <Checkbox>Employees&apos; Provident Fund (EPF)</Checkbox>
          </Form.Item>
          <Form.Item name={['statutory', 'eligibleForEps']} valuePropName="checked" style={{ marginBottom: 4 }}>
            <Checkbox disabled={!epfTicked}>Employee Pension Scheme (EPS)</Checkbox>
          </Form.Item>
          <Form.Item
            name={['statutory', 'contributesEpsOnHigherWages']}
            valuePropName="checked"
            style={{ marginBottom: 4 }}
          >
            <Checkbox disabled={!epfTicked || !epsTicked}>EPS on higher wages</Checkbox>
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item name={['statutory', 'eligibleForPt']} valuePropName="checked" style={{ marginBottom: 4 }}>
            <Checkbox>Professional Tax (PT)</Checkbox>
          </Form.Item>
          <Form.Item name={['statutory', 'eligibleForEsi']} valuePropName="checked" style={{ marginBottom: 4 }}>
            <Checkbox>Employee State Insurance (ESI)</Checkbox>
          </Form.Item>
          {esiWarning && <Alert type="warning" showIcon message={esiWarning} data-testid="esi-ceiling-warning" />}
        </Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 8 }}>
        <Col span={8}>
          <Form.Item
            name={['statutory', 'pfAccountNumber']}
            label="PF account number"
            rules={fieldRules.pfAccountNumber}
          >
            <Input id="input-pf-account-number" maxLength={PF_ACCOUNT_MAX_LENGTH} placeholder="AA/AAA/0000000/XXX/0000000" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name={['statutory', 'uan']}
            label="UAN"
            rules={fieldRules.uan}
            dependencies={[['statutory', 'eligibleForPf']]}
          >
            <Input id="input-uan" maxLength={UAN_MAX_LENGTH} placeholder="12 digits" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item
            name={['statutory', 'esiNumber']}
            label="ESI number"
            rules={fieldRules.esiNumber}
            dependencies={[['statutory', 'eligibleForEsi']]}
          >
            <Input id="input-esi-number" maxLength={ESI_NUMBER_MAX_LENGTH} placeholder="10 digits" />
          </Form.Item>
        </Col>
      </Row>
    </Card>
  );

  const componentRows = preview
    ? [...(preview.earnings || []), ...(preview.benefits || []), ...(preview.reimbursements || [])]
    : [];

  const renderSummary = () => (
    <Card
      size="small"
      title={
        <Space>
          <span>Summary</span>
          {(previewLoading || previewPending) && <Spin size="small" />}
        </Space>
      }
      data-testid="salary-setup-summary"
    >
      {!preview && !previewError && (
        <Text type="secondary">Enter the annual CTC to see the split, the statutory amounts and the take-home.</Text>
      )}
      {previewError && (
        <Alert
          type="error"
          showIcon
          message={previewError.message || 'The preview could not be computed'}
          description={
            previewError.unplaced?.length ? previewError.unplaced.join('. ') : 'Correct the fields marked above.'
          }
        />
      )}
      {preview && (
        <>
          <Table
            id="table-salary-preview-components"
            dataSource={componentRows}
            rowKey={(r, i) => `${r.componentId}-${i}`}
            pagination={false}
            size="small"
            style={{ marginBottom: 16 }}
            columns={[
              { title: 'Component', dataIndex: 'componentName', key: 'componentName' },
              { title: 'Monthly', dataIndex: 'monthlyAmount', key: 'monthlyAmount', render: (v) => formatMoney(v) },
              { title: 'Annual', dataIndex: 'annualAmount', key: 'annualAmount', render: (v) => formatMoney(v) },
              {
                title: 'CTC',
                dataIndex: 'includedInCtc',
                key: 'includedInCtc',
                render: (included) =>
                  included ? <Tag color="blue">In CTC</Tag> : <Text type="secondary">Not in CTC</Text>,
              },
            ]}
          />
          <Title level={5}>Employer contributions</Title>
          <div style={{ marginBottom: 16 }}>
            <StatutoryContributionsTable
              id="table-salary-preview-statutory"
              statutory={(preview.statutory || []).filter((line) => line.share === 'EMPLOYER')}
            />
          </div>
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="Gross (monthly)">{formatMoney(preview.grossMonthly)}</Descriptions.Item>
            {(preview.employeeDeductionsMonthly || []).map((line) => (
              <Descriptions.Item key={line.componentCode} label={statutoryName(line.componentCode)}>
                {formatMoney(line.monthlyAmount)}
              </Descriptions.Item>
            ))}
            {!preview.ptEstimated && (
              <Descriptions.Item label="Professional tax">
                <Text type="secondary">Not estimated: the employee has no work location</Text>
              </Descriptions.Item>
            )}
            <Descriptions.Item label="Estimated take-home (monthly)">
              <Space direction="vertical" size={0}>
                <Text strong data-testid="salary-take-home">
                  {formatMoney(preview.takeHomeMonthlyEstimate)}
                </Text>
                <Text type="secondary">before TDS and loss of pay</Text>
              </Space>
            </Descriptions.Item>
          </Descriptions>
        </>
      )}
    </Card>
  );

  const titleText =
    mode === 'revise' ? 'Revise salary' : mode === 'edit' ? 'Edit salary version' : 'Set up salary';

  return (
    <Drawer
      title={titleText}
      open={open}
      onClose={onClose}
      width={1000}
      destroyOnHidden
      footer={
        <div style={{ textAlign: 'right' }}>
          <Space>
            <Button onClick={onClose}>Cancel</Button>
            {canManage && (
              <Button
                id="btn-salary-version-submit"
                type="primary"
                loading={submitting}
                disabled={saveBlocked}
                onClick={handleSave}
              >
                Save
              </Button>
            )}
          </Space>
        </div>
      }
    >
      {saveError && (
        <Alert
          type="error"
          message={saveError.message}
          description={saveError.unplaced?.length ? saveError.unplaced.join('. ') : undefined}
          showIcon
          closable
          onClose={() => setSaveError(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Form
        form={form}
        layout="vertical"
        disabled={!canManage}
        initialValues={{ earnings: [], benefits: [], reimbursements: [], statutory: EMPTY_PROFILE }}
        onValuesChange={schedulePreview}
      >
        <Card size="small" title="Basics" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="annualCtc"
                label="Annual CTC (₹)"
                rules={[
                  { required: true, message: 'Annual CTC is required' },
                  { pattern: DECIMAL_REGEX, message: 'Must be a valid decimal (max 4 places)' },
                ]}
              >
                <Input id="input-annual-ctc" placeholder="e.g. 600000" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="effectiveFrom"
                label="Effective from"
                rules={[{ required: true, message: 'Effective date is required' }]}
              >
                <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="notes" label="Notes" style={{ marginBottom: 0 }}>
            <TextArea rows={2} placeholder="Optional notes for this salary version" />
          </Form.Item>
        </Card>

        {renderEarnings()}
        {renderOtherList('benefits', 'Benefits', catalogue.benefits || [])}
        {renderOtherList('reimbursements', 'Reimbursements', catalogue.reimbursements || [])}
        {renderStatutory()}
      </Form>

      {renderSummary()}
    </Drawer>
  );
}

SalarySetupDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
  onProfileSaved: PropTypes.func,
  employeeId: PropTypes.string.isRequired,
  mode: PropTypes.oneOf(['create', 'revise', 'edit']),
  initialValues: PropTypes.object,
  dateOfJoining: PropTypes.string,
};
