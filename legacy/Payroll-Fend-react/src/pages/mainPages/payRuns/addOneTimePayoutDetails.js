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

export default function AddOneTimePayoutDetails() {
    const navigate = useNavigate();
    const [employees, setEmployees] = useState([]);
    const [showAddEmployeeForm, setShowAddEmployeeForm] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [searchText, setSearchText] = useState('');
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
            // Find the selected employee from options
            const selectedEmployee = employeeOptions.find(emp =>
                emp.basicDetails.employeeId === values.employee
            );

            if (!selectedEmployee) {
                errorMsg("Error", "Selected employee not found", false);
                setSubmitting(false);
                return;
            }

            // Add the new employee to the list
            const newEmployee = {
                key: Date.now(),
                name: `${selectedEmployee.basicDetails.firstName} ${selectedEmployee.basicDetails.lastName}`,
                employeeId: selectedEmployee.basicDetails.employeeId,
                grossPay: values.amount,
                deductions: (values.amount * 0.1).toFixed(2), // 10% deduction for demo
                netPay: (values.amount * 0.9).toFixed(2), // 90% net pay for demo
            };

            setEmployees([...employees, newEmployee]);
            setSubmitting(false);
            resetForm();
            setShowAddEmployeeForm(false);
        } catch (error) {
            errorMsg("Error", "Failed to add employee. Please try again.", false);
            setSubmitting(false);
        }
    };

    const handleDeleteEmployee = (key) => {
        setEmployees(employees.filter(emp => emp.key !== key));
    };

    const filteredEmployees = employees.filter(employee =>
        employee.name.toLowerCase().includes(searchText.toLowerCase()) ||
        employee.employeeId.toLowerCase().includes(searchText.toLowerCase())
    );

    const hasEmployees = employees.length > 0;

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Add One Time Payout Details</title>
            </Helmet>

            <div className="w-100 bg-white px-3 px-md-5 py-3 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Add One Time Payout Details</h5>
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
                                        .required("Amount is required")
                                        .min(1, "Amount must be greater than 0"),
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
                                                    onChange={(value) => setFieldValue("employee", value)}
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

                                            {/* Amount Input */}
                                            <div className="mb-4">
                                                <label className="form-label fs-6 fw-bold text-dark d-block mb-3">
                                                    Amount <RequiredStar />
                                                </label>
                                                <Field
                                                    type="number"
                                                    name="amount"
                                                    placeholder="Enter Amount"
                                                    className={`form-control form-control-solid ${errors.amount && touched.amount ? "is-invalid" : ""
                                                        }`}
                                                />
                                                <ErrorMessage
                                                    name="amount"
                                                    component="div"
                                                    className="invalid-feedback"
                                                />
                                            </div>
                                        </div>

                                        <div className="modal-footer">
                                            <button
                                                type="button"
                                                className="btn btn-light"
                                                onClick={() => setShowAddEmployeeForm(false)}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="submit"
                                                className="btn btn-primary"
                                                disabled={isSubmitting}
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