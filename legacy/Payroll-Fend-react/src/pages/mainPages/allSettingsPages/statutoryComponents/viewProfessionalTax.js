// viewProfessionalTax.js (with "View Tax Slabs (Revise)" integration)
import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate, useLocation } from "react-router-dom";
import { BiChevronRight, BiCheck, BiX } from "react-icons/bi";
import { Modal, Table } from "antd";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import Loader from "../../../../shared/components/loaders/fullPageLoader";

export default function ProfessionalTax() {
  const [activeTab, setActiveTab] = useState("Professional Tax");
  const tabs = [
    { key: "/statutory-components", label: "EPF" },
    { key: "/statutory-components/esi", label: "ESI" },
    {
      key: "/statutory-components/professional-tax",
      label: "Professional Tax",
    },
    {
      key: "/statutory-components/labour-welfare-fund",
      label: "Labour Welfare Fund",
    },
    { key: "/statutory-components/statutory-bonus", label: "Statutory Bonus" },
  ];

  const navigate = useNavigate();
  const location = useLocation();

  const [isEditingPtNumber, setIsEditingPtNumber] = useState(false);
  const [editingTaxId, setEditingTaxId] = useState(null);
  const [ptNumberValue, setPtNumberValue] = useState("");

  const [isTaxSlabsModalVisible, setIsTaxSlabsModalVisible] = useState(false);
  const [loading, setLoading] = useState(false);

  const [professionalTaxList, setProfessionalTaxList] = useState([]);
  const [taxSlabsData, setTaxSlabsData] = useState([]);
  const [selectedTax, setSelectedTax] = useState(null);

  // Get organization ID from localStorage
  const organizationId = localStorage.getItem("organizationId");

  useEffect(() => {
    fetchProfessionalTaxData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchProfessionalTaxData = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/professional-tax`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      const list = response?.data?.data || [];

      if (list.length > 0) {
        setProfessionalTaxList(list);
      } else {
        setProfessionalTaxList([]);
        errorMsg(
          "Info",
          "No professional tax configuration found. Please configure it first.",
          false
        );
      }
    } catch (error) {
      console.error("Error fetching professional tax data:", error);
      errorMsg(
        "Error",
        error?.response?.data?.message ||
        "Failed to load professional tax data",
        true
      );
    } finally {
      setLoading(false);
    }
  };

  const handleEditPtNumber = (tax) => {
    setIsEditingPtNumber(true);
    setEditingTaxId(tax.taxId);
    setPtNumberValue(tax.registrationNumber || "");
  };

  const handleSavePtNumber = async () => {
    if (!editingTaxId) return;

    try {
      setLoading(true);

      const tax = professionalTaxList.find((t) => t.taxId === editingTaxId);
      if (!tax) return;

      const updatedData = {
        ...tax,
        registrationNumber: ptNumberValue,
      };

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/professional-tax/${tax.taxId}`,
        updatedData,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data && response.data.status === 200) {
        successMsg("Success", "PT Number updated successfully", false);
        setIsEditingPtNumber(false);
        setEditingTaxId(null);
        await fetchProfessionalTaxData();
      }
    } catch (error) {
      console.error("Error updating PT number:", error);
      errorMsg(
        "Error",
        error?.response?.data?.message || "Failed to update PT number",
        true
      );
    } finally {
      setLoading(false);
    }
  };

  const handleCancelEdit = () => {
    setIsEditingPtNumber(false);
    setEditingTaxId(null);
    setPtNumberValue("");
  };

  const handleViewTaxSlabs = (tax) => {
    if (!tax) return;

    setSelectedTax(tax);
    console.log("Selected Tax for slabs:", tax);

    // Always take slabs ONLY from tax.slabDetails (ignore slabRateConfigurations)
    const baseSlabs = Array.isArray(tax.slabDetails) ? tax.slabDetails : [];

    const slabs = baseSlabs.map((slab, index) => ({
      key: index.toString(),
      monthly_gross_salary: `${slab.startAmount} - ${slab.endAmount}`,
      monthly_tax_amount: slab.payAmount,
      isFemaleExempted:
        slab.femaleExempted ?? slab.isFemaleExempted ?? false,
    }));

    console.log("Final Slabs Data:", slabs);

    setTaxSlabsData(slabs);
    setIsTaxSlabsModalVisible(true);
  };


  const handleTaxSlabsModalClose = () => {
    setIsTaxSlabsModalVisible(false);
    setSelectedTax(null);
    setTaxSlabsData([]);
  };

  // NEW: open ptDetails page for this taxId
  const handleReviseTaxSlabs = (tax) => {
    if (!tax) return;

    navigate(
      `/statutory-components/professional-tax/pt-details/${encodeURIComponent(
        tax.taxId
      )}`,
      {
        state: {
          taxId: tax.taxId,
          stateName: tax.state,
          locationName: tax.locationName,
        },
      }
    );
  };

  // Utility to show deduction cycle nicely
  const getDeductionCycleLabel = (tax) => {
    const freq =
      tax?.slabRateConfigurations?.[0]?.deductionFrequency ||
      tax?.deductionFrequency ||
      tax?.taxConfigurationFrequency ||
      "monthly";

    const lower = String(freq).toLowerCase();
    return lower.charAt(0).toUpperCase() + lower.slice(1);
  };

  // Table columns for tax slabs
  const taxSlabsColumns = [
    {
      title: "MONTHLY GROSS SALARY (₹)",
      dataIndex: "monthly_gross_salary",
      key: "monthly_gross_salary",
      className: "fw-semibold",
    },
    {
      title: "MONTHLY TAX AMOUNT (₹)",
      dataIndex: "monthly_tax_amount",
      key: "monthly_tax_amount",
      className: "fw-semibold",
    },
  ];

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Professional Tax</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex flex-column border-bottom">
        {/* Header */}
        <div className="d-flex justify-content-between align-items-center mb-3">
          <h5 className="mb-0 fw-semibold">Statutory Components</h5>
          <div className="d-flex align-items-center gap-2"></div>
        </div>

        {/* Tabs */}
        <ul className="nav nav-tabs border-0">
          {tabs.map((tab) => (
            <li className="nav-item" key={tab.key}>
              <button
                className={`nav-link fw-semibold px-3 py-2 ${location.pathname === tab.key
                    ? "active text-primary border-primary border-bottom"
                    : "text-dark"
                  }`}
                onClick={() => {
                  setActiveTab(tab.label);
                  navigate(tab.key);
                }}
              >
                {tab.label}
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div
            className="container-fluid p-10 bg-white"
            style={{
              minHeight: "100vh",
              overflowY: "auto",
              display: "flex",
              justifyContent: "center",
            }}
          >
            <div className="w-100" style={{ maxWidth: "1200px" }}>
              <div className="py-5">
                <div className="d-flex justify-content-between align-items-center mb-4">
                  <h5 className="mb-0">Professional Tax</h5>
                </div>

                {/* Information Text */}
                <div className="fv-row mb-10">
                  <div className="alert alert-info bg-light-primary border-0">
                    <div className="d-flex">
                      <i className="bi bi-info-circle me-2 text-primary"></i>
                      <div className="text-gray-700">
                        This tax is levied on an employee&apos;s income by the
                        State Government. Tax slabs differ in each state.
                      </div>
                    </div>
                  </div>
                </div>

                {/* Cards for each work location / state */}
                <div className="row g-4">
                  {professionalTaxList && professionalTaxList.length > 0 ? (
                    professionalTaxList.map((tax) => (
                      <div
                        className="col-12 col-md-6 col-lg-4"
                        key={tax.taxId}
                      >
                        <div className="card border-0 shadow-sm h-100">
                          <div className="card-body d-flex flex-column">
                            {/* Location / Work location name */}
                            <h6 className="fw-bold text-gray-800 mb-4">
                              {tax.locationName || tax.state || "Work Location"}
                            </h6>

                            {/* PT Number */}
                            <div className="mb-3">
                              <div className="text-muted small mb-1">
                                PT Number
                              </div>

                              {isEditingPtNumber &&
                                editingTaxId === tax.taxId ? (
                                <div className="d-flex align-items-center gap-2">
                                  <input
                                    type="text"
                                    className="form-control form-control-solid"
                                    value={ptNumberValue}
                                    onChange={(e) =>
                                      setPtNumberValue(e.target.value)
                                    }
                                    style={{ maxWidth: "200px" }}
                                    autoFocus
                                  />
                                  <button
                                    className="btn btn-icon btn-sm btn-success"
                                    type="button"
                                    onClick={handleSavePtNumber}
                                  >
                                    <BiCheck size="1.2rem" />
                                  </button>
                                  <button
                                    className="btn btn-icon btn-sm btn-light"
                                    type="button"
                                    onClick={handleCancelEdit}
                                  >
                                    <BiX size="1.2rem" />
                                  </button>
                                </div>
                              ) : (
                                <div className="d-flex align-items-center gap-2">
                                  <span className="fw-semibold">
                                    {tax.registrationNumber || "-"}
                                  </span>
                                  <button
                                    type="button"
                                    className="btn btn-link p-0 text-primary"
                                    onClick={() => handleEditPtNumber(tax)}
                                  >
                                    Update PT Number
                                  </button>
                                </div>
                              )}
                            </div>

                            {/* State */}
                            <div className="mb-3">
                              <div className="text-muted small mb-1">State</div>
                              <div className="fw-semibold">
                                {tax.state || "-"}
                              </div>
                            </div>

                            {/* Deduction Cycle */}
                            <div className="mb-3">
                              <div className="text-muted small mb-1">
                                Deduction Cycle
                              </div>
                              <div className="fw-semibold">
                                {getDeductionCycleLabel(tax)}
                              </div>
                            </div>

                            {/* PT Slabs */}
                            <div className="mt-auto">
                              <div className="text-muted small mb-1">
                                PT Slabs
                              </div>
                              <div className="d-flex align-items-center gap-2">
                                <button
                                  type="button"
                                  className="btn btn-link p-0 text-primary d-flex align-items-center"
                                  onClick={() => handleViewTaxSlabs(tax)}
                                  disabled={
                                    !(
                                      (tax.slabRateConfigurations &&
                                        tax.slabRateConfigurations.length > 0 &&
                                        tax.slabRateConfigurations[0]
                                          .slabDetails &&
                                        tax.slabRateConfigurations[0]
                                          .slabDetails.length > 0) ||
                                      (tax.slabDetails &&
                                        tax.slabDetails.length > 0)
                                    )
                                  }
                                >
                                  View Tax Slabs
                                  <BiChevronRight
                                    className="ms-1"
                                    size="1.2rem"
                                  />
                                </button>

                                {/* NEW (Revise) link */}
                                <button
                                  type="button"
                                  className="btn btn-link p-0 text-primary"
                                  onClick={() => handleReviseTaxSlabs(tax)}
                                >
                                  (Revise)
                                </button>
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="col-12">
                      <div className="text-muted">
                        No professional tax configuration found.
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Tax Slabs Modal */}
      <Modal
        title={
          <div className="d-flex align-items-center">
            <span className="fw-bold fs-5">
              Tax Slabs for{" "}
              {selectedTax?.locationName ||
                selectedTax?.state ||
                "Work Location"}
            </span>
          </div>
        }
        open={isTaxSlabsModalVisible}
        onCancel={handleTaxSlabsModalClose}
        footer={[
          <button
            key="close"
            className="btn btn-primary"
            onClick={handleTaxSlabsModalClose}
          >
            Close
          </button>,
        ]}
        width={800}
        centered
        className="tax-slabs-modal"
      >
        <div className="modal-content-container">
          {/* Header Information */}
          <div className="row mb-4">
            <div className="col-md-6">
              <div className="d-flex align-items-center mb-2">
                <span>{selectedTax?.state}</span>
              </div>
              <div className="d-flex align-items-center mb-2">
                <span className="fw-semibold me-2">Deduction Cycle:</span>
                <span className="text-capitalize">
                  {selectedTax ? getDeductionCycleLabel(selectedTax) : "Monthly"}
                </span>
              </div>
            </div>
            <div className="col-md-6 text-md-end">
              <div className="text-muted">
                Effective from{" "}
                {selectedTax &&
                  selectedTax.slabRateConfigurations &&
                  selectedTax.slabRateConfigurations.length > 0 &&
                  selectedTax.slabRateConfigurations[0].effectiveFrom
                  ? selectedTax.slabRateConfigurations[0].effectiveFrom
                  : "N/A"}
              </div>
            </div>
          </div>

          {/* Tax Slabs Table */}
          <div className="table-responsive">
            <Table
              columns={taxSlabsColumns}
              dataSource={taxSlabsData}
              pagination={false}
              size="middle"
              className="tax-slabs-table"
              scroll={{ x: true }}
            />
          </div>
        </div>
      </Modal>

      {loading && <Loader />}

      {/* Mobile-friendly CSS */}
      <style jsx>{`
        @media (max-width: 768px) {
          .tax-slabs-modal :global(.ant-modal) {
            width: 95% !important;
            margin: 10px auto;
            max-width: 100%;
          }

          .tax-slabs-modal :global(.ant-modal-content) {
            border-radius: 12px;
          }

          .tax-slabs-modal :global(.ant-modal-header) {
            padding: 16px;
            border-bottom: 1px solid #f0f0f0;
          }

          .tax-slabs-modal :global(.ant-modal-body) {
            padding: 16px;
          }

          .tax-slabs-modal :global(.ant-modal-footer) {
            padding: 16px;
            border-top: 1px solid #f0f0f0;
          }

          .tax-slabs-table :global(.ant-table) {
            font-size: 14px;
          }

          .tax-slabs-table :global(.ant-table-thead > tr > th) {
            padding: 12px 8px;
            background-color: #f8f9fa;
            font-weight: 600;
          }

          .tax-slabs-table :global(.ant-table-tbody > tr > td) {
            padding: 12px 8px;
          }

          .modal-content-container {
            max-height: 70vh;
            overflow-y: auto;
          }
        }

        @media (max-width: 576px) {
          .tax-slabs-table :global(.ant-table-thead > tr > th),
          .tax-slabs-table :global(.ant-table-tbody > tr > td) {
            padding: 8px 6px;
            font-size: 12px;
          }

          .tax-slabs-modal :global(.ant-modal-title) {
            font-size: 16px;
          }
        }

        .tax-slabs-table :global(.ant-table-container) {
          overflow-x: auto;
        }

        .tax-slabs-modal :global(.ant-btn) {
          height: 40px;
          border-radius: 6px;
          font-weight: 500;
        }
      `}</style>
    </>
  );
}
