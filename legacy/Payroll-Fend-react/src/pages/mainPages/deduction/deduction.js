import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Input, Button, Spin, Tag, DatePicker, Select, Empty, Popconfirm, Modal, Form, Upload, Row, Col, Pagination } from "antd";
import { SearchOutlined, PlusOutlined, FilterOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, UploadOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const { Option } = Select;

export default function EmployeeDeductionList() {
  const navigate = useNavigate();

  // State
  const [loading, setLoading] = useState(false);
  const [deductions, setDeductions] = useState([]);
  const [filterEmployee, setFilterEmployee] = useState("");
  const [filterMonth, setFilterMonth] = useState(null); // dayjs object or null
  const [filterStatus, setFilterStatus] = useState("ALL");

  // Server-side pagination state
  const [currentPage, setCurrentPage] = useState(1); // 1-indexed for UI
  const [pageSize, setPageSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);
  const [employeesList, setEmployeesList] = useState([]);

  // Edit Modal State
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState(null);
  const [form] = Form.useForm();
  const [uploading, setUploading] = useState(false);
  const [proofUrl, setProofUrl] = useState("");
  const [proofPublicId, setProofPublicId] = useState("");
  const [editFileList, setEditFileList] = useState([]);

  // Delete handler
  const handleDelete = async (id) => {
    try {
      const res = await axios.delete(`${GlobalConst.API_URL}/api/employee-deductions/${id}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        }
      });
      if (res.status === 200) {
        successMsg("Deleted", "Deduction deleted successfully.", true);
        fetchDeductions();
      }
    } catch (err) {
      errorMsg("Delete Failed", err.response?.data?.message || "Failed to delete deduction.", true);
    }
  };

  // Edit modal opener
  const handleEditClick = (record) => {
    if (record.status !== "ACTIVE") {
      errorMsg("Edit Blocked", "Only ACTIVE deductions can be updated.", true);
      return;
    }
    setEditingRecord(record);
    setProofUrl(record.proofUrl || "");
    setProofPublicId(record.proofPublicId || "");
    setEditFileList(record.proofUrl ? [{
      uid: '-1',
      name: 'Existing Proof Document',
      status: 'done',
      url: record.proofUrl
    }] : []);
    
    form.setFieldsValue({
      employeeId: record.employeeId,
      deductionAmount: record.deductionAmount,
      deductionMonth: dayjs(record.deductionMonth, "YYYY-MM"),
      reason: record.reason,
      remarks: record.remarks
    });
    setIsEditModalOpen(true);
  };

  // Custom upload handler for edit proof file
  const handleUpload = async ({ file, onSuccess, onError }) => {
    setUploading(true);
    const formData = new FormData();
    formData.append("file", file);
    formData.append("employeeId", editingRecord?.employeeId);

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
        setProofUrl(data.proofUrl);
        setProofPublicId(data.proofPublicId);
        onSuccess(response.data);
        successMsg("Uploaded", "Proof file uploaded successfully.", true);
      } else {
        throw new Error(response.data?.message || "Upload failed");
      }
    } catch (err) {
      console.error("Upload error:", err);
      onError(err);
      errorMsg("Upload Failed", err.response?.data?.message || "Failed to upload file. Please try again.", true);
    } finally {
      setUploading(false);
    }
  };

  // Edit submit handler
  const handleEditSubmit = async (values) => {
    try {
      const payload = {
        employeeId: editingRecord.employeeId,
        deductionAmount: values.deductionAmount,
        deductionMonth: values.deductionMonth.format("YYYY-MM"),
        reason: values.reason,
        remarks: values.remarks,
        proofUrl: proofUrl,
        proofPublicId: proofPublicId
      };

      const res = await axios.put(
        `${GlobalConst.API_URL}/api/employee-deductions/${editingRecord.id}`,
        payload,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId
          }
        }
      );

      if (res.status === 200) {
        successMsg("Success", "Deduction updated successfully.", true);
        setIsEditModalOpen(false);
        setEditingRecord(null);
        fetchDeductions();
      }
    } catch (err) {
      errorMsg("Update Failed", err.response?.data?.message || "Failed to update deduction.", true);
    }
  };

  // Fetch or local storage initialization
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");

  // Load deductions (server-side pagination + filters)
  const fetchDeductions = async (page = currentPage, size = pageSize) => {
    // Guard against invalid/NaN values (e.g. when called from an event handler)
    const safePage = Number.isInteger(page) && page > 0 ? page : 1;
    const safeSize = Number.isInteger(size) && size > 0 ? size : 10;

    setLoading(true);
    try {
      const params = {
        page: safePage - 1, // backend is 0-indexed
        size: safeSize
      };
      if (filterEmployee) params.search = filterEmployee;
      if (filterMonth) params.month = filterMonth.format("YYYY-MM");
      if (filterStatus && filterStatus !== "ALL") params.status = filterStatus;

      const response = await axios.get(`${GlobalConst.API_URL}/api/employee-deductions`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
        params
      });

      if (response.data && (response.data.status === 200 || response.data.code === 0)) {
        const data = response.data.data || response.data.deductions || [];
        setDeductions(data);
        setTotalElements(response.data.totalElements ?? data.length);
      } else {
        throw new Error("Failed to load");
      }
    } catch (error) {
      console.error("Failed to fetch deductions from backend:", error);
      errorMsg(
        "Fetch Error", 
        error.response?.data?.message || "Failed to load deductions from the server.", 
        true
      );
      setDeductions([]);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  };

  // Handle page / page-size change from the Pagination component
  const handlePageChange = (page, size) => {
    setCurrentPage(page);
    setPageSize(size);
    fetchDeductions(page, size);
  };

  // Fetch employees list for autocomplete/filters
  const fetchEmployees = async () => {
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
      console.warn("Failed to load employees for dropdown.");
    }
  };

  useEffect(() => {
    fetchEmployees();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Re-fetch from page 1 whenever a filter changes (server-side filtering)
  useEffect(() => {
    setCurrentPage(1);
    fetchDeductions(1, pageSize);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterEmployee, filterMonth, filterStatus]);

  // Records come already filtered & paginated from the server
  const filteredDeductions = deductions;

  // Format currency helper
  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency: "INR",
      minimumFractionDigits: 2
    }).format(amount);
  };

  return (
    <>
      <Helmet>
        <title>Employee Deductions | HRMS Admin</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white min-vh-100">
        <div className="p-6">
          {/* Header */}
          <div className="d-flex justify-content-between align-items-center mb-6">
            <div>
              <h2 className="fw-bolder text-gray-900 mb-1">Employee Deductions</h2>
              <div className="text-muted fs-6">Manage company-driven deductions for salary runs.</div>
            </div>
            <div className="d-flex gap-2">
              <Button 
                onClick={() => fetchDeductions(currentPage, pageSize)}
                icon={<ReloadOutlined />}
                disabled={loading}
                style={{ height: '41px' }}
              />
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={() => navigate("/employee-deductions/bulk-add")}
                style={{ height: '41px', backgroundColor: '#1B84FF', borderColor: '#1B84FF' }}
                className="fw-semibold"
              >
                Add Deduction
              </Button>
            </div>
          </div>

          {/* Filters Card */}
          <div className="card border-0 shadow-sm mb-6 bg-light-purple" style={{ backgroundColor: "#F9F9FC", border: "1px solid #E1E3EA" }}>
            <div className="card-body py-4">
              <div className="row g-4 align-items-center">
                {/* Employee Search */}
                <div className="col-md-4">
                  <label className="form-label fw-bold text-gray-700 mb-2">Search Employee</label>
                  <Input
                    placeholder="Search by name or employee ID..."
                    prefix={<SearchOutlined className="text-muted" />}
                    value={filterEmployee}
                    onChange={(e) => setFilterEmployee(e.target.value)}
                    style={{ height: "41px", borderRadius: "6px" }}
                    allowClear
                  />
                </div>

                {/* Deduction Month Select */}
                <div className="col-md-3">
                  <label className="form-label fw-bold text-gray-700 mb-2">Deduction Month</label>
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

                {/* Status Dropdown */}
                <div className="col-md-3">
                  <label className="form-label fw-bold text-gray-700 mb-2">Status</label>
                  <Select
                    value={filterStatus}
                    onChange={(val) => setFilterStatus(val)}
                    style={{ width: "100%", height: "41px" }}
                    placeholder="Select Status"
                  >
                    <Option value="ALL">All Statuses</Option>
                    <Option value="ACTIVE">ACTIVE</Option>
                    <Option value="PROCESSED">PROCESSED</Option>
                  </Select>
                </div>

                {/* Reset Filters button */}
                <div className="col-md-2 d-flex align-items-end mt-5">
                  <Button
                    onClick={() => {
                      setFilterEmployee("");
                      setFilterMonth(null);
                      setFilterStatus("ALL");
                    }}
                    type="text"
                    icon={<FilterOutlined />}
                    className="text-primary fw-semibold"
                  >
                    Clear Filters
                  </Button>
                </div>
              </div>
            </div>
          </div>

          {/* List Card */}
          <div className="card border-0 shadow-sm" style={{ border: "1px solid #E1E3EA" }}>
            <div className="card-header bg-white py-4">
              <h5 className="card-title fw-bold text-gray-800 m-0">
                Deduction Records
                <span className="text-muted fs-7 ms-2">({totalElements} entries found)</span>
              </h5>
            </div>
            <div className="card-body p-0">
              {loading ? (
                <div className="text-center py-10">
                  <Spin size="large" />
                  <div className="mt-3 text-muted">Fetching deduction records...</div>
                </div>
              ) : filteredDeductions.length === 0 ? (
                <div className="py-10 text-center">
                  <Empty description="No deductions matching your filters." />
                </div>
              ) : (
                <div className="table-responsive">
                  <table className="table table-hover align-middle mb-0">
                    <thead>
                      <tr className="bg-light text-muted fw-bold fs-7 text-uppercase">
                        <th className="ps-6 py-4">Employee</th>
                        <th className="py-4">Amount</th>
                        <th className="py-4">Month</th>
                        <th className="py-4">Reason / Remarks</th>
                        <th className="py-4">Status</th>
                        <th className="py-4">Proof</th>
                        <th className="py-4">Created By</th>
                        <th className="py-4">Created Date</th>
                        <th className="pe-6 py-4 text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="fs-6">
                      {filteredDeductions.map((item) => (
                        <tr key={item.id}>
                          {/* Employee info */}
                          <td className="ps-6">
                            <div className="d-flex flex-column">
                              <span className="fw-bolder text-gray-800">{item.employeeName}</span>
                              <span className="text-muted fs-7">{item.employeeNumber || item.employeeId}</span>
                            </div>
                          </td>
                          {/* Amount */}
                          <td className="fw-bolder text-gray-800">
                            {formatCurrency(item.deductionAmount)}
                          </td>
                          {/* Month */}
                          <td>
                            <span className="fw-semibold text-gray-600">
                              {item.deductionMonth ? dayjs(item.deductionMonth).format("MMMM YYYY") : "N/A"}
                            </span>
                          </td>
                          {/* Reason and remarks */}
                          <td>
                            <div className="d-flex flex-column">
                              <span className="fw-bolder text-gray-700">{item.reason}</span>
                              {item.remarks && <span className="text-muted fs-7">{item.remarks}</span>}
                            </div>
                          </td>
                          {/* Status Badge */}
                          <td>
                            <Tag 
                              color={item.status === "PROCESSED" ? "success" : "warning"}
                              className="fw-bold px-2 py-0.5"
                            >
                              {item.status}
                            </Tag>
                          </td>
                          {/* Proof */}
                          <td>
                            {item.proofUrl ? (
                              <a
                                href={item.proofUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="btn btn-sm btn-light-primary fw-bold py-1 px-3"
                                style={{ fontSize: "11px", borderRadius: "6px" }}
                              >
                                View Proof
                              </a>
                            ) : (
                              <span className="text-gray-400 fs-7">-</span>
                            )}
                          </td>
                          {/* Created By */}
                          <td className="text-gray-600 fs-7">{item.createdBy}</td>
                          {/* Created Date */}
                          <td className="text-gray-600 fs-7">
                            {dayjs(item.createdAt).format("DD MMM YYYY, hh:mm A")}
                          </td>
                          {/* Actions */}
                          <td className="pe-6 text-end">
                            {item.status === "ACTIVE" ? (
                              <div className="d-flex justify-content-end gap-2">
                                <Button 
                                  size="small"
                                  onClick={() => handleEditClick(item)}
                                  icon={<EditOutlined />}
                                  className="btn btn-icon btn-bg-light btn-active-color-primary"
                                />
                                <Popconfirm
                                  title="Are you sure you want to delete this deduction?"
                                  onConfirm={() => handleDelete(item.id)}
                                  okText="Yes"
                                  cancelText="No"
                                >
                                  <Button 
                                    size="small"
                                    danger
                                    icon={<DeleteOutlined />}
                                    className="btn btn-icon btn-bg-light btn-active-color-danger"
                                  />
                                </Popconfirm>
                              </div>
                            ) : (
                              <span className="text-muted fs-7">Locked</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Pagination */}
              {!loading && totalElements > 0 && (
                <div className="d-flex justify-content-end p-4">
                  <Pagination
                    current={currentPage}
                    pageSize={pageSize}
                    total={totalElements}
                    showSizeChanger
                    pageSizeOptions={["10", "20", "50", "100"]}
                    onChange={handlePageChange}
                    onShowSizeChange={handlePageChange}
                    showTotal={(total, range) => `${range[0]}-${range[1]} of ${total} deductions`}
                  />
                </div>
              )}
            </div>
          </div>

        </div>

        {/* Edit Modal */}
        <Modal
          title={<h4 className="fw-bolder text-gray-900 m-0">Edit Salary Deduction</h4>}
          open={isEditModalOpen}
          onCancel={() => {
            setIsEditModalOpen(false);
            setEditingRecord(null);
          }}
          footer={null}
          destroyOnClose
          width={600}
        >
          <Form
            form={form}
            layout="vertical"
            onFinish={handleEditSubmit}
            style={{ marginTop: "20px" }}
          >
            {/* Read-only Employee Display */}
            <Form.Item label={<span className="fw-semibold text-gray-700">Employee</span>}>
              <Input
                value={editingRecord ? `${editingRecord.employeeName} (${editingRecord.employeeNumber || "N/A"})` : ""}
                disabled
                style={{ height: "40px", borderRadius: "6px" }}
              />
            </Form.Item>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label={<span className="fw-semibold text-gray-700">Deduction Month</span>}
                  name="deductionMonth"
                  rules={[{ required: true, message: "Please select deduction month" }]}
                >
                  <DatePicker
                    picker="month"
                    format="MMMM YYYY"
                    style={{ width: "100%", height: "40px", borderRadius: "6px" }}
                  />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label={<span className="fw-semibold text-gray-700">Amount (INR)</span>}
                  name="deductionAmount"
                  rules={[
                    { required: true, message: "Please enter deduction amount" },
                    {
                      validator: (_, value) =>
                        value > 0 ? Promise.resolve() : Promise.reject(new Error("Amount must be greater than zero"))
                    }
                  ]}
                >
                  <Input
                    type="number"
                    min={1}
                    style={{ height: "40px", borderRadius: "6px" }}
                  />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item
              label={<span className="fw-semibold text-gray-700">Reason</span>}
              name="reason"
              rules={[{ required: true, message: "Please enter reason" }]}
            >
              <Input placeholder="e.g. Laptop Damage" style={{ height: "40px", borderRadius: "6px" }} />
            </Form.Item>

            <Form.Item
              label={<span className="fw-semibold text-gray-700">Remarks</span>}
              name="remarks"
            >
              <Input.TextArea rows={3} placeholder="Additional details..." style={{ borderRadius: "6px" }} />
            </Form.Item>

            {/* Proof Upload */}
            <Form.Item label={<span className="fw-semibold text-gray-700">Supporting Proof</span>}>
              <Upload
                customRequest={handleUpload}
                fileList={editFileList}
                onChange={({ fileList }) => setEditFileList(fileList)}
                onRemove={() => {
                  setProofUrl("");
                  setProofPublicId("");
                  setEditFileList([]);
                }}
                maxCount={1}
              >
                <Button
                  icon={uploading ? <Spin size="small" /> : <UploadOutlined />}
                  disabled={uploading}
                  style={{ height: "40px", borderRadius: "6px" }}
                >
                  {uploading ? "Uploading..." : "Click to upload proof (JPG, PNG, PDF max 5MB)"}
                </Button>
              </Upload>
            </Form.Item>

            <div className="d-flex justify-content-end gap-2 mt-6">
              <Button
                onClick={() => {
                  setIsEditModalOpen(false);
                  setEditingRecord(null);
                }}
                style={{ height: "40px", borderRadius: "6px" }}
              >
                Cancel
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                loading={uploading}
                style={{ height: "40px", borderRadius: "6px", backgroundColor: "#1B84FF", borderColor: "#1B84FF" }}
              >
                Save Changes
              </Button>
            </div>
          </Form>
        </Modal>

      </div>
    </>
  );
}
