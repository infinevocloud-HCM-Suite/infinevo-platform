import { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Space, Spin, Table, Tag, Typography } from 'antd';
import { Link, useParams } from 'react-router-dom';
import { reviewService } from './reviewService.js';
import { STATUS_COLOR, STATUS_LABEL, formatHundredths } from '../timesheet/weekGrid.js';
import { dayTotals, gridColumns, sumRows, taskRows } from './reviewGrid.js';

/** One week, read-only (W-48.3 §5): project and task rows, Mon to Sun, totals, rejection reasons. */
export function WeekView() {
  const { id } = useParams();
  const [sheet, setSheet] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let live = true;
    reviewService
      .get(id)
      .then((d) => live && setSheet(d))
      .catch(() => live && setError('Could not load the timesheet.'));
    return () => {
      live = false;
    };
  }, [id]);

  if (error) return <Alert type="error" message={error} />;
  if (!sheet) return <Spin />;

  const rows = taskRows(sheet.projects);
  const { cols, dates } = gridColumns(sheet.week_start_date);
  const totals = dayTotals(rows, dates);
  const rejected = (sheet.projects || []).filter((p) => p.rejection_reason);

  return (
    <Card title="Timesheet" extra={<Link to="/hrms/timesheet-review">Back to review</Link>}>
      <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Employee">{sheet.employee_name || sheet.employee_id}</Descriptions.Item>
        <Descriptions.Item label="Week">
          {sheet.week_start_date} to {sheet.week_end_date}
        </Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[sheet.status]}>{STATUS_LABEL[sheet.status] || sheet.status}</Tag>
        </Descriptions.Item>
      </Descriptions>
      <Space wrap style={{ marginBottom: 16 }}>
        {(sheet.projects || []).map((p) => (
          <Tag key={p.id} color={STATUS_COLOR[p.status]}>
            {p.project_name || p.project_id}: {STATUS_LABEL[p.status] || p.status}
          </Tag>
        ))}
      </Space>
      <Table
        rowKey="key"
        size="small"
        pagination={false}
        columns={cols}
        dataSource={rows}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0} colSpan={2}>
              Total
            </Table.Summary.Cell>
            {totals.map((t, i) => (
              <Table.Summary.Cell key={dates[i]} index={i + 2} align="right">
                {formatHundredths(t)}
              </Table.Summary.Cell>
            ))}
            <Table.Summary.Cell index={9} align="right">
              {formatHundredths(sumRows(rows))}
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
      {rejected.map((p) => (
        <Typography.Paragraph key={p.id} type="danger" style={{ marginTop: 12 }}>
          {p.project_name || p.project_id} rejected: {p.rejection_reason}
        </Typography.Paragraph>
      ))}
    </Card>
  );
}
