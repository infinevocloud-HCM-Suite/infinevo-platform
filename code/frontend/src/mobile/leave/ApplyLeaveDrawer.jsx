import { useState, useEffect, useMemo } from 'react';
import PropTypes from 'prop-types';
import {
  Drawer,
  Form,
  Select,
  Switch,
  Radio,
  Input,
  Button,
  Alert,
  Typography,
  Tag,
  DatePicker,
} from 'antd';
import { SendOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { mobileLeaveService } from './mobileLeaveService.js';
import { successMsg, errorMsg } from '../../shared/ui/msgHelper.js';

const { Text } = Typography;
const { TextArea } = Input;

/**
 * Mobile Apply Leave Bottom Sheet (W-98.3 §4.2)
 * Native touch-friendly bottom drawer for applying leave with instant balance validation.
 */
export function ApplyLeaveDrawer({ open, onClose, balances = [], onSuccess, preselectedTypeId = null }) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [isHalfDay, setIsHalfDay] = useState(false);
  const [selectedTypeId, setSelectedTypeId] = useState(preselectedTypeId);
  const [fromDate, setFromDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [toDate, setToDate] = useState(dayjs().format('YYYY-MM-DD'));
  const [errorBanner, setErrorBanner] = useState(null);

  // Filter out expired leave types for new applications
  const eligibleTypes = useMemo(() => balances.filter((b) => !b.expired), [balances]);

  useEffect(() => {
    if (open) {
      setErrorBanner(null);
      setIsHalfDay(false);
      const defaultType =
        preselectedTypeId && eligibleTypes.some((t) => t.leaveTypeId === preselectedTypeId)
          ? preselectedTypeId
          : eligibleTypes[0]?.leaveTypeId || null;

      const todayStr = dayjs().format('YYYY-MM-DD');
      setSelectedTypeId(defaultType);
      setFromDate(todayStr);
      setToDate(todayStr);

      form.setFieldsValue({
        leaveTypeId: defaultType,
        isHalfDay: false,
        halfDayPeriod: 'FIRST_HALF',
        fromDate: dayjs(),
        toDate: dayjs(),
        reason: '',
      });
    }
  }, [open, preselectedTypeId, eligibleTypes, form]);

  const selectedBalance = eligibleTypes.find((b) => b.leaveTypeId === selectedTypeId);
  const availableDays = Number(selectedBalance?.availableDays ?? 0);

  const requestedDays = mobileLeaveService.calculateRequestedDays(fromDate, toDate, isHalfDay);
  const isInsufficient = selectedBalance && requestedDays > availableDays;

  const handleFinish = async (values) => {
    setSubmitting(true);
    setErrorBanner(null);
    try {
      const fromFormatted = values.fromDate ? dayjs(values.fromDate).format('YYYY-MM-DD') : fromDate;
      const toFormatted = isHalfDay
        ? fromFormatted
        : values.toDate
          ? dayjs(values.toDate).format('YYYY-MM-DD')
          : toDate;

      const payload = {
        leaveTypeId: values.leaveTypeId,
        fromDate: fromFormatted,
        toDate: toFormatted,
        isHalfDay: !!values.isHalfDay,
        halfDayPeriod: values.isHalfDay ? values.halfDayPeriod : null,
        reason: values.reason?.trim() || '',
        submit: true,
      };

      await mobileLeaveService.applyLeave(payload);
      await successMsg(
        'Leave Request Submitted',
        'Your leave request has been submitted for approval.'
      );

      form.resetFields();
      if (onSuccess) onSuccess();
      onClose();
    } catch (err) {
      const msg =
        err?.response?.data?.message || err?.message || 'Failed to submit leave request';
      setErrorBanner(msg);
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title={
        <div style={{ textAlign: 'center' }}>
          <div className="mobile-drawer-handle" aria-hidden="true" />
          <Text strong style={{ fontSize: 17 }}>Apply for Leave</Text>
        </div>
      }
      placement="bottom"
      onClose={onClose}
      open={open}
      destroyOnClose
      className="mobile-apply-leave-drawer"
      data-testid="apply-leave-drawer"
      styles={{
        header: { borderBottom: '1px solid #f0f0f0', padding: '12px 16px' },
        body: { padding: '16px 20px', paddingBottom: 32 },
      }}
    >
      {errorBanner && (
        <Alert
          type="error"
          showIcon
          message={errorBanner}
          style={{ marginBottom: 16, borderRadius: 8 }}
          data-testid="apply-leave-error"
        />
      )}

      <Form
        form={form}
        layout="vertical"
        onFinish={handleFinish}
        initialValues={{
          halfDayPeriod: 'FIRST_HALF',
          isHalfDay: false,
        }}
      >
        {/* Leave Category Selector */}
        <Form.Item
          label="Leave Type"
          name="leaveTypeId"
          rules={[{ required: true, message: 'Please select a leave category' }]}
        >
          <Select
            placeholder="Select leave category"
            size="large"
            onChange={(val) => setSelectedTypeId(val)}
            data-testid="select-leave-type"
            options={eligibleTypes.map((type) => ({
              value: type.leaveTypeId,
              label: (
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span>{type.leaveTypeName || type.leaveTypeCode}</span>
                  <Tag color="cyan" style={{ borderRadius: 4, marginLeft: 8 }}>
                    {type.availableDays}d available
                  </Tag>
                </div>
              ),
            }))}
          />
        </Form.Item>

        {/* Half Day Switch */}
        <Form.Item label="Half Day Application" name="isHalfDay" valuePropName="checked">
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text type="secondary">Apply for 0.5 day only</Text>
            <Switch
              checked={isHalfDay}
              onChange={(checked) => {
                setIsHalfDay(checked);
                form.setFieldValue('isHalfDay', checked);
                if (checked) {
                  form.setFieldValue('toDate', form.getFieldValue('fromDate'));
                  setToDate(fromDate);
                }
              }}
              data-testid="switch-half-day"
            />
          </div>
        </Form.Item>

        {/* Half Day Period Selection */}
        {isHalfDay && (
          <Form.Item
            label="Half Day Period"
            name="halfDayPeriod"
            rules={[{ required: true, message: 'Select period' }]}
          >
            <Radio.Group buttonStyle="solid" style={{ width: '100%', display: 'flex' }}>
              <Radio.Button value="FIRST_HALF" style={{ flex: 1, textAlign: 'center' }}>
                First Half (Morning)
              </Radio.Button>
              <Radio.Button value="SECOND_HALF" style={{ flex: 1, textAlign: 'center' }}>
                Second Half (Afternoon)
              </Radio.Button>
            </Radio.Group>
          </Form.Item>
        )}

        {/* Date Selection */}
        <div style={{ display: 'flex', gap: 12 }}>
          <Form.Item
            label={isHalfDay ? 'Leave Date' : 'From Date'}
            name="fromDate"
            style={{ flex: 1 }}
            rules={[{ required: true, message: 'Select date' }]}
          >
            <DatePicker
              size="large"
              style={{ width: '100%' }}
              format="YYYY-MM-DD"
              allowClear={false}
              onChange={(d) => {
                const formatted = d ? d.format('YYYY-MM-DD') : '';
                setFromDate(formatted);
                if (isHalfDay) {
                  setToDate(formatted);
                  form.setFieldValue('toDate', d);
                }
              }}
              data-testid="picker-from-date"
            />
          </Form.Item>

          {!isHalfDay && (
            <Form.Item
              label="To Date"
              name="toDate"
              style={{ flex: 1 }}
              rules={[{ required: true, message: 'Select date' }]}
            >
              <DatePicker
                size="large"
                style={{ width: '100%' }}
                format="YYYY-MM-DD"
                allowClear={false}
                onChange={(d) => setToDate(d ? d.format('YYYY-MM-DD') : '')}
                data-testid="picker-to-date"
              />
            </Form.Item>
          )}
        </div>

        {/* Requested Days and Balance Info Banner */}
        <div
          style={{
            padding: '10px 14px',
            backgroundColor: isInsufficient ? '#fff2f0' : '#f0f5ff',
            borderRadius: 8,
            marginBottom: 16,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
          data-testid="leave-duration-summary"
        >
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>Total Requested</Text>
            <div>
              <Text strong style={{ fontSize: 15, color: isInsufficient ? '#cf1322' : '#1677ff' }}>
                {requestedDays} {requestedDays === 1 ? 'day' : 'days'}
              </Text>
            </div>
          </div>
          {selectedBalance && (
            <div style={{ textAlign: 'right' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>Available Balance</Text>
              <div>
                <Text strong style={{ fontSize: 15 }}>
                  {availableDays} days
                </Text>
              </div>
            </div>
          )}
        </div>

        {isInsufficient && (
          <Alert
            type="warning"
            showIcon
            message={`Insufficient balance: requested ${requestedDays} days exceeds available ${availableDays} days.`}
            style={{ marginBottom: 16, borderRadius: 8 }}
            data-testid="insufficient-balance-alert"
          />
        )}

        {/* Reason for Leave */}
        <Form.Item
          label="Reason"
          name="reason"
          rules={[
            { required: true, message: 'Please provide a reason for leave' },
            { max: 500, message: 'Reason must be at most 500 characters' },
          ]}
        >
          <TextArea
            rows={3}
            placeholder="Reason for taking leave..."
            maxLength={500}
            showCount
            data-testid="input-leave-reason"
          />
        </Form.Item>

        {/* Submit Button */}
        <Button
          type="primary"
          htmlType="submit"
          size="large"
          block
          loading={submitting}
          disabled={submitting || isInsufficient || requestedDays <= 0}
          icon={<SendOutlined />}
          style={{
            height: 48,
            borderRadius: 10,
            fontWeight: 600,
            fontSize: 16,
            marginTop: 8,
          }}
          data-testid="btn-submit-leave"
        >
          Submit Leave Request
        </Button>
      </Form>
    </Drawer>
  );
}

ApplyLeaveDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  balances: PropTypes.arrayOf(
    PropTypes.shape({
      leaveTypeId: PropTypes.string,
      leaveTypeCode: PropTypes.string,
      leaveTypeName: PropTypes.string,
      availableDays: PropTypes.number,
      expired: PropTypes.bool,
    })
  ),
  onSuccess: PropTypes.func,
  preselectedTypeId: PropTypes.string,
};

