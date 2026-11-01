import PropTypes from 'prop-types';
import { Table, Tag, Typography } from 'antd';
import { EMPTY, formatMoney, humanize } from '@shared/ui/format.js';
import { statutoryName } from './statutoryLabels.js';
import { isPositiveDecimal, trimDecimal } from './decimal.js';

const { Text } = Typography;

// Which company switch shows each employer line in the salary structure (Payroll -> Settings -> EPF / ESI).
const STRUCTURE_SWITCH = {
  EPF_EMPLOYER: ['epf', 'includeEmployerInStructure'],
  EPS_EMPLOYER: ['epf', 'includeEmployerInStructure'],
  EDLI: ['epf', 'includeEdliInStructure'],
  EPF_ADMIN: ['epf', 'includeAdminInStructure'],
  ESI_EMPLOYER: ['esi', 'includeInStructure'],
};

// EDLI and admin: the structure switch decides whether they are part of the salary at all (W-76.3).
const PART_OF_SALARY_ONLY_WHEN_ON = new Set(['EDLI', 'EPF_ADMIN']);

/**
 * Whether a line belongs in the salary structure the officer sees (W-76.2): employee lines always;
 * EDLI and admin only when their switch is on (they are then also paid, W-76.3); employer PF, EPS and ESI
 * when their display switch is on or they count in CTC (a CTC line must stay visible, or the balancing
 * allowance drops for no visible reason). Settings not loaded -> shown, as before.
 */
export function shownInStructure(line, { epf, esi } = {}) {
  const code = line?.componentCode;
  const rule = STRUCTURE_SWITCH[code];
  if (!rule) return true;
  const settings = rule[0] === 'epf' ? epf : esi;
  if (!settings) return true;
  // EDLI and admin are part of the salary only when their switch is on, whatever the CTC switch says.
  if (PART_OF_SALARY_ONLY_WHEN_ON.has(code)) return Boolean(settings[rule[1]]);
  return line.includedInCtc || Boolean(settings[rule[1]]);
}

/**
 * The statutory lines of a salary version - EPF, EPS, EDLI, admin charges and ESI, employer and
 * employee - from `SalaryVersionResponse.statutory` or a preview's `statutory` (W-75.2 §5, D-130).
 * Every amount is the server's; the table only formats it.
 */
export function StatutoryContributionsTable({
  statutory,
  id = 'table-salary-statutory',
  showAnnual = true,
  epfSetting = null,
  esiSetting = null,
}) {
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

  // A zero line (EPS for an employee not in EPS) says nothing; a line the company keeps out of the
  // structure is not shown.
  const rows = (statutory || []).filter(
    (line) =>
      isPositiveDecimal(line.monthlyAmount) && shownInStructure(line, { epf: epfSetting, esi: esiSetting })
  );

  return (
    <Table
      id={id}
      dataSource={rows}
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
  epfSetting: PropTypes.object,
  esiSetting: PropTypes.object,
};
