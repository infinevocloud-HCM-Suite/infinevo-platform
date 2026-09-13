import React from "react";

/**
 * StatusBadge – renders a styled pill badge based on status string.
 * Supports: PENDING, APPROVED, REJECTED (request status)
 *           PAID, UNPAID (payment status)
 */
export default function StatusBadge({ status }) {
  if (!status) return <span className="badge badge-light">—</span>;

  const normalized = status.toUpperCase();

  const styleMap = {
    PENDING: {
      background: "#fff8e1",
      color: "#f59e0b",
      border: "1px solid #fcd34d",
    },
    APPROVED: {
      background: "#ecfdf5",
      color: "#059669",
      border: "1px solid #6ee7b7",
    },
    REJECTED: {
      background: "#fef2f2",
      color: "#dc2626",
      border: "1px solid #fca5a5",
    },
    PAID: {
      background: "#ecfdf5",
      color: "#059669",
      border: "1px solid #6ee7b7",
    },
    UNPAID: {
      background: "#fff7ed",
      color: "#ea580c",
      border: "1px solid #fdba74",
    },
  };

  const style = styleMap[normalized] || {
    background: "#f3f4f6",
    color: "#6b7280",
    border: "1px solid #d1d5db",
  };

  return (
    <span
      style={{
        ...style,
        display: "inline-block",
        padding: "3px 10px",
        borderRadius: "20px",
        fontSize: "0.75rem",
        fontWeight: 600,
        letterSpacing: "0.4px",
        whiteSpace: "nowrap",
      }}
    >
      {status.charAt(0).toUpperCase() + status.slice(1).toLowerCase()}
    </span>
  );
}
