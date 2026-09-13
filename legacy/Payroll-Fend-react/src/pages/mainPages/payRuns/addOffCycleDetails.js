import { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Card, Dropdown, Space, Table, Tag, Input } from 'antd';
import { DownOutlined, SearchOutlined, MoreOutlined } from '@ant-design/icons';
import { useNavigate } from "react-router-dom";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg } from "../../../shared/helpers/msgHelper";
import { Select } from "antd";
import "antd/dist/reset.css";

import addOneTimePayout from '../../../assets/images/addOneTimePayout.png';
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import _ from "lodash";
import axios from "axios";
import employeeData from "../../../shared/appConfig/employeeData";

const { Option } = Select;
const { Column } = Table;

export default function AddOffCycleDetails() {
    const navigate = useNavigate();
    const [employees, setEmployees] = useState([]);
    const [showAddEmployeeForm, setShowAddEmployeeForm] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [searchText, setSearchText] = useState('');
    const [selectedEmployee, setSelectedEmployee] = useState(null);
    const [lopAdjustments, setLopAdjustments] = useState([]);
    const [earnings, setEarnings] = useState([]);
    const [showPastLopSection, setShowPastLopSection] = useState(false);
    const RequiredStar = () => <span className="text-danger">*</span>;

    // Header dropdown items
    const headerDropdownItems = [
        {
            label: 'Import Employees',
            key: 'import-employees',
        },
        {
            type: 'divider',
        },
        {
            label: 'Delete Payrun',
            key: 'delete-payrun',
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

    // Past LOP Adjustment options
    const pastLopOptions = [
        { value: 'jan-2023', label: 'January 2023' },
        { value: 'feb-2023', label: 'February 2023' },
        { value: 'mar-2023', label: 'March 2023' },
        { value: 'apr-2023', label: 'April 2023' },
    ];

    // Reversal options
    const reversalOptions = [
        { value: 'reversal', label: 'Reversal' },
        { value: 'correction', label: 'Correction' },
    ];

    // Earnings options
    const earningsOptions = [
        { value: 'leave_encashment', label: 'Leave Encashment' },
        { value: 'bonus', label: 'Bonus' },
        { value: 'overtime', label: 'Overtime' },
        { value: 'allowance', label: 'Allowance' },
    ];

    useEffect(() => {
        // Simulate API call to fetch employee data
        const fetchEmployeeData = () => {
            setIsLoading(true);
            setTimeout(() => {
                // For demo purposes, we'll use empty array to show the "no data" state
                // Replace with actual employee data when available
                setEmployees([]);
                setIsLoading(false);
            }, 1000);
        };

        fetchEmployeeData();
    }, []);

    const handleHeaderMenuClick = (e) => {
        if (e.key === 'import-employees') {
            navigate("/addOneTimePayoutDetails/import");
        } else if (e.key === 'delete-payrun') {
            // Show delete confirmation modal
            if (window.confirm("Are you sure you want to delete this payrun?")) {
                console.log("Payrun deleted");
                // Add actual delete logic here
            }
        }
    };

    const handleAddEmployeeSubmit = async (values, { setSubmitting, resetForm }) => {
        setSubmitting(true);
        try {
            // Calculate total earnings amount
            const totalEarnings = earnings.reduce((sum, earning) => sum + parseFloat(earning.amount || 0), 0);
            
            // Calculate total deductions from LOP adjustments
            const totalDeductions = lopAdjustments.reduce((sum, adjustment) => sum + parseFloat(adjustment.days || 0), 0);
            
            // For demo purposes, let's assume a fixed daily rate
            const dailyRate = 2000;
            const deductionAmount = totalDeductions * dailyRate;

            // Add the new employee to the list
            const newEmployee = {
                key: Date.now(),
                name: `${selectedEmployee.basicDetails.firstName} ${selectedEmployee.basicDetails.lastName}`,
                employeeId: selectedEmployee.basicDetails.employeeId,
                grossPay: totalEarnings,
                deductions: deductionAmount.toFixed(2),
                netPay: (totalEarnings - deductionAmount).toFixed(2),
            };

            setEmployees([...employees, newEmployee]);
            setSubmitting(false);
            resetForm();
            setShowAddEmployeeForm(false);
            setSelectedEmployee(null);
            setLopAdjustments([]);
            setEarnings([]);
            setShowPastLopSection(false);
        } catch (error) {
            errorMsg("Error", "Failed to add employee. Please try again.", false);
            setSubmitting(false);
        }
    };

    const handleDeleteEmployee = (key) => {
        setEmployees(employees.filter(emp => emp.key !== key));
    };

    const handleEmployeeSelect = (value) => {
        const employee = employeeOptions.find(emp => emp.basicDetails.employeeId === value);
        setSelectedEmployee(employee);
    };

    const handleAddLopAdjustment = () => {
        setLopAdjustments([...lopAdjustments, { id: Date.now(), select: '', reversal: '', days: '' }]);
    };

    const handleRemoveLopAdjustment = (id) => {
        setLopAdjustments(lopAdjustments.filter(item => item.id !== id));
    };

    const handleLopAdjustmentChange = (id, field, value) => {
        setLopAdjustments(lopAdjustments.map(item => 
            item.id === id ? { ...item, [field]: value } : item
        ));
    };

    const handleAddEarning = () => {
        setEarnings([...earnings, { id: Date.now(), type: '', amount: '' }]);
    };

    const handleRemoveEarning = (id) => {
        setEarnings(earnings.filter(item => item.id !== id));
    };

    const handleEarningChange = (id, field, value) => {
        setEarnings(earnings.map(item => 
            item.id === id ? { ...item, [field]: value } : item
        ));
    };

    const filteredEmployees = employees.filter(employee =>
        employee.name.toLowerCase().includes(searchText.toLowerCase()) ||
        employee.employeeId.toLowerCase().includes(searchText.toLowerCase())
    );

    const hasEmployees = employees.length > 0;

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Add Off Cycle Details</title>
            </Helmet>

            <div className="w-100 bg-white px-3 px-md-5 py-3 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Add Off Cycle Details</h5>
                <Dropdown
                    menu={{
                        items: headerDropdownItems,
                        onClick: handleHeaderMenuClick,
                        style: { minWidth: '200px' }
                    }}
                    trigger={['click']}
                    placement="bottomRight"
                >
                    <a onClick={(e) => e.preventDefault()} style={{ display: 'inline-block' }}>
                        <Space className="btn btn-light d-flex align-items-center justify-content-center p-2">
                            <MoreOutlined style={{ fontSize: '20px' }} />
                        </Space>
                    </a>
                </Dropdown>
            </div>

            <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
                <div className="d-flex flex-column flex-lg-row-fluid">
                    <div
                        className="container-fluid p-3 p-md-10 bg-white"
                        style={{ minHeight: '100vh', overflowY: 'auto' }}
                    >
                        {isLoading ? (
                            <div className="text-center py-5">
                                <div className="spinner-border text-primary" role="status">
                                    <span className="visually-hidden">Loading...</span>
                                </div>
                            </div>
                        ) : hasEmployees ? (
                            <>
                                <div className="d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center mb-4 gap-2">
                                    <div className="d-flex align-items-center">
                                        <span className="me-2 fw-semibold d-none d-md-block">FILTER BY:</span>
                                        <Input
                                            placeholder="Search Employee"
                                            prefix={<SearchOutlined />}
                                            value={searchText}
                                            onChange={(e) => setSearchText(e.target.value)}
                                            className="me-2 border-0"
                                            style={{ 
                                                fontSize: '16px',
                                                width: '250px'
                                            }}
                                            bordered={false}
                                        />
                                    </div>
                                    <div className="d-flex gap-2">
                                        <button
                                            className="btn btn-primary"
                                            onClick={() => setShowAddEmployeeForm(true)}
                                        >
                                            <i className="bi bi-plus fs-6 me-1 me-md-2"></i>
                                            <span className="d-none d-md-inline">Add Employee</span>
                                            <span className="d-md-none">Add</span>
                                        </button>
                                        <button
                                            className="btn btn-light"
                                            onClick={() => navigate("/preview")}
                                        >
                                            <i className="bi bi-upload fs-6 me-1 me-md-2"></i>
                                            <span className="d-none d-md-inline">Continue to payroll</span>
                                            <span className="d-md-none">Continue to Payroll</span>
                                        </button>
                                    </div>
                                </div>

                                <div className="table-responsive">
                                    <Table
                                        dataSource={filteredEmployees}
                                        pagination={false}
                                        className="mt-4"
                                        scroll={{ x: true }}
                                    >
                                        <Column
                                            title="EMPLOYEE NAME"
                                            dataIndex="name"
                                            key="name"
                                            fixed="left"
                                            width={150}
                                        />
                                        <Column
                                            title="GROSS PAY"
                                            dataIndex="grossPay"
                                            key="grossPay"
                                            render={(grossPay) => `₹${parseFloat(grossPay).toLocaleString()}`}
                                            width={120}
                                        />
                                        <Column
                                            title="DEDUCTIONS"
                                            dataIndex="deductions"
                                            key="deductions"
                                            render={(deductions) => `₹${parseFloat(deductions).toLocaleString()}`}
                                            width={120}
                                        />
                                        <Column
                                            title="NET PAY"
                                            dataIndex="netPay"
                                            key="netPay"
                                            render={(netPay) => `₹${parseFloat(netPay).toLocaleString()}`}
                                            width={120}
                                        />
                                       
                                    </Table>
                                </div>
                            </>
                        ) : (
                            <Card className="text-center border-0 shadow-none">
                                <div className="mb-5 mb-md-7">
                                    <img
                                        src={addOneTimePayout}
                                        alt="No OneTimePayout"
                                        className="mw-100 h-150px h-sm-250px h-md-325px"
                                    />
                                </div>

                                <div className="mb-5 mb-md-10">
                                    <h3 className="fw-bold text-gray-900 mb-2">Add Employee details</h3>
                                    <div className="text-muted fw-semibold fs-6 fs-md-5">
                                        Click Add employees to add eligible employees to the payrun.
                                    </div>
                                </div>

                                <div className="d-flex flex-column flex-sm-row justify-content-center gap-3 gap-sm-5">
                                    <button
                                        className="btn btn-primary"
                                        onClick={() => setShowAddEmployeeForm(true)}
                                    >
                                        <i className="bi bi-plus fs-6 me-1 me-md-2"></i> Add Employee
                                    </button>
                                    <button
                                        className="btn btn-light"
                                        onClick={() => navigate("/addOneTimePayoutDetails/import")}
                                    >
                                        <i className="bi bi-upload fs-6 me-1 me-md-2"></i> Import Employees
                                    </button>
                                </div>
                            </Card>
                        )}
                    </div>
                </div>
            </div>

            {/* Add Employee Modal */}
            {showAddEmployeeForm && (
                <div
                    className="modal fade show d-block"
                    tabIndex="-1"
                    style={{ backgroundColor: "rgba(0, 0, 0, 0.5)", zIndex: 1050 }}
                >
                    <div className="modal-dialog modal-dialog-centered modal-lg">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h2 className="modal-title">Add Employee</h2>
                                <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light"
                                    onClick={() => {
                                        setShowAddEmployeeForm(false);
                                        setSelectedEmployee(null);
                                        setLopAdjustments([]);
                                        setEarnings([]);
                                        setShowPastLopSection(false);
                                    }}
                                >
                                    <i className="bi bi-x fs-2"></i>
                                </button>
                            </div>

                            <Formik
                                initialValues={{ employee: "" }}
                                validationSchema={Yup.object({
                                    employee: Yup.string().required("Employee is required"),
                                })}
                                onSubmit={handleAddEmployeeSubmit}
                            >
                                {({ values, setFieldValue, isSubmitting, errors, touched }) => (
                                    <Form>
                                        <div className="modal-body">
                                            {/* Employee Dropdown with Search */}
                                            <div className="mb-4">
                                                <label className="form-label fs-6 fw-bold text-dark d-block mb-3">
                                                    Employee <RequiredStar />
                                                </label>
                                                <Select
                                                    showSearch
                                                    placeholder="Search Employee"
                                                    optionFilterProp="label"
                                                    value={values.employee || undefined}
                                                    onChange={(value) => {
                                                        setFieldValue("employee", value);
                                                        handleEmployeeSelect(value);
                                                    }}
                                                    filterOption={(input, option) =>
                                                        (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                                    }
                                                    className="w-100"
                                                    dropdownStyle={{ zIndex: 1060 }}
                                                >
                                                    {employeeOptions.map((emp) => {
                                                        const label = `${emp.basicDetails.firstName} ${emp.basicDetails.lastName} (${emp.basicDetails.employeeId}) - ${emp.basicDetails.departmentName}`;
                                                        return (
                                                            <Select.Option
                                                                key={emp.basicDetails.employeeId}
                                                                value={emp.basicDetails.employeeId}
                                                                label={label}
                                                            >
                                                                {label}
                                                            </Select.Option>
                                                        );
                                                    })}
                                                </Select>
                                                <ErrorMessage
                                                    name="employee"
                                                    component="div"
                                                    className="text-danger mt-1"
                                                />
                                            </div>

                                            {/* Past LOP Adjustment Toggle */}
                                            {selectedEmployee && (
                                                <div className="mb-4">
                                                    <div className="form-check form-switch">
                                                        <input
                                                            className="form-check-input"
                                                            type="checkbox"
                                                            id="pastLopToggle"
                                                            checked={showPastLopSection}
                                                            onChange={() => setShowPastLopSection(!showPastLopSection)}
                                                        />
                                                        <label className="form-check-label fw-bold" htmlFor="pastLopToggle">
                                                            + Adjust Past LOP
                                                        </label>
                                                    </div>
                                                </div>
                                            )}

                                            {/* Past Month LOP Adjustments */}
                                            {showPastLopSection && selectedEmployee && (
                                                <div className="mb-4 border rounded p-3">
                                                    <h5 className="mb-3">Past Month LOP Adjustments ({lopAdjustments.length})</h5>
                                                    
                                                    {lopAdjustments.length > 0 && (
                                                        <div className="row mb-2 fw-semibold small">
                                                            <div className="col-md-4">Select</div>
                                                            <div className="col-md-3">Reversal</div>
                                                            <div className="col-md-3">No. of Days</div>
                                                            <div className="col-md-2">Action</div>
                                                        </div>
                                                    )}
                                                    
                                                    {lopAdjustments.map((adjustment) => (
                                                        <div key={adjustment.id} className="row mb-3 align-items-center">
                                                            <div className="col-md-4 mb-2 mb-md-0">
                                                                <Select
                                                                    showSearch
                                                                    placeholder="Select"
                                                                    value={adjustment.select || undefined}
                                                                    onChange={(value) => handleLopAdjustmentChange(adjustment.id, 'select', value)}
                                                                    filterOption={(input, option) =>
                                                                        (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                                                    }
                                                                    className="w-100"
                                                                    dropdownStyle={{ zIndex: 1060 }}
                                                                >
                                                                    {pastLopOptions.map(option => (
                                                                        <Select.Option key={option.value} value={option.value} label={option.label}>
                                                                            {option.label}
                                                                        </Select.Option>
                                                                    ))}
                                                                </Select>
                                                            </div>
                                                            <div className="col-md-3 mb-2 mb-md-0">
                                                                <Select
                                                                    showSearch
                                                                    placeholder="Reversal"
                                                                    value={adjustment.reversal || undefined}
                                                                    onChange={(value) => handleLopAdjustmentChange(adjustment.id, 'reversal', value)}
                                                                    filterOption={(input, option) =>
                                                                        (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                                                    }
                                                                    className="w-100"
                                                                    dropdownStyle={{ zIndex: 1060 }}
                                                                >
                                                                    {reversalOptions.map(option => (
                                                                        <Select.Option key={option.value} value={option.value} label={option.label}>
                                                                            {option.label}
                                                                        </Select.Option>
                                                                    ))}
                                                                </Select>
                                                            </div>
                                                            <div className="col-md-3 mb-2 mb-md-0">
                                                                <Input
                                                                    type="number"
                                                                    placeholder="No. of Days"
                                                                    value={adjustment.days}
                                                                    onChange={(e) => handleLopAdjustmentChange(adjustment.id, 'days', e.target.value)}
                                                                />
                                                            </div>
                                                            <div className="col-md-2 text-end">
                                                                <Dropdown
                                                                    menu={{
                                                                        items: [
                                                                            {
                                                                                key: 'remove',
                                                                                label: 'Remove',
                                                                                onClick: () => handleRemoveLopAdjustment(adjustment.id)
                                                                            }
                                                                        ]
                                                                    }}
                                                                    trigger={['click']}
                                                                    placement="bottomRight"
                                                                >
                                                                    <a onClick={(e) => e.preventDefault()} className="text-dark">
                                                                        <MoreOutlined style={{ fontSize: '20px' }} />
                                                                    </a>
                                                                </Dropdown>
                                                            </div>
                                                        </div>
                                                    ))}
                                                    
                                                    <button
                                                        type="button"
                                                        className="btn btn-light btn-sm"
                                                        onClick={handleAddLopAdjustment}
                                                    >
                                                        + Add LOP Adjustment
                                                    </button>
                                                </div>
                                            )}

                                            {/* Earnings Section */}
                                            {selectedEmployee && (
                                                <div className="mb-4">
                                                    <h5 className="mb-3">(+) EARNINGS</h5>
                                                    
                                                    {earnings.length > 0 && (
                                                        <div className="row mb-2 fw-semibold small">
                                                            <div className="col-md-8">EARNING TYPE</div>
                                                            <div className="col-md-3">AMOUNT</div>
                                                            <div className="col-md-1">ACTION</div>
                                                        </div>
                                                    )}
                                                    
                                                    {earnings.length === 0 ? (
                                                        <div className="text-muted mb-3">
                                                            You haven't added any earnings yet.
                                                        </div>
                                                    ) : (
                                                        earnings.map((earning) => (
                                                            <div key={earning.id} className="row mb-3 align-items-center">
                                                                <div className="col-md-8 mb-2 mb-md-0">
                                                                    <Select
                                                                        showSearch
                                                                        placeholder="Search Earning Type"
                                                                        value={earning.type || undefined}
                                                                        onChange={(value) => handleEarningChange(earning.id, 'type', value)}
                                                                        filterOption={(input, option) =>
                                                                            (option?.label ?? "").toLowerCase().includes(input.toLowerCase())
                                                                        }
                                                                        className="w-100"
                                                                        dropdownStyle={{ zIndex: 1060 }}
                                                                    >
                                                                        {earningsOptions.map(option => (
                                                                            <Select.Option key={option.value} value={option.value} label={option.label}>
                                                                                {option.label}
                                                                            </Select.Option>
                                                                        ))}
                                                                    </Select>
                                                                </div>
                                                                <div className="col-md-3 mb-2 mb-md-0">
                                                                    <Input
                                                                        type="number"
                                                                        placeholder="Amount"
                                                                        value={earning.amount}
                                                                        onChange={(e) => handleEarningChange(earning.id, 'amount', e.target.value)}
                                                                        prefix="₹"
                                                                    />
                                                                </div>
                                                                <div className="col-md-1 text-end">
                                                                    <Dropdown
                                                                        menu={{
                                                                            items: [
                                                                                {
                                                                                    key: 'remove',
                                                                                    label: 'Remove',
                                                                                    onClick: () => handleRemoveEarning(earning.id)
                                                                                }
                                                                            ]
                                                                        }}
                                                                        trigger={['click']}
                                                                        placement="bottomRight"
                                                                    >
                                                                        <a onClick={(e) => e.preventDefault()} className="text-dark">
                                                                            <MoreOutlined style={{ fontSize: '20px' }} />
                                                                        </a>
                                                                    </Dropdown>
                                                                </div>
                                                            </div>
                                                        ))
                                                    )}
                                                    
                                                    <button
                                                        type="button"
                                                        className="btn btn-light"
                                                        onClick={handleAddEarning}
                                                    >
                                                        + Add Earning
                                                    </button>
                                                </div>
                                            )}
                                        </div>

                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => {
                                                    setShowAddEmployeeForm(false);
                                                    setSelectedEmployee(null);
                                                    setLopAdjustments([]);
                                                    setEarnings([]);
                                                    setShowPastLopSection(false);
                                                }}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="submit"
                                                className="btn btn-primary"
                                                disabled={isSubmitting || !selectedEmployee}
                                            >
                                                {isSubmitting ? (
                                                    <span className="spinner-border spinner-border-sm me-1"></span>
                                                ) : (
                                                    "Save"
                                                )}
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