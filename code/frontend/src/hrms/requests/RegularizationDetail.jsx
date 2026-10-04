import { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Spin, Tag } from 'antd';
import { Link, useLocation, useParams } from 'react-router-dom';
import { NotFound } from '@shell/screens';
import { requestService } from './requestService.js';
import { STATUS_COLOR, stamp, timeOf } from './range.js';

/** The back link: to the inbox when opened from it, else to the list (W-48.5 §5). */
export function backLink(location, list) {
  return location.state?.from === '/approvals' ? (
    <Link to="/approvals">Back to approvals</Link>
  ) : (
    <Link to={list}>Back to list</Link>
  );
}

/** One regularization request, read-only (W-48.5 §5). */
export function RegularizationDetail() {
  const { id } = useParams();
  const location = useLocation();
  const [item, setItem] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let live = true;
    setItem(null);
    setError(null);
    requestService
      .regularization(id)
      .then((d) => live && setItem(d))
      .catch((err) => live && setError(err || {}));
    return () => {
      live = false;
    };
  }, [id]);

  const back = backLink(location, '/hrms/regularizations');
  if (error?.status === 404) return <NotFound />;
  if (error) return <Alert type="error" message={error.message || 'Could not load the request'} action={back} />;
  if (!item) return <Spin />;

  return (
    <Card title="Regularization" extra={back}>
      <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="Date">{item.date}</Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[item.status]}>{item.status}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="In">{timeOf(item.inAt)}</Descriptions.Item>
        <Descriptions.Item label="Out">{timeOf(item.outAt)}</Descriptions.Item>
        <Descriptions.Item label="Reason" span={2}>
          {item.reason}
        </Descriptions.Item>
        <Descriptions.Item label="Decided at">{stamp(item.decidedAt)}</Descriptions.Item>
        <Descriptions.Item label="Decided by">{item.decidedBy || '—'}</Descriptions.Item>
        <Descriptions.Item label="Decision comment" span={2}>
          {item.decisionComment || '—'}
        </Descriptions.Item>
      </Descriptions>
    </Card>
  );
}
