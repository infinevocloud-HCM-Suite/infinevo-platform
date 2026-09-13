import React, { useState, useEffect } from "react";
import { Formik, Form, Field } from "formik";
import * as Yup from "yup";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { Tooltip } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';

const RequiredStar = () => <span className="text-danger">*</span>;

// Safe numeric coercion: keeps an explicit 0, never returns NaN/null/undefined.
const toNum = (v, fallback = 0) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : fallback;
};

// Sanitize a percentage text input: allow digits and a single decimal point.
const sanitizeDecimal = (raw) =>
    String(raw)
        .replace(/[^0-9.]/g, "")      // keep digits and dots
        .replace(/(\..*)\./g, "$1");  // collapse to a single decimal point

// Tolerance (in rupees) used when checking that the salary components
// reconcile against the entered Annual CTC. Allows for rounding noise.
const CTC_RECONCILE_TOLERANCE = 2;

// When the user toggles a component between "percentage" and "flat", convert the
// CURRENT effective amount into the target representation so the rupee value
// stays continuous (instead of jumping to a stale percent/flat value).
const buildCalcTypePatch = (component, newType, sd) => {
    const annualCTC = toNum(sd.annualCTC);
    const basicAnnual = toNum(sd.basic);

    const currentMonthly = {
        basic: toNum(sd.basicMonthly),
        hra: toNum(sd.hraMonthly),
        conveyance: toNum(sd.conveyanceMonthly),
        fixed: toNum(sd.fixedMonthly),
    }[component];

    const currentAnnual = {
        basic: toNum(sd.basic),
        hra: toNum(sd.hra),
        conveyance: toNum(sd.conveyanceDisplay),
        fixed: toNum(sd.fixedAllowance),
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
        } else if (component === "conveyance") {
            patch.conveyance = round2(currentAnnual);
        }
    }

    return patch;
};

