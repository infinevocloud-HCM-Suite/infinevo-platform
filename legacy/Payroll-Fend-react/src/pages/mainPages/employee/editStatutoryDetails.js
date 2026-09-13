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

export default function EditStatutoryDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [employee, setEmployee] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";

  // Fetch employee data
  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);

        const employeeResponse = await axios.get(
          `${GlobalConst.API_URL}/api/employees/${id}`,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId
            }
          }
        );

        if (employeeResponse.data && employeeResponse.data.data) {
          setEmployee(employeeResponse.data.data);
        }
      } catch (error) {
        console.error("Failed to fetch data:", error);
        message.error("Failed to load employee data");
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [id, organizationId]);

  const validationSchema = Yup.object({
    eligibleForPf: Yup.boolean(),
    pfAccountNumber: Yup.string().when("eligibleForPf", {
      is: true,
      then: (schema) => schema.required("PF Account Number is required"),
      otherwise: (schema) => schema.nullable(),
    }),
    uan: Yup.string().when("eligibleForPf", {
      is: true,
      then: (schema) => schema.required("UAN is required"),
      otherwise: (schema) => schema.nullable(),
    }),
    eligibleForEps: Yup.boolean(),
    canContributeToEpsOnHigherWages: Yup.boolean(),
    eligibleForPt: Yup.boolean(),
    eligibleForLwf: Yup.boolean(),
    eligibleForEsi: Yup.boolean(),
  });


  const handleSubmit = async (values) => {
    setSubmitting(true);
    try {
      const updateData = {
        ...employee, // include all existing fields (so dateOfJoining etc. is preserved)
        eligibleForPf: values.eligibleForPf || false,
        pfAccountNumber: values.pfAccountNumber || null,
        uan: values.uan || null,
        eligibleForEps: values.eligibleForEps || false,
        canContributeToEpsOnHigherWages: values.canContributeToEpsOnHigherWages || false,
        eligibleForPt: values.eligibleForPt || false,
        eligibleForLwf: values.eligibleForLwf || false,
        eligibleForEsi: values.eligibleForEsi || false,
        organizationId: organizationId,
      };

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/employees/${id}`,
        updateData,
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          }
        }
      );

      if (response.data && response.data.status === 200) {
        successMsg("Success", "Employee statutory details updated successfully", false);
        navigate(`/employees/view/${id}`);
      } else {
        errorMsg("Update Failed", "Failed to update employee statutory details", false);
      }
    } catch (error) {
      console.error("API Error:", error);
      if (error.response) {
        errorMsg("Update Failed", error.response.data?.message || "Failed to update employee details", false);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSubmitting(false);
    }
  };


  if (loading) return <Loader />;

  if (!employee) {
    return (
      <div className="container-fluid p-10 bg-white">
        <div className="text-center">
          <h3>Employee not found</h3>
          <Button type="primary" onClick={() => navigate("/employees")}>
            Back to Employees
          </Button>
        </div>
      </div>
    );
  }

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Statutory Details</title>
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
              Edit Statutory Details - {employee.employeeNumber}
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
                  initialValues={{
                    eligibleForPf: employee.eligibleForPf || false,
                    pfAccountNumber: employee.pfAccountNumber || "",
                    uan: employee.uan || "",
                    eligibleForEps: employee.eligibleForEps || false,
                    canContributeToEpsOnHigherWages: employee.canContributeToEpsOnHigherWages || false,
                    eligibleForPt: employee.eligibleForPt || false,
                    eligibleForLwf: employee.eligibleForLwf || false,
                    eligibleForEsi: employee.eligibleForEsi || false,
                  }}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize={true}
                >
                  {({ values, setFieldValue }) => (
                    <Form className="form w-100">
                      <div className="mb-4">
                        <h6 className="fw-bold mb-2">Statutory Components</h6>
                        <div className="text-muted small mb-3">
                          Enable the necessary benefits and tax applicable for this employee.
                        </div>

                        {/* PF */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForPf"
                            id="eligibleForPf"
                            className="form-check-input"
                            checked={values.eligibleForPf}
                            onChange={(e) => setFieldValue("eligibleForPf", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForPf">
                            Employees' Provident Fund
                          </label>
                        </div>

                        {values.eligibleForPf && (
                          <div className="row g-3 mb-3">
                            <div className="col-12 col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                PF Account Number<RequiredStar />
                              </label>
                              <Field
                                type="text"
                                name="pfAccountNumber"
                                placeholder="AA/AAA/0000000/XXX/0000000"
                                className="form-control"
                              />
                              <ErrorMessage
                                name="pfAccountNumber"
                                component="div"
                                className="invalid-feedback"
                              />
                              <div className="text-muted small mt-1">
                                Format: AA/AAA/0000000/XXX/0000000
                              </div>
                            </div>

                            <div className="col-12 col-md-6">
                              <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                                UAN<RequiredStar />
                              </label>
                              <Field
                                type="text"
                                name="uan"
                                placeholder="000000000000"
                                className="form-control"
                              />
                              <ErrorMessage
                                name="uan"
                                component="div"
                                className="invalid-feedback"
                              />
                            </div>
                          </div>
                        )}

                        {/* EPS */}
                        {values.eligibleForPf && (
                          <div className="form-check form-check-custom form-check-dark mb-2 ms-3">
                            <Field
                              type="checkbox"
                              name="eligibleForEps"
                              id="eligibleForEps"
                              className="form-check-input"
                              checked={values.eligibleForEps}
                              onChange={(e) => setFieldValue("eligibleForEps", e.target.checked)}
                            />
                            <label className="form-check-label" htmlFor="eligibleForEps">
                              Contribute to Employee Pension Scheme
                            </label>
                          </div>
                        )}

                        {values.eligibleForEps && (
                          <div className="form-check form-check-custom form-check-dark mb-2 ms-5">
                            <Field
                              type="checkbox"
                              name="canContributeToEpsOnHigherWages"
                              id="canContributeToEpsOnHigherWages"
                              className="form-check-input"
                              checked={values.canContributeToEpsOnHigherWages}
                              onChange={(e) =>
                                setFieldValue("canContributeToEpsOnHigherWages", e.target.checked)
                              }
                            />
                            <label className="form-check-label" htmlFor="canContributeToEpsOnHigherWages">
                              Contribute EPS at actual PF Wages
                            </label>
                          </div>
                        )}

                        {/* PT */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForPt"
                            id="eligibleForPt"
                            className="form-check-input"
                            checked={values.eligibleForPt}
                            onChange={(e) => setFieldValue("eligibleForPt", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForPt">
                            Professional Tax
                          </label>
                        </div>

                        {/* LWF */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForLwf"
                            id="eligibleForLwf"
                            className="form-check-input"
                            checked={values.eligibleForLwf}
                            onChange={(e) => setFieldValue("eligibleForLwf", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForLwf">
                            Labor Welfare Fund
                          </label>
                        </div>

                        {/* ESI */}
                        <div className="form-check form-check-custom form-check-dark mb-2">
                          <Field
                            type="checkbox"
                            name="eligibleForEsi"
                            id="eligibleForEsi"
                            className="form-check-input"
                            checked={values.eligibleForEsi}
                            onChange={(e) => setFieldValue("eligibleForEsi", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="eligibleForEsi">
                            Employee State Insurance (ESI)
                          </label>
                        </div>
                      </div>

                      {/* Actions */}
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
                          icon={<EditOutlined />}
                        >
                          Update Statutory Details
                        </Button>
                      </div>
                    </Form>
                  )}
                </Formik>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
