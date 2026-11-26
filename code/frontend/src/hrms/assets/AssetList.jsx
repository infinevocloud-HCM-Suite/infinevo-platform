import { useState, useEffect, useCallback, useMemo } from 'react';
import { Table, Tag, Button, Space, Card, Select, Typography, message, theme, Input, Row, Col } from 'antd';
import { PlusOutlined, SearchOutlined, UserOutlined, UndoOutlined, EyeOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { assetService } from './assetService.js';
import { AssetDrawer } from './AssetDrawer.jsx';
import { AssignModal } from './AssignModal.jsx';
import { ReturnModal } from './ReturnModal.jsx';

const { Text } = Typography;

const STATUS_TAGS = {
  AVAILABLE: { color: 'success', text: 'Available' },
  ASSIGNED: { color: 'processing', text: 'Assigned' },
  UNDER_REPAIR: { color: 'warning', text: 'Under Repair' },
  RETIRED: { color: 'default', text: 'Retired' },
  LOST: { color: 'error', text: 'Lost' },
};

const CATEGORY_LABELS = {
  LAPTOP: 'Laptop',
  MONITOR: 'Monitor',
  MOBILE_DEVICE: 'Mobile Device',
  ACCESS_CARD: 'Access Card',
  PERIPHERAL: 'Peripheral',
  VEHICLE: 'Vehicle',
  FURNITURE: 'Furniture',
  OTHER: 'Other',
};

export function AssetList() {
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const canManage = useCan('hrms.asset.manage');

  const [loading, setLoading] = useState(true);
  const [assets, setAssets] = useState([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [searchText, setSearchText] = useState('');

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedAssetForAssign, setSelectedAssetForAssign] = useState(null);
  const [selectedAssetForReturn, setSelectedAssetForReturn] = useState(null);

  const fetchAssets = useCallback(async () => {
    setLoading(true);
    try {
      const data = await assetService.list(
        statusFilter || undefined,
        categoryFilter || undefined
      );
      setAssets(Array.isArray(data) ? data : []);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load assets');
    } finally {
      setLoading(false);
    }
  }, [statusFilter, categoryFilter]);

  useEffect(() => {
    fetchAssets();
  }, [fetchAssets]);

  const filteredAssets = useMemo(() => {
    if (!searchText.trim()) return assets;
    const query = searchText.toLowerCase().trim();
    return assets.filter((a) => {
      const tag = a.assetTag?.toLowerCase() || '';
      const name = a.assetName?.toLowerCase() || '';
      const serial = a.serialNumber?.toLowerCase() || '';
      const custodian = a.activeAssignment?.employeeName?.toLowerCase() || '';
      return tag.includes(query) || name.includes(query) || serial.includes(query) || custodian.includes(query);
    });
  }, [assets, searchText]);

  const columns = [
    {
      title: 'Asset Tag',
      dataIndex: 'assetTag',
      key: 'assetTag',
      render: (tag, row) => (
        <Button
          type="link"
          style={{ padding: 0, fontWeight: 600 }}
          onClick={() => navigate(`/hrms/assets/${row.id}`)}
        >
          {tag}
        </Button>
      ),
    },
    {
      title: 'Asset Name & Model',
      key: 'name',
      render: (_, row) => (
        <div>
          <Text strong style={{ fontSize: token.fontSize }}>
            {row.assetName}
          </Text>
          {(row.brand || row.modelNumber) && (
            <div>
              <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                {[row.brand, row.modelNumber].filter(Boolean).join(' • ')}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      render: (cat) => <Tag color="blue">{CATEGORY_LABELS[cat] || cat}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'currentStatus',
      key: 'currentStatus',
      render: (st) => {
        const info = STATUS_TAGS[st] || { color: 'default', text: st };
        return <Tag color={info.color}>{info.text}</Tag>;
      },
    },
    {
      title: 'Condition',
      dataIndex: 'itemCondition',
      key: 'itemCondition',
      render: (cond) => <Text>{cond}</Text>,
    },
    {
      title: 'Current Custodian',
      key: 'custodian',
      render: (_, row) => {
        if (row.activeAssignment?.employeeName) {
          return (
            <Space orientation="horizontal" size={4}>
              <UserOutlined style={{ color: token.colorPrimary }} />
              <Text strong>{row.activeAssignment.employeeName}</Text>
            </Space>
          );
        }
        return <Text type="secondary">—</Text>;
      },
    },
    {
      title: 'Serial Number',
      dataIndex: 'serialNumber',
      key: 'serialNumber',
      render: (sn) => <Text code>{sn || '—'}</Text>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) => (
        <Space size="small">
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => navigate(`/hrms/assets/${row.id}`)}
          >
            View
          </Button>
          {canManage && row.currentStatus === 'AVAILABLE' && (
            <Button
              size="small"
              type="primary"
              icon={<UserOutlined />}
              onClick={() => setSelectedAssetForAssign(row)}
            >
              Assign
            </Button>
          )}
          {canManage && row.currentStatus === 'ASSIGNED' && (
            <Button
              size="small"
              danger
              icon={<UndoOutlined />}
              onClick={() => setSelectedAssetForReturn(row)}
            >
              Return
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }}>
      <PageHeader
        title="Asset Inventory"
        subtitle="Hardware, peripherals, and physical company equipment registry"
        actions={
          canManage && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setDrawerOpen(true)}
            >
              Register Asset
            </Button>
          )
        }
      />

      <Card variant="borderless" style={{ marginTop: token.marginLG }}>
        <Row gutter={[16, 16]} style={{ marginBottom: token.marginMD }}>
          <Col xs={24} sm={10} md={8}>
            <Input
              prefix={<SearchOutlined style={{ color: token.colorTextSecondary }} />}
              placeholder="Search by tag, name, serial or custodian..."
              allowClear
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
          </Col>
          <Col xs={12} sm={7} md={5}>
            <Select
              style={{ width: '100%' }}
              placeholder="Filter by Status"
              allowClear
              value={statusFilter || undefined}
              onChange={(val) => setStatusFilter(val || '')}
              options={[
                { value: 'AVAILABLE', label: 'Available' },
                { value: 'ASSIGNED', label: 'Assigned' },
                { value: 'UNDER_REPAIR', label: 'Under Repair' },
                { value: 'RETIRED', label: 'Retired' },
                { value: 'LOST', label: 'Lost' },
              ]}
            />
          </Col>
          <Col xs={12} sm={7} md={5}>
            <Select
              style={{ width: '100%' }}
              placeholder="Filter by Category"
              allowClear
              value={categoryFilter || undefined}
              onChange={(val) => setCategoryFilter(val || '')}
              options={[
                { value: 'LAPTOP', label: 'Laptop' },
                { value: 'MONITOR', label: 'Monitor' },
                { value: 'MOBILE_DEVICE', label: 'Mobile Device' },
                { value: 'ACCESS_CARD', label: 'Access Card' },
                { value: 'PERIPHERAL', label: 'Peripheral' },
                { value: 'VEHICLE', label: 'Vehicle' },
                { value: 'FURNITURE', label: 'Furniture' },
                { value: 'OTHER', label: 'Other' },
              ]}
            />
          </Col>
        </Row>

        <Table
          columns={columns}
          dataSource={filteredAssets}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 15, showSizeChanger: true }}
        />
      </Card>

      <AssetDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onSuccess={fetchAssets}
      />

      <AssignModal
        open={Boolean(selectedAssetForAssign)}
        onClose={() => setSelectedAssetForAssign(null)}
        asset={selectedAssetForAssign}
        onSuccess={fetchAssets}
      />

      <ReturnModal
        open={Boolean(selectedAssetForReturn)}
        onClose={() => setSelectedAssetForReturn(null)}
        asset={selectedAssetForReturn}
        onSuccess={fetchAssets}
      />
    </div>
  );
}
