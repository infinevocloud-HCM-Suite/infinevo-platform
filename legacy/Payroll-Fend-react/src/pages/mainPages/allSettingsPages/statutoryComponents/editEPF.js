// editEPF.js
import React, { useMemo, useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate } from "react-router-dom";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { updateToken } from "../../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

// ---- Configurable rates (tweak if needed)
const RATES = {
  EMPLOYEE_EPF: 0.12,        // 12%
  EMPLOYER_TOTAL: 0.12,      // 12% (EPS + ER EPF)
  EPS: 0.0833,               // 8.33% (capped @ 15k)
  EPS_WAGE_CAP: 15000,
  EDLI: 0.005,               // 0.50% (capped @ 15k)
  EDLI_WAGE_CAP: 15000,
  ADMIN: 0.005,              // 0.50% (capped @ 15k)
  ADMIN_WAGE_CAP: 15000,
  SAMPLE_WAGE: 20000         // Right-side demo wage
};

export default function EditEPF() {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true);
  const [initialData, setInitialData] = useState(null);
  const [showLopPreview, setShowLopPreview] = useState(false);
  const organizationId = localStorage.getItem("organizationId");

  const initialValues = {
    registrationNumber: "",
    deductionCycle: "monthly",
    epfEmployeeContribution: "12_actual",
    epfEmployerContribution: "12_actual",
    isEmployerContributionIncludedSalaryStructure: true,
    isEdliIncludedSalaryStructure: false,
    isAdminChargesIncludedSalaryStructure: false,
    canOverrideRestrictedBasic: false,
    canProRateRestrictedBasic: true,
    considerEarnedSalaryForEpf: true
  };

  const RATE_OPTIONS = [
    { value: "12_actual", label: "12% of Actual PF Wage" },
    { value: "restrict_15000", label: "Restrict Contribution to ₹15,000 of PF Wage" },
  ];

  const validationSchema = Yup.object().shape({
    registrationNumber: Yup.string()
     .transform((val) => (val ? val.toUpperCase() : val))
     .matches(
       /^[A-Z]{2}\/[A-Z]{3}\/\d{7}\/[A-Z0-9]{3}$/,
       "EPF Number must be in format: AA/AAA/0000000/XXX"
     )
     .max(18, "EPF Number must be 18 characters including slashes")
     .required("EPF Number is required"),
    deductionCycle: Yup.string().required("Deduction cycle is required"),
    epfEmployeeContribution: Yup.string().required("Employee contribution rate is required"),
    epfEmployerContribution: Yup.string().required("Employer contribution rate is required"),
    isEmployerContributionIncludedSalaryStructure: Yup.boolean(),
    isEdliIncludedSalaryStructure: Yup.boolean(),
    isAdminChargesIncludedSalaryStructure: Yup.boolean(),
    canOverrideRestrictedBasic: Yup.boolean(),
    canProRateRestrictedBasic: Yup.boolean(),
    considerEarnedSalaryForEpf: Yup.boolean()
  });

  useEffect(() => {
    fetchEpfData();
  }, []);

  const fetchEpfData = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/epf`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });

      if (response.data && response.data.data) {
        const epfData = response.data.data;
        // Ensure the form receives booleans/expected tokens (map here so Formik initialValues are stable)
        const mapped = mapApiToForm(epfData);
        setInitialData(mapped);
      } else {
        navigate("/statutory-components/epf/create");
      }
    } catch (error) {
      console.error("Error fetching EPF data:", error);
      errorMsg("Error", "Failed to fetch EPF data");
      navigate("/statutory-components/epf/create");
    } finally {
      setLoading(false);
    }
  };

  // Map API DTO to form-friendly payload
  const mapApiToForm = (raw) => {
    if (!raw) return initialValues;

    const detectRestrict = (val, fallbackFlag) => {
      if (!val && !fallbackFlag) return false;
      if (typeof val === "string" && val.toLowerCase().includes("restrict")) return true;
      if (fallbackFlag === true) return true;
      return false;
    };

    const rawEmployee = raw.epfEmployeeContribution || raw.epf_employee_contribution || raw.epfEmployeeContributionFormatted || null;
    const rawEmployer = raw.epfEmployerContribution || raw.epf_employer_contribution || raw.epfEmployerContributionFormatted || null;

    const employeeRestrict = detectRestrict(rawEmployee, false);
    const employerRestrict = detectRestrict(rawEmployer, raw.is_employer_restricted_basic_enabled || raw.isEmployerRestrictedBasicEnabled);

    return {
      registrationNumber: raw.registrationNumber || raw.epf_number || "",
      deductionCycle: raw.deductionCycle || raw.deduction_cycle || "monthly",
      epfEmployeeContribution: employeeRestrict ? "restrict_15000" : "12_actual",
      epfEmployerContribution: employerRestrict ? "restrict_15000" : "12_actual",
      isEmployerContributionIncludedSalaryStructure: typeof raw.isEmployerContributionIncludedSalaryStructure !== "undefined" ? raw.isEmployerContributionIncludedSalaryStructure : (raw.include_employer_in_employee_structure ?? true),
      isEdliIncludedSalaryStructure: typeof raw.isEdliIncludedSalaryStructure !== "undefined" ? raw.isEdliIncludedSalaryStructure : (raw.is_edli_included_salary_structure ?? false),
      isAdminChargesIncludedSalaryStructure: typeof raw.isAdminChargesIncludedSalaryStructure !== "undefined" ? raw.isAdminChargesIncludedSalaryStructure : (raw.is_admin_charges_included_salary_structure ?? false),
      canOverrideRestrictedBasic: typeof raw.canOverrideRestrictedBasic !== "undefined" ? raw.canOverrideRestrictedBasic : !!raw.can_override_restricted_basic,
      canProRateRestrictedBasic: typeof raw.canProRateRestrictedBasic !== "undefined" ? raw.canProRateRestrictedBasic : !!raw.can_pro_rate_restricted_basic,
      considerEarnedSalaryForEpf: typeof raw.considerEarnedSalaryForEpf !== "undefined" ? raw.considerEarnedSalaryForEpf : !!raw.consider_earned_salary_for_epf,
      _raw: raw
    };
  };

  const handleSubmit = async (values, { setSubmitting }) => {
    setSigningIn(true);
    try {
      const postData = {
        registrationNumber: values.registrationNumber,
        deductionCycle: values.deductionCycle,
        epfEmployeeContribution: values.epfEmployeeContribution,
        epfEmployerContribution: values.epfEmployerContribution,
        isEmployerContributionIncludedSalaryStructure: values.isEmployerContributionIncludedSalaryStructure,
        isEdliIncludedSalaryStructure: values.isEdliIncludedSalaryStructure,
        isAdminChargesIncludedSalaryStructure: values.isAdminChargesIncludedSalaryStructure,
        canOverrideRestrictedBasic: values.canOverrideRestrictedBasic,
        canProRateRestrictedBasic: values.canProRateRestrictedBasic,
        considerEarnedSalaryForEpf: values.considerEarnedSalaryForEpf,
        isActive: true
      };

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/epf`,
        postData,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data && (response.data.status === 200 || response.status === 200)) {
        successMsg("Success", "EPF configuration updated successfully", false);
        navigate("/statutory-components");
      } else {
        errorMsg(
          "Update Failed",
          `There was an error updating EPF settings. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Update Failed", e.response.data.message || "Failed to update EPF configuration", false);
      } else {
        errorMsg(e.code, e.message, true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  /**
   * buildSample
   * - vals: form values (tokens: '12_actual' or 'restrict_15000' for epfEmployeeContribution / epfEmployerContribution)
   * - lopFlag: if true, simulate 15 days LOP (halving wage then cap @ 15k for employer-calculations)
   *
   * Returns object with components: wage, employeeEPF, employerEPS, employerEPFPart, edli, admin, totals.
   *
   * NOTE: Employee EPF amount for preview is displayed as 12% of SAMPLE_WAGE (₹20,000) to match reference UI.
   *       Employer components are calculated using the restrict/LOP rules (cap/half where applicable).
   *       EDLI/Admin will be included in the displayed Total only when the corresponding checkboxes are CHECKED.
   */
  const buildSample = (vals, lopFlag = false) => {
    // Determine employer calculation base (wage) — this may be halved under LOP or restricted to 15000
    let employerWage;
    if (!lopFlag) {
      employerWage =
        vals.epfEmployeeContribution === "restrict_15000" || vals.epfEmployerContribution === "restrict_15000"
          ? Math.min(RATES.SAMPLE_WAGE, RATES.EPS_WAGE_CAP)
          : RATES.SAMPLE_WAGE;
    } else {
      // halved wage for employer calculations when LOP is applied
      const half = Math.floor(RATES.SAMPLE_WAGE / 2);
      employerWage = half;
      // employer components will use caps where appropriate (EPS, EDLI, Admin use min(employerWage, cap))
    }

    // Employee EPF for preview: ALWAYS 12% of SAMPLE_WAGE (as per reference screenshot)
    const employeeEPF = Math.round(RATES.SAMPLE_WAGE * RATES.EMPLOYEE_EPF);

    // Employer EPS (on capped base)
    const epsBase = Math.min(employerWage, RATES.EPS_WAGE_CAP);
    const employerEPS = Math.round(epsBase * RATES.EPS);

    // Employer total 12% of employerWage (rounded)
    const employerTotal12pct = Math.round(employerWage * RATES.EMPLOYER_TOTAL);

    // Employer EPF part (remainder after EPS)
    const employerEPFPart = Math.max(0, employerTotal12pct - employerEPS);

    // EDLI & Admin — calculated on their caps (usually 15k)
    const edliBase = Math.min(employerWage, RATES.EDLI_WAGE_CAP);
    const edli = Math.round(edliBase * RATES.EDLI);

    const adminBase = Math.min(employerWage, RATES.ADMIN_WAGE_CAP);
    const admin = Math.round(adminBase * RATES.ADMIN);

    // Employer total is EPS + employerEPFPart + optionally EDLI + optionally Admin
    let employerExtras = 0;
    // IMPORTANT: include EDLI/Admin in displayed employer total ONLY if the user has CHECKED the respective checkbox
    if (vals.isEdliIncludedSalaryStructure) {
      employerExtras += edli;
    }
    if (vals.isAdminChargesIncludedSalaryStructure) {
      employerExtras += admin;
    }

    const employerTotal = employerEPS + employerEPFPart + employerExtras;

    // The preview TOTAL displayed in UI should be the employer total (to match reference image)
    const grandTotalDisplayed = employerTotal;

    return {
      employerWage,
      employeeEPF,
      employerEPS,
      employerEPFPart,
      edli,
      admin,
      employeeTotal: employeeEPF,
      employerTotal,
      grandTotalDisplayed,
    };
  };

  const rupee = (v) =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(v);

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit EPF</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Employees' Provident Fund</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100">
              {initialData && (
                <Formik
                  initialValues={initialData}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize
                >
                  {({ isSubmitting, errors, touched, values, setFieldValue }) => {
                    const sampleNormal = buildSample(values, false);
                    const sampleLop = buildSample(values, true);
                    const sample = showLopPreview ? sampleLop : sampleNormal;

                    return (
                      <Form className="form w-100">
                        <div className="row g-5">
                          <div className="col-lg-7">
                            <div className="row">
                              <div className="col-md-6 mb-3">
                                <label className="form-label fs-6 fw-bold text-dark">EPF Number</label>

                                <Field name="registrationNumber">
                                  {({ field }) => (
                                    <input
                                      {...field}
                                      type="text"
                                      placeholder="AA/AAA/0000000/XXX"
                                      maxLength={18}
                                      value={field.value || ""}
                                      onChange={(e) => {
                                        let val = e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, "");
                                        let formatted = "";
                                        if (val.length <= 2) {
                                          formatted = val;
                                        } else if (val.length <= 5) {
                                          formatted = val.slice(0, 2) + "/" + val.slice(2);
                                        } else if (val.length <= 12) {
                                          formatted = val.slice(0, 2) + "/" + val.slice(2, 5) + "/" + val.slice(5);
                                        } else {
                                          formatted =
                                            val.slice(0, 2) +
                                            "/" +
                                            val.slice(2, 5) +
                                            "/" +
                                            val.slice(5, 12) +
                                            "/" +
                                            val.slice(12, 15);
                                        }
                                        setFieldValue("registrationNumber", formatted);
                                      }}
                                      className={`form-control ${errors.registrationNumber && touched.registrationNumber ? "is-invalid" : ""}`}
                                    />
                                  )}
                                </Field>

                                <ErrorMessage
                                  name="registrationNumber"
                                  component="div"
                                  className="invalid-feedback"
                                />
                              </div>

                              <div className="col-md-6 mb-3">
                                <label className="form-label fs-6 fw-bold text-dark">
                                  Deduction Cycle
                                  <i
                                    className="bi bi-info-circle ms-2"
                                    title="Contribution deduction frequency"
                                  ></i>
                                </label>

                                <Field name="deductionCycle">
                                  {({ field, form: { setFieldValue } }) => {
                                    // Ensure value always stays "Monthly"
                                    if (field.value !== "Monthly" && field.value !== "monthly") setFieldValue("deductionCycle", "Monthly");
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
                                  name="deductionCycle"
                                  component="div"
                                  className="invalid-feedback"
                                />
                              </div>
                            </div>

                            <div className="mb-3">
                              <label className="form-label fs-6 fw-bold text-dark">
                                Employee Contribution Rate
                              </label>
                              <Field
                                name="epfEmployeeContribution"
                                as="select"
                                disabled={values.epfEmployerContribution === "12_actual"}
                                className={`form-select form-select-lg form-select-solid ${
                                  errors.epfEmployeeContribution && touched.epfEmployeeContribution ? "is-invalid" : ""
                                }`}
                              >
                                {RATE_OPTIONS.map(opt => (
                                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                                ))}
                              </Field>
                              {values.epfEmployerContribution === "12_actual" && (
                                <div className="form-text">
                                  Locked because Employer Contribution Rate is "12% of Actual PF Wage".
                                </div>
                              )}
                              <ErrorMessage
                                name="epfEmployeeContribution"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>

                            <div className="mb-3">
                              <label className="form-label fs-6 fw-bold text-dark">
                                Employer Contribution Rate
                              </label>
                              <Field name="epfEmployerContribution" as="select"
                                className={`form-select form-select-lg form-select-solid ${
                                  errors.epfEmployerContribution && touched.epfEmployerContribution ? "is-invalid" : ""
                                }`}
                              >
                                {RATE_OPTIONS.map(opt => (
                                  <option key={opt.value} value={opt.value}>{opt.label}</option>
                                ))}
                              </Field>
                              <ErrorMessage
                                name="epfEmployerContribution"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>

                            <div className="mb-3">
                              <div className="form-check">
                                <Field
                                  type="checkbox"
                                  name="isEmployerContributionIncludedSalaryStructure"
                                  className="form-check-input"
                                  id="inclEmpInEmpStruct"
                                />
                                <label htmlFor="inclEmpInEmpStruct" className="form-check-label fw-semibold">
                                  Include employer's contribution in employee's salary structure.
                                </label>
                              </div>

                              <div className="ms-4 mt-2 d-flex flex-column gap-2">
                                <div className="form-check">
                                  <Field
                                    type="checkbox"
                                    name="isEdliIncludedSalaryStructure"
                                    className="form-check-input"
                                    id="inclEDLIInEmp"
                                    disabled={!values.isEmployerContributionIncludedSalaryStructure}
                                  />
                                  <label htmlFor="inclEDLIInEmp" className="form-check-label">
                                    Include employer's EDLI contribution in employee's salary structure.
                                    <i className="bi bi-info-circle ms-1" title="EDLI 0.50%, capped @ ₹15,000 wage"></i>
                                  </label>
                                </div>

                                <div className="form-check">
                                  <Field
                                    type="checkbox"
                                    name="isAdminChargesIncludedSalaryStructure"
                                    className="form-check-input"
                                    id="inclAdminInEmp"
                                    disabled={!values.isEmployerContributionIncludedSalaryStructure}
                                  />
                                  <label htmlFor="inclAdminInEmp" className="form-check-label">
                                    Include admin charges in employee's salary structure.
                                  </label>
                                </div>
                              </div>
                            </div>

                            <div className="mb-4">
                              <div className="form-check">
                                <Field
                                  type="checkbox"
                                  name="canOverrideRestrictedBasic"
                                  className="form-check-input"
                                  id="overrideEmpLvl"
                                />
                                <label htmlFor="overrideEmpLvl" className="form-check-label fw-semibold">
                                  Override PF contribution rate at employee level
                                </label>
                              </div>
                            </div>

                            <div className="mb-2">
                              <div className="fw-bold text-dark mb-2">PF Configuration when LOP Applied</div>

                              <div className="form-check mb-2">
                                <Field
                                  type="checkbox"
                                  name="canProRateRestrictedBasic"
                                  className="form-check-input"
                                  id="prorateRestricted"
                                />
                                <label htmlFor="prorateRestricted" className="form-check-label">
                                  Pro-rate Restricted PF Wage
                                </label>
                                <div className="form-text">
                                  PF contribution will be pro-rated based on the number of days worked by the employee.
                                </div>
                              </div>

                              <div className="form-check">
                                <Field
                                  type="checkbox"
                                  name="considerEarnedSalaryForEpf"
                                  className="form-check-input"
                                  id="considerAllComps"
                                />
                                <label htmlFor="considerAllComps" className="form-check-label">
                                  Consider all applicable salary components if PF wage is less than ₹15,000 after Loss of Pay
                                </label>
                                <div className="form-text">
                                  PF wage will be computed using the salary earned in that month (based on LOP) rather than the
                                  actual amount mentioned in the salary structure.
                                </div>
                              </div>
                            </div>

                            <div className="d-flex align-items-center mt-5" style={{ gap: "10px" }}>
                              <button type="submit" className="btn btn-lg btn-primary" disabled={isSubmitting}>
                                {isSubmitting ? "Updating..." : "Update"}
                              </button>
                              <button 
                                type="button" 
                                className="btn btn-lg btn-secondary" 
                                onClick={() => navigate("/statutory-components")}
                              >
                                Cancel
                              </button>
                            </div>
                          </div>

                          <div className="col-lg-5">
                            <div className="card border-0 shadow-sm">
                              <div className="card-header bg-white d-flex justify-content-between align-items-center">
                                <h6 className="mb-0 fw-semibold">Sample EPF Calculation</h6>
                                <div className="form-check form-switch">
                                  <input
                                    className="form-check-input"
                                    type="checkbox"
                                    id="lopPreview"
                                    checked={showLopPreview}
                                    onChange={() => setShowLopPreview((s) => !s)}
                                  />
                                  <label className="form-check-label small ms-2" htmlFor="lopPreview">With 15 days LOP</label>
                                </div>
                              </div>
                              <div className="card-body">
                                <div className="text-muted mb-3">
                                  Let's assume the PF wage is <strong>{rupee(RATES.SAMPLE_WAGE)}</strong>. The breakup of contribution will be:
                                </div>

                                <div className="border rounded p-3">
                                  <div className="mb-2 fw-semibold">Employee's Contribution</div>
                                  <div className="d-flex justify-content-between py-1 border-bottom">
                                    <span>
                                      EPF ({`12% of ${RATES.SAMPLE_WAGE}`})
                                    </span>
                                    <span>{rupee(sample.employeeEPF)}</span>
                                  </div>

                                  <div className="mt-3 mb-2 fw-semibold">Employer's Contribution</div>
                                  <div className="d-flex justify-content-between py-1">
                                    <span>
                                      EPS (8.33% of {Math.min(showLopPreview ? Math.floor(RATES.SAMPLE_WAGE / 2) : RATES.SAMPLE_WAGE, RATES.EPS_WAGE_CAP)} (Max of ₹{RATES.EPS_WAGE_CAP}))
                                    </span>
                                    <span>{rupee(sample.employerEPS)}</span>
                                  </div>
                                  <div className="d-flex justify-content-between py-1">
                                    <span>
                                      EPF (12% of {(values.epfEmployerContribution === "restrict_15000") ? RATES.EPS_WAGE_CAP : (showLopPreview ? Math.floor(RATES.SAMPLE_WAGE / 2) : RATES.SAMPLE_WAGE)} − EPS)
                                    </span>
                                    <span>{rupee(sample.employerEPFPart)}</span>
                                  </div>

                                  <div className="d-flex justify-content-between py-1">
                                    <div>
                                      <div>EDLI Contribution (0.50% of {RATES.EDLI_WAGE_CAP})</div>
                                      <div className="small text-muted">
                                        {values.isEdliIncludedSalaryStructure ? "Included in salary structure" : "Not included in salary structure"}
                                      </div>
                                    </div>
                                    <div>{rupee(sample.edli)}</div>
                                  </div>

                                  <div className="d-flex justify-content-between py-1">
                                    <div>
                                      <div>EPF Admin Charges (0.50% of {RATES.ADMIN_WAGE_CAP})</div>
                                      <div className="small text-muted">
                                        {values.isAdminChargesIncludedSalaryStructure ? "Included in salary structure" : "Not included in salary structure"}
                                      </div>
                                    </div>
                                    <div>{rupee(sample.admin)}</div>
                                  </div>

                                  <div className="d-flex justify-content-between py-2 border-top mt-2 fw-bold">
                                    <span>Total</span>
                                    <span>{rupee(sample.grandTotalDisplayed)}</span>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </Form>
                    );
                  }}
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
