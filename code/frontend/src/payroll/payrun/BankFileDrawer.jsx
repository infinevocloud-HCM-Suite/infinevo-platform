import { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Select, Radio, Table, Input, Button, Space, Alert, Typography, Descriptions, Spin, Empty } from 'antd';
import { DownloadOutlined, EyeOutlined } from '@ant-design/icons';
import { formatMoney } from '@shared/ui/format.js';
import { payrunService } from './payrunService.js';
import { bankFileService, saveBlob } from './bankFileService.js';

const { Text } = Typography;

const empId = (r) => r.employee_id || r.employeeId;
const empNo = (r) => r.employee_number || r.employeeNumber || '';
const PAGE = 100; // the server's largest page

/** Every included employee of the run, a page of 100 at a time. */
async function allIncluded(payrunId) {
  const rows = [];
  for (let page = 0; page < 100; page += 1) {
    const data = await payrunService.employees(payrunId, { inclusion: 'INCLUDED', page, size: PAGE });
    const content = data?.content || [];
    rows.push(...content);
    if (data?.last !== false || content.length === 0) break;
  }
  return rows;
}

/**
 * W-88.2: make a bank file for this run from a template — everyone, or the ticked employees — preview it, then
 * generate. The file is stored on the run and downloads straight away.
 */
