import { useState, useEffect } from 'react';
import PropTypes from 'prop-types';
import { useSelector } from 'react-redux';
import dayjs from 'dayjs';
import { Descriptions, Tag, Button, Typography, Row, Col, Input, Select, Switch, DatePicker, Card, theme } from 'antd';
import { EditOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { employeeService } from '../employeeService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Text } = Typography;

export function OverviewTab({ employee, onUpdate }) {
  const { token } = theme.useToken();
  const canUpdate = useCan('core.employee.update');
  const masters = useSelector((state) => state.employee?.masters || {});

  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [formErrors, setFormErrors] = useState({});
  const [formData, setFormData] = useState({
    employeeNumber: employee.employeeNumber || '',
    firstName: employee.firstName || '',
    middleName: employee.middleName || '',
    lastName: employee.lastName || '',
    gender: employee.gender || null,
    dateOfJoining: employee.dateOfJoining || '',
    status: employee.status || 'ACTIVE',
    workEmail: employee.workEmail || '',
    mobile: employee.mobile || '',
    departmentId: employee.departmentId || null,
    designationId: employee.designationId || null,
    workLocationId: employee.workLocationId || null,
    portalEnabled: employee.portalEnabled ?? true,
  });

  useEffect(() => {
    setFormData({
      employeeNumber: employee.employeeNumber || '',
      firstName: employee.firstName || '',
      middleName: employee.middleName || '',
      lastName: employee.lastName || '',
      gender: employee.gender || null,
      dateOfJoining: employee.dateOfJoining || '',
      status: employee.status || 'ACTIVE',
      workEmail: employee.workEmail || '',
      mobile: employee.mobile || '',
      departmentId: employee.departmentId || null,
      designationId: employee.designationId || null,
      workLocationId: employee.workLocationId || null,
      portalEnabled: employee.portalEnabled ?? true,
    });
    setFormErrors({});
  }, [employee]);

  const validate = () => {
    const errs = {};
    if (!formData.employeeNumber || !formData.employeeNumber.trim()) {
      errs.employeeNumber = 'Employee Number is required';
    }
    if (!formData.firstName || !formData.firstName.trim()) {
      errs.firstName = 'First Name is required';
    }
    if (!formData.dateOfJoining) {
      errs.dateOfJoining = 'Date of Joining is required';
    }
    if (formData.workEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.workEmail.trim())) {
      errs.workEmail = 'Invalid email address';
    }
    setFormErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSave = async () => {
    if (!validate()) {
      return;
    }
    setLoading(true);
    try {
      const payload = {
        ...formData,
        employeeNumber: formData.employeeNumber.trim(),
        firstName: formData.firstName.trim(),
        middleName: formData.middleName ? formData.middleName.trim() : null,
        lastName: formData.lastName ? formData.lastName.trim() : null,
        gender: formData.gender || null,
        workEmail: formData.workEmail ? formData.workEmail.trim() : null,
        mobile: formData.mobile ? formData.mobile.trim() : null,
        departmentId: formData.departmentId || null,
        designationId: formData.designationId || null,
        workLocationId: formData.workLocationId || null,
        terminationDate: employee.terminationDate || null,
      };

      const updated = await employeeService.update(employee.id, payload);
      await successMsg('Employee Updated', 'Employee details saved successfully.');
      setIsEditing(false);
      setFormErrors({});
      onUpdate(updated);
    } catch (err) {
      if (err?.fieldErrors && Object.keys(err.fieldErrors).length > 0) {
        setFormErrors(err.fieldErrors);
      }
      await errorMsg(err);
    } finally {
      setLoading(false);
    }
  };

  const departmentName = (employee.departmentId && masters.departments?.[employee.departmentId]) || '—';
  const designationName = (employee.designationId && masters.designations?.[employee.designationId]) || '—';
  const locationName = (employee.workLocationId && masters.workLocations?.[employee.workLocationId]) || '—';

  const departmentOptions = (masters.raw?.departments || []).map((d) => ({ value: d.id, label: d.name }));
  const designationOptions = (masters.raw?.designations || []).map((d) => ({ value: d.id, label: d.name }));
  const workLocationOptions = (masters.raw?.workLocations || []).map((w) => ({ value: w.id, label: w.name }));

  if (isEditing) {
    return (
      <Card variant="borderless" style={{ padding: 0 }}>
        <Row gutter={[token.margin, token.margin]}>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-empNum"><Text strong>Employee Number *</Text></label>
            <Input
              id="edit-empNum"
              status={formErrors.employeeNumber ? 'error' : ''}
              value={formData.employeeNumber}
              onChange={(e) => {
                setFormData({ ...formData, employeeNumber: e.target.value });
                if (formErrors.employeeNumber) setFormErrors({ ...formErrors, employeeNumber: null });
              }}
            />
            {formErrors.employeeNumber && (
              <Text type="danger" style={{ fontSize: 12, display: 'block' }}>{formErrors.employeeNumber}</Text>
            )}
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-firstName"><Text strong>First Name *</Text></label>
            <Input
              id="edit-firstName"
              status={formErrors.firstName ? 'error' : ''}
              value={formData.firstName}
              onChange={(e) => {
                setFormData({ ...formData, firstName: e.target.value });
                if (formErrors.firstName) setFormErrors({ ...formErrors, firstName: null });
              }}
            />
            {formErrors.firstName && (
              <Text type="danger" style={{ fontSize: 12, display: 'block' }}>{formErrors.firstName}</Text>
            )}
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-middleName"><Text strong>Middle Name</Text></label>
            <Input
              id="edit-middleName"
              value={formData.middleName}
              onChange={(e) => setFormData({ ...formData, middleName: e.target.value })}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-lastName"><Text strong>Last Name</Text></label>
            <Input
              id="edit-lastName"
              value={formData.lastName}
              onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-gender"><Text strong>Gender</Text></label>
            <Select
              id="edit-gender"
              style={{ width: '100%' }}
              allowClear
              placeholder="Select gender"
              value={formData.gender}
              onChange={(val) => setFormData({ ...formData, gender: val || null })}
              options={[
                { value: 'MALE', label: 'Male' },
                { value: 'FEMALE', label: 'Female' },
                { value: 'OTHER', label: 'Other' },
              ]}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-status"><Text strong>Status</Text></label>
            {formData.status === 'TERMINATED' ? (
              <div style={{ paddingTop: 4 }}><Tag color="error">TERMINATED</Tag></div>
            ) : (
              <Select
                id="edit-status"
                style={{ width: '100%' }}
                value={formData.status}
                onChange={(val) => setFormData({ ...formData, status: val })}
                options={[
                  { value: 'ACTIVE', label: 'Active' },
                  { value: 'SUSPENDED', label: 'Suspended' },
                ]}
              />
            )}
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-joiningDate"><Text strong>Date of Joining *</Text></label>
            <DatePicker
              id="edit-joiningDate"
              status={formErrors.dateOfJoining ? 'error' : ''}
              style={{ width: '100%' }}
              value={formData.dateOfJoining ? dayjs(formData.dateOfJoining) : null}
              onChange={(_, dateStr) => {
                setFormData({ ...formData, dateOfJoining: dateStr });
                if (formErrors.dateOfJoining) setFormErrors({ ...formErrors, dateOfJoining: null });
              }}
            />
            {formErrors.dateOfJoining && (
              <Text type="danger" style={{ fontSize: 12, display: 'block' }}>{formErrors.dateOfJoining}</Text>
            )}
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-workEmail"><Text strong>Work Email</Text></label>
            <Input
              id="edit-workEmail"
              status={formErrors.workEmail ? 'error' : ''}
              value={formData.workEmail}
              onChange={(e) => {
                setFormData({ ...formData, workEmail: e.target.value });
                if (formErrors.workEmail) setFormErrors({ ...formErrors, workEmail: null });
              }}
            />
            {formErrors.workEmail && (
              <Text type="danger" style={{ fontSize: 12, display: 'block' }}>{formErrors.workEmail}</Text>
            )}
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-mobile"><Text strong>Mobile</Text></label>
            <Input
              id="edit-mobile"
              value={formData.mobile}
              onChange={(e) => setFormData({ ...formData, mobile: e.target.value })}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-department"><Text strong>Department</Text></label>
            <Select
              id="edit-department"
              style={{ width: '100%' }}
              allowClear
              value={formData.departmentId}
              onChange={(val) => setFormData({ ...formData, departmentId: val })}
              options={departmentOptions}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-designation"><Text strong>Designation</Text></label>
            <Select
              id="edit-designation"
              style={{ width: '100%' }}
              allowClear
              value={formData.designationId}
              onChange={(val) => setFormData({ ...formData, designationId: val })}
              options={designationOptions}
            />
          </Col>
          <Col xs={24} sm={12} md={8}>
            <label htmlFor="edit-workLocation"><Text strong>Work Location</Text></label>
            <Select
              id="edit-workLocation"
              style={{ width: '100%' }}
              allowClear
              value={formData.workLocationId}
              onChange={(val) => setFormData({ ...formData, workLocationId: val })}
              options={workLocationOptions}
            />
          </Col>
          <Col xs={24}>
            <Switch
              id="edit-portalEnabled"
              checked={formData.portalEnabled}
              onChange={(checked) => setFormData({ ...formData, portalEnabled: checked })}
            />
            <Text style={{ marginLeft: token.marginSM }}>Portal Enabled</Text>
          </Col>
        </Row>
        <div style={{ marginTop: token.marginLG, display: 'flex', gap: token.marginSM, justifyContent: 'flex-end' }}>
          <Button icon={<CloseOutlined />} onClick={() => setIsEditing(false)}>Cancel</Button>
          <Button type="primary" icon={<SaveOutlined />} loading={loading} onClick={handleSave} id="btn-save-overview">Save Changes</Button>
        </div>
      </Card>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: token.margin }}>
        {canUpdate && (
          <Button icon={<EditOutlined />} onClick={() => setIsEditing(true)} id="btn-edit-overview">
            Edit Details
          </Button>
        )}
      </div>

      <Descriptions bordered column={{ xs: 1, sm: 2, md: 3 }}>
        <Descriptions.Item label="Employee ID">{employee.employeeNumber}</Descriptions.Item>
        <Descriptions.Item label="Full Name">
          {[employee.firstName, employee.middleName, employee.lastName].filter(Boolean).join(' ')}
        </Descriptions.Item>
        <Descriptions.Item label="Status">
          <Tag color={employee.status === 'ACTIVE' ? 'success' : 'default'}>{employee.status}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label="Work Email">{employee.workEmail || '—'}</Descriptions.Item>
        <Descriptions.Item label="Mobile">{employee.mobile || '—'}</Descriptions.Item>
        <Descriptions.Item label="Gender">{employee.gender || '—'}</Descriptions.Item>
        <Descriptions.Item label="Date of Joining">{employee.dateOfJoining || '—'}</Descriptions.Item>
        <Descriptions.Item label="Department">{departmentName}</Descriptions.Item>
        <Descriptions.Item label="Designation">{designationName}</Descriptions.Item>
        <Descriptions.Item label="Work Location">{locationName}</Descriptions.Item>
        <Descriptions.Item label="Portal Enabled">
          <Tag color={employee.portalEnabled ? 'blue' : 'default'}>
            {employee.portalEnabled ? 'Enabled' : 'Disabled'}
          </Tag>
        </Descriptions.Item>
        {employee.terminationDate && (
          <Descriptions.Item label="Termination Date">
            <Text type="danger">{employee.terminationDate}</Text>
          </Descriptions.Item>
        )}
      </Descriptions>
    </div>
  );
}

OverviewTab.propTypes = {
  employee: PropTypes.shape({
    id: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    employeeNumber: PropTypes.string,
    firstName: PropTypes.string,
    middleName: PropTypes.string,
    lastName: PropTypes.string,
    gender: PropTypes.string,
    dateOfJoining: PropTypes.string,
    status: PropTypes.string,
    workEmail: PropTypes.string,
    mobile: PropTypes.string,
    portalEnabled: PropTypes.bool,
    terminationDate: PropTypes.string,
    departmentId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    designationId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
    workLocationId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]),
  }),
  onUpdate: PropTypes.func,
};
