import { useEffect, useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Drawer, Form, Input, Radio, Switch, Button, Space, Table, Select, Alert, Typography, Tooltip } from 'antd';
import { ArrowUpOutlined, ArrowDownOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';

const { Text } = Typography;

/** Made-up values so the officer sees the layout before saving (W-88.2 spec section 5). */
const SAMPLE = {
  EMPLOYEE_NUMBER: 'E-101',
  EMPLOYEE_NAME: 'Asha Rao',
  FIRST_NAME: 'Asha',
  LAST_NAME: 'Rao',
  ACCOUNT_HOLDER_NAME: 'ASHA RAO',
  ACCOUNT_NUMBER: '000123456789',
  IFSC: 'HDFC0001234',
  BANK_NAME: 'HDFC Bank',
  ACCOUNT_TYPE: 'SAVINGS',
  WORK_EMAIL: 'asha@company.in',
  MOBILE: '9876543210',
  DEPARTMENT: 'Engineering',
  DESIGNATION: 'Engineer',
  NARRATION: 'Salary 2026-11',
};

const AMOUNT_SAMPLE = { DECIMAL_2: '65000.50', WHOLE_RUPEES: '65001', PAISE: '6500050' };
const DATE_SAMPLE = {
  'dd/MM/yyyy': '30/11/2026',
  'dd-MM-yyyy': '30-11-2026',
  'yyyy-MM-dd': '2026-11-30',
  ddMMyyyy: '30112026',
  'MM/dd/yyyy': '11/30/2026',
};
const PERIOD_SAMPLE = { 'yyyy-MM': '2026-11', 'MMM yyyy': 'Nov 2026', 'MM/yyyy': '11/2026' };

let nextKey = 0;
const withKey = (column) => ({ ...column, key: `col-${nextKey++}` });

/** The sample cell for one column, given the catalogue. */
export function sampleValue(column, fieldsByCode) {
  if (column.source === 'SERIAL') return '1';
  if (column.source === 'FIXED') return column.value || '';
  const field = fieldsByCode[column.field];
  if (!field) return '';
  const format = column.format || field.formats?.[0];
  if (field.kind === 'AMOUNT') return AMOUNT_SAMPLE[format] || '';
  if (field.kind === 'DATE') return DATE_SAMPLE[format] || '';
  if (field.kind === 'PERIOD') return PERIOD_SAMPLE[format] || '';
  return SAMPLE[column.field] || '';
}

/** The request body the API takes, from the editor state. */
export function toRequest(state) {
  return {
    name: state.name?.trim(),
    fileFormat: state.fileFormat,
    includeHeader: state.includeHeader,
    columns: state.columns.map(({ heading, source, field, value, format }) => ({
      heading: heading?.trim(),
      source,
      field: source === 'FIELD' ? field || null : null,
      value: source === 'FIXED' ? value || '' : null,
      format: source === 'FIELD' ? format || null : null,
    })),
  };
}

/**
 * W-88.2: name a template, choose CSV or Excel and the header row, and lay out its columns — each a field from
 * the catalogue, a fixed text or a serial number, with a heading and, for amounts and dates, a format.
 */
export function BankFileTemplateEditor({ open, template, fields, saving, error, onSave, onClose }) {
  const [state, setState] = useState(null);

  useEffect(() => {
    if (!open) return;
    setState({
      name: template?.name || '',
      fileFormat: template?.fileFormat || 'CSV',
      includeHeader: template ? template.includeHeader !== false : true,
      columns: (template?.columns?.length
        ? template.columns
        : [{ heading: 'Account Number', source: 'FIELD', field: 'ACCOUNT_NUMBER' }]
      ).map(withKey),
    });
  }, [open, template]);

  const fieldsByCode = useMemo(() => Object.fromEntries((fields || []).map((f) => [f.code, f])), [fields]);

  if (!state) return null;

  const setColumn = (index, patch) =>
    setState((s) => ({ ...s, columns: s.columns.map((c, i) => (i === index ? { ...c, ...patch } : c)) }));
  const move = (index, delta) =>
    setState((s) => {
      const columns = [...s.columns];
      const target = index + delta;
      if (target < 0 || target >= columns.length) return s;
      [columns[index], columns[target]] = [columns[target], columns[index]];
      return { ...s, columns };
    });
  const remove = (index) => setState((s) => ({ ...s, columns: s.columns.filter((_, i) => i !== index) }));
  const add = () =>
    setState((s) => ({
      ...s,
      columns: [...s.columns, withKey({ heading: `Column ${s.columns.length + 1}`, source: 'FIELD', field: null })],
    }));

  const columns = [
    {
      title: 'Heading',
      key: 'heading',
      width: 170,
      render: (_, c, i) => (
        <Input
          aria-label={`Heading ${i + 1}`}
          value={c.heading}
          maxLength={60}
          onChange={(e) => setColumn(i, { heading: e.target.value })}
        />
      ),
    },
    {
      title: 'Fills with',
      key: 'source',
      width: 130,
      render: (_, c, i) => (
        <Select
          aria-label={`Source ${i + 1}`}
          value={c.source}
          style={{ width: '100%' }}
          options={[
            { value: 'FIELD', label: 'A field' },
            { value: 'FIXED', label: 'Fixed text' },
            { value: 'SERIAL', label: 'Serial no.' },
          ]}
          onChange={(source) => setColumn(i, { source, field: null, value: '', format: null })}
        />
      ),
    },
    {
      title: 'Field or text',
      key: 'what',
      render: (_, c, i) => {
        if (c.source === 'SERIAL') return <Text type="secondary">1, 2, 3 …</Text>;
        if (c.source === 'FIXED') {
          return (
            <Input
              aria-label={`Text ${i + 1}`}
              value={c.value}
              maxLength={100}
              placeholder="e.g. NEFT"
              onChange={(e) => setColumn(i, { value: e.target.value })}
            />
          );
        }
        return (
          <Select
            aria-label={`Field ${i + 1}`}
            value={c.field || undefined}
            placeholder="Choose a field"
            style={{ width: '100%' }}
            showSearch
            optionFilterProp="label"
            options={(fields || []).map((f) => ({ value: f.code, label: f.label }))}
            onChange={(field) => setColumn(i, { field, format: null })}
          />
        );
      },
    },
    {
      title: 'Format',
      key: 'format',
      width: 140,
      render: (_, c, i) => {
        const formats = c.source === 'FIELD' ? fieldsByCode[c.field]?.formats || [] : [];
        if (formats.length === 0) return <Text type="secondary">—</Text>;
        return (
          <Select
            aria-label={`Format ${i + 1}`}
            value={c.format || formats[0]}
            style={{ width: '100%' }}
            options={formats.map((f) => ({ value: f, label: f }))}
            onChange={(format) => setColumn(i, { format })}
          />
        );
      },
    },
    {
      title: 'Sample',
      key: 'sample',
      width: 130,
      render: (_, c) => <Text code>{sampleValue(c, fieldsByCode) || ' '}</Text>,
    },
    {
      title: '',
      key: 'actions',
      width: 110,
      render: (_, c, i) => (
        <Space size={2}>
          <Tooltip title="Move up">
            <Button size="small" icon={<ArrowUpOutlined />} disabled={i === 0} onClick={() => move(i, -1)} aria-label={`Move ${i + 1} up`} />
          </Tooltip>
          <Tooltip title="Move down">
            <Button
              size="small"
              icon={<ArrowDownOutlined />}
              disabled={i === state.columns.length - 1}
              onClick={() => move(i, 1)}
              aria-label={`Move ${i + 1} down`}
            />
          </Tooltip>
          <Tooltip title="Remove">
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              disabled={state.columns.length === 1}
              onClick={() => remove(i)}
              aria-label={`Remove ${i + 1}`}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      title={template?.id ? `Edit ${template.name}` : 'New bank file template'}
      open={open}
      onClose={onClose}
      width={980}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" loading={saving} onClick={() => onSave(toRequest(state))} data-testid="btn-save-template">
            Save
          </Button>
        </Space>
      }
    >
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      <Form layout="vertical">
        <Form.Item label="Template name" required>
          <Input
            aria-label="Template name"
            value={state.name}
            maxLength={80}
            placeholder="e.g. HDFC bulk upload"
            onChange={(e) => setState((s) => ({ ...s, name: e.target.value }))}
          />
        </Form.Item>
        <Space size={32} style={{ marginBottom: 16 }}>
          <Form.Item label="File type" style={{ marginBottom: 0 }}>
            <Radio.Group
              value={state.fileFormat}
              onChange={(e) => setState((s) => ({ ...s, fileFormat: e.target.value }))}
              options={[
                { value: 'CSV', label: 'CSV' },
                { value: 'XLSX', label: 'Excel (.xlsx)' },
              ]}
            />
          </Form.Item>
          <Form.Item label="Heading row" style={{ marginBottom: 0 }}>
            <Switch
              aria-label="Heading row"
              checked={state.includeHeader}
              onChange={(includeHeader) => setState((s) => ({ ...s, includeHeader }))}
            />
          </Form.Item>
        </Space>
      </Form>
      <Table
        size="small"
        rowKey="key"
        pagination={false}
        dataSource={state.columns}
        columns={columns}
        data-testid="template-columns"
      />
      <Button icon={<PlusOutlined />} onClick={add} disabled={state.columns.length >= 40} style={{ marginTop: 12 }} data-testid="btn-add-column">
        Add column
      </Button>
    </Drawer>
  );
}

BankFileTemplateEditor.propTypes = {
  open: PropTypes.bool,
  template: PropTypes.object,
  fields: PropTypes.arrayOf(PropTypes.object),
  saving: PropTypes.bool,
  error: PropTypes.string,
  onSave: PropTypes.func.isRequired,
  onClose: PropTypes.func.isRequired,
};
