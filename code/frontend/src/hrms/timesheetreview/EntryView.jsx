import { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Spin, Table, Tag, Typography } from 'antd';
import { Link, useParams } from 'react-router-dom';
import { reviewService } from './reviewService.js';
import { STATUS_COLOR, STATUS_LABEL, formatHundredths } from '../timesheet/weekGrid.js';
import { dayTotals, gridColumns, sumRows, taskRows } from './reviewGrid.js';

/** The one project line an approver decides (W-48.3 §5), opened from the approvals inbox. */
export function EntryView() {
  const { entryId } = useParams();
  const [entry, setEntry] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let live = true;
    reviewService
      .entry(entryId)
      .then((d) => live && setEntry(d))
      .catch(() => live && setError('Could not load the timesheet entry.'));
    return () => {
      live = false;
    };
  }, [entryId]);

  const back = <Link to="/approvals">Back to approvals</Link>;
  if (error) return <Alert type="error" message={error} action={back} />;
  if (!entry) return <Spin />;

  const rows = taskRows([entry]);
  const { cols, dates } = gridColumns(entry.week_start_date, { withProject: false });
  const totals = dayTotals(rows, dates);

  return (
    <Card title={entry.project_name || entry.project_id} extra={back}>
      <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Employee">{entry.employee_name || entry.employee_id}</Descriptions.Item>
        <Descriptions.Item label="Week">
          {entry.week_start_date} to {entry.week_end_date}
        </Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[entry.status]}>{STATUS_LABEL[entry.status] || entry.status}</Tag>
        </Descriptions.Item>
      </Descriptions>
      <Table
        rowKey="key"
        size="small"
        pagination={false}
        columns={cols}
        dataSource={rows}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}>Total</Table.Summary.Cell>
            {totals.map((t, i) => (
              <Table.Summary.Cell key={dates[i]} index={i + 1} align="right">
                {formatHundredths(t)}
              </Table.Summary.Cell>
            ))}
            <Table.Summary.Cell index={8} align="right">
              {formatHundredths(sumRows(rows))}
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
      {entry.rejection_reason && (
        <Typography.Paragraph type="danger" style={{ marginTop: 12 }}>
          Rejected: {entry.rejection_reason}
        </Typography.Paragraph>
      )}
    </Card>
  );
}
