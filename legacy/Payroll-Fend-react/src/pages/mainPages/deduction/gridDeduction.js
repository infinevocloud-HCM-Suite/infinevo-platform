import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Select, InputNumber, DatePicker, Input, Button, Spin, Card, Tooltip, Upload } from "antd";
import {
  ArrowLeftOutlined,
  SaveOutlined,
  PlusOutlined,
  DeleteOutlined,
  UserAddOutlined,
  UploadOutlined,
  CheckCircleFilled
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const { Option } = Select;

// Predefined deduction types + "Other"
const DEDUCTION_TYPES = [
  "Salary Advance",
  "Loan Recovery",
  "Asset Damage",
  "Penalty",
  "Notice Period Recovery",
  "Excess Payment Recovery",
  "Insurance Premium",
  "Other"
];

export default function GridDeduction() {
  const navigate = useNavigate();

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");

  const [employeesList, setEmployeesList] = useState([]);
  const [loadingEmployees, setLoadingEmployees] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Grid state: array of employee blocks, each with rows of deduction entries
  const [employeeBlocks, setEmployeeBlocks] = useState([]);

  // Fetch employees
  const fetchEmployees = async () => {
    setLoadingEmployees(true);
    try {
      const response = await axios.get(`${GlobalConst.API_URL}/api/employees`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
        params: { page: 0, size: 100 }
      });
      if (response.data && response.data.status === 200) {
        setEmployeesList(response.data.data || []);
      }
    } catch (err) {
      console.error("Failed to load employees:", err);
      errorMsg("Error", "Failed to load employees. Please try again.", true);
    } finally {
      setLoadingEmployees(false);
    }
  };

  useEffect(() => {
    fetchEmployees();
  }, []);

  const createEmptyRow = () => ({
    key: Date.now() + Math.random(),
    deductionType: null,
    customType: "",
    amount: null,
    month: null,
    reason: "",
    proofUrl: null,
    proofPublicId: null,
    uploading: false
  });

  // Add a new employee block
  const addEmployeeBlock = () => {
    setEmployeeBlocks([
      ...employeeBlocks,
      {
        key: Date.now() + Math.random(),
        employeeId: null,
        rows: [createEmptyRow()]
      }
    ]);
  };

  // Remove an employee block
  const removeEmployeeBlock = (blockKey) => {
    setEmployeeBlocks(employeeBlocks.filter((b) => b.key !== blockKey));
  };

  // Set employee for a block
  const setBlockEmployee = (blockKey, employeeId) => {
    setEmployeeBlocks(
      employeeBlocks.map((b) => (b.key === blockKey ? { ...b, employeeId } : b))
    );
  };

  // Add a deduction row to a block
  const addRow = (blockKey) => {
    setEmployeeBlocks(
      employeeBlocks.map((b) =>
        b.key === blockKey ? { ...b, rows: [...b.rows, createEmptyRow()] } : b
      )
    );
  };

  // Remove a row from a block
  const removeRow = (blockKey, rowKey) => {
    setEmployeeBlocks(
      employeeBlocks.map((b) =>
        b.key === blockKey
          ? { ...b, rows: b.rows.filter((r) => r.key !== rowKey) }
          : b
      )
    );
  };

  // Update a field in a row
  const updateRow = (blockKey, rowKey, field, value) => {
    setEmployeeBlocks(
      employeeBlocks.map((b) =>
        b.key === blockKey
          ? {
              ...b,
              rows: b.rows.map((r) =>
                r.key === rowKey ? { ...r, [field]: value } : r
              )
            }
          : b
      )
    );
  };

  // Validate file before upload (JPG/PNG/WEBP/PDF, max 5MB)
  const beforeUploadProof = (file, block) => {
    const isValidType =
      file.type === "image/jpeg" ||
      file.type === "image/jpg" ||
      file.type === "image/png" ||
      file.type === "image/webp" ||
      file.type === "application/pdf";

    if (!isValidType) {
      errorMsg("Format Error", "Only JPG, JPEG, PNG, WEBP, and PDF files are allowed.", true);
      return Upload.LIST_IGNORE;
    }
    const isLt5M = file.size / 1024 / 1024 < 5;
    if (!isLt5M) {
      errorMsg("Size Error", "File must be smaller than 5 MB.", true);
      return Upload.LIST_IGNORE;
    }
    if (!block.employeeId) {
      errorMsg("Validation Error", "Please select an employee before uploading proof.", true);
      return Upload.LIST_IGNORE;
    }
    return true;
  };

  // Upload proof to Cloudinary via existing /upload endpoint
  const handleProofUpload = async (blockKey, rowKey, employeeId, file) => {
    updateRow(blockKey, rowKey, "uploading", true);
    const formData = new FormData();
    formData.append("file", file);
    formData.append("employeeId", employeeId);

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/employee-deductions/upload`,
        formData,
        {
          headers: {
            "Content-Type": "multipart/form-data",
            Authorization: `Bearer ${token}`,
            organizationId: organizationId
          }
        }
      );
      if (response.data && response.data.status === 200) {
        const data = response.data.data;
        // Update both proof fields together
        setEmployeeBlocks((prev) =>
          prev.map((b) =>
            b.key === blockKey
              ? {
                  ...b,
                  rows: b.rows.map((r) =>
                    r.key === rowKey
                      ? { ...r, proofUrl: data.proofUrl, proofPublicId: data.proofPublicId, uploading: false }
                      : r
                  )
                }
              : b
          )
        );
        successMsg("Uploaded", "Proof uploaded successfully.", true);
      } else {
        throw new Error(response.data?.message || "Upload failed");
      }
    } catch (err) {
      console.error("Proof upload error:", err);
      updateRow(blockKey, rowKey, "uploading", false);
      errorMsg("Upload Failed", err.response?.data?.message || "Failed to upload proof.", true);
    }
  };

  // Remove uploaded proof from a row
  const removeProof = (blockKey, rowKey) => {
    setEmployeeBlocks((prev) =>
      prev.map((b) =>
        b.key === blockKey
          ? {
              ...b,
              rows: b.rows.map((r) =>
                r.key === rowKey ? { ...r, proofUrl: null, proofPublicId: null } : r
              )
            }
          : b
      )
    );
  };

  // Employees already selected (to prevent duplicate selection)
  const selectedEmployeeIds = employeeBlocks.map((b) => b.employeeId).filter(Boolean);

  // Build and submit payload
  const handleSubmit = async () => {
    const entries = [];

    for (const block of employeeBlocks) {
      if (!block.employeeId) {
        errorMsg("Validation Error", "Please select an employee for each block.", true);
        return;
      }
      for (const row of block.rows) {
        const finalType =
          row.deductionType === "Other" ? (row.customType || "").trim() : row.deductionType;

        if (!finalType) {
          errorMsg("Validation Error", "Please select or enter a deduction type for all rows.", true);
          return;
        }
        if (!row.amount || row.amount <= 0) {
          errorMsg("Validation Error", "Please enter a valid amount for all rows.", true);
          return;
        }
        if (!row.month) {
          errorMsg("Validation Error", "Please select a month for all rows.", true);
          return;
        }

        entries.push({
          employeeId: block.employeeId,
          deductionType: finalType,
          deductionAmount: row.amount,
          deductionMonth: row.month.format("YYYY-MM"),
          reason: row.reason || finalType,
          remarks: "",
          proofUrl: row.proofUrl || null,
          proofPublicId: row.proofPublicId || null
        });
      }
    }

    if (entries.length === 0) {
      errorMsg("Validation Error", "Please add at least one deduction entry.", true);
      return;
    }

    setSubmitting(true);
    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/employee-deductions/grid`,
        { entries },
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId
          }
        }
      );

      if (response.status === 201 || response.data?.status === 201) {
        const data = response.data?.data;
        const successCount = data?.successCount || 0;
        const failedCount = data?.failedCount || 0;

        if (failedCount > 0) {
          successMsg(
            "Partial Success",
            `${successCount} deduction(s) created. ${failedCount} failed.`,
            true
          );
        } else {
          successMsg("Success", `${successCount} deduction(s) created successfully.`, true);
        }
        navigate("/employee-deductions");
      } else {
        throw new Error("API failed");
      }
    } catch (error) {
      console.error("Failed to save grid deductions:", error);
      errorMsg(
        "Save Failed",
        error.response?.data?.message || "Failed to save deductions to the server.",
        true
      );
    } finally {
      setSubmitting(false);
    }
  };

  const disabledDate = (current) => current && current.isBefore(dayjs().startOf("month"));

  return (
    <>
      <Helmet>
        <title>Bulk Deduction Entry | HRMS Admin</title>
      </Helmet>

      <div className="container-fluid p-0 min-vh-100" style={{ backgroundColor: "#F4F6F9" }}>
        {/* Header */}
        <div className="w-100 bg-white px-4 py-3 d-flex align-items-center border-bottom mb-4">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate("/employee-deductions")}
            className="me-3"
          />
          <h4 className="mb-0 fw-bold text-gray-900" style={{ fontSize: "1.1rem" }}>
            Bulk Deduction Entry (Grid)
          </h4>
        </div>

        <div className="container-fluid px-4 pb-6">
          {/* Add Employee button */}
          <div className="d-flex justify-content-between align-items-center mb-3">
            <span className="text-muted fs-7">
              Add employees and their deductions. Each employee can have multiple deduction types.
            </span>
            <Button
              type="dashed"
              icon={<UserAddOutlined />}
              onClick={addEmployeeBlock}
              disabled={loadingEmployees}
            >
              Add Employee
            </Button>
          </div>

          {/* Empty state */}
          {employeeBlocks.length === 0 && (
            <Card className="text-center text-muted" style={{ borderRadius: "10px" }}>
              No employees added yet. Click "Add Employee" to start.
            </Card>
          )}

          {/* Employee blocks */}
          {employeeBlocks.map((block) => (
            <Card
              key={block.key}
              className="mb-3"
              style={{ borderRadius: "10px", border: "1px solid #E4E6EF" }}
              bodyStyle={{ padding: "16px" }}
            >
              {/* Employee selector row */}
              <div className="d-flex align-items-center gap-2 mb-3">
                <Select
                  showSearch
                  placeholder="Select employee..."
                  loading={loadingEmployees}
                  value={block.employeeId}
                  onChange={(val) => setBlockEmployee(block.key, val)}
                  style={{ minWidth: "300px" }}
                  filterOption={(input, option) =>
                    (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                  }
                  options={employeesList
                    .filter(
                      (emp) =>
                        emp.employeeId === block.employeeId ||
                        !selectedEmployeeIds.includes(emp.employeeId)
                    )
                    .map((emp) => ({
                      value: emp.employeeId,
                      label: `${emp.firstName} ${emp.lastName} (${emp.employeeNumber || "N/A"})`
                    }))}
                />
                <Tooltip title="Remove this employee">
                  <Button
                    danger
                    type="text"
                    icon={<DeleteOutlined />}
                    onClick={() => removeEmployeeBlock(block.key)}
                  />
                </Tooltip>
              </div>

              {/* Sub-grid: deduction rows */}
              <div style={{ overflowX: "auto" }}>
                <table className="table table-sm mb-2" style={{ minWidth: "920px" }}>
                  <thead>
                    <tr style={{ fontSize: "12px", color: "#7E8299" }}>
                      <th style={{ width: "200px" }}>Deduction Type</th>
                      <th style={{ width: "160px" }}>Amount (INR)</th>
                      <th style={{ width: "150px" }}>Month</th>
                      <th>Reason</th>
                      <th style={{ width: "170px" }}>Proof</th>
                      <th style={{ width: "50px" }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {block.rows.map((row) => (
                      <tr key={row.key}>
                        <td>
                          <Select
                            placeholder="Select type"
                            value={row.deductionType}
                            onChange={(val) => updateRow(block.key, row.key, "deductionType", val)}
                            style={{ width: "100%" }}
                          >
                            {DEDUCTION_TYPES.map((type) => (
                              <Option key={type} value={type}>
                                {type}
                              </Option>
                            ))}
                          </Select>
                          {/* Custom type text box when "Other" is selected */}
                          {row.deductionType === "Other" && (
                            <Input
                              className="mt-1"
                              placeholder="Enter custom type"
                              value={row.customType}
                              onChange={(e) =>
                                updateRow(block.key, row.key, "customType", e.target.value)
                              }
                            />
                          )}
                        </td>
                        <td>
                          <InputNumber
                            placeholder="e.g. 5000"
                            value={row.amount}
                            min={1}
                            onChange={(val) => updateRow(block.key, row.key, "amount", val)}
                            style={{ width: "100%" }}
                            formatter={(value) => `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ",")}
                            parser={(value) => value.replace(/\$\s?|(,*)/g, "")}
                          />
                        </td>
                        <td>
                          <DatePicker
                            picker="month"
                            format="MMM YYYY"
                            value={row.month}
                            disabledDate={disabledDate}
                            onChange={(val) => updateRow(block.key, row.key, "month", val)}
                            style={{ width: "100%" }}
                            placeholder="Month"
                          />
                        </td>
                        <td>
                          <Input
                            placeholder="Optional reason"
                            value={row.reason}
                            onChange={(e) => updateRow(block.key, row.key, "reason", e.target.value)}
                          />
                        </td>
                        <td>
                          {row.proofUrl ? (
                            <div className="d-flex align-items-center gap-1">
                              <CheckCircleFilled style={{ color: "#52c41a" }} />
                              <a href={row.proofUrl} target="_blank" rel="noopener noreferrer" style={{ fontSize: "12px" }}>
                                View
                              </a>
                              <Button
                                type="text"
                                size="small"
                                danger
                                icon={<DeleteOutlined />}
                                onClick={() => removeProof(block.key, row.key)}
                              />
                            </div>
                          ) : (
                            <Upload
                              showUploadList={false}
                              accept=".jpg,.jpeg,.png,.webp,.pdf"
                              beforeUpload={(file) => beforeUploadProof(file, block)}
                              customRequest={({ file }) =>
                                handleProofUpload(block.key, row.key, block.employeeId, file)
                              }
                            >
                              <Button
                                size="small"
                                icon={row.uploading ? <Spin size="small" /> : <UploadOutlined />}
                                disabled={row.uploading}
                              >
                                {row.uploading ? "Uploading..." : "Upload"}
                              </Button>
                            </Upload>
                          )}
                        </td>
                        <td>
                          {block.rows.length > 1 && (
                            <Button
                              danger
                              type="text"
                              size="small"
                              icon={<DeleteOutlined />}
                              onClick={() => removeRow(block.key, row.key)}
                            />
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <Button
                type="link"
                icon={<PlusOutlined />}
                onClick={() => addRow(block.key)}
                style={{ padding: 0 }}
              >
                Add Deduction Type
              </Button>
            </Card>
          ))}

          {/* Save button */}
          {employeeBlocks.length > 0 && (
            <div className="d-flex justify-content-end gap-3 mt-4">
              <Button onClick={() => navigate("/employee-deductions")} disabled={submitting}>
                Cancel
              </Button>
              <Button
                type="primary"
                icon={submitting ? <Spin size="small" /> : <SaveOutlined />}
                onClick={handleSubmit}
                disabled={submitting}
                style={{ backgroundColor: "#1B84FF", borderColor: "#1B84FF" }}
              >
                Save All Deductions
              </Button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
