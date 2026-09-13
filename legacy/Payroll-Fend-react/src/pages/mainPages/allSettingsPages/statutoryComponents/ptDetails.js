// ptDetails.js
import React, { useEffect, useState } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";

export default function PtDetails() {
    const navigate = useNavigate();
    const { taxId } = useParams();
    const location = useLocation();

    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);

    const [taxConfig, setTaxConfig] = useState(null);
    const [deductionCycle, setDeductionCycle] = useState("monthly");
    const [slabs, setSlabs] = useState([]);
    const [effectiveFrom, setEffectiveFrom] = useState("");

    const organizationId = localStorage.getItem("organizationId");

    // Helper: default months if backend doesn't send them
    const DEFAULT_MONTHS = [
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December",
    ];

    useEffect(() => {
        fetchTaxDetails();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [taxId]);

    const fetchTaxDetails = async () => {
        try {
            setLoading(true);
            // Reuse list API and filter by taxId (safe because you already have it working)
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
            const config = list.find((item) => item.taxId === taxId);

            if (!config) {
                errorMsg("Error", "Professional Tax configuration not found", true);
                navigate("/statutory-components/professional-tax");
                return;
            }

            setTaxConfig(config);

            // IMPORTANT: Always take deduction cycle from top-level config (NOT slabRateConfigurations)
            const currentDeductionCycle =
                config.deductionFrequency ||
                config.taxConfigurationFrequency ||
                "monthly";

            setDeductionCycle(currentDeductionCycle.toLowerCase());

            // IMPORTANT: Always take effectiveFrom from top-level config (or keep empty)
            const currentEffectiveFrom = config.effectiveFrom || "";
            setEffectiveFrom(currentEffectiveFrom);

            // ALWAYS use slabDetails from the top-level config (ignore slabRateConfigurations)
            const baseSlabs = Array.isArray(config.slabDetails)
                ? config.slabDetails
                : [];

            // Map and preserve id & defaultFromMaster
            const mappedSlabs = baseSlabs.map((s) => ({
                id: s.id ?? null,
                startAmount: s.startAmount != null ? s.startAmount.toString() : "",
                endAmount: s.endAmount != null ? s.endAmount.toString() : "",
                payAmount: s.payAmount != null ? s.payAmount.toString() : "",
                defaultFromMaster: typeof s.defaultFromMaster === "boolean" ? s.defaultFromMaster : (s.defaultFromMaster ?? true),
                deductionMonths: Array.isArray(s.deductionMonths) ? s.deductionMonths : DEFAULT_MONTHS,
            }));

            if (mappedSlabs.length === 0) {
                mappedSlabs.push({
                    id: null,
                    startAmount: "",
                    endAmount: "",
                    payAmount: "",
                    defaultFromMaster: false,
                    deductionMonths: DEFAULT_MONTHS,
                });
            }

            setSlabs(mappedSlabs);
        } catch (error) {
            console.error("Error fetching PT details:", error);
            errorMsg(
                "Error",
                error?.response?.data?.message ||
                "Failed to load Professional Tax details",
                true
            );
        } finally {
            setLoading(false);
        }
    };

    const handleSlabChange = (index, field, value) => {
        const updated = [...slabs];
        updated[index][field] = value;
        // if user edits an original defaultFromMaster row, keep defaultFromMaster as-is
        setSlabs(updated);
    };

    const handleAddSlab = () => {
        setSlabs([
            ...slabs,
            {
                id: null,
                startAmount: "",
                endAmount: "",
                payAmount: "",
                defaultFromMaster: false, // newly added entries are not default
                deductionMonths: DEFAULT_MONTHS,
            },
        ]);
    };

    const handleRemoveSlab = (index) => {
        const target = slabs[index];
        // Prevent removing defaultFromMaster entries
        if (target && target.defaultFromMaster) {
            errorMsg("Not Allowed", "This slab is from master and cannot be removed.", false);
            return;
        }

        // allow removal for non-default entries
        const updated = slabs.filter((_, i) => i !== index);
        setSlabs(updated);
    };

    const handleCancel = () => {
        navigate("/statutory-components/professional-tax");
    };

    const hasNonDefaultSlabs = () => {
        return slabs.some((s) => !s.defaultFromMaster);
    };

    const handleResetToDefault = async () => {
        if (!taxConfig) return;
        const confirmReset = window.confirm(
            "Are you sure you want to reset to default slabs? This will remove any non-default (custom) slabs."
        );
        if (!confirmReset) return;

        try {
            setLoading(true);
            const response = await axios.delete(
                `${GlobalConst.API_URL}/api/professional-tax/${taxId}/slab/reset`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId,
                    },
                }
            );

            if (response?.data?.status === 200 && response?.data?.data) {
                // API returns updated ProfessionalTaxDTO — update local state
                const updatedConfig = response.data.data;
                setTaxConfig(updatedConfig);

                // update effectiveFrom from top-level (after reset)
                const updatedEffectiveFrom = updatedConfig.effectiveFrom || "";
                setEffectiveFrom(updatedEffectiveFrom);

                // Use top-level slabDetails from returned DTO (authoritative)
                const baseSlabs = Array.isArray(updatedConfig.slabDetails)
                    ? updatedConfig.slabDetails
                    : [];

                const mappedSlabs = baseSlabs.map((s) => ({
                    id: s.id ?? null,
                    startAmount: s.startAmount != null ? s.startAmount.toString() : "",
                    endAmount: s.endAmount != null ? s.endAmount.toString() : "",
                    payAmount: s.payAmount != null ? s.payAmount.toString() : "",
                    defaultFromMaster: typeof s.defaultFromMaster === "boolean" ? s.defaultFromMaster : (s.defaultFromMaster ?? true),
                    deductionMonths: Array.isArray(s.deductionMonths) ? s.deductionMonths : DEFAULT_MONTHS,
                }));

                setSlabs(mappedSlabs.length ? mappedSlabs : [{
                    id: null,
                    startAmount: "",
                    endAmount: "",
                    payAmount: "",
                    defaultFromMaster: false,
                    deductionMonths: DEFAULT_MONTHS,
                }]);

                successMsg("Success", "Reset to default slabs completed", false);
            } else {
                errorMsg(
                    "Error",
                    response?.data?.message || "Failed to reset to default slabs",
                    true
                );
            }
        } catch (error) {
            console.error("Error resetting PT slabs:", error);
            errorMsg(
                "Error",
                error?.response?.data?.message || "Failed to reset to default slabs",
                true
            );
        } finally {
            setLoading(false);
        }
    };

    const handleSave = async () => {
        if (!taxConfig) return;

        // ✅ Effective date mandatory validation
        if (!effectiveFrom) {
            errorMsg("Validation", "Please select Effective From date", false);
            return;
        }

        try {
            setSaving(true);

            // Derive existing deduction months from top-level slabDetails (NOT slabRateConfigurations)
            const existingDeductionMonths =
                Array.isArray(taxConfig.slabDetails) &&
                    taxConfig.slabDetails.length > 0 &&
                    Array.isArray(taxConfig.slabDetails[0].deductionMonths) &&
                    taxConfig.slabDetails[0].deductionMonths.length > 0
                    ? taxConfig.slabDetails[0].deductionMonths
                    : DEFAULT_MONTHS;

            const normalizedSlabs = slabs
                .filter(
                    (s) =>
                        (s.startAmount !== "" && s.startAmount != null) ||
                        (s.endAmount !== "" && s.endAmount != null) ||
                        (s.payAmount !== "" && s.payAmount != null)
                )
                .map((s) => ({
                    id: s.id ?? null,
                    startAmount: Number(s.startAmount || 0),
                    endAmount: Number(s.endAmount || 0),
                    payAmount: Number(s.payAmount || 0),
                    deductionMonths:
                        Array.isArray(s.deductionMonths) && s.deductionMonths.length > 0
                            ? s.deductionMonths
                            : existingDeductionMonths,
                    femaleExempted: false,
                    defaultFromMaster: !!s.defaultFromMaster,
                }));

            if (normalizedSlabs.length === 0) {
                errorMsg("Validation", "Please add at least one tax slab", false);
                setSaving(false);
                return;
            }

            // Prepare a rate config object — keeping structure (server expects slabRateConfigurations),
            // but slabDetails payload will be from normalizedSlabs (which came from top-level slabDetails).
            const updatedRateConfig = {
                // preserve any existing fields if present in taxConfig.slabRateConfigurations[0]
                ...((taxConfig.slabRateConfigurations && taxConfig.slabRateConfigurations[0]) || {}),
                taxRateSettingsId:
                    (taxConfig.slabRateConfigurations &&
                        taxConfig.slabRateConfigurations[0] &&
                        taxConfig.slabRateConfigurations[0].taxRateSettingsId) ||
                    undefined,
                taxConfigurationFrequency:
                    (taxConfig.slabRateConfigurations &&
                        taxConfig.slabRateConfigurations[0] &&
                        taxConfig.slabRateConfigurations[0].taxConfigurationFrequency) ||
                    "monthly",
                grossSalaryConfigurationFrequency:
                    (taxConfig.slabRateConfigurations &&
                        taxConfig.slabRateConfigurations[0] &&
                        taxConfig.slabRateConfigurations[0].grossSalaryConfigurationFrequency) ||
                    "monthly",
                deductionFrequency: deductionCycle.toLowerCase(),
                // ⛔ DO NOT set effectiveFrom here anymore (as per requirement)
                effectiveTo:
                    (taxConfig.slabRateConfigurations &&
                        taxConfig.slabRateConfigurations[0] &&
                        taxConfig.slabRateConfigurations[0].effectiveTo) ||
                    null,
                slabDetails: normalizedSlabs,
                active:
                    typeof (taxConfig.slabRateConfigurations &&
                        taxConfig.slabRateConfigurations[0] &&
                        taxConfig.slabRateConfigurations[0].active) === "boolean"
                        ? taxConfig.slabRateConfigurations[0].active
                        : true,
                editable:
                    typeof (taxConfig.slabRateConfigurations &&
                        taxConfig.slabRateConfigurations[0] &&
                        taxConfig.slabRateConfigurations[0].editable) === "boolean"
                        ? taxConfig.slabRateConfigurations[0].editable
                        : true,
            };

            const updatedTaxConfig = {
                ...taxConfig,
                deductionFrequency: deductionCycle.toLowerCase(),
                // ✅ Store effective date at top-level ProfessionalTax
                effectiveFrom: effectiveFrom,
                // Keep slabRateConfigurations in payload (server likely expects it) but ensure it reflects top-level slabDetails
                slabRateConfigurations: [updatedRateConfig],
                // also update top-level slabDetails to match (you requested slabDetails to be authoritative)
                slabDetails: normalizedSlabs,
            };

            const response = await axios.put(
                `${GlobalConst.API_URL}/api/professional-tax/${taxId}/slab`,
                updatedTaxConfig,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("__t")}`,
                        organizationId: organizationId,
                    },
                }
            );

            if (response?.data?.status === 200) {
                successMsg(
                    "Success",
                    "Professional Tax slabs updated successfully",
                    false
                );
                navigate("/statutory-components/professional-tax");
            } else {
                errorMsg(
                    "Error",
                    response?.data?.message || "Failed to update Professional Tax slabs",
                    true
                );
            }
        } catch (error) {
            console.error("Error saving PT slabs:", error);
            errorMsg(
                "Error",
                error?.response?.data?.message ||
                "Failed to update Professional Tax slabs",
                true
            );
        } finally {
            setSaving(false);
        }
    };

    const getHeaderTitle = () => {
        const stateName =
            location.state?.stateName || taxConfig?.state || "State";
        const locName =
            location.state?.locationName || taxConfig?.locationName || "";
        if (locName) {
            return `Professional Tax, ${locName} (${stateName})`;
        }
        return `Professional Tax, ${stateName}`;
    };

    return (
        <>
            <Helmet>
                <title>HRMS InfiNevoCloud - Professional Tax Details</title>
            </Helmet>

            {(loading || saving) && <Loader />}

            <div className="d-flex flex-column flex-lg-row flex-column-fluid flex-grow-1 bg-white">
                <div className="d-flex flex-column flex-lg-row-fluid py-4 px-5">
                    <div className="w-100" style={{ maxWidth: "900px", margin: "0 auto" }}>
                        <h5 className="fw-bold mb-4">{getHeaderTitle()}</h5>

                        {/* Deduction Cycle */}
                        <div className="mb-4">
                            <label className="form-label fw-semibold">
                                Deduction Cycle{" "}
                                <i
                                    className="bi bi-info-circle text-muted"
                                    title="Frequency at which Professional Tax will be deducted"
                                ></i>
                            </label>
                            <select
                                className="form-select"
                                value={deductionCycle}
                                onChange={(e) => setDeductionCycle(e.target.value)}
                            >
                                <option value="monthly">Monthly</option>
                                {/* In future, if backend supports other values, add here */}
                            </select>
                        </div>

                        {/* Tax Slabs */}
                        <div className="mb-4">
                            <div className="d-flex justify-content-between align-items-center mb-2">
                                <div className="fw-semibold">
                                    Tax Slabs based on{" "}
                                    <span className="fw-bold">Monthly Gross Salary</span>
                                </div>
                            </div>

                            <div className="table-responsive">
                                <table className="table align-middle">
                                    <thead>
                                        <tr>
                                            <th className="text-muted small">
                                                START RANGE (₹)
                                            </th>
                                            <th className="text-muted small">
                                                END RANGE (₹)
                                            </th>
                                            <th className="text-muted small">
                                                MONTHLY TAX AMOUNT (₹)
                                            </th>
                                            <th style={{ width: "40px" }}></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {slabs.map((slab, index) => (
                                            <tr key={index}>
                                                <td style={{ maxWidth: "180px" }}>
                                                    <input
                                                        type="number"
                                                        className="form-control"
                                                        value={slab.startAmount}
                                                        onChange={(e) =>
                                                            handleSlabChange(
                                                                index,
                                                                "startAmount",
                                                                e.target.value
                                                            )
                                                        }
                                                        placeholder="Start"
                                                    />
                                                </td>
                                                <td style={{ maxWidth: "180px" }}>
                                                    <input
                                                        type="number"
                                                        className="form-control"
                                                        value={slab.endAmount}
                                                        onChange={(e) =>
                                                            handleSlabChange(
                                                                index,
                                                                "endAmount",
                                                                e.target.value
                                                            )
                                                        }
                                                        placeholder="End"
                                                    />
                                                </td>
                                                <td style={{ maxWidth: "180px" }}>
                                                    <input
                                                        type="number"
                                                        className="form-control"
                                                        value={slab.payAmount}
                                                        onChange={(e) =>
                                                            handleSlabChange(
                                                                index,
                                                                "payAmount",
                                                                e.target.value
                                                            )
                                                        }
                                                        placeholder="Tax Amount"
                                                    />
                                                </td>
                                                <td className="text-center">
                                                    {/* Only allow per-row remove if NOT defaultFromMaster */}
                                                    {!slab.defaultFromMaster && slabs.length > 1 && (
                                                        <button
                                                            type="button"
                                                            className="btn btn-link text-danger p-0"
                                                            onClick={() => handleRemoveSlab(index)}
                                                            title="Remove slab"
                                                        >
                                                            ×
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>

                            <button
                                type="button"
                                className="btn btn-link text-primary p-0 mt-1"
                                onClick={handleAddSlab}
                            >
                                + Additional Slab
                            </button>
                        </div>

                        {/* Effective From */}
                        <div className="mb-4">
                            <label className="form-label fw-semibold">Effective From</label>
                            <div style={{ maxWidth: "220px" }}>
                                <input
                                    type="date"
                                    className="form-control"
                                    value={effectiveFrom || ""}
                                    onChange={(e) => setEffectiveFrom(e.target.value)}
                                />
                            </div>
                        </div>

                        {/* Reset/Delete section - visible only when there is any non-default slab */}
                        {hasNonDefaultSlabs() && (
                            <div className="mb-4 d-flex justify-content-end">
                                <span
                                    onClick={handleResetToDefault}
                                    style={{
                                        color: "red",
                                        cursor: "pointer",
                                        display: "flex",
                                        alignItems: "center"
                                    }}
                                    title="Reset to default slabs"
                                >
                                    {/* Text first, then trash icon */}
                                    Delete
                                    <i
                                        className="bi bi-trash3-fill ms-2"
                                        style={{ fontSize: "18px" }}
                                    ></i>
                                </span>
                            </div>
                        )}

                        {/* Actions */}
                        <div className="d-flex gap-3">
                            <button
                                type="button"
                                className="btn btn-primary"
                                onClick={handleSave}
                                disabled={saving}
                            >
                                Save
                            </button>
                            <button
                                type="button"
                                className="btn btn-light"
                                onClick={handleCancel}
                                disabled={saving}
                            >
                                Cancel
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
}
