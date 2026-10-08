import { useState, useEffect, useCallback, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Card,
  Progress,
  List,
  Tag,
  Button,
  Typography,
  Space,
  Spin,
  Alert,
} from 'antd';
import { useCan, useHasModule, NotEntitled } from '@shell/screens';
import { setupService } from './setupService.js';
import { stepLinks, stepActions } from './stepLinks.js';
import { SkipStepModal } from './SkipStepModal.jsx';

const { Title, Text, Paragraph } = Typography;

function formatDateTime(isoString) {
  if (!isoString) return '';
  try {
    const d = new Date(isoString);
    return isNaN(d.getTime()) ? isoString : d.toLocaleString();
  } catch {
    return isoString;
  }
}

function getStepStatusTag(step) {
  if (step.completed) {
    return <Tag color="success">Done</Tag>;
  }
  if (step.skipped) {
    return <Tag color="warning">Skipped</Tag>;
  }
  if (step.newStep) {
    return <Tag color="processing">New</Tag>;
  }
  return <Tag>To do</Tag>;
}

function renderStepDetails(step) {
  if (step.completed) {
    return step.completedAt ? (
      <Text type="secondary" style={{ fontSize: 12 }}>
        Completed on {formatDateTime(step.completedAt)}
      </Text>
    ) : null;
  }
  if (step.skipped) {
    return step.skipReason ? (
      <Text type="secondary" style={{ fontSize: 12 }}>
        Reason: {step.skipReason}
      </Text>
    ) : null;
  }
  if (step.newStep) {
    return (
      <Text type="secondary" style={{ fontSize: 12 }}>
        New step — not counted in progress percentage yet
      </Text>
    );
  }
  return null;
}

