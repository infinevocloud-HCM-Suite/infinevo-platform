import React, { useEffect, useState, useMemo } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

// Optimized EditReimbursement component
// - immediate render using cached data (sessionStorage)
// - background fetch to refresh values
// - do not block initial render with full-page loader
// - disable submit until required server-only fields are available

export default function EditReimbursement() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true); // background fetch

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  // Try to use cached values to render form instantly
  const cachedInitial = (() => {
    try {
      const raw = sessionStorage.getItem(`reimbursement_${id}`);
      if (raw) return JSON.parse(raw);
    } catch (e) {
      // ignore parse errors
    }
    return {
      reimbursementType: "",
      nameInPayslip: "",
      isFlexibleBenefitPlan: false,
      unclaimedReimbursement: "",
      amount: 0,
      isActive: false,
    };
  })();

  const [initialValues, setInitialValues] = useState(cachedInitial);

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = useMemo(() =>
    Yup.object().shape({
      nameInPayslip: Yup.string()
        .required("Name in Payslip is required")
        .min(2, "Name must be at least 2 characters"),
      isFlexibleBenefitPlan: Yup.boolean(),
      unclaimedReimbursement: Yup.string().required("Please select an option"),
      amount: Yup.number()
        .required("Amount is required")
        .min(1, "Amount must be greater than 0"),
      isActive: Yup.boolean(),
    }),
    []
  );

  // Background fetch with safe mounted flag
  useEffect(() => {
    let mounted = true;
    const fetchReimbursement = async () => {
      setLoading(true);
      try {
        const token = localStorage.getItem("__t");
        const response = await axios.get(
          `${GlobalConst.API_URL}/api/reimbursements/${id}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
              organizationId: organizationId,
            },
          }
        );

        if (response.data && response.data.data && mounted) {
          const reimbursement = response.data.data;

          const mapped = {
            reimbursementType: reimbursement.reimbursementType || reimbursement.reimbursementTypeFormatted || "",
            nameInPayslip: reimbursement.displayName || reimbursement.reimbursementName || "",
            isFlexibleBenefitPlan: !!reimbursement.isFbpComponent,
            unclaimedReimbursement: reimbursement.carryForwardOption === "carry_forward" ? "carry-forward" : "monthly-encash",
            amount: reimbursement.maxLimit || 0,
            isActive: reimbursement.status === "active",
          };

          setInitialValues(mapped);
          // keep a short-lived cache so next open is instant
          try {
            sessionStorage.setItem(`reimbursement_${id}`, JSON.stringify(mapped));
          } catch (e) {
            // ignore storage errors
          }
        } else if (mounted) {
          errorMsg("Error", "Unexpected response format from server", true);
        }
      } catch (error) {
        console.error("Fetch Error:", error);
        if (error.response) {
          errorMsg("Error", error.response.data?.message || "Failed to load reimbursement data", true);
        } else if (error.request) {
          errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
        } else {
          errorMsg("Error", "An unexpected error occurred", true);
        }
      } finally {
        if (mounted) setLoading(false);
      }
    };

    if (id) fetchReimbursement();
    return () => {
      mounted = false;
    };
  }, [id, organizationId]);

  // Submit handler (prevents submit if reimbursementType is missing)
  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    setSigningIn(true);

    // reimburseType usually comes from server and is required by backend
    const reimbursementTypeFinal = values.reimbursementType || initialValues.reimbursementType || null;
    if (!reimbursementTypeFinal) {
      errorMsg("Update Failed", "Required field 'reimbursementType' is missing. Please wait until data loads.", true);
      setSubmitting(false);
      setSigningIn(false);
      return;
    }

    const payload = {
      reimbursementType: reimbursementTypeFinal,
      reimbursementName: values.nameInPayslip,
      displayName: values.nameInPayslip,
      isFbpComponent: !!values.isFlexibleBenefitPlan,
      carryForwardOption: values.unclaimedReimbursement === "carry-forward" ? "carry_forward" : "monthly_encash",
      maxLimit: values.amount,
      isIncludedInCtc: true,
      isIncludedInSalaryStructure: true,
      status: values.isActive ? "active" : "inactive",
      statusFormatted: values.isActive ? "Active" : "Inactive",
      isOptIn: false,
      isAssociatedWithEmployee: false,
    };

    try {
      const token = localStorage.getItem("__t");
      const response = await axios.put(`${GlobalConst.API_URL}/api/reimbursements/${id}`, payload, {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
          organizationId: organizationId,
        },
      });

      if (response?.data?.status === 200) {
        // update cache so subsequent opens are instant
        try {
          sessionStorage.setItem(`reimbursement_${id}`, JSON.stringify({ ...values, reimbursementType: reimbursementTypeFinal }));
        } catch (e) {}

        successMsg("Success", "Reimbursement updated successfully", false);
        navigate("/salary-components/reimbursements");
      } else {
        errorMsg("Update Failed", response?.data?.message || "Could not update reimbursement. Try again later.", true);
      }
    } catch (error) {
      console.error("Update Error:", error);
      if (error.response) {
        errorMsg("Update Failed", error.response.data?.message || error.message, true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Update Failed", error.message, true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  // form is ready to submit only after background fetch provided server-only fields
  const readyToSubmit = !loading && !!initialValues.reimbursementType;

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Reimbursement</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Reimbursement</h5>
        {loading && (
          <div className="d-flex align-items-center ms-3">
            <div className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></div>
            <small className="ms-2 text-muted">Loading latest data…</small>
          </div>
        )}
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
                {({ isSubmitting, errors, touched, values }) => (
                  <Form className="form w-100">
                    {/* Reimbursement Type - read only */}
                    <div className="fv-row mb-10">
                      <label htmlFor="reimbursementType" className="form-label fs-6 fw-bold text-dark">Reimbursement Type</label>
                      <Field
                        type="text"
                        name="reimbursementType"
                        id="reimbursementType"
                        className="form-control form-control-lg form-control-solid w-75 bg-light"
                        readOnly
                        disabled
                      />
                      <div className="form-text text-muted">Reimbursement type cannot be changed once created.</div>
                    </div>

                    {/* Name in payslip */}
                    <div className="fv-row mb-10">
                      <label htmlFor="nameInPayslip" className="form-label fs-6 fw-bold text-dark">Name in Payslip <RequiredStar /></label>
                      <Field
                        type="text"
                        name="nameInPayslip"
                        id="nameInPayslip"
                        className={`form-control form-control-lg form-control-solid w-75 ${errors.nameInPayslip && touched.nameInPayslip ? "is-invalid" : ""}`}
                        placeholder="Enter name for payslip"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage name="nameInPayslip" component="div" className="invalid-feedback" />
                    </div>

                    {/* FBP checkbox */}
                    <div>
                      <div className="mb-3">
                        <div className="form-check">
                          <Field type="checkbox" name="isFlexibleBenefitPlan" id="isFlexibleBenefitPlan" className="form-check-input" disabled={isSubmitting} />
                          <label className="form-check-label text-dark" htmlFor="isFlexibleBenefitPlan">Include this as a Flexible Benefit Plan component</label>
                          <div className="form-text mt-1">FBP allows your employees to personalise their salary structure by choosing how much they want to receive under each FBP component.</div>
                        </div>
                      </div>

                      {/* Unclaimed reimbursement */}
                      <div className="fv-row mb-10">
                        <label className="form-label fs-6 fw-bold text-dark">How do you want to handle unclaimed reimbursement? <RequiredStar /></label>

                        <div className="d-flex flex-column mt-2">
                          <div className="form-check mb-2">
                            <Field type="radio" name="unclaimedReimbursement" value="carry-forward" id="carry-forward" className="form-check-input" disabled={isSubmitting} />
                            <label className="form-check-label ms-2 text-dark" htmlFor="carry-forward">Carry forward and encash at the end of the fiscal year</label>
                          </div>

                          <div className="form-check">
                            <Field type="radio" name="unclaimedReimbursement" value="monthly-encash" id="monthly-encash" className="form-check-input" disabled={isSubmitting} />
                            <label className="form-check-label ms-2 text-dark" htmlFor="monthly-encash">Do not carry forward and encash monthly</label>
                          </div>
                        </div>
                        <ErrorMessage name="unclaimedReimbursement" component="div" className="invalid-feedback d-block" />
                      </div>
                    </div>

                    {/* Amount */}
                    <div className="fv-row mb-10">
                      <label htmlFor="amount" className="form-label fs-6 fw-bold text-dark">Enter Amount <RequiredStar /></label>

                      <div className="input-group" style={{ maxWidth: "300px" }}>
                        <span className="input-group-text">₹</span>
                        <Field type="number" name="amount" id="amount" className={`form-control ${errors.amount && touched.amount ? "is-invalid" : ""}`} placeholder="0" disabled={isSubmitting} />
                        <span className="input-group-text bg-light">per month</span>
                      </div>

                      <ErrorMessage name="amount" component="div" className="invalid-feedback d-block" />
                    </div>

                    <div className="fv-row mb-10 form-check">
                      <Field type="checkbox" name="isActive" className="form-check-input" id="isActive" disabled={isSubmitting} />
                      <label className="form-check-label text-dark" htmlFor="isActive">Mark this as Active</label>
                    </div>

                    <div className="alert alert-warning mt-4" role="alert">
                      <strong>Note:</strong> Once this component is associated with an employee, you may edit the Name in the Payslip and the Amount. Any Amount changes will apply only to newly added employees.
                    </div>

                    <br />
                    <hr />

                    <div className="d-flex align-items-center mt-5" style={{ gap: "10px" }}>
                      <button type="submit" className="btn btn-lg btn-primary" disabled={isSubmitting || !readyToSubmit}>
                        {isSubmitting ? "Updating..." : "Update"}
                      </button>

                      <button type="button" className="btn btn-light" onClick={() => navigate("/salary-components/reimbursements")} disabled={isSubmitting}>
                        Cancel
                      </button>
                    </div>

                    {!readyToSubmit && (
                      <div className="mt-3 text-muted small">Saving is disabled until the latest server data loads (this prevents missing required fields).</div>
                    )}
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
