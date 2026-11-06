import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useEffect, useState, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Table,
  Button,

  Typography,
  Card,
  Form,
  Select,
  DatePicker,
  Tag,

  theme,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useCan, NotEntitled } from '@shell/screens';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { holidayService } from './holidayService.js';
import { workLocationService } from '../org/workLocationService.js';
import { formatDate } from '@shared/ui/format.js';

const { Text } = Typography;
const { RangePicker } = DatePicker;

export function HolidayLookup() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const canRead = useCan('core.holiday.read');

  const [loadingLocations, setLoadingLocations] = useState(false);
  const [querying, setQuerying] = useState(false);
  const [locations, setLocations] = useState([]);
  const [holidays, setHolidays] = useState([]);
  const [selectedLocation, setSelectedLocation] = useState(null);

  const currentYear = useMemo(() => new Date().getFullYear(), []);
  const [dateRange, setDateRange] = useState([
    dayjs(`${currentYear}-01-01`),
    dayjs(`${currentYear}-12-31`),
  ]);

  const loadLocations = useCallback(async () => {
    setLoadingLocations(true);
    try {
      const data = await workLocationService.list(false);
      setLocations(Array.isArray(data) ? data : []);
    } catch {
      // ignore
    } finally {
      setLoadingLocations(false);
    }
  }, []);

  const handleQuery = useCallback(async () => {
    setQuerying(true);
    try {
      const from = dateRange?.[0] ? dateRange[0].format('YYYY-MM-DD') : null;
      const to = dateRange?.[1] ? dateRange[1].format('YYYY-MM-DD') : null;
      const res = await holidayService.between({
        workLocationId: selectedLocation || undefined,
        from,
        to,
      });
      setHolidays(Array.isArray(res) ? res : []);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setQuerying(false);
    }
  }, [selectedLocation, dateRange]);

  useEffect(() => {
    if (canRead) {
      loadLocations();
      handleQuery();
    }
  }, [canRead, loadLocations, handleQuery]);

  const columns = [
    {
      title: 'Date / Range',
      key: 'dates',
      width: 220,
      render: (_, record) => {
        if (!record.from) return '—';
        if (!record.to || record.from === record.to) {
          return <Text>{formatDate(record.from)}</Text>;
        }
        return (
          <Text>
            {formatDate(record.from)} <Text type="secondary">to</Text> {formatDate(record.to)}
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
          <Tag color="gold" id="tag-restricted" style={{ borderRadius: 6, fontWeight: 500 }}>
            Restricted
          </Tag>
        ) : (
          <Tag color="blue" id="tag-regular" style={{ borderRadius: 6, fontWeight: 500 }}>
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
  ];

  if (!canRead) {
    return <NotEntitled />;
  }

  return (
    <div className="saas-page-container fadeSlideUp">
      <Card className="saas-card" variant="borderless">
        <PageHeader
          title="Holiday Range Lookup"
          subtitle="Query applicable holidays exactly as the leave and pay run engines resolve them for a given location and period."
          onBack={() => navigate('/holidays')}
          backLabel="Calendars"
          backProps={{ id: 'btn-back-calendars' }}
        />

        <Form layout="inline" style={{ marginBottom: token.marginLG, gap: token.marginMD }}>
          <Form.Item label="Work Location">
            <Select
              allowClear
              loading={loadingLocations}
              placeholder="All / Default Location"
              value={selectedLocation}
              onChange={(val) => setSelectedLocation(val)}
              style={{ width: 240 }}
              id="select-lookup-location"
              options={locations.map((loc) => ({
                label: loc.name,
                value: loc.id,
              }))}
            />
          </Form.Item>

          <Form.Item label="Date Range">
            <RangePicker
              value={dateRange}
              onChange={(vals) => setDateRange(vals)}
              format="YYYY-MM-DD"
              id="picker-lookup-range"
            />
          </Form.Item>

          <Form.Item>
            <Button
              type="primary"
              className="btn-spring"
              icon={<SearchOutlined />}
              loading={querying}
              onClick={handleQuery}
              id="btn-submit-lookup"
            >
              Query
            </Button>
          </Form.Item>
        </Form>

        <Table
          className="saas-table"
          dataSource={holidays}
          columns={columns}
          rowKey="id"
          loading={querying}
          pagination={{ pageSize: 25 }}
        />
      </Card>
    </div>
  );
}
