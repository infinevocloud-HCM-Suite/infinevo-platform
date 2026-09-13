import React, { useState, useEffect, useMemo } from "react";
import { Helmet } from "react-helmet-async";
import { Table, Avatar, Tag, Grid, Modal, Select } from "antd";
import { Dropdown } from "react-bootstrap";
import { useNavigate } from "react-router-dom";
import { CiImport } from "react-icons/ci";
import { IoPersonAdd } from "react-icons/io5";
import { HiUpload } from "react-icons/hi";
import { HiUser, HiMail, HiOfficeBuilding, HiStatusOnline, HiFilter } from "react-icons/hi";
import { MdMoreVert } from "react-icons/md";
import { GrFormView } from "react-icons/gr";
import { TbEdit } from "react-icons/tb";
import { BsTrash } from "react-icons/bs";
import { MdToggleOn, MdToggleOff } from "react-icons/md";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import employee from '../../../assets/images/employees.png';
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const { useBreakpoint } = Grid;
const { confirm } = Modal;
const { Option } = Select;

export default function Employees() {
  const navigate = useNavigate();
  const screens = useBreakpoint();
  const [loading, setLoading] = useState(true);
  const [employees, setEmployees] = useState([]);
  const [fetchError, setFetchError] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0
  });
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [selectedImportType, setSelectedImportType] = useState(null);
  const [filterName, setFilterName] = useState("");
  const [filterEmail, setFilterEmail] = useState("");
  const [filterDept, setFilterDept] = useState("ALL");
  const [filterStatus, setFilterStatus] = useState("ALL");
  const [showFilters, setShowFilters] = useState(true);

  // Get all unique departments from employees list
  const departments = useMemo(() => {
    const uniqueDepts = new Set();
    employees.forEach(emp => {
      if (emp.department) {
        uniqueDepts.add(emp.department);
      }
    });
    return Array.from(uniqueDepts).sort();
  }, [employees]);

  // Memoized search filtering for employees (client-side only)
  const filteredEmployees = useMemo(() => {
    return employees.filter(emp => {
      // 1. Employee Name or ID filter
      if (filterName.trim()) {
        const query = filterName.toLowerCase().trim();
        const matchesName = emp.name && emp.name.toLowerCase().includes(query);
        const matchesId = emp.employeeId && emp.employeeId.toString().toLowerCase().includes(query);
        if (!matchesName && !matchesId) {
          return false;
        }
      }

      // 2. Work Email filter
      if (filterEmail.trim()) {
        const query = filterEmail.toLowerCase().trim();
        if (!emp.workEmail || !emp.workEmail.toLowerCase().includes(query)) {
          return false;
        }
      }

      // 3. Department filter
      if (filterDept !== "ALL") {
        if (!emp.department || emp.department !== filterDept) {
          return false;
        }
      }

      // 4. Status filter
      if (filterStatus !== "ALL") {
        if (!emp.status || emp.status !== filterStatus) {
          return false;
        }
      }

      return true;
    });
  }, [employees, filterName, filterEmail, filterDept, filterStatus]);

  // Reset pagination to page 1 when any filter changes
  useEffect(() => {
    setPagination(prev => ({
      ...prev,
      current: 1
    }));
  }, [filterName, filterEmail, filterDept, filterStatus]);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");
  const token = localStorage.getItem("__t");

  // Import options data
  const importOptions = [
    { value: "basic", label: "Employee Basic Details", route: "/importbasicdetails" },
    { value: "statutory", label: "Employee Statutory Details", route: "/importstatutorydetails" },
    { value: "payment", label: "Employees Payment Information", route: "/importpaymentinfo" },
    { value: "salary", label: "Salary Details", route: "/importsalarydetails" },
    { value: "full", label: "Employee Details", route: "/importemployeedetails" }
  ];

  // Fetch employees from API (fetching all pages frontend-side)
  const fetchEmployees = async () => {
    try {
      setLoading(true);
      const pageSize = 100; // maximum page size supported by the backend limit
      const response = await axios.get(`${GlobalConst.API_URL}/api/employees`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
        params: {
          page: 0,
          size: pageSize,
          sortBy: "id",
          sortDir: "asc"
        }
      });

      if (response.data && response.data.status === 200) {
        let employeeData = response.data.data;
        const totalElements = response.data.totalElements;

        // If there are more pages, fetch them in parallel and combine
        if (totalElements > pageSize) {
          const totalPages = Math.ceil(totalElements / pageSize);
          const promises = [];
          for (let p = 1; p < totalPages; p++) {
            promises.push(
              axios.get(`${GlobalConst.API_URL}/api/employees`, {
                headers: {
                  Authorization: `Bearer ${token}`,
                  organizationId: organizationId
                },
                params: {
                  page: p,
                  size: pageSize,
                  sortBy: "id",
                  sortDir: "asc"
                }
              })
            );
          }

          const responses = await Promise.all(promises);
          responses.forEach(res => {
            if (res.data && res.data.status === 200) {
              employeeData = employeeData.concat(res.data.data);
            }
          });
        }

        // Transform data for table
        const transformedData = employeeData.map(employee => ({
          key: employee.id,
          name: `${employee.firstName} ${employee.lastName}`,
          employeeId: employee.employeeNumber,
          workEmail: employee.workMail,
          department: employee.departmentName || employee.departmentId, // Use departmentName if available
          departmentId: employee.departmentId,
          status: employee.employeeStatus || "ACTIVE",
          profileComplete: true,
          portalEnabled: employee.portalEnabled,
          basicDetails: employee,
          // Check if profile is complete based on completionStatus
          completionStatus: employee.completionStatus || {}
        }));

        setEmployees(transformedData);
        setPagination(prev => ({
          ...prev,
          total: totalElements
        }));
        setFetchError(false);
      } else {
        setFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("Failed to fetch employees:", error);

      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load employees", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEmployees();
  }, []);

  // Check if employee profile is complete
  const isProfileComplete = (completionStatus) => {
    if (!completionStatus) return true;

    return completionStatus.personalDetail &&
      completionStatus.ctc &&
      completionStatus.basicDetails &&
      completionStatus.bankDetail;
  };

  // Handle import modal operations
  const showImportModal = () => {
    setImportModalVisible(true);
    setSelectedImportType(null);
  };

  const handleImportCancel = () => {
    setImportModalVisible(false);
    setSelectedImportType(null);
  };

  const handleImportProceed = () => {
    if (!selectedImportType) {
      errorMsg("Error", "Please select import type", true);
      return;
    }

    const selectedOption = importOptions.find(option => option.value === selectedImportType);
    if (selectedOption) {
      setImportModalVisible(false);
      navigate(selectedOption.route);
    }
  };

  const handleImportTypeChange = (value) => {
    setSelectedImportType(value);
  };

  // Handle pagination change locally
  const handleTableChange = (newPagination) => {
    setPagination(prev => ({
      ...prev,
      current: newPagination.current,
      pageSize: newPagination.pageSize
    }));
  };

  // Toggle employee status (ACTIVE <-> INACTIVE)
  const toggleEmployeeStatus = async (employeeId, currentStatus) => {
    try {
      setLoading(true);

      // choose endpoint depending on current status
      const endpoint =
        currentStatus === "ACTIVE"
          ? `${GlobalConst.API_URL}/api/employees-portal/${employeeId}/deactivate`
          : `${GlobalConst.API_URL}/api/employees-portal/${employeeId}/activate`;

      const response = await axios.put(endpoint, null, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      if (response.data && response.data.status === 200) {
        successMsg("Success", response.data.message, false);
        // Refresh the employee list after update
        fetchEmployees();
      } else {
        errorMsg("Error", "Failed to update employee status", true);
      }
    } catch (error) {
      console.error("Failed to update employee status:", error);
      errorMsg(
        "Error",
        error.response?.data?.message || "Failed to update employee status",
        true
      );
    } finally {
      setLoading(false);
    }
  };

  // Soft delete employee with confirmation modal
  const deleteEmployee = (employeeId, employeeName) => {
    confirm({
      title: 'Are you sure you want to delete this employee?',
      content: `Employee: ${employeeName}`,
      okText: 'Yes, Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          setLoading(true);
          const response = await axios.delete(
            `${GlobalConst.API_URL}/api/employees-portal/${employeeId}/soft-delete`,
            {
              headers: {
                Authorization: `Bearer ${token}`,
                organizationId: organizationId
              }
            }
          );

          if (response.data && response.data.status === 200) {
            successMsg("Success", "Employee deleted successfully", false);
            fetchEmployees();
          } else {
            errorMsg("Error", "Failed to delete employee", true);
          }
        } catch (error) {
          console.error("Delete Error:", error);
          errorMsg("Error", error.response?.data?.message || "Failed to delete employee", true);
        } finally {
          setLoading(false);
        }
      },
      onCancel() {
        console.log('Delete cancelled');
      },
    });
  };

  // Mobile card view for employees
  const renderMobileCard = (record) => {
    const profileComplete = isProfileComplete(record.completionStatus);

    return (
      <div
        key={record.key}
        className="card card-flush mb-5 mb-xl-10"
      >
        <div className="card-body pt-5">
          <div className="d-flex justify-content-between align-items-start mb-4">
            <div className="d-flex align-items-center">
              <Avatar
                style={{ backgroundColor: '#1890ff' }}
                size={40}
                className="me-3"
              >
                {record.name.charAt(0)}
              </Avatar>
              <div>
                <div
                  className="fw-bold text-primary text-hover-primary cursor-pointer fs-6"
                  onClick={() => navigate(`/employees/view/${record.key}`)}
                >
                  {record.name}
                </div>
                <div className="text-gray-400 fs-7">{record.employeeId}</div>
              </div>
            </div>
            <Dropdown>
              <Dropdown.Toggle as={"a"} className="btn btn-sm btn-light btn-flex btn-center btn-active-light-primary">
                <MdMoreVert />
              </Dropdown.Toggle>
              <Dropdown.Menu className="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-gray-600 menu-state-bg-light-primary fw-semibold fs-7 w-200px py-4">
                <div className="menu-item px-3">
                  <a href="#" className="menu-link px-3" onClick={(e) => { e.preventDefault(); navigate(`/employees/view/${record.key}`); }}>
                    <GrFormView className="me-3" /> View
                  </a>
                </div>
                <div className="menu-item px-3">
                  <a href="#" className="menu-link px-3" onClick={(e) => { e.preventDefault(); toggleEmployeeStatus(record.key, record.status); }}>
                    {record.status === "ACTIVE" ? 
                      <MdToggleOff className="me-3 fs-3 text-warning" /> : 
                      <MdToggleOn className="me-3 fs-3 text-success" />}
                    <span className={record.status === "ACTIVE" ? "text-warning" : "text-success"}>
                      {record.status === "ACTIVE" ? "Mark as Inactive" : "Mark as Active"}
                    </span>
                  </a>
                </div>
                <div className="separator my-2"></div>
                <div className="menu-item px-3">
                  <a href="#" className="menu-link px-3 text-danger" onClick={(e) => { e.preventDefault(); deleteEmployee(record.key, record.name); }}>
                    <BsTrash className="me-3" /> Delete
                  </a>
                </div>
              </Dropdown.Menu>
            </Dropdown>
          </div>

          <div className="mb-4">
            <div className="fw-semibold text-gray-600 mb-2">
              <span className="text-gray-400">Email:</span> {record.workEmail}
            </div>

            <div className="fw-semibold text-gray-600 mb-2">
              <span className="text-gray-400">Department:</span> {typeof record.department === 'string' && isNaN(record.department) ?
                record.department : record.department || "No Department"}
            </div>

            {/* Profile Completion Status for Mobile */}
            {!profileComplete && (
              <div className="mb-2">
                <span
                  className="text-danger fs-7 text-hover-primary cursor-pointer"
                  style={{ textDecoration: "underline" }}
                  onClick={() => navigate(`/employees/view/${record.key}`)}
                >
                  This employee's profile is incomplete. Complete now
                </span>
              </div>
            )}
          </div>

          <div className="d-flex justify-content-between align-items-center">
            <span className={`badge badge-light-${record.status === "ACTIVE" ? "success" : "danger"}`}>
              {record.status}
            </span>
            {!record.portalEnabled && (
              <span className="badge badge-light-warning">
                Portal disabled
              </span>
            )}
          </div>
        </div>
      </div>
    );
  };

  // Table columns structure
  const columns = [
    {
      title: (
        <span className="d-flex align-items-center">
          <HiUser className="me-2" /> Employee Name
        </span>
      ),
      dataIndex: "name",
      key: "employeeName",
      render: (_, record) => {
        const profileComplete = isProfileComplete(record.completionStatus);

        return (
          <div>
            <div className="d-flex align-items-center">
              <Avatar
                style={{ backgroundColor: '#1890ff' }}
                size={32}
                className="me-3"
              >
                {record.name.charAt(0)}
              </Avatar>
              <div>
                <div
                  className="text-gray-800 text-hover-primary fw-bold cursor-pointer fs-6"
                  onClick={() => navigate(`/employees/view/${record.key}`)}
                >
                  {record.name}
                </div>
                <div className="text-gray-500 fw-semibold d-block fs-8"><span className="text-gray-400">Emp ID:</span> {record.employeeId}</div>
              </div>
            </div>
            {/* Profile Completion Status Message */}
            {!profileComplete && (
              <div className="mt-2">
                <span
                  className="text-danger fs-8 text-hover-primary cursor-pointer"
                  style={{ textDecoration: "underline" }}
                  onClick={() => navigate(`/employees/view/${record.key}`)}
                >
                  This employee's profile is incomplete. Complete now
                </span>
              </div>
            )}
          </div>
        );
      },
    },
    {
      title: (
        <span className="d-flex align-items-center">
          <HiMail className="me-2" /> Work Email
        </span>
      ),
      dataIndex: "workEmail",
      key: "workEmail",
      responsive: ['md'],
    },
    {
      title: (
        <span className="d-flex align-items-center">
          <HiOfficeBuilding className="me-2" /> Dept
        </span>
      ),
      dataIndex: "department",
      key: "department",
      responsive: ['lg'],
      align: 'center',
      render: (department) => {
        // If department is already a name, use it
        if (typeof department === 'string' && isNaN(department)) {
          return department;
        }

        // Otherwise, check if we have department name in basicDetails
        // This assumes the API might return departmentName in the employee object
        return department || "No Department";
      }
    },
    {
      title: (
        <span className="d-flex align-items-center">
          <HiStatusOnline className="me-2" /> Status
        </span>
      ),
      dataIndex: "status",
      key: "status",
      responsive: ['sm'],
      render: (status) => (
        <span className={`badge badge-light-${status === "ACTIVE" ? "success" : "danger"}`}>
          {status}
        </span>
      )
    },
    {
      title: (
        <span className="d-flex align-items-center w-100 justify-content-center">
          <MdMoreVert className="me-2" /> Actions
        </span>
      ),
      key: "actions",
      align: 'center',
      render: (_, record) => {
        return (
          <Dropdown>
            <Dropdown.Toggle as={"a"} className="btn btn-sm btn-light btn-flex px-2 btn-center btn-active-light-primary">
              <MdMoreVert className="me-2" />
            </Dropdown.Toggle>
            <Dropdown.Menu className="menu menu-sub menu-sub-dropdown menu-column menu-rounded menu-gray-600 menu-state-bg-light-primary fw-semibold fs-7 w-200px py-4">
              <div className="menu-item px-3">
                <a href="#" className="menu-link px-3" onClick={(e) => { e.preventDefault(); navigate(`/employees/view/${record.key}`); }}>
                  <GrFormView className="me-3" /> View
                </a>
              </div>
              <div className="menu-item px-3">
                <a href="#" className="menu-link px-3" onClick={(e) => { e.preventDefault(); toggleEmployeeStatus(record.key, record.status); }}>
                  {record.status === "ACTIVE" ? 
                    <MdToggleOff className="me-3 fs-3 text-warning" /> : 
                    <MdToggleOn className="me-3 fs-3 text-success" />}
                  <span className={record.status === "ACTIVE" ? "text-warning" : "text-success"}>
                    {record.status === "ACTIVE" ? "Mark as Inactive" : "Mark as Active"}
                  </span>
                </a>
              </div>
              <div className="separator my-2"></div>
              <div className="menu-item px-3">
                <a href="#" className="menu-link px-3 text-danger" onClick={(e) => { e.preventDefault(); deleteEmployee(record.key, record.name); }}>
                  <BsTrash className="me-3" /> Delete
                </a>
              </div>
            </Dropdown.Menu>
          </Dropdown>
        );
      },
    }
  ];

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Employees</title>
      </Helmet>

      <section className="card card-flush d-lg-flex">




        <div id="kt_app_toolbar" class="app-toolbar pt-7">

          <div id="kt_app_toolbar_container" class="app-container container-fluid d-flex align-items-stretch">

            <div class="app-toolbar-wrapper d-flex flex-stack flex-wrap gap-4 w-100">

              <div class="page-title d-flex flex-column gap-1 me-3 mb-2">

                <ul class="breadcrumb breadcrumb-separatorless fw-semibold mb-6">

                  <li class="breadcrumb-item text-gray-700 fw-bold lh-1">
                    <a href="/" class="text-gray-500">
                      <i class="ki-duotone ki-home fs-3 text-gray-400 me-n1"></i>
                    </a>
                  </li>

                  <li class="breadcrumb-item">
                    <i class="ki-duotone ki-right fs-4 text-gray-700 mx-n1"></i>
                  </li>

                  <li class="breadcrumb-item text-gray-700 fw-bold lh-1">Dashboard</li>

                  <li class="breadcrumb-item">
                    <i class="ki-duotone ki-right fs-4 text-gray-700 mx-n1"></i>
                  </li>

                  <li class="breadcrumb-item text-gray-700">Employees</li>

                </ul>

                <h1 class="page-heading d-flex flex-column justify-content-center text-dark fw-bolder fs-1 lh-0">Employees</h1>

              </div>

              <div className="d-flex align-items-center gap-3 py-3 flex-wrap flex-md-nowrap w-100 w-md-auto justify-content-end">
                {/* Filters toggle button */}
                <button
                  className={`btn btn-sm btn-light-primary d-flex align-items-center gap-2 ${showFilters ? 'active' : ''}`}
                  onClick={() => setShowFilters(!showFilters)}
                  style={{
                    transition: "all 0.3s ease",
                    fontWeight: "600",
                    height: "36px",
                    whiteSpace: "nowrap"
                  }}
                >
                  <HiFilter className="fs-6 text-primary" />
                  {showFilters ? "Hide Filters" : "Show Filters"}
                </button>

                <div className="d-flex align-items-center gap-2">
                  <button
                    className="btn btn-success btn-sm"
                    onClick={() => navigate("/employees/add")}
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    <IoPersonAdd className="fs-7 me-2 " />
                    {screens.xs ? "Add" : "Add Employee"}
                  </button>
                  <button
                    className="btn btn-icon btn-light-primary btn-sm"
                    onClick={showImportModal}
                    title="Import Employees"
                  >
                    <CiImport size={18} />
                  </button>
                </div>
              </div>
              
            </div>

          </div>

        </div>





        <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
          <div className="d-flex flex-column flex-lg-row-fluid py-2">
            <div className="container-fluid p-3 p-md-4 p-lg-10 pb-0 pb-lg-0 pt-lg-4 bg-white">
              {employees.length > 0 ? (
                <>
                  {/* Collapsible Filter Panel */}
                  {showFilters && (
                    <div
                      className="card border-0 mb-6 p-5 rounded-3 shadow-xs"
                      style={{
                        backgroundColor: "#f8f9fa",
                        border: "1px solid #eef2f7"
                      }}
                    >
                      <div className="d-flex align-items-center justify-content-between mb-4">
                        <span className="fw-bold text-gray-800 fs-6">
                          <HiFilter className="text-primary me-2" /> Search Filters
                        </span>
                        {(filterName || filterEmail || filterDept !== "ALL" || filterStatus !== "ALL") && (
                          <button
                            className="btn btn-sm btn-link text-primary p-0 fw-semibold fs-7"
                            onClick={() => {
                              setFilterName("");
                              setFilterEmail("");
                              setFilterDept("ALL");
                              setFilterStatus("ALL");
                            }}
                          >
                            Clear Filters
                          </button>
                        )}
                      </div>
                      
                      <div className="row g-3">
                        {/* Employee Name */}
                        <div className="col-12 col-md-3">
                          <label className="form-label fw-semibold fs-7 text-gray-600 mb-1">Employee Name / ID</label>
                          <input
                            type="text"
                            className="form-control form-control-sm rounded-2"
                            placeholder="Filter by name or ID..."
                            value={filterName}
                            onChange={(e) => setFilterName(e.target.value)}
                            style={{
                              backgroundColor: "#ffffff",
                              border: "1px solid #E4E6EF",
                            }}
                          />
                        </div>
                        
                        {/* Work Email */}
                        <div className="col-12 col-md-3">
                          <label className="form-label fw-semibold fs-7 text-gray-600 mb-1">Work Email</label>
                          <input
                            type="text"
                            className="form-control form-control-sm rounded-2"
                            placeholder="Filter by email..."
                            value={filterEmail}
                            onChange={(e) => setFilterEmail(e.target.value)}
                            style={{
                              backgroundColor: "#ffffff",
                              border: "1px solid #E4E6EF",
                            }}
                          />
                        </div>
                        
                        {/* Department */}
                        <div className="col-12 col-md-3">
                          <label className="form-label fw-semibold fs-7 text-gray-600 mb-1">Dept</label>
                          <Select
                            placeholder="All Departments"
                            style={{ width: '100%' }}
                            value={filterDept}
                            onChange={(value) => setFilterDept(value)}
                            size="middle"
                          >
                            <Option value="ALL">All Departments</Option>
                            {departments.map(dept => (
                              <Option key={dept} value={dept}>{dept}</Option>
                            ))}
                          </Select>
                        </div>
                        
                        {/* Status */}
                        <div className="col-12 col-md-3">
                          <label className="form-label fw-semibold fs-7 text-gray-600 mb-1">Status</label>
                          <Select
                            placeholder="All Statuses"
                            style={{ width: '100%' }}
                            value={filterStatus}
                            onChange={(value) => setFilterStatus(value)}
                            size="middle"
                          >
                            <Option value="ALL">All Statuses</Option>
                            <Option value="ACTIVE">ACTIVE</Option>
                            <Option value="INACTIVE">INACTIVE</Option>
                          </Select>
                        </div>
                      </div>
                    </div>
                  )}

                  {screens.xs ? (
                    <div className="w-100">
                      {filteredEmployees.length > 0 ? (
                        filteredEmployees.slice((pagination.current - 1) * pagination.pageSize, pagination.current * pagination.pageSize).map(record => renderMobileCard(record))
                      ) : (
                        <div className="text-center py-10 text-muted fw-semibold fs-6">
                          No matching employees found.
                        </div>
                      )}

                      {/* Simple pagination for mobile */}
                      <div className="d-flex justify-content-between align-items-center mt-5">
                        <button
                          className="btn btn-sm btn-light-primary"
                          disabled={pagination.current === 1}
                          onClick={() => handleTableChange({ ...pagination, current: pagination.current - 1 })}
                        >
                          <i className="bi bi-chevron-left fs-6"></i> Previous
                        </button>
                        <span className="fw-semibold text-gray-600">
                          Page {pagination.current} of {Math.ceil(filteredEmployees.length / pagination.pageSize) || 1}
                        </span>
                        <button
                          className="btn btn-sm btn-light-primary"
                          disabled={pagination.current >= Math.ceil(filteredEmployees.length / pagination.pageSize)}
                          onClick={() => handleTableChange({ ...pagination, current: pagination.current + 1 })}
                        >
                          Next <i className="bi bi-chevron-right fs-6"></i>
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="w-100">

                      <Table
                        columns={columns}
                        dataSource={filteredEmployees}
                        bordered={false}
                        size="small"
                        pagination={{
                          current: pagination.current,
                          pageSize: pagination.pageSize,
                          total: filteredEmployees.length,
                          showSizeChanger: true,
                          pageSizeOptions: ['10', '20', '50', '100'],
                          className: `pagination pagination-override`,
                          showTotal: (total, range) =>
                            `${range[0]}-${range[1]} of ${total} items`
                        }}
                        onChange={handleTableChange}
                        className='table align-middle table-row-dashed dataTable no-footer table-rounded table-hover gy-5 gs-5 antd-override mb-0'
                        scroll={screens.md ? undefined : { x: 800 }}
                        onHeaderRow={() => ({
                          className: 'text-start text-gray-400 fw-bold fs-7 gs-0'
                        })}
                        onRow={() => ({
                          className: 'fw-semibold text-gray-600'
                        })}
                      />
                    </div>
                  )}
                </>
              ) : (
                <div className="w-100" style={{ maxWidth: "600px", margin: "0 auto" }}>
                  <div className="card-body py-5 py-md-10">
                    <div className="text-center">
                      <div className="mb-5 mb-md-7">
                        <img
                          src={employee}
                          alt="No Employees"
                          className="mw-100 h-150px h-sm-250px h-md-325px"
                        />
                      </div>

                      <div className="mb-5 mb-md-10">
                        <h3 className="fw-bold text-gray-900 mb-2">Get your employees onboard</h3>
                        <div className="text-muted fw-semibold fs-6 fs-md-5">
                          {fetchError ?
                            "Failed to load employees. Please try again later." :
                            "You can add new employees or import existing employee data from a file."}
                        </div>
                      </div>

                      <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                        <button
                          className="btn btn-primary"
                          onClick={() => navigate("/employees/add")}
                        >
                          <IoPersonAdd className="fs-3 me-2" /> New Employee
                        </button>

                        <button
                          className="btn btn-light-primary"
                          onClick={showImportModal}
                        >
                          <HiUpload className="fs-3 me-2" /> Import Employee
                        </button>
                      </div>

                      <div className="mt-5 mt-md-10 pt-5 pt-md-10 border-top">
                        <h4 className="fw-bold text-gray-900 mb-3 mb-md-4">Capture all necessary details about your employees and manage their salary, allowances and reimbursement details in this module.</h4>
                        <div className="row g-3 g-md-4 g-lg-5">
                          <div className="col-12 col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-file-earmark-text fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-900 mb-1">Employee Records</h5>
                                <div className="text-muted">Maintain complete employee records</div>
                              </div>
                            </div>
                          </div>
                          <div className="col-12 col-md-6">
                            <div className="d-flex align-items-center">
                              <i className="bi bi-cash-coin fs-2x text-primary me-3 me-md-4"></i>
                              <div>
                                <h5 className="fw-bold text-gray-900 mb-1">Salary Management</h5>
                                <div className="text-muted">Handle salary components efficiently</div>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Additional Styles */}
      <style jsx>{`
        .cursor-pointer {
          cursor: pointer;
        }
        
        @media (max-width: 576px) {
          .container-fluid {
            padding-left: 12px !important;
            padding-right: 12px !important;
          }
        }
      `}</style>


      {/* Import Data Modal */}
      <Modal
        title="Import Data"
        open={importModalVisible}
        onCancel={handleImportCancel}
        footer={[
          <button
            key="cancel"
            className="btn btn-light"
            onClick={handleImportCancel}
          >
            Cancel
          </button>,
          <button
            key="proceed"
            className="btn btn-primary"
            onClick={handleImportProceed}
            disabled={!selectedImportType}
          >
            Proceed
          </button>,
        ]}
        width={400}
        centered
      >
        <div className="p-3">
          <p className="mb-3">Select the type of employee details to import</p>
          <Select
            placeholder="Select"
            style={{ width: '100%' }}
            value={selectedImportType}
            onChange={handleImportTypeChange}
            size="large"
          >
            {importOptions.map(option => (
              <Option key={option.value} value={option.value}>
                {option.label}
              </Option>
            ))}
          </Select>
        </div>
      </Modal>

    </>
  );
}