import React, { useState } from "react";
import { Modal } from "antd";
import { PiEyeDuotone, PiReceiptDuotone, PiFilePdfDuotone, PiImageDuotone } from "react-icons/pi";
import StatusBadge from "../../../../shared/components/reimbursement/StatusBadge";

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

const PAGE_SIZE = 5;

export default function ReimbursementTable({ data = [], loading = false, onApply }) {
  const [search, setSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [viewRecord, setViewRecord] = useState(null);

  // Filter by search (matches type, description, status, month, payment)
  const filtered = data.filter((row) => {
    const q = search.toLowerCase();
    const type = row.reimbursementType || row.type || "";
    return (
      !q ||
      type.toLowerCase().includes(q) ||
      row.description?.toLowerCase().includes(q) ||
      row.status?.toLowerCase().includes(q) ||
      row.paymentStatus?.toLowerCase().includes(q) ||
      row.reimbursementMonth?.toLowerCase().includes(q) ||
      row.requestDate?.toLowerCase().includes(q) ||
      row.billDate?.toLowerCase().includes(q)
    );
  });

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE) || 1;
  const paginated = filtered.slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE
  );

  const handleSearchChange = (e) => {
    setSearch(e.target.value);
    setCurrentPage(1);
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

  // Loading skeleton rows
  if (loading) {
    return (
      <div style={{ padding: "30px", textAlign: "center", color: "#9ca3af" }}>
        <div className="spinner-border spinner-border-sm text-success me-2" role="status" />
        <span>Loading reimbursement history...</span>
      </div>
    );
  }

  return (
    <div>
      {/* Search bar */}
      <div
        style={{
          display: "flex",
          justifyContent: "flex-end",
          marginBottom: "14px",
        }}
      >
        <input
          type="text"
          placeholder="Search by type, description, status, billing month..."
          value={search}
          onChange={handleSearchChange}
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

      {/* Table */}
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
            minWidth: "1000px",
          }}
        >
          <thead>
            <tr>
              <th style={thStyle}>#</th>
              <th style={thStyle}>Request Date</th>
              <th style={thStyle}>Type</th>
              <th style={thStyle}>Description</th>
              <th style={{ ...thStyle, textAlign: "right" }}>Requested (₹)</th>
              <th style={{ ...thStyle, textAlign: "right" }}>Approved (₹)</th>
              <th style={thStyle}>Billing Month</th>
              <th style={thStyle}>Status</th>
              <th style={thStyle}>Payment</th>
              <th style={thStyle}>Remarks</th>
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
                    padding: "48px 20px",
                    color: "#9ca3af",
                    fontSize: "0.9rem",
                  }}
                >
                  {search ? (
                    <>
                      <div style={{ marginBottom: "8px", fontSize: "1.5rem" }}>
                        🔍
                      </div>
                      No reimbursements match your search.
                    </>
                  ) : (
                    <>
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
                        No Reimbursements Found
                      </div>
                      <div
                        style={{
                          fontSize: "0.875rem",
                          color: "#6b7280",
                          marginBottom: "18px",
                        }}
                      >
                        You haven't submitted any reimbursement requests yet.
                      </div>
                      {onApply && (
                        <button
                          className="btn btn-sm btn-success"
                          onClick={onApply}
                          style={{ padding: "8px 20px", fontWeight: 600 }}
                        >
                          + Apply Reimbursement
                        </button>
                      )}
                    </>
                  )}
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
                  <td style={tdStyle}>
                    <span style={{ color: "#9ca3af", fontSize: "0.8rem" }}>
                      {(currentPage - 1) * PAGE_SIZE + idx + 1}
                    </span>
                  </td>
                  <td style={tdStyle}>{row.requestDate}</td>
                  <td style={tdStyle}>
                    <span
                      style={{
                        fontWeight: 600,
                        color: "#111827",
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
                  <td style={{ ...tdStyle, textAlign: "right", fontWeight: 600 }}>
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
                  <td style={tdStyle}>
                    <StatusBadge status={row.paymentStatus} />
                  </td>
                  <td
                    style={{
                      ...tdStyle,
                      maxWidth: "140px",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                      color: "#6b7280",
                    }}
                    title={row.remarks}
                  >
                    {row.remarks || "—"}
                  </td>
                  <td style={{ ...tdStyle, textAlign: "center" }}>
                    <button
                      title="View details"
                      style={{
                        background: "#f0fdf4",
                        border: "1px solid #bbf7d0",
                        borderRadius: "6px",
                        padding: "5px 8px",
                        cursor: "pointer",
                        color: "#059669",
                        display: "inline-flex",
                        alignItems: "center",
                        transition: "background 0.15s",
                      }}
                      onMouseEnter={(e) =>
                        (e.currentTarget.style.background = "#dcfce7")
                      }
                      onMouseLeave={(e) =>
                        (e.currentTarget.style.background = "#f0fdf4")
                      }
                      onClick={() => setViewRecord(row)}
                    >
                      <PiEyeDuotone size={16} />
                    </button>
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
            Showing {Math.min((currentPage - 1) * PAGE_SIZE + 1, filtered.length)}–
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

      {/* View Details Modal for Employee */}
      {viewRecord && (
        <Modal
          open={Boolean(viewRecord)}
          onCancel={() => setViewRecord(null)}
          footer={null}
          title={
            <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
              <span
                style={{
                  background: "#ecfdf5",
                  borderRadius: "8px",
                  padding: "6px 8px",
                  display: "flex",
                  alignItems: "center",
                }}
              >
                <PiReceiptDuotone size={20} style={{ color: "#059669" }} />
              </span>
              <div>
                <div style={{ fontWeight: 700, fontSize: "1.05rem", color: "#111827" }}>
                  Reimbursement Details
                </div>
                <div style={{ fontSize: "0.78rem", color: "#6b7280", fontWeight: 400 }}>
                  Request #{viewRecord.id}
                </div>
              </div>
            </div>
          }
          width={580}
        >
          <div style={{ paddingTop: "12px" }}>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "1fr 1fr",
                gap: "12px 20px",
                background: "#f9fafb",
                borderRadius: "8px",
                padding: "16px",
                marginBottom: "16px",
              }}
            >
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  TYPE
                </span>
                <span style={{ fontWeight: 600, color: "#111827", fontSize: "0.9rem" }}>
                  {formatReimbursementType(viewRecord.reimbursementType || viewRecord.type, viewRecord.description)}
                </span>
              </div>
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  STATUS
                </span>
                <StatusBadge status={viewRecord.status} />
              </div>
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  REQUEST DATE
                </span>
                <span style={{ color: "#374151", fontSize: "0.875rem" }}>
                  {viewRecord.requestDate || "—"}
                </span>
              </div>
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  BILL DATE
                </span>
                <span style={{ color: "#374151", fontSize: "0.875rem" }}>
                  {viewRecord.billDate || "—"}
                </span>
              </div>
              {viewRecord.employeeNumber && (
                <div>
                  <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                    EMPLOYEE NUMBER
                  </span>
                  <span style={{ color: "#374151", fontSize: "0.875rem", fontWeight: 600 }}>
                    {viewRecord.employeeNumber}
                  </span>
                </div>
              )}
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  REQUESTED AMOUNT
                </span>
                <span style={{ fontWeight: 700, color: "#111827", fontSize: "1rem" }}>
                  {formatCurrency(viewRecord.requestedAmount)}
                </span>
              </div>
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  APPROVED AMOUNT
                </span>
                <span style={{ fontWeight: 700, color: "#059669", fontSize: "1rem" }}>
                  {formatCurrency(viewRecord.approvedAmount)}
                </span>
              </div>
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  BILLING MONTH
                </span>
                <span
                  style={{
                    color: viewRecord.status === "APPROVED" && viewRecord.reimbursementMonth ? "#374151" : "#9ca3af",
                    fontSize: "0.875rem",
                    fontWeight: 500,
                  }}
                >
                  {viewRecord.status === "APPROVED" ? (viewRecord.reimbursementMonth || "—") : "—"}
                </span>
              </div>
              <div>
                <span style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, display: "block" }}>
                  PAYMENT STATUS
                </span>
                <StatusBadge status={viewRecord.paymentStatus} />
              </div>
            </div>

            {/* Description */}
            <div style={{ marginBottom: "14px" }}>
              <div style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, marginBottom: "4px" }}>
                DESCRIPTION
              </div>
              <div
                style={{
                  background: "#fff",
                  border: "1px solid #e5e7eb",
                  borderRadius: "6px",
                  padding: "10px 12px",
                  fontSize: "0.875rem",
                  color: "#374151",
                  minHeight: "44px",
                }}
              >
                {formatDescription(viewRecord.description) || "No description provided."}
              </div>
            </div>

            {/* Remarks */}
            {viewRecord.remarks && (
              <div style={{ marginBottom: "14px" }}>
                <div style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, marginBottom: "4px" }}>
                  REMARKS / NOTES
                </div>
                <div
                  style={{
                    background: "#fefce8",
                    border: "1px solid #fef08a",
                    borderRadius: "6px",
                    padding: "10px 12px",
                    fontSize: "0.875rem",
                    color: "#854d0e",
                  }}
                >
                  {viewRecord.remarks}
                </div>
              </div>
            )}

            {/* Attachments */}
            {(() => {
              // Support new multi-attachment (attachmentUrls list) and legacy single-URL
              const urls =
                Array.isArray(viewRecord.attachmentUrls) && viewRecord.attachmentUrls.length > 0
                  ? viewRecord.attachmentUrls
                  : viewRecord.attachmentUrl
                  ? [viewRecord.attachmentUrl]
                  : [];
              const fileNames =
                Array.isArray(viewRecord.attachmentFileNames) && viewRecord.attachmentFileNames.length > 0
                  ? viewRecord.attachmentFileNames
                  : viewRecord.attachmentFileName
                  ? [viewRecord.attachmentFileName]
                  : [];
              if (urls.length === 0) return null;
              return (
                <div style={{ marginBottom: "14px" }}>
                  <div style={{ fontSize: "0.75rem", color: "#6b7280", fontWeight: 600, marginBottom: "6px" }}>
                    ATTACHMENTS ({urls.length})
                  </div>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
                    {urls.map((url, i) => {
                      const trimmed = (url || "").trim();
                      const fileName = fileNames[i] || (trimmed.startsWith("http") ? `Attachment ${i + 1}` : trimmed);
                      const isPdf = trimmed.toLowerCase().endsWith(".pdf") || fileName.toLowerCase().endsWith(".pdf");
                      const isUrl = trimmed.startsWith("http://") || trimmed.startsWith("https://");
                      return (
                        <div
                          key={i}
                          onClick={() => { if (isUrl) window.open(trimmed, "_blank", "noopener,noreferrer"); }}
                          title={isUrl ? `Click to view / download: ${fileName}` : trimmed}
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: "6px",
                            padding: "6px 12px",
                            background: isUrl ? "#ecfdf5" : "#f3f4f6",
                            border: `1px solid ${isUrl ? "#a7f3d0" : "#e5e7eb"}`,
                            borderRadius: "6px",
                            fontSize: "0.82rem",
                            color: isUrl ? "#047857" : "#374151",
                            cursor: isUrl ? "pointer" : "default",
                            fontWeight: isUrl ? 600 : 500,
                          }}
                        >
                          {isPdf ? (
                            <PiFilePdfDuotone size={16} style={{ color: "#ef4444" }} />
                          ) : (
                            <PiImageDuotone size={16} style={{ color: "#059669" }} />
                          )}
                          <span
                            style={{
                              maxWidth: "220px",
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

            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "20px" }}>
              <button
                className="btn btn-sm btn-secondary"
                onClick={() => setViewRecord(null)}
                style={{ padding: "7px 18px", fontWeight: 600 }}
              >
                Close
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
