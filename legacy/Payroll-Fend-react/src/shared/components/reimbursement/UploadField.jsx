import React, { useRef } from "react";
import { PiUploadSimpleDuotone, PiFilePdfDuotone, PiImageDuotone, PiFileDuotone, PiXBold } from "react-icons/pi";

/**
 * Format file size into readable KB / MB
 */
const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`;
};

/**
 * Get icon according to file extension/type
 */
const getFileIcon = (file) => {
  const name = file?.name?.toLowerCase() || "";
  const type = file?.type || "";

  if (name.endsWith(".pdf") || type === "application/pdf") {
    return <PiFilePdfDuotone size={20} style={{ color: "#dc2626", flexShrink: 0 }} />;
  }
  if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || type.startsWith("image/")) {
    return <PiImageDuotone size={20} style={{ color: "#2563eb", flexShrink: 0 }} />;
  }
  return <PiFileDuotone size={20} style={{ color: "#6b7280", flexShrink: 0 }} />;
};

/**
 * UploadField – a styled multi-file upload input component.
 *
 * Props:
 *  - value   : Array of File objects (or null/undefined)
 *  - onChange: (files: File[]) => void
 *  - error   : string error message
 *  - disabled: boolean
 */
export default function UploadField({ value = [], onChange, error, disabled = false }) {
  const inputRef = useRef();

  // Normalize value to array
  const files = Array.isArray(value) ? value : value ? [value] : [];

  const handleClick = () => {
    if (!disabled && inputRef.current) {
      inputRef.current.click();
    }
  };

  const handleFileChange = (e) => {
    const selected = Array.from(e.target.files || []);
    if (selected.length === 0) return;

    // Combine existing files with new selections, avoiding exact duplicates (by name & size & lastModified)
    const existingKeys = new Set(files.map((f) => `${f.name}-${f.size}-${f.lastModified}`));
    const newFiles = selected.filter((f) => !existingKeys.has(`${f.name}-${f.size}-${f.lastModified}`));

    const updated = [...files, ...newFiles];
    onChange && onChange(updated);

    // Reset input value so same file can be re-selected if removed
    if (e.target) e.target.value = "";
  };

  const handleRemove = (indexToRemove, e) => {
    e.stopPropagation();
    const updated = files.filter((_, idx) => idx !== indexToRemove);
    onChange && onChange(updated);
  };

  return (
    <div>
      {/* Upload drop/click zone */}
      <div
        onClick={handleClick}
        style={{
          border: `2px dashed ${error ? "#dc2626" : "#d1d5db"}`,
          borderRadius: "8px",
          padding: "14px 16px",
          cursor: disabled ? "not-allowed" : "pointer",
          display: "flex",
          alignItems: "center",
          gap: "10px",
          background: error ? "#fef2f2" : disabled ? "#f3f4f6" : "#f9fafb",
          transition: "border-color 0.2s, background 0.2s",
          opacity: disabled ? 0.7 : 1,
        }}
        onMouseEnter={(e) => {
          if (!disabled) {
            e.currentTarget.style.borderColor = "#059669";
            e.currentTarget.style.background = "#ecfdf5";
          }
        }}
        onMouseLeave={(e) => {
          if (!disabled) {
            e.currentTarget.style.borderColor = error ? "#dc2626" : "#d1d5db";
            e.currentTarget.style.background = error ? "#fef2f2" : "#f9fafb";
          }
        }}
      >
        <PiUploadSimpleDuotone
          size={22}
          style={{ color: error ? "#dc2626" : "#059669", flexShrink: 0 }}
        />
        <div style={{ flex: 1, minWidth: 0 }}>
          <span style={{ fontSize: "0.875rem", color: "#374151", fontWeight: 500 }}>
            {files.length > 0
              ? `Add more attachments (${files.length} selected)`
              : "Click to choose files"}
          </span>
          <span style={{ fontSize: "0.78rem", color: "#9ca3af", marginLeft: "6px" }}>
            (PDF, JPG, PNG — multiple allowed)
          </span>
        </div>
        <span
          style={{
            fontSize: "0.8rem",
            color: "#059669",
            fontWeight: 600,
            flexShrink: 0,
          }}
        >
          {files.length > 0 ? "+ Add More" : "Browse"}
        </span>
      </div>

      <input
        ref={inputRef}
        type="file"
        multiple
        accept=".pdf,.jpg,.jpeg,.png"
        disabled={disabled}
        style={{ display: "none" }}
        onChange={handleFileChange}
      />

      {/* Selected files list */}
      {files.length > 0 && (
        <div
          style={{
            marginTop: "10px",
            display: "flex",
            flexDirection: "column",
            gap: "6px",
            maxHeight: "180px",
            overflowY: "auto",
            paddingRight: "2px",
          }}
        >
          {files.map((file, idx) => (
            <div
              key={`${file.name}-${idx}`}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: "10px",
                padding: "7px 12px",
                background: "#f0fdf4",
                border: "1px solid #bbf7d0",
                borderRadius: "6px",
                fontSize: "0.82rem",
                transition: "background 0.15s",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  minWidth: 0,
                  flex: 1,
                }}
              >
                {getFileIcon(file)}
                <span
                  style={{
                    color: "#111827",
                    fontWeight: 500,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                  title={file.name}
                >
                  {file.name}
                </span>
                <span
                  style={{
                    color: "#6b7280",
                    fontSize: "0.75rem",
                    flexShrink: 0,
                  }}
                >
                  ({formatFileSize(file.size)})
                </span>
              </div>

              {!disabled && (
                <button
                  type="button"
                  title="Remove attachment"
                  onClick={(e) => handleRemove(idx, e)}
                  style={{
                    background: "transparent",
                    border: "none",
                    cursor: "pointer",
                    color: "#9ca3af",
                    padding: "2px 4px",
                    borderRadius: "4px",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    transition: "color 0.15s, background 0.15s",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.color = "#dc2626";
                    e.currentTarget.style.background = "#fee2e2";
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.color = "#9ca3af";
                    e.currentTarget.style.background = "transparent";
                  }}
                >
                  <PiXBold size={13} />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {error && (
        <div style={{ color: "#dc2626", fontSize: "0.78rem", marginTop: "4px" }}>
          {error}
        </div>
      )}
    </div>
  );
}

