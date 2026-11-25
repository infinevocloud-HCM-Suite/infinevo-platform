import { PageHeader } from '@shared/ui/PageHeader.jsx';
import { useEffect, useState, useMemo } from 'react';
import { Formik, Form as FormikForm } from 'formik';
import * as Yup from 'yup';
import { Card, Row, Col, Input, Button, Typography, Space, Spin, Upload, Avatar, Select, Checkbox, theme } from 'antd';
import { SaveOutlined, UploadOutlined, DeleteOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { companyProfileService } from './companyProfileService.js';
import { documentService } from '../document/documentService.js';
import { logoFileProblem, resizeLogo } from './logoImage.js';
import { getTimezonesForCountry, getAllTimezones } from './timezones.js';

const { Text } = Typography;

const TAGLINE_MAX = 80;

const MONTH_OPTIONS = [
  { value: 1, label: 'January' },
  { value: 2, label: 'February' },
  { value: 3, label: 'March' },
  { value: 4, label: 'April' },
  { value: 5, label: 'May' },
  { value: 6, label: 'June' },
  { value: 7, label: 'July' },
  { value: 8, label: 'August' },
  { value: 9, label: 'September' },
  { value: 10, label: 'October' },
  { value: 11, label: 'November' },
  { value: 12, label: 'December' },
];

const validationSchema = Yup.object().shape({
  tagline: Yup.string().trim().max(TAGLINE_MAX, `Tagline must be at most ${TAGLINE_MAX} characters`),
  countryCode: Yup.string().required('Country is required'),
  timezone: Yup.string().required('Timezone is required'),
  leaveYearStartMonth: Yup.number().min(1).max(12).required('Leave year start month is required'),
});

/** "Acme Ltd" → "AL": the header's fallback, shown here so the admin sees what no logo looks like. */
function initialsOf(name) {
  if (!name) return '';
  return name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w.charAt(0).toUpperCase())
    .join('');
}

/**
 * Company profile (W-73.1, W-116) at `/settings/company`: the company name (read-only),
 * logo, tagline, country, timezone, and leave year start month.
 */
