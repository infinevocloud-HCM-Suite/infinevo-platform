import { useEffect, useState, useCallback, useRef } from 'react';
import PropTypes from 'prop-types';
import { Table, Tag, Segmented, Typography, Alert, Card, Button } from 'antd';
import { EyeOutlined } from '@ant-design/icons';
import { payrunService } from './payrunService.js';
import { LinesDrawer } from './LinesDrawer.jsx';

const { Text } = Typography;

export function formatSkipReason(code) {
  if (!code) return '-';
  switch (code) {
    case 'NO_SALARY':
      return 'no salary structure in force';
    case 'NO_BANK_DETAILS':
      return 'no bank details';
    default:
      return code;
  }
}

export function RunEmployees({ payrunId, initialEmployees, initialTotal }) {
  const [loading, setLoading] = useState(false);
  const [employees, setEmployees] = useState(initialEmployees || []);
  const [total, setTotal] = useState(initialTotal || 0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [inclusionFilter, setInclusionFilter] = useState('ALL');
  const [loadError, setLoadError] = useState(null);
  // Names by employee id, kept across pages and filters so each employee is looked up once.
  const [names, setNames] = useState({});
  const namesAsked = useRef(new Set());

  const [selectedEmployee, setSelectedEmployee] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const fetchEmployees = useCallback(async () => {
    if (!payrunId) return;
    if (initialEmployees && inclusionFilter === 'ALL' && page === 1) {
      setEmployees(initialEmployees);
      setTotal(initialTotal || initialEmployees.length);
      return;
    }

    setLoading(true);
    setLoadError(null);
    try {
      const res = await payrunService.employees(payrunId, {
        inclusion: inclusionFilter !== 'ALL' ? inclusionFilter : undefined,
        page: page - 1,
        size: pageSize,
      });
      const content = res?.content || (Array.isArray(res) ? res : []);
      setEmployees(content);
      setTotal(res?.totalElements || res?.total_elements || content.length);
    } catch (err) {
      setEmployees([]);
      setTotal(0);
      setLoadError(err?.message || 'Failed to load employee runs');
    } finally {
      setLoading(false);
    }
  }, [payrunId, inclusionFilter, page, pageSize, initialEmployees, initialTotal]);

  useEffect(() => {
    fetchEmployees();
  }, [fetchEmployees]);

  // The run's rows carry only the employee number; names come from core, by path (W-47.2 §14.3).
  useEffect(() => {
    const missing = employees
      .map((e) => e.employee_id || e.employeeId)
      .filter((id) => id && !namesAsked.current.has(id));
    if (missing.length === 0) return;
    missing.forEach((id) => namesAsked.current.add(id));
    // Merged even if the page has changed meanwhile: these ids are marked asked, and a name is
    // the same whichever page shows it.
    payrunService
      .employeeNames(missing)
      .then((found) => {
        if (found.size > 0) {
          setNames((prev) => ({ ...prev, ...Object.fromEntries(found) }));
        }
      })
      .catch(() => {
        // Names are a courtesy: the number still identifies the row. Let a later page retry them.
        missing.forEach((id) => namesAsked.current.delete(id));
      });
  }, [employees]);

  const handleRowClick = (record) => {
    const name = names[record.employee_id || record.employeeId];
    setSelectedEmployee(name && !record.employee_name ? { ...record, employee_name: name } : record);
    setDrawerOpen(true);
  };

  const columns = [
    {
      title: 'Emp ID',
      dataIndex: 'employee_number',
      key: 'employee_number',
      render: (num, record) => (
        <Text strong>{num || record.employeeNumber || '-'}</Text>
      ),
    },
    {
      title: 'Name',
      key: 'employee_name',
      render: (_, record) => {
        const name =
          record.employee_name ||
          record.employeeName ||
          names[record.employee_id || record.employeeId] ||
          '-';
        return <Text>{name}</Text>;
      },
    },
    {
      title: 'Inclusion',
      dataIndex: 'inclusion_status',
      key: 'inclusion_status',
      render: (status, record) => {
        const val = status || record.inclusionStatus;
        const isIncluded = val === 'INCLUDED';
        return (
          <Tag color={isIncluded ? 'success' : 'warning'}>
            {val || 'UNKNOWN'}
          </Tag>
        );
      },
    },
    {
      title: 'Skip Reason',
      dataIndex: 'skip_reason',
      key: 'skip_reason',
      render: (reason, record) => {
        const code = reason || record.skipReason;
        const text = formatSkipReason(code);
        return text !== '-' ? <Text type="secondary">{text}</Text> : <Text type="secondary">-</Text>;
      },
    },
    {
      title: 'Gross Earnings',
      dataIndex: 'gross_earnings',
      key: 'gross_earnings',
      align: 'right',
      render: (amt, record) => {
        const val = amt !== undefined ? amt : record.grossEarnings;
        return val !== undefined && val !== null ? (
          <Text style={{ fontFamily: 'monospace' }}>{Number(val).toFixed(2)}</Text>
        ) : (
          '-'
        );
      },
    },
    {
      title: 'Net Pay',
      dataIndex: 'net_pay',
      key: 'net_pay',
      align: 'right',
      render: (amt, record) => {
        const val = amt !== undefined ? amt : record.netPay;
        return val !== undefined && val !== null ? (
          <Text strong style={{ fontFamily: 'monospace' }}>
            {Number(val).toFixed(2)}
          </Text>
        ) : (
          '-'
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'center',
      render: (_, record) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={(e) => {
            e.stopPropagation();
            handleRowClick(record);
          }}
        >
          Lines
        </Button>
      ),
    },
  ];

  return (
    <Card
      title={
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>Employees in this Run</span>
          <Segmented
            options={[
              { label: 'All', value: 'ALL' },
              { label: 'Included', value: 'INCLUDED' },
              { label: 'Skipped', value: 'SKIPPED' },
            ]}
            value={inclusionFilter}
            onChange={(val) => {
              setInclusionFilter(val);
              setPage(1);
            }}
          />
        </div>
      }
      style={{ marginTop: 24 }}
    >
      {loadError && (
        <Alert
          message={loadError}
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          action={
            <Button size="small" onClick={fetchEmployees}>
              Retry
            </Button>
          }
        />
      )}

      <Table
        dataSource={employees}
        columns={columns}
        rowKey={(r) => r.id || r.employee_id || r.employeeId}
        loading={loading}
        onRow={(record) => ({
          onClick: () => handleRowClick(record),
          style: { cursor: 'pointer' },
        })}
        pagination={{
          current: page,
          pageSize,
          total,
          onChange: (p, ps) => {
            setPage(p);
            setPageSize(ps);
          },
          showSizeChanger: true,
        }}
      />

      <LinesDrawer
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setSelectedEmployee(null);
        }}
        payrunId={payrunId}
        employee={selectedEmployee}
      />
    </Card>
  );
}

RunEmployees.propTypes = {
  payrunId: PropTypes.string,
  initialEmployees: PropTypes.array,
  initialTotal: PropTypes.number,
};
