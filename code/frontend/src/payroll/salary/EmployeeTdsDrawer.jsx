import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Form, Input, Button, Space, DatePicker, Alert, Typography } from 'antd';
import dayjs from 'dayjs';
import { employeeTdsService } from './employeeTdsService.js';
import { fyForApi } from '../tax/financialYear.js';
import { isDecimal, compareDecimal } from './decimal.js';
import { fyMonthBounds, isMonthOutsideFy, defaultEffectiveMonth } from './tdsMonths.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { humanize } from '@shared/ui/format.js';

const { Text } = Typography;

const NOTE_MAX_LENGTH = 200;
const TWO_PLACES = /^\d+(\.\d{1,2})?$/;

/** Annual tax: required, a plain decimal of at most two places, not below zero. Kept as text. */
function annualTaxRule() {
  return {
    validator(_, value) {
      const text = value === undefined || value === null ? '' : String(value).trim();
      if (!text) return Promise.reject(new Error('Enter the annual tax'));
      if (!isDecimal(text) || compareDecimal(text, '0') < 0) {
        return Promise.reject(new Error('Annual tax must be a number, zero or more'));
      }
      if (!TWO_PLACES.test(text)) return Promise.reject(new Error('Annual tax takes at most two decimal places'));
      return Promise.resolve();
    },
  };
}

/**
 * Edit an employee's TDS figure for a financial year (D-145): the annual tax, the month it applies
 * from and a note. Saves through `PUT …/tds/{fy}`, which supersedes the active record; annual gross,
 * taxable income and regime are carried over from that record unchanged.
 */
export function EmployeeTdsDrawer({ open, onClose, employeeId, fy, record = null, onSaved }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const bounds = fyMonthBounds(fy);
  const regime = record?.regime || 'NEW';

  useEffect(() => {
    if (!open) return;
    const month = record?.effective_from_period || defaultEffectiveMonth(fy);
    form.setFieldsValue({
      annualTax: record?.annual_tax !== undefined && record?.annual_tax !== null ? String(record.annual_tax) : '',
      effectiveMonth: month ? dayjs(`${month}-01`) : null,
      note: '',
    });
  }, [open, record, fy, form]);

  const handleSave = async () => {
    let values;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    const body = {
      regime,
      annual_gross: record?.annual_gross !== undefined && record?.annual_gross !== null ? String(record.annual_gross) : '0',
      annual_taxable_income:
        record?.annual_taxable_income !== undefined && record?.annual_taxable_income !== null
          ? String(record.annual_taxable_income)
          : '0',
      annual_tax: String(values.annualTax).trim(),
      effective_from_period: values.effectiveMonth.format('YYYY-MM'),
      note: values.note.trim(),
    };
    setSubmitting(true);
    try {
      const saved = await employeeTdsService.save(employeeId, fyForApi(fy), body);
      setSubmitting(false);
      await successMsg('Success', 'TDS figures saved');
      if (onSaved) onSaved(saved);
      onClose();
    } catch (err) {
      setSubmitting(false);
      errorMsg(err);
    }
  };

  return (
    <Drawer
      title={`Edit TDS for FY ${fy}`}
      open={open}
      onClose={onClose}
      width={420}
      destroyOnHidden
      data-testid="employee-tds-drawer"
      footer={
        <div style={{ textAlign: 'right' }}>
          <Space>
            <Button id="tds-btn-cancel" className="btn-spring" onClick={onClose}>
              Cancel
            </Button>
            <Button
              id="tds-btn-save"
              data-testid="employee-tds-save"
              type="primary"
              className="btn-spring"
              loading={submitting}
              onClick={handleSave}
            >
              Save
            </Button>
          </Space>
        </div>
      }
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        data-testid="employee-tds-replace-warning"
        message="A later declaration submit, proof verification or salary revision will replace this figure"
      />
      <Form form={form} layout="vertical" name="employee-tds">
        <Form.Item label="Regime">
          <Text id="tds-regime" data-testid="employee-tds-regime">
            {humanize(regime)}
          </Text>
        </Form.Item>
        <Form.Item name="annualTax" label="Annual tax" rules={[annualTaxRule()]}>
          <Input id="tds-input-annual-tax" inputMode="decimal" prefix="₹" placeholder="0.00" />
        </Form.Item>
        <Form.Item
          name="effectiveMonth"
          label="Effective month"
          rules={[
            { required: true, message: 'Choose the month the figure applies from' },
            {
              validator(_, value) {
                if (!value) return Promise.resolve();
                return isMonthOutsideFy(value.format('YYYY-MM'), fy)
                  ? Promise.reject(new Error(`Choose a month from April to March of FY ${fy}`))
                  : Promise.resolve();
              },
            },
          ]}
          extra={bounds ? 'From April to March of the financial year' : undefined}
        >
          <DatePicker
            id="tds-input-effective-month"
            picker="month"
            format="MMM YYYY"
            allowClear={false}
            style={{ width: '100%' }}
            disabledDate={(current) => isMonthOutsideFy(current.format('YYYY-MM'), fy)}
            defaultPickerValue={bounds ? dayjs(`${bounds.first}-01`) : undefined}
          />
        </Form.Item>
        <Form.Item
          name="note"
          label="Note"
          rules={[
            { required: true, whitespace: true, message: 'Say why the figure is being set' },
            { max: NOTE_MAX_LENGTH, message: `At most ${NOTE_MAX_LENGTH} characters` },
          ]}
        >
          <Input.TextArea id="tds-input-note" rows={3} maxLength={NOTE_MAX_LENGTH} showCount />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

EmployeeTdsDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  employeeId: PropTypes.string.isRequired,
  fy: PropTypes.string.isRequired,
  record: PropTypes.object,
  onSaved: PropTypes.func,
};
