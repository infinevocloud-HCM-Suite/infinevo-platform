// salaryComponents.js - Benefits Tab
import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form } from "formik";
import { Table, Dropdown, Menu, Modal } from "antd";
import { MoreOutlined, ExclamationCircleFilled } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

const { confirm } = Modal;

export default function Benefits() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [activeTab, setActiveTab] = useState('benefits');
  const [rows, setRows] = useState([]);
  const [hasBenefits, setHasBenefits] = useState(false);
  const [fetchError, setFetchError] = useState(false);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  useEffect(() => {
    fetchBenefits();
  }, [activeTab]);

  const fetchBenefits = async () => {
    try {
      setSigningIn(true);
      const token = localStorage.getItem("__t");
      
      const response = await axios.get(`${GlobalConst.API_URL}/api/benefits`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
      });
      
      if (response.data && response.data.data) {
        // Transform API data to match table format
        const formattedData = response.data.data.map((benefit, index) => ({
          key: benefit.benefitId || `temp-${index}`,
          name: benefit.benefitName || "N/A",
          benefitType: benefit.benefitPlanNameFormatted || benefit.benefitPlan || "N/A",
          benefitFrequency: getBenefitFrequency(benefit),
          status: benefit.statusFormatted || (benefit.status === "active" ? "Active" : "Inactive"),
          rawData: benefit // Store the raw data for reference
        }));
        
        setRows(formattedData);
        setHasBenefits(formattedData.length > 0);
        setFetchError(false);
      } else {
        setFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("API Error:", error);
      
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load benefits", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setSigningIn(false);
    }
  };

  // Helper function to format benefit frequency
  const getBenefitFrequency = (benefit) => {
    return benefit.isOneTime ? "One Time" : "Recurring";
  };

  const handleStatusToggle = async (record) => {
    try {
      setSigningIn(true);
      const token = localStorage.getItem("__t");
      const newStatus = record.status.toLowerCase() === "active" ? "inactive" : "active";
      
      const endpoint = newStatus === "active" 
        ? `${GlobalConst.API_URL}/api/benefits/active/${record.key}`
        : `${GlobalConst.API_URL}/api/benefits/inactive/${record.key}`;
      
      await axios.put(endpoint, {}, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
      });
      
      successMsg("Success", `Benefit ${newStatus === "active" ? "activated" : "inactivated"} successfully`, false);
      fetchBenefits(); // Refresh the list
    } catch (error) {
      console.error("Status toggle error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to update benefit status", true);
    } finally {
      setSigningIn(false);
    }
  };

  const showDeleteConfirm = (record) => {
    confirm({
      title: 'Are you sure you want to delete this benefit component?',
      icon: <ExclamationCircleFilled />,
      content: 'This action cannot be undone and the benefit will be permanently deleted from the database.',
      okText: 'Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      centered: true,
      onOk() {
        handleDelete(record);
      },
      onCancel() {
        console.log('Cancelled delete operation');
      },
    });
  };

  const handleDelete = async (record) => {
    try {
      setSigningIn(true);
      const token = localStorage.getItem("__t");
      
      await axios.delete(`${GlobalConst.API_URL}/api/benefits/${record.key}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
      });
      
      successMsg("Success", "Benefit deleted successfully", false);
      fetchBenefits(); // Refresh the list
    } catch (error) {
      console.error("Delete error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to delete benefit", true);
    } finally {
      setSigningIn(false);
    }
  };

  // Function to manually retry fetching data
  const retryFetch = () => {
    fetchBenefits();
  };

  // AntD Table columns for Benefits
  const benefitsColumns = [
    {
      title: "NAME",
      dataIndex: "name",
      key: "name",
      onHeaderCell: () => ({
        style: { 
          padding: '6px',  
          backgroundColor: '#fff', 
          border: '1px solid #d9d9d9', 
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
      onCell: () => ({
        style: { 
          padding: '6px', 
          backgroundColor: '#fff', 
          border: '1px solid #d9d9d9', 
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
    },
    {
      title: "BENEFIT TYPE",
      dataIndex: "benefitType",
      key: "benefitType",
      onHeaderCell: () => ({
        style: { 
          backgroundColor: '#fff', 
          border: '1px solid #d9d9d9', 
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
      onCell: () => ({
        style: { 
          backgroundColor: '#fff', 
          border: '1px solid ',
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
      render: (benefitType) => (
        <span style={{ color: "#504d4dff" }}>{benefitType}</span>
      )
    },
    {
      title: "BENEFIT FREQUENCY",
      dataIndex: "benefitFrequency",
      key: "benefitFrequency",
      onHeaderCell: () => ({
        style: { 
          backgroundColor: '#fff', 
          border: '1px solid #d9d9d9', 
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
      onCell: () => ({
        style: { 
          backgroundColor: '#fff', 
          border: '1px solid #d9d9d9', 
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
      render: (benefitFrequency) => (
        <span style={{ color: "#504d4dff" }}>{benefitFrequency}</span>
      )
    },
    {
      title: "STATUS",
      dataIndex: "status",
      key: "status",
      onHeaderCell: () => ({
        style: { 
          backgroundColor: '#fff', 
          border: '1px solid #d9d9d9', 
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
      onCell: () => ({
        style: { 
          backgroundColor: '#fff', 
          border: '1px solid #d9d9d9', 
          color: '#000', 
          fontWeight: 'bold',
        }
      }),
      render: (status) => (
        <span
          style={{
            color: status === "Active" ? "#16a34a" : "#dc2626",
            fontWeight: 600,
          }}
        >
          {status}
        </span>
      ),
    },
    {
      title: "Actions",
      key: "actions",
      onHeaderCell: () => ({
        style: {
          backgroundColor: '#fff',
          border: '1px solid #d9d9d9',
          color: '#000',
          fontWeight: 'bold',
        }
      }),
      onCell: () => ({
        style: {
          backgroundColor: '#fff',
          border: '1px solid #d9d9d9',
          color: '#000',
          fontWeight: 'bold',
        }
      }),
      render: (_, record) => {
        const menu = (
          <Menu
            onClick={({ key }) => {
              if (key === "edit") {
                navigate(`/salary-components/benefits/edit/${record.key}`);
              } else if (key === "toggle-status") {
                handleStatusToggle(record);
              } else if (key === "delete") {
                showDeleteConfirm(record);
              }
            }}
            items={[
              { key: "edit", label: "Edit" },
              {
                key: "toggle-status",
                label: record.status === "Active" ? "Mark as Inactive" : "Mark as Active",
              },
              { type: "divider" },
              { key: "delete", label: "Delete", danger: true },
            ]}
          />
        );
        return (
          <Dropdown overlay={menu} trigger={["click"]} placement="bottomRight">
            <MoreOutlined style={{ fontSize: 20, cursor: "pointer", color: '#000' }} />
          </Dropdown>
        );
      },
    }
  ];

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Salary Components</title>
      </Helmet>

      <div className="w-100 bg-white px-3 px-lg-5 py-4 d-flex flex-column border-bottom">
        {/* Header */}
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h5 className="mb-0 fw-semibold">Salary Components</h5>

          <div className="dropdown">
            <button
              className="btn btn-primary btn-sm dropdown-toggle"
              type="button"
              id="addComponentDropdown"
              data-bs-toggle="dropdown"
              aria-expanded="false"
            >
              Add Component
            </button>
            <ul className="dropdown-menu" aria-labelledby="addComponentDropdown">
              <li>
                <button className="dropdown-item" onClick={() => navigate("/salary-components/add/earning")}>
                  Earning
                </button>
              </li>
              <li>
                <button className="dropdown-item" onClick={() => navigate("/salary-components/add/correction")}>
                  Correction
                </button>
              </li>
              <li>
                <button className="dropdown-item" onClick={() => navigate("/salary-components/add/benefits")}>
                  Benefit
                </button>
              </li>
              <li>
                <button className="dropdown-item" onClick={() => navigate("/salary-components/add/deduction")}>
                  Deduction
                </button>
              </li>
              <li>
                <button className="dropdown-item" onClick={() => navigate("/salary-components/add/reimbursement")}>
                  Reimbursement
                </button>
              </li>
            </ul>
          </div>
        </div>

        {/* Tabs */}
        <ul className="nav nav-tabs border-0 flex-nowrap overflow-auto">
          <li className="nav-item flex-shrink-0">
            <button
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${
                activeTab === 'earnings'
                  ? "active text-primary border-primary border-bottom"
                  : "text-dark"
              }`}
              onClick={() => {
                setActiveTab('earnings');
                navigate("/salary-components");
              }}
            >
              Earnings
            </button>
          </li>
          <li className="nav-item flex-shrink-0">
            <button
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${
                activeTab === 'deductions'
                  ? "active text-primary border-primary border-bottom"
                  : "text-dark"
              }`}
              onClick={() => {
                setActiveTab('deductions');
                navigate("/salary-components/deductions");
              }}
            >
              Deductions
            </button>
          </li>
          <li className="nav-item flex-shrink-0">
            <button
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${
                activeTab === 'benefits'
                  ? "active text-primary border-primary border-bottom"
                  : "text-dark"
              }`}
              onClick={() => {
                setActiveTab('benefits');
                navigate("/salary-components/benefits");
              }}
            >
              Benefits
            </button>
          </li>
          <li className="nav-item flex-shrink-0">
            <button
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${
                activeTab === 'reimbursements'
                  ? "active text-primary border-primary border-bottom"
                  : "text-dark"
              }`}
              onClick={() => {
                setActiveTab('reimbursements');
                navigate("/salary-components/reimbursements");
              }}
            >
              Reimbursements
            </button>
          </li>
        </ul>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid p-3 p-md-10 bg-white" style={{ minHeight: '100vh', overflowY: 'auto' }}>
            <div className="w-100">
              {hasBenefits ? (
                <Formik enableReinitialize>
                  {() => (
                    <Form className="form w-100">
                      <div className="table-responsive">
                        <Table
                          columns={benefitsColumns}
                          dataSource={rows}
                          bordered
                          pagination={false}
                          style={{ backgroundColor: '#f5f0fcff' }}
                          scroll={{ x: true }}
                        />
                      </div>
                    </Form>
                  )}
                </Formik>
              ) : (
                <div className="text-center py-10">
                  <div className="mb-7">
                    <i className="bi bi-cash-coin fs-1 text-muted"></i>
                  </div>
                  <div className="mb-10">
                    <h3 className="fw-bold text-gray-900 mb-2">
                      {fetchError ? "Failed to load benefits" : "No benefits configured yet"}
                    </h3>
                    <div className="text-muted fw-semibold fs-5">
                      {fetchError 
                        ? "Please try again later or contact support." 
                        : "Add benefit components to build your salary structure"}
                    </div>
                  </div>
                  <div className="d-flex flex-column flex-sm-row justify-content-center gap-3">
                    <button
                      className="btn btn-primary"
                      onClick={() => navigate("/salary-components/add/benefits")}
                    >
                      <i className="bi bi-plus fs-2"></i> Add Benefit Component
                    </button>
                    {fetchError && (
                      <button
                        className="btn btn-secondary"
                        onClick={retryFetch}
                      >
                        <i className="bi bi-arrow-clockwise fs-2"></i> Retry
                      </button>
                    )}
                  </div>
                  {fetchError && (
                    <div className="mt-5 p-3 bg-light rounded">
                      <h5 className="fw-bold">Troubleshooting Tips:</h5>
                      <ul className="text-start">
                        <li>Check if your backend server is running</li>
                        <li>Verify that the API endpoint /api/benefits exists</li>
                        <li>Check browser console for more detailed error information</li>
                        <li>Ensure your authentication token is valid</li>
                      </ul>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
      {signingIn && <Loader />}
    </>
  );
}