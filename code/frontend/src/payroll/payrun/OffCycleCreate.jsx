import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Steps,
  Form,
  DatePicker,
  Select,
  Input,
  InputNumber,
  Button,
  Space,
  Typography,
  Alert,
  Table,
  Tag,
  Row,
  Col,
  Breadcrumb,
} from 'antd';
import {
  ArrowLeftOutlined,
  PlusOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { payrunService } from './payrunService.js';

const { Title, Text } = Typography;

// core.payinput.PayInputKind less LOP_DAYS, which an off-cycle run refuses (W-30.2 §4).
export const INPUT_KINDS = [
  { label: 'One-Time Payout', value: 'ONE_TIME_PAYOUT' },
  { label: 'Overtime', value: 'OVERTIME' },
  { label: 'Reimbursement', value: 'REIMBURSEMENT' },
  { label: 'Ad-Hoc Deduction', value: 'AD_HOC_DEDUCTION' },
];

const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi;

/**
 * The server's 400 for employees it cannot put on the run (EmployeeNotInRunException) is a message
 * only, ending in their ids: "No such employee in this tenant: [id, id]". Splits it into the reason
 * and the ids, so the screen can name each employee instead of printing the ids (W-47.2 §5).
 */
export function parseEmployeeError(message) {
  const text = message || '';
  const ids = text.match(UUID_PATTERN) || [];
  const reason = ids.length > 0 ? text.replace(/:\s*\[[^\]]*\]\s*$/, '') : text;
  return { reason, ids };
}

