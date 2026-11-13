import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Alert, Table, Typography, Space, Spin, Empty, Tag } from 'antd';
import { payrunService } from './payrunService.js';
import { MoneyText } from '@shared/ui/MoneyText.jsx';

const { Title, Text } = Typography;

const SECTION_CONFIGS = [
  { key: 'EARNING', title: 'Earnings', color: 'green' },
  { key: 'DEDUCTION', title: 'Deductions', color: 'red' },
  { key: 'BENEFIT', title: 'Benefits', color: 'blue' },
  { key: 'REIMBURSEMENT', title: 'Reimbursements', color: 'purple' },
];

/** "2.50" as "2.5" - day figures trimmed as text, never through a float. */
const days = (v) => {
  const text = String(v);
  return text.includes('.') ? text.replace(/\.?0+$/, '') : text;
};

/** "Paid days N · LOP days N · Payable days N" from the run row, only the figures it carries (D-140). */
function daysLine(employee) {
  const parts = [
    ['Paid days', employee?.paid_days],
    ['LOP days', employee?.lop_days],
    ['Payable days', employee?.payable_days],
  ].filter(([, v]) => v != null && v !== '');
  return parts.map(([label, v]) => `${label} ${days(v)}`).join(' · ');
}

export function LinesDrawer({ open, onClose, payrunId, employee, lines: initialLines, computationError: initialError }) {
  const [loading, setLoading] = useState(false);
  const [lines, setLines] = useState(initialLines || []);
  const [computationError, setComputationError] = useState(initialError || null);

  const employeeId = employee?.employee_id || employee?.employeeId;
  const employeeNum = employee?.employee_number || employee?.employeeNumber || '';
  const employeeName = employee?.employee_name || employee?.employeeName || employeeNum;

  useEffect(() => {
    if (initialLines) {
      setLines(initialLines);
      setComputationError(initialError || null);
      return;
    }

    if (open && payrunId && employeeId) {
      setLoading(true);
      payrunService
        .lines(payrunId, employeeId)
        .then((res) => {
          setLines(res?.lines || []);
          setComputationError(res?.computation_error || null);
        })
        .catch((err) => {
          setLines([]);
          setComputationError(err?.message || 'Failed to load pay lines');
        })
        .finally(() => {
          setLoading(false);
        });
    } else if (!open) {
      setLines([]);
      setComputationError(null);
    }
  }, [open, payrunId, employeeId, initialLines, initialError]);

  const columns = [
    {
      title: 'Code',
      dataIndex: 'component_code',
      key: 'component_code',
      render: (code, record) => <Text type="secondary">{code || record.componentCode}</Text>,
    },
    {
      title: 'Component Name',
      dataIndex: 'component_name',
      key: 'component_name',
      render: (name, record) => <Text strong>{name || record.componentName}</Text>,
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right',
      render: (amt) => <MoneyText value={amt} />,
    },
  ];

  return (
    <Drawer
      title={
        <Space direction="vertical" size={2}>
          <Title level={5} style={{ margin: 0 }}>
            Pay Lines — {employeeName}
          </Title>
          {employeeNum && <Text type="secondary">Employee ID: {employeeNum}</Text>}
        </Space>
      }
      open={open}
      onClose={onClose}
      width={600}
      destroyOnClose
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <Spin size="large" />
        </div>
      ) : (
        <div>
          {computationError && (
            <Alert
              message="Computation Error"
              description={computationError}
              type="error"
              showIcon
              style={{ marginBottom: 20 }}
            />
          )}

          {daysLine(employee) && (
            <Text type="secondary" data-testid="lines-days" style={{ display: 'block', marginBottom: 16 }}>
              {daysLine(employee)}
            </Text>
          )}

          {SECTION_CONFIGS.map(({ key, title, color }) => {
            const sectionLines = lines.filter(
              (l) => (l.line_kind || l.lineKind) === key
            );

            return (
              <div key={key} style={{ marginBottom: 24 }}>
                <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8, gap: 8 }}>
                  <Title level={5} style={{ margin: 0 }}>
                    {title}
                  </Title>
                  <Tag color={color}>{sectionLines.length}</Tag>
                </div>
                {sectionLines.length > 0 ? (
                  <Table
                    dataSource={sectionLines}
                    columns={columns}
                    rowKey={(r) => r.id || `${r.component_code || r.componentCode}_${r.sort_order || 0}`}
                    pagination={false}
                    size="small"
                  />
                ) : (
                  <Empty
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description={`No ${title.toLowerCase()}`}
                    style={{ margin: '8px 0' }}
                  />
                )}
              </div>
            );
          })}
        </div>
      )}
    </Drawer>
  );
}

LinesDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  payrunId: PropTypes.string,
  employee: PropTypes.object,
  lines: PropTypes.array,
  computationError: PropTypes.string,
};
