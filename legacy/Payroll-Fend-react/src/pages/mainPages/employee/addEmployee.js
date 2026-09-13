import React, { useState, useEffect, useCallback, useRef } from "react";
import { Stepper, Step } from "react-form-stepper";
import BasicDetails from "./basicDetails";
import SalaryDetails from "./salaryDetails";
import PersonalDetails from "./personalDetails";
import PaymentInformation from "./paymentInformation";
import { Alert, Modal, Spin, Button, message } from "antd";
import { useNavigate } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../shared/appConfig/globalConst";
import {
  clearAddEmployeeDraft,
  saveAddEmployeeWizardDraft,
  readAddEmployeeWizardDraft,
} from "../../../shared/helpers/addEmployeeDraft";

const AddEmployee = () => {
  const [activeStep, setActiveStep] = useState(0);
  const navigate = useNavigate();
  const [employeeId, setEmployeeId] = useState(null);
  const [draftChecked, setDraftChecked] = useState(false);
  const [resumeMessage, setResumeMessage] = useState(null);
  const didWarnUnload = useRef(false);

  const [formData, setFormData] = useState({
    firstName: "",
    middleName: "",
    lastName: "",
    employeeNumber: "",
    dateOfJoining: "",
    workMail: "",
    mobile: "",
    gender: "",
    WorkLocationName: "",
    departmentName: "",
    designationName: "",
    director: false,
    enablePortalAccess: false,
    statutoryComponents: {
      employeesProvidentFund: false,
      pfAccountNumber: "",
      uan: "",
      employeePensionScheme: false,
      epsAtActualPfWages: false,
      professionalTax: false,
    },
    salaryDetails: {
      annualCTC: 0,
      basicPercent: 50,
      hraPercent: 50,
      conveyance: 0,
      basicMonthly: 0,
      hraMonthly: 0,
      conveyanceMonthly: 0,
      fixedMonthly: 0,
      basic: 0,
      hra: 0,
      conveyanceDisplay: 0,
      fixedAllowance: 0,
      monthlyCTC: 0,
      annualCTCComputed: 0,
    },
    dateOfBirth: "",
    age: "",
    fatherName: "",
    panNumber: "",
    differentlyAbledType: "",
    personalEmail: "",
    residentialAddress: {
      addressLine1: "",
      addressLine2: "",
      city: "",
      state: "",
      pincode: "",
    },
    paymentType: "",
    bankName: "",
    accountNumber: "",
    ifscCode: "",
    accountHolderName: "",
  });

  const persistDraftAfterStepChange = useCallback((nextStep, empIdOverride) => {
    const emp =
      empIdOverride !== undefined ? empIdOverride : employeeId;
    saveAddEmployeeWizardDraft({
      employeeId: emp,
      activeStep: nextStep,
    });
  }, [employeeId]);

  useEffect(() => {
    const token = localStorage.getItem("__t");
    const organizationId = localStorage.getItem("organizationId");
    const { employeeId: draftEmpId, activeStep: draftStep } =
      readAddEmployeeWizardDraft();

    if (!draftEmpId || !organizationId || !token) {
      setDraftChecked(true);
      return;
    }

    (async () => {
      try {
        await axios.get(
          `${GlobalConst.API_URL}/api/employees/${encodeURIComponent(draftEmpId)}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
              organizationId,
            },
          }
        );

        saveAddEmployeeWizardDraft({
          employeeId: draftEmpId,
          activeStep: draftStep,
        });
        setEmployeeId(draftEmpId);
        setActiveStep(draftStep);
        if (draftStep > 0) {
          setResumeMessage(
            "This browser had an unfinished add-employee wizard. You can continue below or start over."
          );
        }
      } catch {
        clearAddEmployeeDraft();
        setEmployeeId(null);
        setActiveStep(0);
      } finally {
        setDraftChecked(true);
      }
    })();
  }, []);

  useEffect(() => {
    if (!draftChecked || activeStep <= 0) return;
    const onBeforeUnload = (e) => {
      if (didWarnUnload.current) return;
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [draftChecked, activeStep]);

  const steps = [
    { label: "Basic Details" },
    { label: "Salary Details" },
    { label: "Personal Details" },
    { label: "Payment Information" },
  ];

  const handleNext = (id = null) => {
    if (id != null) {
      setEmployeeId(id);
    }
    const empForDraft = id != null ? id : employeeId;
    setActiveStep((prev) => {
      const next = prev + 1;
      saveAddEmployeeWizardDraft({
        employeeId: empForDraft != null ? empForDraft : id,
        activeStep: next,
      });
      return next;
    });
  };

  const handlePrev = () => {
    setActiveStep((prev) => {
      const next = Math.max(prev - 1, 0);
      persistDraftAfterStepChange(next);
      return next;
    });
  };

  const handleStartOver = () => {
    Modal.confirm({
      title: "Start over?",
      content:
        "This clears the wizard draft saved in this browser only. Anyone already saved in Step 1 will still appear under Employees—you can edit them there.",
      okText: "Start over",
      cancelText: "Cancel",
      onOk: () => {
        clearAddEmployeeDraft();
        setEmployeeId(null);
        setActiveStep(0);
        setResumeMessage(null);
      },
    });
  };

  const handleSubmitFinal = (values) => {
    didWarnUnload.current = true;
    const finalData = {
      ...formData,
      ...values,
      id: employeeId,
    };

    console.log("Final form data:", finalData);
    clearAddEmployeeDraft();
    message.success({
      content:
        "🎉 Employee has been created successfully and added to the organization records!",
      duration: 3,
      style: {
        marginTop: "10vh",
        fontSize: "16px",
        fontWeight: 500,
      },
    });
    navigate("/employees");
  };

  const updateFormData = (stepData) => {
    setFormData((prev) => ({ ...prev, ...stepData }));
  };

  return (
    <div className="card">
      <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
        <h3 className="card-title mb-0">Add Employee</h3>
        {(employeeId != null || activeStep > 0) && (
          <Button size="small" onClick={handleStartOver}>
            Start over wizard
          </Button>
        )}
      </div>
      <div className="card-body">
        {!draftChecked ? (
          <div className="text-center py-5">
            <Spin tip="Checking saved progress…" />
          </div>
        ) : (
          <>
            {resumeMessage ? (
              <Alert
                className="mb-3"
                type="info"
                showIcon
                closable
                onClose={() => setResumeMessage(null)}
                message={resumeMessage}
              />
            ) : null}

            <Stepper activeStep={activeStep}>
              {steps.map((step, index) => (
                <Step key={index} label={step.label} />
              ))}
            </Stepper>

            <div className="mt-5">
              {activeStep === 0 && (
                <BasicDetails
                  initialValues={formData}
                  onNext={handleNext}
                />
              )}

              {activeStep === 1 && employeeId ? (
                <SalaryDetails
                  initialValues={formData}
                  onNext={handleNext}
                  onPrev={handlePrev}
                  employeeId={employeeId}
                />
              ) : activeStep === 1 ? (
                <Alert
                  type="warning"
                  message="Employee ID missing. Save Basic Details again or Start over."
                />
              ) : null}

              {activeStep === 2 && employeeId ? (
                <PersonalDetails
                  initialValues={formData}
                  onNext={handleNext}
                  onPrev={handlePrev}
                  employeeId={employeeId}
                />
              ) : activeStep === 2 ? (
                <Alert
                  type="warning"
                  message="Employee ID missing. Save Basic Details again or Start over."
                />
              ) : null}

              {activeStep === 3 && employeeId ? (
                <PaymentInformation
                  initialValues={formData}
                  onSubmit={handleSubmitFinal}
                  onPrev={handlePrev}
                  employeeId={employeeId}
                />
              ) : activeStep === 3 ? (
                <Alert
                  type="warning"
                  message="Employee ID missing. Save Basic Details again or Start over."
                />
              ) : null}
            </div>
          </>
        )}
      </div>
    </div>
  );
};

export default AddEmployee;
