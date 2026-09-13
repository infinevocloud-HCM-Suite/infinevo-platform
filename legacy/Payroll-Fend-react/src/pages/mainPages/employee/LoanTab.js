import React, { useState, useEffect } from "react";
import { Card, Row, Col, Button, Typography, Table, Tag, Progress, Modal, Form, Input, InputNumber } from "antd";
import { useOutletContext } from "react-router-dom";
import { EditOutlined, PlusOutlined, EyeOutlined, DeleteOutlined } from "@ant-design/icons";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const { Title } = Typography;
const { TextArea } = Input;

export default function LoanTab() {
    const { 
        formatCurrency,
        formatDate,
        organizationId,
        id 
    } = useOutletContext();
    
    const [loading, setLoading] = useState(true);
    const [loans, setLoans] = useState([]);
    const [isModalVisible, setIsModalVisible] = useState(false);
    const [form] = Form.useForm();

    // Fetch loan data
    const fetchLoans = async () => {
        try {
            setLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/loans`, {
                params: { employeeId: id },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.data) {
                setLoans(response.data.data);
            } else {
                setLoans([]);
            }
        } catch (error) {
            console.error("Error fetching loans:", error);
            errorMsg("Error", "Failed to load loan details", true);
            setLoans([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchLoans();
    }, [id]);

    const handleAddLoan = async (values) => {
        try {
            const response = await axios.post(`${GlobalConst.API_URL}/api/employees-portal/loans`, 
                { ...values, employeeId: id },
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId
                    },
                }
            );

            if (response.data && response.data.success) {
                successMsg("Success", "Loan added successfully");
                setIsModalVisible(false);
                form.resetFields();
                fetchLoans();
            }
        } catch (error) {
            console.error("Error adding loan:", error);
            errorMsg("Error", "Failed to add loan", true);
        }
    };

    const columns = [
        {
            title: 'Loan Type',
            dataIndex: 'loanType',
            key: 'loanType',
            render: (text) => <span className="fw-semibold">{text}</span>
        },
        {
            title: 'Loan Amount',
            dataIndex: 'loanAmount',
            key: 'loanAmount',
            render: (amount) => <span className="fw-bold text-gray-800">{formatCurrency(amount)}</span>
        },
        {
            title: 'Interest Rate',
            dataIndex: 'interestRate',
            key: 'interestRate',
            render: (rate) => <span>{rate}%</span>
        },
        {
            title: 'Tenure',
            dataIndex: 'tenure',
            key: 'tenure',
            render: (tenure) => <span>{tenure} months</span>
        },
        {
            title: 'EMI',
            dataIndex: 'emiAmount',
            key: 'emiAmount',
            render: (amount) => <span className="fw-bold text-primary">{formatCurrency(amount)}</span>
        },
        {
            title: 'Status',
            dataIndex: 'status',
            key: 'status',
            render: (status) => {
                const color = status === 'Active' ? 'green' : 
                             status === 'Closed' ? 'blue' : 'red';
                return <Tag color={color}>{status}</Tag>;
            }
        },
        {
            title: 'Balance',
            key: 'balance',
            render: (_, record) => {
                const progress = ((record.loanAmount - record.paidAmount) / record.loanAmount) * 100;
                return (
                    <div>
                        <div className="fw-bold">{formatCurrency(record.loanAmount - record.paidAmount)}</div>
                        <Progress 
                            percent={Math.round(progress)} 
                            size="small" 
                            status={record.status === 'Active' ? 'active' : 'success'}
                        />
                    </div>
                );
            }
        },
        {
            title: 'Actions',
            key: 'actions',
            render: (_, record) => (
                <div className="d-flex gap-2">
                    <Button type="link" size="small" icon={<EyeOutlined />}>
                        View
                    </Button>
                    <Button type="link" size="small" icon={<EditOutlined />}>
                        Edit
                    </Button>
                    {record.status === 'Active' && (
                        <Button type="link" size="small" icon={<DeleteOutlined />} danger>
                            Close
                        </Button>
                    )}
                </div>
            )
        }
    ];

    const calculateTotalOutstanding = () => {
        return loans
            .filter(loan => loan.status === 'Active')
            .reduce((total, loan) => total + (loan.loanAmount - loan.paidAmount), 0);
    };

    const calculateTotalEMI = () => {
        return loans
            .filter(loan => loan.status === 'Active')
            .reduce((total, loan) => total + loan.emiAmount, 0);
    };

    if (loading) {
        return <Loader />;
    }

    return (
        <div className="tab-content">
            <div className="tab-pane fade show active" id="kt_tab_pane_loan" role="tabpanel">
                <div className="d-flex justify-content-between align-items-center mb-4">
                    <Title level={4} className="mb-0">Loan Details</Title>
                    <Button 
                        type="primary" 
                        icon={<PlusOutlined />}
                        onClick={() => setIsModalVisible(true)}
                    >
                        Add New Loan
                    </Button>
                </div>

                {/* Loan Summary Cards */}
                <Row gutter={[16, 16]} className="mb-5">
                    <Col xs={24} md={8}>
                        <Card className="bg-light-primary border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {formatCurrency(calculateTotalOutstanding())}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Total Outstanding</div>
                                </div>
                                <div className="text-primary fs-1">
                                    <i className="ki-duotone ki-money">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                    <Col xs={24} md={8}>
                        <Card className="bg-light-success border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {formatCurrency(calculateTotalEMI())}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Total Monthly EMI</div>
                                </div>
                                <div className="text-success fs-1">
                                    <i className="ki-duotone ki-calendar">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                    <Col xs={24} md={8}>
                        <Card className="bg-light-info border-0">
                            <div className="d-flex align-items-center">
                                <div className="flex-grow-1">
                                    <div className="fw-bold text-gray-800 fs-3">
                                        {loans.filter(l => l.status === 'Active').length}
                                    </div>
                                    <div className="text-gray-600 fw-semibold">Active Loans</div>
                                </div>
                                <div className="text-info fs-1">
                                    <i className="ki-duotone ki-chart-line-up">
                                        <span className="path1"></span>
                                        <span className="path2"></span>
                                    </i>
                                </div>
                            </div>
                        </Card>
                    </Col>
                </Row>

                {/* Loan Details Table */}
                <Card
                    title={
                        <div className="d-flex justify-content-between align-items-center">
                            <span className="fw-bold fs-4">Loan Details</span>
                            <span className="text-muted fw-semibold">
                                Showing {loans.length} loan{loans.length !== 1 ? 's' : ''}
                            </span>
                        </div>
                    }
                    className="shadow-sm mb-4"
                >
                    {loans.length > 0 ? (
                        <Table
                            columns={columns}
                            dataSource={loans}
                            rowKey="id"
                            pagination={{ pageSize: 5 }}
                        />
                    ) : (
                        <div className="text-center py-5">
                            <div className="fs-3 text-muted mb-3">No Loans Found</div>
                            <p className="text-muted">No active or closed loans found for this employee.</p>
                            <Button 
                                type="primary" 
                                icon={<PlusOutlined />}
                                onClick={() => setIsModalVisible(true)}
                            >
                                Add First Loan
                            </Button>
                        </div>
                    )}
                </Card>

                {/* Loan Statistics */}
                <Row gutter={[16, 16]}>
                    <Col xs={24} md={12}>
                        <Card title="Loan Types Distribution" className="h-100">
                            {loans.length > 0 ? (
                                <div>
                                    {['Personal Loan', 'Home Loan', 'Vehicle Loan', 'Education Loan'].map(type => {
                                        const typeLoans = loans.filter(l => l.loanType === type);
                                        if (typeLoans.length === 0) return null;
                                        
                                        const totalAmount = typeLoans.reduce((sum, loan) => sum + loan.loanAmount, 0);
                                        return (
                                            <div key={type} className="mb-3">
                                                <div className="d-flex justify-content-between mb-1">
                                                    <span className="fw-semibold">{type}</span>
                                                    <span className="fw-bold">{formatCurrency(totalAmount)}</span>
                                                </div>
                                                <Progress 
                                                    percent={Math.round((totalAmount / calculateTotalOutstanding()) * 100)} 
                                                    strokeColor="#6993FF"
                                                />
                                            </div>
                                        );
                                    })}
                                </div>
                            ) : (
                                <div className="text-center py-3 text-muted">
                                    No loan data available
                                </div>
                            )}
                        </Card>
                    </Col>
                    <Col xs={24} md={12}>
                        <Card title="Recent Loan Activity" className="h-100">
                            {loans.slice(0, 3).map((loan, index) => (
                                <div key={index} className="d-flex justify-content-between align-items-center py-3 border-bottom">
                                    <div>
                                        <div className="fw-semibold">{loan.loanType}</div>
                                        <div className="text-muted small">
                                            Sanctioned: {formatDate(loan.sanctionDate)}
                                        </div>
                                    </div>
                                    <div className="text-end">
                                        <div className="fw-bold text-gray-800">{formatCurrency(loan.loanAmount)}</div>
                                        <Tag color={loan.status === 'Active' ? 'green' : 'blue'} size="small">
                                            {loan.status}
                                        </Tag>
                                    </div>
                                </div>
                            ))}
                            {loans.length === 0 && (
                                <div className="text-center py-3 text-muted">
                                    No recent loan activity
                                </div>
                            )}
                        </Card>
                    </Col>
                </Row>

                {/* Add Loan Modal */}
                <Modal
                    title="Add New Loan"
                    open={isModalVisible}
                    onCancel={() => {
                        setIsModalVisible(false);
                        form.resetFields();
                    }}
                    footer={null}
                    width={600}
                >
                    <Form
                        form={form}
                        layout="vertical"
                        onFinish={handleAddLoan}
                    >
                        <Row gutter={16}>
                            <Col xs={24} md={12}>
                                <Form.Item
                                    name="loanType"
                                    label="Loan Type"
                                    rules={[{ required: true, message: 'Please select loan type' }]}
                                >
                                    <Input placeholder="e.g., Personal Loan, Home Loan" />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                                <Form.Item
                                    name="loanAmount"
                                    label="Loan Amount"
                                    rules={[{ required: true, message: 'Please enter loan amount' }]}
                                >
                                    <InputNumber
                                        style={{ width: '100%' }}
                                        formatter={value => `₹ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                                        parser={value => value.replace(/₹\s?|(,*)/g, '')}
                                        placeholder="Enter amount"
                                    />
                                </Form.Item>
                            </Col>
                        </Row>

                        <Row gutter={16}>
                            <Col xs={24} md={12}>
                                <Form.Item
                                    name="interestRate"
                                    label="Interest Rate (%)"
                                    rules={[{ required: true, message: 'Please enter interest rate' }]}
                                >
                                    <InputNumber
                                        style={{ width: '100%' }}
                                        min={0}
                                        max={100}
                                        placeholder="Enter interest rate"
                                    />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                                <Form.Item
                                    name="tenure"
                                    label="Tenure (months)"
                                    rules={[{ required: true, message: 'Please enter tenure' }]}
                                >
                                    <InputNumber
                                        style={{ width: '100%' }}
                                        min={1}
                                        max={360}
                                        placeholder="Enter tenure in months"
                                    />
                                </Form.Item>
                            </Col>
                        </Row>

                        <Row gutter={16}>
                            <Col xs={24} md={12}>
                                <Form.Item
                                    name="sanctionDate"
                                    label="Sanction Date"
                                    rules={[{ required: true, message: 'Please select sanction date' }]}
                                >
                                    <Input type="date" />
                                </Form.Item>
                            </Col>
                            <Col xs={24} md={12}>
                                <Form.Item
                                    name="emiAmount"
                                    label="Monthly EMI"
                                    rules={[{ required: true, message: 'Please enter EMI amount' }]}
                                >
                                    <InputNumber
                                        style={{ width: '100%' }}
                                        formatter={value => `₹ ${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                                        parser={value => value.replace(/₹\s?|(,*)/g, '')}
                                        placeholder="Enter EMI amount"
                                    />
                                </Form.Item>
                            </Col>
                        </Row>

                        <Form.Item
                            name="remarks"
                            label="Remarks"
                        >
                            <TextArea rows={3} placeholder="Any additional remarks..." />
                        </Form.Item>

                        <div className="d-flex justify-content-end gap-3">
                            <Button onClick={() => {
                                setIsModalVisible(false);
                                form.resetFields();
                            }}>
                                Cancel
                            </Button>
                            <Button type="primary" htmlType="submit">
                                Add Loan
                            </Button>
                        </div>
                    </Form>
                </Modal>
            </div>
        </div>
    );
}