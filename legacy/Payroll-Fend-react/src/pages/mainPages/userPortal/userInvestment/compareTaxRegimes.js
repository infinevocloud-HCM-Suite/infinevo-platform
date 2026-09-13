import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { getDecodedToken } from "../../../../shared/helpers/tokenHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { successMsg, errorMsg } from "../../../../shared/helpers/msgHelper";


export default function CompareTaxRegimes() {


    // =======================
    // FINANCIAL YEAR UTIL
    // FY = END YEAR
    // Example: FY 2026 → 2025-04 to 2026-03
    // =======================
    const getCurrentFinancialYear = () => {
        const today = new Date();
        const month = today.getMonth() + 1;
        const year = today.getFullYear();

        return month >= 4 ? year + 1 : year;
    };


    // =======================
    // IT DECLARATION / TAX COMPARISON FY (UPCOMING FY)
    // Example:
    // Current FY 2026 (2025–26) → Compare FY 2027 (2026–27)
    // =======================
    // const getDeclarationFinancialYear = () => {
    //     const today = new Date();
    //     const month = today.getMonth() + 1;
    //     const year = today.getFullYear();

    //     const currentFYEnd = month >= 4 ? year + 1 : year;
    //     return currentFYEnd + 1;
    // };


    const navigate = useNavigate();
    const [selectedRegime, setSelectedRegime] = useState("OLD");
    const [isNewRegimeProcessed, setIsNewRegimeProcessed] = useState(false);

    // State for API data

    const [isSaving, setIsSaving] = useState(false);

    const [isLoading, setIsLoading] = useState(true);
    const [taxLoading, setTaxLoading] = useState(true);
    const [extraLoading, setExtraLoading] = useState(false);
    const [employeeId, setEmployeeId] = useState("");
    const [organizationId, setOrganizationId] = useState("");
    const [financialYear, setFinancialYear] = useState(getCurrentFinancialYear());


    // const [financialYear, setFinancialYear] = useState(getDeclarationFinancialYear());

    // Tax data from backend
    const [taxData, setTaxData] = useState({
        OLD: {
            totalTax: "₹0.00",
            description: "Old Tax Regime with all deductions",
            breakdown: {
                houseRent: "₹0.00",
                metroCity: "",
                epf: "₹0.00",
                lifeInsurance: "₹0.00",
                mediclaimParents: "₹0.00"
            }
        },
        NEW: {
            totalTax: "₹0.00",
            description: "New Tax Regime with standard deduction",
            breakdown: {
                incomeFromHouseProperty: "₹0.00",
                letOutProperty: "₹0.00",
                annualRent: "₹0.00",
                municipalTaxes: "₹0.00",
                netAnnualValue: "₹0.00",
                standardDeduction: "₹0.00",
                homeLoanInterest: "₹0.00"
            }
        }
    });

    // House rent details for old regime
    const [houseRentDetails, setHouseRentDetails] = useState([]);

    // Investment limits
    const [investmentLimits, setInvestmentLimits] = useState({
        "80C": { max: "₹1,50,000.00" },
        "80D": { max: "₹1,00,000.00" }
    });

    // Currency formatter (memoized)
    const currencyFormatter = new Intl.NumberFormat('en-IN', {
        style: 'currency',
        currency: 'INR',
        minimumFractionDigits: 2
    });

    const formatCurrency = (value = 0) => {
        if (value === null || value === undefined) return '₹0.00';
        if (typeof value === 'string' && value.includes('₹')) return value;
        return currencyFormatter.format(value);
    };

    // Helper to safely get section deductions
    const getSectionDeduction = (sectionWiseDeductions, sectionKey) => {
        if (!sectionWiseDeductions) return 0;

        // Handle both Map (from Java) and plain object (after JSON serialization)
        if (typeof sectionWiseDeductions.get === 'function') {
            // It's a Map
            return sectionWiseDeductions.get(sectionKey) || 0;
        } else if (typeof sectionWiseDeductions === 'object') {
            // It's a plain object after JSON serialization
            return sectionWiseDeductions[sectionKey] || 0;
        }
        return 0;
    };

    // Initialize employee and organization
    useEffect(() => {
        const decodedToken = getDecodedToken();
        const orgId = localStorage.getItem("organizationId");

        if (decodedToken?.sub && orgId) {
            setEmployeeId(decodedToken.sub);
            setOrganizationId(orgId);
        }
    }, []);


    // Fetch tax data when IDs are available
    useEffect(() => {
        if (employeeId && organizationId) {
            fetchTaxData();
        }
    }, [employeeId, organizationId]);

    // Fetch tax data from backend
    const fetchTaxData = async () => {
        try {
            setTaxLoading(true);

            // Fetch both tax regimes in parallel for faster loading
            await Promise.all([
                fetchOldTax(),
                fetchNewTax()
            ]);

            setTaxLoading(false);

            // Load non-critical data after tax data is available
            fetchHouseRentDetails();
            fetchInvestmentLimits();

        } catch (error) {
            console.error("Error fetching tax data:", error);
            setTaxLoading(false);
        } finally {
            setIsLoading(false);
        }
    };

    // Fetch old tax calculation
    const fetchOldTax = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/tax/calculate/old/${employeeId}/${financialYear}`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId
                    }
                }
            );

            if (response.data) {
                const oldTaxResult = response.data;
                console.log("Old Tax Result:", oldTaxResult); // Debug log

                // Safely extract section deductions
                const sectionDeductions = oldTaxResult.sectionWiseDeductions || {};
                const hraExemption = oldTaxResult.hraExemption || 0;
                const eightyCAmount = getSectionDeduction(sectionDeductions, "80C") || 0;
                const eightyDAmount = getSectionDeduction(sectionDeductions, "80D") || 0;

                setTaxData(prev => ({
                    ...prev,
                    OLD: {
                        totalTax: formatCurrency(oldTaxResult.taxPayable),
                        description: "Old Tax Regime with all deductions",
                        breakdown: {
                            houseRent: formatCurrency(hraExemption),
                            metroCity: "Pune (Metro)", // This should come from backend
                            epf: formatCurrency(eightyCAmount),
                            lifeInsurance: formatCurrency(eightyCAmount), // Using 80C for both for now
                            mediclaimParents: formatCurrency(eightyDAmount)
                        }
                    }
                }));
            }
        } catch (error) {
            console.error("Error fetching old tax:", error);
        }
    };

    // Fetch new tax calculation
    const fetchNewTax = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/tax/calculate/new/${employeeId}/${financialYear}`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId
                    }
                }
            );

            if (response.data) {
                const newTaxResult = response.data;
                console.log("New Tax Result:", newTaxResult); // Debug log

                setTaxData(prev => ({
                    ...prev,
                    NEW: {
                        totalTax: formatCurrency(newTaxResult.taxPayable),
                        description: "New Tax Regime with standard deduction",
                        breakdown: {
                            incomeFromHouseProperty: formatCurrency(0),
                            letOutProperty: formatCurrency(0),
                            annualRent: formatCurrency(0),
                            municipalTaxes: formatCurrency(0),
                            netAnnualValue: formatCurrency(0),
                            standardDeduction: formatCurrency(newTaxResult.standardDeduction || 0),
                            homeLoanInterest: formatCurrency(0)
                        }
                    }
                }));
            }
        } catch (error) {
            console.error("Error fetching new tax:", error);
        }
    };

    // Fetch house rent details
    const fetchHouseRentDetails = async () => {
        try {
            setExtraLoading(true);

            // This should be replaced with actual API endpoint
            // For now, using static data as placeholder
            const houseRentData = [
                {
                    period: "Sep 2025 - Nov 2025",
                    amount: "₹20,000.00/month",
                    total: "₹60,000.00",
                    city: "Pune",
                    type: "Metro"
                }
            ];

            setHouseRentDetails(houseRentData);
        } catch (error) {
            console.error("Error fetching house rent details:", error);
        } finally {
            setExtraLoading(false);
        }
    };

    // Fetch investment limits
    const fetchInvestmentLimits = async () => {
        try {
            // This should be replaced with actual API endpoint
            // For now, using static data as placeholder
            const limits = {
                "80C": { max: "₹1,50,000.00" },
                "80D": { max: "₹1,00,000.00" }
            };

            setInvestmentLimits(limits);
        } catch (error) {
            console.error("Error fetching investment limits:", error);
        }
    };

    // Save tax regime selection
    // const saveTaxRegime = async () => {
    //     try {
    //         let response;

    //         if (selectedRegime === 'OLD') {
    //             response = await axios.post(
    //                 `${GlobalConst.API_URL}/tax/calculate/old/save/${employeeId}/${financialYear}`,
    //                 {},
    //                 {
    //                     headers: {
    //                         Authorization: `Bearer ${localStorage.getItem("__t")}`,
    //                         organizationId: organizationId
    //                     }
    //                 }
    //             );

    //             // If user also wants to process under new regime
    //             if (isNewRegimeProcessed) {
    //                 await axios.post(
    //                     `${GlobalConst.API_URL}/tax/calculate/new/save/${employeeId}/${financialYear}`,
    //                     {},
    //                     {
    //                         headers: {
    //                             Authorization: `Bearer ${localStorage.getItem("__t")}`,
    //                             organizationId: organizationId
    //                         }
    //                     }
    //                 );
    //             }
    //         } else {
    //             response = await axios.post(
    //                 `${GlobalConst.API_URL}/tax/calculate/new/save/${employeeId}/${financialYear}`,
    //                 {},
    //                 {
    //                     headers: {
    //                         Authorization: `Bearer ${localStorage.getItem("__t")}`,
    //                         organizationId: organizationId
    //                     }
    //                 }
    //             );
    //         }

    //         const regimeName = selectedRegime === 'OLD' ? 'Old Tax Regime' : 'New Tax Regime';
    //         const message = isNewRegimeProcessed && selectedRegime === 'OLD'
    //             ? `Tax regime changed to ${regimeName}. You will also be processed under New Tax Regime for comparison.`
    //             : `Tax regime changed to ${regimeName}`;

    //         alert(message);
    //         console.log(`Selected Regime: ${selectedRegime}`);
    //         console.log(`Also process under New Regime: ${isNewRegimeProcessed}`);

    //         // Return to previous page
    //         navigate(-1);

    //     } catch (error) {
    //         console.error("Error saving tax regime:", error);
    //         alert("Failed to save tax regime. Please try again.");
    //     }
    // };


    const saveTaxRegime = async () => {
        if (isSaving) return; // prevent double click

        try {
            setIsSaving(true);

            if (selectedRegime === "OLD") {
                await axios.post(
                    `${GlobalConst.API_URL}/tax/calculate/old/save/${employeeId}/${financialYear}`,
                    {},
                    {
                        headers: {
                            Authorization: `Bearer ${localStorage.getItem("__t")}`,
                            organizationId: organizationId
                        }
                    }
                );

                if (isNewRegimeProcessed) {
                    await axios.post(
                        `${GlobalConst.API_URL}/tax/calculate/new/save/${employeeId}/${financialYear}`,
                        {},
                        {
                            headers: {
                                Authorization: `Bearer ${localStorage.getItem("__t")}`,
                                organizationId: organizationId
                            }
                        }
                    );
                }
            } else {
                await axios.post(
                    `${GlobalConst.API_URL}/tax/calculate/new/save/${employeeId}/${financialYear}`,
                    {},
                    {
                        headers: {
                            Authorization: `Bearer ${localStorage.getItem("__t")}`,
                            organizationId: organizationId
                        }
                    }
                );
            }

            successMsg(
                "Success",
                `Tax regime successfully changed to ${selectedRegime} regime`,
                true
            );

            setTimeout(() => {
                navigate(`/user-investment`);
            }, 1200);

        } catch (error) {
            console.error("Error saving tax regime:", error);

            errorMsg(
                "Error",
                error.response?.data?.message || "Failed to save tax regime. Please try again.",
                true
            );
        } finally {
            setIsSaving(false);
        }
    };


    // Show loader while tax data is loading
    if (taxLoading) {
        return <Loader />;
    }

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Compare Tax Regimes</title>
            </Helmet>

            <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Compare Tax Regimes</h5>
                <div className="d-flex align-items-center gap-3">

                    <span className="text-muted fs-7">
                        Select your preferred tax regime
                    </span>
                </div>
                <span className="text-muted">
                    Financial Year:{" "}
                    <span className="text-primary">
                        {financialYear - 1}-{financialYear}
                    </span>
                </span>

            </div>

            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-fluid py-2">
                    <div className="container-fluid d-flex align-items-start justify-content-start p-10 bg-white">
                        <div className="w-100" style={{ maxWidth: "1000px" }}>
                            <div className="card mb-6">
                                <div className="card-body">
                                    <h3 className="fw-bold text-gray-800 mb-6">Compare Tax Details</h3>

                                    {/* Tax Regime Selection Cards */}
                                    <div className="row g-6 mb-8">
                                        <div className="col-md-6">
                                            <div
                                                className={`card cursor-pointer h-100 ${selectedRegime === 'OLD' ? 'border-primary border-2' : 'border-light'}`}
                                                onClick={() => setSelectedRegime("OLD")}
                                                style={{ cursor: 'pointer' }}
                                            >
                                                <div className="card-body text-center p-6">
                                                    <div className="symbol symbol-60px mb-4 mx-auto">
                                                        <span className="symbol-label bg-light-primary">
                                                            <i className="ki-outline ki-shield-tick text-primary fs-2"></i>
                                                        </span>
                                                    </div>
                                                    <h4 className="fw-bold text-gray-800 mb-2">Old Tax Regime</h4>
                                                    <div className="text-muted fs-7 mb-4">
                                                        With all deductions and exemptions
                                                    </div>
                                                    <div className="fw-bold fs-1 text-primary mb-2">
                                                        {taxData.OLD.totalTax}
                                                    </div>
                                                    <div className="text-muted fs-8 mb-4">
                                                        Total Tax
                                                    </div>
                                                    {selectedRegime === 'OLD' && (
                                                        <div className="mt-2">
                                                            <span className="badge badge-success fs-7">
                                                                <i className="ki-outline ki-check fs-2 me-1"></i>
                                                                Selected
                                                            </span>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </div>

                                        <div className="col-md-6">
                                            <div
                                                className={`card cursor-pointer h-100 ${selectedRegime === 'NEW' ? 'border-success border-2' : 'border-light'}`}
                                                onClick={() => setSelectedRegime("NEW")}
                                                style={{ cursor: 'pointer' }}
                                            >
                                                <div className="card-body text-center p-6">
                                                    <div className="symbol symbol-60px mb-4 mx-auto">
                                                        <span className="symbol-label bg-light-success">
                                                            <i className="ki-outline ki-rocket text-success fs-2"></i>
                                                        </span>
                                                    </div>
                                                    <h4 className="fw-bold text-gray-800 mb-2">New Tax Regime</h4>
                                                    <div className="text-muted fs-7 mb-4">
                                                        Simplified with standard deduction
                                                    </div>
                                                    <div className="fw-bold fs-1 text-success mb-2">
                                                        {taxData.NEW.totalTax}
                                                    </div>
                                                    <div className="text-muted fs-8 mb-4">
                                                        Total Tax
                                                    </div>
                                                    {selectedRegime === 'NEW' && (
                                                        <div className="mt-2">
                                                            <span className="badge badge-success fs-7">
                                                                <i className="ki-outline ki-check fs-2 me-1"></i>
                                                                Selected
                                                            </span>
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Tax Breakdown Section */}
                                    <div className="card mt-6">
                                        <div className="card-header border-0 bg-light-primary">
                                            <h5 className="fw-bold text-gray-800 mb-0">
                                                {selectedRegime === 'OLD' ? 'Old Tax Regime Details' : 'New Tax Regime Details'}
                                            </h5>
                                        </div>
                                        <div className="card-body">
                                            {/* Summary Alert */}
                                            <div className="alert alert-info mb-6">
                                                <div className="d-flex align-items-center">
                                                    <div className="symbol symbol-40px me-3">
                                                        <i className="ki-outline ki-information-2 text-info fs-2"></i>
                                                    </div>
                                                    <div className="flex-grow-1">
                                                        <h6 className="alert-heading mb-1">
                                                            Total Tax: {taxData[selectedRegime].totalTax}
                                                        </h6>
                                                        <p className="mb-0">
                                                            {taxData[selectedRegime].description}
                                                        </p>
                                                    </div>
                                                </div>
                                            </div>

                                            {/* Dynamic Content based on Selected Regime */}
                                            {selectedRegime === 'OLD' ? (
                                                <div className="row">
                                                    {/* <div className="col-12">
                                                        <h6 className="fw-bold text-gray-800 mb-4 border-bottom pb-2">PARTICULARS</h6>
                                                    </div> */}

                                                    {/* House Rent Paid Details */}
                                                    {/* <div className="col-12 mb-6">
                                                        <div className="card bg-light-info">
                                                            <div className="card-header bg-transparent">
                                                                <h6 className="fw-bold text-gray-800 mb-0">House Rent Paid Details</h6>
                                                            </div>
                                                            <div className="card-body">
                                                                {extraLoading ? (
                                                                    <div className="text-center py-4">
                                                                        <div className="spinner-border text-primary" role="status">
                                                                            <span className="visually-hidden">Loading...</span>
                                                                        </div>
                                                                        <p className="text-muted mt-2">Loading details...</p>
                                                                    </div>
                                                                ) : houseRentDetails.length > 0 ? (
                                                                    <>
                                                                        <div className="table-responsive">
                                                                            <table className="table table-hover align-middle">
                                                                                <thead>
                                                                                    <tr className="fw-bold text-gray-700">
                                                                                        <th>Period</th>
                                                                                        <th>Amount</th>
                                                                                        <th>Total</th>
                                                                                        <th>City</th>
                                                                                        <th>Type</th>
                                                                                    </tr>
                                                                                </thead>
                                                                                <tbody>
                                                                                    {houseRentDetails.map((detail, index) => (
                                                                                        <tr key={index}>
                                                                                            <td className="fw-semibold">{detail.period}</td>
                                                                                            <td className="text-muted">{detail.amount}</td>
                                                                                            <td className="fw-bold text-primary">{detail.total}</td>
                                                                                            <td>
                                                                                                <span className="badge badge-light-primary">{detail.city}</span>
                                                                                            </td>
                                                                                            <td>
                                                                                                <span className="badge badge-light-info">{detail.type}</span>
                                                                                            </td>
                                                                                        </tr>
                                                                                    ))}
                                                                                </tbody>
                                                                            </table>
                                                                        </div>
                                                                        <div className="mt-3 text-muted fs-7">
                                                                            Location: {taxData.OLD.breakdown.metroCity}
                                                                        </div>
                                                                    </>
                                                                ) : (
                                                                    <div className="text-center py-4 text-muted">
                                                                        No house rent details available
                                                                    </div>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </div> */}

                                                    {/* 80C Investments */}
                                                    {/* <div className="col-12 mb-6">
                                                        <div className="card bg-light-success">
                                                            <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
                                                                <h6 className="fw-bold text-gray-800 mb-0">
                                                                    80C Investments (Max Limit: {investmentLimits["80C"].max})
                                                                </h6>
                                                                <span className="badge badge-success">Benefit Available</span>
                                                            </div>
                                                            <div className="card-body">
                                                                <div className="row">
                                                                    <div className="col-md-6 mb-4">
                                                                        <div className="d-flex justify-content-between align-items-center p-3 bg-white rounded">
                                                                            <div>
                                                                                <div className="fw-bold text-gray-800">Employee Provident Fund (Benefit)</div>
                                                                                <div className="text-muted fs-7">Tax saving investment</div>
                                                                            </div>
                                                                            <div className="fw-bold text-success fs-4">
                                                                                {taxData.OLD.breakdown.epf}
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                    <div className="col-md-6 mb-4">
                                                                        <div className="d-flex justify-content-between align-items-center p-3 bg-white rounded">
                                                                            <div>
                                                                                <div className="fw-bold text-gray-800">Life Insurance Premium</div>
                                                                                <div className="text-muted fs-7">Policy premium payments</div>
                                                                            </div>
                                                                            <div className="fw-bold text-success fs-4">
                                                                                {taxData.OLD.breakdown.lifeInsurance}
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div> */}

                                                    {/* 80D Exemptions */}
                                                    {/* <div className="col-12">
                                                        <div className="card bg-light-warning">
                                                            <div className="card-header bg-transparent d-flex justify-content-between align-items-center">
                                                                <h6 className="fw-bold text-gray-800 mb-0">
                                                                    80D Exemptions (Max Limit: {investmentLimits["80D"].max})
                                                                </h6>
                                                                <span className="badge badge-warning">Health Insurance</span>
                                                            </div>
                                                            <div className="card-body">
                                                                <div className="d-flex justify-content-between align-items-center p-3 bg-white rounded">
                                                                    <div>
                                                                        <div className="fw-bold text-gray-800">Medi Claim Policy for parents - 80D</div>
                                                                        <div className="text-muted fs-7">Health insurance for parents</div>
                                                                    </div>
                                                                    <div className="fw-bold text-warning fs-4">
                                                                        {taxData.OLD.breakdown.mediclaimParents}
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div> */}
                                                </div>
                                            ) : (
                                                <div className="row">
                                                    {/* <div className="col-12">
                                                        <h6 className="fw-bold text-gray-800 mb-4 border-bottom pb-2">Net Income / Loss from House Property</h6>
                                                    </div> */}

                                                    {/* Property Details */}
                                                    <div className="col-12">
                                                        <div className="card bg-light">
                                                            <div className="card-body">
                                                                {/* <div className="row mb-4">
                                                                    <div className="col-md-8">
                                                                        <h6 className="fw-bold text-gray-800 mb-3">Total Income / Loss from House Property</h6>
                                                                    </div>
                                                                    <div className="col-md-4 text-end">
                                                                        <span className="fw-bold fs-3 text-gray-800">1</span>
                                                                    </div>
                                                                </div> */}

                                                                <div className="table-responsive">
                                                                    {/* <table className="table table-bordered table-hover">
                                                                        <thead className="bg-light-primary">
                                                                            <tr>
                                                                                <th className="fw-bold text-gray-800">PARTICULARS</th>
                                                                                <th className="fw-bold text-gray-800 text-end">DECLARED AMOUNT</th>
                                                                            </tr>
                                                                        </thead>
                                                                        <tbody>
                                                                            <tr>
                                                                                <td className="fw-semibold text-gray-700">
                                                                                    <i className="ki-outline ki-home text-primary me-2"></i>
                                                                                    Net Income / Loss from Let Out Property - 1
                                                                                </td>
                                                                                <td className="text-end fw-bold">{taxData.NEW.breakdown.letOutProperty}</td>
                                                                            </tr>
                                                                            <tr>
                                                                                <td className="fw-semibold text-gray-700">
                                                                                    <i className="ki-outline ki-dollar text-success me-2"></i>
                                                                                    Annual Rent Received
                                                                                </td>
                                                                                <td className="text-end fw-bold">{taxData.NEW.breakdown.annualRent}</td>
                                                                            </tr>
                                                                            <tr>
                                                                                <td className="fw-semibold text-gray-700">
                                                                                    <i className="ki-outline ki-tax text-danger me-2"></i>
                                                                                    Municipal Taxes Paid
                                                                                </td>
                                                                                <td className="text-end fw-bold">{taxData.NEW.breakdown.municipalTaxes}</td>
                                                                            </tr>
                                                                            <tr>
                                                                                <td className="fw-semibold text-gray-700">
                                                                                    <i className="ki-outline ki-calculator text-info me-2"></i>
                                                                                    Net Annual Value
                                                                                </td>
                                                                                <td className="text-end fw-bold">{taxData.NEW.breakdown.netAnnualValue}</td>
                                                                            </tr>
                                                                            <tr>
                                                                                <td className="fw-semibold text-gray-700">
                                                                                    <i className="ki-outline ki-discount text-warning me-2"></i>
                                                                                    Standard Deduction (@ 30% of Net Annual Value)
                                                                                </td>
                                                                                <td className="text-end fw-bold">{taxData.NEW.breakdown.standardDeduction}</td>
                                                                            </tr>
                                                                            <tr>
                                                                                <td className="fw-semibold text-gray-700">
                                                                                    <i className="ki-outline ki-bank text-danger me-2"></i>
                                                                                    Interest Paid on Home Loan
                                                                                </td>
                                                                                <td className="text-end fw-bold">{taxData.NEW.breakdown.homeLoanInterest}</td>
                                                                            </tr>
                                                                        </tbody>
                                                                        <tfoot className="bg-light-success">
                                                                            <tr>
                                                                                <td className="fw-bold text-gray-800">
                                                                                    <i className="ki-outline ki-chart-line text-success me-2"></i>
                                                                                    Total Income / Loss from House Property
                                                                                </td>
                                                                                <td className="text-end fw-bold text-success fs-4">
                                                                                    {taxData.NEW.breakdown.incomeFromHouseProperty}
                                                                                </td>
                                                                            </tr>
                                                                        </tfoot>
                                                                    </table> */}
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            )}

                                            {/* Additional Processing Option
                                            {selectedRegime === 'OLD' && (
                                                <div className="mt-6">
                                                    <div className="form-check form-check-solid">
                                                        <input
                                                            className="form-check-input"
                                                            type="checkbox"
                                                            id="processBothRegimes"
                                                            checked={isNewRegimeProcessed}
                                                            onChange={(e) => setIsNewRegimeProcessed(e.target.checked)}
                                                            disabled={extraLoading}
                                                        />
                                                        <label className="form-check-label fw-semibold text-gray-800" htmlFor="processBothRegimes">
                                                            <i className="ki-outline ki-setting-3 text-primary me-2"></i>
                                                            You will also be processed based on the New Tax Regime
                                                        </label>
                                                    </div>
                                                    
                                                </div>
                                            )} */}
                                        </div>
                                    </div>

                                    {/* Action Buttons */}
                                    <div className="d-flex justify-content-between pt-5 border-top mt-6">
                                        <div className="d-flex gap-3">
                                            <button
                                                type="button"
                                                className="btn btn-lg btn-light"
                                                onClick={() => navigate(-1)}
                                                disabled={isLoading}
                                            >
                                                <i className="ki-outline ki-arrow-left me-2"></i>
                                                Back
                                            </button>
                                            <button
                                                type="button"
                                                className="btn btn-lg btn-secondary"
                                                onClick={() => {
                                                    navigate(-1);
                                                }}
                                                disabled={isLoading}
                                            >
                                                <i className="ki-outline ki-edit me-2"></i>
                                                Edit Declaration
                                            </button>
                                        </div>
                                        <div className="d-flex gap-3">
                                            <button
                                                type="button"
                                                className="btn btn-lg btn-light-danger"
                                                onClick={() => navigate(-1)}
                                                disabled={isLoading}
                                            >
                                                Cancel
                                            </button>
                                            <button
                                                type="button"
                                                className="btn btn-lg btn-primary"
                                                onClick={saveTaxRegime}
                                                disabled={isSaving || isLoading || taxLoading}
                                            >

                                               {isSaving ? (

                                                    <>
                                                        <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
                                                        Saving...
                                                    </>
                                                ) : (
                                                    <>
                                                        <i className="ki-outline ki-check-circle me-2"></i>
                                                        Save & Apply
                                                    </>
                                                )}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}