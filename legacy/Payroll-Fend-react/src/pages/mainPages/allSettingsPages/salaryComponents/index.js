// salaryComponents.js - Earnings Tab (fixed canonical status handling + debug)
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
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

const { confirm } = Modal;

// utility: detect active as whole word (avoids matching "inactive")
const isActiveString = (s) => {
  if (s === undefined || s === null) return false;
  const normalized = String(s).trim().toLowerCase();
  return /\bactive\b/.test(normalized) || normalized === "true";
};

export default function SalaryComponents() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [activeTab, setActiveTab] = useState("earnings");
  const [rows, setRows] = useState([]);
  const [hasEarnings, setHasEarnings] = useState(false);
  const [fetchError, setFetchError] = useState(false);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  useEffect(() => {
    fetchEarnings();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab]);

  // --- FETCH EARNINGS (prefer canonical status; debug)
  const fetchEarnings = async () => {
    try {
      setSigningIn(true);
      const token = localStorage.getItem("__t");

      const response = await axios.get(`${GlobalConst.API_URL}/api/earnings`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      if (response.data && response.data.data) {
        const formattedData = response.data.data.map((earning, index) => {
          // canonical raw status: prefer earning.status, then state, then statusFormatted
          const canonicalRaw =
            earning.status !== undefined && earning.status !== null
              ? String(earning.status)
              : earning.state !== undefined && earning.state !== null
              ? String(earning.state)
              : earning.statusFormatted !== undefined && earning.statusFormatted !== null
              ? String(earning.statusFormatted)
              : "";

          const displayStatus = isActiveString(canonicalRaw) ? "Active" : "Inactive";

          // copy raw and force statusFormatted to canonicalRaw to avoid other code using stale formatted
          const rawCopy = { ...(earning || {}), status: earning.status, statusFormatted: canonicalRaw };

          return {
            key: earning.earningId || earning.id || `temp-${index}`,
            name: earning.earningName || earning.displayName || "N/A",
            earningType: earning.earningTypeFormatted || earning.earningType || "N/A",
            calculationType: getCalculationType(earning),
            considerForEPF: getEpfConsideration(earning),
            considerForESI: getEsiConsideration(earning),
            status: displayStatus, // mapped UI value
            rawData: rawCopy,
          };
        });

        // DEBUG: confirm what frontend will render
        console.info("FETCHED EARNINGS -> formattedData:", formattedData);

        setRows(formattedData);
        setHasEarnings(formattedData.length > 0);
        setFetchError(false);
      } else {
        setFetchError(true);
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      setFetchError(true);
      console.error("API Error:", error);

      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load earnings", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setSigningIn(false);
    }
  };
  // --- end fetchEarnings

  // Helpers
  const getCalculationType = (earning) => {
    if (earning.isAmountInPercentage) {
      return `Percentage; ${earning.value || 0}%`;
    } else if (earning.value) {
      return `Fixed; ${earning.valueFormatted || earning.value}`;
    }
    return "Not specified";
  };

  const getEpfConsideration = (earning) => {
    if (earning.isIncludedInEpf) {
      return earning.epfInclusionTypeFormatted || earning.epfInclusionType || "Yes";
    }
    return "No";
  };

  const getEsiConsideration = (earning) => {
    return earning.isIncludedInEsi ? "Yes" : "No";
  };

  // derive current status from authoritative row/rawData
  const getStatusFromRecord = (record) => {
    const candidates = [
      record?.rawData?.status,
      record?.status,
      record?.rawData?.statusFormatted,
      record?.rawData?.state,
    ];
    const found = candidates.find((c) => c !== undefined && c !== null && String(c).trim() !== "");
    if (!found) return "Inactive";
    return isActiveString(found) ? "Active" : "Inactive";
  };

  // Toggle status: call backend and update specific row from response (safer)
  const handleStatusToggle = async (record) => {
    try {
      setSigningIn(true);
      const token = localStorage.getItem("__t");

      const rowInState = rows.find((r) => r.key === record.key) || record;
      const currentStatus = getStatusFromRecord(rowInState);
      const shouldActivate = currentStatus.toLowerCase() !== "active";
      const endpoint = shouldActivate
        ? `${GlobalConst.API_URL}/api/earnings/active/${record.key}`
        : `${GlobalConst.API_URL}/api/earnings/inactive/${record.key}`;

      const res = await axios.put(endpoint, {}, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      // Determine new status from response if returned
      let newStatus = shouldActivate ? "Active" : "Inactive";
      if (res?.data?.data) {
        const returned = res.data.data;
        const canonical = returned.status ?? returned.state ?? returned.statusFormatted ?? "";
        newStatus = isActiveString(canonical) ? "Active" : "Inactive";
      }

      // update the single row in UI (status + rawData)
      setRows((prevRows) =>
        prevRows.map((r) =>
          r.key === record.key
            ? { ...r, status: newStatus, rawData: { ...(r.rawData || {}), ...(res?.data?.data || {}), status: newStatus, statusFormatted: newStatus } }
            : r
        )
      );

      setHasEarnings((prev) => rows.length > 0);
      successMsg("Success", `Earning ${newStatus === "Active" ? "activated" : "inactivated"} successfully`, false);
    } catch (error) {
      console.error("Status toggle error:", error);
      const message = error?.response?.data?.message || "Failed to update earning status";
      errorMsg("Error", message, true);
    } finally {
      setSigningIn(false);
    }
  };

  const showDeleteConfirm = (record) => {
    confirm({
      title: 'Are you sure you want to delete this earning component?',
      icon: <ExclamationCircleFilled />,
      content: 'This action cannot be undone and the earning will be permanently deleted from the database.',
      okText: 'Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      centered: true,
      onOk() {
        handleDelete(record);
      },
      onCancel() {
        // no-op
      },
    });
  };

  const handleDelete = async (record) => {
    try {
      setSigningIn(true);
      const token = localStorage.getItem("__t");

      await axios.delete(`${GlobalConst.API_URL}/api/earnings/${record.key}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      successMsg("Success", "Earning deleted successfully", false);

      // remove from UI
      setRows((prev) => {
        const next = prev.filter((r) => r.key !== record.key);
        setHasEarnings(next.length > 0);
        return next;
      });
    } catch (error) {
      console.error("Delete error:", error);
      errorMsg("Error", error.response?.data?.message || "Failed to delete earning", true);
    } finally {
      setSigningIn(false);
    }
  };

  const retryFetch = () => {
    fetchEarnings();
  };

  // Table columns
  const columns = [
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
      title: "EARNING TYPE",
      dataIndex: "earningType",
      key: "earningType",
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
      render: (earningType) => <span style={{ color: "#504d4dff" }}>{earningType}</span>
    },
    {
      title: "CALCULATION TYPE",
      dataIndex: "calculationType",
      key: "calculationType",
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
      render: (calculationType) => <span style={{ color: "#504d4dff" }}>{calculationType}</span>
    },
    {
      title: "CONSIDER FOR EPF",
      dataIndex: "considerForEPF",
      key: "considerForEPF",
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
      render: (considerForEPF) => <span style={{ color: "#504d4dff" }}>{considerForEPF}</span>
    },
    {
      title: "CONSIDER FOR ESI",
      dataIndex: "considerForESI",
      key: "considerForESI",
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
      render: (considerForESI) => <span style={{ color: "#504d4dff" }}>{considerForESI}</span>
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
      render: (status) => <span style={{ color: status === "Active" ? "#16a34a" : "#dc2626", fontWeight: 600 }}>{status}</span>
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
        // compute current status from authoritative rows state
        const authoritativeRow = rows.find((r) => r.key === record.key);
        const currentStatus = authoritativeRow ? getStatusFromRecord(authoritativeRow) : getStatusFromRecord(record);
        const toggleLabel = currentStatus === "Active" ? "Mark as Inactive" : "Mark as Active";

        const menu = (
          <Menu
            onClick={({ key }) => {
              if (key === "edit") {
                navigate(`/salary-components/edit/${record.key}`);
              } else if (key === "toggle-status") {
                handleStatusToggle(record);
              } else if (key === "delete") {
                showDeleteConfirm(record);
              }
            }}
            items={[
              { key: "edit", label: "Edit" },
              { key: "toggle-status", label: toggleLabel },
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
      }
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
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${activeTab === 'earnings' ? "active text-primary border-primary border-bottom" : "text-dark"}`}
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
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${activeTab === 'deductions' ? "active text-primary border-primary border-bottom" : "text-dark"}`}
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
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${activeTab === 'benefits' ? "active text-primary border-primary border-bottom" : "text-dark"}`}
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
              className={`nav-link fw-semibold px-2 px-md-3 py-2 ${activeTab === 'reimbursements' ? "active text-primary border-primary border-bottom" : "text-dark"}`}
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
              {hasEarnings ? (
                <Formik enableReinitialize>
                  {() => (
                    <Form className="form w-100">
                      <div className="table-responsive">
                        <Table
                          columns={columns}
                          dataSource={rows}
                          rowKey="key" // use stable key
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
                      {fetchError ? "Failed to load earnings" : "No earnings configured yet"}
                    </h3>
                    <div className="text-muted fw-semibold fs-5">
                      {fetchError
                        ? "Please try again later or contact support."
                        : "Add earning components to build your salary structure"}
                    </div>
                  </div>
                  <div className="d-flex flex-column flex-sm-row justify-content-center gap-3">
                    <button
                      className="btn btn-primary"
                      onClick={() => navigate("/salary-components/add/earning")}
                    >
                      <i className="bi bi-plus fs-2"></i> Add Earning Component
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
                        <li>Verify that the API endpoint /api/earnings exists</li>
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
