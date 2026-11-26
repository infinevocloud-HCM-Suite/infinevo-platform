import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Descriptions,
  Tag,
  Button,
  Space,
  Timeline,
  Typography,
  Spin,
  message,
  theme,
  Row,
  Col,
  Empty,
  Modal,
  Select,
  Input,
} from 'antd';
import {
  EditOutlined,
  UserOutlined,
  CheckCircleOutlined,
  HistoryOutlined,
  ToolOutlined,
} from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { assetService } from './assetService.js';
import { AssetDrawer } from './AssetDrawer.jsx';
import { AssignModal } from './AssignModal.jsx';
import { ReturnModal } from './ReturnModal.jsx';

const { Text, Title } = Typography;

const STATUS_TAGS = {
  AVAILABLE: { color: 'success', text: 'Available' },
  ASSIGNED: { color: 'processing', text: 'Assigned' },
  UNDER_REPAIR: { color: 'warning', text: 'Under Repair' },
  RETIRED: { color: 'default', text: 'Retired' },
  LOST: { color: 'error', text: 'Lost' },
};

const CONDITION_TAGS = {
  NEW: { color: 'green', text: 'New' },
  GOOD: { color: 'blue', text: 'Good' },
  FAIR: { color: 'orange', text: 'Fair' },
  DAMAGED: { color: 'red', text: 'Damaged' },
};

