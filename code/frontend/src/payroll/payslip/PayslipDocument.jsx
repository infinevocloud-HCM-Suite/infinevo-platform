import PropTypes from 'prop-types';
import dayjs from 'dayjs';
import { formatMoney, getAmountInWords, orEmpty } from '../../shared/ui/format.js';

function formatFullDate(value) {
  if (!value) return 'N/A';
  const d = dayjs(value);
  return d.isValid() ? d.format('DD/MM/YYYY') : String(value);
}

function formatFullMonth(period) {
  if (!period) return 'N/A';
  const text = String(period).trim();
  const d = dayjs(/^\d{4}-\d{2}$/.test(text) ? `${text}-01` : text);
  return d.isValid() ? d.format('MMMM YYYY') : text;
}

export function PayslipDocument({ payslip, testIdPrefix = 'payslip' }) {
  if (!payslip) return null;

  const run = payslip.run || {};
  const employer = payslip.employer || {};
  const employee = payslip.employee || {};
  const days = payslip.days || {};
  const totals = payslip.totals || {};
  const earnings = payslip.earnings || [];
  const reimbursements = payslip.reimbursements || [];

  const companyName = employer.name || 'INFINEVOCLOUD TECHNOLOGY SOLUTIONS PVT LTD';
  const payPeriodText = formatFullMonth(run.period);
  const payDateText = formatFullDate(run.pay_date);
  const joiningDateText = formatFullDate(employee.date_of_joining);

  const paidDaysVal = days.paid_days !== undefined && days.paid_days !== null ? String(days.paid_days) : (days.payable_days !== undefined ? String(days.payable_days) : '0');
  const lopDaysVal = days.lop_days !== undefined && days.lop_days !== null ? String(days.lop_days) : '0';
  const lwpDaysVal = days.unpaid_days !== undefined && days.unpaid_days !== null ? String(days.unpaid_days) : '0';

  const employeeStatusVal = employee.status ? String(employee.status).toUpperCase() : 'ACTIVE';
  const pfNoVal = employee.pf_number || 'N/A';
  const uanNoVal = employee.uan_number || 'N/A';

  const amountInWordsText = getAmountInWords(totals.net_pay);

  // Ensure standard statutory deductions (Income Tax, EPF Contribution, Professional Tax) appear matching legacy layout
  const rawDeductions = payslip.deductions || [];
  const findDeduction = (patterns) => {
    return rawDeductions.find((d) => {
      const code = String(d?.code || '').toUpperCase();
      const name = String(d?.name || '').toUpperCase();
      return patterns.some((p) => code.includes(p) || name.includes(p));
    });
  };

  const taxItem = findDeduction(['TDS', 'INCOME TAX', 'INCOMETAX', 'TAX']);
  const epfItem = findDeduction(['EPF', 'PF', 'PROVIDENT']);
  const ptItem = findDeduction(['PROFESSIONAL_TAX', 'PROFESSIONAL TAX', 'PT']);

  const standardDeductions = [
    {
      code: taxItem?.code || 'TDS',
      name: taxItem?.name || 'Income Tax',
      amount: taxItem ? taxItem.amount : 0,
    },
    {
      code: epfItem?.code || 'EPF',
      name: epfItem?.name || 'EPF Contribution',
      amount: epfItem ? epfItem.amount : 0,
    },
    {
      code: ptItem?.code || 'PT',
      name: ptItem?.name || 'Professional Tax',
      amount: ptItem ? ptItem.amount : 0,
    },
  ];

  const matchedItems = new Set([taxItem, epfItem, ptItem].filter(Boolean));
  const otherDeductions = rawDeductions.filter((d) => !matchedItems.has(d));
  const displayDeductions = [...standardDeductions, ...otherDeductions];

  return (
    <div
      id="payslip-to-print"
      className="payslip-document-card"
      style={{
        fontFamily: '"Ubuntu", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
        fontSize: '14px',
        color: '#333333',
        backgroundColor: '#ffffff',
        border: '1px solid #dee2e6',
        borderRadius: '10px',
        padding: '24px',
        boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06)',
      }}
    >
      {/* Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '20px',
          borderBottom: '1px solid #f0f0f0',
          paddingBottom: '16px',
        }}
      >
        <div>
          <h3 style={{ margin: 0, color: '#191970', fontWeight: 'bold' }}>{companyName}</h3>
          <p style={{ margin: '4px 0 0', color: '#666666', fontSize: '13px' }}>HRMS &amp; Payroll Solutions</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p style={{ margin: 0, color: '#666666', fontSize: '13px' }}>Payslip For the Month</p>
          <h5 style={{ margin: '4px 0 0', fontWeight: 'bold', fontSize: '16px' }}>{payPeriodText}</h5>
        </div>
      </div>

      {/* EMPLOYEE SUMMARY Box */}
      <div
        className="employee-summary-box"
        data-testid={`${testIdPrefix}-employee`}
        style={{
          backgroundColor: '#f8f9fa',
          border: '1px solid #e9ecef',
          borderRadius: '8px',
          padding: '16px 20px',
          marginBottom: '20px',
        }}
      >
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '20px' }}>
          {/* Left Column */}
          <div style={{ flex: '1 1 320px', minWidth: '280px' }}>
            <h6 style={{ color: '#666666', fontWeight: 'bold', marginBottom: '12px', fontSize: '13px' }}>
              EMPLOYEE SUMMARY
            </h6>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', lineHeight: '24px' }}>
              <tbody>
                <tr>
                  <td style={{ color: '#666666', width: '130px' }}>Employee Name</td>
                  <td>: <strong>{orEmpty(employee.name)}</strong></td>
                </tr>
                <tr>
                  <td style={{ color: '#666666' }}>Employee ID</td>
                  <td>: {orEmpty(employee.number)}</td>
                </tr>
                <tr>
                  <td style={{ color: '#666666' }}>Designation</td>
                  <td>: {orEmpty(employee.designation)}</td>
                </tr>
                {employee.department && (
                  <tr>
                    <td style={{ color: '#666666' }}>Department</td>
                    <td>: {employee.department}</td>
                  </tr>
                )}
                <tr>
                  <td style={{ color: '#666666' }}>Pay Period</td>
                  <td>: {payPeriodText}</td>
                </tr>
                <tr>
                  <td style={{ color: '#666666' }}>Pay Date</td>
                  <td>: {payDateText}</td>
                </tr>
                <tr>
                  <td style={{ color: '#666666' }}>Pay Days</td>
                  <td>: {paidDaysVal}</td>
                </tr>
                <tr>
                  <td style={{ color: '#666666' }}>LOP Days</td>
                  <td>: {lopDaysVal}</td>
                </tr>
                <tr>
                  <td style={{ color: '#666666' }}>LWP Days</td>
                  <td>: {lwpDaysVal}</td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* Right Column: Net Pay Highlight Card */}
          <div style={{ flex: '1 1 280px', minWidth: '260px' }}>
            <div
              style={{
                backgroundColor: '#ffffff',
                border: '1px solid #dee2e6',
                borderRadius: '8px',
                overflow: 'hidden',
              }}
            >
              <div
                style={{
                  backgroundColor: '#e8f5e8',
                  padding: '12px 16px',
                  borderBottom: '1px dashed #c8cdd5',
                }}
              >
                <h3 style={{ margin: 0, color: '#2e7d32', fontWeight: 'bold' }}>
                  {formatMoney(totals.net_pay)}
                </h3>
                <p style={{ margin: 0, color: '#2e7d32', fontSize: '12px', fontWeight: '500' }}>
                  Employee Net Pay
                </p>
              </div>
              <div style={{ padding: '12px 16px' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px', lineHeight: '24px' }}>
                  <tbody>
                    <tr>
                      <td style={{ color: '#666666', width: '120px' }}>Employee Status</td>
                      <td>: <strong>{employeeStatusVal}</strong></td>
                    </tr>
                    <tr>
                      <td style={{ color: '#666666' }}>Date of Joining</td>
                      <td>: {joiningDateText}</td>
                    </tr>
                    <tr>
                      <td style={{ color: '#666666' }}>PF No</td>
                      <td>: {pfNoVal}</td>
                    </tr>
                    <tr>
                      <td style={{ color: '#666666' }}>UAN No</td>
                      <td>: {uanNoVal}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Days & Attendance Reference (data-testid anchor for backwards compatibility) */}
      <div data-testid={`${testIdPrefix}-days`} style={{ display: 'none' }}>
        <span>{paidDaysVal}</span>
        <span>{lopDaysVal}</span>
        <span>{lwpDaysVal}</span>
      </div>

      {/* EARNINGS & DEDUCTIONS Side-by-Side Tables */}
      <div
        style={{
          border: '1px solid #dee2e6',
          borderRadius: '8px 8px 0 0',
          borderBottom: 'none',
          backgroundColor: '#ffffff',
          overflow: 'hidden',
        }}
      >
        <div style={{ display: 'flex', flexWrap: 'wrap' }}>
          {/* Earnings Column */}
          <div
            data-testid={`${testIdPrefix}-earnings`}
            style={{
              flex: '1 1 50%',
              minWidth: '280px',
              borderRight: '1px solid #dee2e6',
              padding: '14px 16px',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                borderBottom: '1px dashed #c5cbd3',
                paddingBottom: '8px',
                marginBottom: '10px',
                fontWeight: 'bold',
                fontSize: '13px',
              }}
            >
              <span>EARNINGS</span>
              <span>AMOUNT</span>
            </div>
            {earnings.length > 0 ? (
              earnings.map((earning, idx) => (
                <div
                  key={earning.code || idx}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    padding: '4px 0',
                    fontSize: '13px',
                  }}
                >
                  <span style={{ color: '#444' }}>{earning.name || earning.code}</span>
                  <span style={{ fontWeight: '500' }}>{formatMoney(earning.amount)}</span>
                </div>
              ))
            ) : (
              <div style={{ padding: '8px 0', color: '#999', fontSize: '13px', textAlign: 'center' }}>
                No earnings components
              </div>
            )}
          </div>

          {/* Deductions Column */}
          <div
            data-testid={`${testIdPrefix}-deductions`}
            style={{
              flex: '1 1 50%',
              minWidth: '280px',
              padding: '14px 16px',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                borderBottom: '1px dashed #c5cbd3',
                paddingBottom: '8px',
                marginBottom: '10px',
                fontWeight: 'bold',
                fontSize: '13px',
              }}
            >
              <span>DEDUCTIONS</span>
              <span>AMOUNT</span>
            </div>
            {displayDeductions.map((deduction, idx) => (
              <div
                key={deduction.code || idx}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '4px 0',
                  fontSize: '13px',
                }}
              >
                <span style={{ color: '#444' }}>{deduction.name || deduction.code}</span>
                <span style={{ fontWeight: '500' }}>{formatMoney(deduction.amount)}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Subtotal & Totals Wrapper */}
      <div data-testid={`${testIdPrefix}-totals`}>
        {/* Subtotal Bar */}
        <div
          style={{
            border: '1px solid #dee2e6',
            borderTop: '1px solid #e9ecef',
            borderRadius: '0 0 8px 8px',
            backgroundColor: '#f8f9fa',
            padding: '12px 16px',
            marginBottom: '20px',
          }}
        >
          <div style={{ display: 'flex', flexWrap: 'wrap' }}>
            <div
              style={{
                flex: '1 1 50%',
                minWidth: '280px',
                borderRight: '1px solid #dee2e6',
                paddingRight: '16px',
                display: 'flex',
                justifyContent: 'space-between',
                fontWeight: 'bold',
                fontSize: '13px',
              }}
            >
              <span>Gross Earnings</span>
              <span>{formatMoney(totals.gross_earnings)}</span>
            </div>
            <div
              style={{
                flex: '1 1 50%',
                minWidth: '280px',
                paddingLeft: '16px',
                display: 'flex',
                justifyContent: 'space-between',
                fontWeight: 'bold',
                fontSize: '13px',
              }}
            >
              <span>Total Deductions</span>
              <span>{formatMoney(totals.total_deductions)}</span>
            </div>
          </div>
        </div>

        {/* Reimbursements / Claims (if any) */}
        {reimbursements.length > 0 && (
          <div
            data-testid={`${testIdPrefix}-reimbursements`}
            style={{
              border: '1px solid #dee2e6',
              borderRadius: '8px',
              backgroundColor: '#ffffff',
              padding: '14px 16px',
              marginBottom: '20px',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                borderBottom: '1px dashed #c5cbd3',
                paddingBottom: '8px',
                marginBottom: '10px',
                fontWeight: 'bold',
                fontSize: '13px',
              }}
            >
              <span>CLAIMS (ADDITIONS)</span>
              <span>AMOUNT</span>
            </div>
            {reimbursements.map((item, idx) => (
              <div
                key={item.code || idx}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  padding: '4px 0',
                  fontSize: '13px',
                }}
              >
                <span>{item.name || item.code}</span>
                <span style={{ fontWeight: '500' }}>{formatMoney(item.amount)}</span>
              </div>
            ))}
          </div>
        )}

        {/* TOTAL NET PAYABLE Banner */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            border: '1px solid #dee2e6',
            borderRadius: '8px',
            backgroundColor: '#ffffff',
            padding: '14px 20px',
            marginBottom: '16px',
          }}
        >
          <div>
            <h6 style={{ margin: 0, fontWeight: 'bold', fontSize: '14px', color: '#222' }}>
              TOTAL NET PAYABLE
            </h6>
            <p style={{ margin: '4px 0 0', color: '#666666', fontSize: '12px' }}>
              Gross Earnings + Claims (Additions) - Total Deductions
            </p>
          </div>
          <div>
            <div
              style={{
                backgroundColor: '#e8f5e8',
                color: '#2e7d32',
                fontWeight: 'bold',
                fontSize: '16px',
                padding: '8px 16px',
                borderRadius: '6px',
                textAlign: 'right',
              }}
            >
              <div style={{ fontSize: '11px', textTransform: 'uppercase', color: '#2e7d32', fontWeight: '600' }}>
                Net Pay
              </div>
              {formatMoney(totals.net_pay)}
            </div>
          </div>
        </div>
      </div>

      {/* LOP Note (if any) */}
      {Number(days.lop_days) > 0 && (
        <div
          style={{
            backgroundColor: '#fff8f8',
            borderLeft: '3px solid #dc3545',
            padding: '8px 12px',
            marginBottom: '16px',
            borderRadius: '4px',
            fontSize: '12px',
            color: '#666666',
          }}
        >
          Note: A loss of pay has been applied to this employee for {days.lop_days} day(s).
        </div>
      )}

      {/* Amount In Words */}
      <div
        style={{
          borderBottom: '1px solid #dee2e6',
          paddingBottom: '12px',
          marginBottom: '16px',
          fontSize: '13px',
          color: '#666666',
          textAlign: 'right',
        }}
      >
        Amount In Words:{' '}
        <strong style={{ color: '#000000' }}>{amountInWordsText}</strong>
      </div>

      {/* System Generated Disclaimer */}
      <div style={{ textAlign: 'center', color: '#666666', fontSize: '12px' }}>
        This payslip is system-generated; therefore, a signature is not required.
      </div>
    </div>
  );
}

PayslipDocument.propTypes = {
  payslip: PropTypes.shape({
    run: PropTypes.object,
    employer: PropTypes.object,
    employee: PropTypes.object,
    days: PropTypes.object,
    earnings: PropTypes.array,
    deductions: PropTypes.array,
    reimbursements: PropTypes.array,
    benefits: PropTypes.array,
    totals: PropTypes.object,
  }),
  testIdPrefix: PropTypes.string,
};

export default PayslipDocument;
