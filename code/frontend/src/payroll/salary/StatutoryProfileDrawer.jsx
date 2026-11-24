import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Form, Input, Button, Checkbox, Space } from 'antd';
import { statutoryProfileService } from './statutoryProfileService.js';
import {
  statutoryFieldRules,
  UAN_MAX_LENGTH,
  PF_ACCOUNT_MAX_LENGTH,
  ESI_NUMBER_MAX_LENGTH,
} from './statutoryRules.js';
import {
  EMPTY_PROFILE,
  STATUTORY_TICKS,
  TDS_COMPANY_OFF_NOTE,
  profileFormValues,
  profilePayload,
} from './statutoryProfileForm.js';
import { useCompanyTdsOff } from './companyTds.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

/** The checkbox id for a tick: `sp-` keeps it apart from the salary drawer's ids. */
const TICK_IDS = {
  eligibleForPf: 'sp-chk-statutory-pf',
  eligibleForEps: 'sp-chk-statutory-eps',
  contributesEpsOnHigherWages: 'sp-chk-statutory-eps-higher-wages',
  eligibleForPt: 'sp-chk-statutory-pt',
  eligibleForEsi: 'sp-chk-statutory-esi',
  eligibleForLwf: 'sp-chk-statutory-lwf',
  director: 'sp-chk-statutory-director',
  tdsApplicable: 'sp-chk-statutory-tds',
};

/**
 * Edit the employee's statutory ticks and numbers on their own (D-143): saves the statutory profile
 * through `PUT …/statutory-profile` and touches no salary version.
 */
export function StatutoryProfileDrawer({ open, onClose, employeeId, profile = null, onSaved }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const epfTicked = Form.useWatch('eligibleForPf', form);
  const epsTicked = Form.useWatch('eligibleForEps', form);
  const companyTdsOff = useCompanyTdsOff(open);

  useEffect(() => {
    if (open) form.setFieldsValue(profileFormValues(profile));
  }, [open, profile, form]);

  const handleSave = async () => {
    try {
      await form.validateFields();
    } catch {
      return;
    }
    const values = form.getFieldsValue(true);
    setSubmitting(true);
    try {
      const saved = await statutoryProfileService.save(employeeId, profilePayload(values));
      setSubmitting(false);
      await successMsg('Success', 'Statutory details saved');
      if (onSaved) onSaved(saved);
      onClose();
    } catch (err) {
      setSubmitting(false);
      errorMsg(err);
    }
  };

  const fieldRules = statutoryFieldRules((name) => form.getFieldValue(name));

  const disabledFor = (name) => {
    if (name === 'eligibleForEps') return !epfTicked;
    if (name === 'contributesEpsOnHigherWages') return !epfTicked || !epsTicked;
    // D-145: the company switch is off, so the tick changes nothing until it is turned on.
    if (name === 'tdsApplicable') return companyTdsOff;
    return false;
  };

  return (
    <Drawer
      title="Edit statutory details"
      open={open}
      onClose={onClose}
      width={420}
      destroyOnHidden
      data-testid="statutory-profile-drawer"
      footer={
        <div style={{ textAlign: 'right' }}>
          <Space>
            <Button id="sp-btn-cancel" className="btn-spring" onClick={onClose}>
              Cancel
            </Button>
            <Button
              id="sp-btn-save"
              data-testid="statutory-profile-save"
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
      <Form form={form} layout="vertical" name="statutory-profile" initialValues={EMPTY_PROFILE}>
        {STATUTORY_TICKS.map((tick) => (
          <Form.Item
            key={tick.name}
            name={tick.name}
            valuePropName="checked"
            style={{ marginBottom: 4 }}
            extra={
              tick.name === 'tdsApplicable' && companyTdsOff ? (
                <span data-testid="tds-company-off-note">{TDS_COMPANY_OFF_NOTE}</span>
              ) : (
                tick.extra
              )
            }
          >
            <Checkbox id={TICK_IDS[tick.name]} disabled={disabledFor(tick.name)}>
              {tick.label}
            </Checkbox>
          </Form.Item>
        ))}
        <Form.Item
          name="pfAccountNumber"
          label="PF account number"
          rules={fieldRules.pfAccountNumber}
          style={{ marginTop: 12 }}
        >
          <Input id="sp-input-pf-account-number" maxLength={PF_ACCOUNT_MAX_LENGTH} placeholder="AA/AAA/0000000/XXX/0000000" />
        </Form.Item>
        <Form.Item name="uan" label="UAN" rules={fieldRules.uan} dependencies={['eligibleForPf']}>
          <Input id="sp-input-uan" maxLength={UAN_MAX_LENGTH} placeholder="12 digits" />
        </Form.Item>
        <Form.Item name="esiNumber" label="ESI number" rules={fieldRules.esiNumber} dependencies={['eligibleForEsi']}>
          <Input id="sp-input-esi-number" maxLength={ESI_NUMBER_MAX_LENGTH} placeholder="10 digits" />
        </Form.Item>
      </Form>
    </Drawer>
  );
}

StatutoryProfileDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  employeeId: PropTypes.string.isRequired,
  profile: PropTypes.object,
  onSaved: PropTypes.func,
};
