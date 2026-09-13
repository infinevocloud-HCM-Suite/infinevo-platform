import { useState } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate } from "react-router-dom";

export default function LeaveAttendanceSetup() {
  const navigate = useNavigate();
  
  const [steps, setSteps] = useState([
    {
      id: 1,
      title: "Leave types",
      description: "Create new leave types or enable default leave types to align with the organisation's specifications.",
      completed: false,
      buttons: [
        { 
          text: "Configure Now", 
          action: "configure", 
          variant: "primary" 
        },
        { 
          text: "Marks as Completed", 
          action: "complete", 
          variant: "light" 
        }
      ]
    },
    {
      id: 2,
      title: "Holiday Management",
      description: "Create new holidays for all year work locations and restrict them for employees, if needed.",
      completed: false,
      buttons: [
        { 
          text: "Configure Now", 
          action: "configure", 
          variant: "primary" 
        },
        { 
          text: "Marks as Completed", 
          action: "complete", 
          variant: "light" 
        }
      ]
    },
    {
      id: 3,
      title: "Attendance Management",
      description: "Customize work shift times, total checks in hours, workshop duration, and attendance regularisation.",
      completed: false,
      buttons: [
        { 
          text: "Configure Now", 
          action: "configure", 
          variant: "primary" 
        },
        { 
          text: "Do It Later", 
          action: "complete", 
          variant: "light" 
        }
      ]
    },
    {
      id: 4,
      title: "Setup Preferences",
      description: "Define the attendance cycle, report generation day, and choose to include leave encashment details for pay runs.",
      completed: false,
      buttons: [
        { 
          text: "Configure Now", 
          action: "configure", 
          variant: "primary" 
        }
      ]
    },
    {
      id: 5,
      title: "Employee Leave Balance",
      description: "Upload your employees' leave balances from your previous accounts to continue from where you left off.",
      completed: false,
      buttons: [
        { 
          text: "Import", 
          action: "configure", 
          variant: "primary" 
        },
        { 
          text: "Do It Later", 
          action: "complete", 
          variant: "light" 
        }
      ]
    }
  ]);

  const [disabledModules, setDisabledModules] = useState(false);

  const handleStepAction = (stepId, action) => {
    if (action === "complete") {
      setSteps(prevSteps => 
        prevSteps.map(step => 
          step.id === stepId ? { ...step, completed: true } : step
        )
      );
    } else if (action === "configure") {
      // Navigate to respective configuration pages
      switch(stepId) {
        case 1:
          navigate("/leave-types");
          break;
        case 2:
          navigate("/holidays");
          break;
        case 3:
          navigate("/attendance");
          break;
        case 4:
          navigate("/attendence-preferences");
          break;
        case 5:
          navigate("/leave-balance/import");
          break;
        default:
          break;
      }
    }
  };

  const completedSteps = steps.filter(step => step.completed).length;
  const totalSteps = steps.length;
  const completionPercentage = (completedSteps / totalSteps) * 100;

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Leave & Attendance Setup</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Leave & Attendance Setup</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-10 bg-white"
            style={{ minHeight: '100vh', overflowY: 'auto', display: 'flex', justifyContent: 'center' }}
          >
            <div className="w-100" style={{ maxWidth: "1200px" }}>
              {/* Progress Bar */}
              <div className="card mb-10">
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-center mb-5">
                    <span className="fw-bold text-gray-600">Setup Progress</span>
                    <span className="fw-bold text-primary">{completedSteps}/{totalSteps} Completed</span>
                  </div>
                  <div className="progress" style={{ height: "10px" }}>
                    <div 
                      className="progress-bar bg-primary" 
                      role="progressbar" 
                      style={{ width: `${completionPercentage}%` }}
                      aria-valuenow={completionPercentage} 
                      aria-valuemin="0" 
                      aria-valuemax="100"
                    ></div>
                  </div>
                </div>
              </div>

              {/* Main Content */}
              <div className="card">
                <div className="card-body p-10">
                  <div className="text-center mb-15">
                    <h1 className="fw-bold text-gray-900 mb-5">Setup Leave And Attendance Modules</h1>
                    <p className="fs-5 text-muted">
                      Configure the modules based on your organisational requirements.
                    </p>
                  </div>

                  {/* Steps Grid */}
                  <div className="row g-10">
                    {steps.map((step) => (
                      <div key={step.id} className="col-12 col-md-6 col-lg-4">
                        <div className="card h-100">
                          <div className="card-body d-flex flex-column">
                            {step.completed ? (
                              <div className="text-center mb-5">
                                <div className="symbol symbol-50px symbol-circle bg-success mb-4">
                                  <i className="bi bi-check-lg fs-2x text-white"></i>
                                </div>
                                <h4 className="fw-bold text-gray-900 mb-2 text-success">{step.title}</h4>
                                <p className="text-muted mb-5">{step.description}</p>
                                <button 
                                  className="btn btn-light-primary w-100"
                                  onClick={() => handleStepAction(step.id, "configure")}
                                >
                                  View Details
                                </button>
                              </div>
                            ) : (
                              <>
                                <h4 className="fw-bold text-gray-900 mb-4">{step.title}</h4>
                                <p className="text-muted flex-grow-1">{step.description}</p>
                                <div className="d-flex flex-column gap-3 mt-auto">
                                  {step.buttons.map((button, index) => (
                                    <button
                                      key={index}
                                      className={`btn btn-${button.variant}${button.variant === 'primary' ? '' : ' border'}`}
                                      onClick={() => handleStepAction(step.id, button.action)}
                                    >
                                      {button.text}
                                    </button>
                                  ))}
                                </div>
                              </>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>

                  {/* Disable Modules Section */}
                  <div className="card mt-15 border-dashed">
                    <div className="card-body">
                      <div className="d-flex flex-column flex-md-row align-items-center justify-content-between">
                        <div className="mb-5 mb-md-0">
                          <h5 className="fw-bold text-gray-900 mb-2">
                            Are all the above modules not required for your organisation?
                          </h5>
                          <p className="text-muted mb-0">
                            Disable Leave And Attendance modules if not needed
                          </p>
                        </div>
                        <div className="form-check form-switch form-check-custom form-check-solid">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            checked={disabledModules}
                            onChange={(e) => setDisabledModules(e.target.checked)}
                            style={{ width: "48px", height: "24px" }}
                          />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}