export function BankFileDrawer({ open, payrunId, onClose, onGenerated }) {
  const [templates, setTemplates] = useState([]);
  const [templateId, setTemplateId] = useState('generic-neft');
  const [scope, setScope] = useState('ALL');
  const [employees, setEmployees] = useState([]);
  const [names, setNames] = useState(new Map());
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState([]);
  const [loading, setLoading] = useState(false);
  const [preview, setPreview] = useState(null);
  const [previewing, setPreviewing] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setPreview(null);
    setError(null);
    setLoading(true);
    (async () => {
      try {
        const [list, rows] = await Promise.all([bankFileService.templates(), allIncluded(payrunId)]);
        if (cancelled) return;
        setTemplates(list);
        setEmployees(rows);
        const found = await payrunService.employeeNames(rows.map(empId));
        if (!cancelled) setNames(found instanceof Map ? found : new Map());
      } catch (err) {
        if (!cancelled) setError(err?.message || 'Failed to load templates or employees');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [open, payrunId]);

  const shown = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return employees;
    return employees.filter(
      (r) => empNo(r).toLowerCase().includes(q) || (names.get(empId(r)) || '').toLowerCase().includes(q),
    );
  }, [employees, names, search]);

  const body = () => ({ templateId, employeeIds: scope === 'SOME' ? selected : [] });
  const canAct = templateId && (scope === 'ALL' || selected.length > 0);

  const runPreview = async () => {
    setPreviewing(true);
    setError(null);
    try {
      setPreview(await bankFileService.preview(payrunId, body()));
    } catch (err) {
      setPreview(null);
      setError(err?.message || 'Failed to preview the bank file');
    } finally {
      setPreviewing(false);
    }
  };

  const generate = async () => {
    setGenerating(true);
    setError(null);
    try {
      const made = await bankFileService.generate(payrunId, body());
      const blob = await bankFileService.download(payrunId, made.id);
      saveBlob(blob, made.fileName);
      onGenerated?.(made);
      onClose();
    } catch (err) {
      setError(err?.message || 'Failed to make the bank file');
    } finally {
      setGenerating(false);
    }
  };

  const previewColumns = (preview?.headings || []).map((h, i) => ({
    title: h,
    key: `c${i}`,
    render: (_, row) => row[i],
  }));

  return (
    <Drawer
      title="Bank file"
      open={open}
      onClose={onClose}
      width={900}
      destroyOnClose
      extra={
        <Space>
          <Button icon={<EyeOutlined />} onClick={runPreview} loading={previewing} disabled={!canAct} data-testid="btn-bank-preview">
            Preview
          </Button>
          <Button
            type="primary"
            icon={<DownloadOutlined />}
            onClick={generate}
            loading={generating}
            disabled={!canAct}
            data-testid="btn-bank-generate"
          >
            Make file
          </Button>
        </Space>
      }
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      {loading ? (
        <Spin />
      ) : (
        <>
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div>
              <Text strong style={{ display: 'block', marginBottom: 6 }}>
                Template
              </Text>
              <Select
                aria-label="Template"
                value={templateId}
                style={{ width: 360 }}
                onChange={(value) => {
                  setTemplateId(value);
                  setPreview(null);
                }}
                options={templates.map((t) => ({ value: t.id, label: t.builtIn ? `${t.name} (built-in)` : t.name }))}
              />
            </div>
            <div>
              <Text strong style={{ display: 'block', marginBottom: 6 }}>
                Employees
              </Text>
              <Radio.Group
                value={scope}
                onChange={(e) => {
                  setScope(e.target.value);
                  setPreview(null);
                }}
                options={[
                  { value: 'ALL', label: `Everyone in this run (${employees.length})` },
                  { value: 'SOME', label: 'Only the ones I tick' },
                ]}
              />
            </div>
            {scope === 'SOME' && (
              <>
                <Input.Search
                  aria-label="Search employees"
                  placeholder="Search by number or name"
                  allowClear
                  onChange={(e) => setSearch(e.target.value)}
                  style={{ width: 320 }}
                />
                <Table
                  size="small"
                  rowKey={empId}
                  dataSource={shown}
                  pagination={{ pageSize: 10 }}
                  rowSelection={{
                    selectedRowKeys: selected,
                    preserveSelectedRowKeys: true,
                    onChange: (keys) => {
                      setSelected(keys);
                      setPreview(null);
                    },
                  }}
                  columns={[
                    { title: 'No.', key: 'no', width: 120, render: (_, r) => empNo(r) },
                    { title: 'Name', key: 'name', render: (_, r) => names.get(empId(r)) || '—' },
                    {
                      title: 'Net pay',
                      key: 'net',
                      width: 140,
                      align: 'right',
                      render: (_, r) => formatMoney(r.net_pay ?? r.netPay),
                    },
                  ]}
                  data-testid="bank-employee-table"
                />
                <Text type="secondary">{selected.length} ticked</Text>
              </>
            )}
          </Space>

          {preview && (
            <div style={{ marginTop: 24 }} data-testid="bank-preview">
              <Descriptions size="small" column={3} bordered style={{ marginBottom: 12 }}>
                <Descriptions.Item label="Rows in file">{preview.rowCount}</Descriptions.Item>
                <Descriptions.Item label="File total">{formatMoney(preview.fileTotal)}</Descriptions.Item>
                <Descriptions.Item label={scope === 'SOME' ? 'Ticked net pay' : 'Run net pay'}>
                  {formatMoney(preview.selectedNetPay)}
                </Descriptions.Item>
              </Descriptions>
              {preview.omitted?.length > 0 && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={`${preview.omitted.length} employee(s) left out — pay them another way or fix their bank details`}
                  description={
                    <ul style={{ margin: 0, paddingLeft: 18 }} data-testid="bank-omitted">
                      {preview.omitted.map((o) => (
                        <li key={o.employeeNumber}>
                          {o.employeeNumber}: {o.reason}
                        </li>
                      ))}
                    </ul>
                  }
                />
              )}
              {preview.rowCount === 0 ? (
                <Empty description="No one in this selection can be paid by bank transfer" />
              ) : (
                <Table
                  size="small"
                  rowKey={(_, i) => `p${i}`}
                  dataSource={preview.rows}
                  columns={previewColumns}
                  pagination={false}
                  scroll={{ x: true }}
                  footer={() =>
                    preview.rowCount > preview.rows.length ? `First ${preview.rows.length} of ${preview.rowCount} rows` : null
                  }
                />
              )}
            </div>
          )}
        </>
      )}
    </Drawer>
  );
}

BankFileDrawer.propTypes = {
  open: PropTypes.bool,
  payrunId: PropTypes.string.isRequired,
  onClose: PropTypes.func.isRequired,
  onGenerated: PropTypes.func,
};
