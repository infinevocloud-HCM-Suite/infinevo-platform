import React, { useState } from "react";
import { Modal } from "antd";
import { PiReceiptDuotone } from "react-icons/pi";
import { successMsg, errorMsg } from "../../../../shared/helpers/msgHelper";
import { createEmployeeReimbursement } from "../../../../shared/services/reimbursementService";
import validateReimbursementForm from "./reimbursementValidation";
import UploadField from "../../../../shared/components/reimbursement/UploadField";

const REIMBURSEMENT_TYPES = [
  "Medical",
  "Travel",
  "Food",
  "Internet",
  "Fuel",
  "Other",
];

const initialFormData = {
  reimbursementType: "",
  isOtherType: false,
  customType: "",
  requestedAmount: "",
  billDate: "",
  description: "",
  attachments: [],
};

/**
 * Modal form to submit a new employee reimbursement request.
 *
 * Props:
 *  - visible       : boolean — controls modal open/close
 *  - onClose       : () => void — called when cancel is clicked (no submission)
 *  - onSuccess     : () => void — called after successful POST (triggers table refresh)
 *  - organizationId: string — from localStorage, passed in by ReimbursementPage
 *
 * Security:
 *  - employeeId is NOT sent in the request body (backend reads it from JWT)
 *  - organizationId is sent as a request header (NOT as a body field)
 *  - status, approvedAmount, paymentStatus, etc. are backend-controlled
 */
