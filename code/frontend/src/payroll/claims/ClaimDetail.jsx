import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Card, Descriptions, Result, Skeleton, Space, Tag, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { NotFound } from '@shell/screens';
import { claimService } from './claimService.js';
import { claimStatus, formatAmount, formatDate } from './claimLabels.js';
import { readError } from '../tax/apiError.js';

const { Title, Text } = Typography;

const show = (v) => (v === null || v === undefined || v === '' ? '-' : String(v));

/**
 * Every field of a claim response, shared by the officer detail and the `/me` panel (DEBT-026).
 * The approval link is the officer's: an employee does not open approval instances.
 */
export function ClaimDescriptions({ claim, showApprovalLink = false, showEmployee = true }) {
  const status = claimStatus(claim.status);
  return (
    <Descriptions bordered column={1} size="small">
      {showEmployee && (
        <Descriptions.Item label="Employee">
          {show(claim.employee_name)}
          {claim.employee_id && (
            <Text type="secondary" style={{ marginLeft: 8 }}>
              {claim.employee_id}
            </Text>
          )}
        </Descriptions.Item>
      )}
      <Descriptions.Item label="Component">
        {show(claim.component_name)}
        {claim.component_code && (
          <Text type="secondary" style={{ marginLeft: 8 }}>
            {claim.component_code}
          </Text>
        )}
      </Descriptions.Item>
      <Descriptions.Item label="Limit">{formatAmount(claim.max_limit)}</Descriptions.Item>
      <Descriptions.Item label="Requested">{formatAmount(claim.requested_amount)}</Descriptions.Item>
      <Descriptions.Item label="Approved">{formatAmount(claim.approved_amount)}</Descriptions.Item>
      <Descriptions.Item label="Bill date">{formatDate(claim.bill_date)}</Descriptions.Item>
      <Descriptions.Item label="Description">{show(claim.description)}</Descriptions.Item>
      <Descriptions.Item label="Status">
        <Space>
          <Tag color={status.color}>{status.label}</Tag>
          {showApprovalLink && claim.status === 'SUBMITTED' && claim.approval_instance_id && (
            <Link to={`/approvals/${claim.approval_instance_id}`}>Open the approval</Link>
          )}
        </Space>
      </Descriptions.Item>
      <Descriptions.Item label="Remarks">{show(claim.remarks)}</Descriptions.Item>
      <Descriptions.Item label="Posted period">{show(claim.posted_period)}</Descriptions.Item>
      <Descriptions.Item label="Document">{show(claim.document_id)}</Descriptions.Item>
      <Descriptions.Item label="Approval">{show(claim.approval_instance_id)}</Descriptions.Item>
      <Descriptions.Item label="Pay input">{show(claim.pay_input_id)}</Descriptions.Item>
      <Descriptions.Item label="Approved by">{show(claim.approved_by)}</Descriptions.Item>
      <Descriptions.Item label="Approved at">{show(claim.approved_at)}</Descriptions.Item>
      <Descriptions.Item label="Created at">{show(claim.created_at)}</Descriptions.Item>
      <Descriptions.Item label="Updated at">{show(claim.updated_at)}</Descriptions.Item>
      <Descriptions.Item label="Claim id">{show(claim.id)}</Descriptions.Item>
    </Descriptions>
  );
}

ClaimDescriptions.propTypes = {
  claim: PropTypes.object.isRequired,
  showApprovalLink: PropTypes.bool,
  showEmployee: PropTypes.bool,
};

/** `/payroll/claims/:id` (W-47.4 §5): reached from the list and from the approvals inbox. */
export function ClaimDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [claim, setClaim] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setClaim(await claimService.get(id));
    } catch (err) {
      setClaim(null);
      setError(readError(err, 'Could not load the claim'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <Card>
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  if (error?.status === 404) {
    return <NotFound />;
  }

  if (error) {
    return (
      <Result
        status="error"
        title="Could not load the claim"
        subTitle={error.message}
        extra={
          <Button type="primary" onClick={load}>
            Retry
          </Button>
        }
      />
    );
  }

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/payroll/claims')}>
          Claims
        </Button>
      </Space>
      <Title level={3}>Reimbursement claim</Title>
      <ClaimDescriptions claim={claim} showApprovalLink />
    </div>
  );
}
