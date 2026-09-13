// File: userPOI.js
// Backend-integrated version with database storage

import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";
import { Upload, Button, message, Modal } from "antd";
import { UploadOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import axios from "axios";

// Import global constants and helpers
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";

/**
 * userPOI.js - Backend Integrated Version
 * 
 * Features:
 * - Integrated with POI backend API
 * - Real database storage instead of localStorage
 * - Organization ID from localStorage
 * - JWT token authentication
 * - Draft functionality using backend (manual Save Draft only)
 * - File upload to Cloudinary via backend
 * - Financial year management
 */

const MAX_FILE_BYTES = 15 * 1024 * 1024; // 15 MB
const ACCEPTED_TYPES = ["application/pdf", "image/jpeg", "image/png"];

const getFinancialYearLabel = (startYear) => `${startYear}-${String(startYear + 1).slice(2)}`;

export default function UserPOI() {
  const navigate = useNavigate();
  
  // Get organization ID from localStorage (following index.js pattern)
  const organizationId = localStorage.getItem("organizationId");
  const token = localStorage.getItem("__t");

  const getDecodedToken = () => {
    if (!token) return null;
    try {
      const payloadBase64 = token.split(".")[1];
      const decoded = JSON.parse(atob(payloadBase64));
      return decoded;
    } catch (e) {
      console.error("Failed to decode token", e);
      return null;
    }
  };

  const employeeId = getDecodedToken()?.sub;

  // compute current & previous FY (FY starts Apr 1)
  const today = new Date();
  const year = today.getFullYear();
  const currentFYStart = (today.getMonth() + 1) >= 4 ? year : year - 1;
  const currentFY = getFinancialYearLabel(currentFYStart);
  const prevFY = getFinancialYearLabel(currentFYStart - 1);

  const [financialYear, setFinancialYear] = useState(currentFY);
  const [rows, setRows] = useState([
    {
      id: Date.now(),
      documentName: "",
      documentType: "",
      file: null,
      fileName: "",
    },
  ]);
  const [errors, setErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);

  const [allowedToUpload, setAllowedToUpload] = useState(false);
  const [preview, setPreview] = useState({ visible: false, title: "", dataUrl: "", mimeType: "" });

  // On mount: check if user has existing submissions for current FY
  useEffect(() => {
    checkExistingSubmissions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ========== BACKEND API METHODS ==========

  const checkExistingSubmissions = async () => {
    try {
      setLoading(true);

      // Always check for the current financial year on first load
      const financialYearNumber = parseInt(currentFY.split('-')[0]);

      const response = await axios.get(
        `${GlobalConst.API_URL}/api/employee-investment-proof/${employeeId}`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId
          },
          params: {
            financialYear: financialYearNumber
          }
        }
      );

      const submission = response?.data?.data;

      if (!submission) {
        // No existing submission for current FY -> show info screen
        setAllowedToUpload(false);
        setFinancialYear(currentFY);
        return;
      }

      const status = submission.status;

      // If status is SUBMITTED (or anything other than DRAFT) -> go to overview, cannot upload again
      if (status && status !== "DRAFT") {
        navigate("/overview-poi");
        return;
      }

      // If status is DRAFT -> open upload screen with existing draft docs
      if (status === "DRAFT") {
        const documents = submission.documents || [];

        if (documents.length > 0) {
          const mappedRows = documents.map((doc, index) => ({
            id: doc.id || Date.now() + index,
            documentName: doc.declaredItemName || "",
            documentType: doc.documentType || "",
            fileName: doc.fileName || "",
            fileUrl: doc.fileUrl || "",
            file: null, // existing file already stored on backend
            status: doc.status || "DRAFT",
          }));
          setRows(mappedRows);
        } else {
          setRows([
            {
              id: Date.now(),
              documentName: "",
              documentType: "",
              file: null,
              fileName: "",
            },
          ]);
        }

        setFinancialYear(getFinancialYearLabel(submission.financialYear || currentFYStart));
        setAllowedToUpload(true);
        return;
      }

      // Fallback: if for some reason no status or unknown status, treat as submitted (view-only)
      navigate("/overview-poi");

    } catch (error) {
      if (error.response && error.response.status === 404) {
        // No existing submissions for current FY
        console.log("No existing POI submissions found for current FY");
        setAllowedToUpload(false);
        setFinancialYear(currentFY);
      } else {
        console.error("Error checking existing submissions:", error);
        errorMsg("Error", "Failed to check existing submissions", true);
      }
    } finally {
      setLoading(false);
    }
  };

  // ========== FORM + UPLOAD HELPERS ==========

  const addRow = () => {
    setRows((prev) => [
      ...prev,
      {
        id: Date.now(),
        documentName: "",
        documentType: "",
        file: null,
        fileName: "",
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

  // Ant Upload customRequest: store file in state
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

    // Create object URL for preview
    const objectUrl = URL.createObjectURL(file);
    
    onChangeField(rowId, "file", file);
    onChangeField(rowId, "fileName", file.name);
    onChangeField(rowId, "fileUrl", objectUrl);
    
    clearInterval(progressInterval);
    onSuccess(null, file);
    message.success(`${file.name} attached`);
  };

  const validateAll = () => {
    const errs = {};
    let hasValidDocument = false;

    rows.forEach((r) => {
      const hasName = r.documentName && r.documentName.trim() !== "";
      if (hasName && !r.file) {
        errs[r.id] = "File upload is required when Document Name is entered.";
      }
      console.log("Row validation:", r, hasName, r.file);
      if (hasName && r.file) {
        hasValidDocument = true;
      }
    });

    if (!hasValidDocument) {
      message.error("Please add at least one document with both name and file.");
      return false;
    }

    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateAll()) return;

    Modal.confirm({
      title: "Confirm submission",
      content: "Are you sure you want to submit these documents for Proof of Investment? HR will verify your documents and approve them. Please check the Overview tab for status and remarks.",
      okText: "Yes, submit",
      cancelText: "Cancel",
      onOk: submitToBackend
    });
  };

  const submitToBackend = async () => {
    setSubmitting(true);

    try {
      const financialYearNumber = parseInt(financialYear.split('-')[0]);
      
      // Build a single FormData and send list in files[i].*
      const formData = new FormData();
      formData.append("financialYear", financialYearNumber.toString());
      formData.append("status", "SUBMITTED");

      let index = 0;

      rows.forEach((row) => {
        if (!row.documentName || !row.file) {
          return; // Skip incomplete rows
        }

        formData.append(`documents[${index}].declaredItemName`, row.documentName);
        formData.append(`documents[${index}].documentType`, row.documentType || "POI");
        formData.append(`documents[${index}].file`, row.file);
        index++;
      });

      if (index === 0) {
        message.error("Please add at least one complete document before submitting.");
        setSubmitting(false);
        return;
      }

      await axios.post(`${GlobalConst.API_URL}/api/employee-investment-proof`, formData, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
          'Content-Type': 'multipart/form-data'
        },
        params: {
          employeeId: employeeId
        }
      });
      
      successMsg("Success", "Documents submitted successfully for verification", false);
      
      // Redirect to overview page
      setTimeout(() => {
        navigate("/overview-poi");
      }, 1000);

    } catch (error) {
      console.error("Error submitting documents:", error);
      errorMsg("Error", "Failed to submit documents", true);
    } finally {
      setSubmitting(false);
    }
  };

  // Save as Draft using same API as submit (status = DRAFT)
  const handleSaveDraft = async () => {
    try {
      const financialYearNumber = parseInt(financialYear.split('-')[0]);
      
      const formData = new FormData();
      formData.append("financialYear", financialYearNumber.toString());
      formData.append("status", "DRAFT");

      let index = 0;

      rows.forEach((row) => {
        const hasName = row.documentName && row.documentName.trim() !== "";
        const hasFile = !!row.file;
        const hasExistingFile = !!row.fileUrl;

        if (!hasName && !hasFile && !hasExistingFile) {
          return; // skip totally empty rows
        }

        formData.append(`documents[${index}].declaredItemName`, row.documentName || "");
        formData.append(`documents[${index}].documentType`, row.documentType || "POI");
        if (row.file) {
          formData.append(`documents[${index}].file`, row.file);
        }
        index++;
      });

      if (index === 0) {
        message.info("Nothing to save as draft.");
        return;
      }

      await axios.post(`${GlobalConst.API_URL}/api/employee-investment-proof`, formData, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
          'Content-Type': 'multipart/form-data'
        },
        params: {
          employeeId: employeeId
        }
      });

      successMsg("Success", "Draft saved successfully", false);
      setAllowedToUpload(true);

    } catch (error) {
      console.error("Error saving draft:", error);
      errorMsg("Error", "Failed to save draft", true);
    }
  };

  const handleCancel = () => {
    setRows([{
      id: Date.now(),
      documentName: "",
      documentType: "",
      file: null,
      fileName: "",
    }]);
    setErrors({});
    setAllowedToUpload(false);
    message.info("Changes discarded");
  };

  const closePreview = () => setPreview({ visible: false, title: "", dataUrl: "", mimeType: "" });

  // UI render
  return (
    <>
      <Helmet>
        <title>Proof Of Investment | User</title>
      </Helmet>

      {loading && <Loader />}

      <div className="app-toolbar pt-4">
        <div className="container-fluid d-flex align-items-center justify-content-start">
          <div>
            <h2 className="fw-bolder mb-1">Proof of Investment</h2>
            <div className="text-muted">Attach proofs for exemptions and declarations</div>
          </div>
        </div>
      </div>

      <div className="app-content mt-4">
        <div className="container-fluid">

          {/* Info card (first-time for current FY) */}
          {!allowedToUpload && (
            <div className="card mb-4 shadow-sm">
              <div className="card-body p-5">
                <div className="row">
                  <div className="col-md-8">
                    <h4 className="fw-bold">Proof of Investment — Tax Declaration</h4>

                    <p className="text-muted mb-3">
                      Use this portal to declare investments and upload documentary proofs for tax exemptions.
                      Submissions are verified by HR/Finance and applied to your payroll for tax computation.
                    </p>

                    <div className="row">
                      <div className="col-sm-6">
                        <h6 className="mb-2 fw-semibold">Purpose</h6>
                        <p className="text-muted small mb-3">
                          To claim deductions and exemptions under the Income Tax Act (e.g. Section 80C, 80D, Section 24(b), etc.)
                          by submitting supporting documents so your taxable income and TDS are calculated correctly.
                        </p>

                        <h6 className="mb-2 fw-semibold">What to Declare</h6>
                        <ul className="list-unstyled text-muted small mb-3">
                          <li>• Investments under Section 80C (PF, PPF, ELSS, Life Insurance, NSC)</li>
                          <li>• Health insurance premiums (Section 80D)</li>
                          <li>• Home loan interest (Section 24(b))</li>
                          <li>• Other exemptions (rent, education loan interest, donations, etc.)</li>
                        </ul>
                      </div>

                      <div className="col-sm-6">
                        <h6 className="mb-2 fw-semibold">Accepted Proofs (examples)</h6>
                        <ul className="list-unstyled text-muted small mb-3">
                          <li>• Insurance premium receipts / policy documents</li>
                          <li>• Bank/Mutual Fund statements showing investments</li>
                          <li>• PPF/NSC passbook screenshots</li>
                          <li>• Loan repayment statement or interest certificate</li>
                          <li>• Donation receipts with registration details</li>
                        </ul>

                        <h6 className="mb-2 fw-semibold">Key Notes</h6>
                        <p className="text-muted small mb-0">
                          Submissions are reviewed by HR/Finance. You can upload documents now — you may edit them until HR gives final approval. Once finally approved, documents become view-only.
                        </p>
                      </div>
                    </div>

                    <div className="mt-4 d-flex gap-2">
                      <Button type="primary" onClick={() => setAllowedToUpload(true)}>
                        Yes, I want to upload documents
                      </Button>

                      <Button onClick={() => message.info("You can upload documents later by clicking 'Upload Documents'")}>
                        No, maybe later
                      </Button>
                    </div>
                  </div>

                  <div className="col-md-4 d-flex align-items-center justify-content-center">
                    <div className="text-center">
                      <div className="mb-3">
                        <svg width="96" height="96" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                          <rect x="2" y="2" width="20" height="20" rx="4" fill="#f8fafc" />
                          <path d="M8 7h8M8 11h8M8 15h5" stroke="#0d6efd" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                          <path d="M15 3v4a1 1 0 0 0 1 1h4" stroke="#0d6efd" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                      </div>
                      <div className="text-muted small">Secure • Confidential • HR Verified</div>
                      <div className="mt-2">
                        <a href="#help" className="link-primary small">Need help? Contact HR</a>
                      </div>
                    </div>
                  </div>
                </div>

              </div>
            </div>
          )}

          {/* Upload form */}
          {allowedToUpload && (
            <div className="card mb-4">
              <div className="card-body p-4">

                {/* FY selector */}
                <div className="row mb-3 align-items-center">
                  <div className="col-auto d-flex align-items-center">
                    <label className="form-label small text-muted mb-0 me-2">Financial Year</label>
                    <select
                      className="form-select form-select-sm"
                      style={{ minWidth: 140 }}
                      value={financialYear}
                      onChange={(e) => setFinancialYear(e.target.value)}
                    >
                      <option value={prevFY}>{prevFY}</option>
                      <option value={currentFY}>{currentFY}</option>
                    </select>
                  </div>
                </div>

                <hr />

                <form onSubmit={handleSubmit}>

                  <div className="row align-items-center mb-2">
                    <div className="col">
                      <div className="fw-semibold">Documents</div>
                      <div className="text-muted small">Add documents and attach proofs</div>
                    </div>
                    <div className="col-auto">
                      <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={addRow}
                        size="small"
                        title="Add document"
                      >
                        Add
                      </Button>
                    </div>
                  </div>

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
                        />
                      </div>

                      <div className="col-12 col-md-4">
                        <label className="form-label small text-muted d-block">Click here to upload your document</label>

                        <Upload
                          accept=".pdf,image/png,image/jpeg"
                          customRequest={(options) => handleCustomUpload(options, r.id)}
                          showUploadList={false}
                        >
                          <div
                            className="border rounded d-flex align-items-center"
                            style={{
                              cursor: "pointer",
                              minHeight: "56px",
                              background: "#f8fbff",
                              padding: "8px 12px",
                              gap: 12
                            }}
                          >
                            <UploadOutlined style={{ fontSize: 20, color: "#0d6efd" }} />
                            <div style={{ fontSize: 14, color: "#0d6efd" }}>Click here to upload your document</div>
                            <div style={{ marginLeft: "auto", fontSize: 13, color: "#6c757d" }}>
                              {r.fileName ? r.fileName : ""}
                            </div>
                          </div>
                        </Upload>

                        <div className="mt-2 d-flex align-items-center gap-3">
                          {/* Thumbnail preview for images */}
                          {r.fileUrl && r.file?.type?.startsWith("image/") && (
                            <img
                              src={r.fileUrl}
                              alt={r.fileName}
                              style={{ width: 56, height: 56, objectFit: "cover", borderRadius: 6, border: "1px solid #e6eefc" }}
                            />
                          )}

                          {/* View / file name area */}
                          <div>
                            {r.fileName ? (
                              <div>
                                <button 
                                  type="button"
                                  className="btn btn-link btn-sm p-0" 
                                  onClick={() => setPreview({ 
                                    visible: true, 
                                    title: r.fileName, 
                                    dataUrl: r.fileUrl, 
                                    mimeType: r.file?.type || "" 
                                  })}
                                >
                                  {r.fileName}
                                </button>
                                <div className="text-muted small">{r.file?.type}</div>
                              </div>
                            ) : (
                              <small className="text-muted">No file attached</small>
                            )}
                          </div>
                        </div>

                        {errors[r.id] && <div className="form-text text-danger">{errors[r.id]}</div>}
                      </div>

                      <div className="col-auto d-flex align-items-center">
                        <Button
                          danger
                          icon={<DeleteOutlined />}
                          onClick={() => removeRow(r.id)}
                          title="Delete document"
                          size="small"
                        />
                      </div>
                    </div>
                  ))}

                  <div className="d-flex justify-content-end gap-2 mt-4">
                    <Button type="default" onClick={handleCancel}>Cancel</Button>
                    <Button type="dashed" onClick={handleSaveDraft}>Save Draft</Button>
                    <button
                      type="submit"
                      className="btn btn-primary"
                      disabled={submitting}
                    >
                      {submitting ? (
                        <>
                          <span className="spinner-border spinner-border-sm me-2"></span>Submitting...
                        </>
                      ) : (
                        "Submit POI"
                      )}
                    </button>
                  </div>
                </form>
              </div>
            </div>
          )}

        </div>
      </div>

      <Modal
        title={preview.title}
        visible={preview.visible}
        onCancel={closePreview}
        footer={[<Button key="close" onClick={closePreview}>Close</Button>]}
        width={900}
        bodyStyle={{ minHeight: 400 }}
      >
        {preview.mimeType?.startsWith("image/") ? (
          <img
            src={preview.dataUrl}
            alt={preview.title}
            style={{
              maxWidth: "100%",
              maxHeight: "70vh",
              display: "block",
              margin: "0 auto",
            }}
          />
        ) : preview.mimeType === "application/pdf" ? (
          <iframe
            title={preview.title}
            src={preview.dataUrl}
            style={{ width: "100%", height: "70vh", border: "none" }}
          />
        ) : preview.dataUrl ? (
          <div className="text-center text-muted">
            Preview not available for this type.
          </div>
        ) : (
          <div className="text-center text-muted">
            Preview not available for this type.
          </div>
        )}
      </Modal>
    </>
  );
}