export default function ApplyReimbursementModal({
  visible,
  onClose,
  onSuccess,
  organizationId,
}) {
  const [formData, setFormData] = useState(initialFormData);
  const [errors, setErrors] = useState({});
  const [submitted, setSubmitted] = useState(false);
  const [submitting, setSubmitting] = useState(false); // prevents double-submit

  const handleChange = (field, value) => {
    const updated = { ...formData, [field]: value };
    setFormData(updated);
    // Live re-validate if user already clicked Submit once
    if (submitted) {
      setErrors(validateReimbursementForm(updated));
    }
  };

  const handleReset = () => {
    setFormData(initialFormData);
    setErrors({});
    setSubmitted(false);
    setSubmitting(false);
  };

  const handleClose = () => {
    handleReset();
    onClose();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitted(true);

    // ── Frontend Validation ────────────────────────────────────────────────
    const validationErrors = validateReimbursementForm(formData);
    setErrors(validationErrors);

    if (Object.keys(validationErrors).length > 0) {
      return; // stop here — let UI show field errors
    }

    // ── Build FormData with MultipartFile ────────────────────────────────
    const attachmentFiles = Array.isArray(formData.attachments)
      ? formData.attachments
      : formData.attachments
      ? [formData.attachments]
      : [];

    let finalDescription = formData.description.trim();
    let typeToSend = "OTHER";

    if (formData.isOtherType) {
      typeToSend = "OTHER";
      if (formData.customType?.trim()) {
        finalDescription = `[Type: ${formData.customType.trim()}] ${finalDescription}`;
      }
    } else {
      typeToSend = formData.reimbursementType.toUpperCase();
    }

    const formDataToSend = new FormData();
    formDataToSend.append("reimbursementType", typeToSend);
    formDataToSend.append("type", typeToSend);
    formDataToSend.append("requestedAmount", String(Number(formData.requestedAmount)));
    formDataToSend.append("billDate", formData.billDate);
    formDataToSend.append("description", finalDescription);

    // Append ALL selected files under the "files" key so the backend receives every attachment
    if (attachmentFiles.length > 0) {
      attachmentFiles.forEach((f) => {
        if (f instanceof File) {
          formDataToSend.append("files", f);
        }
      });
    }

    // ── POST /api/employee/reimbursements ──────────────────────────────────
    setSubmitting(true);
    try {
      const resData = await createEmployeeReimbursement(formDataToSend);

      if (resData && (resData.status === 201 || resData.status === 200 || resData.data)) {
        // Success: show message, reset form, close modal, trigger table refresh
        await successMsg(
          "Submitted!",
          "Your reimbursement request has been submitted successfully.",
          true
        );
        handleReset();
        if (typeof onSuccess === "function") {
          onSuccess(); // → triggers fetchReimbursements() in the page
        }
      } else {
        throw new Error(resData?.message || "Unexpected response from server.");
      }
    } catch (err) {
      console.error("Error submitting reimbursement:", err);
      if (err?.response?.status === 403) {
        errorMsg(
          "Access Denied",
          "You are not authorized to submit a reimbursement request.",
          true
        );
      } else if (err?.response?.status === 400) {
        const errorList = err.response?.data?.errors;
        const msg = Array.isArray(errorList) && errorList.length > 0
          ? errorList.map((e) => e.message).join("; ")
          : err.response?.data?.message || "Please check the reimbursement details.";
        errorMsg("Invalid Request", msg, true);
      } else if (err?.response?.status === 401) {
        errorMsg("Session Expired", "Please log in again to continue.", true);
      } else if (err?.response?.status === 500) {
        errorMsg("Server Error", "Unable to submit reimbursement. Please try again.", true);
      } else {
        const message =
          err?.response?.data?.message ||
          err?.message ||
          "Failed to submit reimbursement request. Please try again.";
        errorMsg("Submission Failed", message, true);
      }
    } finally {
      setSubmitting(false);
    }
  };

  // Today's date in YYYY-MM-DD for max attribute (prevents future date in browser)
  const todayStr = new Date().toISOString().split("T")[0];

  const inputStyle = (hasError) => ({
    width: "100%",
    border: `1px solid ${hasError ? "#dc2626" : "#d1d5db"}`,
    borderRadius: "6px",
    padding: "9px 12px",
    fontSize: "0.875rem",
    color: "#111827",
    outline: "none",
    background: "#fff",
    transition: "border-color 0.2s",
    boxSizing: "border-box",
  });

  const labelStyle = {
    display: "block",
    fontSize: "0.8125rem",
    fontWeight: 600,
    color: "#374151",
    marginBottom: "5px",
  };

  const errorStyle = {
    color: "#dc2626",
    fontSize: "0.76rem",
    marginTop: "3px",
  };

  const requiredStar = (
    <span style={{ color: "#dc2626", marginLeft: "2px" }}>*</span>
  );

  return (
    <Modal
      open={visible}
      onCancel={handleClose}
      title={
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: "10px",
            padding: "4px 0",
          }}
        >
          <span
            style={{
              background: "linear-gradient(135deg, #ecfdf5, #d1fae5)",
              borderRadius: "8px",
              padding: "6px 8px",
              display: "flex",
              alignItems: "center",
            }}
          >
            <PiReceiptDuotone size={20} style={{ color: "#059669" }} />
          </span>
          <span
            style={{ fontWeight: 700, fontSize: "1rem", color: "#111827" }}
          >
            Apply Reimbursement
          </span>
        </div>
      }
      footer={null}
      width={680}
      destroyOnClose
      closable={!submitting}
      maskClosable={!submitting}
      styles={{
        header: {
          borderBottom: "1px solid #f3f4f6",
          paddingBottom: "12px",
          marginBottom: "0",
        },
        body: { padding: "20px 24px 0" },
      }}
    >
      <form onSubmit={handleSubmit} noValidate>
        {/* Row 1: Type + Amount */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "16px",
            marginBottom: "16px",
          }}
        >
          {/* Reimbursement Type */}
          <div>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <label style={labelStyle}>
                Reimbursement Type {requiredStar}
              </label>
              {formData.isOtherType && (
                <button
                  type="button"
                  onClick={() => {
                    const updated = {
                      ...formData,
                      isOtherType: false,
                      customType: "",
                      reimbursementType: "",
                    };
                    setFormData(updated);
                    if (submitted) {
                      setErrors(validateReimbursementForm(updated));
                    }
                  }}
                  disabled={submitting}
                  style={{
                    background: "none",
                    border: "none",
                    color: "#059669",
                    fontSize: "0.75rem",
                    fontWeight: 600,
                    cursor: submitting ? "not-allowed" : "pointer",
                    padding: "0 0 4px 0",
                    textDecoration: "underline",
                  }}
                >
                  Choose from list
                </button>
              )}
            </div>

            {formData.isOtherType ? (
              <div style={{ position: "relative" }}>
                <input
                  type="text"
                  placeholder="Enter reimbursement type..."
                  value={formData.customType}
                  onChange={(e) => handleChange("customType", e.target.value)}
                  disabled={submitting}
                  autoFocus
                  maxLength={100}
                  style={{
                    ...inputStyle(!!errors.reimbursementType),
                    paddingRight: "28px",
                    cursor: submitting ? "not-allowed" : "text",
                  }}
                />
                <button
                  type="button"
                  onClick={() => {
                    const updated = {
                      ...formData,
                      isOtherType: false,
                      customType: "",
                      reimbursementType: "",
                    };
                    setFormData(updated);
                    if (submitted) {
                      setErrors(validateReimbursementForm(updated));
                    }
                  }}
                  title="Switch back to dropdown list"
                  disabled={submitting}
                  style={{
                    position: "absolute",
                    right: "8px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    background: "none",
                    border: "none",
                    color: "#9ca3af",
                    fontSize: "0.85rem",
                    cursor: submitting ? "not-allowed" : "pointer",
                    padding: "2px 4px",
                    lineHeight: 1,
                  }}
                >
                  ✕
                </button>
              </div>
            ) : (
              <select
                value={formData.reimbursementType}
                onChange={(e) => {
                  if (e.target.value === "Other") {
                    const updated = {
                      ...formData,
                      reimbursementType: "Other",
                      isOtherType: true,
                      customType: "",
                    };
                    setFormData(updated);
                    if (submitted) {
                      setErrors(validateReimbursementForm(updated));
                    }
                  } else {
                    handleChange("reimbursementType", e.target.value);
                  }
                }}
                disabled={submitting}
                style={{
                  ...inputStyle(!!errors.reimbursementType),
                  cursor: submitting ? "not-allowed" : "pointer",
                  appearance: "auto",
                }}
              >
                <option value="">Select type</option>
                {REIMBURSEMENT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
            )}

            {errors.reimbursementType && (
              <div style={errorStyle}>{errors.reimbursementType}</div>
            )}
          </div>

          {/* Requested Amount */}
          <div>
            <label style={labelStyle}>
              Requested Amount (₹) {requiredStar}
            </label>
            <input
              type="number"
              min="1"
              placeholder="Enter amount"
              value={formData.requestedAmount}
              onChange={(e) =>
                handleChange("requestedAmount", e.target.value)
              }
              disabled={submitting}
              style={{
                ...inputStyle(!!errors.requestedAmount),
                cursor: submitting ? "not-allowed" : "text",
              }}
            />
            {errors.requestedAmount && (
              <div style={errorStyle}>{errors.requestedAmount}</div>
            )}
          </div>
        </div>

        {/* Row 2: Bill Date */}
        <div style={{ marginBottom: "16px" }}>
          <label style={labelStyle}>
            Bill Date {requiredStar}
          </label>
          <input
            type="date"
            max={todayStr}
            value={formData.billDate}
            onChange={(e) => handleChange("billDate", e.target.value)}
            disabled={submitting}
            style={{
              ...inputStyle(!!errors.billDate),
              maxWidth: "320px",
              cursor: submitting ? "not-allowed" : "pointer",
            }}
          />
          {errors.billDate && (
            <div style={errorStyle}>{errors.billDate}</div>
          )}
        </div>

        {/* Row 3: Description */}
        <div style={{ marginBottom: "16px" }}>
          <label style={labelStyle}>
            Description {requiredStar}
          </label>
          <textarea
            rows={3}
            placeholder="Enter reimbursement details..."
            value={formData.description}
            onChange={(e) => handleChange("description", e.target.value)}
            disabled={submitting}
            style={{
              ...inputStyle(!!errors.description),
              resize: "vertical",
              lineHeight: "1.5",
              fontFamily: "inherit",
              cursor: submitting ? "not-allowed" : "text",
            }}
          />
          {errors.description && (
            <div style={errorStyle}>{errors.description}</div>
          )}
        </div>

        {/* Row 4: Attachments */}
        <div style={{ marginBottom: "24px" }}>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              marginBottom: "5px",
            }}
          >
            <label style={{ ...labelStyle, marginBottom: 0 }}>Attachments</label>
            {formData.attachments && formData.attachments.length > 0 && (
              <span
                style={{
                  fontSize: "0.75rem",
                  color: "#059669",
                  fontWeight: 600,
                  background: "#ecfdf5",
                  padding: "2px 8px",
                  borderRadius: "10px",
                }}
              >
                {formData.attachments.length}{" "}
                {formData.attachments.length === 1 ? "file" : "files"}
              </span>
            )}
          </div>
          <UploadField
            value={formData.attachments}
            onChange={(files) => handleChange("attachments", files)}
            error={errors.attachment || errors.attachments}
            disabled={submitting}
          />
          <div
            style={{ fontSize: "0.75rem", color: "#9ca3af", marginTop: "4px" }}
          >
            Allowed formats: PDF, JPG, PNG. Max size: 5 MB per file. You can select 1 or multiple attachments.
          </div>
        </div>

        {/* Footer Buttons */}
        <div
          style={{
            display: "flex",
            justifyContent: "flex-end",
            gap: "10px",
            padding: "14px 0 20px",
            borderTop: "1px solid #f3f4f6",
          }}
        >
          <button
            type="button"
            onClick={handleClose}
            disabled={submitting}
            style={{
              padding: "9px 20px",
              border: "1px solid #d1d5db",
              borderRadius: "6px",
              background: "#fff",
              color: submitting ? "#9ca3af" : "#374151",
              fontWeight: 600,
              fontSize: "0.875rem",
              cursor: submitting ? "not-allowed" : "pointer",
              transition: "background 0.15s",
            }}
            onMouseEnter={(e) => {
              if (!submitting) e.currentTarget.style.background = "#f9fafb";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "#fff";
            }}
          >
            Cancel
          </button>
          <button
            type="submit"
            className="btn btn-sm btn-success"
            disabled={submitting}
            style={{
              padding: "9px 22px",
              fontSize: "0.875rem",
              fontWeight: 600,
              borderRadius: "6px",
              cursor: submitting ? "not-allowed" : "pointer",
              opacity: submitting ? 0.75 : 1,
              display: "flex",
              alignItems: "center",
              gap: "6px",
            }}
          >
            {submitting && (
              <span
                className="spinner-border spinner-border-sm"
                role="status"
                aria-hidden="true"
              />
            )}
            {submitting ? "Submitting..." : "Submit"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
