import PropTypes from 'prop-types';
import { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Table,
  Button,
  Space,
  Typography,
  Tag,
  Modal,
  Form,
  DatePicker,
  Select,
  Popconfirm,
  theme,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { delegationService } from './delegationService.js';
import { useEmployeeSearch } from './useEmployeeSearch.js';

const { Title, Text } = Typography;

const FLOW_TYPE_OPTIONS = [
  { label: 'Leave', value: 'LEAVE' },
  { label: 'Regularization', value: 'REGULARIZATION' },
  { label: 'Overtime', value: 'OVERTIME' },
  { label: 'Reimbursement', value: 'REIMBURSEMENT' },
  { label: 'Proof of Investment', value: 'PROOF_OF_INVESTMENT' },
  { label: 'Pay Run', value: 'PAY_RUN' },
  { label: 'Timesheet', value: 'TIMESHEET' },
];

/**
 * Where a delegation stands today. `isActive` is false only once it has been revoked; whether it
 * is in force is a matter of its dates.
 */
export function delegationStatus(record, today = new Date().toISOString().split('T')[0]) {
  const active = record.isActive !== undefined ? record.isActive : record.active;
  if (active === false) return { label: 'REVOKED', color: 'default' };
  const from = record.from || record.startsOn;
  const to = record.to || record.endsOn;
  if (to && to < today) return { label: 'EXPIRED', color: 'default' };
  if (from && from > today) return { label: 'SCHEDULED', color: 'blue' };
  return { label: 'ACTIVE', color: 'green' };
}

export function Delegations({ me: propMe, form: externalForm }) {
  const { token } = theme.useToken();
  const canDelegate = useCan('core.approval.delegate');

  const [loading, setLoading] = useState(false);
  const [delegations, setDelegations] = useState([]);

  // Create Modal
  const [createOpen, setCreateOpen] = useState(false);
  const [internalForm] = Form.useForm();
  const form = externalForm || internalForm;
  const [creating, setCreating] = useState(false);

  // Employee options for delegate selection
  const {
    options: employeeOptions,
    loading: searchLoading,
    error: searchError,
    search: searchEmployees,
  } = useEmployeeSearch();

  const loadData = useCallback(async () => {
    if (!canDelegate) return;
    setLoading(true);
    try {
      const data = await delegationService.list(propMe ? { employeeId: propMe } : {});
      setDelegations(Array.isArray(data) ? data : []);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [canDelegate, propMe]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleCreate = async () => {
    try {
      const values = await form.validateFields();
      setCreating(true);

      const startsOnStr = values.dates[0].format('YYYY-MM-DD');
      const endsOnStr = values.dates[1].format('YYYY-MM-DD');
      const flowTypesStr = Array.isArray(values.flowTypes)
        ? values.flowTypes.join(',')
        : values.flowTypes;

      const payload = {
        delegateId: values.delegateEmployeeId,
        from: startsOnStr,
        to: endsOnStr,
        flowTypes: flowTypesStr,
      };

      await delegationService.create(payload);
      setCreateOpen(false);
      form.resetFields();
      await successMsg('Delegation Created', 'Approval authority has been delegated.');
      loadData();
    } catch (err) {
      if (err?.errorFields) return;
      await errorMsg(err);
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (id) => {
    try {
      await delegationService.remove(id);
      await successMsg('Delegation Revoked', 'Delegation has been removed.');
      loadData();
    } catch (err) {
      await errorMsg(err);
    }
  };

  const columns = [
    {
      title: 'Delegator',
      dataIndex: 'delegatorEmployeeId',
      key: 'delegatorEmployeeId',
      width: 180,
      render: (id) => <Text type="secondary">{id || 'Me'}</Text>,
    },
    {
      title: 'Delegate Colleague',
      dataIndex: 'delegateEmployeeId',
      key: 'delegateEmployeeId',
      render: (id) => <Text strong>{id}</Text>,
    },
    {
      title: 'Valid From',
      key: 'validFrom',
      width: 140,
      render: (_, record) => record.from || record.startsOn || '—',
    },
    {
      title: 'Valid To',
      key: 'validTo',
      width: 140,
      render: (_, record) => record.to || record.endsOn || '—',
    },
    {
      title: 'Flow Types',
      dataIndex: 'flowTypes',
      key: 'flowTypes',
      render: (raw) => {
        const types = typeof raw === 'string'
          ? raw.split(',').map((s) => s.trim()).filter(Boolean)
          : (raw || []);
        return (
          <Space size={[0, 4]} wrap>
            {types.map((t) => (
              <Tag color="blue" key={t}>
                {t}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: 'Status',
      key: 'active',
      width: 110,
      render: (_, record) => {
        const status = delegationStatus(record);
        return <Tag color={status.color}>{status.label}</Tag>;
      },
    },
  ];

  if (canDelegate) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      width: 100,
      // The list also holds delegations made to me. Only the delegator of an active one may
      // revoke it (`revocable`, set by the server), so only those rows get the button.
      render: (_, record) => record.revocable && (
        <Popconfirm
          title="Revoke Delegation"
          description="Are you sure you want to revoke this delegation?"
          onConfirm={() => handleDelete(record.id)}
          okText="Yes"
          cancelText="No"
        >
          <Button
            type="text"
            danger
            icon={<DeleteOutlined />}
            id={`btn-delete-delegation-${record.id}`}
          />
        </Popconfirm>
      ),
    });
  }

  if (!canDelegate) {
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
            Approval Delegations
          </Title>
          <Text type="secondary">
            Delegate your approval authority to a colleague during absence.
          </Text>
        </div>

        {canDelegate && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              searchEmployees('');
              setCreateOpen(true);
            }}
            id="btn-new-delegation"
          >
            New Delegation
          </Button>
        )}
      </div>

      <Table
        dataSource={delegations}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title="Create Approval Delegation"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        confirmLoading={creating}
        okText="Create"
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="delegateEmployeeId"
            label="Delegate Employee"
            rules={[{ required: true, message: 'Please specify the delegate employee.' }]}
          >
            <Select
              id="input-delegate-employee"
              showSearch
              placeholder="Search employee by name or ID"
              filterOption={false}
              loading={searchLoading}
              onSearch={searchEmployees}
              onFocus={() => { if (employeeOptions.length === 0) searchEmployees(''); }}
              options={employeeOptions}
              notFoundContent={searchError || undefined}
            />
          </Form.Item>

          <Form.Item
            name="dates"
            label="Delegation Period"
            rules={[{ required: true, message: 'Please select start and end dates.' }]}
          >
            <DatePicker.RangePicker id="picker-delegation-range" style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item
            name="flowTypes"
            label="Flow Types"
            rules={[{ required: true, message: 'Please select at least one flow type.' }]}
          >
            <Select
              id="select-delegation-flows"
              mode="multiple"
              placeholder="Select flow types"
              options={FLOW_TYPE_OPTIONS}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

Delegations.propTypes = {
  me: PropTypes.string,
  form: PropTypes.object,
};