export function OffCycleCreate() {
  const navigate = useNavigate();
  const [currentStep, setCurrentStep] = useState(0);

  // Step 1: Run creation
  const [payDate, setPayDate] = useState(null);
  const [selectedEmployees, setSelectedEmployees] = useState([]);
  const [notes, setNotes] = useState('');
  const [employeeOptions, setEmployeeOptions] = useState([]);
  const [searching, setSearching] = useState(false);
  const [step1Loading, setStep1Loading] = useState(false);
  const [step1Error, setStep1Error] = useState(null);
  const [unconsideredEmployees, setUnconsideredEmployees] = useState([]);
  // Every employee the picker has shown, by id; a later search replaces the options but not this.
  const knownEmployees = useRef(new Map());

  // Step 2: Inputs Grid
  const [createdRun, setCreatedRun] = useState(null);
  const [inputRows, setInputRows] = useState([]);
  const [step2Loading, setStep2Loading] = useState(false);
  const [step2Error, setStep2Error] = useState(null);
  const [savedCount, setSavedCount] = useState(0);

  // Search employees on mount or typing
  const searchEmployees = async (query = '') => {
    setSearching(true);
    try {
      const list = await payrunService.searchEmployees(query);
      const opts = (list || []).map((emp) => {
        const id = emp.id || emp.employee_id;
        const num = emp.employeeNumber || emp.employee_number || '';
        const name = [emp.firstName, emp.lastName].filter(Boolean).join(' ') || num || id;
        return {
          value: id,
          label: num ? `${num} — ${name}` : name,
          num,
          name,
        };
      });
      opts.forEach((opt) => knownEmployees.current.set(opt.value, opt));
      setEmployeeOptions(opts);
    } catch {
      setEmployeeOptions([]);
    } finally {
      setSearching(false);
    }
  };

  // The chosen employees as options, from every search so far, not only the latest one.
  const selectedOptions = selectedEmployees.map(
    (id) => knownEmployees.current.get(id) || { value: id, label: id }
  );
  const pickerOptions = [
    ...selectedOptions,
    ...employeeOptions.filter((o) => !selectedEmployees.includes(o.value)),
  ];

  useEffect(() => {
    searchEmployees('');
  }, []);

  const handleStep1Submit = async () => {
    if (!payDate || selectedEmployees.length === 0) return;
    setStep1Loading(true);
    setStep1Error(null);
    setUnconsideredEmployees([]);

    const dateStr = payDate.format('YYYY-MM-DD');
    try {
      const res = await payrunService.createOffCycle({
        payDate: dateStr,
        employeeIds: selectedEmployees,
        notes,
      });
      setCreatedRun(res);
      // Pre-populate input rows for each chosen employee
      const initialRows = selectedEmployees.map((empId, idx) => ({
        key: `row_${Date.now()}_${idx}`,
        employeeId: empId,
        kind: 'ONE_TIME_PAYOUT',
        amount: null,
        sourceRef: `OFF_${Date.now()}_${idx + 1}`,
        status: null,
        payInputId: null,
      }));
      setInputRows(initialRows);
      setCurrentStep(1);
    } catch (err) {
      const { reason, ids } = parseEmployeeError(err?.message);
      setStep1Error(reason || 'Failed to create off-cycle pay run');
      setUnconsideredEmployees(
        ids.map((id) => {
          const known = knownEmployees.current.get(id);
          if (!known) return `Unknown employee (${id})`;
          return known.num ? `${known.name} (${known.num})` : known.name;
        })
      );
    } finally {
      setStep1Loading(false);
    }
  };

  const handleAddInputRow = () => {
    const newRow = {
      key: `row_${Date.now()}_${inputRows.length}`,
      employeeId: selectedEmployees[0] || '',
      kind: 'ONE_TIME_PAYOUT',
      amount: null,
      sourceRef: `OFF_${Date.now()}_${inputRows.length + 1}`,
      status: null,
      payInputId: null,
    };
    setInputRows([...inputRows, newRow]);
  };

  const handleRemoveRow = (key) => {
    setInputRows(inputRows.filter((r) => r.key !== key));
  };

  const handleRowChange = (key, field, value) => {
    setInputRows((prev) =>
      prev.map((r) => (r.key === key ? { ...r, [field]: value } : r))
    );
  };

  const handleSaveInputs = async () => {
    if (!createdRun?.id || inputRows.length === 0) return;
    setStep2Loading(true);
    setStep2Error(null);

    // The server strips the reference and echoes it back stripped; trimming here keeps each result
    // matched to its row (W-47.2 §5).
    const trimmedRows = inputRows.map((r) => ({ ...r, sourceRef: (r.sourceRef || '').trim() }));
    const trimmedRefs = new Map(trimmedRows.map((r) => [r.key, r.sourceRef]));
    const payload = trimmedRows
      .filter(
        (r) => r.employeeId && r.kind && r.amount !== null && r.amount !== undefined && r.sourceRef
      )
      .map((r) => ({
        employeeId: r.employeeId,
        kind: r.kind,
        amount: r.amount,
        sourceRef: r.sourceRef,
      }));

    if (payload.length === 0) {
      setStep2Error('Please enter at least one valid input row with employee, kind, amount, and reference.');
      setStep2Loading(false);
      return;
    }

    try {
      const results = await payrunService.addInputs(createdRun.id, payload);
      const resultMap = new Map();
      (results || []).forEach((res) => {
        const key = `${res.employee_id || res.employeeId}_${res.source_ref || res.sourceRef}`;
        resultMap.set(key, res);
      });

      const outcomeOf = (res) =>
        res.result || (res.pay_input_id || res.payInputId ? 'RECORDED' : 'DUPLICATE');
      // Counted here, not inside the state updater, which React may run twice.
      const recorded = (results || []).filter((res) => outcomeOf(res) === 'RECORDED').length;
      setInputRows((prev) =>
        prev.map((current) => {
          const row = trimmedRefs.has(current.key)
            ? { ...current, sourceRef: trimmedRefs.get(current.key) }
            : current;
          const key = `${row.employeeId}_${row.sourceRef}`;
          const res = resultMap.get(key);
          if (res) {
            const outcome = outcomeOf(res);
            return {
              ...row,
              status: outcome,
              payInputId: res.pay_input_id || res.payInputId,
            };
          }
          return row;
        })
      );
      setSavedCount(recorded);
    } catch (err) {
      setStep2Error(err?.message || 'Failed to save off-cycle inputs');
    } finally {
      setStep2Loading(false);
    }
  };

  const inputColumns = [
    {
      title: 'Employee',
      dataIndex: 'employeeId',
      key: 'employeeId',
      width: 250,
      render: (val, record) => (
        <Select
          value={val}
          onChange={(v) => handleRowChange(record.key, 'employeeId', v)}
          options={selectedEmployees.length === 0 ? employeeOptions : selectedOptions}
          style={{ width: '100%' }}
          placeholder="Select employee"
        />
      ),
    },
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      width: 200,
      render: (val, record) => (
        <Select
          value={val}
          onChange={(v) => handleRowChange(record.key, 'kind', v)}
          options={INPUT_KINDS}
          style={{ width: '100%' }}
        />
      ),
    },
    {
      title: 'Amount (₹)',
      dataIndex: 'amount',
      key: 'amount',
      width: 160,
      render: (val, record) => (
        <InputNumber
          value={val}
          onChange={(v) => handleRowChange(record.key, 'amount', v)}
          min={0.01}
          step={0.01}
          precision={2}
          style={{ width: '100%' }}
          placeholder="0.00"
        />
      ),
    },
    {
      title: 'Source Reference',
      dataIndex: 'sourceRef',
      key: 'sourceRef',
      render: (val, record) => (
        <Input
          value={val}
          onChange={(e) => handleRowChange(record.key, 'sourceRef', e.target.value)}
          placeholder="e.g. OT-SEP-2026"
        />
      ),
    },
    {
      title: 'Status / Outcome',
      key: 'status',
      width: 160,
      render: (_, record) => {
        if (!record.status) return <Text type="secondary">-</Text>;
        if (record.status === 'DUPLICATE') {
          return (
            <Tag icon={<WarningOutlined />} color="warning">
              DUPLICATE
            </Tag>
          );
        }
        return (
          <Tag icon={<CheckCircleOutlined />} color="success">
            {record.payInputId ? `ID: ${String(record.payInputId).slice(0, 8)}` : 'RECORDED'}
          </Tag>
        );
      },
    },
    {
      title: 'Action',
      key: 'delete',
      width: 80,
      align: 'center',
      render: (_, record) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleRemoveRow(record.key)}
        />
      ),
    },
  ];

  return (
    <div style={{ padding: '24px', maxWidth: 1200, margin: '0 auto' }}>
      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => navigate('/payroll/runs')}>Pay Runs</a> },
          { title: 'New Off-Cycle Run' },
        ]}
      />

      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/payroll/runs')}>
          Runs
        </Button>
        <Title level={3} style={{ margin: 0 }}>
          Create Off-Cycle Pay Run
        </Title>
      </div>

      <Card style={{ marginBottom: 24 }}>
        <Steps
          current={currentStep}
          items={[
            { title: 'Run Details', description: 'Date & Employees' },
            { title: 'Pay Inputs', description: 'Tagged Amounts' },
          ]}
        />
      </Card>

      {currentStep === 0 && (
        <Card title="Step 1: Off-Cycle Run Configuration">
          {step1Error && (
            <Alert
              message="Failed to create off-cycle run"
              description={
                <div>
                  <Text>{step1Error}</Text>
                  {unconsideredEmployees.length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      <Text strong>Employees not considered:</Text>
                      <ul>
                        {unconsideredEmployees.map((name, i) => (
                          <li key={i}>{name}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              }
              type="error"
              showIcon
              style={{ marginBottom: 20 }}
            />
          )}

          <Form layout="vertical" onFinish={handleStep1Submit}>
            <Row gutter={24}>
              <Col xs={24} md={12}>
                <Form.Item label="Payment Date" required>
                  <DatePicker
                    value={payDate}
                    onChange={setPayDate}
                    style={{ width: '100%' }}
                    placeholder="Select payment date"
                  />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item label="Employees" required help="Select one or more employees to include in this off-cycle run.">
                  <Select
                    mode="multiple"
                    value={selectedEmployees}
                    onChange={setSelectedEmployees}
                    options={pickerOptions}
                    onSearch={searchEmployees}
                    filterOption={false}
                    loading={searching}
                    placeholder="Search employees by name or ID"
                    style={{ width: '100%' }}
                  />
                </Form.Item>
              </Col>
              <Col xs={24}>
                <Form.Item label="Notes / Reason">
                  <Input.TextArea
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    rows={3}
                    placeholder="e.g. arrears correction, overtime settlement"
                  />
                </Form.Item>
              </Col>
            </Row>

            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={step1Loading}
                disabled={!payDate || selectedEmployees.length === 0}
              >
                Create Run & Enter Inputs →
              </Button>
            </div>
          </Form>
        </Card>
      )}

      {currentStep === 1 && (
        <Card
          title={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <span>Step 2: Tagged Inputs for Run {createdRun?.period}</span>
                <Tag color="purple" style={{ marginLeft: 8 }}>
                  OFF_CYCLE
                </Tag>
              </div>
              <Space>
                <Button icon={<PlusOutlined />} onClick={handleAddInputRow}>
                  Add Input Row
                </Button>
                <Button
                  type="primary"
                  onClick={handleSaveInputs}
                  loading={step2Loading}
                  disabled={inputRows.length === 0}
                >
                  Save Inputs
                </Button>
                <Button onClick={() => navigate(`/payroll/runs/${createdRun?.id}`)}>
                  View Pay Run Page →
                </Button>
              </Space>
            </div>
          }
        >
          {step2Error && (
            <Alert
              message={step2Error}
              type="error"
              showIcon
              closable
              onClose={() => setStep2Error(null)}
              style={{ marginBottom: 16 }}
            />
          )}

          {savedCount > 0 && (
            <Alert
              message={`Successfully recorded ${savedCount} pay input(s).`}
              type="success"
              showIcon
              style={{ marginBottom: 16 }}
            />
          )}

          <Table
            dataSource={inputRows}
            columns={inputColumns}
            pagination={false}
            rowKey="key"
            size="small"
          />
        </Card>
      )}
    </div>
  );
}
