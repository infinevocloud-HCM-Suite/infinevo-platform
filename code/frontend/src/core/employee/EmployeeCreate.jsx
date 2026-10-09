import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
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
  InputNumber,
  Select,
  Switch,
  Button,
  DatePicker,
  Collapse,
  Checkbox,
  Alert,
  Typography,
  Space,
  theme,
} from 'antd';
import { ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { employeeService } from './employeeService.js';
import { orgMasterService } from './orgMasterService.js';
import { setMasters } from './employeeSlice.js';
import { roleService } from '../approvals/roleService.js';
import { employeeInvitationService } from '../invitation/employeeInvitationService.js';
import {
  GENDER_OPTIONS,
  EMPLOYMENT_TYPE_OPTIONS,
  NOTICE_PERIOD_MIN,
  NOTICE_PERIOD_MAX,
} from './sectionFields.js';
import { successMsg, errorMsg } from '../../shared/ui/msgHelper.js';

const { Title, Text } = Typography;

/**
 * Fields in the collapsed "More" group (D-40). When one of them fails validation the group opens,
 * so the error is never hidden behind a closed panel.
 */
export const MORE_FIELDS = [
  'middleName',
  'gender',
  'mobile',
  'employmentType',
  'probationEndDate',
  'noticePeriodDays',
  'status',
  'portalEnabled',
];

const MORE_KEY = 'more';

export const employeeSchema = Yup.object().shape({
  employeeNumber: Yup.string()
    .trim()
    .required('Employee number is required')
    .max(64, 'Maximum 64 characters'),
  firstName: Yup.string().trim().required('First name is required'),
  lastName: Yup.string().trim().required('Last name is required'),
  workEmail: Yup.string()
    .trim()
    .email('Invalid email address')
    .required('Work email is required'),
  dateOfJoining: Yup.string().required('Date of joining is required'),
  departmentId: Yup.string().nullable(),
  designationId: Yup.string().nullable(),
  workLocationId: Yup.string().nullable(),
  middleName: Yup.string().nullable(),
  gender: Yup.string().nullable(),
  mobile: Yup.string().nullable(),
  employmentType: Yup.string().nullable(),
  probationEndDate: Yup.string()
    .nullable()
    .test(
      'not-before-joining',
      'Probation end cannot be before the date of joining',
      function notBeforeJoining(value) {
        const { dateOfJoining } = this.parent;
        if (!value || !dateOfJoining) return true;
        return !dayjs(value).isBefore(dayjs(dateOfJoining), 'day');
      }
    ),
  noticePeriodDays: Yup.number()
    .nullable()
    .integer('Whole days only')
    .min(NOTICE_PERIOD_MIN, `At least ${NOTICE_PERIOD_MIN} days`)
    .max(NOTICE_PERIOD_MAX, `At most ${NOTICE_PERIOD_MAX} days`),
  status: Yup.string().default('ACTIVE'),
  portalEnabled: Yup.boolean().default(true),
});

const trimOrNull = (value) => {
  if (typeof value !== 'string') return value ?? null;
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
};

/** The create request (EmployeeRequest), from the form's values. */
export function toCreatePayload(values) {
  return {
    employeeNumber: values.employeeNumber.trim(),
    firstName: values.firstName.trim(),
    middleName: trimOrNull(values.middleName),
    lastName: trimOrNull(values.lastName),
    gender: values.gender || null,
    dateOfJoining: values.dateOfJoining,
    status: values.status,
    workEmail: trimOrNull(values.workEmail),
    mobile: trimOrNull(values.mobile),
    departmentId: values.departmentId || null,
    designationId: values.designationId || null,
    workLocationId: values.workLocationId || null,
    portalEnabled: values.portalEnabled,
    employmentType: values.employmentType || null,
    probationEndDate: values.probationEndDate || null,
    noticePeriodDays:
      values.noticePeriodDays === '' || values.noticePeriodDays === undefined
        ? null
        : values.noticePeriodDays,
  };
}

/** Never offered on Add Employee: the platform role is not a tenant grant (W-73.3). */
export const PLATFORM_ADMIN_CODE = 'platform-admin';
/** Choosing extra roles needs the same action as PUT /users/{id}/roles; the server refuses it too. */
export const ROLE_ASSIGN_ACTION = 'core.role.assign';
/** Granted by the server on accept; shown pre-ticked and locked. */
export const EMPLOYEE_CODE = 'employee';

/** Options for the roles select: every role but platform-admin, employee first and locked. */
export function roleOptions(roles) {
  const usable = (roles || []).filter((r) => r.code !== PLATFORM_ADMIN_CODE);
  const ordered = [
    ...usable.filter((r) => r.code === EMPLOYEE_CODE),
    ...usable.filter((r) => r.code !== EMPLOYEE_CODE),
  ];
  return ordered.map((r) => ({
    value: r.id,
    label: r.name || r.code,
    disabled: r.code === EMPLOYEE_CODE,
  }));
}

const employeeRoleIdOf = (roles) => (roles || []).find((r) => r.code === EMPLOYEE_CODE)?.id;

/**
 * The invitation request: the extra roles only, the employee role stripped. Without
 * core.role.assign no extra role is sent at all.
 */
export function toInvitationPayload(employeeId, values, roles, canAssignRoles = true) {
  const employeeRoleId = employeeRoleIdOf(roles);
  return {
    employeeId,
    roleIds: canAssignRoles ? (values.roleIds || []).filter((id) => id !== employeeRoleId) : [],
  };
}

function FieldError({ name, errors, touched }) {
  if (!touched[name] || !errors[name]) return null;
  return (
    <Text type="danger" style={{ fontSize: 12 }}>
      {errors[name]}
    </Text>
  );
}

FieldError.propTypes = {
  name: PropTypes.string.isRequired,
  errors: PropTypes.object.isRequired,
  touched: PropTypes.object.isRequired,
};

export function EmployeeCreate() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { token } = theme.useToken();
  const canAssignRoles = useCan(ROLE_ASSIGN_ACTION);

  const masters = useSelector((state) => state.employee?.masters || {});
  const mastersLoadedAt = useSelector((state) => state.employee?.loadedAt);
  const [loading, setLoading] = useState(false);
  const [openPanels, setOpenPanels] = useState([]);
  const [roles, setRoles] = useState([]);
  const [partialFailure, setPartialFailure] = useState(null);

  useEffect(() => {
    if (!canAssignRoles) return;
    roleService.list().then(setRoles).catch(() => setRoles([]));
  }, [canAssignRoles]);

  const employeeRoleId = employeeRoleIdOf(roles);

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
    employmentType: null,
    probationEndDate: null,
    noticePeriodDays: null,
    giveAccess: false,
    roleIds: [],
  };

  const handleSubmit = async (values) => {
    setLoading(true);
    try {
      const created = await employeeService.create(toCreatePayload(values));
      if (!values.giveAccess) {
        await successMsg('Employee Created', `Employee ${created.employeeNumber} created successfully.`);
        navigate(`/employees/${created.id}`);
        return;
      }
      // Second step: the employee exists now, so a failure here must not read as "not saved".
      try {
        await employeeInvitationService.create(toInvitationPayload(created.id, values, roles, canAssignRoles));
        await successMsg('Employee Created', `Employee ${created.employeeNumber} created and invited.`);
        navigate(`/employees/${created.id}`);
      } catch (inviteErr) {
        setPartialFailure({ employeeId: created.id, message: inviteErr?.message || 'unknown error' });
      }
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

      {partialFailure && (
        <Alert
          type="warning"
          showIcon
          id="alert-invitation-failed"
          style={{ marginBottom: token.marginLG }}
          message={`Saved. Invitation failed: ${partialFailure.message} — invite from the employee page`}
          action={
            <Button size="small" onClick={() => navigate(`/employees/${partialFailure.employeeId}`)}>
              Open employee page
            </Button>
          }
        />
      )}

      <Formik
        initialValues={initialValues}
        validationSchema={employeeSchema}
        onSubmit={handleSubmit}
      >
        {({ values, errors, touched, handleChange, setFieldValue, submitForm, validateForm }) => {
          const onCreate = async () => {
            const found = await validateForm();
            if (Object.keys(found).some((key) => MORE_FIELDS.includes(key))) {
              setOpenPanels((prev) => (prev.includes(MORE_KEY) ? prev : [...prev, MORE_KEY]));
            }
            submitForm();
          };

          const moreFields = (
            <Row gutter={[token.margin, token.margin]}>
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
                <label htmlFor="select-gender"><Text strong>Gender</Text></label>
                <Select
                  id="select-gender"
                  style={{ width: '100%' }}
                  allowClear
                  placeholder="Select gender"
                  value={values.gender}
                  onChange={(val) => setFieldValue('gender', val ?? null)}
                  options={GENDER_OPTIONS}
                />
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
                <label htmlFor="select-employmentType"><Text strong>Employment Type</Text></label>
                <Select
                  id="select-employmentType"
                  style={{ width: '100%' }}
                  allowClear
                  placeholder="Select employment type"
                  value={values.employmentType}
                  onChange={(val) => setFieldValue('employmentType', val ?? null)}
                  options={EMPLOYMENT_TYPE_OPTIONS}
                />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="picker-probationEndDate"><Text strong>Probation End Date</Text></label>
                <DatePicker
                  id="picker-probationEndDate"
                  style={{ width: '100%' }}
                  placeholder="Probation end date"
                  value={values.probationEndDate ? dayjs(values.probationEndDate) : null}
                  onChange={(_, dateStr) => setFieldValue('probationEndDate', dateStr || null, true)}
                  status={touched.probationEndDate && errors.probationEndDate ? 'error' : ''}
                />
                <FieldError name="probationEndDate" errors={errors} touched={touched} />
              </Col>

              <Col xs={24} sm={12} md={8}>
                <label htmlFor="input-noticePeriodDays"><Text strong>Notice Period (days)</Text></label>
                <InputNumber
                  id="input-noticePeriodDays"
                  style={{ width: '100%' }}
                  placeholder="e.g. 30"
                  min={NOTICE_PERIOD_MIN}
                  max={NOTICE_PERIOD_MAX}
                  precision={0}
                  value={values.noticePeriodDays}
                  onChange={(val) => setFieldValue('noticePeriodDays', val ?? null)}
                  status={touched.noticePeriodDays && errors.noticePeriodDays ? 'error' : ''}
                />
                <FieldError name="noticePeriodDays" errors={errors} touched={touched} />
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
          );

          return (
            <FormikForm>
              <Title level={5} style={{ marginTop: 0 }}>Required</Title>
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
                  <FieldError name="employeeNumber" errors={errors} touched={touched} />
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
                  <FieldError name="firstName" errors={errors} touched={touched} />
                </Col>

                <Col xs={24} sm={12} md={8}>
                  <label htmlFor="input-lastName"><Text strong>Last Name *</Text></label>
                  <Input
                    id="input-lastName"
                    name="lastName"
                    placeholder="Last name"
                    value={values.lastName}
                    onChange={handleChange}
                    status={touched.lastName && errors.lastName ? 'error' : ''}
                  />
                  <FieldError name="lastName" errors={errors} touched={touched} />
                </Col>

                <Col xs={24} sm={12} md={8}>
                  <label htmlFor="input-workEmail"><Text strong>Work Email *</Text></label>
                  <Input
                    id="input-workEmail"
                    name="workEmail"
                    placeholder="email@company.com"
                    value={values.workEmail}
                    onChange={handleChange}
                    status={touched.workEmail && errors.workEmail ? 'error' : ''}
                  />
                  <FieldError name="workEmail" errors={errors} touched={touched} />
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
                  <FieldError name="dateOfJoining" errors={errors} touched={touched} />
                </Col>

                <Col xs={24} sm={12} md={8}>
                  <label htmlFor="select-department"><Text strong>Department</Text></label>
                  <Select
                    id="select-department"
                    style={{ width: '100%' }}
                    placeholder="Select department"
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    value={values.departmentId}
                    onChange={(val) => setFieldValue('departmentId', val ?? null)}
                    options={departmentOptions}
                    status={touched.departmentId && errors.departmentId ? 'error' : ''}
                  />
                  <FieldError name="departmentId" errors={errors} touched={touched} />
                </Col>

                <Col xs={24} sm={12} md={8}>
                  <label htmlFor="select-designation"><Text strong>Designation</Text></label>
                  <Select
                    id="select-designation"
                    style={{ width: '100%' }}
                    placeholder="Select designation"
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    value={values.designationId}
                    onChange={(val) => setFieldValue('designationId', val ?? null)}
                    options={designationOptions}
                    status={touched.designationId && errors.designationId ? 'error' : ''}
                  />
                  <FieldError name="designationId" errors={errors} touched={touched} />
                </Col>

                <Col xs={24} sm={12} md={8}>
                  <label htmlFor="select-workLocation"><Text strong>Work Location</Text></label>
                  <Select
                    id="select-workLocation"
                    style={{ width: '100%' }}
                    placeholder="Select location"
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    value={values.workLocationId}
                    onChange={(val) => setFieldValue('workLocationId', val ?? null)}
                    options={workLocationOptions}
                    status={touched.workLocationId && errors.workLocationId ? 'error' : ''}
                  />
                  <FieldError name="workLocationId" errors={errors} touched={touched} />
                </Col>
              </Row>

              <Collapse
                style={{ marginTop: token.marginLG }}
                activeKey={openPanels}
                onChange={(keys) => setOpenPanels(Array.isArray(keys) ? keys : [keys])}
                items={[
                  {
                    key: MORE_KEY,
                    label: <Text strong>More</Text>,
                    children: moreFields,
                  },
                ]}
              />

              <Title level={5} style={{ marginTop: token.marginLG }}>Portal access</Title>
              <Checkbox
                id="check-giveAccess"
                checked={values.giveAccess}
                onChange={(e) => setFieldValue('giveAccess', e.target.checked)}
              >
                Give portal access
              </Checkbox>
              {values.giveAccess && !canAssignRoles && (
                <div style={{ marginTop: token.marginSM }}>
                  <Text strong>Roles: </Text>
                  <Text id="text-locked-role">Employee</Text>
                  <div>
                    <Text type="secondary" id="text-access-note">An email goes to the work email.</Text>
                  </div>
                </div>
              )}
              {values.giveAccess && canAssignRoles && (
                <div style={{ marginTop: token.marginSM }}>
                  <label htmlFor="select-roles"><Text strong>Roles</Text></label>
                  <Select
                    mode="multiple"
                    id="select-roles"
                    style={{ width: '100%' }}
                    placeholder="Select roles"
                    optionFilterProp="label"
                    options={roleOptions(roles)}
                    value={[employeeRoleId, ...values.roleIds].filter(Boolean)}
                    onChange={(ids) =>
                      setFieldValue('roleIds', (ids || []).filter((rid) => rid !== employeeRoleId))
                    }
                  />
                  <Text type="secondary" id="text-access-note">An email goes to the work email.</Text>
                </div>
              )}

              <div style={{ marginTop: token.marginLG, display: 'flex', justifyContent: 'flex-end', gap: token.marginSM }}>
                <Button onClick={() => navigate('/employees')}>Cancel</Button>
                <Button
                  type="primary"
                  icon={<SaveOutlined />}
                  loading={loading}
                  disabled={Boolean(partialFailure)}
                  onClick={onCreate}
                  id="btn-submit-employee"
                >
                  Create Employee
                </Button>
              </div>
            </FormikForm>
          );
        }}
      </Formik>
    </Card>
  );
}
