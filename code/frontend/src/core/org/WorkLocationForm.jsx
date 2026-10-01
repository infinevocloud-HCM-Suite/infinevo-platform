import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { Formik, Form as FormikForm } from 'formik';
import * as Yup from 'yup';
import {
  Card,
  Row,
  Col,
  Input,
  Switch,
  Button,
  Typography,
  Space,
  Spin,
  theme,
} from 'antd';
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { useCan, NotEntitled, NotFound } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { workLocationService } from './workLocationService.js';
import { invalidateMasters } from '../employee/employeeSlice.js';

const { Title, Text } = Typography;

const validationSchema = Yup.object().shape({
  name: Yup.string().trim().required('Location name is required'),
  code: Yup.string().trim().required('Location code is required'),
  addressLine1: Yup.string().trim().nullable(),
  addressLine2: Yup.string().trim().nullable(),
  city: Yup.string().trim().nullable(),
  state: Yup.string().trim().nullable(),
  stateCode: Yup.string()
    .trim()
    .nullable()
    .max(8, 'State code must be at most 8 characters'),
  zipCode: Yup.string()
    .trim()
    .nullable(),
  countryCode: Yup.string()
    .trim()
    .nullable()
    .test('iso-country-code', 'Country code must be 2 letters', (val) => !val || val.length === 2),
  filingAddress: Yup.boolean().default(false),
  active: Yup.boolean().default(true),
});

