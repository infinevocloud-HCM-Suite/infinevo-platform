import React, { useState, useEffect, useCallback } from "react";
import { Helmet } from "react-helmet-async";
import { PiReceiptDuotone } from "react-icons/pi";
import { successMsg, errorMsg } from "../../../shared/helpers/msgHelper";
import {
  getAdminReimbursements,
  approveAdminReimbursement,
  rejectAdminReimbursement,
} from "../../../shared/services/reimbursementService";
import mockAdminReimbursements from "./mockAdminReimbursements";
import AdminReimbursementTable from "./AdminReimbursementTable";

// ── Filters ──────────────────────────────────────────────────────────────────
const FILTERS = [
  { label: "All", value: "ALL" },
  { label: "Pending", value: "PENDING" },
  { label: "Approved", value: "APPROVED" },
  { label: "Rejected", value: "REJECTED" },
];

/**
 * AdminReimbursementPage
 *
 * Admin portal page for managing employee reimbursement requests.
 * Connected to live backend APIs:
 *  - GET /admin/reimbursements
 *  - PUT /admin/reimbursements/{id}/approve
 *  - PUT /admin/reimbursements/{id}/reject
 */
export default function AdminReimbursementPage() {
  // ── Core data state ────────────────────────────────────────────────────────
  const [reimbursements, setReimbursements] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [isApproving, setIsApproving] = useState(false);
  const [isRejecting, setIsRejecting] = useState(false);

  // ── UI state ───────────────────────────────────────────────────────────────
  const [activeFilter, setActiveFilter] = useState("ALL");
  const [searchTerm, setSearchTerm] = useState("");

  // ── Fetch reimbursements from backend ───────────────────────────────────────
  const fetchReimbursements = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getAdminReimbursements();
      if (Array.isArray(data)) {
        setReimbursements(data);
      } else {
        setReimbursements([]);
      }
    } catch (err) {
      console.warn("Could not fetch reimbursements from backend, checking fallback:", err);
      setError("Unable to load reimbursements.");
      errorMsg(
        "Error",
        "Failed to load reimbursement requests. Please try again.",
        true
      );
      // Fallback to mock data if backend unavailable during initial setup
      setReimbursements(mockAdminReimbursements);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReimbursements();
  }, [fetchReimbursements]);

  // ── Derived summary counts (calculated dynamically from fetched data) ──────
  const total = reimbursements.length;
  const pending = reimbursements.filter((r) => r.status === "PENDING").length;
  const approved = reimbursements.filter((r) => r.status === "APPROVED").length;
  const rejected = reimbursements.filter((r) => r.status === "REJECTED").length;

  // ── Approve handler ────────────────────────────────────────────────────────
  const handleApprove = async (id, approvedAmount, remarks, reimbursementMonth) => {
    if (isApproving) return;
    setIsApproving(true);
    try {
      const resData = await approveAdminReimbursement(id, {
        approvedAmount,
        remarks,
        reimbursementMonth,
      });

      await successMsg(
        "Approved!",
        resData?.message || "Reimbursement request approved successfully.",
        true
      );
      await fetchReimbursements();
    } catch (err) {
      console.error("Error approving reimbursement:", err);
      if (err?.response?.status === 403) {
        errorMsg("Access Denied", "You do not have permission to approve reimbursements.", true);
      } else {
        const msg =
          err?.response?.data?.message ||
          err?.message ||
          "Failed to approve reimbursement.";
        errorMsg("Approval Failed", msg, true);
      }
    } finally {
      setIsApproving(false);
    }
  };

  // ── Reject handler ─────────────────────────────────────────────────────────
  const handleReject = async (id, remarks) => {
    if (isRejecting) return;
    setIsRejecting(true);
    try {
      const resData = await rejectAdminReimbursement(id, { remarks });

      await successMsg(
        "Rejected!",
        resData?.message || "Reimbursement request rejected.",
        true
      );
      await fetchReimbursements();
    } catch (err) {
      console.error("Error rejecting reimbursement:", err);
      if (err?.response?.status === 403) {
        errorMsg("Access Denied", "You do not have permission to reject reimbursements.", true);
      } else {
        const msg =
          err?.response?.data?.message ||
          err?.message ||
          "Failed to reject reimbursement.";
        errorMsg("Rejection Failed", msg, true);
      }
    } finally {
      setIsRejecting(false);
    }
  };

  return (
    <>
      <Helmet>
        <title>Reimbursement | HRMS InfiNevoCloud</title>
      </Helmet>

      {/* ── Page Toolbar ─────────────────────────────────────────────────── */}
      <div id="kt_app_toolbar" className="app-toolbar pt-5">
        <div
          id="kt_app_toolbar_container"
          className="app-container container-fluid d-flex align-items-stretch"
        >
          <div className="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">
            {/* Page title */}
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
                  <PiReceiptDuotone size={24} style={{ color: "#059669" }} />
                </span>
                <div>
                  <h1 className="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0 mb-0">
                    Reimbursement
                  </h1>
                  <span className="text-gray-400 fw-semibold fs-7 mt-1 d-block">
                    Manage and review employee reimbursement requests
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ── Page Content ─────────────────────────────────────────────────── */}
      <div id="kt_app_content" className="app-content flex-column-fluid">
        <div
          id="kt_app_content_container"
          className="app-container container-fluid"
        >
          {/* Summary Stat Cards */}
          <div className="row g-4 mb-5">
            <SummaryCard
              label="Total Requests"
              value={total}
              color="#3b82f6"
              bg="#eff6ff"
            />
            <SummaryCard
              label="Pending"
              value={pending}
              color="#f59e0b"
              bg="#fff8e1"
            />
            <SummaryCard
              label="Approved"
              value={approved}
              color="#059669"
              bg="#ecfdf5"
            />
            <SummaryCard
              label="Rejected"
              value={rejected}
              color="#dc2626"
              bg="#fef2f2"
            />
          </div>

          {/* Main Card with Toolbar & Table */}
          <div className="card card-flush shadow-sm">
            {/* Card Header with Filter Tabs & Search */}
            <div
              className="card-header pt-6 pb-4"
              style={{ borderBottom: "1px solid #f3f4f6" }}
            >
              <div className="card-title">
                {/* Status Filter Tabs */}
                <div style={{ display: "flex", gap: "6px" }}>
                  {FILTERS.map((tab) => {
                    const isActive = activeFilter === tab.value;
                    const count =
                      tab.value === "ALL"
                        ? total
                        : tab.value === "PENDING"
                        ? pending
                        : tab.value === "APPROVED"
                        ? approved
                        : rejected;

                    return (
                      <button
                        key={tab.value}
                        id={`filter-tab-${tab.value.toLowerCase()}`}
                        onClick={() => setActiveFilter(tab.value)}
                        style={{
                          padding: "6px 14px",
                          borderRadius: "20px",
                          border: "1px solid",
                          borderColor: isActive ? "#059669" : "#e5e7eb",
                          background: isActive ? "#059669" : "#fff",
                          color: isActive ? "#fff" : "#4b5563",
                          fontWeight: isActive ? 700 : 500,
                          fontSize: "0.82rem",
                          cursor: "pointer",
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "6px",
                          transition: "all 0.15s",
                        }}
                      >
                        <span>{tab.label}</span>
                        <span
                          style={{
                            background: isActive
                              ? "rgba(255,255,255,0.25)"
                              : "#f3f4f6",
                            color: isActive ? "#fff" : "#6b7280",
                            borderRadius: "10px",
                            padding: "1px 6px",
                            fontSize: "0.75rem",
                            fontWeight: 700,
                          }}
                        >
                          {count}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Search Bar */}
              <div className="card-toolbar">
                <input
                  id="admin-reimbursement-search"
                  type="text"
                  placeholder="Search by employee, type, billing month..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    padding: "8px 14px",
                    border: "1px solid #d1d5db",
                    borderRadius: "6px",
                    fontSize: "0.85rem",
                    width: "280px",
                    outline: "none",
                    color: "#374151",
                  }}
                  onFocus={(e) => (e.target.style.borderColor = "#059669")}
                  onBlur={(e) => (e.target.style.borderColor = "#d1d5db")}
                />
              </div>
            </div>

            {/* Card Body */}
            <div className="card-body pt-4">
              {error && !loading && reimbursements.length === 0 ? (
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
                <AdminReimbursementTable
                  data={reimbursements}
                  loading={loading}
                  isApproving={isApproving}
                  isRejecting={isRejecting}
                  onApprove={handleApprove}
                  onReject={handleReject}
                  searchTerm={searchTerm}
                  activeFilter={activeFilter}
                />
              )}
            </div>
          </div>
        </div>
      </div>
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
