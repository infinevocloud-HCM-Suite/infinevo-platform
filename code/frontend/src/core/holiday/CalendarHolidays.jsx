import { useEffect, useState, useCallback, useMemo } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  Table,
  Button,
  Space,
  Typography,
  Card,
  Modal,
  Form,
  Input,
  DatePicker,
  Switch,
  Tag,
  Popconfirm,
  Select,
  Breadcrumb,
  theme,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { holidayCalendarService } from './holidayCalendarService.js';

const { Title, Text } = Typography;

export function CalendarHolidays() {
  const { token } = theme.useToken();
  const { id: calendarId } = useParams();
  const navigate = useNavigate();

  const canRead = useCan('core.holiday.read');
  const canManage = useCan('core.holiday.manage');

  const currentYear = useMemo(() => new Date().getFullYear(), []);
  const [selectedYear, setSelectedYear] = useState(currentYear);

  const [loading, setLoading] = useState(false);
  const [calendar, setCalendar] = useState(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  const loadCalendar = useCallback(async () => {
    if (!calendarId) return;
    setLoading(true);
    try {
      const data = await holidayCalendarService.get(calendarId);
      setCalendar(data);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [calendarId]);

  useEffect(() => {
    if (canRead) {
      loadCalendar();
    }
  }, [canRead, loadCalendar]);

  const handleOpenAdd = () => {
    form.resetFields();
    form.setFieldsValue({
      isRestricted: false,
      from: dayjs(`${selectedYear}-01-01`),
    });
    setModalOpen(true);
  };

  const handleCloseModal = () => {
    setModalOpen(false);
    form.resetFields();
  };

  const handleAddHoliday = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);

      const fromStr = values.from ? values.from.format('YYYY-MM-DD') : null;
      const toStr = values.to ? values.to.format('YYYY-MM-DD') : fromStr;

      const payload = {
        name: values.name,
        from: fromStr,
        to: toStr,
        isRestricted: Boolean(values.isRestricted),
        description: values.description || '',
      };

      await holidayCalendarService.addHoliday(calendarId, payload);
      await successMsg('Holiday Added', `Holiday "${values.name}" added to calendar.`);
      handleCloseModal();
      loadCalendar();
    } catch (err) {
      if (err?.errorFields) return;
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeleteHoliday = async (holidayId) => {
    try {
      await holidayCalendarService.removeHoliday(calendarId, holidayId);
      await successMsg('Holiday Removed', 'Holiday was deleted from calendar.');
      loadCalendar();
    } catch (err) {
      await errorMsg(err);
    }
  };

  // Filter holidays by selected year and sort chronologically
  const filteredHolidays = useMemo(() => {
    const list = calendar?.holidays || [];
    return list
      .filter((h) => {
        if (!selectedYear) return true;
        const fromYear = h.from ? new Date(h.from).getFullYear() : null;
        const toYear = h.to ? new Date(h.to).getFullYear() : null;
        return fromYear === selectedYear || toYear === selectedYear;
      })
      .sort((a, b) => (a.from || '').localeCompare(b.from || ''));
  }, [calendar, selectedYear]);

  const yearOptions = useMemo(() => {
    const base = currentYear;
    return [base - 2, base - 1, base, base + 1, base + 2].map((y) => ({
      label: String(y),
      value: y,
    }));
  }, [currentYear]);

  const columns = [
    {
      title: 'Date / Range',
      key: 'dates',
      width: 220,
      render: (_, record) => {
        if (!record.from) return '—';
        if (!record.to || record.from === record.to) {
          return <Text strong>{record.from}</Text>;
        }
        return (
          <Text strong>
            {record.from} <Text type="secondary">to</Text> {record.to}
          </Text>
        );
      },
    },
    {
      title: 'Day',
      key: 'weekday',
      width: 140,
      render: (_, record) => {
        if (!record.from) return '—';
        const d1 = dayjs(record.from).format('dddd');
        if (!record.to || record.from === record.to) {
          return <Text type="secondary">{d1}</Text>;
        }
        const d2 = dayjs(record.to).format('dddd');
        return <Text type="secondary">{`${d1} – ${d2}`}</Text>;
      },
    },
    {
      title: 'Holiday Name',
      dataIndex: 'name',
      key: 'name',
      render: (name) => <Text strong>{name}</Text>,
    },
    {
      title: 'Type',
      dataIndex: 'isRestricted',
      key: 'isRestricted',
      width: 140,
      render: (isRestricted) =>
        isRestricted ? (
          <Tag color="gold" id="tag-restricted">
            Restricted
          </Tag>
        ) : (
          <Tag color="blue" id="tag-regular">
            Regular
          </Tag>
        ),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      render: (desc) => desc || <Text type="secondary">—</Text>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      render: (_, record) =>
        canManage ? (
          <Popconfirm
            title="Delete Holiday"
            description={`Are you sure you want to delete "${record.name}"?`}
            onConfirm={() => handleDeleteHoliday(record.id)}
            okText="Delete"
            cancelText="Cancel"
            okButtonProps={{ danger: true, id: `btn-confirm-delete-holiday-${record.id}` }}
          >
            <Button
              danger
              type="text"
              size="small"
              icon={<DeleteOutlined />}
              id={`btn-delete-holiday-${record.id}`}
            />
          </Popconfirm>
        ) : null,
    },
  ];

  if (!canRead) {
    return <NotEntitled />;
  }

  return (
    <Card style={{ margin: token.marginLG }}>
      <Breadcrumb
        style={{ marginBottom: token.marginMD }}
        items={[
          { title: <Link to="/holidays" id="link-breadcrumb-holidays">Calendars</Link> },
          { title: calendar?.name || 'Calendar Details' },
        ]}
      />

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: token.marginLG,
        }}
      >
        <Space direction="vertical" size={2}>
          <Space align="center">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/holidays')}
              id="btn-back-calendars"
            />
            <Title level={3} style={{ marginBottom: 0 }}>
              {calendar?.name || 'Calendar Holidays'}
            </Title>
            {calendar?.isDefault && <Tag color="blue">Default Calendar</Tag>}
          </Space>
          <Text type="secondary" style={{ marginLeft: 32 }}>
            Manage specific holidays and restricted dates observed for this calendar.
          </Text>
        </Space>

        <Space>
          <Select
            value={selectedYear}
            onChange={(val) => setSelectedYear(val)}
            options={yearOptions}
            style={{ width: 110 }}
            id="select-year"
          />
          {canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenAdd}
              id="btn-add-holiday"
            >
              Add Holiday
            </Button>
          )}
        </Space>
      </div>

      <Table
        dataSource={filteredHolidays}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 25 }}
      />

      <Modal
        title="Add Holiday"
        open={modalOpen}
        onCancel={handleCloseModal}
        footer={[
          <Button key="cancel" onClick={handleCloseModal} id="btn-cancel-holiday-modal">
            Cancel
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={submitting}
            onClick={handleAddHoliday}
            id="btn-submit-holiday"
          >
            Add Holiday
          </Button>,
        ]}
      >
        <Form form={form} layout="vertical" initialValues={{ isRestricted: false }}>
          <Form.Item
            name="name"
            label="Holiday Name"
            rules={[
              { required: true, message: 'Holiday name is required' },
              { max: 100, message: 'Name must not exceed 100 characters' },
            ]}
          >
            <Input placeholder="e.g. Gandhi Jayanti" id="input-holiday-name" />
          </Form.Item>

          <Space style={{ display: 'flex' }} align="start">
            <Form.Item
              name="from"
              label="Start Date"
              rules={[{ required: true, message: 'Start date is required' }]}
            >
              <DatePicker format="YYYY-MM-DD" id="picker-holiday-from" />
            </Form.Item>

            <Form.Item
              name="to"
              label="End Date"
              extra="Defaults to start date for single-day holidays."
            >
              <DatePicker format="YYYY-MM-DD" id="picker-holiday-to" />
            </Form.Item>
          </Space>

          <Form.Item
            name="isRestricted"
            label="Restricted / Optional Holiday"
            valuePropName="checked"
            extra="If marked restricted, employees can choose this holiday subject to policy limits."
          >
            <Switch id="switch-holiday-restricted" />
          </Form.Item>

          <Form.Item name="description" label="Description">
            <Input.TextArea
              rows={3}
              placeholder="Optional notes or statutory reference"
              id="input-holiday-description"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