export function WorkLocationForm() {
  const canManage = useCan('core.org.manage');
  const { token } = theme.useToken();
  const navigate = useNavigate();
  const { id } = useParams();
  const dispatch = useDispatch();

  const isEdit = Boolean(id);
  const [loading, setLoading] = useState(isEdit);
  const [notFound, setNotFound] = useState(false);
  const [initialValues, setInitialValues] = useState({
    name: '',
    code: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    state: '',
    stateCode: '',
    zipCode: '',
    countryCode: 'IN',
    filingAddress: false,
    active: true,
  });

  useEffect(() => {
    if (!canManage || !id) return;
    let active = true;
    setLoading(true);
    workLocationService
      .get(id)
      .then((data) => {
        if (!active) return;
        if (!data) {
          setNotFound(true);
          return;
        }
        setInitialValues({
          name: data.name || '',
          code: data.code || '',
          addressLine1: data.addressLine1 || '',
          addressLine2: data.addressLine2 || '',
          city: data.city || '',
          state: data.state || '',
          stateCode: data.stateCode || '',
          zipCode: data.zipCode || '',
          countryCode: data.countryCode || 'IN',
          filingAddress: Boolean(data.filingAddress),
          active: data.active ?? true,
        });
      })
      .catch((err) => {
        if (active) {
          setNotFound(true);
          errorMsg(err);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [canManage, id]);

  if (!canManage) {
    return <NotEntitled />;
  }

  if (notFound) {
    return <NotFound />;
  }

  const handleSubmit = async (values, { setSubmitting }) => {
    try {
      const payload = {
        name: values.name.trim(),
        code: values.code.trim().toUpperCase(),
        addressLine1: values.addressLine1.trim(),
        addressLine2: values.addressLine2 ? values.addressLine2.trim() : null,
        city: values.city.trim(),
        state: values.state.trim(),
        stateCode: values.stateCode.trim().toUpperCase(),
        zipCode: values.zipCode.trim(),
        countryCode: (values.countryCode || 'IN').trim().toUpperCase(),
        filingAddress: Boolean(values.filingAddress),
        active: Boolean(values.active),
      };

      if (isEdit) {
        await workLocationService.update(id, payload);
        dispatch(invalidateMasters());
        await successMsg('Location Updated', `${payload.name} has been updated.`);
      } else {
        await workLocationService.create(payload);
        dispatch(invalidateMasters());
        await successMsg('Location Created', `${payload.name} has been created.`);
      }
      navigate('/org/work-locations');
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Card style={{ margin: token.marginLG, textAlign: 'center', padding: token.paddingLG }}>
        <Spin size="large" />
      </Card>
    );
  }

  return (
    <Card style={{ margin: token.marginLG }}>
      <div style={{ marginBottom: token.marginLG }}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/org/work-locations')}
          style={{ marginBottom: token.marginSM }}
        >
          Back to Work Locations
        </Button>
        <Title level={3} style={{ marginTop: 0 }}>
          {isEdit ? 'Edit Work Location' : 'New Work Location'}
        </Title>
        <Text type="secondary">
          Configure physical address and statutory filing settings for this office.
        </Text>
      </div>

      <Formik
        initialValues={initialValues}
        validationSchema={validationSchema}
        enableReinitialize={true}
        onSubmit={handleSubmit}
      >
        {({ values, errors, touched, handleChange, setFieldValue, isSubmitting }) => (
          <FormikForm noValidate>
            <Row gutter={[24, 16]}>
              <Col xs={24} sm={12}>
                <label htmlFor="input-name" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>Location Name *</Text>
                </label>
                <Input
                  id="input-name"
                  name="name"
                  placeholder="e.g. Bengaluru HQ"
                  value={values.name}
                  onChange={handleChange}
                  status={touched.name && errors.name ? 'error' : ''}
                />
                {touched.name && errors.name && (
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {errors.name}
                  </Text>
                )}
              </Col>

              <Col xs={24} sm={12}>
                <label htmlFor="input-code" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>Location Code *</Text>
                </label>
                <Input
                  id="input-code"
                  name="code"
                  placeholder="e.g. BLR"
                  value={values.code}
                  onChange={handleChange}
                  status={touched.code && errors.code ? 'error' : ''}
                />
                {touched.code && errors.code && (
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {errors.code}
                  </Text>
                )}
              </Col>

              <Col xs={24}>
                <label htmlFor="input-addressLine1" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>Address Line 1</Text>
                </label>
                <Input
                  id="input-addressLine1"
                  name="addressLine1"
                  placeholder="Building, street, or suite number (optional)"
                  value={values.addressLine1}
                  onChange={handleChange}
                  status={touched.addressLine1 && errors.addressLine1 ? 'error' : ''}
                />
                {touched.addressLine1 && errors.addressLine1 && (
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {errors.addressLine1}
                  </Text>
                )}
              </Col>

              <Col xs={24}>
                <label htmlFor="input-addressLine2" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>Address Line 2</Text>
                </label>
                <Input
                  id="input-addressLine2"
                  name="addressLine2"
                  placeholder="Apartment, unit, floor (optional)"
                  value={values.addressLine2}
                  onChange={handleChange}
                />
              </Col>

              <Col xs={24} sm={12}>
                <label htmlFor="input-city" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>City</Text>
                </label>
                <Input
                  id="input-city"
                  name="city"
                  placeholder="e.g. Bengaluru (optional)"
                  value={values.city}
                  onChange={handleChange}
                  status={touched.city && errors.city ? 'error' : ''}
                />
                {touched.city && errors.city && (
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {errors.city}
                  </Text>
                )}
              </Col>

              <Col xs={24} sm={6}>
                <label htmlFor="input-state" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>State</Text>
                </label>
                <Input
                  id="input-state"
                  name="state"
                  placeholder="e.g. Karnataka (optional)"
                  value={values.state}
                  onChange={handleChange}
                  status={touched.state && errors.state ? 'error' : ''}
                />
                {touched.state && errors.state && (
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {errors.state}
                  </Text>
                )}
              </Col>

              <Col xs={24} sm={6}>
                <label htmlFor="input-stateCode" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>State Code (optional)</Text>
                </label>
                <Input
                  id="input-stateCode"
                  name="stateCode"
                  placeholder="e.g. KA"
                  maxLength={8}
                  value={values.stateCode}
                  onChange={handleChange}
                  status={touched.stateCode && errors.stateCode ? 'error' : ''}
                />
                {touched.stateCode && errors.stateCode && (
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {errors.stateCode}
                  </Text>
                )}
              </Col>

              <Col xs={24} sm={12}>
                <label htmlFor="input-zipCode" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>PIN Code</Text>
                </label>
                <Input
                  id="input-zipCode"
                  name="zipCode"
                  placeholder="PIN code (optional)"
                  maxLength={16}
                  value={values.zipCode}
                  onChange={handleChange}
                  status={touched.zipCode && errors.zipCode ? 'error' : ''}
                />
                {touched.zipCode && errors.zipCode && (
                  <Text type="danger" style={{ fontSize: 12 }}>
                    {errors.zipCode}
                  </Text>
                )}
              </Col>

              <Col xs={24} sm={12}>
                <label htmlFor="input-countryCode" style={{ display: 'block', marginBottom: 4 }}>
                  <Text strong>Country Code</Text>
                </label>
                <Input
                  id="input-countryCode"
                  name="countryCode"
                  value={values.countryCode}
                  onChange={handleChange}
                  disabled={true}
                />
              </Col>

              <Col xs={24} sm={12}>
                <Space direction="vertical" size="small">
                  <label htmlFor="switch-filingAddress">
                    <Text strong>Statutory Filing Address</Text>
                  </label>
                  <Space align="center">
                    <Switch
                      id="switch-filingAddress"
                      checked={values.filingAddress}
                      onChange={(checked) => setFieldValue('filingAddress', checked)}
                    />
                    <Text type="secondary">
                      Mark as official filing address for statutory reporting (at most one per organization)
                    </Text>
                  </Space>
                </Space>
              </Col>

              <Col xs={24} sm={12}>
                <Space direction="vertical" size="small">
                  <label htmlFor="switch-active">
                    <Text strong>Active</Text>
                  </label>
                  <Space align="center">
                    <Switch
                      id="switch-active"
                      checked={values.active}
                      onChange={(checked) => setFieldValue('active', checked)}
                    />
                    <Text type="secondary">Available for employee assignments and schedules</Text>
                  </Space>
                </Space>
              </Col>
            </Row>

            <div style={{ marginTop: token.marginLG, display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
              <Button onClick={() => navigate('/org/work-locations')}>Cancel</Button>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SaveOutlined />}
                loading={isSubmitting}
                id="btn-submit-location"
              >
                {isEdit ? 'Save Changes' : 'Create Location'}
              </Button>
            </div>
          </FormikForm>
        )}
      </Formik>
    </Card>
  );
}
