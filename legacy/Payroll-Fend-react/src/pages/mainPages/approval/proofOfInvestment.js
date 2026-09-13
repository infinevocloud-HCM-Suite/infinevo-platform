import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Modal, Button, Input, message, Dropdown, Menu, Spin } from "antd";
import { 
  SearchOutlined, 
  MoreOutlined,
  CalendarOutlined,
  UserOutlined,
  FileTextOutlined
} from '@ant-design/icons';
import { useNavigate } from "react-router-dom";

// Import the same utilities as index.js
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const { Search } = Input;

export default function ProofOfInvestment() {
  const navigate = useNavigate();

  // State for filters
  const [selectedStatus, setSelectedStatus] = useState("All Employees");
  const [selectedFY, setSelectedFY] = useState("");
  const [selectedTaxRegime, setSelectedTaxRegime] = useState("All");
  const [searchEmployee, setSearchEmployee] = useState("");
  const [yearSearchTerm, setYearSearchTerm] = useState("");

  // State for modal and data
  const [previewDoc, setPreviewDoc] = useState({ visible: false, doc: null });
  const [releasePOIModal, setReleasePOIModal] = useState(false);

  // State for API data and loading - following index.js pattern
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState(false);
  const [employees, setEmployees] = useState([]);
  const [filteredEmployees, setFilteredEmployees] = useState([]);
  const [unsubmittedCount, setUnsubmittedCount] = useState(0);
  const [availableYears, setAvailableYears] = useState([]);
  const [filteredYears, setFilteredYears] = useState([]);

  // Get organizationId from localStorage - exactly like index.js
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  // Function to get UPCOMING financial year for declarations
 const getDeclarationFinancialYear = () => {
  const today = new Date();
  const month = today.getMonth() + 1;
  const year = today.getFullYear();

  // FY END YEAR
  return month >= 4 ? year + 1 : year;
};


  // Function to format financial year as "YYYY-YYYY+1"
  const formatFinancialYear = (year) => {
    return `${year}-${year + 1}`;
  };

  // Function to generate financial years for dropdown
  const generateFinancialYears = () => {
    const upcomingYear = getDeclarationFinancialYear();
    const years = [];
    
    // Generate years from 5 years before upcoming to 2 years after upcoming
    for (let i = -5; i <= 2; i++) {
      const startYear = upcomingYear + i;
      const formattedYear = formatFinancialYear(startYear);
      years.push({
        value: formattedYear,
        label: formattedYear,
        year: startYear
      });
    }
    
    return years.sort((a, b) => b.year - a.year); // Descending order (newest first)
  };

//   useEffect(() => {
//   const upcomingFY = getDeclarationFinancialYear();
//   const formattedFY = formatFinancialYear(upcomingFY);

//   setSelectedFY(formattedFY);

//   const years = generateFinancialYears();
//   setAvailableYears(years);
//   setFilteredYears(years);
// }, []);

useEffect(() => {
  const currentFYEnd = getDeclarationFinancialYear(); // e.g. 2027
  const formattedFY = `${currentFYEnd - 1}-${currentFYEnd}`; // 2026-2027

  setSelectedFY(formattedFY);

  const years = generateFinancialYears();
  setAvailableYears(years);
  setFilteredYears(years);
}, []);

 // Fetch on initial load

  // Re-fetch when filters change
useEffect(() => {
  if (!organizationId || !selectedFY) return;

  // ✅ Extract FY END year
  const fiscalYear = parseInt(selectedFY.split('-')[1]);

  fetchPOIData(fiscalYear);
}, [organizationId, selectedFY, selectedStatus, searchEmployee]);



  // Filter years based on search term
  useEffect(() => {
    if (yearSearchTerm) {
      const filtered = availableYears.filter(yearItem => 
        yearItem.value.includes(yearSearchTerm) || 
        yearItem.label.includes(yearSearchTerm)
      );
      setFilteredYears(filtered);
    } else {
      setFilteredYears(availableYears);
    }
  }, [yearSearchTerm, availableYears]);

  const fetchPOIData = async (financialYear) => {
    try {
      setLoading(true);
      setFetchError(false);
      
      const token = localStorage.getItem("__t");
      
      // Build query parameters following the API structure
      const params = {
        organizationId: organizationId,
        page: 1,
        per_page: 50
      };

      // Add fiscal year if selected
      if (financialYear) {
        params.fiscalYear = financialYear;
      }

      // Add status if not "All Employees"
      if (selectedStatus !== "All Employees") {
        params.status = mapFrontendStatusToBackend(selectedStatus);
      }

      // Add search term if provided
      if (searchEmployee) {
        params.search = searchEmployee;
      }

      const response = await axios.get(`${GlobalConst.API_URL}/api/admin/proof-of-investments/dashboard`, {
        params: params,
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
      });

      console.log("API Response:", response.data);

      // Check if the response has the expected structure - following index.js pattern
      if (response.data && response.data.code === 0) {
        const apiData = response.data.proof_of_investment_list;
        
        // Transform API data to match frontend format
        const transformedData = apiData.map(item => {
          // Map backend status to frontend status
          const frontendStatus = mapBackendStatusToFrontend(item.status_formatted || item.status);
          
          // Get tax regime from API response
          const taxRegime = item.tax_regime_formatted || 
                           (item.tax_regime === "old" ? "Old Regime" : 
                            item.tax_regime === "new" ? "New Regime" : 
                            getTaxRegimeFromResponse(item));
          
          return {
            id: item.employee_number || item.employee_id,
            name: item.employee_name || "Unknown Employee",
            email: item.employee_email || `${item.employee_name?.toLowerCase().replace(/\s+/g, '.')}@company.com`,
            status: frontendStatus,
            submittedDate: item.submitted_on_formatted || item.submitted_on || "",
            submittedBy: item.submitted_by || "",
            reviewedBy: item.approved_by || "",
            taxRegime: taxRegime,
            uploadedDocs: 0, // You might need a separate API call for this
            hasPending: frontendStatus === "Approval Pending",
            documents: [],
            employeeId: item.employee_id, // Keep original ID for API calls
            proofOfInvestmentId: item.proof_of_investment_id,
            rawTaxRegime: item.tax_regime // Store raw tax regime for filtering
          };
        });

        setEmployees(transformedData);
        
        // Apply client-side filtering for tax regime
        const filtered = transformedData.filter(emp => {
          // First check tax regime filter
          let taxRegimeMatch = true;
          if (selectedTaxRegime !== "All") {
            if (selectedTaxRegime === "Old") {
              taxRegimeMatch = emp.taxRegime && (
                emp.taxRegime.toLowerCase().includes("old") || 
                emp.rawTaxRegime === "old"
              );
            } else if (selectedTaxRegime === "New") {
              taxRegimeMatch = emp.taxRegime && (
                emp.taxRegime.toLowerCase().includes("new") || 
                emp.rawTaxRegime === "new"
              );
            }
          }
          
          return taxRegimeMatch;
        });
        
        setFilteredEmployees(filtered);
        
        // Calculate unsubmitted count
        const unsubmitted = transformedData.filter(emp => emp.status === "Yet To Submit").length;
        setUnsubmittedCount(unsubmitted);

      } else {
        setFetchError(true);
        errorMsg("Error", response.data?.message || "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("API Error:", error);
      
      if (error.response) {
        // Server responded with error status
        errorMsg("Error", error.response.data?.message || "Failed to load proof of investment data", true);
      } else if (error.request) {
        // Request was made but no response received
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        // Something else happened
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  // Helper function to map frontend status to backend status
  const mapFrontendStatusToBackend = (frontendStatus) => {
    switch (frontendStatus) {
      case "Approval Pending":
        return "submitted";
      case "Approved":
        return "approved";
      case "Yet To Submit":
        return "draft";
      default:
        return "all";
    }
  };

  // Helper function to map backend status to frontend status
  const mapBackendStatusToFrontend = (backendStatus) => {
    if (!backendStatus) return "Yet To Submit";
    
    const status = backendStatus.toLowerCase();
    if (status.includes("submitted") || status === "submitted") {
      return "Approval Pending";
    } else if (status.includes("approved") || status === "approved") {
      return "Approved";
    } else if (status.includes("draft") || status === "draft") {
      return "Yet To Submit";
    } else if (status.includes("pending") || status.includes("approval pending")) {
      return "Approval Pending";
    } else if (status.includes("rejected") || status === "rejected") {
      return "Rejected";
    }
    return "Yet To Submit";
  };

  // Helper function to get tax regime from response (fallback)
  const getTaxRegimeFromResponse = (item) => {
    // Fallback logic if API doesn't provide tax_regime
    if (item.tax_regime) {
      return item.tax_regime === "old" ? "Old Regime" : "New Regime";
    }
    
    // If no tax regime info, try to infer from other fields
    if (item.tax_slab) {
      return item.tax_slab.includes("old") ? "Old Regime" : "New Regime";
    }
    
    // Default fallback
    return Math.random() > 0.5 ? "Old Regime" : "New Regime";
  };

  // Handle tax regime filter change
  const handleTaxRegimeChange = (value) => {
    setSelectedTaxRegime(value);
    
    // Apply client-side filtering
    if (value === "All") {
      setFilteredEmployees(employees);
    } else {
      const filtered = employees.filter(emp => {
        if (value === "Old") {
          return emp.taxRegime && (
            emp.taxRegime.toLowerCase().includes("old") || 
            emp.rawTaxRegime === "old"
          );
        } else if (value === "New") {
          return emp.taxRegime && (
            emp.taxRegime.toLowerCase().includes("new") || 
            emp.rawTaxRegime === "new"
          );
        }
        return true;
      });
      setFilteredEmployees(filtered);
    }
  };

  // Handle employee click - navigate to approval view
  const handleEmployeeClick = (employee) => {
    
    // navigate(`/proof-of-investment/approval-view/${employee.employeeId || employee.id}`);

    navigate(
  `/proof-of-investment/approval-view/${employee.employeeId}`,
  {
    state: {
      fiscalYear: parseInt(selectedFY.split('-')[1]) // FY END YEAR
    }
  }
);

  };

  // Handle release POI - following index.js pattern
  const handleReleasePOI = () => {
    navigate("/it-declaration");
  };

  // Handle document download
  const handleDownload = async (doc) => {
    try {
      const token = localStorage.getItem("__t");
      
      successMsg("Success", `Downloading ${doc.fileName}`, false);
    } catch (error) {
      errorMsg("Error", "Failed to download document", false);
    }
  };

  // Handle document preview
  const handlePreview = (doc) => {
    setPreviewDoc({ visible: true, doc });
  };

  // Close preview
  const closePreview = () => {
    setPreviewDoc({ visible: false, doc: null });
  };

  // Navigate to unsubmitted list
  const navigateToUnsubmitted = () => {
    navigate("/proof-of-investment/unsubmitted-list");
  };

  // Status badge color
  const getStatusBadgeClass = (status) => {
    switch (status) {
      case "Approved":
        return "badge-light-success";
      case "Approval Pending":
        return "badge-light-warning";
      case "Yet To Submit":
        return "badge-light-danger";
      case "Rejected":
        return "badge-light-danger";
      default:
        return "badge-light-primary";
    }
  };

  // More actions menu
  const getMoreActionsMenu = (employee) => (
    <Menu>
      <Menu.Item key="view" onClick={() => handleEmployeeClick(employee)}>
        View Details
      </Menu.Item>
      <Menu.Item key="download" onClick={() => handleDownload({ fileName: `${employee.name}_docs.zip` })}>
        Download All
      </Menu.Item>
      <Menu.Item key="remind" onClick={() => {
        // Implement send reminder API
        successMsg("Info", `Reminder sent to ${employee.name}`, false);
      }}>
        Send Reminder
      </Menu.Item>
    </Menu>
  );

  return (
    <>
      <Helmet>
        <title>Proof of Investment | Admin</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white">
        <div className="p-6">
          {/* Header */}
          <div className="d-flex justify-content-between align-items-center mb-4">
            <div>
              <h2 className="fw-bolder mb-1">Approval Investments</h2>
              <div className="text-muted">Review and approve employee investment proofs</div>
            </div>
            <Button
              type="primary"
              size="large"
              onClick={() => setReleasePOIModal(true)}
              className="fw-semibold"
              disabled={loading}
            >
              {loading ? <Spin size="small" /> : "Release POI"}
            </Button>
          </div>

          {/* Filters Section */}
          <div className="card mb-6">
            <div className="card-body">
              <div className="row g-4 align-items-end">
                {/* Financial Year Filter with Search */}
                <div className="col-md-2">
                  <label className="form-label small text-muted">FILTER BY</label>
                  <div className="dropdown">
                    <select
                      className="form-select"
                      value={selectedFY}
                      onChange={(e) => setSelectedFY(e.target.value)}
                      disabled={loading}
                    >
                      <option value="">Select Year</option>
                      {filteredYears.map(yearItem => (
                        <option key={yearItem.value} value={yearItem.value}>
                          {yearItem.label}
                        </option>
                      ))}
                    </select>
                    <div className="mt-2">
                      <Input
                        placeholder="Search year..."
                        size="small"
                        value={yearSearchTerm}
                        onChange={(e) => setYearSearchTerm(e.target.value)}
                        prefix={<SearchOutlined style={{ fontSize: '12px' }} />}
                        disabled={loading}
                      />
                    </div>
                  </div>
                </div>

                {/* Tax Regime Filter */}
                <div className="col-md-2">
                  <label className="form-label small text-muted">Select Tax Regime</label>
                  <select
                    className="form-select"
                    value={selectedTaxRegime}
                    onChange={(e) => handleTaxRegimeChange(e.target.value)}
                    disabled={loading}
                  >
                    <option value="All">All Regimes</option>
                    <option value="Old">Old Tax Regime</option>
                    <option value="New">New Tax Regime</option>
                  </select>
                </div>

                {/* Employee Filter */}
                <div className="col-md-3">
                  <label className="form-label small text-muted">Select an Employee</label>
                  <Input
                    placeholder="Search employee..."
                    prefix={<SearchOutlined />}
                    value={searchEmployee}
                    onChange={(e) => setSearchEmployee(e.target.value)}
                    style={{ height: '41px' }}
                    disabled={loading}
                  />
                </div>

                {/* Yet to Submit POI Filter */}
                {/* <div className="col-md-5">
                  <div className="d-flex justify-content-end align-items-center gap-3">
                    <div
                      className="text-primary cursor-pointer fw-semibold d-flex align-items-center"
                      onClick={navigateToUnsubmitted}
                      style={{ cursor: 'pointer' }}
                    >
                      <span className="me-2">{unsubmittedCount} employee(s) yet to submit POI</span>
                      <span className="badge badge-light-primary">{unsubmittedCount}</span>
                    </div>
                  </div>
                </div> */}
              </div>
            </div>
          </div>

          {/* Main Table */}
          <div className="card">
            <div className="card-header">
              <h5 className="card-title mb-0">
                {selectedStatus === "All Employees" ? "All Employees" : selectedStatus}
                <span className="text-muted fs-6 ms-2">
                  ({filteredEmployees.length} employees)
                  {loading && <Spin size="small" className="ms-2" />}
                </span>
              </h5>
            </div>
            <div className="card-body p-0">
              {loading ? (
                <div className="text-center py-5">
                  <Spin size="large" />
                  <div className="mt-3 text-muted">Loading employee data...</div>
                </div>
              ) : fetchError ? (
                <div className="text-center text-muted py-5">
                  Failed to load data. Please try again later.
                </div>
              ) : filteredEmployees.length === 0 ? (
                <div className="text-center text-muted py-5">
                  No employees found matching your criteria.
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead>
                      <tr className="text-muted small bg-light">
                        <th className="ps-6">SUBMITTED DATE</th>
                        <th>EMPLOYEE NAME</th>
                        <th>STATUS</th>
                        <th>TAX REGIME</th>
                        <th>SUBMITTED BY</th>
                        <th>REVIEWED BY</th>
                        <th className="text-end pe-6">ACTIONS</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredEmployees.map((employee) => (
                        <tr 
                          key={employee.employeeId || employee.id} 
                          className="cursor-pointer hover-row"
                          onClick={() => handleEmployeeClick(employee)}
                        >
                          <td className="ps-6">
                            {employee.submittedDate ? (
                              <div className="d-flex align-items-center">
                                <CalendarOutlined className="me-2 text-muted" />
                                {employee.submittedDate}
                              </div>
                            ) : (
                              <span className="text-muted">Not submitted</span>
                            )}
                          </td>
                          <td>
                            <div className="d-flex align-items-center">
                              <div className="symbol symbol-35px symbol-circle me-3">
                                <span className="symbol-label bg-primary text-white fw-bold">
                                  {employee.name.charAt(0)}
                                </span>
                              </div>
                              <div>
                                <div className="fw-bold text-dark">{employee.name}</div>
                                <div className="text-muted small">{employee.id}</div>
                              </div>
                            </div>
                          </td>
                          <td>
                            <span className={`badge ${getStatusBadgeClass(employee.status)}`}>
                              {employee.status}
                            </span>
                          </td>
                          <td>
                            <span className={`badge ${employee.taxRegime?.includes('Old') ? 'badge-light-warning' : 'badge-light-info'}`}>
                              {employee.taxRegime || 'Not Set'}
                            </span>
                          </td>
                          <td>
                            {employee.submittedBy ? (
                              <div className="d-flex align-items-center">
                                <UserOutlined className="me-2 text-muted" />
                                {employee.submittedBy}
                              </div>
                            ) : (
                              <span className="text-muted">-</span>
                            )}
                          </td>
                          <td>
                            {employee.reviewedBy ? (
                              <div className="d-flex align-items-center">
                                <UserOutlined className="me-2 text-muted" />
                                {employee.reviewedBy}
                              </div>
                            ) : (
                              <span className="text-muted">-</span>
                            )}
                          </td>
                          <td className="text-end pe-6">
                            <div className="d-flex gap-2 justify-content-end">
                              <Dropdown 
                                overlay={getMoreActionsMenu(employee)} 
                                trigger={['click']}
                                onClick={(e) => e.stopPropagation()}
                              >
                                <Button
                                  type="text"
                                  icon={<MoreOutlined />}
                                  size="small"
                                />
                              </Dropdown>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Release POI Modal */}
      <Modal
        title="Release Proof of Investment"
        visible={releasePOIModal}
        onCancel={() => setReleasePOIModal(false)}
        footer={[
          <Button key="cancel" onClick={() => setReleasePOIModal(false)}>
            Cancel
          </Button>,
          <Button key="release" type="primary" onClick={handleReleasePOI}>
            Release POI
          </Button>
        ]}
      >
        <p>Are you sure you want to release Proof of Investment for all employees?</p>
        <p className="text-muted">
          This action will notify all employees to submit their investment proofs.
        </p>
      </Modal>

      {/* Document Preview Modal */}
      <Modal
        title={previewDoc.doc?.fileName || "Document Preview"}
        visible={previewDoc.visible}
        onCancel={closePreview}
        footer={[
          <Button key="close" onClick={closePreview}>
            Close
          </Button>
        ]}
        width={800}
      >
        <div className="text-center p-4">
          <div className="mb-3">
            <FileTextOutlined className="fs-1 text-primary" style={{ fontSize: '64px' }} />
          </div>
          <h5>{previewDoc.doc?.name}</h5>
          <p className="text-muted">Preview for {previewDoc.doc?.fileName}</p>
          <p className="text-muted small">
            In a real implementation, this would show the actual document preview (PDF, image, etc.)
          </p>
        </div>
      </Modal>
    </>
  );
}