import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import { FaPlus } from "react-icons/fa";
import * as Yup from "yup";
import Loader from "../../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import { useDispatch } from "react-redux";
import { updateToken } from "../../../../../shared/redux/reducers/authReducer";
import { errorMsg } from "../../../../../shared/helpers/msgHelper";
import { format } from "date-fns";

export default function AddNewEarning() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [initialValues, setInitialValues] = useState({
    organizationName: "",
    firstName: "",
    industry: "",
    dateFormat: "",
    addressLine1: "",
    addressLine2: "",
    state: "",
    city: "",
    pinCode: "",
    logo: null,
    updateAllTransactions: false,
    filingAddress: "",
  });

  const [searchTerm, setSearchTerm] = useState("");
  const [selected, setSelected] = useState("");
  const [isOpen, setIsOpen] = useState(false);

  const options = [
    "Basic",
    "House Rent Allowance",
    "Dearness Allowance",
    "Conveyance Allowance",
    "Bonus",
    "Commission",
    "Children Education Allowance",
    "Hostel Expenditure Allowance",
    "Transport Allowance",
    "Helper Allowance",
    "Travelling Allowance",
    "Uniform Allowance",
    "Daily Allowance",
    "City Compensatory Allowance",
    "Overtime Allowance",
    "Telephone Allowance",
    "Fixed Medical Allowance",
    "Project Allowance",
    "Food Allowance",
    "Holiday Allowance",
    "Entertainment Allowance",
    "Custom Allowance",
    "Food Coupon",
    "Gift Coupon",
    "Research Allowance",
    "Books and Periodicals Allowance",
    "Shift Allowance",
    "Fuel Allowance",
    "Driver Allowance",
    "Leave Travel Allowance",
    "Vehicle Maintenance Allowance",
    "Telephone And Internet Allowance"
  ];

  const filteredOptions = options.filter((opt) =>
    opt.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const date = new Date();
  const dateFormats = [
    {
      value: "dd/MM/yyyy",
      label: `dd/MM/yyyy [ ${format(date, "dd/MM/yyyy")} ]`,
    },
    {
      value: "MM-dd-yyyy",
      label: `MM-dd-yyyy [ ${format(date, "MM-dd-yyyy")} ]`,
    },
    { value: "yy.MM.dd", label: `yy.MM.dd [ ${format(date, "yy.MM.dd")} ]` },
  ];

  const [showFilingModal, setShowFilingModal] = useState(false);
  const RequiredStar = () => <span className="text-danger">*</span>;

  const validationSchema = Yup.object().shape({
    organizationName: Yup.string()
      .required("Organisation name is required")
      .min(2, "Organisation name must be at least 2 characters")
      .max(100, "Organisation name cannot exceed 100 characters"),
    firstName: Yup.string()
      .required("Business location is required")
      .min(2, "Business location must be at least 2 characters"),
    industry: Yup.string()
      .required("Industry is required"),
    dateFormat: Yup.string()
      .required("Date format is required"),
    addressLine1: Yup.string()
      .required("Address Line 1 is required")
      .min(5, "Address Line 1 must be at least 5 characters"),
    addressLine2: Yup.string(),
    state: Yup.string()
      .required("State is required"),
    city: Yup.string()
      .required("City is required")
      .min(2, "City must be at least 2 characters"),
    pinCode: Yup.string()
      .required("PIN code is required")
      .test("valid-pin", "PIN code must be a 6-digit number", function (value) {
        return /^\d{6}$/.test(value);
      }),
    logo: Yup.mixed()
      .test("fileSize", "Logo must be less than 1MB", function (value) {
        return !value || (value && value.size <= 1024 * 1024);
      })
      .test("fileType", "Unsupported file format. Only PNG, JPG, JPEG allowed", function (value) {
        return (
          !value ||
          (value &&
            ["image/jpeg", "image/jpg", "image/png"].includes(value.type))
        );
      }),
    updateAllTransactions: Yup.boolean(),
    filingAddress: Yup.string(),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    if (!_.isEmpty(values.organizationName) && !_.isEmpty(values.addressLine1)) {
      setSubmitting(true);
      const postData = new FormData();
      postData.append("organizationName", values.organizationName);
      postData.append("firstName", values.firstName);
      postData.append("industry", values.industry);
      postData.append("dateFormat", values.dateFormat);
      postData.append("addressLine1", values.addressLine1);
      postData.append("addressLine2", values.addressLine2);
      postData.append("state", values.state);
      postData.append("city", values.city);
      postData.append("pinCode", values.pinCode);
      postData.append("updateAllTransactions", values.updateAllTransactions);
      postData.append("filingAddress", values.filingAddress);
      if (values.logo) {
        postData.append("logo", values.logo);
      }

      try {
        const response = await axios.post(
          `${GlobalConst.API_URL}/organisation-profile/save`,
          postData,
          {
            headers: {
              "Content-Type": "multipart/form-data",
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
            },
          }
        );

        if (
          !_.isEmpty(response) &&
          !_.isEmpty(response.data) &&
          response.data.message === "Profile saved successfully"
        ) {
          console.log("Organisation profile saved.");
        } else {
          errorMsg(
            "Save Failed",
            `There was an error saving the profile. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
            true
          );
        }
      } catch (e) {
        if (!_.isEmpty(e?.response?.data)) {
          errorMsg("Save Failed", e.response.data.err_msg, false);
        } else {
          errorMsg(e.code, e.message, true);
        }
      } finally {
        setSubmitting(false);
      }
    }
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Login</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">New Earning</h5>
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
                    <div className="fv-row mb-10 d-flex align-items-start">
                      <div style={{ flex: 1 }}>
                        <label htmlFor="earningType" className="form-label fs-6 fw-bold text-dark">
                          Earning Type <RequiredStar />
                        </label>

                        <div className="dropdown w-100" style={{ maxWidth: "500px" }}>
                          <button
                            type="button"
                            className={`btn btn-light dropdown-toggle w-100 text-start ${
                              errors.earningType && touched.earningType ? "is-invalid" : ""
                            }`}
                            onClick={() => setIsOpen(!isOpen)}
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
                                      // Navigate to custom earning form with the selected option
                                      navigate("/salary-components/add/custom-earning", { 
                                        state: { earningType: opt } 
                                      });
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
                        onClick={() => navigate("/salary-components")}
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
