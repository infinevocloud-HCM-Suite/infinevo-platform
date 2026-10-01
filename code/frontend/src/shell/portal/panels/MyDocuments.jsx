import { useEffect, useState } from 'react';
import { Card, Table, Tag, Skeleton, Result, Button, Space, Typography, theme } from 'antd';
import { FileTextOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { portalService } from '../portalService.js';

const { Text } = Typography;

export function MyDocuments() {
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const { token } = theme.useToken();

  const fetchDocuments = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await portalService.getDocuments();
      setDocuments(Array.isArray(data) ? data : data?.items || []);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Failed to load documents');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDocuments();
  }, []);

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
      title: 'Document Name',
      dataIndex: 'documentName',
      key: 'documentName',
      render: (text, record) => (
        <Space>
          <FileTextOutlined style={{ color: token.colorPrimary }} />
          <Text strong>{text || record.name || record.fileName || 'Untitled Document'}</Text>
        </Space>
      ),
    },
    {
      title: 'Category / Kind',
      dataIndex: 'documentKind',
      key: 'documentKind',
      render: (kind, record) => (
        <Tag color="blue">{kind || record.category || 'GENERAL'}</Tag>
      ),
    },
    {
      title: 'Uploaded At',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (date) => (date ? new Date(date).toLocaleDateString() : '—'),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (
        <Tag color="success" icon={<CheckCircleOutlined />}>
          {status || 'VERIFIED'}
        </Tag>
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
        rowKey={(d, index) => d.id || index}
        pagination={{ pageSize: 10 }}
        locale={{ emptyText: 'No personal documents uploaded yet.' }}
      />
    </Card>
  );
}

export default MyDocuments;
