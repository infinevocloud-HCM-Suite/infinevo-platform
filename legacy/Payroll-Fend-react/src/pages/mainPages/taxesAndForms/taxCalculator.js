// TaxCalculator.js
import React, { useState, useMemo } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";

/**
 * TaxCalculator.js
 * - Bootstrap / Keentheme classes only
 * - Inputs:
 *    Assessment Year (dropdown)
 *    Tax Payer (Individual only)
 *    Whether opting out new tax regime of section 115BAC (1A) ? (Yes/No)
 *    Category (Age) - Less than 60 years only
 *    Residential Status - Resident / Non Resident
 *    Net Taxable Income - numeric
 * - Outputs (auto-calculated on input change):
 *    Income Tax after relief u/s 87A
 *    Surcharge
 *    Health and Education Cess
 *    Total Tax Liability
 *
 * Notes:
 * - Rebate under section 87A implemented for resident individuals with income <= 7,00,000 (cap ₹25,000)
 * - Surcharge tiers implemented (common slab approach). If you require exact legislative changes,
 *   update the surcharge logic accordingly.
 * - Cess = 4% on (tax + surcharge - rebate)
 */

const formatNumber = (val) => {
    if (val === "" || val === null || isNaN(Number(val))) return "";
    return Number(val).toLocaleString("en-IN", { maximumFractionDigits: 0 });
};

const parseNumber = (val) => {
    if (!val) return 0;
    const cleaned = String(val).replace(/[,₹\s]/g, "");
    return Number(cleaned || 0);
};

