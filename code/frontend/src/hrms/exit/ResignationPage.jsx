import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, Select, Space, Table, Tag, message } from 'antd';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { exitService } from './exitService.js';

const REASON_OPTIONS = [
  { value: 'CAREER_GROWTH', label: 'Career Growth' },
  { value: 'BETTER_OPPORTUNITY', label: 'Better Opportunity' },
  { value: 'PERSONAL', label: 'Personal Reasons' },
  { value: 'RELOCATION', label: 'Relocation' },
  { value: 'HIGHER_STUDIES', label: 'Higher Studies' },
];

const STATUS_COLORS = {
  SUBMITTED: 'gold',
  CLEARANCE_IN_PROGRESS: 'processing',
  READY_FOR_FNF: 'cyan',
  SETTLED: 'green',
  REJECTED: 'red',
  WITHDRAWN: 'default',
};

/** Employee self-service resignation submission & status tracker under /me/exit (W-103 §7). */
export function ResignationPage() {
  const [form] = Form.useForm();
  const [exits, setExits] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await exitService.myExits();
      setExits(data);
    } catch (err) {
      setError(err?.message || 'Could not load exit requests');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const onSubmit = async (values) => {
    setSubmitting(true);
    setError(null);
    try {
      await exitService.submitResignation({
        resignationDate: values.resignationDate,
        requestedLwd: values.requestedLwd,
        exitType: 'RESIGNATION',
        reasonCategory: values.reasonCategory,
        reasonNotes: values.reasonNotes || null,
      });
      message.success('Resignation submitted');
      form.resetFields();
      await load();
    } catch (err) {
      setError(err?.message || 'Could not submit resignation');
    } finally {
      setSubmitting(false);
    }
  };

  const onWithdraw = async (id) => {
    try {
      await exitService.withdrawMyExit(id);
      message.success('Resignation withdrawn');
      await load();
    } catch (err) {
      message.error(err?.message || 'Could not withdraw resignation');
    }
  };

  const columns = [
    { title: 'Resignation Date', dataIndex: 'resignationDate', key: 'resignationDate' },
    { title: 'Requested LWD', dataIndex: 'requestedLwd', key: 'requestedLwd' },
    { title: 'Approved LWD', dataIndex: 'approvedLwd', key: 'approvedLwd', render: (v) => v || '—' },
    { title: 'Reason', dataIndex: 'reasonCategory', key: 'reasonCategory' },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s) => <Tag color={STATUS_COLORS[s] || 'default'}>{s}</Tag>,
    },
    {
      title: 'Action',
      key: 'action',
      render: (_, r) =>
        r.status === 'SUBMITTED' ? (
          <Button size="small" danger onClick={() => onWithdraw(r.id)}>
            Withdraw
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <PageHeader title="Resignation & Exit" subtitle="Submit your resignation and track departmental clearances." />

      {error && <Alert type="error" message={error} showIcon />}

      <Card title="Submit Resignation">
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Space wrap size="middle" style={{ width: '100%' }}>
            <Form.Item
              name="resignationDate"
              label="Resignation Date"
              rules={[{ required: true, message: 'Enter resignation date (YYYY-MM-DD)' }]}
            >
              <Input type="date" />
            </Form.Item>
            <Form.Item
              name="requestedLwd"
              label="Requested Last Working Day"
              rules={[{ required: true, message: 'Enter requested last working day (YYYY-MM-DD)' }]}
            >
              <Input type="date" />
            </Form.Item>
            <Form.Item
              name="reasonCategory"
              label="Reason Category"
              rules={[{ required: true, message: 'Select a reason category' }]}
            >
              <Select style={{ width: 220 }} options={REASON_OPTIONS} placeholder="Select reason" />
            </Form.Item>
          </Space>
          <Form.Item name="reasonNotes" label="Notes">
            <Input.TextArea rows={3} placeholder="Optional remarks" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting}>
            Submit Resignation
          </Button>
        </Form>
      </Card>

      <Card title="My Exit Requests">
        <Table rowKey="id" loading={loading} dataSource={exits} columns={columns} pagination={false} />
      </Card>
    </Space>
  );
}