export function CompanyProfile() {
  const canRead = useCan('core.tenant.read');
  const canManage = useCan('core.tenant.manage');
  const { token } = theme.useToken();

  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState(null);
  const [countries, setCountries] = useState([]);
  const [showAllTimezones, setShowAllTimezones] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [preview, setPreview] = useState(null);

  useEffect(() => {
    if (!canRead) return undefined;
    let active = true;
    setLoading(true);
    Promise.all([
      companyProfileService.get(),
      companyProfileService.countries().catch(() => []),
    ])
      .then(([profileData, countriesData]) => {
        if (!active) return;
        setProfile(profileData || {});
        setPreview(profileData?.logoUrl || null);
        setCountries(Array.isArray(countriesData) ? countriesData : []);
      })
      .catch((err) => {
        if (active) errorMsg(err);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [canRead]);

  const allTimezones = useMemo(() => getAllTimezones(), []);

  if (!canRead) {
    return <NotEntitled />;
  }

  if (loading || !profile) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: token.paddingLG * 2 }}>
        <Spin size="large" />
      </div>
    );
  }

  const chooseLogo = async (file, setFieldValue) => {
    const problem = logoFileProblem(file);
    if (problem) {
      errorMsg('Logo not accepted', problem);
      return false;
    }
    setUploading(true);
    try {
      const resized = await resizeLogo(file);
      const doc = await documentService.upload(resized, 'TENANT_LOGO');
      setFieldValue('logoDocumentId', doc?.id || null);
      if (typeof URL !== 'undefined' && typeof URL.createObjectURL === 'function') {
        setPreview(URL.createObjectURL(resized));
      } else {
        setPreview(null);
      }
    } catch (err) {
      errorMsg(err);
    } finally {
      setUploading(false);
    }
    return false;
  };

  const handleSubmit = async (values, { setSubmitting, setFieldError }) => {
    try {
      const saved = await companyProfileService.update({
        tagline: values.tagline ? values.tagline.trim() : null,
        logoDocumentId: values.logoDocumentId || null,
        countryCode: values.countryCode || null,
        timezone: values.timezone || null,
        leaveYearStartMonth: values.leaveYearStartMonth != null ? Number(values.leaveYearStartMonth) : null,
      });
      setProfile(saved || {});
      setPreview(saved?.logoUrl || null);
      await successMsg('Company profile saved', 'The header shows the change on the next page load.');
    } catch (err) {
      const status = err?.status || err?.response?.status;
      const message = err?.message || err?.response?.data?.message || '';
      if (status === 409) {
        if (/leave year/i.test(message)) {
          setFieldError('leaveYearStartMonth', message);
        } else if (/country/i.test(message)) {
          setFieldError('countryCode', message);
        } else {
          errorMsg(err);
        }
      } else {
        errorMsg(err);
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="saas-page-container fadeSlideUp" style={{ maxWidth: 900, margin: '0 auto' }}>
      <Card className="saas-card" variant="borderless">
        <PageHeader
          title="Company profile"
          subtitle={`What everyone in ${profile.name} sees in the header.`}
        />

        <Formik
          enableReinitialize
          initialValues={{
            tagline: profile.tagline || '',
            logoDocumentId: profile.logoDocumentId || null,
            countryCode: profile.countryCode || 'IN',
            timezone: profile.timezone || 'Asia/Kolkata',
            leaveYearStartMonth: profile.leaveYearStartMonth != null ? profile.leaveYearStartMonth : 4,
          }}
          validationSchema={validationSchema}
          onSubmit={handleSubmit}
        >
          {({ values, errors, touched, handleChange, handleBlur, isSubmitting, setFieldValue }) => {
            const countryTimezones = getTimezonesForCountry(values.countryCode);
            const tzOptions = (showAllTimezones || countryTimezones.length === 0 ? allTimezones : countryTimezones).map(
              (tz) => ({ value: tz, label: tz }),
            );

            return (
              <FormikForm>
                <Card
                  variant="borderless"
                  style={{
                    background: token.colorFillAlter,
                    border: `1px solid ${token.colorBorderSecondary}`,
                    borderRadius: 12,
                    padding: 8,
                  }}
                >
                  <Row gutter={[token.margin, token.margin]}>
                    <Col xs={24} md={12}>
                      <label htmlFor="company-name">
                        <Text strong>Company name</Text>
                      </label>
                      <Input
                        id="company-name"
                        value={profile.name || ''}
                        readOnly
                        disabled
                        style={{ marginTop: 4, borderRadius: 8 }}
                      />
                      <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                        Set when the company was created.
                      </Text>
                    </Col>

                    <Col xs={24} md={12}>
                      <label htmlFor="company-tagline">
                        <Text strong>Tagline</Text>
                      </label>
                      <Input
                        id="company-tagline"
                        name="tagline"
                        value={values.tagline}
                        onChange={handleChange}
                        onBlur={handleBlur}
                        maxLength={TAGLINE_MAX}
                        showCount
                        disabled={!canManage}
                        placeholder="A line under the company name, or nothing"
                        status={touched.tagline && errors.tagline ? 'error' : ''}
                        style={{ marginTop: 4, borderRadius: 8 }}
                      />
                      {touched.tagline && errors.tagline && <Text type="danger">{errors.tagline}</Text>}
                    </Col>

                    <Col xs={24} md={12}>
                      <label htmlFor="company-country">
                        <Text strong>Country</Text>
                      </label>
                      <Select
                        id="company-country"
                        data-testid="company-country-select"
                        value={values.countryCode}
                        onChange={(val) => {
                          setFieldValue('countryCode', val);
                          const common = getTimezonesForCountry(val);
                          if (common.length > 0 && !common.includes(values.timezone)) {
                            setFieldValue('timezone', common[0]);
                          }
                        }}
                        disabled={!canManage || profile.countryLocked}
                        showSearch
                        optionFilterProp="label"
                        options={
                          countries.length > 0
                            ? countries.map((c) => ({ value: c.code, label: `${c.name} (${c.code})` }))
                            : [{ value: values.countryCode || 'IN', label: values.countryCode || 'IN' }]
                        }
                        status={errors.countryCode ? 'error' : ''}
                        style={{ width: '100%', marginTop: 4 }}
                      />
                      {profile.countryLocked ? (
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM, display: 'block' }}>
                          Locked after the first pay run — contact support
                        </Text>
                      ) : (
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM, display: 'block' }}>
                          Editable until the first pay run is created.
                        </Text>
                      )}
                      {errors.countryCode && <Text type="danger">{errors.countryCode}</Text>}
                    </Col>

                    <Col xs={24} md={12}>
                      <label htmlFor="company-leave-year">
                        <Text strong>Leave year start month</Text>
                      </label>
                      <Select
                        id="company-leave-year"
                        data-testid="company-leave-year-select"
                        value={values.leaveYearStartMonth}
                        onChange={(val) => setFieldValue('leaveYearStartMonth', val)}
                        disabled={!canManage || profile.leaveYearLocked}
                        options={MONTH_OPTIONS}
                        status={errors.leaveYearStartMonth ? 'error' : ''}
                        style={{ width: '100%', marginTop: 4 }}
                      />
                      {profile.leaveYearLocked ? (
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM, display: 'block' }}>
                          Locked once leave has been allocated or requested — contact support
                        </Text>
                      ) : (
                        <Text type="secondary" style={{ fontSize: token.fontSizeSM, display: 'block' }}>
                          This cannot be changed once leave is allocated or requested.
                        </Text>
                      )}
                      {errors.leaveYearStartMonth && <Text type="danger">{errors.leaveYearStartMonth}</Text>}
                    </Col>

                    <Col xs={24} md={12}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <label htmlFor="company-timezone">
                          <Text strong>Timezone</Text>
                        </label>
                        <Checkbox
                          checked={showAllTimezones}
                          onChange={(e) => setShowAllTimezones(e.target.checked)}
                          disabled={!canManage}
                          style={{ fontSize: token.fontSizeSM }}
                        >
                          Show all timezones
                        </Checkbox>
                      </div>
                      <Select
                        id="company-timezone"
                        data-testid="company-timezone-select"
                        value={values.timezone}
                        onChange={(val) => setFieldValue('timezone', val)}
                        disabled={!canManage}
                        showSearch
                        optionFilterProp="label"
                        options={tzOptions}
                        status={touched.timezone && errors.timezone ? 'error' : ''}
                        style={{ width: '100%', marginTop: 4 }}
                      />
                      <Text type="secondary" style={{ fontSize: token.fontSizeSM, display: 'block' }}>
                        Attendance and leave dates use this timezone from now on.
                      </Text>
                      {touched.timezone && errors.timezone && <Text type="danger">{errors.timezone}</Text>}
                    </Col>

                    <Col xs={24}>
                      <Text strong>Logo</Text>
                      <div style={{ display: 'flex', alignItems: 'center', gap: token.margin, marginTop: token.marginXS }}>
                        {values.logoDocumentId && preview ? (
                          <img
                            src={preview}
                            alt="Company logo"
                            data-testid="logo-preview"
                            style={{ height: 64, maxWidth: 240, objectFit: 'contain' }}
                          />
                        ) : (
                          <Avatar
                            size={64}
                            data-testid="logo-initials"
                            style={{
                              background: 'linear-gradient(135deg, var(--brand-blue-hover) 0%, var(--brand-blue) 100%)',
                              color: token.colorTextLightSolid,
                              fontWeight: 700,
                              fontSize: 22,
                              boxShadow: '0 4px 12px rgba(37, 99, 235, 0.25)',
                            }}
                          >
                            {initialsOf(profile.name)}
                          </Avatar>
                        )}
                        {canManage && (
                          <Space>
                            <Upload
                              accept=".png,.jpg,.jpeg,image/png,image/jpeg"
                              showUploadList={false}
                              beforeUpload={(file) => chooseLogo(file, setFieldValue)}
                            >
                              <Button className="btn-spring" icon={<UploadOutlined />} loading={uploading}>
                                {values.logoDocumentId ? 'Replace logo' : 'Upload logo'}
                              </Button>
                            </Upload>
                            {values.logoDocumentId && (
                              <Button
                                className="btn-spring"
                                icon={<DeleteOutlined />}
                                onClick={() => {
                                  setFieldValue('logoDocumentId', null);
                                  setPreview(null);
                                }}
                              >
                                Remove logo
                              </Button>
                            )}
                          </Space>
                        )}
                      </div>
                      <Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                        PNG or JPG, at most 512 KB. Without a logo the header shows the company&apos;s initials.
                      </Text>
                    </Col>
                  </Row>

                  {canManage && (
                    <div style={{ marginTop: token.marginLG }}>
                      <Button
                        type="primary"
                        className="btn-spring"
                        htmlType="submit"
                        icon={<SaveOutlined />}
                        loading={isSubmitting}
                        disabled={uploading}
                      >
                        Save
                      </Button>
                    </div>
                  )}
                </Card>
              </FormikForm>
            );
          }}
        </Formik>
      </Card>
    </div>
  );
}