export default function TaxCalculator() {
    const navigate = useNavigate();

    const [assessmentYear, setAssessmentYear] = useState("2025-26");
    const [taxPayer, setTaxPayer] = useState("Individual");
    const [optOut115BAC, setOptOut115BAC] = useState("Yes"); // Yes -> opt out => use old regime
    const [ageCategory, setAgeCategory] = useState("Less than 60 years");
    const [residentialStatus, setResidentialStatus] = useState("Resident");
    const [netTaxableIncomeStr, setNetTaxableIncomeStr] = useState("");
    const [showActModal, setShowActModal] = useState(false);


    // Derived numeric input
    const netTaxableIncome = useMemo(() => parseNumber(netTaxableIncomeStr), [netTaxableIncomeStr]);

    // --- Tax calculation helpers ---
    const calcOldRegimeTax = (income) => {
        // Old regime (basic slabs for <60 years - example matching your screenshot)
        // 0 - 2.5L : 0
        // 2.5L - 5L : 5%
        // 5L - 10L  : 20%
        // 10L - ... : 30%
        let tax = 0;
        if (income <= 250000) return 0;
        let remaining = income;

        // slab 2.5L - 5L
        if (income > 250000) {
            const slabAmount = Math.max(0, Math.min(500000, income) - 250000);
            tax += slabAmount * 0.05;
        }

        // slab 5L - 10L
        if (income > 500000) {
            const slabAmount = Math.max(0, Math.min(1000000, income) - 500000);
            tax += slabAmount * 0.2;
        }

        // slab 10L+
        if (income > 1000000) {
            const slabAmount = income - 1000000;
            tax += slabAmount * 0.3;
        }

        return Math.round(tax);
    };

    const calcNewRegimeTax = (income) => {
        // Example new regime slabs (simple implementation)
        // Up to 3L : 0
        // 3L - 6L : 5%
        // 6L - 9L : 10%
        // 9L - 12L: 15%
        // 12L - 15L: 20%
        // Above 15L: 30%
        let tax = 0;
        if (income <= 300000) return 0;

        if (income > 300000) {
            const slab = Math.max(0, Math.min(600000, income) - 300000);
            tax += slab * 0.05;
        }
        if (income > 600000) {
            const slab = Math.max(0, Math.min(900000, income) - 600000);
            tax += slab * 0.10;
        }
        if (income > 900000) {
            const slab = Math.max(0, Math.min(1200000, income) - 900000);
            tax += slab * 0.15;
        }
        if (income > 1200000) {
            const slab = Math.max(0, Math.min(1500000, income) - 1200000);
            tax += slab * 0.20;
        }
        if (income > 1500000) {
            const slab = income - 1500000;
            tax += slab * 0.30;
        }

        return Math.round(tax);
    };

    const calcSurcharge = (income, baseTax) => {
        // Surcharge applied as percentage of baseTax.
        // Common brackets:
        // <= 50L : 0%
        // >50L & <=1Cr : 10%
        // >1Cr & <=2Cr : 15%
        // >2Cr & <=5Cr : 25%
        // >5Cr : 37%
        if (income <= 5000000) return 0; // upto 50 lakh -> 0
        if (income > 5000000 && income <= 10000000) return Math.round(baseTax * 0.10); // 50L-1Cr
        if (income > 10000000 && income <= 20000000) return Math.round(baseTax * 0.15); // 1Cr-2Cr
        if (income > 20000000 && income <= 50000000) return Math.round(baseTax * 0.25); // 2Cr-5Cr
        if (income > 50000000) return Math.round(baseTax * 0.37); // >5Cr
        return 0;
    };

    const calcRebate87A = (income, taxBeforeRebate, resident) => {
        // Current implemented logic: resident individual with income <= 7,00,000 eligible for rebate under section 87A.
        // rebate is limited to ₹25,000 (typical cap). Adjust if law changes.
        if (resident !== true) return 0;
        if (income <= 700000) {
            // rebate is lesser of taxBeforeRebate and 25,000
            return Math.min(25000, Math.round(taxBeforeRebate));
        }
        return 0;
    };

    // Compute final values
    const {
        taxAfterReliefDisplay,
        surchargeDisplay,
        cessDisplay,
        totalTaxLiabilityDisplay,
        incomeTaxBeforeRebate,
        surchargeAmount,
        rebateAmount,
        cessAmount,
        totalTaxLiability
    } = useMemo(() => {
        const income = Number(netTaxableIncome || 0);
        let baseTax = 0;

        const useOld = optOut115BAC === "Yes"; // yes => opt out => use old regime
        if (useOld) baseTax = calcOldRegimeTax(income);
        else baseTax = calcNewRegimeTax(income);

        const surcharge = calcSurcharge(income, baseTax);

        // rebate eligibility: resident
        const resident = residentialStatus === "Resident";

        // rebate applies on tax + surcharge (but cannot exceed that amount).
        // we'll compute rebate cap as min(tax + surcharge, rebate cap).
        const provisionalTaxBeforeRebate = baseTax + surcharge;
        const rebate = calcRebate87A(income, provisionalTaxBeforeRebate, resident);

        const taxableAfterRebate = provisionalTaxBeforeRebate - rebate;
        const cess = Math.round(taxableAfterRebate * 0.04); // 4% cess

        const total = taxableAfterRebate + cess;

        return {
            taxAfterReliefDisplay: provisionalTaxBeforeRebate - rebate, // this is "Income Tax after relief u/s 87A"
            surchargeDisplay: surcharge,
            cessDisplay: cess,
            totalTaxLiabilityDisplay: total,
            incomeTaxBeforeRebate: baseTax,
            surchargeAmount: surcharge,
            rebateAmount: rebate,
            cessAmount: cess,
            totalTaxLiability: total
        };
    }, [netTaxableIncome, optOut115BAC, residentialStatus]);

    // Reset function
    const handleReset = () => {
        setAssessmentYear("2025-26");
        setTaxPayer("Individual");
        setOptOut115BAC("Yes");
        setAgeCategory("Less than 60 years");
        setResidentialStatus("Resident");
        setNetTaxableIncomeStr("");
    };

    // Basic validation: only numbers allowed for income input
    const handleIncomeChange = (e) => {
        const raw = e.target.value;
        // allow digits, commas
        const cleaned = raw.replace(/[^\d,]/g, "");
        setNetTaxableIncomeStr(cleaned);
    };



    return (
        <div className="d-flex flex-column" style={{ padding: "1.25rem" }}>
            <Helmet>
                <title>Tax Calculator — HRMS InfiNevoCloud</title>
            </Helmet>

            <div className="card shadow-sm rounded-3" style={{ border: "1px solid #eef1f5" }}>
                <div className="card-body">
                    <div className="d-flex justify-content-between align-items-center mb-4">
                        <div>
                            <h3 className="fw-bolder mb-0">Tax Calculator</h3>
                            <small className="text-muted">As amended upto Finance Act 2025</small>
                        </div>


                        <div>

                            <a
                                href="#"
                                onClick={(e) => {
                                    e.preventDefault();
                                    setShowActModal(true);
                                }}
                                className="text-muted small"
                                style={{ textDecoration: "none" }}
                            >
                                Click here to view relevant
                                <strong style={{ color: "#0d6efd", marginLeft: "4px" }}>Act &amp; Rule</strong>
                            </a>

                            <button className="btn btn-sm btn-outline-secondary me-2" onClick={() => navigate(-1)}>
                                Back
                            </button>
                            {/* <button className="btn btn-sm btn-light" disabled>
                Help
              </button> */}




                        </div>
                    </div>

                    <div className="row g-3">
                        <div className="col-md-4">
                            <label className="form-label">Assessment Year</label>
                            <select
                                className="form-select"
                                value={assessmentYear}
                                onChange={(e) => setAssessmentYear(e.target.value)}
                            >
                                <option>2025-26</option>
                                <option>2024-25</option>
                                <option>2023-24</option>
                            </select>
                        </div>

                        <div className="col-md-4">
                            <label className="form-label">Tax Payer</label>
                            <select
                                className="form-select"
                                value={taxPayer}
                                onChange={(e) => setTaxPayer(e.target.value)}
                            >
                                <option>Individual</option>
                            </select>
                        </div>

                        <div className="col-md-4">
                            <label className="form-label">Whether opting out new tax regime of section 115BAC (1A)?</label>
                            <select
                                className="form-select"
                                value={optOut115BAC}
                                onChange={(e) => setOptOut115BAC(e.target.value)}
                            >
                                <option>Yes</option>
                                <option>No</option>
                            </select>
                        </div>

                        <div className="col-md-4">
                            <label className="form-label">Category (Age)</label>
                            <select
                                className="form-select"
                                value={ageCategory}
                                onChange={(e) => setAgeCategory(e.target.value)}
                            >
                                <option>Less than 60 years</option>
                            </select>
                        </div>

                        <div className="col-md-4">
                            <label className="form-label">Residential Status</label>
                            <select
                                className="form-select"
                                value={residentialStatus}
                                onChange={(e) => setResidentialStatus(e.target.value)}
                            >
                                <option>Resident</option>
                                <option>Non Resident</option>
                            </select>
                        </div>

                        <div className="col-md-4">
                            <label className="form-label">Net Taxable Income</label>
                            <input
                                type="text"
                                className="form-control"
                                placeholder="Enter amount (numbers only)"
                                value={netTaxableIncomeStr}
                                onChange={handleIncomeChange}
                            />
                            <small className="text-muted">Example: 15,00,000</small>
                        </div>
                    </div>

                    <hr className="my-4" />

                    <div className="row g-3">
                        <div className="col-md-6">
                            <label className="form-label">Income Tax after relief u/s 87A</label>
                            <input
                                readOnly
                                className="form-control bg-light"
                                value={taxAfterReliefDisplay !== undefined ? `₹ ${Number(taxAfterReliefDisplay || 0).toLocaleString("en-IN")}` : ""}
                            />
                        </div>

                        <div className="col-md-6">
                            <label className="form-label">Surcharge</label>
                            <input
                                readOnly
                                className="form-control bg-light"
                                value={`₹ ${Number(surchargeDisplay || 0).toLocaleString("en-IN")}`}
                            />
                        </div>

                        <div className="col-md-6">
                            <label className="form-label">Health and Education Cess</label>
                            <input
                                readOnly
                                className="form-control bg-light"
                                value={`₹ ${Number(cessDisplay || 0).toLocaleString("en-IN")}`}
                            />
                        </div>

                        <div className="col-md-6">
                            <label className="form-label">Total Tax Liability</label>
                            <input
                                readOnly
                                className="form-control bg-light fw-bold"
                                value={`₹ ${Number(totalTaxLiabilityDisplay || 0).toLocaleString("en-IN")}`}
                            />
                        </div>
                    </div>

                    <div className="d-flex justify-content-end mt-4">
                        <button className="btn btn-secondary me-2" onClick={handleReset}>
                            Reset
                        </button>
                        <button
                            className="btn btn-primary"
                            onClick={() => {
                                // optional: copy summary or show modal — keep simple for now
                                // we simply focus the total field
                                const el = document.querySelector("input[readonly].fw-bold");
                                if (el) el.focus();
                            }}
                        >
                            Calculate
                        </button>
                    </div>

                    <small className="text-muted d-block mt-3">
                        Advisory: This calculator is for quick estimates only. For filing returns, use the
                        exact provisions contained in the relevant Acts / Rules.
                    </small>
                </div>
            </div>


            {showActModal && (
                <div
                    className="modal fade show d-block"
                    tabIndex="-1"
                    role="dialog"
                    aria-modal="true"
                    style={{ background: "rgba(0,0,0,0.4)" }}
                    onClick={() => setShowActModal(false)}
                >
                    <div
                        className="modal-dialog modal-lg modal-dialog-centered"
                        role="document"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">Relevant Acts &amp; Rules</h5>
                                <button type="button" className="btn-close" aria-label="Close" onClick={() => setShowActModal(false)} />
                            </div>
                            <div className="modal-body">
                                <p className="mb-2">
                                    The calculator uses the following sections and acts (for reference only):
                                </p>

                                <ul>
                                    <li>
                                        <strong>Section 115BAC (1A)</strong> — New tax regime option: explains taxation rules for taxpayers who opt for the simplified/new tax regime. (Used to determine whether to run old vs new regime tax slabs.)
                                    </li>
                                    <li>
                                        <strong>Section 87A</strong> — Rebate for resident individuals: rebate available for resident individuals subject to the specified income threshold. The calculator applies a rebate cap (₹25,000) for incomes ≤ ₹7,00,000 for quick estimates; adjust as required.
                                    </li>
                                    <li>
                                        <strong>Finance Act 2025</strong> — The calculator title notes "As amended upto Finance Act 2025". Update this text when new Finance Acts affect slabs/cess/surcharge.
                                    </li>
                                </ul>

                                <p className="small text-muted">
                                    <strong>Note:</strong> This modal is only for quick reference. For authoritative text, consult the official Income Tax Act, Finance Act, and notifications published by the Government of India.
                                </p>
                            </div>
                            <div className="modal-footer">
                                <a
                                    // className="btn btn-outline-primary"
                                    href="https://www.incometax.gov.in" // replace with exact URL if you wish
                                    target="_blank"
                                    rel="noreferrer"
                                >
                                    Official Income Tax Dept.
                                </a>
                                <button className="btn btn-secondary" onClick={() => setShowActModal(false)}>Close</button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
