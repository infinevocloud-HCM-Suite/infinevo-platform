import { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, InputNumber, Radio, Spin, Switch, Tag } from 'antd';
import { useFormik } from 'formik';
import * as Yup from 'yup';
import { useCan } from '@shell/screens';
import { attendanceService } from './attendanceService.js';

const hours = Yup.number().typeError('Enter hours').required('Required').min(0, 'At least 0').max(24, 'At most 24');
const whole = Yup.number().nullable().integer('Whole number').min(0, 'At least 0');

export const schema = Yup.object({
  hoursCalculation: Yup.string().oneOf(['FIRST_IN_LAST_OUT', 'EVERY_SESSION']).required(),
  fullDayMinimumHours: hours,
  halfDayMinimumHours: hours.test('below-full', 'Half day must be below full day', function check(v) {
    const full = this.parent.fullDayMinimumHours;
    return v === undefined || v === null || full === undefined || full === null || v < full;
  }),
  regularizationWindowDays: whole,
  maxRegularizationsPerMonth: whole,
  allowRegularizationWithoutSession: Yup.boolean(),
});

/** Minimum hours go to the server with two decimals (W-48.4 §11). */
const two = (n) => Math.round(Number(n) * 100) / 100;

/** HR: the tenant's attendance rules (W-48.4 §5). */
export function AttendanceSettings() {
  const canManage = useCan('core.attendance.manage');
  const [pref, setPref] = useState(null);
  const [error, setError] = useState(null);
  const [saved, setSaved] = useState(false);

  const formik = useFormik({
    enableReinitialize: true,
    initialValues: {
      hoursCalculation: pref?.hoursCalculation || 'EVERY_SESSION',
      fullDayMinimumHours: pref?.fullDayMinimumHours != null ? Number(pref.fullDayMinimumHours) : 9,
      halfDayMinimumHours: pref?.halfDayMinimumHours != null ? Number(pref.halfDayMinimumHours) : 4.5,
      regularizationWindowDays: pref?.regularizationWindowDays ?? null,
      maxRegularizationsPerMonth: pref?.maxRegularizationsPerMonth ?? null,
      allowRegularizationWithoutSession: pref?.allowRegularizationWithoutSession ?? true,
    },
    validationSchema: schema,
    onSubmit: async (values) => {
      setError(null);
      setSaved(false);
      try {
        const next = await attendanceService.savePreferences({
          ...values,
          fullDayMinimumHours: two(values.fullDayMinimumHours),
          halfDayMinimumHours: two(values.halfDayMinimumHours),
        });
        setPref(next);
        setSaved(true);
      } catch (err) {
        setError(err?.message || 'Could not save');
      }
    },
  });

  useEffect(() => {
    attendanceService
      .preferences()
      .then(setPref)
      .catch((err) => setError(err?.message || 'Could not load settings'));
  }, []);

  if (!pref && !error) {
    return (
      <Card>
        <Spin />
      </Card>
    );
  }

  const fieldError = (name) => (formik.touched[name] || formik.submitCount > 0 ? formik.errors[name] : undefined);
  const item = (name, label) => ({
    label,
    validateStatus: fieldError(name) ? 'error' : undefined,
    help: fieldError(name),
  });
  const setNum = (name) => (v) => {
    formik.setFieldTouched(name, true, false);
    formik.setFieldValue(name, v);
  };

  return (
    <Card title="Attendance settings" extra={pref?.isDefault ? <Tag color="blue">Using defaults</Tag> : null}>
      {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
      {saved && <Alert type="success" message="Saved" showIcon style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={formik.handleSubmit} disabled={!canManage}>
        <Form.Item {...item('hoursCalculation', 'Hours calculation')}>
          <Radio.Group
            value={formik.values.hoursCalculation}
            onChange={(e) => formik.setFieldValue('hoursCalculation', e.target.value)}
          >
            <Radio value="FIRST_IN_LAST_OUT">First in, last out</Radio>
            <Radio value="EVERY_SESSION">Every session</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item {...item('fullDayMinimumHours', 'Full-day minimum hours')}>
          <InputNumber
            aria-label="Full-day minimum hours"
            min={0}
            max={24}
            step={0.25}
            value={formik.values.fullDayMinimumHours}
            onChange={setNum('fullDayMinimumHours')}
          />
        </Form.Item>
        <Form.Item {...item('halfDayMinimumHours', 'Half-day minimum hours')}>
          <InputNumber
            aria-label="Half-day minimum hours"
            min={0}
            max={24}
            step={0.25}
            value={formik.values.halfDayMinimumHours}
            onChange={setNum('halfDayMinimumHours')}
          />
        </Form.Item>
        <Form.Item {...item('regularizationWindowDays', 'Regularization window (days)')}>
          <InputNumber
            aria-label="Regularization window"
            min={0}
            precision={0}
            value={formik.values.regularizationWindowDays}
            onChange={setNum('regularizationWindowDays')}
          />
        </Form.Item>
        <Form.Item {...item('maxRegularizationsPerMonth', 'Regularizations per month')}>
          <InputNumber
            aria-label="Regularizations per month"
            min={0}
            precision={0}
            value={formik.values.maxRegularizationsPerMonth}
            onChange={setNum('maxRegularizationsPerMonth')}
          />
        </Form.Item>
        <Form.Item label="Allow regularization without a session">
          <Switch
            aria-label="Allow regularization without a session"
            checked={formik.values.allowRegularizationWithoutSession}
            onChange={(v) => formik.setFieldValue('allowRegularizationWithoutSession', v)}
          />
        </Form.Item>
        {canManage && (
          <Button type="primary" htmlType="submit" loading={formik.isSubmitting}>
            Save
          </Button>
        )}
      </Form>
    </Card>
  );
}
