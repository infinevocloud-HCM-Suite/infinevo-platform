import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Steps,
  Form,
  Radio,
  Input,
  DatePicker,
  Button,
  Space,
  Row,
  Col,
  Alert,
  Spin,
  Tag,
  Table,
  Typography,
  Result,
  Divider,
  theme,
} from 'antd';
import {
  SafetyCertificateOutlined,
  MedicineBoxOutlined,
  BankOutlined,
  CheckCircleOutlined,
  ArrowRightOutlined,
  ArrowLeftOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { guidedStatutoryService } from './guidedStatutoryService.js';

const { Text, Title, Paragraph } = Typography;

export function GuidedStatutorySetup() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [form] = Form.useForm();

  const [currentStep, setCurrentStep] = useState(0);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [readiness, setReadiness] = useState(null);
  const [error, setError] = useState(null);
  const [completed, setCompleted] = useState(false);
  const [submitResult, setSubmitResult] = useState(null);

  // Form values state for the wizard
  const [formData, setFormData] = useState({
    epf: {
      registered: true,
      registrationNumber: '',
      registrationDate: null,
      restrictToWageCeiling: true,
      includeEmployerInCtc: true,
      includeEdliInCtc: false,
      includeAdminInCtc: false,
    },
    esi: {
      registered: false,
      registrationNumber: '',
      registrationDate: null,
      includeEmployerInCtc: true,
    },
    ptStates: {},
  });

  const loadReadiness = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await guidedStatutoryService.getReadiness();
      setReadiness(data);

      // Pre-fill default form state from readiness
      const initialPt = {};
      (data.operatingStates || []).forEach((st) => {
        if (st.leviesPt) {
          initialPt[st.stateCode] = {
            registrationNumber: st.registrationNumber || '',
            effectiveFrom: st.effectiveFrom ? dayjs(st.effectiveFrom) : dayjs().startOf('year'),
          };
        }
      });

      const epfReg = data.epf?.registrationNumber || '';
      const esiReg = data.esi?.registrationNumber || '';
      form.setFieldsValue({
        epfRegistrationNumber: epfReg,
        esiRegistrationNumber: esiReg,
      });

      setFormData((prev) => ({
        ...prev,
        epf: {
          ...prev.epf,
          registered: data.epf?.isEnabled ?? true,
          registrationNumber: epfReg,
          registrationDate: data.epf?.registrationDate ? dayjs(data.epf.registrationDate) : null,
          restrictToWageCeiling: data.epf?.restrictEmployeeToCeiling ?? true,
          includeEmployerInCtc: data.epf?.includeEmployerInCtc ?? true,
          includeEdliInCtc: data.epf?.includeEdliInCtc ?? false,
          includeAdminInCtc: data.epf?.includeAdminInCtc ?? false,
        },
        esi: {
          ...prev.esi,
          registered: data.esi?.isEnabled ?? false,
          registrationNumber: esiReg,
          registrationDate: data.esi?.registrationDate ? dayjs(data.esi.registrationDate) : null,
          includeEmployerInCtc: data.esi?.includeEmployerInCtc ?? true,
        },
        ptStates: initialPt,
      }));
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load statutory readiness');
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    loadReadiness();
  }, [loadReadiness]);

  useEffect(() => {
    form.setFieldsValue({
      epfRegistrationNumber: formData.epf.registrationNumber || '',
      esiRegistrationNumber: formData.esi.registrationNumber || '',
    });
  }, [currentStep, formData.epf.registrationNumber, formData.esi.registrationNumber, form]);

  const handleNext = async () => {
    try {
      if (currentStep === 1 && formData.epf.registered) {
        if (!formData.epf.registrationNumber?.trim()) {
          form.setFields([{ name: 'epfRegistrationNumber', errors: ['Registration number is required'] }]);
          return;
        }
      }
      if (currentStep === 2 && formData.esi.registered) {
        if (!formData.esi.registrationNumber?.trim()) {
          form.setFields([{ name: 'esiRegistrationNumber', errors: ['ESI Registration Code is required'] }]);
          return;
        }
      }
      setError(null);
      setCurrentStep((prev) => prev + 1);
    } catch {
      // validation error
    }
  };

  const handlePrev = () => {
    setError(null);
    setCurrentStep((prev) => prev - 1);
  };

  const handleSubmit = async () => {
    if (!readiness) {
      setError('Cannot save settings before statutory readiness has successfully loaded.');
      return;
    }
    try {
      setSubmitting(true);
      setError(null);

      const ptStatesPayload = Object.entries(formData.ptStates)
        .filter(([, val]) => val && val.registrationNumber?.trim())
        .map(([stateCode, val]) => ({
          stateCode,
          registrationNumber: val.registrationNumber.trim(),
          effectiveFrom: val.effectiveFrom ? val.effectiveFrom.format('YYYY-MM-DD') : dayjs().format('YYYY-MM-DD'),
        }));

      const payload = {
        epf: {
          registered: formData.epf.registered,
          registrationNumber: formData.epf.registered ? formData.epf.registrationNumber?.trim() : null,
          registrationDate:
            formData.epf.registered && formData.epf.registrationDate
              ? formData.epf.registrationDate.format('YYYY-MM-DD')
              : null,
          restrictToWageCeiling: formData.epf.restrictToWageCeiling,
          includeEmployerInCtc: formData.epf.includeEmployerInCtc,
          includeEdliInCtc: formData.epf.includeEdliInCtc,
          includeAdminInCtc: formData.epf.includeAdminInCtc,
        },
        esi: {
          registered: formData.esi.registered,
          registrationNumber: formData.esi.registered ? formData.esi.registrationNumber?.trim() : null,
          registrationDate:
            formData.esi.registered && formData.esi.registrationDate
              ? formData.esi.registrationDate.format('YYYY-MM-DD')
              : null,
          includeEmployerInCtc: formData.esi.includeEmployerInCtc,
        },
        ptStates: ptStatesPayload,
      };

      const result = await guidedStatutoryService.applySetup(payload);
      setSubmitResult(result);
      setCompleted(true);
    } catch (err) {
      const fieldErrors = err.response?.data?.fieldErrors;
      if (fieldErrors && typeof fieldErrors === 'object') {
        const msg = Object.entries(fieldErrors)
          .map(([k, v]) => `${k}: ${v}`)
          .join(', ');
        setError(msg);
      } else {
        setError(err.response?.data?.message || err.message || 'Failed to save statutory setup');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '60px 0' }} data-testid="guided-setup-loading">
        <Spin size="large" tip="Loading statutory readiness..." />
      </div>
    );
  }

  if (error && !readiness) {
    return (
      <Card
        className="saas-card"
        style={{
          borderRadius: 14,
          background: token.colorBgContainer,
          border: `1px solid ${token.colorBorderSecondary}`,
          padding: '24px 0',
        }}
        data-testid="guided-setup-error"
      >
        <Result
          status="error"
          title="Failed to Load Statutory Settings"
          subTitle={error}
          extra={[
            <Button
              type="primary"
              key="retry"
              onClick={loadReadiness}
              data-testid="btn-retry-load"
            >
              Retry
            </Button>,
          ]}
        />
      </Card>
    );
  }

  if (completed) {
    return (
      <Card
        className="saas-card"
        style={{
          borderRadius: 14,
          background: token.colorBgContainer,
          border: `1px solid ${token.colorBorderSecondary}`,
          padding: '24px 0',
        }}
        data-testid="guided-setup-success"
      >
        <Result
          status="success"
          title="Statutory Setup Completed!"
          subTitle="Employees' Provident Fund, Employee State Insurance, and Professional Tax compliance have been configured atomically and synced to your payroll engine."
          extra={[
            <Button
              type="primary"
              key="checklist"
              onClick={() => navigate('/setup')}
              data-testid="btn-to-checklist"
            >
              Go to Setup Checklist
            </Button>,
            <Button
              key="components"
              onClick={() => navigate('/payroll/components')}
              data-testid="btn-to-components"
            >
              Configure Salary Components
            </Button>,
            <Button
              key="settings"
              onClick={() => navigate('/payroll/settings/epf')}
              data-testid="btn-to-epf-settings"
            >
              View Settings
            </Button>,
          ]}
        />
        {submitResult && (
          <div style={{ maxWidth: 640, margin: '0 auto', padding: '0 24px' }}>
            <Divider />
            <Text type="secondary" style={{ fontSize: 13 }}>
              Configured components:{' '}
              {submitResult.epf?.isEnabled ? 'EPF (Active)' : 'EPF (Not Registered)'},{' '}
              {submitResult.esi?.isEnabled ? 'ESI (Active)' : 'ESI (Exempt)'},{' '}
              {submitResult.ptStates?.length || 0} state PT registrations recorded.
            </Text>
          </div>
        )}
      </Card>
    );
  }

  const stepsItems = [
    { title: 'Overview', icon: <InfoCircleOutlined /> },
    { title: 'EPF', icon: <SafetyCertificateOutlined /> },
    { title: 'ESI', icon: <MedicineBoxOutlined /> },
    { title: 'Professional Tax', icon: <BankOutlined /> },
    { title: 'Review & Confirm', icon: <CheckCircleOutlined /> },
  ];

  const operatingStates = readiness?.operatingStates || [];

  return (
    <div data-testid="guided-statutory-setup" style={{ width: '100%', maxWidth: 960, margin: '0 auto' }}>
      <div style={{ marginBottom: 24 }}>
        <Title level={3} style={{ marginBottom: 4 }}>
          Guided Statutory Setup
        </Title>
        <Text type="secondary">
          Configure EPF, ESI, and Professional Tax compliance with verified legal rates and automatic state detection.
        </Text>
      </div>

      <Card
        className="saas-card"
        style={{
          borderRadius: 14,
          background: token.colorBgContainer,
          border: `1px solid ${token.colorBorderSecondary}`,
          boxShadow: '0 4px 20px -2px rgba(15, 23, 42, 0.05)',
          marginBottom: 24,
        }}
      >
        <Steps
          current={currentStep}
          items={stepsItems}
          style={{ marginBottom: 32 }}
          data-testid="guided-setup-steps"
        />

        {error && (
          <Alert
            message="Configuration Error"
            description={error}
            type="error"
            showIcon
            closable
            onClose={() => setError(null)}
            style={{ marginBottom: 24 }}
            data-testid="guided-setup-error"
          />
        )}

        <Form form={form} layout="vertical" preserve={true}>
          {/* STEP 0: OVERVIEW & LOCATIONS */}
          {currentStep === 0 && (
            <div data-testid="step-overview">
              <Title level={4}>Welcome to Statutory Compliance Setup</Title>
              <Paragraph type="secondary">
                This guided wizard establishes your company’s compliance under Indian statutory acts. In less than 2
                minutes, we’ll configure your Provident Fund (EPF), State Insurance (ESI), and state-specific
                Professional Tax (PT) rules with standard statutory rates.
              </Paragraph>

              <Divider style={{ margin: '16px 0 24px 0' }} />

              <Title level={5}>Detected Operating Locations</Title>
              <Paragraph type="secondary">
                Professional tax requirements are derived from the physical states of your active work locations:
              </Paragraph>

              {operatingStates.length === 0 ? (
                <Alert
                  type="info"
                  showIcon
                  message="No Active Work Locations Found"
                  description={
                    <span>
                      You have not configured work locations yet. You can still proceed with EPF and ESI setup now, and
                      add location-specific PT registration later in{' '}
                      <a onClick={() => navigate('/payroll/settings/professional-tax')}>Professional Tax Settings</a>.
                    </span>
                  }
                  style={{ marginBottom: 20 }}
                />
              ) : (
                <Table
                  dataSource={operatingStates}
                  rowKey="stateCode"
                  pagination={false}
                  size="small"
                  style={{ marginBottom: 24 }}
                  columns={[
                    {
                      title: 'State',
                      dataIndex: 'stateName',
                      key: 'stateName',
                      render: (name, rec) => (
                        <Space>
                          <Text strong>{name}</Text>
                          <Text type="secondary">({rec.stateCode})</Text>
                        </Space>
                      ),
                    },
                    {
                      title: 'Statutory PT Status',
                      dataIndex: 'leviesPt',
                      key: 'leviesPt',
                      render: (levies) =>
                        levies ? (
                          <Tag color="blue">PT Applicable</Tag>
                        ) : (
                          <Tag color="default">Exempt / No PT Levied</Tag>
                        ),
                    },
                    {
                      title: 'Current Status',
                      dataIndex: 'configured',
                      key: 'configured',
                      render: (conf, rec) =>
                        conf ? (
                          <Tag color="green">Configured {rec.registrationNumber ? `(${rec.registrationNumber})` : ''}</Tag>
                        ) : (
                          <Tag color="orange">Pending Setup</Tag>
                        ),
                    },
                  ]}
                />
              )}
            </div>
          )}

          {/* STEP 1: EPF */}
          {currentStep === 1 && (
            <div data-testid="step-epf">
              <Title level={4}>Employees’ Provident Fund (EPF)</Title>
              <Paragraph type="secondary">
                Governed under the <em>Employees’ Provident Funds and Miscellaneous Provisions Act, 1952</em>.
                Mandatory for organizations with 20 or more employees.
              </Paragraph>

              <Form.Item label="Is your organization registered with the EPFO?" style={{ marginBottom: 16 }}>
                <Radio.Group
                  value={formData.epf.registered}
                  onChange={(e) => {
                    const reg = e.target.value;
                    setFormData((prev) => ({
                      ...prev,
                      epf: { ...prev.epf, registered: reg },
                    }));
                    if (reg) {
                      form.setFieldsValue({ epfRegistrationNumber: formData.epf.registrationNumber || '' });
                    }
                  }}
                  data-testid="radio-epf-registered"
                >
                  <Space orientation="vertical">
                    <Radio value={true}>Yes, we are registered with EPFO</Radio>
                    <Radio value={false}>No, not registered / exempt</Radio>
                  </Space>
                </Radio.Group>
              </Form.Item>

              {formData.epf.registered ? (
                <div
                  style={{
                    padding: 16,
                    background: token.colorFillAlter,
                    borderRadius: 8,
                    marginBottom: 20,
                  }}
                >
                  <Row gutter={16}>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="epfRegistrationNumber"
                        label="EPF Establishment Code / Registration Number"
                        required
                        rules={[{ required: true, message: 'Registration number is required' }]}
                      >
                        <Input
                          placeholder="e.g. DLCPM1234567000"
                          maxLength={32}
                          value={formData.epf.registrationNumber}
                          onChange={(e) => {
                            const val = e.target.value;
                            setFormData((prev) => ({
                              ...prev,
                              epf: { ...prev.epf, registrationNumber: val },
                            }));
                            form.setFieldsValue({ epfRegistrationNumber: val });
                          }}
                          data-testid="input-epf-registration-number"
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="Registration Date (Optional)">
                        <DatePicker
                          style={{ width: '100%' }}
                          value={formData.epf.registrationDate}
                          onChange={(date) =>
                            setFormData((prev) => ({
                              ...prev,
                              epf: { ...prev.epf, registrationDate: date },
                            }))
                          }
                          data-testid="picker-epf-registration-date"
                        />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Form.Item label="Statutory Wage Ceiling (₹15,000/month)">
                    <Radio.Group
                      value={formData.epf.restrictToWageCeiling}
                      onChange={(e) =>
                        setFormData((prev) => ({
                          ...prev,
                          epf: { ...prev.epf, restrictToWageCeiling: e.target.value },
                        }))
                      }
                      data-testid="radio-epf-ceiling"
                    >
                      <Space orientation="vertical">
                        <Radio value={true}>
                          <Text strong>Restrict contribution to wage ceiling of ₹15,000</Text>
                          <br />
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            Standard compliance: EPF is calculated on capped wage if Basic &gt; ₹15,000.
                          </Text>
                        </Radio>
                        <Radio value={false}>
                          <Text strong>Calculate 12% on actual Basic wages</Text>
                          <br />
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            No ceiling restriction applied.
                          </Text>
                        </Radio>
                      </Space>
                    </Radio.Group>
                  </Form.Item>

                  <Form.Item label="Include Employer PF (12%) in Employee CTC?">
                    <Radio.Group
                      value={formData.epf.includeEmployerInCtc}
                      onChange={(e) =>
                        setFormData((prev) => ({
                          ...prev,
                          epf: { ...prev.epf, includeEmployerInCtc: e.target.value },
                        }))
                      }
                      data-testid="radio-epf-ctc"
                    >
                      <Radio value={true}>Yes, include in CTC</Radio>
                      <Radio value={false}>No, treat as company overhead</Radio>
                    </Radio.Group>
                  </Form.Item>

                  <Alert
                    type="info"
                    showIcon
                    message="Standard legal split for a new setup"
                    description="Employee deduction: 12.00% · Employer share: 12.00% (3.67% EPF + 8.33% EPS pension) · EDLI: 0.50% · Admin charges: 0.50%. If your company already has its own rates or wage ceiling in EPF settings, they are kept."
                  />
                </div>
              ) : (
                <Alert
                  type="warning"
                  showIcon
                  message="EPF Disabled"
                  description="EPF deductions will not be computed for your employees. You can enable this later if your headcount reaches 20."
                  style={{ marginBottom: 20 }}
                />
              )}
            </div>
          )}

          {/* STEP 2: ESI */}
          {currentStep === 2 && (
            <div data-testid="step-esi">
              <Title level={4}>Employee State Insurance (ESI)</Title>
              <Paragraph type="secondary">
                Governed under the <em>Employees’ State Insurance Act, 1948</em>. Applicable to employees earning gross
                monthly wages of ₹21,000 or less in notified areas.
              </Paragraph>

              <Form.Item label="Is your organization covered under ESI?" style={{ marginBottom: 16 }}>
                <Radio.Group
                  value={formData.esi.registered}
                  onChange={(e) => {
                    const reg = e.target.value;
                    setFormData((prev) => ({
                      ...prev,
                      esi: { ...prev.esi, registered: reg },
                    }));
                    if (reg) {
                      form.setFieldsValue({ esiRegistrationNumber: formData.esi.registrationNumber || '' });
                    }
                  }}
                  data-testid="radio-esi-registered"
                >
                  <Space orientation="vertical">
                    <Radio value={true}>Yes, we are registered under ESI</Radio>
                    <Radio value={false}>No, not covered / exempt</Radio>
                  </Space>
                </Radio.Group>
              </Form.Item>

              {formData.esi.registered ? (
                <div
                  style={{
                    padding: 16,
                    background: token.colorFillAlter,
                    borderRadius: 8,
                    marginBottom: 20,
                  }}
                >
                  <Row gutter={16}>
                    <Col xs={24} md={12}>
                      <Form.Item
                        name="esiRegistrationNumber"
                        label="17-Digit ESI Employer Registration Code"
                        required
                        rules={[{ required: true, message: 'ESI Registration Code is required' }]}
                      >
                        <Input
                          placeholder="e.g. 31000123450000101"
                          maxLength={32}
                          value={formData.esi.registrationNumber}
                          onChange={(e) => {
                            const val = e.target.value;
                            setFormData((prev) => ({
                              ...prev,
                              esi: { ...prev.esi, registrationNumber: val },
                            }));
                            form.setFieldsValue({ esiRegistrationNumber: val });
                          }}
                          data-testid="input-esi-registration-number"
                        />
                      </Form.Item>
                    </Col>
                    <Col xs={24} md={12}>
                      <Form.Item label="Registration Date (Optional)">
                        <DatePicker
                          style={{ width: '100%' }}
                          value={formData.esi.registrationDate}
                          onChange={(date) =>
                            setFormData((prev) => ({
                              ...prev,
                              esi: { ...prev.esi, registrationDate: date },
                            }))
                          }
                          data-testid="picker-esi-registration-date"
                        />
                      </Form.Item>
                    </Col>
                  </Row>

                  <Form.Item label="Include Employer ESI (3.25%) in Employee CTC?">
                    <Radio.Group
                      value={formData.esi.includeEmployerInCtc}
                      onChange={(e) =>
                        setFormData((prev) => ({
                          ...prev,
                          esi: { ...prev.esi, includeEmployerInCtc: e.target.value },
                        }))
                      }
                      data-testid="radio-esi-ctc"
                    >
                      <Radio value={true}>Yes, include in CTC</Radio>
                      <Radio value={false}>No, treat as company overhead</Radio>
                    </Radio.Group>
                  </Form.Item>

                  <Alert
                    type="info"
                    showIcon
                    message="Standard legal ESI rates for a new setup"
                    description="Employee deduction: 0.75% of gross wages · Employer contribution: 3.25% of gross wages · Wage ceiling: ₹21,000/month. If your company already has its own rates or wage ceiling in ESI settings, they are kept."
                  />
                </div>
              ) : (
                <Alert
                  type="warning"
                  showIcon
                  message="ESI Disabled"
                  description="ESI contributions will not be deducted. You can enable this later if needed."
                  style={{ marginBottom: 20 }}
                />
              )}
            </div>
          )}

          {/* STEP 3: PROFESSIONAL TAX */}
          {currentStep === 3 && (
            <div data-testid="step-pt">
              <Title level={4}>Professional Tax (PT)</Title>
              <Paragraph type="secondary">
                Professional Tax is a state-level statutory deduction. States levying PT require a valid Professional
                Tax Registration Certificate (PTRC) number.
              </Paragraph>

              {operatingStates.length === 0 ? (
                <Alert
                  type="info"
                  showIcon
                  message="No Operating States Found"
                  description="No active work locations are registered. You can configure work locations first, or proceed without PT entries."
                  style={{ marginBottom: 20 }}
                />
              ) : (
                <div>
                  {operatingStates.map((st) => {
                    const isApplicable = st.leviesPt;
                    const stData = formData.ptStates[st.stateCode] || {};

                    return (
                      <Card
                        key={st.stateCode}
                        size="small"
                        style={{
                          marginBottom: 16,
                          borderRadius: 8,
                          border: `1px solid ${token.colorBorderSecondary}`,
                        }}
                      >
                        <Row align="middle" justify="space-between" gutter={16}>
                          <Col xs={24} md={6}>
                            <Space direction="vertical" size={2}>
                              <Text strong style={{ fontSize: 14 }}>
                                {st.stateName} ({st.stateCode})
                              </Text>
                              {isApplicable ? (
                                <Tag color="blue">PT Applicable</Tag>
                              ) : (
                                <Tag color="default">Exempt / No PT</Tag>
                              )}
                            </Space>
                          </Col>

                          {isApplicable ? (
                            <Col xs={24} md={18}>
                              <Row gutter={12}>
                                <Col xs={24} sm={14}>
                                  <Form.Item label="PTRC Number" style={{ marginBottom: 0 }}>
                                    <Input
                                      placeholder="e.g. 27123456789P"
                                      maxLength={32}
                                      value={stData.registrationNumber || ''}
                                      onChange={(e) => {
                                        const val = e.target.value;
                                        setFormData((prev) => ({
                                          ...prev,
                                          ptStates: {
                                            ...prev.ptStates,
                                            [st.stateCode]: {
                                              ...prev.ptStates[st.stateCode],
                                              registrationNumber: val,
                                            },
                                          },
                                        }));
                                      }}
                                      data-testid={`input-pt-${st.stateCode}`}
                                    />
                                  </Form.Item>
                                </Col>
                                <Col xs={24} sm={10}>
                                  <Form.Item label="Effective From" style={{ marginBottom: 0 }}>
                                    <DatePicker
                                      style={{ width: '100%' }}
                                      value={stData.effectiveFrom || dayjs().startOf('year')}
                                      onChange={(date) => {
                                        setFormData((prev) => ({
                                          ...prev,
                                          ptStates: {
                                            ...prev.ptStates,
                                            [st.stateCode]: {
                                              ...prev.ptStates[st.stateCode],
                                              effectiveFrom: date,
                                            },
                                          },
                                        }));
                                      }}
                                    />
                                  </Form.Item>
                                </Col>
                              </Row>
                            </Col>
                          ) : (
                            <Col xs={24} md={18}>
                              <Text type="secondary" style={{ fontSize: 13 }}>
                                This state does not levy Professional Tax. No registration certificate or salary deduction is required.
                              </Text>
                            </Col>
                          )}
                        </Row>
                      </Card>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* STEP 4: REVIEW & CONFIRM */}
          {currentStep === 4 && (
            <div data-testid="step-review">
              <Title level={4}>Review & Apply Compliance Rules</Title>
              <Paragraph type="secondary">
                Review your statutory configuration choices below. Clicking &ldquo;Complete Statutory Setup&rdquo; will
                save all rules atomically and update your tenant setup checklist.
              </Paragraph>

              <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col xs={24} md={8}>
                  <Card size="small" title="1. EPF Setup" style={{ height: '100%', borderRadius: 8 }}>
                    <p>
                      <Text type="secondary">Status:</Text>{' '}
                      {formData.epf.registered ? (
                        <Tag color="green">Registered</Tag>
                      ) : (
                        <Tag color="default">Not Registered</Tag>
                      )}
                    </p>
                    {formData.epf.registered && (
                      <>
                        <p>
                          <Text type="secondary">Code:</Text>{' '}
                          <Text strong>{formData.epf.registrationNumber || '—'}</Text>
                        </p>
                        <p>
                          <Text type="secondary">Wage Ceiling:</Text>{' '}
                          {formData.epf.restrictToWageCeiling ? 'Capped at ₹15,000' : 'Actual Basic'}
                        </p>
                        <p>
                          <Text type="secondary">In CTC:</Text>{' '}
                          {formData.epf.includeEmployerInCtc ? 'Yes (12%)' : 'No'}
                        </p>
                      </>
                    )}
                  </Card>
                </Col>

                <Col xs={24} md={8}>
                  <Card size="small" title="2. ESI Setup" style={{ height: '100%', borderRadius: 8 }}>
                    <p>
                      <Text type="secondary">Status:</Text>{' '}
                      {formData.esi.registered ? (
                        <Tag color="green">Covered</Tag>
                      ) : (
                        <Tag color="default">Exempt</Tag>
                      )}
                    </p>
                    {formData.esi.registered && (
                      <>
                        <p>
                          <Text type="secondary">Code:</Text>{' '}
                          <Text strong>{formData.esi.registrationNumber || '—'}</Text>
                        </p>
                        <p>
                          <Text type="secondary">Wage Ceiling:</Text> ₹21,000 / month
                        </p>
                        <p>
                          <Text type="secondary">In CTC:</Text>{' '}
                          {formData.esi.includeEmployerInCtc ? 'Yes (3.25%)' : 'No'}
                        </p>
                      </>
                    )}
                  </Card>
                </Col>

                <Col xs={24} md={8}>
                  <Card size="small" title="3. Professional Tax" style={{ height: '100%', borderRadius: 8 }}>
                    <p>
                      <Text type="secondary">Operating States:</Text> {operatingStates.length}
                    </p>
                    {operatingStates.map((st) => (
                      <p key={st.stateCode} style={{ marginBottom: 4 }}>
                        <Text strong>{st.stateCode}:</Text>{' '}
                        {st.leviesPt ? (
                          formData.ptStates[st.stateCode]?.registrationNumber ? (
                            <Tag color="blue">{formData.ptStates[st.stateCode].registrationNumber}</Tag>
                          ) : (
                            <Tag color="orange">Pending Reg</Tag>
                          )
                        ) : (
                          <Tag color="default">Exempt</Tag>
                        )}
                      </p>
                    ))}
                  </Card>
                </Col>
              </Row>
            </div>
          )}

          {/* NAVIGATION BUTTONS */}
          <Divider style={{ margin: '24px 0 16px 0' }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            {currentStep > 0 ? (
              <Button onClick={handlePrev} icon={<ArrowLeftOutlined />} data-testid="btn-wizard-prev">
                Previous
              </Button>
            ) : (
              <div />
            )}

            {currentStep < stepsItems.length - 1 ? (
              <Button type="primary" onClick={handleNext} icon={<ArrowRightOutlined />} data-testid="btn-wizard-next">
                Next
              </Button>
            ) : (
              <Button
                type="primary"
                onClick={handleSubmit}
                loading={submitting}
                icon={<CheckCircleOutlined />}
                data-testid="submit-guided-setup"
              >
                Complete Statutory Setup
              </Button>
            )}
          </div>
        </Form>
      </Card>
    </div>
  );
}

export default GuidedStatutorySetup;

