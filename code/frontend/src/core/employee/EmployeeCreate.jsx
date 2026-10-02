import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch, useSelector } from 'react-redux';
import { Formik, Form as FormikForm } from 'formik';
import * as Yup from 'yup';
import dayjs from 'dayjs';
import {
  Card,
  Row,
  Col,
  Input,
  Select,
  Switch,
  Button,
  DatePicker,
  Typography,
  Space,
  theme,
} from 'antd';
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';
import { setMasters } from './employeeSlice.js';
import { successMsg, errorMsg } from '../../shared/ui/msgHelper.js';

const { Title, Text } = Typography;

const employeeSchema = Yup.object().shape({
  employeeNumber: Yup.string()
    .trim()
    .required('Employee number is required')
    .max(64, 'Maximum 64 characters'),
  firstName: Yup.string().trim().required('First name is required'),
  middleName: Yup.string().nullable(),
  lastName: Yup.string().nullable(),
  gender: Yup.string().nullable(),
  dateOfJoining: Yup.string().required('Date of joining is required'),
  status: Yup.string().default('ACTIVE'),
  workEmail: Yup.string().email('Invalid email address').nullable(),
  mobile: Yup.string().nullable(),
  departmentId: Yup.string().nullable(),
  designationId: Yup.string().nullable(),
  workLocationId: Yup.string().nullable(),
  portalEnabled: Yup.boolean().default(true),
});

