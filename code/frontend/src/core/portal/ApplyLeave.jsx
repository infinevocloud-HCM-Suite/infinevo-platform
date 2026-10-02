import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { useNavigate } from 'react-router-dom';
import { Card, Form, Button, Space, Typography, Alert, Spin, theme } from 'antd';
import { ArrowLeftOutlined, SaveOutlined, SendOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { portalService } from '@shell/portal/portalService.js';
import { leaveTypeService } from '../leave/leaveTypeService.js';
import { leaveRequestService } from '../leave/leaveRequestService.js';
import { LeaveRequestFields } from '../leave/LeaveRequestFields.jsx';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title } = Typography;

export function ApplyLeave({ employeeId: propEmployeeId, initialValues, onSuccess }) {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const canApply = useCan('core.leave.apply');

  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [panels, setPanels] = useState(null);
  const [eligibleTypes, setEligibleTypes] = useState([]);
  const [isHalfDay, setIsHalfDay] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    let active = true;

    async function init() {
      setLoading(true);
      setErrorMessage(null);
      try {
        const [panelsData, profileData] = await Promise.all([
          portalService.getPanels().catch(() => []),
          propEmployeeId ? Promise.resolve({ id: propEmployeeId }) : portalService.getProfile().catch(() => null),
        ]);

        if (!active) return;

        const resolvedPanels = Array.isArray(panelsData) ? panelsData : [];
        setPanels(resolvedPanels);

        const meId = profileData?.id || propEmployeeId;

        if (meId) {
          const types = await leaveTypeService.eligible(meId);
          if (active) {
            setEligibleTypes(Array.isArray(types) ? types : []);
          }
        }
      } catch (err) {
        if (active) {
          setErrorMessage(err?.response?.data?.message || err?.message || 'Failed to initialize leave application');
        }
      } finally {
        if (active) setLoading(false);
      }
    }

    init();

    return () => {
      active = false;
    };
  }, [propEmployeeId]);

  if (!canApply) {
    return <NotEntitled action="core.leave.apply" />;
  }

  if (!loading && panels && !panels.some((p) => p.code === 'leave' || p.key === 'leave')) {
    return <NotEntitled action="leave module / panel" />;
  }

  const handleFinish = async (values, submitNow) => {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      const fromDate = values.fromDate ? dayjs(values.fromDate).format('YYYY-MM-DD') : null;
      const toDate = values.toDate ? dayjs(values.toDate).format('YYYY-MM-DD') : null;

      const payload = {
        leaveTypeId: values.leaveTypeId,
        fromDate,
        toDate,
        isHalfDay: !!values.isHalfDay,
        halfDayPeriod: values.isHalfDay ? values.halfDayPeriod : null,
        reason: values.reason,
        submit: submitNow,
      };

      await leaveRequestService.create(payload);
      await successMsg(
        submitNow ? 'Leave Request Submitted' : 'Draft Saved',
        submitNow ? 'Your leave request has been submitted for approval.' : 'Your leave request draft has been saved.'
      );

      if (onSuccess) {
        onSuccess();
      } else {
        navigate('/me/leave');
      }
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to save leave request';
      setErrorMessage(msg);
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: token.paddingLG }} data-testid="apply-leave-page">
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Space align="center" style={{ marginBottom: token.marginMD }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/me/leave')}>
            Back to Leave
          </Button>
          <Title level={3} style={{ margin: 0 }}>
            Apply for Leave
          </Title>
        </Space>

        {loading ? (
          <Card style={{ borderRadius: token.borderRadiusLG, textAlign: 'center', padding: token.paddingXL }}>
            <Spin size="large" tip="Loading eligible leave types..." />
          </Card>
        ) : (
          <Card
            style={{
              borderRadius: token.borderRadiusLG,
              boxShadow: token.boxShadowTertiary,
            }}
          >
            {errorMessage && (
              <Alert
                type="error"
                showIcon
                message="Leave Application Error"
                description={errorMessage}
                style={{ marginBottom: token.marginLG }}
                closable
                onClose={() => setErrorMessage(null)}
              />
            )}

            <Form
              form={form}
              layout="vertical"
              initialValues={{ isHalfDay: false, ...initialValues }}
              onFinish={(vals) => handleFinish(vals, true)}
            >
              <LeaveRequestFields
                isEmployeeView={true}
                leaveTypes={eligibleTypes}
                isHalfDay={isHalfDay}
                onHalfDayChange={(checked) => setIsHalfDay(checked)}
              />

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: token.marginSM, marginTop: token.marginLG }}>
                <Button
                  icon={<SaveOutlined />}
                  loading={submitting}
                  onClick={() => {
                    form.validateFields()
                      .then((vals) => handleFinish(vals, false))
                      .catch(() => {});
                  }}
                  id="btn-save-draft"
                >
                  Save Draft
                </Button>
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  loading={submitting}
                  htmlType="submit"
                  id="btn-submit-leave"
                >
                  Submit
                </Button>
              </div>
            </Form>
          </Card>
        )}
      </Space>
    </div>
  );
}

ApplyLeave.propTypes = {
  employeeId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  initialValues: PropTypes.object,
  onSuccess: PropTypes.func,
};

export default ApplyLeave;
