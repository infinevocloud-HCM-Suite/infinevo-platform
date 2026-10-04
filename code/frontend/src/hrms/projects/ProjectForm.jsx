import PropTypes from 'prop-types';
import { Button, DatePicker, Form, Input, InputNumber, Select, Space } from 'antd';
import { useFormik } from 'formik';
import * as Yup from 'yup';
import dayjs from 'dayjs';
import { EmployeePicker } from './EmployeePicker.jsx';
import { LABEL, PRIORITIES } from './projectService.js';

const DATE = 'YYYY-MM-DD';

export const projectSchema = Yup.object({
  name: Yup.string().trim().required('Name is required'),
  priority: Yup.string().required('Priority is required'),
  start_date: Yup.string().nullable(),
  end_date: Yup.string()
    .nullable()
    .test('after-start', 'End date cannot be before start date', function check(end) {
      const start = this.parent.start_date;
      return !start || !end || end >= start;
    }),
});

/** Create or edit a project (W-48.1 §5). Submits the snake_case `ProjectRequest` body. */
export function ProjectForm({ initial, onSubmit, onCancel, submitting }) {
  const f = useFormik({
    initialValues: {
      name: initial?.name ?? '',
      category: initial?.category ?? '',
      description: initial?.description ?? '',
      start_date: initial?.start_date ?? null,
      end_date: initial?.end_date ?? null,
      priority: initial?.priority ?? 'MEDIUM',
      budget: initial?.budget ?? null,
      manager_employee_id: initial?.manager_employee_id ?? null,
    },
    validationSchema: projectSchema,
    onSubmit: (v) =>
      onSubmit({
        ...v,
        name: v.name.trim(),
        category: v.category || null,
        description: v.description || null,
        budget: v.budget === null || v.budget === '' ? null : String(v.budget),
      }),
  });
  const err = (k) => (f.touched[k] || f.submitCount > 0) && f.errors[k];
  const date = (k, label) => (
    <DatePicker
      aria-label={label}
      value={f.values[k] ? dayjs(f.values[k]) : null}
      onChange={(d) => f.setFieldValue(k, d ? d.format(DATE) : null)}
      style={{ width: '100%' }}
    />
  );

  return (
    <Form layout="vertical" onFinish={f.handleSubmit}>
      <Form.Item label="Name" required validateStatus={err('name') ? 'error' : ''} help={err('name')}>
        <Input
          aria-label="Name"
          name="name"
          value={f.values.name}
          onChange={f.handleChange}
          onBlur={f.handleBlur}
        />
      </Form.Item>
      <Form.Item label="Category">
        <Input aria-label="Category" name="category" value={f.values.category} onChange={f.handleChange} />
      </Form.Item>
      <Form.Item label="Description">
        <Input.TextArea name="description" value={f.values.description} onChange={f.handleChange} rows={3} />
      </Form.Item>
      <Form.Item label="Start date">{date('start_date', 'Start date')}</Form.Item>
      <Form.Item label="End date" validateStatus={err('end_date') ? 'error' : ''} help={err('end_date')}>
        {date('end_date', 'End date')}
      </Form.Item>
      <Form.Item
        label="Priority"
        required
        validateStatus={err('priority') ? 'error' : ''}
        help={err('priority')}
      >
        <Select
          aria-label="Priority"
          value={f.values.priority}
          onChange={(v) => f.setFieldValue('priority', v)}
          options={PRIORITIES.map((p) => ({ value: p, label: LABEL[p] }))}
        />
      </Form.Item>
      <Form.Item label="Budget">
        <InputNumber
          min={0}
          stringMode
          value={f.values.budget}
          onChange={(v) => f.setFieldValue('budget', v)}
          style={{ width: '100%' }}
        />
      </Form.Item>
      <Form.Item label="Manager">
        <EmployeePicker
          value={f.values.manager_employee_id ?? undefined}
          initialLabel={initial?.manager_name}
          onChange={(v) => f.setFieldValue('manager_employee_id', v ?? null)}
        />
      </Form.Item>
      <Space>
        <Button type="primary" htmlType="submit" loading={submitting}>
          Save
        </Button>
        {onCancel && <Button onClick={onCancel}>Cancel</Button>}
      </Space>
    </Form>
  );
}

ProjectForm.propTypes = {
  initial: PropTypes.shape({
    name: PropTypes.string,
    category: PropTypes.string,
    description: PropTypes.string,
    start_date: PropTypes.string,
    end_date: PropTypes.string,
    priority: PropTypes.string,
    budget: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    manager_employee_id: PropTypes.string,
    manager_name: PropTypes.string,
  }),
  onSubmit: PropTypes.func.isRequired,
  onCancel: PropTypes.func,
  submitting: PropTypes.bool,
};
