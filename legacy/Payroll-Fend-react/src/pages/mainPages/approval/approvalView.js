import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import {
  Modal,
  Button,
  Input,
  message,
  Dropdown,
  Menu,
  Badge,
  Tooltip
} from "antd";
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
  DownloadOutlined,
  CommentOutlined,
  EditOutlined,
  SaveOutlined,
  DeleteOutlined,
  FileTextOutlined,
  CalendarOutlined,
  UserOutlined,
  InfoCircleOutlined,
  MoreOutlined,
  PaperClipOutlined
} from '@ant-design/icons';
import { useNavigate, useParams } from "react-router-dom";
import axios from "axios";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

import { useLocation } from "react-router-dom";
const { TextArea } = Input;

export default function ApprovalView() {
  const navigate = useNavigate();
  const { id } = useParams();

  const location = useLocation();

  const [fiscalYear, setFiscalYear] = useState(
    location.state?.fiscalYear || null
  );

  // =======================
  // FINANCIAL YEAR UTIL
  // FY = END YEAR
  // Example: FY 2026 → 2025-04 to 2026-03
  // =======================
  const getCurrentFinancialYear = () => {
    const today = new Date();
    const month = today.getMonth() + 1;
    const year = today.getFullYear();

    return month >= 4 ? year + 1 : year;
  };

  //   const getDeclarationFinancialYear = () => {
  //     const today = new Date();
  //     const month = today.getMonth() + 1;
  //     const year = today.getFullYear();

  //     const currentFY = month >= 4 ? year + 1 : year;
  //     return currentFY + 1; // UPCOMING financial year
  // };

  // State
  const [employeeData, setEmployeeData] = useState(null);
  const [investments, setInvestments] = useState([]);
  const [editAmountId, setEditAmountId] = useState(null);
  const [editAmountValue, setEditAmountValue] = useState("");
  const [commentsModal, setCommentsModal] = useState({ visible: false, investmentId: null, comments: [] });
  const [newComment, setNewComment] = useState("");
  const [approveAllModal, setApproveAllModal] = useState(false);
  const [rejectModal, setRejectModal] = useState({ visible: false, investmentId: null, reason: "" });
  const [previewDoc, setPreviewDoc] = useState({ visible: false, doc: null });
  const [loading, setLoading] = useState(false);
  // const [fiscalYear, setFiscalYear] = useState(getDeclarationFinancialYear());


  const [isApprovedAll, setIsApprovedAll] = useState(false);
  const [isConsideredForIT, setIsConsideredForIT] = useState(false);


  // Get organization, token, and fiscal year
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t") || localStorage.getItem("token");



  const getApiErrorMessage = (error, fallback) => {
    if (error?.response?.data?.message) {
      return error.response.data.message;
    }
    if (error?.message) {
      return error.message;
    }
    return fallback;
  };


  // API base URL - using same pattern as index.js
  const GlobalConst = {
    API_URL: process.env.REACT_APP_API_URL || 'http://localhost:3032'
  };



  const handleApproveAllConfirm = () => {
    Modal.confirm({
      title: "Approve Application",
      content: "Do you want to approve the application to enable ‘Consider for IT’?",
      okText: "Approve",
      cancelText: "Cancel",
      onOk: async () => {
        await handleApproveAll();
        setIsApprovedAll(true);
      }
    });
  };

  const handleConsiderForITConfirm = () => {
    Modal.confirm({
      title: "Consider for IT Declaration",
      content:
        "Do you want to consider this declaration for TDS calculation for the upcoming financial year?",
      okText: "Confirm",
      cancelText: "Cancel",
      onOk: async () => {
        await handleConsiderForIT();
        setIsConsideredForIT(true);
      }
    });
  };


  // const fiscalYear = employeeData?.fiscalYear || getCurrentFinancialYear();
  // Fetch employee POI data
  const fetchEmployeePOI = async () => {
    try {
      setLoading(true);

      const response = await axios.get(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId
          }
        }
      );

      console.log("Employee POI Response:", response.data);

      if (response.data && response.data.data) {
        const poiData = response.data.data;

        // Set employee data with proper name
        const employeeName = poiData.employeeName ||
          (poiData.employeeFirstName ?
            `${poiData.employeeFirstName} ${poiData.employeeLastName || ''}`.trim()
            : id);

        setEmployeeData({
          id: poiData.employeeId,
          employeeId: poiData.employeeId,
          employeeNumber: poiData.employeeNumber || "N/A",
          name: employeeName,
          firstName: poiData.employeeFirstName || employeeName.split(' ')[0],
          lastName: poiData.employeeLastName || employeeName.split(' ').slice(1).join(' ') || "",
          status: poiData.status || "DRAFT",
          submittedDate: formatDate(poiData.submittedDate),
          submittedBy: poiData.submittedBy || "N/A",
          approvedBy: poiData.approvedBy || "N/A",
          lastEditedBy: poiData.updatedBy || poiData.lastEditedBy || "N/A",
          lastEditedDate: formatDate(poiData.lastEditedDate),
          taxRegime: poiData.taxRegime || poiData.taxRegimeAtSubmission || "Not specified",
          taxRegimeFormatted: poiData.taxRegimeFormatted || "Not specified",
          hasPending: poiData.status === "SUBMITTED" || poiData.status === "DRAFT"
        });

        // 🔐 Sync button states from backend
        setIsConsideredForIT(!!poiData.consideredForIt);
        setIsApprovedAll(
          poiData.status === "APPROVED" || !!poiData.consideredForIt
        );


        // Set investments
        if (poiData.poiItems && Array.isArray(poiData.poiItems)) {
          const formattedInvestments = poiData.poiItems.map(item => ({
            id: item.id,
            poiItemId: item.id,
            declarationType: item.investmentType || "Investment",
            declaredAmount: item.declaredAmount || 0,
            approvedAmount: item.approvedAmount || 0,
            actualAmount: item.actualAmount || 0,
            status: mapStatus(item.status),
            proofFile: item.documents && item.documents.length > 0 ? item.documents[0].documentName : "No document",
            documents: item.documents || [],
            comments: item.comments || [],
            adminComment: item.adminComment,
            // Additional fields if available
            period: item.period,
            address: item.address,
            policyNo: item.policyNo,
            section: item.sectionName,
            category: item.investmentType
          }));
          setInvestments(formattedInvestments);
        }
      }
    } catch (error) {
      console.error("Error fetching employee POI:", error);
      const msg = getApiErrorMessage(
        error,
        `No Proof of Investment found for FY ${fiscalYear - 1}-${fiscalYear}`
      );

      errorMsg(
        "Investment Data Not Found",
        msg,
        true
      );

      setEmployeeData(null);
    } finally {
      setLoading(false);
    }
  };

  // Fetch comments for a specific POI item
  const fetchComments = async (poiItemId) => {
    try {
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}/items/${poiItemId}/comments`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId
          }
        }
      );

      if (response.data && response.data.data) {
        return response.data.data;
      }
      return [];
    } catch (error) {
      console.error("Error fetching comments:", error);
      errorMsg("Error", "Failed to load comments", false);
      return [];
    }
  };

  useEffect(() => {
    if (id && fiscalYear) {
      fetchEmployeePOI();
    }
  }, [id, fiscalYear]);


  // Helper functions
  const formatDate = (dateString) => {
    if (!dateString) return "N/A";
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      });
    } catch (error) {
      return "Invalid date";
    }
  };

  const formatDateTime = (dateString) => {
    if (!dateString) return "N/A";
    try {
      const date = new Date(dateString);
      return date.toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
    } catch (error) {
      return "Invalid date";
    }
  };

  const mapStatus = (backendStatus) => {
    switch (backendStatus) {
      case "APPROVED":
        return "Approved";
      case "REJECTED":
        return "Rejected";
      case "SUBMITTED":
        return "Pending";
      case "DRAFT":
        return "Draft";
      case "APPROVAL_PENDING":
        return "Pending";
      default:
        return backendStatus || "Pending";
    }
  };

  // Handle back navigation
  const handleBack = () => {
    navigate("/proof-of-investment");
  };

  // Handle amount edit
  const handleEditAmount = (investment) => {
    setEditAmountId(investment.id);
    setEditAmountValue(investment.approvedAmount.toString());
  };

  // Handle save amount
  const handleSaveAmount = async (investmentId) => {
    const amount = parseFloat(editAmountValue);
    if (isNaN(amount) || amount <= 0) {
      errorMsg(
        "Invalid Approved Amount",
        "Approved amount must be a valid number greater than zero.",
        false
      );
      return;
    }

    try {
      const investment = investments.find(inv => inv.id === investmentId);
      if (amount > investment.actualAmount) {
        errorMsg(
          "Invalid Approved Amount",
          "Approved amount cannot be greater than the actual amount submitted by the employee.",
          false
        );
        return;
      }

      const response = await axios.post(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}/items/${investmentId}/approve`,
        {
          approvedAmount: amount,
          adminComment: `Amount updated to ${amount}`
        },
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
            'Content-Type': 'application/json'
          }
        }
      );

      if (response.data && response.data.status === 200) {
        setInvestments(prev => prev.map(inv =>
          inv.id === investmentId
            ? { ...inv, approvedAmount: amount }
            : inv
        ));
        setEditAmountId(null);
        successMsg("Success", "Amount updated successfully", false);
      }
    } catch (error) {
      console.error("Error updating amount:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to update amount", false);
    }
  };

  // Handle remove amount
  const handleRemoveAmount = async (investmentId) => {
    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}/items/${investmentId}/reject`,
        {
          adminComment: "Amount removed by admin"
        },
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
            'Content-Type': 'application/json'
          }
        }
      );

      if (response.data && response.data.status === 200) {
        setInvestments(prev => prev.map(inv =>
          inv.id === investmentId
            ? { ...inv, approvedAmount: 0, status: "Rejected" }
            : inv
        ));
        successMsg("Success", "Amount removed", false);
      }
    } catch (error) {
      console.error("Error removing amount:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to remove amount", false);
    }
  };

  // Handle approve investment
  const handleApprove = async (investmentId) => {
    const investment = investments.find(inv => inv.id === investmentId);

    // 🔴 VALIDATION BEFORE APPROVE
    if (!investment.approvedAmount || investment.approvedAmount <= 0) {
      errorMsg(
        "Approval Not Allowed Please save the Approved Amount before approving this investment.",
        false
      );
      return;
    }

    Modal.confirm({
      title: `Approve Investment Proof`,
      content:
        "Approved amount is already saved. Do you want to proceed with approval?",
      okText: "Approve",
      okType: "primary",
      cancelText: "Cancel",
      onOk: async () => {
        try {
          const response = await axios.post(
            `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}/items/${investmentId}/approve`,
            {
              approvedAmount: investment.approvedAmount,
              adminComment: "Approved after saving approved amount"
            },
            {
              headers: {
                Authorization: `Bearer ${token}`,
                organizationId,
                "Content-Type": "application/json"
              }
            }
          );

          if (response.data?.status === 200) {
            setInvestments(prev =>
              prev.map(inv =>
                inv.id === investmentId
                  ? { ...inv, status: "Approved" }
                  : inv
              )
            );

            successMsg(
              "Investment Approved",
              "The investment has been approved successfully.",
              false
            );
          }
        } catch (error) {
          errorMsg(
            "Approval Failed",
            getApiErrorMessage(error, "Unable to approve the investment."),
            false
          );
        }
      }
    });
  };


  // Handle reject investment
  const handleReject = async (investmentId) => {
    const reason = rejectModal.reason;
    if (!reason.trim()) {
      errorMsg(
        "Rejection Reason Required",
        "Please enter a clear reason so the employee understands why it was rejected.",
        false
      );
      return;
    }

    const employeeName = employeeData?.name || "Employee";

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}/items/${investmentId}/reject`,
        {
          adminComment: reason
        },
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
            'Content-Type': 'application/json'
          }
        }
      );

      if (response.data && response.data.status === 200) {
        setInvestments(prev => prev.map(inv =>
          inv.id === investmentId
            ? { ...inv, status: "Rejected" }
            : inv
        ));

        successMsg("Success", "Investment rejected successfully", false);
        setRejectModal({ visible: false, investmentId: null, reason: "" });
      }
    } catch (error) {
      console.error("Error rejecting investment:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to reject investment", false);
    }
  };

  // Handle approve all
  const handleApproveAll = async () => {
    const employeeName = employeeData?.name || "Employee";

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}/final-approve`,

        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
            'Content-Type': 'application/json'
          }
        }
      );

      if (response.data && response.data.status === 200) {
        setInvestments(prev => prev.map(inv =>
          ({ ...inv, status: "Approved" })
        ));

        if (employeeData) {
          setEmployeeData({
            ...employeeData,
            status: "Approved",
            hasPending: false
          });
        }

        setApproveAllModal(false);
        successMsg("Success", `All investments approved for ${employeeName}`, false);
      }
    } catch (error) {
      console.error("Error approving all investments:", error);
      errorMsg(
        "Approve  Failed",
        getApiErrorMessage(
          error,
          "Some investments are still pending approved amounts. Please review them first."
        ),
        false
      );
    }
  };

  // ======================= CONSIDER POI FOR IT =======================
  const handleConsiderForIT = async () => {
    try {
      const employeeId = employeeData?.employeeId || id;

      const response = await axios.post(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${id}/${fiscalYear}/consider-for-it`,
        {},
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data && response.data.status === 200) {
        successMsg(
          "Success",
          response.data.message || "POI considered for IT calculation successfully",
          false
        );

        // OPTIONAL: update local state if needed
        setEmployeeData((prev) => ({
          ...prev,
          consideredForIt: true,
        }));
      }
    } catch (error) {
      console.error("Error considering POI for IT:", error);

      const msg =
        error?.response?.data?.message ||
        "Failed to consider POI for IT calculation";

      errorMsg("Error", msg, false);
    }
  };

  // ======================= CONSIDER POI FOR IT =======================
  // const handleConsiderForIT = async () => {
  //   try {
  //     const empId = employeeData?.employeeId || id;

  //     // 1️⃣ Consider POI for IT
  //     const response = await axios.post(
  //       `${GlobalConst.API_URL}/api/admin/proof-of-investments/${id}/${fiscalYear}/consider-for-it`,
  //       {},
  //       {
  //         headers: {
  //           Authorization: `Bearer ${token}`,
  //           organizationId: organizationId,
  //         },
  //       }
  //     );

  //     if (response.data && response.data.status === 200) {
  //       // 2️⃣ Recalculate OLD regime tax using POI (CORRECT API)
  //       await axios.post(
  //         `${GlobalConst.API_URL}/tax/calculate/old/poi/calculate/${empId}/${fiscalYear}`,
  //         {},
  //         {
  //           headers: {
  //             Authorization: `Bearer ${token}`,
  //             organizationId: organizationId,
  //           },
  //         }
  //       );

  //       successMsg(
  //         "Success",
  //         "POI considered and old tax recalculated using POI successfully",
  //         false
  //       );

  //       setEmployeeData((prev) => ({
  //         ...prev,
  //         consideredForIt: true,
  //       }));
  //     }
  //   } catch (error) {
  //     console.error("Error considering POI for IT:", error);

  //     const msg =
  //       error?.response?.data?.message ||
  //       "Failed to consider POI for IT calculation";

  //     errorMsg("Error", msg, false);
  //   }
  // };






  // Handle view comments
  const handleViewComments = async (investment) => {
    try {
      const comments = await fetchComments(investment.poiItemId || investment.id);

      const formattedComments = comments.map(comment => ({
        id: comment.id,
        user: comment.commentedByAdminName ||
          (comment.commentedByEmployee ?
            `${comment.commentedByEmployee.firstName || ''} ${comment.commentedByEmployee.lastName || ''}`.trim()
            : "Employee") ||
          comment.commentedBy ||
          "Unknown",
        text: comment.comment,
        timestamp: formatDateTime(comment.createdTime),
        isAdmin: !!comment.commentedByAdminName || !!comment.commentedByAdmin
      }));

      setCommentsModal({
        visible: true,
        investmentId: investment.id,
        comments: formattedComments
      });
    } catch (error) {
      console.error("Error fetching comments:", error);
      errorMsg("Error", "Failed to load comments", false);
    }
  };

  // Handle add comment
  const handleAddComment = async () => {
    if (!newComment.trim()) {
      errorMsg("Error", "Please enter a comment", false);
      return;
    }

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/admin/proof-of-investments/${organizationId}/${id}/${fiscalYear}/items/${commentsModal.investmentId}/comments`,
        {
          comment: newComment
        },
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
            'Content-Type': 'application/json'
          }
        }
      );

      if (response.data && response.data.status === 200) {
        const newCommentObj = response.data.data;

        const formattedComment = {
          id: newCommentObj.id,
          user: "Admin", // Current user - should come from auth context
          text: newComment,
          timestamp: formatDateTime(newCommentObj.createdTime),
          isAdmin: true
        };

        const updatedComments = [...commentsModal.comments, formattedComment];

        setCommentsModal(prev => ({
          ...prev,
          comments: updatedComments
        }));

        // Clear the comment input
        setNewComment("");

        // Update the investment's comments in local state
        setInvestments(prev => prev.map(inv =>
          inv.id === commentsModal.investmentId
            ? { ...inv, comments: updatedComments }
            : inv
        ));

        successMsg("Success", "Comment added successfully", false);
      }
    } catch (error) {
      console.error("Error adding comment:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to add comment", false);
    }
  };

  // Handle preview document
  const handlePreview = (doc) => {
    if (doc.documentUrl) {
      // Open in new tab for preview
      window.open(doc.documentUrl, '_blank');
    } else {
      // Show placeholder modal if no URL
      setPreviewDoc({ visible: true, doc });
    }
  };

  // Handle download document
  const handleDownload = async (doc) => {
    try {
      if (doc.documentUrl) {
        // Create a temporary anchor element for download
        const link = document.createElement('a');
        link.href = doc.documentUrl;
        link.download = doc.documentName || 'document.pdf';

        // Add to DOM, click, and remove
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        successMsg("Success", `Downloading ${doc.documentName || 'document'}`, false);
      } else {
        errorMsg("Error", "No document URL available", false);
      }
    } catch (error) {
      console.error("Error downloading document:", error);
      errorMsg("Error", "Failed to download document", false);
    }
  };

  // Status badge
  const getStatusBadge = (status) => {
    let badgeClass = "";
    let badgeText = status;

    switch (status) {
      case "Approved":
        badgeClass = "badge-light-success";
        break;
      case "Partially Approved":
        badgeClass = "badge-light-warning";
        break;
      case "Rejected":
        badgeClass = "badge-light-danger";
        break;
      case "Pending":
        badgeClass = "badge-light-warning";
        badgeText = "Pending";
        break;
      case "Draft":
        badgeClass = "badge-light-secondary";
        break;
      default:
        badgeClass = "badge-light-primary";
    }

    return <span className={`badge ${badgeClass}`}>{badgeText}</span>;
  };

  // Consider for IT dropdown menu
  const considerForITMenu = (
    <Menu>
      <Menu.Item
        key="approveAll"
        disabled={isApprovedAll}
        onClick={handleApproveAllConfirm}
      >
        <CheckCircleOutlined className="me-2" />
        Approve
      </Menu.Item>

      <Menu.Item
        key="considerForIT"
        disabled={!isApprovedAll || isConsideredForIT}
        onClick={handleConsiderForITConfirm}
      >
        <FileTextOutlined className="me-2" />
        Consider for IT
      </Menu.Item>
    </Menu>
  );



  if (loading) {
    return (
      <div className="container-fluid p-0 bg-white">
        <div className="p-6 text-center">
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading...</span>
          </div>
          <p className="mt-3">Loading investment proofs...</p>
        </div>
      </div>
    );
  }

  if (!employeeData) {
    return (
      <div className="container-fluid p-0 bg-white">
        <div className="p-6 text-center">
          <p className="text-danger">Employee data not found</p>
          <Button type="primary" onClick={handleBack}>
            Go Back
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      <Helmet>
        <title>{employeeData.name}'s Investment Proofs | Approval</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white">
        <div className="p-6">
          {/* Header with Back Button */}
          <div className="d-flex align-items-center mb-4">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={handleBack}
              className="me-3"
            >
              Back
            </Button>
            <div className="flex-grow-1">
              <div className="d-flex align-items-center">
                <div className="symbol symbol-50px symbol-circle me-3">
                  <span className="symbol-label bg-primary text-white fw-bold fs-3">
                    {employeeData.firstName?.charAt(0) || employeeData.name?.charAt(0) || "E"}
                  </span>
                </div>
                <div>
                  <h2 className="fw-bolder mb-0">{employeeData.name}'s Investment Proofs</h2>
                  <div className="text-muted">
                    Employee ID: {employeeData.employeeNumber}
                  </div>
                </div>
              </div>
            </div>
            <div className="d-flex align-items-center gap-2">
              <span
                className={`badge ${employeeData.hasPending
                  ? "badge-light-warning fs-7"
                  : "badge-light-success fs-7"
                  }`}
              >
                {employeeData.status}
              </span>

              {/* Approve All Button */}
              <Tooltip
                title={
                  employeeData?.consideredForIt
                    ? "This declaration is already considered for IT calculation. Approval is locked."
                    : isApprovedAll
                      ? "Investment proofs are already approved."
                      : "Approve investment proofs before considering the declaration for IT calculation."
                }
              >

                <Button
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  onClick={handleApproveAllConfirm}
                  disabled={isApprovedAll || employeeData?.consideredForIt}
                >
                  Approve
                </Button>

              </Tooltip>

              {/* Consider for IT Button */}
              <Tooltip
                title={
                  isConsideredForIT
                    ? "This declaration has already been considered for IT calculation."
                    : !isApprovedAll
                      ? "Please approve investment proofs before considering this declaration for IT calculation."
                      : "Consider this declaration for TDS calculation for the upcoming financial year."
                }
              >
                <Button
                  type="primary"
                  icon={<FileTextOutlined />}
                  onClick={handleConsiderForITConfirm}
                  disabled={
                    !isApprovedAll ||
                    isConsideredForIT ||
                    employeeData?.consideredForIt
                  }
                >
                  Consider for IT
                </Button>

              </Tooltip>
            </div>

          </div>

          {/* Information Row */}
          <div className="row mb-6 g-4">
            <div className="col-lg-3 col-md-6">
              <div className="card h-100 border-light shadow-sm">
                <div className="card-body">
                  <div className="d-flex align-items-center mb-2">
                    <CalendarOutlined className="fs-5 text-primary me-3" />
                    <div>
                      <div className="text-muted small mb-1">Submitted Date</div>
                      <div className="fw-bold">{employeeData.submittedDate}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className="col-lg-3 col-md-6">
              <div className="card h-100 border-light shadow-sm">
                <div className="card-body">
                  <div className="d-flex align-items-center mb-2">
                    <UserOutlined className="fs-5 text-primary me-3" />
                    <div>
                      <div className="text-muted small mb-1">Submitted By</div>
                      <div className="fw-bold">{employeeData.submittedBy}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className="col-lg-3 col-md-6">
              <div className="card h-100 border-light shadow-sm">
                <div className="card-body">
                  <div className="d-flex align-items-center mb-2">
                    <UserOutlined className="fs-5 text-primary me-3" />
                    <div>
                      <div className="text-muted small mb-1">Last modified By</div>
                      <div className="fw-bold">{employeeData.lastEditedBy}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div className="col-lg-3 col-md-6">
              <div className="card h-100 border-light shadow-sm">
                <div className="card-body">
                  <div className="d-flex align-items-center mb-2">
                    <CalendarOutlined className="fs-5 text-primary me-3" />
                    <div>
                      <div className="text-muted small mb-1">Last modified Date</div>
                      <div className="fw-bold">{employeeData.lastEditedDate}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Tax Regime Info */}
          <div className="card mb-6 border-light shadow-sm">
            <div className="card-body bg-light-primary rounded">
              <div className="d-flex align-items-center">
                <InfoCircleOutlined className="fs-5 text-primary me-3" />
                <div>
                  <span className="fw-bold fs-6">Tax Regime: </span>
                  <span className="fw-semibold">{employeeData.taxRegimeFormatted}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Investments Table */}
          <div className="card border-0 shadow-sm">
            <div className="card-header bg-white border-bottom py-4">
              <h5 className="card-title mb-0 fw-bold text-gray-800">
                Investment Details
                <span className="text-muted fs-7 ms-2">({investments.length} items)</span>
              </h5>
            </div>
            <div className="card-body p-0">
              <div className="table-responsive">
                <table className="table table-hover align-middle mb-0">
                  <thead className="bg-light">
                    <tr className="text-muted small">
                      <th className="ps-6 py-3 border-bottom" style={{ width: '25%' }}>
                        <span className="fw-semibold">DECLARATION TYPE</span>
                      </th>
                      <th className="py-3 border-bottom" style={{ width: '10%' }}>
                        <span className="fw-semibold">ACTUAL AMOUNT</span>
                      </th>
                      <th className="py-3 border-bottom" style={{ width: '10%' }}>
                        <span className="fw-semibold">PROOF</span>
                      </th>
                      <th className="py-3 border-bottom" style={{ width: '10%' }}>
                        <span className="fw-semibold">COMMENTS</span>
                      </th>
                      <th className="py-3 border-bottom" style={{ width: '15%' }}>
                        <span className="fw-semibold">APPROVED AMOUNT</span>
                      </th>
                      <th className="py-3 border-bottom" style={{ width: '10%' }}>
                        <span className="fw-semibold">STATUS</span>
                      </th>
                      <th className="text-end pe-6 py-3 border-bottom" style={{ width: '20%' }}>
                        <span className="fw-semibold">ACTIONS</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {investments.length === 0 ? (
                      <tr>
                        <td colSpan={7} className="text-center text-muted py-5">
                          <div className="py-4">
                            <FileTextOutlined className="fs-1 text-muted mb-3" />
                            <div className="fw-semibold">No investment proofs found</div>
                            <div className="text-muted small">This employee hasn't submitted any investment proofs yet.</div>
                          </div>
                        </td>
                      </tr>
                    ) : (
                      investments.map((investment) => (
                        <tr key={investment.id} className="border-bottom hover-row">
                          {/* Declaration Type */}
                          <td className="ps-6 py-4">
                            <div className="fw-semibold text-gray-800 mb-1">{investment.declarationType}</div>
                            {investment.declaredAmount > 0 && (
                              <div className="text-muted small mb-1">
                                <span className="fw-medium">Declared:</span> ₹{investment.declaredAmount.toLocaleString('en-IN')}
                              </div>
                            )}
                            {investment.approvedAmount > 0 && (
                              <div className="text-muted small mb-1">
                                <span className="fw-medium text-success">Approved:</span> ₹{investment.approvedAmount.toLocaleString('en-IN')}
                              </div>
                            )}
                            {investment.actualAmount > 0 && (
                              <div className="text-muted small mb-1">
                                <span className="fw-medium">Actual:</span> ₹{investment.actualAmount.toLocaleString('en-IN')}
                              </div>
                            )}
                            {investment.period && (
                              <div className="text-muted small"><CalendarOutlined className="me-1" /> {investment.period}</div>
                            )}
                            {investment.policyNo && (
                              <div className="text-muted small"><FileTextOutlined className="me-1" /> Policy: {investment.policyNo}</div>
                            )}
                            {investment.address && (
                              <div className="text-muted small"><InfoCircleOutlined className="me-1" /> {investment.address}</div>
                            )}
                            {investment.adminComment && (
                              <div className="text-muted small mt-2">
                                <div className="fw-medium">Admin Note:</div>
                                <div className="bg-light-warning rounded p-2 mt-1">{investment.adminComment}</div>
                              </div>
                            )}
                          </td>

                          {/* Actual Amount */}
                          <td className="py-4">
                            <div className="fw-bold text-gray-800 fs-5">
                              ₹{investment.actualAmount.toLocaleString('en-IN')}
                            </div>
                          </td>

                          {/* Proof */}
                          <td className="py-4">
                            {investment.documents && investment.documents.length > 0 ? (
                              <div className="d-flex gap-2">
                                <Tooltip title="View Document">
                                  <Button
                                    type="text"
                                    shape="circle"
                                    icon={<EyeOutlined />}
                                    onClick={() => handlePreview(investment.documents[0])}
                                    className="btn-icon btn-light-primary"
                                    size="small"
                                  />
                                </Tooltip>
                                <Tooltip title="Download Document">
                                  <Button
                                    type="text"
                                    shape="circle"
                                    icon={<DownloadOutlined />}
                                    onClick={() => handleDownload(investment.documents[0])}
                                    className="btn-icon btn-light-success"
                                    size="small"
                                  />
                                </Tooltip>
                                <span className="badge badge-light-primary align-self-center">
                                  {investment.documents.length}
                                </span>
                              </div>
                            ) : (
                              <span className="text-muted small">No document</span>
                            )}
                          </td>

                          {/* Comments */}
                          <td className="py-4">
                            <Button
                              type="text"
                              shape="circle"
                              icon={<CommentOutlined />}
                              onClick={() => handleViewComments(investment)}
                              className={`btn-icon ${investment.comments?.length > 0 ? 'btn-light-warning' : 'btn-light-secondary'}`}
                              size="small"
                            >
                              {investment.comments?.length > 0 && (
                                <span className="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">
                                  {investment.comments.length}
                                </span>
                              )}
                            </Button>
                          </td>

                          {/* Approved Amount */}
                          <td className="py-4">
                            <div className="d-flex align-items-center gap-2">
                              {editAmountId === investment.id ? (
                                <>
                                  <div className="input-group input-group-sm">
                                    <span className="input-group-text">₹</span>
                                    <Input
                                      value={editAmountValue}
                                      onChange={(e) => setEditAmountValue(e.target.value)}
                                      style={{ width: '100px' }}
                                      size="small"
                                    />
                                  </div>

                                  {/* SAVE ICON */}
                                  <Tooltip title="Save">
                                    <Button
                                      type="text"
                                      shape="circle"
                                      icon={<SaveOutlined style={{ fontSize: '18px' }} />}
                                      onClick={() => handleSaveAmount(investment.id)}
                                      className="btn-icon btn-light-success"
                                      size="small"
                                    />
                                  </Tooltip>

                                  {/* DELETE ICON */}
                                  <Tooltip title="Delete">
                                    <Button
                                      type="text"
                                      shape="circle"
                                      danger
                                      icon={<DeleteOutlined style={{ fontSize: '18px' }} />}
                                      onClick={() => {
                                        setEditAmountId(null);
                                        handleRemoveAmount(investment.id);
                                      }}
                                      className="btn-icon"
                                      size="small"
                                    />
                                  </Tooltip>
                                </>
                              ) : (
                                <>
                                  <div className="fw-bold text-gray-800 fs-5">
                                    ₹{investment.approvedAmount.toLocaleString('en-IN')}
                                  </div>

                                  {/* EDIT ICON */}
                                  <Tooltip title="Edit">
                                    <Button
                                      type="text"
                                      shape="circle"
                                      icon={<EditOutlined style={{ fontSize: '18px' }} />}
                                      onClick={() => handleEditAmount(investment)}
                                      className="btn-icon btn-light-primary"
                                      size="small"
                                    />
                                  </Tooltip>
                                </>
                              )}
                            </div>
                          </td>


                          {/* Status */}
                          <td className="py-4">
                            {getStatusBadge(investment.status)}
                          </td>

                          {/* Actions */}
                          <td className="text-end pe-6 py-4">
                            <div className="d-flex gap-2 justify-content-end">
                              <Button
                                type="primary"
                                icon={<CheckCircleOutlined />}
                                onClick={() => handleApprove(investment.id)}
                                disabled={investment.status === "Approved"}
                                className="btn-sm"
                              >
                                Approve
                              </Button>
                              <Button
                                danger
                                icon={<CloseCircleOutlined />}
                                onClick={() => setRejectModal({
                                  visible: true,
                                  investmentId: investment.id,
                                  reason: ""
                                })}
                                disabled={investment.status === "Rejected"}
                                className="btn-sm"
                              >
                                Reject
                              </Button>
                            </div>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Comments Modal */}
      <Modal
        title={
          <div className="d-flex align-items-center">
            <CommentOutlined className="me-2 text-primary" />
            <span>Comments</span>
            <span className="badge bg-primary ms-2">{commentsModal.comments.length}</span>
          </div>
        }
        visible={commentsModal.visible}
        onCancel={() => setCommentsModal({ visible: false, investmentId: null, comments: [] })}
        footer={null}
        width={600}
        className="modal-dialog-scrollable"
      >
        <div className="mb-4">
          <div className="border rounded p-3 bg-light" style={{ maxHeight: '300px', overflowY: 'auto' }}>
            {commentsModal.comments.length === 0 ? (
              <div className="text-center text-muted py-4">
                <CommentOutlined className="fs-1 text-muted mb-3" />
                <div className="fw-semibold">No comments yet</div>
                <div className="text-muted small">Be the first to add a comment</div>
              </div>
            ) : (
              commentsModal.comments.map((comment) => (
                <div key={comment.id} className="mb-3 pb-3 border-bottom">
                  <div className="d-flex justify-content-between align-items-start mb-2">
                    <div className="d-flex align-items-center">
                      <span className={`fw-bold ${comment.isAdmin ? 'text-primary' : 'text-success'}`}>
                        {comment.user}
                      </span>
                      {comment.isAdmin && (
                        <span className="badge bg-primary ms-2">Admin</span>
                      )}
                    </div>
                    <span className="text-muted small">{comment.timestamp}</span>
                  </div>
                  <div className="p-2 rounded bg-white">{comment.text}</div>
                </div>
              ))
            )}
          </div>
        </div>

        <div>
          <TextArea
            rows={3}
            placeholder="Add your comment..."
            value={newComment}
            onChange={(e) => setNewComment(e.target.value)}
            className="mb-3"
          />
          <div className="d-flex gap-2">
            <Button type="primary" onClick={handleAddComment} className="flex-grow-1">
              <CommentOutlined className="me-2" />
              Add Comment
            </Button>
            <Button onClick={() => {
              setNewComment("");
              setCommentsModal({ visible: false, investmentId: null, comments: [] });
            }}>
              Cancel
            </Button>
          </div>
        </div>
      </Modal>

      {/* Approve All Modal */}
      <Modal
        title={
          <div className="d-flex align-items-center">
            <CheckCircleOutlined className="me-2 text-success" />
            <span>Approve Investments</span>
          </div>
        }
        visible={approveAllModal}
        onCancel={() => setApproveAllModal(false)}
        footer={[
          <Button key="cancel" onClick={() => setApproveAllModal(false)}>
            Cancel
          </Button>,
          <Button key="approve" type="primary" onClick={handleApproveAll}>
            <CheckCircleOutlined className="me-2" />
            Approve
          </Button>
        ]}
      >
        <div className="text-center mb-4">
          <div className="symbol symbol-80px symbol-circle mb-3">
            <span className="symbol-label bg-light-success">
              <CheckCircleOutlined className="text-success fs-1" />
            </span>
          </div>
          <h5 className="fw-bold">Approve Investments</h5>
          <p className="text-muted">
            Are you sure you want to approve investment proofs for <span className="fw-bold">{employeeData.name}</span>?
          </p>
        </div>
        <div className="alert alert-warning border-warning">
          <InfoCircleOutlined className="me-2" />
          <span className="fw-medium">Note:</span> You have one or more investment proofs pending review.
          Approve or reject them to proceed.
        </div>
      </Modal>

      {/* Reject Modal */}
      <Modal
        title={
          <div className="d-flex align-items-center">
            <span>Reject Investment Proof</span>
          </div>
        }
        visible={rejectModal.visible}
        onCancel={() => setRejectModal({ visible: false, investmentId: null, reason: "" })}
        footer={[
          <Button key="cancel" onClick={() => setRejectModal({ visible: false, investmentId: null, reason: "" })}>
            Cancel
          </Button>,
          <Button key="reject" danger onClick={() => handleReject(rejectModal.investmentId)}>
            <CloseCircleOutlined className="me-2" />
            Reject
          </Button>
        ]}
      >
        <div className="mb-4">
          {/* <div className="d-flex align-items-center mb-3">
            <div className="symbol symbol-40px symbol-circle me-3">
              <span className="symbol-label bg-light-danger">
                <CloseCircleOutlined className="text-danger" />
              </span>
            </div>
            <div>
              <div className="text-muted small">For: {employeeData.name}</div>
            </div>
          </div> */}
          <p className="mb-3">
            Are you sure you want to reject this investment proof for <span className="fw-bold">{employeeData.name}</span>?
          </p>
        </div>
        <div className="mb-3">
          <label className="form-label fw-medium">Reason for Rejection</label>
          <TextArea
            rows={3}
            placeholder="Please provide reason for rejection..."
            value={rejectModal.reason}
            onChange={(e) => setRejectModal(prev => ({ ...prev, reason: e.target.value }))}
            className="form-control"
          />
          <div className="text-muted small mt-1">This reason will be visible to the employee.</div>
        </div>
      </Modal>

      {/* Document Preview Modal */}
      <Modal
        title={
          <div className="d-flex align-items-center">
            <FileTextOutlined className="me-2 text-primary" />
            <span>Document Preview</span>
          </div>
        }
        visible={previewDoc.visible}
        onCancel={() => setPreviewDoc({ visible: false, doc: null })}
        footer={[
          <Button key="close" onClick={() => setPreviewDoc({ visible: false, doc: null })}>
            Close
          </Button>
        ]}
        width={800}
      >
        <div className="text-center p-4">
          <div className="mb-3">
            <FileTextOutlined className="text-primary fs-1" style={{ fontSize: '64px' }} />
          </div>
          <h5>{previewDoc.doc?.name}</h5>
          <p className="text-muted">Preview for {previewDoc.doc?.fileName}</p>
          <p className="text-muted small">
            In a real implementation, this would show the actual document preview (PDF, image, etc.)
          </p>
          {previewDoc.doc?.documentUrl && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => handleDownload(previewDoc.doc)}
              className="mt-3"
            >
              Download Document
            </Button>
          )}
        </div>
      </Modal>
    </>
  );
}