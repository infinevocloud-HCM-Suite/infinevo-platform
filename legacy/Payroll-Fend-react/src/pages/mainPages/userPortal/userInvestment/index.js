import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { getDecodedToken } from "../../../../shared/helpers/tokenHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";



const getCurrentFinancialYear = () => {
    const today = new Date();
    const month = today.getMonth() + 1;
    const year = today.getFullYear();

    // FY = END YEAR
    // April onwards → next year
    return month >= 4 ? year + 1 : year;
};


// const getDeclarationFinancialYear = () => {
//     const today = new Date();
//     const month = today.getMonth() + 1;
//     const year = today.getFullYear();

//     const currentFY = month >= 4 ? year + 1 : year;
//     return currentFY + 1; // UPCOMING financial year
// };


// Component for House Rent Details
const HouseRentDetails = ({ data, section6aItems }) => {
    if (!data || data.length === 0) return null;

    const formatMonth = (monthString) => {
        if (!monthString) return '';
        const [year, month] = monthString.split('-');
        const date = new Date(year, month - 1);
        return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
    };

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const calculateTotalAmount = (rent) => {
        if (!rent.fromMonth || !rent.toMonth || !rent.amountPerMonth) return 0;
        const fromMonth = new Date(rent.fromMonth);
        const toMonth = new Date(rent.toMonth);
        const months = (toMonth.getFullYear() - fromMonth.getFullYear()) * 12 +
            (toMonth.getMonth() - fromMonth.getMonth()) + 1;
        return rent.amountPerMonth * months;
    };

    const totalHouseRent = data.reduce((sum, rent) => sum + calculateTotalAmount(rent), 0);

    // Get max limit for house rent from section6aItems
    const houseRentLimitItem = section6aItems?.find(item =>
        item.type === "house_rent" ||
        item.typeFormatted?.toLowerCase().includes("house rent")
    );
    const maxLimitFormatted = houseRentLimitItem?.maxLimitFormatted || "As per tax rules";

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">
                        House Rent Details {maxLimitFormatted && `(Max Limit: ${maxLimitFormatted})`}
                    </h3>
                </div>
            </div>
            <div className="card-body pt-0">
                {data.map((rent, index) => (
                    <div key={rent.id || index} className="border rounded p-5 mb-4 bg-light">
                        <div className="row">
                            <div className="col-md-8">
                                <div className="d-flex align-items-center mb-3">
                                    <div className="symbol symbol-40px me-3">
                                        <span className="symbol-label bg-light-success">
                                            <i className="ki-outline ki-calendar-8 text-success fs-4"></i>
                                        </span>
                                    </div>
                                    <div>
                                        <div className="fw-bold fs-5 text-gray-800">
                                            {formatMonth(rent.fromMonth)} - {formatMonth(rent.toMonth)}
                                        </div>
                                        <div className="text-muted fs-7">{rent.address}</div>
                                    </div>
                                </div>

                                <div className="ms-5 ps-2">
                                    <div className="mb-2">
                                        <span className="text-muted fs-7">Monthly Rent: </span>
                                        <span className="fw-bold fs-6 text-gray-800">{formatCurrency(rent.amountPerMonth)}</span>
                                    </div>
                                    <div className="mb-2">
                                        <span className="text-muted fs-7">Landlord Name: </span>
                                        <span className="fw-bold fs-6 text-gray-800">{rent.landlordName || "Not Provided"}</span>
                                    </div>
                                    <div className="mb-2">
                                        <span className="text-muted fs-7">PAN: </span>
                                        <span className="fw-bold fs-6 text-gray-800">{rent.landlordPan || "Not Provided"}</span>
                                        <span className="badge bg-light-primary text-primary ms-2">
                                            • {rent.isMetro ? "Metro City" : "Non-Metro City"}
                                        </span>
                                    </div>
                                </div>
                            </div>
                            <div className="col-md-4 text-end">
                                <div className="bg-white rounded p-3 d-inline-block">
                                    <div className="text-muted fs-7 mb-1">Total Amount</div>
                                    <div className="fw-bold fs-2 text-success">
                                        {formatCurrency(calculateTotalAmount(rent))}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                ))}
                <div className="text-end mt-4">
                    <div className="fw-bold fs-5 text-gray-800">
                        Total House Rent: {formatCurrency(totalHouseRent)}
                    </div>
                </div>
            </div>
        </div>
    );
};

