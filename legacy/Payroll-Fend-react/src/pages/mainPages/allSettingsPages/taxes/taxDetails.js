import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { format } from "date-fns";

export default function TaxDetails() {
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(false);
  const [showPanHint, setShowPanHint] = useState(false);
  const [showTanHint, setShowTanHint] = useState(false);
  const [showTdsHint, setShowTdsHint] = useState(false);
  const [employees, setEmployees] = useState([]);
  const [loadingEmployees, setLoadingEmployees] = useState(false);

  const [initialValues, setInitialValues] = useState({
    panNumber: "",
    tanNumber: "",
    tdsCircle: "",
    authorizedPersonName: "",
    authorizedPersonParent: "",
    authorizedPersonDesignation: "",
    depositSchedule: "Monthly",
    employeeId: "",
    deductorType: "Employee",
  });

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");

  useEffect(() => {
    fetchTaxDetails();
    fetchEmployees();
  }, []);

  const fetchTaxDetails = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/income-tax-details`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data && response.data.data) {
        const taxData = response.data.data;
        setInitialValues({
          panNumber: taxData.panNumber || "",
          tanNumber: taxData.tanNumber || "",
          tdsCircle: taxData.tdsCircle || "",
          authorizedPersonName: taxData.authorizedPersonName || "",
          authorizedPersonParent: taxData.authorizedPersonParent || "",
          authorizedPersonDesignation:
            taxData.authorizedPersonDesignation || "",
          depositSchedule: taxData.depositSchedule || "Monthly",
          employeeId: taxData.employeeId || "",
          deductorType: taxData.employeeId ? "Employee" : "Non-Employee",
        });
      }
    } catch (error) {
      console.error("API Error (GET):", error);
      // If no tax details exist, just keep defaults in initialValues
    } finally {
      setLoading(false);
    }
  };

  const fetchEmployees = async () => {
    try {
      setLoadingEmployees(true);

      // Fetch employees from API
      const response = await axios.get(`${GlobalConst.API_URL}/api/employees`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId,
        },
      });

      if (response.data && response.data.data) {
        // Map the API response to the expected format
        const employeeList = response.data.data.map((employee) => ({
          employeeId: employee.id || employee.employeeId,
          name:
            employee.name ||
            `${employee.firstName || ""} ${employee.middleName ? employee.middleName + " " : ""}${employee.lastName || ""}`.trim(),
          fatherName: employee.fatherName || "", // <-- correct mapping
        }));

        setEmployees(employeeList);
      } else if (response.data && Array.isArray(response.data)) {
        // If response is directly an array
        const employeeList = response.data.map((employee) => ({
          employeeId: employee.id || employee.employeeId,
          name:
            employee.name ||
            `${employee.firstName || ""} ${employee.middleName ? employee.middleName + " " : ""}${employee.lastName || ""}`.trim(),
          fatherName: employee.fatherName || "",
        }));

        setEmployees(employeeList);
      }
    } catch (error) {
      console.error("Error fetching employees:", error);
      errorMsg(
        "Error",
        "Failed to fetch employee list. Please try again.",
        false
      );
    } finally {
      setLoadingEmployees(false);
    }
  };

  // Alternative API endpoint if the above doesn't work
  const fetchEmployeesAlternative = async () => {
    try {
      setLoadingEmployees(true);

      // Try alternative API endpoints
      const endpoints = [
        `${GlobalConst.API_URL}/api/employees/list`,
        `${GlobalConst.API_URL}/api/employees/all`,
        `${GlobalConst.API_URL}/api/users/employees`,
        `${GlobalConst.API_URL}/api/organization/employees`,
      ];

      let employeeData = [];

      for (const endpoint of endpoints) {
        try {
          const response = await axios.get(endpoint, {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          });

          if (response.data) {
            if (response.data.data && Array.isArray(response.data.data)) {
              employeeData = response.data.data;
              break;
            } else if (Array.isArray(response.data)) {
              employeeData = response.data;
              break;
            }
          }
        } catch (error) {
          console.log(`Trying next endpoint... ${endpoint} failed`);
          continue;
        }
      }

      if (employeeData.length > 0) {
        const employeeList = employeeData.map((employee) => ({
          employeeId: employee.id || employee.employeeId || employee._id,
          name:
            employee.name ||
            `${employee.firstName || ""} ${employee.middleName ? employee.middleName + " " : ""}${employee.lastName || ""}`.trim() ||
            employee.email ||
            "Unnamed Employee",
          fatherName: employee.fatherName || "",
        }));

        setEmployees(employeeList);
      } else {
        console.warn("No employees found or API endpoints not available");
        setEmployees([]);
      }
    } catch (error) {
      console.error(
        "Error fetching employees from alternative endpoints:",
        error
      );
      setEmployees([]);
    } finally {
      setLoadingEmployees(false);
    }
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    panNumber: Yup.string()
      .transform((val) => (val ? val.toUpperCase() : val))
      .required("PAN is required")
      .max(10, "PAN must be 10 characters")
      .matches(/^[A-Z]{5}[0-9]{4}[A-Z]$/, "PAN must be in format: AAAAA0000A"),

    tanNumber: Yup.string()
      .transform((val) => (val ? val.toUpperCase() : val))
      .max(10, "TAN must be 10 characters")
      .matches(/^[A-Z]{4}[0-9]{5}[A-Z]$/, "TAN must be in format: AAAA00000A")
      .nullable()
      .notRequired(),

    tdsCircle: Yup.string()
      .transform((val) => (val ? val.toUpperCase() : val))
      // allow empty OR exact format AAA/AA/000/00
      .matches(
        /^(?:[A-Z]{3}\/[A-Z]{2}\/\d{3}\/\d{2})?$/,
        "TDS Circle must be in format: AAA/AA/000/00"
      )
      .nullable()
      .notRequired(),

    authorizedPersonName: Yup.string().when("deductorType", {
      is: "Non-Employee",
      then: (schema) =>
        schema.required(
          "Authorized Person Name is required for Non-Employee deductors"
        ),
    }),

    authorizedPersonParent: Yup.string(),
    authorizedPersonDesignation: Yup.string(),

    depositSchedule: Yup.string().required("Deposit Schedule is required"),

    employeeId: Yup.string().when("deductorType", {
      is: "Employee",
      then: (schema) => schema.required("Employee selection is required"),
    }),

    deductorType: Yup.string().required("Deductor Type is required"),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    try {
      setSigningIn(true);
      setSubmitting(true);

      // Prepare data for backend
      const putData = {
        panNumber: values.panNumber,
        tanNumber: values.tanNumber,
        tdsCircle: values.tdsCircle,
        authorizedPersonName: values.authorizedPersonName,
        authorizedPersonParent: values.authorizedPersonParent,
        authorizedPersonDesignation: values.authorizedPersonDesignation,
        depositSchedule: values.depositSchedule,
        employeeId:
          values.deductorType === "Employee" ? values.employeeId : null,
      };

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/income-tax-details`,
        putData,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data.status === 200) {
        successMsg("Success", "Tax details updated successfully", false);
      } else {
        errorMsg(
          "Save Failed",
          response.data?.message || "Failed to update tax details",
          false
        );
      }
    } catch (error) {
      console.error("API Error (PUT):", error);

      if (error.response) {
        errorMsg(
          "Save Failed",
          error.response.data?.message || "Failed to save tax details",
          false
        );
      } else if (error.request) {
        errorMsg(
          "Network Error",
          "Cannot connect to the server. Please check your connection.",
          false
        );
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSigningIn(false);
      setSubmitting(false);
    }
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Tax Details</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Tax Details</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "600px" }}>
              <h5 className="mb-5">Organisation Tax Details</h5>

              {loading ? (
                <Loader />
              ) : (
                <Formik
                  initialValues={initialValues}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize
                >
                  {({
                    isSubmitting,
                    errors,
                    touched,
                    values,
                    setFieldValue,
                  }) => (
                    <Form className="form w-100">
                      <div className="row">
                        {/* PAN */}
                        <div className="col-md-6 mb-3">
                          <label
                            htmlFor="panNumber"
                            className="form-label fs-6 fw-bold text-dark"
                          >
                            PAN <span className="text-danger">*</span>
                          </label>
                          <Field name="panNumber">
                            {({
                              field,
                              form: { setFieldValue, errors, touched },
                            }) => (
                              <input
                                {...field}
                                type="text"
                                placeholder="AAAAA0000A"
                                disabled={isSubmitting}
                                onFocus={() => setShowPanHint(true)}
                                onBlur={(e) => {
                                  setShowPanHint(false);
                                  setFieldValue(
                                    "panNumber",
                                    (e.target.value || "")
                                      .toUpperCase()
                                      .slice(0, 10)
                                  );
                                }}
                                onChange={(e) => {
                                  const val = e.target.value
                                    .toUpperCase()
                                    .slice(0, 10);
                                  setFieldValue("panNumber", val);
                                }}
                                maxLength={10}
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.panNumber && touched.panNumber
                                    ? "is-invalid"
                                    : ""
                                }`}
                              />
                            )}
                          </Field>
                          {showPanHint && (
                            <div className="form-text text-muted">
                              Format: AAAAA0000A
                            </div>
                          )}
                          <ErrorMessage
                            name="panNumber"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        {/* TAN */}
                        <div className="col-md-6 mb-3">
                          <label
                            htmlFor="tanNumber"
                            className="form-label fs-6 fw-bold text-dark"
                          >
                            TAN
                          </label>
                          <Field name="tanNumber">
                            {({
                              field,
                              form: { setFieldValue, errors, touched },
                            }) => (
                              <input
                                {...field}
                                type="text"
                                placeholder="AAAA00000A"
                                disabled={isSubmitting}
                                onFocus={() => setShowTanHint(true)}
                                onBlur={(e) => {
                                  setShowTanHint(false);
                                  setFieldValue(
                                    "tanNumber",
                                    (e.target.value || "")
                                      .toUpperCase()
                                      .slice(0, 10)
                                  );
                                }}
                                onChange={(e) => {
                                  const val = e.target.value
                                    .toUpperCase()
                                    .slice(0, 10);
                                  setFieldValue("tanNumber", val);
                                }}
                                maxLength={10}
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.tanNumber && touched.tanNumber
                                    ? "is-invalid"
                                    : ""
                                }`}
                              />
                            )}
                          </Field>
                          {showTanHint && (
                            <div className="form-text text-muted">
                              Format: AAAA00000A
                            </div>
                          )}
                          <ErrorMessage
                            name="tanNumber"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      <div className="row">
                        {/* TDS Circle */}
                        <div className="col-md-6 mb-3">
                          <label
                            htmlFor="tdsCircle"
                            className="form-label fs-6 fw-bold text-dark"
                          >
                            TDS Circle
                            <i
                              className="bi bi-question-circle ms-1"
                              title="Enter TDS circle code"
                            ></i>
                          </label>

                          {/* small inputs row */}
                          <div className="d-flex align-items-center gap-2">
                            {(() => {
                              const parts = (values.tdsCircle || "")
                                .toString()
                                .split("/");
                              const p1 = parts[0] || "";
                              const p2 = parts[1] || "";
                              const p3 = parts[2] || "";
                              const p4 = parts[3] || "";

                              return (
                                <>
                                  {/* 1: 3 alpha */}
                                  <input
                                    id="tds-1"
                                    type="text"
                                    inputMode="text"
                                    maxLength={3}
                                    value={p1}
                                    placeholder="AAA"
                                    disabled={isSubmitting}
                                    onChange={(e) => {
                                      const v = (e.target.value || "")
                                        .toUpperCase()
                                        .replace(/[^A-Z]/g, "")
                                        .slice(0, 3);
                                      setFieldValue(
                                        "tdsCircle",
                                        `${v}/${p2}/${p3}/${p4}`
                                      );
                                    }}
                                    onBlur={() => {
                                      const cur = (values.tdsCircle || "")
                                        .toString()
                                        .split("/");
                                      setFieldValue(
                                        "tdsCircle",
                                        `${(cur[0] || "")
                                          .toUpperCase()
                                          .slice(0, 3)}/${(cur[1] || "")
                                          .toUpperCase()
                                          .slice(0, 2)}/${(cur[2] || "").slice(
                                          0,
                                          3
                                        )}/${(cur[3] || "").slice(0, 2)}`
                                      );
                                    }}
                                    className={`form-control form-control-sm text-center`}
                                    style={{ width: "64px" }}
                                  />

                                  {/* separator */}
                                  <div className="mx-1">/</div>

                                  {/* 2: 2 alpha */}
                                  <input
                                    id="tds-2"
                                    type="text"
                                    inputMode="text"
                                    maxLength={2}
                                    value={p2}
                                    placeholder="AA"
                                    disabled={isSubmitting}
                                    onChange={(e) => {
                                      const v = (e.target.value || "")
                                        .toUpperCase()
                                        .replace(/[^A-Z]/g, "")
                                        .slice(0, 2);
                                      setFieldValue(
                                        "tdsCircle",
                                        `${p1}/${v}/${p3}/${p4}`
                                      );
                                    }}
                                    onBlur={() => {
                                      const cur = (values.tdsCircle || "")
                                        .toString()
                                        .split("/");
                                      setFieldValue(
                                        "tdsCircle",
                                        `${(cur[0] || "")
                                          .toUpperCase()
                                          .slice(0, 3)}/${(cur[1] || "")
                                          .toUpperCase()
                                          .slice(0, 2)}/${(cur[2] || "").slice(
                                          0,
                                          3
                                        )}/${(cur[3] || "").slice(0, 2)}`
                                      );
                                    }}
                                    className={`form-control form-control-sm text-center`}
                                    style={{ width: "48px" }}
                                  />

                                  <div className="mx-1">/</div>

                                  {/* 3: 3 digits */}
                                  <input
                                    id="tds-3"
                                    type="text"
                                    inputMode="numeric"
                                    maxLength={3}
                                    value={p3}
                                    placeholder="000"
                                    disabled={isSubmitting}
                                    onChange={(e) => {
                                      const v = (e.target.value || "")
                                        .replace(/[^0-9]/g, "")
                                        .slice(0, 3);
                                      setFieldValue(
                                        "tdsCircle",
                                        `${p1}/${p2}/${v}/${p4}`
                                      );
                                    }}
                                    onBlur={() => {
                                      const cur = (values.tdsCircle || "")
                                        .toString()
                                        .split("/");
                                      setFieldValue(
                                        "tdsCircle",
                                        `${(cur[0] || "")
                                          .toUpperCase()
                                          .slice(0, 3)}/${(cur[1] || "")
                                          .toUpperCase()
                                          .slice(0, 2)}/${(cur[2] || "").slice(
                                          0,
                                          3
                                        )}/${(cur[3] || "").slice(0, 2)}`
                                      );
                                    }}
                                    className={`form-control form-control-sm text-center`}
                                    style={{ width: "64px" }}
                                  />

                                  <div className="mx-1">/</div>

                                  {/* 4: 2 digits */}
                                  <input
                                    id="tds-4"
                                    type="text"
                                    inputMode="numeric"
                                    maxLength={2}
                                    value={p4}
                                    placeholder="00"
                                    disabled={isSubmitting}
                                    onChange={(e) => {
                                      const v = (e.target.value || "")
                                        .replace(/[^0-9]/g, "")
                                        .slice(0, 2);
                                      setFieldValue(
                                        "tdsCircle",
                                        `${p1}/${p2}/${p3}/${v}`
                                      );
                                    }}
                                    onBlur={() => {
                                      const cur = (values.tdsCircle || "")
                                        .toString()
                                        .split("/");
                                      setFieldValue(
                                        "tdsCircle",
                                        `${(cur[0] || "")
                                          .toUpperCase()
                                          .slice(0, 3)}/${(cur[1] || "")
                                          .toUpperCase()
                                          .slice(0, 2)}/${(cur[2] || "").slice(
                                          0,
                                          3
                                        )}/${(cur[3] || "").slice(0, 2)}`
                                      );
                                    }}
                                    className={`form-control form-control-sm text-center`}
                                    style={{ width: "48px" }}
                                  />
                                </>
                              );
                            })()}
                          </div>

                          {/* Validation message */}
                          <div className="mt-2">
                            <ErrorMessage
                              name="tdsCircle"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>

                          <style jsx>{`
                            .invalid-feedback + .form-control,
                            .form-control.is-invalid {
                              border-color: #dc3545;
                            }
                          `}</style>
                        </div>

                        {/* Tax Payment Frequency */}
                        <div className="col-md-6 mb-3">
                          <label
                            htmlFor="depositSchedule"
                            className="form-label fs-6 fw-bold text-dark"
                          >
                            Tax Payment Frequency{" "}
                            <i
                              className="bi bi-question-circle ms-1"
                              title="Indicates how often tax is paid"
                            ></i>
                          </label>

                          {/* Fixed readonly field */}
                          <Field name="depositSchedule">
                            {({ field, form: { setFieldValue } }) => {
                              // ensure it's always "Monthly"
                              if (field.value !== "Monthly")
                                setFieldValue("depositSchedule", "Monthly");
                              return (
                                <input
                                  type="text"
                                  readOnly
                                  disabled
                                  className="form-control form-control-lg form-control-solid"
                                  value="Monthly"
                                />
                              );
                            }}
                          </Field>

                          <ErrorMessage
                            name="depositSchedule"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      <br />

                      <h5 className="mb-5">Tax Deductor Details</h5>

                      <div className="row">
                        {/* Deductor's Type */}
                        <div className="col-md-6 mb-3">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Deductor's Type <span className="text-danger">*</span>
                          </label>
                          <div className="d-flex align-items-center gap-4">
                            <div className="form-check">
                              <Field
                                type="radio"
                                name="deductorType"
                                value="Employee"
                                className="form-check-input"
                              />
                              <label className="form-check-label text-dark fw-semibold">
                                Employee
                              </label>
                            </div>
                            <div className="form-check">
                              <Field
                                type="radio"
                                name="deductorType"
                                value="Non-Employee"
                                className="form-check-input"
                              />
                              <label className="form-check-label text-dark fw-semibold">
                                Non-Employee
                              </label>
                            </div>
                          </div>
                          <ErrorMessage
                            name="deductorType"
                            component="div"
                            className="invalid-feedback d-block"
                          />
                        </div>
                      </div>

                      <br />

                      {/* -------- Deductor Details Section START -------- */}
                      <div className="row">
                        {values.deductorType === "Employee" ? (
                          <>
                            {/* EMPLOYEE MODE: Name (dropdown) + Father's Name (auto) */}

                            {/* Deductor's Name - DROPDOWN (Employee) */}
                            <div className="col-md-6 mb-3">
                              <label
                                htmlFor="employeeId"
                                className="form-label fs-6 fw-bold text-dark"
                              >
                                Deductor's Name <span className="text-danger">*</span>
                              </label>

                              {loadingEmployees ? (
                                <div className="d-flex align-items-center">
                                  <div
                                    className="spinner-border spinner-border-sm me-2"
                                    role="status"
                                  >
                                    <span className="visually-hidden">
                                      Loading...
                                    </span>
                                  </div>
                                  <span>Loading employees...</span>
                                </div>
                              ) : (
                                <Field
                                  as="select"
                                  name="employeeId"
                                  className={`form-select form-select-lg form-select-solid ${
                                    errors.employeeId && touched.employeeId ? "is-invalid" : ""
                                  }`}
                                  disabled={isSubmitting || employees.length === 0}
                                  onChange={(e) => {
                                    const id = e.target.value;
                                    setFieldValue("employeeId", id);

                                    const emp = employees.find((x) => x.employeeId === id);
                                    // auto-fill both name and father's name
                                    setFieldValue("authorizedPersonName", emp?.name || "");
                                    setFieldValue("authorizedPersonParent", emp?.fatherName || "");
                                  }}
                                >
                                  <option value="">Select an Employee</option>
                                  {employees.length > 0 ? (
                                    employees.map((employee) => (
                                      <option
                                        key={employee.employeeId}
                                        value={employee.employeeId}
                                      >
                                        {employee.name}
                                      </option>
                                    ))
                                  ) : (
                                    <option value="" disabled>
                                      No employees available
                                    </option>
                                  )}
                                </Field>
                              )}

                              {employees.length === 0 && !loadingEmployees && (
                                <div className="form-text text-warning">
                                  No employees found. Please add employees first.
                                </div>
                              )}
                              <ErrorMessage
                                name="employeeId"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>

                            {/* Deductor's Father's Name - AUTO (read-only) */}
                            <div className="col-md-6 mb-3">
                              <label
                                htmlFor="authorizedPersonParent"
                                className="form-label fs-6 fw-bold text-dark"
                              >
                                Deductor's Father's Name
                              </label>
                              <Field
                                type="text"
                                name="authorizedPersonParent"
                                placeholder="Auto-filled from employee"
                                readOnly
                                disabled
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.authorizedPersonParent && touched.authorizedPersonParent
                                    ? "is-invalid"
                                    : ""
                                }`}
                              />
                              <ErrorMessage
                                name="authorizedPersonParent"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>
                          </>
                        ) : (
                          <>
                            {/* NON-EMPLOYEE MODE: 3 text fields */}

                            {/* Deductor's Name */}
                            <div className="col-md-6 mb-3">
                              <label
                                htmlFor="authorizedPersonName"
                                className="form-label fs-6 fw-bold text-dark"
                              >
                                Deductor's Name <span className="text-danger">*</span>
                              </label>
                              <Field
                                type="text"
                                name="authorizedPersonName"
                                placeholder="Enter Deductor's name"
                                disabled={isSubmitting}
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.authorizedPersonName && touched.authorizedPersonName
                                    ? "is-invalid"
                                    : ""
                                }`}
                              />
                              <ErrorMessage
                                name="authorizedPersonName"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>

                            {/* Deductor's Father's Name */}
                            <div className="col-md-6 mb-3">
                              <label
                                htmlFor="authorizedPersonParent"
                                className="form-label fs-6 fw-bold text-dark"
                              >
                                Deductor's Father's Name
                              </label>
                              <Field
                                type="text"
                                name="authorizedPersonParent"
                                placeholder="Enter Deductor's father's name"
                                disabled={isSubmitting}
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.authorizedPersonParent && touched.authorizedPersonParent
                                    ? "is-invalid"
                                    : ""
                                }`}
                              />
                              <ErrorMessage
                                name="authorizedPersonParent"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>

                            {/* Deductor's Designation */}
                            <div className="col-md-6 mb-3">
                              <label
                                htmlFor="authorizedPersonDesignation"
                                className="form-label fs-6 fw-bold text-dark"
                              >
                                Deductor's Designation
                              </label>
                              <Field
                                type="text"
                                name="authorizedPersonDesignation"
                                placeholder="Enter Deductor's designation"
                                disabled={isSubmitting}
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.authorizedPersonDesignation &&
                                  touched.authorizedPersonDesignation
                                    ? "is-invalid"
                                    : ""
                                }`}
                              />
                              <ErrorMessage
                                name="authorizedPersonDesignation"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>
                          </>
                        )}
                      </div>
                      {/* -------- Deductor Details Section END -------- */}

                      <br />

                      <hr className="my-4 border-2 border-dark" />

                      <div className="d-flex align-items-center justify-content-between mt-5">
                        <button
                          type="submit"
                          className="btn btn-lg btn-primary"
                          disabled={isSubmitting}
                        >
                          {isSubmitting ? "Saving..." : "Save"}
                        </button>

                        <span className="text-danger">
                          * indicates mandatory fields
                        </span>
                      </div>
                    </Form>
                  )}
                </Formik>
              )}
            </div>
          </div>
        </div>
      </div>
      {signingIn && <Loader />}
    </>
  );
}
