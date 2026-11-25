import { useState } from 'react';
import PropTypes from 'prop-types';
import { Button, Drawer, List, Space, Tag, Typography, message, theme } from 'antd';
import { exitService } from './exitService.js';

const { Text } = Typography;

const STATUS_COLOR = {
  PENDING: 'gold',
  CLEARED: 'green',
  WAIVED: 'blue',
};

const DEPARTMENTS = ['IT', 'FINANCE', 'ADMIN', 'HR'];

/** Drawer to inspect and tick off departmental assets and exit clearance checklist (W-103 §7). */
export function ClearanceDrawer({ exit, open, onClose, onUpdated }) {
  const { token } = theme.useToken();
  const [busyKey, setBusyKey] = useState(null);

  if (!exit) return null;

  const items = Array.isArray(exit.clearanceItems) ? exit.clearanceItems : [];

  const handleToggleItem = async (item, targetStatus) => {
    setBusyKey(item.id);
    try {
      const updated = await exitService.updateClearanceItem(exit.id, item.id, targetStatus, '');
      message.success(`Updated "${item.itemName}" to ${targetStatus}`);
      onUpdated?.(updated);
    } catch (err) {
      message.error(err?.message || 'Failed to update clearance item');
    } finally {
      setBusyKey(null);
    }
  };

  const handleSignOffDept = async (dept) => {
    setBusyKey(dept);
    try {
      const updated = await exitService.signOffDepartment(exit.id, dept, 'CLEARED', 'Department cleared');
      message.success(`${dept} clearance signed off`);
      onUpdated?.(updated);
    } catch (err) {
      message.error(err?.message || `Failed to sign off ${dept}`);
    } finally {
      setBusyKey(null);
    }
  };

  return (
    <Drawer
      title={`Exit Clearance — ${exit.employeeName || exit.employeeId}`}
      open={open}
      onClose={onClose}
      width={560}
      destroyOnClose
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div>
          <Text type="secondary">Status: </Text>
          <Tag color={exit.status === 'READY_FOR_FNF' ? 'green' : 'processing'}>{exit.status}</Tag>
        </div>

        {DEPARTMENTS.map((dept) => {
          const deptItems = items.filter((i) => i.department === dept);
          const allDone = deptItems.length > 0 && deptItems.every((i) => i.status !== 'PENDING');
          return (
            <div
              key={dept}
              style={{ border: `1px solid ${token.colorBorderSecondary}`, borderRadius: 8, padding: 12 }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
                <Text strong>{dept} Clearance</Text>
                {deptItems.length > 0 && !allDone && (
                  <Button
                    size="small"
                    type="primary"
                    loading={busyKey === dept}
                    onClick={() => handleSignOffDept(dept)}
                  >
                    Sign Off {dept}
                  </Button>
                )}
              </div>
              <List
                size="small"
                dataSource={deptItems}
                locale={{ emptyText: 'No items' }}
                renderItem={(item) => (
                  <List.Item
                    actions={
                      item.status === 'PENDING'
                        ? [
                            <Button
                              key="clear"
                              size="small"
                              loading={busyKey === item.id}
                              onClick={() => handleToggleItem(item, 'CLEARED')}
                            >
                              Clear
                            </Button>,
                            <Button
                              key="waive"
                              size="small"
                              loading={busyKey === item.id}
                              onClick={() => handleToggleItem(item, 'WAIVED')}
                            >
                              Waive
                            </Button>,
                          ]
                        : [<Tag key="tag" color={STATUS_COLOR[item.status]}>{item.status}</Tag>]
                    }
                  >
                    <List.Item.Meta
                      title={item.itemName}
                      description={item.clearedBy ? `Cleared by ${item.clearedBy}` : 'Pending sign-off'}
                    />
                  </List.Item>
                )}
              />
            </div>
          );
        })}
      </Space>
    </Drawer>
  );
}

ClearanceDrawer.propTypes = {
  exit: PropTypes.shape({
    id: PropTypes.string.isRequired,
    employeeId: PropTypes.string,
    employeeName: PropTypes.string,
    status: PropTypes.string.isRequired,
    clearanceItems: PropTypes.arrayOf(PropTypes.object),
  }),
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onUpdated: PropTypes.func,
};
