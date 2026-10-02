import { useState, useEffect, useCallback } from 'react';
import { useDispatch } from 'react-redux';
import {
  Card,
  Tabs,
  Table,
  Button,
  Switch,
  Tag,
  Space,
  Popconfirm,
  Typography,
  theme,
} from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { componentService } from './componentService.js';
import { ComponentDrawer } from './ComponentDrawer.jsx';
import { invalidateComponents } from './salarySlice.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title } = Typography;

export function ComponentsScreen() {
  const { token } = theme.useToken();
  const dispatch = useDispatch();

  const canRead = useCan('payroll.structure.read');
  const canManage = useCan('payroll.structure.manage');

  const [activeTab, setActiveTab] = useState('earnings');
  const [data, setData] = useState({
    earnings: [],
    deductions: [],
    benefits: [],
    reimbursements: [],
  });
  const [loading, setLoading] = useState(false);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedComponent, setSelectedComponent] = useState(null);

  const loadData = useCallback(async (kind) => {
    setLoading(true);
    try {
      const list = await componentService.list(kind, { activeOnly: false });
      setData((prev) => ({ ...prev, [kind]: list || [] }));
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (canRead) {
      loadData(activeTab);
    }
  }, [activeTab, canRead, loadData]);

  if (!canRead) {
    return <NotEntitled />;
  }

  const handleToggleActive = async (record, checked) => {
    try {
      await componentService.setActive(activeTab, record.id, checked);
      dispatch(invalidateComponents());
      setData((prev) => ({
        ...prev,
        [activeTab]: prev[activeTab].map((item) =>
          item.id === record.id ? { ...item, active: checked } : item
        ),
      }));
    } catch (err) {
      errorMsg(err);
    }
  };

  const handleDelete = async (record) => {
    try {
      await componentService.remove(activeTab, record.id);
      dispatch(invalidateComponents());
      await successMsg('Deleted', `${record.name} has been removed.`);
      loadData(activeTab);
    } catch (err) {
      errorMsg(err);
    }
  };

  const getColumns = (kind) => {
    const columns = [
      {
        title: 'Name',
        dataIndex: 'name',
        key: 'name',
        render: (name, record) => (
          <div>
            <div style={{ fontWeight: 500 }}>{name}</div>
            {record.displayName && record.displayName !== name && (
              <small style={{ color: token.colorTextSecondary }}>{record.displayName}</small>
            )}
          </div>
        ),
      },
      {
        title: 'Code',
        dataIndex: 'code',
        key: 'code',
        render: (code) => <Tag color="geekblue">{code}</Tag>,
      },
      {
        title: 'Calculation Type',
        dataIndex: 'calculationType',
        key: 'calculationType',
        render: (type, record) => (
          <span>
            {type === 'PERCENTAGE'
              ? `${type} of ${record.percentageOf || 'CTC'}`
              : 'FLAT AMOUNT'}
          </span>
        ),
      },
      {
        title: 'Default Value',
        dataIndex: 'defaultValue',
        key: 'defaultValue',
        render: (val, record) => (
          <span style={{ fontFamily: 'monospace' }}>
            {record.calculationType === 'PERCENTAGE' ? `${val}%` : `₹${val}`}
          </span>
        ),
      },
    ];

    if (kind === 'earnings') {
      columns.push({
        title: 'Tags',
        key: 'tags',
        render: (_, record) => (
          <Space wrap orientation="horizontal" size={[0, 4]}>
            {record.includedInEpf && <Tag color="blue">EPF</Tag>}
            {record.includedInEsi && <Tag color="cyan">ESI</Tag>}
            {record.fbpComponent && <Tag color="purple">FBP</Tag>}
            {record.taxable && <Tag color="orange">Taxable</Tag>}
          </Space>
        ),
      });
    }

    if (kind === 'benefits' || kind === 'reimbursements') {
      columns.push({
        title: 'Tags',
        key: 'tags',
        render: (_, record) => (
          <Space orientation="horizontal" size={[0, 4]}>
            {record.fbpComponent && <Tag color="purple">FBP</Tag>}
            {record.preTax && <Tag color="green">Pre-Tax</Tag>}
            {record.includedInCtc && <Tag color="blue">CTC</Tag>}
          </Space>
        ),
      });
    }

    columns.push(
      {
        title: 'Status',
        dataIndex: 'active',
        key: 'active',
        render: (active, record) => (
          <Switch
            checked={active}
            disabled={!canManage}
            onChange={(checked) => handleToggleActive(record, checked)}
          />
        ),
      },
      {
        title: 'Actions',
        key: 'actions',
        render: (_, record) => (
          <Space>
            {canManage && (
              <>
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  onClick={() => {
                    setSelectedComponent(record);
                    setDrawerOpen(true);
                  }}
                />
                <Popconfirm
                  title="Delete component"
                  description="Are you sure you want to delete this component?"
                  onConfirm={() => handleDelete(record)}
                  okText="Yes"
                  cancelText="No"
                >
                  <Button type="text" danger icon={<DeleteOutlined />} />
                </Popconfirm>
              </>
            )}
          </Space>
        ),
      }
    );

    return columns;
  };

  const tabItems = [
    {
      key: 'earnings',
      label: 'Earnings',
      children: (
        <Table
          rowKey="id"
          columns={getColumns('earnings')}
          dataSource={data.earnings}
          loading={loading}
          pagination={{ pageSize: 15 }}
        />
      ),
    },
    {
      key: 'deductions',
      label: 'Deductions',
      children: (
        <Table
          rowKey="id"
          columns={getColumns('deductions')}
          dataSource={data.deductions}
          loading={loading}
          pagination={{ pageSize: 15 }}
        />
      ),
    },
    {
      key: 'benefits',
      label: 'Benefits',
      children: (
        <Table
          rowKey="id"
          columns={getColumns('benefits')}
          dataSource={data.benefits}
          loading={loading}
          pagination={{ pageSize: 15 }}
        />
      ),
    },
    {
      key: 'reimbursements',
      label: 'Reimbursements',
      children: (
        <Table
          rowKey="id"
          columns={getColumns('reimbursements')}
          dataSource={data.reimbursements}
          loading={loading}
          pagination={{ pageSize: 15 }}
        />
      ),
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={<Title level={4} style={{ margin: 0 }}>Salary Components</Title>}
        extra={
          canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setSelectedComponent(null);
                setDrawerOpen(true);
              }}
            >
              Add {activeTab.slice(0, -1)}
            </Button>
          )
        }
      >
        <Tabs activeKey={activeTab} items={tabItems} onChange={setActiveTab} />
      </Card>

      <ComponentDrawer
        open={drawerOpen}
        kind={activeTab}
        initialData={selectedComponent}
        onClose={() => setDrawerOpen(false)}
        onSuccess={() => loadData(activeTab)}
      />
    </div>
  );
}
