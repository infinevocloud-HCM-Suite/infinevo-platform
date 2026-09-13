// EPFSampleModal.jsx
import React, { useMemo, useState } from "react";
import PropTypes from "prop-types";

const EPFSampleModal = ({ show, onClose }) => {
  const [expandedConfig, setExpandedConfig] = useState(false);
  const [withLOP, setWithLOP] = useState(false);

  const basePackages = useMemo(
    () => [
      { id: 1, name: "Package 1", basic: 25000, transport: 4000, telephone: 3500 },
      { id: 2, name: "Package 2", basic: 18000, transport: 2000, telephone: 3000 },
      { id: 3, name: "Package 3", basic: 12000, transport: 1500, telephone: 1000 },
    ],
    []
  );

  const PF_RATE = 0.12;

  const calculatePackage = (pkg, lop) => {
    if (!lop) {
      if (pkg.basic >= 15000) {
        const pfWage = pkg.basic;
        return { basic: pkg.basic, transport: pkg.transport, telephone: pkg.telephone, pfWage, epf: Math.round(pfWage * PF_RATE) };
      } else {
        const pfWage = pkg.basic + pkg.transport + pkg.telephone;
        return { basic: pkg.basic, transport: pkg.transport, telephone: pkg.telephone, pfWage, epf: Math.round(pfWage * PF_RATE) };
      }
    } else {
      const halfBasic = Math.round(pkg.basic / 2);
      const halfTransport = Math.round(pkg.transport / 2);
      const halfTelephone = Math.round(pkg.telephone / 2);
      let pfWageSum = halfBasic + halfTransport + halfTelephone;
      const pfWage = Math.min(pfWageSum, 15000);
      return { basic: halfBasic, transport: halfTransport, telephone: halfTelephone, pfWage, epf: Math.round(pfWage * PF_RATE) };
    }
  };

  const computed = basePackages.map((p) => ({ ...p, calc: calculatePackage(p, withLOP) }));

  if (!show) return null;

  const r = (val) =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(val);

  return (
    <div className="modal fade show" style={{ display: "block", backgroundColor: "rgba(18, 23, 39, 0.5)" }}>
      <div className="modal-dialog modal-xl modal-dialog-centered" role="document">
        <div className="modal-content rounded-3 border-0 shadow">
          <div className="modal-header border-0">
            <h5 className="modal-title">EPF Sample Calculation</h5>
            <button type="button" className="btn-close" aria-label="Close" onClick={onClose}></button>
          </div>

          <div className="modal-body">
            <p className="text-muted">
              Let's assume the salary packages considered for EPF is as shown as below, the calculation is based on the
              settings we've configured
            </p>

            <button type="button" className="btn btn-link p-0 mb-3" onClick={() => setExpandedConfig((s) => !s)}>
              <small className="text-primary">Show current configuration {expandedConfig ? "▲" : "▼"}</small>
            </button>

            {expandedConfig && (
              <div className="card mb-3">
                <div className="card-body">
                  <h6 className="text-uppercase small text-muted">PF Contribution Settings</h6>
                  <div className="row mb-2">
                    <div className="col-sm-6"><strong>Employer Contribution:</strong> 12% of Actual PF Wage</div>
                    <div className="col-sm-6"><strong>Employee Contribution:</strong> 12% of Actual PF Wage</div>
                  </div>
                  <h6 className="text-uppercase small text-muted mt-3">LOP Contribution</h6>
                  <div className="row">
                    <div className="col-sm-6"><strong>Pro-rate Restricted PF Wage:</strong> Disabled</div>
                    <div className="col-sm-6"><strong>Consider all components when PF wage &lt; ₹15,000 after LOP:</strong> <span className="text-success">Enabled</span></div>
                  </div>
                </div>
              </div>
            )}

            <div className="d-flex justify-content-end align-items-center mb-2">
              <div className="form-check">
                <input id="lopCheck" type="checkbox" className="form-check-input" checked={withLOP} onChange={(e) => setWithLOP(e.target.checked)} />
                <label className="form-check-label" htmlFor="lopCheck">With 15 days LOP</label>
              </div>
            </div>

            <div className="table-responsive">
              <table className="table table-bordered align-middle">
                <thead className="table-light">
                  <tr>
                    <th style={{ minWidth: 280 }}>Salary Components {withLOP && <span className="badge bg-warning text-dark ms-2">WITH 15 DAYS LOP</span>}</th>
                    {computed.map((p) => <th key={p.id} className="text-center"><div className="d-flex align-items-center justify-content-center"><span className="me-2">{p.name}</span></div></th>)}
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td><div className="fw-bold">Basic</div><small className="text-muted">Always considered for EPF</small></td>
                    {computed.map((p) => <td key={p.id} className="align-middle text-center">{r(withLOP ? p.calc.basic : p.basic)}</td>)}
                  </tr>
                  <tr>
                    <td><div className="fw-bold">Transport Allowance</div><small className="text-muted">Considered for EPF only when PF wage &lt; ₹ 15,000</small></td>
                    {computed.map((p) => <td key={p.id} className="text-center align-middle">{r(withLOP ? p.calc.transport : p.transport)}</td>)}
                  </tr>
                  <tr>
                    <td><div className="fw-bold">Telephone Allowance</div><small className="text-muted">Considered for EPF only when PF wage &lt; ₹ 15,000</small></td>
                    {computed.map((p) => <td key={p.id} className="text-center align-middle">{r(withLOP ? p.calc.telephone : p.telephone)}</td>)}
                  </tr>
                  <tr className="table-light">
                    <td className="fw-bold">EPF Contribution {withLOP && <span className="badge bg-warning text-dark ms-2">WITH 15 DAYS LOP</span>}</td>
                    {computed.map((p) => (
                      <td key={p.id} className="align-middle text-center">
                        <div className="fw-semibold">{r(p.calc.epf)}</div>
                        <div className="small text-muted">{`${Math.round(PF_RATE * 100)}% of ${p.calc.pfWage.toLocaleString("en-IN")}`}</div>
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>

          </div>

          <div className="modal-footer border-0">
            <div className="w-100 d-flex justify-content-start">
              <button type="button" className="btn btn-light me-2" onClick={() => { setWithLOP(false); onClose(); }}>Cancel</button>
            </div>
            <div>
              <button type="button" className="btn btn-primary" onClick={onClose}>Okay, Got It!</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

EPFSampleModal.propTypes = {
  show: PropTypes.bool,
  onClose: PropTypes.func.isRequired,
};

EPFSampleModal.defaultProps = {
  show: false,
};

export default EPFSampleModal;