export function EmployeeCreate() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { token } = theme.useToken();

  const masters = useSelector((state) => state.employee?.masters || {});
  const mastersLoadedAt = useSelector((state) => state.employee?.loadedAt);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!mastersLoadedAt) {
      orgMasterService.all().then((res) => {
        dispatch(setMasters(res));
      }).catch(() => {});
    }
  }, [dispatch, mastersLoadedAt]);

  const initialValues = {
    employeeNumber: '',
    firstName: '',
    middleName: '',
    lastName: '',
    gender: null,
    dateOfJoining: '',
    status: 'ACTIVE',
    workEmail: '',
    mobile: '',
    departmentId: null,
    designationId: null,
    workLocationId: null,
    portalEnabled: true,
  };

  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      const payload = {
        ...values,
        employeeNumber: values.employeeNumber.trim(),
        firstName: values.firstName.trim(),
        middleName: values.middleName ? values.middleName.trim() : null,
        lastName: values.lastName ? values.lastName.trim() : null,
        workEmail: values.workEmail ? values.workEmail.trim() : null,
        mobile: values.mobile ? values.mobile.trim() : null,
        departmentId: values.departmentId || null,
        designationId: values.designationId || null,
        workLocationId: values.workLocationId || null,
      };

      const created = await employeeService.create(payload);
      await successMsg('Employee Created', `Employee ${created.employeeNumber} created successfully.`);
      navigate(`/employees/${created.id}`);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  };

  const departmentOptions = (masters.raw?.departments || []).map((d) => ({
    value: d.id,
    label: d.name,
  }));
  const designationOptions = (masters.raw?.designations || []).map((d) => ({
    value: d.id,
    label: d.name,
  }));
  const workLocationOptions = (masters.raw?.workLocations || []).map((w) => ({
    value: w.id,
    label: w.name,
  }));

  return (
    <Card variant="borderless" style={{ borderRadius: token.borderRadiusLG }}>
      <div style={{ display: 'flex', alignItems: 'center', marginBottom: token.marginLG }}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/employees')}
          style={{ marginRight: token.marginSM }}
        />
        <Title level={4} style={{ margin: 0 }}>Create New Employee</Title>
      </div>

      <Formik
        initialValues={initialValues}
        validationSchema={employeeSchema}
        onSubmit={handleSubmit}
      >
        {({ values, errors, touched, handleChange, setFieldValue, submitForm }) => (
          <FormikForm>
            <Row gutter={[token.margin, token.margin]}>
              <Col xs={24} sm={12} md={8}>
                <label htmlFor="input-employeeNumber"><Text strong>Employee Number *</Text></label>
                <Input
                  id="input-employeeNumber"
                  name="employeeNumber"
                  placeholder="e.g. EMP001"
                  value={values.employeeNumber}
                  onChange={handleChange}
                  status={touched.employeeNumber && errors.employeeNumber ? 'error' : ''}
                />
                {touched.employeeNumber && errors.employeeNumber && (
                  <Text type="danger" style={{ fontSize: 12 }}>{errors.employeeNumber}</Text>
                )}
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="input-firstName"><Text strong>First Name *</Text></label>
                <Input
                  id="input-firstName"
                  name="firstName"
                  placeholder="First name"
                  value={values.firstName}
                  onChange={handleChange}
                  status={touched.firstName && errors.firstName ? 'error' : ''}
                />
                {touched.firstName && errors.firstName && (
                  <Text type="danger" style={{ fontSize: 12 }}>{errors.firstName}</Text>
                )}
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="input-middleName"><Text strong>Middle Name</Text></label>
                <Input
                  id="input-middleName"
                  name="middleName"
                  placeholder="Middle name"
                  value={values.middleName}
                  onChange={handleChange}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="input-lastName"><Text strong>Last Name</Text></label>
                <Input
                  id="input-lastName"
                  name="lastName"
                  placeholder="Last name"
                  value={values.lastName}
                  onChange={handleChange}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="select-gender"><Text strong>Gender</Text></label>
                <Select
                  id="select-gender"
                  style={{ width: '100%' }}
                  allowClear
                  placeholder="Select gender"
                  value={values.gender}
                  onChange={(val) => setFieldValue('gender', val)}
                  options={[
                    { value: 'MALE', label: 'Male' },
                    { value: 'FEMALE', label: 'Female' },
                    { value: 'OTHER', label: 'Other' },
                  ]}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="picker-dateOfJoining"><Text strong>Date of Joining *</Text></label>
                <DatePicker
                  id="picker-dateOfJoining"
                  style={{ width: '100%' }}
                  value={values.dateOfJoining ? dayjs(values.dateOfJoining) : null}
                  onChange={(_, dateStr) => setFieldValue('dateOfJoining', dateStr)}
                  status={touched.dateOfJoining && errors.dateOfJoining ? 'error' : ''}
                />
                {touched.dateOfJoining && errors.dateOfJoining && (
                  <Text type="danger" style={{ fontSize: 12 }}>{errors.dateOfJoining}</Text>
                )}
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="input-workEmail"><Text strong>Work Email</Text></label>
                <Input
                  id="input-workEmail"
                  name="workEmail"
                  placeholder="email@company.com"
                  value={values.workEmail}
                  onChange={handleChange}
                  status={touched.workEmail && errors.workEmail ? 'error' : ''}
                />
                {touched.workEmail && errors.workEmail && (
                  <Text type="danger" style={{ fontSize: 12 }}>{errors.workEmail}</Text>
                )}
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="input-mobile"><Text strong>Mobile</Text></label>
                <Input
                  id="input-mobile"
                  name="mobile"
                  placeholder="Mobile number"
                  value={values.mobile}
                  onChange={handleChange}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="select-status"><Text strong>Status</Text></label>
                <Select
                  id="select-status"
                  style={{ width: '100%' }}
                  value={values.status}
                  onChange={(val) => setFieldValue('status', val)}
                  options={[
                    { value: 'ACTIVE', label: 'Active' },
                    { value: 'SUSPENDED', label: 'Suspended' },
                  ]}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="select-department"><Text strong>Department</Text></label>
                <Select
                  id="select-department"
                  style={{ width: '100%' }}
                  placeholder="Select department"
                  allowClear
                  value={values.departmentId}
                  onChange={(val) => setFieldValue('departmentId', val)}
                  options={departmentOptions}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="select-designation"><Text strong>Designation</Text></label>
                <Select
                  id="select-designation"
                  style={{ width: '100%' }}
                  placeholder="Select designation"
                  allowClear
                  value={values.designationId}
                  onChange={(val) => setFieldValue('designationId', val)}
                  options={designationOptions}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="select-workLocation"><Text strong>Work Location</Text></label>
                <Select
                  id="select-workLocation"
                  style={{ width: '100%' }}
                  placeholder="Select location"
                  allowClear
                  value={values.workLocationId}
                  onChange={(val) => setFieldValue('workLocationId', val)}
                  options={workLocationOptions}
                />
              </Col>

              <Col xs={24}>
                <Space style={{ marginTop: token.marginSM }}>
                  <Text strong>Portal Enabled:</Text>
                  <Switch
                    id="switch-portalEnabled"
                    checked={values.portalEnabled}
                    onChange={(checked) => setFieldValue('portalEnabled', checked)}
                  />
                  <Text type="secondary">(Allows employee self-service login)</Text>
                </Space>
              </Col>
            </Row>

            <div style={{ marginTop: token.marginLG, display: 'flex', justifyContent: 'flex-end', gap: token.marginSM }}>
              <Button onClick={() => navigate('/employees')}>Cancel</Button>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={loading}
                onClick={submitForm}
                id="btn-submit-employee"
              >
                Create Employee
              </Button>
            </div>
          </FormikForm>
        )}
      </Formik>
    </Card>
  );
}
