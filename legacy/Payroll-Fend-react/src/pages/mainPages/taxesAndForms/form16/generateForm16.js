// src/pages/mainPages/taxesAndForms/form16/generateForm16.js
import React, { useRef, useState } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";
import {
  FaCloudUploadAlt,
  FaInfoCircle,
  FaCheckCircle,
  FaTimes,
} from "react-icons/fa";

export default function GenerateForm16() {
  const navigate = useNavigate();
  const inputRef = useRef(null);
  const [dragActive, setDragActive] = useState(false);
  const [file, setFile] = useState(null);

  const onBrowseClick = () => inputRef.current?.click();
  const onFileSelected = (e) => {
    const f = e.target.files?.[0];
    if (f) setFile(f);
  };
  const onDragOver = (e) => {
    e.preventDefault();
    setDragActive(true);
  };
  const onDragLeave = (e) => {
    e.preventDefault();
    setDragActive(false);
  };
  const onDrop = (e) => {
    e.preventDefault();
    setDragActive(false);
    const f = e.dataTransfer.files?.[0];
    if (f) setFile(f);
  };
  const onClear = () => setFile(null);
  const onUploadAndGenerate = () => {
    // TODO: wire API to upload ZIP and trigger generation
    // const fd = new FormData(); fd.append("file", file)
    // axios.post(..., fd, { headers: {...} })
    console.log("Uploading file:", file);
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Generate Form 16</title>
      </Helmet>

      {/* Header (white + bottom border) */}
      <div className="w-100 bg-white px-3 px-lg-5 py-3 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Generate Form 16</h5>
        <div />
      </div>

      {/* BODY — two columns: left content + right full-height sidebar with vertical divider */}
      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-3 p-md-4 p-lg-10 bg-white"
            style={{ minHeight: "calc(100vh - 64px)" }} // ensure enough height for the sidebar line
          >
            <div className="row g-0" style={{ minHeight: "70vh" }}>
              {/* LEFT: Uploader (limited width, not full) */}
              <div className="col-12 col-lg-8 pe-lg-5 d-flex">
                <div className="w-100" style={{ maxWidth: 501 }}>
                  <h6 className="fw-semibold mb-3">
                    Upload Form 16 – Part A ZIP File that you&apos;ve downloaded from TRACES utility application
                  </h6>

                  {/* Drop zone */}
                  <div
                    className={`border-2 rounded-3 p-4 d-flex align-items-center justify-content-center text-center ${
                      dragActive ? "border-primary" : "border-secondary-subtle"
                    }`}
                    style={{
                      minHeight: 220,
                      borderStyle: "dashed",
                      background: dragActive ? "rgba(59,130,246,0.04)" : "transparent",
                      transition: "all .15s ease",
                    }}
                    onDragOver={onDragOver}
                    onDragLeave={onDragLeave}
                    onDrop={onDrop}
                  >
                    <div>
                      <FaCloudUploadAlt size={48} className="text-primary mb-2" />
                      {!file ? (
                        <>
                          <div className="fw-semibold">Drop your files here</div>
                          <div className="text-muted small">
                            Drag and drop to upload or click here{" "}
                            <button
                              type="button"
                              className="btn btn-link p-0 align-baseline"
                              onClick={onBrowseClick}
                            >
                              Browse
                            </button>
                          </div>
                        </>
                      ) : (
                        <div className="d-flex flex-column align-items-center">
                          <div className="fw-semibold mb-1">{file.name}</div>
                          <div className="text-muted small mb-2">
                            {(file.size / (1024 * 1024)).toFixed(2)} MB
                          </div>
                          <span className="badge bg-light text-success border d-inline-flex align-items-center">
                            <FaCheckCircle className="me-1" /> Ready to upload
                          </span>
                          <button
                            type="button"
                            className="btn btn-sm btn-link text-danger mt-2"
                            onClick={onClear}
                          >
                            <FaTimes className="me-1" /> Remove file
                          </button>
                        </div>
                      )}
                    </div>
                    <input
                      ref={inputRef}
                      type="file"
                      accept=".zip"
                      onChange={onFileSelected}
                      hidden
                    />
                  </div>

                  {/* Note */}
                  <div className="text-muted small mt-3">
                    <span className="fw-semibold">Note:</span> The ZIP file you uploaded should
                    contain Form 16 – Part A files including the employee&apos;s PAN and its
                    file size should not exceed 25MB.
                  </div>

                  {/* Divider */}
                  <hr className="my-4" />

                  {/* Actions */}
                  <div className="d-flex align-items-center gap-2">
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={!file}
                      onClick={onUploadAndGenerate}
                    >
                      Upload and Generate
                    </button>
                    <button
                      type="button"
                      className="btn btn-light border"
                      onClick={() => navigate(-1)}
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </div>

              {/* RIGHT: Full-height sidebar with vertical separator */}
              <div className="col-12 col-lg-4 ps-lg-5">
                <div className="h-100 d-flex">
                  {/* vertical line + content */}
              <div className="row g-0 align-items-stretch" style={{ minHeight: "110vh" }}>

                    <div className="border rounded-3 p-3 p-md-5 bg-white mt-2 mt-lg-0">
                      <div className="d-flex align-items-center mb-2">
                        <FaInfoCircle className="text-warning me-2" />
                        <div className="fw-semibold">Things to Note</div>
                      </div>
                      <ul className="small mb-0 ps-3">
                        <li className="mb-2">
                          Since the employees will be matched with their form using PAN, make sure
                          that you have the correct PAN for all employees.
                        </li>
                   <li className="mb-2">
  Go to the TRACES utility app{" "}
  <a
    href="https://www.tdscpc.gov.in/app/login.xhtml"
    target="_blank"
    rel="noopener noreferrer"
    className="text-decoration-none text-primary"
  >
    (https://www.tdscpc.gov.in/app/login.xhtml)
  </a>{" "}
  to download Form 16 – Part A for your employees.
</li>

                        <li className="mb-2">
                          The Form 16 – Part A file should be in PDF. You can use the TRACES PDF
                          generation utility to convert the TXT files to PDF.
                        </li>
                        <li className="mb-2">
                          The name of the individual files inside the ZIP file should contain only
                          the PAN of the corresponding employees.
                        </li>
                        <li className="mb-2">
                          The Form 16 – Part A should <span className="fw-semibold">not</span> be
                          digitally signed while uploading it here.
                        </li>
                        <li className="mb-2">
                          We merge the Form 16 – Part A PDF file along with system generated Part B
                          file and give a single PDF document for every employee, which they can
                          access through the employee portal.
                        </li>
                        <li className="mb-2">
                          Form 12BA will be merged along with Form 16 for eligible employees.
                        </li>
                      </ul>
                    </div>

                    {/* push content to use vertical space nicely */}
                    <div className="flex-grow-1" />
                  </div>
                </div>
              </div>
            </div>
            {/* /row */}
          </div>
        </div>
      </div>
    </>
  );
}
