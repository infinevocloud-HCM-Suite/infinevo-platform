import { useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Modal, DatePicker, Select, Input, Typography, Space } from 'antd';
import { errorMsg, successMsg } from '@shared/ui/msgHelper.js';
import { employeeService } from './employeeService.js';

const { Text } = Typography;

export const TERMINATION_REASONS = [
  { value: 'RESIGNATION', label: 'Resignation' },
  { value: 'TERMINATION', label: 'Involuntary Termination' },
  { value: 'RETIREMENT', label: 'Retirement' },
  { value: 'CONTRACT_END', label: 'End of Contract' },
  { value: 'OTHER', label: 'Other' },
];

export function TerminateModal({ open, employee, onCancel, onSuccess }) {
  const [terminationDate, setTerminationDate] = useState('');
  const [reason, setReason] = useState('');
  const [remarks, setRemarks] = useState('');
  const [loading, setLoading] = useState(false);

  const handleCancel = () => {
    setTerminationDate('');
    setReason('');
    setRemarks('');
    onCancel();
  };

  const handleOk = async () => {
    if (!terminationDate || !reason) {
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
        terminationReason: reason,
        terminationRemarks: remarks || null,
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
      setTerminationDate('');
      setReason('');
      setRemarks('');
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
      onCancel={handleCancel}
      confirmLoading={loading}
      okButtonProps={{ danger: true, disabled: !terminationDate || !reason, id: 'btn-confirm-terminate' }}
      destroyOnHidden={true}
    >
      <Space direction="vertical" style={{ width: '100%', marginTop: 12 }} size="middle">
        <Text>
          Please select the official termination details for{' '}
          <Text strong>{[employee?.firstName, employee?.lastName].filter(Boolean).join(' ')}</Text>.
        </Text>
        <div>
          <label htmlFor="picker-terminationDate" style={{ display: 'block', marginBottom: 4 }}>
            <Text strong>Last Working Day / Termination Date *</Text>
          </label>
          <DatePicker
            id="picker-terminationDate"
            style={{ width: '100%' }}
            value={terminationDate ? dayjs(terminationDate) : null}
            onChange={(_, dateStr) => setTerminationDate(dateStr)}
          />
        </div>
        <div>
          <label htmlFor="select-terminationReason" style={{ display: 'block', marginBottom: 4 }}>
            <Text strong>Termination Reason *</Text>
          </label>
          <Select
            id="select-terminationReason"
            style={{ width: '100%' }}
            placeholder="Select Reason"
            value={reason || undefined}
            onChange={(val) => setReason(val)}
            options={TERMINATION_REASONS}
          />
        </div>
        <div>
          <label htmlFor="input-terminationRemarks" style={{ display: 'block', marginBottom: 4 }}>
            <Text strong>Remarks / Exit Notes</Text>
          </label>
          <Input.TextArea
            id="input-terminationRemarks"
            rows={3}
            placeholder="Enter reason notes or handover remarks..."
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
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
