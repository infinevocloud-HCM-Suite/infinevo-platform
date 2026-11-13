import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Button, DatePicker, Drawer, Form, Input, InputNumber, Select, message } from 'antd';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';
import { formatMonth } from '@shared/ui/format.js';
import { employeeLabel, lossOfPayService } from './lossOfPayService.js';

const MONTH = 'YYYY-MM';

/** Whole or half days only - the ledger takes any decimal, but no one loses a third of a day. */
const halfDays = (_, v) =>
  v == null || Number.isInteger(Number(v) * 2) ? Promise.resolve() : Promise.reject(new Error('In steps of 0.5'));

/**
 * HR enters loss-of-pay days by hand (D-140): one `LOP_DAYS` pay input for one employee and month.
 * A locked month is not refused here - the ledger posts to the next open month and says so in
 * `posted_period`, which this drawer reports as a warning and still treats as saved.
 */
export function LossOfPayDrawer({ open, onClose, onSaved }) {
  const [form] = Form.useForm();
  const [options, setOptions] = useState([]);
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const seq = useRef(0);

  const search = useCallback(async (q) => {
    const mine = ++seq.current;
    setSearching(true);
    try {
      const list = await lossOfPayService.searchActive(q);
      if (mine === seq.current) setOptions(list.map((e) => ({ value: e.id, label: employeeLabel(e) })));
    } catch {
      if (mine === seq.current) setOptions([]);
    } finally {
      if (mine === seq.current) setSearching(false);
    }
  }, []);

  useEffect(() => {
    if (open) search('');
  }, [open, search]);

  const onFinish = async (v) => {
    const period = v.month.format(MONTH);
    setSaving(true);
    try {
      const saved = await lossOfPayService.record({
        employeeId: v.employeeId,
        period,
        days: Number(v.days).toFixed(2),
        reason: v.reason.trim(),
      });
      const posted = saved?.posted_period;
      if (posted && posted !== period) {
        message.warning(`${formatMonth(period)} is locked; posted to ${formatMonth(posted)}`);
      } else {
        successMsg('Loss of pay recorded');
      }
      form.resetFields();
      onSaved?.(saved);
    } catch (err) {
      errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      title="Add loss of pay"
      open={open}
      onClose={onClose}
      destroyOnClose
      width={420}
      data-testid="lop-drawer"
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={onFinish}
        initialValues={{ month: dayjs().startOf('month') }}
        id="lop-form"
      >
        <Form.Item name="employeeId" label="Employee" rules={[{ required: true, message: 'Pick an employee' }]}>
          <Select
            id="lop-employee"
            showSearch
            filterOption={false}
            onSearch={search}
            options={options}
            loading={searching}
            placeholder="Search active employees"
            aria-label="Employee"
          />
        </Form.Item>
        <Form.Item name="month" label="Month" rules={[{ required: true, message: 'Pick a month' }]}>
          <DatePicker id="lop-month" picker="month" format="MMM YYYY" allowClear={false} aria-label="Month" />
        </Form.Item>
        <Form.Item
          name="days"
          label="Days"
          rules={[{ required: true, message: 'Enter the days' }, { validator: halfDays }]}
        >
          <InputNumber id="lop-days" min={0.5} max={31} step={0.5} aria-label="Days" style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="reason"
          label="Reason"
          rules={[
            { required: true, whitespace: true, message: 'Enter a reason' },
            { max: 200, message: 'At most 200 characters' },
          ]}
        >
          <Input id="lop-reason" maxLength={200} aria-label="Reason" placeholder="e.g. Absent 3-5 Oct, no leave applied" />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={saving} data-testid="lop-save">
          Save
        </Button>
      </Form>
    </Drawer>
  );
}

LossOfPayDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSaved: PropTypes.func,
};
