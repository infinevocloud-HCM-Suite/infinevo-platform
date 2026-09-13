// src/pages/workLocations/EditWorkLocation.js
import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate, useParams } from "react-router-dom";
import { getStates } from "../../../../shared/appConfig/stateList";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import axios from "axios";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";

export default function EditWorkLocationForm() {
  const navigate = useNavigate();
  const { workLocationId } = useParams();
  const organizationId = localStorage.getItem("organizationId");

  const [initialValues, setInitialValues] = useState(null);
  const [loading, setLoading] = useState(true);

  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    workLocationName: Yup.string().required("Work location name is required"),
    streetAddress1: Yup.string().required("Address Line 1 is required"),
    streetAddress2: Yup.string(), // optional
    state: Yup.string().required("State is required"),
    city: Yup.string().required("City is required"),
    zipCode: Yup.string()
      .required("PIN code is required")
      .matches(/^\d{6}$/, "PIN code must be a 6-digit number"),
    country: Yup.string().required("Country is required"),
  });

  // Fetch existing work location details
  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await axios.get(
          `${GlobalConst.API_URL}/api/worklocations/${workLocationId}`,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          }
        );


        if (response.data?.status === 200) {
          const dto = response.data.data;
          setInitialValues({
            workLocationName: dto.workLocationName || "",
            streetAddress1: dto.streetAddress1 || "",
            streetAddress2: dto.streetAddress2 || "",
            state: dto.state || "",
            city: dto.city || "",
            zipCode: dto.zipCode || "",
            country: dto.country || "India",
            isFilingAddress: dto.isFilingAddress || false,
          });
        } else {
          errorMsg("Error", "Failed to retrieve work location details", true);
        }
      } catch (e) {
        errorMsg("Fetch Failed", e.response?.data?.message || e.message, true);
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [workLocationId, organizationId]);

  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    try {
      const response = await axios.put(
        `${GlobalConst.API_URL}/api/worklocations/${workLocationId}`,
        values,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data?.status === 200) {
        successMsg("Success", "Work location updated successfully", false);
        navigate("/work-locations");
      } else {
        errorMsg(
          "Update Failed",
          `There was an error updating the work location. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      errorMsg("Update Failed", e.response?.data?.message || e.message, true);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="container-fluid p-10">
        <h6>Loading work location details...</h6>
      </div>
    );
  }

  if (!initialValues) {
    return (
      <div className="container-fluid p-10">
        <h6>Work location not found.</h6>
      </div>
    );
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Work Location</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Work Location</h5>
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
                    {/* Work Location Name */}
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="workLocationName"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Work Location Name <RequiredStar />
                      </label>
                      <Field
                        type="text"
                        name="workLocationName"
                        id="workLocationName"
                        className={`form-control form-control-lg form-control-solid ${errors.workLocationName && touched.workLocationName
                          ? "is-invalid"
                          : ""
                          }`}
                        placeholder="Enter work location name"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="workLocationName"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    {/* Address Fields */}
                    <div className="fv-row mb-10">
                      <label
                        htmlFor="streetAddress1"
                        className="form-label fs-6 fw-bold text-dark"
                      >
                        Address <RequiredStar />
                      </label>
                      <Field
                        type="text"
                        name="streetAddress1"
                        id="streetAddress1"
                        className={`form-control form-control-lg form-control-solid mb-3 ${errors.streetAddress1 && touched.streetAddress1 ? "is-invalid" : ""
                          }`}
                        placeholder="Enter address line 1"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="streetAddress1"
                        component="div"
                        className="invalid-feedback"
                      />
                      <Field
                        type="text"
                        name="streetAddress2"
                        id="streetAddress2"
                        className={`form-control form-control-lg form-control-solid ${errors.streetAddress2 && touched.streetAddress2 ? "is-invalid" : ""
                          }`}

                        placeholder="Enter address line 2"
                        disabled={isSubmitting}
                      />
                      <ErrorMessage
                        name="streetAddress2"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    {/* State, City, PIN */}
                    <div className="row">
                      <div className="col-md-4 mb-3">
                        <label htmlFor="state" className="form-label fs-6 fw-bold text-dark">
                          State <RequiredStar />
                        </label>
                        <Field
                          as="select"
                          name="state"
                          className={`form-control form-control-lg form-control-solid form-select ${errors.state && touched.state ? "is-invalid" : ""
                            }`}
                          disabled={isSubmitting}
                        >
                          <option value="">Select State</option>
                          {getStates().map((state) => (
                            <option key={state} value={state}>
                              {state}
                            </option>
                          ))}
                        </Field>
                        <ErrorMessage
                          name="state"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      <div className="col-md-4 mb-3">
                        <label htmlFor="city" className="form-label fs-6 fw-bold text-dark">
                          City <RequiredStar />
                        </label>
                        <Field
                          type="text"
                          name="city"
                          className={`form-control form-control-lg form-control-solid ${errors.city && touched.city ? "is-invalid" : ""
                            }`}
                          placeholder="City"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="city"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>

                      <div className="col-md-4 mb-3">
                        <label htmlFor="zipCode" className="form-label fs-6 fw-bold text-dark">
                          PIN Code <RequiredStar />
                        </label>
                        <Field
                          type="text"
                          name="zipCode"
                          className={`form-control form-control-lg form-control-solid ${errors.zipCode && touched.zipCode
                            ? "is-invalid"
                            : ""
                            }`}
                          placeholder="PIN Code"
                          disabled={isSubmitting}
                        />
                        <ErrorMessage
                          name="zipCode"
                          component="div"
                          className="invalid-feedback"
                        />
                      </div>
                    </div>

                    {/* Filing Address */}
                    <div className="fv-row mb-10">
                      <div className="form-check form-switch form-check-custom form-check-solid">
                        <Field
                          type="checkbox"
                          name="isFilingAddress"
                          id="isFilingAddress"
                          className="form-check-input"
                          disabled={isSubmitting}
                        />
                        <label className="form-check-label" htmlFor="isFilingAddress">
                          Set as filing address
                        </label>
                      </div>
                    </div>

                    {/* Save & Cancel */}
                    <div className="d-flex justify-content-between align-items-center mt-5">
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? "Updating..." : "Update"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/work-locations")}
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
    </>
  );
}
