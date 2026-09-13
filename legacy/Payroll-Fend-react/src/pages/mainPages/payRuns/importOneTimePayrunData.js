//empty setting format
 
import { useState,useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";
import { useNavigate } from "react-router-dom";
import Dropzone from "dropzone";
 import "dropzone/dist/dropzone.css";

 
import Loader from "../../../shared/components/loaders/fullPageLoader";
 
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";
 
// import qs from 'qs';
import { useDispatch } from "react-redux";
// import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg } from "../../../shared/helpers/msgHelper";
//import { dateFormats } from "../../../shared/appConfig/dateFormat";
import { format } from "date-fns";
 
 
export default function ImportOneTimePayrunData() {
      const navigate = useNavigate();
   
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
 
const [initialValues, setInitialValues] = useState({
  fileUpload: null
});
 
 
useEffect(() => {
  Dropzone.autoDiscover = false;
 
  const element = document.querySelector("#kt_dropzonejs_example_1");
  if (!element) return;
 
  const dz = new Dropzone(element, {
    url: "/upload", // your backend endpoint
    maxFiles: 10,
    maxFilesize: 5, // MB
    acceptedFiles: ".csv,.tsv,.xls",
    addRemoveLinks: true,
    clickable: true,
    dictDefaultMessage: "" // 🔹 removes Dropzone's default text/button
  });
 
  return () => {
    dz.destroy();
  };
}, []);
 
 

 
  const RequiredStar = () => <span className="text-danger">*</span>;
 

 
 const validationSchema = Yup.object().shape({
  fileUpload: Yup.mixed()
    .required("Please select a file to upload")
    .test(
      "fileSize",
      "File size must be less than 5 MB",
      function (value) {
        return !value || (value && value.size <= 5 * 1024 * 1024);
      }
    )
    .test(
      "fileType",
      "Unsupported file format. Only CSV, TSV, XLS allowed",
      function (value) {
        return (
          !value ||
          (value &&
            ["text/csv", "application/vnd.ms-excel", "text/tab-separated-values"].includes(
              value.type
            ))
        );
      }
    ),
});
 
 
 
 
  const handleSubmit = async (values, { setSubmitting }) => {
  if (!_.isEmpty(values.fileUpload)) {
    setSubmitting(true);
 
    // Build FormData for file upload
    const postData = new FormData();
    postData.append("fileUpload", values.fileUpload);
 
    try {
      const response = await axios.post(
        `${GlobalConst.API_URL}/employee/import`, // 🔹 Your API endpoint
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
        response.data.message === "File uploaded successfully"
      ) {
        console.log("File upload successful.");
        // success message or redirect
      } else {
        errorMsg(
          "Upload Failed",
          `There was an error uploading the file. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Upload Failed", e.response.data.err_msg, false);
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
        <title>HRMS InfiNevoCloud - import Employee</title>
      </Helmet>
 
      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">One Time Payrun Data - Select File</h5>
        
 
      </div>
 
      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          {/* <div className="d-flex flex-center flex-column flex-column-fluid"> */}
          {/* <div className="d-flex flex-column flex-column-fluid align-items-start"> */}
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div
              className="w-100"
              style={{ maxWidth: "600px" }}
            >
              {/* Start of card content */}
 
              {/* Optional title/header */}
 
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
    htmlFor="fileUpload"
    className="form-label fs-6 fw-bold text-dark"
  >
    Import File <RequiredStar />
  </label>
 
  <div className="text-muted mb-3 fs-7">
    Download a sample{" "}
    <a href="/sample.csv" className="text-primary">
      .csv format
    </a>{" "}
    or{" "}
    <a href="/sample.xls" className="text-primary">
      .xls format
    </a>{" "}
    file and compare it with your import file to ensure that the file is ready to import.
  </div>
 
  <div
    id="kt_dropzonejs_example_1"
    className={`dropzone dz-clickable border border-dashed rounded bg-light text-center p-10 ${
      errors.fileUpload && touched.fileUpload ? "is-invalid" : ""
    }`}
  >
    <i className="ki-duotone ki-cloud-upload fs-3x text-gray-500 mb-2">
      <span className="path1"></span>
      <span className="path2"></span>
    </i>
    <div className="fw-semibold text-gray-800">
      Drop files here or click here to upload
    </div>
    <div className="text-muted fs-7 mt-1">
      Maximum File Size: 5 MB | File Format: CSV or TSV or XLS
    </div>
  </div>
 
  <ErrorMessage
    name="fileUpload"
    component="div"
    className="invalid-feedback"
  />
</div>
 
 
 
              {/* Save & Cancel */}
                    <div className="d-flex justify-content-between align-items-center mt-5">
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
                        onClick={() => navigate("/addOneTimePayoutDetails")}
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
 