const SalaryDetails = ({ initialValues, onNext, onPrev, employeeId }) => {
    const [earnings, setEarnings] = useState([]);
    const [loading, setLoading] = useState(false);
    const [signingIn, setSigningIn] = useState(false);
    const [earningsLoaded, setEarningsLoaded] = useState(false);
    const [statutoryConfig, setStatutoryConfig] = useState(null);
    const [employeeBasicDetails, setEmployeeBasicDetails] = useState(null);

    const organizationId = localStorage.getItem("organizationId") || "default-org-id";
    const token = localStorage.getItem("__t");

    useEffect(() => {
        fetchEarnings();
        fetchOrgStatutoryConfig();
        fetchEmployeeBasicDetails();
    }, [organizationId, employeeId]);

    const validationSchema = Yup.object().shape({
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
                .typeError("Basic % must be a number")
                .when("basicCalculationType", {
                    is: "percentage",
                    then: (schema) =>
                        schema
                            .required("Basic % is required")
                            .min(1, "Must be at least 1%")
                            .max(100, "Cannot exceed 100%"),
                    otherwise: (schema) => schema.notRequired(),
                }),

            hraPercent: Yup.number()
                .typeError("HRA % must be a number")
                .when("hraCalculationType", {
                    is: "percentage",
                    then: (schema) =>
                        schema
                            .required("HRA % is required")
                            .min(1, "Must be at least 1%")
                            .max(100, "Cannot exceed 100%"),
                    otherwise: (schema) => schema.notRequired(),
                }),

            conveyanceFlatAmount: Yup.number()
                .typeError("Conveyance amount must be a number")
                .when("conveyanceCalculationType", {
                    is: "flat",
                    then: (schema) =>
                        schema
                            .required("Conveyance amount is required")
                            .min(0, "Must be 0 or greater"),
                    otherwise: (schema) => schema.notRequired(),
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
                    if (toNum(sd.annualCTC) <= 0) return true; // handled by AnnualCTC rule
                    const totalEarnings =
                        toNum(sd.basic) +
                        toNum(sd.hra) +
                        toNum(sd.conveyanceDisplay) +
                        toNum(sd.fixedAllowance);
                    return totalEarnings > 0;
                }
            )
            .test(
                "fixed-not-negative",
                "Basic + HRA + Conveyance exceed the Annual CTC. Reduce them so the residual Fixed Allowance is not negative.",
                (sd) => {
                    if (!sd) return true;
                    return toNum(sd.fixedAllowance) >= 0;
                }
            ),
    });

    const fetchEarnings = async () => {
        setLoading(true);
        setEarningsLoaded(false);
        try {
            const { data } = await axios.get(
                `${GlobalConst.API_URL}/api/employees/salary/preview/${employeeId}`,
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
        } finally {
            setLoading(false);
        }
    };

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

    const fetchEmployeeBasicDetails = async () => {
        try {
            const response = await axios.get(
                `${GlobalConst.API_URL}/api/employees/${employeeId}`,
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

    const calculateStatutoryBenefits = (values) => {
        const basicMonthly = Number(values.salaryDetails.basicMonthly) || 0;
        const monthlyCTC = Number(values.salaryDetails.monthlyCTC) || 0;
        const annualCTC = Number(values.salaryDetails.annualCTC) || 0;

        const benefits = {};

        // EPF calculations
        if (employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled) {
            benefits.epfEmployerMonthly = Math.round(basicMonthly * 12 / 100);

            const calculatedEdli = Math.round(basicMonthly * 0.5 / 100);
            benefits.edliMonthly = Math.min(calculatedEdli, 75);

            benefits.epfAdminMonthly = Math.round(basicMonthly * 0.5 / 100);

            benefits.epfEmployerAnnual = Math.round(benefits.epfEmployerMonthly * 12);
            benefits.edliAnnual = Math.round(benefits.edliMonthly * 12);
            benefits.epfAdminAnnual = Math.round(benefits.epfAdminMonthly * 12);

            if (employeeBasicDetails?.eligibleForEps) {
                benefits.epsEnabled = true;
            }
        }

        // ESI calculations - Fix the bug where it gets calculated if CTC is 0!
        if (employeeBasicDetails?.eligibleForEsi && statutoryConfig?.esiEnabled) {
            if (annualCTC > 0 && annualCTC <= 200000) {
                benefits.esiEmployerMonthly = 482;
            } else {
                benefits.esiEmployerMonthly = 0;
            }
            benefits.esiEmployerAnnual = Math.round(benefits.esiEmployerMonthly * 12);
        }

        return benefits;
    };

    const getCtcReconciliation = (values) => {
        const sd = values?.salaryDetails || {};
        const annualCTC = toNum(sd.annualCTC);

        if (annualCTC <= 0) {
            return {
                isValid: false,
                message: "Enter a valid Annual CTC greater than 0.",
                annualCTC,
                componentsAnnual: 0,
                benefitsAnnual: 0,
            };
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
                annualCTC,
                componentsAnnual,
                benefitsAnnual,
            };
        }

        if (toNum(sd.fixedAllowance) < 0) {
            return {
                isValid: false,
                message: "Basic + HRA + Conveyance exceed the Annual CTC, leaving a negative Fixed Allowance. Reduce them.",
                annualCTC,
                componentsAnnual,
                benefitsAnnual,
            };
        }

        if (Math.abs(componentsAnnual - annualCTC) > CTC_RECONCILE_TOLERANCE) {
            return {
                isValid: false,
                message: `Components total ₹${Math.round(componentsAnnual).toLocaleString("en-IN")} but Final CTC is ₹${Math.round(annualCTC).toLocaleString("en-IN")}. They must match before saving.`,
                annualCTC,
                componentsAnnual,
                benefitsAnnual,
            };
        }

        return {
            isValid: true,
            message: "",
            annualCTC,
            componentsAnnual,
            benefitsAnnual,
        };
    };

    const calculateSalaryDetails = (sd, setFieldValue, calculationTypes) => {
        const annualCTC = Math.round(Number(sd.annualCTC) || 0); 
        const basicPct = Number(sd.basicPercent) || 0;
        const hraPct = Number(sd.hraPercent) || 0;

        const basicCalcType = calculationTypes?.basicCalculationType || sd.basicCalculationType || 'percentage';
        const hraCalcType = calculationTypes?.hraCalculationType || sd.hraCalculationType || 'percentage';
        const conveyanceCalcType = calculationTypes?.conveyanceCalculationType || sd.conveyanceCalculationType || 'percentage';
        const fixedCalcType = calculationTypes?.fixedCalculationType || sd.fixedCalculationType || 'percentage';

        const basicFlatAmount = Number(sd.basicFlatAmount) || 0;
        const hraFlatAmount = Number(sd.hraFlatAmount) || 0;
        const conveyanceFlatAmount = Number(sd.conveyanceFlatAmount) || 0;
        const fixedFlatAmount = Number(sd.fixedFlatAmount) || 0;

        let basicAnnual, hraAnnual, convAnnual, fixedAnnual;

        if (basicCalcType === 'percentage') {
            basicAnnual = Number(((annualCTC * basicPct) / 100).toFixed(2));
        } else {
            basicAnnual = Number((basicFlatAmount * 12).toFixed(2));
        }

        if (hraCalcType === 'percentage') {
            hraAnnual = Number(((basicAnnual * hraPct) / 100).toFixed(2));
        } else {
            hraAnnual = Number((hraFlatAmount * 12).toFixed(2));
        }

        if (conveyanceCalcType === 'percentage') {
            convAnnual = Number(sd.conveyance) || 0;
        } else {
            convAnnual = Number((conveyanceFlatAmount * 12).toFixed(2));
        }

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

        if (fixedCalcType === 'percentage') {
            fixedAnnual = Number(
                (annualCTC - basicAnnual - hraAnnual - convAnnual - totalBenefitsAnnual).toFixed(2)
            );
        } else {
            fixedAnnual = Number((fixedFlatAmount * 12).toFixed(2));
        }

        const monthlyCTCExact = annualCTC / 12; 
        const basicMonthlyExact = basicAnnual / 12;
        const hraMonthlyExact = hraAnnual / 12;
        const convMonthlyExact = convAnnual / 12;

        const monthlyCTC = Number(monthlyCTCExact.toFixed(2));
        const basicMonthly = Number(basicMonthlyExact.toFixed(2));
        const hraMonthly = Number(hraMonthlyExact.toFixed(2));
        const convMonthly = Number(convMonthlyExact.toFixed(2));

        let fixedMonthly;
        if (fixedCalcType === 'percentage') {
            fixedMonthly = Number((fixedAnnual / 12).toFixed(2));
        } else {
            fixedMonthly = fixedFlatAmount;
        }

        setFieldValue("salaryDetails.basicMonthly", basicMonthly);
        setFieldValue("salaryDetails.hraMonthly", hraMonthly);
        setFieldValue("salaryDetails.conveyanceMonthly", convMonthly);
        setFieldValue("salaryDetails.fixedMonthly", fixedMonthly);

        setFieldValue("salaryDetails.basic", basicAnnual);
        setFieldValue("salaryDetails.hra", hraAnnual);
        setFieldValue("salaryDetails.conveyanceDisplay", convAnnual);
        setFieldValue("salaryDetails.fixedAllowance", fixedAnnual);

        setFieldValue("salaryDetails.monthlyCTC", monthlyCTC);
        setFieldValue("salaryDetails.annualCTCComputed", annualCTC);
    };

    const handleSubmit = async (values, { setSubmitting }) => {
        console.log("🎯 Submit button clicked");

        if (!earningsLoaded) {
            errorMsg("Error", "Earnings data is still loading. Please wait.", false);
            setSubmitting(false);
            return;
        }

        const reconciliation = getCtcReconciliation(values);
        if (!reconciliation.isValid) {
            errorMsg("Cannot Save", reconciliation.message, false);
            setSubmitting(false);
            return;
        }

        setSigningIn(true);
        try {
            const ctcData = mapToBackendDTO(values, employeeId);
            console.log("🚀 Sending CTC Data:", JSON.stringify(ctcData, null, 2));

            const response = await axios.post(
                `${GlobalConst.API_URL}/api/v1/ctc-structures`,
                ctcData,
                {
                    headers: {
                        "Content-Type": "application/json",
                        Authorization: `Bearer ${token}`,
                        organizationId: organizationId
                    }
                }
            );

            console.log("✅ API Response:", response.data);

            if (response.data && (response.data.status === 201 || response.status === 200)) {
                successMsg("Success", "Salary details saved successfully", false);
                localStorage.setItem('currentEmployeeId', response.data.data.id);
                onNext(); 
            } else {
                errorMsg("Save Failed", "Failed to save salary details", false);
            }
        } catch (error) {
            console.error("❌ API Error:", error);
            if (error.response) {
                console.error("❌ Error Response Data:", error.response.data);
                console.error("❌ Error Response Status:", error.response.status);
                errorMsg("Save Failed", error.response.data?.message || "Failed to save salary details", false);
            } else if (error.request) {
                console.error("❌ No Response Received:", error.request);
                errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
            } else {
                console.error("❌ Request Setup Error:", error.message);
                errorMsg("Error", "An unexpected error occurred", false);
            }
        } finally {
            setSigningIn(false);
            setSubmitting(false);
        }
    };

    const mapToBackendDTO = (values, employeeId) => {
        const { salaryDetails } = values;

        const basic = earnings.find(e => (e.name || e.earningName || "").toLowerCase() === "basic");
        const hra = earnings.find(e => (e.name || e.earningName || "").toLowerCase() === "house rent allowance");
        const conveyance = earnings.find(e => (e.name || e.earningName || "").toLowerCase().includes("conveyance"));
        const fixed = earnings.find(e => (e.name || e.earningName || "").toLowerCase().includes("fixed"));
        const bonusEarning = earnings.find(e => (e.name || e.earningName || "").toLowerCase().includes("bonus"));

        if (!basic) {
            throw new Error("Basic earning not found in preview earnings");
        }
        if (!hra) {
            throw new Error("HRA earning not found in preview earnings");
        }

        const earningsDTO = [
            {
                id: basic.id || basic.earningId,
                enabled: true,
                editable: true,
                isVariable: basic.isVariable ?? false,
                earningFrequency: "monthly",
                amount: toNum(salaryDetails.basicMonthly),
                amountInPercentage:
                    salaryDetails.basicCalculationType === "percentage"
                        ? toNum(salaryDetails.basicPercent)
                        : null,
                overrideAmount:
                    salaryDetails.basicCalculationType === "flat"
                        ? toNum(salaryDetails.basicFlatAmount)
                        : null,
                calculationBasis:
                    salaryDetails.basicCalculationType === "flat"
                        ? "FLAT"
                        : "PERCENTAGE"
            },
            {
                id: hra.id || hra.earningId,
                enabled: true,
                editable: true,
                isVariable: hra.isVariable ?? false,
                earningFrequency: "monthly",
                amount: toNum(salaryDetails.hraMonthly),
                amountInPercentage:
                    salaryDetails.hraCalculationType === "percentage"
                        ? toNum(salaryDetails.hraPercent)
                        : null,
                overrideAmount:
                    salaryDetails.hraCalculationType === "flat"
                        ? toNum(salaryDetails.hraFlatAmount)
                        : null,
                calculationBasis:
                    salaryDetails.hraCalculationType === "flat"
                        ? "FLAT"
                        : "PERCENTAGE"
            }
        ];

        if (conveyance) {
            earningsDTO.push({
                id: conveyance.id || conveyance.earningId,
                enabled: true,
                editable: true,
                isVariable: conveyance.isVariable ?? false,
                earningFrequency: "monthly",
                amount: toNum(salaryDetails.conveyanceMonthly),
                amountInPercentage:
                    salaryDetails.conveyanceCalculationType === "percentage"
                        ? toNum(salaryDetails.conveyancePercent)
                        : null,
                overrideAmount:
                    salaryDetails.conveyanceCalculationType === "flat"
                        ? toNum(salaryDetails.conveyanceFlatAmount)
                        : null,
                calculationBasis:
                    salaryDetails.conveyanceCalculationType === "flat"
                        ? "FLAT"
                        : "PERCENTAGE"
            });
        }

        if (fixed) {
            earningsDTO.push({
                id: fixed.id || fixed.earningId,
                enabled: true,
                editable: true,
                isVariable: fixed.isVariable ?? false,
                earningFrequency: "monthly",
                amount: toNum(salaryDetails.fixedMonthly),
                amountInPercentage:
                    salaryDetails.fixedCalculationType === "percentage"
                        ? 0
                        : null,
                overrideAmount:
                    salaryDetails.fixedCalculationType === "flat"
                        ? toNum(salaryDetails.fixedFlatAmount)
                        : null,
                calculationBasis:
                    salaryDetails.fixedCalculationType === "flat"
                        ? "FLAT"
                        : "PERCENTAGE"
            });
        }

        if (bonusEarning) {
            earningsDTO.push({
                id: bonusEarning.id || bonusEarning.earningId,
                enabled: salaryDetails.includeBonusInCTC ? true : false,
                editable: true,
                isVariable: bonusEarning.isVariable ?? false,
                earningFrequency: salaryDetails.includeBonusInCTC ? (salaryDetails.bonusFrequency || "yearly") : "yearly",
                amount: salaryDetails.includeBonusInCTC ? (Number(salaryDetails.bonus) || 0) : 0,
                amountInPercentage: null,
                overrideAmount: salaryDetails.includeBonusInCTC ? (Number(salaryDetails.bonus) || 0) : null,
                calculationBasis: "FLAT"
            });
        }

        const benefits = calculateStatutoryBenefits(values);
        const basicMonthly = Number(salaryDetails.basicMonthly) || 0;
        const monthlyCTC = Number(salaryDetails.monthlyCTC) || 0;

        const epfComponents = [];
        if (employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled) {
            epfComponents.push({
                componentCode: "EPF_EMPLOYER",
                componentLabel: "EPF - Employer Contribution",
                percentage: "12.00",
                monthlyAmount: benefits.epfEmployerMonthly || 0,
                annualAmount: benefits.epfEmployerAnnual || 0,
                calculationType: "12% of PF Wages"
            });

            if (statutoryConfig.isEdliIncludedSalaryStructure) {
                epfComponents.push({
                    componentCode: "EDLI_EMPLOYER",
                    componentLabel: "EDLI - Employer Contribution",
                    percentage: "0.50",
                    monthlyAmount: benefits.edliMonthly || 0,
                    annualAmount: benefits.edliAnnual || 0,
                    calculationType: "0.50% of PF Wages"
                });
            }

            if (statutoryConfig.isAdminChargesIncludedSalaryStructure) {
                epfComponents.push({
                    componentCode: "EPF_ADMIN",
                    componentLabel: "EPF Admin Charges - Employer Contribution",
                    percentage: "0.50",
                    monthlyAmount: benefits.epfAdminMonthly || 0,
                    annualAmount: benefits.epfAdminAnnual || 0,
                    calculationType: "0.50% of PF Wages"
                });
            }
        }

        const esiComponents = [];
        if (employeeBasicDetails?.eligibleForEsi && statutoryConfig?.esiEnabled) {
            esiComponents.push({
                componentCode: "ESI_EMPLOYER",
                componentLabel: "ESI - Employer Contribution",
                percentage: "3.25",
                monthlyAmount: benefits.esiEmployerMonthly || 0,
                annualAmount: benefits.esiEmployerAnnual || 0,
                calculationType: "3.25% of ESI Wages"
            });
        }

        return {
            employeeId,
            ctc: Number(salaryDetails.AnnualCTCBeforeBonus),
            monthlySalary: salaryDetails.monthlyCTC,
            organizationId,
            earnings: earningsDTO,
            variableEarnings: [],
            benefits: [],
            fbpComponents: [],
            reimbursements: [],
            epfComponents: epfComponents,
            esiComponents: esiComponents
        };
    };

    const canSubmit = earningsLoaded;

    return (
        <>
            <Formik
                initialValues={{
                    ...initialValues,
                    salaryDetails: {
                        ...initialValues.salaryDetails,
                        basicCalculationType: initialValues.salaryDetails?.basicCalculationType || 'percentage',
                        hraCalculationType: initialValues.salaryDetails?.hraCalculationType || 'percentage',
                        conveyanceCalculationType: initialValues.salaryDetails?.conveyanceCalculationType || 'percentage',
                        fixedCalculationType: initialValues.salaryDetails?.fixedCalculationType || 'percentage',
                        basicFlatAmount: initialValues.salaryDetails?.basicFlatAmount || 0,
                        hraFlatAmount: initialValues.salaryDetails?.hraFlatAmount || 0,
                        conveyanceFlatAmount: initialValues.salaryDetails?.conveyanceFlatAmount || 0,
                        fixedFlatAmount: initialValues.salaryDetails?.fixedFlatAmount || 0,
                        AnnualCTCBeforeBonus: initialValues.salaryDetails?.AnnualCTCBeforeBonus !== undefined ? initialValues.salaryDetails.AnnualCTCBeforeBonus : (initialValues.salaryDetails?.annualCTC || ""),
                        bonus: initialValues.salaryDetails?.bonus !== undefined ? initialValues.salaryDetails.bonus : "",
                        bonusFrequency: initialValues.salaryDetails?.bonusFrequency || "",
                        includeBonusInCTC: initialValues.salaryDetails?.includeBonusInCTC !== undefined
                            ? initialValues.salaryDetails.includeBonusInCTC
                            : (Number(initialValues.salaryDetails?.bonus) > 0),
                    }
                }}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize={true}
                validateOnMount={true}
            >
                {({ isSubmitting, errors, touched, setFieldValue, values, isValid, dirty }) => {
                    const currentBenefits = calculateStatutoryBenefits(values);
                    const reconciliation = getCtcReconciliation(values);
                    const componentsValid = reconciliation.isValid;

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
                            <div className="mb-4">
                                {loading && (
                                    <div className="alert alert-info">
                                        <i className="bi bi-info-circle me-2"></i>
                                        Loading earnings data...
                                    </div>
                                )}

                                {!loading && earnings.length === 0 && earningsLoaded && (
                                    <div className="alert alert-warning">
                                        <i className="bi bi-exclamation-triangle me-2"></i>
                                        No earnings components found. Will use fallback configuration.
                                    </div>
                                )}

                                <label className="form-label fs-6 fw-bold text-dark mb-2">
                                    Annual CTC<RequiredStar />
                                </label>
                                <div className="input-group mb-3">
                                    <span className="input-group-text">₹</span>
                                    <Field
                                        type="text"
                                        name="salaryDetails.AnnualCTCBeforeBonus"
                                        inputMode="numeric"
                                        pattern="[0-9]*"
                                        className="form-control"
                                        placeholder="Enter Annual CTC"
                                        value={values.salaryDetails.AnnualCTCBeforeBonus || ""}
                                        onChange={(e) => {
                                            const val = e.target.value.replace(/[^0-9]/g, "");
                                            setFieldValue("salaryDetails.AnnualCTCBeforeBonus", val);
                                            const currentBonus = values.salaryDetails.includeBonusInCTC ? (values.salaryDetails.bonus || 0) : 0;
                                            const calculatedCTC = Math.max(0, Number(val) - Number(currentBonus));
                                            setFieldValue("salaryDetails.annualCTC", calculatedCTC);
                                            calculateSalaryDetails(
                                                { ...values.salaryDetails, annualCTC: calculatedCTC, AnnualCTCBeforeBonus: val },
                                                setFieldValue,
                                                {
                                                    basicCalculationType: values.salaryDetails.basicCalculationType,
                                                    hraCalculationType: values.salaryDetails.hraCalculationType,
                                                    conveyanceCalculationType: values.salaryDetails.conveyanceCalculationType,
                                                    fixedCalculationType: values.salaryDetails.fixedCalculationType
                                                }
                                            );
                                        }}
                                    />
                                    <span className="input-group-text">per year</span>
                                </div>
                                {errors.salaryDetails?.AnnualCTCBeforeBonus && touched.salaryDetails?.AnnualCTCBeforeBonus && (
                                    <div className="text-danger small mb-3">{errors.salaryDetails.AnnualCTCBeforeBonus}</div>
                                )}

                                <div className="form-check form-check-custom form-check-solid mb-3">
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
                                                    bonus: checked ? values.salaryDetails.bonus : ""
                                                },
                                                setFieldValue,
                                                {
                                                    basicCalculationType: values.salaryDetails.basicCalculationType,
                                                    hraCalculationType: values.salaryDetails.hraCalculationType,
                                                    conveyanceCalculationType: values.salaryDetails.conveyanceCalculationType,
                                                    fixedCalculationType: values.salaryDetails.fixedCalculationType
                                                }
                                            );
                                        }}
                                    />
                                    <label className="form-check-label fs-6 fw-bold text-dark" htmlFor="includeBonusInCTC">
                                        Include Bonus in CTC
                                    </label>
                                </div>

                                {values.salaryDetails.includeBonusInCTC && (
                                    <div className="row mb-3">
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
                                                        setFieldValue("salaryDetails.annualCTC", calculatedCTC);
                                                        calculateSalaryDetails(
                                                            { ...values.salaryDetails, annualCTC: calculatedCTC, bonus: val },
                                                            setFieldValue,
                                                            {
                                                                basicCalculationType: values.salaryDetails.basicCalculationType,
                                                                hraCalculationType: values.salaryDetails.hraCalculationType,
                                                                conveyanceCalculationType: values.salaryDetails.conveyanceCalculationType,
                                                                fixedCalculationType: values.salaryDetails.fixedCalculationType
                                                            }
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

                                <label className="form-label fs-6 fw-bold text-dark mb-2">
                                    Final CTC
                                </label>
                                <div className="input-group mb-3">
                                    <span className="input-group-text">₹</span>
                                    <input
                                        type="text"
                                        className="form-control bg-light"
                                        readOnly
                                        value={values.salaryDetails.annualCTC || 0}
                                    />
                                    <span className="input-group-text">per year</span>
                                </div>
                                {errors.salaryDetails?.annualCTC && touched.salaryDetails?.annualCTC && (
                                    <div className="text-danger small mb-3">{errors.salaryDetails.annualCTC}</div>
                                )}

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

                                            {/* Basic Row */}
                                            <tr>
                                                <td>Basic</td>
                                                <td>
                                                    <div className="d-flex flex-column gap-2">
                                                        <div className="d-flex align-items-center gap-3">
                                                            <label className="d-flex align-items-center gap-1">
                                                                <Field
                                                                    type="radio"
                                                                    name="salaryDetails.basicCalculationType"
                                                                    value="percentage"
                                                                    checked={values.salaryDetails.basicCalculationType === 'percentage'}
                                                                    onChange={() => onCalcTypeChange("basic", "percentage")}
                                                                />
                                                                <span>%</span>
                                                            </label>
                                                            <label className="d-flex align-items-center gap-1">
                                                                <Field
                                                                    type="radio"
                                                                    name="salaryDetails.basicCalculationType"
                                                                    value="flat"
                                                                    checked={values.salaryDetails.basicCalculationType === 'flat'}
                                                                    onChange={() => onCalcTypeChange("basic", "flat")}
                                                                />
                                                                <span>Flat Amount</span>
                                                            </label>
                                                        </div>

                                                        {values.salaryDetails.basicCalculationType === 'percentage' ? (
                                                            <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                                                <Field
                                                                    type="text"
                                                                    inputMode="decimal"
                                                                    name="salaryDetails.basicPercent"
                                                                    className="form-control form-control-sm flex-grow-1"
                                                                    style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                    onChange={(e) => {
                                                                        const val = sanitizeDecimal(e.target.value);
                                                                        setFieldValue("salaryDetails.basicPercent", val);
                                                                        calculateSalaryDetails(
                                                                            { ...values.salaryDetails, basicPercent: val },
                                                                            setFieldValue,
                                                                            values.salaryDetails
                                                                        );
                                                                    }}
                                                                />
                                                                <span className="text-muted">% of CTC</span>
                                                            </div>
                                                        ) : (
                                                            <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                                                <span className="input-group-text">₹</span>
                                                                <Field
                                                                    type="number"
                                                                    name="salaryDetails.basicFlatAmount"
                                                                    className="form-control form-control-sm flex-grow-1"
                                                                    style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                    placeholder="Monthly amount"
                                                                    onChange={(e) => {
                                                                        const val = e.target.value;
                                                                        setFieldValue("salaryDetails.basicFlatAmount", val);
                                                                        calculateSalaryDetails(
                                                                            { ...values.salaryDetails, basicFlatAmount: val },
                                                                            setFieldValue,
                                                                            values.salaryDetails
                                                                        );
                                                                    }}
                                                                />
                                                                <span className="text-muted">per month</span>
                                                            </div>
                                                        )}
                                                    </div>
                                                    {errors.salaryDetails?.basicPercent && touched.salaryDetails?.basicPercent && values.salaryDetails.basicCalculationType === 'percentage' && (
                                                        <div className="text-danger small">{errors.salaryDetails.basicPercent}</div>
                                                    )}
                                                </td>
                                                <td className="text-end">₹{values.salaryDetails.basicMonthly}</td>
                                                <td className="text-end">₹{values.salaryDetails.basic}</td>
                                            </tr>

                                            {/* HRA Row */}
                                            <tr>
                                                <td>House Rent Allowance</td>
                                                <td>
                                                    <div className="d-flex flex-column gap-2">
                                                        <div className="d-flex align-items-center gap-3">
                                                            <label className="d-flex align-items-center gap-1">
                                                                <Field
                                                                    type="radio"
                                                                    name="salaryDetails.hraCalculationType"
                                                                    value="percentage"
                                                                    checked={values.salaryDetails.hraCalculationType === 'percentage'}
                                                                    onChange={() => onCalcTypeChange("hra", "percentage")}
                                                                />
                                                                <span>%</span>
                                                            </label>
                                                            <label className="d-flex align-items-center gap-1">
                                                                <Field
                                                                    type="radio"
                                                                    name="salaryDetails.hraCalculationType"
                                                                    value="flat"
                                                                    checked={values.salaryDetails.hraCalculationType === 'flat'}
                                                                    onChange={() => onCalcTypeChange("hra", "flat")}
                                                                />
                                                                <span>Flat Amount</span>
                                                            </label>
                                                        </div>

                                                        {values.salaryDetails.hraCalculationType === 'percentage' ? (
                                                            <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                                                <Field
                                                                    type="text"
                                                                    inputMode="decimal"
                                                                    name="salaryDetails.hraPercent"
                                                                    className="form-control form-control-sm flex-grow-1"
                                                                    style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                    onChange={(e) => {
                                                                        const val = sanitizeDecimal(e.target.value);
                                                                        setFieldValue("salaryDetails.hraPercent", val);
                                                                        calculateSalaryDetails(
                                                                            { ...values.salaryDetails, hraPercent: val },
                                                                            setFieldValue,
                                                                            values.salaryDetails
                                                                        );
                                                                    }}
                                                                />
                                                                <span className="text-muted">% of Basic</span>
                                                            </div>
                                                        ) : (
                                                            <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                                                <span className="input-group-text">₹</span>
                                                                <Field
                                                                    type="number"
                                                                    name="salaryDetails.hraFlatAmount"
                                                                    className="form-control form-control-sm flex-grow-1"
                                                                    style={{ minWidth: "80px", maxWidth: "120px" }}
                                                                    placeholder="Monthly amount"
                                                                    onChange={(e) => {
                                                                        const val = e.target.value;
                                                                        setFieldValue("salaryDetails.hraFlatAmount", val);
                                                                        calculateSalaryDetails(
                                                                            { ...values.salaryDetails, hraFlatAmount: val },
                                                                            setFieldValue,
                                                                            values.salaryDetails
                                                                        );
                                                                    }}
                                                                />
                                                                <span className="text-muted">per month</span>
                                                            </div>
                                                        )}
                                                    </div>
                                                    {errors.salaryDetails?.hraPercent && touched.salaryDetails?.hraPercent && values.salaryDetails.hraCalculationType === 'percentage' && (
                                                        <div className="text-danger small">{errors.salaryDetails.hraPercent}</div>
                                                    )}
                                                </td>
                                                <td className="text-end">₹{values.salaryDetails.hraMonthly}</td>
                                                <td className="text-end">₹{values.salaryDetails.hra}</td>
                                            </tr>

                                            {/* Conveyance Row */}
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
                                                                    onChange={() => onCalcTypeChange("conveyance", "percentage")}
                                                                />
                                                                <span>%</span>
                                                            </label>
                                                            <label className="d-flex align-items-center gap-1">
                                                                <Field
                                                                    type="radio"
                                                                    name="salaryDetails.conveyanceCalculationType"
                                                                    value="flat"
                                                                    checked={values.salaryDetails.conveyanceCalculationType === 'flat'}
                                                                    onChange={() => onCalcTypeChange("conveyance", "flat")}
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
                                                                        calculateSalaryDetails(
                                                                            { ...values.salaryDetails, conveyanceFlatAmount: val },
                                                                            setFieldValue,
                                                                            values.salaryDetails
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
                                                                    onChange={() => onCalcTypeChange("fixed", "percentage")}
                                                                />
                                                                <span>%</span>
                                                            </label>
                                                            <label className="d-flex align-items-center gap-1">
                                                                <Field
                                                                    type="radio"
                                                                    name="salaryDetails.fixedCalculationType"
                                                                    value="flat"
                                                                    checked={values.salaryDetails.fixedCalculationType === 'flat'}
                                                                    onChange={() => onCalcTypeChange("fixed", "flat")}
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

                                {/* Reconcile Alert */}
                                {!componentsValid && values.salaryDetails?.annualCTC > 0 && (
                                    <div className="alert alert-danger d-flex align-items-center mt-3">
                                        <i className="bi bi-exclamation-triangle-fill me-2 fs-4"></i>
                                        <div>{reconciliation.message}</div>
                                    </div>
                                )}
                            </div>

                            <div className="d-flex justify-content-between mt-5">
                                <button
                                    type="button"
                                    className="btn btn-light"
                                    onClick={onPrev}
                                >
                                    Previous
                                </button>
                                <button
                                    type="submit"
                                    className="btn btn-primary"
                                    disabled={isSubmitting || signingIn || !canSubmit || !isValid || !componentsValid}
                                >
                                    {signingIn ? (
                                        <span className="spinner-border spinner-border-sm me-2"></span>
                                    ) : null}
                                    {signingIn ? "Saving..." : "Save and Continue"}
                                </button>
                            </div>
                        </Form>
                    );
                }}
            </Formik>

            {signingIn && <Loader />}
        </>
    );
};

export default SalaryDetails;