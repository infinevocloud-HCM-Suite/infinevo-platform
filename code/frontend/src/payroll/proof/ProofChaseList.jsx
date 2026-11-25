import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Alert, Button, Space, Typography, Modal, InputNumber, Input, Card } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { formatDate } from '@shared/ui/format.js';
import { readError } from '../tax/apiError';
import { proofService } from './proofService';
import { ProofTable } from './ProofTable';

const { Text } = Typography;

/** The statuses the chase list shows: every employee who still owes a proof (W-34.3 `POI_PENDING`). */
export const PENDING_STATUSES = ['NOT_STARTED', 'DRAFT', 'REJECTED'];

const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

/** How a rule reads: "7 days before the due date at 10:00, then every 2 days". */
export function describeRule(rule) {
  const time = String(rule.send_at_local_time || '').slice(0, 5);
  const repeat = rule.repeat_every_days ? `, then every ${rule.repeat_every_days} days` : '';
  const state = rule.is_active === false ? ' (paused)' : '';
  return `${rule.offset_days} days before the due date at ${time}${repeat}${state}`;
}

/**
 * Who has not submitted proof yet, and the reminder that chases them (W-34.3). The reminder is a
 * `POI_REMINDER` rule the worker's sweep sends (W-20.2); whoever may manage reminder rules sets it here.
 */
export function ProofChaseList({ fy, dueDate, onOpen }) {
  const canManageReminders = useCan('core.reminder_rule.manage');
  const [rules, setRules] = useState([]);
  const [ruleError, setRuleError] = useState(null);
  const [editing, setEditing] = useState(false);
  const [offsetDays, setOffsetDays] = useState(7);
  const [sendAt, setSendAt] = useState('10:00');
  const [repeatEveryDays, setRepeatEveryDays] = useState(2);
  const [problem, setProblem] = useState(null);
  const [saving, setSaving] = useState(false);

  const loadRules = useCallback(async () => {
    if (!canManageReminders) return;
    setRuleError(null);
    try {
      setRules(await proofService.reminderRules());
    } catch (err) {
      setRules([]);
      setRuleError(readError(err, 'Failed to load the reminder rule').message);
    }
  }, [canManageReminders]);

  useEffect(() => {
    loadRules();
  }, [loadRules]);

  const save = async () => {
    if (!Number.isInteger(offsetDays) || offsetDays < 0) {
      setProblem('Days before the due date must be 0 or more');
      return;
    }
    if (!TIME_PATTERN.test(sendAt)) {
      setProblem('Send time is HH:MM, for example 10:00');
      return;
    }
    if (repeatEveryDays !== null && (!Number.isInteger(repeatEveryDays) || repeatEveryDays < 1)) {
      setProblem('Repeat every must be 1 day or more, or left empty');
      return;
    }
    setSaving(true);
    try {
      await proofService.createReminderRule({ offsetDays, sendAt, repeatEveryDays });
      setEditing(false);
      successMsg('Reminder Set', 'Employees who still owe proof will be reminded before the due date.');
      await loadRules();
    } catch (err) {
      errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <Card size="small" style={{ marginBottom: 12 }} data-testid="proof-reminder">
        <Space direction="vertical" style={{ width: '100%' }}>
          <Space>
            <BellOutlined />
            <Text strong>Reminder</Text>
            <Text type="secondary">
              {dueDate ? `Proof is due by ${formatDate(dueDate)}.` : 'No proof due date is set for this year.'}
            </Text>
          </Space>
          {!canManageReminders && (
            <Text type="secondary">
              Reminders go out from the proof reminder rule, which an administrator who manages reminders sets.
            </Text>
          )}
          {canManageReminders && ruleError && <Alert type="warning" showIcon message={ruleError} />}
          {canManageReminders && !ruleError && rules.length === 0 && (
            <Text type="secondary" data-testid="proof-reminder-none">
              No reminder is set: nobody on this list is reminded.
            </Text>
          )}
          {canManageReminders &&
            rules.map((rule) => (
              <Text key={rule.id} data-testid="proof-reminder-rule">
                Reminds each employee {describeRule(rule)}.
              </Text>
            ))}
          {canManageReminders && rules.length === 0 && !ruleError && (
            <Button
              icon={<BellOutlined />}
              onClick={() => {
                setProblem(null);
                setEditing(true);
              }}
              data-testid="proof-reminder-open"
            >
              Set up reminder
            </Button>
          )}
        </Space>
      </Card>

      <ProofTable fy={fy} statuses={PENDING_STATUSES} defaultStatus="NOT_STARTED" onOpen={onOpen} openLabel="View" />

      <Modal
        open={editing}
        title="Proof of investment reminder"
        onCancel={() => setEditing(false)}
        onOk={save}
        okText="Save reminder"
        confirmLoading={saving}
        destroyOnClose
      >
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text>Start how many days before the due date</Text>
          <InputNumber min={0} precision={0} value={offsetDays} onChange={setOffsetDays} aria-label="Days before due date" />
          <Text>Send at (HH:MM, company time)</Text>
          <Input value={sendAt} onChange={(e) => setSendAt(e.target.value)} aria-label="Send time" style={{ width: 120 }} />
          <Text>Repeat every (days, empty for once)</Text>
          <InputNumber min={1} precision={0} value={repeatEveryDays} onChange={setRepeatEveryDays} aria-label="Repeat every" />
          {problem && <Alert type="error" showIcon message={problem} data-testid="reminder-problem" />}
        </Space>
      </Modal>
    </>
  );
}

ProofChaseList.propTypes = {
  fy: PropTypes.string.isRequired,
  dueDate: PropTypes.string,
  onOpen: PropTypes.func.isRequired,
};

export default ProofChaseList;
