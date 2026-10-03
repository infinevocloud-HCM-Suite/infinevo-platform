import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Formik } from 'formik';
import * as Yup from 'yup';
import { Alert, Button, DatePicker, Drawer, Form, Input, InputNumber, Result, Select, Space } from 'antd';
import { claimService } from './claimService.js';
import { compareAmounts, formatAmount, toAmountString } from './claimLabels.js';
import { readError } from '../tax/apiError.js';

const DESCRIPTION_MAX = 500;

// A 400's fieldErrors may name the wire field or its Java name; both land on the same field.
const FIELD_OF = {
  reimbursement_id: 'reimbursement_id',
  reimbursementId: 'reimbursement_id',
  requested_amount: 'requested_amount',
  requestedAmount: 'requested_amount',
  bill_date: 'bill_date',
  billDate: 'bill_date',
  description: 'description',
};

const isFuture = (day) => Boolean(day) && dayjs(day).isAfter(dayjs(), 'day');

const INITIAL = { reimbursement_id: undefined, requested_amount: null, bill_date: null, description: '' };

// Money stays text throughout: the checks below compare decimal digits, never floats.
export const claimSchema = Yup.object({
  reimbursement_id: Yup.string().required('Choose what you are claiming for'),
  requested_amount: Yup.mixed()
    .nullable()
    .test('required', 'Enter the amount', (v) => v !== null && v !== undefined && v !== '')
    .test('decimals', 'At most two decimals', (v) => v === null || v === undefined || v === '' || Boolean(toAmountString(v)))
    .test(
      'min',
      'The amount must be at least 0.01',
      (v) => !toAmountString(v) || compareAmounts(toAmountString(v), '0.01') !== -1
    ),
  bill_date: Yup.mixed()
    .nullable()
    .test('required', 'Choose the bill date', (v) => Boolean(v))
    .test('future', 'The bill date cannot be in the future', (v) => !isFuture(v)),
  description: Yup.string().max(DESCRIPTION_MAX, `At most ${DESCRIPTION_MAX} characters`),
});

/**
 * "New claim" drawer (W-47.4 §5, W-35.1 §4). The amount is sent as text with two decimals; a
 * claim above the component's `max_limit` shows a warning but is not blocked. No receipt upload
 * (§ 13 decision 2): `document_id` is sent as null until the follow-up ticket gives it a control.
 */
