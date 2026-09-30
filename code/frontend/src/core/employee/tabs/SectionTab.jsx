import { useEffect, useState } from 'react';
import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { Row, Col, Input, Select, Switch, Button, DatePicker, Typography, Spin, Card, theme } from 'antd';
import { SaveOutlined, EyeOutlined, EyeInvisibleOutlined } from '@ant-design/icons';
import { useCan } from '@shell/screens';
import { sectionFields } from '../sectionFields.js';
import { employeeService } from '../employeeService.js';
import { successMsg, errorMsg } from '@shared/ui/msgHelper.js';

const { Text } = Typography;

export function SectionTab({ employeeId, sectionName }) {
  const { token } = theme.useToken();
  const config = sectionFields[sectionName] || { fields: [], permission: 'core.employee.update' };
  const canUpdate = useCan(config.permission);

  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [values, setValues] = useState({});
  const [focusedField, setFocusedField] = useState(null);
  const [revealedFields, setRevealedFields] = useState({});

  useEffect(() => {
    let active = true;
    setLoading(true);
    employeeService
      .section(employeeId, sectionName)
      .then((data) => {
        if (active) {
          setValues(data || {});
        }
      })
      .catch((err) => {
        // 404 means section not filled yet, which is expected for fresh employee
        if (err?.code !== 'NOT_FOUND') {
          errorMsg(err);
        }
        if (active) {
          setValues({});
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [employeeId, sectionName]);

  const handleChange = (name, val) => {
    setValues((prev) => ({ ...prev, [name]: val }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      await employeeService.saveSection(employeeId, sectionName, values);
      await successMsg('Section Saved', `${config.title} saved successfully.`);
    } catch (err) {
      await errorMsg(err);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  return (
    <Card variant="borderless" style={{ padding: 0 }}>
      <Row gutter={[token.margin, token.margin]}>
        {config.fields.map((field) => {
          const val = values[field.name];

          let control = null;
          if (field.type === 'select') {
            control = (
              <Select
                id={`field-${field.name}`}
                style={{ width: '100%' }}
                disabled={!canUpdate}
                value={val || undefined}
                placeholder={`Select ${field.label}`}
                allowClear
                onChange={(v) => handleChange(field.name, v)}
                options={field.options}
              />
            );
          } else if (field.type === 'date') {
            control = (
              <DatePicker
                id={`field-${field.name}`}
                style={{ width: '100%' }}
                disabled={!canUpdate}
                value={val ? dayjs(val) : null}
                onChange={(_, dateStr) => handleChange(field.name, dateStr)}
              />
            );
          } else if (field.type === 'boolean') {
            control = (
              <div style={{ paddingTop: 4 }}>
                <Switch
                  id={`field-${field.name}`}
                  disabled={!canUpdate}
                  checked={Boolean(val)}
                  onChange={(checked) => handleChange(field.name, checked)}
                />
              </div>
            );
          } else if (field.type === 'textarea') {
            control = (
              <Input.TextArea
                id={`field-${field.name}`}
                rows={3}
                disabled={!canUpdate}
                value={val || ''}
                onChange={(e) => handleChange(field.name, e.target.value)}
              />
            );
          } else if (!canUpdate && field.masked) {
            const isRevealed = Boolean(revealedFields[field.name]);
            const displayVal = val ? (isRevealed ? val : '••••••••••••') : '—';
            control = (
              <div
                id={`field-${field.name}`}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: token.marginXS,
                  minHeight: 32,
                  padding: '4px 11px',
                  background: token.colorBgContainerDisabled,
                  border: `1px solid ${token.colorBorder}`,
                  borderRadius: token.borderRadius,
                }}
              >
                <Text style={{ flex: 1, fontFamily: isRevealed ? 'inherit' : 'monospace' }}>
                  {displayVal}
                </Text>
                {val ? (
                  <Button
                    type="text"
                    size="small"
                    aria-label={isRevealed ? `Hide ${field.label}` : `Reveal ${field.label}`}
                    icon={isRevealed ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                    onClick={() =>
                      setRevealedFields((prev) => ({ ...prev, [field.name]: !prev[field.name] }))
                    }
                  />
                ) : null}
              </div>
            );
          } else {
            // text or email
            const isMasked = field.masked && focusedField !== field.name;
            control = (
              <Input
                id={`field-${field.name}`}
                type={isMasked ? 'password' : 'text'}
                disabled={!canUpdate}
                value={val || ''}
                placeholder={`Enter ${field.label}`}
                onChange={(e) => handleChange(field.name, e.target.value)}
                onFocus={() => setFocusedField(field.name)}
                onBlur={() => setFocusedField(null)}
              />
            );
          }

          return (
            <Col xs={24} sm={12} md={field.type === 'textarea' ? 24 : 8} key={field.name}>
              <label htmlFor={`field-${field.name}`}>
                <Text strong>{field.label}</Text>
              </label>
              <div style={{ marginTop: 4 }}>{control}</div>
            </Col>
          );
        })}
      </Row>

      {canUpdate && (
        <div style={{ marginTop: token.marginLG, display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
            onClick={handleSave}
            id={`btn-save-${sectionName}`}
          >
            Save {config.title}
          </Button>
        </div>
      )}
    </Card>
  );
}

SectionTab.propTypes = {
  employeeId: PropTypes.oneOfType([PropTypes.string, PropTypes.number]).isRequired,
  sectionName: PropTypes.string.isRequired,
};
