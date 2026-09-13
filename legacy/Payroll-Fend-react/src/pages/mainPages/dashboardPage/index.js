// index.js
import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import Chart from "react-apexcharts";

import terms2 from "../../../assets/images/terms-2.png"; // adjust path if needed
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import Loader from "../../../shared/components/loaders/fullPageLoader";

export default function DashboardPage() {
  // --- Static demo data (replace with API calls later as needed) ---
  const stats = {
    epf: { title: "EPF", value: "-", details: "View Details" },
    esi: { title: "ESI", value: "-", details: "View Details" },
    tds: { title: "TDS", value: "-", details: "View Details" },
    employeesActive: 12,
    totalCost: "₹2,50,000",
    lastRunDate: "01-Apr-2025",
    payslipGeneratedPct: 68,
  };

  const recentPayRuns = [
    { id: "PR-2025-04", period: "Apr 2025", date: "01-Apr-2025", status: "Completed", amount: "₹45,000" },
    { id: "PR-2025-03", period: "Mar 2025", date: "01-Mar-2025", status: "Completed", amount: "₹43,200" },
    { id: "PR-2025-02", period: "Feb 2025", date: "01-Feb-2025", status: "Completed", amount: "₹41,800" },
  ];

  const upcomingPayments = [
    { title: "Salary Payment", date: "01-May-2025", amount: "₹50,000" },
    { title: "TDS Due", date: "07-May-2025", amount: "₹3,200" },
  ];

  // border style used on all cards for consistent subtle border
  const cardBorder = { border: "1px solid #eef1f5", background: "#ffffff" };

  // --- PayRun backend integration state & helpers (ported/adapted from your PayRuns index.js) ---
  const [payRunsData, setPayRunsData] = useState([]); // will hold formatted payruns
  const [isPayrunLoading, setIsPayrunLoading] = useState(false);
  const [payrunFetchError, setPayrunFetchError] = useState(false);
  const navigate = useNavigate();

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  // Map API type to display name (same mapping from PayRuns)
  const getPayrollTypeDisplayName = (type) => {
    if (!type) return 'Unknown';
    const typeMap = {
      'regular': 'Regular Payroll',
      'one-time payout': 'One Time Payout',
      'off cycle payroll': 'Off Cycle Payroll',
      'past payroll': 'Past Payroll',
      'final settlement payroll': 'Final Settlement Payroll',
      'bulk final settlement payroll': 'Bulk Final Settlement Payroll',
      'resettlement payroll': 'Resettlement Payroll'
    };
    return typeMap[type.toLowerCase()] || type;
  };

  const getBackendPayrollType = (displayName) => {
    if (!displayName) return 'regular';
    const reverseTypeMap = {
      'Regular Payroll': 'regular',
      'One Time Payout': 'one-time payout',
      'Off Cycle Payroll': 'off cycle payroll',
      'Past Payroll': 'past payroll',
      'Final Settlement Payroll': 'final settlement payroll',
      'Bulk Final Settlement Payroll': 'bulk final settlement payroll',
      'Resettlement Payroll': 'resettlement payroll'
    };
    return reverseTypeMap[displayName] || displayName.toLowerCase();
  };

  const getStatusColor = (status) => {
    switch (status) {
      case 'DRAFT':
        return '#0d6efd';
      case 'PENDING':
        return '#fd7e14';
      case 'APPROVED':
        return '#198754';
      case 'PROCESSED':
        return '#6f42c1';
      case 'READY':
        return '#198754';
      case 'COMPLETED':
        return '#198754';
      default:
        return '#6c757d';
    }
  };

  const formatCurrency = (amount = 0, currency = '₹') => {
    // ensure number
    const num = typeof amount === "number" ? amount : Number(amount || 0);
    return `${currency}${num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB');
  };

  const isDueToday = (paymentDate) => {
    if (!paymentDate) return false;
    const today = new Date();
    const payment = new Date(paymentDate);
    return today.toDateString() === payment.toDateString();
  };

  // fetch payruns from backend and format for dashboard card rendering
  const fetchPayRunsForDashboard = async () => {
    try {
      setIsPayrunLoading(true);
      setPayrunFetchError(false);

      const response = await axios.get(`${GlobalConst.API_URL}/api/payruns`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });

      if (response.data && (response.data.payrollRuns || response.data.readyStatePayrollRuns)) {
        const payrollRuns = response.data.payrollRuns || [];
        const readyState = response.data.readyStatePayrollRuns;

        const formattedRuns = payrollRuns.map((run) => ({
          id: run.payrunId,
          type: getPayrollTypeDisplayName(run.type),
          status: run.status,
          netPay: run.payrollTotal || 0,
          paymentDate: run.payDate,
          employeeCount: run.noOfEmployees,
          currency: "₹",
          dueMessage: run.statusInfo || "",
          paymentDue: run.paymentDue || false,
          payPeriodStartDate: run.payPeriodStartDate,
          payPeriodEndDate: run.payPeriodEndDate,
          processingPeriod: run.processingPeriod,
          approvalType: run.approvalType,
        }));

        let allRuns = [...formattedRuns];

        if (readyState) {
          const readyStateRun = {
            id: "ready-state",
            type: getPayrollTypeDisplayName(readyState.type),
            status: readyState.status,
            netPay: readyState.payrollTotal || 0,
            paymentDate: readyState.payDate,
            employeeCount: readyState.noOfEmployees,
            currency: "₹",
            dueMessage: readyState.statusInfo || "",
            paymentDue: readyState.paymentDue || false,
            payPeriodStartDate: readyState.payPeriodStartDate,
            payPeriodEndDate: readyState.payPeriodEndDate,
            processingPeriod: readyState.processingPeriod,
            isReadyState: true
          };
          allRuns = [readyStateRun, ...formattedRuns];
        }

        setPayRunsData(allRuns);
        setPayrunFetchError(false);
      } else {
        setPayrunFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setPayrunFetchError(true);
      console.error("API Error (fetch payruns):", error);

      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load pay runs", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setIsPayrunLoading(false);
    }
  };

  useEffect(() => {
    // fetch payruns only for the payrun card(s)
    fetchPayRunsForDashboard();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Handle Create Payrun for READY status (ported)
  const handleCreatePayrun = async (payRunData) => {
    try {
      // show loader
      // (we can reuse payrun loader - but use local state)
      setIsPayrunLoading(true);

      const postData = {
        type: getBackendPayrollType(payRunData.type),
        payPeriodStartDate: payRunData.payPeriodStartDate,
        payPeriodEndDate: payRunData.payPeriodEndDate,
        payDate: payRunData.paymentDate,
        processingPeriod: payRunData.processingPeriod,
        employeeCount: payRunData.employeeCount
      };

      const response = await axios.post(`${GlobalConst.API_URL}/api/payruns`, postData, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });

      if (response.data.status === 200 || response.data.status === 201) {
        successMsg("Success", "Payrun created successfully", false);
        // refresh list
        fetchPayRunsForDashboard();
        const createdPayrun = response.data.payrollRun || response.data.data || response.data;
        const payrunId = createdPayrun?.payrunId || createdPayrun?.payRunId || createdPayrun?.payrollRunId;
        if (payrunId) {
          navigate(`/preview/${payrunId}`, { state: { payrunData: createdPayrun } });
        } else {
          errorMsg("Navigation Failed", "Created payrun id not returned by server.", true);
        }
      } else {
        errorMsg("Creation Failed", response.data?.message || "Failed to create payrun", true);
      }
    } catch (error) {
      console.error("Create Payrun Error:", error);
      if (error.response) {
        errorMsg("Creation Failed", error.response.data?.message || "Failed to create payrun", false);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setIsPayrunLoading(false);
    }
  };

  // --- Bootstrap-styled PayRunCard (keeps the same info but fits dashboard look) ---
  const PayRunCard = ({ payRun }) => {
    return (
      <div className="card shadow-sm rounded-3 mb-3" style={{ border: "1px solid #eef1f5", background: "#fff" }}>
        <div className="card-body">
          <div className="d-flex justify-content-between align-items-start mb-3">
            <div>
              <div className="fw-bold">{payRun.type}{payRun.subType && ` (${payRun.subType})`}</div>
              <div className="text-muted fs-8">{payRun.processingPeriod ? payRun.processingPeriod : ""}</div>
            </div>

            <div className="text-end">
              <span
                className="fw-bold px-2 py-1 rounded"
                style={{
                  background: `${getStatusColor(payRun.status)}22`,
                  color: getStatusColor(payRun.status),
                  fontSize: 12,
                  borderRadius: 6,
                }}
              >
                {payRun.status}
              </span>
            </div>
          </div>

          <div className="d-flex flex-column flex-md-row gap-3">
            <div className="flex-grow-1">
              <div className="d-flex justify-content-between align-items-center">
                <span className="text-muted fs-7">EMPLOYEES' NET PAY</span>
                <span className="fw-bold fs-5">{formatCurrency(payRun.netPay, payRun.currency)}</span>
              </div>

              <div className="d-flex justify-content-between align-items-center mt-2">
                <div className="text-muted fs-7">
                  <i className="bi bi-calendar3 me-2"></i>
                  PAYMENT DATE
                </div>
                <div className={isDueToday(payRun.paymentDate) ? 'text-danger fw-bold' : 'fw-semibold'}>
                  {formatDate(payRun.paymentDate)}
                </div>
              </div>

              <div className="d-flex justify-content-between align-items-center mt-2">
                <div className="text-muted fs-7">
                  <i className="bi bi-people me-2"></i>
                  NO. OF EMPLOYEES
                </div>
                <div className="fw-semibold">{payRun.employeeCount}</div>
              </div>

              <div className="d-flex justify-content-between align-items-center mt-2">
                <div className="text-muted fs-7">
                  <i className="bi bi-calendar-range me-2"></i>
                  Processing Period
                </div>
                <div className="fw-semibold">{payRun.processingPeriod}</div>
              </div>

              {payRun.dueMessage && (
                <div className="alert alert-warning mt-3 mb-0 py-2">
                  <small className="fw-semibold">{payRun.dueMessage}</small>
                </div>
              )}
            </div>

            <div className="d-flex flex-column align-items-end justify-content-between">
              <div className="d-flex flex-column align-items-end">
                {payRun.status === "READY" ? (
                  <button
                    className="btn btn-primary"
                    onClick={() => handleCreatePayrun(payRun)}
                  >
                    Create Payrun
                  </button>
                ) : (
                  <button
                    className="btn btn-outline-primary"
                    onClick={() => {
                      const navigationMap = {
                        'APPROVED': `/summary/${payRun.id}`,
                        'PROCESSED': `/summary/${payRun.id}`,
                        'COMPLETED': `/summary/${payRun.id}`,
                        'PAID': `/summary/${payRun.id}`,
                        'DRAFT': `/preview/${payRun.id}`,
                        'PENDING': `/preview/${payRun.id}`,
                        'REJECTED': `/preview/${payRun.id}`
                      };
                      const targetPath = navigationMap[payRun.status] || `/preview/${payRun.id}`;
                      navigate(targetPath);
                    }}
                  >
                    View Details
                  </button>
                )}
              </div>

              <div className="text-muted fs-8 mt-3 text-end">
                {payRun.isReadyState ? <small className="text-muted">Ready state</small> : null}
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  };

  // --- PayRun cards block to show just under overview row (keeps other dashboard cards static) ---
  const payrunSection = (
    <div className="row g-4 mb-6">
      <div className="col-12 d-flex">
        <div className="card shadow-sm rounded-3 flex-fill" style={cardBorder}>
          <div className="card-body">
            <div className="d-flex justify-content-between align-items-center mb-3">
              <div>
                <div className="fw-bolder fs-6 text-dark">Pay Runs (Overview)</div>
                <div className="text-muted fs-8">Latest pay runs and actions</div>
              </div>
              <div>
                <button className="btn btn-sm btn-light me-2" onClick={() => fetchPayRunsForDashboard()}>Refresh</button>
                {/* <button className="btn btn-sm btn-primary" onClick={() => navigate('/payruns')}>Open Pay Runs</button> */}
              </div>
            </div>

            {isPayrunLoading ? (
              <div className="text-center py-4">
                <div className="spinner-border text-primary" role="status">
                  <span className="visually-hidden">Loading...</span>
                </div>
              </div>
            ) : payrunFetchError ? (
              <div className="alert alert-danger py-2 mb-0">Failed to load pay runs.</div>
            ) : payRunsData.length === 0 ? (
              <div className="text-center py-4">
                <div className="mb-3">
                  <i className="bi bi-clock-history fs-1 text-muted"></i>
                </div>
                <div className="text-muted">No pay runs available. Create your first pay run or refresh.</div>
              </div>
            ) : (
              <div>
                {payRunsData.map((pr) => (
                  <PayRunCard key={pr.id} payRun={pr} />
                ))}
              </div>
            )}

          </div>
        </div>
      </div>
    </div>
  );

  // --- Render dashboard with integrated payrunSection placed after Overview+PaySchedule row ---
  return (
    <>
      <Helmet>
        <title>Payroll Dashboard | HRMS InfiNevoCloud</title>
      </Helmet>

      <div
        className="d-flex flex-column flex-column-fluid"
        style={{ background: "#ffffff", paddingTop: "2rem", paddingBottom: "2rem" }}
      >
        <div className="content fs-6 d-flex flex-column-fluid" id="kt_content">
          <div className="container">

            {/* Header / toolbar */}
            <div className="toolbar py-6 mb-6 bg-white shadow-sm rounded-3" id="kt_toolbar" style={{ border: "1px solid #f3f4f6" }}>
              <div className="container d-flex justify-content-between align-items-center flex-wrap">
                <div className="d-flex flex-column">
                  <h1 className="fw-bolder text-dark fs-1 mb-1">Welcome, <span className="text-primary">InfiNevoCloud</span> 👋</h1>
                  <p className="text-gray-600 fs-6 mb-0">Manage payroll, compliance & employee payouts in one place.</p>
                </div>

                <div className="text-end">
                  <h3 className="fw-bolder text-primary fs-2 mb-1">Payroll Dashboard</h3>
                  <span className="text-muted fs-7">Real-time payroll insights — beautiful & simple</span>
                </div>
              </div>
            </div>

            {/* ROW: Payroll Overview + Pay Schedule (unchanged layout) */}
            <div className="row g-4 mb-6">
              <div className="col-lg-8 d-flex">
                <div className="card card-stretch h-100 shadow-sm rounded-3 d-flex flex-column flex-fill" style={cardBorder}>
                  <div className="card-body d-flex flex-column">
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <div className="fs-7 text-muted">Overview</div>
                        <div className="fw-bolder fs-3 text-dark">Payroll Overview</div>
                        <div className="text-muted fs-8 mt-1">
                          Quick snapshot: total payroll, recent run, pending items, and quick actions
                        </div>
                      </div>

                      <div className="d-flex align-items-center">
                        <div className="me-3 text-end">
                          <div className="fs-8 text-muted">Total Payroll (YTD)</div>
                          <div className="fw-bolder fs-4 text-dark">{stats.totalCost}</div>
                          <div className="text-muted fs-8 mt-1">Last run: {stats.lastRunDate}</div>
                        </div>

                        <div>
                          <button className="btn btn-primary btn-lg me-2">Run Payroll</button>
                          <button className="btn btn-outline-primary btn-lg">Export</button>
                        </div>
                      </div>
                    </div>

                    <div className="d-flex align-items-center mt-4">
                      <div className="me-4">
                        <div className="fs-7 text-muted">Employees</div>
                        <div className="fw-bolder fs-2 text-success">{stats.employeesActive}</div>
                        <div className="text-muted fs-8 mt-1">Active this month</div>
                      </div>

                      <div style={{ width: 1, height: 60, background: "#f1f3f5", marginRight: 20 }} />

                      <div className="me-4">
                        <div className="fs-7 text-muted">Payslips Generated</div>
                        <div className="fw-bolder fs-4 text-dark">{stats.payslipGeneratedPct}%</div>
                        <div className="text-muted fs-8 mt-1">Auto / Manual combination</div>
                      </div>

                      <div style={{ flex: 1 }} />

                      <div className="text-end">
                        <div
                          className="bgi-no-repeat bgi-size-contain bgi-position-center"
                          style={{
                            width: 160,
                            height: 96,
                            backgroundImage: `url(${terms2})`,
                            backgroundRepeat: "no-repeat",
                            backgroundSize: "contain",
                            backgroundPosition: "center",
                            marginLeft: "auto",
                          }}
                        />
                      </div>
                    </div>

                    <div className="mt-4 pt-3 border-top mt-auto">
                      <div className="row g-2 text-center">
                        <div className="col">
                          <div className="fs-8 text-muted">Open Tasks</div>
                          <div className="fw-bolder fs-5 text-warning">2</div>
                        </div>
                        <div className="col">
                          <div className="fs-8 text-muted">Due Taxes</div>
                          <div className="fw-bolder fs-5 text-danger">₹3,200</div>
                        </div>
                        <div className="col">
                          <div className="fs-8 text-muted">Next Payroll</div>
                          <div className="fw-bolder fs-5 text-primary">01-May-2025</div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="col-lg-4 d-flex">
                <div className="card shadow-sm rounded-3 h-100 flex-fill d-flex flex-column" style={cardBorder}>
                  <div className="card-body d-flex flex-column">
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <div className="fw-bolder text-dark fs-5">Pay Schedule</div>
                        <div className="text-muted fs-8">Upcoming payrolls</div>
                      </div>
                      <div>
                        <a href="#" className="text-primary fs-8">View All</a>
                      </div>
                    </div>

                    <div className="d-flex align-items-center mt-4">
                      <div className="bgi-no-repeat bgi-size-contain bgi-position-center flex-shrink-0 me-3"
                        style={{
                          width: 120,
                          height: 100,
                          backgroundImage: "url('https://preview.keenthemes.com/metronic8/demo1/assets/media/illustrations/sketchy-1/2.png')",
                          backgroundRepeat: "no-repeat",
                          backgroundSize: "contain",
                          backgroundPosition: "center",
                        }}
                      />

                      <div className="flex-grow-1">
                        <div className="fw-bolder fs-6 text-dark mb-1">
                          Next Payroll: <span className="text-primary">01-May-2025</span>
                        </div>
                        <div className="text-muted fs-8 mb-2">Schedule Type: <strong>Monthly</strong></div>
                        <div className="d-flex align-items-center mt-2">
                          <span className="badge" style={{ background: "#e6f4ea", color: "#198754" }}>On Track</span>
                          <span className="fs-8 text-muted ms-2">No delays expected</span>
                        </div>
                      </div>
                    </div>

                    <div className="border-top my-4" />

                    <ul className="list-unstyled mb-0">
                      <li className="d-flex justify-content-between align-items-center py-2">
                        <div className="text-muted fs-8">June Payroll</div>
                        <span className="fw-bolder fs-8 text-dark">01-Jun-2025</span>
                      </li>
                      <li className="d-flex justify-content-between align-items-center py-2">
                        <div className="text-muted fs-8">July Payroll</div>
                        <span className="fw-bolder fs-8 text-dark">01-Jul-2025</span>
                      </li>
                      <li className="d-flex justify-content-between align-items-center py-2">
                        <div className="text-muted fs-8">August Payroll</div>
                        <span className="fw-bolder fs-8 text-dark">01-Aug-2025</span>
                      </li>
                    </ul>

                    <div className="mt-auto text-center pt-3">
                      <button className="btn btn-outline-primary btn-sm w-100">Manage Schedule</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* ===========================
                Insert payrunSection here (immediately after Overview+PaySchedule)
                =========================== */}
            {payrunSection}

            {/* ROW 2: Benefits & Deductions | Active Employees | Generate Payslip (donut) */}
            <div className="row g-4 mb-6">
              {/* Benefits & Deductions */}
              <div className="col-lg-6 d-flex">
                <div className="card shadow-sm rounded-3 h-100 flex-fill d-flex flex-column" style={cardBorder}>
                  <div className="card-body d-flex flex-column">
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <div className="fw-bolder text-dark fs-6">Benefits & Deductions</div>
                        <div className="text-muted fs-8">Summary for previous month</div>
                      </div>
                      <div>
                        <a href="#" className="text-primary fs-8">View history</a>
                      </div>
                    </div>

                    <div className="row g-3 mt-3">
                      <div className="col-sm-4 d-flex">
                        <div className="card h-100 border-0 shadow-xs rounded-3 text-center py-3 flex-fill d-flex flex-column justify-content-center" style={{ background: "#fafbfc" }}>
                          <div className="mb-2">
                            <div className="symbol symbol-40px mx-auto">
                              <span className="symbol-label bg-light-primary rounded-circle">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M12 2v20" stroke="#0d6efd" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /><circle cx="12" cy="8" r="2.4" stroke="#0d6efd" strokeWidth="1.6" /></svg>
                              </span>
                            </div>
                          </div>
                          <div className="fs-8 text-muted">EPF</div>
                          <div className="fw-bolder fs-5 mt-1">{stats.epf.value}</div>
                          <a href="#" className="d-block fs-8 mt-2 text-primary">{stats.epf.details}</a>
                        </div>
                      </div>

                      <div className="col-sm-4 d-flex">
                        <div className="card h-100 border-0 shadow-xs rounded-3 text-center py-3 flex-fill d-flex flex-column justify-content-center" style={{ background: "#fafbfc" }}>
                          <div className="mb-2">
                            <div className="symbol symbol-40px mx-auto">
                              <span className="symbol-label bg-light-success rounded-circle">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M12 3v18" stroke="#2ca84a" strokeWidth="1.6" strokeLinecap="round" /><path d="M6 8h12" stroke="#2ca84a" strokeWidth="1.6" strokeLinecap="round" /></svg>
                              </span>
                            </div>
                          </div>
                          <div className="fs-8 text-muted">ESI</div>
                          <div className="fw-bolder fs-5 mt-1">{stats.esi.value}</div>
                          <a href="#" className="d-block fs-8 mt-2 text-primary">{stats.esi.details}</a>
                        </div>
                      </div>

                      <div className="col-sm-4 d-flex">
                        <div className="card h-100 border-0 shadow-xs rounded-3 text-center py-3 flex-fill d-flex flex-column justify-content-center" style={{ background: "#fafbfc" }}>
                          <div className="mb-2">
                            <div className="symbol symbol-40px mx-auto">
                              <span className="symbol-label bg-light-warning rounded-circle">
                                <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><rect x="4" y="4" width="16" height="16" stroke="#ffb020" strokeWidth="1.6" rx="3" /></svg>
                              </span>
                            </div>
                          </div>
                          <div className="fs-8 text-muted">TDS</div>
                          <div className="fw-bolder fs-5 mt-1">{stats.tds.value}</div>
                          <a href="#" className="d-block fs-8 mt-2 text-primary">{stats.tds.details}</a>
                        </div>
                      </div>
                    </div>

                    <div className="mt-auto d-flex justify-content-end">
                      <a className="btn btn-sm btn-light" href="#">Open full report</a>
                    </div>
                  </div>
                </div>
              </div>

              {/* Active Employees */}
              <div className="col-lg-3 d-flex">
                <div className="card h-100 shadow-sm rounded-3 text-center d-flex flex-column justify-content-center flex-fill" style={cardBorder}>
                  <div className="card-body d-flex flex-column justify-content-center">
                    <div className="mb-3">
                      <div className="symbol symbol-60px mx-auto">
                        <span className="symbol-label bg-light-info rounded-circle">
                          <svg width="28" height="28" viewBox="0 0 24 24" fill="none"><path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" stroke="#39afd1" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /><path d="M4 20a8 8 0 0 1 16 0" stroke="#39afd1" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
                        </span>
                      </div>
                    </div>

                    <div className="fs-8 text-muted">ACTIVE EMPLOYEES</div>
                    <div className="fw-bolder fs-2 text-success mt-2">{stats.employeesActive}</div>
                    <a href="#" className="d-block fs-8 mt-3 text-primary">View Employees</a>
                  </div>
                </div>
              </div>

              {/* Generate Payslip (donut chart) */}
              <div className="col-lg-3 d-flex">
                <div className="card h-100 shadow-sm rounded-3 flex-fill d-flex flex-column" style={cardBorder}>
                  <div className="card-body d-flex flex-column">
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <div className="fw-bolder text-dark fs-6">Payslip Generation</div>
                        <div className="text-muted fs-8">This payroll cycle</div>
                      </div>
                      <div>
                        <a href="#" className="text-primary fs-8">Details</a>
                      </div>
                    </div>

                    <div className="d-flex align-items-center mt-3">
                      <div style={{ width: 100 }}>
                        <Chart
                          options={{
                            chart: { id: "payslip-donut", sparkline: { enabled: true } },
                            labels: ["Generated", "Pending"],
                            colors: ["#50cd89", "#e6e9ef"],
                            legend: { show: false },
                            dataLabels: { enabled: false },
                          }}
                          series={[stats.payslipGeneratedPct, 100 - stats.payslipGeneratedPct]}
                          type="donut"
                          width="100"
                        />
                      </div>

                      <div className="ms-3">
                        <div className="fw-bolder fs-4">{stats.payslipGeneratedPct}%</div>
                        <div className="text-muted fs-8">Generated</div>
                        <div className="mt-2">
                          <button className="btn btn-sm btn-outline-primary">Generate Payslips</button>
                        </div>
                      </div>
                    </div>

                    <div className="mt-auto d-flex justify-content-end">
                      <a className="btn btn-sm btn-light" href="#">View all</a>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Middle row: Payroll Cost Summary chart + Quick Actions & small details */}
            <div className="row g-4 mb-6">
              <div className="col-xl-9 d-flex">
                <div className="card shadow-sm rounded-3 h-100 flex-fill" style={cardBorder}>
                  <div className="card-header d-flex align-items-center justify-content-between border-0 pt-4 pb-0">
                    <div>
                      <div className="fw-bolder text-dark fs-6">Payroll Cost Summary</div>
                      <div className="text-muted fs-8">This Year</div>
                    </div>
                    <div className="text-muted fs-8">View: <strong className="text-dark">Year</strong></div>
                  </div>

                  <div className="card-body">
                    <div className="border rounded p-3" style={{ minHeight: 300, background: "#fff" }}>
                      <Chart
                        options={{
                          chart: {
                            id: "payroll-area",
                            type: "area",
                            toolbar: { show: false },
                            zoom: { enabled: false },
                            animations: { enabled: true },
                            foreColor: "#6b7280"
                          },
                          colors: ["#50cd89"],
                          stroke: { curve: "smooth", width: 3 },
                          fill: {
                            type: "gradient",
                            gradient: {
                              shade: "light",
                              type: "vertical",
                              shadeIntensity: 0.5,
                              gradientToColors: ["#ffffff"],
                              inverseColors: false,
                              opacityFrom: 0.45,
                              opacityTo: 0,
                              stops: [0, 80, 100]
                            }
                          },
                          markers: { size: 4, colors: ["#50cd89"], strokeColors: "#ffffff", strokeWidth: 2, hover: { size: 6 } },
                          grid: { borderColor: "#e6e9ef", strokeDashArray: 4, xaxis: { lines: { show: false } }, yaxis: { lines: { show: true } } },
                          yaxis: {
                            labels: {
                              style: { colors: "#99a1b7", fontSize: "12px" },
                              formatter: (val) => {
                                if (Math.abs(val) >= 1000) return `₹${(val / 1000).toFixed(1)}K`;
                                return `₹${val}`;
                              }
                            },
                            tickAmount: 4,
                          },
                          xaxis: {
                            categories: [
                              "Apr 2025", "May 2025", "Jun 2025", "Jul 2025",
                              "Aug 2025", "Sep 2025", "Oct 2025", "Nov 2025",
                              "Dec 2025", "Jan 2026", "Feb 2026", "Mar 2026"
                            ],
                            labels: { style: { colors: "#9aa0a6", fontSize: "11px" } },
                            axisBorder: { show: false },
                          },
                          tooltip: { theme: "light", y: { formatter: (val) => `₹${Number(val).toLocaleString()}` } },
                          legend: { show: false },
                          dataLabels: { enabled: false },
                        }}
                        series={[
                          { name: "Payroll Cost", data: [10000, 15000, 12000, 18000, 20000, 17000, 22000, 21000, 24000, 23000, 20000, 25000] }
                        ]}
                        type="area"
                        height="300"
                      />
                    </div>
                  </div>
                </div>
              </div>

              {/* Quick actions / Upcoming payments + PaySchedule & Tax details */}
              <div className="col-xl-3 d-flex">
                <div className="card shadow-sm rounded-3 h-100 flex-fill" style={cardBorder}>
                  <div className="card-body d-flex flex-column">
                    <div className="d-grid gap-2 mb-3">
                      {/* <button className="btn btn-primary btn-lg">Run Payroll</button> */}
                      <button className="btn btn-outline-primary btn-lg">Generate Payslips</button>
                    </div>

                    <div className="mt-3">
                      <div className="fw-bolder mb-2">Upcoming Payments</div>
                      <ul className="list-unstyled mb-3">
                        {upcomingPayments.map((u, idx) => (
                          <li key={idx} className="mb-2">
                            <div className="d-flex justify-content-between align-items-start">
                              <div>
                                <div className="fw-bolder">{u.title}</div>
                                <div className="text-muted fs-8">{u.date}</div>
                              </div>
                              <div className="fw-bolder">{u.amount}</div>
                            </div>
                          </li>
                        ))}
                      </ul>

                      <div className="fw-bolder mb-2">Pay Schedule</div>
                      <div className="text-muted fs-8 mb-3">Next payroll: <strong className="text-dark">01-May-2025</strong></div>

                      <div className="fw-bolder mb-2">Tax Details</div>
                      <div className="text-muted fs-8">TDS due next: <strong className="text-danger">₹3,200</strong></div>
                    </div>

                    <div className="mt-auto text-center">
                      <a className="btn btn-sm btn-light" href="#">View all schedules</a>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Bottom row: Recent Pay Runs table */}
            <div className="row g-4">
              <div className="col-12 d-flex">
                <div className="card shadow-sm rounded-3 flex-fill" style={cardBorder}>
                  <div className="card-header border-0 pt-4 pb-0 d-flex align-items-center">
                    <h3 className="card-title fw-bolder text-dark fs-5">Recent Pay Runs</h3>
                    <div className="ms-auto">
                      <a className="btn btn-sm btn-light" href="#">View all</a>
                    </div>
                  </div>

                  <div className="card-body pt-2">
                    <div className="table-responsive">
                      <table className="table table-hover align-middle">
                        <thead>
                          <tr>
                            <th style={{ width: 60 }}>#</th>
                            <th>Pay Run ID</th>
                            <th>Period</th>
                            <th>Date</th>
                            <th>Status</th>
                            <th className="text-end">Amount</th>
                          </tr>
                        </thead>
                        <tbody>
                          {recentPayRuns.map((r, i) => (
                            <tr key={r.id}>
                              <td>{i + 1}</td>
                              <td><a href="#" className="text-dark fw-bolder">{r.id}</a></td>
                              <td>{r.period}</td>
                              <td>{r.date}</td>
                              <td>
                                <span className={`badge ${r.status === "Completed" ? "badge-light-success" : "badge-light-warning"}`}>
                                  {r.status}
                                </span>
                              </td>
                              <td className="text-end fw-bolder">{r.amount}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>

                </div>
              </div>
            </div>

          </div> {/* container */}
        </div> {/* content */}
      </div> {/* flex */}

      {/* global loader when payrun creation/fetch is in progress */}
      {isPayrunLoading && <Loader />}
    </>
  );
}
