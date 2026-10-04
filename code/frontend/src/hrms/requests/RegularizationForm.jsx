import { useState } from 'react';
import PropTypes from 'prop-types';
import { Alert, Button, Form, Input } from 'antd';
import { useFormik } from 'formik';
import * as Yup from 'yup';
import dayjs from 'dayjs';
import { requestService } from './requestService.js';

export const schema = Yup.object({
  date: Yup.string().required('Required'),
  inTime: Yup.string().required('Required'),
  outTime: Yup.string()
    .required('Required')
    .test('after-in', 'Out must be after in', function check(v) {
      const inTime = this.parent.inTime;
      return !v || !inTime || v > inTime;
    }),
  reason: Yup.string().trim().required('Required').max(500, 'At most 500 characters'),
});

/** A date plus HH:mm as an ISO offset date-time in the browser's zone, e.g. 2026-10-01T09:00:00+05:30. */
export function atTime(date, time) {
  return dayjs(`${date}T${time}`).format('YYYY-MM-DDTHH:mm:ssZ');
}

/** Ask for a day to be regularized (W-48.5 §5). */
export function RegularizationForm({ onSaved }) {
  const [error, setError] = useState(null);
  const formik = useFormik({
    initialValues: { date: '', inTime: '', outTime: '', reason: '' },
    validationSchema: schema,
    onSubmit: async (v, { resetForm }) => {
      setError(null);
      try {
        const saved = await requestService.submitRegularization({
          date: v.date,
          inAt: atTime(v.date, v.inTime),
          outAt: atTime(v.date, v.outTime),
          reason: v.reason.trim(),
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
      <Form.Item {...item('inTime', 'In')}>
        <Input type="time" aria-label="In" name="inTime" value={formik.values.inTime} onChange={formik.handleChange} />
      </Form.Item>
      <Form.Item {...item('outTime', 'Out')}>
        <Input
          type="time"
          aria-label="Out"
          name="outTime"
          value={formik.values.outTime}
          onChange={formik.handleChange}
        />
      </Form.Item>
      <Form.Item {...item('reason', 'Reason')}>
        <Input.TextArea
          aria-label="Reason"
          name="reason"
          rows={3}
          maxLength={500}
          value={formik.values.reason}
          onChange={formik.handleChange}
        />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={formik.isSubmitting}>
        Submit
      </Button>
    </Form>
  );
}

RegularizationForm.propTypes = { onSaved: PropTypes.func };
