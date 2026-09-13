import React, { useState, useEffect, useCallback, useMemo } from "react";
import { Helmet } from "react-helmet-async";
import {
  Select,
  InputNumber,
  Button,
  Modal,
  Spin,
  message,
  Popconfirm,
  Empty,
  Input,
  DatePicker,
  Table,
  Tag,
  Tooltip,
  Tabs,
  Alert,
  Upload,
} from "antd";
import {
  PlusOutlined,
  DeleteOutlined,
  UserOutlined,
  InfoCircleOutlined,
  SearchOutlined,
  EyeOutlined,
  EditOutlined,
  DownOutlined,
  UpOutlined,
  ArrowLeftOutlined,
  FileExcelOutlined,
  UploadOutlined,
  InboxOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DownloadOutlined,
} from "@ant-design/icons";
import dayjs from "dayjs";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { fetchLeaveAllocationsFromApi } from "../../../../shared/services/leaveStore";

const { Option } = Select;

const DEFAULT_YEAR = String(new Date().getFullYear());

const DEFAULT_LEAVE_TYPES = [
  "Sick Leave",
  "Earned Leave",
  "Casual Leave",
  "Compensatory Off",
  "Maternity Leave",
  "Paternity Leave",
];

const getAllocationSortScore = (item) => {
  const parseVal = (v) => {
    if (!v) return 0;
    const d = dayjs(v);
    return d.isValid() ? d.valueOf() : 0;
  };

  let maxTime = Math.max(parseVal(item.updatedAt), parseVal(item.createdAt));
  let maxId = 0;

  if (Array.isArray(item.leaveTypes)) {
    for (const lt of item.leaveTypes) {
      const ltTime = Math.max(parseVal(lt.updatedAt), parseVal(lt.createdAt));
      if (ltTime > maxTime) maxTime = ltTime;
      const idNum = Number(lt.id);
      if (!isNaN(idNum) && idNum > maxId) maxId = idNum;
    }
  }

  return { maxTime, maxId };
};

const compareAllocationsDesc = (a, b) => {
  const scoreA = getAllocationSortScore(a);
  const scoreB = getAllocationSortScore(b);
  if (scoreB.maxTime !== scoreA.maxTime) {
    return scoreB.maxTime - scoreA.maxTime;
  }
  return scoreB.maxId - scoreA.maxId;
};

