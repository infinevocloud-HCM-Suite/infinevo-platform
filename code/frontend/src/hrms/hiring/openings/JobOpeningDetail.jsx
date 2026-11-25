import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, Tag, Button, Space, Typography, Spin, message, Modal, Select, Input, Divider, Row, Col, theme } from 'antd';
import { ArrowLeftOutlined, UserAddOutlined, CalendarOutlined, FileTextOutlined } from '@ant-design/icons';
import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useCan } from '@shell/screens';
import { hiringService } from '../hiringService.js';

const { Text } = Typography;

const STAGES = [
  { key: 'APPLIED', label: 'Applied', color: 'blue' },
  { key: 'SCREENING', label: 'Screening', color: 'cyan' },
  { key: 'INTERVIEWING', label: 'Interviewing', color: 'purple' },
  { key: 'OFFERED', label: 'Offered', color: 'orange' },
  { key: 'HIRED', label: 'Hired', color: 'green' },
  { key: 'REJECTED', label: 'Rejected', color: 'red' },
];

export function JobOpeningDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const canManage = useCan('hrms.hiring.manage');

  const [loading, setLoading] = useState(true);
  const [opening, setOpening] = useState(null);
  const [applications, setApplications] = useState([]);
  const [candidates, setCandidates] = useState([]);

  // Link candidate modal
  const [linkModalOpen, setLinkModalOpen] = useState(false);
  const [selectedCandidateId, setSelectedCandidateId] = useState(null);

  // Move stage modal
  const [stageModalOpen, setStageModalOpen] = useState(false);
  const [selectedApp, setSelectedApp] = useState(null);
  const [targetStage, setTargetStage] = useState(null);
  const [rejectionReason, setRejectionReason] = useState('');

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [opData, appsData, candsData] = await Promise.all([
        hiringService.openings.get(id),
        hiringService.openings.listApplications(id),
        hiringService.candidates.list(),
      ]);
      setOpening(opData);
      setApplications(appsData);
      setCandidates(candsData);
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to load opening details');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleLinkCandidate = async () => {
    if (!selectedCandidateId) {
      message.warning('Please select a candidate');
      return;
    }
    try {
      await hiringService.openings.apply(id, selectedCandidateId);
      message.success('Candidate added to opening pipeline');
      setLinkModalOpen(false);
      setSelectedCandidateId(null);
      loadData();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Failed to add candidate');
    }
  };

  const handleStageChange = async () => {
    if (!selectedApp || !targetStage) return;
    try {
      await hiringService.applications.updateStage(
        selectedApp.id,
        targetStage,
        targetStage === 'REJECTED' ? rejectionReason : null
      );
      message.success(`Application moved to ${targetStage}`);
      setStageModalOpen(false);
      setSelectedApp(null);
      setTargetStage(null);
      setRejectionReason('');
      loadData();
    } catch (err) {
      message.error(err?.response?.data?.message || err?.message || 'Stage update failed');
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!opening) {
    return (
      <div style={{ padding: 24 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/hrms/hiring/openings')}>
          Back to Openings
        </Button>
        <Text type="danger" style={{ display: 'block', marginTop: 16 }}>Opening not found</Text>
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Button
        icon={<ArrowLeftOutlined />}
        onClick={() => navigate('/hrms/hiring/openings')}
        style={{ marginBottom: 12 }}
      >
        All Openings
      </Button>

      <PageHeader
        title={`${opening.openingCode}: ${opening.title}`}
        subtitle={`Target Headcount: ${opening.openingsCount} • Filled: ${opening.filledCount} • Status: ${opening.status}`}
        actions={
          canManage && (
            <Button
              type="primary"
              icon={<UserAddOutlined />}
              onClick={() => setLinkModalOpen(true)}
            >
              Add Candidate to Pipeline
            </Button>
          )
        }
      />

      {/* Stage Columns / Pipeline Board */}
      <div style={{ marginTop: 20 }}>
        <Row gutter={[16, 16]}>
          {STAGES.map((stg) => {
            const stageApps = applications.filter((a) => a.stage === stg.key);
            return (
              <Col xs={24} sm={12} md={8} lg={4} key={stg.key}>
                <Card
                  size="small"
                  title={
                    <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                      <span>{stg.label}</span>
                      <Tag color={stg.color}>{stageApps.length}</Tag>
                    </Space>
                  }
                  style={{
                    minHeight: 460,
                    backgroundColor: token.colorFillAlter,
                    borderRadius: token.borderRadiusLG,
                  }}
                >
                  <Space direction="vertical" style={{ width: '100%' }} size="small">
                    {stageApps.length === 0 ? (
                      <Text type="secondary" style={{ fontSize: 12, textAlign: 'center', display: 'block', padding: '16px 0' }}>
                        No candidates
                      </Text>
                    ) : (
                      stageApps.map((app) => (
                        <Card
                          key={app.id}
                          size="small"
                          style={{
                            borderRadius: token.borderRadiusSM,
                            boxShadow: token.boxShadowTertiary,
                          }}
                        >
                          <Text strong style={{ display: 'block' }}>{app.candidateName}</Text>
                          <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>{app.candidateEmail}</Text>
                          {app.candidatePhone && (
                            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>{app.candidatePhone}</Text>
                          )}

                          <Divider style={{ margin: '8px 0' }} />

                          {canManage && (
                            <Space size={4} wrap>
                              <Button
                                size="small"
                                type="link"
                                style={{ padding: 0 }}
                                onClick={() => {
                                  setSelectedApp(app);
                                  setTargetStage(app.stage);
                                  setStageModalOpen(true);
                                }}
                              >
                                Move Stage
                              </Button>
                              <Button
                                size="small"
                                type="link"
                                icon={<CalendarOutlined />}
                                onClick={() => navigate('/hrms/hiring/interviews')}
                              />
                              <Button
                                size="small"
                                type="link"
                                icon={<FileTextOutlined />}
                                onClick={() => navigate('/hrms/hiring/offers')}
                              />
                            </Space>
                          )}
                        </Card>
                      ))
                    )}
                  </Space>
                </Card>
              </Col>
            );
          })}
        </Row>
      </div>

      {/* Link candidate modal */}
      <Modal
        title="Add Candidate to Opening"
        open={linkModalOpen}
        onOk={handleLinkCandidate}
        onCancel={() => setLinkModalOpen(false)}
        okText="Add to Pipeline"
      >
        <div style={{ marginTop: 12 }}>
          <Text style={{ display: 'block', marginBottom: 8 }}>Select candidate from talent directory:</Text>
          <Select
            showSearch
            style={{ width: '100%' }}
            placeholder="Search candidate by name or email"
            optionFilterProp="label"
            value={selectedCandidateId}
            onChange={setSelectedCandidateId}
            options={candidates.map((c) => ({
              value: c.id,
              label: `${c.firstName} ${c.lastName} (${c.email})`,
            }))}
          />
        </div>
      </Modal>

      {/* Stage movement modal */}
      <Modal
        title={`Move Application: ${selectedApp?.candidateName}`}
        open={stageModalOpen}
        onOk={handleStageChange}
        onCancel={() => setStageModalOpen(false)}
        okText="Confirm Stage Move"
      >
        <div style={{ marginTop: 12 }}>
          <Text style={{ display: 'block', marginBottom: 8 }}>Select new stage:</Text>
          <Select
            style={{ width: '100%' }}
            value={targetStage}
            onChange={setTargetStage}
            options={STAGES.map((s) => ({ value: s.key, label: s.label }))}
          />

          {targetStage === 'REJECTED' && (
            <div style={{ marginTop: 16 }}>
              <Text style={{ display: 'block', marginBottom: 8 }}>Rejection Reason:</Text>
              <Input.TextArea
                rows={3}
                placeholder="Optional explanation for rejection"
                value={rejectionReason}
                onChange={(e) => setRejectionReason(e.target.value)}
              />
            </div>
          )}
        </div>
      </Modal>
    </div>
  );
}

