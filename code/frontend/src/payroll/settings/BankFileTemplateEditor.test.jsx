import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { BankFileTemplateEditor, sampleValue, toRequest } from './BankFileTemplateEditor.jsx';

const FIELDS = [
  { code: 'ACCOUNT_NUMBER', label: 'Account number', kind: 'TEXT', formats: [] },
  { code: 'NET_PAY', label: 'Net pay', kind: 'AMOUNT', formats: ['DECIMAL_2', 'WHOLE_RUPEES', 'PAISE'] },
  { code: 'PAID_ON', label: 'Paid on', kind: 'DATE', formats: ['dd/MM/yyyy', 'yyyy-MM-dd'] },
];
const byCode = Object.fromEntries(FIELDS.map((f) => [f.code, f]));

describe('BankFileTemplateEditor (W-88.2)', () => {
  it('sampleValue shows each source and format the way the file will', () => {
    expect(sampleValue({ source: 'SERIAL' }, byCode)).toBe('1');
    expect(sampleValue({ source: 'FIXED', value: 'NEFT' }, byCode)).toBe('NEFT');
    expect(sampleValue({ source: 'FIELD', field: 'NET_PAY' }, byCode)).toBe('65000.50');
    expect(sampleValue({ source: 'FIELD', field: 'NET_PAY', format: 'PAISE' }, byCode)).toBe('6500050');
    expect(sampleValue({ source: 'FIELD', field: 'PAID_ON', format: 'yyyy-MM-dd' }, byCode)).toBe('2026-11-30');
    expect(sampleValue({ source: 'FIELD', field: 'ACCOUNT_NUMBER' }, byCode)).toBe('000123456789');
  });

  it('toRequest trims and keeps only what each source needs', () => {
    expect(
      toRequest({
        name: '  HDFC  ',
        fileFormat: 'XLSX',
        includeHeader: false,
        columns: [
          { key: 'a', heading: ' Mode ', source: 'FIXED', value: 'NEFT', field: 'IFSC', format: 'x' },
          { key: 'b', heading: 'Amt', source: 'FIELD', field: 'NET_PAY', format: 'PAISE', value: 'junk' },
          { key: 'c', heading: '#', source: 'SERIAL' },
        ],
      }),
    ).toEqual({
      name: 'HDFC',
      fileFormat: 'XLSX',
      includeHeader: false,
      columns: [
        { heading: 'Mode', source: 'FIXED', field: null, value: 'NEFT', format: null },
        { heading: 'Amt', source: 'FIELD', field: 'NET_PAY', value: null, format: 'PAISE' },
        { heading: '#', source: 'SERIAL', field: null, value: null, format: null },
      ],
    });
  });

  it('adds, moves and saves columns in the order shown', () => {
    const onSave = vi.fn();
    const template = {
      id: 't1',
      name: 'HDFC',
      fileFormat: 'CSV',
      includeHeader: true,
      columns: [
        { heading: 'A/c', source: 'FIELD', field: 'ACCOUNT_NUMBER' },
        { heading: 'Amt', source: 'FIELD', field: 'NET_PAY', format: 'DECIMAL_2' },
      ],
    };
    render(<BankFileTemplateEditor open template={template} fields={FIELDS} onSave={onSave} onClose={vi.fn()} />);

    fireEvent.click(screen.getByLabelText('Move 2 up'));
    fireEvent.click(screen.getByTestId('btn-add-column'));
    fireEvent.change(screen.getByLabelText('Heading 3'), { target: { value: 'Note' } });
    fireEvent.click(screen.getByTestId('btn-save-template'));

    const saved = onSave.mock.calls[0][0];
    expect(saved.columns.map((c) => c.heading)).toEqual(['Amt', 'A/c', 'Note']);
    expect(saved.name).toBe('HDFC');
    const rows = within(screen.getByTestId('template-columns')).getAllByRole('row');
    expect(rows.length).toBeGreaterThan(3);
  });

  it('cannot remove the last column', () => {
    render(
      <BankFileTemplateEditor
        open
        template={{ name: 'X', columns: [{ heading: 'A', source: 'SERIAL' }] }}
        fields={FIELDS}
        onSave={vi.fn()}
        onClose={vi.fn()}
      />,
    );
    expect(screen.getByLabelText('Remove 1').closest('button').disabled).toBe(true);
  });
});
