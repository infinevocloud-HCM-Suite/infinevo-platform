import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { Card, Table, Tag, Typography, Button, message, theme, Result } from 'antd';
import { CheckCircleOutlined, WarningOutlined, UndoOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { assetService } from './assetService.js';
import { ReturnModal } from './ReturnModal.jsx';

const { Text, Title } = Typography;

export function AssetsTab({ employeeId, employee }) {
  const { token } = theme.useToken();
  const canManage = useCan('hrms.asset.manage');
  const targetId = employeeId || employee?.id;

  const [loading, setLoading] = useState(true);
  const [clearance, setClearance] = useState(null);
  const [selectedAssetForReturn, setSelectedAssetForReturn] = useState(null);

  const fetchClearance = useCallback(async () => {
    if (!targetId) return;
    setLoading(true);
    try {
      const data = await assetService.getEmployeeClearance(targetId);
      setClearance(data);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load employee asset clearance');
    } finally {
      setLoading(false);
    }
  }, [targetId]);

  useEffect(() => {
    fetchClearance();
  }, [fetchClearance]);

  const columns = [
    {
      title: 'Asset Tag',
      dataIndex: 'assetTag',
      key: 'assetTag',
      render: (tag) => <Text strong>{tag}</Text>,
    },
    {
      title: 'Equipment Name',
      dataIndex: 'assetName',
      key: 'assetName',
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
      key: 'handoverDate',
      render: (_, row) => <Text>{row.activeAssignment?.assignedDate || '—'}</Text>,
    },
    {
      title: 'Handover Condition',
      key: 'condition',
      render: (_, row) => (
        <Tag color="cyan">{row.activeAssignment?.assignmentCondition || row.itemCondition}</Tag>
      ),
    },
    {
      title: 'Handover Notes',
      key: 'notes',
      render: (_, row) => (
        <Text type="secondary">{row.activeAssignment?.assignmentNotes || '—'}</Text>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) => (
        canManage && (
          <Button
            size="small"
            danger
            icon={<UndoOutlined />}
            onClick={() => setSelectedAssetForReturn(row)}
          >
            Accept Return
          </Button>
        )
      ),
    },
  ];

  const isClear = clearance?.clearanceStatus === 'CLEAR';

  return (
    <div>
      <Card variant="borderless" style={{ marginBottom: token.marginMD }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <Title level={5} style={{ margin: 0 }}>
              Hardware & Equipment Clearance Status
            </Title>
            <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
              Verified company equipment custody for separation and exit clearance
            </Text>
          </div>
          <div>
            {isClear ? (
              <Tag
                icon={<CheckCircleOutlined />}
                color="success"
                style={{ fontSize: token.fontSize, padding: '4px 12px' }}
              >
                Clearance: CLEAR (0 Active Assets)
              </Tag>
            ) : (
              <Tag
                icon={<WarningOutlined />}
                color="warning"
                style={{ fontSize: token.fontSize, padding: '4px 12px' }}
              >
                Clearance: PENDING RETURN ({clearance?.activeAssetCount || 0} Assets Held)
              </Tag>
            )}
          </div>
        </div>
      </Card>

      {isClear ? (
        <Card variant="borderless">
          <Result
            status="success"
            title="All Company Assets Returned"
            subTitle="This employee has no outstanding hardware, access cards, or equipment in custody."
          />
        </Card>
      ) : (
        <Card title="Assigned Equipment" variant="borderless">
          <Table
            columns={columns}
            dataSource={clearance?.activeAssets || []}
            rowKey="id"
            loading={loading}
            pagination={false}
          />
        </Card>
      )}

      <ReturnModal
        open={Boolean(selectedAssetForReturn)}
        onClose={() => setSelectedAssetForReturn(null)}
        asset={selectedAssetForReturn}
        onSuccess={fetchClearance}
      />
    </div>
  );
}

AssetsTab.propTypes = {
  employeeId: PropTypes.string,
  employee: PropTypes.object,
};
