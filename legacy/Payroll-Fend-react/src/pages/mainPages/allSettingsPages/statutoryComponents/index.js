// index.js (StatutoryComponents) — updated to match editEPF sample behaviour
import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { CiImport } from "react-icons/ci";
import { BiEdit, BiTrash } from "react-icons/bi";
import { useNavigate, useLocation } from "react-router-dom";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { format } from "date-fns";
import desigimg from '../../../../assets/images/desigimg.jpg';
import EPFSampleModal from "./EPFSampleModal";

export default function StatutoryComponents() {
  const [activeTab, setActiveTab] = useState("EPF");
  const tabs = [
    { key: "/statutory-components", label: "EPF" },
    { key: "/statutory-components/esi", label: "ESI" },
    { key: "/statutory-components/professional-tax", label: "Professional Tax" },
    { key: "/statutory-components/labour-welfare-fund", label: "Labour Welfare Fund" },
    { key: "/statutory-components/statutory-bonus", label: "Statutory Bonus" },
  ];

  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [epfData, setEpfData] = useState(null);
  const [hasEpf, setHasEpf] = useState(false);
  const [loading, setLoading] = useState(false);
  const [showEpfModal, setShowEpfModal] = useState(false);


  const [initialValues, setInitialValues] = useState({
    id: null,
    name: "",
  });

  const RequiredStar = () => <span className="text-danger">*</span>;

  // Normalize backend DTO -> UI expected fields
  const normalizeEpfDtoToUi = (dto) => {
    if (!dto) return null;

    return {
      // UI expects epf_number, deduction_cycle, employee_rate, employer_rate, etc.
      epf_number: dto.registrationNumber || dto.epf_number || null,
      deduction_cycle: dto.deductionCycle || dto.deduction_cycle || null,
      employee_rate: dto.epfEmployeeContribution || dto.epf_employee_contribution || null,
      employer_rate: dto.epfEmployerContribution || dto.epf_employer_contribution || null,
      include_employer_in_employee_structure:
        typeof dto.isEmployerContributionIncludedSalaryStructure !== "undefined"
          ? dto.isEmployerContributionIncludedSalaryStructure
          : dto.include_employer_in_employee_structure || false,
      include_edli_in_employee:
        typeof dto.isEdliIncludedSalaryStructure !== "undefined"
          ? dto.isEdliIncludedSalaryStructure
          : dto.include_edli_in_employee || false,
      include_admin_in_employee:
        typeof dto.isAdminChargesIncludedSalaryStructure !== "undefined"
          ? dto.isAdminChargesIncludedSalaryStructure
          : dto.include_admin_in_employee || false,
      override_at_employee_level:
        typeof dto.canOverrideRestrictedBasic !== "undefined"
          ? dto.canOverrideRestrictedBasic
          : dto.override_at_employee_level || false,
      prorate_restricted_pf_wage:
        typeof dto.canProRateRestrictedBasic !== "undefined"
          ? dto.canProRateRestrictedBasic
          : dto.prorate_restricted_pf_wage || false,
      consider_components_below_15k_after_lop:
        typeof dto.considerEarnedSalaryForEpf !== "undefined"
          ? dto.considerEarnedSalaryForEpf
          : dto.consider_components_below_15k_after_lop || false,
      created_at: dto.registrationDate || dto.created_at || null,
      // keep full original dto for future use if needed
      _raw: dto
    };
  };

  // ---- Configurable rates (same as editEPF)
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

  // Build sample method reworked to match editEPF's buildSample logic
  const buildSample = (vals) => {
    // vals should have:
    // epfEmployeeContribution ('12_actual'|'restrict_15000'), epfEmployerContribution (same),
    // isEdliIncludedSalaryStructure (bool), isAdminChargesIncludedSalaryStructure (bool)

    // Employer calculation base (wage) uses cap when any restrict token is set
    const employerWage =
      vals.epfEmployeeContribution === "restrict_15000" || vals.epfEmployerContribution === "restrict_15000"
        ? Math.min(RATES.SAMPLE_WAGE, RATES.EPS_WAGE_CAP)
        : RATES.SAMPLE_WAGE;

    // Employee EPF for preview: ALWAYS 12% of SAMPLE_WAGE (matches editEPF preview)
    const employeeEPF = Math.round(RATES.SAMPLE_WAGE * RATES.EMPLOYEE_EPF);

    // Employer EPS (on capped base)
    const epsBase = Math.min(employerWage, RATES.EPS_WAGE_CAP);
    const employerEPS = Math.round(epsBase * RATES.EPS);

    // Employer total 12% of employerWage
    const employerTotal12pct = Math.round(employerWage * RATES.EMPLOYER_TOTAL);

    // Employer EPF part (remainder after EPS)
    const employerEPFPart = Math.max(0, employerTotal12pct - employerEPS);

    // EDLI & Admin — calculated on their caps
    const edliBase = Math.min(employerWage, RATES.EDLI_WAGE_CAP);
    const edli = Math.round(edliBase * RATES.EDLI);

    const adminBase = Math.min(employerWage, RATES.ADMIN_WAGE_CAP);
    const admin = Math.round(adminBase * RATES.ADMIN);

    // Include EDLI/Admin ONLY if configuration indicates inclusion (matching editEPF)
    let employerExtras = 0;
    if (vals.isEdliIncludedSalaryStructure) {
      employerExtras += edli;
    }
    if (vals.isAdminChargesIncludedSalaryStructure) {
      employerExtras += admin;
    }

    const employerTotal = employerEPS + employerEPFPart + employerExtras;

    // To match editEPF sample display where the "Total" matches employer total in preview
    const grandTotalDisplayed = employerTotal;

    return {
      wage: employerWage,
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

  // Fetch EPF data from backend and normalize for UI
  const fetchEpfData = async () => {
    try {
      setLoading(true);
      setFetchError(false);
      const organizationId = localStorage.getItem("organizationId");
      const response = await axios.get(`${GlobalConst.API_URL}/api/epf`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });

      if (response.data && response.data.data) {
        const dto = response.data.data;
        const normalized = normalizeEpfDtoToUi(dto);
        setEpfData(normalized);
        setHasEpf(true);
      } else {
        // no EPF configured
        setEpfData(null);
        setHasEpf(false);
      }
    } catch (error) {
      console.error("Error fetching EPF data:", error);
      setFetchError(true);
      setEpfData(null);
      setHasEpf(false);
      errorMsg("Error", "Failed to fetch EPF data", true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEpfData();
  }, []);

  const handleEnableEpf = () => {
    navigate("/statutory-components/epf");
  };

  const handleEditEpf = () => {
    navigate("/statutory-components/epf");
  };

  const handleDeleteEpf = async () => {
    try {
      setLoading(true);
      const organizationId = localStorage.getItem("organizationId");
      // backend uses POST /api/epf/disable (per your controller)
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/epf/disable`,
        null,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      // if success, clear local UI state
      setEpfData(null);
      setHasEpf(false);
      successMsg("Success", "EPF configuration disabled successfully", false);
    } catch (error) {
      console.error("Error disabling EPF:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to disable EPF", true);
    } finally {
      setLoading(false);
    }
  };

  // Helper function to format rate display (unchanged)
  const formatRateDisplay = (rate) => {
    return rate === "restrict_15000"
      ? "12% of PF Wage (Restricted to ₹15,000)"
      : "12% of Actual PF Wage";
  };

  // Helper to map raw DTO to select-like tokens used by buildSample
  const mapRawToSampleOptions = (raw) => {
    if (!raw) {
      // defaults maintained as in edit page
      return {
        epfEmployeeContribution: "12_actual",
        epfEmployerContribution: "12_actual",
        isEdliIncludedSalaryStructure: false,
        isAdminChargesIncludedSalaryStructure: false,
      };
    }

    // try common fields
    const rawEmployee = raw.epfEmployeeContribution || raw.epf_employee_contribution || raw.epfEmployeeContributionFormatted || null;
    const rawEmployer = raw.epfEmployerContribution || raw.epf_employer_contribution || raw.epfEmployerContributionFormatted || null;

    const detectRestrict = (val, fallbackFlag) => {
      if (!val && !fallbackFlag) return false;
      if (typeof val === "string" && val.toLowerCase().includes("restrict")) return true;
      // if backend gives percentage string like "12.00%" treat it as actual
      // if there's a boolean fallback flag like is_employer_restricted_basic_enabled use that
      if (fallbackFlag === true) return true;
      return false;
    };

    const epfEmployeeContribution = detectRestrict(rawEmployee, false) ? "restrict_15000" : "12_actual";
    const epfEmployerContribution = detectRestrict(rawEmployer, raw.is_employer_restricted_basic_enabled || raw.isEmployerRestrictedBasicEnabled) ? "restrict_15000" : "12_actual";

    const isEdliIncludedSalaryStructure = !!(raw.is_edli_included_salary_structure || raw.isEdliIncludedSalaryStructure || raw.include_edli_in_employee || raw.include_edli_in_employee_structure);
    const isAdminChargesIncludedSalaryStructure = !!(raw.is_admin_charges_included_salary_structure || raw.isAdminChargesIncludedSalaryStructure || raw.include_admin_in_employee || raw.include_admin_in_employee_structure);

    return {
      epfEmployeeContribution,
      epfEmployerContribution,
      isEdliIncludedSalaryStructure,
      isAdminChargesIncludedSalaryStructure,
    };
  };

  const sampleOpts = mapRawToSampleOptions(epfData?._raw);

  // Compute sample for the index card
  const sample = buildSample(sampleOpts);

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Statutory Components</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex flex-column border-bottom">
        {/* Header */}
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h5 className="mb-0 fw-semibold">Statutory Components</h5>
          <div className="d-flex align-items-center gap-2"></div>
        </div>

        {/* Tabs */}
        <ul className="nav nav-tabs border-0">
          {tabs.map((tab) => (
            <li className="nav-item" key={tab.key}>
              <button
                className={`nav-link fw-semibold px-3 py-2 ${
                  location.pathname === tab.key
                    ? "active text-primary border-primary border-bottom"
                    : "text-dark"
                }`}
                onClick={() => navigate(tab.key)}
              >
                {tab.label}
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-10 bg-white"
            style={{ minHeight: '100vh', overflowY: 'auto', display: 'flex', justifyContent: 'center' }}
          >
            <div className="w-100" style={{ maxWidth: "1200px" }}>
              {loading ? (
                <Loader />
              ) : hasEpf ? (
                <div className="py-5">
                  <div className="d-flex justify-content-between align-items-center mb-4">
                    <h5 className="mb-0">Employees' Provident Fund</h5>
                    <div className="d-flex gap-2">
                      <button
                        className="btn btn-icon btn-sm btn-light"
                        onClick={() => navigate("/statutory-components/epf/edit")}
                      >
                        <BiEdit className="text-primary" />
                      </button>
                      <button
                        className="btn btn-icon btn-sm btn-light"
                        onClick={handleDeleteEpf}
                      >
                        <BiTrash className="text-danger" />
                      </button>
                    </div>
                  </div>

                  <div className="card border-0 shadow-sm">
                    <div className="card-body">
                      <div className="row">
                        <div className="col-md-6">
                          <div className="mb-3 row">
                            <label className="col-sm-5 col-form-label fw-semibold">EPF Number</label>
                            <div className="col-sm-7">
                              <p className="form-control-plaintext">{epfData?.epf_number || "Not provided"}</p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-5 col-form-label fw-semibold">Deduction Cycle</label>
                            <div className="col-sm-7">
                              <p className="form-control-plaintext text-capitalize">{epfData?.deduction_cycle || "Monthly"}</p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-5 col-form-label fw-semibold">Employee Contribution Rate</label>
                            <div className="col-sm-7">
                              <p className="form-control-plaintext">
                                {formatRateDisplay(epfData?.employee_rate)}
                              </p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-5 col-form-label fw-semibold">Employer Contribution Rate</label>
                            <div className="col-sm-7">
                              <p className="form-control-plaintext">
                                {formatRateDisplay(epfData?.employer_rate)}
                                <button
                                  type="button"
                                  className="btn btn-link p-0 ms-2"
                                  data-bs-toggle="tooltip"
                                  data-bs-placement="top"
                                  title="EPS: 8.33% (capped at ₹15,000 wage) & Employer EPF: remainder of 12% after EPS"
                                >
                                  (View Splitup)
                                </button>
                              </p>
                            </div>
                          </div>
                        </div>

                        <div className="col-md-6">
                          <div className="mb-3 row">
                            <label className="col-sm-7 col-form-label fw-semibold">Contribution Preferences</label>
                            <div className="col-sm-5">
                              <p className="form-control-plaintext">
                                {epfData?.include_employer_in_employee_structure ? "✔" : "✘"} Employer's PF contribution<br />
                                {epfData?.include_edli_in_employee ? "✔" : "✘"} EDLI contribution<br />
                                {epfData?.include_admin_in_employee ? "✔" : "✘"} Admin charges
                              </p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-7 col-form-label fw-semibold">Allow Employee level Override</label>
                            <div className="col-sm-5">
                              <p className="form-control-plaintext">{epfData?.override_at_employee_level ? "Yes" : "No"}</p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-7 col-form-label fw-semibold">Pro-rate Restricted PF Wage</label>
                            <div className="col-sm-5">
                              <p className="form-control-plaintext">{epfData?.prorate_restricted_pf_wage ? "Yes" : "No"}</p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-7 col-form-label fw-semibold">Consider applicable salary components based on LOP</label>
                            <div className="col-sm-5">
                              <p className="form-control-plaintext">
                                {epfData?.consider_components_below_15k_after_lop
                                  ? "Yes (when PF wage is less than ₹15,000)"
                                  : "No"}
                              </p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-7 col-form-label fw-semibold">Eligible for ABRY Scheme</label>
                            <div className="col-sm-5">
                              <p className="form-control-plaintext">No</p>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Sample EPF Calculation Card - DYNAMIC (matches editEPF preview logic) */}
                  <div className="card border-0 shadow-sm mt-4">
                    <div className="card-header bg-white">
                      <h6 className="mb-0 fw-semibold">Sample EPF Calculation</h6>
                    </div>
                    <div className="card-body">
                      <div className="text-muted mb-3">
                        Let's assume the PF wage is <strong>{rupee(RATES.SAMPLE_WAGE)}</strong>. The breakup of contribution will be:
                      </div>

                      <div className="border rounded p-3">
                        <div className="mb-2 fw-semibold">Employee's Contribution</div>
                        <div className="d-flex justify-content-between py-1 border-bottom">
                          <span>
                            EPF ({sampleOpts.epfEmployeeContribution === "restrict_15000" ? `12% of ${RATES.EPS_WAGE_CAP}` : `12% of ${RATES.SAMPLE_WAGE}`})
                          </span>
                          <span>{rupee(sample.employeeEPF)}</span>
                        </div>

                        <div className="mt-3 mb-2 fw-semibold">Employer's Contribution</div>
                        <div className="d-flex justify-content-between py-1">
                          <span>
                            EPS (8.33% of {Math.min(RATES.SAMPLE_WAGE, RATES.EPS_WAGE_CAP)} (Max of ₹{RATES.EPS_WAGE_CAP}))
                          </span>
                          <span>{rupee(sample.employerEPS)}</span>
                        </div>
                        <div className="d-flex justify-content-between py-1">
                          <span>
                            EPF (12% of {sampleOpts.epfEmployerContribution === "restrict_15000" ? 15000 : RATES.SAMPLE_WAGE} − EPS)
                          </span>
                          <span>{rupee(sample.employerEPFPart)}</span>
                        </div>

                        <div className="d-flex justify-content-between py-1">
                          <div>
                            <div>EDLI Contribution (0.50% of {RATES.EDLI_WAGE_CAP})</div>
                            <div className="small text-muted">
                              {sampleOpts.isEdliIncludedSalaryStructure ? "Included in salary structure" : "Not included in salary structure"}
                            </div>
                          </div>
                          <div>{rupee(sample.edli)}</div>
                        </div>

                        <div className="d-flex justify-content-between py-1">
                          <div>
                            <div>EPF Admin Charges (0.50% of {RATES.ADMIN_WAGE_CAP})</div>
                            <div className="small text-muted">
                              {sampleOpts.isAdminChargesIncludedSalaryStructure ? "Included in salary structure" : "Not included in salary structure"}
                            </div>
                          </div>
                          <div>{rupee(sample.admin)}</div>
                        </div>

                        <div className="d-flex justify-content-between py-2 border-top mt-2">
                          <span className="fw-semibold">Total</span>
                          <span className="fw-semibold">{rupee(sample.grandTotalDisplayed)}</span>
                        </div>
                      </div>

                      <div className="alert alert-warning d-flex align-items-start gap-2 mt-3 mb-0">
                        <i className="bi bi-lightbulb"></i>
                        <div>
                          Do you want to preview EPF calculation for multiple cases, based on the preferences you have configured?
                          <div className="mt-1">
                            <button
                              type="button"
                              className="btn btn-link p-0"
                              onClick={() => setShowEpfModal(true)}
                            >
                              Preview EPF Calculation
                            </button>
                          </div>

                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              ) : (
                <center>
                  <div className="card-body py-10">
                    <div className="text-center">
                      <div className="mb-7">
                        <img
                          src={desigimg}
                          alt="No EPF configuration"
                          className="mw-100 h-200px h-sm-325px"
                        />
                      </div>

                      <div className="mb-10">
                        <h3 className="fw-bold text-gray-900 mb-2">Are you registered for EPF?</h3>
                        <div className="text-muted fw-semibold fs-5">
                          {fetchError ?
                            "Failed to load EPF configuration. Please try again later." :
                            "Any establishment with a workforce of 20 or more must enroll in the Employee Provident Fund (EPF) scheme, a statutory retirement benefit designed for salaried employees."}
                        </div>
                      </div>

                      <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                          className="btn btn-primary"
                          onClick={handleEnableEpf}
                        >
                          <i className="bi bi-plus fs-2"></i> Enable EPF
                        </button>
                      </div>
                    </div>
                  </div>
                </center>
              )}
            </div>
          </div>
        </div>
        {/* pass raw dto into modal for accurate preview */}
        <EPFSampleModal show={showEpfModal} onClose={() => setShowEpfModal(false)} epfConfigRaw={epfData?._raw} />

      </div>

      {signingIn && <Loader />}
    </>
  );
}
