import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Button, Space } from 'antd';

import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { LeaveRequestFields, normalizeDocumentIds } from './LeaveRequestFields.jsx';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';



export function RecordLeave() {
  const navigate = useNavigate();
  const canManage = useCan('core.leave.manage');

  const [form] = Form.useForm();
  const [eligibleTypes, setEligibleTypes] = useState([]);
  const [isHalfDay, setIsHalfDay] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleEmployeeChange = useCallback(async (employeeId) => {
    form.setFieldsValue({ leaveTypeId: undefined });
    if (!employeeId) {
      setEligibleTypes([]);
      return;
    }
    try {
      const types = await leaveTypeService.eligible(employeeId);
      setEligibleTypes(Array.isArray(types) ? types : []);
    } catch {
      setEligibleTypes([]);
    }
  }, [form]);

  const handleSubmit = async (values) => {
    setSubmitting(true);
    try {
      const payload = {
        employeeId: values.employeeId,
        leaveTypeId: values.leaveTypeId,
        fromDate: values.fromDate ? values.fromDate.format('YYYY-MM-DD') : null,
        toDate: values.toDate ? values.toDate.format('YYYY-MM-DD') : null,
        isHalfDay: Boolean(values.isHalfDay),
        halfDayPeriod: values.isHalfDay ? values.halfDayPeriod : null,
        reason: values.reason,
        documentIds: normalizeDocumentIds(values.documentIds),
      };

      const res = await leaveRequestService.onBehalf(payload);
      const days = res?.workingDays ?? '';
      await successMsg(
        `Leave recorded successfully (${days ? `${days} working day(s)` : 'Approved'})`
      );
      navigate('/leave/requests');
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  if (!canManage) {
    return <NotEntitled />;
  }

  return (
    <Card variant="borderless" style={{ maxWidth: 800 }}>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <PageHeader
          title="Record Leave on Behalf of Employee"
          subtitle="Recording leave on behalf creates an immediately approved leave request with no approval workflow."
          onBack={() => navigate('/leave/requests')}
          backLabel="Back"
        />

        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ isHalfDay: false }}
        >
          <LeaveRequestFields
            form={form}
            leaveTypes={eligibleTypes}
            onEmployeeChange={handleEmployeeChange}
            isHalfDay={isHalfDay}
            onHalfDayChange={setIsHalfDay}
          />

          <Form.Item style={{ marginTop: 24 }}>
            <Space>
              <Button type="primary" htmlType="submit" loading={submitting}>
                Record Leave
              </Button>
              <Button onClick={() => navigate('/leave/requests')}>
                Cancel
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Space>
    </Card>
  );
}
