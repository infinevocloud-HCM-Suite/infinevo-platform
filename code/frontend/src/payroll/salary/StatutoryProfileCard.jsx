import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { Card, Descriptions, Typography, Spin } from 'antd';
import { useCan } from '@shell/screens';
import { statutoryProfileService } from './statutoryProfileService.js';
import { errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

const NOT_SET = 'Not set';

function yesNo(value) {
  return value ? 'Yes' : 'No';
}

function text(value) {
  return value && String(value).trim() ? value : <Text type="secondary">{NOT_SET}</Text>;
}

/**
 * The employee's statutory details, read only. They are edited in the Set up salary / Revise drawer
 * and nowhere else, so the two can never disagree (W-76.2).
 */
export function StatutoryProfileCard({ employeeId }) {
  const canManage = useCan('payroll.salary.manage');
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!employeeId) return undefined;
    let live = true;
    setLoading(true);
    statutoryProfileService
      .get(employeeId)
      .then((data) => {
        if (live) setProfile(data || null);
      })
      .catch((err) => {
        if (live && err?.code !== 'NOT_FOUND') errorMsg(err);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [employeeId]);

  const p = profile || {};

  return (
    <Card
      variant="borderless"
      id="card-statutory-details"
      title={<Title level={5} style={{ margin: 0 }}>Statutory Details</Title>}
      extra={
        canManage ? (
          <Text type="secondary" data-testid="statutory-edit-hint">
            Edit in Set up salary or Revise
          </Text>
        ) : null
      }
      style={{ marginTop: 24 }}
    >
      <Spin spinning={loading}>
        <Descriptions bordered size="small" column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="Employees' Provident Fund (EPF)">{yesNo(p.eligibleForPf)}</Descriptions.Item>
          <Descriptions.Item label="Employee Pension Scheme (EPS)">{yesNo(p.eligibleForEps)}</Descriptions.Item>
          <Descriptions.Item label="EPS on higher wages">{yesNo(p.contributesEpsOnHigherWages)}</Descriptions.Item>
          <Descriptions.Item label="Professional Tax (PT)">{yesNo(p.eligibleForPt)}</Descriptions.Item>
          <Descriptions.Item label="Employee State Insurance (ESI)">{yesNo(p.eligibleForEsi)}</Descriptions.Item>
          <Descriptions.Item label="Labour Welfare Fund (LWF)">{yesNo(p.eligibleForLwf)}</Descriptions.Item>
          <Descriptions.Item label="Director">{yesNo(p.director)}</Descriptions.Item>
          <Descriptions.Item label="TDS applicable">
            <span data-testid="statutory-tds-applicable">{yesNo(p.tdsApplicable !== false)}</span>
          </Descriptions.Item>
          <Descriptions.Item label="PF account number">{text(p.pfAccountNumber)}</Descriptions.Item>
          <Descriptions.Item label="UAN">{text(p.uan)}</Descriptions.Item>
          <Descriptions.Item label="ESI number">{text(p.esiNumber)}</Descriptions.Item>
        </Descriptions>
      </Spin>
    </Card>
  );
}

StatutoryProfileCard.propTypes = {
  employeeId: PropTypes.string.isRequired,
};
