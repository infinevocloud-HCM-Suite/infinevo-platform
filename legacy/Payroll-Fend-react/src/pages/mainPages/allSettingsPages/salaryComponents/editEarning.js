import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import { FaPlus } from "react-icons/fa";
import * as Yup from "yup";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { useNavigate, useParams } from "react-router-dom";

export default function EditEarnings() {
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const navigate = useNavigate();
  const { earningId } = useParams();
  const [loading, setLoading] = useState(true);
  const [initialValues, setInitialValues] = useState(null);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  // --- Initial Values ---
  const defaultInitialValues = {
    earningName: "",
    earningType: "Basic",
    displayName: "",
    amount: "",
    value: "",
    valueType: "",
    maxLimit: "",
    isAmountInPercentage: false,
    isProRata: false,
    isIncludedInCtc: false,
    isIncludedInSalaryStructure: false,
    status: "active",
    isFbpComponent: false,
    isVariable: false,
    canIncludeAsFbp: false,
    componentType: "",
    isOneTimeComponent: false,
    isUserConfigurable: false,
    isAssociatedWithEmployee: false,
    canEditProrataConfiguration: false,
    canChangeScheduleEarningConfiguration: false,
    canChangePayType: false,
    gratuityExemptionRuleDetails: "",
    isIncludedInEpf: false,
    epfInclusionType: "Always",
    isIncludedInEsi: false,
    isTaxable: true,
    showInPayslip: true,
    canCalculateTaxWithoutProjection: false,
    isOptIn: false,
    parentEarningId: "",
    parentEarningName: "",
    isFormulaBasedCalculationSupported: false,
    formulaBasedOn: "",
    isScheduledEarning: false,
    canChangeDefaultPercentageVariables: false,
    earningFrequency: "monthly",
    calculationType: "Flat Amount", // Frontend only field
    percentage: "" // Frontend only field
  };

  const [searchTerm, setSearchTerm] = useState("");
  const [selected, setSelected] = useState(defaultInitialValues.earningType);
  const [isOpen, setIsOpen] = useState(false);

  const options = [
    "Basic", "House Rent Allowance", "Dearness Allowance",
    "Conveyance Allowance", "Bonus", "Commission",
    "Children Education Allowance", "Hostel Expenditure Allowance",
    "Transport Allowance", "Helper Allowance", "Travelling Allowance",
    "Uniform Allowance", "Daily Allowance", "City Compensatory Allowance",
    "Overtime Allowance", "Telephone Allowance", "Fixed Medical Allowance",
    "Project Allowance", "Food Allowance", "Holiday Allowance",
    "Entertainment Allowance", "Custom Allowance", "Food Coupon",
    "Gift Coupon", "Research Allowance", "Books and Periodicals Allowance",
    "Shift Allowance", "Fuel Allowance", "Driver Allowance",
    "Leave Travel Allowance", "Vehicle Maintenance Allowance",
    "Telephone And Internet Allowance"
  ];

  const filteredOptions = options.filter((opt) =>
    opt.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // Fetch earning data on component mount
  useEffect(() => {
    fetchEarningData();
  }, [earningId]);

  const fetchEarningData = async () => {
    setLoading(true);
    try {
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/earnings/${earningId}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          }
        }
      );

      if (response.data && response.data.data) {
        const earningData = response.data.data;
        
        // Map backend data to form values
        const formValues = {
          ...defaultInitialValues,
          ...earningData,
          // Handle frontend-only fields
          calculationType: earningData.isAmountInPercentage ? "Percentage of CTC" : "Flat Amount",
          percentage: earningData.isAmountInPercentage ? earningData.value : "",
          amount: !earningData.isAmountInPercentage ? earningData.value : ""
        };
        
        setInitialValues(formValues);
        setSelected(earningData.earningType || "Basic");
      }
    } catch (error) {
      console.error("Error fetching earning data:", error);
      errorMsg("Error", "Failed to load earning data", true);
    } finally {
      setLoading(false);
    }
  };

  // --- Validation Schema ---
  const validationSchema = Yup.object().shape({
    earningName: Yup.string().required("Earning name is required"),
    displayName: Yup.string().required("Name in payslip is required"),
    calculationType: Yup.string().required("Calculation type is required"),
    amount: Yup.number()
      .typeError("Amount must be a number")
      .when('calculationType', (calculationType, schema) => {
        return calculationType === 'Flat Amount'
          ? schema.required("Amount is required").positive("Amount must be positive")
          : schema;
      }),
    percentage: Yup.number()
      .typeError("Percentage must be a number")
      .when('calculationType', (calculationType, schema) => {
        return calculationType === 'Percentage of CTC'
          ? schema.required("Percentage is required").min(0, "Percentage cannot be negative").max(100, "Percentage cannot exceed 100%")
          : schema;
      })
  });

  // --- Submit with PUT API ---
  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    setSigningIn(true);

    // Prepare payload based on calculation type
    const payload = {
      earningName: values.earningName,
      earningType: values.earningType,
      displayName: values.displayName,
      isAmountInPercentage: values.calculationType === "Percentage of CTC",
      value: values.calculationType === "Percentage of CTC" ? values.percentage : values.amount,
      isProRata: values.isProRata,
      isIncludedInSalaryStructure: values.isIncludedInSalaryStructure,
      status: values.status,
      isFbpComponent: values.isFbpComponent,
      isVariable: values.isVariable,
      isIncludedInEpf: values.isIncludedInEpf,
      epfInclusionType: values.epfInclusionType,
      isIncludedInEsi: values.isIncludedInEsi,
      isTaxable: values.isTaxable,
      showInPayslip: values.showInPayslip,
      isScheduledEarning: values.isScheduledEarning
    };

    try {
      // PUT request for update
      const response = await axios.put(
        `${GlobalConst.API_URL}/api/earnings/${earningId}`,
        payload,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          }
        }
      );

      if (response?.data?.status === 200 || response?.data?.status === 201) {
        successMsg("Earning updated successfully");
        navigate("/salary-components");
      } else {
        errorMsg(
          "Update Failed",
          response?.data?.message ||
            `Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      errorMsg("Update Failed", e.response?.data?.err_msg || e.message, true);
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  const RequiredStar = () => <span className="text-danger">*</span>;

  if (loading) {
    return <Loader />;
  }

  if (!initialValues) {
    return <div>Error loading earning data</div>;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Earning</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Earning</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "800px" }}>
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ values, isSubmitting, errors, touched, setFieldValue }) => (
                  <Form className="form w-100">
                    {/* --- Earning Type Dropdown --- */}
                    <div className="fv-row mb-10 d-flex align-items-start">
                      <div style={{ flex: 1 }}>
                        <label className="form-label fs-6 fw-bold text-dark">
                          Earning Type <RequiredStar />
                        </label>
                        <div className="dropdown w-100" style={{ maxWidth: "500px" }}>
                          <button
                            type="button"
                            className={`btn btn-light dropdown-toggle w-100 text-start ${
                              errors.earningType && touched.earningType ? "is-invalid" : ""
                            }`}
                            onClick={() => setIsOpen(!isOpen)}
                            disabled={true} // Disabled in edit mode
                          >
                            {selected || "Select"}
                          </button>

                          {isOpen && (
                            <div className="dropdown-menu show w-100 p-2">
                              <input
                                type="text"
                                className="form-control mb-2"
                                placeholder="Search"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                              />
                              <div style={{ maxHeight: "200px", overflowY: "auto" }}>
                                {filteredOptions.map((opt, index) => (
                                  <button
                                    key={index}
                                    type="button"
                                    className="dropdown-item"
                                    onClick={() => {
                                      setSelected(opt);
                                      setIsOpen(false);
                                      setFieldValue("earningType", opt);
                                    }}
                                  >
                                    {opt}
                                  </button>
                                ))}
                              </div>
                              <div className="dropdown-divider"></div>
                              <button
                                type="button"
                                className="dropdown-item text-primary fw-semibold d-flex align-items-center"
                                onClick={() => navigate("/salary-components/add/custom-earning")}
                              >
                                <FaPlus className="me-2" />
                                New Custom Allowance
                              </button>
                            </div>
                          )}
                        </div>
                        <Field type="hidden" name="earningType" />
                        <ErrorMessage
                          name="earningType"
                          component="div"
                          className="invalid-feedback d-block"
                        />
                      </div>

                      {/* small message box to the right of dropdown */}
                      <div style={{ width: "300px", marginLeft: "16px" }}>
                        <div className="alert alert-warning mb-0 small p-2">
                          <strong>Info:</strong> Fixed amount paid at the end of every month.
                        </div>
                      </div>
                    </div>

                    <hr />

                    {/* --- Two Column Layout --- */}
                    <div className="row">
                      {/* LEFT SIDE */}
                      <div className="col-md-6 border-end pe-4">
                        {/* Earning Name */}
                        <div className="fv-row mb-10">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Earning Name <RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="earningName"
                            className={`form-control form-control-lg form-control-solid w-75 ${
                              errors.earningName && touched.earningName ? "is-invalid" : ""
                            }`}
                            placeholder="Enter earning name"
                          />
                          <ErrorMessage name="earningName" component="div" className="invalid-feedback" />
                        </div>

                        {/* Name in Payslip */}
                        <div className="fv-row mb-10">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Name in Payslip <RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="displayName"
                            className={`form-control form-control-lg form-control-solid w-75 ${
                              errors.displayName && touched.displayName ? "is-invalid" : ""
                            }`}
                            placeholder="Enter name for payslip"
                          />
                          <ErrorMessage name="displayName" component="div" className="invalid-feedback" />
                        </div>

                        {/* Calculation Type */}
                        <div className="fv-row mb-10">
                          <label className="form-label fs-6 fw-bold text-dark">
                            Calculation Type <RequiredStar />
                          </label>
                          <div>
                            <div className="form-check form-check-inline">
                              <Field
                                type="radio"
                                name="calculationType"
                                value="Flat Amount"
                                className="form-check-input"
                                id="flatAmount"
                              />
                              <label className="form-check-label" htmlFor="flatAmount">
                                Flat Amount
                              </label>
                            </div>
                            <div className="form-check form-check-inline">
                              <Field
                                type="radio"
                                name="calculationType"
                                value="Percentage of CTC"
                                className="form-check-input"
                                id="percentageCTC"
                              />
                              <label className="form-check-label" htmlFor="percentageCTC">
                                Percentage of CTC
                              </label>
                            </div>
                          </div>
                          <ErrorMessage name="calculationType" component="div" className="invalid-feedback" />
                        </div>

                        {/* Conditional Fields */}
                        {values.calculationType === "Flat Amount" && (
                          <div className="fv-row mb-10">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Enter Amount <RequiredStar />
                            </label>
                            <div className="input-group w-75">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="amount"
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.amount && touched.amount ? "is-invalid" : ""
                                }`}
                                placeholder="0"
                                step="0.01"
                              />
                            </div>
                            <ErrorMessage name="amount" component="div" className="invalid-feedback" />
                          </div>
                        )}

                        {values.calculationType === "Percentage of CTC" && (
                          <div className="fv-row mb-10">
                            <label className="form-label fs-6 fw-bold text-dark">
                              Enter Percentage <RequiredStar />
                            </label>
                            <div className="input-group w-75">
                              <Field
                                type="number"
                                name="percentage"
                                step="0.01"
                                className={`form-control form-control-lg form-control-solid ${
                                  errors.percentage && touched.percentage ? "is-invalid" : ""
                                }`}
                                placeholder="Enter percentage"
                              />
                              <span className="input-group-text">%</span>
                            </div>
                            <ErrorMessage name="percentage" component="div" className="invalid-feedback" />
                          </div>
                        )}

                        {/* Mark Active */}
                        <div className="fv-row mb-10 form-check">
                          <Field 
                            type="checkbox" 
                            name="status" 
                            className="form-check-input" 
                            id="isActive" 
                            checked={values.status === "active"}
                            onChange={(e) => setFieldValue("status", e.target.checked ? "active" : "inactive")}
                          />
                          <label className="form-check-label fw-bold text-dark" htmlFor="isActive">
                            Mark this as Active
                          </label>
                        </div>
                      </div>

                      {/* RIGHT SIDE */}
                      <div className="col-md-6 ps-4">
                        <h6 className="fw-bold text-dark mb-3">Other Configurations</h6>

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="isIncludedInSalaryStructure"
                            className="form-check-input"
                            id="isIncludedInSalaryStructure"
                          />
                          <label className="form-check-label" htmlFor="isIncludedInSalaryStructure">
                            Make this earning a part of the employee's salary structure
                          </label>
                        </div>

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="isTaxable"
                            className="form-check-input"
                            id="isTaxable"
                          />
                          <label className="form-check-label fw-bold" htmlFor="isTaxable">
                            This is a taxable earning
                          </label>
                          <div className="text-muted small">
                            The income tax amount will be divided equally and deducted every month across the financial year.
                          </div>
                        </div>

                        <div className="form-check mb-4">
                          <Field
                            type="checkbox"
                            name="isProRata"
                            className="form-check-input"
                            id="isProRata"
                          />
                          <label className="form-check-label" htmlFor="isProRata">
                            Calculate on pro-rata basis
                          </label>
                        </div>

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="showInPayslip"
                            className="form-check-input"
                            id="showInPayslip"
                          />
                          <label className="form-check-label" htmlFor="showInPayslip">
                            Show in payslip
                          </label>
                        </div>

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="isIncludedInEpf"
                            className="form-check-input"
                            id="isIncludedInEpf"
                          />
                          <label className="form-check-label" htmlFor="isIncludedInEpf">
                            Include in EPF
                          </label>
                        </div>

                        {values.isIncludedInEpf && (
                          <div className="fv-row mb-2 ms-4">
                            <label className="form-label fs-6 fw-bold text-dark">EPF Inclusion Type</label>
                            <div>
                              <div className="form-check form-check-inline">
                                <Field
                                  type="radio"
                                  name="epfInclusionType"
                                  value="Always"
                                  className="form-check-input"
                                  id="epfAlways"
                                />
                                <label className="form-check-label" htmlFor="epfAlways">
                                  Always
                                </label>
                              </div>
                              <div className="form-check form-check-inline">
                                <Field
                                  type="radio"
                                  name="epfInclusionType"
                                  value="Conditional"
                                  className="form-check-input"
                                  id="epfConditional"
                                />
                                <label className="form-check-label" htmlFor="epfConditional">
                                  Conditional
                                </label>
                              </div>
                            </div>
                          </div>
                        )}

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="isIncludedInEsi"
                            className="form-check-input"
                            id="isIncludedInEsi"
                          />
                          <label className="form-check-label" htmlFor="isIncludedInEsi">
                            Include in ESI
                          </label>
                        </div>

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="isFbpComponent"
                            className="form-check-input"
                            id="isFbpComponent"
                          />
                          <label className="form-check-label" htmlFor="isFbpComponent">
                            This is a FBP component
                          </label>
                        </div>

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="isVariable"
                            className="form-check-input"
                            id="isVariable"
                          />
                          <label className="form-check-label" htmlFor="isVariable">
                            This is a variable component
                          </label>
                        </div>

                        <div className="form-check mb-2">
                          <Field
                            type="checkbox"
                            name="isScheduledEarning"
                            className="form-check-input"
                            id="isScheduledEarning"
                          />
                          <label className="form-check-label" htmlFor="isScheduledEarning">
                            This is a scheduled earning
                          </label>
                        </div>
                      </div>
                    </div>

                    <hr />

                    {/* --- Action Buttons --- */}
                    <div className="d-flex justify-content-end gap-3">
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/salary-components")}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? (
                          <span>
                            <span className="spinner-border spinner-border-sm me-2"></span>
                            Updating...
                          </span>
                        ) : (
                          "Update Earning"
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

      {signingIn && <Loader />}
    </>
  );
}