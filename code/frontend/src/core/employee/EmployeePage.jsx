import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Card, Tabs, Button, Tag, Space, Typography, Modal, Spin, theme } from 'antd';
import {
  ArrowLeftOutlined,
  StopOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';
import { setMasters } from './employeeSlice.js';
import { employeeTabs } from './employeeTabs.jsx';
import { TerminateModal } from './TerminateModal.jsx';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

const statusColorMap = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  TERMINATED: 'error',
};

export function EmployeePage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { token } = theme.useToken();

  const canUpdate = useCan('core.employee.update');
  const canDelete = useCan('core.employee.delete');
  const canReadIdentification = useCan('core.employee_identification.read');
  const canReadBank = useCan('core.employee_bank.read');
  const canReadOrg = useCan('core.org.read');

  const mastersLoadedAt = useSelector((state) => state.employee?.loadedAt);

  const [loading, setLoading] = useState(true);
  const [employee, setEmployee] = useState(null);
  const [terminateModalOpen, setTerminateModalOpen] = useState(false);

  const loadEmployee = useCallback(async () => {
    setLoading(true);
    try {
      const data = await employeeService.get(id);
      setEmployee(data);
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadEmployee();
  }, [loadEmployee]);

  useEffect(() => {
    if (!mastersLoadedAt) {
      orgMasterService.all().then((res) => {
        dispatch(setMasters(res));
      }).catch(() => {});
    }
  }, [dispatch, mastersLoadedAt]);

  const handleDelete = () => {
    Modal.confirm({
      title: 'Delete Employee',
      content: `Are you sure you want to soft-delete employee ${employee.employeeNumber}? They will no longer appear in default searches.`,
      okText: 'Delete',
      okType: 'danger',
      onOk: async () => {
        try {
          await employeeService.remove(employee.id);
          await successMsg('Employee Deleted', `Employee ${employee.employeeNumber} soft-deleted.`);
          navigate('/employees');
        } catch (err) {
          await errorMsg(err);
        }
      },
    });
  };

  const handleReactivate = () => {
    Modal.confirm({
      title: 'Reactivate Employee',
      content: `Are you sure you want to reactivate employee ${employee.employeeNumber}? Their status will be set back to ACTIVE.`,
      okText: 'Reactivate',
      onOk: async () => {
        try {
          const updated = await employeeService.update(employee.id, {
            ...employee,
            status: 'ACTIVE',
            terminationDate: null,
          });
          await successMsg('Employee Reactivated', `Employee ${employee.employeeNumber} reactivated successfully.`);
          setEmployee(updated);
        } catch (err) {
          await errorMsg(err);
        }
      },
    });
  };

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!employee) {
    return (
      <Card bordered={false}>
        <Text type="secondary">Employee record could not be loaded.</Text>
      </Card>
    );
  }

  const fullName = [employee.firstName, employee.middleName, employee.lastName].filter(Boolean).join(' ');
  // Both SUSPENDED and TERMINATED employees can be reactivated by HR
  const canReactivate = employee.status === 'SUSPENDED' || employee.status === 'TERMINATED';
  const canTerminate = employee.status === 'ACTIVE' || employee.status === 'SUSPENDED';

  const visibleTabs = employeeTabs.filter((tab) => {
    if (tab.key === 'identification' && !canReadIdentification) return false;
    if (tab.key === 'bank' && !canReadBank) return false;
    if (tab.key === 'reporting-line' && !canReadOrg) return false;
    return true;
  });

  const tabItems = visibleTabs.map((tab) => ({
    key: tab.key,
    label: tab.label,
    children: tab.render({ employee, setEmployee }),
  }));

  return (
    <div>
      {/* Header bar */}
      <Card variant="borderless" style={{ marginBottom: token.marginLG, borderRadius: token.borderRadiusLG }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: token.marginSM }}>
          <Space size="middle">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/employees')}
            />
            <div>
              <Space align="center" size="small">
                <Title level={4} style={{ margin: 0 }}>{fullName}</Title>
                <Tag color={statusColorMap[employee.status] || 'default'}>{employee.status}</Tag>
              </Space>
              <Text type="secondary" style={{ display: 'block', marginTop: 2 }}>
                Emp ID: {employee.employeeNumber} {employee.workEmail ? `• ${employee.workEmail}` : ''}
              </Text>
            </div>
          </Space>

          {/* Action buttons */}
          <Space size="small">
            {canUpdate && canReactivate && (
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={handleReactivate}
                id="btn-reactivate-employee"
              >
                Reactivate
              </Button>
            )}

            {canUpdate && canTerminate && (
              <Button
                danger
                icon={<StopOutlined />}
                onClick={() => setTerminateModalOpen(true)}
                id="btn-terminate-employee"
              >
                Terminate
              </Button>
            )}

            {canDelete && (
              <Button
                danger
                type="dashed"
                icon={<DeleteOutlined />}
                onClick={handleDelete}
                id="btn-delete-employee"
              >
                Delete
              </Button>
            )}
          </Space>
        </div>
      </Card>

      {/* Tabs */}
      <Card variant="borderless" style={{ borderRadius: token.borderRadiusLG }}>
        <Tabs defaultActiveKey="overview" items={tabItems} destroyInactiveTabPane={false} />
      </Card>

      <TerminateModal
        open={terminateModalOpen}
        employee={employee}
        onCancel={() => setTerminateModalOpen(false)}
        onSuccess={(updated) => {
          setEmployee(updated);
          setTerminateModalOpen(false);
        }}
      />
    </div>
  );
}
