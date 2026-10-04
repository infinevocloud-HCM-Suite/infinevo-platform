import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Col, Empty, Result, Row, Select, Space, Spin, Typography } from 'antd';
import { dashboardService } from './dashboardService.js';
import { currentStartYear, options } from './financialYear.js';
import { CurrentRunCard } from './CurrentRunCard.jsx';
import { HeadcountCard } from './HeadcountCard.jsx';
import { StatutoryTiles } from './StatutoryTiles.jsx';
import { YearTable } from './YearTable.jsx';
import { SetupCard } from './SetupCard.jsx';
import { readError } from '../tax/apiError.js';

const { Title } = Typography;

/** How often the page re-reads the dashboard while the current run is `COMPUTING` (W-47.5 §3). */
export const POLL_MS = 10000;

/**
 * `/payroll/dashboard` (W-47.5 §5), built from `GET /v1/payroll/dashboard` alone. There are no
 * constants and no fallback figures (DEBT-028): a failed load shows an error with Retry.
 */
export function DashboardPage() {
  const [fy, setFy] = useState(() => currentStartYear());
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const requestId = useRef(0);

  const load = useCallback(
    async ({ quiet = false } = {}) => {
      const id = ++requestId.current;
      if (!quiet) {
        setLoading(true);
        setError(null);
      }
      try {
        const next = await dashboardService.summary(fy);
        if (id === requestId.current) setData(next);
      } catch (err) {
        if (id === requestId.current) {
          setData(null);
          setError(readError(err, 'Could not load the payroll dashboard').message);
        }
      } finally {
        if (id === requestId.current) setLoading(false);
      }
    },
    [fy]
  );

  useEffect(() => {
    load();
  }, [load]);

  const computing = data?.current_run?.status === 'COMPUTING';

  useEffect(() => {
    if (!computing) return undefined;
    const timer = setInterval(() => load({ quiet: true }), POLL_MS);
    return () => clearInterval(timer);
  }, [computing, load]);

  const noRuns = data && !data.current_run && (data.recent_runs ?? []).length === 0;

  let body;
  if (error) {
    body = (
      <Result
        status="error"
        title="Could not load the payroll dashboard"
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
    body = (
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} lg={16}>
            {data.current_run ? (
              <CurrentRunCard run={data.current_run} />
            ) : (
              noRuns && (
                <Empty description="No pay run yet">
                  <Link to="/payroll/runs">Go to pay runs</Link>
                </Empty>
              )
            )}
          </Col>
          <Col xs={24} lg={8}>
            <HeadcountCard employees={data.employees} />
          </Col>
        </Row>
        {!noRuns && (
          <>
            <StatutoryTiles statutory={data.statutory} fy={fy} />
            <YearTable months={data.months} yearTotals={data.year_totals} recentRuns={data.recent_runs} />
          </>
        )}
      </Space>
    );
  }

  return (
    <div style={{ padding: 24, maxWidth: 1200, margin: '0 auto' }}>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={2} style={{ margin: 0 }}>
          Payroll dashboard
        </Title>
        <Select
          aria-label="Financial year"
          value={fy}
          options={options()}
          onChange={setFy}
          style={{ width: 140 }}
        />
      </Space>
      <SetupCard />
      {body}
    </div>
  );
}
