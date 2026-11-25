import { useCallback, useEffect, useState } from 'react';
import { Card, Table, Button, Space, Tag, Popconfirm, Alert, Typography, message, theme } from 'antd';
import { BankOutlined, CopyOutlined, EditOutlined, DeleteOutlined, LockOutlined, PlusOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { formatDateTime } from '@shared/ui/format.js';
import { bankFileService } from '../payrun/bankFileService.js';
import { BankFileTemplateEditor } from './BankFileTemplateEditor.jsx';

const { Text } = Typography;

/**
 * Payroll › Settings › Bank files (W-88.2): the built-in layout and the company's own. Changing a template needs
 * payroll.settings.manage; the built-in can only be copied.
 */
export function BankFileTemplates() {
  const { token } = theme.useToken();
  const canManage = useCan('payroll.settings.manage');
  const [templates, setTemplates] = useState([]);
  const [fields, setFields] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [list, catalogue] = await Promise.all([bankFileService.templates(), bankFileService.fields()]);
      setTemplates(list);
      setFields(catalogue);
    } catch (err) {
      setLoadError(err?.message || 'Failed to load bank file templates');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openEditor = (template) => {
    setEditing(template);
    setSaveError(null);
    setEditorOpen(true);
  };

  const save = async (body) => {
    setSaving(true);
    setSaveError(null);
    try {
      if (editing?.id) {
        await bankFileService.update(editing.id, body);
      } else {
        await bankFileService.create(body);
      }
      message.success('Template saved');
      setEditorOpen(false);
      load();
    } catch (err) {
      setSaveError(err?.message || 'Failed to save the template');
    } finally {
      setSaving(false);
    }
  };

  const copy = async (template) => {
    try {
      const created = await bankFileService.copy(template.id);
      message.success(`Saved as ${created?.name || 'a copy'}`);
      load();
    } catch (err) {
      message.error(err?.message || 'Failed to copy the template');
    }
  };

  const remove = async (template) => {
    try {
      await bankFileService.remove(template.id);
      message.success('Template deleted');
      load();
    } catch (err) {
      message.error(err?.message || 'Failed to delete the template');
    }
  };

  const columns = [
    {
      title: 'Template',
      key: 'name',
      render: (_, t) => (
        <Space>
          <Text strong>{t.name}</Text>
          {t.builtIn && (
            <Tag icon={<LockOutlined />} data-testid={`builtin-${t.id}`}>
              Built-in
            </Tag>
          )}
        </Space>
      ),
    },
    { title: 'File', dataIndex: 'fileFormat', key: 'fileFormat', width: 90, render: (f) => (f === 'XLSX' ? 'Excel' : 'CSV') },
    { title: 'Columns', key: 'columns', width: 90, render: (_, t) => t.columns?.length ?? 0 },
    {
      title: 'Updated',
      key: 'updatedAt',
      width: 200,
      render: (_, t) => (t.builtIn ? '—' : `${formatDateTime(t.updatedAt)}${t.updatedBy ? ` · ${t.updatedBy}` : ''}`),
    },
    {
      title: '',
      key: 'actions',
      width: 230,
      render: (_, t) =>
        canManage ? (
          <Space size="small">
            {!t.builtIn && (
              <Button size="small" icon={<EditOutlined />} onClick={() => openEditor(t)} data-testid={`btn-edit-${t.id}`}>
                Edit
              </Button>
            )}
            <Button size="small" icon={<CopyOutlined />} onClick={() => copy(t)} data-testid={`btn-copy-${t.id}`}>
              Copy
            </Button>
            {!t.builtIn && (
              <Popconfirm
                title="Delete this template?"
                description="Files already made from it stay in each pay run's history."
                okText="Delete"
                okButtonProps={{ danger: true }}
                onConfirm={() => remove(t)}
              >
                <Button size="small" danger icon={<DeleteOutlined />} data-testid={`btn-delete-${t.id}`}>
                  Delete
                </Button>
              </Popconfirm>
            )}
          </Space>
        ) : null,
    },
  ];

  return (
    <Card
      variant="borderless"
      title={
        <Space>
          <BankOutlined style={{ color: token.colorPrimary }} />
          <span>Bank files</span>
        </Space>
      }
      extra={
        canManage && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openEditor(null)} data-testid="btn-new-template">
            New template
          </Button>
        )
      }
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Lay out the file your bank&apos;s bulk upload asks for, once. On an approved pay run, choose the template and
        the employees, and download the file.
      </Text>
      {loadError && <Alert type="error" showIcon message={loadError} style={{ marginBottom: 16 }} />}
      <Table rowKey="id" loading={loading} dataSource={templates} columns={columns} pagination={false} />
      <BankFileTemplateEditor
        open={editorOpen}
        template={editing}
        fields={fields}
        saving={saving}
        error={saveError}
        onSave={save}
        onClose={() => setEditorOpen(false)}
      />
    </Card>
  );
}
