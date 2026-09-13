import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import axios from "axios";
import { FiEdit } from "react-icons/fi";
import { useNavigate } from "react-router-dom";
import { errorMsg } from "../../../../shared/helpers/msgHelper";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";

export default function PayScheduleView() {
  const [payData, setPayData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isEditable, setIsEditable] = useState(true);
  const navigate = useNavigate();
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  const formatWorkingDays = (days) => {
    const dayMap = {
      mon: "Mon", tue: "Tue", wed: "Wed", 
      thu: "Thu", fri: "Fri", sat: "Sat", sun: "Sun"
    };
    return days ? days.map(day => dayMap[day] || day).join(", ") : "";
  };

  const formatPayDay = (payDay) => {
    if (payDay === "last_working_day") return "Last working day of the month";
    if (!isNaN(payDay)) return `${payDay}${getOrdinalSuffix(parseInt(payDay))} of every month`;
    return payDay || "";
  };

  const getOrdinalSuffix = (day) => {
    const j = day % 10, k = day % 100;
    if (j === 1 && k !== 11) return "st";
    if (j === 2 && k !== 12) return "nd";
    if (j === 3 && k !== 13) return "rd";
    return "th";
  };

  const formatMonthYear = (dateString) => {
    if (!dateString) return "";
    const date = new Date(dateString);
    return date.toLocaleString("default", { month: "long" }) + " " + date.getFullYear();
  };

  const formatDate = (dateString) => {
    if (!dateString) return "";
    const date = new Date(dateString);
    return date.toLocaleDateString("en-GB");
  };

  const parseLocalDate = (yyyyMmDd) => {
  // yyyy-mm-dd -> local Date at midnight
  const [y, m, d] = yyyyMmDd.split("-").map(Number);
  return new Date(y, m - 1, d);
};


const formatMonthLabel = (year, monthIdx) => {
  const temp = new Date(year, monthIdx, 1);
  const monthName = temp.toLocaleString("default", { month: "long" });
  return `${monthName}-${year}`;
};

const getDaysInMonth = (year, monthIdx) => {
  return new Date(year, monthIdx + 1, 0).getDate();
};

const workingDaySetFrom = (workingDays = []) => {
  // Map your "mon".."sun" array to 0..6 weekdays (Sun=0..Sat=6)
  const map = { sun: 0, mon: 1, tue: 2, wed: 3, thu: 4, fri: 5, sat: 6 };
  const set = new Set();
  workingDays.forEach((d) => {
    if (map[d] !== undefined) set.add(map[d]);
  });
  // Fallback: if none provided, assume Mon-Fri
  if (set.size === 0) [1,2,3,4,5].forEach((d) => set.add(d));
  return set;
};

const isWorkingDay = (date, workingDaySet, includeWeekends) => {
  if (includeWeekends) return true;
  return workingDaySet.has(date.getDay());
};

const adjustToPrevWorkingDay = (date, workingDaySet, includeWeekends) => {
  if (includeWeekends) return date;
  const d = new Date(date);
  while (!isWorkingDay(d, workingDaySet, false)) {
    d.setDate(d.getDate() - 1);
  }
  return d;
};

const computeMonthlyPayDate = (year, monthIdx, payData) => {
  const workingDaySet = workingDaySetFrom(payData.workingDays);
  const includeWeekends = !!payData.includeWeekends;

  if (payData.payDay === "last_working_day") {
    // Last calendar day of month, then back up to a working day if needed
    const last = new Date(year, monthIdx + 1, 0);
    return adjustToPrevWorkingDay(last, workingDaySet, includeWeekends);
  }

  // Numeric day-of-month
  const dayNum = parseInt(payData.payDay, 10);
  const maxDay = getDaysInMonth(year, monthIdx);
  const targetDay = Math.min(isNaN(dayNum) ? maxDay : dayNum, maxDay);
  const candidate = new Date(year, monthIdx, targetDay);

  // If weekends excluded and this day isn't working, back up
  return adjustToPrevWorkingDay(candidate, workingDaySet, includeWeekends);
};

const maxDate = (a, b) => (a.getTime() >= b.getTime() ? a : b);

  // Calculate upcoming payrolls based on pay schedule
  // const getUpcomingPayrolls = (payData) => {
  //   if (!payData) return [];
    
  //   const upcoming = [];
  //   const currentDate = new Date();
    
  //   // Generate next 3 payrolls
  //   for (let i = 0; i < 3; i++) {
  //     const month = new Date(currentDate.getFullYear(), currentDate.getMonth() + i, 1);
  //     const monthName = month.toLocaleString("default", { month: "long" });
  //     const year = month.getFullYear();
      
  //     let payDate;
  //     if (payData.payDay === "last_working_day") {
  //       // Calculate last working day of the month
  //       const lastDay = new Date(year, month.getMonth() + 1, 0);
  //       payDate = new Date(lastDay);
  //       while (payDate.getDay() === 0 || payDate.getDay() === 6) {
  //         payDate.setDate(payDate.getDate() - 1);
  //       }
  //     } else {
  //       // Specific day of month
  //       payDate = new Date(year, month.getMonth(), parseInt(payData.payDay));
  //       // Adjust if weekend
  //       while (payDate.getDay() === 0 || payDate.getDay() === 6) {
  //         payDate.setDate(payDate.getDate() - 1);
  //       }
  //     }
      
  //     upcoming.push({
  //       month: `${monthName}-${year}`,
  //       payDate: formatDate(payDate)
  //     });
  //   }
    
  //   return upcoming;
  // };


  // Assumes you already have:
// - parseLocalDate
// - computeMonthlyPayDate(year, monthIdx, payData)
// - formatMonthLabel(year, monthIdx)
// - formatDate(date)

const getUpcomingPayrolls = (payData) => {
  if (!payData) return [];

  // Base period month = the month user selected as "Start your first payroll from"
  const baseMonthDate = payData.payPeriodStartDate
    ? parseLocalDate(payData.payPeriodStartDate) // e.g., 2025-06-01
    : parseLocalDate(payData.payDate);           // fallback

  const baseYear = baseMonthDate.getFullYear();
  const baseMonth = baseMonthDate.getMonth(); // 0..11

  const results = [];

  // We want the next 3 pay PERIODS starting from the base period
  for (let i = 0; i < 3; i++) {
    // period month label (June, July, August, ...)
    const periodAbsMonth = baseMonth + i;
    const periodYear = baseYear + Math.floor(periodAbsMonth / 12);
    const periodMonthIdx = ((periodAbsMonth % 12) + 12) % 12;

    // pay date is in the NEXT month for that period
    const payAbsMonth = periodAbsMonth + 1;
    const payYear = baseYear + Math.floor(payAbsMonth / 12);
    const payMonthIdx = ((payAbsMonth % 12) + 12) % 12;

    const payDate = computeMonthlyPayDate(payYear, payMonthIdx, payData);

    results.push({
      // LABEL with the PERIOD month (the month worked)
      month: formatMonthLabel(periodYear, periodMonthIdx),
      // SHOW the computed PAY DATE (usually in next month)
      payDate: formatDate(payDate),
    });
  }

  return results;
};



  const fetchPaySchedule = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/paySchedule`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        }
      });
      
      setPayData(response.data);
      
      // Check if pay schedule is editable (no pay runs processed yet)
      // This would need to be implemented based on your business logic
      setIsEditable(true);
      
    } catch (err) {
      if (err.response && err.response.status === 404) {
        setError("No pay schedule found. Please create one first.");
        navigate("/pay-schedules");
      } else {
        setError("Failed to load pay schedule data");
        console.error(err);
        errorMsg("Error", "Failed to load pay schedule", false);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPaySchedule();
  }, []);

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center min-vh-100">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="alert alert-danger m-5">
        {error}
      </div>
    );
  }

  if (!payData) {
    return (
      <div className="alert alert-info m-5">
        No pay schedule data available
      </div>
    );
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Pay Schedule</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Pay Schedule</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "800px" }}>
              <div className="card shadow-sm">
                <div className="card-body p-5">
                  {isEditable && (
                    <div className="alert alert-warning">
                      <i className="bi bi-exclamation-triangle-fill me-2"></i>
                      Pay Schedule cannot be edited once you process the first pay run.
                    </div>
                  )}

                  <div className="d-flex justify-content-between align-items-center mb-4">
                    <h5 className="mb-0 fw-bold">
                      This Organisation's payroll runs on this schedule.
                    </h5>
                    
                    {isEditable && (
                      <button 
                        className="btn btn-sm btn-outline-primary d-flex align-items-center gap-2"
                        onClick={() => navigate(`/pay-schedules/edit/${payData.payScheduleId}`, { state: { payData } })}
                      >
                        <FiEdit size={16} />
                        Edit
                      </button>
                    )}
                  </div>

                  <div className="row mb-4">
                    <div className="col-md-6">
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-1">Pay Frequency</label>
                        <div className="fw-bold">Every month</div>
                      </div>
                    </div>
                    <div className="col-md-6">
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-1">Working Days</label>
                        <div className="fw-bold">
                          {formatWorkingDays(payData.workingDays)}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="row mb-4">
                    <div className="col-md-6">
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-1">Pay Day</label>
                        <div className="fw-bold">
                          {formatPayDay(payData.payDay)}
                        </div>
                      </div>
                    </div>
                    <div className="col-md-6">
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-1">Salary Calculation</label>
                        <div className="fw-bold">
                          {payData.workingDaysCalculationType === "actual_days" 
                            ? "Actual days in month" 
                            : `Organization working days (${payData.noOfWorkingDays} days)`}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="row mb-4">
                    <div className="col-md-6">
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-1">First Pay Period</label>
                        <div className="fw-bold">
                          {formatMonthYear(payData.payPeriodStartDate)}
                        </div>
                      </div>
                    </div>
                    <div className="col-md-6">
                      <div className="mb-3">
                        <label className="form-label text-muted small mb-1">First Pay Date</label>
                        <div className="fw-bold">
                          {formatDate(payData.payDate)}
                        </div>
                      </div>
                    </div>
                  </div>

                  <hr className="my-4 border-2" />

                  <h5 className="mb-4 fw-bold">Upcoming Payrolls</h5>
                  
                  <div className="list-group">
                    {getUpcomingPayrolls(payData).map((payroll, index) => (
                      <div key={index} className="list-group-item border-0 px-0 py-2">
                        <div className="d-flex justify-content-between align-items-center">
                          <div className="fw-bold">{payroll.month}</div>
                          <div className="text-muted">Pay Date: {payroll.payDate}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}