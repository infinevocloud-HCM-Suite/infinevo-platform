import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import {
  Modal,
  Form,
  Input,
  InputNumber,
  DatePicker,
  Select,
  Row,
  Col,
  Alert,
  Divider,
  theme,
} from 'antd';
import { form24QService } from './form24QService';
import { fyOptions } from '../tax/financialYear';

export function ChallanModal({ open, onClose, onSuccess, financialYear, quarter, challan }) {
  const { token } = theme.useToken();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Watch amount fields for dynamic total calculation
  const taxAmount = Form.useWatch('taxAmount', form) || 0;
  const surchargeAmount = Form.useWatch('surchargeAmount', form) || 0;
  const cessAmount = Form.useWatch('cessAmount', form) || 0;
  const interestAmount = Form.useWatch('interestAmount', form) || 0;
  const feePenaltyAmount = Form.useWatch('feePenaltyAmount', form) || 0;

  const calculatedTotal = (
    Number(taxAmount) +
    Number(surchargeAmount) +
    Number(cessAmount) +
    Number(interestAmount) +
    Number(feePenaltyAmount)
  ).toFixed(2);

  useEffect(() => {
    if (open) {
      form.resetFields();
      setError(null);
      form.setFieldsValue(
        challan
          ? {
              financialYear: challan.financialYear,
              quarter: challan.quarter,
              bsrCode: challan.bsrCode,
              challanSerialNo: challan.challanSerialNo,
              tenderDate: dayjs(challan.tenderDate),
              minorHead: challan.minorHead,
              taxAmount: Number(challan.taxAmount),
              surchargeAmount: Number(challan.surchargeAmount),
              cessAmount: Number(challan.cessAmount),
              interestAmount: Number(challan.interestAmount),
              feePenaltyAmount: Number(challan.feePenaltyAmount),
              remarks: challan.remarks,
            }
          : {
              financialYear: financialYear || '2026-27',
              quarter: quarter || 'Q1',
              minorHead: '200',
              tenderDate: dayjs(),
              taxAmount: 0,
              surchargeAmount: 0,
              cessAmount: 0,
              interestAmount: 0,
              feePenaltyAmount: 0,
            },
      );
    }
  }, [open, financialYear, quarter, challan, form]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      setError(null);

      const payload = {
        financialYear: values.financialYear,
        quarter: values.quarter,
        bsrCode: values.bsrCode.trim(),
        challanSerialNo: values.challanSerialNo.trim(),
        tenderDate: values.tenderDate ? dayjs(values.tenderDate).format('YYYY-MM-DD') : undefined,
        taxAmount: Number(values.taxAmount || 0),
        surchargeAmount: Number(values.surchargeAmount || 0),
        cessAmount: Number(values.cessAmount || 0),
        interestAmount: Number(values.interestAmount || 0),
        feePenaltyAmount: Number(values.feePenaltyAmount || 0),
        minorHead: values.minorHead,
        remarks: values.remarks?.trim() || null,
      };

      if (challan?.id) {
        await form24QService.updateChallan(challan.id, payload);
      } else {
        await form24QService.createChallan(payload);
      }
      if (onSuccess) {
        onSuccess();
      }
      onClose();
    } catch (err) {
      if (err.errorFields) {
        // Validation error inside form
        return;
      }
      const msg = err?.message || 'Failed to save the TDS challan. Please check the details and try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={challan?.id ? 'Edit TDS challan' : 'Record TDS challan (ITNS 281)'}
      open={open}
      onOk={handleSubmit}
      onCancel={onClose}
      confirmLoading={loading}
      okText={challan?.id ? 'Save' : 'Record challan'}
      cancelText="Cancel"
      width={680}
      destroyOnHidden
    >
      {error && (
        <Alert
          type="error"
          showIcon
          message="Submission Error"
          description={error}
          style={{ marginBottom: 16 }}
        />
      )}

      <Form form={form} layout="vertical" preserve={false}>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="financialYear"
              label="Financial Year"
              rules={[{ required: true, message: 'Please select financial year' }]}
            >
              <Select options={fyOptions()} placeholder="Select FY" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="quarter"
              label="Quarter"
              rules={[{ required: true, message: 'Please select quarter' }]}
            >
              <Select
                options={[
                  { label: 'Q1 (Apr - Jun)', value: 'Q1' },
                  { label: 'Q2 (Jul - Sep)', value: 'Q2' },
                  { label: 'Q3 (Oct - Dec)', value: 'Q3' },
                  { label: 'Q4 (Jan - Mar)', value: 'Q4' },
                ]}
                placeholder="Select quarter"
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="bsrCode"
              label="BSR Code (Bank Branch)"
              rules={[
                { required: true, message: 'Please enter BSR code' },
                { pattern: /^[0-9]{7}$/, message: 'BSR code must be exactly 7 digits' },
              ]}
            >
              <Input placeholder="e.g. 0510304" maxLength={7} />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="challanSerialNo"
              label="Challan Serial No"
              rules={[
                { required: true, message: 'Please enter challan serial no' },
                { pattern: /^[0-9]{1,5}$/, message: 'Challan serial no must be 1 to 5 digits' },
              ]}
            >
              <Input placeholder="e.g. 00123" maxLength={5} />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="tenderDate"
              label="Tender / Deposit Date"
              rules={[{ required: true, message: 'Please select deposit date' }]}
            >
              <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="minorHead"
              label="Minor Head"
              rules={[{ required: true, message: 'Please select minor head' }]}
            >
              <Select
                options={[
                  { label: '200 - TDS Payable by Taxpayer', value: '200' },
                  { label: '400 - TDS Regular Assessment', value: '400' },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>

        <Divider orientation="left" plain>
          Amounts deposited (whole rupees)
        </Divider>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              name="taxAmount"
              label="Tax"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                precision={0}
                step={1}
                placeholder="0"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="surchargeAmount" label="Surcharge">
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                precision={0}
                step={1}
                placeholder="0"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="cessAmount" label="Education Cess">
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                precision={0}
                step={1}
                placeholder="0"
              />
            </Form.Item>
          </Col>
        </Row>

        <Row gutter={16}>
          <Col span={8}>
            <Form.Item name="interestAmount" label="Interest">
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                precision={0}
                step={1}
                placeholder="0"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item name="feePenaltyAmount" label="Fee / Penalty (Sec 234E)">
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                precision={0}
                step={1}
                placeholder="0"
              />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item label="Total Deposited Amount">
              <div
                style={{
                  height: 32,
                  lineHeight: '32px',
                  padding: '0 11px',
                  background: token.colorFillAlter,
                  border: `1px solid ${token.colorBorder}`,
                  borderRadius: 6,
                  fontWeight: 600,
                  color: token.colorPrimary,
                }}
              >
                ₹ {Number(calculatedTotal).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </div>
            </Form.Item>
          </Col>
        </Row>

        <Form.Item name="remarks" label="Remarks / Bank Branch Name">
          <Input.TextArea rows={2} placeholder="Optional notes e.g. SBI Connaught Place Branch" maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

ChallanModal.propTypes = {
  open: PropTypes.bool.isRequired,
  onClose: PropTypes.func.isRequired,
  onSuccess: PropTypes.func,
  financialYear: PropTypes.string,
  quarter: PropTypes.string,
  challan: PropTypes.object,
};
