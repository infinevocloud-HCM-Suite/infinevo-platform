import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import dayjs from 'dayjs';
import { Card, Tabs, Button, Tag, Space, Typography, Modal, Spin, Popconfirm, theme } from 'antd';
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
import { employeeInvitationService } from '../invitation/employeeInvitationService.js';
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
  const canInvite = useCan('core.employee.create');
  const canReadDocuments = useCan('core.document.read');

  const mastersLoadedAt = useSelector((state) => state.employee?.loadedAt);

  const [loading, setLoading] = useState(true);
  const [employee, setEmployee] = useState(null);
  const [terminateModalOpen, setTerminateModalOpen] = useState(false);
  const [access, setAccess] = useState(null);

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

  const employeeId = employee?.id;

  /** Portal access badge (W-73.3). A failure only hides the badge. */
  const loadAccess = useCallback(async () => {
    if (!employeeId) return;
    try {
      setAccess(await employeeService.getAccess(employeeId));
    } catch {
      setAccess(null);
    }
  }, [employeeId]);

  useEffect(() => {
    loadAccess();
  }, [loadAccess]);

  const handleInvite = async () => {
    try {
      await employeeInvitationService.create({ employeeId: employee.id, roleIds: [] });
      await successMsg('Invitation Sent', 'An email went to the work email.');
      await loadAccess();
    } catch (err) {
      await errorMsg(err);
    }
  };

  const handleResend = async () => {
    try {
      await employeeInvitationService.resend(access.invitationId);
      await successMsg('Invitation Sent', 'An email went to the work email.');
      await loadAccess();
    } catch (err) {
      await errorMsg(err);
    }
  };

  // W-73.4: the employee invitations list moved to Users & access (core.user.manage), so HR revokes here.
  const handleRevoke = async () => {
    try {
      await employeeInvitationService.revoke(access.invitationId);
      await successMsg('Invitation Revoked', 'The link in the email no longer works.');
      await loadAccess();
    } catch (err) {
      await errorMsg(err);
    }
  };

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
  // TERMINATED is terminal on the server (EmploymentStatus.allowedNext): only a suspended
  // employee can be reactivated, and an active or suspended one can be terminated.
  const canReactivate = employee.status === 'SUSPENDED';
  const canTerminate = employee.status === 'ACTIVE' || employee.status === 'SUSPENDED';

  const visibleTabs = employeeTabs.filter((tab) => {
    if (tab.key === 'identification' && !canReadIdentification) return false;
    if (tab.key === 'bank' && !canReadBank) return false;
    if (tab.key === 'reporting-line' && !canReadOrg) return false;
    if (tab.key === 'documents' && !canReadDocuments) return false;
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
                {access?.state === 'NONE' && (
                  <>
                    <Tag id="tag-access">No access</Tag>
                    {canInvite && (
                      <Button size="small" id="btn-invite-employee" onClick={handleInvite}>
                        Invite
                      </Button>
                    )}
                  </>
                )}
                {access?.state === 'INVITED' && (
                  <>
                    <Tag color="processing" id="tag-access">
                      {`Invited, expires ${dayjs(access.expiresAt).format('DD MMM YYYY')}`}
                    </Tag>
                    {canInvite && (
                      <>
                        <Button size="small" id="btn-resend-invitation" onClick={handleResend}>
                          Resend
                        </Button>
                        <Popconfirm
                          title="Revoke this invitation?"
                          okText="Revoke"
                          okButtonProps={{ danger: true, id: 'btn-confirm-revoke-invitation' }}
                          onConfirm={handleRevoke}
                        >
                          <Button size="small" danger id="btn-revoke-invitation">
                            Revoke
                          </Button>
                        </Popconfirm>
                      </>
                    )}
                  </>
                )}
                {access?.state === 'ACTIVE' && (
                  <>
                    <Tag color="success" id="tag-access">Active</Tag>
                    {(access.roles || []).map((r) => (
                      <Tag key={r.id}>{r.code}</Tag>
                    ))}
                  </>
                )}
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
