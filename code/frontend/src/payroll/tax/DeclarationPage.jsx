import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import {
  Card,
  Row,
  Col,
  Space,
  Typography,
  Select,
  Tag,
  Button,
  Alert,
  Spin,
  Tabs,
  Popconfirm,
  Modal,
  Radio,
  Switch,
  message,
  Divider,
} from 'antd';
import {
  FileTextOutlined,
  CheckCircleOutlined,
  SendOutlined,
  UnlockOutlined,
  SwapOutlined,
  HomeOutlined,
  AuditOutlined,
  DollarCircleOutlined,
  CalculatorOutlined,
} from '@ant-design/icons';
import { store } from '@shell/store';
import { currentFy, fyOptions, formatFyDisplay } from './financialYear';
import { declarationService } from './declarationService';
import {
  setFy as setSliceFy,
  setHeader as setSliceHeader,
  setLoading as setSliceLoading,
  setError as setSliceError,
} from './taxSlice';
import { HousingSection } from './HousingSection';
import { DeductionsSection } from './DeductionsSection';
import { OtherIncomeSection } from './OtherIncomeSection';
import { SummarySection } from './SummarySection';

const { Title, Text, Paragraph } = Typography;

export function DeclarationPage({ initialFy, renderSection }) {
  const [selectedFy, setSelectedFy] = useState(initialFy || currentFy());
  const [header, setHeader] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [reopening, setReopening] = useState(false);
  const [error, setError] = useState(null);
  const [activeTab, setActiveTab] = useState('housing');

  // Regime change modal
  const [regimeModalVisible, setRegimeModalVisible] = useState(false);
  const [targetRegime, setTargetRegime] = useState('NEW');
  const [savingRegime, setSavingRegime] = useState(false);

  const fetchHeader = useCallback(async (fy) => {
    setLoading(true);
    setError(null);
    store.dispatch(setSliceFy(fy));
    store.dispatch(setSliceLoading(true));
    store.dispatch(setSliceError(null));
    try {
      const data = await declarationService.header(fy);
      setHeader(data);
      store.dispatch(setSliceHeader(data));
      setTargetRegime(data?.tax_regime || 'NEW');
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to load tax declaration';
      setError(msg);
      store.dispatch(setSliceError(msg));
      setHeader(null);
      store.dispatch(setSliceHeader(null));
    } finally {
      setLoading(false);
      store.dispatch(setSliceLoading(false));
    }
  }, []);

  useEffect(() => {
    fetchHeader(selectedFy);
  }, [selectedFy, fetchHeader]);

  const handleFyChange = (val) => {
    setSelectedFy(val);
  };

  const handleApiError = useCallback(
    async (err, fallbackMsg) => {
      const status = err?.status || err?.response?.status;
      const code = err?.code || err?.response?.data?.code;
      const msg = err?.response?.data?.message || err?.message || fallbackMsg;

      if (status === 409 || code === 'ALREADY_SUBMITTED' || code === 'NOT_EDITABLE' || code === 'OFFICER_ENTERED') {
        await fetchHeader(selectedFy);
      }
      setError(msg);
      store.dispatch(setSliceError(msg));
      message.error(msg);
    },
    [fetchHeader, selectedFy],
  );

  const handleToggleHousingFlag = async (field, value) => {
    if (!header || !header.editable || !header.window_open) return;
    try {
      const updatedBody = {
        tax_regime: header.tax_regime || 'NEW',
        is_staying_in_rented_house:
          field === 'is_staying_in_rented_house' ? value : Boolean(header.is_staying_in_rented_house),
        is_repaying_self_occupied_loan:
          field === 'is_repaying_self_occupied_loan' ? value : Boolean(header.is_repaying_self_occupied_loan),
        has_let_out_property:
          field === 'has_let_out_property' ? value : Boolean(header.has_let_out_property),
      };
      const res = await declarationService.saveHeader(selectedFy, updatedBody);
      const nextHeader = { ...header, ...res, ...updatedBody };
      setHeader(nextHeader);
      store.dispatch(setSliceHeader(nextHeader));
      message.success('Housing preferences updated');
    } catch (err) {
      await handleApiError(err, 'Failed to update preferences');
    }
  };

  const handleOpenRegimeModal = () => {
    setTargetRegime(header?.tax_regime || 'NEW');
    setRegimeModalVisible(true);
  };

  const handleConfirmRegimeChange = async () => {
    if (!header || targetRegime === header.tax_regime) {
      setRegimeModalVisible(false);
      return;
    }
    setSavingRegime(true);
    try {
      const updatedBody = {
        tax_regime: targetRegime,
        is_staying_in_rented_house: Boolean(header.is_staying_in_rented_house),
        is_repaying_self_occupied_loan: Boolean(header.is_repaying_self_occupied_loan),
        has_let_out_property: Boolean(header.has_let_out_property),
      };
      const res = await declarationService.saveHeader(selectedFy, updatedBody);
      const nextHeader = { ...header, ...res, tax_regime: targetRegime };
      setHeader(nextHeader);
      store.dispatch(setSliceHeader(nextHeader));
      message.success(`Tax regime switched to ${targetRegime === 'NEW' ? 'New Regime (115BAC)' : 'Old Regime'}`);
      setRegimeModalVisible(false);
    } catch (err) {
      await handleApiError(err, 'Failed to change tax regime');
    } finally {
      setSavingRegime(false);
    }
  };

  const handleSubmitDeclaration = async () => {
    setSubmitting(true);
    try {
      await declarationService.submit(selectedFy);
      await fetchHeader(selectedFy);
      message.success('Tax declaration submitted successfully');
    } catch (err) {
      await handleApiError(err, 'Failed to submit tax declaration');
    } finally {
      setSubmitting(false);
    }
  };

  const handleReopenDeclaration = async () => {
    setReopening(true);
    try {
      await declarationService.reopen(selectedFy);
      await fetchHeader(selectedFy);
      message.success('Tax declaration reopened for editing');
    } catch (err) {
      await handleApiError(err, 'Failed to reopen tax declaration');
    } finally {
      setReopening(false);
    }
  };

  const isEditable = Boolean(header?.editable && header?.window_open);
  const isSubmitted = header?.status?.toUpperCase() === 'SUBMITTED';
  const canReopen = Boolean(header && isSubmitted && header?.window_open && header?.reopenable !== false);
  const canChangeRegime = Boolean(header && isEditable && header?.can_change_tax_regime !== false);

  const tabItems = [
    {
      key: 'housing',
      label: (
        <span>
          <HomeOutlined /> Housing (HRA / Loans)
        </span>
      ),
      children: renderSection ? (
        renderSection('housing', { fy: selectedFy, header, editable: isEditable, refresh: () => fetchHeader(selectedFy) })
      ) : (
        <div data-testid="housing-tab-content">
          <HousingSection fy={selectedFy} header={header} editable={isEditable} onRefresh={() => fetchHeader(selectedFy)} />
        </div>
      ),
    },
    {
      key: 'deductions',
      label: (
        <span>
          <AuditOutlined /> Deductions & 80C
        </span>
      ),
      children: renderSection ? (
        renderSection('deductions', { fy: selectedFy, header, editable: isEditable, refresh: () => fetchHeader(selectedFy) })
      ) : (
        <div data-testid="deductions-tab-content">
          <DeductionsSection fy={selectedFy} header={header} editable={isEditable} onRefresh={() => fetchHeader(selectedFy)} />
        </div>
      ),
    },
    {
      key: 'otherIncome',
      label: (
        <span>
          <DollarCircleOutlined /> Other Income
        </span>
      ),
      children: renderSection ? (
        renderSection('otherIncome', { fy: selectedFy, header, editable: isEditable, refresh: () => fetchHeader(selectedFy) })
      ) : (
        <div data-testid="other-income-tab-content">
          <OtherIncomeSection fy={selectedFy} header={header} editable={isEditable} onRefresh={() => fetchHeader(selectedFy)} />
        </div>
      ),
    },
    {
      key: 'summary',
      label: (
        <span>
          <CalculatorOutlined /> Tax Summary
        </span>
      ),
      children: renderSection ? (
        renderSection('summary', { fy: selectedFy, header, editable: isEditable, refresh: () => fetchHeader(selectedFy) })
      ) : (
        <div data-testid="summary-tab-content">
          <SummarySection fy={selectedFy} header={header} />
        </div>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto', padding: '24px 16px' }}>
      <Card
        title={
          <Row justify="space-between" align="middle" wrap gutter={[12, 12]}>
            <Col>
              <Space align="center">
                <FileTextOutlined style={{ fontSize: 22, color: '#1677ff' }} />
                <div>
                  <Title level={4} style={{ margin: 0 }}>
                    Tax Declaration (Investment Declarations)
                  </Title>
                  <Text type="secondary" style={{ fontSize: 13 }}>
                    Financial Year {formatFyDisplay(selectedFy)}
                  </Text>
                </div>
              </Space>
            </Col>
            <Col>
              <Space align="center" wrap>
                <Text strong>FY:</Text>
                <Select
                  value={selectedFy}
                  onChange={handleFyChange}
                  options={fyOptions().map((opt) => ({
                    value: opt.value,
                    label: opt.label,
                  }))}
                  style={{ width: 130 }}
                  data-testid="declaration-fy-select"
                />
              </Space>
            </Col>
          </Row>
        }
        extra={
          header && (
            <Space align="center" wrap>
              <Tag color={header.tax_regime === 'NEW' ? 'cyan' : 'magenta'}>
                {header.tax_regime === 'NEW' ? 'New Regime (115BAC)' : 'Old Regime'}
              </Tag>
              <Tag color={header.status === 'SUBMITTED' ? 'blue' : 'orange'}>
                {header.status || 'DRAFT'}
              </Tag>
              <Tag color={header.window_open ? 'green' : 'default'}>
                {header.window_open ? 'Window Open' : 'Window Closed'}
              </Tag>
              <Tag color={header.editable ? 'blue' : 'default'}>
                {header.editable ? 'Editable' : 'Locked'}
              </Tag>
            </Space>
          )
        }
      >
        {error && (
          <Alert
            message="Error"
            description={error}
            type="error"
            showIcon
            closable
            onClose={() => setError(null)}
            style={{ marginBottom: 20 }}
          />
        )}

        <Spin spinning={loading}>
          {header && (
            <>
              {(!header.window_open || !header.editable) && (
                <Alert
                  type="warning"
                  showIcon
                  message={
                    !header.window_open
                      ? 'Declaration Window is Currently Closed'
                      : 'Declaration is Locked'
                  }
                  description={
                    !header.window_open
                      ? 'The payroll admin has closed or not yet opened the declaration window for this financial year. Values are read-only.'
                      : 'This declaration has been submitted or locked by the payroll officer and cannot be edited.'
                  }
                  style={{ marginBottom: 20 }}
                />
              )}

              {/* Action Bar & Regime Controls */}
              <div
                style={{
                  background: '#fafafa',
                  padding: '16px 20px',
                  borderRadius: 8,
                  marginBottom: 20,
                  border: '1px solid #f0f0f0',
                }}
              >
                <Row justify="space-between" align="middle" gutter={[16, 16]}>
                  <Col xs={24} md={14}>
                    <Space direction="vertical" size={4}>
                      <Space align="center" wrap>
                        <Text strong>Selected Regime:</Text>
                        <Tag color={header.tax_regime === 'NEW' ? 'cyan' : 'magenta'}>
                          {header.tax_regime === 'NEW' ? 'New Tax Regime' : 'Old Tax Regime'}
                        </Tag>
                        {canChangeRegime && (
                          <Button
                            size="small"
                            icon={<SwapOutlined />}
                            onClick={handleOpenRegimeModal}
                            data-testid="change-regime-btn"
                          >
                            Change Regime
                          </Button>
                        )}
                      </Space>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {header.tax_regime === 'NEW'
                          ? 'New Tax Regime offers concessional slab rates with limited deductions under Section 115BAC.'
                          : 'Old Tax Regime allows claiming exemptions (HRA, 80C, 80D, home loan interest, etc.).'}
                      </Text>
                    </Space>
                  </Col>

                  <Col xs={24} md={10} style={{ textAlign: 'right' }}>
                    <Space wrap>
                      {canReopen && (
                        <Popconfirm
                          title="Reopen Tax Declaration?"
                          description="This will return your declaration to Draft status so you can update entries."
                          onConfirm={handleReopenDeclaration}
                          okText="Yes, Reopen"
                          cancelText="Cancel"
                        >
                          <Button
                            icon={<UnlockOutlined />}
                            loading={reopening}
                            data-testid="reopen-declaration-btn"
                          >
                            Reopen Declaration
                          </Button>
                        </Popconfirm>
                      )}

                      {isEditable && !isSubmitted && (
                        <Popconfirm
                          title="Submit Tax Declaration?"
                          description="Once submitted, your declaration will be finalized for payroll computation."
                          onConfirm={handleSubmitDeclaration}
                          okText="Yes, Submit"
                          cancelText="Cancel"
                        >
                          <Button
                            type="primary"
                            icon={<SendOutlined />}
                            loading={submitting}
                            data-testid="submit-declaration-btn"
                          >
                            Submit Declaration
                          </Button>
                        </Popconfirm>
                      )}

                      {isSubmitted && (
                        <Tag icon={<CheckCircleOutlined />} color="success" style={{ padding: '6px 12px' }}>
                          Submitted
                        </Tag>
                      )}
                    </Space>
                  </Col>
                </Row>

                <Divider style={{ margin: '14px 0' }} />

                {/* Housing Declarations Preferences */}
                <Row gutter={[24, 12]} align="middle">
                  <Col xs={24} sm={8}>
                    <Space align="center">
                      <Switch
                        size="small"
                        checked={Boolean(header.is_staying_in_rented_house)}
                        disabled={!isEditable}
                        onChange={(chk) => handleToggleHousingFlag('is_staying_in_rented_house', chk)}
                        data-testid="switch-rented-house"
                      />
                      <Text>Staying in Rented House</Text>
                    </Space>
                  </Col>
                  <Col xs={24} sm={8}>
                    <Space align="center">
                      <Switch
                        size="small"
                        checked={Boolean(header.is_repaying_self_occupied_loan)}
                        disabled={!isEditable}
                        onChange={(chk) => handleToggleHousingFlag('is_repaying_self_occupied_loan', chk)}
                        data-testid="switch-home-loan"
                      />
                      <Text>Home Loan Repayment</Text>
                    </Space>
                  </Col>
                  <Col xs={24} sm={8}>
                    <Space align="center">
                      <Switch
                        size="small"
                        checked={Boolean(header.has_let_out_property)}
                        disabled={!isEditable}
                        onChange={(chk) => handleToggleHousingFlag('has_let_out_property', chk)}
                        data-testid="switch-let-out"
                      />
                      <Text>Has Let-Out Property</Text>
                    </Space>
                  </Col>
                </Row>
              </div>

              {/* Tabs for Declaration Sub-Sections */}
              <Tabs
                activeKey={activeTab}
                onChange={setActiveTab}
                items={tabItems}
                type="card"
              />
            </>
          )}
        </Spin>
      </Card>

      {/* Regime Change Modal */}
      <Modal
        title="Change Tax Regime"
        open={regimeModalVisible}
        onOk={handleConfirmRegimeChange}
        onCancel={() => setRegimeModalVisible(false)}
        confirmLoading={savingRegime}
        okText="Apply Regime Change"
      >
        <Paragraph>
          Select the tax regime for Financial Year <strong>{formatFyDisplay(selectedFy)}</strong>:
        </Paragraph>
        <Radio.Group
          value={targetRegime}
          onChange={(e) => setTargetRegime(e.target.value)}
          style={{ width: '100%' }}
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <Radio value="NEW" style={{ padding: '8px 0' }}>
              <div>
                <Text strong>New Tax Regime (Section 115BAC)</Text>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Default regime with lower tax rates across slabs. Exemptions and deductions under Chapter VI-A are not allowed.
                  </Text>
                </div>
              </div>
            </Radio>
            <Radio value="OLD" style={{ padding: '8px 0' }}>
              <div>
                <Text strong>Old Tax Regime</Text>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    Permits deductions under Section 80C, 80D, HRA exemption, and home loan interest.
                  </Text>
                </div>
              </div>
            </Radio>
          </Space>
        </Radio.Group>
      </Modal>
    </div>
  );
}

DeclarationPage.propTypes = {
  initialFy: PropTypes.string,
  renderSection: PropTypes.func,
};

export default DeclarationPage;
