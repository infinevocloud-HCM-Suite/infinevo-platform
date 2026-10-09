import { useEffect, useState, useCallback } from 'react';
import PropTypes from 'prop-types';
import { Formik } from 'formik';
import * as Yup from 'yup';
import {
  Table,
  Button,
  Drawer,
  Modal,
  Select,
  Upload,
  Tag,
  Typography,
  Space,
  Card,
  theme,
} from 'antd';
import {
  FileTextOutlined,
  UploadOutlined,
  DownloadOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import { useCan } from '@shell/screens';
import {
  documentService,
  DOCUMENT_LABELS,
  labelText,
  MAX_DOCUMENT_BYTES,
  ACCEPTED_DOCUMENT_TYPES,
} from '../../document/documentService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Title, Text } = Typography;

const ACCEPTED_NAME = /\.(pdf|png|jpe?g)$/i;

const uploadSchema = Yup.object({
  label: Yup.string().nullable().required('Choose a label'),
  file: Yup.mixed()
    .nullable()
    .required('Choose a file')
    .test('size', 'The file is larger than 10 MB', (f) => !f || f.size <= MAX_DOCUMENT_BYTES)
    .test('type', 'Only PDF, PNG or JPG files are accepted', (f) => !f || ACCEPTED_NAME.test(f.name || '')),
});

/** Bytes as KB or MB with one decimal. */
export function formatSize(bytes) {
  if (bytes == null) return '—';
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Documents tab on the employee page (W-73.5 §2, §5): the employee's EMPLOYEE_DOCUMENT rows,
 * an Upload drawer with a label, a signed-link Download and a confirmed Delete.
 */
export function DocumentsTab({ employeeId }) {
  const { token } = theme.useToken();
  const canUpload = useCan('core.document.upload');
  const canUpdate = useCan('core.employee.update');
  const canDeleteDoc = useCan('core.document.delete');
  // Spec §2 gates Delete on core.employee.update; the server guard is core.document.delete.
  const canDelete = canUpdate && canDeleteDoc;

  const [loading, setLoading] = useState(false);
  const [documents, setDocuments] = useState([]);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    try {
      const data = await documentService.listForEmployee(employeeId);
      setDocuments(Array.isArray(data) ? data : []);
    } catch (err) {
      errorMsg(err);
    } finally {
      setLoading(false);
    }
  }, [employeeId]);

  useEffect(() => {
    loadDocuments();
  }, [loadDocuments]);

  const handleDownload = async (id) => {
    try {
      const { url } = await documentService.link(id);
      window.open(url, '_blank', 'noopener');
    } catch (err) {
      errorMsg(err);
    }
  };

  const handleDelete = (doc) => {
    Modal.confirm({
      title: 'Delete document',
      content: `Delete ${doc.fileName}? The file is kept in storage but no longer listed.`,
      okText: 'Delete',
      okType: 'danger',
      onOk: async () => {
        try {
          await documentService.remove(doc.id);
          await successMsg('Document deleted', `${doc.fileName} is no longer listed.`);
          loadDocuments();
        } catch (err) {
          await errorMsg(err);
        }
      },
    });
  };

  const handleUpload = async (values, { setSubmitting }) => {
    try {
      await documentService.uploadForEmployee(values.file, employeeId, values.label);
      setDrawerOpen(false);
      await successMsg('Document uploaded', `${values.file.name} filed as ${labelText(values.label)}.`);
      loadDocuments();
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      title: 'File',
      dataIndex: 'fileName',
      key: 'fileName',
      render: (name) => (
        <Space size="small">
          <FileTextOutlined style={{ color: token.colorPrimary }} />
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: 'Label',
      dataIndex: 'label',
      key: 'label',
      render: (label) => (label ? <Tag color="blue">{labelText(label)}</Tag> : '—'),
    },
    {
      title: 'Size',
      dataIndex: 'sizeBytes',
      key: 'sizeBytes',
      render: (bytes) => formatSize(bytes),
    },
    {
      title: 'Uploaded by',
      dataIndex: 'uploadedBy',
      key: 'uploadedBy',
      render: (who) => who || '—',
    },
    {
      title: 'Uploaded at',
      dataIndex: 'uploadedAt',
      key: 'uploadedAt',
      render: (at) => (at ? new Date(at).toLocaleString() : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, doc) => (
        <Space size="small">
          <Button
            type="text"
            icon={<DownloadOutlined />}
            onClick={() => handleDownload(doc.id)}
            id={`btn-download-${doc.id}`}
          >
            Download
          </Button>
          {canDelete && (
            <Button
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(doc)}
              id={`btn-delete-${doc.id}`}
            >
              Delete
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card variant="borderless" style={{ padding: 0 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: token.marginSM }}>
        <Title level={5} style={{ margin: 0 }}>Documents</Title>
        {canUpload && (
          <Button
            type="primary"
            icon={<UploadOutlined />}
            onClick={() => setDrawerOpen(true)}
            id="btn-upload-document"
          >
            Upload
          </Button>
        )}
      </div>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={documents}
        loading={loading}
        pagination={false}
        locale={{ emptyText: 'No documents uploaded yet.' }}
      />

      <Drawer
        title="Upload document"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={420}
        destroyOnHidden
      >
        <Formik
          initialValues={{ label: null, file: null }}
          validationSchema={uploadSchema}
          onSubmit={handleUpload}
        >
          {({ values, errors, touched, submitCount, isSubmitting, setFieldValue, setFieldTouched, handleSubmit }) => {
            const showError = (name) => (touched[name] || submitCount > 0) && errors[name];
            return (
              <form onSubmit={handleSubmit} noValidate>
                <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                  <div>
                    <label htmlFor="select-document-label" style={{ display: 'block', marginBottom: 4 }}>
                      <Text strong>Label *</Text>
                    </label>
                    <Select
                      id="select-document-label"
                      style={{ width: '100%' }}
                      placeholder="Choose a label"
                      value={values.label}
                      options={DOCUMENT_LABELS}
                      onChange={(val) => setFieldValue('label', val)}
                      onBlur={() => setFieldTouched('label', true)}
                    />
                    {showError('label') && (
                      <Text type="danger" id="error-document-label">{errors.label}</Text>
                    )}
                  </div>

                  <div>
                    <Text strong style={{ display: 'block', marginBottom: 4 }}>File *</Text>
                    <Upload
                      beforeUpload={() => false}
                      maxCount={1}
                      accept={ACCEPTED_DOCUMENT_TYPES}
                      fileList={values.file ? [{ uid: values.file.uid || 'file', name: values.file.name, status: 'done' }] : []}
                      onChange={({ fileList }) => {
                        const last = fileList[fileList.length - 1];
                        setFieldValue('file', last ? last.originFileObj || last : null, true);
                        setFieldTouched('file', true, false);
                      }}
                    >
                      <Button icon={<UploadOutlined />} id="btn-choose-file">Choose file</Button>
                    </Upload>
                    <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
                      PDF, PNG or JPG, up to 10 MB.
                    </Text>
                    {showError('file') && (
                      <Text type="danger" id="error-document-file">{errors.file}</Text>
                    )}
                  </div>

                  <Button
                    type="primary"
                    htmlType="submit"
                    loading={isSubmitting}
                    id="btn-submit-upload"
                  >
                    Upload
                  </Button>
                </Space>
              </form>
            );
          }}
        </Formik>
      </Drawer>
    </Card>
  );
}

DocumentsTab.propTypes = {
  employeeId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
};