// Component for Home Loan Details
const HomeLoanDetails = ({ data, section6aItems }) => {
    if (!data || data.length === 0) return null;

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const totalPrincipal = data.reduce((sum, loan) => sum + (loan.principalPaid || 0), 0);
    const totalInterest = data.reduce((sum, loan) => sum + (loan.interestPaid || 0), 0);
    const totalAmount = totalPrincipal + totalInterest;

    // Get max limit for home loan from section6aItems
    const homeLoanLimitItem = section6aItems?.find(item =>
        item.type === "home_loan" ||
        item.typeFormatted?.toLowerCase().includes("home loan")
    );
    const maxLimitFormatted = homeLoanLimitItem?.maxLimitFormatted || "As per tax rules";

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">
                        Home Loan Details {maxLimitFormatted && `(Max Limit: ${maxLimitFormatted})`}
                    </h3>
                </div>
            </div>
            <div className="card-body pt-0">
                <div className="table-responsive">
                    <table className="table table-hover table-rounded border gs-5">
                        <thead className="bg-light">
                            <tr>
                                <th className="fw-semibold text-gray-700">Lender Name</th>
                                <th className="fw-semibold text-gray-700">Lender PAN</th>
                                <th className="fw-semibold text-gray-700 text-end">Principal Paid</th>
                                <th className="fw-semibold text-gray-700 text-end">Interest Paid</th>
                                <th className="fw-semibold text-gray-700 text-end">Total Amount</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.map((loan, index) => (
                                <tr key={loan.id || index}>
                                    <td className="text-gray-800">{loan.lenderName || "Not Provided"}</td>
                                    <td className="text-gray-800">{loan.lenderPan || "Not Provided"}</td>
                                    <td className="fw-bold text-gray-800 text-end">{formatCurrency(loan.principalPaid)}</td>
                                    <td className="fw-bold text-gray-800 text-end">{formatCurrency(loan.interestPaid)}</td>
                                    <td className="fw-bold text-gray-800 text-end">
                                        {formatCurrency((loan.principalPaid || 0) + (loan.interestPaid || 0))}
                                    </td>
                                </tr>
                            ))}
                            <tr className="bg-light-success">
                                <td colSpan="2" className="fw-bold text-gray-800">Totals</td>
                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(totalPrincipal)}</td>
                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(totalInterest)}</td>
                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(totalAmount)}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

// Component for Section 80C Investments
const Section80CInvestments = ({ data, section6aItems }) => {
    if (!data || data.length === 0) return null;

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const totalAmount = data.reduce((sum, item) => sum + (item.amount || 0), 0);

    // Get max limit for 80C from section6aItems
    const eightyCLimitItem = section6aItems?.find(item =>
        item.category === "80C" ||
        item.categoryFormatted?.includes("80C")
    );
    const maxLimitFormatted = eightyCLimitItem?.maxLimitFormatted || "₹1,50,000.00";

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">
                        80C Investments (Max Limit: {maxLimitFormatted})
                    </h3>
                </div>
            </div>
            <div className="card-body pt-0">
                <div className="table-responsive">
                    <table className="table table-hover table-rounded border gs-5">
                        <thead className="bg-light">
                            <tr>
                                <th className="fw-semibold text-gray-700" width="70%">Investment Type</th>
                                <th className="fw-semibold text-gray-700 text-end" width="30%">Amount</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.map((item, index) => (
                                <tr key={item.id || index}>
                                    <td className="text-gray-800">{item.typeFormatted || item.type}</td>
                                    <td className="fw-bold text-gray-800 text-end">{formatCurrency(item.amount)}</td>
                                </tr>
                            ))}
                            <tr className="bg-light-warning">
                                <td className="fw-bold text-gray-800">Total 80C Investments</td>
                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(totalAmount)}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

// Component for Section 80D Exemptions
const Section80DExemptions = ({ data, section6aItems }) => {
    if (!data || data.length === 0) return null;

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const totalAmount = data.reduce((sum, item) => sum + (item.amount || 0), 0);

    // Get max limit for 80D from section6aItems
    const eightyDLimitItem = section6aItems?.find(item =>
        item.category === "80D" ||
        item.categoryFormatted?.includes("80D")
    );
    const maxLimitFormatted = eightyDLimitItem?.maxLimitFormatted || "₹1,00,000.00";

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">
                        80D Exemptions (Max Limit: {maxLimitFormatted})
                    </h3>
                </div>
            </div>
            <div className="card-body pt-0">
                <div className="table-responsive">
                    <table className="table table-hover table-rounded border gs-5">
                        <thead className="bg-light">
                            <tr>
                                <th className="fw-semibold text-gray-700" width="70%">Exemption Type</th>
                                <th className="fw-semibold text-gray-700 text-end" width="30%">Amount</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.map((item, index) => (
                                <tr key={item.id || index}>
                                    <td className="text-gray-800">{item.typeFormatted || item.type}</td>
                                    <td className="fw-bold text-gray-800 text-end">{formatCurrency(item.amount)}</td>
                                </tr>
                            ))}
                            <tr className="bg-light-primary">
                                <td className="fw-bold text-gray-800">Total 80D Exemptions</td>
                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(totalAmount)}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

