// esi.js
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
//import esiimg from '../../../../assets/images/esiimg.jpg'; // optional image if you have it

export default function ViewESI() {
  const [activeTab, setActiveTab] = useState("ESI");
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
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState(false);
  const [esiData, setEsiData] = useState(null);
  const [hasEsi, setHasEsi] = useState(false);

  const [initialValues, setInitialValues] = useState({
    id: null,
    name: "",
  });

  // Helper to parse backend DTO -> UI fields
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

  const normalizeEsiDtoToUi = (dto) => {
    if (!dto) return null;
    return {
      esi_number: dto.registrationNumber || "",
      deduction_cycle: dto.deductionCycle || "monthly",
      employee_contribution_rate: parseNumericRate(dto.employeeContribution, 0.75),
      employer_contribution_rate: parseNumericRate(dto.employerContribution, 3.25),
      include_employer_in_employee_structure:
        typeof dto.isIncludedInSalaryStructure !== "undefined"
          ? dto.isIncludedInSalaryStructure
          : !!dto.isIncludedInCtc || false,
      created_at: dto.registrationDate || dto.registrationDateFormatted || null,
      _raw: dto
    };
  };

  const fetchEsiData = async () => {
    try {
      setLoading(true);
      setFetchError(false);
      const organizationId = localStorage.getItem("organizationId");
      const response = await axios.get(`${GlobalConst.API_URL}/api/esi`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId,
        },
      });

      if (response.data && response.data.data) {
        const dto = response.data.data;
        const normalized = normalizeEsiDtoToUi(dto);
        setEsiData(normalized);
        setHasEsi(true);
      } else {
        setEsiData(null);
        setHasEsi(false);
      }
    } catch (error) {
      console.error("Error fetching ESI data:", error);
      setFetchError(true);
      setEsiData(null);
      setHasEsi(false);
      errorMsg("Error", error.response?.data?.message || "Failed to fetch ESI data", true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEsiData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = {}; // kept same shape as original (not used here)

  const handleEnableEsi = () => {
    navigate("/statutory-components/esi/form");
  };

  const handleEditEsi = () => {
    navigate("/statutory-components/esi/edit");
  };

  const handleDeleteEsi = async () => {
    try {
      setLoading(true);
      const organizationId = localStorage.getItem("organizationId");
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/esi/disable`,
        null,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data && response.data.status === 200) {
        // Backend returns disabled dto in data
        setEsiData(null);
        setHasEsi(false);
        successMsg("Success", "ESI configuration disabled successfully", false);
      } else {
        // still consider success if HTTP 200 but body differs
        setEsiData(null);
        setHasEsi(false);
        successMsg("Success", "ESI configuration disabled successfully", false);
      }
    } catch (error) {
      console.error("Error disabling ESI:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to disable ESI", true);
    } finally {
      setLoading(false);
    }
  };

  if (loading) return <Loader />;

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
            style={{ minHeight: "100vh", overflowY: "auto", display: "flex", justifyContent: "center" }}
          >
            <div className="w-100" style={{ maxWidth: "1200px" }}>
              {hasEsi ? (
                <div className="py-5">
                  <div className="d-flex justify-content-between align-items-center mb-4">
                    <h5 className="mb-0">Employees' State Insurance</h5>
                    <div className="d-flex gap-2">
                      <button
                        className="btn btn-icon btn-sm btn-light"
                        onClick={handleEditEsi}
                      >
                        <BiEdit className="text-primary" />
                      </button>
                      <button
                        className="btn btn-icon btn-sm btn-light"
                        onClick={handleDeleteEsi}
                      >
                        <BiTrash className="text-danger" />
                      </button>
                    </div>
                  </div>

                  <div className="card border-0 shadow-sm">
                    <div className="card-body">
                      <div className="row">
                        <div className="col-12">
                          <div className="mb-3 row">
                            <label className="col-sm-3 col-form-label fw-semibold">ESI Number</label>
                            <div className="col-sm-9">
                              <p className="form-control-plaintext">{esiData?.esi_number || "Not provided"}</p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-3 col-form-label fw-semibold">Deduction Cycle</label>
                            <div className="col-sm-9">
                              <p className="form-control-plaintext text-capitalize">{esiData?.deduction_cycle || "Monthly"}</p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-3 col-form-label fw-semibold">Employees' Contribution</label>
                            <div className="col-sm-9">
                              <p className="form-control-plaintext">
                                {typeof esiData?.employee_contribution_rate !== "undefined"
                                  ? `${esiData.employee_contribution_rate}% of Gross Pay`
                                  : "Not set"}
                              </p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-3 col-form-label fw-semibold">Employer's Contribution</label>
                            <div className="col-sm-9">
                              <p className="form-control-plaintext">
                                {typeof esiData?.employer_contribution_rate !== "undefined"
                                  ? `${esiData.employer_contribution_rate}% of Gross Pay`
                                  : "Not set"}
                              </p>
                            </div>
                          </div>

                          <div className="mb-3 row">
                            <label className="col-sm-3 col-form-label fw-semibold">Other Details</label>
                            <div className="col-sm-9">
                              <p className="form-control-plaintext">
                                {esiData?.include_employer_in_employee_structure
                                  ? "Employer's contribution is included in employee's salary structure."
                                  : "Employer's contribution is not included in employee's salary structure."}
                              </p>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Disable ESI Button */}
                  <div className="mt-4">
                    <button
                      className="btn btn-outline-danger"
                      onClick={handleDeleteEsi}
                    >
                      Disable ESI
                    </button>
                  </div>
                </div>
              ) : (
                <center>
                  <div className="card-body py-10">
                    <div className="text-center">
                      <div className="mb-7">
                        <img
                          //src={esiimg}
                          alt="No ESI configuration"
                          className="mw-100 h-200px h-sm-325px"
                        />
                      </div>

                      <div className="mb-10">
                        <h3 className="fw-bold text-gray-900 mb-2">Are you registered for ESI?</h3>
                        <div className="text-muted fw-semibold fs-5">
                          {fetchError ?
                            "Failed to load ESI configuration. Please try again later." :
                            "Organisations having 10 or more employees must register for Employee State Insurance (ESI). This scheme provides cash allowances and medical benefits for employees whose monthly salary is less than ₹21,000."}
                        </div>
                      </div>

                      <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                          className="btn btn-primary"
                          onClick={() => navigate("/statutory-components/esi/form")}
                        >
                          <i className="bi bi-plus fs-2"></i> Enable ESI
                        </button>
                      </div>
                    </div>
                  </div>
                </center>
              )}
            </div>
          </div>
        </div>
      </div>

      {signingIn && <Loader />}
    </>
  );
}
