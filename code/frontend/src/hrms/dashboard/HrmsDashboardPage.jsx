import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Col, Divider, Result, Row, Spin, Typography } from 'antd';
import { NotEntitled } from '@shell/screens';
import { hrmsDashboardService } from './hrmsDashboardService.js';
import { TodayCard } from './TodayCard.jsx';
import { TimesheetsCard } from './TimesheetsCard.jsx';
import { MyProjectsCard } from './MyProjectsCard.jsx';
import { MyTasksCard } from './MyTasksCard.jsx';
import { ManagedProjectsCard } from './ManagedProjectsCard.jsx';
import { ApprovalsCard } from './ApprovalsCard.jsx';
import { ReportsCard } from './ReportsCard.jsx';

const { Title, Text } = Typography;

const forbidden = (err) => err?.status === 403 || Boolean(err?.isModuleNotEntitled) || Boolean(err?.isForbidden);

/**
 * `/hrms/dashboard` (W-48.6 §5), built from `GET /v1/hrms/dashboard` alone. A `null` block hides its
 * card; a `null` `team` hides the Team section. No constants and no fallback figures (DEBT-028).
 */
export function HrmsDashboardPage() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notEntitled, setNotEntitled] = useState(false);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const next = await hrmsDashboardService.summary();
      if (id === requestId.current) setData(next);
    } catch (err) {
      if (id === requestId.current) {
        setData(null);
        if (forbidden(err)) setNotEntitled(true);
        else setError(err?.message || 'Could not load the dashboard');
      }
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (notEntitled) return <NotEntitled />;

  let body = null;
  if (error) {
    body = (
      <Result
        status="error"
        title="Could not load the dashboard"
        subTitle={error}
        extra={
          <Button type="primary" onClick={() => load()}>
            Retry
          </Button>
        }
      />
    );
  } else if (loading && !data) {
    body = (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Spin />
      </div>
    );
  } else if (data) {
    const me = data.me ?? {};
    const team = data.team;
    const meCards = [
      me.today && <TodayCard key="today" today={me.today} />,
      me.timesheets && <TimesheetsCard key="timesheets" timesheets={me.timesheets} />,
      me.projects && <MyProjectsCard key="projects" projects={me.projects} />,
      me.tasks && <MyTasksCard key="tasks" tasks={me.tasks} asOf={data.as_of} />,
    ].filter(Boolean);
    const teamCards = team
      ? [
          team.projects && <ManagedProjectsCard key="team-projects" projects={team.projects} />,
          team.approvals && <ApprovalsCard key="approvals" approvals={team.approvals} />,
          team.reports && <ReportsCard key="reports" reports={team.reports} />,
        ].filter(Boolean)
      : [];
    body = (
      <>
        <Text type="secondary">{`As of ${data.as_of}`}</Text>
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          {meCards.map((card) => (
            <Col key={card.key} xs={24} lg={12}>
              {card}
            </Col>
          ))}
        </Row>
        {team && (
          <>
            <Divider orientation="left">Team</Divider>
            <Row gutter={[16, 16]}>
              {teamCards.map((card) => (
                <Col key={card.key} xs={24} lg={12}>
                  {card}
                </Col>
              ))}
            </Row>
          </>
        )}
      </>
    );
  }

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Title level={2}>Dashboard</Title>
      {body}
    </div>
  );
}
