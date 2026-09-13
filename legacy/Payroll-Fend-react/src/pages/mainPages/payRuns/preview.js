import { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import {
    Card,
    Dropdown,
    Space,
    Table,
    Tag,
    Input,
    Button,
    Row,
    Col,
    Statistic,
    Modal,
    message
} from 'antd';
import {
    DownOutlined,
    SearchOutlined,
    MoreOutlined,
    DownloadOutlined,
    UserOutlined,
    FileTextOutlined,
    BarChartOutlined,
    EyeOutlined,
    DeleteOutlined,
    PlusOutlined,
    UploadOutlined
} from '@ant-design/icons';
import { useNavigate, useLocation } from "react-router-dom";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg } from "../../../shared/helpers/msgHelper";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import _ from "lodash";
import axios from "axios";
import employeeData from "../../../shared/appConfig/employeeData";
import { Select } from "antd";
import { useParams } from "react-router-dom";


const { Column } = Table;

export default function Preview() {
    const navigate = useNavigate();
    const location = useLocation();
    const [activeTab, setActiveTab] = useState('employeeSummary');
    const [isDeleteModalVisible, setIsDeleteModalVisible] = useState(false);
    const [selectedEmployee, setSelectedEmployee] = useState(null);
    const [searchText, setSearchText] = useState('');
    const [employeeFilter, setEmployeeFilter] = useState('All Employees');
    const [employees, setEmployees] = useState([]);
    const [selectedRowKeys, setSelectedRowKeys] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const { payrunId } = useParams();
    const [isDeleting, setIsDeleting] = useState(false);

    const [showAddEmployeeForm, setShowAddEmployeeForm] = useState(false);
    const RequiredStar = () => <span className="text-danger">*</span>;

    // PayRun data from backend
    const [payRunData, setPayRunData] = useState({
        id: 1,
        type: 'One Time Payout',
        subType: 'Leave Encashment',
        status: 'DRAFT',
        period: 'August 2025',
        netPay: 0,
        payrollCost: 0,
        taxesDeductions: 0,
        paymentDate: '2025-08-27',
        employeeCount: 0,
        currency: '₹'
    });

    // Filter options for employees
    const employeeFilterOptions = [
        'All Employees',
        'Active Employees',
        'Tax Overridden Employees',
        'Yet to pay Employees',
        'Paid Employees',
        'Yet to Pay and Payment Failed Employees'
    ];

    const headerDropdownItems = [
        {
            label: 'Show Download',
            key: 'download',
            icon: <DownloadOutlined />
        },
        {
            type: 'divider',
        },
        {
            label: 'Delete Payrun',
            key: 'delete-payrun',
            icon: <DeleteOutlined />,
            danger: true,
            disabled: isDeleting,
        },
    ];

    // Extract employees array from the imported data
    const getEmployeeOptions = () => {
        if (employeeData && employeeData.employees && Array.isArray(employeeData.employees)) {
            return employeeData.employees;
        }

        // Fallback data if employeeData is not in expected format
        return [
            {
                basicDetails: {
                    firstName: "John",
                    lastName: "Doe",
                    employeeId: "EMP001",
                    departmentName: "Engineering"
                }
            },
            {
                basicDetails: {
                    firstName: "Jane",
                    lastName: "Smith",
                    employeeId: "EMP002",
                    departmentName: "Marketing"
                }
            },
            {
                basicDetails: {
                    firstName: "Michael",
                    lastName: "Scott",
                    employeeId: "EMP003",
                    departmentName: "Management"
                }
            }
        ];
    };

    const employeeOptions = getEmployeeOptions();

    useEffect(() => {
        console.log("Current Payrun ID:", payrunId);
    }, [payrunId]);

    // Fetch payrun data from backend
    const fetchPayRunData = async () => {
        try {
            setIsLoading(true);
            const organizationId = localStorage.getItem('organizationId') || 'default-org-id';

            // Fetch payrun details
            const payRunResponse = await axios.get(
                `${GlobalConst.API_URL}/api/payruns/${payrunId}`,
                {
                    headers: {
                        'organizationId': organizationId
                    }
                }
            );

            if (payRunResponse.data && payRunResponse.data.payrollRun) {
                const backendData = payRunResponse.data.payrollRun;

                // Transform backend data to match frontend structure
                setPayRunData({
                    id: backendData.payrunId,
                    type: backendData.type ? backendData.type.charAt(0).toUpperCase() + backendData.type.slice(1).toLowerCase() : 'Regular Payroll',
                    subType: backendData.compensationName || 'Regular',
                    status: backendData.status || 'DRAFT',
                    period: backendData.processingPeriod || 'August 2025',
                    netPay: backendData.totalNetPay ? parseFloat(backendData.totalNetPay) : 0,
                    totalLeaves: (backendData.totalNoOfLeaves !== undefined && backendData.totalNoOfLeaves !== null) ? backendData.totalNoOfLeaves : 0,

                    payrollCost: backendData.totalPayrollCost ? parseFloat(backendData.totalPayrollCost) : 0,
                    taxesDeductions: backendData.totalTaxes ? parseFloat(backendData.totalTaxes) : 0,
                    paymentDate: backendData.payDate || '2025-08-27',
                    employeeCount: backendData.noOfEmployees || 0,
                    currency: '₹'
                });
                console.log("totalLeaves backendData:", backendData.totalNoOfLeaves);
            }

            // Fetch employee list for this payrun
            const employeesResponse = await axios.get(
                `${GlobalConst.API_URL}/api/payrun-employees/list/${payrunId}`,
                {
                    headers: {
                        'organizationId': organizationId
                    }
                }
            );

            if (employeesResponse.data && employeesResponse.data.employees) {
                const backendEmployees = employeesResponse.data.employees;

                // Transform backend employee data to match frontend structure
                // All calculations are done in backend, just map the data
                const transformedEmployees = backendEmployees.map(emp => {
                    // Calculate total benefits from epfComponents (only EPF_EMPLOYER)
                    let benefits = 0;
                    if (emp.epfComponents && Array.isArray(emp.epfComponents)) {
                        benefits = emp.epfComponents.reduce((total, component) => {
                            if (component.componentCode === 'EPF_EMPLOYER') {
                                return total + (component.monthlyAmount || 0);
                            }
                            return total;
                        }, 0);
                    }

                    return {
                        key: emp.employeeId,
                        name: emp.employeeName || emp.fullName || 'Unknown Employee',
                        employeeId: emp.employeeId,
                        employeeNumber: emp.employeeNumber,
                        paidDays: emp.paidDays || 30,
                        totalLeaves: (emp.totalNoOfLeaves !== undefined && emp.totalNoOfLeaves !== null) ? emp.totalNoOfLeaves : 0,
                        lopAmount: (emp.lop !== undefined && emp.lop !== null) ? emp.lop : 0,

                        grossPay: emp.totalEarnings || 0, // Already calculated in backend
                        deductions: emp.totalDeductions || 0, // Direct from backend
                        taxes: emp.totalTaxes || 0, // Professional Tax from backend
                        benefits: benefits, // EPF_EMPLOYER from backend
                        reimbursements: emp.totalReimbursements || 0,
                        bonus: emp.bonus || 0,
                        netPay: emp.netPay || 0, // Already calculated in backend
                        tds: emp.monthlyTds || 0,
                        claimDeduction: emp.claimDeduction || 0,
                        claimReimbursement: emp.claimReimbursement || 0,

                        status: emp.employeeStatus === 'ACTIVE' ? 'Active' : 'Inactive',
                        paymentStatus: emp.paymentStatus || 'yet_to_pay',
                        monthlySalary: emp.monthlySalary || 0,
                        epfComponents: emp.epfComponents || [], // EPF components from backend
                        professionalTaxSlabs: emp.professionalTaxSlabs || [] // Professional tax slabs from backend
                    };
                });

                setEmployees(transformedEmployees);

                // Calculate totals from backend data
                const totalNetPay = transformedEmployees.reduce((sum, emp) => sum + parseFloat(emp.netPay), 0);
                const totalGrossPay = transformedEmployees.reduce((sum, emp) => sum + parseFloat(emp.grossPay), 0);
                const totalTaxes = transformedEmployees.reduce((sum, emp) => sum + parseFloat(emp.taxes), 0);
                const totalBenefits = transformedEmployees.reduce((sum, emp) => sum + parseFloat(emp.benefits), 0);
                const totalDeductions = transformedEmployees.reduce((sum, emp) => sum + parseFloat(emp.deductions), 0);

                // Update payrun data with calculated totals
                setPayRunData(prev => ({
                    ...prev,
                    netPay: totalNetPay,
                    payrollCost: totalGrossPay,
                    taxesDeductions: totalTaxes,
                    employeeCount: transformedEmployees.length
                }));
            }

        } catch (error) {
            console.error('Error fetching payrun data:', error);
            errorMsg("Error", "Failed to load payrun data. Please try again.", false);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchPayRunData();
        console.log("Current Payrun ID:", payrunId);
    }, [location]);

    const handleAddEmployeeSubmit = async (values, { setSubmitting, resetForm }) => {
        setSubmitting(true);
        try {
            // Find the selected employee from options
            const selectedEmployee = employeeOptions.find(emp =>
                emp.basicDetails.employeeId === values.employee
            );

            if (!selectedEmployee) {
                errorMsg("Error", "Selected employee not found", false);
                setSubmitting(false);
                return;
            }

            // Note: Adding employee should be done through backend API
            // This frontend-only addition won't have proper calculations
            // For now, we'll just add with placeholder values
            const newEmployee = {
                key: Date.now(),
                name: `${selectedEmployee.basicDetails.firstName} ${selectedEmployee.basicDetails.lastName}`,
                employeeId: selectedEmployee.basicDetails.employeeId,
                paidDays: 30,
                grossPay: values.amount,
                deductions: 0, // Will be calculated by backend
                taxes: 0, // Will be calculated by backend
                benefits: 0, // Will be calculated by backend
                reimbursements: 0,
                netPay: values.amount, // Temporary, should be calculated by backend
                tds: 0,
                status: 'Active'
            };

            setEmployees([...employees, newEmployee]);

            // Update payrun data with new totals
            setPayRunData(prev => ({
                ...prev,
                netPay: prev.netPay + parseFloat(newEmployee.netPay),
                payrollCost: prev.payrollCost + parseFloat(newEmployee.grossPay),
                employeeCount: prev.employeeCount + 1
            }));

            setSubmitting(false);
            resetForm();
            setShowAddEmployeeForm(false);
        } catch (error) {
            errorMsg("Error", "Failed to add employee. Please try again.", false);
            setSubmitting(false);
        }
    };

    // Employee action dropdown items
    const getEmployeeActionItems = (employee) => [
        {
            label: 'View Details',
            key: 'view-details',
            icon: <EyeOutlined />,
            onClick: () => handleViewEmployee(employee)
        },
        {
            type: 'divider',
        },
        {
            label: 'Remove Employee',
            key: 'remove-employee',
            icon: <DeleteOutlined />,
            danger: true,
            onClick: () => handleRemoveEmployee(employee)
        }
    ];

    const handleHeaderMenuClick = (e) => {
        if (e.key === 'download') {
            handleDownload();
        } else if (e.key === 'delete-payrun') {
            handleDeletePayrun();
        }
    };

    const handleDownload = () => {
        message.success('Download initiated');
        // Add actual download logic here
    };

    const handleDeletePayrun = () => {
        Modal.confirm({
            title: 'Delete Payrun',
            content: 'Are you sure you want to delete this payrun? This action cannot be undone.',
            okText: 'Yes',
            okType: 'danger',
            cancelText: 'No',
            async onOk() {
                try {
                    if (isDeleting) return;

                    setIsDeleting(true);
                    const hide = message.loading('Deleting payrun...', 0);

                    const organizationId = localStorage.getItem('organizationId') || 'default-org-id';

                    const url = `${GlobalConst.API_URL}/api/payruns/${payrunId}`;

                    const response = await axios.delete(url, {
                        headers: {
                            'organizationId': organizationId
                        }
                    });

                    hide();
                    setIsDeleting(false);

                    message.success(response.data?.message || 'Payrun deleted successfully');
                    navigate('/payruns');

                } catch (error) {
                    setIsDeleting(false);
                    try { message.destroy(); } catch (e) { }
                    console.error("Error deleting payrun:", error);

                    const serverMsg = error?.response?.data?.message || error?.message || 'Failed to delete payrun. Please try again.';
                    errorMsg("Error", serverMsg, false);
                    message.error(serverMsg);
                }
            }
        });
    };

    const handleSubmitAndApprove = () => {
        const paymentDate = new Date(payRunData.paymentDate);
        const formattedDate = paymentDate.toLocaleDateString('en-GB');
        const period = payRunData.period;

        Modal.confirm({
            title: 'Approve Payroll',
            content: (
                <div>
                    <p>The LOP data from InfiNevoCloud People has not been synced yet.</p>
                    <p>
                        You are about to approve the payroll for <strong>{period}</strong>. Once approved, payments for all employees can be processed on the scheduled pay date, <strong>{formattedDate}</strong>.
                    </p>
                </div>
            ),
            okText: 'Submit and Approve',
            cancelText: 'Cancel',
            async onOk() {
                try {
                    const organizationId = localStorage.getItem('organizationId') || 'default-org-id';

                    const response = await axios.put(
                        `${GlobalConst.API_URL}/api/payruns/${payrunId}/approve?canPostPayrunTransactions=true`,
                        {},
                        {
                            headers: {
                                'organizationId': organizationId
                            }
                        }
                    );

                    if (response.status === 200) {
                        message.success(response.data?.message || "Pay run has been approved successfully!");
                        navigate(`/summary/${payrunId}`);
                    } else {
                        errorMsg("Error", "Failed to approve payrun. Please try again.", false);
                    }
                } catch (error) {
                    console.error("Error approving payrun:", error);
                    errorMsg("Error", "Something went wrong while approving the payrun.", false);
                }
            }
        });
    };

    const handleViewEmployee = (employee) => {
        navigate(`/employee/${employee.employeeId}`);
    };

    const handleRemoveEmployee = (employee) => {
        setSelectedEmployee(employee);
        setIsDeleteModalVisible(true);
    };

    const confirmRemoveEmployee = () => {
        if (selectedEmployee) {
            const updatedEmployees = employees.filter(emp => emp.key !== selectedEmployee.key);
            setEmployees(updatedEmployees);

            if (selectedRowKeys.includes(selectedEmployee.key)) {
                setSelectedRowKeys([]);
                setSelectedEmployee(null);
            }

            message.success(`Employee ${selectedEmployee.name} removed successfully`);
        }
        setIsDeleteModalVisible(false);
    };

    const handleImportEmployees = () => {
        message.info('Import employees functionality');
    };

    const filteredEmployees = employees.filter(employee =>
        employee.name.toLowerCase().includes(searchText.toLowerCase()) ||
        employee.employeeId.toLowerCase().includes(searchText.toLowerCase())
    );

    // Handle row selection
    const rowSelection = {
        selectedRowKeys,
        onChange: (selectedKeys, selectedRows) => {
            setSelectedRowKeys(selectedKeys);
            if (selectedRows.length > 0) {
                setSelectedEmployee(selectedRows[0]);
            } else {
                setSelectedEmployee(null);
            }
        },
    };

    // Tax details data - will be fetched from backend
    const [taxDetails, setTaxDetails] = useState([
        {
            key: '1',
            taxName: 'Income Tax',
            paidByEmployer: 15000.00,
            paidByEmployee: 11000.00
        }
    ]);

    // Benefits data - will be fetched from backend
    const [benefitsData, setBenefitsData] = useState([
        {
            key: '1',
            benefitName: 'Health Insurance',
            employerContribution: 5000.00,
            employeeContribution: 2000.00
        }
    ]);

    // Donations data
    const donationsData = [
        {
            key: '1',
            deductionName: 'Charity Donation',
            employeeContribution: 1000.00
        }
    ];

    // Component breakdown data
    const componentBreakdown = [
        {
            key: '1',
            component: 'One Time Earning',
            employeesInvolved: 1,
            totalAmount: 100000.00
        },
        {
            key: '2',
            component: 'Commission',
            employeesInvolved: 1,
            totalAmount: 50000.00
        },
        {
            key: '3',
            component: 'Leave Encashment',
            employeesInvolved: 1,
            totalAmount: 50000.00
        }
    ];

    const tabList = [
        {
            key: 'employeeSummary',
            tab: (
                <span>
                    <UserOutlined />
                    Employee Summary
                </span>
            ),
        },
    ];

    const contentList = {
        employeeSummary: (
            <div className="p-3 p-md-4">
                <div className="d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center mb-4 gap-3">
                    <div className="d-flex flex-column flex-md-row align-items-start align-items-md-center gap-2">
                        <span className="fw-semibold">All Employees</span>
                        <Dropdown
                            menu={{
                                items: employeeFilterOptions.map(filter => ({
                                    key: filter,
                                    label: filter,
                                })),
                                onClick: ({ key }) => setEmployeeFilter(key),
                            }}
                            trigger={['click']}
                        >
                            <Button size="small" className="d-flex align-items-center">
                                {employeeFilter} <DownOutlined />
                            </Button>
                        </Dropdown>

                        <Input
                            placeholder="Search Employee"
                            prefix={<SearchOutlined />}
                            value={searchText}
                            onChange={(e) => setSearchText(e.target.value)}
                            style={{ width: 200 }}
                            size="small"
                        />
                    </div>
                </div>

                {isLoading ? (
                    <div className="text-center py-5">
                        <div className="spinner-border text-primary" role="status">
                            <span className="visually-hidden">Loading...</span>
                        </div>
                    </div>
                ) : (
                    <Table
                        dataSource={filteredEmployees}
                        pagination={false}
                        scroll={{ x: true }}
                        rowSelection={rowSelection}
                        size="small"
                        loading={isLoading}
                    >
                        <Column
                            title="EMPLOYEE NAME"
                            dataIndex="name"
                            key="name"
                            fixed="left"
                            width={200}
                            render={(text, record) => (
                                <div>
                                    <div className="fw-bold">{text}</div>
                                    <div className="text-muted small">#{record.employeeNumber || record.employeeId}</div>
                                </div>
                            )}
                        />
                        <Column
                            title="PAID DAYS"
                            dataIndex="paidDays"
                            key="paidDays"
                            width={100}
                            align="center"
                            render={(days) => days || 30}
                        />
                        <Column
                            title="GROSS PAY"
                            dataIndex="grossPay"
                            key="grossPay"
                            width={120}
                            render={(amount) => `₹${parseFloat(amount).toLocaleString()}`}
                        />
                        <Column
                            title="DEDUCTIONS"
                            dataIndex="deductions"
                            key="deductions"
                            width={120}
                            render={(amount) => `₹${parseFloat(amount).toLocaleString()}`}
                        />
                        <Column
                            title=" PROFESSIONAL TAX"
                            dataIndex="taxes"
                            key="taxes"
                            width={100}
                            render={(amount) => `₹${parseFloat(amount).toLocaleString()}`}
                        />
                        <Column
                            title="BENEFITS"
                            dataIndex="benefits"
                            key="benefits"
                            width={120}
                            render={(amount) => `₹${parseFloat(amount).toLocaleString()}`}
                        />
                        <Column
                            title="REIMBURSEMENTS"
                            dataIndex="reimbursements"
                            key="reimbursements"
                            width={140}
                            render={(amount) => `₹${parseFloat(amount).toLocaleString()}`}
                        />

                        <Column
                            title="BONUS"
                            dataIndex="bonus"
                            key="bonus"
                            width={140}
                            render={(amount) => `₹${parseFloat(amount || 0).toLocaleString()}`}
                        />

                        <Column
                            title="TDS"
                            dataIndex="tds"
                            key="tds"
                            width={120}
                            render={(amount) => `₹${parseFloat(amount || 0).toLocaleString()}`}
                        />

                        <Column
                            title="LOP DAYS"
                            dataIndex="totalLeaves"
                            key="totalLeaves"
                            align="center"
                            render={(val) => (val !== null && val !== undefined) ? val : 0}
                        />

                        <Column
                            title="LOP AMOUNT"
                            dataIndex="lopAmount"
                            key="lopAmount"
                            width={120}
                            render={(amount) => `₹${parseFloat(amount).toLocaleString()}`}

                        />
                        <Column
                            title="CLAIM DEDUCTION"
                            dataIndex="claimDeduction"
                            key="claimDeduction"
                            width={140}
                            render={(amount) => `₹${parseFloat(amount || 0).toLocaleString()}`}
                        />
                        <Column
                            title="CLAIM REIMBURSEMENT"
                            dataIndex="claimReimbursement"
                            key="claimReimbursement"
                            width={160}
                            render={(amount) => `₹${parseFloat(amount || 0).toLocaleString()}`}
                        />
                        <Column
                            title="NET PAY"
                            dataIndex="netPay"
                            key="netPay"
                            width={120}
                            fixed="right"
                            render={(amount) => (
                                <div className="fw-bold">
                                    ₹{parseFloat(amount).toLocaleString()}
                                </div>
                            )}
                        />
                        <Column
                            title="ACTION"
                            key="action"
                            fixed="right"
                            width={80}
                            render={(_, record) => (
                                <Dropdown
                                    menu={{
                                        items: getEmployeeActionItems(record),
                                    }}
                                    trigger={['click']}
                                >
                                    <Button type="text" icon={<MoreOutlined />} size="small" />
                                </Dropdown>
                            )}
                        />
                    </Table>
                )}
            </div>
        ),

        taxesDeductions: (
            <div className="p-3 p-md-4">
                <div className="mb-5">
                    <h5 className="fw-bold mb-3">Tax Details</h5>
                    <Table
                        dataSource={taxDetails}
                        pagination={false}
                        scroll={{ x: true }}
                        size="small"
                        summary={() => (
                            <Table.Summary>
                                <Table.Summary.Row>
                                    <Table.Summary.Cell index={0} className="fw-bold">Total</Table.Summary.Cell>
                                    <Table.Summary.Cell index={1} className="fw-bold">
                                        ₹{taxDetails.reduce((sum, item) => sum + item.paidByEmployer, 0).toLocaleString()}
                                    </Table.Summary.Cell>
                                    <Table.Summary.Cell index={2} className="fw-bold">
                                        ₹{taxDetails.reduce((sum, item) => sum + item.paidByEmployee, 0).toLocaleString()}
                                    </Table.Summary.Cell>
                                </Table.Summary.Row>
                            </Table.Summary>
                        )}
                    >
                        <Column title="TAX NAME" dataIndex="taxName" key="taxName" />
                        <Column
                            title="PAID BY EMPLOYER"
                            dataIndex="paidByEmployer"
                            key="paidByEmployer"
                            render={(amount) => `₹${amount.toLocaleString()}`}
                        />
                        <Column
                            title="PAID BY EMPLOYEE"
                            dataIndex="paidByEmployee"
                            key="paidByEmployee"
                            render={(amount) => `₹${amount.toLocaleString()}`}
                        />
                    </Table>
                </div>

                <div className="mb-5">
                    <h5 className="fw-bold mb-3">Benefits</h5>
                    <Table
                        dataSource={benefitsData}
                        pagination={false}
                        scroll={{ x: true }}
                        size="small"
                    >
                        <Column title="BENEFIT NAME" dataIndex="benefitName" key="benefitName" />
                        <Column
                            title="EMPLOYER'S CONTRIBUTION"
                            dataIndex="employerContribution"
                            key="employerContribution"
                            render={(amount) => `₹${amount.toLocaleString()}`}
                        />
                        <Column
                            title="EMPLOYEES' CONTRIBUTION"
                            dataIndex="employeeContribution"
                            key="employeeContribution"
                            render={(amount) => `₹${amount.toLocaleString()}`}
                        />
                    </Table>
                    {benefitsData.length === 0 && (
                        <div className="text-center text-muted py-4">
                            There are no benefits present in this payrun.
                        </div>
                    )}
                </div>

                <div>
                    <h5 className="fw-bold mb-3">Donations</h5>
                    <Table
                        dataSource={donationsData}
                        pagination={false}
                        scroll={{ x: true }}
                        size="small"
                    >
                        <Column title="DEDUCTION NAME" dataIndex="deductionName" key="deductionName" />
                        <Column
                            title="EMPLOYEES' CONTRIBUTION"
                            dataIndex="employeeContribution"
                            key="employeeContribution"
                            render={(amount) => `₹${amount.toLocaleString()}`}
                        />
                    </Table>
                    {donationsData.length === 0 && (
                        <div className="text-center text-muted py-4">
                            There are no donations present in this payrun.
                        </div>
                    )}
                </div>
            </div>
        ),

        overallInsights: (
            <div className="p-3 p-md-4">
                <div className="text-center mb-5">
                    <h4 className="fw-bold">Insights for {payRunData.period} Payrun</h4>
                </div>

                <div className="mb-5">
                    <h5 className="fw-bold mb-3">Employee Breakdown</h5>
                    <Row gutter={[16, 16]}>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">{payRunData.employeeCount}</div>
                                <div className="text-muted small">Active Employees</div>
                            </Card>
                        </Col>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">0</div>
                                <div className="text-muted small">Paid Employees</div>
                            </Card>
                        </Col>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">0</div>
                                <div className="text-muted small">New Joinee's Skipped</div>
                            </Card>
                        </Col>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">0</div>
                                <div className="text-muted small">Skipped Employees</div>
                            </Card>
                        </Col>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">0</div>
                                <div className="text-muted small">Salary Withheld Employees</div>
                            </Card>
                        </Col>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">0</div>
                                <div className="text-muted small">New Joinee's Arrear Released</div>
                            </Card>
                        </Col>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">0</div>
                                <div className="text-muted small">Salary Released Employees</div>
                            </Card>
                        </Col>
                        <Col xs={12} md={8} lg={6}>
                            <Card size="small" className="text-center">
                                <div className="fw-bold fs-4">0</div>
                                <div className="text-muted small">Lop Reversed Employees</div>
                            </Card>
                        </Col>
                    </Row>
                </div>

                <div className="mb-5">
                    <h5 className="fw-bold mb-3">Payment Mode Summary</h5>
                    <Row gutter={[16, 16]}>
                        <Col xs={12} md={6}>
                            <Card size="small">
                                <div className="text-center">
                                    <div className="fw-bold">Direct Deposit</div>
                                    <div className="fs-5">0</div>
                                </div>
                            </Card>
                        </Col>
                        <Col xs={12} md={6}>
                            <Card size="small">
                                <div className="text-center">
                                    <div className="fw-bold">Bank Transfer</div>
                                    <div className="fs-5">1</div>
                                </div>
                            </Card>
                        </Col>
                        <Col xs={12} md={6}>
                            <Card size="small">
                                <div className="text-center">
                                    <div className="fw-bold">Cheque</div>
                                    <div className="fs-5">0</div>
                                </div>
                            </Card>
                        </Col>
                        <Col xs={12} md={6}>
                            <Card size="small">
                                <div className="text-center">
                                    <div className="fw-bold">Cash</div>
                                    <div className="fs-5">0</div>
                                </div>
                            </Card>
                        </Col>
                    </Row>
                </div>

                <div className="mb-5">
                    <h5 className="fw-bold mb-3">Component Wise Breakdown</h5>
                    <Table
                        dataSource={componentBreakdown}
                        pagination={false}
                        scroll={{ x: true }}
                        size="small"
                        summary={() => (
                            <Table.Summary>
                                <Table.Summary.Row>
                                    <Table.Summary.Cell index={0} className="fw-bold">Total Earnings</Table.Summary.Cell>
                                    <Table.Summary.Cell index={1} className="fw-bold">
                                        {componentBreakdown.reduce((sum, item) => sum + item.employeesInvolved, 0)}
                                    </Table.Summary.Cell>
                                    <Table.Summary.Cell index={2} className="fw-bold">
                                        ₹{componentBreakdown.reduce((sum, item) => sum + item.totalAmount, 0).toLocaleString()}
                                    </Table.Summary.Cell>
                                </Table.Summary.Row>
                            </Table.Summary>
                        )}
                    >
                        <Column title="COMPONENTS" dataIndex="component" key="component" />
                        <Column title="EMPLOYEES INVOLVED" dataIndex="employeesInvolved" key="employeesInvolved" />
                        <Column
                            title="TOTAL AMOUNT"
                            dataIndex="totalAmount"
                            key="totalAmount"
                            render={(amount) => `₹${amount.toLocaleString()}`}
                        />
                    </Table>
                </div>

                <Card className="mb-4">
                    <h5 className="fw-bold mb-3">Statutory Summary</h5>
                    <div className="text-center text-muted">
                        No data to display
                    </div>
                </Card>
            </div>
        ),
    };

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Payrun Preview</title>
            </Helmet>

            {/* Header Section */}
            <div className="w-100 bg-white px-3 px-md-5 py-3 d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center border-bottom gap-3">
                <div>
                    <h5 className="mb-1 fw-semibold">
                        {payRunData.type} ({payRunData.subType})
                    </h5>
                    <div className="text-muted">Period: {payRunData.period}</div>
                </div>

                <div className="d-flex gap-2">
                    <Button type="primary" onClick={handleSubmitAndApprove} size="small">
                        Submit and Approve
                    </Button>

                    <Dropdown
                        menu={{
                            items: headerDropdownItems,
                            onClick: handleHeaderMenuClick,
                        }}
                        trigger={['click']}
                    >
                        <Button icon={<MoreOutlined />} size="small" />
                    </Dropdown>
                </div>
            </div>

            {/* Stats Overview */}
            <div className="bg-light py-4 px-3 px-md-5 border-bottom">
                <Row gutter={[16, 16]}>
                    {/* Card 1: Period, Payroll Cost and Net Pay */}
                    <Col xs={24} md={12} lg={8}>
                        <Card className="h-100 text-center" style={{ backgroundColor: '#e3f2fd', borderColor: '#bbdefb' }}>
                            <div className="text-muted fw-semibold mb-3">Period: {payRunData.period}</div>
                            <Row gutter={[16, 16]}>
                                <Col xs={12}>
                                    <div>
                                        <div className="fw-bold fs-4" style={{ color: '#1565c0' }}>
                                            {(() => {
                                                if (selectedRowKeys.length === 0) {
                                                    // No selection: Show total payroll cost for all employees
                                                    return `₹${payRunData.payrollCost.toLocaleString()}`;
                                                } else if (selectedRowKeys.length === 1 && selectedEmployee) {
                                                    // Single employee selected
                                                    return `₹${parseFloat(selectedEmployee.grossPay || 0).toLocaleString()}`;
                                                } else {
                                                    // Multiple employees selected: Sum their gross pay
                                                    const selectedEmployeesGrossPay = employees
                                                        .filter(emp => selectedRowKeys.includes(emp.key))
                                                        .reduce((sum, emp) => sum + parseFloat(emp.grossPay || 0), 0);
                                                    return `₹${selectedEmployeesGrossPay.toLocaleString()}`;
                                                }
                                            })()}
                                        </div>
                                        <div className="text-muted small">PAYROLL COST</div>
                                    </div>
                                </Col>
                                <Col xs={12}>
                                    <div>
                                        <div className="fw-bold fs-4" style={{ color: '#1565c0' }}>
                                            {(() => {
                                                if (selectedRowKeys.length === 0) {
                                                    // No selection: Show total net pay for all employees
                                                    return `₹${payRunData.netPay.toLocaleString()}`;
                                                } else if (selectedRowKeys.length === 1 && selectedEmployee) {
                                                    // Single employee selected
                                                    return `₹${parseFloat(selectedEmployee.netPay || 0).toLocaleString()}`;
                                                } else {
                                                    // Multiple employees selected: Sum their net pay
                                                    const selectedEmployeesNetPay = employees
                                                        .filter(emp => selectedRowKeys.includes(emp.key))
                                                        .reduce((sum, emp) => sum + parseFloat(emp.netPay || 0), 0);
                                                    return `₹${selectedEmployeesNetPay.toLocaleString()}`;
                                                }
                                            })()}
                                        </div>
                                        <div className="text-muted small">TOTAL NET PAY</div>
                                    </div>
                                </Col>
                            </Row>

                            {selectedRowKeys.length > 0 && (
                                <div className="mt-3 pt-2 border-top">
                                    <div className="text-muted small">
                                        {selectedRowKeys.length === 1 ? 'Selected Employee' : 'Selected Employees'}
                                    </div>
                                    <div className="fw-semibold">
                                        {selectedRowKeys.length === 1 && selectedEmployee ?
                                            selectedEmployee.name :
                                            `${selectedRowKeys.length} employees selected`
                                        }
                                    </div>
                                    {selectedRowKeys.length === 1 && selectedEmployee && (
                                        <div className="text-muted small">{selectedEmployee.employeeNumber || selectedEmployee.employeeId}</div>
                                    )}
                                </div>
                            )}
                        </Card>
                    </Col>
                    {/* Card 2: Pay Day */}
                    <Col xs={24} md={12} lg={4}>
                        <Card className="h-100 text-center" style={{ backgroundColor: '#fff3e0', borderColor: '#ffe0b2' }}>
                            <div className="text-muted small mb-2">PAY DAY</div>

                            {(() => {
                                const raw = payRunData?.paymentDate || payRunData?.payDate;
                                if (!raw) return (
                                    <>
                                        <div className="fw-bold fs-3" style={{ color: '#e65100' }}>--</div>
                                        <div className="text-muted small">--, ----</div>
                                    </>
                                );

                                const d = new Date(raw);
                                const day = d.getDate();
                                const month = d.toLocaleString('en-US', { month: 'short' }).toUpperCase();
                                const year = d.getFullYear();

                                return (
                                    <>
                                        <div className="fw-bold fs-3" style={{ color: '#e65100' }}>{day}</div>
                                        <div className="text-muted small">{month}, {year}</div>
                                    </>
                                );
                            })()}

                            <div className="text-muted small mt-2">
                                {selectedRowKeys.length === 0 ?
                                    `${payRunData.employeeCount} Employees` :
                                    selectedRowKeys.length === 1 ?
                                        '1 Employee' :
                                        `${selectedRowKeys.length} Employees`
                                }
                            </div>

                            {selectedRowKeys.length > 0 && (
                                <div className="mt-3 pt-2 border-top">
                                    <div className="text-muted small">
                                        {selectedRowKeys.length === 1 ? 'Employee ID' : 'Selected Employees'}
                                    </div>
                                    <div className="fw-semibold">
                                        {selectedRowKeys.length === 1 && selectedEmployee ?
                                            selectedEmployee.employeeNumber :
                                            `${selectedRowKeys.length} selected`
                                        }
                                    </div>
                                </div>
                            )}
                        </Card>
                    </Col>

                    {/* Card 3: Taxes & Deductions - Vertical Layout */}
                    <Col xs={24} md={24} lg={12}>
                        <Card className="h-100" style={{ backgroundColor: '#e8f5e9', borderColor: '#c8e6c9' }}>
                            <div className="text-center text-muted small mb-3 fw-semibold" style={{ color: '#2e7d32' }}>Taxes & Deductions</div>
                            <div className="d-flex flex-column gap-2">
                                <div className="d-flex justify-content-between align-items-center">
                                    <span className="text-muted">Taxes</span>
                                    <span className="fw-semibold" style={{ color: '#2e7d32' }}>
                                        {(() => {
                                            if (selectedRowKeys.length === 0) {
                                                // No selection: Show total taxes for all employees
                                                const totalTaxes = employees.reduce((sum, emp) => sum + parseFloat(emp.taxes || 0), 0);
                                                return `₹${totalTaxes.toLocaleString()}`;
                                            } else if (selectedRowKeys.length === 1 && selectedEmployee) {
                                                // Single employee selected
                                                return `₹${parseFloat(selectedEmployee.taxes || 0).toLocaleString()}`;
                                            } else {
                                                // Multiple employees selected: Sum their taxes
                                                const selectedEmployeesTaxes = employees
                                                    .filter(emp => selectedRowKeys.includes(emp.key))
                                                    .reduce((sum, emp) => sum + parseFloat(emp.taxes || 0), 0);
                                                return `₹${selectedEmployeesTaxes.toLocaleString()}`;
                                            }
                                        })()}
                                    </span>
                                </div>
                                <div className="d-flex justify-content-between align-items-center">
                                    <span className="text-muted">Benefits</span>
                                    <span className="fw-semibold" style={{ color: '#2e7d32' }}>
                                        {(() => {
                                            if (selectedRowKeys.length === 0) {
                                                // No selection: Show total benefits for all employees
                                                const totalBenefits = employees.reduce((sum, emp) => sum + parseFloat(emp.benefits || 0), 0);
                                                return `₹${totalBenefits.toLocaleString()}`;
                                            } else if (selectedRowKeys.length === 1 && selectedEmployee) {
                                                // Single employee selected
                                                return `₹${parseFloat(selectedEmployee.benefits || 0).toLocaleString()}`;
                                            } else {
                                                // Multiple employees selected: Sum their benefits
                                                const selectedEmployeesBenefits = employees
                                                    .filter(emp => selectedRowKeys.includes(emp.key))
                                                    .reduce((sum, emp) => sum + parseFloat(emp.benefits || 0), 0);
                                                return `₹${selectedEmployeesBenefits.toLocaleString()}`;
                                            }
                                        })()}
                                    </span>
                                </div>
                                <div className="d-flex justify-content-between align-items-center">
                                    <span className="text-muted">Donations</span>
                                    <span className="fw-semibold" style={{ color: '#2e7d32' }}>₹0.00</span>
                                </div>
                                <div className="d-flex justify-content-between align-items-center border-top pt-2 mt-1">
                                    <span className="fw-semibold" style={{ color: '#2e7d32' }}>Total Deductions</span>
                                    <span className="fw-semibold" style={{ color: '#2e7d32' }}>
                                        {(() => {
                                            if (selectedRowKeys.length === 0) {
                                                // No selection: Show total deductions for all employees
                                                // Total Deductions = Sum of (benefits + taxes) for all employees
                                                const totalDeductions = employees.reduce((sum, emp) =>
                                                    sum + parseFloat(emp.benefits || 0) + parseFloat(emp.taxes || 0), 0);
                                                return `₹${totalDeductions.toLocaleString()}`;
                                            } else if (selectedRowKeys.length === 1 && selectedEmployee) {
                                                // Single employee selected
                                                const employeeDeductions =
                                                    parseFloat(selectedEmployee.benefits || 0) +
                                                    parseFloat(selectedEmployee.taxes || 0);
                                                return `₹${employeeDeductions.toLocaleString()}`;
                                            } else {
                                                // Multiple employees selected: Sum their deductions
                                                const selectedEmployeesDeductions = employees
                                                    .filter(emp => selectedRowKeys.includes(emp.key))
                                                    .reduce((sum, emp) =>
                                                        sum + parseFloat(emp.benefits || 0) + parseFloat(emp.taxes || 0), 0);
                                                return `₹${selectedEmployeesDeductions.toLocaleString()}`;
                                            }
                                        })()}
                                    </span>
                                </div>
                            </div>
                            {selectedRowKeys.length > 0 && (
                                <div className="mt-3 pt-2 border-top">
                                    <div className="text-muted small">
                                        {selectedRowKeys.length === 1 ? 'Selected Employee' : 'Selected Employees'}
                                    </div>
                                    <div className="fw-semibold">
                                        {selectedRowKeys.length === 1 && selectedEmployee ?
                                            `${selectedEmployee.name}` :
                                            `${selectedRowKeys.length} employees selected`
                                        }
                                    </div>
                                    {selectedRowKeys.length === 1 && selectedEmployee && (
                                        <>
                                            <div className="text-muted small mt-1">Employee ID: {selectedEmployee.employeeNumber}</div>
                                            <div className="mt-2">
                                                <Tag color={selectedEmployee.status === 'Active' ? 'green' : 'default'} size="small">
                                                    {selectedEmployee.status}
                                                </Tag>
                                            </div>
                                        </>
                                    )}
                                </div>
                            )}
                        </Card>
                    </Col>
                </Row>
            </div>

            {/* Main Content */}
            <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
                <div className="d-flex flex-column flex-lg-row-fluid">
                    <div
                        className="container-fluid p-3 p-md-10 bg-white"
                        style={{ minHeight: '100vh', overflowY: 'auto' }}
                    >
                        <Card
                            tabList={tabList}
                            activeTabKey={activeTab}
                            onTabChange={setActiveTab}
                        >
                            {contentList[activeTab]}
                        </Card>
                    </div>
                </div>
            </div>

            {/* Remove Employee Confirmation Modal */}
            <Modal
                title="Remove Employee"
                open={isDeleteModalVisible}
                onOk={confirmRemoveEmployee}
                onCancel={() => setIsDeleteModalVisible(false)}
                okText="Yes"
                cancelText="No"
                okType="danger"
            >
                {selectedEmployee && (
                    <p>
                        You are about to remove {selectedEmployee.name} from {payRunData.type}.
                        Are you sure you want to proceed?
                    </p>
                )}
            </Modal>

            {/* Add Employee Modal */}
            {showAddEmployeeForm && (
                <div
                    className="modal fade show d-block"
                    tabIndex="-1"
                    style={{ backgroundColor: "rgba(0, 0, 0, 0.5)", zIndex: 1050 }}
                >
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h2 className="modal-title">Add Employee</h2>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light"
                                    onClick={() => setShowAddEmployeeForm(false)}
                                >
                                    <i className="bi bi-x fs-2"></i>
                                </button>
                            </div>

                            <Formik
                                initialValues={{ employee: "", amount: "" }}
                                validationSchema={Yup.object({
                                    employee: Yup.string().required("Employee is required"),
                                    amount: Yup.number()
                                        .typeError("Amount must be a number")
                                        .positive("Amount must be positive")
                                        .required("Amount is required"),
                                })}
                                onSubmit={handleAddEmployeeSubmit}
                            >
                                {({ isSubmitting, setFieldValue, values }) => (
                                    <Form>
                                        <div className="modal-body">
                                            <div className="mb-3">
                                                <label className="form-label">
                                                    Employee <RequiredStar />
                                                </label>
                                                <Select
                                                    placeholder="Select Employee"
                                                    style={{ width: "100%" }}
                                                    onChange={(value) => setFieldValue("employee", value)}
                                                    value={values.employee || undefined}
                                                    showSearch
                                                    filterOption={(input, option) =>
                                                        (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                                    }
                                                >
                                                    {employeeOptions.map((emp) => (
                                                        <Select.Option
                                                            key={emp.basicDetails.employeeId}
                                                            value={emp.basicDetails.employeeId}
                                                            label={`${emp.basicDetails.firstName} ${emp.basicDetails.lastName}`}
                                                        >
                                                            {emp.basicDetails.firstName} {emp.basicDetails.lastName} - {emp.basicDetails.employeeId}
                                                        </Select.Option>
                                                    ))}
                                                </Select>
                                                <ErrorMessage
                                                    name="employee"
                                                    component="div"
                                                    className="text-danger small mt-1"
                                                />
                                            </div>

                                            <div className="mb-3">
                                                <label className="form-label">
                                                    Amount <RequiredStar />
                                                </label>
                                                <Field
                                                    type="number"
                                                    name="amount"
                                                    className="form-control"
                                                    placeholder="Enter amount"
                                                />
                                                <ErrorMessage
                                                    name="amount"
                                                    component="div"
                                                    className="text-danger small mt-1"
                                                />
                                            </div>
                                        </div>

                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => setShowAddEmployeeForm(false)}
                                                disabled={isSubmitting}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="submit"
                                                className="btn btn-primary"
                                                disabled={isSubmitting}
                                            >
                                                {isSubmitting ? "Adding..." : "Add Employee"}
                                            </button>
                                        </div>
                                    </Form>
                                )}
                            </Formik>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}