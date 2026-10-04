import PropTypes from 'prop-types';
import { useState } from 'react';
import { Alert, Button, DatePicker, Popconfirm, Space, Table } from 'antd';
import dayjs from 'dayjs';
import { EmployeePicker } from './EmployeePicker.jsx';
import { errorMessage, projectService } from './projectService.js';

/** Project team (W-48.1 §5): assignments with names; assign and remove need `canManage`. */
export function TeamTab({ projectId, assignments, canManage, onChange }) {
  const [employeeId, setEmployeeId] = useState(undefined);
  const [assignedOn, setAssignedOn] = useState(dayjs());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const assign = async () => {
    if (!employeeId) return;
    setBusy(true);
    setError(null);
    try {
      await projectService.assign(projectId, {
        employee_id: employeeId,
        assigned_on: assignedOn ? assignedOn.format('YYYY-MM-DD') : null,
      });
      setEmployeeId(undefined);
      onChange();
    } catch (e) {
      setError(errorMessage(e, 'Could not assign the employee'));
    } finally {
      setBusy(false);
    }
  };

  const remove = async (id) => {
    setError(null);
    try {
      await projectService.unassign(projectId, id);
      onChange();
    } catch (e) {
      setError(errorMessage(e, 'Could not remove the employee'));
    }
  };

  const columns = [
    {
      title: 'Employee',
      dataIndex: 'employee_name',
      render: (v, r) => v || r.employee_id,
    },
    { title: 'Assigned on', dataIndex: 'assigned_on', render: (v) => v || '-' },
  ];
  if (canManage) {
    columns.push({
      title: '',
      key: 'remove',
      render: (_, r) => (
        <Popconfirm title="Remove from the team?" onConfirm={() => remove(r.employee_id)}>
          <Button danger size="small">
            Remove
          </Button>
        </Popconfirm>
      ),
    });
  }

  return (
    <>
      {canManage && (
        <Space style={{ marginBottom: 16 }} wrap>
          <div style={{ width: 260 }}>
            <EmployeePicker value={employeeId} onChange={setEmployeeId} aria-label="Employee" />
          </div>
          <DatePicker aria-label="Assigned on" value={assignedOn} onChange={setAssignedOn} />
          <Button type="primary" onClick={assign} loading={busy} disabled={!employeeId}>
            Assign
          </Button>
        </Space>
      )}
      {error && <Alert type="error" message={error} style={{ marginBottom: 16 }} />}
      <Table rowKey="employee_id" columns={columns} dataSource={assignments} pagination={false} />
    </>
  );
}

TeamTab.propTypes = {
  projectId: PropTypes.string.isRequired,
  assignments: PropTypes.arrayOf(PropTypes.object).isRequired,
  canManage: PropTypes.bool,
  onChange: PropTypes.func.isRequired,
};
