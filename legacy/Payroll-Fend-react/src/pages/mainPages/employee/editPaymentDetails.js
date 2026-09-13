import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate, useParams } from "react-router-dom";
import { Button, Radio } from "antd";
import {
  BankOutlined,
  FileTextOutlined,
  DollarOutlined,
  EditOutlined,
  ArrowLeftOutlined,
} from "@ant-design/icons";
import Loader from "../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
import { successMsg, errorMsg } from "../../../shared/helpers/msgHelper";

const RequiredStar = () => <span className="text-danger">*</span>;

export default function EditPaymentDetails() {
  const navigate = useNavigate();
  const { id } = useParams(); // 👈 this is employeeId (UUID string)

  const [loading, setLoading] = useState(true);
  const [existingBankDetails, setExistingBankDetails] = useState(null);
  const [initialValues, setInitialValues] = useState({
    paymentMode: "",
    accountHolderName: "",
    bankName: "",
    bankAccountNumber: "",
    confirmAccountNumber: "",
    ifscCode: "",
    bankAccountType: "",
  });
  const [submitting, setSubmittingState] = useState(false);

  const organizationId =
    localStorage.getItem("organizationId") || "default-org-id";

  // 🔹 Fetch existing bank details on load (if any)
  useEffect(() => {
    const fetchBankDetails = async () => {
      try {
        setLoading(true);

        const response = await axios.get(
          `${GlobalConst.API_URL}/api/v1/employees/bank-details/${id}`,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          }
        );

        if (response.data && response.data.data) {
          const data = response.data.data;
          setExistingBankDetails(data);
          setInitialValues({
            paymentMode: data.paymentMode || "",
            accountHolderName: data.accountHolderName || "",
            bankName: data.bankName || "",
            bankAccountNumber: data.bankAccountNumber || "",
            confirmAccountNumber: data.bankAccountNumber || "",
            ifscCode: data.ifscCode || "",
            bankAccountType: data.bankAccountType || "",
          });
        } else {
          // No data – allow add new
          setExistingBankDetails(null);
        }
      } catch (error) {
        console.error("Failed to fetch bank details:", error);

        if (error.response) {
          const status = error.response.status;
          const message = error.response.data?.message || "";

          // ✅ Treat 404 as "no bank details yet"
          if (status === 404) {
            setExistingBankDetails(null);
          }
          // ✅ Treat this specific 500 as "no bank details yet" (your current backend bug)
          else if (
            status === 500 &&
            message.includes("EmployeeBankDetail.getId")
          ) {
            setExistingBankDetails(null);
          } else {
            errorMsg(
              "Fetch Failed",
              message || "Failed to load bank details",
              false
            );
          }
        } else if (error.request) {
          errorMsg(
            "Network Error",
            "Cannot connect to the server. Please check your connection.",
            false
          );
        } else {
          errorMsg("Error", "An unexpected error occurred", false);
        }
      } finally {
        setLoading(false);
      }
    };

    fetchBankDetails();
  }, [id, organizationId]);

  // 🔹 Validation schema (same rules as earlier)
  const validationSchema = Yup.object().shape({
    paymentMode: Yup.string()
      .required("Payment Mode is required")
      .oneOf(["banktransfer", "check", "cash"], "Invalid payment mode"),

    accountHolderName: Yup.string()
      .trim()
      .when("paymentMode", (paymentMode, schema) => {
        return paymentMode === "banktransfer"
          ? schema
              .required("Account Holder Name is required")
              .matches(
                /^[A-Za-z .]+$/,
                "Only alphabets, spaces and dot (.) are allowed"
              )
              .min(3, "Account Holder Name must be at least 3 characters")
              .max(100, "Account Holder Name cannot exceed 100 characters")
          : schema.nullable();
      }),

    bankName: Yup.string()
      .trim()
      .when("paymentMode", (paymentMode, schema) => {
        return paymentMode === "banktransfer"
          ? schema
              .required("Bank Name is required")
              .matches(
                /^[A-Za-z0-9 &().-]+$/,
                "Bank Name contains invalid characters"
              )
              .min(3, "Bank Name must be at least 3 characters")
              .max(100, "Bank Name cannot exceed 100 characters")
          : schema.nullable();
      }),

    bankAccountNumber: Yup.string()
      .trim()
      .when("paymentMode", (paymentMode, schema) => {
        return paymentMode === "banktransfer"
          ? schema
              .required("Account Number is required")
              .matches(/^\d{9,18}$/, "Account Number must be 9–18 digits")
              .test(
                "not-all-same",
                "Invalid Account Number (repeated digits)",
                (value) => {
                  if (!value) return true;
                  return !/^(\d)\1{8,17}$/.test(value);
                }
              )
          : schema.nullable();
      }),

    confirmAccountNumber: Yup.string()
      .trim()
      .when("paymentMode", (paymentMode, schema) => {
        return paymentMode === "banktransfer"
          ? schema
              .required("Please confirm account number")
              .oneOf(
                [Yup.ref("bankAccountNumber"), null],
                "Account numbers must match"
              )
          : schema.nullable();
      }),

    ifscCode: Yup.string()
      .trim()
      .when("paymentMode", (paymentMode, schema) => {
        return paymentMode === "banktransfer"
          ? schema
              .transform((val) => (val ? val.toUpperCase() : val))
              .required("IFSC Code is required")
              .matches(
                /^[A-Z]{4}0[A-Z0-9]{6}$/,
                "Invalid IFSC Code format (e.g., HDFC0001234)"
              )
          : schema.nullable();
      }),

    bankAccountType: Yup.string().when(
      "paymentMode",
      (paymentMode, schema) => {
        return paymentMode === "banktransfer"
          ? schema
              .required("Account Type is required")
              .oneOf(["current", "savings"], "Select a valid account type")
          : schema.nullable();
      }
    ),
  });

  // 🔹 Create-or-update submit handler
  const handleSubmit = async (values, { setSubmitting }) => {
    setSubmitting(true);
    setSubmittingState(true);

    try {
      const bankDetailsData = {
        // ✅ send as string UUID, do NOT convert to Number
        employeeId: id,
        paymentMode: values.paymentMode,
        accountHolderName:
          values.paymentMode === "banktransfer"
            ? values.accountHolderName?.trim()
            : null,
        bankName:
          values.paymentMode === "banktransfer"
            ? values.bankName?.trim()
            : null,
        bankAccountNumber:
          values.paymentMode === "banktransfer"
            ? values.bankAccountNumber?.trim()
            : null,
        ifscCode:
          values.paymentMode === "banktransfer"
            ? values.ifscCode?.toUpperCase().trim()
            : null,
        bankAccountType:
          values.paymentMode === "banktransfer"
            ? values.bankAccountType
            : null,
      };

      let response;
      if (existingBankDetails) {
        // 🔁 Update existing bank details (PUT)
        response = await axios.put(
          `${GlobalConst.API_URL}/api/v1/employees/bank-details/${id}`,
          bankDetailsData,
          {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          }
        );
      } else {
        // 🆕 Create new bank details (POST)
        response = await axios.post(
          `${GlobalConst.API_URL}/api/v1/employees/bank-details`,
          bankDetailsData,
          {
            headers: {
              "Content-Type": "application/json",
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          }
        );
      }

      if (
        response.data &&
        response.data.status >= 200 &&
        response.data.status < 300
      ) {
        successMsg(
          "Success",
          response.data.message || "Bank details saved successfully",
          false
        );
        navigate(`/employees/view/${id}`);
      } else {
        errorMsg(
          "Save Failed",
          response.data?.message || "Failed to save bank details",
          false
        );
      }
    } catch (error) {
      console.error("API Error:", error);
      if (error.response) {
        errorMsg(
          "Save Failed",
          error.response.data?.message || "Failed to save bank details",
          false
        );
      } else if (error.request) {
        errorMsg(
          "Network Error",
          "Cannot connect to the server. Please check your connection.",
          false
        );
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setSubmitting(false);
      setSubmittingState(false);
    }
  };

  if (loading) return <Loader />;

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Payment Details</title>
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
            <h6 className="mb-0 fw-semibold">Edit Payment Details - {id}</h6>
            <small className="text-muted">
              {existingBankDetails?.accountHolderName || ""}
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
                  initialValues={initialValues}
                  validationSchema={validationSchema}
                  onSubmit={handleSubmit}
                  enableReinitialize={true}
                >
                  {({
                    values,
                    setFieldValue,
                    isSubmitting,
                    errors,
                    touched,
                  }) => (
                    <Form className="form w-100">
                      <div className="mb-4">
                        <label className="form-label fs-6 fw-bold text-dark d-block mb-3">
                          How would you like to pay this employee?{" "}
                          <RequiredStar />
                        </label>

                        <ErrorMessage
                          name="paymentMode"
                          component="div"
                          className="text-danger small mb-2"
                        />

                        <div className="p-4 border-top border-gray-200 custom-radio-section">
                          <Radio.Group
                            onChange={(e) =>
                              setFieldValue("paymentMode", e.target.value)
                            }
                            value={values.paymentMode}
                            className="w-100"
                          >
                            <div className="d-flex flex-column gap-4">
                              {/* Bank Transfer */}
                              <Radio
                                value="banktransfer"
                                className="custom-radio-option p-3 border rounded"
                              >
                                <div className="d-flex align-items-center justify-content-between w-100">
                                  <div className="d-flex align-items-start">
                                    <BankOutlined className="fs-3 text-primary me-3" />
                                    <div>
                                      <div className="fw-bold fs-5 text-dark">
                                        Bank Transfer
                                      </div>
                                      <div className="text-muted fs-7">
                                        Manual transfer via bank website
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              </Radio>

                              {/* Bank Transfer Fields */}
                              {values.paymentMode === "banktransfer" && (
                                <div className="px-4 pb-4 border border-gray-300 rounded bg-light mt-3">
                                  <label className="form-label fw-semibold">
                                    Bank Account Details
                                  </label>

                                  {/* Account Holder Name */}
                                  <div className="mb-3">
                                    <label className="form-label fw-bold">
                                      Account Holder Name <RequiredStar />
                                    </label>
                                    <Field
                                      type="text"
                                      name="accountHolderName"
                                      className={`form-control ${
                                        errors.accountHolderName &&
                                        touched.accountHolderName
                                          ? "is-invalid"
                                          : ""
                                      }`}
                                      onInput={(e) => {
                                        e.target.value = e.target.value
                                          .replace(/[^A-Za-z .]/g, "")
                                          .toUpperCase();
                                      }}
                                    />
                                    <ErrorMessage
                                      name="accountHolderName"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  </div>

                                  {/* Bank Name */}
                                  <div className="mb-3">
                                    <label className="form-label fw-bold">
                                      Bank Name <RequiredStar />
                                    </label>
                                    <Field
                                      type="text"
                                      name="bankName"
                                      className={`form-control ${
                                        errors.bankName && touched.bankName
                                          ? "is-invalid"
                                          : ""
                                      }`}
                                      onInput={(e) => {
                                        e.target.value = e.target.value
                                          .replace(/[^A-Za-z0-9 &().-]/g, "")
                                          .toUpperCase();
                                      }}
                                    />
                                    <ErrorMessage
                                      name="bankName"
                                      component="div"
                                      className="invalid-feedback"
                                    />
                                  </div>

                                  {/* Account Numbers */}
                                  <div className="row g-3 mb-3">
                                    <div className="col-md-6">
                                      <label className="form-label fw-bold">
                                        Account Number <RequiredStar />
                                      </label>
                                      <Field
                                        type="text"
                                        name="bankAccountNumber"
                                        className={`form-control ${
                                          errors.bankAccountNumber &&
                                          touched.bankAccountNumber
                                            ? "is-invalid"
                                            : ""
                                        }`}
                                        maxLength={18}
                                        onInput={(e) => {
                                          e.target.value = e.target.value.replace(
                                            /\D/g,
                                            ""
                                          );
                                        }}
                                      />
                                      <ErrorMessage
                                        name="bankAccountNumber"
                                        component="div"
                                        className="invalid-feedback"
                                      />
                                    </div>
                                    <div className="col-md-6">
                                      <label className="form-label fw-bold">
                                        Confirm Account Number{" "}
                                        <RequiredStar />
                                      </label>
                                      <Field
                                        type="text"
                                        name="confirmAccountNumber"
                                        className={`form-control ${
                                          errors.confirmAccountNumber &&
                                          touched.confirmAccountNumber
                                            ? "is-invalid"
                                            : ""
                                        }`}
                                        maxLength={18}
                                        onInput={(e) => {
                                          e.target.value = e.target.value.replace(
                                            /\D/g,
                                            ""
                                          );
                                        }}
                                      />
                                      <ErrorMessage
                                        name="confirmAccountNumber"
                                        component="div"
                                        className="invalid-feedback"
                                      />
                                    </div>
                                  </div>

                                  {/* IFSC + Type */}
                                  <div className="row g-3">
                                    <div className="col-md-6">
                                      <label className="form-label fw-bold">
                                        IFSC <RequiredStar />
                                      </label>
                                      <Field
                                        type="text"
                                        name="ifscCode"
                                        className={`form-control ${
                                          errors.ifscCode && touched.ifscCode
                                            ? "is-invalid"
                                            : ""
                                        }`}
                                        maxLength={11}
                                        onInput={(e) => {
                                          e.target.value = e.target.value
                                            .toUpperCase()
                                            .replace(/[^A-Z0-9]/g, "");
                                        }}
                                      />
                                      <ErrorMessage
                                        name="ifscCode"
                                        component="div"
                                        className="invalid-feedback"
                                      />
                                    </div>
                                    <div className="col-md-6">
                                      <label className="form-label fw-bold">
                                        Account Type <RequiredStar />
                                      </label>
                                      <div>
                                        <Radio.Group
                                          onChange={(e) =>
                                            setFieldValue(
                                              "bankAccountType",
                                              e.target.value
                                            )
                                          }
                                          value={values.bankAccountType}
                                        >
                                          <Radio value="current">Current</Radio>
                                          <Radio value="savings">Savings</Radio>
                                        </Radio.Group>
                                      </div>
                                      <ErrorMessage
                                        name="bankAccountType"
                                        component="div"
                                        className="invalid-feedback"
                                      />
                                    </div>
                                  </div>
                                </div>
                              )}

                              {/* Cheque */}
                              <Radio
                                value="check"
                                className="custom-radio-option p-3 border rounded"
                              >
                                <div className="d-flex align-items-center justify-content-between w-100">
                                  <div className="d-flex align-items-center">
                                    <FileTextOutlined className="fs-3 text-primary me-3" />
                                    <div className="fw-bold fs-5 text-dark">
                                      Cheque
                                    </div>
                                  </div>
                                </div>
                              </Radio>

                              {/* Cash */}
                              <Radio
                                value="cash"
                                className="custom-radio-option p-3 border rounded"
                              >
                                <div className="d-flex align-items-center justify-content-between w-100">
                                  <div className="d-flex align-items-center">
                                    <DollarOutlined className="fs-3 text-primary me-3" />
                                    <div className="fw-bold fs-5 text-dark">
                                      Cash
                                    </div>
                                  </div>
                                </div>
                              </Radio>
                            </div>
                          </Radio.Group>
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
                          loading={submitting || isSubmitting}
                          icon={<EditOutlined />}
                        >
                          {existingBankDetails
                            ? "Update Payment Details"
                            : "Save Payment Details"}
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

      <style jsx="true">{`
        .custom-radio-section .ant-radio-wrapper {
          font-size: 1.1rem;
        }
        .custom-radio-section .ant-radio-inner {
          width: 22px !important;
          height: 22px !important;
          border-width: 2px;
        }
        .custom-radio-section .ant-radio-checked .ant-radio-inner {
          border-color: #1677ff;
        }
        .custom-radio-section .ant-radio-inner::after {
          width: 12px;
          height: 12px;
          top: 4px;
          left: 4px;
        }
        .custom-radio-option:hover {
          border-color: #1677ff;
          background-color: #f9fcff;
          transition: all 0.2s ease;
        }
      `}</style>
    </>
  );
}
