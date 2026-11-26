import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Tag, Typography, theme, Empty, Row, Col, Descriptions } from 'antd';
import { LaptopOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { assetService } from './assetService.js';

const { Text, Title } = Typography;

export function MyAssets() {
  const { token } = theme.useToken();
  const [loading, setLoading] = useState(true);
  const [assets, setAssets] = useState([]);

  const fetchMyAssets = useCallback(async () => {
    setLoading(true);
    try {
      const data = await assetService.getMyAssets();
      setAssets(Array.isArray(data) ? data : []);
    } catch {
      setAssets([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMyAssets();
  }, [fetchMyAssets]);

  const columns = [
    {
      title: 'Asset Tag',
      dataIndex: 'assetTag',
      key: 'assetTag',
      render: (tag) => <Text strong>{tag}</Text>,
    },
    {
      title: 'Equipment Name',
      key: 'name',
      render: (_, row) => (
        <div>
          <Text strong>{row.assetName}</Text>
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
      render: (cat) => <Tag color="blue">{cat}</Tag>,
    },
    {
      title: 'Serial Number',
      dataIndex: 'serialNumber',
      key: 'serialNumber',
      render: (sn) => <Text code>{sn || '—'}</Text>,
    },
    {
      title: 'Handover Date',
      key: 'date',
      render: (_, row) => <Text>{row.activeAssignment?.assignedDate || '—'}</Text>,
    },
    {
      title: 'Handover Condition',
      key: 'cond',
      render: (_, row) => (
        <Tag color="cyan">{row.activeAssignment?.assignmentCondition || row.itemCondition}</Tag>
      ),
    },
  ];

  return (
    <div style={{ padding: token.paddingLG }}>
      <PageHeader
        title="My Assigned Equipment"
        subtitle="Company hardware, laptops, monitors, and equipment currently in your custody"
      />

      {assets.length > 0 && (
        <Row gutter={[16, 16]} style={{ marginTop: token.marginLG, marginBottom: token.marginLG }}>
          {assets.map((asset) => (
            <Col xs={24} md={12} lg={8} key={asset.id}>
              <Card
                variant="borderless"
                title={
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <LaptopOutlined style={{ color: token.colorPrimary }} />
                      <Text strong>{asset.assetTag}</Text>
                    </div>
                    <Tag color="blue">{asset.category}</Tag>
                  </div>
                }
              >
                <Title level={5} style={{ margin: 0 }}>
                  {asset.assetName}
                </Title>
                <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                  {[asset.brand, asset.modelNumber].filter(Boolean).join(' • ') || 'Standard equipment'}
                </Text>

                <Descriptions size="small" column={1} style={{ marginTop: token.marginSM }}>
                  <Descriptions.Item label="Serial Number">
                    <Text code>{asset.serialNumber || '—'}</Text>
                  </Descriptions.Item>
                  <Descriptions.Item label="Handover Date">
                    {asset.activeAssignment?.assignedDate || '—'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Condition">
                    <Tag color="cyan">
                      {asset.activeAssignment?.assignmentCondition || asset.itemCondition}
                    </Tag>
                  </Descriptions.Item>
                  {asset.activeAssignment?.assignmentNotes && (
                    <Descriptions.Item label="Notes">
                      <Text type="secondary">{asset.activeAssignment.assignmentNotes}</Text>
                    </Descriptions.Item>
                  )}
                </Descriptions>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      <Card
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <CheckCircleOutlined style={{ color: token.colorSuccess }} />
            <span>Equipment Custody Inventory</span>
          </div>
        }
        variant="borderless"
        style={{ marginTop: assets.length === 0 ? token.marginLG : 0 }}
      >
        {assets.length === 0 && !loading ? (
          <Empty description="No company equipment is currently assigned to you." />
        ) : (
          <Table
            columns={columns}
            dataSource={assets}
            rowKey="id"
            loading={loading}
            pagination={false}
          />
        )}
      </Card>
    </div>
  );
}

