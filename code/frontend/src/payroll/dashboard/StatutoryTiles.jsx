import PropTypes from 'prop-types';
import { Card, Col, Descriptions, Row, Typography } from 'antd';
import { formatAmount } from '../claims/claimLabels.js';
import { label } from './financialYear.js';

const { Text } = Typography;

const TILES = [
  { key: 'epf', title: 'EPF', employer: true },
  { key: 'esi', title: 'ESI', employer: true },
  { key: 'professional_tax', title: 'Professional tax', employer: false },
  { key: 'tds', title: 'TDS', employer: false },
];

/** A zero is the true answer and shows as `0.00`, never a dash (W-47.5 §5, DEBT-028). */
function amount(value) {
  return formatAmount(value ?? '0');
}

/** The four statutory tiles, summed by W-37 over PAID runs in the year (W-47.5 §5). */
export function StatutoryTiles({ statutory, fy }) {
  return (
    <Row gutter={[16, 16]}>
      {TILES.map((t) => {
        const tile = statutory?.[t.key];
        return (
          <Col key={t.key} xs={24} sm={12} lg={6}>
            <Card title={t.title} size="small" data-testid={`tile-${t.key}`}>
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Employee">{amount(tile?.employee)}</Descriptions.Item>
                {t.employer && (
                  <Descriptions.Item label="Employer">{amount(tile?.employer)}</Descriptions.Item>
                )}
              </Descriptions>
              <Text type="secondary">PAID runs, FY {label(fy)}</Text>
            </Card>
          </Col>
        );
      })}
    </Row>
  );
}

const tileShape = PropTypes.shape({
  employee: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
  employer: PropTypes.oneOfType([PropTypes.number, PropTypes.string]),
});

StatutoryTiles.propTypes = {
  statutory: PropTypes.shape({
    epf: tileShape,
    esi: tileShape,
    professional_tax: tileShape,
    tds: tileShape,
  }),
  fy: PropTypes.number.isRequired,
};
