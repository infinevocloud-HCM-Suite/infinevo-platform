import PropTypes from 'prop-types';
import { Form, Select, DatePicker, Switch, Input, Row, Col } from 'antd';

const { TextArea } = Input;

export function LeaveRequestFields({
  isEmployeeView = false,
  employees = [],
  leaveTypes = [],
  onEmployeeChange,
  isHalfDay = false,
  onHalfDayChange,
}) {
  return (
    <>
      {!isEmployeeView && (
        <Form.Item
          name="employeeId"
          label="Employee"
          rules={[{ required: true, message: 'Please select an employee' }]}
        >
          <Select
            placeholder="Select employee"
            showSearch
            optionFilterProp="label"
            onChange={onEmployeeChange}
            options={employees.map((emp) => ({
              value: emp.id,
              label: `${emp.firstName || ''} ${emp.lastName || ''} (${emp.employeeNumber || emp.id})`.trim(),
            }))}
          />
        </Form.Item>
      )}

      <Form.Item
        name="leaveTypeId"
        label="Leave Type"
        rules={[{ required: true, message: 'Please select a leave type' }]}
      >
        <Select
          placeholder="Select leave type"
          showSearch
          optionFilterProp="label"
          options={leaveTypes.map((t) => ({
            value: t.id,
            label: `${t.name} (${t.code})`,
          }))}
        />
      </Form.Item>

      <Row gutter={16}>
        <Col span={12}>
          <Form.Item
            name="fromDate"
            label="From Date"
            rules={[{ required: true, message: 'Please select start date' }]}
          >
            <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item
            name="toDate"
            label="To Date"
            rules={[{ required: true, message: 'Please select end date' }]}
          >
            <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
          </Form.Item>
        </Col>
      </Row>

      <Row gutter={16} align="middle">
        <Col span={8}>
          <Form.Item name="isHalfDay" label="Half Day" valuePropName="checked">
            <Switch onChange={onHalfDayChange} />
          </Form.Item>
        </Col>
        {isHalfDay && (
          <Col span={16}>
            <Form.Item
              name="halfDayPeriod"
              label="Period"
              rules={[{ required: true, message: 'Select half day period' }]}
            >
              <Select
                placeholder="Select period"
                options={[
                  { value: 'FIRST', label: 'First Half' },
                  { value: 'SECOND', label: 'Second Half' },
                ]}
              />
            </Form.Item>
          </Col>
        )}
      </Row>

      <Form.Item
        name="reason"
        label="Reason"
        rules={[{ required: true, message: 'Please enter a reason' }]}
      >
        <TextArea rows={3} placeholder="Enter reason for leave" />
      </Form.Item>
    </>
  );
}

LeaveRequestFields.propTypes = {
  isEmployeeView: PropTypes.bool,
  employees: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      firstName: PropTypes.string,
      lastName: PropTypes.string,
      employeeNumber: PropTypes.string,
    })
  ),
  leaveTypes: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string.isRequired,
      name: PropTypes.string.isRequired,
      code: PropTypes.string.isRequired,
    })
  ),
  onEmployeeChange: PropTypes.func,
  isHalfDay: PropTypes.bool,
  onHalfDayChange: PropTypes.func,
};
