import { useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Modal, DatePicker, Typography, Space } from 'antd';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';
import { employeeService } from './employeeService.js';

const { Text } = Typography;

export function TerminateModal({ open, employee, onCancel, onSuccess }) {
  const [terminationDate, setTerminationDate] = useState('');
  const [loading, setLoading] = useState(false);

  const handleOk = async () => {
    if (!terminationDate) {
      return;
    }
    setLoading(true);
    try {
      const payload = {
        employeeNumber: employee.employeeNumber,
        firstName: employee.firstName,
        middleName: employee.middleName,
        lastName: employee.lastName,
        gender: employee.gender,
        dateOfJoining: employee.dateOfJoining,
        terminationDate,
        status: 'TERMINATED',
        workEmail: employee.workEmail,
        mobile: employee.mobile,
        portalEnabled: employee.portalEnabled,
        departmentId: employee.departmentId || null,
        designationId: employee.designationId || null,
        workLocationId: employee.workLocationId || null,
      };

      const updated = await employeeService.update(employee.id, payload);
      await successMsg('Employee Terminated', `Employee ${employee.employeeNumber} status updated to Terminated.`);
      onSuccess(updated);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title="Terminate Employee"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={loading}
      okButtonProps={{ danger: true, disabled: !terminationDate, id: 'btn-confirm-terminate' }}
      destroyOnHidden={true}
    >
      <Space direction="vertical" style={{ width: '100%', marginTop: 12 }}>
        <Text>
          Please select the official termination date for{' '}
          <Text strong>{[employee?.firstName, employee?.lastName].filter(Boolean).join(' ')}</Text>.
        </Text>
        <div>
          <label htmlFor="picker-terminationDate" style={{ display: 'block', marginBottom: 4 }}>
            <Text strong>Termination Date *</Text>
          </label>
          <DatePicker
            id="picker-terminationDate"
            style={{ width: '100%' }}
            value={terminationDate ? dayjs(terminationDate) : null}
            onChange={(_, dateStr) => setTerminationDate(dateStr)}
          />
        </div>
      </Space>
    </Modal>
  );
}

TerminateModal.propTypes = {
  open: PropTypes.bool,
  employee: PropTypes.shape({
    id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    employeeNumber: PropTypes.string,
    firstName: PropTypes.string,
    middleName: PropTypes.string,
    lastName: PropTypes.string,
    gender: PropTypes.string,
    dateOfJoining: PropTypes.string,
    workEmail: PropTypes.string,
    mobile: PropTypes.string,
    portalEnabled: PropTypes.bool,
    departmentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    designationId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    workLocationId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  }),
  onCancel: PropTypes.func,
  onSuccess: PropTypes.func,
};
