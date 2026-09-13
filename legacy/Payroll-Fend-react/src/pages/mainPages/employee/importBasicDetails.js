import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

import Dropzone from "dropzone";
import "dropzone/dist/dropzone.css";

import Loader from "../../../shared/components/loaders/fullPageLoader";

import { GlobalConst } from "../../../shared/appConfig/globalConst";
import axios from "axios";

import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../shared/helpers/msgHelper";
import { format } from "date-fns";

export default function ImportBasicDetails() {
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
      const lines = content
        .split('\n')
        .filter(line => line.trim() !== '');
      
      if (lines.length === 0) {
        setParsedData([]);
        setShowPreview(false);
        return;
      }

      // Parse CSV headers
      const headers = lines[0].split(',').map(header => header.trim());
      
      // Parse data rows
      const data = lines.slice(1).map(line => {
        const values = line.split(',').map(value => value.trim());
        
        // Create object from CSV row
        const employee = {};
        headers.forEach((header, index) => {
          employee[header] = values[index] || '';
        });
        
        return employee;
      }).filter(employee => 
        employee['Employee Number'] && 
        employee['First Name'] && 
        employee['Last Name']
      ); // Filter out empty rows
      
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
      errorMsg("Error", "No valid employee data found in the file", false);
      return;
    }

    try {
      setSubmitting(true);
      setSigningIn(true);

      // Transform CSV data to match BasicDetailsDTO structure
      const employeesToImport = parsedData.map(employee => ({
        employeeNumber: employee['Employee Number'],
        firstName: employee['First Name'],
        middleName: employee['Middle Name'] || '',
        lastName: employee['Last Name'],
        gender: employee['Gender'],
        employeeStatus: employee['Employee Status'] || 'ACTIVE',
        dateOfJoining: employee['Date of Joining'],
        // designationName: employee['Designation'], // This should be designation ID, not name
        workMail: employee['Work Email'],
        // departmentName: employee['Department'], // This should be department ID, not name
        // workLocationName: employee['Worklocation Name'], // This should be work location ID, not name
        isPortalEnabled: employee['Enable Portal']?.toLowerCase() === 'yes',
        mobile: employee['Mobile Number'],
        // Additional fields that might be in your DTO
        eligibleForPf: false, // Default values, adjust as needed
        eligibleForPt: false,
        eligibleForLwf: false,
        eligibleForEsi: false,
        director: false,
        eligibleForEps: false,
        canContributeToEpsOnHigherWages: false
      }));

      const response = await axios.post(
        `${GlobalConst.API_URL}/api/employees/imports`,
        employeesToImport, // Send the array directly
        {
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data?.status === 201) {
        successMsg("Success", response.data.message || "Employees imported successfully", false);
        setTimeout(() => {
          navigate("/employees");
        }, 1500);
      } else {
        errorMsg(
          "Upload Failed",
          response.data?.message || `There was an error importing the employees. Please contact ${GlobalConst.SUPPORT_EMAIL}`,
          true
        );
      }
    } catch (e) {
      if (!_.isEmpty(e?.response?.data)) {
        errorMsg("Import Failed", e.response.data.message || "Failed to import employees", false);
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
      content = `Employee Number,First Name,Middle Name,Last Name,Gender,Employee Status,Date of Joining,Work Email, Name,Enable Portal,Personal Email,Father Name,Mobile Number,Date of Birth,Personal AddressLine1,Personal AddressLine2,Personal City,Personal State,Code,Personal Country,Personal PostalCode,PAN Number
1001,John,A,Doe,Male,Active,2023-01-15,Software Engineer,sample@company.com,IT,Pune Office,Yes,sample2@gmail.com,Robert Doe,9123456789,1995-05-20,123 Main St,Apt 101,Pune,Maharashtra,IN,India,411001,ABCDE1234F
1002,Jane,B,Smith,Female,Active,2022-06-10,Business Analyst,smith@company.com,Business,Mumbai Office,Yes,smith2@gmail.com,Michael Smith,9876543210,1993-08-12,456 Market Rd,Suite 202,Mumbai,Maharashtra,IN,India,400001,XYZAB5678K`;
    }
    
    const blob = new Blob([content], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `employee_basic_details_sample.${format}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Import Employee Basic Details</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Employee Basic Details - Select File</h5>
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
                        <a
                          href="#"
                          onClick={(e) => {
                            e.preventDefault(); // ✅ prevent navigation to /import#
                            downloadSample("csv");
                          }}
                          className="text-primary"
                        >
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
                        <h6 className="fw-bold">Preview ({parsedData.length} employees)</h6>
                        <div className="table-responsive">
                          <table className="table table-bordered">
                            <thead>
                              <tr>
                                <th>Employee Number</th>
                                <th>First Name</th>
                                <th>Last Name</th>
                                <th>Work Email</th>
                                {/* <th>Department</th> */}
                                <th>Status</th>
                              </tr>
                            </thead>
                            <tbody>
                              {parsedData.slice(0, 5).map((employee, index) => (
                                <tr key={index}>
                                  <td>{employee['Employee Number']}</td>
                                  <td>{employee['First Name']}</td>
                                  <td>{employee['Last Name']}</td>
                                  <td>{employee['Work Email']}</td>
                                  {/* <td>{employee['Department']}</td> */}
                                  <td>{employee['Employee Status'] || 'ACTIVE'}</td>
                                </tr>
                              ))}
                              {parsedData.length > 5 && (
                                <tr>
                                  <td colSpan="6" className="text-center">
                                    ... and {parsedData.length - 5} more employees
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
                        {isSubmitting ? "Importing..." : "Import Employees"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-light"
                        onClick={() => navigate("/employees")}
                      >
                        Cancel import
                      </button>
                    </div>
                  </Form>
                )}
              </Formik>
            </div>
          </div>
        </div>
      </div>
      {/* {signingIn && <Loader />} */}
    </>
  );
}

