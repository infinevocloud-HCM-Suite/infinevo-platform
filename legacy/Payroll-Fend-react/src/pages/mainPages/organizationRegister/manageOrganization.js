import React, { useState, useEffect } from 'react';
import { Helmet } from 'react-helmet-async';
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios';
import { GlobalConst } from '../../../shared/appConfig/globalConst';
import { errorMsg, successMsg } from '../../../shared/helpers/msgHelper';
import jwtDecode from 'jwt-decode';
import { getUserInfoFromToken } from "../../../shared/helpers/tokenHelper";


export default function ManageOrganization() {
    const navigate = useNavigate();
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [selectedOrg, setSelectedOrg] = useState(null);
    const [organizations, setOrganizations] = useState([]);
    const [loading, setLoading] = useState(false);
    const [user, setUser] = useState("");


    // 🔹 Decode username from token
   useEffect(() => {
    const userInfo = getUserInfoFromToken();
    if (userInfo) {
      setUser(userInfo.name || "User");
    }

  }, []);

    // 🔹 Fetch active organizations
    useEffect(() => {
        const fetchOrganizations = async () => {
            setLoading(true);
            try {
                const token = localStorage.getItem('__t');
                const response = await axios.get(
                    `${GlobalConst.API_URL}/api/organizations/active-organizations`,
                    {
                        headers: {
                            Authorization: `Bearer ${token}`,
                        },
                    }
                );

                if (response.data?.status === 200 && response.data?.data) {
                    setOrganizations(response.data.data);
                } else {
                    errorMsg(
                        'Load Failed',
                        response.data?.message || 'Unable to load organizations.',
                        false
                    );
                }
            } catch (e) {
                console.error('Fetch Error: ', e);
                errorMsg(
                    'Error',
                    e.response?.data?.message || e.message || 'Something went wrong',
                    false
                );
            } finally {
                setLoading(false);
            }
        };

        fetchOrganizations();
    }, []);

    const handleDeleteOrg = (org) => {
        setSelectedOrg(org);
        setShowDeleteModal(true);
    };

    const confirmDeleteOrg = async () => {
        if (!selectedOrg) return;

        try {
            const token = localStorage.getItem('__t');
            await axios.delete(
                `${GlobalConst.API_URL}/api/organizations/${selectedOrg.organizationId}`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                }
            );

            successMsg('Success', 'Organization deleted successfully', false);
            setOrganizations((prev) =>
                prev.filter((org) => org.organizationId !== selectedOrg.organizationId)
            );
        } catch (e) {
            console.error('Delete Error: ', e);
            errorMsg(
                'Error',
                e.response?.data?.message || 'Failed to delete organization',
                true
            );
        } finally {
            setShowDeleteModal(false);
            setSelectedOrg(null);
        }
    };

    const handleGoToOrganization = (orgId) => {
        navigate('/onboarding-dashboard', { state: { orgId } });
    };

    return (
        <>
            <Helmet>
                <title>Manage Organizations - INFINEVOCLOUD</title>
            </Helmet>

            <div className="d-flex flex-column flex-column-fluid">
                <div className="app-container py-5 py-lg-10">
                    <div className="card mx-auto" style={{ maxWidth: '1000px' }}>
                        <div className="card-body p-5 p-lg-10">
                            {/* Header */}
                            <div className="d-flex flex-column flex-sm-row align-items-start align-items-sm-center justify-content-between border-bottom pb-5 mb-7">
                                <div className="d-flex align-items-center mb-3 mb-sm-0">
                                    <Link
                                        to="/dashboard"
                                        className="btn btn-icon btn-sm btn-light me-3 me-sm-5"
                                    >
                                        <i className="ki-duotone ki-black-left fs-2">
                                            <span className="path1"></span>
                                            <span className="path2"></span>
                                        </i>
                                    </Link>
                                    <h2 className="mb-0 text-gray-800 fs-2">Organizations</h2>
                                </div>
                                <Link to="/create-new-organization" className="btn btn-primary">
                                    Create New Organization
                                </Link>
                            </div>

                            {/* Welcome Message */}
                            <div className="mb-7">
                                <h3 className="text-gray-900 mb-2 fs-3">Hi {user}</h3>
                                <p className="text-muted fs-6">
                                    You belong to the following active organisations. Please select
                                    the organisation you wish to access now.
                                </p>
                            </div>

                            {/* Organizations Section */}
                            <div className="mb-0">
                                <h4 className="text-gray-800 mb-5 fs-4">My Organisations</h4>

                                {loading && <p>Loading organizations...</p>}

                                {organizations.length === 0 && !loading && (
                                    <p className="text-muted">No organizations found.</p>
                                )}

                                {organizations.map((org) => (
                                    <div
                                        key={org.organizationId}
                                        className="card mb-5 border-0 shadow-sm"
                                    >
                                        <div className="card-body p-5">
                                            <div className="d-flex flex-column flex-md-row align-items-start align-items-md-center justify-content-between">
                                                {/* Organization Details */}
                                                <div className="flex-grow-1 me-0 me-md-5 mb-3 mb-md-0">
                                                    <div className="d-flex align-items-center mb-2">
                                                        <h5 className="text-gray-900 fw-bold me-2 mb-0 fs-5">
                                                            {org.organizationName}
                                                        </h5>
                                                    </div>
                                                    <div className="text-muted mb-1 fs-7">
                                                        Organisation ID: {org.organizationId}
                                                    </div>
                                                    <div className="text-muted fs-7">
                                                        <em>Organisation created on {org.createdDate}</em>
                                                    </div>
                                                </div>

                                                {/* Action Buttons */}
                                                <div className="d-flex align-items-center gap-2 w-100 w-md-auto">
                                                    <button
                                                        onClick={() => handleGoToOrganization(org.organizationId)}
                                                        className="btn btn-sm btn-primary flex-grow-1 flex-md-grow-0"
                                                        style={{ minWidth: '120px' }}
                                                    >
                                                        Go to this Organisation
                                                    </button>

                                                    <button
                                                        className="btn btn-sm btn-icon btn-color-gray-500 btn-active-color-primary"
                                                        onClick={() => handleDeleteOrg(org)}
                                                    >
                                                        <i className="ki-duotone ki-trash fs-2 text-danger">
                                                            <span className="path1"></span>
                                                            <span className="path2"></span>
                                                        </i>
                                                    </button>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Delete Organization Modal */}
            {showDeleteModal && selectedOrg && (
                <div
                    className="modal fade show d-block"
                    tabIndex="-1"
                    style={{ backgroundColor: 'rgba(0,0,0,0.5)', zIndex: 1050 }}
                >
                    <div className="modal-dialog modal-dialog-centered">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">
                                    Delete {selectedOrg.organizationName} organization?
                                </h5>
                                <button
                                    type="button"
                                    className="btn-close"
                                    onClick={() => setShowDeleteModal(false)}
                                ></button>
                            </div>
                            <div className="modal-body">
                                <p className="text-danger mb-3">
                                    <strong>Warning: This action cannot be undone.</strong>
                                </p>
                                <p className="text-muted">
                                    All data associated with this organization will be permanently
                                    deleted. This includes employee records, payroll data, and all
                                    other organization information.
                                </p>
                                <p className="text-muted mb-0">
                                    Are you sure you want to delete this organization?
                                </p>
                            </div>
                            <div className="modal-footer">
                                <button
                                    type="button"
                                    className="btn btn-light"
                                    onClick={() => setShowDeleteModal(false)}
                                >
                                    Cancel
                                </button>
                                <button
                                    type="button"
                                    className="btn btn-danger"
                                    onClick={confirmDeleteOrg}
                                >
                                    Delete Organisation
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
