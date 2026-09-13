import React, { useState } from "react";
import { Modal, Input } from "antd";
import {
  PiEyeDuotone,
  PiCheckCircleDuotone,
  PiXCircleDuotone,
} from "react-icons/pi";
import { PaperClipOutlined as AntPaperClip } from "@ant-design/icons";
import StatusBadge from "../../../shared/components/reimbursement/StatusBadge";
import { getAdminReimbursementById } from "../../../shared/services/reimbursementService";

const { TextArea } = Input;

const formatCurrency = (amount) => {
  if (amount === null || amount === undefined || amount === "") return "—";
  return `₹${Number(amount).toLocaleString("en-IN", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })}`;
};

const formatReimbursementType = (rawType, description) => {
  const t = rawType || "";
  const match = (description || "").match(/^\[Type:\s*(.+?)\]/i);
  if (match && match[1]) {
    const capitalized = t ? t.charAt(0).toUpperCase() + t.slice(1).toLowerCase() : "Other";
    return `${capitalized} (${match[1]})`;
  }
  return t ? t.charAt(0).toUpperCase() + t.slice(1).toLowerCase() : "—";
};

const formatDescription = (description) => {
  if (!description) return "—";
  return description.replace(/^\[Type:\s*(.+?)\]\s*/i, "");
};

const PAGE_SIZE = 8;

/**
 * AdminReimbursementTable
 *
 * Props:
 *  - data          : array of reimbursement objects
 *  - loading       : boolean
 *  - isApproving   : boolean
 *  - isRejecting   : boolean
 *  - onApprove     : (id, approvedAmount, remarks, reimbursementMonth) => void
 *  - onReject      : (id, remarks) => void
 *  - searchTerm    : string (controlled from parent)
 *  - activeFilter  : "ALL" | "PENDING" | "APPROVED" | "REJECTED"
 */
