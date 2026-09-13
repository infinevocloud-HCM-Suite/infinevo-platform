import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { useParams, useLocation, useNavigate } from "react-router-dom";

export default function EditPaySchedule() {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const [monthsList, setMonthsList] = useState([]);


  const weekDays = [
    { key: "sun", label: "SUN" }, { key: "mon", label: "MON" }, { key: "tue", label: "TUE" },
    { key: "wed", label: "WED" }, { key: "thu", label: "THU" }, { key: "fri", label: "FRI" },
    { key: "sat", label: "SAT" },
  ];

  // const monthsList = [
  //   { value: "2025-04", label: "April-2025" }, { value: "2025-05", label: "May-2025" },
  //   { value: "2025-06", label: "June-2025" },  { value: "2025-07", label: "July-2025" },
  //   { value: "2025-08", label: "August-2025" },{ value: "2025-09", label: "September-2025" },
  //   { value: "2025-10", label: "October-2025" },{ value: "2025-11", label: "November-2025" },
  // ];



  // Generate months dynamically from April (FY start) to May (next FY)
  const generatePayrollMonths = () => {
    const months = [];
    const now = new Date();

    const currentYear = now.getFullYear();
    const currentMonth = now.getMonth(); // 0-based

    // Financial year logic
    const fyStartYear = currentMonth >= 3 ? currentYear : currentYear - 1;
    const startDate = new Date(fyStartYear, 3, 1); // April
    const endDate = new Date(fyStartYear + 1, 4, 1); // May next year

    const temp = new Date(startDate);

    while (temp <= endDate) {
      const year = temp.getFullYear();
      const month = temp.getMonth() + 1;

      months.push({
        value: `${year}-${String(month).padStart(2, "0")}`,
        label: `${temp.toLocaleString("default", { month: "long" })}-${year}`,
      });

      temp.setMonth(temp.getMonth() + 1);
    }

    return months;
  };


  const [signingIn, setSigningIn] = useState(false);
  const [initialValues, setInitialValues] = useState({
    workingDays: [],
    workingDaysCalculationType: "actual_days",
    noOfWorkingDays: "",
    payDay: "last_working_day",
    payDaySpecific: "",
    payPeriodStartDate: "",
    firstPayDate: "",
    payPeriodYear: "",
    payPeriodMonth: "",
  });

  const [originalData, setOriginalData] = useState(null);

  useEffect(() => {
    if (location.state && location.state.payData) {
      const payData = location.state.payData;
      setOriginalData(payData);

      // Map backend data to form values
      const isSpecificDay = !isNaN(payData.payDay) && payData.payDay !== "last_working_day";

      setInitialValues({
        workingDays: payData.workingDays || [],
        workingDaysCalculationType: payData.workingDaysCalculationType || "actual_days",
        noOfWorkingDays: payData.noOfWorkingDays?.toString() || "",
        payDay: isSpecificDay ? "specific_day" : "last_working_day",
        payDaySpecific: isSpecificDay ? payData.payDay.toString() : "",
        payPeriodStartDate: payData.payPeriodStartDate
          ? new Date(payData.payPeriodStartDate).toISOString().substring(0, 7)
          : "",
        firstPayDate: payData.payDate
          ? new Date(payData.payDate).toISOString().substring(0, 10)
          : "",
        payPeriodYear: payData.payPeriodStartDate
          ? new Date(payData.payPeriodStartDate).getFullYear().toString()
          : "",

        payPeriodMonth: payData.payPeriodStartDate
          ? String(new Date(payData.payPeriodStartDate).getMonth() + 1).padStart(2, "0")
          : "",
      });
    } else {
      // If no data passed, fetch from API
      fetchPaySchedule();
    }
  }, [id, location.state]);

  useEffect(() => {
    setMonthsList(generatePayrollMonths());
  }, []);


  const fetchPaySchedule = async () => {
    try {
      setSigningIn(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/paySchedule/${id}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        }
      });

      const payData = response.data;
      setOriginalData(payData);

      // Map backend data to form values
      const isSpecificDay = !isNaN(payData.payDay) && payData.payDay !== "last_working_day";

      setInitialValues({
        workingDays: payData.workingDays || [],
        workingDaysCalculationType: payData.workingDaysCalculationType || "actual_days",
        noOfWorkingDays: payData.noOfWorkingDays?.toString() || "",
        payDay: isSpecificDay ? "specific_day" : "last_working_day",
        payDaySpecific: isSpecificDay ? payData.payDay.toString() : "",
        payPeriodStartDate: payData.payPeriodStartDate
          ? new Date(payData.payPeriodStartDate).toISOString().substring(0, 7)
          : "",
        firstPayDate: payData.payDate
          ? new Date(payData.payDate).toISOString().substring(0, 10)
          : ""
      });

    } catch (error) {
      console.error("Error fetching pay schedule:", error);
      errorMsg("Error", "Failed to load pay schedule data", false);
      navigate("/pay-schedules/view");
    } finally {
      setSigningIn(false);
    }
  };

  const getLastWorkingDay = (year, monthIndex) => {
    const last = new Date(year, monthIndex + 1, 0);
    const d = new Date(last);
    while (d.getDay() === 0 || d.getDay() === 6) {
      d.setDate(d.getDate() - 1);
    }
    return d;
  };

  const adjustToPreviousWorkingDay = (date) => {
    const d = new Date(date);
    while (d.getDay() === 0 || d.getDay() === 6) {
      d.setDate(d.getDate() - 1);
    }
    return d;
  };

  const formatShort = (date) => {
    const d = new Date(date);
    const dd = String(d.getDate()).padStart(2, "0");
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const yy = String(d.getFullYear()).slice(-2);
    return `${dd}/${mm}/${yy}`;
  };
  // Add this helper near your other helpers
  const labelForNextMonth = (ym) => {
    if (!ym) return "-";
    const [y, m] = ym.split("-").map(Number);
    const d = new Date(y, (m - 1) + 1, 1); // next month
    const monthName = d.toLocaleString("default", { month: "long" });
    return `${monthName}-${d.getFullYear()}`;
  };


  // const computeFirstPayrollOptions = (startMonthValue, payOn, payDay) => {
  //   if (!startMonthValue) return [];

  //   const [y, m] = startMonthValue.split("-");
  //   const year = parseInt(y, 10);
  //   const monthIndex = parseInt(m, 10) - 1;

  //   if (payOn === "last_working_day") {
  //     const dThis = getLastWorkingDay(year, monthIndex);
  //     const dNext = getLastWorkingDay(year, monthIndex + 1);
  //     return [
  //       { value: formatISODate(dThis), label: formatShort(dThis) },
  //       { value: formatISODate(dNext), label: formatShort(dNext) },
  //     ];
  //   }

  //   if (payOn === "specific_day") {
  //     if (!payDay) return [];
  //     const candidate = new Date(year, monthIndex, parseInt(payDay, 10));
  //     const adjusted = adjustToPreviousWorkingDay(candidate);
  //     return [{ value: formatISODate(adjusted), label: formatShort(adjusted) }];
  //   }

  //   return [];
  // };
  // const computeFirstPayrollOptions = (startMonthValue, payOn, payDay) => {
  //   if (!startMonthValue) return [];

  //   const [y, m] = startMonthValue.split("-").map(Number);
  //   // Move to NEXT month
  //   const nextMonthDate = new Date(y, (m - 1) + 1, 1);
  //   const year = nextMonthDate.getFullYear();
  //   const monthIndex = nextMonthDate.getMonth(); // 0..11

  //   if (payOn === "last_working_day") {
  //     const dNext = getLastWorkingDay(year, monthIndex);
  //     return [{ value: formatISODate(dNext), label: formatShort(dNext) }];
  //   }

  //   if (payOn === "specific_day") {
  //     if (!payDay) return [];
  //     const candidate = new Date(year, monthIndex, parseInt(payDay, 10));
  //     const adjusted = adjustToPreviousWorkingDay(candidate);
  //     return [{ value: formatISODate(adjusted), label: formatShort(adjusted) }];
  //   }

  //   return [];
  // };

  const computeFirstPayrollOptions = (startMonthValue, payOn, payDay) => {
    if (!startMonthValue) return [];

    const [year, month] = startMonthValue.split("-").map(Number);
    const monthIndex = month - 1; // CURRENT month ✅

    if (payOn === "last_working_day") {
      const d = getLastWorkingDay(year, monthIndex);
      return [{ value: formatISODate(d), label: formatShort(d) }];
    }

    if (payOn === "specific_day") {
      if (!payDay) return [];
      const candidate = new Date(year, monthIndex, parseInt(payDay, 10));
      const adjusted = adjustToPreviousWorkingDay(candidate);
      return [{ value: formatISODate(adjusted), label: formatShort(adjusted) }];
    }

    return [];
  };


  const payDayOptions = Array.from({ length: 28 }, (_, i) => i + 1);
  const dayOptions = Array.from({ length: 11 }, (_, i) => i + 20);

  const validationSchema = Yup.object().shape({
    workingDays: Yup.array()
      .min(1, "Select at least one working day"),
    workingDaysCalculationType: Yup.string()
      .required("Please select salary calculation type"),
    noOfWorkingDays: Yup.string().when("workingDaysCalculationType", {
      is: "org_days",
      then: (schema) => schema.required("Please select working days"),
      otherwise: (schema) => schema.nullable(),
    }),
    payDay: Yup.string()
      .required("Please select pay day option"),
    payDaySpecific: Yup.string().when("payDay", {
      is: "specific_day",
      then: (schema) => schema.required("Please select a day of the month"),
      otherwise: (schema) => schema.nullable(),
    }),
    payPeriodStartDate: Yup.string()
      .required("Start month is required"),

    payPeriodYear: Yup.string().required("Year is required"),
    payPeriodMonth: Yup.string().required("Month is required"),
    firstPayDate: Yup.string().when("payPeriodStartDate", {
      is: (v) => !!v,
      then: (schema) => schema.required("Select a pay date for your first payroll"),
      otherwise: (schema) => schema.nullable(),

    }),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    if (!_.isEmpty(values.workingDays) && !_.isEmpty(values.payPeriodStartDate)) {
      setSubmitting(true);
      setSigningIn(true);

      try {
        // Map frontend values to backend DTO structure
        const payScheduleDTO = {
          payScheduleId: id,
          payScheduleType: "monthly",
          payDay: values.payDay === "specific_day" ? values.payDaySpecific : values.payDay,
          payPeriodStartDate: new Date(values.payPeriodStartDate + "-01"),
          payPeriodEndDate: new Date(new Date(values.payPeriodStartDate + "-01").getFullYear(),
            new Date(values.payPeriodStartDate + "-01").getMonth() + 1, 0),
          payDate: new Date(values.firstPayDate),
          workingDays: values.workingDays,
          noOfWorkingDays: values.workingDaysCalculationType === "org_days" ? parseInt(values.noOfWorkingDays) : values.workingDays.length,
          includeHolidays: false,
          includeWeekends: values.workingDays.includes("sat") || values.workingDays.includes("sun"),
          workingDaysCalculationType: values.workingDaysCalculationType
        };

        console.log("PaySchedule ID before API call:", id);

        const response = await axios.put(
          `${GlobalConst.API_URL}/api/paySchedule/${id}`,
          payScheduleDTO,
          {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            },
          }
        );

        if (response.status === 200) {
          successMsg("Success", "Pay schedule updated successfully", false);
          navigate("/pay-schedules/view");
        } else {
          errorMsg(
            "Update Failed",
            `There was an error updating the pay schedule. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
            true
          );
        }
      } catch (e) {
        if (!_.isEmpty(e?.response?.data)) {
          errorMsg("Update Failed", e.response.data.message || "Failed to update pay schedule", false);
        } else {
          errorMsg(e.code, e.message, true);
        }
      } finally {
        setSubmitting(false);
        setSigningIn(false);
      }
    }
  };

  const formatISODate = (date) => {
    const d = new Date(date);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  };

  function SmallCalendar({ selectedIso }) {
    if (!selectedIso) return null;

    const [yy, mm, dd] = selectedIso.split("-").map(Number);
    const selected = new Date(yy, mm - 1, dd);

    const year = selected.getFullYear();
    const month = selected.getMonth();
    const firstDay = new Date(year, month, 1).getDay();
    const lastDate = new Date(year, month + 1, 0).getDate();

    const weeks = [];
    let day = 1 - firstDay;

    while (day <= lastDate) {
      const week = [];
      for (let i = 0; i < 7; i++, day++) {
        if (day > 0 && day <= lastDate) {
          week.push(day);
        } else {
          week.push(null);
        }
      }
      weeks.push(week);
    }

    return (
      <div className="small-calendar border p-2" style={{ maxWidth: 260 }}>
        <div className="d-flex justify-content-between align-items-center mb-1">
          <strong>
            {selected.toLocaleString("default", { month: "long" })} {year}
          </strong>
        </div>

        <div className="d-flex justify-content-between text-muted small mb-1">
          {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((d) => (
            <div key={d} style={{ width: 28, textAlign: "center" }}>
              {d}
            </div>
          ))}
        </div>

        {weeks.map((week, wi) => (
          <div className="d-flex mb-1" key={wi}>
            {week.map((d, i) => (
              <div
                key={i}
                style={{
                  width: 28,
                  height: 28,
                  lineHeight: "28px",
                  textAlign: "center",
                  marginRight: 6,
                }}
              >
                {d ? (
                  <div
                    className={
                      d === selected.getDate()
                        ? "fw-bold rounded-circle d-inline-block"
                        : "d-inline-block"
                    }
                    style={{
                      width: 28,
                      height: 28,
                      lineHeight: "28px",
                      borderRadius: 14,
                      background: d === selected.getDate() ? "#dbefff" : "transparent",
                      color: d === selected.getDate() ? "#034f84" : "inherit",
                    }}
                  >
                    {d}
                  </div>
                ) : (
                  <div style={{ width: 28, height: 28 }} />
                )}
              </div>
            ))}
          </div>
        ))}
      </div>
    );
  }



  // const yearList = [];
  // const currentYear = new Date().getFullYear();

  // for (let i = currentYear - 1; i <= currentYear + 2; i++) {
  //   yearList.push(i);
  // }


  const generateYearList = () => {
    const years = [];
    const currentYear = new Date().getFullYear();

    for (let i = 1950; i <= currentYear; i++) {
      years.push(i.toString());
    }

    return years.reverse(); // latest year on top (better UX)
  };

  const yearList = generateYearList();

  const monthList = [
    { value: "01", label: "January" },
    { value: "02", label: "February" },
    { value: "03", label: "March" },
    { value: "04", label: "April" },
    { value: "05", label: "May" },
    { value: "06", label: "June" },
    { value: "07", label: "July" },
    { value: "08", label: "August" },
    { value: "09", label: "September" },
    { value: "10", label: "October" },
    { value: "11", label: "November" },
    { value: "12", label: "December" }
  ];

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Pay Schedule</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Pay Schedule</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "600px" }}>
              {originalData ? (
                <Formik
                  initialValues={initialValues}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize
                >
                  {({ isSubmitting, errors, touched, values, setFieldValue }) => (
                    <Form className="form w-100">
                      <div className="mb-4">
                        <label className="form-label fs-6 fw-bold text-dark">
                          Select your work week <span className="text-danger">*</span>
                        </label>

                        <div className="d-flex flex-wrap gap-2 mt-2">
                          {weekDays.map((day) => (
                            <Field name="workingDays" key={day.key}>
                              {({ field, form }) => {
                                const isSelected = form.values.workingDays.includes(day.key);

                                const toggleDay = () => {
                                  let updatedDays = [...form.values.workingDays];
                                  if (isSelected) {
                                    updatedDays = updatedDays.filter((d) => d !== day.key);
                                  } else {
                                    updatedDays.push(day.key);
                                  }
                                  form.setFieldValue("workingDays", updatedDays);
                                };

                                return (
                                  <button
                                    type="button"
                                    className={`btn px-3 py-2 fw-semibold ${isSelected ? "btn-primary" : "btn-outline-secondary"
                                      }`}
                                    onClick={toggleDay}
                                  >
                                    {day.label}
                                  </button>
                                );
                              }}
                            </Field>
                          ))}
                        </div>

                        <ErrorMessage
                          name="workingDays"
                          component="div"
                          className="invalid-feedback d-block"
                        />
                      </div>

                      <div className="mb-4">
                        <label className="form-label fs-6 fw-bold text-dark">
                          Calculate monthly salary based on <span className="text-danger">*</span>
                          <i
                            className="bi bi-info-circle ms-2"
                            title="Info will be added here later"
                          ></i>
                        </label>

                        <div className="d-flex flex-column gap-3 mt-2">
                          <div className="form-check">
                            <Field
                              type="radio"
                              name="workingDaysCalculationType"
                              value="actual_days"
                              className="form-check-input"
                            />
                            <label className="form-check-label text-dark fw-semibold">
                              Actual days in a month
                            </label>
                          </div>

                          <div className="form-check d-flex align-items-center gap-2">
                            <Field
                              type="radio"
                              name="workingDaysCalculationType"
                              value="org_days"
                              className="form-check-input"
                            />
                            <label className="form-check-label text-dark fw-semibold">
                              Organisation working days –
                            </label>

                            <Field name="noOfWorkingDays">
                              {({ field, form }) => (
                                <select
                                  {...field}
                                  className={`form-select form-select-sm ${form.errors.noOfWorkingDays && form.touched.noOfWorkingDays
                                    ? "is-invalid"
                                    : ""
                                    }`}
                                  disabled={form.values.workingDaysCalculationType !== "org_days"}
                                  style={{ width: "120px" }}
                                >
                                  <option value="">Select</option>
                                  {dayOptions.map((num) => (
                                    <option key={num} value={num}>
                                      {num}
                                    </option>
                                  ))}
                                </select>
                              )}
                            </Field>

                            <span>days per month</span>
                          </div>
                        </div>

                        <ErrorMessage
                          name="workingDaysCalculationType"
                          component="div"
                          className="invalid-feedback d-block"
                        />
                        <ErrorMessage
                          name="noOfWorkingDays"
                          component="div"
                          className="invalid-feedback d-block"
                        />
                      </div>

                      <div className="mb-4">
                        <label className="form-label fs-6 fw-bold text-dark">
                          Pay on <span className="text-danger">*</span>
                        </label>

                        <div className="d-flex flex-column gap-3 mt-2">
                          <Field name="payDay">
                            {({ field, form }) => (
                              <>
                                <div className="form-check">
                                  <input
                                    type="radio"
                                    name={field.name}
                                    value="last_working_day"
                                    checked={field.value === "last_working_day"}
                                    onChange={() => {
                                      form.setFieldValue("payDay", "last_working_day");
                                      const opts = computeFirstPayrollOptions(
                                        form.values.payPeriodStartDate,
                                        "last_working_day",
                                        form.values.payDaySpecific
                                      );
                                      form.setFieldValue("firstPayDate", opts.length ? opts[0].value : "");
                                    }}
                                    className="form-check-input"
                                  />
                                  <label className="form-check-label text-dark fw-semibold">
                                    Last working day of the month
                                  </label>
                                </div>

                                <div className="form-check d-flex align-items-center gap-2 mt-2">
                                  <input
                                    type="radio"
                                    name={field.name}
                                    value="specific_day"
                                    checked={field.value === "specific_day"}
                                    onChange={() => {
                                      form.setFieldValue("payDay", "specific_day");
                                      const opts = computeFirstPayrollOptions(
                                        form.values.payPeriodStartDate,
                                        "specific_day",
                                        form.values.payDaySpecific
                                      );
                                      form.setFieldValue("firstPayDate", opts.length ? opts[0].value : "");
                                    }}
                                    className="form-check-input"
                                  />
                                  <label className="form-check-label text-dark fw-semibold">On</label>

                                  <Field name="payDaySpecific">
                                    {({ field: fd, form: frm }) => (
                                      <select
                                        {...fd}
                                        className={`form-select form-select-sm ${frm.errors.payDaySpecific && frm.touched.payDaySpecific ? "is-invalid" : ""
                                          }`}
                                        disabled={frm.values.payDay !== "specific_day"}
                                        style={{ width: "100px" }}
                                        onChange={(e) => {
                                          frm.setFieldValue("payDaySpecific", e.target.value);
                                          const opts = computeFirstPayrollOptions(
                                            frm.values.payPeriodStartDate,
                                            frm.values.payDay,
                                            e.target.value
                                          );
                                          frm.setFieldValue("firstPayDate", opts.length ? opts[0].value : "");
                                        }}
                                      >
                                        <option value="">Select</option>
                                        {payDayOptions.map((num) => (
                                          <option key={num} value={num}>
                                            {num}
                                          </option>
                                        ))}
                                      </select>
                                    )}
                                  </Field>

                                  <span>day of the month</span>
                                </div>
                              </>
                            )}
                          </Field>
                        </div>

                        <ErrorMessage
                          name="payDay"
                          component="div"
                          className="invalid-feedback d-block"
                        />
                        <ErrorMessage
                          name="payDaySpecific"
                          component="div"
                          className="invalid-feedback d-block"
                        />

                        <div className="form-text text-muted mt-2">
                          Note: If the chosen payday falls on a weekend or holiday, the salary will be processed on the preceding business day.
                        </div>
                      </div>

                      <div className="row">
                        <div className="col-md-6 mb-3">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Start your first payroll from <span className="text-danger">*</span>
                          </label>

                          {/* <Field name="payPeriodStartDate">
                            {({ field, form }) => (
                              <select
                                {...field}
                                className={`form-select form-select-lg form-select-solid ${form.errors.payPeriodStartDate && form.touched.payPeriodStartDate
                                  ? "is-invalid"
                                  : ""
                                  }`}
                                onChange={(e) => {
                                  const val = e.target.value;
                                  form.setFieldValue("payPeriodStartDate", val);
                                  form.setFieldTouched("payPeriodStartDate", true);

                                  const opts = computeFirstPayrollOptions(val, form.values.payDay, form.values.payDaySpecific);
                                  form.setFieldValue("firstPayDate", opts.length ? opts[0].value : "");
                                }}
                              >
                                <option value="">Select</option>
                                {monthsList.map((m) => (
                                  <option key={m.value} value={m.value}>
                                    {m.label}
                                  </option>
                                ))}
                              </select>
                            )}
                          </Field> */}


                          <div className="d-flex gap-2">
                            {/* Year Dropdown */}
                            <Field name="payPeriodYear">
                              {({ field, form }) => (
                                <select
                                  {...field}
                                  className="form-select form-select-lg form-select-solid"
                                  onChange={(e) => {
                                    const year = e.target.value;
                                    form.setFieldValue("payPeriodYear", year);

                                    const month = form.values.payPeriodMonth;
                                    if (year && month) {
                                      const combined = `${year}-${month}`;
                                      form.setFieldValue("payPeriodStartDate", combined);

                                      const opts = computeFirstPayrollOptions(
                                        combined,
                                        form.values.payDay,
                                        form.values.payDaySpecific
                                      );
                                      form.setFieldValue("firstPayDate", opts.length ? opts[0].value : "");
                                    }
                                  }}
                                >
                                  <option value="">Year</option>

                                  {yearList.map((y) => (
                                    <option key={y} value={y}>
                                      {y}
                                    </option>
                                  ))}
                                </select>
                              )}
                            </Field>

                            {/* Month Dropdown */}
                            <Field name="payPeriodMonth">
                              {({ field, form }) => (
                                <select
                                  {...field}
                                  className="form-select form-select-lg form-select-solid"
                                  onChange={(e) => {
                                    const month = e.target.value;
                                    form.setFieldValue("payPeriodMonth", month);

                                    const year = form.values.payPeriodYear;
                                    if (year && month) {
                                      const combined = `${year}-${month}`;
                                      form.setFieldValue("payPeriodStartDate", combined);

                                      const opts = computeFirstPayrollOptions(
                                        combined,
                                        form.values.payDay,
                                        form.values.payDaySpecific
                                      );
                                      form.setFieldValue("firstPayDate", opts.length ? opts[0].value : "");
                                    }
                                  }}
                                >
                                  <option value="">Month</option>
                                  {monthList.map((m) => (
                                    <option key={m.value} value={m.value}>{m.label}</option>
                                  ))}
                                </select>
                              )}
                            </Field>
                          </div>

                          <ErrorMessage
                            name="payPeriodStartDate"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-md-6 mb-3">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Select a pay date for your first payroll <span className="text-danger">*</span>
                            <div className="form-text text-muted small">
                              Pay Period:{" "}
                              {/* <strong>
                                {monthsList.find((m) => m.value === values.payPeriodStartDate)?.label || "-"}
                              </strong> */}




                              <strong>
                                {monthsList.find((m) => m.value === values.payPeriodStartDate)?.label || "-"}
                              </strong>

                            </div>
                          </label>

                          <Field name="firstPayDate">
                            {({ field, form }) => {
                              const options = computeFirstPayrollOptions(
                                form.values.payPeriodStartDate,
                                form.values.payDay,
                                form.values.payDaySpecific
                              );

                              return (
                                <>
                                  <select
                                    {...field}
                                    disabled={!form.values.payPeriodStartDate || options.length === 0}
                                    className={`form-select form-select-lg form-select-solid ${form.errors.firstPayDate && form.touched.firstPayDate ? "is-invalid" : ""
                                      }`}
                                    onChange={(e) => form.setFieldValue("firstPayDate", e.target.value)}
                                    value={form.values.firstPayDate || ""}
                                  >
                                    <option value="">Select</option>
                                    {options.map((o) => (
                                      <option key={o.value} value={o.value}>
                                        {o.label}
                                      </option>
                                    ))}
                                  </select>

                                  <ErrorMessage
                                    name="firstPayDate"
                                    component="div"
                                    className="invalid-feedback d-block"
                                  />

                                  {form.values.firstPayDate && (
                                    <div className="mt-3">
                                      <SmallCalendar selectedIso={form.values.firstPayDate} />
                                    </div>
                                  )}
                                </>
                              );
                            }}
                          </Field>
                        </div>
                      </div>

                      <br></br>
                      <hr className="my-4 border-2 border-dark" />

                      <div className="d-flex align-items-center mt-5" style={{ gap: "10px" }}>
                        <button
                          type="submit"
                          className="btn btn-lg btn-primary"
                          disabled={isSubmitting}
                        >
                          {isSubmitting ? "Updating..." : "Update"}
                        </button>
                        <button
                          type="button"
                          className="btn btn-lg btn-outline-secondary"
                          onClick={() => navigate("/pay-schedules/view")}
                        >
                          Cancel
                        </button>
                      </div>
                    </Form>
                  )}
                </Formik>
              ) : (
                <div className="text-center py-5">
                  <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">Loading...</span>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
      {signingIn && <Loader />}
    </>
  );
}