export default function LeaveAllocation() {
  const [selectedYear, setSelectedYear] = useState(DEFAULT_YEAR);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeEmployees, setActiveEmployees] = useState([]);
  const [loadingEmployees, setLoadingEmployees] = useState(false);

  // View Mode: 'list' (History Table) | 'form' (Multi-Employee Cards Editor Form)
  const [viewMode, setViewMode] = useState("list");
  // Form Mode: 'add' (Multi-employee add) | 'view' (Single employee view/edit)
  const [formMode, setFormMode] = useState("add");

  // Saved allocations from database
  const [savedAllocations, setSavedAllocations] = useState([]);

  // Active working form allocations (for multi-employee cards)
  const [formAllocations, setFormAllocations] = useState([]);

  // Search in History Table
  const [tableSearch, setTableSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");

  // Server-side pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [totalElements, setTotalElements] = useState(0);

  // Selected row keys in History Table for bulk edit
  const [selectedHistoryEmpIds, setSelectedHistoryEmpIds] = useState([]);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(tableSearch);
      setCurrentPage(1);
    }, 300);
    return () => clearTimeout(handler);
  }, [tableSearch]);

  // Modal: Select Employees
  const [isSelectEmpModalOpen, setIsSelectEmpModalOpen] = useState(false);
  const [selectedEmpIds, setSelectedEmpIds] = useState([]);
  const [empSearchTerm, setEmpSearchTerm] = useState("");

  // Modal: Review Employee Allocation Details
  const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
  const [reviewEmployeeRecord, setReviewEmployeeRecord] = useState(null);
  const [loadingReview, setLoadingReview] = useState(false);

  // Modal: Sample Excel Formats (Insert vs Update)
  const [isSampleModalOpen, setIsSampleModalOpen] = useState(false);

  // Modal: Import New Leave Allocations (Insert Only)
  const [isImportModalOpen, setIsImportModalOpen] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [importResult, setImportResult] = useState(null);

  const handleResetImport = () => {
    setImportFile(null);
    setImportResult(null);
    setIsImportModalOpen(false);
  };

  const handleImportAllocations = async () => {
    if (!importFile) {
      message.error("Please select an Excel or CSV file to import.");
      return;
    }

    const formData = new FormData();
    formData.append("file", importFile);

    try {
      setUploading(true);
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/leave-allocation/import`,
        formData,
        {
          headers: {
            ...getHeaders(),
            "Content-Type": "multipart/form-data",
          },
        }
      );

      if (response.data && response.data.status === 200) {
        const res = response.data.data;
        setImportResult(res);
        if (res.successCount > 0) {
          successMsg("Import Complete", `Successfully imported ${res.successCount} leave allocation record(s).`);
          fetchAllocations(1, pageSize, "");
          setCurrentPage(1);
        }
        if (res.failureCount > 0) {
          errorMsg("Import Notice", `${res.failureCount} row(s) had errors and were not imported. Review the error table below.`);
        }
      } else {
        throw new Error(response.data?.message || "Import failed");
      }
    } catch (err) {
      console.error("Import error:", err);
      errorMsg("Import Error", err.response?.data?.message || err.message || "Failed to process import file.");
    } finally {
      setUploading(false);
    }
  };

  // Download Sample CSV / Excel File
  const downloadSampleFile = (type) => {
    let headers = [];
    let rows = [];
    let filename = "";

    const yr = selectedYear || String(new Date().getFullYear());

    if (type === "insert") {
      filename = `Leave_Allocation_Insert_Sample_${yr}.csv`;
      headers = ["Employee ID", "year", "Leave Type", "Anual Days", "Expairation Date", "Carry Forword"];
      rows = [
        ["HR345", yr, "sick Leave", "4", `25-12-${yr}`, "yes"],
        ["", "", "casual Leave", "5", `25-12-${yr}`, "NO"],
        ["EMP002", yr, "sick Leave", "12", `25-12-${yr}`, "yes"],
        ["", "", "casual Leave", "8", `25-12-${yr}`, "NO"],
      ];
    } else {
      filename = `Leave_Allocation_Update_Sample_${yr}.csv`;
      headers = ["Employee ID", "year", "Leave Type", "Anual Days", "Expairation Date", "Carry Forword"];
      rows = [
        ["HR345", yr, "sick Leave", "10", `25-12-${yr}`, "yes"],
        ["", "", "casual Leave", "6", `25-12-${yr}`, "NO"],
        ["EMP002", yr, "sick Leave", "9", `25-12-${yr}`, "yes"],
        ["", "", "casual Leave", "5", `25-12-${yr}`, "NO"],
      ];
    }

    const csvContent = "data:text/csv;charset=utf-8," + [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement("a");
    link.setAttribute("href", encodedUri);
    link.setAttribute("download", filename);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");

  const getHeaders = useCallback(() => {
    return {
      Authorization: `Bearer ${token}`,
      organizationId: organizationId,
    };
  }, [token, organizationId]);

  // Handler: Open Review Modal for an Employee
  const handleOpenReviewModal = async (record) => {
    setReviewEmployeeRecord(record);
    setIsReviewModalOpen(true);
    try {
      setLoadingReview(true);
      const res = await axios.get(
        `${GlobalConst.API_URL}/api/leave-allocation/${record.employeeId}?year=${selectedYear}`,
        { headers: getHeaders() }
      );
      if (res.data && res.data.status === 200 && res.data.data) {
        setReviewEmployeeRecord(res.data.data);
      }
    } catch (err) {
      console.warn("Could not fetch detailed allocation for review, using row data:", err);
    } finally {
      setLoadingReview(false);
    }
  };

  // 1. Fetch active employees
  const fetchActiveEmployees = useCallback(async () => {
    try {
      setLoadingEmployees(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/employees`, {
        headers: getHeaders(),
        params: { page: 0, size: 200 },
      });
      const empData = response.data?.data || response.data || [];
      setActiveEmployees(Array.isArray(empData) ? empData : []);
    } catch (err) {
      console.error("Failed to load active employees:", err);
    } finally {
      setLoadingEmployees(false);
    }
  }, [getHeaders]);

  // 2. Fetch existing allocations for selected year with server pagination & search
  const fetchAllocations = useCallback(async (page = currentPage, size = pageSize, search = debouncedSearch) => {
    try {
      setLoading(true);
      const params = {
        year: selectedYear,
        page: Math.max(0, page - 1),
        size: size,
      };
      if (search && search.trim()) {
        params.search = search.trim();
      }

      const response = await axios.get(
        `${GlobalConst.API_URL}/api/leave-allocation`,
        {
          headers: getHeaders(),
          params,
        }
      );

      if (response.data && response.data.status === 200) {
        const resData = response.data.data;
        let rawList = [];
        if (Array.isArray(resData)) {
          rawList = resData;
          setTotalElements(resData.length);
        } else if (resData && Array.isArray(resData.content)) {
          rawList = resData.content;
          setTotalElements(resData.totalElements !== undefined ? resData.totalElements : resData.content.length);
        } else {
          rawList = [];
          setTotalElements(0);
        }

        const formatted = rawList.map((item) => ({
          employeeId: item.employeeId,
          employeeName: item.employeeName || "Employee",
          employeeNumber: item.employeeNumber || "",
          createdBy: item.createdBy || "Admin",
          createdAt: item.createdAt,
          updatedAt: item.updatedAt,
          collapsed: false,
          leaveTypes: (item.leaveTypes || []).map((lt) => ({
            id: lt.id,
            leaveType: lt.leaveType,
            annualDays: lt.annualDays || 0,
            carriedForwardDays: lt.carriedForwardDays || 0,
            consumedDays: lt.consumedDays || 0,
            totalDays: lt.totalDays || 0,
            balanceDays: lt.balanceDays || 0,
            lopDays: lt.lopDays || 0,
            expirationDate: lt.expirationDate ? dayjs(lt.expirationDate) : null,
            carryForward: Boolean(lt.carryForward),
          })),
        })).sort(compareAllocationsDesc);
        setSavedAllocations(formatted);
      } else {
        throw new Error(response.data?.message || "Failed to fetch allocations");
      }
    } catch (err) {
      console.error("Failed to load allocations:", err);
      errorMsg("Fetch Error", err.response?.data?.message || "Failed to load allocations.", true);
    } finally {
      setLoading(false);
    }
  }, [getHeaders, selectedYear, currentPage, pageSize, debouncedSearch]);

  useEffect(() => {
    fetchActiveEmployees();
  }, [fetchActiveEmployees]);

  useEffect(() => {
    fetchAllocations(currentPage, pageSize, debouncedSearch);
  }, [fetchAllocations, selectedYear, currentPage, pageSize, debouncedSearch]);

  // Available employees for multi-select (all active employees except those already in active form)
  const availableEmployees = useMemo(() => {
    const formEmpIds = new Set(formAllocations.map((a) => a.employeeId));
    return activeEmployees.filter((emp) => {
      const empId = emp.employeeId || emp.id;
      return !formEmpIds.has(empId);
    });
  }, [activeEmployees, formAllocations]);

  // Filtered in modal
  const filteredAvailableEmployees = useMemo(() => {
    if (!empSearchTerm.trim()) return availableEmployees;
    const term = empSearchTerm.toLowerCase();
    return availableEmployees.filter((emp) => {
      const name = `${emp.firstName || ""} ${emp.lastName || ""}`.toLowerCase();
      const num = (emp.employeeNumber || "").toLowerCase();
      return name.includes(term) || num.includes(term);
    });
  }, [availableEmployees, empSearchTerm]);

  // Handler: Open Select Employee Modal
  const handleOpenAddModal = () => {
    setFormMode("add");
    setSelectedEmpIds([]);
    setEmpSearchTerm("");
    setIsSelectEmpModalOpen(true);
  };

  // Handler: Confirm Add Selected Employees (Multi-select)
  const handleConfirmAddEmployees = () => {
    if (selectedEmpIds.length === 0) {
      message.warning("Please select at least one employee.");
      return;
    }

    const defaultExpDate = dayjs(`${selectedYear}-12-31`);

    const newEntries = selectedEmpIds.map((empId) => {
      const existingSaved = savedAllocations.find((s) => s.employeeId === empId);
      if (existingSaved) {
        return {
          ...formatRecordForEdit(existingSaved),
          isExisting: true,
        };
      }

      const emp = activeEmployees.find((e) => (e.employeeId || e.id) === empId);
      const name = emp ? `${emp.firstName || ""} ${emp.lastName || ""}`.trim() : "Employee";
      const number = emp ? emp.employeeNumber || "" : "";

      return {
        employeeId: empId,
        employeeName: name,
        employeeNumber: number,
        createdBy: "Admin",
        createdAt: null,
        updatedAt: null,
        collapsed: false,
        isExisting: false,
        leaveTypes: [
          { id: null, leaveType: "Sick Leave", annualDays: 12, remainingDays: 12, carriedForwardDays: 0, consumedDays: 0, expirationDate: defaultExpDate, carryForward: false },
          { id: null, leaveType: "Earned Leave", annualDays: 15, remainingDays: 15, carriedForwardDays: 0, expirationDate: defaultExpDate, carryForward: true },
          { id: null, leaveType: "Casual Leave", annualDays: 8, remainingDays: 8, carriedForwardDays: 0, expirationDate: defaultExpDate, carryForward: false },
        ],
      };
    });

    setFormAllocations((prev) => [...prev, ...newEntries]);
    setIsSelectEmpModalOpen(false);
    setSelectedEmpIds([]);
    setViewMode("form"); // Switch to multi-employee cards form
  };

  // Helper: Format record for Edit mode
  const formatRecordForEdit = (record) => {
    const cloned = JSON.parse(JSON.stringify(record));
    if (cloned.leaveTypes) {
      cloned.leaveTypes = cloned.leaveTypes.map((lt) => {
        const annual = Number(lt.annualDays) || 0;
        const carried = Number(lt.carriedForwardDays) || 0;
        const consumed = Number(lt.consumedDays) || 0;
        const lop = Number(lt.lopDays) || 0;
        const balance =
          lt.balanceDays !== undefined
            ? Number(lt.balanceDays)
            : Math.max(0, annual + carried - consumed);
        return {
          ...lt,
          remainingDays: balance,
          annualDays: annual,
          consumedDays: consumed,
          carriedForwardDays: carried,
          lopDays: lop,
        };
      });
    }
    return cloned;
  };

  // Handler: View/Edit Single Employee from Action Column
  const handleViewEmployee = (record) => {
    setFormAllocations([formatRecordForEdit(record)]);
    setFormMode("view");
    setViewMode("form");
  };

  // Handler: Edit Multiple Selected Employees from Search Bar Action
  const handleEditSelectedEmployees = () => {
    if (selectedHistoryEmpIds.length === 0) {
      message.warning("Please select at least one employee to edit.");
      return;
    }
    const selectedRecords = savedAllocations.filter((rec) =>
      selectedHistoryEmpIds.includes(rec.employeeId)
    );
    if (selectedRecords.length === 0) return;

    setFormAllocations(selectedRecords.map(formatRecordForEdit));
    setFormMode("view");
    setViewMode("form");
  };

  // Handler: Toggle Collapse for Employee Card in form
  const toggleCollapse = (employeeId) => {
    setFormAllocations((prev) =>
      prev.map((item) =>
        item.employeeId === employeeId ? { ...item, collapsed: !item.collapsed } : item
      )
    );
  };

  // Handler: Remove Employee Card from form
  const handleRemoveEmployeeFromForm = (employeeId) => {
    setFormAllocations((prev) => prev.filter((a) => a.employeeId !== employeeId));
  };

  // Handler: Add Leave Type Row
  const handleAddLeaveType = (employeeId) => {
    setFormAllocations((prev) =>
      prev.map((emp) => {
        if (emp.employeeId !== employeeId) return emp;

        if (emp.leaveTypes.length >= DEFAULT_LEAVE_TYPES.length) {
          message.info("All available leave types have already been added.");
          return emp;
        }

        const usedTypes = new Set(emp.leaveTypes.map((lt) => lt.leaveType));
        const nextType = DEFAULT_LEAVE_TYPES.find((t) => !usedTypes.has(t));
        if (!nextType) return emp;

        const defaultExpDate = dayjs(`${selectedYear}-12-31`);
        return {
          ...emp,
          leaveTypes: [
            ...emp.leaveTypes,
            { id: null, leaveType: nextType, annualDays: 10, remainingDays: 10, carriedForwardDays: 0, consumedDays: 0, expirationDate: defaultExpDate, carryForward: false },
          ],
        };
      })
    );
  };

  // Handler: Remove Leave Type Row
  const handleRemoveLeaveType = (employeeId, index) => {
    setFormAllocations((prev) =>
      prev.map((emp) => {
        if (emp.employeeId !== employeeId) return emp;
        if (emp.leaveTypes.length <= 1) {
          message.warning("At least one leave type is required per employee.");
          return emp;
        }
        const targetLt = emp.leaveTypes[index];
        if (targetLt && Number(targetLt.consumedDays) > 0) {
          message.error(`Cannot remove '${targetLt.leaveType}' because ${targetLt.consumedDays} day(s) have already been consumed.`);
          return emp;
        }
        const updatedTypes = emp.leaveTypes.filter((_, i) => i !== index);
        return { ...emp, leaveTypes: updatedTypes };
      })
    );
  };

  // Handler: Update Leave Type Field
  const handleUpdateLeaveType = (employeeId, index, field, value) => {
    setFormAllocations((prev) =>
      prev.map((emp) => {
        if (emp.employeeId !== employeeId) return emp;
        const updatedTypes = emp.leaveTypes.map((lt, i) => {
          if (i !== index) return lt;
          if (field === "remainingDays") {
            const newRemaining = value !== null && value !== undefined ? Number(value) : 0;
            return {
              ...lt,
              remainingDays: newRemaining,
            };
          }
          if (field === "annualDays") {
            const newAnnual = value !== null && value !== undefined ? Number(value) : 0;
            return {
              ...lt,
              annualDays: newAnnual,
            };
          }
          return { ...lt, [field]: value };
        });
        return { ...emp, leaveTypes: updatedTypes };
      })
    );
  };

  // Handler: Save All Allocations in Form
  const handleSaveAll = async () => {
    if (formAllocations.length === 0) {
      message.warning("Please add at least one employee before saving.");
      return;
    }

    for (const emp of formAllocations) {
      if (!emp.leaveTypes || emp.leaveTypes.length === 0) {
        message.error(`Please add at least one leave type for ${emp.employeeName}.`);
        return;
      }

      const seenTypes = new Set();
      for (const lt of emp.leaveTypes) {
        if (!lt.leaveType) {
          message.error(`Leave type is required for ${emp.employeeName}.`);
          return;
        }
        if (seenTypes.has(lt.leaveType.toLowerCase())) {
          message.error(`Duplicate leave type '${lt.leaveType}' for ${emp.employeeName}.`);
          return;
        }
        seenTypes.add(lt.leaveType.toLowerCase());

        if (!emp.isExisting && (!lt.annualDays || Number(lt.annualDays) <= 0)) {
          message.error(`Annual days must be greater than 0 for ${lt.leaveType} (${emp.employeeName}).`);
          return;
        }

        if (!lt.expirationDate) {
          message.error(`Expiration date is required for ${lt.leaveType} (${emp.employeeName}).`);
          return;
        }
      }
    }

    setSaving(true);
    try {
      const existingEmployees = formAllocations.filter(
        (emp) => emp.isExisting || savedAllocations.some((s) => s.employeeId === emp.employeeId)
      );
      const newEmployees = formAllocations.filter(
        (emp) => !emp.isExisting && !savedAllocations.some((s) => s.employeeId === emp.employeeId)
      );

      const tasks = [];

      // 1. Update existing allocations via PUT endpoint
      if (existingEmployees.length > 0) {
        existingEmployees.forEach((emp) => {
          const updatePayload = {
            employeeId: emp.employeeId,
            leaveTypes: emp.leaveTypes.map((lt) => ({
              leaveType: lt.leaveType,
              remainingDays: Number(
                lt.remainingDays !== undefined
                  ? lt.remainingDays
                  : lt.balanceDays !== undefined
                  ? lt.balanceDays
                  : 0
              ),
              carriedForwardDays: Number(lt.carriedForwardDays || 0),
              expirationDate: dayjs.isDayjs(lt.expirationDate)
                ? lt.expirationDate.format("YYYY-MM-DD")
                : lt.expirationDate,
              carryForward: Boolean(lt.carryForward),
            })),
          };

          tasks.push(
            axios.put(
              `${GlobalConst.API_URL}/api/leave-allocation/${emp.employeeId}?year=${selectedYear}`,
              updatePayload,
              { headers: getHeaders() }
            )
          );
        });
      }

      // 2. Create new employee allocations via POST /bulk endpoint
      if (newEmployees.length > 0) {
        const payload = {
          year: selectedYear,
          allocations: newEmployees.map((emp) => ({
            employeeId: emp.employeeId,
            leaveTypes: emp.leaveTypes.map((lt) => ({
              leaveType: lt.leaveType,
              annualDays: Number(lt.annualDays),
              carriedForwardDays: Number(lt.carriedForwardDays || 0),
              expirationDate: dayjs.isDayjs(lt.expirationDate)
                ? lt.expirationDate.format("YYYY-MM-DD")
                : lt.expirationDate,
              carryForward: Boolean(lt.carryForward),
            })),
          })),
        };

        tasks.push(
          axios.post(`${GlobalConst.API_URL}/api/leave-allocation/bulk`, payload, {
            headers: getHeaders(),
          })
        );
      }

      const responses = await Promise.all(tasks);
      const hasError = responses.some((res) => !res.data || res.data.status !== 200);

      if (!hasError) {
        successMsg(
          "Success",
          `Leave allocations for ${formAllocations.length} employee(s) saved successfully!`
        );
        setSelectedHistoryEmpIds([]);
        setCurrentPage(1);
        await fetchAllocations(1, pageSize, debouncedSearch);
        try {
          await fetchLeaveAllocationsFromApi(selectedYear);
        } catch (syncErr) {
          console.warn("Could not sync leaveStore on save:", syncErr);
        }
        setFormAllocations([]);
        setViewMode("list");
      } else {
        throw new Error("Failed to save one or more allocations.");
      }
    } catch (err) {
      console.error("Save/Update allocations failed:", err);
      errorMsg("Operation Failed", err.response?.data?.message || "Failed to save allocations.", true);
    } finally {
      setSaving(false);
    }
  };

  // Handler: Delete Employee Allocation from database
  const handleDeleteSavedEmployee = async (employeeId, employeeName) => {
    const targetRec = savedAllocations.find((s) => s.employeeId === employeeId);
    const totalConsumed = (targetRec?.leaveTypes || []).reduce(
      (sum, lt) => sum + (Number(lt.consumedDays) || 0),
      0
    );
    if (totalConsumed > 0) {
      message.error(`Cannot delete allocation for ${employeeName}: ${totalConsumed} day(s) have already been consumed in ${selectedYear}.`);
      return;
    }
    try {
      setLoading(true);
      await axios.delete(
        `${GlobalConst.API_URL}/api/leave-allocation/${employeeId}?year=${selectedYear}`,
        { headers: getHeaders() }
      );
      try {
        await axios.delete(
          `${GlobalConst.API_URL}/api/leave-consumption/${encodeURIComponent(employeeId)}?year=${encodeURIComponent(selectedYear)}`,
          { headers: getHeaders() }
        );
      } catch (consDelErr) {
        // No consumption record to remove, continue
      }

      message.success(`Leave allocation for ${employeeName} removed.`);
      await fetchAllocations(currentPage, pageSize, debouncedSearch);
      try {
        await fetchLeaveAllocationsFromApi(selectedYear);
      } catch (syncErr) {
        console.warn("Could not sync leaveStore on delete:", syncErr);
      }
    } catch (err) {
      console.error("Failed to delete allocation:", err);
      errorMsg("Delete Error", err.response?.data?.message || "Failed to remove allocation.", true);
    } finally {
      setLoading(false);
    }
  };

  // Displayed saved allocations
  const filteredSavedAllocations = useMemo(() => {
    return savedAllocations;
  }, [savedAllocations]);

  // Master History Table Columns
  const historyColumns = [
    {
      title: "EMPLOYEE NAME",
      key: "employee",
      width: "28%",
      render: (_, record) => (
        <div className="d-flex align-items-center gap-3">
          <div
            className="d-flex align-items-center justify-content-center bg-light-primary text-primary rounded-circle"
            style={{ width: "38px", height: "38px", fontSize: "16px", flexShrink: 0 }}
          >
            <UserOutlined />
          </div>
          <div>
            <div className="fw-bolder text-gray-900 fs-6">{record.employeeName}</div>
            <div className="text-muted fs-8">
              {record.employeeNumber ? `ID: ${record.employeeNumber}` : ""}
            </div>
          </div>
        </div>
      ),
    },
    {
      title: "ALLOCATED LEAVES",
      key: "leaves",
      width: "22%",
      render: (_, record) => {
        const totalAnnual = (record.leaveTypes || []).reduce((sum, lt) => sum + (Number(lt.annualDays) || 0), 0);
        const totalCarried = (record.leaveTypes || []).reduce((sum, lt) => sum + (Number(lt.carriedForwardDays) || 0), 0);
        const grandTotal = totalAnnual + totalCarried;

        return (
          <div className="d-flex flex-column gap-1">
            <div className="d-flex align-items-center gap-2 mb-1">
              <span
                className="badge bg-light-primary text-primary fw-bolder px-2 py-1 fs-7"
                style={{ border: "1px solid #BFDBFE", borderRadius: "4px" }}
              >
                Total: {grandTotal} Days
              </span>
              <span className="text-muted fs-8">({record.leaveTypes?.length || 0} types)</span>
            </div>
            <div className="d-flex flex-wrap gap-1">
              {record.leaveTypes?.map((lt, idx) => (
                <Tag key={idx} color="blue" style={{ borderRadius: "4px", fontSize: "11px", padding: "1px 6px" }}>
                  <strong>{lt.leaveType}:</strong> {lt.annualDays}d
                  {lt.carriedForwardDays > 0 ? ` (+${lt.carriedForwardDays} CF)` : ""}
                </Tag>
              ))}
            </div>
          </div>
        );
      },
    },
    {
      title: "CONSUMED LEAVE",
      key: "consumed",
      width: "15%",
      render: (_, record) => {
        const totalConsumed = (record.leaveTypes || []).reduce((sum, lt) => sum + (Number(lt.consumedDays) || 0), 0);
        return (
          <div className="d-flex flex-column gap-1">
            <div className="mb-1">
              <span
                className="badge bg-light text-gray-700 fw-bold px-2 py-1 fs-7"
                style={{ border: "1px solid #E2E8F0", borderRadius: "4px" }}
              >
                Total: {totalConsumed} Days
              </span>
            </div>
            <div className="d-flex flex-wrap gap-1">
              {record.leaveTypes?.map((lt, idx) => (
                <Tag key={idx} style={{ borderRadius: "4px", fontSize: "11px", padding: "1px 6px", backgroundColor: "#F8FAFC", color: "#64748B", border: "1px solid #E2E8F0" }}>
                  <strong>{lt.leaveType}:</strong> {lt.consumedDays || 0}d
                </Tag>
              ))}
            </div>
          </div>
        );
      },
    },
    {
      title: "REMAINING LEAVE",
      key: "remaining",
      width: "17%",
      render: (_, record) => {
        const totalRemaining = (record.leaveTypes || []).reduce((sum, lt) => {
          const bal = lt.balanceDays !== undefined ? Number(lt.balanceDays) : Math.max(0, ((Number(lt.annualDays) || 0) + (Number(lt.carriedForwardDays) || 0)) - (Number(lt.consumedDays) || 0));
          return sum + bal;
        }, 0);

        return (
          <div className="d-flex flex-column gap-1">
            <div className="mb-1">
              <span
                className="badge bg-light-success text-success fw-bolder px-2 py-1 fs-7"
                style={{ border: "1px solid #A7F3D0", borderRadius: "4px" }}
              >
                Total: {totalRemaining} Days
              </span>
            </div>
            <div className="d-flex flex-wrap gap-1">
              {record.leaveTypes?.map((lt, idx) => {
                const rem = lt.balanceDays !== undefined ? Number(lt.balanceDays) : Math.max(0, ((Number(lt.annualDays) || 0) + (Number(lt.carriedForwardDays) || 0)) - (Number(lt.consumedDays) || 0));
                return (
                  <Tag key={idx} color="green" style={{ borderRadius: "4px", fontSize: "11px", padding: "1px 6px" }}>
                    <strong>{lt.leaveType}:</strong> {rem}d
                  </Tag>
                );
              })}
            </div>
          </div>
        );
      },
    },
    {
      title: "CREATED AT",
      dataIndex: "createdAt",
      key: "createdAt",
      width: "14%",
      render: (val) => (
        <span className="text-gray-700 fs-7 fw-semibold">
          {val ? dayjs(val).format("DD MMM YYYY, hh:mm A") : "—"}
        </span>
      ),
    },
    {
      title: "CREATED BY",
      dataIndex: "createdBy",
      key: "createdBy",
      width: "11%",
      render: (val) => <span className="fw-semibold text-gray-800">{val || "Admin"}</span>,
    },
    {
      title: "REVIEW",
      key: "review",
      width: "9%",
      align: "center",
      render: (_, record) => (
        <Button
          size="small"
          icon={<EyeOutlined />}
          onClick={() => handleOpenReviewModal(record)}
          style={{
            borderRadius: "6px",
            fontWeight: "600",
            backgroundColor: "#059669",
            color: "#ffffff",
            border: "none",
            boxShadow: "0 1px 2px rgba(5, 150, 105, 0.2)",
            display: "inline-flex",
            alignItems: "center",
            gap: "4px",
            height: "28px",
            padding: "0 10px",
          }}
        >
          Review
        </Button>
      ),
    },
    {
      title: "ACTION",
      key: "action",
      width: "12%",
      align: "center",
      render: (_, record) => {
        const totalConsumed = (record.leaveTypes || []).reduce(
          (sum, lt) => sum + (Number(lt.consumedDays) || 0),
          0
        );
        const hasConsumedLeaves = totalConsumed > 0;

        return (
          <div className="d-flex align-items-center justify-content-center gap-2">
            <Button
              type="primary"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleViewEmployee(record)}
              style={{ borderRadius: "6px", fontWeight: "600", backgroundColor: "#2563EB" }}
            >
              Edit
            </Button>

            {hasConsumedLeaves ? (
              <Tooltip title={`Cannot delete: ${record.employeeName} has consumed ${totalConsumed} day(s) of leave in ${selectedYear}.`}>
                <span>
                  <Button
                    type="text"
                    disabled
                    size="small"
                    icon={<DeleteOutlined />}
                    style={{ fontSize: "15px", color: "#CBD5E1", cursor: "not-allowed" }}
                  />
                </span>
              </Tooltip>
            ) : (
              <Popconfirm
                title="Remove Allocation"
                description={`Remove leave allocation for ${record.employeeName} (${selectedYear})?`}
                onConfirm={() => handleDeleteSavedEmployee(record.employeeId, record.employeeName)}
                okText="Remove"
                cancelText="Cancel"
                okButtonProps={{ danger: true }}
              >
                <Button
                  type="text"
                  danger
                  size="small"
                  icon={<DeleteOutlined />}
                  style={{ fontSize: "15px" }}
                />
              </Popconfirm>
            )}
          </div>
        );
      },
    },
  ];

  return (
    <>
      <Helmet>
        <title>Leave Allocation | HRMS InfiNevoCloud</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white min-vh-100">
        <div className="p-6">
          {/* Header & Controls */}
          <div className="d-flex justify-content-between align-items-center mb-6 flex-wrap gap-4">
            <div>
              <div className="d-inline-block px-3 py-1 bg-light-primary text-primary fw-bold rounded fs-8 mb-2">
                1. ADMIN – LEAVE ALLOCATION (Allocate Annual Entitlement)
              </div>
              <h2 className="fw-bolder text-gray-900 mb-1">
                {viewMode === "form" ? "Configure Leave Allocation" : "Leave Allocation"}
              </h2>
              <div className="text-muted fs-6">
                {viewMode === "form"
                  ? "Enter leave entitlement for selected employees and save all."
                  : "Allocate annual leave entitlement for employees for the selected year."}
              </div>
            </div>

            {/* Right Controls */}
            <div className="d-flex align-items-center gap-3 flex-wrap">
              {viewMode === "form" ? (
                <Button
                  icon={<ArrowLeftOutlined />}
                  onClick={() => setViewMode("list")}
                  style={{ height: "41px", borderRadius: "6px", fontWeight: "600" }}
                >
                  Back to List
                </Button>
              ) : null}

              {viewMode === "list" && (
                <Button
                  icon={<FileExcelOutlined style={{ color: "#107c41" }} />}
                  onClick={() => setIsSampleModalOpen(true)}
                  style={{
                    height: "41px",
                    borderRadius: "6px",
                    fontWeight: "600",
                    borderColor: "#107c41",
                    color: "#107c41",
                  }}
                >
                  Sample Formats
                </Button>
              )}

              {viewMode === "list" && (
                <Button
                  icon={<UploadOutlined />}
                  onClick={() => {
                    setImportFile(null);
                    setImportResult(null);
                    setIsImportModalOpen(true);
                  }}
                  style={{
                    height: "41px",
                    borderRadius: "6px",
                    fontWeight: "600",
                  }}
                >
                  Import
                </Button>
              )}

              <div className="d-flex align-items-center gap-2">
                <span className="text-muted fw-bold fs-7">Year:</span>
                <DatePicker
                  picker="year"
                  value={selectedYear ? dayjs(String(selectedYear), "YYYY") : dayjs()}
                  onChange={(date, dateString) => {
                    if (dateString) {
                      setSelectedYear(dateString);
                      setFormAllocations([]);
                      setSelectedHistoryEmpIds([]);
                      setViewMode("list");
                    }
                  }}
                  allowClear={false}
                  style={{ width: "120px", height: "41px", borderRadius: "6px", fontWeight: "600" }}
                />
              </div>

              {viewMode === "list" && (
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={handleOpenAddModal}
                  style={{ height: "41px", borderRadius: "6px", fontWeight: "600" }}
                >
                  Add Employee
                </Button>
              )}
            </div>
          </div>

          {/* VIEW 1: HISTORY TABLE VIEW */}
          {viewMode === "list" ? (
            <>
              {/* Search & Summary Count */}
              <div className="d-flex justify-content-between align-items-center mb-4 flex-wrap gap-3">
                <div className="d-flex align-items-center gap-3 flex-wrap">
                  <Input
                    prefix={<SearchOutlined className="text-muted" />}
                    placeholder="Search by employee name or ID..."
                    value={tableSearch}
                    onChange={(e) => setTableSearch(e.target.value)}
                    style={{ width: "340px", height: "40px", borderRadius: "6px" }}
                    allowClear
                  />
                  <Button
                    type="primary"
                    icon={<EditOutlined />}
                    disabled={selectedHistoryEmpIds.length === 0}
                    onClick={handleEditSelectedEmployees}
                    style={{
                      height: "40px",
                      borderRadius: "6px",
                      fontWeight: "600",
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "6px",
                    }}
                  >
                    Edit {selectedHistoryEmpIds.length > 0 ? `(${selectedHistoryEmpIds.length})` : ""}
                  </Button>
                </div>
                <div className="text-muted fs-7">
                  Total Allocated Employees: <strong>{totalElements}</strong>
                </div>
              </div>

              {/* Master Table */}
              <div
                className="card border-0 shadow-sm mb-6"
                style={{ borderRadius: "10px", border: "1px solid #E2E8F0", overflow: "hidden" }}
              >
                <Table
                  rowSelection={{
                    selectedRowKeys: selectedHistoryEmpIds,
                    onChange: (selectedKeys) => setSelectedHistoryEmpIds(selectedKeys),
                  }}
                  columns={historyColumns}
                  dataSource={filteredSavedAllocations}
                  rowKey="employeeId"
                  loading={loading}
                  pagination={{
                    current: currentPage,
                    pageSize: pageSize,
                    total: totalElements,
                    showSizeChanger: true,
                    pageSizeOptions: ["10", "20", "50", "100"],
                    showTotal: (total, range) => `${range[0]}-${range[1]} of ${total} employees`,
                    onChange: (page, newPageSize) => {
                      setCurrentPage(page);
                      if (newPageSize !== pageSize) {
                        setPageSize(newPageSize);
                        setCurrentPage(1);
                      }
                    },
                  }}
                  locale={{
                    emptyText: (
                      <Empty
                        description={
                          <div>
                            <h5 className="fw-bold text-gray-700">No Employee Allocations for {selectedYear}</h5>
                            <p className="text-muted fs-7">
                              Click "+ Add Employee" above to select and allocate leaves for employees.
                            </p>
                          </div>
                        }
                      >
                        <Button type="primary" icon={<PlusOutlined />} onClick={handleOpenAddModal}>
                          + Add Employee
                        </Button>
                      </Empty>
                    ),
                  }}
                />
              </div>
            </>
          ) : (
            /* VIEW 2: MULTI-EMPLOYEE CARDS FORM (MATCHES SCREENSHOT) */
            <>
              <div className="d-flex flex-column gap-5 mb-8">
                {formAllocations.map((emp) => (
                  <div
                    key={emp.employeeId}
                    className="card border-0 shadow-sm"
                    style={{
                      borderRadius: "10px",
                      border: "1px solid #E2E8F0",
                      overflow: "hidden",
                    }}
                  >
                    {/* Employee Card Header */}
                    <div
                      className="card-header bg-white py-3 px-6 d-flex align-items-center justify-content-between cursor-pointer border-bottom"
                      style={{ minHeight: "60px" }}
                    >
                      <div
                        className="d-flex align-items-center gap-3 flex-grow-1"
                        onClick={() => toggleCollapse(emp.employeeId)}
                      >
                        <button
                          type="button"
                          className="btn btn-sm btn-icon btn-light border-0"
                          style={{ width: "28px", height: "28px" }}
                        >
                          {emp.collapsed ? <DownOutlined className="fs-8 text-gray-600" /> : <UpOutlined className="fs-8 text-gray-600" />}
                        </button>

                        <div
                          className="d-flex align-items-center justify-content-center bg-light-primary text-primary rounded-circle"
                          style={{ width: "36px", height: "36px" }}
                        >
                          <UserOutlined className="fs-5" />
                        </div>

                        <div>
                          <span className="fw-bolder text-gray-900 fs-6 me-2">
                            {emp.employeeName}
                          </span>
                          {emp.employeeNumber && (
                            <span className="text-muted fw-semibold fs-7 me-2">
                              ({emp.employeeNumber})
                            </span>
                          )}
                          {(emp.isExisting || formMode === "view") ? (
                            <span className="badge bg-light-warning text-warning fs-9 px-2 py-1" style={{ border: "1px solid #fde68a", borderRadius: "4px" }}>
                              Edit Existing Allocation
                            </span>
                          ) : (
                            <span className="badge bg-light-success text-success fs-9 px-2 py-1" style={{ border: "1px solid #bbf7d0", borderRadius: "4px" }}>
                              New Allocation
                            </span>
                          )}
                        </div>
                      </div>

                      {formMode !== "view" && (
                        <div>
                          <Button
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => handleRemoveEmployeeFromForm(emp.employeeId)}
                            className="d-flex align-items-center gap-1 fw-bold fs-7"
                          >
                            Remove Employee
                          </Button>
                        </div>
                      )}
                    </div>

                    {/* Employee Sub-grid Table */}
                    {!emp.collapsed && (
                      <div className="card-body p-0">
                        <div className="table-responsive">
                          <table className="table align-middle mb-0">
                            <thead>
                              <tr
                                className="text-muted fw-bold fs-7 text-uppercase"
                                style={{ backgroundColor: "#F8FAFC", borderBottom: "1px solid #E2E8F0" }}
                              >
                                <th className="ps-8 py-3" style={{ width: "26%" }}>
                                  Leave Type <span className="text-danger">*</span>
                                </th>
                                <th className="py-3" style={{ width: "24%" }}>
                                  {(emp.isExisting || formMode === "view") ? "Remaining Days" : "Annual Days"}{" "}
                                  <span className="text-danger">*</span>
                                </th>
                                <th className="py-3" style={{ width: "22%" }}>
                                  Expiration Date <span className="text-danger">*</span>
                                </th>
                                <th className="py-3" style={{ width: "18%" }}>
                                  Carry Forward
                                </th>
                                <th className="pe-8 py-3 text-center" style={{ width: "10%" }}>
                                  Action
                                </th>
                              </tr>
                            </thead>
                            <tbody>
                              {emp.leaveTypes.map((lt, idx) => {
                                const usedTypesInOtherRows = new Set(
                                  emp.leaveTypes
                                    .filter((_, i) => i !== idx)
                                    .map((item) => item.leaveType)
                                );

                                return (
                                  <tr key={idx} style={{ borderBottom: "1px solid #F1F5F9" }}>
                                    <td className="ps-8 py-3">
                                      <Select
                                        value={lt.leaveType}
                                        onChange={(val) =>
                                          handleUpdateLeaveType(emp.employeeId, idx, "leaveType", val)
                                        }
                                        style={{ width: "100%", maxWidth: "260px", height: "40px" }}
                                      >
                                        {DEFAULT_LEAVE_TYPES.map((type) => {
                                          const isUsed = usedTypesInOtherRows.has(type);
                                          return (
                                            <Option key={type} value={type} disabled={isUsed}>
                                              {type} {isUsed ? "(Already added)" : ""}
                                            </Option>
                                          );
                                        })}
                                      </Select>
                                    </td>
                                    <td className="py-3">
                                      {(emp.isExisting || formMode === "view") ? (
                                        <>
                                          <InputNumber
                                            min={0}
                                            max={365}
                                            value={
                                              lt.remainingDays !== undefined
                                                ? lt.remainingDays
                                                : lt.balanceDays !== undefined
                                                ? lt.balanceDays
                                                : Math.max(
                                                    0,
                                                    (lt.annualDays || 0) +
                                                      (lt.carriedForwardDays || 0) -
                                                      (lt.consumedDays || 0)
                                                  )
                                            }
                                            onChange={(val) =>
                                              handleUpdateLeaveType(
                                                emp.employeeId,
                                                idx,
                                                "remainingDays",
                                                val
                                              )
                                            }
                                            style={{
                                              width: "100%",
                                              maxWidth: "160px",
                                              height: "40px",
                                            }}
                                            placeholder="Enter remaining days"
                                          />
                                          <div className="text-muted fs-8 mt-1">
                                            <span className="fw-bold text-gray-700">
                                              Annual: {lt.annualDays || 0}d
                                            </span>
                                            {(lt.consumedDays || 0) > 0 && (
                                              <span className="ms-2 text-primary fw-semibold">
                                                (Consumed: {lt.consumedDays}d)
                                              </span>
                                            )}
                                            {(lt.lopDays || 0) > 0 && (
                                              <span className="ms-2 text-danger fw-semibold">
                                                (LOP: {lt.lopDays}d)
                                              </span>
                                            )}
                                            {(lt.carriedForwardDays || 0) > 0 && (
                                              <span className="ms-1 text-success fw-semibold">
                                                +{lt.carriedForwardDays} carry
                                              </span>
                                            )}
                                          </div>
                                        </>
                                      ) : (
                                        <>
                                          <InputNumber
                                            min={1}
                                            max={365}
                                            value={lt.annualDays}
                                            onChange={(val) =>
                                              handleUpdateLeaveType(
                                                emp.employeeId,
                                                idx,
                                                "annualDays",
                                                val
                                              )
                                            }
                                            style={{
                                              width: "100%",
                                              maxWidth: "160px",
                                              height: "40px",
                                            }}
                                            placeholder="Enter days"
                                          />
                                          {lt.carriedForwardDays > 0 && (
                                            <div className="text-success fs-8 mt-1 fw-semibold">
                                              +{lt.carriedForwardDays} Carried Over (Total:{" "}
                                              {(lt.annualDays || 0) + lt.carriedForwardDays})
                                            </div>
                                          )}
                                        </>
                                      )}
                                    </td>
                                    <td className="py-3">
                                      <DatePicker
                                        format="YYYY-MM-DD"
                                        value={lt.expirationDate ? (dayjs.isDayjs(lt.expirationDate) ? lt.expirationDate : dayjs(lt.expirationDate)) : null}
                                        onChange={(date) =>
                                          handleUpdateLeaveType(emp.employeeId, idx, "expirationDate", date)
                                        }
                                        style={{ width: "100%", maxWidth: "200px", height: "40px", borderRadius: "6px" }}
                                        placeholder="Select expiration"
                                        allowClear
                                      />
                                    </td>
                                    <td className="py-3">
                                      <Select
                                        value={Boolean(lt.carryForward)}
                                        onChange={(val) =>
                                          handleUpdateLeaveType(emp.employeeId, idx, "carryForward", val)
                                        }
                                        style={{ width: "100%", maxWidth: "130px", height: "40px" }}
                                      >
                                        <Option value={true}>Yes</Option>
                                        <Option value={false}>No</Option>
                                      </Select>
                                    </td>
                                    <td className="pe-8 py-3 text-center">
                                      {Number(lt.consumedDays) > 0 ? (
                                        <Tooltip title={`Cannot remove: ${lt.consumedDays} day(s) consumed`}>
                                          <span>
                                            <Button
                                              type="text"
                                              disabled
                                              icon={<DeleteOutlined />}
                                              style={{ fontSize: "16px", color: "#CBD5E1", cursor: "not-allowed" }}
                                            />
                                          </span>
                                        </Tooltip>
                                      ) : (
                                        <Button
                                          type="text"
                                          danger
                                          icon={<DeleteOutlined />}
                                          onClick={() => handleRemoveLeaveType(emp.employeeId, idx)}
                                          disabled={emp.leaveTypes.length <= 1}
                                          style={{ fontSize: "16px" }}
                                        />
                                      )}
                                    </td>
                                  </tr>
                                );
                              })}
                            </tbody>
                          </table>
                        </div>

                        {/* Add Type button inside card */}
                        <div className="py-3 px-8 bg-white border-top d-flex align-items-center justify-content-between">
                          {emp.leaveTypes.length < DEFAULT_LEAVE_TYPES.length ? (
                            <Button
                              type="link"
                              icon={<PlusOutlined />}
                              onClick={() => handleAddLeaveType(emp.employeeId)}
                              className="p-0 fw-bold text-primary"
                            >
                              + Add Type
                            </Button>
                          ) : (
                            <span className="text-muted fs-8 fw-semibold">
                              ✓ All available leave types have been added ({DEFAULT_LEAVE_TYPES.length}/{DEFAULT_LEAVE_TYPES.length})
                            </span>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {/* Form Bottom Actions */}
              <div className="d-flex justify-content-between align-items-center mb-6 flex-wrap gap-4">
                {formMode !== "view" ? (
                  <Button
                    type="dashed"
                    icon={<PlusOutlined />}
                    onClick={handleOpenAddModal}
                    style={{
                      height: "44px",
                      borderColor: "#3B82F6",
                      color: "#2563EB",
                      fontWeight: "600",
                      borderRadius: "8px",
                      padding: "0 20px",
                    }}
                  >
                    + Add Another Employee
                  </Button>
                ) : (
                  <div />
                )}

                <div className="d-flex align-items-center gap-3">
                  <Button
                    onClick={() => setViewMode("list")}
                    disabled={saving}
                    style={{ height: "44px", borderRadius: "8px", padding: "0 24px" }}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="primary"
                    onClick={handleSaveAll}
                    loading={saving}
                    style={{
                      height: "44px",
                      borderRadius: "8px",
                      padding: "0 28px",
                      fontWeight: "600",
                      backgroundColor: "#2563EB",
                    }}
                  >
                    {formMode === "view" ? "Save Allocation" : "Save All Allocations"}
                  </Button>
                </div>
              </div>
            </>
          )}

          {/* Bottom Rules Info Box */}
          <div
            className="p-4 d-flex align-items-center gap-3"
            style={{
              backgroundColor: "#EFF6FF",
              border: "1px solid #BFDBFE",
              borderRadius: "8px",
              color: "#1E40AF",
              fontSize: "13px",
            }}
          >
            <InfoCircleOutlined className="fs-5 flex-shrink-0" />
            <div>
              <strong>Rules:</strong> Expiration date is mandatory &bull; One allocation per type per employee &bull; Carry forward transfers unused balance &bull; Year-based allocation ({selectedYear})
            </div>
          </div>
        </div>
      </div>

      {/* Select Employees Modal (Multi-Select) */}
      <Modal
        title="Select Employees for Leave Allocation"
        open={isSelectEmpModalOpen}
        onOk={handleConfirmAddEmployees}
        onCancel={() => setIsSelectEmpModalOpen(false)}
        okText={`Add Selected (${selectedEmpIds.length})`}
        cancelText="Cancel"
        width={600}
        destroyOnClose
      >
        <div className="py-2">
          <div className="d-flex align-items-center justify-content-between mb-3 gap-3">
            <Input
              prefix={<SearchOutlined className="text-muted" />}
              placeholder="Search employees..."
              value={empSearchTerm}
              onChange={(e) => setEmpSearchTerm(e.target.value)}
              style={{ height: "40px", borderRadius: "6px" }}
              allowClear
            />
            {filteredAvailableEmployees.length > 0 && (
              <Button
                type="dashed"
                size="small"
                onClick={() => {
                  if (selectedEmpIds.length === filteredAvailableEmployees.length) {
                    setSelectedEmpIds([]);
                  } else {
                    setSelectedEmpIds(filteredAvailableEmployees.map((e) => e.employeeId || e.id));
                  }
                }}
                className="flex-shrink-0"
              >
                {selectedEmpIds.length === filteredAvailableEmployees.length ? "Deselect All" : "Select All"}
              </Button>
            )}
          </div>

          <div className="d-flex justify-content-between align-items-center mb-2 px-1">
            <span className="text-muted fs-8">
              Showing {filteredAvailableEmployees.length} available employee(s)
            </span>
            {selectedEmpIds.length > 0 && (
              <span className="text-primary fw-bold fs-8">
                {selectedEmpIds.length} selected
              </span>
            )}
          </div>

          {loadingEmployees ? (
            <div className="text-center py-8">
              <Spin />
              <div className="mt-2 text-muted">Loading organization employees...</div>
            </div>
          ) : filteredAvailableEmployees.length === 0 ? (
            <div className="py-8 text-center text-muted">
              {availableEmployees.length === 0
                ? "All active employees have already been added to the allocation list."
                : "No matching employees found."}
            </div>
          ) : (
            <div
              style={{
                maxHeight: "340px",
                overflowY: "auto",
                border: "1px solid #E2E8F0",
                borderRadius: "8px",
              }}
            >
              {filteredAvailableEmployees.map((emp) => {
                const empId = emp.employeeId || emp.id;
                const isSelected = selectedEmpIds.includes(empId);
                const fullName = `${emp.firstName || ""} ${emp.lastName || ""}`.trim() || "Employee";
                const isAlreadyAllocated = savedAllocations.some((s) => s.employeeId === empId);

                return (
                  <div
                    key={empId}
                    onClick={() => {
                      setSelectedEmpIds((prev) =>
                        isSelected ? prev.filter((id) => id !== empId) : [...prev, empId]
                      );
                    }}
                    className={`d-flex align-items-center justify-content-between p-3 border-bottom cursor-pointer ${
                      isSelected ? "bg-light-primary" : "hover-bg-light"
                    }`}
                    style={{ transition: "background 0.2s" }}
                  >
                    <div className="d-flex align-items-center gap-3">
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => {}}
                        className="form-check-input"
                        style={{ cursor: "pointer" }}
                      />
                      <div>
                        <div className="fw-bold text-gray-900 d-flex align-items-center gap-2">
                          {fullName}
                          {isAlreadyAllocated && (
                            <span
                              className="badge bg-light-warning text-warning fs-9 px-2 py-0 fw-semibold"
                              style={{ border: "1px solid #fde68a", borderRadius: "4px" }}
                            >
                              Allocated ({selectedYear})
                            </span>
                          )}
                        </div>
                        <div className="text-muted fs-8">
                          {emp.employeeNumber ? `ID: ${emp.employeeNumber}` : ""}
                          {emp.email ? ` • ${emp.email}` : ""}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </Modal>

      {/* MODAL 2: Review Employee Leave Allocation Details */}
      <Modal
        open={isReviewModalOpen}
        onCancel={() => {
          setIsReviewModalOpen(false);
          setReviewEmployeeRecord(null);
        }}
        footer={[
          <Button
            key="close"
            onClick={() => {
              setIsReviewModalOpen(false);
              setReviewEmployeeRecord(null);
            }}
            style={{ borderRadius: "6px", fontWeight: "600" }}
          >
            Close
          </Button>,
          <Button
            key="edit"
            type="primary"
            icon={<EditOutlined />}
            onClick={() => {
              const rec = reviewEmployeeRecord;
              setIsReviewModalOpen(false);
              setReviewEmployeeRecord(null);
              if (rec) {
                handleViewEmployee(rec);
              }
            }}
            style={{ borderRadius: "6px", fontWeight: "600", backgroundColor: "#2563EB" }}
          >
            Edit Allocation
          </Button>,
        ]}
        width={850}
        destroyOnClose
        title={
          <div className="d-flex align-items-center gap-3 py-1">
            <div
              style={{
                width: "36px",
                height: "36px",
                borderRadius: "8px",
                backgroundColor: "#ecfdf5",
                color: "#059669",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: "18px",
              }}
            >
              <EyeOutlined />
            </div>
            <div>
              <div className="fw-bolder fs-5 text-gray-900">
                Employee Leave Allocation Review
              </div>
              <div className="text-muted fs-8 fw-normal">
                Complete entitlement and consumption details for Year <b>{selectedYear}</b>
              </div>
            </div>
          </div>
        }
      >
        {loadingReview ? (
          <div className="text-center py-10">
            <Spin size="large" />
            <div className="mt-3 text-muted">Fetching latest allocation details...</div>
          </div>
        ) : reviewEmployeeRecord ? (
          <div className="py-2">
            {/* Employee Summary Card */}
            <div
              style={{
                background: "linear-gradient(135deg, #f8fafc, #f1f5f9)",
                borderRadius: "10px",
                border: "1px solid #e2e8f0",
                padding: "16px 20px",
                marginBottom: "20px",
              }}
            >
              <div className="d-flex align-items-center justify-content-between flex-wrap gap-3">
                <div className="d-flex align-items-center gap-3">
                  <div
                    style={{
                      width: "44px",
                      height: "44px",
                      borderRadius: "10px",
                      background: "linear-gradient(135deg, #0284c7, #0369a1)",
                      color: "#ffffff",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontWeight: "700",
                      fontSize: "16px",
                    }}
                  >
                    <UserOutlined />
                  </div>
                  <div>
                    <h4 className="fw-bolder text-gray-900 mb-1 fs-5">
                      {reviewEmployeeRecord.employeeName || "Employee"}
                    </h4>
                    <div className="d-flex align-items-center gap-2 flex-wrap text-muted fs-7">
                      <span className="badge bg-white text-dark border border-gray-300 fs-8 fw-bold">
                        {reviewEmployeeRecord.employeeNumber ? `ID: ${reviewEmployeeRecord.employeeNumber}` : "ID: —"}
                      </span>
                      <span>•</span>
                      <span>Year: <strong className="text-gray-900">{reviewEmployeeRecord.year || selectedYear}</strong></span>
                      <span>•</span>
                      <span>Allocated By: <strong className="text-gray-900">{reviewEmployeeRecord.createdBy || "Admin"}</strong></span>
                    </div>
                  </div>
                </div>

                <div className="text-end">
                  <span className="badge bg-light-primary text-primary fw-bold px-3 py-2 fs-7" style={{ border: "1px solid #bfdbfe", borderRadius: "6px" }}>
                    Active Allocation
                  </span>
                </div>
              </div>

              {/* 3 Stat KPI boxes (Allocated, Consumed, Remaining) */}
              {(() => {
                const types = reviewEmployeeRecord.leaveTypes || [];
                const totalAlloc = types.reduce((s, t) => s + (Number(t.annualDays) || 0), 0);
                const totalCons = types.reduce((s, t) => s + (Number(t.consumedDays) || 0), 0);
                const totalRem = types.reduce((s, t) => {
                  const rem = t.balanceDays !== undefined ? Number(t.balanceDays) : Math.max(0, (Number(t.annualDays) || 0) - (Number(t.consumedDays) || 0));
                  return s + rem;
                }, 0);

                return (
                  <div className="row g-3 mt-3 pt-3 border-top border-gray-200">
                    <div className="col-12 col-sm-4">
                      <div className="bg-white p-3 rounded-3 border border-gray-200 text-center">
                        <span className="text-muted fs-8 fw-semibold d-block">Total Allocated</span>
                        <span className="fs-5 fw-bold text-gray-900">{totalAlloc} Days</span>
                      </div>
                    </div>
                    <div className="col-12 col-sm-4">
                      <div className="bg-white p-3 rounded-3 border border-gray-200 text-center">
                        <span className="text-muted fs-8 fw-semibold d-block">Consumed</span>
                        <span className="fs-5 fw-bold text-gray-900">{totalCons} Days</span>
                      </div>
                    </div>
                    <div className="col-12 col-sm-4">
                      <div className="bg-white p-3 rounded-3 border border-gray-200 text-center">
                        <span className="text-muted fs-8 fw-semibold d-block">Remaining Balance</span>
                        <span className="fs-5 fw-bold text-success">{totalRem} Days</span>
                      </div>
                    </div>
                  </div>
                );
              })()}
            </div>

            {/* Breakdown Table */}
            <div className="d-flex align-items-center justify-content-between mb-2">
              <h6 className="fw-bolder text-gray-900 mb-0">Leave Type Breakdown</h6>
              <span className="text-muted fs-8">
                {reviewEmployeeRecord.leaveTypes?.length || 0} Leave Types configured
              </span>
            </div>

            <div
              style={{
                border: "1px solid #e5e7eb",
                borderRadius: "8px",
                overflow: "hidden",
              }}
            >
              <table className="table table-bordered align-middle mb-0" style={{ fontSize: "13px" }}>
                <thead style={{ backgroundColor: "#f8fafc" }}>
                  <tr className="text-gray-700 fw-bold">
                    <th className="ps-3 py-3">Leave Type</th>
                    <th className="text-center py-3">Annual Days</th>
                    <th className="text-center py-3">Consumed</th>
                    <th className="text-center py-3 text-success">Remaining</th>
                    <th className="text-center py-3">Carry Forward</th>
                    <th className="text-center pe-3 py-3">Expiration</th>
                  </tr>
                </thead>
                <tbody>
                  {(reviewEmployeeRecord.leaveTypes || []).map((lt, idx) => {
                    const annual = Number(lt.annualDays) || 0;
                    const consumed = Number(lt.consumedDays) || 0;
                    const rem = lt.balanceDays !== undefined ? Number(lt.balanceDays) : Math.max(0, annual - consumed);

                    return (
                      <tr key={idx}>
                        <td className="ps-3 py-3 fw-bold text-gray-900">
                          {lt.leaveType}
                        </td>
                        <td className="text-center py-3 fw-bold text-primary">{annual}d</td>
                        <td className="text-center py-3">{consumed}d</td>
                        <td className="text-center py-3 fw-bold text-success">
                          <span className="badge bg-light-success text-success px-2 py-1" style={{ border: "1px solid #bbf7d0", borderRadius: "4px" }}>
                            {rem}d
                          </span>
                        </td>
                        <td className="text-center py-3">
                          {lt.carryForward ? (
                            <Tag color="green" style={{ borderRadius: "4px" }}>Yes</Tag>
                          ) : (
                            <Tag color="default" style={{ borderRadius: "4px" }}>No</Tag>
                          )}
                        </td>
                        <td className="text-center pe-3 py-3 text-muted fs-8">
                          {lt.expirationDate ? dayjs(lt.expirationDate).format("DD MMM YYYY") : "31 Dec " + selectedYear}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* Audit Details */}
            <div className="d-flex align-items-center justify-content-between mt-3 text-muted fs-8 px-1">
              <div>
                Created: <b>{reviewEmployeeRecord.createdAt ? dayjs(reviewEmployeeRecord.createdAt).format("DD MMM YYYY, hh:mm A") : "—"}</b>
              </div>
              <div>
                Last Updated: <b>{reviewEmployeeRecord.updatedAt ? dayjs(reviewEmployeeRecord.updatedAt).format("DD MMM YYYY, hh:mm A") : "—"}</b>
              </div>
            </div>
          </div>
        ) : null}
      </Modal>

      {/* Modal: Sample Excel Formats for Insert & Update */}
      <Modal
        title={
          <div className="d-flex align-items-center gap-2">
            <FileExcelOutlined style={{ color: "#107c41", fontSize: "20px" }} />
            <span className="fw-bolder fs-5 text-gray-900">
              Sample Excel Formats for Leave Allocation
            </span>
          </div>
        }
        open={isSampleModalOpen}
        onCancel={() => setIsSampleModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setIsSampleModalOpen(false)} style={{ borderRadius: "6px", fontWeight: "600" }}>
            Close
          </Button>,
        ]}
        width={850}
        centered
      >
        <div className="text-muted fs-7 mb-4">
          Review the required column layout and download ready-to-use sample spreadsheets for either creating fresh allocations or updating existing remaining balances.
        </div>

        <Tabs
          defaultActiveKey="insert"
          items={[
            {
              key: "insert",
              label: (
                <span className="fw-bold fs-7">
                  <span className="badge bg-light-primary text-primary me-2">1. Addition</span>
                  New Allocation Format
                </span>
              ),
              children: (
                <div>
                  <Alert
                    type="info"
                    showIcon
                    className="mb-4"
                    message="Addition Format Instructions"
                    description={
                      <div className="fs-8 text-gray-700 d-flex flex-column gap-1">
                        <div>
                          Use this template when allocating fresh annual leave quotas for employees.
                        </div>
                        <div>
                          • <strong>Columns:</strong> <code>Employee ID</code>, <code>year</code>, <code>Leave Type</code>, <code>Anual Days</code>, <code>Expairation Date</code>, <code>Carry Forword</code>.
                        </div>
                        <div>
                          • <strong>Inherited Rows:</strong> For an employee with multiple leave types, enter the <code>Employee ID</code> and <code>year</code> on the first row—subsequent rows for that employee can leave <code>Employee ID</code> and <code>year</code> blank to automatically inherit them.
                        </div>
                        <div>
                          • <strong>Expairation Date:</strong> Mandatory (e.g. <code>25-12-2026</code> or <code>2026-12-25</code>). Cannot be in the past and must be within the specified Year.
                        </div>
                      </div>
                    }
                  />

                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <span className="fw-bold text-gray-800 fs-7">Preview of Columns & Values:</span>
                    <Button
                      type="primary"
                      icon={<DownloadOutlined />}
                      onClick={() => downloadSampleFile("insert")}
                      style={{
                        backgroundColor: "#107c41",
                        borderColor: "#107c41",
                        borderRadius: "6px",
                        fontWeight: "600",
                      }}
                    >
                      Download Insert Sample (.CSV)
                    </Button>
                  </div>

                  <div className="table-responsive border rounded" style={{ borderColor: "#E2E8F0" }}>
                    <table className="table table-sm table-striped align-middle mb-0 text-center fs-7">
                      <thead className="table-light">
                        <tr className="fw-bold text-gray-800 text-uppercase fs-8">
                          <th className="py-2 text-start ps-3">Employee ID</th>
                          <th className="py-2">year</th>
                          <th className="py-2 text-start ps-3">Leave Type</th>
                          <th className="py-2">Anual Days</th>
                          <th className="py-2">Expairation Date</th>
                          <th className="py-2">Carry Forword</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td className="py-2 text-start ps-3 fw-semibold text-primary">HR345</td>
                          <td><Tag color="blue">{selectedYear || "2026"}</Tag></td>
                          <td className="text-start ps-3"><Tag color="blue">sick Leave</Tag></td>
                          <td className="fw-bold text-success">4</td>
                          <td className="text-muted">25-12-{selectedYear || "2026"}</td>
                          <td><Tag color="success">yes</Tag></td>
                        </tr>
                        <tr>
                          <td className="py-2 text-start ps-3 text-muted fst-italic">↳ (inherited: HR345)</td>
                          <td className="text-muted fst-italic">↳ ({selectedYear || "2026"})</td>
                          <td className="text-start ps-3"><Tag color="green">casual Leave</Tag></td>
                          <td className="fw-bold text-success">5</td>
                          <td className="text-muted">25-12-{selectedYear || "2026"}</td>
                          <td>NO</td>
                        </tr>
                        <tr>
                          <td className="py-2 text-start ps-3 fw-semibold text-primary">EMP002</td>
                          <td><Tag color="blue">{selectedYear || "2026"}</Tag></td>
                          <td className="text-start ps-3"><Tag color="blue">sick Leave</Tag></td>
                          <td className="fw-bold text-success">12</td>
                          <td className="text-muted">25-12-{selectedYear || "2026"}</td>
                          <td><Tag color="success">yes</Tag></td>
                        </tr>
                        <tr>
                          <td className="py-2 text-start ps-3 text-muted fst-italic">↳ (inherited: EMP002)</td>
                          <td className="text-muted fst-italic">↳ ({selectedYear || "2026"})</td>
                          <td className="text-start ps-3"><Tag color="green">casual Leave</Tag></td>
                          <td className="fw-bold text-success">8</td>
                          <td className="text-muted">25-12-{selectedYear || "2026"}</td>
                          <td>NO</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              ),
            },
            {
              key: "update",
              label: (
                <span className="fw-bold fs-7">
                  <span className="badge bg-light-warning text-warning me-2">2. Updation</span>
                  Update Existing Allocation Format
                </span>
              ),
              children: (
                <div>
                  <Alert
                    type="warning"
                    showIcon
                    className="mb-4"
                    message="Updation Format Instructions & Rules"
                    description={
                      <div className="fs-8 text-gray-700 d-flex flex-column gap-2">
                        <div>
                          Use this template when modifying existing employee records. Provide <strong>Remaining Days</strong>. The system automatically recalculates both Remaining Days and Annual Days without affecting past consumed days.
                        </div>
                        <div
                          className="p-2 rounded bg-white text-danger fw-bold fs-8 d-flex align-items-center gap-2"
                          style={{ border: "1px solid #fed7aa" }}
                        >
                          <span style={{ fontSize: "14px" }}>⚠️</span>
                          <span>
                            <strong>Important Rule:</strong> If leaves have already been consumed by an employee, you cannot change or delete that Leave Type.
                          </span>
                        </div>
                      </div>
                    }
                  />

                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <span className="fw-bold text-gray-800 fs-7">Preview of Columns & Values:</span>
                    <Button
                      type="primary"
                      icon={<DownloadOutlined />}
                      onClick={() => downloadSampleFile("update")}
                      style={{
                        backgroundColor: "#107c41",
                        borderColor: "#107c41",
                        borderRadius: "6px",
                        fontWeight: "600",
                      }}
                    >
                      Download Update Sample (.CSV)
                    </Button>
                  </div>

                  <div className="table-responsive border rounded" style={{ borderColor: "#E2E8F0" }}>
                    <table className="table table-sm table-striped align-middle mb-0 text-center fs-7">
                      <thead className="table-light">
                        <tr className="fw-bold text-gray-800 text-uppercase fs-8">
                          <th className="py-2">Employee ID</th>
                          <th className="py-2">Year</th>
                          <th className="py-2">Leave Type</th>
                          <th className="py-2">Remaining Days</th>
                          <th className="py-2">Carry Forward</th>
                          <th className="py-2">Expiration Date</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td className="py-2 fw-semibold text-primary">EMP001</td>
                          <td>{selectedYear || "2026"}</td>
                          <td><Tag color="blue">Sick Leave</Tag></td>
                          <td className="fw-bold text-success">10</td>
                          <td>No</td>
                          <td className="text-muted">{selectedYear || "2026"}-12-31</td>
                        </tr>
                        <tr>
                          <td className="py-2 fw-semibold text-primary">EMP001</td>
                          <td>{selectedYear || "2026"}</td>
                          <td><Tag color="green">Casual Leave</Tag></td>
                          <td className="fw-bold text-success">6</td>
                          <td>No</td>
                          <td className="text-muted">{selectedYear || "2026"}-12-31</td>
                        </tr>
                        <tr>
                          <td className="py-2 fw-semibold text-primary">EMP001</td>
                          <td>{selectedYear || "2026"}</td>
                          <td><Tag color="purple">Earned Leave</Tag></td>
                          <td className="fw-bold text-success">14</td>
                          <td><Tag color="success">Yes</Tag></td>
                          <td className="text-muted">{selectedYear || "2026"}-12-31</td>
                        </tr>
                        <tr>
                          <td className="py-2 fw-semibold text-primary">EMP002</td>
                          <td>{selectedYear || "2026"}</td>
                          <td><Tag color="blue">Sick Leave</Tag></td>
                          <td className="fw-bold text-success">9</td>
                          <td>No</td>
                          <td className="text-muted">{selectedYear || "2026"}-12-31</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              ),
            },
          ]}
        />
      </Modal>

      {/* MODAL: IMPORT NEW LEAVE ALLOCATIONS */}
      <Modal
        title={
          <div className="d-flex align-items-center gap-2">
            <UploadOutlined className="text-primary fs-5" />
            <span className="fw-bolder fs-5 text-gray-900">Import Leave Allocations (Insert Only)</span>
          </div>
        }
        open={isImportModalOpen}
        onCancel={handleResetImport}
        width={900}
        footer={[
          <Button key="close" onClick={handleResetImport}>
            Close
          </Button>,
          <Button
            key="upload"
            type="primary"
            icon={<UploadOutlined />}
            loading={uploading}
            disabled={!importFile}
            onClick={handleImportAllocations}
            style={{ borderRadius: "6px", fontWeight: "600" }}
          >
            Upload & Allocate
          </Button>,
        ]}
      >
        <div className="py-2">
          <Alert
            type="info"
            showIcon
            className="mb-4"
            message="Excel / CSV Import Guidelines & Validation Rules"
            description={
              <div className="fs-8 text-gray-700 d-flex flex-column gap-2 mt-1">
                <div>
                  • <strong>Columns:</strong> <code>Employee ID</code>, <code>year</code>, <code>Leave Type</code>, <code>Anual Days</code>, <code>Expairation Date</code>, <code>Carry Forword</code>.
                </div>
                <div>
                  • <strong>Multi-Row Inheritance:</strong> For an employee with multiple leave types, enter the <code>Employee ID</code> and <code>year</code> on the first row—subsequent rows can leave them blank to automatically inherit them!
                </div>
                <div>
                  • <strong>Year:</strong> The allocation year is read directly from each row in the file.
                </div>
                <div>
                  • <strong>Expairation Date:</strong> <span className="text-danger fw-bold">Mandatory</span> (format: <code>25-12-2026</code> or <code>2026-12-25</code>). <strong>Cannot be in the past</strong> and <strong>must fall within the row's Year</strong>.
                </div>
                <div>
                  • <strong>Insert Only:</strong> Existing allocations for an employee and leave type in the specified year cannot be duplicated.
                </div>
                <div>
                  • <strong>Supported Formats:</strong> <code>.xlsx</code>, <code>.xls</code>, <code>.csv</code> (Max size: 10MB).
                </div>
              </div>
            }
          />

          <div className="d-flex justify-content-between align-items-center mb-3">
            <span className="fw-bold text-gray-800 fs-7">Select or Drop Allocation File:</span>
            <Button
              type="dashed"
              icon={<DownloadOutlined />}
              onClick={() => downloadSampleFile("insert")}
              style={{ borderRadius: "6px", fontWeight: "500", borderColor: "#107c41", color: "#107c41" }}
            >
              Download Sample Template
            </Button>
          </div>

          <Upload.Dragger
            accept=".xlsx, .xls, .csv"
            maxCount={1}
            beforeUpload={(file) => {
              const isLt10M = file.size / 1024 / 1024 < 10;
              if (!isLt10M) {
                message.error("File must be smaller than 10MB!");
                return Upload.LIST_IGNORE;
              }
              setImportFile(file);
              setImportResult(null);
              return false;
            }}
            onRemove={() => {
              setImportFile(null);
              setImportResult(null);
            }}
            fileList={importFile ? [importFile] : []}
            className="mb-4"
            style={{ padding: "20px", background: "#f8fafc", borderRadius: "8px", border: "2px dashed #cbd5e1" }}
          >
            <p className="ant-upload-drag-icon mb-2">
              <InboxOutlined style={{ fontSize: "36px", color: "#0284c7" }} />
            </p>
            <p className="ant-upload-text fw-bold fs-7 mb-1 text-gray-800">
              Click or drag Excel/CSV file to this area to upload
            </p>
            <p className="ant-upload-hint fs-8 text-muted">
              Columns: Employee ID, year, Leave Type, Anual Days, Expairation Date, Carry Forword (Employee ID & Year are automatically carried forward if left blank on subsequent rows)
            </p>
          </Upload.Dragger>

          {/* IMPORT RESULT SUMMARY & ERROR TABLE */}
          {importResult && (
            <div className="mt-4 p-3 border rounded bg-light">
              <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2">
                <span className="fw-bolder fs-7 text-gray-900">Import Processing Summary:</span>
                <div className="d-flex align-items-center gap-2">
                  <Tag color="default" className="fs-8">
                    Total Rows: <strong>{importResult.totalRowsProcessed || 0}</strong>
                  </Tag>
                  <Tag color="success" className="fs-8">
                    <CheckCircleOutlined className="me-1" />
                    Success: <strong>{importResult.successCount || 0}</strong>
                  </Tag>
                  <Tag color={importResult.failureCount > 0 ? "error" : "default"} className="fs-8">
                    <CloseCircleOutlined className="me-1" />
                    Failed: <strong>{importResult.failureCount || 0}</strong>
                  </Tag>
                </div>
              </div>

              {importResult.errors && importResult.errors.length > 0 ? (
                <div>
                  <div className="text-danger fw-bold fs-8 mb-2">
                    Validation Errors ({importResult.errors.length} rejected):
                  </div>
                  <Table
                    size="small"
                    bordered
                    pagination={{ pageSize: 5, size: "small" }}
                    dataSource={importResult.errors.map((err, idx) => ({ ...err, key: idx }))}
                    columns={[
                      {
                        title: "Row #",
                        dataIndex: "rowNumber",
                        key: "rowNumber",
                        width: 70,
                        align: "center",
                        render: (val) => <span className="badge bg-light text-dark">{val}</span>,
                      },
                      {
                        title: "Employee ID",
                        dataIndex: "employeeId",
                        key: "employeeId",
                        width: 120,
                        render: (val) => <span className="fw-semibold text-primary">{val || "—"}</span>,
                      },
                      {
                        title: "Year",
                        dataIndex: "year",
                        key: "year",
                        width: 80,
                        align: "center",
                        render: (val) => <Tag color="blue">{val || "—"}</Tag>,
                      },
                      {
                        title: "Leave Type",
                        dataIndex: "leaveType",
                        key: "leaveType",
                        width: 140,
                        render: (val) => val ? <Tag color="purple">{val}</Tag> : "—",
                      },
                      {
                        title: "Error / Reason",
                        dataIndex: "errorMessage",
                        key: "errorMessage",
                        render: (val) => <span className="text-danger fs-8 fw-medium">{val}</span>,
                      },
                    ]}
                  />
                </div>
              ) : (
                <Alert
                  type="success"
                  showIcon
                  message="All leave allocation records were validated and inserted successfully!"
                  className="mt-2"
                />
              )}
            </div>
          )}
        </div>
      </Modal>
    </>
  );
}
