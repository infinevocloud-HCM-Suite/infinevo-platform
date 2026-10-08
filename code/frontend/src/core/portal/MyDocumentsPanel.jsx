import { useCallback, useEffect, useState } from 'react';
import { Card, Table, Tag, Skeleton, Result, Button, Space, Typography, theme } from 'antd';
import { FileTextOutlined, DownloadOutlined } from '@ant-design/icons';
import { portalService } from '@shell/portal/portalService.js';
import { documentService, labelText } from '../document/documentService.js';
import { errorMsg } from '@shared/ui/msgHelper.js';

const { Text } = Typography;

/**
 * The employee's own documents on /me (W-73.5 §2): list over /me/documents, download only.
 */
export function MyDocumentsPanel() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { token } = theme.useToken();

  const fetchDocuments = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await portalService.getDocuments();
      setDocuments(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err?.message || 'Failed to load documents');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDocuments();
  }, [fetchDocuments]);

  const handleDownload = async (id) => {
    try {
      const { url } = await documentService.link(id);
      window.open(url, '_blank', 'noopener');
    } catch (err) {
      errorMsg(err);
    }
  };

  if (loading) {
    return (
      <Card data-testid="panel-documents-loading">
        <Skeleton active paragraph={{ rows: 5 }} />
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <Result
          status="error"
          title="Unable to Load Documents"
          subTitle={error}
          extra={
            <Button type="primary" onClick={fetchDocuments}>
              Retry
            </Button>
          }
        />
      </Card>
    );
  }

  const columns = [
    {
      title: 'Document',
      dataIndex: 'fileName',
      key: 'fileName',
      render: (text) => (
        <Space>
          <FileTextOutlined style={{ color: token.colorPrimary }} />
          <Text strong>{text || 'Untitled Document'}</Text>
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
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      render: (kind) => (kind ? <Tag>{kind}</Tag> : '—'),
    },
    {
      title: 'Uploaded',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date) => (date ? new Date(date).toLocaleDateString() : '—'),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, doc) => (
        <Button
          type="text"
          icon={<DownloadOutlined />}
          onClick={() => handleDownload(doc.id)}
          id={`btn-download-${doc.id}`}
        >
          Download
        </Button>
      ),
    },
  ];

  return (
    <Card
      title={
        <Space>
          <FileTextOutlined />
          <span>My Documents</span>
        </Space>
      }
      style={{ borderRadius: token.borderRadiusLG }}
      data-testid="panel-documents"
    >
      <Table
        dataSource={documents}
        columns={columns}
        rowKey="id"
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        locale={{ emptyText: 'No documents yet.' }}
      />
    </Card>
  );
}

export default MyDocumentsPanel;
