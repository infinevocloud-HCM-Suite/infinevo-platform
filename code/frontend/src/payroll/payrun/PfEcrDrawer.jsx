import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Drawer,
  Card,
  Row,
  Col,
  Table,
  Button,
  Space,
  Alert,
  Typography,
  Spin,
  Tag,
  Descriptions,
  theme,
} from 'antd';
import {
  DownloadOutlined,
  SafetyCertificateOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { formatMoney } from '@shared/ui/format.js';
import { saveBlob } from './bankFileService.js';
import { pfEcrService } from './pfEcrService.js';

const { Title, Text } = Typography;

export function PfEcrDrawer({ open, payrunId, runPeriod, onClose }) {
  const { token } = theme.useToken();
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [error, setError] = useState(null);
  const [preview, setPreview] = useState(null);

  useEffect(() => {
    if (!open || !payrunId) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    setPreview(null);

    (async () => {
      try {
        const data = await pfEcrService.getPreview(payrunId);
        if (!cancelled) {
          setPreview(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err?.response?.data?.message || err?.message || 'Failed to load PF ECR preview');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [open, payrunId]);

  const handleDownload = async (format) => {
    setDownloading(true);
    setError(null);
    try {
      const blob = await pfEcrService.downloadFile(payrunId, format);
      const estCode = preview?.establishmentCode || 'EST';
      const periodStr = (runPeriod || 'period').replace('-', '_');
      const ext = format === 'TXT' ? 'txt' : format === 'XLSX' ? 'xlsx' : 'csv';
      const fileName = `ECR_${estCode}_${periodStr}.${ext}`;
      saveBlob(blob, fileName);
    } catch (err) {
      setError(err?.response?.data?.message || err?.message || `Failed to export ECR ${format} file`);
    } finally {
      setDownloading(false);
    }
  };

  const columns = [
    {
      title: 'UAN',
      dataIndex: 'uan',
      key: 'uan',
      render: (uan) => (
        <Text copyable={Boolean(uan)} code={Boolean(uan)}>
          {uan || <Tag color="error">Missing</Tag>}
        </Text>
      ),
    },
    {
      title: 'Member Name',
      dataIndex: 'memberName',
      key: 'memberName',
      render: (name) => <Text strong>{name}</Text>,
    },
    {
      title: 'Gross Wages',
      dataIndex: 'grossWages',
      key: 'grossWages',
      align: 'right',
      render: (val) => formatMoney(val),
    },
    {
      title: 'EPF Wages',
      dataIndex: 'epfWages',
      key: 'epfWages',
      align: 'right',
      render: (val) => formatMoney(val),
    },
    {
      title: 'EPS Wages',
      dataIndex: 'epsWages',
      key: 'epsWages',
      align: 'right',
      render: (val) => formatMoney(val),
    },
    {
      title: 'EE Share (12%)',
      dataIndex: 'eeShare',
      key: 'eeShare',
      align: 'right',
      render: (val) => formatMoney(val),
    },
    {
      title: 'EPS Share (8.33%)',
      dataIndex: 'epsShare',
      key: 'epsShare',
      align: 'right',
      render: (val) => formatMoney(val),
    },
    {
      title: 'ER Share (3.67%)',
      dataIndex: 'erShare',
      key: 'erShare',
      align: 'right',
      render: (val) => formatMoney(val),
    },
    {
      title: 'NCP Days',
      dataIndex: 'ncpDays',
      key: 'ncpDays',
      align: 'center',
      render: (val) => val ?? 0,
    },
  ];

  const warnings = preview?.warnings || [];

  return (
    <Drawer
      title={
        <Space orientation="horizontal" size="middle">
          <SafetyCertificateOutlined style={{ color: token.colorPrimary, fontSize: 20 }} />
          <span>PF Electronic Challan cum Return (ECR 2.0)</span>
          {runPeriod && <Tag color="blue">{runPeriod}</Tag>}
        </Space>
      }
      open={open}
      onClose={onClose}
      width={980}
      destroyOnClose
      extra={
        <Space orientation="horizontal">
          <Button
            icon={<FileTextOutlined />}
            type="primary"
            onClick={() => handleDownload('TXT')}
            loading={downloading}
            disabled={loading || !preview?.rows?.length}
            data-testid="ecr-download-txt-btn"
          >
            Download ECR File (.txt)
          </Button>
          <Button
            icon={<FileExcelOutlined />}
            onClick={() => handleDownload('XLSX')}
            loading={downloading}
            disabled={loading || !preview?.rows?.length}
            data-testid="ecr-download-xlsx-btn"
          >
            Export Excel
          </Button>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => handleDownload('CSV')}
            loading={downloading}
            disabled={loading || !preview?.rows?.length}
            data-testid="ecr-download-csv-btn"
          >
            Export CSV
          </Button>
        </Space>
      }
    >
      {error && (
        <Alert
          type="error"
          message={error}
          showIcon
          closable
          style={{ marginBottom: 16 }}
        />
      )}

      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin size="large" />
        </div>
      ) : preview ? (
        <Space orientation="vertical" size="large" style={{ width: '100%' }}>
          {/* Top Summary Cards */}
          <Row gutter={[16, 16]}>
            <Col span={6}>
              <Card size="small" variant="borderless" style={{ background: token.colorFillAlter }}>
                <Text type="secondary">Total Members</Text>
                <Title level={3} style={{ margin: '4px 0 0' }}>
                  {preview.totalMembers}
                </Title>
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small" variant="borderless" style={{ background: token.colorFillAlter }}>
                <Text type="secondary">Total EPF Wages</Text>
                <Title level={3} style={{ margin: '4px 0 0' }}>
                  {formatMoney(preview.totalEpfWages)}
                </Title>
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small" variant="borderless" style={{ background: token.colorFillAlter }}>
                <Text type="secondary">Total EPS Wages</Text>
                <Title level={3} style={{ margin: '4px 0 0' }}>
                  {formatMoney(preview.totalEpsWages)}
                </Title>
              </Card>
            </Col>
            <Col span={6}>
              <Card
                size="small"
                variant="borderless"
                style={{ background: token.colorInfoBg, border: `1px solid ${token.colorInfoBorder}` }}
              >
                <Text type="secondary" style={{ color: token.colorInfoText }}>Grand Total Challan</Text>
                <Title level={3} style={{ margin: '4px 0 0', color: token.colorInfoText }}>
                  {formatMoney(preview.grandTotalChallan)}
                </Title>
              </Card>
            </Col>
          </Row>

          {/* Account Breakdown Summary */}
          <Card size="small" title="EPFO Account Breakdown (Challan Schedule)">
            <Descriptions size="small" column={{ xs: 1, sm: 2, md: 5 }} bordered>
              <Descriptions.Item label="A/C 01 (EPF EE + ER)">
                {formatMoney(preview.totalEeShare + preview.totalErShare)}
              </Descriptions.Item>
              <Descriptions.Item label="A/C 02 (Admin 0.50%)">
                {formatMoney(preview.totalAdminCharges)}
              </Descriptions.Item>
              <Descriptions.Item label="A/C 10 (EPS Pension)">
                {formatMoney(preview.totalEpsShare)}
              </Descriptions.Item>
              <Descriptions.Item label="A/C 21 (EDLI 0.50%)">
                {formatMoney(preview.totalEdliCharges)}
              </Descriptions.Item>
              <Descriptions.Item label="Establishment Code">
                <Tag color="cyan">{preview.establishmentCode || 'DEFAULT'}</Tag>
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {/* Validation Warnings */}
          {warnings.length > 0 && (
            <Alert
              type="warning"
              icon={<WarningOutlined />}
              showIcon
              message={`Pre-filing Validation Warnings (${warnings.length} issues)`}
              description={
                <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
                  {warnings.map((w, idx) => (
                    <li key={idx}>
                      <Text strong>{w.employeeName || w.employeeNumber || 'Employee'}</Text>: {w.message}
                    </li>
                  ))}
                </ul>
              }
            />
          )}

          {/* Data Table */}
          <Table
            dataSource={preview.rows || []}
            columns={columns}
            rowKey={(r) => r.uan || r.memberName}
            pagination={{ pageSize: 10, showSizeChanger: true }}
            size="small"
            scroll={{ x: 900 }}
          />
        </Space>
      ) : null}
    </Drawer>
  );
}

PfEcrDrawer.propTypes = {
  open: PropTypes.bool.isRequired,
  payrunId: PropTypes.string,
  runPeriod: PropTypes.string,
  onClose: PropTypes.func.isRequired,
};

