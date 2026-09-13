
import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

import Dropzone from "dropzone";
import "dropzone/dist/dropzone.css";
// import Keycloak from "keycloak-js";
// import OAuth2Login from 'react-simple-oauth2-login';

import Loader from "../../../../shared/components/loaders/fullPageLoader";

import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";

// import qs from 'qs';
import { useDispatch } from "react-redux";
// import { updateToken } from "../../../shared/redux/reducers/authReducer";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
//import { dateFormats } from "../../../shared/appConfig/dateFormat";
import { format } from "date-fns";


export default function ImportDepartments() {
  const navigate = useNavigate();

  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [fileData, setFileData] = useState(null);
  const [parsedData, setParsedData] = useState([]);
  const [showPreview, setShowPreview] = useState(false);

  const [initialValues, setInitialValues] = useState({
    fileUpload: null
  });

  // Get organization ID from localStorage
  const organizationId = localStorage.getItem("organizationId");

  useEffect(() => {
    Dropzone.autoDiscover = false;

    const element = document.querySelector("#kt_dropzonejs_example_1");
    if (!element) return;

    const dz = new Dropzone(element, {
      url: "#", // We'll handle the upload manually
      maxFiles: 1,
      maxFilesize: 5, // MB
      acceptedFiles: ".csv,.tsv,.xlsx,.xls",
      addRemoveLinks: true,
      clickable: true,
      autoProcessQueue: false, // We'll handle the upload manually
      dictDefaultMessage: "" // 🔹 removes Dropzone's default text/button
    });

    dz.on("addedfile", (file) => {
      // Handle the file when it's added
      setFileData(file);
      parseFile(file);
      
      // Manually trigger form validation by updating the form field
      const fileInput = document.querySelector('input[name="fileUpload"]');
      if (fileInput) {
        const dataTransfer = new DataTransfer();
        dataTransfer.items.add(file);
        fileInput.files = dataTransfer.files;
        
        // Dispatch change event to trigger Formik validation
        const event = new Event('change', { bubbles: true });
        fileInput.dispatchEvent(event);
      }
    });

    dz.on("removedfile", () => {
      // Reset when file is removed
      setFileData(null);
      setParsedData([]);
      setShowPreview(false);
      
      // Clear the form field
      const fileInput = document.querySelector('input[name="fileUpload"]');
      if (fileInput) {
        fileInput.value = '';
        const event = new Event('change', { bubbles: true });
        fileInput.dispatchEvent(event);
      }
    });

    return () => {
      dz.destroy();
    };
  }, []);

  const parseFile = (file) => {
    const reader = new FileReader();
    
    reader.onload = (e) => {
      const content = e.target.result;
      const lines = content.split('\n').filter(line => line.trim() !== '');
      
      // Skip header row and parse data
      const data = lines.slice(1).map(line => {
        const values = line.split(',');
        return {
          name: values[0] ? values[0].trim() : '',
          departmentCode: values[1] ? values[1].trim() : '',
          description: values[2] ? values[2].trim() : ''
        };
      }).filter(dept => dept.name && dept.departmentCode); // Filter out empty rows
      
      setParsedData(data);
      setShowPreview(true);
    };
    
    reader.readAsText(file);
  };

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
          if (!value) return true; // Allow empty values (handled by required)
          const allowedTypes = [
            "text/csv", 
            "application/vnd.ms-excel", 
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel.sheet.macroEnabled.12"
          ];
          return allowedTypes.includes(value.type);
        }
      ),
  });

  const handleSubmit = async (values, { setSubmitting }) => {
    if (!fileData) {
      errorMsg("Error", "Please select a file to upload", false);
      return;
    }

    if (parsedData.length === 0) {
      errorMsg("Error", "No valid department data found in the file", false);
      return;
    }

    try {
      setSubmitting(true);
      setSigningIn(true);

      // Prepare data in the format expected by the backend (list of DepartmentDTO objects)
      const departmentsToImport = parsedData.map(dept => ({
        name: dept.name,
        departmentCode: dept.departmentCode,
        description: dept.description
      }));

      const response = await axios.post(
        `${GlobalConst.API_URL}/api/departments/imports`,
        departmentsToImport, // Send the array directly
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data?.status === 201) {
        successMsg("Success", response.data.message || "Departments imported successfully", false);
        setTimeout(() => {
          navigate("/departments");
        }, 1500);
      } else {
        errorMsg(
          "Upload Failed",
          response.data?.message || `There was an error importing the departments. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Import Failed", e.response.data.message || "Failed to import departments", false);
      } else {
        errorMsg(e.code, e.message, true);
      }
    } finally {
      setSubmitting(false);
      setSigningIn(false);
    }
  };

  const downloadSample = (format) => {
    let content = "";
    
    if (format === "csv") {
      content = "name,departmentCode,description\nHuman Resources,HR001,Handles all HR activities\nInformation Technology,IT001,Manages technical infrastructure";
    }
    
    const blob = new Blob([content], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `departments_sample.${format}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Import Departments</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Departments - Select File</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div
              className="w-100"
              style={{ maxWidth: "800px" }}
            >
              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
                enableReinitialize
              >
                {({ isSubmitting, errors, touched, setFieldValue }) => (
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
                        <a href="#" onClick={() => downloadSample("csv")} className="text-primary">
                          .csv format
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

                      {/* Hidden file input for Formik validation */}
                      <input
                        type="file"
                        name="fileUpload"
                        style={{ display: 'none' }}
                        onChange={(event) => {
                          setFieldValue("fileUpload", event.currentTarget.files[0]);
                        }}
                      />

                      <ErrorMessage
                        name="fileUpload"
                        component="div"
                        className="invalid-feedback"
                      />
                    </div>

                    {showPreview && parsedData.length > 0 && (
                      <div className="fv-row mb-10">
                        <h6 className="fw-bold">Preview ({parsedData.length} departments)</h6>
                        <div className="table-responsive">
                          <table className="table table-bordered">
                            <thead>
                              <tr>
                                <th>Name</th>
                                <th>Code</th>
                                <th>Description</th>
                              </tr>
                            </thead>
                            <tbody>
                              {parsedData.slice(0, 5).map((dept, index) => (
                                <tr key={index}>
                                  <td>{dept.name}</td>
                                  <td>{dept.departmentCode}</td>
                                  <td>{dept.description}</td>
                                </tr>
                              ))}
                              {parsedData.length > 5 && (
                                <tr>
                                  <td colSpan="3" className="text-center">
                                    ... and {parsedData.length - 5} more departments
                                  </td>
                                </tr>
                              )}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    )}

                    <div className="d-flex justify-content-between align-items-center mt-5">
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting || !fileData || parsedData.length === 0}
                      >
                        {isSubmitting ? "Importing..." : "Import Departments"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/departments")}
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