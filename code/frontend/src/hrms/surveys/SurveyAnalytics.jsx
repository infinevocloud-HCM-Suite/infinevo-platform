import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card,
  Row,
  Col,
  Statistic,
  Progress,
  Tag,
  Rate,
  Alert,
  Spin,
  Typography,
  Space,
  Empty,
  message,
  theme,
} from 'antd';
import {
  LockOutlined,
  CheckCircleOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { surveyService } from './surveyService.js';

const { Title, Text, Paragraph } = Typography;

export function SurveyAnalytics() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { token } = theme.useToken();

  const [loading, setLoading] = useState(true);
  const [results, setResults] = useState(null);

  const fetchResults = useCallback(async () => {
    setLoading(true);
    try {
      const data = await surveyService.getResults(id);
      setResults(data);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load survey analytics');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchResults();
  }, [fetchResults]);

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!results) {
    return (
      <div style={{ padding: 24 }}>
        <Empty description="No survey results found" />
      </div>
    );
  }

  return (
    <div style={{ padding: token.paddingLG }}>
      <PageHeader
        title={results.title || 'Survey Analytics'}
        subtitle={results.isAnonymous ? 'Anonymous Survey' : 'Identified Survey'}
        onBack={() => navigate('/hrms/surveys')}
        backLabel="Back to Surveys"
      />

      {results.resultsWithheld ? (
        <Card style={{ marginTop: token.marginLG, borderColor: token.colorWarningBorder, background: token.colorWarningBg }}>
          <Alert
            message="Respondent Confidentiality Protected"
            description={
              results.withholdingReason ||
              'Results for anonymous surveys are withheld until at least 5 responses are collected to protect individual anonymity.'
            }
            type="warning"
            showIcon
            icon={<LockOutlined />}
          />
          <div style={{ marginTop: token.marginMD }}>
            <Statistic
              title="Responses Collected So Far"
              value={results.totalResponses}
              suffix="/ 5 minimum required"
            />
          </div>
        </Card>
      ) : (
        <>
          <Row gutter={[16, 16]} style={{ marginTop: token.marginMD }}>
            <Col xs={24} sm={8}>
              <Card style={{ borderColor: token.colorBorderSecondary }}>
                <Statistic title="Total Responses" value={results.totalResponses} prefix={<CheckCircleOutlined />} />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card style={{ borderColor: token.colorBorderSecondary }}>
                <Statistic title="Total Eligible Audience" value={results.totalEligible} />
              </Card>
            </Col>
            <Col xs={24} sm={8}>
              <Card style={{ borderColor: token.colorBorderSecondary }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Text type="secondary">Participation Rate</Text>
                    <Title level={3} style={{ margin: 0 }}>
                      {results.participationRate}%
                    </Title>
                  </div>
                  <Progress type="circle" percent={Math.round(results.participationRate)} size={54} />
                </div>
              </Card>
            </Col>
          </Row>

          <Title level={4} style={{ marginTop: token.marginLG, marginBottom: token.marginMD }}>
            Question Breakdown
          </Title>

          <div style={{ display: 'flex', flexDirection: 'column', gap: token.marginMD }}>
            {(results.questionResults || []).map((q, idx) => (
              <Card
                key={q.questionId || idx}
                style={{ borderColor: token.colorBorderSecondary }}
                title={
                  <Space>
                    <QuestionCircleOutlined />
                    <span>{`Q${idx + 1}. ${q.questionText}`}</span>
                    <Tag color="cyan">{q.questionType}</Tag>
                  </Space>
                }
              >
                {q.questionType === 'RATING_1_5' && (
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: token.marginMD, marginBottom: token.marginMD }}>
                      <Title level={2} style={{ margin: 0 }}>
                        {q.averageRating ? q.averageRating.toFixed(1) : '—'}
                      </Title>
                      <div>
                        <Rate allowHalf disabled value={q.averageRating || 0} />
                        <div>
                          <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                            Average rating out of 5 stars
                          </Text>
                        </div>
                      </div>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, maxWidth: 450 }}>
                      {[5, 4, 3, 2, 1].map((star) => {
                        const count = q.ratingDistribution?.[star] || 0;
                        const pct = results.totalResponses > 0 ? (count / results.totalResponses) * 100 : 0;
                        return (
                          <div key={star} style={{ display: 'flex', alignItems: 'center', gap: token.marginSM }}>
                            <Text style={{ width: 50 }}>{star} Stars</Text>
                            <Progress percent={Math.round(pct)} style={{ flex: 1, margin: 0 }} size="small" />
                            <Text type="secondary" style={{ width: 40, textAlign: 'right' }}>
                              {count}
                            </Text>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {(q.questionType === 'SINGLE_CHOICE' || q.questionType === 'MULTIPLE_CHOICE') && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: token.marginSM, maxWidth: 600 }}>
                    {(q.optionBreakdown || []).map((opt) => (
                      <div key={opt.option} style={{ marginBottom: 4 }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 2 }}>
                          <Text strong>{opt.option}</Text>
                          <Text type="secondary">
                            {opt.count} ({opt.percentage}%)
                          </Text>
                        </div>
                        <Progress percent={Math.round(opt.percentage)} size="small" />
                      </div>
                    ))}
                  </div>
                )}

                {q.questionType === 'TEXT' && (
                  <div>
                    {(!q.textAnswers || q.textAnswers.length === 0) ? (
                      <Text type="secondary">No text comments submitted yet.</Text>
                    ) : (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: token.marginXS }}>
                        {q.textAnswers.map((txt, cIdx) => (
                          <Card
                            key={cIdx}
                            size="small"
                            style={{
                              background: token.colorFillAlter,
                              borderColor: token.colorBorderSecondary,
                            }}
                          >
                            <Paragraph style={{ margin: 0 }}>{txt}</Paragraph>
                          </Card>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </Card>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