// Component for Other Investments
const OtherInvestments = ({ data, section6aItems }) => {
    if (!data || data.length === 0) return null;

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const totalAmount = data.reduce((sum, item) => sum + (item.amount || 0), 0);

    // Group by category
    const groupedData = data.reduce((acc, item) => {
        const category = item.categoryFormatted || item.category;
        if (!acc[category]) {
            acc[category] = {
                category: category,
                items: [],
                total: 0
            };
        }
        acc[category].items.push(item);
        acc[category].total += item.amount || 0;
        return acc;
    }, {});

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">
                        Other Investments & Exemptions
                    </h3>
                </div>
            </div>
            <div className="card-body pt-0">
                {Object.values(groupedData).map((group, groupIndex) => {
                    // Get max limit for this category from section6aItems
                    const categoryLimitItem = section6aItems?.find(item =>
                        item.categoryFormatted === group.category ||
                        item.category === group.category?.replace("Section ", "")
                    );
                    const maxLimitFormatted = categoryLimitItem?.maxLimitFormatted || "";

                    return (
                        <div key={groupIndex} className="mb-6">
                            <h5 className="fw-bold text-gray-800 mb-3">
                                {group.category} {maxLimitFormatted && `(Max Limit: ${maxLimitFormatted})`}
                            </h5>
                            <div className="table-responsive mb-4">
                                <table className="table table-hover table-rounded border gs-5">
                                    <thead className="bg-light">
                                        <tr>
                                            <th className="fw-semibold text-gray-700" width="70%">Investment/Exemption Type</th>
                                            <th className="fw-semibold text-gray-700 text-end" width="30%">Amount</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {group.items.map((item, index) => (
                                            <tr key={item.id || index}>
                                                <td className="text-gray-800">
                                                    <div>
                                                        <div className="fw-semibold">{item.typeFormatted || item.type}</div>
                                                    </div>
                                                </td>
                                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(item.amount)}</td>
                                            </tr>
                                        ))}
                                        <tr className="bg-light-success">
                                            <td className="fw-bold text-gray-800">Total {group.category}</td>
                                            <td className="fw-bold text-gray-800 text-end">{formatCurrency(group.total)}</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    );
                })}
                <div className="text-end mt-4">
                    <div className="fw-bold fs-5 text-gray-800">
                        Total Other Investments: {formatCurrency(totalAmount)}
                    </div>
                </div>
            </div>
        </div>
    );
};

// Component for Previous Employment
const PreviousEmploymentDetails = ({ data }) => {
    if (!data || data.length === 0) return null;

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const totalAmount = data.reduce((sum, item) => sum + (item.amount || 0), 0);

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">Previous Employment</h3>
                </div>
            </div>
            <div className="card-body pt-0">
                <div className="table-responsive">
                    <table className="table table-hover table-rounded border gs-5">
                        <thead className="bg-light">
                            <tr>
                                <th className="fw-semibold text-gray-700" width="70%">Particulars</th>
                                <th className="fw-semibold text-gray-700 text-end" width="30%">Amount</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.map((item, index) => (
                                <tr key={item.id || index}>
                                    <td className="text-gray-800">
                                        <div>
                                            <div className="fw-semibold">{item.typeFormatted || item.type}</div>
                                            <div className="text-muted fs-7">{item.name}</div>
                                        </div>
                                    </td>
                                    <td className="fw-bold text-gray-800 text-end">{formatCurrency(item.amount)}</td>
                                </tr>
                            ))}
                            <tr className="bg-light-info">
                                <td className="fw-bold text-gray-800">Previous Employment Total</td>
                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(totalAmount)}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

