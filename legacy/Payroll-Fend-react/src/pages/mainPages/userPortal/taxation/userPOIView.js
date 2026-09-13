import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet-async";
import { Modal, Button } from "antd";
import { useNavigate } from "react-router-dom";
import axios from "axios";

// Import global constants and helpers
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";

export default function UserPOIView() {
  const navigate = useNavigate();

  // Get organization ID and token from localStorage
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

  const [allSubmissions, setAllSubmissions] = useState([]);
  const [selectedFY, setSelectedFY] = useState("");
  const [currentDocs, setCurrentDocs] = useState([]);
  const [preview, setPreview] = useState({
    visible: false,
    title: "",
    dataUrl: "",
    mimeType: "",
  });
  const [loading, setLoading] = useState(false);
  const [financialYears, setFinancialYears] = useState([]);

  useEffect(() => {
    loadAllSubmissions();
  }, []);

  useEffect(() => {
    if (selectedFY) {
      const financialYearNumber = parseInt(selectedFY.split("-")[0]);
      loadDocumentsForFinancialYear(financialYearNumber);
    } else {
      setCurrentDocs([]);
      setAllSubmissions([]);
    }
  }, [selectedFY]);

  // ========== BACKEND API METHODS ==========

  const loadAllSubmissions = async () => {
    try {
      setLoading(true);

      // Get available financial years (current + previous)
      const today = new Date();
      const year = today.getFullYear();
      const currentFYStart = today.getMonth() + 1 >= 4 ? year : year - 1;
      const currentFY = getFinancialYearLabel(currentFYStart);
      const prevFY = getFinancialYearLabel(currentFYStart - 1);

      const availableFYs = [prevFY, currentFY];
      setFinancialYears(availableFYs);

      // Set default selected FY to current FY
      if (!selectedFY) {
        setSelectedFY(currentFY);
      }
    } catch (error) {
      console.error("Error loading submissions:", error);
      errorMsg("Error", "Failed to load submissions", true);
    } finally {
      setLoading(false);
    }
  };

  const loadDocumentsForFinancialYear = async (financialYearNumber) => {
    try {
      setLoading(true);

      const response = await axios.get(
        `${GlobalConst.API_URL}/api/employee-investment-proof/${employeeId}`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
          },
          params: {
            financialYear: financialYearNumber,
          },
        }
      );

      if (response.data && response.data.data) {
        // 🔴 IMPORTANT: backend returns an object with `documents` array
        const submission = response.data.data;
        const documents = submission.documents || [];

        setCurrentDocs(documents);

        const submissionForFY = {
          financialYear: getFinancialYearLabel(financialYearNumber),
          documents: documents,
          submittedAt:
            submission.createdDate ||
            (documents.length > 0
              ? documents[0].submittedDate
              : new Date().toISOString()),
          status: submission.status || "",
        };

        setAllSubmissions([submissionForFY]);
      } else {
        setCurrentDocs([]);
        setAllSubmissions([]);
      }
    } catch (error) {
      console.error("Error loading documents for financial year:", error);
      if (error.response && error.response.status === 404) {
        // No documents found for this financial year
        setCurrentDocs([]);
        setAllSubmissions([]);
      } else {
        errorMsg("Error", "Failed to load documents", true);
      }
    } finally {
      setLoading(false);
    }
  };

  // Kept for compatibility, but not used for download now (only fileUrl is used)
  const getDownloadUrl = async (documentId) => {
    try {
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/proof-of-investment/documents/${documentId}/download`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data && response.data.data) {
        return response.data.data.downloadUrl;
      }
      return null;
    } catch (error) {
      console.error("Error getting download URL:", error);
      errorMsg("Error", "Failed to get download URL", true);
      return null;
    }
  };

  const openPreview = async (doc) => {
    try {
      if (!doc.fileUrl) {
        errorMsg("Error", "No preview available", true);
        return;
      }

      // If we already have a blob URL, clean it up
      if (preview.dataUrl && preview.dataUrl.startsWith("blob:")) {
        URL.revokeObjectURL(preview.dataUrl);
      }

      // Fetch the file as a blob so PDF can be shown in iframe without redirecting
      const response = await axios.get(doc.fileUrl, {
        responseType: "blob",
      });

      const blob = response.data;
      const mimeType = blob.type || getMimeTypeFromFileName(doc.fileName);
      const objectUrl = URL.createObjectURL(blob);

      setPreview({
        visible: true,
        title: doc.fileName || "Document Preview",
        dataUrl: objectUrl,
        mimeType: mimeType,
      });
    } catch (error) {
      console.error("Error opening preview:", error);
      errorMsg("Error", "Failed to open preview", true);
    }
  };

  const closePreview = () => {
    // Cleanup blob URL if any
    if (preview.dataUrl && preview.dataUrl.startsWith("blob:")) {
      URL.revokeObjectURL(preview.dataUrl);
    }
    setPreview({ visible: false, title: "", dataUrl: "", mimeType: "" });
  };

  const handleEdit = (fy) => {
    navigate(`/edit-user-poi?fy=${encodeURIComponent(fy)}`);
  };

  // ========== HELPER METHODS ==========

  const getFinancialYearLabel = (startYear) => {
    return `${startYear}-${String(startYear + 1).slice(2)}`;
  };

  const getMimeTypeFromFileName = (fileName) => {
    if (!fileName) return "";
    if (fileName.toLowerCase().endsWith(".pdf")) return "application/pdf";
    if (
      fileName.toLowerCase().endsWith(".jpg") ||
      fileName.toLowerCase().endsWith(".jpeg")
    )
      return "image/jpeg";
    if (fileName.toLowerCase().endsWith(".png")) return "image/png";
    return "";
  };

  const statusBadge = (status) => {
    switch (status) {
      case "PENDING":
      case "Pending":
        return <span className="badge badge-light-warning">Pending</span>;
      case "APPROVED":
      case "Approved":
        return <span className="badge badge-light-success">Approved</span>;
      case "REJECTED":
      case "Rejected":
        return <span className="badge badge-light-danger">Rejected</span>;
      case "DRAFT":
      case "Draft":
        return <span className="badge badge-light-info">Draft</span>;
      case "SUBMITTED":
      case "Submitted":
        return <span className="badge badge-light-warning">Submitted</span>;
      case "Overridden":
        return <span className="badge badge-light-info">Overridden</span>;
      default:
        return (
          <span className="badge badge-light-secondary">
            {status || "Unknown"}
          </span>
        );
    }
  };

  const computeFYOptions = () => {
    return financialYears;
  };

  const getSelectedSubmissionMeta = () => {
    return allSubmissions.find((s) => s.financialYear === selectedFY) || null;
  };

  const isSubmissionFinalApproved = (documents) => {
    // Now treat submission as final approved if overall status is APPROVED
    const meta = getSelectedSubmissionMeta();
    if (!meta || !meta.status) return false;
    return meta.status === "APPROVED" || meta.status === "Approved";
  };

  const canEditDocuments = (documents) => {
    // Can edit if submission is not finally approved
    const meta = getSelectedSubmissionMeta();
    if (!meta || !meta.status) return true;
    return !isSubmissionFinalApproved(documents);
  };

  const handleDownload = async (doc) => {
    try {
      if (!doc.fileUrl) {
        errorMsg("Error", "No download URL available for this document", true);
        return;
      }

      // 👇 Pure frontend download: fetch as blob, then trigger browser download
      const response = await axios.get(doc.fileUrl, {
        responseType: "blob",
      });

      const blob = response.data;
      const downloadUrl = URL.createObjectURL(blob);

      const link = document.createElement("a");
      link.href = downloadUrl;
      link.download = doc.fileName || "document";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      URL.revokeObjectURL(downloadUrl);

      successMsg("Success", "Download started", false);
    } catch (error) {
      console.error("Error downloading document:", error);
      errorMsg("Error", "Failed to download document", true);
    }
  };

  // Pre-compute current submission meta + normalized status for UI
  const selectedSubmissionMeta = getSelectedSubmissionMeta();
  const normalizedStatus = selectedSubmissionMeta?.status
    ? selectedSubmissionMeta.status.toUpperCase()
    : "";

  return (
    <>
      <Helmet>
        <title>Overview — Proof of Investment</title>
      </Helmet>

      {loading && <Loader />}

      <div className="container-fluid p-0 bg-white">
        <div className="p-6">
          <h2 className="fw-bolder">Overview — Proof of Investment</h2>
          <div className="text-muted mb-2">
            View submitted documents, statuses and remarks
          </div>

          {/* Filter */}
          <div className="row g-2 mb-3">
            <div className="col-auto">
              <label className="form-label small text-muted">Filter by</label>
              <select
                className="form-select form-select-sm"
                style={{ minWidth: 200 }}
                value={selectedFY}
                onChange={(e) => setSelectedFY(e.target.value)}
              >
                <option value="">Select Financial Year</option>
                {computeFYOptions().map((fy) => (
                  <option key={fy} value={fy}>
                    {fy}
                  </option>
                ))}
              </select>
            </div>

            <div className="col-auto align-self-end">
              <button
                className="btn btn-sm btn-light"
                onClick={loadAllSubmissions}
                disabled={loading}
              >
                {loading ? "Loading..." : "Refresh"}
              </button>
            </div>
          </div>

          {!selectedFY && (
            <div className="card mb-3">
              <div className="card-body">
                <h5 className="mb-1">No financial year selected</h5>
                <p className="text-muted mb-0">
                  Use the filter above to select a financial year.
                </p>
              </div>
            </div>
          )}

          {selectedFY && currentDocs.length === 0 && !loading && (
            <div className="card mb-3">
              <div className="card-body text-center">
                <h5 className="mb-1">No documents found</h5>
                <p className="text-muted mb-0">
                  No Proof of Investment documents found for {selectedFY}.
                </p>
                <button
                  className="btn btn-primary mt-3"
                  onClick={() => navigate("/user-poi")}
                >
                  Upload Documents
                </button>
              </div>
            </div>
          )}

          {selectedFY && currentDocs.length > 0 && (
            <div className="card">
              <div className="card-header d-flex justify-content-between align-items-center">
                <div>
                  <h5 className="mb-0 fw-bold">{selectedFY}</h5>
                  <small className="text-muted">
                    {selectedSubmissionMeta && selectedSubmissionMeta.submittedAt
                      ? `Submitted: ${new Date(
                          selectedSubmissionMeta.submittedAt
                        ).toLocaleString()}`
                      : "Documents available"}
                  </small>
                </div>

                <div>
                  {selectedSubmissionMeta && selectedSubmissionMeta.status ? (
                    statusBadge(selectedSubmissionMeta.status)
                  ) : (
                    <span className="badge badge-light-secondary">
                      Status Unknown
                    </span>
                  )}
                </div>
              </div>

              <div className="card-body">
                <div className="table-responsive">
                  <table className="table align-middle">
                    <thead>
                      <tr className="text-muted small">
                        <th>Sr. No</th>
                        <th>Declared Item (Name)</th>
                        <th>Type</th>
                        <th>Uploaded File</th>
                        <th>Status</th>
                        <th>Remarks</th>
                        <th>Submitted Date</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>

                    <tbody>
                      {currentDocs.map((d, idx) => (
                        <tr key={d.id || idx}>
                          <td>{idx + 1}</td>
                          <td className="fw-semibold">
                            {d.declaredItemName || "-"}
                          </td>
                          <td>{d.documentType || "-"}</td>

                          <td>
                            {d.fileName ? (
                              <div className="d-flex align-items-center gap-2">
                                <button
                                  className="btn btn-sm btn-link p-0 text-start"
                                  onClick={() => openPreview(d)}
                                >
                                  {d.fileName}
                                </button>
                                <button
                                  className="btn btn-sm btn-outline-primary"
                                  onClick={() => handleDownload(d)}
                                  title="Download"
                                >
                                  <i className="bi bi-download"></i>
                                </button>
                              </div>
                            ) : (
                              <span className="text-muted">No file</span>
                            )}
                          </td>

                          <td>
                            {statusBadge(
                              d.status || selectedSubmissionMeta?.status
                            )}
                          </td>

                          <td>
                            {d.remarks ? (
                              d.remarks
                            ) : (
                              <span className="text-muted">—</span>
                            )}
                          </td>

                          <td>
                            {d.submittedDate ? (
                              <small className="text-muted">
                                {new Date(
                                  d.submittedDate
                                ).toLocaleDateString()}
                              </small>
                            ) : selectedSubmissionMeta?.submittedAt ? (
                              <small className="text-muted">
                                {new Date(
                                  selectedSubmissionMeta.submittedAt
                                ).toLocaleDateString()}
                              </small>
                            ) : (
                              <span className="text-muted">—</span>
                            )}
                          </td>

                          <td className="text-end">
                            <div className="dropdown">
                              <button
                                className="btn btn-sm btn-light"
                                type="button"
                                id={`actionMenu${idx}`}
                                data-bs-toggle="dropdown"
                                aria-expanded="false"
                              >
                                ⋯
                              </button>

                              <ul
                                className="dropdown-menu dropdown-menu-end"
                                aria-labelledby={`actionMenu${idx}`}
                              >
                                <li>
                                  <button
                                    className="dropdown-item"
                                    onClick={() => openPreview(d)}
                                  >
                                    <i className="bi bi-eye me-2"></i>View
                                  </button>
                                </li>

                                <li>
                                  <button
                                    className="dropdown-item"
                                    onClick={() => handleDownload(d)}
                                  >
                                    <i className="bi bi-download me-2"></i>
                                    Download
                                  </button>
                                </li>

                                <li>
                                  <hr className="dropdown-divider" />
                                </li>

                                <li>
                                  {!canEditDocuments(currentDocs) ? (
                                    <button
                                      className="dropdown-item"
                                      disabled
                                    >
                                      <i className="bi bi-lock me-2"></i>
                                      Edit (Locked)
                                    </button>
                                  ) : (
                                    <button
                                      className="dropdown-item"
                                      onClick={() => handleEdit(selectedFY)}
                                    >
                                      <i className="bi bi-pencil me-2"></i>
                                      Edit
                                    </button>
                                  )}
                                </li>
                              </ul>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Summary Section */}
                <div className="mt-4 p-3 bg-light rounded">
                  <div className="row text-center">
                    <div className="col">
                      <div className="fw-bold text-primary">
                        {currentDocs.length}
                      </div>
                      <div className="text-muted small">Total Documents</div>
                    </div>
                    <div className="col">
                      <div className="fw-bold text-success">
                        {normalizedStatus === "APPROVED"
                          ? currentDocs.length
                          : 0}
                      </div>
                      <div className="text-muted small">Approved</div>
                    </div>
                    <div className="col">
                      <div className="fw-bold text-warning">
                        {normalizedStatus === "PENDING" ||
                        normalizedStatus === "SUBMITTED"
                          ? currentDocs.length
                          : 0}
                      </div>
                      <div className="text-muted small">Pending</div>
                    </div>
                    <div className="col">
                      <div className="fw-bold text-danger">
                        {normalizedStatus === "REJECTED"
                          ? currentDocs.length
                          : 0}
                      </div>
                      <div className="text-muted small">Rejected</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      <Modal
        title={preview.title}
        visible={preview.visible}
        onCancel={closePreview}
        footer={[<Button onClick={closePreview}>Close</Button>]}
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
            <div className="mb-3">
              <i className="bi bi-file-earmark-text display-4 text-muted"></i>
            </div>
            <div>Preview not available for this file type.</div>
          </div>
        ) : (
          <div className="text-center text-muted">
            <div>Loading preview...</div>
          </div>
        )}
      </Modal>
    </>
  );
}