export function AssetDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const canManage = useCan('hrms.asset.manage');

  const [loading, setLoading] = useState(true);
  const [asset, setAsset] = useState(null);
  const [history, setHistory] = useState([]);

  const [editDrawerOpen, setEditDrawerOpen] = useState(false);
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [returnModalOpen, setReturnModalOpen] = useState(false);
  const [statusModalOpen, setStatusModalOpen] = useState(false);
  const [newStatus, setNewStatus] = useState(null);
  const [statusNotes, setStatusNotes] = useState('');
  const [updatingStatus, setUpdatingStatus] = useState(false);

  const fetchAssetData = useCallback(async () => {
    setLoading(true);
    try {
      const [assetData, historyData] = await Promise.all([
        assetService.get(id),
        assetService.getHistory(id),
      ]);
      setAsset(assetData);
      setHistory(Array.isArray(historyData) ? historyData : []);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load asset details');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchAssetData();
  }, [fetchAssetData]);

  const handleStatusUpdate = async () => {
    if (!newStatus) return;
    setUpdatingStatus(true);
    try {
      await assetService.updateStatus(id, {
        status: newStatus,
        notes: statusNotes,
      });
      message.success('Status updated successfully');
      setStatusModalOpen(false);
      fetchAssetData();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to update status');
    } finally {
      setUpdatingStatus(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: token.paddingXL }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!asset) {
    return (
      <div style={{ padding: token.paddingLG }}>
        <Empty description="Asset not found">
          <Button type="primary" onClick={() => navigate('/hrms/assets')}>
            Back to Asset List
          </Button>
        </Empty>
      </div>
    );
  }

  const statusInfo = STATUS_TAGS[asset.currentStatus] || { color: 'default', text: asset.currentStatus };
  const conditionInfo = CONDITION_TAGS[asset.itemCondition] || { color: 'default', text: asset.itemCondition };

  return (
    <div style={{ padding: token.paddingLG }}>
      <PageHeader
        onBack={() => navigate('/hrms/assets')}
        title={
          <Space align="center">
            <span>{asset.assetName}</span>
            <Tag color={statusInfo.color}>{statusInfo.text}</Tag>
            <Tag color={conditionInfo.color}>{conditionInfo.text}</Tag>
          </Space>
        }
        subtitle={`Asset Tag: ${asset.assetTag}`}
        actions={
          canManage && (
            <Space>
              <Button icon={<EditOutlined />} onClick={() => setEditDrawerOpen(true)}>
                Edit Details
              </Button>
              {asset.currentStatus === 'AVAILABLE' && (
                <Button type="primary" icon={<UserOutlined />} onClick={() => setAssignModalOpen(true)}>
                  Assign Asset
                </Button>
              )}
              {asset.currentStatus === 'ASSIGNED' && (
                <Button type="primary" danger onClick={() => setReturnModalOpen(true)}>
                  Accept Return
                </Button>
              )}
              <Button icon={<ToolOutlined />} onClick={() => setStatusModalOpen(true)}>
                Change Status
              </Button>
            </Space>
          )
        }
      />

      <Row gutter={[16, 16]} style={{ marginTop: token.marginLG }}>
        <Col xs={24} lg={16}>
          <Card title="Equipment Specifications" variant="borderless">
            <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
              <Descriptions.Item label="Asset Tag">{asset.assetTag}</Descriptions.Item>
              <Descriptions.Item label="Category">{asset.category}</Descriptions.Item>
              <Descriptions.Item label="Brand">{asset.brand || '—'}</Descriptions.Item>
              <Descriptions.Item label="Model">{asset.modelNumber || '—'}</Descriptions.Item>
              <Descriptions.Item label="Serial Number">{asset.serialNumber || '—'}</Descriptions.Item>
              <Descriptions.Item label="Condition">{conditionInfo.text}</Descriptions.Item>
              <Descriptions.Item label="Purchase Date">{asset.purchaseDate || '—'}</Descriptions.Item>
              <Descriptions.Item label="Purchase Cost">
                {asset.purchaseCost ? `$${Number(asset.purchaseCost).toLocaleString()}` : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Warranty Expiry">{asset.warrantyExpiry || '—'}</Descriptions.Item>
              <Descriptions.Item label="Vendor">{asset.vendorName || '—'}</Descriptions.Item>
              <Descriptions.Item label="Notes / Specs" span={2}>
                {asset.notes || 'No extra notes'}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          <Card
            title={
              <Space orientation="horizontal">
                <HistoryOutlined />
                <span>Assignment & Custody History</span>
              </Space>
            }
            variant="borderless"
            style={{ marginTop: token.marginLG }}
          >
            {history.length === 0 ? (
              <Empty description="No custody history on record" />
            ) : (
              <Timeline
                style={{ marginTop: token.marginSM }}
                items={history.map((h) => ({
                  color: h.isActive ? token.colorPrimary : token.colorTextSecondary,
                  children: (
                    <div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <Text strong style={{ fontSize: token.fontSize }}>
                          {h.employeeName || `Employee ID: ${h.employeeId}`}
                        </Text>
                        {h.isActive ? (
                          <Tag color="processing">Current Custodian</Tag>
                        ) : (
                          <Tag color="default">Returned</Tag>
                        )}
                      </div>
                      <div style={{ marginTop: token.marginXXS }}>
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                          Assigned on {h.assignedDate} by {h.assignedBy}
                          {h.assignmentCondition && ` • Condition: ${h.assignmentCondition}`}
                        </Text>
                      </div>
                      {h.assignmentNotes && (
                        <div style={{ marginTop: token.marginXXS }}>
                          <Text italic style={{ fontSize: token.fontSizeSM }}>
                            Note: {h.assignmentNotes}
                          </Text>
                        </div>
                      )}
                      {!h.isActive && h.returnedDate && (
                        <div style={{ marginTop: token.marginXS }}>
                          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                            <CheckCircleOutlined style={{ color: token.colorSuccess, marginRight: 4 }} />
                            Returned on {h.returnedDate} by {h.returnedBy || '—'}
                            {h.returnCondition && ` • Return Condition: ${h.returnCondition}`}
                          </Text>
                          {h.returnNotes && (
                            <div>
                              <Text italic style={{ fontSize: token.fontSizeSM }}>
                                Return note: {h.returnNotes}
                              </Text>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  ),
                }))}
              />
            )}
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card title="Current Custodian" variant="borderless">
            {asset.activeAssignment ? (
              <div>
                <Space direction="vertical" style={{ width: '100%' }}>
                  <div>
                    <Text type="secondary">Assigned Employee</Text>
                    <Title level={5} style={{ margin: 0 }}>
                      {asset.activeAssignment.employeeName || 'Active Custodian'}
                    </Title>
                    <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                      ID: {asset.activeAssignment.employeeId}
                    </Text>
                  </div>
                  <Descriptions size="small" column={1} style={{ marginTop: token.marginSM }}>
                    <Descriptions.Item label="Handover Date">
                      {asset.activeAssignment.assignedDate}
                    </Descriptions.Item>
                    <Descriptions.Item label="Handover By">
                      {asset.activeAssignment.assignedBy}
                    </Descriptions.Item>
                    <Descriptions.Item label="Handover Condition">
                      <Tag color="blue">{asset.activeAssignment.assignmentCondition}</Tag>
                    </Descriptions.Item>
                    {asset.activeAssignment.assignmentNotes && (
                      <Descriptions.Item label="Handover Notes">
                        {asset.activeAssignment.assignmentNotes}
                      </Descriptions.Item>
                    )}
                  </Descriptions>
                  {canManage && (
                    <Button
                      type="primary"
                      danger
                      block
                      style={{ marginTop: token.marginSM }}
                      onClick={() => setReturnModalOpen(true)}
                    >
                      Record Equipment Return
                    </Button>
                  )}
                </Space>
              </div>
            ) : (
              <div style={{ textAlign: 'center', padding: token.paddingMD }}>
                <Empty description="Asset is currently in inventory (unassigned)">
                  {canManage && asset.currentStatus === 'AVAILABLE' && (
                    <Button type="primary" onClick={() => setAssignModalOpen(true)}>
                      Assign to Employee
                    </Button>
                  )}
                </Empty>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <AssetDrawer
        open={editDrawerOpen}
        onClose={() => setEditDrawerOpen(false)}
        asset={asset}
        onSuccess={fetchAssetData}
      />

      <AssignModal
        open={assignModalOpen}
        onClose={() => setAssignModalOpen(false)}
        asset={asset}
        onSuccess={fetchAssetData}
      />

      <ReturnModal
        open={returnModalOpen}
        onClose={() => setReturnModalOpen(false)}
        asset={asset}
        onSuccess={fetchAssetData}
      />

      <Modal
        title={`Update Status: ${asset.assetTag}`}
        open={statusModalOpen}
        onCancel={() => setStatusModalOpen(false)}
        onOk={handleStatusUpdate}
        confirmLoading={updatingStatus}
        destroyOnHidden
      >
        <div style={{ marginTop: 16 }}>
          <Text strong>Select New Status</Text>
          <Select
            style={{ width: '100%', marginTop: 8 }}
            defaultValue={asset.currentStatus}
            onChange={setNewStatus}
            options={[
              { value: 'AVAILABLE', label: 'Available' },
              { value: 'UNDER_REPAIR', label: 'Under Repair' },
              { value: 'RETIRED', label: 'Retired' },
              { value: 'LOST', label: 'Lost' },
            ]}
          />
          <div style={{ marginTop: 16 }}>
            <Text strong>Reason / Notes</Text>
            <Input.TextArea
              rows={3}
              style={{ marginTop: 8 }}
              value={statusNotes}
              onChange={(e) => setStatusNotes(e.target.value)}
              placeholder="Reason for status change..."
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
