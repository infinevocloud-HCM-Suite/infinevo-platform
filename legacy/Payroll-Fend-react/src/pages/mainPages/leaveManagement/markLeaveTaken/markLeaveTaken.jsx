import React from "react";
import { Helmet } from "react-helmet-async";
import { Empty } from "antd";

export default function MarkLeaveTaken() {
  return (
    <>
      <Helmet>
        <title>Mark Leaves Taken | HRMS InfiNevoCloud</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white min-vh-100">
        <div className="p-6">
          <div className="d-inline-block px-3 py-1 bg-light-primary text-primary fw-bold rounded fs-8 mb-2">
            2. ADMIN – MARK LEAVES TAKEN (Monthly Consumption)
          </div>
          <h2 className="fw-bolder text-gray-900 mb-1">Mark Leaves Taken</h2>
          <div className="text-muted fs-6 mb-6">
            Record monthly leaves consumed by employees to deduct from allocated balance and auto-calculate LOP.
          </div>

          <div className="card border-0 shadow-sm p-12 text-center" style={{ border: "1px dashed #CBD5E1", borderRadius: "12px" }}>
            <Empty description="Mark Leaves Taken functionality will be active in the next step." />
          </div>
        </div>
      </div>
    </>
  );
}
