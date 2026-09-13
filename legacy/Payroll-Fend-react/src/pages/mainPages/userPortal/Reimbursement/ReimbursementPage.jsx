import React, { useState, useCallback, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { PiReceiptDuotone } from "react-icons/pi";
import { getDecodedToken } from "../../../../shared/helpers/tokenHelper";
import { errorMsg } from "../../../../shared/helpers/msgHelper";
import { getEmployeeReimbursements } from "../../../../shared/services/reimbursementService";
import ReimbursementTable from "./ReimbursementTable";
import ApplyReimbursementModal from "./ApplyReimbursementModal";

export default function ReimbursementPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [reimbursements, setReimbursements] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Auth — follows exact same pattern as payslipGenerator.js
  const employeeId = getDecodedToken()?.sub;
  const organizationId =
    localStorage.getItem("organizationId") || "default-org-id";

  // ── Fetch reimbursements from GET /api/employee/reimbursements ──────────────
  const fetchReimbursements = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);

      const data = await getEmployeeReimbursements();

      if (Array.isArray(data)) {
        setReimbursements(data);
      } else {
        setReimbursements([]);
      }
    } catch (err) {
      console.error("Error fetching reimbursements:", err);
      setError("Unable to load reimbursements. Please try again.");
      errorMsg(
        "Error",
        "Failed to fetch reimbursement records. Please try again.",
        true
      );
    } finally {
      setLoading(false);
    }
  }, []);

  // Load on mount
  useEffect(() => {
    if (employeeId) {
      fetchReimbursements();
    }
  }, [fetchReimbursements, employeeId]);

  // ── Called by modal after successful POST ────────────────────────────────────
  const handleSubmitSuccess = () => {
    setIsModalOpen(false);
    fetchReimbursements(); // refresh table from backend
  };

  return (
    <>
      <Helmet>
        <title>Reimbursement | HRMS InfiNevoCloud</title>
      </Helmet>

      {/* Page Toolbar */}
      <div id="kt_app_toolbar" className="app-toolbar pt-5">
        <div
          id="kt_app_toolbar_container"
          className="app-container container-fluid d-flex align-items-stretch"
        >
          <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
            <div className="page-title d-flex flex-column gap-1 me-3 mb-2">
              <div className="d-flex align-items-center gap-3">
                <span
                  style={{
                    background: "linear-gradient(135deg, #ecfdf5, #d1fae5)",
                    borderRadius: "10px",
                    padding: "8px 10px",
                    display: "flex",
                    alignItems: "center",
                  }}
                >
                  <PiReceiptDuotone
                    size={24}
                    style={{ color: "#059669" }}
                  />
                </span>
                <h1 className="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0 mb-0">
                  Reimbursement
                </h1>
              </div>
            </div>
            <button
              id="apply-reimbursement-btn"
              className="btn btn-sm btn-success ms-3 px-4 py-3"
              onClick={() => setIsModalOpen(true)}
            >
              + Apply Reimbursement
            </button>
          </div>
        </div>
      </div>

      {/* Page Content */}
      <div id="kt_app_content" className="app-content flex-column-fluid">
        <div
          id="kt_app_content_container"
          className="app-container container-fluid"
        >
          {/* Summary Stats */}
          <div className="row g-4 mb-5">
            <SummaryCard
              label="Total Requests"
              value={reimbursements.length}
              color="#3b82f6"
              bg="#eff6ff"
            />
            <SummaryCard
              label="Pending"
              value={
                reimbursements.filter((r) => r.status === "PENDING").length
              }
              color="#f59e0b"
              bg="#fff8e1"
            />
            <SummaryCard
              label="Approved"
              value={
                reimbursements.filter((r) => r.status === "APPROVED").length
              }
              color="#059669"
              bg="#ecfdf5"
            />
            <SummaryCard
              label="Rejected"
              value={
                reimbursements.filter((r) => r.status === "REJECTED").length
              }
              color="#dc2626"
              bg="#fef2f2"
            />
          </div>

          {/* Reimbursement History Table Card */}
          <div className="card card-flush shadow-sm">
            <div
              className="card-header pt-6 pb-4"
              style={{ borderBottom: "1px solid #f3f4f6" }}
            >
              <div className="card-title">
                <h3 className="card-label fw-bold text-dark mb-1 fs-5">
                  Reimbursement History
                </h3>
                <span className="text-gray-400 fw-semibold fs-7 d-block">
                  All your submitted reimbursement requests
                </span>
              </div>
            </div>
            <div className="card-body pt-4">
              {/* Error state with Retry */}
              {error && !loading ? (
                <div
                  style={{
                    textAlign: "center",
                    padding: "40px 20px",
                    color: "#6b7280",
                  }}
                >
                  <div style={{ fontSize: "1.8rem", marginBottom: "10px" }}>
                    ⚠️
                  </div>
                  <div
                    style={{
                      fontWeight: 600,
                      color: "#374151",
                      marginBottom: "6px",
                    }}
                  >
                    Unable to load reimbursements.
                  </div>
                  <div style={{ fontSize: "0.875rem", marginBottom: "16px" }}>
                    Please try again.
                  </div>
                  <button
                    className="btn btn-sm btn-outline-success"
                    onClick={fetchReimbursements}
                  >
                    Retry
                  </button>
                </div>
              ) : (
                <ReimbursementTable
                  data={reimbursements}
                  loading={loading}
                  onApply={() => setIsModalOpen(true)}
                />
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Apply Reimbursement Modal */}
      <ApplyReimbursementModal
        visible={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={handleSubmitSuccess}
        organizationId={organizationId}
      />
    </>
  );
}

/** Small summary stat card */
function SummaryCard({ label, value, color, bg }) {
  return (
    <div className="col-6 col-md-3">
      <div
        className="card"
        style={{
          border: "none",
          borderRadius: "10px",
          background: bg,
          padding: "16px 20px",
          boxShadow: "0 1px 4px rgba(0,0,0,0.06)",
        }}
      >
        <div
          style={{ fontSize: "1.6rem", fontWeight: 800, color, lineHeight: 1 }}
        >
          {value}
        </div>
        <div
          style={{
            fontSize: "0.8rem",
            fontWeight: 600,
            color: "#6b7280",
            marginTop: "4px",
          }}
        >
          {label}
        </div>
      </div>
    </div>
  );
}
