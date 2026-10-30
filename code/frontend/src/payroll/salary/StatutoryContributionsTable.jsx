import PropTypes from 'prop-types';
import { Table, Tag, Typography } from 'antd';
import { EMPTY, formatMoney, humanize } from '@shared/ui/format.js';
import { statutoryName } from './statutoryLabels.js';
import { trimDecimal } from './decimal.js';

const { Text } = Typography;

/**
 * The statutory lines of a salary version - EPF, EPS, EDLI, admin charges and ESI, employer and
 * employee - from `SalaryVersionResponse.statutory` or a preview's `statutory` (W-75.2 §5, D-130).
 * Every amount is the server's; the table only formats it.
 */
export function StatutoryContributionsTable({ statutory, id = 'table-salary-statutory', showAnnual = true }) {
  const columns = [
    {
      title: 'Component',
      dataIndex: 'componentCode',
      key: 'componentCode',
      render: (code) => statutoryName(code),
    },
    {
      title: 'Share',
      dataIndex: 'share',
      key: 'share',
      width: 110,
      render: (share) => humanize(share),
    },
    {
      title: 'Rate and base',
      key: 'rate',
      render: (_, r) =>
        r.rate != null ? `${trimDecimal(r.rate)}% of ${formatMoney(r.wageBase)}` : EMPTY,
    },
    {
      title: 'Monthly',
      dataIndex: 'monthlyAmount',
      key: 'monthlyAmount',
      width: 140,
      render: (amt) => formatMoney(amt),
    },
    ...(showAnnual
      ? [
          {
            title: 'Annual',
            dataIndex: 'annualAmount',
            key: 'annualAmount',
            width: 140,
            render: (amt) => formatMoney(amt),
          },
        ]
      : []),
    {
      title: 'CTC',
      dataIndex: 'includedInCtc',
      key: 'includedInCtc',
      width: 110,
      render: (included) =>
        included ? <Tag color="blue">In CTC</Tag> : <Text type="secondary">Not in CTC</Text>,
    },
  ];

  return (
    <Table
      id={id}
      dataSource={statutory || []}
      columns={columns}
      rowKey={(r) => r.id || `${r.componentCode}-${r.share}`}
      pagination={false}
      size="small"
      locale={{ emptyText: 'No statutory contributions on this version' }}
    />
  );
}

StatutoryContributionsTable.propTypes = {
  statutory: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.string,
      componentCode: PropTypes.string,
      share: PropTypes.string,
      wageBase: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      rate: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      monthlyAmount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      annualAmount: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
      includedInCtc: PropTypes.bool,
    })
  ),
  id: PropTypes.string,
  showAnnual: PropTypes.bool,
};
