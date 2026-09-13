import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet-async";
import { Upload, Button, message } from "antd";
import { UploadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from "react-router-dom";

/**
 * editUserpoi.js
 * - Edit page for a particular financial year (query param ?fy=2024-25)
 * - If submission.finalApproved === true -> view-only (no edits allowed)
 * - Allows editing document name/type and replacing attachments (files read as base64)
 * - On submit, updates STORAGE_KEY entry for that FY (keeps finalApproved as-is)
 * - Uses Ant Upload customRequest to read file to base64 data URL
 */

const STORAGE_KEY = "poi_submissions_demo";
const MAX_FILE_BYTES = 15 * 1024 * 1024; // 15 MB
const ACCEPTED_TYPES = ["application/pdf", "image/jpeg", "image/png"];

const getQuery = (search) => {
  return new URLSearchParams(search);
};

export default function EditUserPOI() {
  const location = useLocation();
  const navigate = useNavigate();
  const params = getQuery(location.search);
  const fy = params.get("fy") || "";

  const [submission, setSubmission] = useState(null); // the object for the FY
  const [rows, setRows] = useState([]);
  const [readonly, setReadonly] = useState(false); // true if finalApproved
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!fy) {
      // no fy provided; redirect to overview
      navigate("/overview-poi");
      return;
    }
    loadSubmission();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fy]);

  const loadSubmission = () => {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      setSubmission(null);
      setRows([]);
      return;
    }
    try {
      const parsed = JSON.parse(raw) || [];
      const found = parsed.find((p) => p.financialYear === fy);
      if (!found) {
        setSubmission(null);
        setRows([]);
        return;
      }
      setSubmission(found);
      setReadonly(Boolean(found.finalApproved));
      // map documents to rows
      const mapped = (found.documents || []).map((d, i) => ({
        id: i + 1,
        documentName: d.documentName || "",
        documentType: d.documentType || "",
        fileDataUrl: d.fileDataUrl || "",
        fileName: d.fileName || "",
        mimeType: d.mimeType || "",
      }));
      setRows(mapped.length ? mapped : [{
        id: Date.now(),
        documentName: "",
        documentType: "",
        fileDataUrl: "",
        fileName: "",
        mimeType: "",
      }]);
    } catch {
      setSubmission(null);
      setRows([]);
    }
  };

  const addRow = () => {
    setRows((prev) => [
      ...prev,
      {
        id: Date.now(),
        documentName: "",
        documentType: "",
        fileDataUrl: "",
        fileName: "",
        mimeType: "",
      },
    ]);
  };

  const removeRow = (id) => {
    setRows((prev) => prev.filter((r) => r.id !== id));
    setErrors((errs) => {
      const c = { ...errs };
      delete c[id];
      return c;
    });
  };

  const onChangeField = (id, field, value) => {
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, [field]: value } : r)));
    setErrors((errs) => {
      const c = { ...errs };
      delete c[id];
      return c;
    });
  };

  const handleCustomUpload = ({ file, onSuccess, onError, onProgress }, rowId) => {
    if (!ACCEPTED_TYPES.includes(file.type)) {
      onError(new Error("Invalid file type. Only PDF, JPG, PNG allowed."));
      message.error("Invalid file type. Only PDF, JPG, PNG allowed.");
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      onError(new Error("File size exceeds 15 MB."));
      message.error("File size exceeds 15 MB.");
      return;
    }

    let loaded = 0;
    const total = file.size;
    const progressInterval = setInterval(() => {
      loaded += Math.min(total / 10, total - loaded);
      onProgress({ percent: Math.min(100, Math.round((loaded / total) * 100)) }, file);
    }, 80);

    const reader = new FileReader();
    reader.onload = (e) => {
      clearInterval(progressInterval);
      const dataUrl = e.target.result;
      onChangeField(rowId, "fileDataUrl", dataUrl);
      onChangeField(rowId, "fileName", file.name);
      onChangeField(rowId, "mimeType", file.type);
      onSuccess(null, file);
      message.success(`${file.name} attached`);
    };
    reader.onerror = (err) => {
      clearInterval(progressInterval);
      onError(err);
      message.error("Error reading file");
    };
    reader.readAsDataURL(file);
  };

  const validateAll = () => {
    const errs = {};
    rows.forEach((r) => {
      const hasName = r.documentName && r.documentName.trim() !== "";
      if (hasName && !r.fileDataUrl) {
        errs[r.id] = "Attach Proof is required when Document Name is entered.";
      }
    });
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (readonly) {
      message.info("This submission is final approved and cannot be edited.");
      return;
    }
    if (!validateAll()) return;

    setSubmitting(true);

    // build new documents array
    const newDocs = rows.map((r) => ({
      documentName: r.documentName,
      documentType: r.documentType,
      fileName: r.fileName,
      fileDataUrl: r.fileDataUrl,
      mimeType: r.mimeType,
      status: "Pending",
      remarks: "",
      submittedAt: new Date().toISOString(),
    }));

    // update storage
    const raw = localStorage.getItem(STORAGE_KEY);
    let arr = [];
    if (raw) {
      try {
        arr = JSON.parse(raw) || [];
      } catch {
        arr = [];
      }
    }

    // replace the FY entry
    const filtered = arr.filter((p) => p.financialYear !== fy);
    filtered.push({
      ...submission,
      financialYear: fy,
      submittedAt: new Date().toISOString(),
      finalApproved: submission?.finalApproved || false,
      documents: newDocs,
    });

    localStorage.setItem(STORAGE_KEY, JSON.stringify(filtered));

    setTimeout(() => {
      setSubmitting(false);
      message.success("Submission updated");
      navigate("/overview-poi");
    }, 600);
  };

  if (!fy) {
    return null;
  }

  return (
    <>
      <Helmet>
        <title>Edit POI - {fy}</title>
      </Helmet>

      <div className="app-toolbar pt-4">
        <div className="container-fluid">
          <h2 className="fw-bolder mb-1">Edit Proof of Investment</h2>
          <div className="text-muted">Financial Year: <strong>{fy}</strong></div>
        </div>
      </div>

      <div className="app-content mt-4">
        <div className="container-fluid">
          <div className="card">
            <div className="card-body p-4">
              {submission && submission.finalApproved && (
                <div className="alert alert-success">
                  This submission has been final approved by admin. You may view documents but cannot edit.
                </div>
              )}

              <form onSubmit={handleSubmit}>
                {/* Add button top */}
                <div className="d-flex justify-content-between align-items-center mb-3">
                  <div>
                    <div className="fw-semibold">Documents</div>
                    <div className="text-muted small">Edit declared items and attach proofs</div>
                  </div>
                  {!readonly && (
                    <Button type="primary" icon={<PlusOutlined />} size="small" onClick={addRow}>Add</Button>
                  )}
                </div>

                {/* Rows */}
                {rows.map((r, idx) => (
                  <div key={r.id} className="row align-items-center gy-3 mb-3">
                    <div className="col-auto">
                      <div className="fw-semibold">{idx + 1}</div>
                    </div>

                    <div className="col-12 col-md-4">
                      <label className="form-label small text-muted">Document Name</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="Enter Document Name"
                        value={r.documentName}
                        onChange={(e) => onChangeField(r.id, "documentName", e.target.value)}
                        disabled={readonly}
                      />
                    </div>

                    <div className="col-12 col-md-3">
                      <label className="form-label small text-muted">Document Type</label>
                      <input
                        type="text"
                        className="form-control"
                        placeholder="Enter Document Type"
                        value={r.documentType}
                        onChange={(e) => onChangeField(r.id, "documentType", e.target.value)}
                        disabled={readonly}
                      />
                    </div>

                    <div className="col-12 col-md-4 d-flex align-items-center">
                      <div>
                        <label className="form-label small text-muted d-block">Attach Proof</label>

                        <Upload
                          accept=".pdf,image/png,image/jpeg"
                          customRequest={(options) => handleCustomUpload(options, r.id)}
                          showUploadList={false}
                          disabled={readonly}
                        >
                          <Button
                            icon={<UploadOutlined />}
                            className="btn btn-sm btn-outline-primary"
                            style={{ display: "inline-flex", alignItems: "center" }}
                            disabled={readonly}
                          >
                            Attach Proof
                          </Button>
                        </Upload>

                        <div className="mt-2">
                          {r.fileName ? (
                            <div>
                              <strong className="me-2">{r.fileName}</strong>
                              <small className="text-muted">({r.mimeType})</small>
                            </div>
                          ) : (
                            <small className="text-muted">No file attached</small>
                          )}
                        </div>

                        {errors[r.id] && <div className="form-text text-danger">{errors[r.id]}</div>}
                      </div>
                    </div>

                    {/* Delete button */}
                    <div className="col-auto d-flex align-items-center">
                      {!readonly ? (
                        <Button danger icon={<DeleteOutlined />} onClick={() => removeRow(r.id)} size="small" />
                      ) : (
                        <Button icon={<DeleteOutlined />} disabled size="small" />
                      )}
                    </div>
                  </div>
                ))}

                <div className="d-flex justify-content-end mt-4">
                  <Button type="default" onClick={() => navigate("/overview-poi")} className="me-2">Cancel</Button>
                  <Button type="primary" htmlType="submit" loading={submitting} disabled={readonly}>
                    Save Changes
                  </Button>
                </div>
              </form>

            </div>
          </div>
        </div>
      </div>
    </>
  );
}
