import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { getDecodedToken } from "../../../../shared/helpers/tokenHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { successMsg, errorMsg } from "../../../../shared/helpers/msgHelper";
import { Modal } from "antd";
import { ExclamationCircleOutlined } from "@ant-design/icons";



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

// const getDeclarationFinancialYear = () => {
//     const today = new Date();
//     const month = today.getMonth() + 1;
//     const year = today.getFullYear();

//     const currentFY = month >= 4 ? year + 1 : year;
//     return currentFY + 1; // UPCOMING financial year
// };



export default function UserInvestmentProof() {
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [poiData, setPoiData] = useState(null);
    const [employeeId, setEmployeeId] = useState("");
    const [fiscalYear, setFiscalYear] = useState(getCurrentFinancialYear());

    // const [fiscalYear, setFiscalYear] = useState(getDeclarationFinancialYear());


    const [activeAttachmentModal, setActiveAttachmentModal] = useState(null);
    const [activeCommentModal, setActiveCommentModal] = useState(null);
    const [commentText, setCommentText] = useState("");
    const [uploadingFile, setUploadingFile] = useState(null);
    const [uploadProgress, setUploadProgress] = useState(0);
    const [actualAmounts, setActualAmounts] = useState({});
    const [comments, setComments] = useState({}); // Store comments for each item
    const [showPoiLockedModal, setShowPoiLockedModal] = useState(false);
    const [showTaxRegimeLockedModal, setShowTaxRegimeLockedModal] = useState(false);
    const navigate = useNavigate();

     console.log(getCurrentFinancialYear());


    const canEditPoiItem = (item) => {
        if (!poiData) return false;

        // Draft → all editable
        if (poiData.status === "DRAFT") return true;

        // Submitted → only rejected items editable
        if (poiData.status === "SUBMITTED" && item.status === "REJECTED") {
            return true;
        }

        return false;
    };

    // Initialize employee ID from token
    useEffect(() => {
        const decodedToken = getDecodedToken();
        if (decodedToken?.sub) {
            const empId = decodedToken.sub;
            setEmployeeId(empId);
            fetchPoiData(empId);
        }
    }, []);

    // Fetch POI data from API
    const fetchPoiData = async (empId) => {
        try {
            setIsLoading(true);
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/proof-of-investments/${empId}/${fiscalYear}`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id"
                    }
                }
            );

            if (response.data && response.data.data) {
                const data = response.data.data;
                setPoiData(data);

                // Initialize actual amounts from data
                const amounts = {};
                // Initialize comments from data

                if (data.poiItems && data.poiItems.length > 0) {
                    // Fetch comments for each POI item
                    for (const item of data.poiItems) {
                        amounts[item.id] = item.actualAmount || 0;
                    }
                }
                setActualAmounts(amounts);
            }
        } catch (error) {
            console.error("Error fetching POI data:", error);
            // If no POI exists, we'll create one from the declaration
            if (error.response?.status === 404) {
                initializePoiFromDeclaration(empId);
            }
        } finally {
            setIsLoading(false);
        }
    };

    // Initialize POI from declaration
    const initializePoiFromDeclaration = async (empId) => {
        try {
            setIsLoading(true);
            const response = await axios.post(
                `${GlobalConst.API_URL}/api/proof-of-investments/${empId}/${fiscalYear}/initialize`,
                {},
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id"
                    }
                }
            );

            if (response.data && response.data.data) {
                setPoiData(response.data.data);

                // Initialize actual amounts from data
                const amounts = {};
                // Initialize comments from data
                const commentsData = {};

                if (response.data.data.poiItems && response.data.data.poiItems.length > 0) {
                    for (const item of response.data.data.poiItems) {
                        amounts[item.id] = item.actualAmount || 0;
                        commentsData[item.id] = [];
                    }
                }
                setActualAmounts(amounts);
                setComments(commentsData);
                successMsg("Success", "Proof of Investment initialized successfully", true);
            }
        } catch (error) {
            console.error("Error initializing POI:", error);
            errorMsg("Error", "Failed to initialize Proof of Investment. Please try again.", true);
        } finally {
            setIsLoading(false);
        }
    };

    // Format currency
    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    // Format month display
    const formatMonth = (monthString) => {
        if (!monthString) return '';
        const [year, month] = monthString.split('-');
        const date = new Date(year, month - 1);
        return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
    };

    // Get investment type display name
    const getInvestmentTypeDisplay = (investmentType) => {
        const typeMap = {
            'epf': 'Employee Provident Fund (Benefit)',
            'nsc': 'National Savings Certificates',
            'medi_claim_self': 'Medi Claim Policy for self, spouse, children - 80D',
            'medi_claim_self_senior': 'Medi Claim Policy for self, spouse, children for senior citizen - 80D',
            'medi_claim_parents': 'Medi Claim Policy for parents - 80D',
            'medi_claim_parents_senior': 'Medi Claim Policy for parents for senior citizen - 80D',
            'voluntary_nps': 'Additional exemption on voluntary NPS',
            'HRA': 'House Rent Allowance',
            'OTHER_INCOME_other_income': 'Income from other sources',
            'OTHER_INCOME_savings_interest': 'Interest from Savings Account',
            'OTHER_INCOME_fd_interest': 'Interest from Fixed Deposit',
            'OTHER_INCOME_nsc_interest': 'Interest from National Savings Certificates',
            'LET_OUT_PROPERTY': 'Let Out Property',
            'PREV_EMPLOYMENT_income': 'Income After Exemptions',
            'PREV_EMPLOYMENT_income_tax': 'Income Tax',
            'PREV_EMPLOYMENT_professional_tax': 'Professional Tax',
            'PREV_EMPLOYMENT_employee_pf': 'Employee Provident Fund',
            'PREV_EMPLOYMENT_leave_encashment': 'Leave Encashment Exemptions'
        };

        return typeMap[investmentType] || investmentType;
    };

    // Get status badge color
    const getStatusBadge = (status) => {
        switch (status) {
            case 'DRAFT':
                return 'badge badge-light-warning';
            case 'SUBMITTED':
                return 'badge badge-light-primary';
            case 'APPROVED':
                return 'badge badge-light-success';
            case 'REJECTED':
                return 'badge badge-light-danger';
            default:
                return 'badge badge-light-secondary';
        }
    };

    // Handle actual amount change
    const handleActualAmountChange = (poiItemId, value) => {
        setActualAmounts(prev => ({
            ...prev,
            [poiItemId]: parseFloat(value) || 0
        }));
    };

    // Update POI item
    const updatePoiItem = async (poiItemId) => {
        try {
            setIsSubmitting(true);

            const actualAmount = actualAmounts[poiItemId];
            const poiItem = poiData.poiItems.find(item => item.id === poiItemId);

            if (!poiItem) {
                errorMsg("Error", "POI item not found", true);
                return;
            }

            // if (actualAmount > poiItem.declaredAmount) {
            //     errorMsg("Error", "Actual amount cannot exceed declared amount", true);
            //     return;
            // }

            const response = await axios.put(
                `${GlobalConst.API_URL}/api/proof-of-investments/${employeeId}/${fiscalYear}/items/${poiItemId}`,
                {
                    actualAmount: actualAmount,
                    investmentType: poiItem.investmentType,
                    section6aItemId: poiItem.section6aItemId
                },
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id",
                        'Content-Type': 'application/json'
                    }
                }
            );

            if (response.data) {
                successMsg("Success", "Actual amount updated successfully", true);
                fetchPoiData(employeeId); // Refresh data
            }
        } catch (error) {
            console.error("Error updating POI item:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to update actual amount", true);
        } finally {
            setIsSubmitting(false);
        }
    };


    const renderLetOutPropertyDetails = (item) => {
        if (!item.propertyDetails || item.propertyDetails.length === 0) return null;

        return (
            <div className="mt-2 ps-3 border-start border-3 border-primary">
                {item.propertyDetails.map((detail, index) => (
                    <div
                        key={index}
                        className="d-flex justify-content-between text-muted fs-7 mb-1"
                    >
                        <span>{detail.typeFormatted}</span>
                        <span className="fw-semibold text-gray-700">
                            {formatCurrency(detail.amount)}
                        </span>
                    </div>
                ))}
            </div>
        );
    };


    // Upload document(s)
    const uploadDocument = async (poiItemId, file) => {
        try {
            setUploadingFile(poiItemId);
            setUploadProgress(0);

            const formData = new FormData();
            formData.append('files', file);

            // Check file type
            const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'application/pdf', 'application/zip'];
            if (!allowedTypes.includes(file.type)) {
                errorMsg("Error", "Allowed file formats: jpg, jpeg, png, pdf, zip", true);
                return;
            }

            // Check file size (10MB limit)
            if (file.size > 10 * 1024 * 1024) {
                errorMsg("Error", "File size should be less than 10MB", true);
                return;
            }

            const response = await axios.post(
                `${GlobalConst.API_URL}/api/proof-of-investments/${employeeId}/${fiscalYear}/items/${poiItemId}/documents`,
                formData,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id",
                        'Content-Type': 'multipart/form-data'
                    },
                    onUploadProgress: (progressEvent) => {
                        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                        setUploadProgress(percentCompleted);
                    }
                }
            );

            if (response.data) {
                successMsg("Success", "Document uploaded successfully", true);
                fetchPoiData(employeeId); // Refresh data
            }
        } catch (error) {
            console.error("Error uploading document:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to upload document", true);
        } finally {
            setUploadingFile(null);
            setUploadProgress(0);
        }
    };

    // Delete document
    const deleteDocument = async (documentId) => {
        try {
            if (!window.confirm("Are you sure you want to delete this document?")) {
                return;
            }

            await axios.delete(
                `${GlobalConst.API_URL}/api/proof-of-investments/documents/${documentId}`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id",
                        employeeId: employeeId
                    }
                }
            );

            successMsg("Success", "Document deleted successfully", true);
            fetchPoiData(employeeId); // Refresh data
        } catch (error) {
            console.error("Error deleting document:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to delete document", true);
        }
    };

    // Fetch comments for a POI item
    const fetchCommentsForItem = async (poiItemId) => {
        if (!employeeId || !fiscalYear || !poiItemId) return;

        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/proof-of-investments/${employeeId}/${fiscalYear}`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId"),
                    },
                }
            );

            const poiItems = response.data?.data?.poiItems || [];

            const currentItem = poiItems.find(item => item.id === poiItemId);

            if (!currentItem || !currentItem.comments) {
                setComments(prev => ({
                    ...prev,
                    [poiItemId]: [],
                }));
                return;
            }

            const formattedComments = currentItem.comments.map(c => ({
                id: c.id,
                comment: c.comment,
                createdBy:
                    c.commentedByEmployeeName ||
                    c.commentedByAdminName ||
                    "You",
                createdAt: c.createdTime,
                createdByEmployee: c.isEmployeeComment === true,
            }));

            setComments(prev => ({
                ...prev,
                [poiItemId]: formattedComments,
            }));
        } catch (error) {
            console.error("Error fetching comments:", error);
        }
    };

    // Add comment
    const addComment = async (poiItemId) => {
        try {
            if (!commentText.trim()) {
                errorMsg("Error", "Please enter a comment", true);
                return;
            }

            // Call API to add comment
            const response = await axios.post(
                `${GlobalConst.API_URL}/api/proof-of-investments/${employeeId}/${fiscalYear}/items/${poiItemId}/comments`,
                {
                    comment: commentText.trim()
                },
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id",
                        'Content-Type': 'application/json'
                    }
                }
            );
            console.log("Employee ID:", employeeId);

            if (response.data) {
                successMsg("Success", "Comment added successfully", true);
                setCommentText("");
                setActiveCommentModal(null);
                // Refresh comments for this item
                await fetchCommentsForItem(poiItemId);
            }
        } catch (error) {
            console.error("Error adding comment:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to add comment", true);
        }
    };

    // Update comment
    const updateComment = async (poiItemId, commentId, updatedText) => {
        try {
            if (!updatedText.trim()) {
                errorMsg("Error", "Comment cannot be empty", true);
                return;
            }

            const response = await axios.put(
                `${GlobalConst.API_URL}/api/proof-of-investments/comments/${commentId}`,
                {
                    comment: updatedText.trim()
                },
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id",
                        employeeId: employeeId,
                        'Content-Type': 'application/json'
                    }
                }
            );

            if (response.data) {
                successMsg("Success", "Comment updated successfully", true);
                // Refresh comments for this item
                await fetchCommentsForItem(poiItemId);
            }
        } catch (error) {
            console.error("Error updating comment:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to update comment", true);
        }
    };

    // Delete comment
    const deleteComment = async (poiItemId, commentId) => {
        try {
            if (!window.confirm("Are you sure you want to delete this comment?")) {
                return;
            }

            await axios.delete(
                `${GlobalConst.API_URL}/api/proof-of-investments/comments/${commentId}`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id",
                        employeeId: employeeId
                    }
                }
            );

            successMsg("Success", "Comment deleted successfully", true);
            // Refresh comments for this item
            await fetchCommentsForItem(poiItemId);
        } catch (error) {
            console.error("Error deleting comment:", error);
            errorMsg("Error", error.response?.data?.message || "Failed to delete comment", true);
        }
    };

    // Submit POI
    const submitPoi = async () => {
        Modal.confirm({
            title: "Submit Proof of Investment",
            icon: <ExclamationCircleOutlined />,
            content:
                "Are you sure you want to submit the Proof of Investment? Once submitted, you cannot make changes unless withdrawn.",
            okText: "Submit",
            cancelText: "Cancel",
            okButtonProps: { loading: isSubmitting },

            onOk: async () => {
                try {
                    setIsSubmitting(true);


                    const missingAmounts = poiData.poiItems.filter(
                        item => !actualAmounts[item.id] || actualAmounts[item.id] <= 0
                    );

                    // if (missingAmounts.length > 0) {
                    //     errorMsg(
                    //         "Validation Error",
                    //         "Please enter Actual Amount for all investment items before submitting.",
                    //         true
                    //     );
                    //     return;
                    // }


                    const response = await axios.post(
                        `${GlobalConst.API_URL}/api/proof-of-investments/${employeeId}/${fiscalYear}/submit`,
                        {},
                        {
                            headers: {
                                Authorization: `Bearer ${localStorage.getItem("__t")}`,
                                organizationId:
                                    localStorage.getItem("organizationId") || "default-org-id",
                                "Content-Type": "application/json",
                            },
                        }
                    );

                    if (response.data) {
                        successMsg(
                            "Success",
                            "Proof of Investment submitted successfully",
                            true
                        );
                        fetchPoiData(employeeId);
                    }
                } catch (error) {
                    console.error("Error submitting POI:", error);
                    errorMsg(
                        "Error",
                        error.response?.data?.message ||
                        "Failed to submit Proof of Investment",
                        true
                    );
                } finally {
                    setIsSubmitting(false);
                }
            },
        });
    };

    // Withdraw POI
    // const withdrawPoi = async () => {
    //     Modal.confirm({
    //         title: "Withdraw Proof of Investment",
    //         icon: <ExclamationCircleOutlined />,
    //         content:
    //             "Are you sure you want to withdraw the Proof of Investment? You can make changes and resubmit.",
    //         okText: "Withdraw",
    //         cancelText: "Cancel",
    //         okButtonProps: { danger: true, loading: isSubmitting },

    //         onOk: async () => {
    //             try {
    //                 setIsSubmitting(true);

    //                 const response = await axios.post(
    //                     `${GlobalConst.API_URL}/api/proof-of-investments/${employeeId}/${fiscalYear}/withdraw`,
    //                     {},
    //                     {
    //                         headers: {
    //                             Authorization: `Bearer ${localStorage.getItem("__t")}`,
    //                             organizationId:
    //                                 localStorage.getItem("organizationId") || "default-org-id",
    //                             "Content-Type": "application/json",
    //                         },
    //                     }
    //                 );

    //                 if (response.data) {
    //                     successMsg(
    //                         "Success",
    //                         "Proof of Investment withdrawn successfully",
    //                         true
    //                     );
    //                     fetchPoiData(employeeId);
    //                 }
    //             } catch (error) {
    //                 console.error("Error withdrawing POI:", error);
    //                 errorMsg(
    //                     "Error",
    //                     error.response?.data?.message ||
    //                     "Failed to withdraw Proof of Investment",
    //                     true
    //                 );
    //             } finally {
    //                 setIsSubmitting(false);
    //             }
    //         },
    //     });
    // };


    // Download Form 12BB
    const downloadForm12BB = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/proof-of-investments/${employeeId}/${fiscalYear}/form-12bb`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id"
                    },
                    responseType: 'blob'
                }
            );

            // Create a blob from the PDF stream
            const blob = new Blob([response.data], { type: 'application/pdf' });
            const url = window.URL.createObjectURL(blob);

            // Create a link element
            const link = document.createElement('a');
            link.href = url;
            link.download = `Form12BB_POI_${employeeId}_${fiscalYear}.pdf`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (error) {
            console.error("Error downloading Form 12BB:", error);
            errorMsg("Error", "Failed to download Form 12BB. Please try again.", true);
        }
    };

    // Group POI items by category
    const groupPoiItems = () => {
        if (!poiData?.poiItems) return {};

        const groups = {};
        poiData.poiItems.forEach(item => {
            let category = 'Other';

            if (item.investmentType === 'HRA') {
                category = 'House Rent Details';
            } else if (item.investmentType.includes('80C') || item.investmentType === 'epf' || item.investmentType === 'nsc') {
                category = '80C Investments';
            } else if (item.investmentType.includes('80D') || item.investmentType.includes('medi_claim')) {
                category = '80D Exemptions';
            } else if (item.investmentType.includes('OTHER_INCOME')) {
                category = 'Other Sources of Income';
            } else if (item.investmentType.includes('PREV_EMPLOYMENT')) {
                category = 'Previous Employment';
            } else if (item.investmentType === 'LET_OUT_PROPERTY') {
                category = 'Let Out Property';
            } else if (item.investmentType === 'voluntary_nps') {
                category = 'Other Investments';
            }

            if (!groups[category]) {
                groups[category] = [];
            }
            groups[category].push(item);
        });

        return groups;
    };

    // Get current POI item for active modal
    const getCurrentPoiItem = () => {
        if (!poiData?.poiItems || !activeAttachmentModal) return null;
        return poiData.poiItems.find(item => item.id === activeAttachmentModal);
    };

    // Load comments when comment modal opens
    useEffect(() => {
        if (activeCommentModal) {
            fetchCommentsForItem(activeCommentModal);
        }
    }, [activeCommentModal]);

    // Handle Edit Declaration button click
    const handleEditDeclaration = () => {
        // Check if edit is allowed from backend data
        if (poiData?.canAllowEdit === false) {
            setShowPoiLockedModal(true);
        } else {
            navigate('/user-proof-edit');
        }
    };

    // Handle Change Tax Regime button click
    const handleChangeTaxRegime = () => {
        // Check if tax regime change is allowed from backend data
        if (poiData?.canChangeTaxRegime === false) {
            setShowTaxRegimeLockedModal(true);
        } else {
            navigate('/compare-tax-regimes');
        }
    };

    if (isLoading) {
        return <Loader />;
    }

    const groupedItems = groupPoiItems();
    const isEditable = poiData?.status === 'DRAFT';
    const currentPoiItem = getCurrentPoiItem();

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Proof of Investments</title>
            </Helmet>

            <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Proof of Investments</h5>
                {/* <span className="text-muted">
                    Financial Year: <span className="text-primary">{fiscalYear}-{fiscalYear + 1}</span>
                </span> */}
            </div>

            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-fluid py-2">
                    <div className="container-fluid d-flex align-items-start justify-content-start p-10 bg-white">
                        <div className="w-100" style={{ maxWidth: "1400px" }}>
                            {/* Header Section */}
                            <div className="card mb-6">
                                <div className="card-body">
                                    <div className="d-flex justify-content-between align-items-center mb-4">
                                        <div>
                                            <h3 className="fw-bold text-gray-800 mb-1">Proof of Investments</h3>
                                            {/* <div className="text-muted fs-7 mb-2">Employee Code: {employeeId}</div> */}
                                        </div>
                                        <div className="text-end">
                                            <div className="d-flex align-items-center gap-3">
                                                <span className={getStatusBadge(poiData?.status)}>
                                                    {poiData?.status || "DRAFT"}
                                                </span>
                                                {/* <button
                                                    className="btn btn-icon btn-light-primary btn-sm"
                                                    onClick={handleEditDeclaration}
                                                    title="Edit Declaration"
                                                >
                                                    <i className="ki-outline ki-pencil fs-2"></i>
                                                </button> */}
                                            </div>
                                        </div>
                                    </div>

                                    <div className="row">
                                        <div className="col-md-6">
                                            <div className="d-flex align-items-center mb-3">
                                                <span className="text-muted me-3">Financial Year:</span>
                                                <select
                                                    className="form-select form-select-sm w-125px"
                                                    value={fiscalYear}
                                                    onChange={(e) => setFiscalYear(parseInt(e.target.value))}
                                                >
                                                    <option value={fiscalYear}>
                                                        {fiscalYear - 1}-{fiscalYear}
                                                    </option>
                                                </select>
                                            </div>

                                        </div>
                                        <div className="col-md-6">
                                            <div className="d-flex align-items-center justify-content-end">
                                                <div className="d-flex align-items-center">
                                                    <span className="text-muted me-3">Tax Regime:</span>
                                                    <span className="fw-bold text-gray-800 me-2">
                                                        {poiData?.taxRegimeFormatted || 'N/A'}
                                                    </span>
                                                    {/* <button
                                                        className="btn btn-sm btn-light-primary"
                                                        onClick={handleChangeTaxRegime}
                                                    >
                                                        (Change Tax Regime)
                                                    </button> */}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* POI Items Table */}
                            <div className="card mb-6">
                                <div className="card-body p-0">
                                    <div className="table-responsive">
                                        <table className="table table-hover table-rounded border gs-5">
                                            <thead className="bg-light">
                                                <tr>
                                                    <th className="fw-semibold text-gray-700" width="25%">Particulars</th>
                                                    <th className="fw-semibold text-gray-700 text-end" width="15%">Declared Amount</th>
                                                    <th className="fw-semibold text-gray-700 text-center" width="10%">Proofs</th>
                                                    <th className="fw-semibold text-gray-700 text-center" width="10%">Comments</th>
                                                    <th className="fw-semibold text-gray-700 text-end">
                                                        Actual Amount <span className="text-danger">*</span>
                                                    </th>
                                                    <th className="fw-semibold text-gray-700 text-center" width="10%">Status</th>
                                                    <th className="fw-semibold text-gray-700 text-end" width="15%">Approved Amount</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                {Object.entries(groupedItems).map(([category, items]) => (
                                                    <React.Fragment key={category}>
                                                        <tr className="bg-light">
                                                            <td colSpan="7" className="fw-bold text-gray-800 py-3">
                                                                {category}
                                                                {category === '80C Investments' && ' (Max Limit: ₹1,50,000.00)'}
                                                                {category === '80D Exemptions' && ' (Max Limit: ₹1,00,000.00)'}
                                                            </td>
                                                        </tr>
                                                        {items.map((item) => (
                                                            <tr key={item.id} className={!isEditable ? 'bg-light-secondary' : ''}>
                                                                <td className="text-gray-800">
                                                                    <div className="fw-semibold">
                                                                        {getInvestmentTypeDisplay(item.investmentType)}
                                                                    </div>

                                                                    {item.itemIdExternal && (
                                                                        <div className="text-muted fs-7 mt-1">
                                                                            ID: {item.itemIdExternal}
                                                                        </div>
                                                                    )}

                                                                    {/* ✅ LET OUT PROPERTY DETAILS */}
                                                                    {item.investmentType === "LET_OUT_PROPERTY" &&
                                                                        renderLetOutPropertyDetails(item)}
                                                                </td>

                                                                <td className="fw-bold text-gray-800 text-end">
                                                                    {formatCurrency(item.declaredAmount)}
                                                                </td>
                                                                <td className="text-center">
                                                                    <div className="position-relative">
                                                                        <button
                                                                            className="btn btn-icon btn-sm btn-light-primary"
                                                                            disabled={!canEditPoiItem(item)}
                                                                            onClick={() => setActiveAttachmentModal(item.id)}
                                                                            title={
                                                                                canEditPoiItem(item)
                                                                                    ? "Upload documents"
                                                                                    : "Editing not allowed"
                                                                            }
                                                                        >
                                                                            <i className="ki-outline ki-paper-clip fs-2"></i>
                                                                        </button>

                                                                        {item.documents && item.documents.length > 0 && (
                                                                            <span className="position-absolute top-0 start-100 translate-middle badge badge-circle badge-primary">
                                                                                {item.documents.length}
                                                                            </span>
                                                                        )}
                                                                    </div>
                                                                </td>
                                                                <td className="text-center">
                                                                    <div className="position-relative">
                                                                        <button
                                                                            className="btn btn-icon btn-sm btn-light-info"
                                                                            onClick={() => {
                                                                                setActiveCommentModal(item.id);
                                                                                setCommentText("");
                                                                            }}
                                                                            title="Add comment"
                                                                        >
                                                                            <i className="ki-outline ki-message-text-2 fs-2"></i>
                                                                        </button>

                                                                        {comments[item.id] && comments[item.id].length > 0 && (
                                                                            <span className="position-absolute top-0 start-100 translate-middle badge badge-circle badge-info">
                                                                                {comments[item.id].length}
                                                                            </span>
                                                                        )}
                                                                    </div>
                                                                </td>
                                                                <td className="text-end">
                                                                    {canEditPoiItem(item) ? (
                                                                        <div className="d-flex align-items-center justify-content-end gap-2">
                                                                            <div className="input-group input-group-sm w-150px">
                                                                                <span className="input-group-text">₹</span>
                                                                                <input
                                                                                    type="text"
                                                                                    inputMode="numeric"
                                                                                    pattern="[0-9]*"
                                                                                    className="form-control"
                                                                                    value={actualAmounts[item.id] ?? ""}
                                                                                    onChange={(e) => {
                                                                                        const value = e.target.value.replace(/[^0-9]/g, "");
                                                                                        handleActualAmountChange(item.id, value);
                                                                                    }}
                                                                                />
                                                                            </div>

                                                                            <button
                                                                                className="btn btn-icon btn-sm btn-light-success"
                                                                                onClick={() => updatePoiItem(item.id)}
                                                                                disabled={isSubmitting || !actualAmounts[item.id]}
                                                                                title="Save actual amount"
                                                                            >
                                                                                <i className="ki-outline ki-check fs-2"></i>
                                                                            </button>
                                                                        </div>
                                                                    ) : (
                                                                        <span className="fw-bold text-gray-800">
                                                                            {formatCurrency(item.actualAmount ?? 0)}
                                                                        </span>
                                                                    )}
                                                                </td>

                                                                <td className="text-center">
                                                                    <span className={getStatusBadge(item.status)}>
                                                                        {item.status || "DRAFT"}
                                                                    </span>
                                                                </td>
                                                                <td className="fw-bold text-gray-800 text-end">
                                                                    {formatCurrency(item.approvedAmount)}
                                                                </td>
                                                            </tr>
                                                        ))}
                                                    </React.Fragment>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            </div>

                            {/* Action Buttons */}
                            <div className="d-flex justify-content-between align-items-center pt-5 border-top">
                                <div>
                                    {poiData?.status === 'DRAFT' && (
                                        <button
                                            className="btn btn-lg btn-primary"
                                            onClick={submitPoi}
                                            disabled={isSubmitting}
                                        >
                                            {isSubmitting ? (
                                                <>
                                                    <span className="spinner-border spinner-border-sm me-2"></span>
                                                    Submitting...
                                                </>
                                            ) : (
                                                'Submit Proof of Investments'
                                            )}
                                        </button>
                                    )}
                                    {/* {poiData?.status === 'SUBMITTED' && (
                                        <button
                                            className="btn btn-lg btn-warning"
                                            onClick={withdrawPoi}
                                            disabled={isSubmitting}
                                        >
                                            {isSubmitting ? (
                                                <>
                                                    <span className="spinner-border spinner-border-sm me-2"></span>
                                                    Withdrawing...
                                                </>
                                            ) : (
                                                'Withdraw Proof of Investments'
                                            )}
                                        </button>
                                    )} */}
                                </div>
                                {/* <div>
                                    <button
                                        className="btn btn-lg btn-primary"
                                        onClick={downloadForm12BB}
                                        disabled={isSubmitting}
                                    >
                                        <i className="ki-outline ki-download fs-2 me-2"></i>
                                        Form 12BB - Employee Proof of Investments 💸 Download
                                    </button>
                                </div> */}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Attachment Modal */}
            {activeAttachmentModal && currentPoiItem && (
                <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
                    <div className="modal-dialog modal-lg">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">
                                    Attachments - {getInvestmentTypeDisplay(currentPoiItem.investmentType)}
                                </h5>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light-danger"
                                    onClick={() => setActiveAttachmentModal(null)}
                                >
                                    <i className="ki-outline ki-cross fs-2"></i>
                                </button>
                            </div>
                            <div className="modal-body">
                                {currentPoiItem.documents && currentPoiItem.documents.length > 0 ? (
                                    currentPoiItem.documents.map((doc) => (
                                        <div key={doc.id} className="border rounded p-3 mb-3 d-flex justify-content-between align-items-center">
                                            <div className="d-flex align-items-center">
                                                <i className="ki-outline ki-file fs-2 text-primary me-3"></i>
                                                <div>
                                                    <span className="fw-bold text-gray-800 d-block">{doc.documentName}</span>
                                                    <div className="text-muted fs-7 mt-1">
                                                        Uploaded: {new Date(doc.uploadedTime).toLocaleDateString()} at {new Date(doc.uploadedTime).toLocaleTimeString()}
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="d-flex gap-2">
                                                <a
                                                    href={doc.documentUrl}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="btn btn-icon btn-sm btn-light-primary"
                                                    title="View document"
                                                >
                                                    <i className="ki-outline ki-eye fs-2"></i>
                                                </a>


                                                <button
                                                    className="btn btn-icon btn-sm btn-light-danger"
                                                    onClick={() => deleteDocument(doc.id)}
                                                    title="Delete document"
                                                >
                                                    <i className="ki-outline ki-trash fs-2"></i>
                                                </button>

                                            </div>
                                        </div>
                                    ))
                                ) : (
                                    <div className="text-center py-5">
                                        <i className="ki-outline ki-document fs-4x text-muted mb-3"></i>
                                        <div className="text-muted">No attachments uploaded yet.</div>
                                    </div>
                                )}


                                <div className="mt-4">
                                    <div className="border-dashed border-2 border-primary rounded p-4 text-center">
                                        <input
                                            type="file"
                                            id="fileUpload"
                                            className="d-none"
                                            onChange={(e) => {
                                                if (e.target.files.length > 0) {
                                                    uploadDocument(activeAttachmentModal, e.target.files[0]);
                                                }
                                            }}
                                            accept=".jpg,.jpeg,.png,.pdf,.zip"
                                        />
                                        <label htmlFor="fileUpload" className="btn btn-light-primary btn-lg mb-3 cursor-pointer">
                                            <i className="ki-outline ki-plus fs-2 me-2"></i>
                                            Add More Attachments
                                        </label>
                                        <div className="text-muted fs-7">
                                            Allowed file formats: jpg, jpeg, png, pdf, zip
                                            <br />
                                            Maximum file size: 10MB
                                        </div>

                                        {uploadingFile === activeAttachmentModal && (
                                            <div className="mt-3">
                                                <div className="progress">
                                                    <div
                                                        className="progress-bar progress-bar-striped progress-bar-animated"
                                                        style={{ width: `${uploadProgress}%` }}
                                                    >
                                                        {uploadProgress}%
                                                    </div>
                                                </div>
                                                <div className="text-muted fs-8 mt-1">
                                                    Uploading document...
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                </div>

                            </div>
                            <div className="modal-footer">
                                <button
                                    type="button"
                                    className="btn btn-light"
                                    onClick={() => setActiveAttachmentModal(null)}
                                >
                                    Close
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Comment Modal */}
            {activeCommentModal && poiData?.poiItems && (
                <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
                    <div className="modal-dialog modal-lg">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">
                                    Comments - {getInvestmentTypeDisplay(poiData.poiItems.find(item => item.id === activeCommentModal)?.investmentType)}
                                </h5>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light-danger"
                                    onClick={() => setActiveCommentModal(null)}
                                >
                                    <i className="ki-outline ki-cross fs-2"></i>
                                </button>
                            </div>
                            <div className="modal-body">
                                {/* Existing Comments */}
                                {comments[activeCommentModal] && comments[activeCommentModal].length > 0 ? (
                                    <div className="mb-4">
                                        <h6 className="text-muted mb-3">Previous Comments:</h6>
                                        {comments[activeCommentModal].map((comment, index) => (
                                            <div key={comment.id || index} className="border rounded p-3 mb-3">
                                                <div className="d-flex justify-content-between align-items-start mb-2">
                                                    <div className="fw-bold text-gray-800">
                                                        {comment.createdBy || "You"}
                                                        {comment.createdByEmployee && <span className="badge badge-light-primary ms-2">Employee</span>}
                                                    </div>
                                                    <div className="text-muted fs-7">
                                                        {new Date(comment.createdAt).toLocaleDateString()} at {new Date(comment.createdAt).toLocaleTimeString()}
                                                    </div>
                                                </div>
                                                <div className="text-gray-700 mb-2">
                                                    {comment.comment}
                                                </div>
                                                {comment.createdBy === "You" && (
                                                    <div className="text-end">
                                                        <button
                                                            className="btn btn-sm btn-light-warning me-2"
                                                            onClick={() => {
                                                                const newText = prompt("Edit comment:", comment.comment);
                                                                if (newText !== null) {
                                                                    updateComment(activeCommentModal, comment.id, newText);
                                                                }
                                                            }}
                                                        >
                                                            <i className="ki-outline ki-pencil fs-2 me-1"></i>
                                                            Edit
                                                        </button>
                                                        <button
                                                            className="btn btn-sm btn-light-danger"
                                                            onClick={() => deleteComment(activeCommentModal, comment.id)}
                                                        >
                                                            <i className="ki-outline ki-trash fs-2 me-1"></i>
                                                            Delete
                                                        </button>
                                                    </div>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                ) : (
                                    <div className="text-center py-3 mb-4">
                                        <i className="ki-outline ki-message-text-2 fs-4x text-muted mb-3"></i>
                                        <div className="text-muted">No comments yet. Add the first comment!</div>
                                    </div>
                                )}

                                {/* Add New Comment */}
                                <div className="border-top pt-4">
                                    <h6 className="text-muted mb-3">Add New Comment:</h6>
                                    <textarea
                                        className="form-control"
                                        rows="4"
                                        placeholder="Enter your comment here..."
                                        value={commentText}
                                        onChange={(e) => setCommentText(e.target.value)}

                                    />
                                    <div className="text-muted fs-7 mt-2">
                                        Comments are visible to administrators for review.
                                    </div>
                                </div>
                            </div>
                            <div className="modal-footer">
                                <button
                                    type="button"
                                    className="btn btn-light"
                                    onClick={() => setActiveCommentModal(null)}
                                >
                                    Cancel
                                </button>

                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={() => addComment(activeCommentModal)}
                                    disabled={!commentText.trim()}
                                >
                                    Save Comment
                                </button>

                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* POI Locked Modal */}
            {showPoiLockedModal && (
                <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">POI Locked</h5>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light-danger"
                                    onClick={() => setShowPoiLockedModal(false)}
                                >
                                    <i className="ki-outline ki-cross fs-2"></i>
                                </button>
                            </div>
                            <div className="modal-body">
                                <div className="text-center py-4">
                                    <i className="ki-outline ki-shield fs-4x text-warning mb-3"></i>
                                    <h5 className="text-gray-800 mb-3">POI is Locked</h5>
                                    <p className="text-muted mb-4">
                                        The Proof of Investment has been locked by HR/Admin and cannot be edited at this time.
                                        Please contact your HR department if you need to make changes.
                                    </p>
                                </div>
                            </div>
                            <div className="modal-footer justify-content-center">
                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={() => setShowPoiLockedModal(false)}
                                >
                                    OK
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Tax Regime Locked Modal */}
            {showTaxRegimeLockedModal && (
                <div className="modal fade show d-block" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">Tax Regime Locked</h5>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light-danger"
                                    onClick={() => setShowTaxRegimeLockedModal(false)}
                                >
                                    <i className="ki-outline ki-cross fs-2"></i>
                                </button>
                            </div>
                            <div className="modal-body">
                                <div className="text-center py-4">
                                    <i className="ki-outline ki-lock fs-4x text-warning mb-3"></i>
                                    <h5 className="text-gray-800 mb-3">Tax Regime is Locked</h5>
                                    <p className="text-muted mb-4">
                                        The tax regime selection has been locked by HR/Admin and cannot be changed at this time.
                                        Please contact your HR department if you need to make changes to your tax regime selection.
                                    </p>
                                </div>
                            </div>
                            <div className="modal-footer justify-content-center">
                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={() => setShowTaxRegimeLockedModal(false)}
                                >
                                    OK
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {isLoading && <Loader />}
        </>
    );
}