// src/pages/form16/index.js

import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { Helmet } from "react-helmet-async";
import {
  FaUserCircle,
  FaPen,
  FaCloudUploadAlt,
  FaFileAlt,
  FaSignature,
  FaPaperPlane,
  FaPlayCircle,
  FaChevronRight,
} from "react-icons/fa";
import { errorMsg } from "../../../../shared/helpers/msgHelper";

import { useNavigate } from "react-router-dom";


import { useEffect, useState } from "react";


export default function Form16() {


    const [deductor, setDeductor] = useState({ name: "", parent: "" });
const [loading, setLoading] = useState(false);
const [fetchError, setFetchError] = useState(false);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");
  const token = localStorage.getItem("__t");


  const navigate = useNavigate();




    // Fetch income tax details (deductor info)
const fetchIncomeTaxDetails = async () => {
  try {
    setLoading(true);

    const response = await axios.get(`${GlobalConst.API_URL}/api/income-tax-details`, {
      headers: {
        Authorization: `Bearer ${token}`,
        organizationId: organizationId
      }
    });

    if (response.data && response.data.status === 200) {
      const data = response.data.data;
      setDeductor({
        name: data.authorizedPersonName || "",
        parent: data.authorizedPersonParent || ""
      });
      setFetchError(false);
    } else {
      setFetchError(true);
      errorMsg("Error", "Unexpected response format from server", true);
    }
  } catch (error) {
    setFetchError(true);
    console.error("Failed to fetch income tax details:", error);

    if (error.response) {
      errorMsg("Error", error.response.data?.message || "Failed to load income tax details", true);
    } else if (error.request) {
      errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
    } else {
      errorMsg("Error", "An unexpected error occurred", true);
    }
  } finally {
    setLoading(false);
  }
};

useEffect(() => {
  fetchIncomeTaxDetails();
}, []);





  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Form 16</title>
      </Helmet>

      {/* Header (white + bottom border) */}
      <div className="w-100 bg-white px-3 px-lg-5 py-3 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">
          Form 16&nbsp;
          <select
            className="form-select form-select-sm d-inline-block"
            style={{
              width: "130px",
              height: "40px",
              fontSize: "14px",
              marginLeft: "6px",
              display: "inline-block",
              verticalAlign: "middle",
              cursor: "pointer",
            }}
          >
            <option value="">Select</option>
            <option value="FY2024">FY 2024-25</option>
            <option value="FY2023">FY 2023-24</option>
          </select>
        </h5>

        <div className="d-flex align-items-center gap-2" />
      </div>

      {/* BODY — same segregation & gap as Employees page */}
      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-3 p-md-4 p-lg-10 bg-white"
            style={{ minHeight: "100vh" }}
          >
            {/* -------- HERO -------- */}
            <div className="text-center mt-2 mt-md-3">
              <h5 className="fw-semibold mb-1">
                It&apos;s time to generate Form 16 for the financial year
              </h5>
              <div
                className="mx-auto"
                style={{
                  width: 36,
                  height: 5,
                  background: "#3b82f6",
                  borderRadius: 2,
                  marginTop: 8,
                }}
              />
            </div>

            {/* -------- Verify your tax deductor -------- */}
            <div className="text-center mt-4">
             <div className="text-dark mb-3 fw-semibold">Verify your tax deductor</div>


              <div
                className="bg-white mx-auto shadow-sm border rounded-3"
                style={{ maxWidth: 386 }}
              >
                <div className="p-8 d-flex align-items-center justify-content-between">
                 <div className="d-flex align-items-center">
  <FaUserCircle size={48} className="text-secondary me-3" />
  <div className="text-start">
    <div className="fw-semibold">
      {loading ? "Loading..." : (deductor.name || "—")}
    </div>
    <div className="small text-muted">
      Son / Daughter of {loading ? "…" : (deductor.parent || "—")}
    </div>
  </div>
</div>

<button
  type="button"
  className="btn btn-light btn-sm border"
  aria-label="Edit deductor"
  onClick={() => navigate("/tax-details")}
>
  <FaPen className="me-1" />
  Edit
</button>

                </div>
              </div>

              <div className="small text-muted mt-2">
                Note: Remember that once you generate Form 16, you cannot change
                the deductor details.
              </div>

              <button
                type="button"
                className="btn btn-primary mt-3"
                style={{ minWidth: 170 }}
                  onClick={() => navigate("/form16s/generate")}
              >
                Generate Form 16
              </button>
            </div>

            {/* -------- Divider area to light section (optional bg tint) -------- */}
          <div style={{ marginTop: "80px" }} />


            {/* -------- Steps card -------- */}
            <div className="text-center">
              <h6 className="fw-semibold mb-3">
                How to generate Form 16 for your employees?
              </h6>

              <div
                className="bg-white shadow-sm border rounded-4 mx-auto p-3 p-md-4"
                style={{ maxWidth: 1158 }}
              >
                {/* Card header */}
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <div className="text-start">
                    <div className="text-uppercase small text-muted">
                      Steps to generate <span className="fw-semibold">Form 16</span>
                    </div>
                  </div>
                  <a href="#help" className="text-decoration-none small">
                    <span role="img" aria-label="sparkles">✨</span> Help Guide
                  </a>
                </div>

                {/* Steps row */}
                <div className="row g-3 align-items-center">
                  {/* Step 1 */}
                  <div className="col-12 col-md d-flex align-items-center justify-content-center justify-content-md-start">
                    <FaCloudUploadAlt size={28} className="text-primary me-2" />
                    <div className="text-start">
                      <div className="small">Upload Form 16 Part A</div>
                    </div>
                  </div>

                  <div className="col-auto d-none d-md-flex justify-content-center text-muted">
                    <FaChevronRight />
                  </div>

                  {/* Step 2 */}
                  <div className="col-12 col-md d-flex align-items-center justify-content-center justify-content-md-start">
                    <FaFileAlt size={26} className="text-warning me-2" />
                    <div className="text-start">
                      <div className="small">Generate Form 16</div>
                    </div>
                  </div>

                  <div className="col-auto d-none d-md-flex justify-content-center text-muted">
                    <FaChevronRight />
                  </div>

                  {/* Step 3 */}
                  <div className="col-12 col-md d-flex align-items-center justify-content-center justify-content-md-start">
                    <FaSignature size={26} className="text-info me-2" />
                    <div className="text-start">
                      <div className="small">Sign Form 16</div>
                    </div>
                  </div>

                  <div className="col-auto d-none d-md-flex justify-content-center text-muted">
                    <FaChevronRight />
                  </div>

                  {/* Step 4 */}
                  <div className="col-12 col-md d-flex align-items-center justify-content-center justify-content-md-start">
                    <FaPaperPlane size={24} className="text-success me-2" />
                    <div className="text-start">
                      <div className="small">Publish/Email</div>
                    </div>
                  </div>

                  {/* Learn card */}
                  <div className="col-12 col-md-4">
                    <div className="border rounded-3 p-3 d-flex align-items-center justify-content-between h-100">
                      <div className="text-start">
                        <div className="small">Learn how to</div>
                        <div className="small fw-semibold">generate Form 16</div>
                      </div>
                      <button type="button" className="btn btn-light border">
                        <FaPlayCircle className="me-1" /> Watch
                      </button>
                    </div>
                  </div>
                </div>
              </div>
              {/* /card */}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
