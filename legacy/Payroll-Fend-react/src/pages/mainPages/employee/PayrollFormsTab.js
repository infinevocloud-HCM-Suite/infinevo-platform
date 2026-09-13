import React, { useState, useEffect } from "react";
import { Card, Row, Col, Button, Typography, Table, Tag, Badge } from "antd";
import { useOutletContext } from "react-router-dom";
import { EditOutlined, DownloadOutlined, EyeOutlined, UploadOutlined } from "@ant-design/icons";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { errorMsg } from "../../../shared/helpers/msgHelper";

const { Title } = Typography;

export default function PayrollFormsTab() {
    const { 
        formatDate,
        organizationId,
        id 
    } = useOutletContext();
    
    const [loading, setLoading] = useState(true);
    const [payrollData, setPayrollData] = useState([]);
    const [formsData, setFormsData] = useState([]);
    const [payslips, setPayslips] = useState([]);

    // Fetch payroll and forms data
    const fetchPayrollData = async () => {
        try {
            setLoading(true);
            // Fetch payroll history
            const payrollResponse = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/payroll-history`, {
                params: { employeeId: id },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            // Fetch forms data
            const formsResponse = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/employee-forms`, {
                params: { employeeId: id },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            // Fetch payslips
            const payslipsResponse = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/payslips`, {
                params: { employeeId: id },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (payrollResponse.data && payrollResponse.data.data) {
                setPayrollData(payrollResponse.data.data);
            }

            if (formsResponse.data && formsResponse.data.data) {
                setFormsData(formsResponse.data.data);
            }

            if (payslipsResponse.data && payslipsResponse.data.data) {
                setPayslips(payslipsResponse.data.data);
            }
        } catch (error) {
            console.error("Error fetching payroll data:", error);
            errorMsg("Error", "Failed to load payroll and forms data", true);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPayrollData();
    }, [id]);

    const payrollColumns = [
        {
            title: 'Month',
            dataIndex: 'month',
            key: 'month',
            render: (text) => <span className="fw-semibold">{text}</span>
        },
        {
            title: 'Year',
            dataIndex: 'year',
            key: 'year'
        },
        {
            title: 'Gross Salary',
            dataIndex: 'grossSalary',
            key: 'grossSalary',
            render: (amount) => <span className="fw-bold text-gray-800">₹{amount?.toLocaleString('en-IN') || '0'}</span>
        },
        {
            title: 'Net Salary',
            dataIndex: 'netSalary',
            key: 'netSalary',
            render: (amount) => <span className="fw-bold text-success">₹{amount?.toLocaleString('en-IN') || '0'}</span>
        },
        {
            title: 'Status',
            dataIndex: 'status',
            key: 'status',
            render: (status) => {
                const color = status === 'Paid' ? 'green' : 
                             status === 'Processing' ? 'blue' : 'red';
                return <Tag color={color}>{status}</Tag>;
            }
        },
        {
            title: 'Payment Date',
            dataIndex: 'paymentDate',
            key: 'paymentDate',
            render: (date) => formatDate(date)
        },
        {
            title: 'Actions',
            key: 'actions',
            render: (_, record) => (
                <div className="d-flex gap-2">
                    <Button type="link" size="small" icon={<EyeOutlined />}>
                        View
                    </Button>
                    <Button type="link" size="small" icon={<DownloadOutlined />}>
                        Download
                    </Button>
                </div>
            )
        }
    ];

    const formsColumns = [
        {
            title: 'Form Name',
            dataIndex: 'formName',
            key: 'formName',
            render: (text) => <span className="fw-semibold">{text}</span>
        },
        {
            title: 'Type',
            dataIndex: 'type',
            key: 'type',
            render: (text) => <Tag color="purple">{text}</Tag>
        },
        {
            title: 'Status',
            dataIndex: 'status',
            key: 'status',
            render: (status) => {
                const color = status === 'Submitted' ? 'green' : 
                             status === 'Pending' ? 'orange' : 'red';
                return <Tag color={color}>{status}</Tag>;
            }
        },
        {
            title: 'Submission Date',
            dataIndex: 'submissionDate',
            key: 'submissionDate',
            render: (date) => date ? formatDate(date) : '-'
        },
        {
            title: 'Due Date',
            dataIndex: 'dueDate',
            key: 'dueDate',
            render: (date) => formatDate(date)
        },
        {
            title: 'Actions',
            key: 'actions',
            render: (_, record) => (
                <div className="d-flex gap-2">
                    {record.status === 'Submitted' ? (
                        <>
                            <Button type="link" size="small" icon={<EyeOutlined />}>
                                View
                            </Button>
                            <Button type="link" size="small" icon={<DownloadOutlined />}>
                                Download
                            </Button>
                        </>
                    ) : (
                        <Button type="primary" size="small" icon={<UploadOutlined />}>
                            Upload
                        </Button>
                    )}
                </div>
            )
        }
    ];

    if (loading) {
        return <Loader />;
    }

    return (
        <div className="tab-content">
            <div className="tab-pane fade show active" id="kt_tab_pane_payroll" role="tabpanel">
                <div className="d-flex justify-content-between align-items-center mb-4">
                    <Title level={4} className="mb-0">Payroll & Forms</Title>
                    <Button type="link" icon={<EditOutlined />} size="small">
                        Manage
                    </Button>
                </div>

                {/* Stats Cards */}
                <Row gutter={[16, 16]} className="mb-5">
                    <Col xs={24} md={6}>
                        <Card className="bg-light-primary border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {payslips.length}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Total Payslips</div>
                                </div>
                                <div className="text-primary fs-1">
                                    <i className="ki-duotone ki-document">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                    <Col xs={24} md={6}>
                        <Card className="bg-light-success border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {formsData.filter(f => f.status === 'Submitted').length}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Forms Submitted</div>
                                </div>
                                <div className="text-success fs-1">
                                    <i className="ki-duotone ki-check-square">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                    <Col xs={24} md={6}>
                        <Card className="bg-light-warning border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {formsData.filter(f => f.status === 'Pending').length}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Forms Pending</div>
                                </div>
                                <div className="text-warning fs-1">
                                    <i className="ki-duotone ki-clock">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                    <Col xs={24} md={6}>
                        <Card className="bg-light-danger border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {formsData.filter(f => f.status === 'Overdue').length}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Forms Overdue</div>
                                </div>
                                <div className="text-danger fs-1">
                                    <i className="ki-duotone ki-cross-circle">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                </Row>

                {/* Payroll History */}
                <Card
                    title={
                        <div className="d-flex justify-content-between align-items-center">
                            <span className="fw-bold fs-4">Payroll History</span>
                            <span className="text-muted fw-semibold">Last 12 Months</span>
                        </div>
                    }
                    className="shadow-sm mb-4"
                >
                    {payrollData.length > 0 ? (
                        <Table
                            columns={payrollColumns}
                            dataSource={payrollData}
                            rowKey="id"
                            pagination={{ pageSize: 6 }}
                        />
                    ) : (
                        <div className="text-center py-5">
                            <div className="fs-3 text-muted mb-3">No Payroll History Found</div>
                            <p className="text-muted">Payroll data will appear here once processed.</p>
                        </div>
                    )}
                </Card>

                {/* Forms & Documents */}
                <Card
                    title={
                        <div className="d-flex justify-content-between align-items-center">
                            <span className="fw-bold fs-4">Forms & Documents</span>
                            <Badge count={formsData.filter(f => f.status === 'Pending').length} overflowCount={9}>
                                <Button type="primary" size="small">Pending Actions</Button>
                            </Badge>
                        </div>
                    }
                    className="shadow-sm"
                >
                    {formsData.length > 0 ? (
                        <Table
                            columns={formsColumns}
                            dataSource={formsData}
                            rowKey="id"
                            pagination={{ pageSize: 5 }}
                        />
                    ) : (
                        <div className="text-center py-5">
                            <div className="fs-3 text-muted mb-3">No Forms Found</div>
                            <p className="text-muted">Forms and documents will appear here when assigned.</p>
                        </div>
                    )}

                    {/* Common Forms Section */}
                    <div className="mt-5">
                        <Title level={5}>Common Forms</Title>
                        <Row gutter={[16, 16]}>
                            <Col xs={24} md={8}>
                                <Card 
                                    title="Form 16" 
                                    size="small"
                                    extra={<Tag color="green">Available</Tag>}
                                >
                                    <div className="d-flex justify-content-between align-items-center mt-3">
                                        <span className="text-muted">FY 2024-25</span>
                                        <Button type="link" size="small" icon={<DownloadOutlined />}>
                                            Download
                                        </Button>
                                    </div>
                                </Card>
                            </Col>
                            <Col xs={24} md={8}>
                                <Card 
                                    title="Investment Proofs" 
                                    size="small"
                                    extra={<Tag color="orange">Pending</Tag>}
                                >
                                    <div className="d-flex justify-content-between align-items-center mt-3">
                                        <span className="text-muted">Due: 31 Mar 2025</span>
                                        <Button type="primary" size="small" icon={<UploadOutlined />}>
                                            Upload
                                        </Button>
                                    </div>
                                </Card>
                            </Col>
                            <Col xs={24} md={8}>
                                <Card 
                                    title="Nomination Form" 
                                    size="small"
                                    extra={<Tag color="red">Overdue</Tag>}
                                >
                                    <div className="d-flex justify-content-between align-items-center mt-3">
                                        <span className="text-muted">Due: 15 Jan 2025</span>
                                        <Button type="link" size="small" icon={<EyeOutlined />}>
                                            View
                                        </Button>
                                    </div>
                                </Card>
                            </Col>
                        </Row>
                    </div>
                </Card>
            </div>
        </div>
    );
}