import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import Loader from "../../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../../shared/helpers/msgHelper";

export default function AddNewReimbursement() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [showSuperannuationMessage, setShowSuperannuationMessage] = useState(false);
  const [showFilingModal, setShowFilingModal] = useState(false);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  const [initialValues] = useState({
    reimbursementType: "",
    nameInPayslip: "",
    isFlexibleBenefitPlan: false,
    unclaimedReimbursement: "",
    amount: 0,
    isActive: false
  });

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    reimbursementType: Yup.string().required("Reimbursement type is required"),
    nameInPayslip: Yup.string()
      .required("Name in Payslip is required")
      .min(2, "Name must be at least 2 characters"),
    isFlexibleBenefitPlan: Yup.boolean(),
    unclaimedReimbursement: Yup.string().required("Please select an option"),
    amount: Yup.number()
      .required("Amount is required")
      .min(1, "Amount must be greater than 0"),
    isActive: Yup.boolean()
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    setSigningIn(true);

    // Map form values to API payload structure
    const payload = {
      reimbursementType: values.reimbursementType,
      reimbursementName: values.nameInPayslip,
      displayName: values.nameInPayslip,
      isFbpComponent: values.isFlexibleBenefitPlan,
      carryForwardOption: values.unclaimedReimbursement === "carry-forward" ? "carry_forward" : "monthly_encash",
      maxLimit: values.amount,
      isIncludedInCtc: true,
      isIncludedInSalaryStructure: true,
      status: values.isActive ? "active" : "inactive",
      statusFormatted: values.isActive ? "Active" : "Inactive",
      isOptIn: false,
      isAssociatedWithEmployee: false
    };

    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/api/reimbursements`,
        payload,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response?.data?.status === 201 || response?.data?.status === 200) {
        successMsg("Success", "Reimbursement created successfully", false);
        navigate("/salary-components/reimbursements");
      } else {
        errorMsg(
          "Save Failed",
          response?.data?.message || "Could not save reimbursement. Try again later.",
          true
        );
      }
    } catch (error) {
      console.error("Save Error:", error);
      if (error.response) {
        errorMsg("Save Failed", error.response.data?.message || error.message, true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Save Failed", error.message, true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - New Reimbursement</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">New Reimbursement</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "600px" }}>
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched }) => (
                  <Form className="form w-100">
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="reimbursementType"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Reimbursement Type <RequiredStar />
                      </label>

                      <Field
                        as="select"
                        name="reimbursementType"
                        id="reimbursementType"
                        style={{ maxWidth: "500px" }}
                        className={`form-select form-select-lg form-select-solid ${
                          errors.reimbursementType && touched.reimbursementType
                            ? "is-invalid"
                            : ""
                        }`}
                        aria-label="Select reimbursement type"
                        disabled={isSubmitting}
                      >
                        <option value="" disabled>
                          Select
                        </option>
                        <option value="Club Reimbursement">Club Reimbursement</option>
                        <option value="Entertainment Reimbursement">Entertainment Reimbursement</option>
                        <option value="Gadget Reimbursement">Gadget Reimbursement</option>
                        <option value="Books and Periodicals Reimbursement">Books and Periodicals Reimbursement</option>
                        <option value="Business Development Expense Reimbursement">Business Development Expense Reimbursement</option>
                        <option value="Helper Reimbursement">Helper Reimbursement</option>
                        <option value="Children Education Reimbursement">Children Education Reimbursement</option>
                        <option value="Hostel Expenditure Reimbursement">Hostel Expenditure Reimbursement</option>
                        <option value="Research Reimbursement">Research Reimbursement</option>
                        <option value="Uniform Reimbursement">Uniform Reimbursement</option>
                        <option value="Internet Reimbursement">Internet Reimbursement</option>
                      </Field>
                      <ErrorMessage
                        name="reimbursementType"
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
                        className={`form-control form-control-lg form-control-solid w-75 ${
                          errors.nameInPayslip && touched.nameInPayslip
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
                      <div className="mb-3">
                        <div className="form-check">
                          <Field
                            type="checkbox"
                            name="isFlexibleBenefitPlan"
                            id="isFlexibleBenefitPlan"
                            className="form-check-input"
                          />
                          <label className="form-check-label text-dark" htmlFor="isFlexibleBenefitPlan">
                            Include this as a Flexible Benefit Plan component
                          </label>
                          <div className="form-text mt-1">
                            FBP allows your employees to personalise their salary structure by choosing how much
                            they want to receive under each FBP component.
                          </div>
                        </div>
                      </div>

                      <div className="fv-row mb-10">
                        <label className="form-label fs-6 fw-bold text-dark">
                          How do you want to handle unclaimed reimbursement? <RequiredStar />
                        </label>

                        <div className="d-flex flex-column mt-2">
                          <div className="form-check mb-2">
                            <Field
                              type="radio"
                              name="unclaimedReimbursement"
                              value="carry-forward"
                              id="carry-forward"
                              className="form-check-input"
                            />
                            <label className="form-check-label ms-2 text-dark" htmlFor="carry-forward">
                              Carry forward and encash at the end of the fiscal year
                            </label>
                          </div>

                          <div className="form-check">
                            <Field
                              type="radio"
                              name="unclaimedReimbursement"
                              value="monthly-encash"
                              id="monthly-encash"
                              className="form-check-input"
                            />
                            <label className="form-check-label ms-2 text-dark" htmlFor="monthly-encash">
                              Do not carry forward and encash monthly
                            </label>
                          </div>
                        </div>
                        <ErrorMessage
                          name="unclaimedReimbursement"
                          component="div"
                          className="invalid-feedback d-block"
                        />
                      </div>
                    </div>

                    <div className="fv-row mb-10">
                      <label htmlFor="amount" className="form-label fs-6 fw-bold text-dark">
                        Enter Amount <RequiredStar />
                      </label>

                      <div className="input-group" style={{ maxWidth: "300px" }}>
                        <span className="input-group-text">₹</span>
                        <Field
                          type="number"
                          name="amount"
                          id="amount"
                          className={`form-control ${
                            errors.amount && touched.amount ? "is-invalid" : ""
                          }`}
                          placeholder="0"
                        />
                        <span className="input-group-text bg-light">per month</span>
                      </div>

                      <ErrorMessage
                        name="amount"
                        component="div"
                        className="invalid-feedback d-block"
                      />
                    </div>

                    <div className="fv-row mb-10 form-check">
                      <Field
                        type="checkbox"
                        name="isActive"
                        className="form-check-input"
                        id="isActive"
                      />
                      <label
                        className="form-check-label text-dark"
                        htmlFor="isActive"
                      >
                        Mark this as Active
                      </label>
                    </div>

                    <div className="alert alert-warning mt-4" role="alert">
                      <strong>Note:</strong> Once this component is associated with an employee, you may edit the Name in the Payslip and the Amount.
                      Any Amount changes will apply only to newly added employees.
                    </div>

                    <br />
                    <hr />

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
                        onClick={() => navigate("/salary-components/reimbursements")}
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