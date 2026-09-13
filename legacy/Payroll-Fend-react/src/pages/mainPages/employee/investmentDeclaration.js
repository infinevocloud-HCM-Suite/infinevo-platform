import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Formik, Form, Field, ErrorMessage } from "formik";
import * as Yup from "yup";

export default function InvestmentDeclaration() {
  const [showRentHouse, setShowRentHouse] = useState(false);
  const [showHomeLoan, setShowHomeLoan] = useState(false);
  const [showRentalIncome, setShowRentalIncome] = useState(false);
  const [rentedHouses, setRentedHouses] = useState([{ id: 1 }]);
  const [letOutProperties, setLetOutProperties] = useState([{ id: 1 }]);
  const [investment80C, setInvestment80C] = useState([{ id: 1 }]);
  const [exemption80D, setExemption80D] = useState([{ id: 1 }]);
  const [otherInvestments, setOtherInvestments] = useState([{ id: 1 }]);

  const [showTaxCalculation, setShowTaxCalculation] = useState(false);
  const [calculatedTax, setCalculatedTax] = useState("20,280.00");
  const [taxSavings, setTaxSavings] = useState("20,280.00");

  const initialValues = {
    // Rent House Details
    isRentedHouse: "no",
    rentedHouses: [
      {
        rentalPeriod: "",
        monthlyAmount: "",
        address: "",
        cityType: "",
        landlordPAN: ""
      }
    ],
    
    // Home Loan Details
    isHomeLoan: "no",
    principalAmount: "",
    interestAmount: "",
    lenderName: "",
    lenderPAN: "",
    
    // Rental Income Details
    isRentalIncome: "no",
    letOutProperties: [
      {
        annualRent: "",
        municipalTaxes: "",
        netAnnualValue: "",
        standardDeduction: "",
        hasHomeLoan: "no",
        netIncomeLoss: ""
      }
    ],
    
    // Other Income Sources
    otherSourcesIncome: "",
    savingsInterest: "",
    fdInterest: "",
    nscInterest: "",
    
    // 80C Investments
    epfBenefit: "",
    investments80C: [
      {
        investmentType: "",
        amount: ""
      }
    ],
    
    // 80D Exemptions
    maxLimit80D: "25000",
    exemptions80D: [
      {
        exemptionType: "",
        amount: ""
      }
    ],
    
    // Other Investments
    otherInvestmentsExemptions: [
      {
        investmentType: "",
        amount: ""
      }
    ],
    
    // Previous Employment
    incomeAfterExemptions: "",
    incomeTax: "",
    professionalTax: "",
    employeePF: "",
    leaveEncashment: ""
  };

  const validationSchema = Yup.object().shape({
    // Add validation rules as needed
  });

  const handleSubmit = (values) => {
    console.log(values);
    // Handle form submission
  };

  const addRentedHouse = () => {
    setRentedHouses([...rentedHouses, { id: rentedHouses.length + 1 }]);
  };

  const removeRentedHouse = (id) => {
    if (rentedHouses.length > 1) {
      setRentedHouses(rentedHouses.filter(house => house.id !== id));
    }
  };

  const addLetOutProperty = () => {
    setLetOutProperties([...letOutProperties, { id: letOutProperties.length + 1 }]);
  };

  const removeLetOutProperty = (id) => {
    if (letOutProperties.length > 1) {
      setLetOutProperties(letOutProperties.filter(property => property.id !== id));
    }
  };

  const add80CInvestment = () => {
    setInvestment80C([...investment80C, { id: investment80C.length + 1 }]);
  };

  const remove80CInvestment = (id) => {
    if (investment80C.length > 1) {
      setInvestment80C(investment80C.filter(inv => inv.id !== id));
    }
  };

  const add80DExemption = () => {
    setExemption80D([...exemption80D, { id: exemption80D.length + 1 }]);
  };

  const remove80DExemption = (id) => {
    if (exemption80D.length > 1) {
      setExemption80D(exemption80D.filter(ex => ex.id !== id));
    }
  };

  const addOtherInvestment = () => {
    setOtherInvestments([...otherInvestments, { id: otherInvestments.length + 1 }]);
  };

  const removeOtherInvestment = (id) => {
    if (otherInvestments.length > 1) {
      setOtherInvestments(otherInvestments.filter(inv => inv.id !== id));
    }
  };

  const calculateTax = () => {
    setShowTaxCalculation(true);
    // Tax calculation logic would go here
  };

  const investment80COptions = [
    "Life Insurance Premium",
    "Public Provident Fund",
    "Unit-linked insurance plan",
    "National Savings Certificates",
    "ELSS Tax Saving Mutual Fund",
    "Children Tuition Fees",
    "Sukanya Samriddhi Deposit Scheme",
    "5 Year fixed deposit in Scheduled Banks",
    "Term deposit in post office",
    "Senior Citizen Savings Scheme",
    "NABARD Rural Bonds",
    "Infrastructure Bonds",
    "Stamp duty and registration fee on buying house property",
    "Interest on National Savings Certificates",
    "80CCC",
    "Contribution to annuity plan of LIC",
    "80CCD(1)",
    "National Pension Scheme"
  ];

  const exemption80DOptions = [
    "Medi Claim Policy for self, spouse, children - 80D",
    "Medi Claim Policy for self, spouse, children for senior citizen - 80D",
    "Medi Claim Policy for parents - 80D",
    "Medi Claim Policy for parents for senior citizen - 80D",
    "Preventive health check up - 80D",
    "Preventive health check up for parents - 80D",
    "Medical Bills for self, spouse, children for senior citizen - 80D",
    "Medical Bills for parents for senior citizen - 80D"
  ];

  const otherInvestmentOptions = [
    "80CCD(1B)",
    "Additional exemption on voluntary NPS",
    "80DD",
    "Treatment of dependent with disability",
    "Treatment of dependent with severe disability",
    "80DDB",
    "Medical expenditure for self or dependent - 80DDB",
    "Medical expenditure for self or dependent for senior citizen - 80DDB",
    "Medical expenditure for self or dependent for very senior citizen - 80DDB",
    "80E",
    "Interest paid on Education loan",
    "80EE",
    "Additional interest on housing loan borrowed between 1 Apr 2016 and 31 Mar 2017",
    "80EEA",
    "Additional interest on housing loan borrowed between 1 Apr 2019 and 31 Mar 2022",
    "80EEB",
    "Interest on electric vehicle loan borrowed between 1 Apr 2019 and 31 Mar 2023",
    "80G",
    "Donation eligible for 100% exemption",
    "Donation eligible for 50% exemption",
    "80GG",
    "House rent paid",
    "80GGC",
    "Donation for political party",
    "80TTA",
    "Interest from Savings Account",
    "80U",
    "Permanent physical disability (self)",
    "Permanent severe physical disability (self)"
  ];

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Investment Declaration</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Investment Declaration</h5>
        <span className="text-muted">
          Financial Year: <span className="text-primary">2024-2025</span>
        </span>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "900px" }}>
              {/* Header Section */}
              <div className="card mb-6">
                <div className="card-body">
                  <div className="d-flex justify-content-between align-items-center mb-4">
                    <div>
                      <h3 className="fw-bold text-gray-800 mb-1">Aishwarya's IT Declaration</h3>
                      <div className="text-muted fs-7 mb-2">Employee Code: EMP001</div>
                    </div>
                    <div className="text-end">
                      <div className="badge bg-light-warning text-warning fs-7 px-3 py-2 mb-2">
                        Tax Regime: Old Tax Regime
                      </div>
                      <div className="text-muted fs-8">
                        (Note: POI will also be processed based on the Old Tax Regime)
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <Formik
                initialValues={initialValues}
                validationSchema={validationSchema}
                onSubmit={handleSubmit}
              >
                {({ isSubmitting, errors, touched, setFieldValue, values }) => (
                  <Form className="form w-100">
                    {/* Rent House Section */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-primary">
                              <i className="bi bi-house-door text-primary fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              Is the employee staying in a rented house?
                            </span>
                          </div>
                          <div className="ms-auto">
                            <div className="form-check form-switch">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                role="switch"
                                id="isRentedHouse"
                                checked={showRentHouse}
                                onChange={(e) => setShowRentHouse(e.target.checked)}
                              />
                              <label className="form-check-label" htmlFor="isRentedHouse">
                                {showRentHouse ? "Yes" : "No"}
                              </label>
                            </div>
                          </div>
                        </div>
                      </div>
                      
                      {showRentHouse && (
                        <div className="card-body">
                          <div className="d-flex justify-content-between align-items-center mb-4">
                            <h6 className="fw-bold text-gray-800 mb-0">House Rent Details</h6>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary"
                              onClick={addRentedHouse}
                            >
                              <i className="bi bi-plus"></i> Add rented house
                            </button>
                          </div>
                          
                          {rentedHouses.map((house, index) => (
                            <div key={house.id} className="border rounded p-4 mb-4 position-relative">
                              {rentedHouses.length > 1 && (
                                <button
                                  type="button"
                                  className="btn btn-icon btn-sm btn-light-danger position-absolute top-0 end-0 m-2"
                                  onClick={() => removeRentedHouse(house.id)}
                                >
                                  <i className="bi bi-dash"></i>
                                </button>
                              )}
                              
                              <div className="row g-3">
                                <div className="col-md-6">
                                  <label className="form-label">Rental Period</label>
                                  <Field
                                    type="text"
                                    name={`rentedHouses[${index}].rentalPeriod`}
                                    className="form-control"
                                    placeholder="e.g., Apr 2024 - Mar 2025"
                                  />
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Amount /mo.</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <Field
                                      type="number"
                                      name={`rentedHouses[${index}].monthlyAmount`}
                                      className="form-control"
                                      placeholder="0"
                                    />
                                  </div>
                                </div>
                                <div className="col-12">
                                  <label className="form-label">Address</label>
                                  <Field
                                    as="textarea"
                                    name={`rentedHouses[${index}].address`}
                                    className="form-control"
                                    rows="2"
                                    placeholder="Enter complete address"
                                  />
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">City Type</label>
                                  <Field
                                    as="select"
                                    name={`rentedHouses[${index}].cityType`}
                                    className="form-select"
                                  >
                                    <option value="">Select</option>
                                    <option value="metro">Metro City</option>
                                    <option value="non-metro">Non-Metro City</option>
                                  </Field>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">LANDLORD PAN</label>
                                  <Field
                                    type="text"
                                    name={`rentedHouses[${index}].landlordPAN`}
                                    className="form-control"
                                    placeholder="Enter PAN number"
                                  />
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Home Loan Section */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-success">
                              <i className="bi bi-bank text-success fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              Is the employee repaying home loan for a self occupied house property?
                            </span>
                          </div>
                          <div className="ms-auto">
                            <div className="form-check form-switch">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                role="switch"
                                id="isHomeLoan"
                                checked={showHomeLoan}
                                onChange={(e) => setShowHomeLoan(e.target.checked)}
                              />
                              <label className="form-check-label" htmlFor="isHomeLoan">
                                {showHomeLoan ? "Yes" : "No"}
                              </label>
                            </div>
                          </div>
                        </div>
                      </div>
                      
                      {showHomeLoan && (
                        <div className="card-body">
                          <div className="row g-3">
                            <div className="col-md-6">
                              <label className="form-label">Principal Paid on Home Loan</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="principalAmount"
                                  className="form-control"
                                  placeholder="0"
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Interest Paid on Home Loan</label>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name="interestAmount"
                                  className="form-control"
                                  placeholder="0"
                                />
                              </div>
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Name of the Lender</label>
                              <Field
                                type="text"
                                name="lenderName"
                                className="form-control"
                                placeholder="Enter lender name"
                              />
                            </div>
                            <div className="col-md-6">
                              <label className="form-label">Lender PAN</label>
                              <Field
                                type="text"
                                name="lenderPAN"
                                className="form-control"
                                placeholder="Enter PAN number"
                              />
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Rental Income Section */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-info">
                              <i className="bi bi-cash-coin text-info fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              Is the employee receiving rental income from let out property?
                            </span>
                          </div>
                          <div className="ms-auto">
                            <div className="form-check form-switch">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                role="switch"
                                id="isRentalIncome"
                                checked={showRentalIncome}
                                onChange={(e) => setShowRentalIncome(e.target.checked)}
                              />
                              <label className="form-check-label" htmlFor="isRentalIncome">
                                {showRentalIncome ? "Yes" : "No"}
                              </label>
                            </div>
                          </div>
                        </div>
                      </div>
                      
                      {showRentalIncome && (
                        <div className="card-body">
                          <div className="d-flex justify-content-between align-items-center mb-4">
                            <h6 className="fw-bold text-gray-800 mb-0">Letout Property</h6>
                            <button
                              type="button"
                              className="btn btn-sm btn-light-primary"
                              onClick={addLetOutProperty}
                            >
                              <i className="bi bi-plus"></i> Add a Let Out Property
                            </button>
                          </div>
                          
                          {letOutProperties.map((property, index) => (
                            <div key={property.id} className="border rounded p-4 mb-4 position-relative">
                              {letOutProperties.length > 1 && (
                                <button
                                  type="button"
                                  className="btn btn-icon btn-sm btn-light-danger position-absolute top-0 end-0 m-2"
                                  onClick={() => removeLetOutProperty(property.id)}
                                >
                                  <i className="bi bi-dash"></i>
                                </button>
                              )}
                              
                              <div className="row g-3">
                                <div className="col-md-6">
                                  <label className="form-label">Annual Rent Received</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <Field
                                      type="number"
                                      name={`letOutProperties[${index}].annualRent`}
                                      className="form-control"
                                      placeholder="0"
                                    />
                                  </div>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Municipal Taxes Paid</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <Field
                                      type="number"
                                      name={`letOutProperties[${index}].municipalTaxes`}
                                      className="form-control"
                                      placeholder="0"
                                    />
                                  </div>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Net Annual Value</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <Field
                                      type="number"
                                      name={`letOutProperties[${index}].netAnnualValue`}
                                      className="form-control"
                                      placeholder="0"
                                      readOnly
                                    />
                                  </div>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Standard Deduction (@ 30% of Net Annual Value)</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <Field
                                      type="number"
                                      name={`letOutProperties[${index}].standardDeduction`}
                                      className="form-control"
                                      placeholder="0"
                                      readOnly
                                    />
                                  </div>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Repaying Home Loan for This Property</label>
                                  <div className="form-check">
                                    <Field
                                      type="radio"
                                      className="form-check-input"
                                      name={`letOutProperties[${index}].hasHomeLoan`}
                                      value="yes"
                                      id={`hasHomeLoanYes${index}`}
                                    />
                                    <label className="form-check-label" htmlFor={`hasHomeLoanYes${index}`}>
                                      Yes
                                    </label>
                                  </div>
                                  <div className="form-check">
                                    <Field
                                      type="radio"
                                      className="form-check-input"
                                      name={`letOutProperties[${index}].hasHomeLoan`}
                                      value="no"
                                      id={`hasHomeLoanNo${index}`}
                                    />
                                    <label className="form-check-label" htmlFor={`hasHomeLoanNo${index}`}>
                                      No
                                    </label>
                                  </div>
                                </div>
                                <div className="col-md-6">
                                  <label className="form-label">Net Income/Loss from House Property</label>
                                  <div className="input-group">
                                    <span className="input-group-text">₹</span>
                                    <Field
                                      type="number"
                                      name={`letOutProperties[${index}].netIncomeLoss`}
                                      className="form-control"
                                      placeholder="0"
                                      readOnly
                                    />
                                  </div>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Other Sources of Income */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-warning">
                              <i className="bi bi-graph-up text-warning fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              Other Sources of Income
                            </span>
                          </div>
                        </div>
                      </div>
                      
                      <div className="card-body">
                        <div className="row g-3">
                          <div className="col-md-6">
                            <label className="form-label">Income from other sources</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="otherSourcesIncome"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Interest Earned from Savings Deposit</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="savingsInterest"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Interest Earned from Fixed Deposit</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="fdInterest"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Interest Earned from National Savings Certificates</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="nscInterest"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* 80C Investments */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-danger">
                              <i className="bi bi-piggy-bank text-danger fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              80C Investments
                            </span>
                          </div>
                        </div>
                      </div>
                      
                      <div className="card-body">
                        <div className="alert alert-info mb-4">
                          <strong>Note:</strong> This section contains the list of investments including LIC schemes, mutual funds and PPF. The maximum limit for this section is ₹1,50,000.00
                        </div>
                        
                        <div className="row mb-4">
                          <div className="col-md-6">
                            <label className="form-label">Employee Provident Fund (Benefit)</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="epfBenefit"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                        </div>
                        
                        <div className="d-flex justify-content-between align-items-center mb-4">
                          <h6 className="fw-bold text-gray-800 mb-0">Other 80C Investments</h6>
                          <button
                            type="button"
                            className="btn btn-sm btn-light-primary"
                            onClick={add80CInvestment}
                          >
                            <i className="bi bi-plus"></i> Add an investment
                          </button>
                        </div>
                        
                        {investment80C.map((investment, index) => (
                          <div key={investment.id} className="d-flex align-items-center gap-3 mb-3 position-relative">
                            <div className="flex-grow-1">
                              <Field
                                as="select"
                                name={`investments80C[${index}].investmentType`}
                                className="form-select"
                              >
                                <option value="">Select 80C Investment Type</option>
                                {investment80COptions.map((option, idx) => (
                                  <option key={idx} value={option}>{option}</option>
                                ))}
                              </Field>
                            </div>
                            <div style={{ width: '200px' }}>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name={`investments80C[${index}].amount`}
                                  className="form-control"
                                  placeholder="0"
                                />
                              </div>
                            </div>
                            {investment80C.length > 1 && (
                              <button
                                type="button"
                                className="btn btn-icon btn-sm btn-light-danger"
                                onClick={() => remove80CInvestment(investment.id)}
                              >
                                <i className="bi bi-dash"></i>
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* 80D Exemptions */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-primary">
                              <i className="bi bi-heart-pulse text-primary fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              80D Exemptions
                            </span>
                          </div>
                        </div>
                      </div>
                      
                      <div className="card-body">
                        <div className="alert alert-info mb-4">
                          <strong>Note:</strong> This section contains Mediclaim policies for yourself, your children, spouse and parents. The maximum limit for this section is ₹1,00,000.00
                        </div>
                        
                        <div className="row mb-4">
                          <div className="col-md-6">
                            <label className="form-label">Max Limit : ₹25,000.00</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="maxLimit80D"
                                className="form-control"
                                value="25000"
                                readOnly
                              />
                            </div>
                          </div>
                        </div>
                        
                        <div className="d-flex justify-content-between align-items-center mb-4">
                          <h6 className="fw-bold text-gray-800 mb-0">80D Exemptions</h6>
                          <button
                            type="button"
                            className="btn btn-sm btn-light-primary"
                            onClick={add80DExemption}
                          >
                            <i className="bi bi-plus"></i> Add an investment
                          </button>
                        </div>
                        
                        {exemption80D.map((exemption, index) => (
                          <div key={exemption.id} className="d-flex align-items-center gap-3 mb-3 position-relative">
                            <div className="flex-grow-1">
                              <Field
                                as="select"
                                name={`exemptions80D[${index}].exemptionType`}
                                className="form-select"
                              >
                                <option value="">Select 80D Exemption Type</option>
                                {exemption80DOptions.map((option, idx) => (
                                  <option key={idx} value={option}>{option}</option>
                                ))}
                              </Field>
                            </div>
                            <div style={{ width: '200px' }}>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name={`exemptions80D[${index}].amount`}
                                  className="form-control"
                                  placeholder="0"
                                />
                              </div>
                            </div>
                            {exemption80D.length > 1 && (
                              <button
                                type="button"
                                className="btn btn-icon btn-sm btn-light-danger"
                                onClick={() => remove80DExemption(exemption.id)}
                              >
                                <i className="bi bi-dash"></i>
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Other Investments & Exemptions */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-success">
                              <i className="bi bi-wallet2 text-success fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              Other Investments & Exemptions
                            </span>
                          </div>
                        </div>
                      </div>
                      
                      <div className="card-body">
                        <div className="alert alert-info mb-4">
                          <strong>Note:</strong> Declare other investments & exemptions such as Voluntary NPS, Interest Paid on Education Loan and Medical Expenditures under this section
                        </div>
                        
                        <div className="d-flex justify-content-between align-items-center mb-4">
                          <h6 className="fw-bold text-gray-800 mb-0">Other Investments</h6>
                          <button
                            type="button"
                            className="btn btn-sm btn-light-primary"
                            onClick={addOtherInvestment}
                          >
                            <i className="bi bi-plus"></i> Add an investment
                          </button>
                        </div>
                        
                        {otherInvestments.map((investment, index) => (
                          <div key={investment.id} className="d-flex align-items-center gap-3 mb-3 position-relative">
                            <div className="flex-grow-1">
                              <Field
                                as="select"
                                name={`otherInvestmentsExemptions[${index}].investmentType`}
                                className="form-select"
                              >
                                <option value="">Select Investment Type</option>
                                {otherInvestmentOptions.map((option, idx) => (
                                  <option key={idx} value={option}>{option}</option>
                                ))}
                              </Field>
                            </div>
                            <div style={{ width: '200px' }}>
                              <div className="input-group">
                                <span className="input-group-text">₹</span>
                                <Field
                                  type="number"
                                  name={`otherInvestmentsExemptions[${index}].amount`}
                                  className="form-control"
                                  placeholder="0"
                                />
                              </div>
                            </div>
                            {otherInvestments.length > 1 && (
                              <button
                                type="button"
                                className="btn btn-icon btn-sm btn-light-danger"
                                onClick={() => removeOtherInvestment(investment.id)}
                              >
                                <i className="bi bi-dash"></i>
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>

                    {/* Previous Employment */}
                    <div className="card mb-6">
                      <div className="card-header">
                        <div className="card-title d-flex align-items-center">
                          <div className="symbol symbol-40px me-3">
                            <span className="symbol-label bg-light-info">
                              <i className="bi bi-briefcase text-info fs-3"></i>
                            </span>
                          </div>
                          <div className="d-flex flex-column">
                            <span className="fw-bold fs-6 text-gray-800">
                              Previous Employment
                            </span>
                          </div>
                        </div>
                      </div>
                      
                      <div className="card-body">
                        <div className="row g-3">
                          <div className="col-md-6">
                            <label className="form-label">Income After Exemptions</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="incomeAfterExemptions"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Income Tax</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="incomeTax"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Professional Tax</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="professionalTax"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Employee Provident Fund</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="employeePF"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                          <div className="col-md-6">
                            <label className="form-label">Leave Encashment Exemptions</label>
                            <div className="input-group">
                              <span className="input-group-text">₹</span>
                              <Field
                                type="number"
                                name="leaveEncashment"
                                className="form-control"
                                placeholder="0"
                              />
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Tax Calculation Section */}
                    <div className="card mb-6">
                      <div className="card-body">
                        <div className="text-center mb-4">
                          <button
                            type="button"
                            className="btn btn-lg btn-primary px-6"
                            onClick={calculateTax}
                          >
                            Click Compute Tax, to calculate the Total Tax Liablility based on the above declaration
                          </button>
                        </div>
                        
                        {showTaxCalculation && (
                          <div className="alert alert-success">
                            <div className="d-flex align-items-center">
                              <div className="symbol symbol-40px me-3">
                                <i className="bi bi-calculator fs-2 text-success"></i>
                              </div>
                              <div className="flex-grow-1">
                                <h5 className="alert-heading mb-1">
                                  Total Tax Liability based on Old Tax Regime : ₹{calculatedTax}
                                </h5>
                                <p className="mb-0">
                                  <strong>Pro Tip :</strong> If you are opting for New Tax Regime you can save ₹{taxSavings}
                                </p>
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Form Actions */}
                    <div className="d-flex justify-content-end gap-3 pt-5 border-top">
                      <button
                        type="button"
                        className="btn btn-lg btn-light"
                        disabled={isSubmitting}
                      >
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn btn-lg btn-primary"
                        disabled={isSubmitting}
                      >
                        {isSubmitting ? (
                          <>
                            <span className="spinner-border spinner-border-sm me-2"></span>
                            Submitting...
                          </>
                        ) : (
                          'Submit'
                        )}
                      </button>
                    </div>
                  </Form>
                )}
              </Formik>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}