export function SetupChecklist() {
  const navigate = useNavigate();
  const location = useLocation();
  const canRead = useCan('core.tenant.read');
  const canManageTenant = useCan('core.tenant.manage');
  const canReadOrg = useCan('core.org.read');
  const canReadEmployee = useCan('core.employee.read');
  const hasHrms = useHasModule('HRMS');
  const hasPayroll = useHasModule('PAYROLL');

  const actionPermissions = useMemo(
    () => ({
      'core.org.read': canReadOrg,
      'core.employee.read': canReadEmployee,
    }),
    [canReadOrg, canReadEmployee]
  );

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [checklistData, setChecklistData] = useState(null);
  const [skipModalStep, setSkipModalStep] = useState(null);

  const fetchChecklist = useCallback(async () => {
    if (!canRead) return;
    try {
      setLoading(true);
      setError(null);
      const data = await setupService.get();
      setChecklistData(data);
    } catch (err) {
      setError(
        err?.response?.data?.message ||
          err?.response?.data?.error ||
          err?.message ||
          'Failed to load setup checklist.'
      );
    } finally {
      setLoading(false);
    }
  }, [canRead]);

  useEffect(() => {
    fetchChecklist();
  }, [fetchChecklist]);

  // `/setup#setup-step-row-<code>` (the welcome page's links, W-73.8): the row exists only once the
  // steps have loaded, and the router does not scroll to a hash by itself.
  useEffect(() => {
    if (!checklistData || !location.hash) return;
    const row = document.getElementById(location.hash.slice(1));
    if (row && typeof row.scrollIntoView === 'function') row.scrollIntoView({ block: 'start' });
  }, [checklistData, location.hash]);

  const groups = useMemo(() => {
    if (!checklistData?.steps) return [];

    const grouped = {};
    for (const step of checklistData.steps) {
      const mod = step.module;
      if (!mod) {
        if (!grouped.core) {
          grouped.core = { key: 'core', title: 'Core Setup', steps: [] };
        }
        grouped.core.steps.push(step);
      } else if (mod === 'HRMS' && hasHrms) {
        if (!grouped.hrms) {
          grouped.hrms = { key: 'hrms', title: 'HRMS Setup', steps: [] };
        }
        grouped.hrms.steps.push(step);
      } else if (mod === 'PAYROLL' && hasPayroll) {
        if (!grouped.payroll) {
          grouped.payroll = { key: 'payroll', title: 'Payroll Setup', steps: [] };
        }
        grouped.payroll.steps.push(step);
      }
    }

    const order = ['core', 'hrms', 'payroll'];
    return order
      .filter((k) => grouped[k] && grouped[k].steps.length > 0)
      .map((k) => {
        grouped[k].steps.sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0));
        return grouped[k];
      });
  }, [checklistData, hasHrms, hasPayroll]);

  if (!canRead) {
    return <NotEntitled />;
  }

  if (loading && !checklistData) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (error && !checklistData) {
    return (
      <div style={{ padding: 24, maxWidth: 1000, margin: '0 auto' }}>
        <Alert
          type="error"
          message="Error Loading Setup Checklist"
          description={error}
          showIcon
          action={
            <Button type="primary" onClick={fetchChecklist}>
              Retry
            </Button>
          }
        />
      </div>
    );
  }

  const progressPercentage = checklistData?.progressPercentage ?? 0;

  return (
    <div style={{ padding: '24px', maxWidth: 1000, margin: '0 auto' }}>
      <Title level={2} id="heading-setup-checklist">
        Setup Checklist
      </Title>
      <Paragraph type="secondary">
        Complete these recommended setup steps to configure your organization on Infinevo.
      </Paragraph>

      {checklistData && (
        <Card style={{ marginBottom: 24 }} id="card-setup-progress">
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Text strong style={{ fontSize: 16 }}>
                Setup Progress: {progressPercentage}%
              </Text>
              <Text type="secondary">
                {checklistData.completedCount} completed, {checklistData.skippedCount} skipped of{' '}
                {checklistData.totalCount} total
              </Text>
            </div>
            <Progress percent={progressPercentage} status="active" />
            {checklistData.newCount > 0 && (
              <Alert
                type="info"
                showIcon
                message={`${checklistData.newCount} new step(s) added recently. New steps are listed below but excluded from progress calculation until addressed.`}
                id="alert-new-steps-info"
              />
            )}
          </Space>
        </Card>
      )}

      {groups.map((group) => (
        <Card
          key={group.key}
          title={group.title}
          style={{ marginBottom: 24 }}
          id={`card-group-${group.key}`}
        >
          <List
            itemLayout="horizontal"
            dataSource={group.steps}
            renderItem={(step) => {
              const path = stepLinks[step.code];
              const requiredAction = stepActions[step.code];
              const canOpen = Boolean(
                path && (!requiredAction || actionPermissions[requiredAction])
              );
              const canSkip = canManageTenant && !step.completed && !step.skipped;

              const actions = [];
              if (canOpen) {
                actions.push(
                  <Button
                    key="open"
                    type="link"
                    onClick={() => navigate(path)}
                    id={`btn-open-step-${step.code.toLowerCase().replace(/_/g, '-')}`}
                  >
                    Open
                  </Button>
                );
              }
              if (canSkip) {
                actions.push(
                  <Button
                    key="skip"
                    danger
                    onClick={() => setSkipModalStep(step)}
                    id={`btn-skip-step-${step.code.toLowerCase().replace(/_/g, '-')}`}
                  >
                    Skip
                  </Button>
                );
              }

              return (
                <List.Item
                  key={step.code}
                  actions={actions}
                  id={`setup-step-row-${step.code.toLowerCase().replace(/_/g, '-')}`}
                >
                  <List.Item.Meta
                    title={
                      <Space align="center">
                        <Text strong>{step.label}</Text>
                        {getStepStatusTag(step)}
                      </Space>
                    }
                    description={renderStepDetails(step)}
                  />
                </List.Item>
              );
            }}
          />
        </Card>
      ))}

      {skipModalStep && (
        <SkipStepModal
          open={Boolean(skipModalStep)}
          step={skipModalStep}
          onClose={() => setSkipModalStep(null)}
          onSuccess={fetchChecklist}
        />
      )}
    </div>
  );
}
