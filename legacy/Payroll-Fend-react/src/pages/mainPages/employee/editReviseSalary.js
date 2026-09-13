import React, { useState, useEffect } from "react";
import { Formik, Form, Field } from "formik";
import * as Yup from "yup";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { Tooltip, Row, Col, Card, Typography, Divider } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from "react-router-dom";

const { Title, Text } = Typography;
const RequiredStar = () => <span className="text-danger">*</span>;


const getEarningAmount = (earnings, code) => {
    const codeLower = code.toLowerCase();
    const item = earnings?.find(e => {
        const name = (e.earningCode || e.name || "").toLowerCase();
        if (codeLower === "basic") {
            return name.includes("basic");
        }
        return name === codeLower || name.includes(codeLower);
    });
    return item ? item.amount : 0;
};

// Safe numeric coercion: keeps an explicit 0, never returns NaN/null/undefined.
const toNum = (v, fallback = 0) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : fallback;
};

// Sanitize a percentage text input: allow digits and a single decimal point.
const sanitizeDecimal = (raw) =>
    String(raw)
        .replace(/[^0-9.]/g, "")
        .replace(/(\..*)\./g, "$1");

// Tolerance (in rupees) used when checking that the salary components
// reconcile against the entered Annual CTC. Allows for rounding noise.
const CTC_RECONCILE_TOLERANCE = 2;

// Convert Basic / HRA between % and flat so the rupee amount stays continuous
// (lossless to the paisa) when the user toggles the calculation type.
//   basic -> % of Annual CTC
//   hra   -> % of Basic (annual)
// (Conveyance and Fixed are a residual pair in this screen and keep their own
//  bespoke toggle handlers, so they are not routed through here.)
const buildCalcTypePatch = (component, newType, sd) => {
    const annualCTC = toNum(sd.annualCTC);
    const basicAnnual = toNum(sd.basic);

    const currentMonthly = {
        basic: toNum(sd.basicMonthly),
        hra: toNum(sd.hraMonthly),
    }[component];

    const currentAnnual = {
        basic: toNum(sd.basic),
        hra: toNum(sd.hra),
    }[component];

    const patch = { [`${component}CalculationType`]: newType };

    const round2 = (n) => Math.round(toNum(n) * 100) / 100;
    const roundPct = (n) => Math.round(toNum(n) * 1e6) / 1e6;

    if (newType === "flat") {
        patch[`${component}FlatAmount`] = round2(currentMonthly);
    } else {
        if (component === "basic") {
            patch.basicPercent = annualCTC > 0 ? roundPct((currentAnnual / annualCTC) * 100) : 0;
        } else if (component === "hra") {
            patch.hraPercent = basicAnnual > 0 ? roundPct((currentAnnual / basicAnnual) * 100) : 0;
        }
    }

    return patch;
};