export function ClaimForm({ open, onClose, onSubmitted }) {
  const [components, setComponents] = useState([]);
  const [loadError, setLoadError] = useState(null);
  const [loadingComponents, setLoadingComponents] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const inFlight = useRef(false);

  const loadComponents = useCallback(async () => {
    setLoadingComponents(true);
    setLoadError(null);
    try {
      const rows = await claimService.components();
      setComponents(Array.isArray(rows) ? rows : []);
    } catch (err) {
      setComponents([]);
      setLoadError(readError(err, 'Could not load the reimbursement components').message);
    } finally {
      setLoadingComponents(false);
    }
  }, []);

  useEffect(() => {
    if (open) {
      setSubmitError(null);
      loadComponents();
    }
  }, [open, loadComponents]);

  const handleSubmit = async (values, { setSubmitting, setErrors }) => {
    if (inFlight.current) return;
    inFlight.current = true;
    setSubmitError(null);
    try {
      const created = await claimService.submit({
        reimbursement_id: values.reimbursement_id,
        requested_amount: toAmountString(values.requested_amount),
        bill_date: dayjs(values.bill_date).format('YYYY-MM-DD'),
        description: values.description?.trim() || null,
        document_id: null,
      });
      onSubmitted?.(created);
      onClose?.();
    } catch (err) {
      const { message, fieldErrors } = readError(err, 'The claim could not be submitted');
      const mapped = {};
      Object.entries(fieldErrors || {}).forEach(([key, msg]) => {
        if (FIELD_OF[key]) mapped[FIELD_OF[key]] = String(msg);
      });
      if (Object.keys(mapped).length > 0) setErrors(mapped);
      setSubmitError(message);
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <Drawer title="New claim" open={open} onClose={onClose} width={480} destroyOnClose>
      {loadError ? (
        <Result
          status="error"
          title="Could not load the components"
          subTitle={loadError}
          extra={
            <Button type="primary" onClick={loadComponents}>
              Retry
            </Button>
          }
        />
      ) : (
        <Formik initialValues={INITIAL} validationSchema={claimSchema} onSubmit={handleSubmit}>
          {({ values, errors, touched, setFieldValue, setFieldTouched, handleSubmit: formikSubmit, isSubmitting }) => {
            const component = components.find((c) => c.id === values.reimbursement_id);
            const overLimit =
              component?.max_limit !== null &&
              component?.max_limit !== undefined &&
              compareAmounts(values.requested_amount, component.max_limit) === 1;
            const fieldStatus = (name) => (touched[name] && errors[name] ? 'error' : '');
            const fieldHelp = (name) => (touched[name] && errors[name]) || undefined;
            return (
              <Form layout="vertical" onFinish={formikSubmit} disabled={isSubmitting}>
                {submitError && (
                  <Alert type="error" showIcon message={submitError} style={{ marginBottom: 16 }} />
                )}
                <Form.Item
                  label="Component"
                  required
                  validateStatus={fieldStatus('reimbursement_id')}
                  help={fieldHelp('reimbursement_id')}
                >
                  <Select
                    aria-label="Component"
                    loading={loadingComponents}
                    placeholder="Choose a component"
                    value={values.reimbursement_id}
                    options={components.map((c) => ({ value: c.id, label: c.name }))}
                    onChange={(v) => setFieldValue('reimbursement_id', v)}
                    onBlur={() => setFieldTouched('reimbursement_id', true)}
                  />
                </Form.Item>
                <Form.Item
                  label="Amount"
                  required
                  validateStatus={fieldStatus('requested_amount')}
                  help={fieldHelp('requested_amount')}
                >
                  <InputNumber
                    aria-label="Amount"
                    stringMode
                    min="0.01"
                    precision={2}
                    value={values.requested_amount}
                    onChange={(v) => setFieldValue('requested_amount', v)}
                    onBlur={() => setFieldTouched('requested_amount', true)}
                    style={{ width: '100%' }}
                    placeholder="0.00"
                  />
                </Form.Item>
                {overLimit && (
                  <Alert
                    type="warning"
                    showIcon
                    data-testid="over-limit"
                    message={`Above the limit of ${formatAmount(component.max_limit)} for this component. You can still submit.`}
                    style={{ marginBottom: 16 }}
                  />
                )}
                <Form.Item
                  label="Bill date"
                  required
                  validateStatus={fieldStatus('bill_date')}
                  help={fieldHelp('bill_date')}
                >
                  <DatePicker
                    aria-label="Bill date"
                    disabledDate={isFuture}
                    format="YYYY-MM-DD"
                    placeholder="Select bill date"
                    value={values.bill_date}
                    onChange={(v) => {
                      setFieldValue('bill_date', v);
                      setFieldTouched('bill_date', true, false);
                    }}
                    style={{ width: '100%' }}
                  />
                </Form.Item>
                <Form.Item
                  label="Description"
                  validateStatus={fieldStatus('description')}
                  help={fieldHelp('description')}
                >
                  <Input.TextArea
                    aria-label="Description"
                    name="description"
                    rows={3}
                    maxLength={DESCRIPTION_MAX}
                    showCount
                    value={values.description}
                    onChange={(e) => setFieldValue('description', e.target.value)}
                    onBlur={() => setFieldTouched('description', true)}
                  />
                </Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" loading={isSubmitting} disabled={isSubmitting}>
                    Submit claim
                  </Button>
                  <Button onClick={onClose} disabled={isSubmitting}>
                    Cancel
                  </Button>
                </Space>
              </Form>
            );
          }}
        </Formik>
      )}
    </Drawer>
  );
}

ClaimForm.propTypes = {
  open: PropTypes.bool,
  onClose: PropTypes.func,
  onSubmitted: PropTypes.func,
};
