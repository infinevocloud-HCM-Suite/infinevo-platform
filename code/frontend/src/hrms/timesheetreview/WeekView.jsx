import { useEffect, useState } from 'react';
import { Alert, Card, Collapse, Descriptions, Space, Spin, Table, Tag, Typography } from 'antd';
import { Link, useParams } from 'react-router-dom';
import { reviewService } from './reviewService.js';
import { STATUS_COLOR, STATUS_LABEL, formatHundredths } from '../timesheet/weekGrid.js';
import { collectDescriptions, dayTotals, gridColumns, sumRows, taskRows } from './reviewGrid.js';
import { formatDate, statusLabel } from '@shared/ui/format.js';

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
  const descriptions = collectDescriptions(rows, dates);

  return (
    <Card title="Timesheet" extra={<Link to="/hrms/timesheet-review">Back to review</Link>}>
      <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Employee">{sheet.employee_name || sheet.employee_id}</Descriptions.Item>
        <Descriptions.Item label="Week">
          {formatDate(sheet.week_start_date)} to {formatDate(sheet.week_end_date)}
        </Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={STATUS_COLOR[sheet.status]}>{STATUS_LABEL[sheet.status] || statusLabel(sheet.status)}</Tag>
        </Descriptions.Item>
      </Descriptions>
      <Space wrap style={{ marginBottom: 16 }}>
        {(sheet.projects || []).map((p) => (
          <Link key={p.id} to={`/hrms/timesheet-review/entries/${p.id}`}>
            <Tag color={STATUS_COLOR[p.status]} style={{ cursor: 'pointer' }}>
              {p.project_name || p.project_id}: {STATUS_LABEL[p.status] || statusLabel(p.status)}
            </Tag>
          </Link>
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
      {descriptions.length > 0 && (
        <Collapse
          ghost
          defaultActiveKey={['descriptions']}
          style={{ marginTop: 16 }}
          items={[
            {
              key: 'descriptions',
              label: <Typography.Text strong>Work descriptions ({descriptions.length})</Typography.Text>,
              children: (
                <Descriptions size="small" column={1} bordered>
                  {descriptions.map((item, idx) => (
                    <Descriptions.Item
                      key={idx}
                      label={`${formatDate(item.date)} · ${item.project ? `${item.project} / ` : ''}${item.task} (${formatHundredths(item.hours)}h)`}
                    >
                      <Typography.Text style={{ whiteSpace: 'pre-wrap' }}>{item.description}</Typography.Text>
                    </Descriptions.Item>
                  ))}
                </Descriptions>
              ),
            },
          ]}
        />
      )}
      {rejected.map((p) => (
        <Typography.Paragraph key={p.id} type="danger" style={{ marginTop: 12 }}>
          {p.project_name || p.project_id} rejected: {p.rejection_reason}
        </Typography.Paragraph>
      ))}
    </Card>
  );
}