export default function AdminReimbursementTable({
  data = [],
  loading = false,
  isApproving = false,
  isRejecting = false,
  onApprove,
  onReject,
  searchTerm = "",
  activeFilter = "ALL",
}) {
  const [currentPage, setCurrentPage] = useState(1);

  // ── Review modal state ─────────────────────────────────────────────────────
  const [reviewModal, setReviewModal] = useState({ open: false, record: null });
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [approvedAmount, setApprovedAmount] = useState("");
  const [remarks, setRemarks] = useState("");
  const [amountError, setAmountError] = useState("");
  const [reimbursementMonth, setReimbursementMonth] = useState("");

  // ── Reject confirm state ──────────────────────────────────────────────────
  const [rejectModal, setRejectModal] = useState({ open: false, record: null });
  const [rejectRemarks, setRejectRemarks] = useState("");
  const [rejectError, setRejectError] = useState("");

  // ── Filtering ──────────────────────────────────────────────────────────────
  const filtered = data.filter((row) => {
    const q = searchTerm.toLowerCase();
    const type = row.reimbursementType || row.type || "";
    const matchesSearch =
      !q ||
      row.employeeName?.toLowerCase().includes(q) ||
      row.employeeNumber?.toLowerCase().includes(q) ||
      type.toLowerCase().includes(q) ||
      row.description?.toLowerCase().includes(q) ||
      row.paymentStatus?.toLowerCase().includes(q) ||
      row.reimbursementMonth?.toLowerCase().includes(q) ||
      row.status?.toLowerCase().includes(q);

    const matchesFilter =
      activeFilter === "ALL" || row.status?.toUpperCase() === activeFilter;

    return matchesSearch && matchesFilter;
  });

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE) || 1;
  const paginated = filtered.slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE
  );

  // Reset to page 1 when filter/search changes
  React.useEffect(() => {
    setCurrentPage(1);
  }, [searchTerm, activeFilter]);

  // ── Open review modal (fetches fresh details from backend) ────────────────
  const openReview = async (record) => {
    setReviewModal({ open: true, record });
    setApprovedAmount(
      record.approvedAmount !== null && record.approvedAmount !== undefined
        ? record.approvedAmount.toString()
        : record.requestedAmount?.toString() || ""
    );
    setRemarks(record.remarks || "");
    setAmountError("");
    setReimbursementMonth(
      record.reimbursementMonth ||
        (() => {
          const now = new Date();
          return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
        })()
    );

    // Fetch latest fresh record from GET /admin/reimbursements/{id}
    if (record.id) {
      setLoadingDetails(true);
      try {
        const fresh = await getAdminReimbursementById(record.id);
        if (fresh) {
          setReviewModal({ open: true, record: fresh });
          if (fresh.approvedAmount !== null && fresh.approvedAmount !== undefined) {
            setApprovedAmount(fresh.approvedAmount.toString());
          }
          if (fresh.remarks) setRemarks(fresh.remarks);
          if (fresh.reimbursementMonth) setReimbursementMonth(fresh.reimbursementMonth);
        }
      } catch (err) {
        console.warn("Could not fetch fresh reimbursement detail, using row record:", err);
      } finally {
        setLoadingDetails(false);
      }
    }
  };

  const closeReview = () => {
    setReviewModal({ open: false, record: null });
    setApprovedAmount("");
    setRemarks("");
    setAmountError("");
    setReimbursementMonth("");
  };

  // ── Approve handler ────────────────────────────────────────────────────────
  const handleApprove = () => {
    const amt = parseFloat(approvedAmount);
    if (!approvedAmount || isNaN(amt) || amt <= 0) {
      setAmountError("Please enter a valid approved amount greater than 0.");
      return;
    }
    if (amt > reviewModal.record.requestedAmount) {
      setAmountError(
        `Approved amount cannot exceed requested amount (${formatCurrency(
          reviewModal.record.requestedAmount
        )}).`
      );
      return;
    }
    setAmountError("");
    onApprove &&
      onApprove(reviewModal.record.id, amt, remarks, reimbursementMonth);
    closeReview();
  };

  // ── Open reject modal ──────────────────────────────────────────────────────
  const openReject = (record) => {
    setRejectModal({ open: true, record });
    setRejectRemarks("");
    setRejectError("");
  };

  const closeReject = () => {
    setRejectModal({ open: false, record: null });
    setRejectRemarks("");
    setRejectError("");
  };

  const handleReject = () => {
    if (!rejectRemarks.trim()) {
      setRejectError("Please provide a reason for rejection.");
      return;
    }
    setRejectError("");
    onReject && onReject(rejectModal.record.id, rejectRemarks.trim());
    closeReject();
  };

  const thStyle = {
    padding: "12px 14px",
    fontSize: "0.78rem",
    fontWeight: 700,
    color: "#6b7280",
    textTransform: "uppercase",
    letterSpacing: "0.5px",
    background: "#f9fafb",
    borderBottom: "1px solid #e5e7eb",
    whiteSpace: "nowrap",
  };

  const tdStyle = {
    padding: "12px 14px",
    fontSize: "0.875rem",
    color: "#374151",
    borderBottom: "1px solid #f3f4f6",
    verticalAlign: "middle",
  };

  if (loading) {
    return (
      <div style={{ padding: "48px", textAlign: "center", color: "#9ca3af" }}>
        <div
          className="spinner-border spinner-border-sm text-success me-2"
          role="status"
        />
        <span>Loading reimbursements...</span>
      </div>
    );
  }

  return (
    <>
      <div
        style={{
          overflowX: "auto",
          borderRadius: "8px",
          border: "1px solid #e5e7eb",
        }}
      >
        <table
          style={{
            width: "100%",
            borderCollapse: "collapse",
            minWidth: "1050px",
          }}
        >
          <thead>
            <tr>
              <th style={thStyle}>#</th>
              <th style={thStyle}>Employee</th>
              <th style={thStyle}>Type</th>
              <th style={thStyle}>Description</th>
              <th style={{ ...thStyle, textAlign: "right" }}>Requested (₹)</th>
              <th style={{ ...thStyle, textAlign: "right" }}>Approved (₹)</th>
              <th style={thStyle}>Billing Month</th>
              <th style={thStyle}>Status</th>
              <th style={thStyle}>Request Date</th>
              <th style={thStyle}>Payment</th>
              <th style={{ ...thStyle, textAlign: "center" }}>Action</th>
            </tr>
          </thead>
          <tbody>
            {paginated.length === 0 ? (
              <tr>
                <td
                  colSpan={11}
                  style={{
                    textAlign: "center",
                    padding: "56px 20px",
                    color: "#9ca3af",
                    fontSize: "0.9rem",
                  }}
                >
                  <div style={{ fontSize: "2.5rem", marginBottom: "12px" }}>
                    📋
                  </div>
                  <div
                    style={{
                      fontWeight: 700,
                      fontSize: "1rem",
                      color: "#374151",
                      marginBottom: "6px",
                    }}
                  >
                    No reimbursement requests found
                  </div>
                  <div style={{ fontSize: "0.875rem" }}>
                    {searchTerm
                      ? "Try adjusting your search or filter."
                      : "No requests match the selected filter."}
                  </div>
                </td>
              </tr>
            ) : (
              paginated.map((row, idx) => (
                <tr
                  key={row.id}
                  style={{
                    background: idx % 2 === 0 ? "#fff" : "#fafafa",
                    transition: "background 0.15s",
                  }}
                  onMouseEnter={(e) =>
                    (e.currentTarget.style.background = "#f0fdf4")
                  }
                  onMouseLeave={(e) =>
                    (e.currentTarget.style.background =
                      idx % 2 === 0 ? "#fff" : "#fafafa")
                  }
                >
                  <td
                    style={{
                      ...tdStyle,
                      color: "#9ca3af",
                      fontSize: "0.8rem",
                    }}
                  >
                    {(currentPage - 1) * PAGE_SIZE + idx + 1}
                  </td>
                  <td style={tdStyle}>
                    <div style={{ fontWeight: 600, color: "#111827" }}>
                      {row.employeeName || row.employeeNumber || "—"}
                    </div>
                    {(row.employeeNumber || row.employeeId) && (
                      <div
                        style={{
                          fontSize: "0.78rem",
                          color: "#6b7280",
                          marginTop: "2px",
                          fontWeight: 500,
                        }}
                      >
                        {row.employeeNumber || row.employeeId}
                      </div>
                    )}
                  </td>
                  <td style={tdStyle}>
                    <span
                      style={{
                        background: "#f0fdf4",
                        color: "#059669",
                        border: "1px solid #bbf7d0",
                        borderRadius: "12px",
                        padding: "2px 10px",
                        fontSize: "0.78rem",
                        fontWeight: 600,
                      }}
                    >
                      {formatReimbursementType(row.reimbursementType || row.type, row.description)}
                    </span>
                  </td>
                  <td
                    style={{
                      ...tdStyle,
                      maxWidth: "180px",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                    title={formatDescription(row.description)}
                  >
                    {formatDescription(row.description)}
                  </td>
                  <td
                    style={{
                      ...tdStyle,
                      textAlign: "right",
                      fontWeight: 600,
                    }}
                  >
                    {formatCurrency(row.requestedAmount)}
                  </td>
                  <td style={{ ...tdStyle, textAlign: "right" }}>
                    {formatCurrency(row.approvedAmount)}
                  </td>
                  <td style={tdStyle}>
                    <span
                      style={{
                        color: row.status === "APPROVED" && row.reimbursementMonth ? "#4b5563" : "#9ca3af",
                        fontWeight: 500,
                        fontSize: "0.83rem",
                      }}
                    >
                      {row.status === "APPROVED" ? (row.reimbursementMonth || "—") : "—"}
                    </span>
                  </td>
                  <td style={tdStyle}>
                    <StatusBadge status={row.status} />
                  </td>
                  <td
                    style={{
                      ...tdStyle,
                      color: "#6b7280",
                      fontSize: "0.82rem",
                    }}
                  >
                    {row.requestDate}
                  </td>
                  <td style={tdStyle}>
                    <StatusBadge status={row.paymentStatus} />
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    {row.status === "PENDING" ? (
                      <button
                        id={`review-btn-${row.id}`}
                        onClick={() => openReview(row)}
                        style={{
                          padding: "5px 14px",
                          background: "#059669",
                          border: "none",
                          borderRadius: "6px",
                          color: "#fff",
                          fontWeight: 600,
                          fontSize: "0.8rem",
                          cursor: "pointer",
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "5px",
                          transition: "background 0.15s",
                        }}
                        onMouseEnter={(e) =>
                          (e.currentTarget.style.background = "#047857")
                        }
                        onMouseLeave={(e) =>
                          (e.currentTarget.style.background = "#059669")
                        }
                      >
                        <PiEyeDuotone size={14} /> Review
                      </button>
                    ) : (
                      <button
                        id={`view-btn-${row.id}`}
                        onClick={() => openReview(row)}
                        style={{
                          padding: "5px 12px",
                          background: "#f3f4f6",
                          border: "1px solid #e5e7eb",
                          borderRadius: "6px",
                          color: "#374151",
                          fontWeight: 500,
                          fontSize: "0.8rem",
                          cursor: "pointer",
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "4px",
                          transition: "background 0.15s",
                        }}
                        onMouseEnter={(e) =>
                          (e.currentTarget.style.background = "#e5e7eb")
                        }
                        onMouseLeave={(e) =>
                          (e.currentTarget.style.background = "#f3f4f6")
                        }
                      >
                        <PiEyeDuotone size={14} /> View
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {filtered.length > 0 && (
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginTop: "14px",
            fontSize: "0.82rem",
            color: "#6b7280",
          }}
        >
          <span>
            Showing{" "}
            {Math.min((currentPage - 1) * PAGE_SIZE + 1, filtered.length)}–
            {Math.min(currentPage * PAGE_SIZE, filtered.length)} of{" "}
            {filtered.length} records
          </span>
          <div style={{ display: "flex", gap: "4px" }}>
            <button
              onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              disabled={currentPage === 1}
              style={{
                padding: "5px 11px",
                border: "1px solid #d1d5db",
                borderRadius: "5px",
                background: currentPage === 1 ? "#f9fafb" : "#fff",
                color: currentPage === 1 ? "#d1d5db" : "#374151",
                cursor: currentPage === 1 ? "default" : "pointer",
                fontWeight: 500,
              }}
            >
              ‹ Prev
            </button>
            {Array.from({ length: totalPages }, (_, i) => i + 1).map((pg) => (
              <button
                key={pg}
                onClick={() => setCurrentPage(pg)}
                style={{
                  padding: "5px 10px",
                  border: "1px solid",
                  borderColor: pg === currentPage ? "#059669" : "#d1d5db",
                  borderRadius: "5px",
                  background: pg === currentPage ? "#059669" : "#fff",
                  color: pg === currentPage ? "#fff" : "#374151",
                  cursor: "pointer",
                  fontWeight: pg === currentPage ? 700 : 400,
                }}
              >
                {pg}
              </button>
            ))}
            <button
              onClick={() =>
                setCurrentPage((p) => Math.min(totalPages, p + 1))
              }
              disabled={currentPage === totalPages}
              style={{
                padding: "5px 11px",
                border: "1px solid #d1d5db",
                borderRadius: "5px",
                background: currentPage === totalPages ? "#f9fafb" : "#fff",
                color: currentPage === totalPages ? "#d1d5db" : "#374151",
                cursor: currentPage === totalPages ? "default" : "pointer",
                fontWeight: 500,
              }}
            >
              Next ›
            </button>
          </div>
        </div>
      )}

      {/* ── Review / Details Modal ─────────────────────────────────────────── */}
      <Modal
        open={reviewModal.open}
        onCancel={closeReview}
        footer={null}
        width={580}
        centered
        destroyOnClose
        title={
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span
              style={{
                background: "linear-gradient(135deg, #ecfdf5, #d1fae5)",
                borderRadius: "8px",
                padding: "6px 8px",
                display: "inline-flex",
              }}
            >
              <PiEyeDuotone size={18} style={{ color: "#059669" }} />
            </span>
            <span
              style={{
                fontWeight: 700,
                fontSize: "1rem",
                color: "#111827",
              }}
            >
              Reimbursement Review & Details
            </span>
          </div>
        }
      >
        {loadingDetails ? (
          <div style={{ padding: "40px", textAlign: "center", color: "#6b7280" }}>
            <div className="spinner-border spinner-border-sm text-success me-2" role="status" />
            <span>Loading details...</span>
          </div>
        ) : (
          reviewModal.record && (
            <div>
              {/* Info grid */}
              <div
                style={{
                  background: "#f9fafb",
                  borderRadius: "10px",
                  border: "1px solid #e5e7eb",
                  padding: "16px 20px",
                  marginBottom: "18px",
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "14px 24px",
                }}
              >
                <InfoRow
                  label="Employee"
                  value={reviewModal.record.employeeName || "—"}
                />
                <InfoRow
                  label="Employee Number"
                  value={
                    reviewModal.record.employeeNumber ||
                    reviewModal.record.employeeId ||
                    "—"
                  }
                />
                <InfoRow
                  label="Type"
                  value={
                    formatReimbursementType(
                      reviewModal.record.reimbursementType ||
                        reviewModal.record.type,
                      reviewModal.record.description
                    )
                  }
                />
                <InfoRow
                  label="Bill Date"
                  value={reviewModal.record.billDate || "—"}
                />
                <InfoRow
                  label="Request Date"
                  value={reviewModal.record.requestDate || "—"}
                />
                <InfoRow
                  label="Requested Amount"
                  value={formatCurrency(reviewModal.record.requestedAmount)}
                  valueStyle={{
                    fontWeight: 700,
                    color: "#111827",
                    fontSize: "1rem",
                  }}
                />
                <InfoRow
                  label="Billing Month"
                  value={
                    reviewModal.record.status === "APPROVED"
                      ? reviewModal.record.reimbursementMonth || "—"
                      : "—"
                  }
                />
                <InfoRow
                  label="Status"
                  value={<StatusBadge status={reviewModal.record.status} />}
                />
                <InfoRow
                  label="Payment Status"
                  value={<StatusBadge status={reviewModal.record.paymentStatus} />}
                />
                <InfoRow
                  label="Description"
                  value={formatDescription(reviewModal.record.description)}
                  span
                />
              </div>

              {/* Editable / View fields */}
              <div style={{ marginBottom: "14px" }}>
                <label
                  style={{
                    display: "block",
                    fontWeight: 600,
                    fontSize: "0.85rem",
                    color: "#374151",
                    marginBottom: "6px",
                  }}
                >
                  Approved Amount (₹)
                </label>
                <input
                  id="admin-approved-amount-input"
                  type="number"
                  min={0}
                  max={reviewModal.record.requestedAmount}
                  value={approvedAmount}
                  onChange={(e) => {
                    setApprovedAmount(e.target.value);
                    setAmountError("");
                  }}
                  disabled={reviewModal.record.status !== "PENDING"}
                  placeholder="Enter approved amount"
                  style={{
                    width: "100%",
                    padding: "9px 13px",
                    border: `1px solid ${amountError ? "#fca5a5" : "#d1d5db"}`,
                    borderRadius: "7px",
                    fontSize: "0.9rem",
                    color: "#111827",
                    background:
                      reviewModal.record.status !== "PENDING"
                        ? "#f9fafb"
                        : "#fff",
                    outline: "none",
                  }}
                  onFocus={(e) =>
                    reviewModal.record.status === "PENDING" &&
                    (e.target.style.borderColor = "#059669")
                  }
                  onBlur={(e) =>
                    (e.target.style.borderColor = amountError
                      ? "#fca5a5"
                      : "#d1d5db")
                  }
                />
                {amountError && (
                  <div
                    style={{
                      color: "#dc2626",
                      fontSize: "0.8rem",
                      marginTop: "5px",
                    }}
                  >
                    {amountError}
                  </div>
                )}
              </div>

              {/* Reimbursement Month dropdown */}
              <ReimbursementMonthDropdown
                value={reimbursementMonth}
                onChange={setReimbursementMonth}
                disabled={reviewModal.record.status !== "PENDING"}
              />

              <div style={{ marginBottom: "18px" }}>
                <label
                  style={{
                    display: "block",
                    fontWeight: 600,
                    fontSize: "0.85rem",
                    color: "#374151",
                    marginBottom: "6px",
                  }}
                >
                  Remarks
                </label>
                <TextArea
                  id="admin-remarks-input"
                  rows={3}
                  value={remarks}
                  onChange={(e) => setRemarks(e.target.value)}
                  disabled={reviewModal.record.status !== "PENDING"}
                  placeholder="Add remarks or comments..."
                  style={{
                    borderRadius: "7px",
                    fontSize: "0.875rem",
                    background:
                      reviewModal.record.status !== "PENDING"
                        ? "#f9fafb"
                        : "#fff",
                  }}
                />
              </div>

              {/* Attachments */}
              {(() => {
                const urls =
                  Array.isArray(reviewModal.record.attachmentUrls) && reviewModal.record.attachmentUrls.length > 0
                    ? reviewModal.record.attachmentUrls
                    : (reviewModal.record.attachmentUrl || reviewModal.record.attachment)
                    ? [(reviewModal.record.attachmentUrl || reviewModal.record.attachment)]
                    : [];
                const fileNames =
                  Array.isArray(reviewModal.record.attachmentFileNames) && reviewModal.record.attachmentFileNames.length > 0
                    ? reviewModal.record.attachmentFileNames
                    : reviewModal.record.attachmentFileName
                    ? [reviewModal.record.attachmentFileName]
                    : [];
                if (urls.length === 0) return null;
                return (
                  <div style={{ marginBottom: "20px" }}>
                    <label
                      style={{
                        display: "block",
                        fontWeight: 600,
                        fontSize: "0.85rem",
                        color: "#374151",
                        marginBottom: "6px",
                      }}
                    >
                      Attachments ({urls.length})
                    </label>
                    <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
                      {urls.map((attUrl, aIdx) => {
                        const trimmed = (attUrl || "").trim();
                        const fileName = fileNames[aIdx] || (trimmed.startsWith("http") ? `Attachment ${aIdx + 1}` : trimmed);
                        const isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://");
                        return (
                          <div
                            key={`${trimmed}-${aIdx}`}
                            onClick={() => { if (isUrl) window.open(trimmed, "_blank", "noopener,noreferrer"); }}
                            title={isUrl ? `Click to view / download: ${fileName}` : trimmed}
                            style={{
                              display: "flex",
                              alignItems: "center",
                              gap: "10px",
                              padding: "8px 12px",
                              background: "#f0fdf4",
                              border: "1px solid #bbf7d0",
                              borderRadius: "8px",
                              cursor: isUrl ? "pointer" : "default",
                            }}
                          >
                            <AntPaperClip style={{ color: "#059669", fontSize: "15px" }} />
                            <span
                              style={{
                                fontSize: "0.85rem",
                                color: isUrl ? "#047857" : "#374151",
                                fontWeight: 600,
                                flex: 1,
                                overflow: "hidden",
                                textOverflow: "ellipsis",
                                whiteSpace: "nowrap",
                                textDecoration: isUrl ? "underline" : "none",
                              }}
                            >
                              {fileName}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })()}

              {/* Action buttons — only for PENDING */}
              {reviewModal.record.status === "PENDING" && (
                <div
                  style={{
                    display: "flex",
                    justifyContent: "flex-end",
                    gap: "10px",
                    paddingTop: "12px",
                    borderTop: "1px solid #f3f4f6",
                  }}
                >
                  <button
                    id="reject-btn"
                    disabled={isApproving || isRejecting}
                    onClick={() => {
                      closeReview();
                      openReject(reviewModal.record);
                    }}
                    style={{
                      padding: "9px 22px",
                      background: "#fff",
                      border: "1px solid #fca5a5",
                      borderRadius: "7px",
                      color: "#dc2626",
                      fontWeight: 600,
                      fontSize: "0.875rem",
                      cursor: isApproving || isRejecting ? "not-allowed" : "pointer",
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "6px",
                      opacity: isApproving || isRejecting ? 0.6 : 1,
                      transition: "background 0.15s",
                    }}
                    onMouseEnter={(e) =>
                      !isApproving &&
                      !isRejecting &&
                      (e.currentTarget.style.background = "#fef2f2")
                    }
                    onMouseLeave={(e) =>
                      (e.currentTarget.style.background = "#fff")
                    }
                  >
                    <PiXCircleDuotone size={16} /> Reject
                  </button>
                  <button
                    id="approve-btn"
                    disabled={isApproving || isRejecting}
                    onClick={handleApprove}
                    style={{
                      padding: "9px 22px",
                      background: "#059669",
                      border: "none",
                      borderRadius: "7px",
                      color: "#fff",
                      fontWeight: 600,
                      fontSize: "0.875rem",
                      cursor: isApproving || isRejecting ? "not-allowed" : "pointer",
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "6px",
                      opacity: isApproving || isRejecting ? 0.6 : 1,
                      transition: "background 0.15s",
                    }}
                    onMouseEnter={(e) =>
                      !isApproving &&
                      !isRejecting &&
                      (e.currentTarget.style.background = "#047857")
                    }
                    onMouseLeave={(e) =>
                      (e.currentTarget.style.background = "#059669")
                    }
                  >
                    <PiCheckCircleDuotone size={16} />
                    {isApproving ? "Approving..." : "Approve"}
                  </button>
                </div>
              )}

              {/* Read-only footer for non-pending */}
              {reviewModal.record.status !== "PENDING" && (
                <div
                  style={{
                    textAlign: "right",
                    paddingTop: "12px",
                    borderTop: "1px solid #f3f4f6",
                  }}
                >
                  <button
                    onClick={closeReview}
                    style={{
                      padding: "9px 24px",
                      background: "#f3f4f6",
                      border: "1px solid #d1d5db",
                      borderRadius: "7px",
                      color: "#374151",
                      fontWeight: 600,
                      fontSize: "0.875rem",
                      cursor: "pointer",
                    }}
                  >
                    Close
                  </button>
                </div>
              )}
            </div>
          )
        )}
      </Modal>

      {/* ── Reject Confirmation Modal ──────────────────────────────────────── */}
      <Modal
        open={rejectModal.open}
        onCancel={closeReject}
        footer={null}
        width={460}
        centered
        destroyOnClose
        title={
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <span
              style={{
                background: "linear-gradient(135deg, #fef2f2, #fee2e2)",
                borderRadius: "8px",
                padding: "6px 8px",
                display: "inline-flex",
              }}
            >
              <PiXCircleDuotone size={18} style={{ color: "#dc2626" }} />
            </span>
            <span
              style={{
                fontWeight: 700,
                fontSize: "1rem",
                color: "#111827",
              }}
            >
              Reject Reimbursement
            </span>
          </div>
        }
      >
        {rejectModal.record && (
          <div>
            <p
              style={{
                color: "#6b7280",
                fontSize: "0.9rem",
                marginBottom: "16px",
              }}
            >
              Are you sure you want to reject the reimbursement request from{" "}
              <strong style={{ color: "#111827" }}>
                {rejectModal.record.employeeName}
                {rejectModal.record.employeeNumber
                  ? ` (${rejectModal.record.employeeNumber})`
                  : ""}
              </strong>{" "}
              for{" "}
              <strong style={{ color: "#111827" }}>
                {formatCurrency(rejectModal.record.requestedAmount)}
              </strong>
              ?
            </p>
            <div style={{ marginBottom: "20px" }}>
              <label
                style={{
                  display: "block",
                  fontWeight: 600,
                  fontSize: "0.85rem",
                  color: "#374151",
                  marginBottom: "6px",
                }}
              >
                Reason for Rejection <span style={{ color: "#dc2626" }}>*</span>
              </label>
              <TextArea
                id="reject-remarks-input"
                rows={3}
                value={rejectRemarks}
                onChange={(e) => {
                  setRejectRemarks(e.target.value);
                  setRejectError("");
                }}
                placeholder="Enter reason for rejection..."
                style={{
                  borderRadius: "7px",
                  fontSize: "0.875rem",
                  border: `1px solid ${rejectError ? "#fca5a5" : "#d1d5db"}`,
                }}
              />
              {rejectError && (
                <div
                  style={{
                    color: "#dc2626",
                    fontSize: "0.8rem",
                    marginTop: "5px",
                  }}
                >
                  {rejectError}
                </div>
              )}
            </div>
            <div
              style={{
                display: "flex",
                justifyContent: "flex-end",
                gap: "10px",
              }}
            >
              <button
                id="cancel-reject-btn"
                onClick={closeReject}
                style={{
                  padding: "9px 20px",
                  background: "#fff",
                  border: "1px solid #d1d5db",
                  borderRadius: "7px",
                  color: "#374151",
                  fontWeight: 600,
                  fontSize: "0.875rem",
                  cursor: "pointer",
                }}
              >
                Cancel
              </button>
              <button
                id="confirm-reject-btn"
                disabled={isRejecting}
                onClick={handleReject}
                style={{
                  padding: "9px 22px",
                  background: "#dc2626",
                  border: "none",
                  borderRadius: "7px",
                  color: "#fff",
                  fontWeight: 600,
                  fontSize: "0.875rem",
                  cursor: isRejecting ? "not-allowed" : "pointer",
                  opacity: isRejecting ? 0.6 : 1,
                  transition: "background 0.15s",
                }}
                onMouseEnter={(e) =>
                  !isRejecting &&
                  (e.currentTarget.style.background = "#b91c1c")
                }
                onMouseLeave={(e) =>
                  (e.currentTarget.style.background = "#dc2626")
                }
              >
                {isRejecting ? "Rejecting..." : "Reject"}
              </button>
            </div>
          </div>
        )}
      </Modal>
    </>
  );
}

/** Small helper for the detail modal info rows */
function InfoRow({ label, value, span = false, valueStyle = {} }) {
  return (
    <div style={span ? { gridColumn: "1 / -1" } : {}}>
      <div
        style={{
          fontSize: "0.75rem",
          color: "#9ca3af",
          fontWeight: 600,
          marginBottom: "3px",
        }}
      >
        {label.toUpperCase()}
      </div>
      <div
        style={{
          fontSize: "0.875rem",
          color: "#374151",
          fontWeight: 500,
          ...valueStyle,
        }}
      >
        {value || "—"}
      </div>
    </div>
  );
}

/** Reimbursement_month custom dropdown helper */
function ReimbursementMonthDropdown({ value, onChange, disabled }) {
  const [open, setOpen] = useState(false);
  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonthIdx = now.getMonth(); // 0 = Jan, 7 = Aug (for Aug)

  const MONTHS = [
    { label: "January", value: "01" },
    { label: "February", value: "02" },
    { label: "March", value: "03" },
    { label: "April", value: "04" },
    { label: "May", value: "05" },
    { label: "June", value: "06" },
    { label: "July", value: "07" },
    { label: "August", value: "08" },
    { label: "September", value: "09" },
    { label: "October", value: "10" },
    { label: "November", value: "11" },
    { label: "December", value: "12" },
  ];

  const selectedMonthObj = MONTHS.find((m) => `${currentYear}-${m.value}` === value);
  const displayLabel = selectedMonthObj
    ? `${selectedMonthObj.label} ${currentYear} (${value})`
    : value || "Select Month";

  return (
    <div style={{ marginBottom: "14px", position: "relative" }}>
      <label
        style={{
          display: "block",
          fontWeight: 600,
          fontSize: "0.85rem",
          color: "#374151",
          marginBottom: "6px",
        }}
      >
        Billing Month
      </label>
      <div
        onClick={() => !disabled && setOpen((prev) => !prev)}
        style={{
          width: "100%",
          padding: "9px 13px",
          border: "1px solid #d1d5db",
          borderRadius: "7px",
          fontSize: "0.9rem",
          color: "#111827",
          background: disabled ? "#f9fafb" : "#fff",
          cursor: disabled ? "default" : "pointer",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          userSelect: "none",
        }}
      >
        <span>{displayLabel}</span>
        <span style={{ fontSize: "0.75rem", color: "#6b7280" }}>
          {open ? "▲" : "▼"}
        </span>
      </div>

      {open && !disabled && (
        <>
          <div
            style={{
              position: "fixed",
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              zIndex: 998,
            }}
            onClick={() => setOpen(false)}
          />
          <div
            style={{
              position: "absolute",
              top: "100%",
              left: 0,
              right: 0,
              zIndex: 999,
              background: "#fff",
              border: "1px solid #e5e7eb",
              borderRadius: "7px",
              boxShadow: "0 10px 15px -3px rgba(0,0,0,0.1)",
              maxHeight: "220px",
              overflowY: "auto",
              marginTop: "4px",
            }}
          >
            {MONTHS.map((m, idx) => {
              const isPast = idx < currentMonthIdx;
              const formattedVal = `${currentYear}-${m.value}`;
              const isSelected = formattedVal === value;

              return (
                <div
                  key={m.value}
                  onClick={() => {
                    if (!isPast) {
                      onChange(formattedVal);
                      setOpen(false);
                    }
                  }}
                  style={{
                    padding: "8px 14px",
                    fontSize: "0.85rem",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    cursor: isPast ? "not-allowed" : "pointer",
                    color: isPast ? "#9ca3af" : isSelected ? "#059669" : "#374151",
                    background: isSelected
                      ? "#ecfdf5"
                      : isPast
                      ? "#f9fafb"
                      : "transparent",
                    fontWeight: isSelected ? 600 : 400,
                  }}
                  onMouseEnter={(e) => {
                    if (!isPast && !isSelected) {
                      e.currentTarget.style.background = "#f3f4f6";
                    }
                  }}
                  onMouseLeave={(e) => {
                    if (!isPast && !isSelected) {
                      e.currentTarget.style.background = "transparent";
                    }
                  }}
                >
                  <span>{`${m.label} ${currentYear}`}</span>
                  {isPast ? (
                    <span style={{ fontSize: "0.72rem", color: "#9ca3af" }}>
                      Past
                    </span>
                  ) : isSelected ? (
                    <span style={{ fontSize: "0.75rem", color: "#059669" }}>
                      ✓
                    </span>
                  ) : null}
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