const EditReviseSalary = () => {
    const { id } = useParams(); // Get employee ID from URL params
    const [employeeData, setEmployeeData] = useState(null);
    const [salaryData, setSalaryData] = useState(null);
    const [basicDetails, setBasicDetails] = useState(null);
    const [earnings, setEarnings] = useState([]);
    const [loading, setLoading] = useState(true);
    const [signingIn, setSigningIn] = useState(false);
    const [earningsLoaded, setEarningsLoaded] = useState(false);
    const [statutoryConfig, setStatutoryConfig] = useState(null);
    const [employeeBasicDetails, setEmployeeBasicDetails] = useState(null);
    const [masterEarnings, setMasterEarnings] = useState([]); // For storing actual earning IDs

    const organizationId = localStorage.getItem("organizationId") || "default-org-id";
    const token = localStorage.getItem("__t");

    const navigate = useNavigate();


    // Fetch all necessary data when component mounts
    useEffect(() => {
        if (id) {
            fetchSalaryData();
            fetchEarnings();
            fetchOrgStatutoryConfig();
            fetchEmployeeBasicDetails();
            fetchMasterEarnings(); // Fetch actual earning IDs
        }
    }, [id, organizationId]);

    const validationSchema = Yup.object().shape({
        revisionType: Yup.string()
            .oneOf(['percentage', 'amount'], 'Please select a revision type')
            .required('Revision type is required'),
        percentage: Yup.number()
            .when('revisionType', {
                is: 'percentage',
                then: schema => schema
                    .typeError("Percentage must be a number")
                    .required("Percentage is required")
                    .min(1, "Must be at least 1%")
                    .max(100, "Cannot exceed 100%")
            }),
        revisedAnnualCTC: Yup.number()
            .typeError("Annual CTC must be a number")
            .required("Annual CTC is required")
            .positive("Annual CTC must be greater than 0")
            .min(1, "Annual CTC must be at least ₹1"),
        revisedSalaryEffectiveFrom: Yup.string()
            .required("Effective from date is required"),

        salaryDetails: Yup.object().shape({
            AnnualCTCBeforeBonus: Yup.number()
                .typeError("Annual CTC must be a number")
                .required("Annual CTC is required")
                .positive("Annual CTC must be greater than 0"),
            bonus: Yup.number()
                .typeError("Bonus must be a number")
                .min(0, "Bonus cannot be negative")
                .notRequired(),
            bonusFrequency: Yup.string()
                .oneOf(["", "yearly", "half-yearly", "quarterly"], "Invalid frequency")
                .notRequired(),
            includeBonusInCTC: Yup.boolean().notRequired(),

            basicPercent: Yup.number()
                .when("basicCalculationType", {
                    is: "percentage",
                    then: schema => schema
                        .required("Basic % is required")
                        .min(1)
                        .max(100),
                    otherwise: schema => schema.notRequired()
                }),

            basicFlatAmount: Yup.number()
                .when("basicCalculationType", {
                    is: "flat",
                    then: schema => schema
                        .required("Basic flat amount is required")
                        .min(0),
                    otherwise: schema => schema.notRequired()
                }),

            hraPercent: Yup.number()
                .when("hraCalculationType", {
                    is: "percentage",
                    then: schema => schema
                        .required("HRA % is required")
                        .min(1)
                        .max(100),
                    otherwise: schema => schema.notRequired()
                }),

            hraFlatAmount: Yup.number()
                .when("hraCalculationType", {
                    is: "flat",
                    then: schema => schema
                        .required("HRA flat amount is required")
                        .min(0),
                    otherwise: schema => schema.notRequired()
                }),

            conveyanceFlatAmount: Yup.number()
                .when("conveyanceCalculationType", {
                    is: "flat",
                    then: schema => schema
                        .required("Conveyance flat amount is required")
                        .min(0),
                    otherwise: schema => schema.notRequired()
                }),

            fixedFlatAmount: Yup.number()
                .typeError("Fixed allowance amount must be a number")
                .when("fixedCalculationType", {
                    is: "flat",
                    then: (schema) =>
                        schema
                            .required("Fixed allowance amount is required")
                            .min(0, "Must be 0 or greater"),
                    otherwise: (schema) => schema.notRequired(),
                }),
        })
            .test(
                "components-not-all-zero",
                "Salary components cannot all be zero",
                (sd) => {
                    if (!sd) return true;
                    if (toNum(sd.annualCTC) <= 0) return true;
                    const totalEarnings =
                        toNum(sd.basic) +
                        toNum(sd.hra) +
                        toNum(sd.conveyanceDisplay) +
                        toNum(sd.fixedAllowance);
                    return totalEarnings > 0;
                }
            )
            .test(
                "residual-not-negative",
                "Basic + HRA exceed the Annual CTC, leaving a negative residual. Reduce them.",
                (sd) => {
                    if (!sd) return true;
                    return toNum(sd.fixedAllowance) >= 0 && toNum(sd.conveyanceDisplay) >= 0;
                }
            ),
    });

    // Fetch current salary data
    const fetchSalaryData = async () => {
        try {
            setLoading(true);
            const response = await axios.get(`${GlobalConst.API_URL}/api/employees-portal/employee-profile`, {
                params: { employeeId: id },
                headers: {
                    Authorization: `Bearer ${token}`,
                    organizationId: organizationId
                },
            });

            console.log("Salary API Response:", response.data);

            if (response.data && response.data.data) {
                const employeeData = response.data.data;
                setEmployeeData(employeeData);
                setSalaryData(employeeData.ctc || {});
                setBasicDetails(employeeData.basicDetails || {});
            } else {
                errorMsg("Error", "No salary data found", true);
            }
        } catch (error) {
            console.error("Error fetching salary data:", error);
            errorMsg("Error", "Failed to load salary details", true);
        } finally {
            setLoading(false);
        }
    };

    // Fetch earnings data
    const fetchEarnings = async () => {
        setEarningsLoaded(false);
        try {
            const { data } = await axios.get(
                `${GlobalConst.API_URL}/api/employees/salary/preview/${id}`,
                { headers: { Authorization: `Bearer ${token}`, organizationId } }
            );

            console.log("📊 Preview response:", data);

            const payload = data?.employee || data?.data;
            const earningsData = payload?.earnings || [];

            setEarnings(earningsData);
            setEarningsLoaded(true);
        } catch (err) {
            console.error("❌ Failed to fetch earnings:", err);
            errorMsg("Error", "Failed to fetch earnings data", false);
            setEarnings([]);
            setEarningsLoaded(true);
        }
    };

    // Fetch master earnings data to get actual IDs
    const fetchMasterEarnings = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/earnings`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        organizationId: organizationId,
                    },
                }
            );

            if (response.data && response.data.data) {
                setMasterEarnings(response.data.data);
                console.log("Master Earnings:", response.data.data);
            }
        } catch (error) {
            console.error("Failed to fetch master earnings:", error);
            // Try alternative endpoint if the first one fails
            try {
                const altResponse = await axios.get(
                    `${GlobalConst.API_URL}/api/employees/salary/components`,
                    {
                        headers: {
                            Authorization: `Bearer ${token}`,
                            organizationId: organizationId,
                        },
                    }
                );
                if (altResponse.data && altResponse.data.earnings) {
                    setMasterEarnings(altResponse.data.earnings);
                }
            } catch (altError) {
                console.error("Failed to fetch alternative earnings:", altError);
            }
        }
    };

    // Fetch organization statutory configuration
    const fetchOrgStatutoryConfig = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/employees/statutory/config`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        organizationId: organizationId,
                    },
                }
            );

            if (response.data && response.data.code === 0 && response.data.statutoryConfig) {
                setStatutoryConfig(response.data.statutoryConfig);
            }
        } catch (error) {
            console.error("Failed to fetch organization statutory config:", error);
        }
    };

    // Fetch employee basic details to check EPF/ESI eligibility
    const fetchEmployeeBasicDetails = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/employees/${id}`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        organizationId: organizationId,
                    },
                }
            );

            if (response.data && response.data.data) {
                setEmployeeBasicDetails(response.data.data);
            }
        } catch (error) {
            console.error("Failed to fetch employee basic details:", error);
        }
    };

    // Format currency
    const formatCurrency = (amount) => {
        return new Intl.NumberFormat('en-IN', {
            style: 'currency',
            currency: 'INR',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(amount || 0);
    };

    // Calculate statutory benefits amounts
    const calculateStatutoryBenefits = (values) => {
        const basicMonthly = Number(values.salaryDetails.basicMonthly) || 0;
        const monthlyCTC = Number(values.salaryDetails.monthlyCTC) || 0;
        const annualCTC = Number(values.salaryDetails.annualCTC) || 0;

        const benefits = {
            epfEmployerMonthly: 0,
            epfEmployerAnnual: 0,
            edliMonthly: 0,
            edliAnnual: 0,
            epfAdminMonthly: 0,
            epfAdminAnnual: 0,
            esiEmployerMonthly: 0,
            esiEmployerAnnual: 0
        };

        // EPF calculations
        if (employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled) {
            // Check if employer contribution restriction (₹15,000 cap) is enabled
            const isEmployerRestricted = statutoryConfig?.epfEmployerContribution === "restrict_15000";
            const pfWage = isEmployerRestricted ? Math.min(basicMonthly, 15000) : basicMonthly;

            // EPF Employer Contribution
            benefits.epfEmployerMonthly = Math.round(pfWage * 12 / 100);

            // EDLI - 0.50% capped at ₹15,000 wage base (Max ₹75) - only if enabled in config
            if (statutoryConfig?.isEdliIncludedSalaryStructure) {
                const edliWage = Math.min(pfWage, 15000);
                benefits.edliMonthly = Math.round(edliWage * 0.5 / 100);
            }

            // EPF Admin Charges - 0.50% capped at ₹15,000 wage base (Max ₹75) - only if enabled in config
            if (statutoryConfig?.isAdminChargesIncludedSalaryStructure) {
                const adminWage = Math.min(pfWage, 15000);
                benefits.epfAdminMonthly = Math.round(adminWage * 0.5 / 100);
            }

            // Annualized benefits
            benefits.epfEmployerAnnual = Math.round(benefits.epfEmployerMonthly * 12);
            benefits.edliAnnual = Math.round(benefits.edliMonthly * 12);
            benefits.epfAdminAnnual = Math.round(benefits.epfAdminMonthly * 12);

            if (employeeBasicDetails?.eligibleForEps) {
                benefits.epsEnabled = true;
            }
        }

        // ESI calculations
        if (employeeBasicDetails?.eligibleForEsi && statutoryConfig?.esiEnabled) {
            if (annualCTC <= 200000) {
                benefits.esiEmployerMonthly = 482;
            } else {
                benefits.esiEmployerMonthly = 0;
            }
            benefits.esiEmployerAnnual = Math.round(benefits.esiEmployerMonthly * 12);
        }

        return benefits;
    };

    // Single source of truth for "do the components add up to the Annual CTC?".
    // The benefit set here MUST match the set subtracted by the residual in
    // calculateSalaryDetails (EPF employer + ESI employer).
    const getCtcReconciliation = (values) => {
        const sd = values?.salaryDetails || {};
        const annualCTC = toNum(sd.annualCTC);

        if (annualCTC <= 0) {
            return { isValid: false, message: "Enter a valid Annual CTC greater than 0." };
        }

        const benefits = calculateStatutoryBenefits(values);
        const benefitsAnnual =
            toNum(benefits.epfEmployerAnnual) + toNum(benefits.esiEmployerAnnual);

        const earningsAnnual =
            toNum(sd.basic) +
            toNum(sd.hra) +
            toNum(sd.conveyanceDisplay) +
            toNum(sd.fixedAllowance);

        const componentsAnnual = earningsAnnual + benefitsAnnual;

        if (earningsAnnual <= 0) {
            return {
                isValid: false,
                message: "Salary components cannot all be zero. Distribute the Annual CTC across Basic / HRA / Conveyance / Fixed.",
            };
        }

        // Conveyance and Fixed are a residual pair — either can go negative if
        // Basic + HRA overshoot the CTC.
        if (toNum(sd.fixedAllowance) < 0 || toNum(sd.conveyanceDisplay) < 0) {
            return {
                isValid: false,
                message: "Basic + HRA exceed the Annual CTC, leaving a negative residual (Conveyance/Fixed). Reduce them.",
            };
        }

        if (Math.abs(componentsAnnual - annualCTC) > CTC_RECONCILE_TOLERANCE) {
            return {
                isValid: false,
                message: `Components total ₹${Math.round(componentsAnnual).toLocaleString("en-IN")} but Annual CTC is ₹${Math.round(annualCTC).toLocaleString("en-IN")}. They must match before saving.`,
            };
        }

        return { isValid: true, message: "" };
    };


    const calculateSalaryDetails = (sd, setFieldValue, calculationTypes) => {
        const annualCTC = Math.round(Number(sd.annualCTC) || 0);

        const basicCalcType = calculationTypes?.basicCalculationType || sd.basicCalculationType || "percentage";
        const hraCalcType = calculationTypes?.hraCalculationType || sd.hraCalculationType || "percentage";
        const conveyanceCalcType = calculationTypes?.conveyanceCalculationType || sd.conveyanceCalculationType || "percentage";
        const fixedCalcType = calculationTypes?.fixedCalculationType || sd.fixedCalculationType || "percentage";

        const basicPct = Number(sd.basicPercent) || 0;
        const hraPct = Number(sd.hraPercent) || 0;

        const basicFlatAmount = Number(sd.basicFlatAmount) || 0;
        const hraFlatAmount = Number(sd.hraFlatAmount) || 0;
        const conveyanceFlatAmount = Number(sd.conveyanceFlatAmount) || 0;
        const fixedFlatAmount = Number(sd.fixedFlatAmount) || 0;

        let basicAnnual, hraAnnual, convAnnual, fixedAnnual;

        // BASIC
        if (basicCalcType === "percentage") {
            basicAnnual = (annualCTC * basicPct) / 100;
        } else {
            basicAnnual = basicFlatAmount * 12;
        }

        // HRA
        if (hraCalcType === "percentage") {
            hraAnnual = (basicAnnual * hraPct) / 100;
        } else {
            hraAnnual = hraFlatAmount * 12;
        }

        // Stat Benefits calculation for Fixed Allowance & Conveyance residue
        const tempValues = {
            salaryDetails: {
                annualCTC,
                basicMonthly: basicAnnual / 12,
                monthlyCTC: annualCTC / 12,
            },
        };
        const benefits = calculateStatutoryBenefits(tempValues);

        const totalBenefitsAnnual =
            (benefits.epfEmployerAnnual || 0) +
            (benefits.esiEmployerAnnual || 0);

        const residualAnnual = annualCTC - basicAnnual - hraAnnual - totalBenefitsAnnual;

        // Conveyance & Fixed Allowance Balancing
        if (fixedCalcType === "flat") {
            // Fixed is explicitly set; Conveyance takes the residual
            fixedAnnual = Number((fixedFlatAmount * 12).toFixed(2));
            convAnnual = Number((residualAnnual - fixedAnnual).toFixed(2));

            // Force Conveyance to flat mode to reflect this calculated residual
            setFieldValue("salaryDetails.conveyanceCalculationType", "flat");
            setFieldValue("salaryDetails.conveyanceFlatAmount", Number((convAnnual / 12).toFixed(2)));
        } else {
            // Fixed is percentage (residual mode); Conveyance is explicitly set
            if (conveyanceCalcType === "percentage") {
                convAnnual = Number(sd.conveyanceDisplay) || 0;
            } else {
                convAnnual = Number((conveyanceFlatAmount * 12).toFixed(2));
            }
            fixedAnnual = Number((residualAnnual - convAnnual).toFixed(2));
        }

        const basicMonthly = basicAnnual / 12;
        const hraMonthly = hraAnnual / 12;
        const convMonthly = convAnnual / 12;

        let fixedMonthly;
        if (fixedCalcType === "percentage") {
            fixedMonthly = Number((fixedAnnual / 12).toFixed(2));
        } else {
            fixedMonthly = fixedFlatAmount;
        }

        setFieldValue("salaryDetails.basic", basicAnnual);
        setFieldValue("salaryDetails.basicMonthly", basicMonthly);

        setFieldValue("salaryDetails.hra", hraAnnual);
        setFieldValue("salaryDetails.hraMonthly", hraMonthly);

        setFieldValue("salaryDetails.conveyanceDisplay", convAnnual);
        setFieldValue("salaryDetails.conveyanceMonthly", convMonthly);

        setFieldValue("salaryDetails.fixedAllowance", fixedAnnual);
        setFieldValue("salaryDetails.fixedMonthly", fixedMonthly);

        setFieldValue("salaryDetails.monthlyCTC", annualCTC / 12);
        setFieldValue("salaryDetails.annualCTCComputed", annualCTC);
    };

    // Handle percentage change
    const handlePercentageChange = (e, setFieldValue, values) => {
        const percentage = e.target.value;
        setFieldValue("percentage", percentage);

        if (percentage && values.previousAnnualCTC) {
            const newCTC = Math.round(values.previousAnnualCTC * (1 + (percentage / 100)));
            setFieldValue("revisedAnnualCTC", newCTC);
            setFieldValue("salaryDetails.annualCTC", newCTC);

            const isBonusIncluded = values.salaryDetails?.includeBonusInCTC;
            const bonusVal = isBonusIncluded ? Number(values.salaryDetails?.bonus || 0) : 0;
            const beforeBonus = newCTC + bonusVal;
            setFieldValue("salaryDetails.AnnualCTCBeforeBonus", beforeBonus);

            calculateSalaryDetails(
                { ...values.salaryDetails, annualCTC: newCTC, AnnualCTCBeforeBonus: beforeBonus },
                setFieldValue
            );
        }
    };

    // Handle revised CTC change
    const handleRevisedCTCChange = (e, setFieldValue, values) => {
        const newCTC = e.target.value.replace(/[^0-9]/g, "");
        setFieldValue("revisedAnnualCTC", newCTC);
        setFieldValue("salaryDetails.annualCTC", newCTC);
        calculateSalaryDetails({ ...values.salaryDetails, annualCTC: newCTC }, setFieldValue);
    };

    // Get earning ID from master earnings
    const getEarningId = (earningName) => {
        if (!masterEarnings || masterEarnings.length === 0) {
            console.warn("Master earnings not loaded yet");
            return null;
        }

        // Try to find by earningName
        const earning = masterEarnings.find(e =>
            e.earningName?.toLowerCase() === earningName.toLowerCase() ||
            e.name?.toLowerCase() === earningName.toLowerCase() ||
            e.earningCode?.toLowerCase() === earningName.toLowerCase()
        );

        if (earning) {
            return earning.earningId || earning.id;
        }

        console.warn(`Earning not found for name: ${earningName}`);
        return null;
    };

    // Prepare API request payload
    const prepareRevisionPayload = (values) => {


        const changeInPercent =
            values.revisionType === "percentage"
                ? Number(values.percentage)
                : null;

        // Get current statutory benefits
        const currentBenefits = calculateStatutoryBenefits(values);

        // Get actual earning IDs from master earnings
        const basicEarningId = getEarningId("Basic");
        const hraEarningId = getEarningId("House Rent Allowance");
        const conveyanceEarningId = getEarningId("Conveyance Allowance");
        const fixedEarningId = getEarningId("Fixed Allowance");
        const bonusEarningId = getEarningId("Bonus");

        if (!basicEarningId || !hraEarningId) {
            throw new Error("Required earning components not found. Please check master earnings configuration.");
        }

        // Prepare earnings from salary details with actual IDs
        const earnings = [];

        const earningsData = salaryData?.earnings || [];
        const findExistingEarning = (code) => {
            const codeLower = code.toLowerCase();
            return earningsData.find((e) => {
                const name = (e.earningCode || e.name || "").toLowerCase();
                if (codeLower === "basic") {
                    return name.includes("basic");
                }
                return name === codeLower || name.includes(codeLower);
            });
        };

        const basicEarning = findExistingEarning("Basic");
        const hraEarning = findExistingEarning("House Rent Allowance");
        const conveyanceEarning = findExistingEarning("Conveyance Allowance");
        const fixedEarning = findExistingEarning("Fixed Allowance");
        const bonusEarning = findExistingEarning("Bonus");

        const sd = values.salaryDetails || {};

        // Basic
        earnings.push({
            id: basicEarningId,
            earningCode: "Basic",
            componentName: "Basic Salary",
            enabled: true,
            amount: toNum(sd.basicMonthly),
            editable: true,
            isVariable: basicEarning?.isVariable ?? false,
            earningFrequency: "monthly",

            amountInPercentage:
                sd.basicCalculationType === "percentage"
                    ? toNum(sd.basicPercent)
                    : null,

            overrideAmount:
                sd.basicCalculationType === "flat"
                    ? toNum(sd.basicFlatAmount)
                    : null,

            calculationBasis:
                sd.basicCalculationType === "flat"
                    ? "FLAT"
                    : "PERCENTAGE"
        });

        // HRA
        earnings.push({
            id: hraEarningId,
            earningCode: "House Rent Allowance",
            componentName: "House Rent Allowance",
            enabled: true,
            amount: toNum(sd.hraMonthly),
            editable: true,
            isVariable: hraEarning?.isVariable ?? false,
            earningFrequency: "monthly",

            amountInPercentage:
                sd.hraCalculationType === "percentage"
                    ? toNum(sd.hraPercent)
                    : null,

            overrideAmount:
                sd.hraCalculationType === "flat"
                    ? toNum(sd.hraFlatAmount)
                    : null,

            calculationBasis:
                sd.hraCalculationType === "flat"
                    ? "FLAT"
                    : "PERCENTAGE"
        });

        // Conveyance
        if (toNum(sd.conveyanceMonthly) >= 0 && conveyanceEarningId) {
            earnings.push({
                id: conveyanceEarningId,
                earningCode: "Conveyance Allowance",
                componentName: "Conveyance Allowance",
                enabled: true,
                amount: toNum(sd.conveyanceMonthly),
                editable: true,
                isVariable: conveyanceEarning?.isVariable ?? false,
                earningFrequency: "monthly",
                amountInPercentage:
                    sd.conveyanceCalculationType === "percentage"
                        ? toNum(sd.conveyancePercent)
                        : null,
                overrideAmount:
                    sd.conveyanceCalculationType === "flat"
                        ? toNum(sd.conveyanceFlatAmount)
                        : null,
                calculationBasis:
                    sd.conveyanceCalculationType === "flat"
                        ? "FLAT"
                        : "PERCENTAGE"
            });
        }

        // Fixed Allowance
        if (toNum(sd.fixedMonthly) >= 0 && fixedEarningId) {
            earnings.push({
                id: fixedEarningId,
                earningCode: "Fixed Allowance",
                componentName: "Fixed Allowance",
                enabled: true,
                amount: toNum(sd.fixedMonthly),
                editable: true,
                isVariable: fixedEarning?.isVariable ?? false,
                earningFrequency: "monthly",
                amountInPercentage:
                    sd.fixedCalculationType === "percentage"
                        ? 0
                        : null,
                overrideAmount:
                    sd.fixedCalculationType === "flat"
                        ? toNum(sd.fixedFlatAmount)
                        : null,
                calculationBasis:
                    sd.fixedCalculationType === "flat"
                        ? "FLAT"
                        : "PERCENTAGE"
            });
        }

        // Bonus
        if (bonusEarningId || bonusEarning) {
            earnings.push({
                id: bonusEarningId || bonusEarning?.id || null,
                earningCode: "Bonus",
                componentName: "Bonus",
                enabled: sd.includeBonusInCTC ? true : false,
                amount: sd.includeBonusInCTC ? (Number(sd.bonus) || 0) : 0,
                amountInPercentage: null,
                overrideAmount: sd.includeBonusInCTC ? (Number(sd.bonus) || 0) : null,
                calculationBasis: "FLAT",
                editable: true,
                isVariable: true,
                earningFrequency: sd.includeBonusInCTC ? (sd.bonusFrequency || "yearly") : "yearly"
            });
        }

        // Prepare EPF components if applicable
        const epfComponents = [];
        if (employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled) {
            if (currentBenefits.epfEmployerMonthly > 0) {
                epfComponents.push({
                    componentCode: "EPF_EMPLOYER",
                    componentLabel: "EPF - Employer Contribution",
                    percentage: statutoryConfig.epfEmployerContribution || 12,
                    monthlyAmount: currentBenefits.epfEmployerMonthly,
                    annualAmount: currentBenefits.epfEmployerAnnual,
                    calculationType: "PERCENTAGE"
                });
            }

            if (currentBenefits.edliMonthly > 0) {
                epfComponents.push({
                    componentCode: "EDLI_EMPLOYER",
                    componentLabel: "EDLI - Employer Contribution",
                    percentage: 0.5,
                    monthlyAmount: currentBenefits.edliMonthly,
                    annualAmount: currentBenefits.edliAnnual,
                    calculationType: "PERCENTAGE"
                });
            }

            if (currentBenefits.epfAdminMonthly > 0) {
                epfComponents.push({
                    componentCode: "EPF_ADMIN",
                    componentLabel: "EPF Admin Charges - Employer Contribution",
                    percentage: 0.5,
                    monthlyAmount: currentBenefits.epfAdminMonthly,
                    annualAmount: currentBenefits.epfAdminAnnual,
                    calculationType: "PERCENTAGE"
                });
            }
        }

        // Prepare ESI components if applicable
        const esiComponents = [];
        if (employeeBasicDetails?.eligibleForEsi && statutoryConfig?.esiEnabled && currentBenefits.esiEmployerMonthly > 0) {
            esiComponents.push({
                componentCode: "ESI_EMPLOYER",
                componentLabel: "ESI - Employer Contribution",
                percentage: statutoryConfig.esiEmployerContribution || 3.25,
                monthlyAmount: currentBenefits.esiEmployerMonthly,
                annualAmount: currentBenefits.esiEmployerAnnual,
                calculationType: "PERCENTAGE"
            });
        }

        // Convert effective date from YYYY-MM to YYYY-MM-DD format
        const effectiveDate = values.revisedSalaryEffectiveFrom + "-01";
        const payload = {
            employeeId: id,
            ctc: Number(values.salaryDetails.AnnualCTCBeforeBonus),
            monthlySalary: Number(values.salaryDetails.monthlyCTC),
            effectiveDate: effectiveDate,

            previousCtc: values.previousAnnualCTC,
            previousMonthlySalary: values.previousMonthlySalary,

            changeInPercent: changeInPercent, // ✅ ADD THIS

            earnings,
            epfComponents,
            esiComponents,

            benefits: [],
            reimbursements: [],
            variableEarnings: [],
            fbpComponents: []
        };



        console.log("📦 Prepared revision payload:", payload);
        return payload;
    };

    // Handle form submission
    const handleSubmit = async (values, { setSubmitting }) => {
        console.log("🎯 Submit button clicked", values);

        // Check if master earnings are loaded
        if (!masterEarnings || masterEarnings.length === 0) {
            errorMsg("Error", "Earnings configuration is still loading. Please wait.", false);
            setSubmitting(false);
            return;
        }

        // Defense-in-depth: re-verify the components reconcile to the Annual CTC.
        const reconciliation = getCtcReconciliation(values);
        if (!reconciliation.isValid) {
            errorMsg("Cannot Save", reconciliation.message, false);
            setSubmitting(false);
            return;
        }

        setSigningIn(true);
        try {
            // Prepare the request payload
            const payload = prepareRevisionPayload(values);

            // Call the revise API
            const response = await axios.post(
                `${GlobalConst.API_URL}/api/v1/ctc-structures/revise`,
                payload,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                        organizationId: organizationId,
                        'Content-Type': 'application/json'
                    }
                }
            );

            console.log("✅ API Response:", response.data);

            if (response.data && response.data.status === 201) {
                successMsg("Success", response.data.message || "Salary revision saved successfully", false);

                // Navigate back after successful save
                setTimeout(() => {
                    navigate(-1);
                }, 1500);
            } else {
                errorMsg("Error", response.data.message || "Failed to save salary revision", false);
            }

        } catch (error) {
            console.error("❌ Save Error:", error);

            // Handle error response
            if (error.response && error.response.data) {
                const errorMessage = error.response.data.message || "Failed to save salary revision";
                if (errorMessage.includes("Earning not found")) {
                    errorMsg("Configuration Error", "Earning components not properly configured. Please check earnings setup.", false);
                } else {
                    errorMsg("Error", errorMessage, false);
                }
            } else if (error.message) {
                errorMsg("Error", error.message, false);
            } else {
                errorMsg("Error", "Failed to save salary revision. Please try again.", false);
            }
        } finally {
            setSigningIn(false);
            setSubmitting(false);
        }
    };

    // Get current month and next month for date inputs
    const getCurrentMonth = () => {
        const now = new Date();
        return now.toISOString().split('T')[0].slice(0, 7); // YYYY-MM format
    };



    // Initial form values
    const getInitialValues = () => {
        const earningsData = earnings || [];

        const basicAnnual = getEarningAmount(earningsData, "Basic") * 12;
        const hraAnnual = getEarningAmount(earningsData, "House Rent Allowance") * 12;
        const conveyanceAnnual = getEarningAmount(earningsData, "Conveyance Allowance") * 12;
        const fixedAnnual = getEarningAmount(earningsData, "Fixed Allowance") * 12;

        const ctcInclusive = salaryData?.ctc || 0;

        // Read bonus data from saved CTC earnings (salaryData.earnings) — these have
        // the actual enabled flag and earningFrequency that was chosen during employee creation.
        // The preview earnings (earningsData) are template data without saved state.
        const savedEarnings = salaryData?.earnings || [];
        const bonusEarning = savedEarnings.find(e =>
            String(e.earningCode || e.name || e.componentName || "").toLowerCase() === "bonus"
        );
        const includeBonusInCTC = bonusEarning ? (bonusEarning.enabled ?? false) : false;
        const bonusAmount = includeBonusInCTC
            ? (bonusEarning?.overrideAmount || bonusEarning?.amount || 0)
            : 0;
        const bonusFrequency = bonusEarning?.earningFrequency || "";

        const AnnualCTCBeforeBonus = ctcInclusive;
        const annualCTC = Math.max(0, ctcInclusive - (includeBonusInCTC ? Number(bonusAmount || 0) : 0));
        const monthlyCTC = salaryData?.monthlySalary || annualCTC / 12;

        const findEarning = (code) => {
            const codeLower = code.toLowerCase();
            return savedEarnings.find((e) => {
                const name = (e.earningCode || e.name || "").toLowerCase();
                if (codeLower === "basic") {
                    return name.includes("basic");
                }
                return name === codeLower || name.includes(codeLower);
            });
        };

        const basicEarning = findEarning("Basic");
        const hraEarning = findEarning("House Rent Allowance");
        const conveyanceEarning = findEarning("Conveyance Allowance");
        const fixedEarning = findEarning("Fixed Allowance");

        let basicCalculationType = "percentage";
        let basicPercent = 50;
        let basicFlatAmount = 0;

        if (basicEarning) {
            const basis = basicEarning.calculationBasis ?? "PERCENTAGE";
            if (basis === "FLAT") {
                basicCalculationType = "flat";
                basicFlatAmount =
                    basicEarning.overrideAmount != null
                        ? Number(basicEarning.overrideAmount)
                        : basicAnnual / 12;
            } else {
                basicCalculationType = "percentage";
                if (
                    basicEarning.amountInPercentage !== null &&
                    basicEarning.amountInPercentage !== undefined
                ) {
                    basicPercent = Number(basicEarning.amountInPercentage);
                } else if (annualCTC > 0 && basicAnnual > 0) {
                    basicPercent = (basicAnnual / annualCTC) * 100;
                }
            }
        }

        let hraCalculationType = "percentage";
        let hraPercent = 50;
        let hraFlatAmount = 0;

        if (hraEarning) {
            const basis = hraEarning.calculationBasis ?? "PERCENTAGE";
            if (basis === "FLAT") {
                hraCalculationType = "flat";
                hraFlatAmount =
                    hraEarning.overrideAmount != null
                        ? Number(hraEarning.overrideAmount)
                        : hraAnnual / 12;
            } else {
                hraCalculationType = "percentage";
                if (
                    hraEarning.amountInPercentage !== null &&
                    hraEarning.amountInPercentage !== undefined
                ) {
                    hraPercent = Number(hraEarning.amountInPercentage);
                } else if (basicAnnual > 0 && hraAnnual > 0) {
                    hraPercent = (hraAnnual / basicAnnual) * 100;
                }
            }
        }

        let conveyanceCalculationType = "percentage";
        let conveyanceFlatAmount = 0;

        if (conveyanceEarning) {
            const basis = conveyanceEarning.calculationBasis ?? "PERCENTAGE";
            if (basis === "FLAT") {
                conveyanceCalculationType = "flat";
                conveyanceFlatAmount =
                    conveyanceEarning.overrideAmount != null
                        ? Number(conveyanceEarning.overrideAmount)
                        : conveyanceAnnual / 12;
            } else {
                conveyanceCalculationType = "percentage";
            }
        }

        let fixedCalculationType = "percentage";
        let fixedFlatAmount = 0;

        if (fixedEarning) {
            const basis = fixedEarning.calculationBasis ?? "PERCENTAGE";
            if (basis === "FLAT") {
                fixedCalculationType = "flat";
                fixedFlatAmount =
                    fixedEarning.overrideAmount != null
                        ? Number(fixedEarning.overrideAmount)
                        : fixedAnnual / 12;
            } else {
                fixedCalculationType = "percentage";
            }
        }

        basicPercent = Math.round(basicPercent * 100) / 100;
        hraPercent = Math.round(hraPercent * 100) / 100;

        return {
            employeeName: `${basicDetails?.firstName || ""} ${basicDetails?.lastName || ""}`,
            previousAnnualCTC: ctcInclusive,
            previousMonthlySalary: monthlyCTC,
            revisionType: "amount",
            percentage: "",
            revisedAnnualCTC: annualCTC,
            revisedSalaryEffectiveFrom: getCurrentMonth(),

            salaryDetails: {
                annualCTC,
                monthlyCTC,
                AnnualCTCBeforeBonus: AnnualCTCBeforeBonus || "",
                bonus: bonusAmount,
                bonusFrequency: bonusFrequency,
                includeBonusInCTC: includeBonusInCTC,

                basic: basicAnnual,
                basicMonthly: basicAnnual / 12,
                basicPercent,

                hra: hraAnnual,
                hraMonthly: hraAnnual / 12,
                hraPercent,

                conveyanceDisplay: conveyanceAnnual,
                conveyanceMonthly: conveyanceAnnual / 12,

                fixedAllowance: fixedAnnual,
                fixedMonthly: fixedAnnual / 12,

                annualCTCComputed: annualCTC,
                basicCalculationType,
                hraCalculationType,
                conveyanceCalculationType,
                fixedCalculationType,

                basicFlatAmount,
                hraFlatAmount,
                conveyanceFlatAmount,
                fixedFlatAmount,
            }
        };
    };


    if (loading) {
        return <Loader />;
    }

    if (!salaryData) {
        return (
            <div className="text-center py-5">
                <div className="fs-3 text-muted mb-3">No Salary Data Found</div>
                <p className="text-muted">Salary details are not available for this employee.</p>
            </div>
        );
    }

    return (

        <div
            className="container-fluid py-4"
            style={{
                backgroundColor: "#ffffff",
                minHeight: "100vh"
            }}
        >
            <div className="position-fixed top-0 end-0 p-4 z-3">
                <button
                    className="btn btn-light-primary btn-sm d-flex align-items-center gap-2 shadow-sm"
                    onClick={() => navigate(-1)}
                >
                    <i className="bi bi-arrow-left fs-4"></i>
                    Back
                </button>
            </div>




            <div className="mb-6">
                <Title level={2} className="mb-1">Salary Revision for {employeeData?.basicDetails?.firstName || "Employee"}</Title>

                {/* Previous Salary Information */}
                <Card
                    className="mb-6"
                    size="small"
                    style={{ backgroundColor: "#ffffff" }}
                >
                    <Row gutter={[16, 16]}>
                        <Col xs={24} md={12}>
                            <div className="d-flex flex-column">
                                <Text type="secondary" className="mb-1">Previous CTC</Text>
                                <Title level={4} className="mb-0">
                                    {formatCurrency(salaryData?.ctc || 0)}
                                </Title>
                            </div>
                        </Col>
                        <Col xs={24} md={12}>
                            <div className="d-flex flex-column">
                                <Text type="secondary" className="mb-1">Previous Monthly Salary</Text>
                                <Title level={4} className="mb-0">
                                    {formatCurrency(salaryData?.monthlySalary || (salaryData?.ctc || 0) / 12)}
                                </Title>
                            </div>
                        </Col>
                    </Row>
                </Card>

                <Divider />

                {/* Salary Revision Form */}
                <Formik
                    initialValues={getInitialValues()}
                    validationSchema={validationSchema}
                    onSubmit={handleSubmit}
                    enableReinitialize={true}
                >
                    {({ isSubmitting, errors, touched, setFieldValue, values, isValid, dirty }) => {
                        // Recalculate benefits when values change
                        const currentBenefits = calculateStatutoryBenefits(values);

                        // Live check that components reconcile to the Annual CTC.
                        const reconciliation = getCtcReconciliation(values);
                        const componentsValid = reconciliation.isValid;

                        // Toggle Basic / HRA between % and flat WITHOUT losing the amount.
                        const onCalcTypeChange = (component, newType) => {
                            const patch = buildCalcTypePatch(component, newType, values.salaryDetails);
                            Object.entries(patch).forEach(([key, val]) => {
                                setFieldValue(`salaryDetails.${key}`, val);
                            });
                            const mergedSd = { ...values.salaryDetails, ...patch };
                            calculateSalaryDetails(mergedSd, setFieldValue, mergedSd);
                        };

                        return (
                            <Form>
                                {/* Show warning if master earnings not loaded */}
                                {masterEarnings.length === 0 && (
                                    <div className="alert alert-warning mb-4">
                                        <i className="bi bi-exclamation-triangle me-2"></i>
                                        Loading earnings configuration... Please wait before saving.
                                    </div>
                                )}

                                {/* Revision Type Selection */}
                                <div className="mb-6">
                                    <label className="form-label fs-6 fw-bold text-dark mb-3">
                                        Select the Salary Revision type <RequiredStar />
                                    </label>

                                    <div className="mb-4">
                                        <div className="form-check mb-3">
                                            <Field
                                                type="radio"
                                                name="revisionType"
                                                id="revisionTypePercentage"
                                                value="percentage"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="revisionTypePercentage">
                                                Revise CTC by percentage
                                            </label>
                                        </div>

                                        {values.revisionType === 'percentage' && (
                                            <div className="row align-items-center mb-3">
                                                <div className="col-auto">
                                                    <Field
                                                        type="number"
                                                        name="percentage"
                                                        className="form-control"
                                                        placeholder="Enter percentage"
                                                        style={{ width: '120px' }}
                                                        onChange={(e) => handlePercentageChange(e, setFieldValue, values)}
                                                    />
                                                    {errors.percentage && touched.percentage && (
                                                        <div className="text-danger small mt-1">{errors.percentage}</div>
                                                    )}
                                                </div>
                                                <div className="col-auto">
                                                    <span className="text-muted">%</span>
                                                </div>
                                            </div>
                                        )}

                                        <div className="form-check mb-3">
                                            <Field
                                                type="radio"
                                                name="revisionType"
                                                id="revisionTypeAmount"
                                                value="amount"
                                                className="form-check-input"
                                            />
                                            <label className="form-check-label fw-semibold" htmlFor="revisionTypeAmount">
                                                Enter the new CTC amount below
                                            </label>
                                        </div>
                                    </div>

                                    {/* Annual CTC Before Bonus Input */}
                                    <div className="mb-4">
                                        <label className="form-label fs-6 fw-bold text-dark mb-2">
                                            Annual CTC Before Bonus <RequiredStar />
                                        </label>
                                        <div className="input-group">
                                            <span className="input-group-text">₹</span>
                                            <Field
                                                type="text"
                                                name="salaryDetails.AnnualCTCBeforeBonus"
                                                inputMode="numeric"
                                                pattern="[0-9]*"
                                                className={`form-control form-control-lg ${errors.salaryDetails?.AnnualCTCBeforeBonus && touched.salaryDetails?.AnnualCTCBeforeBonus ? "is-invalid" : ""}`}
                                                placeholder="Enter Annual CTC"
                                                readOnly={values.revisionType === "percentage"}
                                                value={values.salaryDetails?.AnnualCTCBeforeBonus || ""}
                                                onChange={(e) => {
                                                    const val = e.target.value.replace(/[^0-9]/g, "");
                                                    setFieldValue("salaryDetails.AnnualCTCBeforeBonus", val);
                                                    const currentBonus = values.salaryDetails.includeBonusInCTC ? (values.salaryDetails.bonus || 0) : 0;
                                                    const calculatedCTC = Math.max(0, Number(val) - Number(currentBonus));
                                                    setFieldValue("revisedAnnualCTC", calculatedCTC);
                                                    setFieldValue("salaryDetails.annualCTC", calculatedCTC);
                                                    calculateSalaryDetails(
                                                        { ...values.salaryDetails, annualCTC: calculatedCTC, AnnualCTCBeforeBonus: val },
                                                        setFieldValue
                                                    );
                                                }}
                                            />
                                            <span className="input-group-text">per year</span>
                                        </div>
                                        {errors.salaryDetails?.AnnualCTCBeforeBonus && touched.salaryDetails?.AnnualCTCBeforeBonus && (
                                            <div className="text-danger small mt-1">{errors.salaryDetails.AnnualCTCBeforeBonus}</div>
                                        )}
                                    </div>

                                    {/* Include Bonus checkbox */}
                                    <div className="form-check form-check-custom form-check-solid mb-4">
                                        <Field
                                            type="checkbox"
                                            name="salaryDetails.includeBonusInCTC"
                                            className="form-check-input me-2"
                                            style={{ border: "1px solid #3f4254" }}
                                            id="includeBonusInCTC"
                                            onChange={(e) => {
                                                const checked = e.target.checked;
                                                setFieldValue("salaryDetails.includeBonusInCTC", checked);
                                                const beforeBonus = values.salaryDetails.AnnualCTCBeforeBonus || 0;
                                                const bonusVal = checked ? (values.salaryDetails.bonus || 0) : 0;
                                                const calculatedCTC = Math.max(0, Number(beforeBonus) - Number(bonusVal));
                                                setFieldValue("revisedAnnualCTC", calculatedCTC);
                                                setFieldValue("salaryDetails.annualCTC", calculatedCTC);
                                                if (!checked) {
                                                    setFieldValue("salaryDetails.bonus", "");
                                                    setFieldValue("salaryDetails.bonusFrequency", "");
                                                }
                                                calculateSalaryDetails(
                                                    {
                                                        ...values.salaryDetails,
                                                        annualCTC: calculatedCTC,
                                                        includeBonusInCTC: checked,
                                                        bonus: checked ? values.salaryDetails.bonus : "",
                                                        bonusFrequency: checked ? values.salaryDetails.bonusFrequency : "",
                                                        AnnualCTCBeforeBonus: beforeBonus
                                                    },
                                                    setFieldValue
                                                );
                                            }}
                                        />
                                        <label className="form-check-label fs-6 fw-bold text-dark" htmlFor="includeBonusInCTC">
                                            Include Bonus in CTC
                                        </label>
                                    </div>

                                    {/* Bonus Frequency and Amount */}
                                    {values.salaryDetails?.includeBonusInCTC && (
                                        <div className="row mb-4">
                                            <div className="col-md-6">
                                                <label className="form-label fs-6 fw-bold text-dark mb-2">
                                                    Bonus
                                                </label>
                                                <Field
                                                    as="select"
                                                    name="salaryDetails.bonusFrequency"
                                                    className="form-select"
                                                >
                                                    <option value="">Select Bonus Frequency</option>
                                                    <option value="yearly">Yearly</option>
                                                    <option value="half-yearly">Half yearly</option>
                                                    <option value="quarterly">Quarterly</option>
                                                </Field>
                                                {errors.salaryDetails?.bonusFrequency && touched.salaryDetails?.bonusFrequency && (
                                                    <div className="text-danger small mt-1">{errors.salaryDetails.bonusFrequency}</div>
                                                )}
                                            </div>
                                            <div className="col-md-6">
                                                <label className="form-label mb-2">&nbsp;</label>
                                                <div className="input-group">
                                                    <span className="input-group-text">₹</span>
                                                    <Field
                                                        type="text"
                                                        name="salaryDetails.bonus"
                                                        inputMode="numeric"
                                                        pattern="[0-9]*"
                                                        className="form-control"
                                                        placeholder="Enter Bonus Amount"
                                                        value={values.salaryDetails.bonus || ""}
                                                        onChange={(e) => {
                                                            const val = e.target.value.replace(/[^0-9]/g, "");
                                                            setFieldValue("salaryDetails.bonus", val);
                                                            const currentBeforeBonus = values.salaryDetails.AnnualCTCBeforeBonus || 0;
                                                            const calculatedCTC = Math.max(0, Number(currentBeforeBonus) - Number(val));
                                                            setFieldValue("revisedAnnualCTC", calculatedCTC);
                                                            setFieldValue("salaryDetails.annualCTC", calculatedCTC);
                                                            calculateSalaryDetails(
                                                                { ...values.salaryDetails, annualCTC: calculatedCTC, bonus: val, AnnualCTCBeforeBonus: currentBeforeBonus },
                                                                setFieldValue
                                                            );
                                                        }}
                                                    />
                                                    <span className="input-group-text">per year</span>
                                                </div>
                                                {errors.salaryDetails?.bonus && touched.salaryDetails?.bonus && (
                                                    <div className="text-danger small mt-1">{errors.salaryDetails.bonus}</div>
                                                )}
                                            </div>
                                        </div>
                                    )}

                                    {/* Final CTC (Actual Salary) */}
                                    <div className="mb-4">
                                        <label className="form-label fs-6 fw-bold text-dark mb-2">
                                            Final CTC
                                        </label>
                                        <div className="input-group mb-3">
                                            <span className="input-group-text">₹</span>
                                            <input
                                                type="text"
                                                className="form-control bg-light"
                                                readOnly
                                                value={values.revisedAnnualCTC || 0}
                                            />
                                            <span className="input-group-text">per year</span>
                                        </div>
                                    </div>
                                </div>

                                {/* Salary Structure Table */}
                                <div className="mb-6">
                                    <div className="table-responsive">
                                        <table className="table gs-7 gy-7 gx-7 align-middle">
                                            <thead>
                                                <tr className="fw-semibold fs-6 text-gray-800 border-bottom border-gray-200">
                                                    <th>Salary Components</th>
                                                    <th>Calculation Type</th>
                                                    <th className="text-end">Monthly Amount</th>
                                                    <th className="text-end">Annual Amount</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <tr>
                                                    <td colSpan={4}>
                                                        <label className="form-label fs-6 fw-bold text-dark mb-2">
                                                            Earnings
                                                        </label>
                                                    </td>
                                                </tr>

                                                <tr>
                                                    <td>Basic</td>
                                                    <td>
                                                        <div className="d-flex flex-column gap-2">

                                                            <div className="d-flex gap-3">
                                                                <label>
                                                                    <Field type="radio"
                                                                        name="salaryDetails.basicCalculationType"
                                                                        value="percentage"
                                                                        checked={values.salaryDetails.basicCalculationType === "percentage"}
                                                                        onChange={() => onCalcTypeChange("basic", "percentage")} />
                                                                    %
                                                                </label>

                                                                <label>
                                                                    <Field type="radio"
                                                                        name="salaryDetails.basicCalculationType"
                                                                        value="flat"
                                                                        checked={values.salaryDetails.basicCalculationType === "flat"}
                                                                        onChange={() => onCalcTypeChange("basic", "flat")} />
                                                                    Flat
                                                                </label>
                                                            </div>

                                                            {values.salaryDetails.basicCalculationType === "percentage" ? (
                                                                <Field
                                                                    type="text"
                                                                    inputMode="decimal"
                                                                    pattern="[0-9]*\.?[0-9]*"
                                                                    name="salaryDetails.basicPercent"
                                                                    className="form-control form-control-sm"
                                                                    onChange={(e) => {
                                                                        const val = sanitizeDecimal(e.target.value);
                                                                        setFieldValue("salaryDetails.basicPercent", val);
                                                                        calculateSalaryDetails(
                                                                            { ...values.salaryDetails, basicPercent: val },
                                                                            setFieldValue
                                                                        );
                                                                    }}
                                                                />
                                                            ) : (
                                                                <Field
                                                                    type="number"
                                                                    name="salaryDetails.basicFlatAmount"
                                                                    className="form-control form-control-sm"
                                                                    onChange={(e) => {
                                                                        const val = e.target.value;
                                                                        setFieldValue("salaryDetails.basicFlatAmount", val);
                                                                        calculateSalaryDetails(
                                                                            { ...values.salaryDetails, basicFlatAmount: val },
                                                                            setFieldValue
                                                                        );
                                                                    }}
                                                                />
                                                            )}
                                                        </div>
                                                    </td>
                                                    <td className="text-end">₹{values.salaryDetails.basicMonthly}</td>
                                                    <td className="text-end">₹{values.salaryDetails.basic}</td>
                                                </tr>

                                                <tr>
                                                    <td>House Rent Allowance</td>
                                                    <td>
                                                        <div className="d-flex flex-column gap-2">

                                                            {/* Radio Buttons */}
                                                            <div className="d-flex align-items-center gap-3">
                                                                <label className="d-flex align-items-center gap-1">
                                                                    <Field
                                                                        type="radio"
                                                                        name="salaryDetails.hraCalculationType"
                                                                        value="percentage"
                                                                        checked={values.salaryDetails.hraCalculationType === "percentage"}
                                                                        onChange={() => onCalcTypeChange("hra", "percentage")}
                                                                    />
                                                                    <span>%</span>
                                                                </label>

                                                                <label className="d-flex align-items-center gap-1">
                                                                    <Field
                                                                        type="radio"
                                                                        name="salaryDetails.hraCalculationType"
                                                                        value="flat"
                                                                        checked={values.salaryDetails.hraCalculationType === "flat"}
                                                                        onChange={() => onCalcTypeChange("hra", "flat")}
                                                                    />
                                                                    <span>Flat Amount</span>
                                                                </label>
                                                            </div>

                                                            {/* Conditional Input */}
                                                            {values.salaryDetails.hraCalculationType === "percentage" ? (
                                                                <div className="d-flex align-items-center gap-2">
                                                                    <Field
                                                                        type="text"
                                                                        inputMode="decimal"
                                                                        pattern="[0-9]*\.?[0-9]*"
                                                                        name="salaryDetails.hraPercent"
                                                                        className="form-control form-control-sm"
                                                                        style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                        onChange={(e) => {
                                                                            const val = sanitizeDecimal(e.target.value);
                                                                            setFieldValue("salaryDetails.hraPercent", val);
                                                                            calculateSalaryDetails(
                                                                                { ...values.salaryDetails, hraPercent: val },
                                                                                setFieldValue
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span className="text-muted">% of Basic</span>
                                                                </div>
                                                            ) : (
                                                                <div className="d-flex align-items-center gap-2">
                                                                    <span className="input-group-text">₹</span>
                                                                    <Field
                                                                        type="number"
                                                                        name="salaryDetails.hraFlatAmount"
                                                                        className="form-control form-control-sm"
                                                                        style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                        placeholder="Monthly amount"
                                                                        onChange={(e) => {
                                                                            const val = e.target.value;
                                                                            setFieldValue("salaryDetails.hraFlatAmount", val);
                                                                            calculateSalaryDetails(
                                                                                { ...values.salaryDetails, hraFlatAmount: val },
                                                                                setFieldValue
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span className="text-muted">per month</span>
                                                                </div>
                                                            )}

                                                        </div>

                                                        {/* Validation Errors */}
                                                        {errors.salaryDetails?.hraPercent &&
                                                            touched.salaryDetails?.hraPercent &&
                                                            values.salaryDetails.hraCalculationType === "percentage" && (
                                                                <div className="text-danger small">
                                                                    {errors.salaryDetails.hraPercent}
                                                                </div>
                                                            )}

                                                        {errors.salaryDetails?.hraFlatAmount &&
                                                            touched.salaryDetails?.hraFlatAmount &&
                                                            values.salaryDetails.hraCalculationType === "flat" && (
                                                                <div className="text-danger small">
                                                                    {errors.salaryDetails.hraFlatAmount}
                                                                </div>
                                                            )}
                                                    </td>
                                                    <td className="text-end">₹{values.salaryDetails.hraMonthly}</td>
                                                    <td className="text-end">₹{values.salaryDetails.hra}</td>
                                                </tr>

                                                {/* Conveyance Allowance Row */}
                                                <tr>
                                                    <td>Conveyance Allowance</td>
                                                    <td>
                                                        <div className="d-flex flex-column gap-2">
                                                            <div className="d-flex align-items-center gap-3">
                                                                <label className="d-flex align-items-center gap-1">
                                                                    <Field
                                                                        type="radio"
                                                                        name="salaryDetails.conveyanceCalculationType"
                                                                        value="percentage"
                                                                        checked={values.salaryDetails.conveyanceCalculationType === 'percentage'}
                                                                        onChange={() => {
                                                                            setFieldValue("salaryDetails.conveyanceCalculationType", "percentage");
                                                                            calculateSalaryDetails(
                                                                                values.salaryDetails,
                                                                                setFieldValue,
                                                                                {
                                                                                    ...values.salaryDetails,
                                                                                    conveyanceCalculationType: "percentage"
                                                                                }
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span>%</span>
                                                                </label>
                                                                <label className="d-flex align-items-center gap-1">
                                                                    <Field
                                                                        type="radio"
                                                                        name="salaryDetails.conveyanceCalculationType"
                                                                        value="flat"
                                                                        checked={values.salaryDetails.conveyanceCalculationType === 'flat'}
                                                                        onChange={() => {
                                                                            const currentMonthly = Math.round((Number(values.salaryDetails.conveyanceMonthly) || 0) * 100) / 100;
                                                                            setFieldValue("salaryDetails.conveyanceCalculationType", "flat");
                                                                            setFieldValue("salaryDetails.conveyanceFlatAmount", currentMonthly);
                                                                            setFieldValue("salaryDetails.fixedCalculationType", "percentage");
                                                                            calculateSalaryDetails(
                                                                                { ...values.salaryDetails, conveyanceFlatAmount: currentMonthly, fixedCalculationType: "percentage" },
                                                                                setFieldValue,
                                                                                {
                                                                                    ...values.salaryDetails,
                                                                                    conveyanceCalculationType: "flat",
                                                                                    fixedCalculationType: "percentage"
                                                                                }
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span>Flat Amount</span>
                                                                </label>
                                                            </div>

                                                            {values.salaryDetails.conveyanceCalculationType === 'percentage' ? (
                                                                <div className="text-muted mt-1">Fixed amount (pre-configured)</div>
                                                            ) : (
                                                                <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                                                    <span className="input-group-text">₹</span>
                                                                    <Field
                                                                        type="number"
                                                                        name="salaryDetails.conveyanceFlatAmount"
                                                                        className="form-control form-control-sm flex-grow-1"
                                                                        style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                        placeholder="Monthly amount"
                                                                        onChange={(e) => {
                                                                            const val = e.target.value;
                                                                            setFieldValue("salaryDetails.conveyanceFlatAmount", val);
                                                                            setFieldValue("salaryDetails.fixedCalculationType", "percentage");
                                                                            calculateSalaryDetails(
                                                                                { ...values.salaryDetails, conveyanceFlatAmount: val, fixedCalculationType: "percentage" },
                                                                                setFieldValue,
                                                                                { ...values.salaryDetails, conveyanceCalculationType: "flat", fixedCalculationType: "percentage" }
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span className="text-muted">per month</span>
                                                                </div>
                                                            )}
                                                        </div>
                                                        {errors.salaryDetails?.conveyanceFlatAmount && touched.salaryDetails?.conveyanceFlatAmount && values.salaryDetails.conveyanceCalculationType === 'flat' && (
                                                            <div className="text-danger small">{errors.salaryDetails.conveyanceFlatAmount}</div>
                                                        )}
                                                    </td>
                                                    <td className="text-end">₹{values.salaryDetails.conveyanceMonthly}</td>
                                                    <td className="text-end">₹{values.salaryDetails.conveyanceDisplay}</td>
                                                </tr>

                                                {/* Fixed Allowance Row */}
                                                <tr>
                                                    <td>
                                                        Fixed Allowance{" "}
                                                        <span
                                                            className="ms-1"
                                                            data-bs-toggle="tooltip"
                                                            title="Monthly CTC - Sum of all other components (including benefits)"
                                                            style={{ cursor: "pointer" }}
                                                        >
                                                            <i className="bi bi-info-circle-fill text-gray-500"></i>
                                                        </span>
                                                    </td>
                                                    <td>
                                                        <div className="d-flex flex-column gap-2">
                                                            <div className="d-flex align-items-center gap-3">
                                                                <label className="d-flex align-items-center gap-1">
                                                                    <Field
                                                                        type="radio"
                                                                        name="salaryDetails.fixedCalculationType"
                                                                        value="percentage"
                                                                        checked={values.salaryDetails.fixedCalculationType === 'percentage'}
                                                                        onChange={() => {
                                                                            setFieldValue("salaryDetails.fixedCalculationType", "percentage");
                                                                            calculateSalaryDetails(
                                                                                values.salaryDetails,
                                                                                setFieldValue,
                                                                                {
                                                                                    ...values.salaryDetails,
                                                                                    fixedCalculationType: "percentage"
                                                                                }
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span>%</span>
                                                                </label>
                                                                <label className="d-flex align-items-center gap-1">
                                                                    <Field
                                                                        type="radio"
                                                                        name="salaryDetails.fixedCalculationType"
                                                                        value="flat"
                                                                        checked={values.salaryDetails.fixedCalculationType === 'flat'}
                                                                        onChange={() => {
                                                                            const currentMonthly = Math.round((Number(values.salaryDetails.fixedMonthly) || 0) * 100) / 100;
                                                                            setFieldValue("salaryDetails.fixedCalculationType", "flat");
                                                                            setFieldValue("salaryDetails.fixedFlatAmount", currentMonthly);
                                                                            calculateSalaryDetails(
                                                                                { ...values.salaryDetails, fixedFlatAmount: currentMonthly },
                                                                                setFieldValue,
                                                                                {
                                                                                    ...values.salaryDetails,
                                                                                    fixedCalculationType: "flat"
                                                                                }
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span>Flat Amount</span>
                                                                </label>
                                                            </div>

                                                            {values.salaryDetails.fixedCalculationType === 'percentage' ? (
                                                                <div className="text-muted mt-1">Auto-calculated (residual)</div>
                                                            ) : (
                                                                <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                                                    <span className="input-group-text">₹</span>
                                                                    <Field
                                                                        type="number"
                                                                        name="salaryDetails.fixedFlatAmount"
                                                                        className="form-control form-control-sm flex-grow-1"
                                                                        style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                        placeholder="Monthly amount"
                                                                        onChange={(e) => {
                                                                            const val = e.target.value;
                                                                            setFieldValue("salaryDetails.fixedFlatAmount", val);
                                                                            calculateSalaryDetails(
                                                                                { ...values.salaryDetails, fixedFlatAmount: val },
                                                                                setFieldValue,
                                                                                values.salaryDetails
                                                                            );
                                                                        }}
                                                                    />
                                                                    <span className="text-muted">per month</span>
                                                                </div>
                                                            )}
                                                        </div>
                                                        {errors.salaryDetails?.fixedFlatAmount && touched.salaryDetails?.fixedFlatAmount && values.salaryDetails.fixedCalculationType === 'flat' && (
                                                            <div className="text-danger small">{errors.salaryDetails.fixedFlatAmount}</div>
                                                        )}
                                                    </td>
                                                    <td className="text-end">₹{values.salaryDetails.fixedMonthly}</td>
                                                    <td className="text-end">₹{values.salaryDetails.fixedAllowance}</td>
                                                </tr>

                                                {/* Benefits Section */}
                                                {(employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled) ||
                                                    (employeeBasicDetails?.eligibleForEsi && statutoryConfig?.esiEnabled) ? (
                                                    <tr>
                                                        <td colSpan={4}>
                                                            <label className="form-label fs-6 fw-bold text-dark mb-2">
                                                                Benefits
                                                            </label>
                                                        </td>
                                                    </tr>
                                                ) : null}

                                                {/* EPF Employer Contribution */}
                                                {employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled && statutoryConfig.epfEmployerContribution && (
                                                    <tr>
                                                        <td>
                                                            EPF - Employer Contribution
                                                            <Tooltip
                                                                title={`${statutoryConfig.epfEmployerContribution.replace('_', ' ')} of PF Wages`}
                                                            >
                                                                <InfoCircleOutlined className="ms-1 text-gray-500" style={{ cursor: "pointer" }} />
                                                            </Tooltip>
                                                        </td>
                                                        <td>
                                                            {statutoryConfig.epfEmployerContribution.replace('_', ' ')} of PF Wages
                                                            {employeeBasicDetails?.eligibleForEps && (
                                                                <div className="text-muted small mt-1">
                                                                    EPS contribution is enabled
                                                                </div>
                                                            )}
                                                        </td>
                                                        <td className="text-end">₹{currentBenefits.epfEmployerMonthly || 0}</td>
                                                        <td className="text-end">₹{currentBenefits.epfEmployerAnnual || 0}</td>
                                                    </tr>
                                                )}

                                                {/* EDLI - Employer Contribution */}
                                                {employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled && statutoryConfig.isEdliIncludedSalaryStructure && (
                                                    <tr>
                                                        <td>
                                                            EDLI - Employer Contribution
                                                            <Tooltip
                                                                title={
                                                                    <div>
                                                                        <div>EDLI contribution is of PF Wage.</div>
                                                                        <div>Maximum Employer Contribution for EDLI is ₹7.75</div>
                                                                        <div>0.50% of PF Wages</div>
                                                                    </div>
                                                                }
                                                            >
                                                                <InfoCircleOutlined className="ms-1 text-gray-500" style={{ cursor: "pointer" }} />
                                                            </Tooltip>
                                                        </td>
                                                        <td>0.50% of PF Wages</td>
                                                        <td className="text-end">₹{currentBenefits.edliMonthly || 0}</td>
                                                        <td className="text-end">₹{currentBenefits.edliAnnual || 0}</td>
                                                    </tr>
                                                )}

                                                {/* EPF Admin Charges - Employer Contribution */}
                                                {employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled && statutoryConfig.isAdminChargesIncludedSalaryStructure && (
                                                    <tr>
                                                        <td>
                                                            EPF Admin Charges - Employer Contribution
                                                            <Tooltip
                                                                title={
                                                                    <div>
                                                                        <div>The maximum PF wage of the Employee or the Employer will be considered for calculation.</div>
                                                                        <div>0.50% of PF Wages</div>
                                                                    </div>
                                                                }
                                                            >
                                                                <InfoCircleOutlined className="ms-1 text-gray-500" style={{ cursor: "pointer" }} />
                                                            </Tooltip>
                                                        </td>
                                                        <td>0.50% of PF Wages</td>
                                                        <td className="text-end">₹{currentBenefits.epfAdminMonthly || 0}</td>
                                                        <td className="text-end">₹{currentBenefits.epfAdminAnnual || 0}</td>
                                                    </tr>
                                                )}

                                                {/* ESI - Employer Contribution */}
                                                {employeeBasicDetails?.eligibleForEsi && statutoryConfig?.esiEnabled && statutoryConfig.esiEmployerContribution && (
                                                    <tr>
                                                        <td>
                                                            ESI - Employer Contribution
                                                            <Tooltip
                                                                title={`${statutoryConfig.esiEmployerContribution}% of ESI Wages`}
                                                            >
                                                                <InfoCircleOutlined className="ms-1 text-gray-500" style={{ cursor: "pointer" }} />
                                                            </Tooltip>
                                                        </td>
                                                        <td>{statutoryConfig.esiEmployerContribution}% of ESI Wages</td>
                                                        <td className="text-end">₹{currentBenefits.esiEmployerMonthly || 0}</td>
                                                        <td className="text-end">₹{currentBenefits.esiEmployerAnnual || 0}</td>
                                                    </tr>
                                                )}
                                            </tbody>

                                            <tfoot>
                                                <tr
                                                    className="fw-bold"
                                                    style={{ backgroundColor: "#F6F8FF" }}
                                                >
                                                    <td colSpan={2}>Cost to Company</td>
                                                    <td className="text-end">₹{values.salaryDetails.monthlyCTC}</td>
                                                    <td className="text-end">₹{values.salaryDetails.annualCTCComputed}</td>
                                                </tr>
                                            </tfoot>
                                        </table>
                                    </div>
                                </div>

                                {/* Reconciliation warning: blocks saving when components do not
                                    add up to the Annual CTC (all-zero, mismatch, negative residual). */}
                                {values.salaryDetails.annualCTC > 0 && !componentsValid && (
                                    <div className="alert alert-danger d-flex align-items-center mb-4">
                                        <i className="bi bi-exclamation-triangle-fill me-2"></i>
                                        <span>{reconciliation.message}</span>
                                    </div>
                                )}

                                <Divider />

                                {/* Payout Preferences */}
                                <div className="mb-6">
                                    <label className="form-label fs-6 fw-bold text-dark mb-3">
                                        Payout Preferences <RequiredStar />
                                    </label>

                                    <Row gutter={[16, 16]} className="mb-4">
                                        <Col xs={24} md={6}>
                                            <div className="mb-3">
                                                <label className="form-label fw-semibold mb-2">
                                                    Revised Salary effective from
                                                </label>
                                                <Field
                                                    type="month"
                                                    name="revisedSalaryEffectiveFrom"
                                                    className="form-control"
                                                />
                                                {errors.revisedSalaryEffectiveFrom && touched.revisedSalaryEffectiveFrom && (
                                                    <div className="text-danger small mt-1">{errors.revisedSalaryEffectiveFrom}</div>
                                                )}
                                            </div>
                                        </Col>

                                    </Row>

                                    <div className="alert alert-info mb-0">
                                        <div className="d-flex">
                                            <i className="bi bi-info-circle me-2 mt-1"></i>
                                            <div>
                                                <strong>Note:</strong> Payroll will automatically calculate any arrears in the salary and process them in the payout month, eliminating the need for manually adding arrear components.
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Action Buttons */}
                                <div className="d-flex justify-content-between mt-6">
                                    <button
                                        type="button"
                                        className="btn btn-light"
                                        onClick={() => navigate(-1)}
                                    >
                                        Cancel
                                    </button>
                                    <button
                                        type="submit"
                                        className="btn btn-primary"
                                        disabled={isSubmitting || signingIn || !isValid || !componentsValid || masterEarnings.length === 0}
                                    >
                                        {signingIn ? (
                                            <span className="spinner-border spinner-border-sm me-2"></span>
                                        ) : null}
                                        {signingIn ? "Saving..." : "Save Revision"}
                                    </button>
                                </div>
                            </Form>
                        );
                    }}
                </Formik>
            </div>

            {signingIn && <Loader />}
        </div>
    );
};

export default EditReviseSalary;