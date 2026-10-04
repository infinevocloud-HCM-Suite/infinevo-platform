import { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Spin, Tag } from 'antd';
import { useLocation, useParams } from 'react-router-dom';
import { NotFound } from '@shell/screens';
import { requestService } from './requestService.js';
import { backLink } from './RegularizationDetail.jsx';
import { STATUS_COLOR, stamp } from './range.js';

/** One overtime request, read-only (W-48.5 §5). Hours and amount are shown as sent; nothing is computed. */
export function OvertimeDetail() {
  const { id } = useParams();
  const location = useLocation();
  const [item, setItem] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let live = true;
    setItem(null);
    setError(null);
    requestService
      .overtime(id)
      .then((d) => live && setItem(d))
      .catch((err) => live && setError(err || {}));
    return () => {
      live = false;
    };
  }, [id]);

  const back = backLink(location, '/hrms/overtime-requests');
  if (error?.status === 404) return <NotFound />;
  if (error) return <Alert type="error" message={error.message || 'Could not load the request'} action={back} />;
  if (!item) return <Spin />;

  return (
    <Card title="Overtime request" extra={back}>
      <Descriptions size="small" column={2} bordered>
        <Descriptions.Item label="Date">{item.overtimeDate}</Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[item.status]}>{item.status}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Hours">{item.hours}</Descriptions.Item>
        <Descriptions.Item label="Amount">{item.amount ?? '—'}</Descriptions.Item>
        <Descriptions.Item label="Source">{item.source || '—'}</Descriptions.Item>
        {item.postedPeriod && <Descriptions.Item label="Posted period">{item.postedPeriod}</Descriptions.Item>}
        <Descriptions.Item label="Remarks" span={2}>
          {item.remarks || '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Requested at">{stamp(item.createdAt)}</Descriptions.Item>
      </Descriptions>
    </Card>
  );
}
