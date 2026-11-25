import { useCallback, useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import { Card, Table, Button, Typography, message } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { formatDateTime, formatMoney } from '@shared/ui/format.js';
import { bankFileService, saveBlob } from './bankFileService.js';

const { Text } = Typography;

/** W-88.2: every bank file made for this run — when, by whom, template, rows, total — and Download again. */
export function BankFileHistory({ payrunId, refreshKey }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRows(await bankFileService.history(payrunId));
    } catch {
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [payrunId]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  const download = async (row) => {
    setDownloading(row.id);
    try {
      saveBlob(await bankFileService.download(payrunId, row.id), row.fileName);
    } catch (err) {
      message.error(err?.message || 'Failed to download the bank file');
    } finally {
      setDownloading(null);
    }
  };

  if (!loading && rows.length === 0) return null;

  return (
    <Card variant="borderless" title="Bank files" style={{ marginBottom: 24 }} data-testid="bank-file-history">
      <Table
        size="small"
        rowKey="id"
        loading={loading}
        dataSource={rows}
        pagination={false}
        columns={[
          { title: 'Made', key: 'createdAt', width: 190, render: (_, r) => formatDateTime(r.createdAt) },
          { title: 'By', dataIndex: 'createdBy', key: 'createdBy', ellipsis: true },
          { title: 'Template', dataIndex: 'templateName', key: 'templateName' },
          {
            title: 'Employees',
            key: 'rows',
            width: 140,
            render: (_, r) => (
              <span>
                {r.rowCount}
                {r.selectedEmployeeCount != null && <Text type="secondary"> (of {r.selectedEmployeeCount} ticked)</Text>}
              </span>
            ),
          },
          { title: 'Total', key: 'total', width: 140, align: 'right', render: (_, r) => formatMoney(r.fileTotal) },
          {
            title: '',
            key: 'download',
            width: 120,
            render: (_, r) => (
              <Button
                size="small"
                icon={<DownloadOutlined />}
                loading={downloading === r.id}
                onClick={() => download(r)}
                data-testid={`btn-bank-history-${r.id}`}
              >
                Download
              </Button>
            ),
          },
        ]}
      />
    </Card>
  );
}

BankFileHistory.propTypes = {
  payrunId: PropTypes.string.isRequired,
  refreshKey: PropTypes.number,
};
