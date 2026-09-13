import React, { useState, useEffect } from 'react';
import { ProgressBar, Card, Button, Container, Badge, Accordion, ListGroup } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { GlobalConst } from '../../../shared/appConfig/globalConst';
import { errorMsg } from '../../../shared/helpers/msgHelper';

const OnBoardingDashboard = () => {
  const navigate = useNavigate();
  const [activeStep, setActiveStep] = useState(1);
  const [completedSteps, setCompletedSteps] = useState([]);
  const [expandedStep, setExpandedStep] = useState(null);
  const [checkedSubSteps, setCheckedSubSteps] = useState({});
  const [orgSetupSteps, setOrgSetupSteps] = useState(null);
  const [loading, setLoading] = useState(true);
  const [organizationId, setOrganizationId] = useState(localStorage.getItem("organizationId"));

  const steps = [
    {
      id: 1,
      title: "Add Organisation Details",
      description: "Enter your company name, address, and basic information",
      completed: true, // Always true by default
      component: "/organisation-profile",
      icon: "🏢"
    },
    {
      id: 2,
      title: "Provide your Tax Details",
      description: "Add tax identification numbers and compliance information",
      completed: false,
      component: "/tax-details",
      icon: "📝",
      apiField: "orgTaxSetup"
    },
    {
      id: 3,
      title: "Configure your Pay Schedule",
      description: "Set up payment frequency and payroll processing dates",
      completed: false,
      component: "/pay-schedules",
      icon: "📅",
      apiField: "payScheduleSetup"
    },
    {
      id: 4,
      title: "Set up Statutory Components",
      description: "Configure mandatory deductions and contributions",
      completed: false,
      component: "/statutory-components",
      icon: "⚖️",
      apiField: "statutoryComponentsSetup",
      subSteps: [
        {
          id: '4-1',
          label: "Employees' Provident Fund",
          component: "/statutory-components/epf",
          completed: false,
          apiField: "epfsetup"
        },
        {
          id: '4-2',
          label: "Employees' State Insurance",
          component: "/statutory-components/esi",
          completed: false,
          apiField: "esisetup"
        },
        {
          id: '4-3',
          label: "Professional Tax (Configure based on Work Location)",
          component: "/statutory-components/professional-tax",
          completed: false,
          apiField: "ptaxsetup"
        }
      ]
    },
    {
      id: 5,
      title: "Set up Salary Components",
      description: "Define earnings, deductions, and benefits structure",
      completed: false,
      component: "/salary-components",
      icon: "💰",
      apiField: "salaryComponentsSetup"
    },
    {
      id: 6,
      title: "Add Employees",
      description: "Import or manually add your employees to the system",
      completed: false,
      component: "/employees/add",
      icon: "👥",
      apiField: "employeeSetup"
    },
    // {
    //   id: 7,
    //   title: "Configure Prior Payroll",
    //   description: "Set up previous payroll data for accurate calculations",
    //   completed: false,
    //   component: "/prior-payroll",
    //   icon: "📊",
    //   apiField: "priorPayrollSetup"
    // }
  ];

  // useEffect(() => {
  //   const fetchOrgSetupSteps = async () => {
  //     try {
  //       const token = localStorage.getItem("__t");
  //       const organizationId = localStorage.getItem("organizationId");

  //       if (!organizationId) {
  //         errorMsg("Error", "Organization not selected", false);
  //         return;
  //       }

  //       const response = await axios.get(
  //         `${GlobalConst.API_URL}/api/org-setup-steps`,
  //         {
  //           headers: {
  //             Authorization: `Bearer ${token}`,
  //             organizationId: organizationId,
  //           },
  //         }
  //       );

  //       if (response.data?.status === 200 && response.data?.data) {
  //         setOrgSetupSteps(response.data.data);
  //         updateStepCompletionStatus(response.data.data);
  //       }
  //     } catch (error) {
  //       console.error("Fetch Error: ", error);
  //     } finally {
  //       setLoading(false);
  //     }
  //   };

  //   fetchOrgSetupSteps();
  // }, [localStorage.getItem("organizationId")]); // 👈 Add orgId dependency


  // Fetch organizationId from localStorage once it becomes available
  useEffect(() => {
    const checkOrgId = setInterval(() => {
      const storedOrgId = localStorage.getItem('organizationId');
      if (storedOrgId) {
        setOrganizationId(storedOrgId);
        clearInterval(checkOrgId);
      }
    }, 200); // check every 200ms until available

    return () => clearInterval(checkOrgId);
  }, []);
  

  // Fetch organization setup steps
  useEffect(() => {
    const fetchOrgSetupSteps = async () => {
      try {
        if (!organizationId) {
          errorMsg("Error", "Organization not selected", false);
          return;
        }

        const token = localStorage.getItem("__t");
        const response = await axios.get(
          `${GlobalConst.API_URL}/api/org-setup-steps`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
              organizationId: organizationId,
            },
          }
        );

        if (response.data?.status === 200 && response.data?.data) {
          setOrgSetupSteps(response.data.data);
          updateStepCompletionStatus(response.data.data);
        } else {
          errorMsg("Load Failed", response.data?.message || "Unable to fetch setup steps.", false);
        }
      } catch (error) {
        console.error("Fetch Error: ", error);
        errorMsg("Error", error.response?.data?.message || error.message || "Something went wrong", false);
      } finally {
        setLoading(false);
      }
    };

    if (organizationId) {
      fetchOrgSetupSteps();
    }
  }, [organizationId]);

  // Update step completion status based on API response
  const updateStepCompletionStatus = (setupData) => {
    const completedStepIds = [];

    steps.forEach(step => {
      if (step.id === 1) {
        // Step 1 is always completed
        completedStepIds.push(1);
      } else if (step.apiField) {
        if (step.id === 4) {
          // Step 4 (Statutory Components) requires all sub-steps to be completed
          const statutoryCompleted = step.subSteps.every(subStep =>
            setupData[subStep.apiField] === true
          );
          if (statutoryCompleted) {
            completedStepIds.push(step.id);
          }
        } else {
          // Other steps check their respective API field
          if (setupData[step.apiField] === true) {
            completedStepIds.push(step.id);
          }
        }
      }
    });

    setCompletedSteps(completedStepIds);

    // Update sub-step completion status
    const subStepStatus = {};
    steps.forEach(step => {
      if (step.subSteps) {
        step.subSteps.forEach(subStep => {
          if (setupData[subStep.apiField] === true) {
            subStepStatus[subStep.id] = true;
          }
        });
      }
    });
    setCheckedSubSteps(subStepStatus);
  };

  const handleStepClick = (stepId, component) => {
    setActiveStep(stepId);
    navigate(component);
  };

  const handleSubStepClick = (stepId, subStepId, component) => {
    navigate(component);
  };

  const toggleStepExpand = (stepId) => {
    setExpandedStep(expandedStep === stepId ? null : stepId);
  };

  const progressPercentage = (completedSteps.length / steps.length) * 100;

  // Check if all steps are completed
  const allStepsCompleted = completedSteps.length === steps.length;

  // Navigate to dashboard when all steps are completed
  useEffect(() => {
    if (allStepsCompleted && orgSetupSteps) {
      navigate("/dashboard");
    }
  }, [allStepsCompleted, orgSetupSteps, navigate]);

  if (loading) {
    return (
      <Container fluid className="p-0 bg-white" style={{ minHeight: '100vh' }}>
        <Container className="py-5">
          <div className="text-center">
            <div className="spinner-border text-primary" role="status">
              <span className="visually-hidden">Loading...</span>
            </div>
            <p className="mt-3">Loading setup progress...</p>
          </div>
        </Container>
      </Container>
    );
  }

  return (
    <Container fluid className="p-0 bg-white" style={{ minHeight: '100vh' }}>
      {/* Main Content */}
      <Container className="py-3 py-md-5">
        <div className="mx-auto" style={{ maxWidth: '900px' }}>
          <Card className="border-0 shadow-sm shadow-md-lg">
            <Card.Body className="p-3 p-md-4 p-lg-5">
              <div className="text-center mb-4 mb-md-5">
                <h2 className="fw-bold mb-2 mb-md-3">Welcome to Infinevocloud Payroll</h2>
                <p className="text-muted fs-5 d-none d-md-block">
                  Complete these simple steps to set up your payroll system and enjoy a seamless experience
                </p>
                <p className="text-muted d-md-none">
                  Complete setup steps for seamless payroll experience
                </p>
              </div>

              {/* Progress Section */}
              <div className="mb-4 mb-md-5 p-3 p-md-4 bg-light rounded-3">
                <div className="d-flex justify-content-between mb-2 mb-md-3">
                  <span className="fw-bold fs-6 fs-md-5">Your progress: {completedSteps.length} of {steps.length} steps</span>
                  <span className="fw-bold fs-6 fs-md-5">{Math.round(progressPercentage)}%</span>
                </div>
                <ProgressBar
                  now={progressPercentage}
                  className="mb-2"
                  style={{ height: '10px', borderRadius: '5px' }}
                  variant="primary"
                  animated
                />
                <div className="text-end">
                  <small className="text-muted">
                    {completedSteps.length === steps.length ?
                      "Setup complete! 🎉" :
                      `${steps.length - completedSteps.length} steps remaining`}
                  </small>
                </div>
              </div>

              {/* Steps List */}
              <div className="onboarding-steps">
                <Accordion activeKey={expandedStep} flush>
                  {steps.map((step) => {
                    const isCompleted = completedSteps.includes(step.id);
                    const isStep4 = step.id === 4;

                    return (
                      <Accordion.Item
                        key={step.id}
                        eventKey={step.id}
                        className={`mb-2 mb-md-3 ${activeStep === step.id ? 'border-primary border-2' : 'border-white'}`}
                      >
                        <Card
                          className={`border-0 ${isCompleted ? 'bg-success bg-opacity-10' : ''}`}
                          onClick={() => !step.subSteps && !isCompleted && handleStepClick(step.id, step.component)}
                          style={{ cursor: !step.subSteps && !isCompleted ? 'pointer' : 'default' }}
                        >
                          <Card.Body className="p-0">
                            <div className="d-flex flex-column flex-md-row align-items-center p-3 p-md-4">
                              <div
                                className={`rounded-circle d-flex align-items-center justify-content-center me-0 me-md-4 mb-2 mb-md-0 flex-shrink-0
                                  ${isCompleted ? 'bg-success text-white' : 'bg-white text-primary border border-2 border-primary'}`}
                                style={{
                                  width: '40px',
                                  height: '40px',
                                  fontWeight: 'bold',
                                  fontSize: '1rem'
                                }}
                              >
                                {step.icon || step.id}
                              </div>
                              <div className="flex-grow-1 text-center text-md-start mb-2 mb-md-0">
                                <h4 className="mb-1 d-flex flex-column flex-md-row align-items-center">
                                  <span className="me-0 me-md-2">{step.title}</span>
                                  {isCompleted && (
                                    <Badge bg="success" className="mt-1 mt-md-0 ms-md-2 fs-6">Completed</Badge>
                                  )}
                                </h4>
                                <p className="text-muted mb-0 d-none d-md-block">{step.description}</p>
                                <p className="text-muted mb-0 d-md-none" style={{ fontSize: '0.85rem' }}>{step.description}</p>
                              </div>

                              {step.subSteps ? (
                                <Button
                                  variant="outline-primary"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    toggleStepExpand(step.id);
                                  }}
                                  className="text-decoration-none px-3 px-md-4 py-1 py-md-2 mt-2 mt-md-0"
                                  size="sm"
                                >
                                  {expandedStep === step.id ? 'Hide ▲' : 'Expand ▼'}
                                </Button>
                              ) : (
                                // <Button
                                //   variant={isCompleted ? 'outline-success' : 'primary'}
                                //   size="sm"
                                //   onClick={(e) => {
                                //     e.stopPropagation();
                                //     if (!isCompleted) {
                                //       handleStepClick(step.id, step.component);
                                //     }
                                //   }}
                                //   className="px-3 px-md-4 py-1 py-md-2 mt-2 mt-md-0"
                                //   disabled={isCompleted}
                                // >
                                //   {isCompleted ? (
                                //     <>
                                //       <i className="bi bi-check-circle me-1 me-md-2"></i>
                                //       <span className="d-none d-md-inline">Completed</span>
                                //     </>
                                //   ) : (
                                //     <>
                                //       <i className="bi bi-arrow-right-circle me-1 me-md-2"></i>
                                //       <span className="d-none d-md-inline">Start</span>
                                //       <span className="d-md-none">Go</span>
                                //     </>
                                //   )}
                                // </Button>





                                <Button
                                  variant={isCompleted ? 'outline-success' : 'primary'}
                                  size="sm"
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    if (!isCompleted && step.id !== 1) {
                                      handleStepClick(step.id, step.component);
                                    }
                                  }}
                                  className="px-3 px-md-4 py-1 py-md-2 mt-2 mt-md-0"
                                  disabled={isCompleted || step.id === 1}  // Step 1 always disabled
                                >
                                  {(isCompleted || step.id === 1) ? (
                                    <>
                                      <i className="bi bi-check-circle me-1 me-md-2"></i>
                                      <span className="d-none d-md-inline">Completed</span>
                                    </>
                                  ) : (
                                    <>
                                      <i className="bi bi-arrow-right-circle me-1 me-md-2"></i>
                                      <span className="d-none d-md-inline">Start</span>
                                      <span className="d-md-none">Go</span>
                                    </>
                                  )}
                                </Button>

                              )}
                            </div>

                            {step.subSteps && (
                              <Accordion.Collapse eventKey={step.id}>
                                <div className="px-2 px-md-4 pb-3 pb-md-4">
                                  <div className="ps-md-5 pe-md-3">
                                    <Card className="border-0 shadow-sm">
                                      <Card.Body className="p-2 p-md-3 p-lg-4">
                                        <h5 className="mb-3 mb-md-4">Configure these statutory components:</h5>
                                        <ListGroup variant="flush">
                                          {step.subSteps.map((subStep) => {
                                            const isSubStepCompleted = checkedSubSteps[subStep.id];
                                            return (
                                              <ListGroup.Item
                                                key={subStep.id}
                                                action
                                                onClick={() => !isSubStepCompleted && handleSubStepClick(step.id, subStep.id, subStep.component)}
                                                className="py-2 py-md-3 d-flex flex-column flex-md-row justify-content-between align-items-start align-items-md-center"
                                                style={{ cursor: !isSubStepCompleted ? 'pointer' : 'default' }}
                                              >
                                                <div className="d-flex align-items-center mb-1 mb-md-0">
                                                  <div
                                                    className={`rounded-circle d-flex align-items-center justify-content-center me-2 me-md-3
                                                      ${isSubStepCompleted ? 'bg-success text-white' : 'bg-light'}`}
                                                    style={{
                                                      width: '28px',
                                                      height: '28px',
                                                      fontWeight: 'bold',
                                                      fontSize: '0.8rem'
                                                    }}
                                                  >
                                                    {isSubStepCompleted ? '✓' : (subStep.id.split('-')[1])}
                                                  </div>
                                                  <span className="fs-6 fs-md-5">{subStep.label}</span>
                                                </div>
                                                <Button
                                                  variant={isSubStepCompleted ? 'outline-success' : 'primary'}
                                                  size="sm"
                                                  onClick={(e) => {
                                                    e.stopPropagation();
                                                    if (!isSubStepCompleted) {
                                                      handleSubStepClick(step.id, subStep.id, subStep.component);
                                                    }
                                                  }}
                                                  className="ms-auto ms-md-0"
                                                  disabled={isSubStepCompleted}
                                                >
                                                  {isSubStepCompleted ? 'Done' : 'Configure'}
                                                </Button>
                                              </ListGroup.Item>
                                            );
                                          })}
                                        </ListGroup>
                                        <div className="d-flex flex-column flex-md-row justify-content-between mt-3 mt-md-4 gap-2">
                                          <Button
                                            variant="outline-secondary"
                                            size="sm"
                                            onClick={() => {
                                              // Mark all substeps as completed
                                              const newChecked = {};
                                              step.subSteps.forEach(sub => {
                                                newChecked[sub.id] = true;
                                              });
                                              setCheckedSubSteps(prev => ({ ...prev, ...newChecked }));
                                              if (!completedSteps.includes(step.id)) {
                                                setCompletedSteps([...completedSteps, step.id]);
                                              }
                                            }}
                                          >
                                            Mark All as Completed
                                          </Button>
                                          <Button
                                            variant="primary"
                                            size="sm"
                                            onClick={() => handleStepClick(step.id, step.component)}
                                          >
                                            <i className="bi bi-gear me-1 me-md-2"></i>
                                            Configure All
                                          </Button>
                                        </div>
                                      </Card.Body>
                                    </Card>
                                  </div>
                                </div>
                              </Accordion.Collapse>
                            )}
                          </Card.Body>
                        </Card>
                      </Accordion.Item>
                    );
                  })}
                </Accordion>
              </div>

              {/* Quick Actions */}
              {allStepsCompleted && (
                <div className="mt-4 mt-md-5 p-3 p-md-4 bg-light rounded-3 text-center">
                  <h4 className="mb-3 mb-md-4">🎉 Setup Complete! What's next?</h4>
                  <div className="d-flex flex-column flex-md-row justify-content-center gap-2">
                    <Button variant="success" size="sm" className="px-3 px-md-4" onClick={() => navigate('/employee-dashboard')}>
                      <i className="bi bi-people-fill me-1 me-md-2"></i>
                      Employee Dashboard
                    </Button>
                    <Button variant="outline-primary" size="sm" className="px-3 px-md-4" onClick={() => navigate('/run-payroll')}>
                      <i className="bi bi-graph-up me-1 me-md-2"></i>
                      Run Payroll
                    </Button>
                  </div>
                </div>
              )}
            </Card.Body>
          </Card>
        </div>
      </Container>
    </Container>
  );
};

export default OnBoardingDashboard;