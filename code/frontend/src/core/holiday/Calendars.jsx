import { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import {
  Table,
  Button,
  Space,
  Typography,
  Card,
  Drawer,
  Form,
  Input,
  Select,
  Switch,
  Tag,
  theme,
} from 'antd';
import { PlusOutlined, EditOutlined, CalendarOutlined, SearchOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { holidayCalendarService } from './holidayCalendarService.js';
import { workLocationService } from '../org/workLocationService.js';

const { Title, Text } = Typography;

export function Calendars() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const location = useLocation();
  const params = useParams();

  const canRead = useCan('core.holiday.read');
  const canManage = useCan('core.holiday.manage');

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [calendars, setCalendars] = useState([]);
  const [workLocations, setWorkLocations] = useState([]);
  const [workLocationMap, setWorkLocationMap] = useState({});

  // Drawer state
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingCalendar, setEditingCalendar] = useState(null);
  const [form] = Form.useForm();

  const loadWorkLocations = useCallback(async () => {
    try {
      const data = await workLocationService.list(false);
      const list = Array.isArray(data) ? data : [];
      setWorkLocations(list);
      const map = {};
      list.forEach((loc) => {
        map[loc.id] = loc.name;
      });
      setWorkLocationMap(map);
    } catch {
      // ignore
    }
  }, []);

  const loadCalendars = useCallback(async () => {
    setLoading(true);
    try {
      const data = await holidayCalendarService.list();
      setCalendars(Array.isArray(data) ? data : []);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canRead) {
      loadCalendars();
      loadWorkLocations();
    }
  }, [canRead, loadCalendars, loadWorkLocations]);

  // Synchronize drawer with URL routes /holidays/new and /holidays/:id/edit
  useEffect(() => {
    if (!canManage) return;
    if (location.pathname === '/holidays/new') {
      setEditingCalendar(null);
      form.resetFields();
      form.setFieldsValue({ isDefault: false, workLocationIds: [] });
      setDrawerOpen(true);
    } else if (params.id && location.pathname.endsWith('/edit')) {
      const found = calendars.find((c) => String(c.id) === String(params.id));
      if (found) {
        setEditingCalendar(found);
        form.setFieldsValue({
          name: found.name,
          isDefault: found.isDefault,
          workLocationIds: Array.from(found.workLocationIds || []),
        });
        setDrawerOpen(true);
      }
    }
  }, [location.pathname, params.id, calendars, form, canManage]);

  const hasDefaultCalendar = useMemo(
    () => calendars.some((c) => c.isDefault),
    [calendars]
  );

  const handleOpenCreate = () => {
    setEditingCalendar(null);
    form.resetFields();
    form.setFieldsValue({ isDefault: !hasDefaultCalendar, workLocationIds: [] });
    setDrawerOpen(true);
  };

  const handleOpenEdit = (calendar) => {
    setEditingCalendar(calendar);
    form.setFieldsValue({
      name: calendar.name,
      isDefault: calendar.isDefault,
      workLocationIds: Array.from(calendar.workLocationIds || []),
    });
    setDrawerOpen(true);
  };

  const handleCloseDrawer = () => {
    setDrawerOpen(false);
    setEditingCalendar(null);
    form.resetFields();
    if (location.pathname === '/holidays/new' || location.pathname.endsWith('/edit')) {
      navigate('/holidays');
    }
  };

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const shouldBeDefault = editingCalendar ? Boolean(values.isDefault) : (!hasDefaultCalendar);
      const payload = {
        name: values.name,
        isDefault: shouldBeDefault,
        workLocationIds: values.workLocationIds || [],
      };

      if (editingCalendar) {
        await holidayCalendarService.update(editingCalendar.id, payload);
        await successMsg('Calendar Updated', `Holiday calendar "${values.name}" updated successfully.`);
      } else {
        await holidayCalendarService.create(payload);
        await successMsg('Calendar Created', `Holiday calendar "${values.name}" created successfully.`);
      }

      handleCloseDrawer();
      loadCalendars();
    } catch (err) {
      if (err?.errorFields) return;
      await errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  const currentYear = useMemo(() => new Date().getFullYear(), []);

  const columns = [
    {
      title: 'Calendar Name',
      dataIndex: 'name',
      key: 'name',
      render: (name, record) => (
        <Space orientation="horizontal" size="small">
          <Button
            type="link"
            style={{ padding: 0, fontWeight: 600 }}
            onClick={() => navigate(`/holidays/${record.id}`)}
            id={`link-calendar-${record.id}`}
          >
            {name}
          </Button>
          {record.isDefault && (
            <Tag color="blue" id={`tag-default-${record.id}`}>
              Default
            </Tag>
          )}
        </Space>
      ),
    },
    {
      title: 'Assigned Work Locations',
      dataIndex: 'workLocationIds',
      key: 'workLocationIds',
      render: (ids) => {
        const idList = Array.from(ids || []);
        if (idList.length === 0) {
          return <Text type="secondary">None (Applies to no locations)</Text>;
        }
        return (
          <Space wrap size={[4, 4]}>
            {idList.map((locId) => (
              <Tag key={locId}>{workLocationMap[locId] || locId}</Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: `Holidays (${currentYear})`,
      key: 'holidayCount',
      width: 150,
      render: (_, record) => {
        const count = (record.holidays || []).filter((h) => {
          const fromYear = h.from ? new Date(h.from).getFullYear() : null;
          const toYear = h.to ? new Date(h.to).getFullYear() : null;
          return fromYear === currentYear || toYear === currentYear;
        }).length;
        return <Text strong>{count}</Text>;
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 220,
      render: (_, record) => (
        <Space size="middle">
          <Button
            type="link"
            size="small"
            icon={<CalendarOutlined />}
            onClick={() => navigate(`/holidays/${record.id}`)}
            id={`btn-view-holidays-${record.id}`}
          >
            Manage Holidays
          </Button>
          {canManage && (
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleOpenEdit(record)}
              id={`btn-edit-calendar-${record.id}`}
            />
          )}
        </Space>
      ),
    },
  ];

  if (!canRead) {
    return <NotEntitled />;
  }

  return (
    <Card style={{ margin: token.marginLG }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: token.marginLG,
        }}
      >
        <div>
          <Title level={3} style={{ marginBottom: 4 }}>
            Holiday Calendars
          </Title>
          <Text type="secondary">
            Configure work-location-specific holiday schedules and statutory non-working days.
          </Text>
        </div>

        <Space>
          <Button
            icon={<SearchOutlined />}
            onClick={() => navigate('/holidays/lookup')}
            id="btn-holiday-lookup"
          >
            Lookup Holidays
          </Button>
          {canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleOpenCreate}
              id="btn-create-calendar"
            >
              Add Calendar
            </Button>
          )}
        </Space>
      </div>

      <Table
        dataSource={calendars}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 15 }}
      />

      <Drawer
        title={editingCalendar ? 'Edit Holiday Calendar' : 'Create Holiday Calendar'}
        width={480}
        open={drawerOpen}
        onClose={handleCloseDrawer}
        extra={
          <Space>
            <Button onClick={handleCloseDrawer} id="btn-cancel-drawer">
              Cancel
            </Button>
            <Button
              type="primary"
              loading={saving}
              onClick={handleSave}
              id="btn-save-calendar"
            >
              Save
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="Calendar Name"
            rules={[
              { required: true, message: 'Calendar name is required' },
              { max: 100, message: 'Name must not exceed 100 characters' },
            ]}
          >
            <Input placeholder="e.g. Bengaluru Office 2026" id="input-calendar-name" />
          </Form.Item>

          <Form.Item
            name="isDefault"
            label="Default Calendar"
            valuePropName="checked"
            extra={
              !editingCalendar && hasDefaultCalendar
                ? "A default calendar already exists for this tenant. This new calendar will be created as location-specific (you can promote it to default later via Edit)."
                : "If marked default, employees without an explicit location calendar will fall back to this calendar."
            }
          >
            <Switch
              id="switch-calendar-default"
              disabled={!editingCalendar && hasDefaultCalendar}
            />
          </Form.Item>

          <Form.Item
            name="workLocationIds"
            label="Assigned Work Locations"
            extra="Select the offices/work locations that observe this holiday calendar."
          >
            <Select
              mode="multiple"
              placeholder="Select work locations"
              id="select-work-locations"
              options={workLocations.map((loc) => ({
                label: loc.name,
                value: loc.id,
              }))}
            />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
}
