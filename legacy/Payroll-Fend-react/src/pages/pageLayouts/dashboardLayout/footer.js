import React from "react";

export default function Footer() {
    return (
        <div className="footer py-4 d-flex flex-lg-column" id="kt_footer" 
             style={{
                 position: 'fixed',
                 bottom: 0,
                 left: 0,
                 right: 0,
                 zIndex: 100
             }}>

            <div className="container d-flex flex-column flex-md-row flex-stack justify-content-center">

                <div className="text-dark order-2 order-md-1 text-muted">
                    Copyright &copy; 2025 Infinevocloud Technology Solutions
                </div>

                <ul className="menu menu-gray-600 menu-hover-primary fw-bold order-1">
                    <li className="menu-item">
                        <a href="https://sec1.io/sec1-terms-and-conditions" target="_blank" className="menu-link px-2">Terms & Conditions</a>
                    </li>
                    <li className="menu-item">
                        <a href="https://sec1.io/sec1-online-order-and-payment-terms" target="_blank" className="menu-link px-2">Order & Payment Terms</a>
                    </li>
                    <li className="menu-item">
                        <a href="https://sec1.io/about-us/" target="_blank" className="menu-link px-2">About</a>
                    </li>
                    <li className="menu-item">
                        <a href="https://sec1.io/contact-us/" target="_blank"
                            className="menu-link px-2">Support</a>
                    </li>
                </ul>

            </div>

        </div>
    );
}