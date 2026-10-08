import { useEffect, useState } from 'react';
import { Formik, Form as FormikForm } from 'formik';
import * as Yup from 'yup';
import { Card, Row, Col, Input, Button, Typography, Space, Spin, Upload, Avatar, theme } from 'antd';
import { SaveOutlined, UploadOutlined, DeleteOutlined } from '@ant-design/icons';
import { useCan, NotEntitled } from '@shell/screens';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';
import { companyProfileService } from './companyProfileService.js';
import { documentService } from '../document/documentService.js';
import { logoFileProblem, resizeLogo } from './logoImage.js';

const { Title, Text } = Typography;

const TAGLINE_MAX = 80;

const validationSchema = Yup.object().shape({
  tagline: Yup.string().trim().max(TAGLINE_MAX, `Tagline must be at most ${TAGLINE_MAX} characters`),
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
 * Company profile (W-73.1 §5) at `/settings/company`: the company name (read-only - provisioning
 * set it), the logo the header shows, and the tagline under the name.
 *
 * The logo goes to the document store first (`POST /api/v1/documents`, kind `TENANT_LOGO`) and
 * the profile then names the stored document; Save writes both fields. The header reads the
 * profile through the navigation feed, so it shows the change on the next page load.
 */
export function CompanyProfile() {
  const canRead = useCan('core.tenant.read');
  const canManage = useCan('core.tenant.manage');
  const { token } = theme.useToken();

  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState(null);
  const [uploading, setUploading] = useState(false);
  // What the preview shows for the chosen logo: the server's signed link, or the chosen file's own
  // object URL until it is saved.
  const [preview, setPreview] = useState(null);

  useEffect(() => {
    if (!canRead) return undefined;
    let active = true;
    setLoading(true);
    companyProfileService
      .get()
      .then((data) => {
        if (!active) return;
        setProfile(data || {});
        setPreview(data?.logoUrl || null);
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

  const handleSubmit = async (values, { setSubmitting }) => {
    try {
      const saved = await companyProfileService.update({
        tagline: values.tagline ? values.tagline.trim() : null,
        logoDocumentId: values.logoDocumentId || null,
      });
      setProfile(saved || {});
      setPreview(saved?.logoUrl || null);
      await successMsg('Company profile saved', 'The header shows the change on the next page load.');
    } catch (err) {
      errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <div style={{ marginBottom: token.marginLG }}>
        <Title level={3} style={{ margin: 0 }}>
          Company profile
        </Title>
        <Text type="secondary">What everyone in {profile.name} sees in the header.</Text>
      </div>

      <Formik
        enableReinitialize
        initialValues={{
          tagline: profile.tagline || '',
          logoDocumentId: profile.logoDocumentId || null,
        }}
        validationSchema={validationSchema}
        onSubmit={handleSubmit}
      >
        {({ values, errors, touched, handleChange, handleBlur, isSubmitting, setFieldValue }) => (
          <FormikForm>
            <Card>
              <Row gutter={[token.margin, token.margin]}>
                <Col xs={24} md={12}>
                  <label htmlFor="company-name">
                    <Text strong>Company name</Text>
                  </label>
                  <Input id="company-name" value={profile.name || ''} readOnly disabled style={{ marginTop: 4 }} />
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
                    style={{ marginTop: 4 }}
                  />
                  {touched.tagline && errors.tagline && <Text type="danger">{errors.tagline}</Text>}
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
                        style={{ background: token.colorPrimary, color: token.colorBgContainer, fontWeight: 600 }}
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
                          <Button icon={<UploadOutlined />} loading={uploading}>
                            {values.logoDocumentId ? 'Replace logo' : 'Upload logo'}
                          </Button>
                        </Upload>
                        {values.logoDocumentId && (
                          <Button
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
        )}
      </Formik>
    </div>
  );
}
