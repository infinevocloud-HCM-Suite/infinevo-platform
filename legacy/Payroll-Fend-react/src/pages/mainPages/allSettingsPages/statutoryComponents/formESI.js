// esiForm.js
import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate, useLocation } from "react-router-dom";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { BiCheck, BiX } from "react-icons/bi";

export default function FormESI() {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true);

  // UI form fields (kept same names as your UI)
  const [initialValues, setInitialValues] = useState({
    esi_number: "",
    deduction_cycle: "monthly",
    employee_contribution_rate: 0.75,
    employer_contribution_rate: 3.25,
    include_employer_in_structure: false,
  });

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    esi_number: Yup.string()
      .required("ESI number is required")
      .matches(/^\d{2}-\d{2}-\d{6}-\d{3}-\d{4}$/, "ESI number must be in format: 00-00-000000-000-0000"),
    deduction_cycle: Yup.string().required("Deduction cycle is required"),
    include_employer_in_structure: Yup.boolean(),
  });

  // helper: parse numeric value from backend string (handles "0.75", "0.75%", "0.75 %", etc.)
  const parseNumericRate = (val, fallback) => {
    if (val === null || typeof val === "undefined") return fallback;
    if (typeof val === "number") return val;
    try {
      const cleaned = String(val).replace("%", "").replace(/\s+/g, "").trim();
      const parsed = parseFloat(cleaned);
      return isNaN(parsed) ? fallback : parsed;
    } catch (e) {
      return fallback;
    }
  };

  // Fetch existing ESI configuration (if any) to prefill the form
  const fetchEsiData = async () => {
    try {
      setLoading(true);
      const organizationId = localStorage.getItem("organizationId");
      const response = await axios.get(`${GlobalConst.API_URL}/api/esi`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId,
        },
      });

      if (response.data && response.data.data) {
        const dto = response.data.data;
        // Map backend DTO -> UI form fields
        const mapped = {
          esi_number: dto.registrationNumber || "",
          deduction_cycle: dto.deductionCycle || "monthly",
          employee_contribution_rate: parseNumericRate(dto.employeeContribution, 0.75),
          employer_contribution_rate: parseNumericRate(dto.employerContribution, 3.25),
          include_employer_in_structure:
            typeof dto.isIncludedInSalaryStructure !== "undefined"
              ? dto.isIncludedInSalaryStructure
              : !!dto.isIncludedInCtc || false,
        };
        setInitialValues(mapped);
      } else {
        // No backend data found; keep defaults
      }
    } catch (error) {
      console.error("Error fetching ESI data:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to fetch ESI data", true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEsiData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleSubmit = async (values, { setSubmitting }) => {
    // basic validation already covered by Formik + Yup
    setSigningIn(true);
    try {
      const organizationId = localStorage.getItem("organizationId");

      // Construct backend DTO according to EsiDTO fields
      const postData = {
        registrationNumber: values.esi_number,
        deductionCycle: values.deduction_cycle,
        employeeContribution: `${values.employee_contribution_rate}`, // backend expects String
        employerContribution: `${values.employer_contribution_rate}`,
        isIncludedInSalaryStructure: !!values.include_employer_in_structure,
        // optionally set isActive true so saved record becomes active
        isActive: true
      };

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/esi`,
        postData,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      // follow pattern used in epf.js: check response.data.status === 200
      if (response.data && response.data.status === 200) {
        successMsg("Success", "ESI configuration saved successfully", false);
        // Redirect to ESI view page
        navigate("/statutory-components/esi");
      } else {
        errorMsg(
          "Save Failed",
          `There was an error saving ESI settings. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      console.error("Error saving ESI:", e);
      if (!_.isEmpty(e?.response?.data)) {
        // backend error object may have message or err_msg
        const msg = e.response.data.message || e.response.data.err_msg || "Failed to save ESI configuration";
        errorMsg("Save Failed", msg, false);
      } else {
        errorMsg(e.code || "Error", e.message || "Failed to save ESI configuration", true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  const handleCancel = () => {
    navigate("/statutory-components/esi");
  };

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - ESI Configuration</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Employees' State Insurance</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "800px" }}>
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched, values }) => (
                  <Form className="form w-100">
                    {/* ESI Number and Deduction Cycle - Side by side */}
                    <div className="row mb-10">
                      <div className="col-md-6">
                        <div className="fv-row">
                          <label
                            htmlFor="esi_number"
                            className="form-label fs-6 fw-bold text-dark"
                          >
                            ESI Number
                            <RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="esi_number"
                            id="esi_number"
                            className={`form-control form-control-lg form-control-solid ${
                              errors.esi_number && touched.esi_number ? "is-invalid" : ""
                            }`}
                            placeholder="00-00-000000-000-0000"
                            disabled={isSubmitting}
                          />
                          <ErrorMessage
                            name="esi_number"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      <div className="col-md-6">
                        <div className="fv-row">
                          <label
                            htmlFor="deduction_cycle"
                            className="form-label fs-6 fw-bold text-dark"
                          >
                            Deduction Cycle
                            <RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="deduction_cycle"
                            className={`form-control form-control-lg form-control-solid form-select ${
                              errors.deduction_cycle && touched.deduction_cycle ? "is-invalid" : ""
                            }`}
                            disabled={isSubmitting}
                          >
                            <option value="monthly">Monthly</option>
                          </Field>
                          <ErrorMessage
                            name="deduction_cycle"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>
                    </div>

                    {/* Employees' Contribution - Fixed value (show fetched if available) */}
                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Employees' Contribution
                      </label>
                      <div className="d-flex align-items-center">
                        <input
                          type="text"
                          className="form-control form-control-lg form-control-solid bg-light"
                          value={`${values.employee_contribution_rate}% of Gross Pay`}
                          disabled
                          style={{ maxWidth: "200px", marginRight: "10px" }}
                        />
                        <span className="text-muted">(Fixed rate)</span>
                      </div>
                    </div>

                    {/* Employer's Contribution - Fixed value (show fetched if available) */}
                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Employer's Contribution
                      </label>
                      <div className="d-flex align-items-center">
                        <input
                          type="text"
                          className="form-control form-control-lg form-control-solid bg-light"
                          value={`${values.employer_contribution_rate}% of Gross Pay`}
                          disabled
                          style={{ maxWidth: "200px", marginRight: "10px" }}
                        />
                        <span className="text-muted">(Fixed rate)</span>
                      </div>
                    </div>

                    {/* Include Employer Contribution Checkbox */}
                    <div className="fv-row mb-10">
                      <div className="form-check form-check-custom form-check-solid">
                        <Field
                          type="checkbox"
                          name="include_employer_in_structure"
                          id="include_employer_in_structure"
                          className="form-check-input"
                          disabled={isSubmitting}
                        />
                        <label
                          className="form-check-label fs-6 fw-bold text-dark"
                          htmlFor="include_employer_in_structure"
                        >
                          Include employer's contribution in employee's salary structure.
                        </label>
                      </div>
                    </div>

                    {/* Information Note */}
                    <div className="fv-row mb-10">
                      <div className="alert alert-info">
                        <div className="d-flex">
                          <i className="bi bi-info-circle me-2"></i>
                          <div>
                            <strong>Note:</strong> ESI deductions will be made only if the employee's monthly salary is less than or equal to ₹21,000. If the employee gets a salary revision which increases their monthly salary above ₹21,000, they would have to continue making ESI contributions till the end of the contribution period in which the salary was revised (April-September or October-March).
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Action Buttons */}
                    <div className="d-flex justify-content-between align-items-center border-top pt-5">
                      <div className="d-flex gap-3">
                        <button
                          type="submit"
                          className="btn btn-lg btn-primary d-flex align-items-center"
                          disabled={isSubmitting}
                        >
                          {isSubmitting ? (
                            <>
                              <span className="spinner-border spinner-border-sm me-2"></span>
                              Saving...
                            </>
                          ) : (
                            <>
                              <BiCheck className="me-2" size="1.2rem" />
                              Enable
                            </>
                          )}
                        </button>
                        <button
                          type="button"
                          className="btn btn-lg btn-light d-flex align-items-center"
                          onClick={handleCancel}
                          disabled={isSubmitting}
                        >
                          <BiX className="me-2" size="1.2rem" />
                          Cancel
                        </button>
                      </div>
                      <div>
                        <span className="text-danger fs-7">* indicates mandatory fields</span>
                      </div>
                    </div>
                  </Form>
                )}
              </Formik>
            </div>
          </div>
        </div>
      </div>

      {signingIn && <Loader />}
    </>
  );
}
