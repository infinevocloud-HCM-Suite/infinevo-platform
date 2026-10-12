import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Empty, Result, Space, Table, theme } from 'antd';
import { DollarOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';
import { formatPeriod } from '../PayslipPage.jsx';
import { formatDate, formatMoney } from '../../../shared/ui/format.js';

const PAGE_SIZE = 12;

/**
 * The caller's paid payslips (D-74), read from GET /api/v1/me/payslips: a Spring page of
 * PayslipSummaryResponse{payrun_id, period, paid_on, net_pay}. View opens /me/payslips/:payrunId.
 */
export function MyPayslips() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setResult(await portalService.getPayslips(page, PAGE_SIZE));
    } catch (err) {
      setError(err?.message || 'Failed to load payslips');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    load();
  }, [load]);

  const rows = Array.isArray(result?.content) ? result.content : [];
  const total = result?.totalElements ?? rows.length;
  const size = result?.size || PAGE_SIZE;

  const columns = [
    { title: 'Period', dataIndex: 'period', key: 'period', render: (period) => formatPeriod(period) },
    { title: 'Paid on', dataIndex: 'paid_on', key: 'paid_on', render: (paidOn) => formatDate(paidOn) },
    {
      title: 'Net pay',
      dataIndex: 'net_pay',
      key: 'net_pay',
      align: 'right',
      render: (netPay) => formatMoney(netPay),
    },
    {
      title: '',
      key: 'view',
      align: 'right',
      render: (_, row) => (
        <Button size="small" onClick={() => navigate(`/me/payslips/${encodeURIComponent(row.payrun_id)}`)}>
          View
        </Button>
      ),
    },
  ];

  let body;
  if (error) {
    body = (
      <Result
        status="error"
        title="Unable to load payslips"
        subTitle={error}
        extra={
          <Button type="primary" onClick={load}>
            Retry
          </Button>
        }
      />
    );
  } else if (!loading && rows.length === 0) {
    body = <Empty description="No payslips yet." />;
  } else {
    body = (
      <Table
        rowKey="payrun_id"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={
          total > size
            ? {
                current: (result?.number ?? page) + 1,
                pageSize: size,
                total,
                showSizeChanger: false,
                onChange: (next) => setPage(next - 1),
              }
            : false
        }
      />
    );
  }

  return (
    <Card
      title={
        <Space>
          <DollarOutlined style={{ color: token.colorPrimary }} />
          <span>My Payslips</span>
        </Space>
      }
      style={{ borderRadius: token.borderRadiusLG }}
      data-testid="panel-payslips"
    >
      {body}
    </Card>
  );
}

export default MyPayslips;
