import React from 'react';
import infilogo from '../../../../assets/images/infilogo.jpg';

const PayslipDocument = ({ payslipData }) => {
  if (!payslipData) return null;

  const {
    employeeSummary,
    payrollSummary,
    earningComponents,
    payPeriod,
    payDate,
    grossEarnings,
    totalDeductions,
    netPay,
    totalReimbursements,
    organizationName,
    organizationFileUrl,
    workLocation,
    epfComponents,
    professionalTax,
    monthlyTds,
    paidDays,
    lop,
    totalNoOfLeaves,
    claimDeduction,
    claimReimbursement
  } = payslipData;

  const formatCurrency = (amount) => {
    if (!amount && amount !== 0) return "₹0.00";
    return `₹${parseFloat(amount).toLocaleString('en-IN', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    })}`;
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
      });
    } catch (error) {
      return dateString;
    }
  };

  const getAmountInWords = (amount) => {
    if (!amount && amount !== 0) return 'Zero Only';

    const ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine'];
    const teens = ['Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
    const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

    const num = Math.floor(Math.abs(Number(amount)));

    if (num === 0) return 'Zero Only';

    function convertToWords(n) {
      if (n < 10) return ones[n];
      if (n < 20) return teens[n - 10];
      if (n < 100) return tens[Math.floor(n / 10)] + (n % 10 !== 0 ? ' ' + ones[n % 10] : '');
      if (n < 1000) return ones[Math.floor(n / 100)] + ' Hundred' + (n % 100 !== 0 ? ' and ' + convertToWords(n % 100) : '');
      if (n < 100000) return convertToWords(Math.floor(n / 1000)) + ' Thousand' + (n % 1000 !== 0 ? ' ' + convertToWords(n % 1000) : '');
      if (n < 10000000) return convertToWords(Math.floor(n / 100000)) + ' Lakh' + (n % 100000 !== 0 ? ' ' + convertToWords(n % 100000) : '');
      return convertToWords(Math.floor(n / 10000000)) + ' Crore' + (n % 10000000 !== 0 ? ' ' + convertToWords(n % 10000000) : '');
    }

    const words = convertToWords(num);
    return (amount < 0 ? 'Minus ' : '') + words + ' Only';
  };

  const getFullAddress = (location) => {
    if (!location) return 'N/A';
    const { streetAddress1, streetAddress2, city, state, zipCode, country } = location;
    return [
      streetAddress1,
      streetAddress2,
      city,
      state,
      zipCode,
      country
    ]
      .filter(Boolean)
      .join(', ');
  };

  // Get EPF Contribution from backend
  const epfContribution = (Array.isArray(epfComponents) ? epfComponents : [])
    .filter(c => c && String(c.componentCode).toUpperCase() === 'EPF_EMPLOYER')
    .reduce((sum, c) => {
      const amt = (c && (c.monthlyAmount || c.monthlyAmount === 0)) ? Number(c.monthlyAmount) : 0;
      return sum + amt;
    }, 0);

  // Use backend calculated values
  const professionalTaxFromBackend = professionalTax || payrollSummary?.professionalTax || 0;
  const monthlyTdsFromBackend = monthlyTds || payrollSummary?.monthlyTds || 0;
  const grossEarningsFromBackend = grossEarnings || payrollSummary?.grossEarnings || 0;
  const totalDeductionsFromBackend = totalDeductions || payrollSummary?.totalDeductions || 0;
  const netPayFromBackend = netPay || payrollSummary?.netPay || 0;
  const claimDeductionFromBackend = claimDeduction || 0;
  const claimReimbursementFromBackend = claimReimbursement || 0;

  // Bonus from payrollSummary
  const bonusFromPayroll = (payrollSummary?.bonus != null && Number(payrollSummary.bonus) > 0)
    ? Number(payrollSummary.bonus)
    : null;

  // Fetch LOP data from backend
  const lopAmount = lop || payrollSummary?.lop || 0;
  const totalLeaves = totalNoOfLeaves || payrollSummary?.totalNoOfLeaves || 0;
  const hasLOP = lopAmount > 0 || totalLeaves > 0;

  const companyLogo = organizationFileUrl || infilogo;
  const companyName = organizationName || 'INFINEVOCLOUD TECHNOLOGY SOLUTIONS PVT LTD';
  const companyAddress = getFullAddress(workLocation);

  return (
    <div style={{
      fontFamily: '"Ubuntu", sans-serif',
      fontSize: '14px',
      margin: '20px',
      backgroundColor: '#ffffff'
    }}>
      <style>
        {`
          @import url('https://fonts.googleapis.com/css2?family=Ubuntu:ital,wght@0,300;0,400;0,500;0,700;1,300;1,400;1,500;1,700&display=swap');
          
          .employee-summary {
            background-color: #f8f9fa;
            padding: 20px;
            border-radius: 10px;
            margin-bottom: 20px;
            border: 1px solid #e9ecef;
          }

          .net-pay {
            background-color: #e8f5e8;
            padding: 10px 20px;
            border-bottom: 1px dashed #c8cdd5;
          }

          .total-net-payable {
            background-color: #e8f5e8;
            padding: 10px 20px;
            border-radius: 5px;
            font-weight: bold;
          }

          .payslip-card {
            border: 1px solid #dee2e6;
            border-radius: 10px;
            background-color: #ffffff;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
          }

          .summary-highlight {
            background-color: #f8f9fa;
            border-radius: 8px;
            padding: 15px;
          }

          .lop-note {
            color: #6c757d;
            font-size: 12px;
            font-style: italic;
            background-color: #f8f9fa;
            padding: 8px 12px;
            border-radius: 4px;
            border-left: 3px solid #dc3545;
            margin-bottom: 10px;
          }

          @media print {
            body * {
              visibility: hidden;
            }
            .payslip-card, .payslip-card * {
              visibility: visible;
            }
            .payslip-card {
              position: absolute;
              left: 0;
              top: 0;
              width: 100%;
              box-shadow: none;
              border: none;
            }
            .no-print {
              display: none !important;
            }
          }
        `}
      </style>

      <div className="payslip-card p-4" id="payslip-to-print">
        <header className="row mb-4 align-items-center">
          <div className="col-sm-9 d-flex align-items-center">
            <div>
              <img
                src={companyLogo}
                alt={companyName}
                style={{ width: '85px', objectFit: 'contain' }}
                onError={(e) => { e.target.src = infilogo; }}
              />
            </div>
            <div className="ms-3">
              <h3 className="mb-0" style={{ color: '#191970' }}>
                <strong>{companyName}</strong>
              </h3>
              <p className="mb-0" style={{ color: '#666666' }}>
                {companyAddress}
              </p>
            </div>
          </div>
          <div className="col-sm-3 text-end">
            <p className="mb-0" style={{ color: '#666666' }}>Payslip For the Month</p>
            <h5><strong>{payPeriod || 'N/A'}</strong></h5>
          </div>
        </header>

        <div className="employee-summary">
          <div className="row">
            <div className="col-sm-7">
              <h6 style={{ color: '#666666' }}>EMPLOYEE SUMMARY</h6>
              <table className="table table-sm table-borderless mb-0">
                <tbody>
                  <tr>
                    <td className="ps-0" style={{ color: '#666666' }}>Employee Name</td>
                    <td>: {employeeSummary?.fullName || 'N/A'}</td>
                  </tr>
                  <tr>
                    <td className="ps-0" style={{ color: '#666666' }}>Employee ID</td>
                    <td>: {employeeSummary?.employeeNumber || 'N/A'}</td>
                  </tr>
                  <tr>
                    <td className="ps-0" style={{ color: '#666666' }}>Designation</td>
                    <td>: {employeeSummary?.designation || 'N/A'}</td>
                  </tr>
                  <tr>
                    <td className="ps-0" style={{ color: '#666666' }}>Pay Period</td>
                    <td>: {payPeriod || 'N/A'}</td>
                  </tr>
                  <tr>
                    <td className="ps-0" style={{ color: '#666666' }}>Pay Date</td>
                    <td>: {formatDate(payDate) || 'N/A'}</td>
                  </tr>
                  <tr>
                    <td className="ps-0" style={{ color: '#666666' }}>Pay Days</td>
                    <td>: {paidDays !== undefined && paidDays !== null ? String(paidDays) : 'N/A'}</td>
                  </tr>
                  <tr>
                    <td className="ps-0" style={{ color: '#666666' }}>LOP Days</td>
                    <td>: {totalLeaves !== undefined && totalLeaves !== null ? String(totalLeaves) : '0'}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div className="col-sm-5">
              <div className="summary-highlight">
                <div className="net-pay">
                  <h4 className="mb-0">{formatCurrency(netPayFromBackend)}</h4>
                  <p className="mb-0" style={{ color: '#2e7d32' }}>Employee Net Pay</p>
                </div>
                <div className="p-3">
                  <table className="table table-sm table-borderless mb-0">
                    <tbody>
                      <tr>
                        <td className="ps-0" style={{ color: '#666666' }}>Employee Status</td>
                        <td>: {employeeSummary?.employeeStatus || 'N/A'}</td>
                      </tr>
                      <tr>
                        <td className="ps-0" style={{ color: '#666666' }}>Date of Joining</td>
                        <td>: {formatDate(employeeSummary?.dateOfJoining) || 'N/A'}</td>
                      </tr>
                      <tr>
                        <td className="ps-0" style={{ color: '#666666', whiteSpace: 'nowrap' }}>PF No</td>
                        <td style={{ whiteSpace: 'nowrap' }}>: {employeeSummary?.pfNumber || 'N/A'}</td>
                      </tr>
                       <tr>
                        <td className="ps-0" style={{ color: '#666666', whiteSpace: 'nowrap' }}>UAN No</td>
                        <td style={{ whiteSpace: 'nowrap' }}>: {employeeSummary?.uanNumber || 'N/A'}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="mb-0 p-3 pb-0" style={{ border: '1px solid #dee2e6', borderRadius: '10px 10px 0 0', borderBottom: '0', backgroundColor: '#ffffff' }}>
          <div className="row">
            <div className="col-sm-6">
              <table className="table table-borderless mb-0">
                <thead>
                  <tr>
                    <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="fw-bold ps-0">EARNINGS</th>
                    <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="text-end fw-bold pe-0">AMOUNT</th>
                  </tr>
                </thead>
                <tbody>
                  {earningComponents && earningComponents.length > 0 ? (
                    earningComponents
                      .filter(earning => String(earning.earningName || '').toLowerCase() !== 'bonus')
                      .map((earning, index) => (
                        <tr key={index}>
                          <td className="ps-0">{earning.earningName}</td>
                          <td className="text-end fw-bold pe-0">{formatCurrency(earning.earningAmount)}</td>
                        </tr>
                      ))
                  ) : (
                    <tr>
                      <td colSpan="2" className="text-center text-muted">No earnings data available</td>
                    </tr>
                  )}

                  {bonusFromPayroll !== null && (
                    <tr>
                      <td className="ps-0">Bonus</td>
                      <td className="text-end fw-bold pe-0">{formatCurrency(bonusFromPayroll)}</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="col-sm-6">
              <table className="table table-borderless mb-0">
                <thead>
                  <tr>
                    <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="fw-bold ps-0">DEDUCTIONS</th>
                    <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="text-end fw-bold pe-0">AMOUNT</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td className="ps-0">Income Tax</td>
                    <td className="text-end fw-bold pe-0">{formatCurrency(monthlyTdsFromBackend)}</td>
                  </tr>
                  <tr>
                    <td className="ps-0">EPF Contribution</td>
                    <td className="text-end fw-bold pe-0">{formatCurrency(epfContribution)}</td>
                  </tr>
                  <tr>
                    <td className="ps-0">Professional Tax</td>
                    <td className="text-end fw-bold pe-0">{formatCurrency(professionalTaxFromBackend)}</td>
                  </tr>
                  <tr>
                    <td>&nbsp;</td>
                    <td>&nbsp;</td>
                  </tr>
                  <tr>
                    <td>&nbsp;</td>
                    <td></td>
                  </tr>
                  <tr>
                    <td>&nbsp;</td>
                    <td>&nbsp;</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div className="mb-3 p-3 pb-0 pt-0"
          style={{ border: '1px solid #dee2e6', borderRadius: '0 0 10px 10px', borderTop: '0', backgroundColor: '#f8f9fa' }}>
          <div className="row">
            <div className="col-sm-6">
              <table className="table table-borderless mb-0">
                <tbody>
                  <tr>
                    <td className="fw-bold ps-0" style={{ backgroundColor: '#f8f9fa' }}>Gross Earnings</td>
                    <td className="text-end fw-bold pe-0" style={{ backgroundColor: '#f8f9fa' }}>
                      {formatCurrency(grossEarningsFromBackend)}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div className="col-sm-6">
              <table className="table table-borderless mb-0">
                <tbody>
                  <tr>
                    <td className="fw-bold ps-0" style={{ backgroundColor: '#f8f9fa' }}>Total Deductions</td>
                    <td className="text-end fw-bold pe-0" style={{ backgroundColor: '#f8f9fa' }}>
                      {formatCurrency(totalDeductionsFromBackend)}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        {/* Claims Section - separate from earnings/deductions */}
        {(claimReimbursementFromBackend > 0 || claimDeductionFromBackend > 0) && (
          <div className="mb-3 p-3" style={{ border: '1px solid #dee2e6', borderRadius: '10px', backgroundColor: '#ffffff' }}>
            <div className="row">
              <div className="col-sm-6">
                <table className="table table-borderless mb-0">
                  <thead>
                    <tr>
                      <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="fw-bold ps-0">CLAIMS (ADDITIONS)</th>
                      <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="text-end fw-bold pe-0">AMOUNT</th>
                    </tr>
                  </thead>
                  <tbody>
                    {claimReimbursementFromBackend > 0 && (
                      <tr>
                        <td className="ps-0">Claim Reimbursement</td>
                        <td className="text-end fw-bold pe-0">{formatCurrency(claimReimbursementFromBackend)}</td>
                      </tr>
                    )}
                    {claimReimbursementFromBackend === 0 && (
                      <tr>
                        <td className="ps-0 text-muted">No reimbursement claims</td>
                        <td className="text-end pe-0">-</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
              <div className="col-sm-6">
                <table className="table table-borderless mb-0">
                  <thead>
                    <tr>
                      <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="fw-bold ps-0">CLAIMS (DEDUCTIONS)</th>
                      <th style={{ borderBottom: '1px dashed #c5cbd3' }} className="text-end fw-bold pe-0">AMOUNT</th>
                    </tr>
                  </thead>
                  <tbody>
                    {claimDeductionFromBackend > 0 && (
                      <tr>
                        <td className="ps-0">Claim Deduction</td>
                        <td className="text-end fw-bold pe-0">{formatCurrency(claimDeductionFromBackend)}</td>
                      </tr>
                    )}
                    {claimDeductionFromBackend === 0 && (
                      <tr>
                        <td className="ps-0 text-muted">No deduction claims</td>
                        <td className="text-end pe-0">-</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        <div className="d-flex align-items-center justify-content-between p-3" style={{ border: '1px solid #dee2e6', borderRadius: '10px', backgroundColor: '#ffffff' }}>
          <div>
            <h6 className="mb-0 fw-bold">TOTAL NET PAYABLE</h6>
            <p className="mb-0" style={{ color: '#666666' }}>Gross Earnings + Claims (Additions) - Total Deductions</p>
          </div>
          <div>
            <div className="total-net-payable">{formatCurrency(netPayFromBackend)}</div>
          </div>
        </div>

        {hasLOP && (
          <div className="d-flex w-100 justify-content-between align-items-center lop-note mt-2">
            <span>
              Note: A loss of pay has been applied to this employee for {totalLeaves} day{totalLeaves !== 1 ? 's' : ''},
            </span>
            <span className="fw-bold">
              totaling {formatCurrency(lopAmount)}.
            </span>
          </div>
        )}

        <p className="footer text-end p-3" style={{ borderBottom: '1px solid #dee2e6', color: '#666666' }}>
          Amount In Words: <span style={{ color: '#000' }}>{getAmountInWords(netPayFromBackend)}</span>
        </p>
        <p className="footer text-center" style={{ color: '#666666' }}>This payslip is system-generated; therefore, a signature is not required.</p>
      </div>
    </div>
  );
};

export default PayslipDocument;
