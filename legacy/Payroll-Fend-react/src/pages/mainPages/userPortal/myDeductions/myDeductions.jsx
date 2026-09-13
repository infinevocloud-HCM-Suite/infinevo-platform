import React, { useState, useEffect, useMemo, useCallback } from "react";
import { Helmet } from "react-helmet-async";
import { Spin, Tag, DatePicker, Select, Empty, Button } from "antd";
import { ReloadOutlined, FilterOutlined } from "@ant-design/icons";
import { PiMinusCircleDuotone, PiCalendarCheckDuotone, PiCoinsDuotone, PiReceiptDuotone } from "react-icons/pi";
import dayjs from "dayjs";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg } from "../../../../shared/helpers/msgHelper";

const { Option } = Select;

export default function MyDeductions() {
  const [loading, setLoading] = useState(false);
  const [deductions, setDeductions] = useState([]);
  const [filterMonth, setFilterMonth] = useState(null);
  const [filterStatus, setFilterStatus] = useState("ALL");

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");

  const fetchMyDeductions = useCallback(async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/employee-deductions/my-deductions`,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data && response.data.status === 200) {
        setDeductions(response.data.data || []);
      } else {
        throw new Error(response.data?.message || "Failed to load deductions");
      }
    } catch (error) {
      console.error("Failed to fetch my deductions:", error);
      errorMsg(
        "Fetch Error",
        error.response?.data?.message || "Failed to load your deduction records.",
        true
      );
      setDeductions([]);
    } finally {
      setLoading(false);
    }
  }, [token, organizationId]);

  useEffect(() => {
    fetchMyDeductions();
  }, [fetchMyDeductions]);

  // Filter deductions
  const filteredDeductions = useMemo(() => {
    return deductions.filter((item) => {
      if (filterMonth) {
        const targetMonth = filterMonth.format("YYYY-MM");
        if (item.deductionMonth !== targetMonth) return false;
      }
      if (filterStatus !== "ALL") {
        if (item.status !== filterStatus) return false;
      }
      return true;
    });
  }, [deductions, filterMonth, filterStatus]);

  // Financial calculations
  const totalAmount = useMemo(() => {
    return deductions.reduce((acc, curr) => acc + (Number(curr.deductionAmount) || 0), 0);
  }, [deductions]);

  const activeCount = useMemo(() => {
    return deductions.filter((d) => d.status === "ACTIVE").length;
  }, [deductions]);

  const processedCount = useMemo(() => {
    return deductions.filter((d) => d.status === "PROCESSED").length;
  }, [deductions]);

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      minimumFractionDigits: 2,
    }).format(amount || 0);
  };

  return (
    <>
      <Helmet>
        <title>My Deductions | Employee Portal</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white min-vh-100">
        <div className="p-6">
          {/* Header */}
          <div className="d-flex justify-content-between align-items-center mb-6">
            <div>
              <h2 className="fw-bolder text-gray-900 mb-1">My Salary Deductions</h2>
              <div className="text-muted fs-6">
                Review one-off company deductions and attached supporting proofs.
              </div>
            </div>
            <Button
              onClick={fetchMyDeductions}
              icon={<ReloadOutlined />}
              loading={loading}
              style={{ height: "41px", borderRadius: "6px" }}
            >
              Refresh
            </Button>
          </div>

          {/* Metric KPI Cards */}
          <div className="row g-5 mb-6">
            <div className="col-md-4">
              <div
                className="card border-0 shadow-sm p-5"
                style={{ backgroundColor: "#F8F9FA", borderRadius: "12px", border: "1px solid #E9ECEF" }}
              >
                <div className="d-flex align-items-center justify-content-between">
                  <div>
                    <span className="text-muted fw-semibold fs-7 text-uppercase">Total Deducted</span>
                    <h3 className="fw-bolder text-gray-900 mb-0 mt-2">{formatCurrency(totalAmount)}</h3>
                  </div>
                  <div
                    className="d-flex align-items-center justify-content-center"
                    style={{
                      width: "48px",
                      height: "48px",
                      borderRadius: "10px",
                      backgroundColor: "rgba(241, 65, 108, 0.1)",
                      color: "#F1416C",
                    }}
                  >
                    <PiCoinsDuotone className="fs-2x" />
                  </div>
                </div>
              </div>
            </div>

            <div className="col-md-4">
              <div
                className="card border-0 shadow-sm p-5"
                style={{ backgroundColor: "#F8F9FA", borderRadius: "12px", border: "1px solid #E9ECEF" }}
              >
                <div className="d-flex align-items-center justify-content-between">
                  <div>
                    <span className="text-muted fw-semibold fs-7 text-uppercase">Scheduled / Active</span>
                    <h3 className="fw-bolder text-warning mb-0 mt-2">{activeCount}</h3>
                  </div>
                  <div
                    className="d-flex align-items-center justify-content-center"
                    style={{
                      width: "48px",
                      height: "48px",
                      borderRadius: "10px",
                      backgroundColor: "rgba(255, 199, 0, 0.1)",
                      color: "#FFC700",
                    }}
                  >
                    <PiMinusCircleDuotone className="fs-2x" />
                  </div>
                </div>
              </div>
            </div>

            <div className="col-md-4">
              <div
                className="card border-0 shadow-sm p-5"
                style={{ backgroundColor: "#F8F9FA", borderRadius: "12px", border: "1px solid #E9ECEF" }}
              >
                <div className="d-flex align-items-center justify-content-between">
                  <div>
                    <span className="text-muted fw-semibold fs-7 text-uppercase">Processed in Payroll</span>
                    <h3 className="fw-bolder text-success mb-0 mt-2">{processedCount}</h3>
                  </div>
                  <div
                    className="d-flex align-items-center justify-content-center"
                    style={{
                      width: "48px",
                      height: "48px",
                      borderRadius: "10px",
                      backgroundColor: "rgba(80, 205, 137, 0.1)",
                      color: "#50CD89",
                    }}
                  >
                    <PiCalendarCheckDuotone className="fs-2x" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Filters Card */}
          <div
            className="card border-0 shadow-sm mb-6"
            style={{ backgroundColor: "#F9F9FC", border: "1px solid #E1E3EA", borderRadius: "10px" }}
          >
            <div className="card-body py-4">
              <div className="row g-4 align-items-center">
                <div className="col-md-4">
                  <label className="form-label fw-bold text-gray-700 mb-2">Filter by Month</label>
                  <DatePicker
                    picker="month"
                    format="MMMM YYYY"
                    value={filterMonth}
                    onChange={(date) => setFilterMonth(date)}
                    style={{ width: "100%", height: "41px", borderRadius: "6px" }}
                    placeholder="Select month"
                    allowClear
                  />
                </div>

                <div className="col-md-4">
                  <label className="form-label fw-bold text-gray-700 mb-2">Status</label>
                  <Select
                    value={filterStatus}
                    onChange={(val) => setFilterStatus(val)}
                    style={{ width: "100%", height: "41px" }}
                  >
                    <Option value="ALL">All Statuses</Option>
                    <Option value="ACTIVE">ACTIVE (Upcoming Payrun)</Option>
                    <Option value="PROCESSED">PROCESSED (Completed)</Option>
                  </Select>
                </div>

                <div className="col-md-4 d-flex align-items-end mt-5">
                  <Button
                    onClick={() => {
                      setFilterMonth(null);
                      setFilterStatus("ALL");
                    }}
                    type="text"
                    icon={<FilterOutlined />}
                    className="text-primary fw-semibold"
                  >
                    Reset Filters
                  </Button>
                </div>
              </div>
            </div>
          </div>

          {/* Deductions Table Card */}
          <div className="card border-0 shadow-sm" style={{ border: "1px solid #E1E3EA", borderRadius: "10px" }}>
            <div className="card-header bg-white py-4 border-0">
              <h5 className="card-title fw-bold text-gray-800 m-0">
                Deduction Records
                <span className="text-muted fs-7 ms-2">({filteredDeductions.length} records)</span>
              </h5>
            </div>

            <div className="card-body p-0">
              {loading ? (
                <div className="text-center py-10">
                  <Spin size="large" />
                  <div className="mt-3 text-muted">Loading your deductions...</div>
                </div>
              ) : filteredDeductions.length === 0 ? (
                <div className="py-10 text-center">
                  <Empty description="No deduction records found." />
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead>
                      <tr className="bg-light text-muted fw-bold fs-7 text-uppercase">
                        <th className="ps-6 py-4">Deduction Month</th>
                        <th className="py-4">Amount</th>
                        <th className="py-4">Reason / Description</th>
                        <th className="py-4">Remarks</th>
                        <th className="py-4">Status</th>
                        <th className="pe-6 py-4 text-end">Proof Document</th>
                      </tr>
                    </thead>
                    <tbody className="fs-6">
                      {filteredDeductions.map((item) => (
                        <tr key={item.id}>
                          <td className="ps-6">
                            <span className="fw-bolder text-gray-800">
                              {item.deductionMonth ? dayjs(item.deductionMonth).format("MMMM YYYY") : "N/A"}
                            </span>
                          </td>
                          <td className="fw-bolder text-danger fs-5">
                            - {formatCurrency(item.deductionAmount)}
                          </td>
                          <td>
                            <span className="fw-bold text-gray-800">{item.reason}</span>
                          </td>
                          <td>
                            <span className="text-muted fs-7">{item.remarks || "-"}</span>
                          </td>
                          <td>
                            <Tag
                              color={item.status === "PROCESSED" ? "success" : "warning"}
                              className="fw-bold px-3 py-1"
                              style={{ borderRadius: "4px" }}
                            >
                              {item.status}
                            </Tag>
                          </td>
                          <td className="pe-6 text-end">
                            {item.proofUrl ? (
                              <a
                                href={item.proofUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="btn btn-sm btn-light-primary fw-bold py-1 px-3 d-inline-flex align-items-center gap-1"
                                style={{ fontSize: "12px", borderRadius: "6px" }}
                              >
                                <PiReceiptDuotone className="fs-5" />
                                View Proof
                              </a>
                            ) : (
                              <span className="text-gray-400 fs-7">No attachment</span>
                            )}
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
    </>
  );
}
