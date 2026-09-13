import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate, useParams } from "react-router-dom";
import { Button, message } from 'antd';
import { EditOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";

const RequiredStar = () => <span className="text-danger">*</span>;

function normalizeEarningLabel(codeOrName) {
  return String(codeOrName ?? "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, " ");
}

function findEarningByMatchers(earnings, predicates) {
  if (!Array.isArray(earnings)) return undefined;
  for (const e of earnings) {
    const label = normalizeEarningLabel(
      e.earningCode ?? e.componentName ?? e.name ?? e.earningType ?? ""
    );
    if (!label) continue;
    for (let i = 0; i < predicates.length; i++) {
      if (predicates[i](label)) return e;
    }
  }
  return undefined;
}

const isBasicLabel = (label) =>
  label === "basic" ||
  label === "basic pay" ||
  label.includes("basic pay") ||
  (label.includes("basic") &&
    !label.includes("hra") &&
    !label.includes("house rent"));

const isHraLabel = (label) =>
  label.includes("house rent") ||
  /\bhra\b/.test(label) ||
  label === "hra";

const isConveyanceLabel = (label) =>
  label.includes("conveyance") || label.includes("transport");

const isFixedAllowanceLabel = (label) =>
  label.includes("fixed allowance") ||
  (label.includes("fixed") && label.includes("allowance"));

const isBonusLabel = (label) =>
  label.includes("bonus");

function mapCtcUpdateUserMessage(error) {
  const status = error.response?.status;
  const data = error.response?.data;
  const code = data?.code;
  const msg = typeof data?.message === "string" ? data.message : "";

  if (status === 404 && code === "NO_ACTIVE_CTC") {
    return "No salary structure found for this employee yet. Complete salary in Add Employee (wizard), then try again.";
  }
  if (
    status === 400 &&
    msg.toLowerCase().includes("date of joining")
  ) {
    return "Date of joining is missing or invalid. Update basic details with joining date (YYYY-MM-DD), then try again.";
  }
  return msg || "Failed to update salary details";
}

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

// Tolerance (in rupees) used when checking that the salary components reconcile.
const CTC_RECONCILE_TOLERANCE = 2;

// When the user toggles a component between "percentage" and "flat", convert the
// CURRENT effective amount into the target representation so the rupee value stays continuous.
const buildCalcTypePatch = (component, newType, sd) => {
    const annualCTC = toNum(sd.ctc);
    const basicAnnual = toNum(sd.basicAnnual);

    const currentMonthly = {
        basic: toNum(sd.basicMonthly),
        hra: toNum(sd.hraMonthly),
        conveyance: toNum(sd.conveyanceMonthly),
        fixed: toNum(sd.fixedMonthly),
    }[component];

    const currentAnnual = {
        basic: toNum(sd.basicAnnual),
        hra: toNum(sd.hraAnnual),
        conveyance: toNum(sd.conveyanceAnnual),
        fixed: toNum(sd.fixedAllowanceAnnual),
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

export default function EditSalaryDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [employee, setEmployee] = useState(null);
  const [ctcData, setCtcData] = useState(null);

  const [statutoryConfig, setStatutoryConfig] = useState(null);
  const [employeeBasicDetails, setEmployeeBasicDetails] = useState(null);
  const [isCreateMode, setIsCreateMode] = useState(false);

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");

  // Fetch employee + CTC data
  useEffect(() => {
    const fetchData = async () => {
      try {
        const resp = await axios.get(
          `${GlobalConst.API_URL}/api/employees-portal/employee-profile`,
          {
            params: { employeeId: id },
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            }
          }
        );

        const apiData = resp.data?.data;
        setEmployee(apiData?.basicDetails ?? null);
        setEmployeeBasicDetails(apiData?.basicDetails ?? null);

        const rawCtc = apiData?.ctc ?? null;
        if (rawCtc) {
          const normalizedEarnings = (rawCtc.earnings || []).map((e) => ({
            id: e.id ?? null,
            earningType: e.earningCode ?? null,
            earningCode: e.earningCode ?? null,
            amount: e.amount ?? 0,
            amountInPercentage: e.amountInPercentage ?? null,
            overrideAmount: e.overrideAmount ?? null,
            calculationBasis: e.calculationBasis ?? 'PERCENTAGE',
            editable: typeof e.editable === "boolean" ? e.editable : true,
            enabled: e.enabled ?? true,
            earningFrequency: e.earningFrequency ?? "",
            isVariable: e.isVariable ?? false,
            componentName: e.componentName ?? e.name ?? e.earningCode ?? ""
          }));

          setCtcData({
            ...rawCtc,
            ctcStructureId: rawCtc.ctcStructureId ?? rawCtc.id ?? null,
            earnings: normalizedEarnings,
          });
          setIsCreateMode(false);
          console.log("📝 [EDIT] UPDATE mode - Existing CTC found");
        } else {
          // CREATE MODE - Employee has NO CTC structure
          console.log("⚠️ [EDIT] No CTC found for employee, enabling CREATE mode");
          
          try {
            const previewResp = await axios.get(
              `${GlobalConst.API_URL}/api/employees/salary/preview/${id}`,
              {
                headers: {
                  Authorization: `Bearer ${localStorage.getItem("__t")}`,
                  organizationId: organizationId
                }
              }
            );

            const previewData = previewResp.data?.employee || previewResp.data?.data;
            const earningsTemplate = (previewData?.earnings || []).map((e) => ({
              id: e.id ?? e.earningId ?? null,
              earningCode: e.earningCode ?? e.name ?? "",
              componentName: e.name ?? e.earningCode ?? "",
              amount: 0,
              amountInPercentage: null,
              overrideAmount: null,
              calculationBasis: 'PERCENTAGE',
              editable: true
            }));

            console.log("✅ [EDIT] Fetched earnings template for CREATE mode:", earningsTemplate);

            setCtcData({
              employeeId: id,
              ctc: 0,
              monthlySalary: 0,
              earnings: earningsTemplate,
              benefits: [],
              reimbursements: []
            });
          } catch (previewError) {
            console.error("❌ [EDIT] Failed to fetch earnings preview:", previewError);
            setCtcData({
              employeeId: id,
              ctc: 0,
              monthlySalary: 0,
              earnings: [],
              benefits: [],
              reimbursements: []
            });
          }
          
          setIsCreateMode(true);
          message.info({
            content: "This employee has no salary structure. You can create one now.",
            duration: 5
          });
        }
      } catch (error) {
        console.error("Failed to fetch data:", error);
        if (error.response?.status === 404) {
          console.log("⚠️ [EDIT] Employee fetch failed (404), enabling CREATE mode");
          
          try {
            const previewResp = await axios.get(
              `${GlobalConst.API_URL}/api/employees/salary/preview/${id}`,
              {
                headers: {
                  Authorization: `Bearer ${localStorage.getItem("__t")}`,
                  organizationId: organizationId
                }
              }
            );

            const previewData = previewResp.data?.employee || previewResp.data?.data;
            const earningsTemplate = (previewData?.earnings || []).map((e) => ({
              id: e.id ?? e.earningId ?? null,
              earningCode: e.earningCode ?? e.name ?? "",
              componentName: e.name ?? e.earningCode ?? "",
              amount: 0,
              amountInPercentage: null,
              overrideAmount: null,
              calculationBasis: 'PERCENTAGE',
              editable: true
            }));

            setCtcData({
              employeeId: id,
              ctc: 0,
              monthlySalary: 0,
              earnings: earningsTemplate,
              benefits: [],
              reimbursements: []
            });
            setIsCreateMode(true);
            message.info({
              content: "This employee has no salary structure. You can create one now.",
              duration: 5
            });
          } catch (previewError) {
            console.error("❌ [EDIT] Failed to fetch earnings preview:", previewError);
            setCtcData({
              employeeId: id,
              ctc: 0,
              monthlySalary: 0,
              earnings: [],
              benefits: [],
              reimbursements: []
            });
            setIsCreateMode(true);
          }
          setEmployee(null);
        } else {
          message.error("Failed to load salary data");
        }
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

    fetchData();
    fetchOrgStatutoryConfig();
  }, [id, organizationId]);

  // Validation schema
  const validationSchema = Yup.object().shape({
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
      .when('basicCalculationType', {
        is: 'percentage',
        then: (schema) => schema
          .required("Basic % is required")
          .min(1, "Must be at least 1%")
          .max(100, "Cannot exceed 100%"),
        otherwise: (schema) => schema.notRequired(),
      }),
    basicFlatAmount: Yup.number()
      .typeError("Basic amount must be a number")
      .when('basicCalculationType', {
        is: 'flat',
        then: (schema) => schema
          .required("Basic amount is required")
          .min(0, "Must be 0 or greater"),
        otherwise: (schema) => schema.notRequired(),
      }),
    hraPercent: Yup.number()
      .typeError("HRA % must be a number")
      .when('hraCalculationType', {
        is: 'percentage',
        then: (schema) => schema
          .required("HRA % is required")
          .min(1, "Must be at least 1%")
          .max(100, "Cannot exceed 100%"),
        otherwise: (schema) => schema.notRequired(),
      }),
    hraFlatAmount: Yup.number()
      .typeError("HRA amount must be a number")
      .when('hraCalculationType', {
        is: 'flat',
        then: (schema) => schema
          .required("HRA amount is required")
          .min(0, "Must be 0 or greater"),
        otherwise: (schema) => schema.notRequired(),
      }),
    conveyanceFlatAmount: Yup.number()
      .typeError("Conveyance amount must be a number")
      .when('conveyanceCalculationType', {
        is: 'flat',
        then: (schema) => schema
          .required("Conveyance amount is required")
          .min(0, "Must be 0 or greater"),
        otherwise: (schema) => schema.notRequired(),
      }),
    fixedFlatAmount: Yup.number()
      .typeError("Fixed allowance amount must be a number")
      .when('fixedCalculationType', {
        is: 'flat',
        then: (schema) => schema
          .required("Fixed allowance amount is required")
          .min(0, "Must be 0 or greater"),
        otherwise: (schema) => schema.notRequired(),
      }),
  })
  .test(
      "components-not-all-zero",
      "Salary components cannot all be zero",
      (values) => {
          if (!values) return true;
          if (toNum(values.ctc) <= 0) return true;
          const totalEarnings =
              toNum(values.basicAnnual) +
              toNum(values.hraAnnual) +
              toNum(values.conveyanceAnnual) +
              toNum(values.fixedAllowanceAnnual);
          return totalEarnings > 0;
      }
  )
  .test(
      "fixed-not-negative",
      "Basic + HRA + Conveyance exceed the Annual CTC. Reduce them so the residual Fixed Allowance is not negative.",
      (values) => {
          if (!values) return true;
          return toNum(values.fixedAllowanceAnnual) >= 0;
      }
  );

  const calculateStatutoryBenefits = (values) => {
    const basicMonthly = Number(values.basicMonthly) || 0;
    const grossMonthly = Number(values.monthlySalary) || 0;
    const annualCTC = Number(values.ctc) || 0;

    const benefits = {
        epfEmployerMonthly: 0,
        epfEmployerAnnual: 0,
        esiEmployerMonthly: 0,
        esiEmployerAnnual: 0,
    };

    // PF calculations
    if (employeeBasicDetails?.eligibleForPf && statutoryConfig?.epfEnabled) {
        benefits.epfEmployerMonthly = Math.round(basicMonthly * 12 / 100);
        benefits.epfEmployerAnnual = Math.round(benefits.epfEmployerMonthly * 12);
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

  // Single source of truth for "do the components add up to the Annual CTC?"
  const getCtcReconciliation = (values) => {
    const annualCTC = toNum(values.ctc);

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
        toNum(values.basicAnnual) +
        toNum(values.hraAnnual) +
        toNum(values.conveyanceAnnual) +
        toNum(values.fixedAllowanceAnnual);

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

    if (toNum(values.fixedAllowanceAnnual) < 0) {
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

  // Calculate salary breakdown based on CTC and percentages/flat amounts
  const calculateSalaryDetails = (values, setFieldValue) => {
    const annualCTC = Number(values.ctc) || 0;
    
    const basicCalcType = values.basicCalculationType || 'percentage';
    const hraCalcType = values.hraCalculationType || 'percentage';
    const conveyanceCalcType = values.conveyanceCalculationType || 'percentage';
    const fixedCalcType = values.fixedCalculationType || 'percentage';

    let basicAnnual, hraAnnual, conveyanceAnnual;

    if (basicCalcType === 'percentage') {
      const basicPct = Number(values.basicPercent) || 0;
      basicAnnual = (annualCTC * basicPct) / 100;
    } else {
      const basicFlatMonthly = Number(values.basicFlatAmount) || 0;
      basicAnnual = basicFlatMonthly * 12;
    }

    if (hraCalcType === 'percentage') {
      const hraPct = Number(values.hraPercent) || 0;
      hraAnnual = (basicAnnual * hraPct) / 100;
    } else {
      const hraFlatMonthly = Number(values.hraFlatAmount) || 0;
      hraAnnual = hraFlatMonthly * 12;
    }

    if (conveyanceCalcType === 'percentage') {
      conveyanceAnnual = Number(values.conveyance) || 0;
    } else {
      const conveyanceFlatMonthly = Number(values.conveyanceFlatAmount) || 0;
      conveyanceAnnual = conveyanceFlatMonthly * 12;
    }

    const monthlyCTC = annualCTC / 12;
    const basicMonthly = basicAnnual / 12;
    const hraMonthly = hraAnnual / 12;
    const conveyanceMonthly = conveyanceAnnual / 12;

    const benefits = calculateStatutoryBenefits({
        ...values,
        monthlySalary: monthlyCTC,
        basicMonthly: basicMonthly,
    });

    const totalBenefitsAnnual =
        (benefits.epfEmployerAnnual || 0) +
        (benefits.esiEmployerAnnual || 0);

    let fixedAllowanceAnnual;
    if (fixedCalcType === 'percentage') {
        fixedAllowanceAnnual = Number(
            Math.abs(
                annualCTC -
                basicAnnual -
                hraAnnual -
                conveyanceAnnual -
                totalBenefitsAnnual
            ).toFixed(2)
        );
    } else {
        const fixedFlatMonthly = Number(values.fixedFlatAmount) || 0;
        fixedAllowanceAnnual = fixedFlatMonthly * 12;
    }

    const fixedMonthly = fixedAllowanceAnnual / 12;

    setFieldValue("monthlySalary", monthlyCTC);
    setFieldValue("basicMonthly", basicMonthly);
    setFieldValue("hraMonthly", hraMonthly);
    setFieldValue("conveyanceMonthly", conveyanceMonthly);
    setFieldValue("fixedMonthly", fixedMonthly);
    setFieldValue("basicAnnual", basicAnnual);
    setFieldValue("hraAnnual", hraAnnual);
    setFieldValue("conveyanceAnnual", conveyanceAnnual);
    setFieldValue("fixedAllowanceAnnual", fixedAllowanceAnnual);
  };

  // Prepare earnings data for backend
  const prepareEarningsData = (values) => {
    const earnings = [];

    if (values.basicAnnual >= 0) {
      const basicCalcType = values.basicCalculationType || 'percentage';
      const isPercentage = basicCalcType === 'percentage';
      
      earnings.push({
        id: values.basicEarningId || null,
        earningCode: 'Basic',
        amount: values.basicMonthly || 0,
        amountInPercentage: isPercentage ? Number(values.basicPercent) || 0 : null,
        overrideAmount: !isPercentage ? Number(values.basicFlatAmount) || 0 : null,
        calculationBasis: isPercentage ? 'PERCENTAGE' : 'FLAT',
        componentName: 'Basic Salary',
        editable: true,
        enabled: true,
        isVariable: values.basicIsVariable ?? false,
        earningFrequency: 'monthly'
      });
    }

    if (values.hraAnnual >= 0) {
      const hraCalcType = values.hraCalculationType || 'percentage';
      const isPercentage = hraCalcType === 'percentage';
      
      earnings.push({
        id: values.hraEarningId || null,
        earningCode: 'House Rent Allowance',
        amount: values.hraMonthly || 0,
        amountInPercentage: isPercentage ? Number(values.hraPercent) || 0 : null,
        overrideAmount: !isPercentage ? Number(values.hraFlatAmount) || 0 : null,
        calculationBasis: isPercentage ? 'PERCENTAGE' : 'FLAT',
        componentName: 'House Rent Allowance',
        editable: true,
        enabled: true,
        isVariable: values.hraIsVariable ?? false,
        earningFrequency: 'monthly'
      });
    }

    if (values.conveyanceAnnual >= 0) {
      const conveyanceCalcType = values.conveyanceCalculationType || 'percentage';
      const isPercentage = conveyanceCalcType === 'percentage';
      
      earnings.push({
        id: values.conveyanceEarningId || null,
        earningCode: 'Conveyance Allowance',
        amount: values.conveyanceMonthly || 0,
        amountInPercentage: isPercentage ? 0 : null,
        overrideAmount: !isPercentage ? Number(values.conveyanceFlatAmount) || 0 : null,
        calculationBasis: isPercentage ? 'PERCENTAGE' : 'FLAT',
        componentName: 'Conveyance Allowance',
        editable: true,
        enabled: true,
        isVariable: values.conveyanceIsVariable ?? false,
        earningFrequency: 'monthly'
      });
    }

    if (values.fixedAllowanceAnnual >= 0) {
      const fixedCalcType = values.fixedCalculationType || 'percentage';
      const isPercentage = fixedCalcType === 'percentage';
      
      earnings.push({
        id: values.fixedEarningId || null,
        earningCode: 'Fixed Allowance',
        amount: values.fixedMonthly || 0,
        amountInPercentage: isPercentage ? 0 : null,
        overrideAmount: !isPercentage ? Number(values.fixedFlatAmount) || 0 : null,
        calculationBasis: isPercentage ? 'PERCENTAGE' : 'FLAT',
        componentName: 'Fixed Allowance',
        editable: true,
        enabled: true,
        isVariable: values.fixedIsVariable ?? false,
        earningFrequency: 'monthly'
      });
    }

    if (values.bonusEarningId) {
      earnings.push({
        id: values.bonusEarningId,
        earningCode: 'Bonus',
        enabled: values.includeBonusInCTC ? true : false,
        editable: true,
        isVariable: values.bonusIsVariable ?? false,
        earningFrequency: values.includeBonusInCTC ? (values.bonusFrequency || 'yearly') : 'yearly',
        amount: values.includeBonusInCTC ? (Number(values.bonus) || 0) : 0,
        amountInPercentage: null,
        overrideAmount: values.includeBonusInCTC ? (Number(values.bonus) || 0) : null,
        calculationBasis: 'FLAT',
        componentName: 'Bonus'
      });
    }

    return earnings;
  };

  const prepareEpfComponents = (values) => {
    if (!employeeBasicDetails?.eligibleForPf || !statutoryConfig?.epfEnabled) {
      return [];
    }

    const benefits = calculateStatutoryBenefits(values);
    
    return [
      {
        componentCode: 'EPF_EMPLOYER',
        componentLabel: 'EPF - Employer Contribution',
        percentage: '12',
        monthlyAmount: benefits.epfEmployerMonthly || 0,
        annualAmount: benefits.epfEmployerAnnual || 0,
        calculationType: 'PERCENTAGE'
      }
    ];
  };

  const prepareEsiComponents = (values) => {
    if (!employeeBasicDetails?.eligibleForEsi || !statutoryConfig?.esiEnabled) {
      return [];
    }

    const benefits = calculateStatutoryBenefits(values);
    
    return [
      {
        componentCode: 'ESI_EMPLOYER',
        componentLabel: 'ESI - Employer Contribution',
        percentage: '3.25',
        monthlyAmount: benefits.esiEmployerMonthly || 0,
        annualAmount: benefits.esiEmployerAnnual || 0,
        calculationType: 'PERCENTAGE'
      }
    ];
  };

  const prepareBenefitsData = (values) => {
    const benefitsArray = [];
    
    if (values.benefits && Array.isArray(values.benefits)) {
      benefitsArray.push(...values.benefits.map(benefit => ({
        id: benefit.id || null,
        benefitCode: benefit.benefitCode || '',
        name: benefit.name || benefit.componentName || '',
        enabled: benefit.enabled !== false,
        amount: benefit.amount || 0,
        amountInPercentage: benefit.amountInPercentage || null
      })));
    }
    
    return benefitsArray;
  };

  // Handle form submission
  const handleSubmit = async (values) => {
    const reconciliation = getCtcReconciliation(values);
    if (!reconciliation.isValid) {
      errorMsg("Cannot Save", reconciliation.message, false);
      return;
    }

    setSubmitting(true);
    try {
      const updateData = {
        employeeId: id,
        ctc: Number(values.AnnualCTCBeforeBonus),
        monthlySalary: Number(values.monthlySalary),
        earnings: prepareEarningsData(values),
        benefits: prepareBenefitsData(values),
        reimbursements: values.reimbursements || [],
        epfComponents: prepareEpfComponents(values),
        esiComponents: prepareEsiComponents(values),
        organizationId: organizationId
      };

      if (!isCreateMode && ctcData?.ctcStructureId) {
        updateData.ctcStructureId = ctcData.ctcStructureId;
      }

      console.log("🔍 [SALARY] Submit triggered");
      console.log(`➡️ Mode: ${isCreateMode ? 'CREATE (POST)' : 'UPDATE (PUT)'}`);
      console.log("➡️ Received ctcData:", ctcData);
      console.log("➡️ updateData to send:", updateData);

      let response;
      
      if (isCreateMode) {
        console.log("🚀 [SALARY] Submitting via POST (create)");
        response = await axios.post(
          `${GlobalConst.API_URL}/api/v1/ctc-structures`,
          updateData,
          {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          }
        );
      } else {
        console.log("📝 [SALARY] Submitting via PUT (update)");
        response = await axios.put(
          `${GlobalConst.API_URL}/api/v1/ctc-structures`,
          updateData,
          {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          }
        );
      }

      const envelopeStatus = response.data?.status;
      const httpOk = response.status === 200 || response.status === 201;
      const envelopeOk =
        envelopeStatus === undefined ||
        envelopeStatus === 200 ||
        envelopeStatus === 201;

      if (httpOk && envelopeOk) {
        const successMessage = isCreateMode 
          ? "Salary structure created successfully" 
          : "Salary details updated successfully";
        successMsg("Success", successMessage, false);
        
        if (isCreateMode && response.data?.data?.ctcStructureId) {
          console.log("✅ [SALARY] CTC created, switching to UPDATE mode");
          setIsCreateMode(false);
          setCtcData({
            ...ctcData,
            ctcStructureId: response.data.data.ctcStructureId
          });
        }
        
        navigate(`/employees/view/${id}`);
      } else {
        errorMsg(
          isCreateMode ? "Create Failed" : "Update Failed",
          response.data?.message || `Failed to ${isCreateMode ? 'create' : 'update'} salary details`,
          false
        );
      }
    } catch (error) {
      console.error("❌ [SALARY] API Error:", error);
      if (error.response) {
        errorMsg(
          isCreateMode ? "Create Failed" : "Update Failed",
          mapCtcUpdateUserMessage(error),
          false
        );
      } else if (error.request) {
        errorMsg(
          "Network Error",
          "Cannot connect to the server. Please check your connection.",
          false
        );
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const getInitialValues = () => {
    if (!ctcData) {
      return {
        AnnualCTCBeforeBonus: 0,
        ctc: 0,
        includeBonusInCTC: false,
        bonus: "",
        bonusFrequency: "",
        bonusEarningId: null,
        bonusIsVariable: false,
        basicCalculationType: 'percentage',
        basicPercent: 50,
        basicFlatAmount: 0,
        basicIsVariable: false,
        hraCalculationType: 'percentage',
        hraPercent: 50,
        hraFlatAmount: 0,
        hraIsVariable: false,
        conveyanceCalculationType: 'percentage',
        conveyance: 0,
        conveyanceFlatAmount: 0,
        conveyanceIsVariable: false,
        fixedCalculationType: 'percentage',
        fixedFlatAmount: 0,
        fixedIsVariable: false,
        monthlySalary: 0,
        basicMonthly: 0,
        hraMonthly: 0,
        conveyanceMonthly: 0,
        fixedMonthly: 0,
        basicAnnual: 0,
        hraAnnual: 0,
        conveyanceAnnual: 0,
        fixedAllowanceAnnual: 0,
        benefits: [],
        reimbursements: []
      };
    }

    const basicEarning = findEarningByMatchers(ctcData.earnings, [isBasicLabel]);
    const hraEarning = findEarningByMatchers(ctcData.earnings, [isHraLabel]);
    const conveyanceEarning = findEarningByMatchers(ctcData.earnings, [isConveyanceLabel]);
    const fixedEarning = findEarningByMatchers(ctcData.earnings, [isFixedAllowanceLabel]);
    const bonusEarning = findEarningByMatchers(ctcData.earnings, [isBonusLabel]);

    const includeBonusInCTC = bonusEarning?.enabled ?? false;
    const bonusAmount = includeBonusInCTC ? (bonusEarning?.overrideAmount || bonusEarning?.amount || 0) : 0;
    const bonusFreqValue = bonusEarning?.earningFrequency || "";

    const basicMonthlyAmount = basicEarning?.amount || 0;
    const hraMonthlyAmount = hraEarning?.amount || 0;
    const conveyanceMonthlyAmount = conveyanceEarning?.amount || 0;
    const fixedMonthlyAmount = fixedEarning?.amount || 0;

    const basicAnnual = basicMonthlyAmount * 12;
    const hraAnnual = hraMonthlyAmount * 12;
    const conveyanceAnnual = conveyanceMonthlyAmount * 12;
    const fixedAllowanceAnnual = fixedMonthlyAmount * 12;

    let basicCalculationType = 'percentage';
    let basicPercent = 50;
    let basicFlatAmount = 0;

    if (basicEarning) {
      if (basicEarning.calculationBasis === 'FLAT') {
        basicCalculationType = 'flat';
        basicFlatAmount = basicEarning.overrideAmount || (basicAnnual / 12);
      } else {
        basicCalculationType = 'percentage';
        if (basicEarning.amountInPercentage !== null && basicEarning.amountInPercentage !== undefined) {
          basicPercent = basicEarning.amountInPercentage;
        } else if (ctcData.ctc > 0 && basicAnnual > 0) {
          basicPercent = (basicAnnual / ctcData.ctc) * 100;
        }
      }
    }

    let hraCalculationType = 'percentage';
    let hraPercent = 50;
    let hraFlatAmount = 0;

    if (hraEarning) {
      if (hraEarning.calculationBasis === 'FLAT') {
        hraCalculationType = 'flat';
        hraFlatAmount = hraEarning.overrideAmount || (hraAnnual / 12);
      } else {
        hraCalculationType = 'percentage';
        if (hraEarning.amountInPercentage !== null && hraEarning.amountInPercentage !== undefined) {
          hraPercent = hraEarning.amountInPercentage;
        } else if (basicAnnual > 0 && hraAnnual > 0) {
          hraPercent = (hraAnnual / basicAnnual) * 100;
        }
      }
    }

    let conveyanceCalculationType = 'percentage';
    let conveyanceFlatAmount = 0;

    if (conveyanceEarning) {
      if (conveyanceEarning.calculationBasis === 'FLAT') {
        conveyanceCalculationType = 'flat';
        conveyanceFlatAmount = conveyanceEarning.overrideAmount || (conveyanceAnnual / 12);
      } else {
        conveyanceCalculationType = 'percentage';
      }
    }

    let fixedCalculationType = 'percentage';
    let fixedFlatAmount = 0;

    if (fixedEarning) {
      if (fixedEarning.calculationBasis === 'FLAT') {
        fixedCalculationType = 'flat';
        fixedFlatAmount = fixedEarning.overrideAmount || (fixedAllowanceAnnual / 12);
      } else {
        fixedCalculationType = 'percentage';
      }
    }

    basicPercent = Math.round(basicPercent * 100) / 100;
    hraPercent = Math.round(hraPercent * 100) / 100;

    return {
      AnnualCTCBeforeBonus: ctcData.ctc || 0,
      ctc: Math.max(0, (ctcData.ctc || 0) - (includeBonusInCTC ? bonusAmount : 0)),
      includeBonusInCTC: includeBonusInCTC,
      bonus: includeBonusInCTC ? bonusAmount : "",
      bonusFrequency: bonusFreqValue,
      bonusEarningId: bonusEarning?.id || null,
      bonusIsVariable: bonusEarning?.isVariable ?? false,
      basicCalculationType: basicCalculationType,
      basicPercent: basicPercent,
      basicFlatAmount: basicFlatAmount,
      basicIsVariable: basicEarning?.isVariable ?? false,
      hraCalculationType: hraCalculationType,
      hraPercent: hraPercent,
      hraFlatAmount: hraFlatAmount,
      hraIsVariable: hraEarning?.isVariable ?? false,
      conveyanceCalculationType: conveyanceCalculationType,
      conveyance: conveyanceAnnual || 0,
      conveyanceFlatAmount: conveyanceFlatAmount,
      conveyanceIsVariable: conveyanceEarning?.isVariable ?? false,
      fixedCalculationType: fixedCalculationType,
      fixedFlatAmount: fixedFlatAmount,
      fixedIsVariable: fixedEarning?.isVariable ?? false,
      monthlySalary: ctcData.monthlySalary || 0,
      basicMonthly: basicAnnual / 12 || 0,
      hraMonthly: hraAnnual / 12 || 0,
      conveyanceMonthly: conveyanceAnnual / 12 || 0,
      fixedMonthly: fixedAllowanceAnnual / 12 || 0,
      basicAnnual: basicAnnual,
      hraAnnual: hraAnnual,
      conveyanceAnnual: conveyanceAnnual,
      fixedAllowanceAnnual: fixedAllowanceAnnual,
      basicEarningId: basicEarning?.id || null,
      hraEarningId: hraEarning?.id || null,
      conveyanceEarningId: conveyanceEarning?.id || null,
      fixedEarningId: fixedEarning?.id || null,
      benefits: ctcData.benefits || [],
      reimbursements: ctcData.reimbursements || []
    };
  };

  if (loading) {
    return <Loader />;
  }

  if (!employee) {
    return (
      <div className="container-fluid p-10 bg-white">
        <div className="text-center">
          <h3>Employee not found</h3>
          <Button type="primary" onClick={() => navigate('/employees')}>
            Back to Employees
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Salary Details</title>
      </Helmet>

      {/* Header */}
      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <div className="d-flex align-items-center gap-3">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(`/employees/view/${id}`)}
          >
            Back
          </Button>
          <div>
            <h6 className="mb-0 fw-semibold">
              Edit Salary Details - {employee.employeeNumber}
            </h6>
            <small className="text-muted">
              {employee.firstName} {employee.lastName}
            </small>
          </div>
        </div>
      </div>

      {/* Form */}
      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid">
          <div className="container-fluid p-4 bg-white">
            <div className="card">
              <div className="card-body">
                <Formik
                  initialValues={getInitialValues()}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize={true}
                  validateOnMount={true}
                >
                 {({ values, setFieldValue, errors, touched, isValid, isSubmitting }) => {
                    const currentBenefits = calculateStatutoryBenefits(values);
                    const reconciliation = getCtcReconciliation(values);
                    const componentsValid = reconciliation.isValid;

                    const onCalcTypeChange = (component, newType) => {
                        const patch = buildCalcTypePatch(component, newType, values);
                        Object.entries(patch).forEach(([key, val]) => {
                            setFieldValue(key, val);
                        });
                        const mergedValues = { ...values, ...patch };
                        calculateSalaryDetails(mergedValues, setFieldValue);
                    };

                    return (
                      <Form className="form w-100">
                        {/* Annual CTC Input */}
                        <div className="mb-4">
                          <label className="form-label fs-6 fw-bold text-dark mb-2">
                            Annual CTC <RequiredStar />
                          </label>
                          <div className="input-group mb-3">
                            <span className="input-group-text">₹</span>
                            <Field
                              type="text"
                              name="AnnualCTCBeforeBonus"
                              inputMode="numeric"
                              pattern="[0-9]*"
                              className={`form-control ${errors.AnnualCTCBeforeBonus && touched.AnnualCTCBeforeBonus ? "is-invalid" : ""}`}
                              value={values.AnnualCTCBeforeBonus || ""}
                              onChange={(e) => {
                                const val = e.target.value.replace(/[^0-9]/g, "");
                                setFieldValue("AnnualCTCBeforeBonus", val);
                                const currentBonus = values.includeBonusInCTC ? (values.bonus || 0) : 0;
                                const calculatedCTC = Math.max(0, Number(val) - Number(currentBonus));
                                setFieldValue("ctc", calculatedCTC);
                                calculateSalaryDetails(
                                  { ...values, ctc: calculatedCTC, AnnualCTCBeforeBonus: val },
                                  setFieldValue
                                );
                              }}
                              placeholder="Enter Annual CTC"
                            />
                            <span className="input-group-text">per year</span>
                          </div>
                          {errors.AnnualCTCBeforeBonus && touched.AnnualCTCBeforeBonus && (
                            <div className="text-danger small mb-3">{errors.AnnualCTCBeforeBonus}</div>
                          )}
                        </div>

                        {/* Include Bonus checkbox */}
                        <div className="form-check form-check-custom form-check-solid mb-3">
                          <Field
                            type="checkbox"
                            name="includeBonusInCTC"
                            className="form-check-input me-2"
                            style={{ border: "1px solid #3f4254" }}
                            id="includeBonusInCTC"
                            onChange={(e) => {
                              const checked = e.target.checked;
                              setFieldValue("includeBonusInCTC", checked);
                              const beforeBonus = values.AnnualCTCBeforeBonus || 0;
                              const bonusVal = checked ? (values.bonus || 0) : 0;
                              const calculatedCTC = Math.max(0, Number(beforeBonus) - Number(bonusVal));
                              setFieldValue("ctc", calculatedCTC);
                              if (!checked) {
                                setFieldValue("bonus", "");
                                setFieldValue("bonusFrequency", "");
                              }
                              calculateSalaryDetails(
                                {
                                  ...values,
                                  ctc: calculatedCTC,
                                  includeBonusInCTC: checked,
                                  bonus: checked ? values.bonus : ""
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
                        {values.includeBonusInCTC && (
                          <div className="row mb-3">
                            <div className="col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark mb-2">
                                Bonus Frequency
                              </label>
                              <Field
                                as="select"
                                name="bonusFrequency"
                                className="form-select"
                              >
                                <option value="">Select Bonus Frequency</option>
                                <option value="yearly">Yearly</option>
                                <option value="half-yearly">Half yearly</option>
                                <option value="quarterly">Quarterly</option>
                              </Field>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark mb-2">
                                Bonus Amount
                              </label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="text"
                                  name="bonus"
                                  inputMode="numeric"
                                  pattern="[0-9]*"
                                  className="form-control"
                                  placeholder="Enter Bonus Amount"
                                  value={values.bonus || ""}
                                  onChange={(e) => {
                                    const val = e.target.value.replace(/[^0-9]/g, "");
                                    setFieldValue("bonus", val);
                                    const currentBeforeBonus = values.AnnualCTCBeforeBonus || 0;
                                    const calculatedCTC = Math.max(0, Number(currentBeforeBonus) - Number(val));
                                    setFieldValue("ctc", calculatedCTC);
                                    calculateSalaryDetails(
                                      { ...values, ctc: calculatedCTC, bonus: val },
                                      setFieldValue
                                    );
                                  }}
                                />
                                <span className="input-group-text">per year</span>
                              </div>
                              {errors.bonus && touched.bonus && (
                                <div className="text-danger small mt-1">{errors.bonus}</div>
                              )}
                            </div>
                          </div>
                        )}

                        {/* Annual CTC (Actual Salary) */}
                        <label className="form-label fs-6 fw-bold text-dark mb-2">
                          Final CTC
                        </label>
                        <div className="input-group mb-3">
                          <span className="input-group-text">₹</span>
                          <input
                            type="text"
                            className="form-control bg-light"
                            readOnly
                            value={values.ctc || 0}
                          />
                          <span className="input-group-text">per year</span>
                        </div>

                        {/* Salary Structure Table */}
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

                              {/* Basic Salary Row */}
                              <tr>
                                <td>Basic</td>
                                <td>
                                  <div className="d-flex flex-column gap-2">
                                    <div className="d-flex align-items-center gap-3">
                                      <label className="d-flex align-items-center gap-1">
                                        <Field
                                          type="radio"
                                          name="basicCalculationType"
                                          value="percentage"
                                          checked={values.basicCalculationType === 'percentage'}
                                          onChange={() => onCalcTypeChange("basic", "percentage")}
                                        />
                                        <span>%</span>
                                      </label>
                                      <label className="d-flex align-items-center gap-1">
                                        <Field
                                          type="radio"
                                          name="basicCalculationType"
                                          value="flat"
                                          checked={values.basicCalculationType === 'flat'}
                                          onChange={() => onCalcTypeChange("basic", "flat")}
                                        />
                                        <span>Flat Amount</span>
                                      </label>
                                    </div>

                                    {values.basicCalculationType === 'percentage' ? (
                                      <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                        <Field
                                          type="text"
                                          inputMode="decimal"
                                          name="basicPercent"
                                          className={`form-control form-control-sm flex-grow-1 ${errors.basicPercent && touched.basicPercent ? "is-invalid" : ""}`}
                                          style={{ minWidth: "80px", maxWidth: "120px" }}
                                          value={values.basicPercent || ""}
                                          onChange={(e) => {
                                            const val = sanitizeDecimal(e.target.value);
                                            setFieldValue("basicPercent", val);
                                            calculateSalaryDetails(
                                              { ...values, basicPercent: val },
                                              setFieldValue
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
                                          name="basicFlatAmount"
                                          className={`form-control form-control-sm flex-grow-1 ${errors.basicFlatAmount && touched.basicFlatAmount ? "is-invalid" : ""}`}
                                          style={{ minWidth: "80px", maxWidth: "120px" }}
                                          placeholder="Monthly amount"
                                          value={values.basicFlatAmount || ""}
                                          onChange={(e) => {
                                            const val = e.target.value;
                                            setFieldValue("basicFlatAmount", val);
                                            calculateSalaryDetails(
                                              { ...values, basicFlatAmount: val },
                                              setFieldValue
                                            );
                                          }}
                                        />
                                        <span className="text-muted">per month</span>
                                      </div>
                                    )}
                                  </div>
                                  {values.basicCalculationType === 'percentage' ? (
                                    <ErrorMessage
                                      name="basicPercent"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  ) : (
                                    <ErrorMessage
                                      name="basicFlatAmount"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  )}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.basicMonthly?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.basicAnnual?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
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
                                          name="hraCalculationType"
                                          value="percentage"
                                          checked={values.hraCalculationType === 'percentage'}
                                          onChange={() => onCalcTypeChange("hra", "percentage")}
                                        />
                                        <span>%</span>
                                      </label>
                                      <label className="d-flex align-items-center gap-1">
                                        <Field
                                          type="radio"
                                          name="hraCalculationType"
                                          value="flat"
                                          checked={values.hraCalculationType === 'flat'}
                                          onChange={() => onCalcTypeChange("hra", "flat")}
                                        />
                                        <span>Flat Amount</span>
                                      </label>
                                    </div>

                                    {values.hraCalculationType === 'percentage' ? (
                                      <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                        <Field
                                          type="text"
                                          inputMode="decimal"
                                          name="hraPercent"
                                          className={`form-control form-control-sm flex-grow-1 ${errors.hraPercent && touched.hraPercent ? "is-invalid" : ""}`}
                                          style={{ minWidth: "80px", maxWidth: "120px" }}
                                          value={values.hraPercent || ""}
                                          onChange={(e) => {
                                            const val = sanitizeDecimal(e.target.value);
                                            setFieldValue("hraPercent", val);
                                            calculateSalaryDetails(
                                              { ...values, hraPercent: val },
                                              setFieldValue
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
                                          name="hraFlatAmount"
                                          className={`form-control form-control-sm flex-grow-1 ${errors.hraFlatAmount && touched.hraFlatAmount ? "is-invalid" : ""}`}
                                          style={{ minWidth: "80px", maxWidth: "120px" }}
                                          placeholder="Monthly amount"
                                          value={values.hraFlatAmount || ""}
                                          onChange={(e) => {
                                            const val = e.target.value;
                                            setFieldValue("hraFlatAmount", val);
                                            calculateSalaryDetails(
                                              { ...values, hraFlatAmount: val },
                                              setFieldValue
                                            );
                                          }}
                                        />
                                        <span className="text-muted">per month</span>
                                      </div>
                                    )}
                                  </div>
                                  {values.hraCalculationType === 'percentage' ? (
                                    <ErrorMessage
                                      name="hraPercent"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  ) : (
                                    <ErrorMessage
                                      name="hraFlatAmount"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  )}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.hraMonthly?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.hraAnnual?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
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
                                          name="conveyanceCalculationType"
                                          value="percentage"
                                          checked={values.conveyanceCalculationType === 'percentage'}
                                          onChange={() => onCalcTypeChange("conveyance", "percentage")}
                                        />
                                        <span>%</span>
                                      </label>
                                      <label className="d-flex align-items-center gap-1">
                                        <Field
                                          type="radio"
                                          name="conveyanceCalculationType"
                                          value="flat"
                                          checked={values.conveyanceCalculationType === 'flat'}
                                          onChange={() => onCalcTypeChange("conveyance", "flat")}
                                        />
                                        <span>Flat Amount</span>
                                      </label>
                                    </div>

                                    {values.conveyanceCalculationType === 'percentage' ? (
                                      <div className="text-muted mt-1">Fixed amount (pre-configured)</div>
                                    ) : (
                                      <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                        <span className="input-group-text">₹</span>
                                        <Field
                                          type="number"
                                          name="conveyanceFlatAmount"
                                          className={`form-control form-control-sm flex-grow-1 ${errors.conveyanceFlatAmount && touched.conveyanceFlatAmount ? "is-invalid" : ""}`}
                                          style={{ minWidth: "80px", maxWidth: "120px" }}
                                          placeholder="Monthly amount"
                                          value={values.conveyanceFlatAmount || ""}
                                          onChange={(e) => {
                                            const val = e.target.value;
                                            setFieldValue("conveyanceFlatAmount", val);
                                            calculateSalaryDetails(
                                              { ...values, conveyanceFlatAmount: val },
                                              setFieldValue
                                            );
                                          }}
                                        />
                                        <span className="text-muted">per month</span>
                                      </div>
                                    )}
                                  </div>
                                  {values.conveyanceCalculationType === 'flat' && (
                                    <ErrorMessage
                                      name="conveyanceFlatAmount"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  )}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.conveyanceMonthly?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.conveyanceAnnual?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                              </tr>

                              {/* Fixed Allowance Row */}
                              <tr>
                                <td>
                                  Fixed Allowance{" "}
                                  <span
                                    className="ms-1"
                                    data-bs-toggle="tooltip"
                                    title="Remaining amount after Basic, HRA and Conveyance"
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
                                          name="fixedCalculationType"
                                          value="percentage"
                                          checked={values.fixedCalculationType === 'percentage'}
                                          onChange={() => onCalcTypeChange("fixed", "percentage")}
                                        />
                                        <span>%</span>
                                      </label>
                                      <label className="d-flex align-items-center gap-1">
                                        <Field
                                          type="radio"
                                          name="fixedCalculationType"
                                          value="flat"
                                          checked={values.fixedCalculationType === 'flat'}
                                          onChange={() => onCalcTypeChange("fixed", "flat")}
                                        />
                                        <span>Flat Amount</span>
                                      </label>
                                    </div>

                                    {values.fixedCalculationType === 'percentage' ? (
                                      <div className="text-muted mt-1">Auto-calculated (residual)</div>
                                    ) : (
                                      <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                                        <span className="input-group-text">₹</span>
                                        <Field
                                          type="number"
                                          name="fixedFlatAmount"
                                          className={`form-control form-control-sm flex-grow-1 ${errors.fixedFlatAmount && touched.fixedFlatAmount ? "is-invalid" : ""}`}
                                          style={{ minWidth: "80px", maxWidth: "120px" }}
                                          placeholder="Monthly amount"
                                          value={values.fixedFlatAmount || ""}
                                          onChange={(e) => {
                                            const val = e.target.value;
                                            setFieldValue("fixedFlatAmount", val);
                                            calculateSalaryDetails(
                                              { ...values, fixedFlatAmount: val },
                                              setFieldValue
                                            );
                                          }}
                                        />
                                        <span className="text-muted">per month</span>
                                      </div>
                                    )}
                                  </div>
                                  {values.fixedCalculationType === 'flat' && (
                                    <ErrorMessage
                                      name="fixedFlatAmount"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  )}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.fixedMonthly?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.fixedAllowanceAnnual?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                              </tr>

                              <tr>
                                <td colSpan={4}>
                                  <label className="form-label fs-6 fw-bold text-dark mb-2">
                                    Benefits
                                  </label>
                                </td>
                              </tr>

                              {employeeBasicDetails?.eligibleForPf && (
                              <tr>
                                <td>EPF - Employer Contribution</td>
                                <td>12% of PF Wages</td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{currentBenefits.epfEmployerMonthly?.toLocaleString('en-IN', {
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                  })}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{currentBenefits.epfEmployerAnnual?.toLocaleString('en-IN', {
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                  })}
                                </td>
                              </tr>
                              )}

                              {employeeBasicDetails?.eligibleForEsi && (
                              <tr>
                                <td>ESI - Employer Contribution</td>
                                <td>3.25% of Gross Salary</td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{currentBenefits.esiEmployerMonthly?.toLocaleString('en-IN', {
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                  })}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{currentBenefits.esiEmployerAnnual?.toLocaleString('en-IN', {
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                  })}
                                </td>
                              </tr>
                              )}
                            </tbody>

                            {/* Footer with Totals */}
                            <tfoot>
                              <tr
                                className="fw-bold"
                                style={{ backgroundColor: "#F6F8FF" }}
                              >
                                <td colSpan={2}>Cost to Company</td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.monthlySalary?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                                <td className="text-end fw-bold text-gray-800">
                                  ₹{values.ctc?.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                </td>
                              </tr>
                            </tfoot>
                          </table>
                        </div>

                        {/* Reconcile Alert */}
                        {!componentsValid && values.ctc > 0 && (
                          <div className="alert alert-danger d-flex align-items-center mt-3">
                            <i className="bi bi-exclamation-triangle-fill me-2 fs-4"></i>
                            <div>{reconciliation.message}</div>
                          </div>
                        )}

                        {/* Form Actions */}
                        <div className="d-flex justify-content-end gap-3 mt-5">
                          <Button
                            type="default"
                            onClick={() => navigate(`/employees/view/${id}`)}
                          >
                            Cancel
                          </Button>
                          <Button
                            type="primary"
                            htmlType="submit"
                            loading={submitting}
                            disabled={!isValid || !componentsValid}
                            icon={<EditOutlined />}
                          >
                            {isCreateMode ? "Create Salary Structure" : "Update Salary Details"}
                          </Button>
                        </div>
                      </Form>
                    );
                }}
                </Formik>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}