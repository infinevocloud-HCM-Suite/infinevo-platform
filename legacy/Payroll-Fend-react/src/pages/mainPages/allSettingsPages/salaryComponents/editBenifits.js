import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function EditBenefit() {
  const navigate = useNavigate();
  const { key: benefitId } = useParams(); // benefitId comes from route :key
  const dispatch = useDispatch();
  const [showSuperannuationMessage, setShowSuperannuationMessage] = useState(false);
  const [signingIn, setSigningIn] = useState(false);
  const [benefitPlans, setBenefitPlans] = useState([]);
  const [associatedBenefits, setAssociatedBenefits] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dataLoaded, setDataLoaded] = useState(false);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  const [initialValues, setInitialValues] = useState({
    benefitPlan: "",
    associateBenefit: "",
    nameInPayslip: "",
    employerContribution: false,
    superannuationFund: false,
    proRata: false,
    isActive: true
  });

  const RequiredStar = () => <span className="text-danger">*</span>;

  // Fetch benefit data and utility data on component mount
  useEffect(() => {
    fetchBenefitData();
  }, [benefitId]);

  const fetchBenefitData = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/benefits/${benefitId}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data && response.data.data) {
        const data = response.data.data;
        const benefit = data.benefit;

        // Set utility data
        setBenefitPlans(
          data.benefitPlans?.map(p => ({
            value: p.plan,
            label: p.planNameFormatted
          })) || []
        );
        
        const grouped = _.groupBy(data.section6aDetails || [], "categoryFormatted");
        setAssociatedBenefits(
          Object.keys(grouped).map(groupName => ({
            groupName,
            options: grouped[groupName].map(item => ({
              value: item.type,
              label: item.typeFormatted
            }))
          }))
        );

        // Set initial values from the fetched benefit
        setInitialValues({
          benefitPlan: benefit.benefitPlan || "",
          associateBenefit: benefit.benefitCategory || "",
          nameInPayslip: benefit.benefitName || "",
          employerContribution: benefit.isIncludedInSalaryStructure || false,
          superannuationFund: benefit.isSuperannuationBenefit || false,
          proRata: benefit.isProRata || false,
          isActive: benefit.status === "active"
        });

        setShowSuperannuationMessage(benefit.isSuperannuationBenefit || false);
        setDataLoaded(true);
      } else {
        errorMsg("Error", "Unexpected response format from server", true);
      }
    } catch (error) {
      console.error("API Error:", error);
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load benefit data", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  const validationSchema = Yup.object().shape({
    benefitPlan: Yup.string().required("Benefit plan is required"),
    associateBenefit: Yup.string().required("Associated benefit is required"),
    nameInPayslip: Yup.string()
      .required("Name in payslip is required")
      .min(2, "Name must be at least 2 characters"),
    employerContribution: Yup.boolean(),
    superannuationFund: Yup.boolean(),
    proRata: Yup.boolean(),
    isActive: Yup.boolean()
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    setSigningIn(true);

    const payload = {
      benefitName: values.nameInPayslip,
      benefitPlan: values.benefitPlan,
      benefitCategory: values.associateBenefit,
      isIncludedInSalaryStructure: values.employerContribution,
      isSuperannuationBenefit: values.superannuationFund,
      isProRata: values.proRata,
      status: values.isActive ? "active" : "inactive"
    };

    try {
      const response = await axios.put(
        `${GlobalConst.API_URL}/api/benefits/${benefitId}`,
        payload,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response?.data?.status === 200) {
        successMsg("Success", "Benefit updated successfully", false);
        navigate("/salary-components/benefits");
      } else {
        errorMsg(
          "Update Failed",
          response?.data?.message || "Could not update benefit. Try again later.",
          true
        );
      }
    } catch (e) {
      console.error("Update Error:", e);
      if (e.response) {
        errorMsg("Update Failed", e.response.data?.message || e.message, true);
      } else if (e.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Update Failed", e.message, true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Benefit</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Benefit</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div
              className="w-100"
              style={{ maxWidth: "600px" }}
            >
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched, values, setFieldValue }) => (
                  <Form className="form w-100">

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="benefitPlan"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Benefit Plan
                      </label>

                      {dataLoaded && (
                        <Field
                          as="select"
                          name="benefitPlan"
                          id="benefitPlan"
                          style={{ maxWidth: "500px" }}
                          className="form-select form-select-lg form-select-solid"
                          aria-label="Select benefit plan"
                          disabled={isSubmitting}
                        >
                          <option value="" disabled>
                            Select
                          </option>
                          {benefitPlans.map((plan) => (
                            <option key={plan.value} value={plan.value}>
                              {plan.label}
                            </option>
                          ))}
                        </Field>
                      )}
                      <ErrorMessage
                        name="benefitPlan"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="associateBenefit"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Associate this benefit with
                      </label>

                      {dataLoaded && (
                        <Field
                          as="select"
                          name="associateBenefit"
                          id="associateBenefit"
                          style={{ maxWidth: "500px" }}
                          className="form-select form-select-lg form-select-solid"
                          aria-label="Select associated benefit"
                          disabled={isSubmitting}
                        >
                          <option value="" disabled>
                            Select
                          </option>
                          {associatedBenefits.map((group) => (
                            <optgroup key={group.groupName} label={group.groupName}>
                              {group.options.map((option) => (
                                <option key={option.value} value={option.value}>
                                  {option.label}
                                </option>
                              ))}
                            </optgroup>
                          ))}
                        </Field>
                      )}
                      <ErrorMessage
                        name="associateBenefit"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div className="fv-row mb-10">
                      <label
                        htmlFor="nameInPayslip"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Name in Payslip <RequiredStar />
                      </label>
                      <Field
                        type="text"
                        name="nameInPayslip"
                        id="nameInPayslip"
                        className={`form-control form-control-lg form-control-solid w-75 ${errors.nameInPayslip && touched.nameInPayslip
                            ? "is-invalid"
                            : ""
                          }`}
                        placeholder="Enter name for payslip"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="nameInPayslip"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    <div>
                      {/* Checkbox 1 */}
                      <div className="mb-3">
                        <div className="form-check">
                          <Field
                            className="form-check-input"
                            type="checkbox"
                            id="employerContribution"
                            name="employerContribution"
                            checked={values.employerContribution}
                            onChange={(e) => setFieldValue("employerContribution", e.target.checked)}
                          />
                          <label className="form-check-label text-dark" htmlFor="employerContribution">
                            Include employer's contribution in employee's salary structure.
                          </label>
                        </div>
                      </div>

                      {/* Checkbox 2: Superannuation Fund */}
                      <div className="mb-3">
                        <div className="form-check">
                          <Field
                            className="form-check-input"
                            type="checkbox"
                            id="superannuationFund"
                            name="superannuationFund"
                            checked={values.superannuationFund}
                            onChange={(e) => {
                              setFieldValue("superannuationFund", e.target.checked);
                              setShowSuperannuationMessage(e.target.checked);
                            }}
                          />
                          <label className="form-check-label text-dark" htmlFor="superannuationFund">
                            Consider this a superannuation fund
                          </label>
                        </div>
                        {showSuperannuationMessage && (
                          <div className="form-text text-danger mt-1">
                            If an employer contributes more than ₹7.5 lakh in a financial year
                            towards EPF, EPS, NPS, and superannuation fund combined, the excess
                            contribution will be considered a taxable benefit for the employee.
                          </div>
                        )}
                      </div>

                      {/* Checkbox 3 */}
                      <div className="mb-3">
                        <div className="form-check">
                          <Field
                            className="form-check-input"
                            type="checkbox"
                            id="proRata"
                            name="proRata"
                            checked={values.proRata}
                            onChange={(e) => setFieldValue("proRata", e.target.checked)}
                          />
                          <label className="form-check-label text-dark" htmlFor="proRata">
                            Calculate on pro-rata basis
                          </label>
                          <div className="form-text">
                            Pay will be adjusted based on employee working days.
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="fv-row mb-10 form-check">
                      <Field
                        type="checkbox"
                        name="isActive"
                        className="form-check-input"
                        id="isActive"
                        checked={values.isActive}
                        onChange={(e) => setFieldValue("isActive", e.target.checked)}
                      />
                      <label
                        className="form-check-label text-dark"
                        htmlFor="isActive"
                      >
                        Mark this as Active
                      </label>
                    </div>

                    <div className="alert alert-warning mt-4" role="alert">
                      <strong>Note:</strong> After a benefit is associated with an employee, only the <strong>Name in the payslip</strong> can be modified. Any changes will apply to both current and future employees.
                    </div>

                    <br></br>
                    <hr></hr>

                    {/* Save & Cancel */}
                    <div className="d-flex align-items-center mt-5" style={{ gap: "10px" }}>
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? "Saving..." : "Save"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/salary-components/benefits")}
                      >
                        Cancel
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