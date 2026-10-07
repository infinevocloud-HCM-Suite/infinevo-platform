import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { useDispatch, useSelector } from 'react-redux';
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
  Collapse,
  Popconfirm,
  Modal,
  Radio,
  Switch,
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
  UploadOutlined,
} from '@ant-design/icons';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { currentFy, fyOptions, formatFyDisplay } from './financialYear';
import { declarationService } from './declarationService';
import {
  setFy as setSliceFy,
  setHeader as setSliceHeader,
  setLoading as setSliceLoading,
  setError as setSliceError,
  selectTaxFy,
  selectTaxHeader,
} from './taxSlice';
import { readError } from './apiError';
import { HousingSection } from './HousingSection';
import { DeductionsSection } from './DeductionsSection';
import { OtherIncomeSection } from './OtherIncomeSection';
import { SummarySection } from './SummarySection';
import { ProofUploadSection } from './ProofUploadSection';

const { Title, Text, Paragraph } = Typography;

const RELOAD_HEADER_CODES = ['ALREADY_SUBMITTED', 'NOT_EDITABLE', 'WINDOW_CLOSED', 'LOCKED', 'OFFICER_ENTERED'];

export function DeclarationPage({ initialFy, renderSection }) {
  const dispatch = useDispatch();
  const sliceFy = useSelector(selectTaxFy);
  const header = useSelector(selectTaxHeader);
  const selectedFy = sliceFy || initialFy || currentFy();

  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [reopening, setReopening] = useState(false);
  const [error, setError] = useState(null);
  const [openPanels, setOpenPanels] = useState(['housing']);

  // Regime change modal
  const [regimeModalVisible, setRegimeModalVisible] = useState(false);
  const [targetRegime, setTargetRegime] = useState('NEW');
  const [savingRegime, setSavingRegime] = useState(false);

  // The FY lives in the slice; changing it drops the header and every section (taxSlice.setFy).
  useEffect(() => {
    dispatch(setSliceFy(initialFy || sliceFy || currentFy()));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dispatch, initialFy]);

  const fetchHeader = useCallback(
    async (fy) => {
      setLoading(true);
      setError(null);
      dispatch(setSliceLoading(true));
      dispatch(setSliceError(null));
      try {
        const data = await declarationService.header(fy);
        dispatch(setSliceHeader(data));
        setTargetRegime(data?.tax_regime || 'NEW');
      } catch (err) {
        const msg = readError(err, 'Failed to load tax declaration').message;
        setError(msg);
        dispatch(setSliceError(msg));
      } finally {
        setLoading(false);
        dispatch(setSliceLoading(false));
      }
    },
    [dispatch],
  );

  useEffect(() => {
    if (sliceFy) fetchHeader(sliceFy);
  }, [sliceFy, fetchHeader]);

  const handleFyChange = (val) => {
    dispatch(setSliceFy(val));
  };

  const handleApiError = useCallback(
    async (err, fallbackMsg) => {
      const info = readError(err, fallbackMsg);
      if (info.status === 409 || RELOAD_HEADER_CODES.includes(info.code)) {
        await fetchHeader(selectedFy);
      }
      setError(info.message);
      dispatch(setSliceError(info.message));
      errorMsg(info);
    },
    [dispatch, fetchHeader, selectedFy],
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
      dispatch(setSliceHeader({ ...header, ...res, ...updatedBody }));
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
      // The Deductions section sees the new regime and re-reads the 6A catalogue.
      dispatch(setSliceHeader({ ...header, ...res, tax_regime: targetRegime }));
      setRegimeModalVisible(false);
      successMsg(
        'Regime Changed',
        `Tax regime switched to ${targetRegime === 'NEW' ? 'the New Regime (115BAC)' : 'the Old Regime'}.`,
      );
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
      successMsg('Declaration Submitted', 'Your tax declaration has been submitted.');
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
      successMsg('Declaration Reopened', 'Your tax declaration is open for editing again.');
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
  const refresh = () => fetchHeader(selectedFy);
  const sectionProps = { fy: selectedFy, header, editable: isEditable, refresh };

  // Four Collapse panels (§5). antd renders a panel's body on its first expand, so each
  // section is read from the server the first time it is opened, then kept in the slice.
  const panels = [
    {
      key: 'housing',
      label: (
        <span data-testid="panel-housing">
          <HomeOutlined /> Housing (HRA / Loans)
        </span>
      ),
      children: renderSection ? (
        renderSection('housing', sectionProps)
      ) : (
        <div data-testid="housing-tab-content">
          <HousingSection fy={selectedFy} header={header} editable={isEditable} onRefresh={refresh} />
        </div>
      ),
    },
    {
      key: 'deductions',
      label: (
        <span data-testid="panel-deductions">
          <AuditOutlined /> Deductions & 80C
        </span>
      ),
      children: renderSection ? (
        renderSection('deductions', sectionProps)
      ) : (
        <div data-testid="deductions-tab-content">
          <DeductionsSection
            fy={selectedFy}
            regime={header?.tax_regime}
            editable={isEditable}
            onRefresh={refresh}
          />
        </div>
      ),
    },
    {
      key: 'otherIncome',
      label: (
        <span data-testid="panel-otherIncome">
          <DollarCircleOutlined /> Other Income
        </span>
      ),
      children: renderSection ? (
        renderSection('otherIncome', sectionProps)
      ) : (
        <div data-testid="other-income-tab-content">
          <OtherIncomeSection fy={selectedFy} editable={isEditable} onRefresh={refresh} />
        </div>
      ),
    },
    {
      key: 'summary',
      label: (
        <span data-testid="panel-summary">
          <CalculatorOutlined /> Tax Summary
        </span>
      ),
      children: renderSection ? (
        renderSection('summary', sectionProps)
      ) : (
        <div data-testid="summary-tab-content">
          <SummarySection fy={selectedFy} />
        </div>
      ),
    },
    {
      key: 'proof',
      label: (
        <span data-testid="panel-proof">
          <UploadOutlined /> Proof of Investment (Receipts)
        </span>
      ),
      children: renderSection ? (
        renderSection('proof', sectionProps)
      ) : (
        <div data-testid="proof-tab-content">
          <ProofUploadSection fy={selectedFy} onRefresh={refresh} />
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
                      {isSubmitted && (
                        <Popconfirm
                          title="Reopen Tax Declaration?"
                          description="This will return your declaration to Draft status so you can update entries."
                          onConfirm={handleReopenDeclaration}
                          okText="Yes, Reopen"
                          cancelText="Cancel"
                          disabled={!canReopen}
                        >
                          <Button
                            icon={<UnlockOutlined />}
                            loading={reopening}
                            disabled={!canReopen}
                            data-testid="reopen-declaration-btn"
                          >
                            Reopen Declaration
                          </Button>
                        </Popconfirm>
                      )}

                      {!isSubmitted && (
                        <Popconfirm
                          title="Submit Tax Declaration?"
                          description="Once submitted, your declaration will be finalized for payroll computation."
                          onConfirm={handleSubmitDeclaration}
                          okText="Yes, Submit"
                          cancelText="Cancel"
                          disabled={!isEditable}
                        >
                          <Button
                            type="primary"
                            icon={<SendOutlined />}
                            loading={submitting}
                            disabled={!isEditable}
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

              {/* The four sections */}
              <Collapse activeKey={openPanels} onChange={setOpenPanels} items={panels} />
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
