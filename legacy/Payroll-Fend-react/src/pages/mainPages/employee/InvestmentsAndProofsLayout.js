import React from 'react';
import { Outlet, useParams, useNavigate } from 'react-router-dom';

export default function InvestmentsAndProofsLayout() {
    const { id } = useParams();
    const navigate = useNavigate();

    return (
        <div className="w-100">
            {/* Tabs Header */}
            <div className="mb-6">
                <ul className="nav nav-stretch nav-line-tabs nav-line-tabs-2x border-transparent fs-5 fw-bold">
                    <li className="nav-item">
                        <button
                            className={`nav-link text-active-primary cursor-pointer ${window.location.pathname.includes('/investments-and-proofs') && !window.location.pathname.includes('/proof') ? 'active' : ''}`}
                            onClick={() => navigate(`/employees/view/${id}/investments-and-proofs`)}
                            style={{
                                border: "none",
                                borderBottom: window.location.pathname.includes('/investments-and-proofs') && !window.location.pathname.includes('/proof') ? "2px solid #009EF7" : "none",
                                color: window.location.pathname.includes('/investments-and-proofs') && !window.location.pathname.includes('/proof') ? "#009EF7" : "#5E6278",
                                padding: "1rem 1.5rem",
                                fontWeight: window.location.pathname.includes('/investments-and-proofs') && !window.location.pathname.includes('/proof') ? "600" : "400",
                                backgroundColor: "transparent"
                            }}
                        >
                            <i className="ki-outline ki-home-2 fs-2 me-2"></i>
                            Investment Declaration
                        </button>
                    </li>
                    <li className="nav-item">
                        <button
                            className={`nav-link text-active-primary cursor-pointer ${window.location.pathname.includes('/proof') ? 'active' : ''}`}
                            onClick={() => navigate(`/employees/view/${id}/investments-and-proofs/proof`)}
                            style={{
                                border: "none",
                                borderBottom: window.location.pathname.includes('/proof') ? "2px solid #009EF7" : "none",
                                color: window.location.pathname.includes('/proof') ? "#009EF7" : "#5E6278",
                                padding: "1rem 1.5rem",
                                fontWeight: window.location.pathname.includes('/proof') ? "600" : "400",
                                backgroundColor: "transparent"
                            }}
                        >
                            <i className="ki-outline ki-document fs-2 me-2"></i>
                            Proof of Investments
                        </button>
                    </li>
                </ul>
            </div>

            {/* Content Area */}
            <div className="tab-content">
                <Outlet />
            </div>
        </div>
    );
}