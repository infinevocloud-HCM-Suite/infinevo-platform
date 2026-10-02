import { useState, useEffect, useCallback } from 'react';
import PropTypes from 'prop-types';
import { Card, Form, Input, Checkbox, Button, Row, Col, Typography, Spin, Divider } from 'antd';
import { useCan } from '@shell/screens';
import { statutoryProfileService } from './statutoryProfileService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

export function StatutoryProfileCard({ employeeId }) {
  const canManage = useCan('payroll.salary.manage');
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const eligibleForPf = Form.useWatch('eligibleForPf', form);
  const eligibleForEps = Form.useWatch('eligibleForEps', form);
  const eligibleForEsi = Form.useWatch('eligibleForEsi', form);

  const loadProfile = useCallback(async () => {
    if (!employeeId) return;
    setLoading(true);
    try {
      const data = await statutoryProfileService.get(employeeId);
      if (data) {
        form.setFieldsValue({
          eligibleForPf: Boolean(data.eligibleForPf),
          eligibleForPt: Boolean(data.eligibleForPt),
          eligibleForLwf: Boolean(data.eligibleForLwf),
          eligibleForEsi: Boolean(data.eligibleForEsi),
          eligibleForEps: Boolean(data.eligibleForEps),
          contributesEpsOnHigherWages: Boolean(data.contributesEpsOnHigherWages),
          director: Boolean(data.director),
          pfAccountNumber: data.pfAccountNumber || '',
          uan: data.uan || '',
          esiNumber: data.esiNumber || '',
        });
      }
    } catch (err) {
      if (err?.code !== 'NOT_FOUND') {
        errorMsg(err);
      }
    } finally {
      setLoading(false);
    }
  }, [employeeId, form]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  const handleFinish = async (values) => {
    setSaving(true);
    try {
      const payload = {
        eligibleForPf: Boolean(values.eligibleForPf),
        eligibleForPt: Boolean(values.eligibleForPt),
        eligibleForLwf: Boolean(values.eligibleForLwf),
        eligibleForEsi: Boolean(values.eligibleForEsi),
        eligibleForEps: Boolean(values.eligibleForEps),
        contributesEpsOnHigherWages: Boolean(values.contributesEpsOnHigherWages),
        director: Boolean(values.director),
        pfAccountNumber: values.pfAccountNumber || null,
        uan: values.uan || null,
        esiNumber: values.esiNumber || null,
      };

      await statutoryProfileService.save(employeeId, payload);
      await successMsg('Success', 'Statutory profile updated successfully');
      loadProfile();
    } catch (err) {
      errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      title={<Title level={5} style={{ margin: 0 }}>Statutory Details</Title>}
      style={{ marginTop: 24 }}
    >
      <Spin spinning={loading}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleFinish}
          initialValues={{
            eligibleForPf: false,
            eligibleForPt: false,
            eligibleForLwf: false,
            eligibleForEsi: false,
            eligibleForEps: false,
            contributesEpsOnHigherWages: false,
            director: false,
            pfAccountNumber: '',
            uan: '',
            esiNumber: '',
          }}
        >
          {/* PF Section */}
          <Form.Item name="eligibleForPf" valuePropName="checked" style={{ marginBottom: 8 }}>
            <Checkbox disabled={!canManage}>Employees&apos; Provident Fund (EPF)</Checkbox>
          </Form.Item>

          <div style={{ paddingLeft: 24, marginBottom: 16, display: eligibleForPf ? 'block' : 'none' }}>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  name="pfAccountNumber"
                  label="PF Account Number"
                  help={<Text type="secondary">Format: AA/AAA/0000000/XXX/0000000</Text>}
                >
                  <Input placeholder="PF Account Number" disabled={!canManage} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  name="uan"
                  label="Universal Account Number (UAN)"
                  help={<Text type="secondary">12-digit number</Text>}
                >
                  <Input placeholder="000000000000" disabled={!canManage} />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item name="eligibleForEps" valuePropName="checked" style={{ marginBottom: 8 }}>
              <Checkbox disabled={!canManage}>Contribute to Employee Pension Scheme (EPS)</Checkbox>
            </Form.Item>

            <div style={{ paddingLeft: 24, display: eligibleForEps ? 'block' : 'none' }}>
              <Form.Item name="contributesEpsOnHigherWages" valuePropName="checked">
                <Checkbox disabled={!canManage}>Contribute EPS at actual PF Wages (higher wages)</Checkbox>
              </Form.Item>
            </div>
          </div>

          <Divider style={{ margin: '12px 0' }} />

          {/* PT & LWF */}
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="eligibleForPt" valuePropName="checked">
                <Checkbox disabled={!canManage}>Professional Tax (PT)</Checkbox>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="eligibleForLwf" valuePropName="checked">
                <Checkbox disabled={!canManage}>Labor Welfare Fund (LWF)</Checkbox>
              </Form.Item>
            </Col>
          </Row>

          <Divider style={{ margin: '12px 0' }} />

          {/* ESI Section */}
          <Form.Item name="eligibleForEsi" valuePropName="checked" style={{ marginBottom: 8 }}>
            <Checkbox disabled={!canManage}>Employee State Insurance (ESI)</Checkbox>
          </Form.Item>

          <div style={{ paddingLeft: 24, marginBottom: 16, display: eligibleForEsi ? 'block' : 'none' }}>
            <Row gutter={16}>
              <Col span={12}>
                <Form.Item name="esiNumber" label="ESI Insurance Number">
                  <Input placeholder="ESI Number" disabled={!canManage} />
                </Form.Item>
              </Col>
            </Row>
          </div>

          <Divider style={{ margin: '12px 0' }} />

          <Form.Item name="director" valuePropName="checked">
            <Checkbox disabled={!canManage}>Director Profile</Checkbox>
          </Form.Item>

          {canManage && (
            <div style={{ textAlign: 'right', marginTop: 16 }}>
              <Button type="primary" htmlType="submit" loading={saving}>
                Save Statutory Details
              </Button>
            </div>
          )}
        </Form>
      </Spin>
    </Card>
  );
}

StatutoryProfileCard.propTypes = {
  employeeId: PropTypes.string.isRequired,
};