// Component for Other Income Sources
const OtherIncomeDetails = ({ data, section6aItems }) => {
    if (!data || data.length === 0) return null;

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const totalAmount = data.reduce((sum, item) => sum + (item.amount || 0), 0);

    // Get max limit for other incomes from section6aItems
    const otherIncomeLimitItems = section6aItems?.filter(item =>
        item.categoryFormatted?.includes("80TTA") ||
        item.categoryFormatted?.includes("80TTB")
    );

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">
                        Other Sources of Income
                    </h3>
                </div>
            </div>
            <div className="card-body pt-0">
                <div className="table-responsive">
                    <table className="table table-hover table-rounded border gs-5">
                        <thead className="bg-light">
                            <tr>
                                <th className="fw-semibold text-gray-700" width="70%">Income Type</th>
                                <th className="fw-semibold text-gray-700 text-end" width="30%">Amount</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.map((item, index) => {
                                // Find matching section6aItem for this type
                                const matchingItem = section6aItems?.find(sectionItem =>
                                    sectionItem.type === item.type ||
                                    sectionItem.typeFormatted === item.typeFormatted
                                );
                                const maxLimitFormatted = matchingItem?.maxLimitFormatted || "";

                                return (
                                    <tr key={item.id || index}>
                                        <td className="text-gray-800">
                                            <div>
                                                <div className="fw-semibold">{item.typeFormatted || item.type}</div>
                                                <div className="text-muted fs-7">{item.name}</div>
                                                {maxLimitFormatted && (
                                                    <div className="text-muted fs-8">Max Limit: {maxLimitFormatted}</div>
                                                )}
                                                {item.nameOfLender && (
                                                    <div className="text-muted fs-8">
                                                        Lender: {item.nameOfLender} (PAN: {item.panOfLender})
                                                    </div>
                                                )}
                                            </div>
                                        </td>
                                        <td className="fw-bold text-gray-800 text-end">{formatCurrency(item.amount)}</td>
                                    </tr>
                                );
                            })}
                            <tr className="bg-light-danger">
                                <td className="fw-bold text-gray-800">Total Other Income</td>
                                <td className="fw-bold text-gray-800 text-end">{formatCurrency(totalAmount)}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

