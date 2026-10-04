import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Form, Input, DatePicker, Button, Table, Typography, Space, theme } from 'antd';
import { errorMsg } from '@shared/ui/msgHelper.js';
import { auditService } from './auditService.js';

const PAGE_SIZE = 20;

/** The one-line hint shown while the tab cannot read this tenant (W-65.3 §13 decision 3). */
export const AUDIT_HINT = 'Audit opens while you act in this tenant: the trail is read from the tenant you are bound to.';

/**
 * A customer tenant's audit trail (W-65.3 §5). `GET /api/v1/audit` reads the bound tenant only,
 * so this works while a session for this tenant is live and the client sends `X-Impersonation`.
 * Without one it fetches nothing and says why - an empty table would read as "no changes".
 */
export function AuditTab({ enabled }) {
  const { token } = theme.useToken();
  const [form] = Form.useForm();
  const [filters, setFilters] = useState({});
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalElements: 0 });
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await auditService.search({ ...filters, page, size: PAGE_SIZE });
      setData({
        content: Array.isArray(res?.content) ? res.content : [],
        totalElements: res?.totalElements ?? 0,
      });
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    if (enabled) load();
  }, [enabled, load]);

  if (!enabled) {
    return <Typography.Text type="secondary">{AUDIT_HINT}</Typography.Text>;
  }

  const onSearch = (values) => {
    setPage(0);
    setFilters({
      entity: values.entity?.trim() || undefined,
      actor: values.actor?.trim() || undefined,
      from: values.from ? values.from.toISOString() : undefined,
      to: values.to ? values.to.toISOString() : undefined,
    });
  };

  const columns = [
    {
      title: 'When',
      dataIndex: 'occurredAt',
      key: 'occurredAt',
      render: (value) => (value ? new Date(value).toLocaleString() : ''),
    },
    { title: 'Actor', dataIndex: 'actorLabel', key: 'actorLabel' },
    { title: 'Operation', dataIndex: 'operation', key: 'operation' },
    { title: 'Entity', dataIndex: 'entityTable', key: 'entityTable' },
    { title: 'Entity id', dataIndex: 'entityId', key: 'entityId' },
    {
      title: 'Changed',
      dataIndex: 'changedColumns',
      key: 'changedColumns',
      render: (cols) => (cols || []).join(', '),
    },
  ];

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={token.marginMD}>
      <Form form={form} layout="inline" onFinish={onSearch}>
        <Form.Item name="entity" label="Entity">
          <Input placeholder="employee" allowClear />
        </Form.Item>
        <Form.Item name="actor" label="Actor">
          <Input allowClear />
        </Form.Item>
        <Form.Item name="from" label="From">
          <DatePicker showTime />
        </Form.Item>
        <Form.Item name="to" label="To">
          <DatePicker showTime />
        </Form.Item>
        <Form.Item>
          <Button htmlType="submit">Search</Button>
        </Form.Item>
      </Form>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={data.content}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total: data.totalElements,
          showSizeChanger: false,
          onChange: (next) => setPage(next - 1),
        }}
      />
    </Space>
  );
}

AuditTab.propTypes = {
  enabled: PropTypes.bool,
};
