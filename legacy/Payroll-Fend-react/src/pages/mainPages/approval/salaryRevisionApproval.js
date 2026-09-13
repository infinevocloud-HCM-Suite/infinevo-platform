import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import {
  Table,
  Button,
  Select,
  DatePicker,
  Dropdown,
  Modal,
  Input,
  Tag,
  Card,
  Row,
  Col,
  Space,
  Typography,
  message,
  Tooltip,
  Checkbox,
  Form,
  Spin
} from "antd";
import {
  SearchOutlined,
  MoreOutlined,
  CalendarOutlined,
  DownloadOutlined,
  DeleteOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  ExportOutlined,
  CloseCircleFilled,
  EyeOutlined,
  LockOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

dayjs.extend(customParseFormat);

const { Option } = Select;
const { RangePicker } = DatePicker;
const { Title, Text } = Typography;
const { TextArea } = Input;

// Format currency in Indian format
const formatCurrency = (amount) => {
  if (!amount) return "₹0";
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0
  }).format(amount || 0);
};

const SalaryRevisionApproval = () => {
  const navigate = useNavigate();

  // State for filters
  const [revisionStatus, setRevisionStatus] = useState("All Revisions");
  // const [payoutMonthRange, setPayoutMonthRange] = useState([]);
  const [selectedEmployee, setSelectedEmployee] = useState("");
  const [searchEmployee, setSearchEmployee] = useState("");

  // State for table and selections
  const [selectedRowKeys, setSelectedRowKeys] = useState([]);
  const [data, setData] = useState([]);
  const [filteredData, setFilteredData] = useState([]);
  const [loading, setLoading] = useState(true);
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
    showSizeChanger: true,
    showQuickJumper: true,
  });

  // State for modals
  const [processLaterModal, setProcessLaterModal] = useState(false);
  const [deleteConfirmModal, setDeleteConfirmModal] = useState(false);
  const [exportModal, setExportModal] = useState(false);
  const [processMonth, setProcessMonth] = useState("");
  const [processNotes, setProcessNotes] = useState("");

  // State for export modal
  // const [exportPayoutMonth, setExportPayoutMonth] = useState("");
  const [exportFilterBy, setExportFilterBy] = useState("All Revisions");
  const [exportFormat, setExportFormat] = useState("CSV");
  const [protectWithPassword, setProtectWithPassword] = useState(false);
  const [exportPassword, setExportPassword] = useState("");
  const [exportLoading, setExportLoading] = useState(false);

  // State for employee dropdown options
  const [employeeOptions, setEmployeeOptions] = useState([]);

  // Get organizationId and token
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");

  // Fetch salary revisions from backend
  const fetchSalaryRevisions = async (page = 1, pageSize = 10) => {
    try {
      setLoading(true);

      const response = await axios.get(`${GlobalConst.API_URL}/api/v1/ctc-structures/revision/list`, {
        params: {
          page: page - 1, // Backend expects 0-based index
          size: pageSize
        },
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      console.log("API Response:", response.data);


      if (response.status === 200 && Array.isArray(response.data.data)) {
        const apiData = response.data.data;

        const transformedData = apiData.map(item => ({
          id: item.revisionId,
          revisionId: item.revisionId,
          ctcStructureId: item.ctcStructureId || null,

          employeeId: item.employeeId,
          name: item.employeeName,
          employeeCode: item.employeeNumber,

          // ✅ FIXED: Revision type
          revisionType:
            item.changeInPercent !== null
              ? `${item.changeInPercent}% Revision`
              : "Flat Amount",

          effectiveFrom: item.effectiveDate
            ? dayjs(item.effectiveDate).format("MMMM, YYYY")
            : "N/A",

          // ✅ FIXED: Status from backend
          status: item.revisionStatus || "PENDING",

          previousCTC: item.previousCtc ?? 0,
          revisedCTC: item.ctc ?? 0,

          revisionAmount:
            item.previousCtc ? item.ctc - item.previousCtc : 0,

          percentageIncrease:
            item.changeInPercent !== null
              ? item.changeInPercent
              : item.previousCtc
                ? Math.round(((item.ctc - item.previousCtc) / item.previousCtc) * 100)
                : 0,

          dateSubmitted: item.createdAt
            ? dayjs(item.createdAt).format("YYYY-MM-DD")
            : "N/A",

          designation: "N/A"
        }));


        setData(transformedData);
        setFilteredData(transformedData);

        setPagination(prev => ({
          ...prev,
          current: page,
          pageSize,
          total: response.data.page_context?.per_page
            ? apiData.length
            : apiData.length
        }));

        // Extract unique employees for dropdown
        const uniqueEmployees = [...new Set(transformedData.map(emp => emp.name).filter(name => name))];
        setEmployeeOptions(uniqueEmployees);

      } else {
        errorMsg("Error", response.data?.message || "Failed to load salary revisions", true);
      }
    } catch (error) {
      console.error("Error fetching salary revisions:", error);
      errorMsg("Error", "Failed to load salary revision data", true);
    } finally {
      setLoading(false);
    }
  };

  // Initial fetch
  useEffect(() => {
    if (organizationId && token) {
      fetchSalaryRevisions();
    } else {
      errorMsg("Error", "Authentication required. Please login again.", true);
      setTimeout(() => {
        navigate("/login");
      }, 2000);
    }
  }, [organizationId, token]);

  // Filter data based on all filter criteria
  useEffect(() => {
    let result = [...data];

    // Filter by status
    if (revisionStatus !== "All Revisions") {
      result = result.filter(item => {
        if (revisionStatus === "Pending Revisions") return item.status === "Pending";
        if (revisionStatus === "Approved Revisions") return item.status === "Approved";
        if (revisionStatus === "Rejected Revisions") return item.status === "Rejected";
        if (revisionStatus === "Scheduled Revisions") return item.status === "Scheduled for Processing";
        return true;
      });
    }

    // Filter by payout month range
    // if (payoutMonthRange && payoutMonthRange.length === 2) {
    //   const startMonth = dayjs(payoutMonthRange[0]).format('MMM YYYY');
    //   const endMonth = dayjs(payoutMonthRange[1]).format('MMM YYYY');

    //   result = result.filter(item => {
    //     const itemMonth = item.payoutMonth;
    //     if (!itemMonth || itemMonth === "Not set") return false;

    //     const itemDate = dayjs(`01 ${itemMonth}`, 'DD MMM YYYY');
    //     const startDate = dayjs(`01 ${startMonth}`, 'DD MMM YYYY');
    //     const endDate = dayjs(`01 ${endMonth}`, 'DD MMM YYYY');

    //     return itemDate.isSameOrAfter(startDate) && itemDate.isSameOrBefore(endDate);
    //   });
    // }

    // Filter by selected employee
    if (selectedEmployee) {
      result = result.filter(item => item.name === selectedEmployee);
    }

    // Filter by search term
    if (searchEmployee) {
      result = result.filter(item =>
        item.name?.toLowerCase().includes(searchEmployee.toLowerCase()) ||
        item.employeeCode?.toLowerCase().includes(searchEmployee.toLowerCase()) ||
        item.employeeId?.toLowerCase().includes(searchEmployee.toLowerCase())
      );
    }

    setFilteredData(result);
  }, [revisionStatus, selectedEmployee, searchEmployee, data]);

  // Handle table pagination change
  const handleTableChange = (newPagination) => {
    fetchSalaryRevisions(newPagination.current, newPagination.pageSize);
  };

  // Handle row selection
  const rowSelection = {
    selectedRowKeys,
    onChange: (selectedKeys) => {
      setSelectedRowKeys(selectedKeys);
    },
    getCheckboxProps: (record) => ({
      disabled: false,
      name: record.name,
    }),
  };

  // Handle export modal
  const handleExportModal = () => {
    setExportModal(true);
  };

  // Handle export functionality - Download Excel from backend
  const handleExport = async () => {
    if (protectWithPassword && !exportPassword) {
      message.error('Please enter password for file protection');
      return;
    }

    setExportLoading(true);

    try {
      const token = localStorage.getItem("__t");
      const organizationId = localStorage.getItem('organizationId');

      const response = await axios.get(`${GlobalConst.API_URL}/api/v1/ctc-structures/revisions/export`, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId
        },
        responseType: 'blob' // Important for file download
      });

      // Create download link
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `Salary_Revisions_${dayjs().format('YYYY-MM-DD')}.xlsx`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      // Reset modal
      setExportModal(false);
      // setExportPayoutMonth("");
      setExportFilterBy("All Revisions");
      setExportFormat("CSV");
      setProtectWithPassword(false);
      setExportPassword("");

      message.success('Salary revisions exported successfully', 3);
    } catch (error) {
      console.error('Export error:', error);
      errorMsg("Error", "Failed to export salary revisions", true);
    } finally {
      setExportLoading(false);
    }
  };

  // Handle process later
  // Replace the handleProcessLater and confirmProcessLater functions with this:

  const handleProcessLater = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('Please select at least one revision to process later');
      return;
    }
    setProcessLaterModal(true);
  };

  const confirmProcessLater = async () => {
    if (!processMonth) {
      message.error('Please select a month to process revision');
      return;
    }

    setLoading(true);

    try {
      const token = localStorage.getItem("__t");
      const organizationId = localStorage.getItem('organizationId');

      // Get the selected revision IDs
      const selectedRevisionIds = selectedRowKeys;

      // Prepare the request data according to ProcessLaterRevisionDTO
      const requestData = {
        revisionIds: selectedRevisionIds, // List of Long IDs
        effectiveDate: dayjs(processMonth, 'MMM YYYY').format('YYYY-MM-DD') // Format as LocalDate string
      };

      console.log("Process Later Request Data:", requestData);
      console.log("Selected Revision IDs:", selectedRevisionIds);

      // Call the backend API with the correct DTO structure
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/v1/ctc-structures/revisions/process-later`,
        requestData,
        {
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
            'Content-Type': 'application/json'
          },
        }
      );

      console.log("Process Later Response:", response.data);

      // Refresh data
      await fetchSalaryRevisions(pagination.current, pagination.pageSize);
      setSelectedRowKeys([]);
      setProcessLaterModal(false);
      setProcessMonth("");
      setProcessNotes("");

      successMsg("Payout month has been successfully updated for the selected salary revisions.", `Scheduled ${selectedRevisionIds.length} revision(s) for processing later`, false);
    } catch (error) {
      console.error('Error processing revisions later:', error);

      // More detailed error logging
      if (error.response) {
        console.error('Error response data:', error.response.data);
        console.error('Error response status:', error.response.status);
        errorMsg("Error", error.response.data?.message || "Failed to schedule revisions", true);
      } else if (error.request) {
        console.error('Error request:', error.request);
        errorMsg("Error", "No response from server. Please check your connection.", true);
      } else {
        errorMsg("Error", error.message || "Failed to schedule revisions", true);
      }
    } finally {
      setLoading(false);
    }
  };
 

  // Handle delete revisions
  const handleDelete = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('Please select at least one revision to delete');
      return;
    }
    setDeleteConfirmModal(true);
  };

  const confirmDelete = async () => {
    setLoading(true);

    try {
      const token = localStorage.getItem("__t");
      const organizationId = localStorage.getItem('organizationId');
      const selectedRevisions = filteredData.filter(item => selectedRowKeys.includes(item.id));

      // Delete each selected revision
      const deletePromises = selectedRevisions.map(async (revision) => {
        return axios.delete(`${GlobalConst.API_URL}/api/v1/ctc-structures/revision/delete`, {
          params: {
            revisionId: revision.revisionId
          },
          headers: {
            Authorization: `Bearer ${token}`,
            organizationId: organizationId,
          },
        });
      });

      await Promise.all(deletePromises);

      // Refresh data
      await fetchSalaryRevisions(pagination.current, pagination.pageSize);
      setSelectedRowKeys([]);
      setDeleteConfirmModal(false);

      successMsg("Success", `Deleted ${selectedRevisions.length} revision(s) successfully`, false);
    } catch (error) {
      console.error('Error deleting revisions:', error);
      errorMsg("Error", "Failed to delete revisions", true);
    } finally {
      setLoading(false);
    }
  };

  // Handle approve/reject actions
  const handleApprove = async (record) => {
    setLoading(true);

    try {
      const token = localStorage.getItem("__t");
      const organizationId = localStorage.getItem('organizationId');

      const updateData = {
        employeeId: record.employeeId,
        ctc: record.revisedCTC,
        effectiveDate: record.effectiveFrom ?
          dayjs(record.effectiveFrom.split(',')[0], 'MMMM').format('YYYY-MM-DD') :
          dayjs().format('YYYY-MM-DD'),
        revisionReason: record.comments || "Approved by admin",
        revisionStatus: "APPROVED",

      };

      if (record.revisionId) {
        updateData.revisionId = record.revisionId;
      }

      await axios.put(`${GlobalConst.API_URL}/api/v1/ctc-structures/revision/update`, updateData, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      // Refresh data
      await fetchSalaryRevisions(pagination.current, pagination.pageSize);
      successMsg("Success", `Approved salary revision for ${record.name}`, false);
    } catch (error) {
      console.error('Error approving revision:', error);
      errorMsg("Error", "Failed to approve revision", true);
    } finally {
      setLoading(false);
    }
  };

  const handleReject = async (record) => {
    setLoading(true);

    try {
      const token = localStorage.getItem("__t");
      const organizationId = localStorage.getItem('organizationId');

      const updateData = {
        employeeId: record.employeeId,
        ctc: record.revisedCTC,
        effectiveDate: record.effectiveFrom ?
          dayjs(record.effectiveFrom.split(',')[0], 'MMMM').format('YYYY-MM-DD') :
          dayjs().format('YYYY-MM-DD'),
        revisionReason: record.comments || "Rejected by admin",
        revisionStatus: "REJECTED",
        // paymentMonth: record.payoutMonth ? 
        //   dayjs(record.payoutMonth, 'MMM YYYY').format('YYYY-MM') : 
        //   dayjs().format('YYYY-MM')
      };

      if (record.revisionId) {
        updateData.revisionId = record.revisionId;
      }

      await axios.put(`${GlobalConst.API_URL}/api/v1/ctc-structures/revision/update`, updateData, {
        headers: {
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      // Refresh data
      await fetchSalaryRevisions(pagination.current, pagination.pageSize);
      successMsg("Success", `Rejected salary revision for ${record.name}`, false);
    } catch (error) {
      console.error('Error rejecting revision:', error);
      errorMsg("Error", "Failed to reject revision", true);
    } finally {
      setLoading(false);
    }
  };

  // Get status tag color and icon
  const getStatusConfig = (status) => {
    switch (status) {
      case 'Approved':
        return {
          color: 'success',
          icon: <CheckCircleOutlined />,
          text: 'Approved'
        };
      case 'Pending':
        return {
          color: 'warning',
          icon: <ClockCircleOutlined />,
          text: 'Pending'
        };
      case 'Rejected':
        return {
          color: 'error',
          icon: <CloseCircleOutlined />,
          text: 'Rejected'
        };
      case 'Scheduled for Processing':
        return {
          color: 'processing',
          icon: <CalendarOutlined />,
          text: 'Scheduled'
        };
      default:
        return {
          color: 'default',
          icon: null,
          text: status
        };
    }
  };

  // Handle row click to navigate to view page
  const handleRowClick = (record) => {
    const revisionId = record.revisionId;

    if (revisionId) {
      navigate(`/view-revise-salary/${record.employeeId}?revisionId=${record.revisionId}`);
    } else {
      errorMsg("Error", "Revision ID not found", true);
    }
  };

  // Table columns
  const columns = [
    {
      title: 'NAME',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (text, record) => (
        <div className="cursor-pointer" onClick={() => handleRowClick(record)}>
          <div className="fw-bold">{text}</div>
          <div className="text-muted small">{record.employeeCode}</div>
          {/* <div className="text-muted small">{record.designation || 'Not specified'}</div> */}
        </div>
      ),
      sorter: (a, b) => a.name.localeCompare(b.name),
    },
    {
      title: 'REVISION TYPE',
      dataIndex: 'revisionType',
      key: 'revisionType',
      width: 150,
      render: (text) => {
        const color = text.includes('Decrease') ? 'red' : text.includes('%') ? 'blue' : 'geekblue';
        return (
          <Tag color={color} className="fw-semibold">
            {text}
          </Tag>
        );
      },
    },
    {
      title: 'EFFECTIVE FROM',
      dataIndex: 'effectiveFrom',
      key: 'effectiveFrom',
      width: 150,
      sorter: (a, b) => {
        const monthOrder = {
          'January': 1, 'February': 2, 'March': 3, 'April': 4, 'May': 5, 'June': 6,
          'July': 7, 'August': 8, 'September': 9, 'October': 10, 'November': 11, 'December': 12
        };
        const aMonth = a.effectiveFrom?.split(',')[0];
        const bMonth = b.effectiveFrom?.split(',')[0];
        return (monthOrder[aMonth] || 0) - (monthOrder[bMonth] || 0);
      },
    },
    // {
    //   title: 'PAYOUT MONTH',
    //   dataIndex: 'payoutMonth',
    //   key: 'payoutMonth',
    //   width: 140,
    //   render: (text) => (
    //     <div className="d-flex align-items-center">
    //       <CalendarOutlined className="me-2 text-muted" />
    //       {text}
    //     </div>
    //   ),
    //   sorter: (a, b) => {
    //     const monthOrder = {
    //       'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
    //       'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12
    //     };
    //     const [aMonth, aYear] = (a.payoutMonth || '').split(' ');
    //     const [bMonth, bYear] = (b.payoutMonth || '').split(' ');
    //     return ((parseInt(aYear) || 0) * 12 + (monthOrder[aMonth] || 0)) - 
    //            ((parseInt(bYear) || 0) * 12 + (monthOrder[bMonth] || 0));
    //   },
    // },
    {
      title: 'STATUS',
      dataIndex: 'status',
      key: 'status',
      width: 150,
      render: (status) => {
        const config = getStatusConfig(status);
        return (
          <Tag icon={config.icon} color={config.color} className="fw-semibold">
            {config.text}
          </Tag>
        );
      },
      filters: [
        { text: 'Pending', value: 'Pending' },
        { text: 'Approved', value: 'Approved' },
        { text: 'Rejected', value: 'Rejected' },
      ],
      onFilter: (value, record) => record.status === value,
    },
    {
      title: 'PREVIOUS CTC',
      dataIndex: 'previousCTC',
      key: 'previousCTC',
      width: 150,
      render: (amount) => (
        <div className="fw-bold text-dark">{formatCurrency(amount)}</div>
      ),
      sorter: (a, b) => (a.previousCTC || 0) - (b.previousCTC || 0),
    },
    {
      title: 'REVISED CTC',
      dataIndex: 'revisedCTC',
      key: 'revisedCTC',
      width: 150,
      render: (amount, record) => {
        const isIncrease = record.revisionAmount > 0;
        const isDecrease = record.revisionAmount < 0;

        return (
          <div>
            <div className={`fw-bold ${isIncrease ? 'text-success' : isDecrease ? 'text-danger' : 'text-dark'}`}>
              {formatCurrency(amount)}
            </div>
            {record.revisionAmount !== 0 && (
              <div className={`small ${isIncrease ? 'text-success' : 'text-danger'}`}>
                {isIncrease ? '↑' : '↓'} {formatCurrency(Math.abs(record.revisionAmount))} ({Math.abs(record.percentageIncrease)}%)
              </div>
            )}
          </div>
        );
      },
      sorter: (a, b) => (a.revisedCTC || 0) - (b.revisedCTC || 0),
    },
    // {
    //   title: 'ACTIONS',
    //   key: 'actions',
    //   width: 120,
    //   fixed: 'right',
    //   render: (_, record) => (
    //     <Space size="small">
    //       <Tooltip title="View Details">
    //         <Button 
    //           type="text" 
    //           size="small"
    //           icon={<EyeOutlined style={{ color: '#1890ff' }} />}
    //           onClick={(e) => {
    //             e.stopPropagation();
    //             const revisionId = record.revisionId;
    //             if (revisionId) {
    //   navigate(`/view-revise-salary/${record.employeeId}?revisionId=${record.revisionId}`);
    //             }
    //           }}
    //         />
    //       </Tooltip>
    //       {record.status === 'Pending' && (
    //         <>
    //           <Tooltip title="Approve">
    //             <Button 
    //               type="text" 
    //               size="small"
    //               icon={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
    //               onClick={(e) => {
    //                 e.stopPropagation();
    //                 handleApprove(record);
    //               }}
    //             />
    //           </Tooltip>
    //           <Tooltip title="Reject">
    //             <Button 
    //               type="text" 
    //               size="small"
    //               icon={<CloseCircleOutlined style={{ color: '#ff4d4f' }} />}
    //               onClick={(e) => {
    //                 e.stopPropagation();
    //                 handleReject(record);
    //               }}
    //             />
    //           </Tooltip>
    //         </>
    //       )}
    //       <Dropdown 
    //         overlay={
    //           <Menu>
    //             <Menu.Item key="view" onClick={(e) => {
    //               e.domEvent.stopPropagation();
    //               const revisionId = record.revisionId;
    //               if (revisionId) {
    //   navigate(`/view-revise-salary/${record.employeeId}?revisionId=${record.revisionId}`);
    //               }
    //             }}>
    //               View Details
    //             </Menu.Item>
    //             {record.status === 'Pending' && (
    //               <>
    //                 <Menu.Item key="approve" onClick={(e) => {
    //                   e.domEvent.stopPropagation();
    //                   handleApprove(record);
    //                 }}>
    //                   Approve
    //                 </Menu.Item>
    //                 <Menu.Item key="reject" onClick={(e) => {
    //                   e.domEvent.stopPropagation();
    //                   handleReject(record);
    //                 }}>
    //                   Reject
    //                 </Menu.Item>
    //               </>
    //             )}
    //             <Menu.Divider />
    //             <Menu.Item key="delete" onClick={(e) => {
    //               e.domEvent.stopPropagation();
    //               setSelectedRowKeys([record.id]);
    //               setDeleteConfirmModal(true);
    //             }}>
    //               Delete
    //             </Menu.Item>
    //           </Menu>
    //         }
    //         trigger={['click']}
    //       >
    //         <Button 
    //           type="text" 
    //           size="small"
    //           icon={<MoreOutlined />}
    //           onClick={(e) => e.stopPropagation()}
    //         />
    //       </Dropdown>
    //     </Space>
    //   ),
    // },
  ];

  // Get selected revisions count
  const selectedCount = selectedRowKeys.length;

  // Clear all selections
  const clearSelection = () => {
    setSelectedRowKeys([]);
  };

  return (
    <>
      <Helmet>
        <title>Salary Revision Approval | Admin</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white">
        <div className="p-6">
          {/* Header */}
          <div className="d-flex justify-content-between align-items-center mb-6">
            <div>
              <Title level={2} className="mb-1">All Revisions</Title>
              <Text type="secondary">Manage and approve employee salary revisions</Text>
            </div>
            <div>
              <Button
                type="primary"
                onClick={() => fetchSalaryRevisions(pagination.current, pagination.pageSize)}
                loading={loading}
              >
                Refresh
              </Button>
            </div>
          </div>

          {/* Filters Section */}
          <Card className="mb-6">
            <Row gutter={[16, 16]} align="middle">
              {/* Revision Status Filter */}
              <Col xs={24} md={6}>
                <div className="mb-3">
                  <label className="form-label small text-muted">REVISION STATUS</label>
                  <Select
                    className="w-100"
                    value={revisionStatus}
                    onChange={setRevisionStatus}
                    style={{ height: '40px' }}
                    disabled={loading}
                  >
                    <Option value="All Revisions">All Revisions</Option>
                    <Option value="Pending Revisions">Pending Revisions</Option>
                    <Option value="Approved Revisions">Approved Revisions</Option>
                    <Option value="Rejected Revisions">Rejected Revisions</Option>
                    <Option value="Scheduled Revisions">Scheduled Revisions</Option>
                  </Select>
                </div>
              </Col>

              {/* Payout Month Filter */}
              {/* <Col xs={24} md={6}>
                <div className="mb-3">
                  <label className="form-label small text-muted">PAYOUT MONTH</label>
                  <RangePicker
                    className="w-100"
                    picker="month"
                    format="MMM YYYY"
                    placeholder={['Start Month', 'End Month']}
                    value={payoutMonthRange}
                    onChange={setPayoutMonthRange}
                    style={{ height: '40px' }}
                    disabled={loading}
                  />
                </div>
              </Col> */}

              {/* Employee Filter */}
              <Col xs={24} md={6}>
                <div className="mb-3">
                  <label className="form-label small text-muted">EMPLOYEES</label>
                  <Select
                    className="w-100"
                    value={selectedEmployee}
                    onChange={setSelectedEmployee}
                    placeholder="Select an Employee"
                    showSearch
                    filterOption={(input, option) =>
                      option?.children?.toLowerCase().indexOf(input.toLowerCase()) >= 0
                    }
                    style={{ height: '40px' }}
                    disabled={loading}
                  >
                    <Option value="">All Employees</Option>
                    {employeeOptions.map(emp => (
                      <Option key={emp} value={emp}>{emp}</Option>
                    ))}
                  </Select>
                </div>
              </Col>

              {/* Search and Export */}
              <Col xs={24} md={6}>
                <div className="d-flex gap-3 mb-3">
                  <Input
                    placeholder="Search employee..."
                    prefix={<SearchOutlined />}
                    value={searchEmployee}
                    onChange={(e) => setSearchEmployee(e.target.value)}
                    style={{ height: '40px' }}
                    disabled={loading}
                  />
                  <Button
                    icon={<ExportOutlined />}
                    style={{ height: '40px' }}
                    onClick={handleExportModal}
                    disabled={loading}
                  >
                    Export
                  </Button>
                </div>
              </Col>
            </Row>
          </Card>

          {/* Selected Items Action Bar */}
          {selectedCount > 0 && (
            <Card className="mb-4" style={{ backgroundColor: '#f0f7ff', border: '1px solid #d0e3ff' }}>
              <div className="d-flex justify-content-between align-items-center">
                <div className="d-flex align-items-center">
                  <Button
                    type="text"
                    icon={<CloseCircleFilled />}
                    onClick={clearSelection}
                    className="me-3"
                  />
                  <div className="fw-bold">
                    {selectedCount} revision{selectedCount > 1 ? 's' : ''} selected
                  </div>
                </div>
                <div className="d-flex gap-2">
                  <Button
                    type="primary"
                    icon={<ClockCircleOutlined />}
                    onClick={handleProcessLater}
                    
                    disabled={loading}
                  >
                    Process Later
                  </Button>
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    onClick={handleDelete}
                    disabled={loading}
                  >
                    Delete
                  </Button>
                </div>
              </div>
            </Card>
          )}

          {/* Main Table */}
          <Card>
            <div className="table-responsive">
              {loading && data.length === 0 ? (
                <div className="text-center py-5">
                  <Spin size="large" />
                  <div className="mt-3 text-muted">Loading salary revisions...</div>
                </div>
              ) : (
                <Table
                  columns={columns}
                  dataSource={filteredData}
                  rowKey="id"
                  rowSelection={rowSelection}
                  loading={loading}
                  pagination={{
                    ...pagination,
                    showTotal: (total, range) =>
                      `${range[0]}-${range[1]} of ${total} revisions`,
                  }}
                  onChange={handleTableChange}
                  scroll={{ x: 1200 }}
                  size="middle"
                  onRow={(record) => ({
                    onClick: () => handleRowClick(record),
                    style: { cursor: 'pointer' }
                  })}
                />
              )}
            </div>
          </Card>

          {/* Process Later Modal */}
          <Modal
            title="Process Revision Later"
            closable={false}
            open={processLaterModal}
            onCancel={() => setProcessLaterModal(false)}
            footer={[
              <Button key="cancel" onClick={() => setProcessLaterModal(false)}>
                Cancel
              </Button>,
              <Button
                key="confirm"
                type="primary"
                onClick={confirmProcessLater}
                loading={loading}
              >
                Confirm
              </Button>
            ]}
            width={500}
          >
            <div className="mb-4">
              <div className="mb-2">
                <label className="form-label fw-semibold">
                  Process Revision by<span className="text-danger">*</span>
                </label>
                <DatePicker
                  picker="month"
                  format="MMM YYYY"
                  className="w-100"
                  value={processMonth ? dayjs(processMonth, 'MMM YYYY') : null}
                  onChange={(date, dateString) => setProcessMonth(dateString)}
                  placeholder="MMM yyyy"
                  style={{ height: '40px' }}
                />
                <div className="text-muted small mt-1">* indicates mandatory fields</div>
              </div>

              <div className="mb-2">
                <label className="form-label fw-semibold">Notes (if any)</label>
                <TextArea
                  rows={4}
                  value={processNotes}
                  onChange={(e) => setProcessNotes(e.target.value)}
                  placeholder="Add any notes or comments..."
                />
              </div>
            </div>
          </Modal>

          {/* Delete Confirmation Modal */}
          <Modal
            title="Confirm Delete"
            open={deleteConfirmModal}
            onCancel={() => setDeleteConfirmModal(false)}
            footer={[
              <Button key="cancel" onClick={() => setDeleteConfirmModal(false)}>
                Cancel
              </Button>,
              <Button
                key="delete"
                danger
                onClick={confirmDelete}
                loading={loading}
              >
                Proceed
              </Button>
            ]}
            width={500}
          >
            <div className="text-center py-3">
              <ExclamationCircleOutlined style={{ fontSize: '48px', color: '#ff4d4f' }} />
              <h5 className="mt-3 mb-2">Are you sure you want to delete the selected salary revisions?</h5>
              <p className="text-muted">
                This action cannot be undone. {selectedCount} revision{selectedCount > 1 ? 's' : ''} will be permanently deleted.
              </p>
            </div>
          </Modal>

          {/* Export Modal */}
          <Modal
            title="Export Salary Revisions"
            open={exportModal}
            closable={false}
            onCancel={() => {
              setExportModal(false);
              setExportPassword("");
              setProtectWithPassword(false);
            }}
            footer={[
              <Button key="cancel" onClick={() => {
                setExportModal(false);
                setExportPassword("");
                setProtectWithPassword(false);
              }}>
                Cancel
              </Button>,
              <Button
                key="export"
                type="primary"
                onClick={handleExport}
                loading={exportLoading}
                icon={<DownloadOutlined />}
              >
                Export
              </Button>
            ]}
            width={500}
          >
            <div className="mb-4">
              <Form layout="vertical">
                {/* Payout Month */}
                {/* <Form.Item label="Payout Month">
                  <DatePicker
                    picker="month"
                    format="MMM yyyy"
                    className="w-100"
                    value={exportPayoutMonth ? dayjs(exportPayoutMonth, 'MMM yyyy') : null}
                    onChange={(date, dateString) => setExportPayoutMonth(dateString)}
                    placeholder="MMM yyyy"
                    style={{ height: '40px' }}
                  />
                </Form.Item> */}

                {/* Filter By */}
                <Form.Item label="Filter By">
                  <Select
                    className="w-100"
                    value={exportFilterBy}
                    onChange={setExportFilterBy}
                    style={{ height: '40px' }}
                  >
                    <Option value="All Revisions">All Revisions</Option>
                    <Option value="Pending Revisions">Pending Revisions</Option>
                    <Option value="Approved Revisions">Approved Revisions</Option>
                    <Option value="Rejected Revisions">Rejected Revisions</Option>
                  </Select>
                </Form.Item>

                {/* Export As */}
                <Form.Item label="Export as">
                  <Select
                    className="w-100"
                    value={exportFormat}
                    onChange={setExportFormat}
                    style={{ height: '40px' }}
                  >
                    <Option value="CSV">CSV (Comma Separated Values)</Option>
                    <Option value="XLS">XLS (Microsoft Excel 1997-2004 Compatible)</Option>
                    <Option value="XLSX">XLSX (Microsoft Excel)</Option>
                  </Select>
                </Form.Item>

                {/* Protect with Password */}
                {/* <Form.Item>
                  <Checkbox
                    checked={protectWithPassword}
                    onChange={(e) => setProtectWithPassword(e.target.checked)}
                  >
                    Protect this file with a password
                  </Checkbox>
                </Form.Item> */}

                {/* Password Input (Conditional) */}
                {/* {protectWithPassword && (
                  <Form.Item
                    label="Password"
                    required
                    validateStatus={protectWithPassword && !exportPassword ? "error" : ""}
                    help={protectWithPassword && !exportPassword ? "Password is required" : ""}
                  >
                    <Input.Password
                      value={exportPassword}
                      onChange={(e) => setExportPassword(e.target.value)}
                      placeholder="Enter password for file protection"
                      prefix={<LockOutlined />}
                      style={{ height: '40px' }}
                    />
                  </Form.Item>
                )} */}
              </Form>
            </div>
          </Modal>
        </div>
      </div>
    </>
  );
};

// Menu component for Dropdown
const Menu = ({ children, ...props }) => {
  const AntMenu = require('antd').Menu;
  return <AntMenu {...props}>{children}</AntMenu>;
};

export default SalaryRevisionApproval;