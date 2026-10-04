import { useState } from 'react';
import PropTypes from 'prop-types';
import { Alert, Button, Form, Input, InputNumber } from 'antd';
import { useFormik } from 'formik';
import * as Yup from 'yup';
import { requestService } from './requestService.js';

export const schema = Yup.object({
  date: Yup.string().required('Required'),
  hours: Yup.number()
    .typeError('Enter hours')
    .required('Required')
    .min(0.25, 'At least 0.25')
    .max(24, 'At most 24')
    .test('quarter', 'In steps of 0.25', (v) => v == null || Number.isInteger(Math.round(v * 100) / 25)),
  remarks: Yup.string().max(500, 'At most 500 characters'),
});

/** Ask for overtime to be paid (W-48.5 §5). Hours go as two-decimal text, which the server reads as BigDecimal. */
export function OvertimeForm({ onSaved }) {
  const [error, setError] = useState(null);
  const formik = useFormik({
    initialValues: { date: '', hours: null, remarks: '' },
    validationSchema: schema,
    onSubmit: async (v, { resetForm }) => {
      setError(null);
      try {
        const saved = await requestService.submitOvertime({
          overtime_date: v.date,
          hours: Number(v.hours).toFixed(2),
          remarks: v.remarks?.trim() || null,
        });
        resetForm();
        onSaved?.(saved);
      } catch (err) {
        setError(err?.message || 'Could not submit');
      }
    },
  });

  const fieldError = (n) => (formik.touched[n] || formik.submitCount > 0 ? formik.errors[n] : undefined);
  const item = (n, label) => ({ label, validateStatus: fieldError(n) ? 'error' : undefined, help: fieldError(n) });

  return (
    <Form layout="vertical" onFinish={formik.handleSubmit}>
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      <Form.Item {...item('date', 'Date')}>
        <Input type="date" aria-label="Date" name="date" value={formik.values.date} onChange={formik.handleChange} />
      </Form.Item>
      <Form.Item {...item('hours', 'Hours')}>
        <InputNumber
          aria-label="Hours"
          min={0.25}
          max={24}
          step={0.25}
          precision={2}
          value={formik.values.hours}
          onChange={(v) => formik.setFieldValue('hours', v)}
        />
      </Form.Item>
      <Form.Item {...item('remarks', 'Remarks')}>
        <Input.TextArea
          aria-label="Remarks"
          name="remarks"
          rows={3}
          maxLength={500}
          value={formik.values.remarks}
          onChange={formik.handleChange}
        />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={formik.isSubmitting}>
        Submit
      </Button>
    </Form>
  );
}

OvertimeForm.propTypes = { onSaved: PropTypes.func };
