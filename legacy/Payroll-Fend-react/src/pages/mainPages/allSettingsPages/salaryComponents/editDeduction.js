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
import { updateToken } from "../../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { format } from "date-fns";

export default function EditDeduction() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const { id } = useParams(); // deductionId from route
  const [signingIn, setSigningIn] = useState(false);
  const [loading, setLoading] = useState(true);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  const [initialValues, setInitialValues] = useState({
    // nameInPayslip: "",
    // deductionFrequency: "",
    // isActive: false,

    // nameInPayslip: deductionData.deductionName || "",
    // deductionFrequency: deductionData.isRecurring ? "recurring" : "one-time",
    // isActive: deductionData.status === "active",
    // deductionType: deductionData.deductionType || null, // keep original type

    nameInPayslip: "",
    deductionFrequency: "",
    isActive: false,
    deductionType: null, // will be filled after API call
  });

  // fetch deduction details
  useEffect(() => {
    const fetchDeduction = async () => {
      try {
        setLoading(true);
        const response = await axios.get(
          `${GlobalConst.API_URL}/api/deductions/${id}`,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            },
          }
        );

        if (response?.data && response.data.data) {
          const deductionData = response.data.data;
          setInitialValues({
            nameInPayslip: deductionData.deductionName || "",
            deductionFrequency: deductionData.isRecurring ? "recurring" : "one-time",
            isActive: deductionData.status === "active",
            deductionType: deductionData.deductionType || null,

          });
        } else {
          errorMsg("Error", "Unexpected response format from server", true);
        }
      } catch (e) {
        console.error("Fetch Error:", e);
        if (e.response) {
          errorMsg("Failed", e.response.data?.message || e.message, true);
        } else if (e.request) {
          errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
        } else {
          errorMsg("Failed", e.message, true);
        }
      } finally {
        setLoading(false);
      }
    };

    if (id) fetchDeduction();
  }, [id, organizationId]);

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    nameInPayslip: Yup.string()
      .required("Name in Payslip is required")
      .min(2, "Name must be at least 2 characters"),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    setSigningIn(true);

    // Only editable field - map to DTO structure
    const payload = {
      // deductionName: values.nameInPayslip,
      // // Keep other fields unchanged
      // isRecurring: values.deductionFrequency === "recurring",
      // status: values.isActive ? "active" : "inactive",
      // statusFormatted: values.isActive ? "Active" : "Inactive"

      deductionName: values.nameInPayslip,
      isRecurring: values.deductionFrequency === "recurring",
      status: values.isActive ? "active" : "inactive",
      statusFormatted: values.isActive ? "Active" : "Inactive",
      deductionType: values.deductionType, // send it back unchanged
    };

    try {
      const response = await axios.put(
        `${GlobalConst.API_URL}/api/deductions/${id}`,
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
        successMsg(
          "Success",
          "Deduction updated successfully",
          false
        );
        navigate("/salary-components/deductions");
      } else {
        errorMsg(
          "Update Failed",
          response?.data?.message || "Could not update deduction. Try again later.",
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
        <title>HRMS InfiNevoCloud - Edit Deduction</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Deduction</h5>
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
                {({ isSubmitting, errors, touched, values, setFieldValue }) => (
                  <Form className="form w-100">
                    {/* Editable Field */}
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

                    {/* Read-only Radio Buttons */}
                    <div className="fv-row mb-10">
                      <label className="form-label fs-6 fw-bold text-dark">
                        Select the deduction frequency <RequiredStar />
                      </label>

                      <div className="d-flex flex-column mt-2">
                        <div className="form-check mb-2">
                          <Field
                            type="radio"
                            name="deductionFrequency"
                            value="one-time"
                            id="one-time"
                            className="form-check-input"
                            checked={values.deductionFrequency === "one-time"}
                            onChange={() => setFieldValue("deductionFrequency", "one-time")}
                            disabled // locked
                          />
                          <label
                            className="form-check-label ms-2"
                            htmlFor="one-time"
                          >
                            One-time deduction
                          </label>
                        </div>

                        <div className="form-check">
                          <Field
                            type="radio"
                            name="deductionFrequency"
                            value="recurring"
                            id="recurring"
                            className="form-check-input"
                            checked={values.deductionFrequency === "recurring"}
                            onChange={() => setFieldValue("deductionFrequency", "recurring")}
                            disabled // locked
                          />
                          <label
                            className="form-check-label ms-2"
                            htmlFor="recurring"
                          >
                            Recurring deduction for subsequent Payrolls
                          </label>
                        </div>
                      </div>
                    </div>

                    {/* Read-only Checkbox */}
                    <div className="fv-row mb-10 form-check">
                      <Field
                        type="checkbox"
                        name="isActive"
                        className="form-check-input"
                        id="isActive"
                        checked={values.isActive}
                        onChange={(e) => setFieldValue("isActive", e.target.checked)}
                        disabled // locked
                      />
                      <label
                        className="form-check-label text-dark"
                        htmlFor="isActive"
                      >
                        Mark this as Active
                      </label>
                    </div>

                    <div className="alert alert-warning mt-4" role="alert">
                      <strong>Note:</strong> Once this benefit is linked to an
                      employee, you can edit only the Name in the Payslip. The
                      update will reflect for all employees, past and future.
                    </div>

                    <br />
                    <hr />

                    {/* Save & Cancel */}
                    <div
                      className="d-flex align-items-center mt-5"
                      style={{ gap: "10px" }}
                    >
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
                        onClick={() => navigate("/salary-components/deductions")}
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