// Component for Let Out Property
const LetOutPropertyDetails = ({ data, section6aItems }) => {
    if (!data || data.length === 0) return null;

    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const totalIncome = data.reduce((sum, property) => {
        const propertyDetailsAmount = property.propertyDetails?.reduce(
            (detailSum, detail) => detailSum + (detail.amount || 0),
            0
        ) || 0;
        return sum + (property.netIncomeLoss || 0) + propertyDetailsAmount;
    }, 0);

    // Get max limit for rental income from section6aItems
    const rentalIncomeLimitItem = section6aItems?.find(item =>
        item.type === "rental_income" ||
        item.typeFormatted?.toLowerCase().includes("rental income")
    );
    const maxLimitFormatted = rentalIncomeLimitItem?.maxLimitFormatted || "As per tax rules";

    return (
        <div className="card mb-8">
            <div className="card-header border-0">
                <div className="card-title">
                    <h3 className="fw-bold text-gray-800">
                        Let Out Property Details {maxLimitFormatted && `(Max Limit: ${maxLimitFormatted})`}
                    </h3>
                </div>
            </div>
            <div className="card-body pt-0">
                {data.map((property, index) => {
                    const propertyDetailsAmount = property.propertyDetails?.reduce(
                        (sum, detail) => sum + (detail.amount || 0),
                        0
                    ) || 0;
                    const propertyTotal = (property.netIncomeLoss || 0) + propertyDetailsAmount;

                    return (
                        <div key={property.id || index} className="border rounded p-5 mb-4 bg-light">
                            <div className="row">
                                <div className="col-md-8">
                                    <div className="d-flex align-items-center mb-3">
                                        <div className="symbol symbol-40px me-3">
                                            <span className="symbol-label bg-light-info">
                                                <i className="ki-outline ki-wallet text-info fs-4"></i>
                                            </span>
                                        </div>
                                        <div>
                                            <div className="fw-bold fs-5 text-gray-800">{property.propertyName}</div>
                                            <div className="text-muted fs-7">{property.address}</div>
                                        </div>
                                    </div>

                                    <div className="ms-5 ps-2">
                                       
                                        {property.propertyDetails && property.propertyDetails.length > 0 && (
                                            <div className="mt-3">
                                                <div className="fw-semibold text-gray-700 mb-2">Property Details:</div>
                                                {property.propertyDetails.map((detail, detailIndex) => (
                                                    <div key={detail.id || detailIndex} className="mb-2">
                                                        <span className="text-muted fs-7">
                                                            {detail.type === "annual_rent" ? "Annual Rent: " :
                                                                detail.type === "municipal_tax" ? "Municipal Taxes: " :
                                                                    detail.type === "interest_on_loan" ? "Interest on Loan: " :
                                                                        detail.type === "INTEREST_ON_LOAN" ? "Interest on Loan: " :
                                                                            detail.type === "ANNUAL_RENT" ? "Annual Rent: " :
                                                                                detail.type === "MUNICIPAL_TAX" ? "Municipal Tax: " :
                                                                                    detail.type}:
                                                        </span>
                                                        <span className="fw-bold fs-6 text-gray-800">
                                                            {formatCurrency(detail.amount)}
                                                        </span>
                                                        {detail.nameOfLender && (
                                                            <div className="text-muted fs-8 ms-2">
                                                                (Lender: {detail.nameOfLender}, PAN: {detail.panOfLender})
                                                            </div>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>
                                </div>
                               
                            </div>
                        </div>
                    );
                })}
              
            </div>
        </div>
    );
};

// Component for Overall Tax Summary
// Component for Overall Tax Summary
const TaxSummarySection = ({ declarationData, fiscalYear, setFiscalYear, fetchDeclarationData, employeeId }) => {
    const formatCurrency = (amount) => {
        if (!amount && amount !== 0) return '₹0.00';
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 2
        }).format(amount);
    };

    const taxSummary = declarationData?.taxSummaries && declarationData.taxSummaries.length > 0
        ? declarationData.taxSummaries[0]
        : {
            netTaxableIncome: 0,
            totalTax: 0,
            taxToBePaid: 0,
            status: declarationData?.statusFormatted || "DECLARED"
        };

    const navigate = useNavigate();
    const [showPoiLockedModal, setShowPoiLockedModal] = useState(false);

    const handleEditClick = () => {
        if (declarationData?.canAllowEdit === true) {
            navigate('/user-investment-declaration');
        } else {
            setShowPoiLockedModal(true);
        }
    };

    const handleCloseModal = () => {
        setShowPoiLockedModal(false);
    };

    return (
        <>
            <div className="card mb-8">
                <div className="card-header border-0">
                    <div className="card-title d-flex align-items-center justify-content-between w-100">
                        <h3 className="fw-bold text-gray-800 mb-0">Overall Tax Summary</h3>
                        <div className="d-flex align-items-center gap-3">
                            <button
                                className="btn btn-icon btn-light-primary btn-sm"
                                onClick={handleEditClick}
                                title="Edit Declaration"
                            >
                                <i className="ki-outline ki-pencil fs-2"></i>
                            </button>
                        </div>
                    </div>

                    <div className="card-toolbar">
                        <div className="d-flex align-items-center gap-3">
                            <div className="d-flex align-items-center">
                                <span className="text-muted me-3">Financial Year:</span>
                                <select
                                    className="form-select form-select-sm w-125px"
                                    value={fiscalYear}
                                    // disabled
                                >
                                    <option value={fiscalYear}>
                                        {fiscalYear - 1}-{fiscalYear}
                                    </option>
                                </select>

                            </div>
                        </div>
                    </div>
                </div>
                
            </div>

            {/* POI Locked Modal */}
            {showPoiLockedModal && (
                <div className="modal fade show d-block" tabIndex="-1" role="dialog" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
                    <div className="modal-dialog modal-dialog-centered" role="document">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title fw-bold text-gray-800">Edit Not Allowed</h5>
                                <button type="button" className="btn-close" onClick={handleCloseModal}></button>
                            </div>
                            <div className="modal-body">
                                <div className="d-flex align-items-center mb-6">
                                    <div className="symbol symbol-60px me-4">
                                        <span className="symbol-label bg-light-danger">
                                            <i className="ki-outline ki-shield-tick text-danger fs-2"></i>
                                        </span>
                                    </div>
                                    <div className="flex-grow-1">
                                        <h4 className="fw-bold text-gray-800 mb-2">Investment Declaration is Locked</h4>
                                        <p className="text-muted fs-6 mb-0">
                                            You cannot edit or declare the investment declaration because POI verification is in progress or completed.
                                            Please contact your HR department for any changes.
                                        </p>
                                    </div>
                                </div>
                            </div>
                            <div className="modal-footer">
                                <button type="button" className="btn btn-light" onClick={handleCloseModal}>
                                    Cancel
                                </button>
                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={() => {
                                        handleCloseModal();
                                        // Optional: Add logic to contact HR or navigate to help page
                                    }}
                                >
                                    OK
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
};

// Component for Tax Regime Section
const TaxRegimeSection = ({ declarationData }) => {
    const navigate = useNavigate();
    const [showTaxRegimeLockedModal, setShowTaxRegimeLockedModal] = useState(false);

    const handleChangeTaxRegime = () => {
        if (declarationData?.canChangeTaxRegime === true) {
            navigate('/compare-tax-regimes');
        } else {
            setShowTaxRegimeLockedModal(true);
        }
    };

    const handleCloseTaxRegimeModal = () => {
        setShowTaxRegimeLockedModal(false);
    };

    return (
        <>
            <div className="card mb-8">
                <div className="card-body">
                    <div className="d-flex align-items-center mb-6">
                        <div className="symbol symbol-50px me-4">
                            <span className="symbol-label bg-light-primary">
                                <i className="ki-outline ki-home-2 text-primary fs-2"></i>
                            </span>
                        </div>
                        <div className="flex-grow-1">
                            <h4 className="fw-bold text-gray-800 mb-1">
                                Tax Regime : {declarationData?.taxRegimeFormatted }
                            </h4>
                            {/* <div className="text-muted fs-7">View your declared investments and exemptions</div> */}
                        </div>
                        {/* <button
                            className="btn btn-light-primary btn-sm"
                            onClick={handleChangeTaxRegime}
                        >
                            Change Tax Regime
                        </button> */}
                    </div>
                </div>
            </div>

            {/* Tax Regime Locked Modal */}
            {showTaxRegimeLockedModal && (
                <div className="modal fade show d-block" tabIndex="-1" role="dialog" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
                    <div className="modal-dialog modal-dialog-centered" role="document">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title fw-bold text-gray-800">Tax Regime Change Not Allowed</h5>
                                <button type="button" className="btn-close" onClick={handleCloseTaxRegimeModal}></button>
                            </div>
                            <div className="modal-body">
                                <div className="d-flex align-items-center mb-6">
                                    <div className="symbol symbol-60px me-4">
                                        <span className="symbol-label bg-light-warning">
                                            <i className="ki-outline ki-lock text-warning fs-2"></i>
                                        </span>
                                    </div>
                                    <div className="flex-grow-1">
                                        <h4 className="fw-bold text-gray-800 mb-2">Tax Regime Change is Restricted</h4>
                                        <p className="text-muted fs-6 mb-0">
                                            You cannot change the tax regime at this time. The tax regime selection period has ended
                                            or your declaration is under review. Please contact your HR department for assistance.
                                        </p>
                                    </div>
                                </div>
                            </div>
                            <div className="modal-footer">
                                <button type="button" className="btn btn-light" onClick={handleCloseTaxRegimeModal}>
                                    Close
                                </button>
                                <button
                                    type="button"
                                    className="btn btn-primary"
                                    onClick={() => {
                                        handleCloseTaxRegimeModal();
                                        // Optional: Add logic to contact HR
                                    }}
                                >
                                    OK
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
};

// Main Component
export default function UserInvestment() {
    const [activeTab, setActiveTab] = useState("declaration");
    const [isLoading, setIsLoading] = useState(true);
    const [declarationData, setDeclarationData] = useState(null);
    const [employeeId, setEmployeeId] = useState("");
    const navigate = useNavigate();

    const [fiscalYear, setFiscalYear] = useState(getCurrentFinancialYear());
    // const [fiscalYear, setFiscalYear] = useState(getDeclarationFinancialYear());


    // Initialize employee ID from token
    useEffect(() => {
        const decodedToken = getDecodedToken();
        if (decodedToken?.sub) {
            setEmployeeId(decodedToken.sub);
        }
    }, []);


    useEffect(() => {
        if (!employeeId || !fiscalYear) return;

        fetchDeclarationData(employeeId);
    }, [employeeId, fiscalYear]);


    // Fetch declaration data from API
    const fetchDeclarationData = async (empId) => {
        try {
            setIsLoading(true);
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/employee-it-declarations/${empId}/${fiscalYear}`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id"
                    }
                }
            );

            if (response.data && response.data.data) {
                setDeclarationData(response.data.data);
            }
        } catch (error) {
            console.error("Error fetching declaration data:", error);
        } finally {
            setIsLoading(false);
        }
    };

    // Download PDF function
    const downloadForm12BB = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/employee-it-declarations/${employeeId}/${fiscalYear}/form-12bb`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: localStorage.getItem("organizationId") || "default-org-id"
                    },
                    responseType: 'blob'
                }
            );

            // Create a blob from the PDF stream
            const blob = new Blob([response.data], { type: 'application/pdf' });
            const url = window.URL.createObjectURL(blob);

            // Create a link element
            const link = document.createElement('a');
            link.href = url;
            link.download = `Form12BB_${employeeId}_${fiscalYear}.pdf`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (error) {
            console.error("Error downloading Form 12BB:", error);
            alert("Failed to download Form 12BB. Please try again.");
        }
    };

    // Filter section data based on categories
    const section80CData = declarationData?.section6aDeclarations?.filter(
        item => item.category === "80c" || item.category === "80C" ||
            item.categoryFormatted?.includes("80C") ||
            (item.category && item.category.toLowerCase().includes("80c"))
    ) || [];

    const section80DData = declarationData?.section6aDeclarations?.filter(
        item => item.category === "80d" || item.category === "80D" ||
            item.categoryFormatted?.includes("80D") ||
            (item.category && item.category.toLowerCase().includes("80d"))
    ) || [];

    const otherInvestmentsData = declarationData?.section6aDeclarations?.filter(
        item => {
            const category = item.category ? item.category.toLowerCase() : '';
            const categoryFormatted = item.categoryFormatted ? item.categoryFormatted.toLowerCase() : '';
            return !(category.includes("80c") ||
                category.includes("80d") ||
                categoryFormatted.includes("80c") ||
                categoryFormatted.includes("80d"));
        }
    ) || [];

    if (isLoading) {
        return <Loader />;
    }

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Investment View</title>
            </Helmet>

            <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
                <h5 className="mb-0 fw-semibold">Investment Declaration</h5>
                {/* <span className="text-muted">
                    Financial Year: <span className="text-primary">{declarationData?.fiscalYear || fiscalYear}-{(declarationData?.fiscalYear || fiscalYear) + 1}</span>
                </span> */}

            </div>


            <div className="d-flex flex-column flex-lg-row flex-column-fluid">
                <div className="d-flex flex-column flex-lg-row-fluid py-2">
                    <div className="container-fluid d-flex align-items-start justify-content-start p-10 bg-white">
                        <div className="w-100" style={{ maxWidth: "1200px" }}>
                            {/* Header Section with Tabs */}
                            <div className="card mb-6">
                                <div className="card-body p-0">
                                    {/* Tabs Header */}
                                    <div className="d-flex flex-column">
                                        <ul className="nav nav-stretch nav-line-tabs nav-line-tabs-2x border-transparent fs-5 fw-bold">
                                            <li className="nav-item">
                                                <button
                                                    className={`nav-link text-active-primary cursor-pointer ${activeTab === "declaration" ? "active" : ""}`}
                                                    onClick={() => setActiveTab("declaration")}
                                                    style={{
                                                        border: "none",
                                                        borderBottom: activeTab === "declaration" ? "2px solid #009EF7" : "none",
                                                        color: activeTab === "declaration" ? "#009EF7" : "#5E6278",
                                                        padding: "1rem 1.5rem",
                                                        fontWeight: activeTab === "declaration" ? "600" : "400",
                                                        backgroundColor: "transparent"
                                                    }}
                                                >
                                                    <i className="ki-outline ki-home-2 fs-2 me-2"></i>
                                                    Investment Declaration
                                                </button>
                                            </li>
                                            <li className="nav-item">
                                                <button
                                                    className={`nav-link text-active-primary cursor-pointer ${activeTab === "proofs" ? "active" : ""}`}
                                                    onClick={() => navigate('/user-investment/proof')}
                                                    style={{
                                                        border: "none",
                                                        borderBottom: activeTab === "proofs" ? "2px solid #009EF7" : "none",
                                                        color: activeTab === "proofs" ? "#009EF7" : "#5E6278",
                                                        padding: "1rem 1.5rem",
                                                        fontWeight: activeTab === "proofs" ? "600" : "400",
                                                        backgroundColor: "transparent"
                                                    }}
                                                >
                                                    <i className="ki-outline ki-document fs-2 me-2"></i>
                                                    Proof of Investments
                                                </button>
                                            </li>
                                        </ul>

                                        {/* Tab Content */}
                                        <div className="tab-content">
                                            {/* Investment Declaration Tab */}
                                            {activeTab === "declaration" && (
                                                <div className="tab-pane fade show active p-8">
                                                    {/* Overall Tax Summary */}
                                                    <TaxSummarySection
                                                        declarationData={declarationData}
                                                        fiscalYear={fiscalYear}
                                                        setFiscalYear={setFiscalYear}
                                                        fetchDeclarationData={fetchDeclarationData}
                                                        employeeId={employeeId}
                                                    />

                                                    {/* Tax Regime Section */}
                                                    {declarationData && <TaxRegimeSection declarationData={declarationData} />}

                                                    {/* House Rent Details - Only show if user declared */}
                                                    {declarationData?.isStayingInRentedHouse &&
                                                        declarationData?.houseRentDeclarations &&
                                                        declarationData.houseRentDeclarations.length > 0 && (
                                                            <HouseRentDetails
                                                                data={declarationData.houseRentDeclarations}
                                                                section6aItems={declarationData.section6aItems}
                                                            />
                                                        )}

                                                    {/* Home Loan Details - Only show if user declared */}
                                                    {declarationData?.isRepayingSelfOccupiedLoan &&
                                                        declarationData?.homeLoanDeclarations &&
                                                        declarationData.homeLoanDeclarations.length > 0 && (
                                                            <HomeLoanDetails
                                                                data={declarationData.homeLoanDeclarations}
                                                                section6aItems={declarationData.section6aItems}
                                                            />
                                                        )}

                                                    {/* 80C Investments - Only show if user declared */}
                                                    {section80CData.length > 0 && (
                                                        <Section80CInvestments
                                                            data={section80CData}
                                                            section6aItems={declarationData?.section6aItems}
                                                        />
                                                    )}

                                                    {/* 80D Exemptions - Only show if user declared */}
                                                    {section80DData.length > 0 && (
                                                        <Section80DExemptions
                                                            data={section80DData}
                                                            section6aItems={declarationData?.section6aItems}
                                                        />
                                                    )}

                                                    {/* Other Investments & Exemptions - Only show if user declared */}
                                                    {otherInvestmentsData.length > 0 && (
                                                        <OtherInvestments
                                                            data={otherInvestmentsData}
                                                            section6aItems={declarationData?.section6aItems}
                                                        />
                                                    )}

                                                    {/* Previous Employment - Only show if user declared */}
                                                    {declarationData?.previousEmploymentDeclarations &&
                                                        declarationData.previousEmploymentDeclarations.length > 0 && (
                                                            <PreviousEmploymentDetails data={declarationData.previousEmploymentDeclarations} />
                                                        )}

                                                    {/* Other Sources of Income - Only show if user declared */}
                                                    {declarationData?.otherIncomesDeclarations &&
                                                        declarationData.otherIncomesDeclarations.length > 0 && (
                                                            <OtherIncomeDetails
                                                                data={declarationData.otherIncomesDeclarations}
                                                                section6aItems={declarationData?.section6aItems}
                                                            />
                                                        )}

                                                    {/* Let Out Property - Only show if user declared */}
                                                    {declarationData?.hasLetOutProperty &&
                                                        declarationData?.letOutPropertyDeclarations &&
                                                        declarationData.letOutPropertyDeclarations.length > 0 && (
                                                            <LetOutPropertyDetails
                                                                data={declarationData.letOutPropertyDeclarations}
                                                                section6aItems={declarationData?.section6aItems}
                                                            />
                                                        )}

                                                    {/* Download Button */}
                                                    {/* <div className="d-flex justify-content-end mt-8 pt-5 border-top">
                                                        <button
                                                            className="btn btn-primary btn-lg"
                                                            onClick={downloadForm12BB}
                                                        >
                                                            <i className="ki-outline ki-download fs-2 me-2"></i>
                                                            Form 12BB - Employee Investment Declaration 💸 Download
                                                        </button>
                                                    </div> */}
                                                </div>
                                            )}

                                            {/* Proof of Investments Tab */}
                                            {activeTab === "proofs" && (
                                                <div className="tab-pane fade show active p-8">
                                                    <div className="card">
                                                        <div className="card-body">
                                                            <div className="text-center py-10">
                                                                <div className="symbol symbol-100px mb-4">
                                                                    <span className="symbol-label bg-light-warning">
                                                                        <i className="ki-outline ki-document text-warning fs-1"></i>
                                                                    </span>
                                                                </div>
                                                                <h3 className="fw-bold text-gray-800 mb-2">Proof of Investments</h3>
                                                                <p className="text-muted fs-6">Navigate to Proof of Investments section to upload and manage your proofs.</p>
                                                                <button
                                                                    className="btn btn-primary mt-4"
                                                                    onClick={() => navigate('/user-investment/proof')}
                                                                >
                                                                    Go to Proof Upload
                                                                </button>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            )}
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