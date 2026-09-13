import React, { useState, useEffect } from "react";
import { Card, Tag, Row, Col, Divider, Button, Typography, message, Popconfirm } from "antd";
import { Link, useOutletContext } from "react-router-dom";
import { EditOutlined } from "@ant-design/icons";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import Loader from "../../../shared/components/loaders/fullPageLoader";

const { Title } = Typography;

export default function OverviewTab() {
    const { 
        employee, 
        formatDate, 
        formatAddress, 
        formatCurrency, 
        getHrDisplay, 
        hrUsers, 
        loadingHrUsers,
        organizationId,
        id 
    } = useOutletContext();
    
    const [loading, setLoading] = useState(false);
    const [employeeData, setEmployeeData] = useState(employee);

    // Fetch fresh employee data for this tab
    const fetchEmployeeData = async () => {
        try {
            setLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/employee-profile`, {
                params: { employeeId: id },
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });

            if (response.data && response.data.data) {
                setEmployeeData(response.data.data);
            }
        } catch (error) {
            console.error("Error fetching employee data:", error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchEmployeeData();
    }, [id]);

    const handleEnablePortal = async () => {
        try {
            setLoading(true);
            await axios.post(`${GlobalConst.API_URL}/api/employees-portal/${id}/enable-portal`, {}, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });
            message.success("Portal access enabled and invitation sent");
            fetchEmployeeData();
        } catch (error) {
            console.error("Error enabling portal:", error);
            message.error("Failed to enable portal");
            setLoading(false);
        }
    };

    const handleResendInvitation = async () => {
        try {
            setLoading(true);
            await axios.post(`${GlobalConst.API_URL}/api/employees-portal/${id}/resend-invitation`, {}, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });
            message.success("Invitation resent successfully");
            fetchEmployeeData();
        } catch (error) {
            console.error("Error resending invitation:", error);
            message.error("Failed to resend invitation");
            setLoading(false);
        }
    };
    
    const handleDisablePortal = async () => {
        try {
            setLoading(true);
            await axios.post(`${GlobalConst.API_URL}/auth/toggle-portal-access`, {
                organizationId: organizationId,
                userEmail: employeeData?.basicDetails?.workMail,
                isEmployeePortalEnable: false
            }, {
                headers: {
                    Authorization: `Bearer ${localStorage.getItem("__t")}`,
                    organizationId: organizationId
                },
            });
            message.success("Portal access disabled");
            fetchEmployeeData();
        } catch (error) {
            console.error("Error disabling portal:", error);
            message.error("Failed to disable portal");
            setLoading(false);
        }
    };

    if (loading) {
        return <Loader />;
    }

    const { basicDetails, personalDetail, bankDetail, ctc } = employeeData || {};

    // Check if profile is incomplete
    const isProfileIncomplete = !personalDetail?.pan || !personalDetail?.fatherName ||
        !personalDetail?.presentResidentialAddress || !personalDetail?.personalMail;

    return (
        <div className="tab-content">
            <div className="tab-pane fade show active" id="kt_tab_pane_overview" role="tabpanel">
                {/* Incomplete Profile Warning */}
                {isProfileIncomplete && (
                    <div className="mb-4 p-3 bg-warning bg-opacity-20 border border-warning rounded">
                        <div className="d-flex justify-content-between align-items-center">
                            <div>
                                <Tag color="orange" className="mb-2" style={{ fontSize: '12px', fontWeight: 'bold' }}>
                                    ⚠️ Incomplete Profile
                                </Tag>
                                <div className="text-dark">This employee's profile is incomplete. <Button type="link" size="small" className="p-0">Complete now</Button></div>
                            </div>
                            <EditOutlined style={{ color: '#fa8c16', fontSize: '16px' }} />
                        </div>
                    </div>
                )}

                {/* Basic Information */}
                <div className="mb-4">
                    <div className="d-flex justify-content-between align-items-center mb-3">
                        <Title level={5} className="mb-0">Basic Information</Title>
                        <Button type="link" size="small" icon={<EditOutlined />}>
                            <Link to={`/employees/edit-basic/${id}`}>Edit</Link>
                        </Button>
                    </div>
                    <Row gutter={[16, 16]}>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Name</div>
                            <div className="fs-6">{`${basicDetails?.firstName || ''} ${basicDetails?.middleName || ''} ${basicDetails?.lastName || ''}`.trim()}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Designation</div>
                            <div className="fs-6">{basicDetails?.designationName || basicDetails?.designation || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Email ID</div>
                            <div className="fs-6">{basicDetails?.workMail || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Department</div>
                            <div className="fs-6">{basicDetails?.departmentName || basicDetails?.department || '-'}</div>
                        </Col>

                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Mobile Number</div>
                            <div className="fs-6">{basicDetails?.mobile || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Work Location</div>
                            <div className="fs-6">{basicDetails?.workLocationName || basicDetails?.workLocation || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Date of Joining</div>
                            <div className="fs-6">{formatDate(basicDetails?.dateOfJoining)}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Reporting HR</div>
                            <div className="fs-6">
                                {loadingHrUsers ? (
                                    'Loading...'
                                ) : (
                                    getHrDisplay(basicDetails?.hrUser)
                                )}
                            </div>
                        </Col>

                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Portal Access</div>
                            <div className="fs-6 d-flex align-items-center gap-2 mt-1">
                                {basicDetails?.isPortalEnabled ? (
                                    <>
                                        <Tag color="green" className="m-0">Enabled</Tag>
                                        <Button type="primary" size="small" onClick={handleResendInvitation}>
                                            Re-invite
                                        </Button>
                                        <Popconfirm
                                            title="Disable portal access?"
                                            onConfirm={handleDisablePortal}
                                        >
                                            <Button type="default" danger size="small">Disable</Button>
                                        </Popconfirm>
                                    </>
                                ) : (
                                    <>
                                        <Tag color="red" className="m-0">Disabled</Tag>
                                        <Button type="primary" size="small" onClick={handleEnablePortal}>
                                            Enable
                                        </Button>
                                    </>
                                )}
                            </div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Gender</div>
                            <div className="fs-6">{basicDetails?.gender || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Employee Status</div>
                            <div className="fs-6">{basicDetails?.employeeStatus || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Employee ID</div>
                            <div className="fs-6">{basicDetails?.employeeNumber || '-'}</div>
                        </Col>
                    </Row>
                </div>

                <Divider />

                {/* Statutory Details */}
                <div className="mb-4">
                    <div className="d-flex justify-content-between align-items-center mb-3">
                        <Title level={5} className="mb-0">Statutory Details</Title>
                        <Button type="link" size="small" icon={<EditOutlined />}>
                            <Link to={`/employees/edit-statutory/${id}`}>Edit</Link>
                        </Button>
                    </div>
                    <Row gutter={[16, 16]}>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">EPF Eligibility</div>
                            <div className="fs-6">
                                {basicDetails?.eligibleForPf ? (
                                    <Tag color="green">Enabled</Tag>
                                ) : (
                                    <Tag color="red">Disabled</Tag>
                                )}
                            </div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Professional Tax</div>
                            <div className="fs-6">
                                {basicDetails?.eligibleForPt ? (
                                    <Tag color="green">Enabled</Tag>
                                ) : (
                                    <Tag color="red">Disabled</Tag>
                                )}
                            </div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">ESI Eligibility</div>
                            <div className="fs-6">
                                {basicDetails?.eligibleForEsi ? (
                                    <Tag color="green">Enabled</Tag>
                                ) : (
                                    <Tag color="red">Disabled</Tag>
                                )}
                            </div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">EPS Eligibility</div>
                            <div className="fs-6">
                                {basicDetails?.eligibleForEps ? (
                                    <Tag color="green">Enabled</Tag>
                                ) : (
                                    <Tag color="red">Disabled</Tag>
                                )}
                            </div>
                        </Col>
                        {basicDetails?.pfAccountNumber && (
                            <Col xs={24} md={12}>
                                <div className="fw-bold text-muted">PF Account Number</div>
                                <div className="fs-6">{basicDetails.pfAccountNumber}</div>
                            </Col>
                        )}
                        {basicDetails?.uan && (
                            <Col xs={24} md={12}>
                                <div className="fw-bold text-muted">UAN</div>
                                <div className="fs-6">{basicDetails.uan}</div>
                            </Col>
                        )}
                    </Row>
                </div>

                <Divider />

                {/* Personal Information */}
                <div className="mb-4">
                    <div className="d-flex justify-content-between align-items-center mb-3">
                        <Title level={5} className="mb-0">Personal Information</Title>
                        <Button type="link" size="small" icon={<EditOutlined />}>
                            <Link to={`/employees/edit-personal/${id}`}>Edit</Link>
                        </Button>
                    </div>
                    <Row gutter={[16, 16]}>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Date of Birth</div>
                            <div className="fs-6">
                                {formatDate(personalDetail?.dateOfBirth)}
                            </div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Personal Email</div>
                            <div className="fs-6">{personalDetail?.personalMail || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Father's Name</div>
                            <div className="fs-6">{personalDetail?.fatherName || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Residential Address</div>
                            <div className="fs-6">
                                {formatAddress(personalDetail?.presentResidentialAddress)}
                            </div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">PAN</div>
                            <div className="fs-6">{personalDetail?.pan || '-'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Differently Abled Type</div>
                            <div className="fs-6">{personalDetail?.differentlyAbledType || 'None'}</div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="fw-bold text-muted">Tax Exemption Eligible</div>
                            <div className="fs-6">
                                {personalDetail?.isEligibleForFullIncomeTaxExemption ? 'Yes' : 'No'}
                            </div>
                        </Col>
                    </Row>
                </div>

                <Divider />

                {/* Payment Information */}
                <div>
                    <div className="d-flex justify-content-between align-items-center mb-3">
                        <Title level={5} className="mb-0">Payment Information</Title>
                        <Button type="link" size="small" icon={<EditOutlined />}>
                            <Link to={`/employees/edit-payment/${id}`}>Edit</Link>
                        </Button>
                    </div>
                    <Row gutter={[16, 16]}>
                        <Col xs={24}>
                            <div className="fw-bold text-muted">Payment Mode</div>
                            <div className="fs-6 text-capitalize">
                                {bankDetail?.paymentMode ?
                                    bankDetail.paymentMode.replace(/([A-Z])/g, ' $1').trim() : '-'
                                }
                            </div>
                        </Col>
                        {bankDetail?.bankName && (
                            <Col xs={24} md={12}>
                                <div className="fw-bold text-muted">Bank Name</div>
                                <div className="fs-6">{bankDetail.bankName}</div>
                            </Col>
                        )}
                        {bankDetail?.accountHolderName && (
                            <Col xs={24} md={12}>
                                <div className="fw-bold text-muted">Account Holder Name</div>
                                <div className="fs-6">{bankDetail.accountHolderName}</div>
                            </Col>
                        )}
                        {bankDetail?.bankAccountNumber && (
                            <Col xs={24} md={12}>
                                <div className="fw-bold text-muted">Account Number</div>
                                <div className="fs-6">••••{bankDetail.bankAccountNumber.slice(-4)}</div>
                            </Col>
                        )}
                        {bankDetail?.ifscCode && (
                            <Col xs={24} md={12}>
                                <div className="fw-bold text-muted">IFSC Code</div>
                                <div className="fs-6">{bankDetail.ifscCode}</div>
                            </Col>
                        )}
                        {bankDetail?.bankAccountType && (
                            <Col xs={24} md={12}>
                                <div className="fw-bold text-muted">Account Type</div>
                                <div className="fs-6">{bankDetail.bankAccountType}</div>
                            </Col>
                        )}
                    </Row>
                </div>
            </div>
        </div>
    );
}