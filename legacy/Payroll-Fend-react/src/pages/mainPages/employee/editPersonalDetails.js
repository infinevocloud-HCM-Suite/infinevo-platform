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
import { getStates } from "../../../shared/appConfig/stateList";

const RequiredStar = () => <span className="text-danger">*</span>;

export default function EditPersonalDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [employee, setEmployee] = useState(null);
  const [personalDetails, setPersonalDetails] = useState(null);

  const organizationId = localStorage.getItem("organizationId") || "default-org-id";
  const token = localStorage.getItem("__t");

  // Fetch employee and personal details data
  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        
        // Fetch employee basic details
        const employeeResponse = await axios.get(
          `${GlobalConst.API_URL}/api/employees/${id}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
              organizationId: organizationId
            }
          }
        );
        
        if (employeeResponse.data && employeeResponse.data.data) {
          setEmployee(employeeResponse.data.data);
        }
        
        // Fetch personal details
        const personalDetailsResponse = await axios.get(
          `${GlobalConst.API_URL}/api/employees/personal-details/${id}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
              organizationId: organizationId
            }
          }
        );
        
        if (personalDetailsResponse.data && personalDetailsResponse.data.data) {
          setPersonalDetails(personalDetailsResponse.data.data);
        }
        
      } catch (error) {
        console.error("Failed to fetch data:", error);
        // If personal details not found, that's okay - we'll create them
        if (error.response && error.response.status !== 404) {
          message.error('Failed to load personal details');
        }
      } finally {
        setLoading(false);
      }
    };
    
    fetchData();
  }, [id, organizationId, token]);

  const validationSchema = Yup.object().shape({
    dateOfBirth: Yup.date().required("Date of Birth is required"),
    fatherName: Yup.string().required("Father Name is required"),
    pan: Yup.string()
      .matches(/^[A-Z]{5}[0-9]{4}[A-Z]{1}$/, "Invalid PAN Number format")
      .required("PAN Number is required"),
    differentlyAbledType: Yup.string().required("Please select a type"),
    personalMail: Yup.string()
      .email("Invalid email address")
      .required("Personal Email is required"),
    presentResidentialAddress: Yup.object({
      addressLine1: Yup.string().required("Address Line 1 is required"),
      addressLine2: Yup.string().nullable(),
      city: Yup.string().required("City is required"),
      state: Yup.string().required("State is required"),
      zipCode: Yup.string()
        .matches(/^[1-9][0-9]{5}$/, "Invalid PIN Code")
        .required("PIN Code is required")
    }),
  });

  const handleSubmit = async (values) => {
    setSubmitting(true);
    try {
      // Prepare data according to EmployeePersonalDetailDTO
      const personalDetailsData = {
       employeeId: id,
        personalMail: values.personalMail,
        dateOfBirth: values.dateOfBirth,
        fatherName: values.fatherName,
        pan: values.pan,
        differentlyAbledType: values.differentlyAbledType,
        isEligibleForFullIncomeTaxExemption: values.isEligibleForFullIncomeTaxExemption || false,
        organizationId: organizationId,
        presentResidentialAddress: {
          addressLine1: values.presentResidentialAddress.addressLine1,
          addressLine2: values.presentResidentialAddress.addressLine2 || "",
          city: values.presentResidentialAddress.city,
          state: values.presentResidentialAddress.state,
          zipCode: values.presentResidentialAddress.zipCode,
          stateCode: "" // You might need to map this based on state
        },
        customFields: [] // Empty array as we don't have custom fields in the form
      };

      // Use PUT for update, POST for create
      const apiUrl = `${GlobalConst.API_URL}/api/employees/personal-details`;
      let response;

      if (personalDetails) {
        // Update existing personal details
        response = await axios.put(
          `${apiUrl}/${id}`,
          personalDetailsData,
          {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${token}`,
              organizationId: organizationId
            }
          }
        );
      } else {
        // Create new personal details
        response = await axios.post(
          apiUrl,
          personalDetailsData,
          {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${token}`,
              organizationId: organizationId
            }
          }
        );
      }

      if (response.data && (response.data.status === 200 || response.data.status === 201)) {
        successMsg("Success", response.data.message || "Personal details saved successfully", false);
        navigate(`/employees/view/${id}`);
      } else {
        errorMsg("Save Failed", "Failed to save personal details", false);
      }
    } catch (error) {
      console.error("API Error:", error);
      if (error.response) {
        errorMsg("Save Failed", error.response.data?.message || "Failed to save personal details", false);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", false);
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSubmitting(false);
    }
  };

  // 🔹 Helper function to calculate age from DOB
  const calculateAge = (dob) => {
    if (!dob) return "";
    const today = new Date();
    const birthDate = new Date(dob);
    let age = today.getFullYear() - birthDate.getFullYear();
    const m = today.getMonth() - birthDate.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
      age--;
    }
    return age;
  };

  if (loading) return <Loader />;

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
        <title>HRMS InfiNevoCloud - Edit Personal Details</title>
      </Helmet>

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
              Edit Personal Details - {employee.employeeNumber}
            </h6>
            <small className="text-muted">
              {employee.firstName} {employee.lastName}
            </small>
          </div>
        </div>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid">
          <div className="container-fluid p-4 bg-white">
            <div className="card">
              <div className="card-body">
                <Formik
                  initialValues={{
                    dateOfBirth: personalDetails?.dateOfBirth || "",
                    age: personalDetails?.dateOfBirth ? calculateAge(personalDetails.dateOfBirth) : "",
                    fatherName: personalDetails?.fatherName || "",
                    pan: personalDetails?.pan || "",
                    differentlyAbledType: personalDetails?.differentlyAbledType || "None",
                    personalMail: personalDetails?.personalMail || "",
                    isEligibleForFullIncomeTaxExemption: personalDetails?.isEligibleForFullIncomeTaxExemption || false,
                    presentResidentialAddress: personalDetails?.presentResidentialAddress || {
                      addressLine1: "",
                      addressLine2: "",
                      city: "",
                      state: "",
                      zipCode: ""
                    }
                  }}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize={true}
                >
                  {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                    <Form className="form w-100">
                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Date of Birth <RequiredStar />
                          </label>
                          <Field
                            type="date"
                            name="dateOfBirth"
                            className={`form-control ${errors.dateOfBirth && touched.dateOfBirth ? "is-invalid" : ""}`}
                            onChange={(e) => {
                              const dob = e.target.value;
                              setFieldValue("dateOfBirth", dob);
                              const age = calculateAge(dob);
                              setFieldValue("age", age);
                            }}
                          />
                          <ErrorMessage
                            name="dateOfBirth"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Age
                          </label>
                          <Field
                            type="number"
                            name="age"
                            placeholder="Age"
                            className="form-control"
                            value={values.age || ""}
                            disabled
                          />
                        </div>
                      </div>

                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Father Name <RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="fatherName"
                            placeholder="Enter Father Name"
                            className={`form-control ${errors.fatherName && touched.fatherName ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage
                            name="fatherName"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            PAN Number <RequiredStar />
                          </label>
                          <Field
                            type="text"
                            name="pan"
                            placeholder="ABCDE1234F"
                            maxLength="10"
                            className={`form-control ${errors.pan && touched.pan ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage
                            name="pan"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      <div className="row g-3 mb-4">
                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Differently Abled Type <RequiredStar />
                          </label>
                          <Field
                            as="select"
                            name="differentlyAbledType"
                            className={`form-select ${errors.differentlyAbledType && touched.differentlyAbledType ? "is-invalid" : ""}`}
                          >
                            <option value="">Select Type</option>
                            <option value="None">None</option>
                            <option value="Visual">Visual</option>
                            <option value="Hearing">Hearing</option>
                            <option value="Speech">Speech</option>
                            <option value="Mobility">Mobility</option>
                            <option value="Other">Other</option>
                          </Field>
                          <ErrorMessage
                            name="differentlyAbledType"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="col-12 col-md-6">
                          <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                            Personal Email Address <RequiredStar />
                          </label>
                          <Field
                            type="email"
                            name="personalMail"
                            placeholder="example@email.com"
                            className={`form-control ${errors.personalMail && touched.personalMail ? "is-invalid" : ""}`}
                          />
                          <ErrorMessage
                            name="personalMail"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>
                      </div>

                      {/* Full Income Tax Exemption Checkbox */}
                      <div className="mb-4">
                        <div className="form-check form-check-custom form-check-dark mb-3 p-2 rounded">
                          <Field
                            type="checkbox"
                            name="isEligibleForFullIncomeTaxExemption"
                            id="isEligibleForFullIncomeTaxExemption"
                            className="form-check-input"
                            checked={values.isEligibleForFullIncomeTaxExemption}
                            onChange={(e) => setFieldValue("isEligibleForFullIncomeTaxExemption", e.target.checked)}
                          />
                          <label className="form-check-label" htmlFor="isEligibleForFullIncomeTaxExemption">
                            Eligible for Full Income Tax Exemption
                          </label>
                        </div>
                      </div>

                      <div className="mb-4">
                        <label className="form-label fs-6 fw-bold text-dark d-block mb-2">
                          Residential Address <RequiredStar />
                        </label>

                        <div className="mb-3">
                          <Field
                            type="text"
                            name="presentResidentialAddress.addressLine1"
                            placeholder="Address Line 1"
                            className={`form-control ${errors.presentResidentialAddress?.addressLine1 &&
                                touched.presentResidentialAddress?.addressLine1
                                ? "is-invalid"
                                : ""}`}
                          />
                          <ErrorMessage
                            name="presentResidentialAddress.addressLine1"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="mb-3">
                          <Field
                            type="text"
                            name="presentResidentialAddress.addressLine2"
                            placeholder="Address Line 2"
                            className={`form-control ${errors.presentResidentialAddress?.addressLine2 &&
                                touched.presentResidentialAddress?.addressLine2
                                ? "is-invalid"
                                : ""}`}
                          />
                          <ErrorMessage
                            name="presentResidentialAddress.addressLine2"
                            component="div"
                            className="invalid-feedback"
                          />
                        </div>

                        <div className="row g-3">
                          <div className="col-12 col-md-4">
                            <Field
                              type="text"
                              name="presentResidentialAddress.city"
                              placeholder="City"
                              className={`form-control ${errors.presentResidentialAddress?.city &&
                                  touched.presentResidentialAddress?.city
                                  ? "is-invalid"
                                  : ""}`}
                            />
                            <ErrorMessage
                              name="presentResidentialAddress.city"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>

                          <div className="col-12 col-md-4">
                            <Field
                              as="select"
                              name="presentResidentialAddress.state"
                              className={`form-select ${errors.presentResidentialAddress?.state &&
                                  touched.presentResidentialAddress?.state
                                  ? "is-invalid"
                                  : ""}`}
                            >
                              <option value="">Select State</option>
                              {getStates().map((state) => (
                                <option key={state} value={state}>
                                  {state}
                                </option>
                              ))}
                            </Field>
                            <ErrorMessage
                              name="presentResidentialAddress.state"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>

                          <div className="col-12 col-md-4">
                            <Field
                              type="text"
                              name="presentResidentialAddress.zipCode"
                              placeholder="PIN Code"
                              maxLength="6"
                              className={`form-control ${errors.presentResidentialAddress?.zipCode &&
                                  touched.presentResidentialAddress?.zipCode
                                  ? "is-invalid"
                                  : ""}`}
                            />
                            <ErrorMessage
                              name="presentResidentialAddress.zipCode"
                              component="div"
                              className="invalid-feedback"
                            />
                          </div>
                        </div>
                      </div>

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
                          loading={submitting || isSubmitting}
                          icon={<EditOutlined />}
                        >
                          Update Personal Details
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