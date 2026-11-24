import { useCallback, useEffect, useRef, useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Button, DatePicker, Drawer, Form, Input, InputNumber, Select } from 'antd';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';
import { isClosed, usePostingPeriod } from '@shared/payInputs/postingPeriod.js';
import { PostingNote } from '@shared/payInputs/PostingNote.jsx';
import { employeeLabel, lossOfPayService } from './lossOfPayService.js';

const MONTH = 'YYYY-MM';

/** Whole or half days only - the ledger takes any decimal, but no one loses a third of a day. */
const halfDays = (_, v) =>
  v == null || Number.isInteger(Number(v) * 2) ? Promise.resolve() : Promise.reject(new Error('In steps of 0.5'));

/**
 * HR enters loss-of-pay days by hand (D-140): one `LOP_DAYS` pay input for one employee and month.
 * Under the month it says which run picks the entry up before saving (D-144): an open month, a
 * locked one (amber - the run must be recomputed), or a closed one (red - Save is disabled and the
 * server answers 409 naming the month to use). The entry always lands in the month chosen: the core
 * POST refuses a closed month rather than moving it, so a month closed between the lookup and the
 * save comes back as that 409, shown through errorMsg.
 */
export function LossOfPayDrawer({ open, onClose, onSaved }) {
  const [form] = Form.useForm();
  const [options, setOptions] = useState([]);
  const [searching, setSearching] = useState(false);
  const [saving, setSaving] = useState(false);
  const seq = useRef(0);
  const month = Form.useWatch('month', form);
  const period = month && dayjs.isDayjs(month) ? month.format(MONTH) : null;
  const posting = usePostingPeriod(open ? period : null);
  const closed = isClosed(posting);

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
      successMsg('Loss of pay recorded');
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
        <Form.Item
          name="month"
          label="Month"
          rules={[{ required: true, message: 'Pick a month' }]}
          extra={<PostingNote posting={posting} testId="lop-posting" />}
        >
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
        <Button type="primary" htmlType="submit" loading={saving} disabled={closed} data-testid="lop-save">
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
