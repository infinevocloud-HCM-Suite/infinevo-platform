import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Button, Space, Typography, Row, Col } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { LeaveRequestFields } from './LeaveRequestFields.jsx';
import { leaveRequestService } from './leaveRequestService.js';
import { leaveTypeService } from './leaveTypeService.js';
import { employeeService } from '../employee/employeeService.js';

const { Title, Text } = Typography;

export function RecordLeave() {
  const navigate = useNavigate();
  const canManage = useCan('core.leave.manage');

  const [form] = Form.useForm();
  const [employees, setEmployees] = useState([]);
  const [eligibleTypes, setEligibleTypes] = useState([]);
  const [isHalfDay, setIsHalfDay] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    employeeService
      .list({ size: 1000 })
      .then((data) => {
        const list = Array.isArray(data) ? data : data?.content || [];
        setEmployees(list);
      })
      .catch(() => {});
  }, []);

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
        documentIds: values.documentIds || [],
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
    <Card style={{ margin: 24, maxWidth: 800 }}>
      <Space direction="vertical" style={{ width: '100%' }} size="large">
        <Row justify="space-between" align="middle">
          <Col>
            <Space align="center">
              <Button
                icon={<ArrowLeftOutlined />}
                onClick={() => navigate('/leave/requests')}
              >
                Back
              </Button>
              <Title level={4} style={{ margin: 0 }}>
                Record Leave on Behalf of Employee
              </Title>
            </Space>
          </Col>
        </Row>

        <Text type="secondary">
          Recording leave on behalf creates an immediately approved leave request with no approval
          workflow.
        </Text>

        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{ isHalfDay: false }}
        >
          <LeaveRequestFields
            form={form}
            employees={employees}
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
