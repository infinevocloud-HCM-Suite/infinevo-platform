import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { getDecodedToken } from "../../../../shared/helpers/tokenHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";

export default function UserProofEdit() {
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [declarationData, setDeclarationData] = useState(null);
  const [fiscalYear, setFiscalYear] = useState(2025); // Default to current fiscal year
  const [employeeId, setEmployeeId] = useState("");

  const [showRentHouse, setShowRentHouse] = useState(false);
  const [showHomeLoan, setShowHomeLoan] = useState(false);
  const [showRentalIncome, setShowRentalIncome] = useState(false);
  const [rentedHouses, setRentedHouses] = useState([{ tempId: Date.now() }]);
  const [letOutProperties, setLetOutProperties] = useState([{ tempId: Date.now() }]);
  const [investment80C, setInvestment80C] = useState([{ tempId: Date.now() }]);
  const [exemption80D, setExemption80D] = useState([{ tempId: Date.now() }]);
  const [otherInvestments, setOtherInvestments] = useState([{ tempId: Date.now() }]);

  // Add state for home loan, other incomes, and previous employment
  const [homeLoanDetails, setHomeLoanDetails] = useState({
    principalPaid: "",
    interestPaid: "",
    lenderName: "",
    lenderPan: ""
  });

  const [otherIncomeDetails, setOtherIncomeDetails] = useState({
    otherSourcesIncome: "",
    savingsInterest: "",
    fdInterest: "",
    nscInterest: ""
  });

  const [previousEmploymentDetails, setPreviousEmploymentDetails] = useState({
    incomeAfterExemptions: "",
    incomeTax: "",
    professionalTax: "",
    employeePF: "",
    leaveEncashment: ""
  });

  const [showTaxCalculation, setShowTaxCalculation] = useState(false);
  const [calculatedTax, setCalculatedTax] = useState("20,280.00");
  const [taxSavings, setTaxSavings] = useState("20,280.00");

  // Section 6A items from backend for dropdowns and max limits
  const [section6aItems, setSection6aItems] = useState([]);
  const [investment80COptions, setInvestment80COptions] = useState([]);
  const [exemption80DOptions, setExemption80DOptions] = useState([]);
  const [otherInvestmentOptions, setOtherInvestmentOptions] = useState([]);

  // Validation errors for dynamic fields
  const [validationErrors, setValidationErrors] = useState({
    investment80C: [],
    exemption80D: [],
    otherInvestments: []
  });

  // Card expanded states
  const [expandedCards, setExpandedCards] = useState({
    rentHouse: false,
    homeLoan: false,
    rentalIncome: false,
    otherSources: true,
    investments80C: true,
    exemptions80D: true,
    otherInvestments: true,
    previousEmployment: true,
    taxCalculation: true
  });

  const toggleCard = (cardName) => {
    setExpandedCards(prev => ({
      ...prev,
      [cardName]: !prev[cardName]
    }));
  };

  // Initialize employee ID from token
  useEffect(() => {
    const decodedToken = getDecodedToken();
    if (decodedToken?.sub) {
      setEmployeeId(decodedToken.sub);
      fetchDeclarationData(decodedToken.sub);
    }
  }, []);

  // Get max limit for a specific type
  const getMaxLimitForType = (type, category) => {
    if (!type || !section6aItems.length) return null;

    const item = section6aItems.find(item =>
      item.type === type ||
      item.typeFormatted === type ||
      (item.category === category && item.type === type)
    );

    return item?.maxLimit || null;
  };

  // Get max limit formatted for display
  const getMaxLimitFormattedForType = (type, category) => {
    if (!type || !section6aItems.length) return "No limit specified";

    const item = section6aItems.find(item =>
      item.type === type ||
      item.typeFormatted === type ||
      (item.category === category && item.type === type)
    );

    return item?.maxLimitFormatted || "No limit specified";
  };

  // Validate individual amount against max limit
  const validateAmountAgainstMaxLimit = (type, amount, category) => {
    const maxLimit = getMaxLimitForType(type, category);
    if (maxLimit === null || maxLimit === undefined) {
      return { isValid: true, message: "" };
    }

    const amountNum = parseFloat(amount) || 0;
    if (amountNum > maxLimit) {
      return {
        isValid: false,
        message: `Amount exceeds maximum limit of ${getMaxLimitFormattedForType(type, category)}`
      };
    }

    return { isValid: true, message: "" };
  };

  // Update validation errors for a specific field
  const updateValidationError = (fieldType, index, error) => {
    setValidationErrors(prev => {
      const newErrors = [...prev[fieldType]];
      newErrors[index] = error;
      return {
        ...prev,
        [fieldType]: newErrors
      };
    });
  };

  // Validate all dynamic fields before submission
  const validateDynamicFields = () => {
    let isValid = true;
    const newErrors = {
      investment80C: [],
      exemption80D: [],
      otherInvestments: []
    };

    // Validate 80C investments
    investment80C.forEach((inv, index) => {
      if (inv.investmentType && inv.amount) {
        const validation = validateAmountAgainstMaxLimit(inv.investmentType, inv.amount, "80C");
        if (!validation.isValid) {
          newErrors.investment80C[index] = validation.message;
          isValid = false;
        }
      }
    });

    // Validate 80D exemptions
    exemption80D.forEach((ex, index) => {
      if (ex.exemptionType && ex.amount) {
        const validation = validateAmountAgainstMaxLimit(ex.exemptionType, ex.amount, "80D");
        if (!validation.isValid) {
          newErrors.exemption80D[index] = validation.message;
          isValid = false;
        }
      }
    });

    // Validate other investments
    otherInvestments.forEach((inv, index) => {
      if (inv.investmentType && inv.amount) {
        // Get category for other investments
        const category = getCategoryFromType(inv.investmentType);
        const validation = validateAmountAgainstMaxLimit(inv.investmentType, inv.amount, category);
        if (!validation.isValid) {
          newErrors.otherInvestments[index] = validation.message;
          isValid = false;
        }
      }
    });

    setValidationErrors(newErrors);
    return isValid;
  };

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
        const data = response.data.data;
        setDeclarationData(data);

        // Set UI states based on data
        if (data.isStayingInRentedHouse) {
          setShowRentHouse(true);
          if (data.houseRentDeclarations && data.houseRentDeclarations.length > 0) {
            const housesWithTempIds = data.houseRentDeclarations.map((house, index) => ({
              ...house,
              tempId: house.id || Date.now() + index
            }));
            setRentedHouses(housesWithTempIds);
          }
        }

        if (data.isRepayingSelfOccupiedLoan) {
          setShowHomeLoan(true);
          // Handle home loan declarations if they exist
          if (data.homeLoanDeclarations && data.homeLoanDeclarations.length > 0) {
            const homeLoan = data.homeLoanDeclarations[0];
            setHomeLoanDetails({
              principalPaid: homeLoan.principalPaid || "",
              interestPaid: homeLoan.interestPaid || "",
              lenderName: homeLoan.lenderName || "",
              lenderPan: homeLoan.lenderPan || ""
            });
          }
        }

        if (data.hasLetOutProperty) {
          setShowRentalIncome(true);
          if (data.letOutPropertyDeclarations && data.letOutPropertyDeclarations.length > 0) {
            const propertiesWithTempIds = data.letOutPropertyDeclarations.map((property, index) => {
              // Extract annual rent and municipal taxes from propertyDetails
              const annualRent = property.propertyDetails?.find(detail => detail.type === "annual_rent")?.amount || "";
              const municipalTaxes = property.propertyDetails?.find(detail => detail.type === "municipal_tax")?.amount || "";
              
              return {
                ...property,
                tempId: property.id || Date.now() + index,
                annualRent: annualRent,
                municipalTaxes: municipalTaxes
              };
            });
            setLetOutProperties(propertiesWithTempIds);
          }
        }

        // Handle other incomes declarations
        if (data.otherIncomesDeclarations && data.otherIncomesDeclarations.length > 0) {
          const otherIncomes = {};
          data.otherIncomesDeclarations.forEach(income => {
            if (income.type === "other_income") {
              otherIncomes.otherSourcesIncome = income.amount || "";
            } else if (income.type === "savings_interest") {
              otherIncomes.savingsInterest = income.amount || "";
            } else if (income.type === "fd_interest") {
              otherIncomes.fdInterest = income.amount || "";
            } else if (income.type === "nsc_interest") {
              otherIncomes.nscInterest = income.amount || "";
            }
          });
          setOtherIncomeDetails(prev => ({ ...prev, ...otherIncomes }));
        }

        // Handle previous employment declarations
        if (data.previousEmploymentDeclarations && data.previousEmploymentDeclarations.length > 0) {
          const prevEmployment = {};
          data.previousEmploymentDeclarations.forEach(item => {
            if (item.type === "income") {
              prevEmployment.incomeAfterExemptions = item.amount || "";
            } else if (item.type === "income_tax") {
              prevEmployment.incomeTax = item.amount || "";
            } else if (item.type === "professional_tax") {
              prevEmployment.professionalTax = item.amount || "";
            } else if (item.type === "employee_pf") {
              prevEmployment.employeePF = item.amount || "";
            } else if (item.type === "leave_encashment") {
              prevEmployment.leaveEncashment = item.amount || "";
            }
          });
          setPreviousEmploymentDetails(prev => ({ ...prev, ...prevEmployment }));
        }

        // Process section6aItems for dropdowns
        if (data.section6aItems && data.section6aItems.length > 0) {
          setSection6aItems(data.section6aItems);

          // Filter for 80C investments
          const eightyCOptions = data.section6aItems
            .filter(item => item.is80c === true && item.isActive === true)
            .map(item => ({
              value: item.type,
              label: item.typeFormatted,
              maxLimit: item.maxLimit,
              maxLimitFormatted: item.maxLimitFormatted,
              category: item.category
            }));
          setInvestment80COptions(eightyCOptions);

          // Filter for 80D exemptions
          const eightyDOptions = data.section6aItems
            .filter(item => item.is80d === true && item.isActive === true)
            .map(item => ({
              value: item.type,
              label: item.typeFormatted,
              maxLimit: item.maxLimit,
              maxLimitFormatted: item.maxLimitFormatted,
              category: item.category
            }));
          setExemption80DOptions(eightyDOptions);

          // Filter for other sections
          const otherOptions = data.section6aItems
            .filter(item => item.isOtherSection === true && item.isActive === true)
            .map(item => ({
              value: item.type,
              label: item.typeFormatted,
              maxLimit: item.maxLimit,
              maxLimitFormatted: item.maxLimitFormatted,
              category: item.category,
              categoryFormatted: item.categoryFormatted
            }));
          setOtherInvestmentOptions(otherOptions);
        }

        // Handle section6aDeclarations (80C investments)
        if (data.section6aDeclarations && data.section6aDeclarations.length > 0) {
          const investments = data.section6aDeclarations.filter(item =>
            item.category === "80c" || item.category === "80C" || item.categoryFormatted?.includes("80C")
          );
          if (investments.length > 0) {
            const investmentsWithTempIds = investments.map((inv, index) => ({
              ...inv,
              tempId: inv.id || Date.now() + index,
              investmentType: inv.type,
              amount: inv.amount
            }));
            setInvestment80C(investmentsWithTempIds);
          }

          // Handle 80D exemptions
          const exemptions = data.section6aDeclarations.filter(item =>
            item.category === "80d" || item.category === "80D" || item.categoryFormatted?.includes("80D")
          );
          if (exemptions.length > 0) {
            const exemptionsWithTempIds = exemptions.map((ex, index) => ({
              ...ex,
              tempId: ex.id || Date.now() + index,
              exemptionType: ex.type,
              amount: ex.amount
            }));
            setExemption80D(exemptionsWithTempIds);
          }

          // Handle other investments
          const otherInvests = data.section6aDeclarations.filter(item => {
            const category = item.category ? item.category.toLowerCase() : '';
            const categoryFormatted = item.categoryFormatted ? item.categoryFormatted.toLowerCase() : '';
            return !(category.includes("80c") ||
              category.includes("80d") ||
              categoryFormatted.includes("80c") ||
              categoryFormatted.includes("80d"));
          });
          if (otherInvests.length > 0) {
            const otherWithTempIds = otherInvests.map((inv, index) => ({
              ...inv,
              tempId: inv.id || Date.now() + index,
              investmentType: inv.type,
              amount: inv.amount
            }));
            setOtherInvestments(otherWithTempIds);
          }
        }

        // Handle tax summaries
        if (data.taxSummaries && data.taxSummaries.length > 0) {
          const taxSummary = data.taxSummaries[0];
          if (taxSummary) {
            setCalculatedTax(taxSummary.taxOnTaxableIncomeFormatted || "20,280.00");
          }
        }
      }
    } catch (error) {
      console.log("No existing declaration found or error:", error);
      // It's okay if no declaration exists yet

      // Fetch section6a items separately if needed
      try {
        const section6aResponse = await axios.get(
          `${GlobalConst.API_URL}/api/section6a-items`,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: localStorage.getItem("organizationId") || "default-org-id"
            }
          }
        );

        if (section6aResponse.data && section6aResponse.data.data) {
          const items = section6aResponse.data.data;
          setSection6aItems(items);

          // Filter for 80C investments
          const eightyCOptions = items
            .filter(item => item.is80c === true && item.isActive === true)
            .map(item => ({
              value: item.type,
              label: item.typeFormatted,
              maxLimit: item.maxLimit,
              maxLimitFormatted: item.maxLimitFormatted,
              category: item.category
            }));
          setInvestment80COptions(eightyCOptions);

          // Filter for 80D exemptions
          const eightyDOptions = items
            .filter(item => item.is80d === true && item.isActive === true)
            .map(item => ({
              value: item.type,
              label: item.typeFormatted,
              maxLimit: item.maxLimit,
              maxLimitFormatted: item.maxLimitFormatted,
              category: item.category
            }));
          setExemption80DOptions(eightyDOptions);

          // Filter for other sections
          const otherOptions = items
            .filter(item => item.isOtherSection === true && item.isActive === true)
            .map(item => ({
              value: item.type,
              label: item.typeFormatted,
              maxLimit: item.maxLimit,
              maxLimitFormatted: item.maxLimitFormatted,
              category: item.category,
              categoryFormatted: item.categoryFormatted
            }));
          setOtherInvestmentOptions(otherOptions);
        }
      } catch (section6aError) {
        console.log("Error fetching section6a items:", section6aError);
      }
    } finally {
      setIsLoading(false);
    }
  };

  // Helper functions for dynamic fields
  const addRentedHouse = () => {
    setRentedHouses([...rentedHouses, { tempId: Date.now() }]);
  };

  const removeRentedHouse = (tempId) => {
    if (rentedHouses.length > 1) {
      setRentedHouses(rentedHouses.filter(house => house.tempId !== tempId));
    }
  };

  const addLetOutProperty = () => {
    setLetOutProperties([...letOutProperties, { tempId: Date.now() }]);
  };

  const removeLetOutProperty = (tempId) => {
    if (letOutProperties.length > 1) {
      setLetOutProperties(letOutProperties.filter(property => property.tempId !== tempId));
    }
  };

  const add80CInvestment = () => {
    setInvestment80C([...investment80C, { tempId: Date.now() }]);
    // Add empty error for new field
    setValidationErrors(prev => ({
      ...prev,
      investment80C: [...prev.investment80C, ""]
    }));
  };

  const remove80CInvestment = (tempId, index) => {
    if (investment80C.length > 1) {
      setInvestment80C(investment80C.filter(inv => inv.tempId !== tempId));
      // Remove corresponding error
      setValidationErrors(prev => ({
        ...prev,
        investment80C: prev.investment80C.filter((_, i) => i !== index)
      }));
    }
  };

  const add80DExemption = () => {
    setExemption80D([...exemption80D, { tempId: Date.now() }]);
    // Add empty error for new field
    setValidationErrors(prev => ({
      ...prev,
      exemption80D: [...prev.exemption80D, ""]
    }));
  };

  const remove80DExemption = (tempId, index) => {
    if (exemption80D.length > 1) {
      setExemption80D(exemption80D.filter(ex => ex.tempId !== tempId));
      // Remove corresponding error
      setValidationErrors(prev => ({
        ...prev,
        exemption80D: prev.exemption80D.filter((_, i) => i !== index)
      }));
    }
  };

  const addOtherInvestment = () => {
    setOtherInvestments([...otherInvestments, { tempId: Date.now() }]);
    // Add empty error for new field
    setValidationErrors(prev => ({
      ...prev,
      otherInvestments: [...prev.otherInvestments, ""]
    }));
  };

  const removeOtherInvestment = (tempId, index) => {
    if (otherInvestments.length > 1) {
      setOtherInvestments(otherInvestments.filter(inv => inv.tempId !== tempId));
      // Remove corresponding error
      setValidationErrors(prev => ({
        ...prev,
        otherInvestments: prev.otherInvestments.filter((_, i) => i !== index)
      }));
    }
  };

  // Handle amount change with validation
  const handleAmountChange = (section, index, value, type) => {
    let updatedArray;
    let errorField;

    switch (section) {
      case '80C':
        updatedArray = [...investment80C];
        updatedArray[index].amount = value;
        setInvestment80C(updatedArray);
        errorField = 'investment80C';
        break;
      case '80D':
        updatedArray = [...exemption80D];
        updatedArray[index].amount = value;
        setExemption80D(updatedArray);
        errorField = 'exemption80D';
        break;
      case 'other':
        updatedArray = [...otherInvestments];
        updatedArray[index].amount = value;
        setOtherInvestments(updatedArray);
        errorField = 'otherInvestments';
        break;
      default:
        return;
    }

    // Validate on change - only if type exists
    if (type && value) {
      const category = section === 'other' ? getCategoryFromType(type) : section;
      const validation = validateAmountAgainstMaxLimit(type, value, category);
      updateValidationError(errorField, index, validation.isValid ? "" : validation.message);
    } else {
      updateValidationError(errorField, index, "");
    }
  };

  // Handle type change with validation
  const handleTypeChange = (section, index, value) => {
    let updatedArray;
    let errorField;
    let currentAmount;

    switch (section) {
      case '80C':
        updatedArray = [...investment80C];
        updatedArray[index].investmentType = value;
        currentAmount = updatedArray[index].amount;
        setInvestment80C(updatedArray);
        errorField = 'investment80C';
        break;
      case '80D':
        updatedArray = [...exemption80D];
        updatedArray[index].exemptionType = value;
        currentAmount = updatedArray[index].amount;
        setExemption80D(updatedArray);
        errorField = 'exemption80D';
        break;
      case 'other':
        updatedArray = [...otherInvestments];
        updatedArray[index].investmentType = value;
        currentAmount = updatedArray[index].amount;
        setOtherInvestments(updatedArray);
        errorField = 'otherInvestments';
        break;
      default:
        return;
    }

    // Validate existing amount if any - only if value exists
    if (value && currentAmount) {
      const category = section === 'other' ? getCategoryFromType(value) : section;
      const validation = validateAmountAgainstMaxLimit(value, currentAmount, category);
      updateValidationError(errorField, index, validation.isValid ? "" : validation.message);
    } else {
      updateValidationError(errorField, index, "");
    }
  };

  // Handle home loan details change
  const handleHomeLoanChange = (field, value) => {
    setHomeLoanDetails(prev => ({
      ...prev,
      [field]: value
    }));
  };

  // Handle other income details change
  const handleOtherIncomeChange = (field, value) => {
    setOtherIncomeDetails(prev => ({
      ...prev,
      [field]: value
    }));
  };

  // Handle previous employment details change
  const handlePreviousEmploymentChange = (field, value) => {
    setPreviousEmploymentDetails(prev => ({
      ...prev,
      [field]: value
    }));
  };

  // Initial form values
  const getInitialValues = () => {
    if (declarationData) {
      return {
        fiscalYear: declarationData.fiscalYear || fiscalYear,
        declarationTaxYearStart: declarationData.declarationTaxYearStart || "2025-04",
        declarationTaxYearEnd: declarationData.declarationTaxYearEnd || "2026-03",
        currentTaxYearStart: declarationData.currentTaxYearStart || "2025-04",
        currentTaxYearEnd: declarationData.currentTaxYearEnd || "2026-03",

        canAllowEdit: declarationData.canAllowEdit || true,
        taxRegime: declarationData.taxRegime || "OLD",
        taxRegimeFormatted: declarationData.taxRegimeFormatted || "Old Tax Regime",
        isMultipleTaxRegimesApplicable: declarationData.isMultipleTaxRegimesApplicable || true,
        isLenderpanMandatory: declarationData.isLenderpanMandatory || false,
        canChangeTaxRegime: declarationData.canChangeTaxRegime || true,

        isStayingInRentedHouse: declarationData.isStayingInRentedHouse || false,
        isRepayingSelfOccupiedLoan: declarationData.isRepayingSelfOccupiedLoan || false,
        hasLetOutProperty: declarationData.hasLetOutProperty || false,

        status: declarationData.status || "DRAFT",
        statusFormatted: declarationData.statusFormatted || "Draft",
        messageTypes: declarationData.messageTypes || "INFO",
        taxPlanCount: declarationData.taxPlanCount || 1,

        // Home loan details - FIXED FIELD NAMES
        principalPaid: homeLoanDetails.principalPaid || "",
        interestPaid: homeLoanDetails.interestPaid || "",
        lenderName: homeLoanDetails.lenderName || "",
        lenderPan: homeLoanDetails.lenderPan || "",

        // Other income sources
        otherSourcesIncome: otherIncomeDetails.otherSourcesIncome || "",
        savingsInterest: otherIncomeDetails.savingsInterest || "",
        fdInterest: otherIncomeDetails.fdInterest || "",
        nscInterest: otherIncomeDetails.nscInterest || "",

        // Previous employment
        incomeAfterExemptions: previousEmploymentDetails.incomeAfterExemptions || "",
        incomeTax: previousEmploymentDetails.incomeTax || "",
        professionalTax: previousEmploymentDetails.professionalTax || "",
        employeePF: previousEmploymentDetails.employeePF || "",
        leaveEncashment: previousEmploymentDetails.leaveEncashment || "",

        // EPF Benefit
        epfBenefit: declarationData.epfBenefit || "",

        // Dynamic arrays will be handled separately
      };
    }

    return {
      fiscalYear: fiscalYear,
      declarationTaxYearStart: "2025-04",
      declarationTaxYearEnd: "2026-03",
      currentTaxYearStart: "2025-04",
      currentTaxYearEnd: "2026-03",

      canAllowEdit: true,
      taxRegime: "OLD",
      taxRegimeFormatted: "Old Tax Regime",
      isMultipleTaxRegimesApplicable: true,
      isLenderpanMandatory: false,
      canChangeTaxRegime: true,

      isStayingInRentedHouse: false,
      isRepayingSelfOccupiedLoan: false,
      hasLetOutProperty: false,

      status: "DRAFT",
      statusFormatted: "Draft",
      messageTypes: "INFO",
      taxPlanCount: 1,

      // Home loan details - FIXED FIELD NAMES
      principalPaid: "",
      interestPaid: "",
      lenderName: "",
      lenderPan: "",

      // Other income sources
      otherSourcesIncome: "",
      savingsInterest: "",
      fdInterest: "",
      nscInterest: "",

      // Previous employment
      incomeAfterExemptions: "",
      incomeTax: "",
      professionalTax: "",
      employeePF: "",
      leaveEncashment: "",

      epfBenefit: "",
    };
  };

  // Helper to get category from investment type
  const getCategoryFromType = (type) => {
    if (!type) return 'other';

    const option = section6aItems.find(item => item.type === type);
    if (option) return option.category;

    if (type.includes('80c')) return '80C';
    if (type.includes('80d')) return '80D';
    if (type.includes('80e')) return '80E';
    if (type.includes('80ee')) return '80EE';
    if (type.includes('80g')) return '80G';
    if (type.includes('80gg')) return '80GG';
    if (type.includes('80tta')) return '80TTA';
    if (type.includes('80u')) return '80U';
    return 'other';
  };

  // Helper to get formatted category
  const getCategoryFormatted = (type) => {
    const option = section6aItems.find(item => item.type === type);
    if (option) return option.categoryFormatted;

    const category = getCategoryFromType(type);
    return `Section ${category}`;
  };

  // Prepare house rent declarations for API
  const prepareHouseRentDeclarations = (formValues) => {
    return rentedHouses.map(house => {
      const houseData = {
        id: house.id || null,
        fromMonth: house.fromMonth || formValues.rentalPeriod?.split(" - ")[0],
        toMonth: house.toMonth || formValues.rentalPeriod?.split(" - ")[1],
        address: house.address || formValues.address,
        landlordName: house.landlordName || "Unknown",
        landlordPan: house.landlordPan || formValues.landlordPAN,
        isMetro: house.isMetro || (formValues.cityType === "metro"),
        // FIXED: Convert string to number
        amountPerMonth: parseFloat(house.amountPerMonth) || 0,
        currency: "INR",
        itemIdExternal: house.itemIdExternal || `HR${Date.now()}`
      };

      return houseData;
    });
  };

  // Prepare home loan declarations for API - FIXED FIELD NAMES
  const prepareHomeLoanDeclarations = (formValues) => {
    if (!showHomeLoan || (!formValues.principalPaid && !formValues.interestPaid)) {
      return [];
    }

    return [{
      id: declarationData?.homeLoanDeclarations?.[0]?.id || null,
      principalPaid: parseFloat(formValues.principalPaid) || 0,
      interestPaid: parseFloat(formValues.interestPaid) || 0,
      lenderName: formValues.lenderName || "",
      lenderPan: formValues.lenderPan || "",
      itemIdExternal: "HL001"
    }];
  };

  // Prepare section6a declarations for API
  const prepareSection6aDeclarations = (formValues) => {
    const declarations = [];

    // EPF Benefit (80C)
    if (formValues.epfBenefit && parseFloat(formValues.epfBenefit) > 0) {
      const epfOption = section6aItems.find(item =>
        item.is80c === true &&
        (item.type === "epf" || item.typeFormatted?.toLowerCase().includes("provident fund"))
      );

      declarations.push({
        id: null, // Will be set by backend for new items
        section6aItemId: epfOption?.id || null,
        category: "80C",
        categoryFormatted: "Section 80C",
        type: "epf",
        typeFormatted: epfOption?.typeFormatted || "Employee Provident Fund",
        amount: parseFloat(formValues.epfBenefit) || 0,
        itemIdExternal: "EPF001"
      });
    }

    // 80C Investments
    investment80C.forEach(inv => {
      if (inv.investmentType && inv.amount && parseFloat(inv.amount) > 0) {
        const option = section6aItems.find(item =>
          item.type === inv.investmentType ||
          item.typeFormatted === inv.investmentType
        );

        declarations.push({
          id: inv.id || null,
          section6aItemId: inv.section6aItemId || option?.id || null,
          category: option?.category || "80C",
          categoryFormatted: option?.categoryFormatted || "Section 80C",
          type: inv.investmentType,
          typeFormatted: option ? option.typeFormatted : inv.investmentType,
          amount: parseFloat(inv.amount) || 0,
          itemIdExternal: inv.itemIdExternal || `80C${Date.now()}`
        });
      }
    });

    // 80D Exemptions
    exemption80D.forEach(ex => {
      if (ex.exemptionType && ex.amount && parseFloat(ex.amount) > 0) {
        const option = section6aItems.find(item =>
          item.type === ex.exemptionType ||
          item.typeFormatted === ex.exemptionType
        );

        declarations.push({
          id: ex.id || null,
          section6aItemId: ex.section6aItemId || option?.id || null,
          category: option?.category || "80D",
          categoryFormatted: option?.categoryFormatted || "Section 80D",
          type: ex.exemptionType,
          typeFormatted: option ? option.typeFormatted : ex.exemptionType,
          amount: parseFloat(ex.amount) || 0,
          itemIdExternal: ex.itemIdExternal || `80D${Date.now()}`
        });
      }
    });

    // Other Investments
    otherInvestments.forEach(inv => {
      if (inv.investmentType && inv.amount && parseFloat(inv.amount) > 0) {
        const option = section6aItems.find(item =>
          item.type === inv.investmentType ||
          item.typeFormatted === inv.investmentType
        );

        declarations.push({
          id: inv.id || null,
          section6aItemId: inv.section6aItemId || option?.id || null,
          category: option?.category || getCategoryFromType(inv.investmentType),
          categoryFormatted: option?.categoryFormatted || getCategoryFormatted(inv.investmentType),
          type: inv.investmentType,
          typeFormatted: option ? option.typeFormatted : inv.investmentType,
          amount: parseFloat(inv.amount) || 0,
          itemIdExternal: inv.itemIdExternal || `OTH${Date.now()}`
        });
      }
    });

    return declarations;
  };

  // Prepare let out property declarations for API
  const prepareLetOutPropertyDeclarations = (formValues) => {
    return letOutProperties.map(property => {
      const propertyData = {
        id: property.id || null,
        propertyName: property.propertyName || "Let Out Property",
        address: property.address || "",
        netIncomeLoss: 0, // Set to 0 as per backend expectation
        itemIdExternal: property.itemIdExternal || `LOP${Date.now()}`,
        propertyDetails: []
      };

      // Add property details if available
      if (property.annualRent) {
        propertyData.propertyDetails.push({
          id: property.propertyDetails?.find(d => d.type === "annual_rent")?.id || null,
          type: "annual_rent",
          amount: parseFloat(property.annualRent) || 0
        });
      }

      if (property.municipalTaxes) {
        propertyData.propertyDetails.push({
          id: property.propertyDetails?.find(d => d.type === "municipal_tax")?.id || null,
          type: "municipal_tax",
          amount: parseFloat(property.municipalTaxes) || 0
        });
      }

      return propertyData;
    });
  };

  // Prepare other incomes declarations for API
  const prepareOtherIncomesDeclarations = (formValues) => {
    const incomes = [];

    if (formValues.otherSourcesIncome && parseFloat(formValues.otherSourcesIncome) > 0) {
      incomes.push({
        id: null,
        type: "other_income",
        typeFormatted: "Other Income",
        name: "Income from other sources",
        amount: parseFloat(formValues.otherSourcesIncome) || 0,
        declaredAmount: parseFloat(formValues.otherSourcesIncome) || 0,
        canEditInPortal: true,
        itemIdExternal: "OI001"
      });
    }

    if (formValues.savingsInterest && parseFloat(formValues.savingsInterest) > 0) {
      incomes.push({
        id: null,
        type: "savings_interest",
        typeFormatted: "Savings Interest",
        name: "Interest from Savings Account",
        amount: parseFloat(formValues.savingsInterest) || 0,
        declaredAmount: parseFloat(formValues.savingsInterest) || 0,
        canEditInPortal: true,
        itemIdExternal: "SI001"
      });
    }

    if (formValues.fdInterest && parseFloat(formValues.fdInterest) > 0) {
      incomes.push({
        id: null,
        type: "fd_interest",
        typeFormatted: "FD Interest",
        name: "Interest from Fixed Deposit",
        amount: parseFloat(formValues.fdInterest) || 0,
        declaredAmount: parseFloat(formValues.fdInterest) || 0,
        canEditInPortal: true,
        itemIdExternal: "FDI001"
      });
    }

    if (formValues.nscInterest && parseFloat(formValues.nscInterest) > 0) {
      incomes.push({
        id: null,
        type: "nsc_interest",
        typeFormatted: "NSC Interest",
        name: "Interest from National Savings Certificates",
        amount: parseFloat(formValues.nscInterest) || 0,
        declaredAmount: parseFloat(formValues.nscInterest) || 0,
        canEditInPortal: true,
        itemIdExternal: "NSCI001"
      });
    }

    return incomes;
  };

  // Prepare previous employment declarations for API
  const preparePreviousEmploymentDeclarations = (formValues) => {
    const prevEmployment = [];

    if (formValues.incomeAfterExemptions && parseFloat(formValues.incomeAfterExemptions) > 0) {
      prevEmployment.push({
        id: null,
        type: "income",
        typeFormatted: "Income",
        name: "Income After Exemptions",
        amount: parseFloat(formValues.incomeAfterExemptions) || 0,
        declaredAmount: parseFloat(formValues.incomeAfterExemptions) || 0,
        canEditInPortal: true,
        itemIdExternal: "PE001"
      });
    }

    if (formValues.incomeTax && parseFloat(formValues.incomeTax) > 0) {
      prevEmployment.push({
        id: null,
        type: "income_tax",
        typeFormatted: "Income Tax",
        name: "Income Tax",
        amount: parseFloat(formValues.incomeTax) || 0,
        declaredAmount: parseFloat(formValues.incomeTax) || 0,
        canEditInPortal: true,
        itemIdExternal: "PE002"
      });
    }

    if (formValues.professionalTax && parseFloat(formValues.professionalTax) > 0) {
      prevEmployment.push({
        id: null,
        type: "professional_tax",
        typeFormatted: "Professional Tax",
        name: "Professional Tax",
        amount: parseFloat(formValues.professionalTax) || 0,
        declaredAmount: parseFloat(formValues.professionalTax) || 0,
        canEditInPortal: true,
        itemIdExternal: "PE003"
      });
    }

    if (formValues.employeePF && parseFloat(formValues.employeePF) > 0) {
      prevEmployment.push({
        id: null,
        type: "employee_pf",
        typeFormatted: "Employee PF",
        name: "Employee Provident Fund",
        amount: parseFloat(formValues.employeePF) || 0,
        declaredAmount: parseFloat(formValues.employeePF) || 0,
        canEditInPortal: true,
        itemIdExternal: "PE004"
      });
    }

    if (formValues.leaveEncashment && parseFloat(formValues.leaveEncashment) > 0) {
      prevEmployment.push({
        id: null,
        type: "leave_encashment",
        typeFormatted: "Leave Encashment",
        name: "Leave Encashment Exemptions",
        amount: parseFloat(formValues.leaveEncashment) || 0,
        declaredAmount: parseFloat(formValues.leaveEncashment) || 0,
        canEditInPortal: true,
        itemIdExternal: "PE005"
      });
    }

    return prevEmployment;
  };

  // Prepare full payload for API
  const preparePayload = (formValues) => {
    const payload = {
      // REMOVED fields that should not be sent
      // fiscalYear: formValues.fiscalYear,
      // declarationTaxYearStart: formValues.declarationTaxYearStart,
      // declarationTaxYearEnd: formValues.declarationTaxYearEnd,
      // currentTaxYearStart: formValues.currentTaxYearStart,
      // currentTaxYearEnd: formValues.currentTaxYearEnd,
      // canAllowEdit: formValues.canAllowEdit,
      
      taxRegime: formValues.taxRegime,
      // taxRegimeFormatted: formValues.taxRegimeFormatted, // REMOVED
      // isMultipleTaxRegimesApplicable: formValues.isMultipleTaxRegimesApplicable, // REMOVED
      // isLenderpanMandatory: formValues.isLenderpanMandatory, // REMOVED
      // canChangeTaxRegime: formValues.canChangeTaxRegime, // REMOVED

      isStayingInRentedHouse: showRentHouse,
      isRepayingSelfOccupiedLoan: showHomeLoan,
      hasLetOutProperty: showRentalIncome,

      status: "SUBMITTED",
      // statusFormatted: "Submitted", // REMOVED
      messageTypes: "SUCCESS",
      // taxPlanCount: formValues.taxPlanCount, // REMOVED

      // Handle arrays
      houseRentDeclarations: showRentHouse ? prepareHouseRentDeclarations(formValues) : [],
      homeLoanDeclarations: showHomeLoan ? prepareHomeLoanDeclarations(formValues) : [],
      section6aDeclarations: prepareSection6aDeclarations(formValues),
      previousEmploymentDeclarations: preparePreviousEmploymentDeclarations(formValues),
      otherIncomesDeclarations: prepareOtherIncomesDeclarations(formValues),
      letOutPropertyDeclarations: showRentalIncome ? prepareLetOutPropertyDeclarations(formValues) : [],
      // section6aItems: section6aItems, // REMOVED - backend will handle this
      // section6aPreTaxDeductionsItems: [], // REMOVED
      // itExcludeItemsInPortal: [], // REMOVED
      // taxSummaries: declarationData?.taxSummaries || [] // REMOVED
    };

    return payload;
  };

  // Handle form submission
  const handleSubmit = async (formValues, { resetForm }) => {
    try {
      // Validate dynamic fields before submission
      if (!validateDynamicFields()) {
        errorMsg("Validation Error", "Some amounts exceed the maximum limits. Please check the values.", true);
        return;
      }

      setIsSubmitting(true);

      const payload = preparePayload(formValues);

      // Determine whether to use POST or PUT
      const method = declarationData ? 'PUT' : 'POST';
      const url = `${GlobalConst.API_URL}/api/employee-it-declarations/${employeeId}/${fiscalYear}`;

      const response = await axios({
        method: method,
        url: url,
        data: payload,
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: localStorage.getItem("organizationId") || "default-org-id",
          'Content-Type': 'application/json'
        }
      });

      // ✅ FIXED SUCCESS CHECK
      if (response.status === 200 || response.status === 201) {
        successMsg("Success", "Investment declaration saved successfully!", true);

        if (response.data?.data) {
          setDeclarationData(response.data.data);
        }

        fetchDeclarationData(employeeId);
      } else {
        errorMsg("Error", "Failed to save declaration. Please try again.", true);
      }
    } catch (error) {
      if (error.response) {
        if (error.response.status === 409) {
          // Conflict - declaration already exists, try PUT instead
          errorMsg("Conflict", "Declaration already exists. Updating with PUT...", true);
          try {
            // Retry with PUT using the same payload
            const putPayload = preparePayload(formValues);
            const putResponse = await axios.put(
              `${GlobalConst.API_URL}/api/employee-it-declarations/${employeeId}/${fiscalYear}`,
              putPayload,
              {
                headers: {
                  Authorization: `Bearer ${localStorage.getItem("__t")}`,
                  organizationId: localStorage.getItem("organizationId") || "default-org-id"
                }
              }
            );

            if (putResponse.data && putResponse.data.success) {
              successMsg("Success", "Investment declaration updated successfully!", true);
              fetchDeclarationData(employeeId);
            }
          } catch (putError) {
            errorMsg("Error", putError.response?.data?.message || "Failed to update declaration", true);
          }
        } else {
          errorMsg("Error", error.response.data?.message || "Failed to save declaration", true);
        }
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  // Calculate tax function
  const calculateTax = () => {
    setShowTaxCalculation(true);
    // In a real implementation, this would call a tax calculation API
    // For now, we'll just show the values from existing data if available
    if (declarationData?.taxSummaries?.[0]) {
      const taxSummary = declarationData.taxSummaries[0];
      setCalculatedTax(taxSummary.taxOnTaxableIncomeFormatted || "20,280.00");
      // Calculate savings (simplified)
      const taxAmount = parseFloat(taxSummary.taxOnTaxableIncome) || 20280;
      const newTaxAmount = taxAmount * 0.8; // Assume 20% savings
      setTaxSavings(newTaxAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 }));
    }
  };

  // Validation schema
  const validationSchema = Yup.object().shape({
    // Add validation rules as needed
    // Example: monthlyAmount: Yup.number().min(0, 'Amount must be positive')
  });

  // If still loading initial data
  if (isLoading && !declarationData && section6aItems.length === 0) {
    return <Loader />;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Investment Declaration</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Investment Declaration</h5>
        <span className="text-muted">
          Financial Year: <span className="text-primary">{fiscalYear}-{fiscalYear + 1}</span>
        </span>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "900px" }}>
              {/* Header Section */}
              <div className="card mb-6">
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-center mb-4">
                    <div>
                      <h3 className="fw-bold text-gray-800 mb-1">Investment Declaration</h3>
                      <div className="text-muted fs-7 mb-2">Employee Code: {employeeId}</div>
                    </div>
                    <div className="text-end">
                      <div className="badge bg-light-warning text-warning fs-7 px-3 py-2 mb-2">
                        Tax Regime: {declarationData?.taxRegimeFormatted || "Old Tax Regime"}
                      </div>
                      <div className="text-muted fs-8">
                        (Note: POI will also be processed based on the selected Tax Regime)
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <Formik
                initialValues={getInitialValues()}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting: formikSubmitting, errors, touched, setFieldValue, values }) => (
                  <Form className="form w-100">
                    {/* Rent House Section */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('rentHouse')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-primary">
                              <i className="ki-outline ki-home-2 fs-2x text-primary"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              Are you staying in a rented house?
                            </span>
                          </div>
                          <div className="ms-auto d-flex align-items-center">
                            <span className="text-muted me-3">
                              {showRentHouse ? "Yes" : "No"}
                            </span>
                            <div className="form-check form-switch">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                role="switch"
                                id="isRentedHouse"
                                checked={showRentHouse}
                                onChange={(e) => {
                                  e.stopPropagation();
                                  setShowRentHouse(e.target.checked);
                                  setFieldValue('isStayingInRentedHouse', e.target.checked);
                                }}
                              />
                            </div>
                            <i className={`ki-outline ki-${expandedCards.rentHouse ? 'minus' : 'plus'} fs-2 ms-3`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.rentHouse && showRentHouse && (
                        <div className="card-body">
                          <div className="d-flex justify-content-between align-items-center mb-4">
                            <h6 className="fw-bold text-gray-800 mb-0">House Rent Details</h6>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary"
                              onClick={addRentedHouse}
                            >
                              <i className="ki-outline ki-plus fs-2 me-1"></i> Add rented house
                            </button>
                          </div>

                          {rentedHouses.map((house, index) => (
                            <div key={house.tempId} className="border rounded p-4 mb-4 position-relative">
                              {rentedHouses.length > 1 && (
                                <button
                                  type="button"
                                  className="btn btn-icon btn-sm btn-light-danger position-absolute top-0 end-0 m-2"
                                  onClick={() => removeRentedHouse(house.tempId)}
                                >
                                  <i className="ki-outline ki-cross fs-2"></i>
                                </button>
                              )}

                              <div className="row g-3">
                                <div className="col-md-6">
                                  <label className="form-label">Rental Period (From)</label>
                                  <Field
                                    type="month"
                                    name={`rentedHouse_${index}_from`}
                                    className="form-control"
                                    value={house.fromMonth || ""}
                                    onChange={(e) => {
                                      const newHouses = [...rentedHouses];
                                      newHouses[index].fromMonth = e.target.value;
                                      setRentedHouses(newHouses);
                                    }}
                                  />
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Rental Period (To)</label>
                                  <Field
                                    type="month"
                                    name={`rentedHouse_${index}_to`}
                                    className="form-control"
                                    value={house.toMonth || ""}
                                    onChange={(e) => {
                                      const newHouses = [...rentedHouses];
                                      newHouses[index].toMonth = e.target.value;
                                      setRentedHouses(newHouses);
                                    }}
                                  />
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Amount /mo.</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <input
                                      type="number"
                                      className="form-control"
                                      placeholder="0"
                                      value={house.amountPerMonth || ""}
                                      onChange={(e) => {
                                        const newHouses = [...rentedHouses];
                                        newHouses[index].amountPerMonth = e.target.value;
                                        setRentedHouses(newHouses);
                                      }}
                                    />
                                  </div>
                                </div>
                                <div className="col-12">
                                  <label className="form-label">Address</label>
                                  <textarea
                                    className="form-control"
                                    rows="2"
                                    placeholder="Enter complete address"
                                    value={house.address || ""}
                                    onChange={(e) => {
                                      const newHouses = [...rentedHouses];
                                      newHouses[index].address = e.target.value;
                                      setRentedHouses(newHouses);
                                    }}
                                  />
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">City Type</label>
                                  <select
                                    className="form-select"
                                    value={house.isMetro === true ? "metro" : house.isMetro === false ? "non-metro" : ""}
                                    onChange={(e) => {
                                      const newHouses = [...rentedHouses];
                                      newHouses[index].isMetro = e.target.value === "metro";
                                      setRentedHouses(newHouses);
                                    }}
                                  >
                                    <option value="">Select</option>
                                    <option value="metro">Metro City</option>
                                    <option value="non-metro">Non-Metro City</option>
                                  </select>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">LANDLORD PAN</label>
                                  <input
                                    type="text"
                                    className="form-control"
                                    placeholder="Enter PAN number"
                                    value={house.landlordPan || ""}
                                    onChange={(e) => {
                                      const newHouses = [...rentedHouses];
                                      newHouses[index].landlordPan = e.target.value.toUpperCase();
                                      setRentedHouses(newHouses);
                                    }}
                                  />
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Landlord Name</label>
                                  <input
                                    type="text"
                                    className="form-control"
                                    placeholder="Enter landlord name"
                                    value={house.landlordName || ""}
                                    onChange={(e) => {
                                      const newHouses = [...rentedHouses];
                                      newHouses[index].landlordName = e.target.value;
                                      setRentedHouses(newHouses);
                                    }}
                                  />
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Home Loan Section */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('homeLoan')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-success">
                              <i className="ki-outline ki-bank fs-2x text-success"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              Are you repaying home loan for a self occupied house property?
                            </span>
                          </div>
                          <div className="ms-auto d-flex align-items-center">
                            <span className="text-muted me-3">
                              {showHomeLoan ? "Yes" : "No"}
                            </span>
                            <div className="form-check form-switch">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                role="switch"
                                id="isHomeLoan"
                                checked={showHomeLoan}
                                onChange={(e) => {
                                  e.stopPropagation();
                                  setShowHomeLoan(e.target.checked);
                                  setFieldValue('isRepayingSelfOccupiedLoan', e.target.checked);
                                }}
                              />
                            </div>
                            <i className={`ki-outline ki-${expandedCards.homeLoan ? 'minus' : 'plus'} fs-2 ms-3`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.homeLoan && showHomeLoan && (
                        <div className="card-body">
                          <div className="row g-3">
                            <div className="col-md-6">
                              <label className="form-label">Principal Paid on Home Loan</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="principalPaid"
                                  className="form-control"
                                  placeholder="0"
                                  value={homeLoanDetails.principalPaid}
                                  onChange={(e) => {
                                    setFieldValue('principalPaid', e.target.value);
                                    handleHomeLoanChange('principalPaid', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Interest Paid on Home Loan</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="interestPaid"
                                  className="form-control"
                                  placeholder="0"
                                  value={homeLoanDetails.interestPaid}
                                  onChange={(e) => {
                                    setFieldValue('interestPaid', e.target.value);
                                    handleHomeLoanChange('interestPaid', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Name of the Lender</label>
                              <Field
                                type="text"
                                name="lenderName"
                                className="form-control"
                                placeholder="Enter lender name"
                                value={homeLoanDetails.lenderName}
                                onChange={(e) => {
                                  setFieldValue('lenderName', e.target.value);
                                  handleHomeLoanChange('lenderName', e.target.value);
                                }}
                              />
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Lender PAN</label>
                              <Field
                                type="text"
                                name="lenderPan"
                                className="form-control"
                                placeholder="Enter PAN number"
                                value={homeLoanDetails.lenderPan}
                                onChange={(e) => {
                                  setFieldValue('lenderPan', e.target.value.toUpperCase());
                                  handleHomeLoanChange('lenderPan', e.target.value.toUpperCase());
                                }}
                              />
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Rental Income Section */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('rentalIncome')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-info">
                              <i className="ki-outline ki-wallet fs-2x text-info"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              Are you receiving rental income from let out property?
                            </span>
                          </div>
                          <div className="ms-auto d-flex align-items-center">
                            <span className="text-muted me-3">
                              {showRentalIncome ? "Yes" : "No"}
                            </span>
                            <div className="form-check form-switch">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                role="switch"
                                id="isRentalIncome"
                                checked={showRentalIncome}
                                onChange={(e) => {
                                  e.stopPropagation();
                                  setShowRentalIncome(e.target.checked);
                                  setFieldValue('hasLetOutProperty', e.target.checked);
                                }}
                              />
                            </div>
                            <i className={`ki-outline ki-${expandedCards.rentalIncome ? 'minus' : 'plus'} fs-2 ms-3`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.rentalIncome && showRentalIncome && (
                        <div className="card-body">
                          <div className="d-flex justify-content-between align-items-center mb-4">
                            <h6 className="fw-bold text-gray-800 mb-0">Letout Property</h6>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary"
                              onClick={addLetOutProperty}
                            >
                              <i className="ki-outline ki-plus fs-2 me-1"></i> Add a Let Out Property
                            </button>
                          </div>

                          {letOutProperties.map((property, index) => (
                            <div key={property.tempId} className="border rounded p-4 mb-4 position-relative">
                              {letOutProperties.length > 1 && (
                                <button
                                  type="button"
                                  className="btn btn-icon btn-sm btn-light-danger position-absolute top-0 end-0 m-2"
                                  onClick={() => removeLetOutProperty(property.tempId)}
                                >
                                  <i className="ki-outline ki-cross fs-2"></i>
                                </button>
                              )}

                              <div className="row g-3">
                                <div className="col-md-6">
                                  <label className="form-label">Annual Rent Received</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <input
                                      type="number"
                                      className="form-control"
                                      placeholder="0"
                                      value={property.annualRent || ""}
                                      onChange={(e) => {
                                        const newProperties = [...letOutProperties];
                                        newProperties[index].annualRent = e.target.value;
                                        setLetOutProperties(newProperties);
                                      }}
                                    />
                                  </div>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Municipal Taxes Paid</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <input
                                      type="number"
                                      className="form-control"
                                      placeholder="0"
                                      value={property.municipalTaxes || ""}
                                      onChange={(e) => {
                                        const newProperties = [...letOutProperties];
                                        newProperties[index].municipalTaxes = e.target.value;
                                        setLetOutProperties(newProperties);
                                      }}
                                    />
                                  </div>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Property Name</label>
                                  <input
                                    type="text"
                                    className="form-control"
                                    placeholder="Enter property name"
                                    value={property.propertyName || ""}
                                    onChange={(e) => {
                                      const newProperties = [...letOutProperties];
                                      newProperties[index].propertyName = e.target.value;
                                      setLetOutProperties(newProperties);
                                    }}
                                  />
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Property Address</label>
                                  <input
                                    type="text"
                                    className="form-control"
                                    placeholder="Enter property address"
                                    value={property.address || ""}
                                    onChange={(e) => {
                                      const newProperties = [...letOutProperties];
                                      newProperties[index].address = e.target.value;
                                      setLetOutProperties(newProperties);
                                    }}
                                  />
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Other Sources of Income */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('otherSources')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-warning">
                              <i className="ki-outline ki-chart-line fs-2x text-warning"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              Other Sources of Income
                            </span>
                          </div>
                          <div className="ms-auto">
                            <i className={`ki-outline ki-${expandedCards.otherSources ? 'minus' : 'plus'} fs-2`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.otherSources && (
                        <div className="card-body">
                          <div className="row g-3">
                            <div className="col-md-6">
                              <label className="form-label">Income from other sources</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="otherSourcesIncome"
                                  className="form-control"
                                  placeholder="0"
                                  value={otherIncomeDetails.otherSourcesIncome}
                                  onChange={(e) => {
                                    setFieldValue('otherSourcesIncome', e.target.value);
                                    handleOtherIncomeChange('otherSourcesIncome', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Interest Earned from Savings Deposit</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="savingsInterest"
                                  className="form-control"
                                  placeholder="0"
                                  value={otherIncomeDetails.savingsInterest}
                                  onChange={(e) => {
                                    setFieldValue('savingsInterest', e.target.value);
                                    handleOtherIncomeChange('savingsInterest', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Interest Earned from Fixed Deposit</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="fdInterest"
                                  className="form-control"
                                  placeholder="0"
                                  value={otherIncomeDetails.fdInterest}
                                  onChange={(e) => {
                                    setFieldValue('fdInterest', e.target.value);
                                    handleOtherIncomeChange('fdInterest', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Interest Earned from National Savings Certificates</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="nscInterest"
                                  className="form-control"
                                  placeholder="0"
                                  value={otherIncomeDetails.nscInterest}
                                  onChange={(e) => {
                                    setFieldValue('nscInterest', e.target.value);
                                    handleOtherIncomeChange('nscInterest', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* 80C Investments */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('investments80C')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-danger">
                              <i className="ki-outline ki-safe-home fs-2x text-danger"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              80C Investments
                            </span>
                          </div>
                          <div className="ms-auto">
                            <i className={`ki-outline ki-${expandedCards.investments80C ? 'minus' : 'plus'} fs-2`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.investments80C && (
                        <div className="card-body">
                          <div className="alert alert-info mb-4">
                            <strong>Note:</strong> This section contains the list of investments including LIC schemes, mutual funds and PPF. The maximum limit for this section is ₹1,50,000.00
                          </div>

                          

                          <div className="d-flex justify-content-between align-items-center mb-4">
                            <h6 className="fw-bold text-gray-800 mb-0">Other 80C Investments</h6>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary"
                              onClick={add80CInvestment}
                            >
                              <i className="ki-outline ki-plus fs-2 me-1"></i> Add an investment
                            </button>
                          </div>

                          {investment80C.map((investment, index) => {
                            const selectedOption = investment80COptions.find(opt => opt.value === investment.investmentType);
                            const maxLimitFormatted = selectedOption?.maxLimitFormatted || getMaxLimitFormattedForType(investment.investmentType, "80C");

                            return (
                              <div key={investment.tempId} className="d-flex align-items-center gap-3 mb-3 position-relative">
                                <div className="flex-grow-1">
                                  <select
                                    className="form-select"
                                    value={investment.investmentType || ""}
                                    onChange={(e) => handleTypeChange('80C', index, e.target.value)}
                                  >
                                    <option value="">Select 80C Investment Type</option>
                                    {investment80COptions.map((option, idx) => (
                                      <option key={idx} value={option.value}>{option.label}</option>
                                    ))}
                                  </select>
                                  {investment.investmentType && (
                                    <div className="text-muted small mt-1">
                                      Max Limit: {maxLimitFormatted}
                                    </div>
                                  )}
                                </div>
                                <div style={{ width: '200px' }}>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <input
                                      type="number"
                                      className={`form-control ${validationErrors.investment80C[index] ? 'is-invalid' : ''}`}
                                      placeholder="0"
                                      value={investment.amount || ""}
                                      onChange={(e) => handleAmountChange('80C', index, e.target.value, investment.investmentType)}
                                    />
                                  </div>
                                  {validationErrors.investment80C[index] && (
                                    <div className="invalid-feedback d-block">
                                      {validationErrors.investment80C[index]}
                                    </div>
                                  )}
                                </div>
                                {investment80C.length > 1 && (
                                  <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light-danger"
                                    onClick={() => remove80CInvestment(investment.tempId, index)}
                                  >
                                    <i className="ki-outline ki-cross fs-2"></i>
                                  </button>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    {/* 80D Exemptions */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('exemptions80D')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-primary">
                              <i className="ki-outline ki-heart fs-2x text-primary"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              80D Exemptions
                            </span>
                          </div>
                          <div className="ms-auto">
                            <i className={`ki-outline ki-${expandedCards.exemptions80D ? 'minus' : 'plus'} fs-2`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.exemptions80D && (
                        <div className="card-body">
                          <div className="alert alert-info mb-4">
                            <strong>Note:</strong> This section contains Mediclaim policies for yourself, your children, spouse and parents. The maximum limit for this section is ₹1,00,000.00
                          </div>

                          <div className="d-flex justify-content-between align-items-center mb-4">
                            <h6 className="fw-bold text-gray-800 mb-0">80D Exemptions</h6>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary"
                              onClick={add80DExemption}
                            >
                              <i className="ki-outline ki-plus fs-2 me-1"></i> Add an exemption
                            </button>
                          </div>

                          {exemption80D.map((exemption, index) => {
                            const selectedOption = exemption80DOptions.find(opt => opt.value === exemption.exemptionType);
                            const maxLimitFormatted = selectedOption?.maxLimitFormatted || getMaxLimitFormattedForType(exemption.exemptionType, "80D");

                            return (
                              <div key={exemption.tempId} className="d-flex align-items-center gap-3 mb-3 position-relative">
                                <div className="flex-grow-1">
                                  <select
                                    className="form-select"
                                    value={exemption.exemptionType || ""}
                                    onChange={(e) => handleTypeChange('80D', index, e.target.value)}
                                  >
                                    <option value="">Select 80D Exemption Type</option>
                                    {exemption80DOptions.map((option, idx) => (
                                      <option key={idx} value={option.value}>{option.label}</option>
                                    ))}
                                  </select>
                                  {exemption.exemptionType && (
                                    <div className="text-muted small mt-1">
                                      Max Limit: {maxLimitFormatted}
                                    </div>
                                  )}
                                </div>
                                <div style={{ width: '200px' }}>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <input
                                      type="number"
                                      className={`form-control ${validationErrors.exemption80D[index] ? 'is-invalid' : ''}`}
                                      placeholder="0"
                                      value={exemption.amount || ""}
                                      onChange={(e) => handleAmountChange('80D', index, e.target.value, exemption.exemptionType)}
                                    />
                                  </div>
                                  {validationErrors.exemption80D[index] && (
                                    <div className="invalid-feedback d-block">
                                      {validationErrors.exemption80D[index]}
                                    </div>
                                  )}
                                </div>
                                {exemption80D.length > 1 && (
                                  <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light-danger"
                                    onClick={() => remove80DExemption(exemption.tempId, index)}
                                  >
                                    <i className="ki-outline ki-cross fs-2"></i>
                                  </button>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    {/* Other Investments & Exemptions */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('otherInvestments')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-success">
                              <i className="ki-outline ki-wallet fs-2x text-success"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              Other Investments & Exemptions
                            </span>
                          </div>
                          <div className="ms-auto">
                            <i className={`ki-outline ki-${expandedCards.otherInvestments ? 'minus' : 'plus'} fs-2`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.otherInvestments && (
                        <div className="card-body">
                          <div className="alert alert-info mb-4">
                            <strong>Note:</strong> Declare other investments & exemptions such as Voluntary NPS, Interest Paid on Education Loan and Medical Expenditures under this section
                          </div>

                          <div className="d-flex justify-content-between align-items-center mb-4">
                            <h6 className="fw-bold text-gray-800 mb-0">Other Investments</h6>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary"
                              onClick={addOtherInvestment}
                            >
                              <i className="ki-outline ki-plus fs-2 me-1"></i> Add an investment
                            </button>
                          </div>

                          {otherInvestments.map((investment, index) => {
                            const selectedOption = otherInvestmentOptions.find(opt => opt.value === investment.investmentType);
                            const category = selectedOption?.category || getCategoryFromType(investment.investmentType);
                            const maxLimitFormatted = selectedOption?.maxLimitFormatted || getMaxLimitFormattedForType(investment.investmentType, category);

                            return (
                              <div key={investment.tempId} className="d-flex align-items-center gap-3 mb-3 position-relative">
                                <div className="flex-grow-1">
                                  <select
                                    className="form-select"
                                    value={investment.investmentType || ""}
                                    onChange={(e) => handleTypeChange('other', index, e.target.value)}
                                  >
                                    <option value="">Select Investment Type</option>
                                    {otherInvestmentOptions.map((option, idx) => (
                                      <option key={idx} value={option.value}>{option.label}</option>
                                    ))}
                                  </select>
                                  {investment.investmentType && (
                                    <div className="text-muted small mt-1">
                                      Max Limit: {maxLimitFormatted} ({selectedOption?.categoryFormatted || category})
                                    </div>
                                  )}
                                </div>
                                <div style={{ width: '200px' }}>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <input
                                      type="number"
                                      className={`form-control ${validationErrors.otherInvestments[index] ? 'is-invalid' : ''}`}
                                      placeholder="0"
                                      value={investment.amount || ""}
                                      onChange={(e) => handleAmountChange('other', index, e.target.value, investment.investmentType)}
                                    />
                                  </div>
                                  {validationErrors.otherInvestments[index] && (
                                    <div className="invalid-feedback d-block">
                                      {validationErrors.otherInvestments[index]}
                                    </div>
                                  )}
                                </div>
                                {otherInvestments.length > 1 && (
                                  <button
                                    type="button"
                                    className="btn btn-icon btn-sm btn-light-danger"
                                    onClick={() => removeOtherInvestment(investment.tempId, index)}
                                  >
                                    <i className="ki-outline ki-cross fs-2"></i>
                                  </button>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    {/* Previous Employment */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('previousEmployment')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-info">
                              <i className="ki-outline ki-briefcase fs-2x text-info"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              Previous Employment
                            </span>
                          </div>
                          <div className="ms-auto">
                            <i className={`ki-outline ki-${expandedCards.previousEmployment ? 'minus' : 'plus'} fs-2`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.previousEmployment && (
                        <div className="card-body">
                          <div className="row g-3">
                            <div className="col-md-6">
                              <label className="form-label">Income After Exemptions</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="incomeAfterExemptions"
                                  className="form-control"
                                  placeholder="0"
                                  value={previousEmploymentDetails.incomeAfterExemptions}
                                  onChange={(e) => {
                                    setFieldValue('incomeAfterExemptions', e.target.value);
                                    handlePreviousEmploymentChange('incomeAfterExemptions', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Income Tax</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="incomeTax"
                                  className="form-control"
                                  placeholder="0"
                                  value={previousEmploymentDetails.incomeTax}
                                  onChange={(e) => {
                                    setFieldValue('incomeTax', e.target.value);
                                    handlePreviousEmploymentChange('incomeTax', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Professional Tax</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="professionalTax"
                                  className="form-control"
                                  placeholder="0"
                                  value={previousEmploymentDetails.professionalTax}
                                  onChange={(e) => {
                                    setFieldValue('professionalTax', e.target.value);
                                    handlePreviousEmploymentChange('professionalTax', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Employee Provident Fund</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="employeePF"
                                  className="form-control"
                                  placeholder="0"
                                  value={previousEmploymentDetails.employeePF}
                                  onChange={(e) => {
                                    setFieldValue('employeePF', e.target.value);
                                    handlePreviousEmploymentChange('employeePF', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Leave Encashment Exemptions</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="leaveEncashment"
                                  className="form-control"
                                  placeholder="0"
                                  value={previousEmploymentDetails.leaveEncashment}
                                  onChange={(e) => {
                                    setFieldValue('leaveEncashment', e.target.value);
                                    handlePreviousEmploymentChange('leaveEncashment', e.target.value);
                                  }}
                                />
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Tax Calculation Section */}
                    <div className="card mb-6">
                      <div
                        className="card-header cursor-pointer"
                        onClick={() => toggleCard('taxCalculation')}
                        style={{ cursor: 'pointer' }}
                      >
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-warning">
                              <i className="ki-outline ki-calculator fs-2x text-warning"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column flex-grow-1">
                            <span className="fw-bold fs-6 text-gray-800">
                              Tax Calculation
                            </span>
                          </div>
                          <div className="ms-auto">
                            <i className={`ki-outline ki-${expandedCards.taxCalculation ? 'minus' : 'plus'} fs-2`}></i>
                          </div>
                        </div>
                      </div>

                      {expandedCards.taxCalculation && (
                        <div className="card-body">
                          <div className="text-center mb-4">
                            <button
                              type="button"
                              className="btn btn-lg btn-primary px-6"
                              onClick={calculateTax}
                            >
                              Click Compute Tax, to calculate the Total Tax Liability based on the above declaration
                            </button>
                          </div>

                          {showTaxCalculation && (
                            <div className="alert alert-success">
                              <div className="d-flex align-items-center">
                                <div className="symbol symbol-40px me-3">
                                  <i className="ki-outline ki-calculator fs-2x text-success"></i>
                                </div>
                                <div className="flex-grow-1">
                                  <h5 className="alert-heading mb-1">
                                    Total Tax Liability based on Old Tax Regime : ₹{calculatedTax}
                                  </h5>
                                  <p className="mb-0">
                                    <strong>Pro Tip :</strong> If you are opting for New Tax Regime you can save ₹{taxSavings}
                                  </p>
                                </div>
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Form Actions */}
                    <div className="d-flex justify-content-end gap-3 pt-5 border-top">
                      <button
                        type="button"
                        className="btn btn-lg btn-light"
                        disabled={isSubmitting}
                        onClick={() => window.history.back()}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? (
                          <>
                            <span className="spinner-border spinner-border-sm me-2"></span>
                            Submitting...
                          </>
                        ) : (
                          declarationData ? 'Update Declaration' : 'Submit Declaration'
                        )}
                      </button>
                    </div>
                  </Form>
                )}
              </Formik>
            </div>
          </div>
        </div>
      </div>

      {isLoading && <Loader />}
    </>
  );
}