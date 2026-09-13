// taxSlabRegime.js
// "TaxSlab Regim" tab — full CRUD for income-tax regime slabs.
//
// Endpoints:
//   GET    /api/tax-slabs         -> load all active regimes
//   POST   /api/tax-slabs         -> create a new regime
//   PUT    /api/tax-slabs/{id}    -> update slabs of an existing regime
//   DELETE /api/tax-slabs/{id}    -> soft-delete (deactivate) a regime
import React, { useEffect, useState } from "react";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import Loader from "../../../../shared/components/loaders/fullPageLoader";

const EMPTY_ROW = { from: "", to: "", rate: "" };
const REGIME_OPTIONS = ["OLD", "NEW"];

export default function TaxSlabRegimeSettings() {
    const [loading, setLoading] = useState(false);
    const [regimes, setRegimes] = useState([]);

    // Edit mode — which existing regime is being edited
    const [editingId, setEditingId] = useState(null);
    const [draftRows, setDraftRows] = useState([]);

    // Create mode — the "Add New Regime" form
    const [adding, setAdding] = useState(false);
    const [fromYear, setFromYear] = useState("");
    const [toYear, setToYear] = useState("");
    const [newRegimeType, setNewRegimeType] = useState("OLD");
    const [newRows, setNewRows] = useState([{ ...EMPTY_ROW }]);

    // Confirm-delete
    const [confirmDeleteId, setConfirmDeleteId] = useState(null);

    const authHeaders = () => ({
        Authorization: `Bearer ${localStorage.getItem("__t")}`,
        organizationId: localStorage.getItem("organizationId"),
    });

    /* ----------------------------------------------------------------- */
    /* LOAD                                                               */
    /* ----------------------------------------------------------------- */
    useEffect(() => { fetchSlabs(); }, []);

    const fetchSlabs = async () => {
        try {
            setLoading(true);
            const res = await axios.get(`${GlobalConst.API_URL}/api/tax-slabs`, { headers: authHeaders() });
            setRegimes(res?.data?.data || []);
        } catch (err) {
            errorMsg("Error", err?.response?.data?.message || "Failed to load tax slabs", true);
        } finally {
            setLoading(false);
        }
    };

    /* ----------------------------------------------------------------- */
    /* VALIDATION (mirrors backend; backend is the hard gate)             */
    /* ----------------------------------------------------------------- */
    const validateRows = (rows) => {
        if (!rows || rows.length === 0) return "At least one tax slab is required.";

        const parsed = rows.map((r) => ({
            from: r.from === "" ? null : Number(r.from),
            to:   r.to   === "" ? null : Number(r.to),
            rate: r.rate === "" ? null : Number(r.rate),
        }));

        for (let i = 0; i < parsed.length; i++) {
            const row = parsed[i];
            const isLast = i === parsed.length - 1;
            const n = i + 1;

            if (row.from === null || isNaN(row.from) || row.from < 0)
                return `Slab #${n}: 'From' must be zero or greater.`;
            if (row.rate === null || isNaN(row.rate) || row.rate < 0 || row.rate > 100)
                return `Slab #${n}: 'Rate' must be between 0 and 100.`;
            if (i === 0 && row.from !== 0)
                return "The first slab must start at 0.";

            if (isLast) {
                if (row.to !== null)
                    return "The last (highest) slab must be open-ended — leave its 'To' empty.";
            } else {
                if (row.to === null || isNaN(row.to))
                    return `Slab #${n}: only the last slab may have an empty 'To'.`;
                if (row.to <= row.from)
                    return `Slab #${n}: 'To' must be greater than 'From'.`;
                const next = parsed[i + 1];
                if (next.from !== row.to && next.from !== row.to + 1)
                    return `Slabs must be continuous. Slab #${n + 1} should start at ${row.to} (or ${row.to + 1}).`;
            }
        }
        return null;
    };

    /* ----------------------------------------------------------------- */
    /* EDIT existing regime                                               */
    /* ----------------------------------------------------------------- */
    const startEdit = (regime) => {
        setAdding(false);
        setEditingId(regime.id);
        setDraftRows((regime.slabs || []).map((s) => ({
            from: s.from ?? "",
            to:   s.to === null || s.to === undefined ? "" : s.to,
            rate: s.rate ?? "",
        })));
    };

    const cancelEdit = () => { setEditingId(null); setDraftRows([]); };

    const updateDraftCell = (i, field, value) =>
        setDraftRows((prev) => prev.map((r, idx) => idx === i ? { ...r, [field]: value } : r));

    const addDraftRow = () => setDraftRows((prev) => [...prev, { ...EMPTY_ROW }]);
    const removeDraftRow = (i) => setDraftRows((prev) => prev.filter((_, idx) => idx !== i));

    const saveEdit = async (regimeId) => {
        const err = validateRows(draftRows);
        if (err) { errorMsg("Invalid Slabs", err, true); return; }

        const slabs = draftRows.map((r) => ({
            from: Number(r.from),
            to:   r.to === "" ? null : Number(r.to),
            rate: Number(r.rate),
        }));

        try {
            setLoading(true);
            await axios.put(
                `${GlobalConst.API_URL}/api/tax-slabs/${regimeId}`,
                { slabs },
                { headers: authHeaders() }
            );
            successMsg("Success", "Tax slabs updated successfully", true);
            cancelEdit();
            fetchSlabs();
        } catch (err) {
            errorMsg("Error", err?.response?.data?.message || "Failed to update tax slabs", true);
        } finally {
            setLoading(false);
        }
    };

    /* ----------------------------------------------------------------- */
    /* CREATE new regime                                                  */
    /* ----------------------------------------------------------------- */
    const openAdd = () => {
        cancelEdit();
        setAdding(true);
        setFromYear("");
        setToYear("");
        setNewRegimeType("OLD");
        setNewRows([{ ...EMPTY_ROW }]);
    };

    const cancelAdd = () => setAdding(false);

    const updateNewCell = (i, field, value) =>
        setNewRows((prev) => prev.map((r, idx) => idx === i ? { ...r, [field]: value } : r));

    const addNewRow = () => setNewRows((prev) => [...prev, { ...EMPTY_ROW }]);
    const removeNewRow = (i) => setNewRows((prev) => prev.filter((_, idx) => idx !== i));

    const saveNew = async () => {
        const fromNum = Number(fromYear);
        const toNum = Number(toYear);
        if (!fromYear || !toYear || isNaN(fromNum) || isNaN(toNum)) {
            errorMsg("Validation", "Please enter both From Year and To Year.", true); return;
        }
        if (toNum !== fromNum + 1) {
            errorMsg("Validation", "To Year must be exactly one year after From Year (e.g. 2025 → 2026).", true); return;
        }

        const fy = `${fromNum}-${toNum}`;

        const err = validateRows(newRows);
        if (err) { errorMsg("Invalid Slabs", err, true); return; }

        const slabs = newRows.map((r) => ({
            from: Number(r.from),
            to:   r.to === "" ? null : Number(r.to),
            rate: Number(r.rate),
        }));

        try {
            setLoading(true);
            await axios.post(
                `${GlobalConst.API_URL}/api/tax-slabs`,
                { financialYear: fy, taxRegime: newRegimeType, slabs },
                { headers: authHeaders() }
            );
            successMsg("Success", "New tax regime created successfully", true);
            cancelAdd();
            fetchSlabs();
        } catch (err) {
            errorMsg("Error", err?.response?.data?.message || "Failed to create tax regime", true);
        } finally {
            setLoading(false);
        }
    };

    /* ----------------------------------------------------------------- */
    /* DELETE (soft) a regime                                             */
    /* ----------------------------------------------------------------- */
    const confirmDelete = async () => {
        if (!confirmDeleteId) return;
        try {
            setLoading(true);
            await axios.delete(
                `${GlobalConst.API_URL}/api/tax-slabs/${confirmDeleteId}`,
                { headers: authHeaders() }
            );
            successMsg("Success", "Tax regime deactivated successfully", true);
            setConfirmDeleteId(null);
            fetchSlabs();
        } catch (err) {
            errorMsg("Error", err?.response?.data?.message || "Failed to delete tax regime", true);
        } finally {
            setLoading(false);
        }
    };

    /* ----------------------------------------------------------------- */
    /* RENDER helpers                                                     */
    /* ----------------------------------------------------------------- */
    const fmt = (v) =>
        v === null || v === undefined || v === "" ? "—" : Number(v).toLocaleString("en-IN");

    const renderSlabTable = (rows, isEdit, onChangeCell, onRemove, onAddRow) => (
        <div className="table-responsive">
            <table className="table table-row-bordered align-middle gs-0 gy-3">
                <thead>
                    <tr className="fw-bold text-gray-700 bg-light">
                        <th className="ps-3">From (₹)</th>
                        <th>To (₹)</th>
                        <th>Rate (%)</th>
                        {isEdit && <th className="text-end pe-3">Action</th>}
                    </tr>
                </thead>
                <tbody>
                    {rows.map((row, i) => {
                        const isLast = i === rows.length - 1;
                        return (
                            <tr key={i}>
                                <td className="ps-3">
                                    {isEdit ? (
                                        <input
                                            type="number"
                                            className="form-control form-control-sm"
                                            value={row.from}
                                            onChange={(e) => onChangeCell(i, "from", e.target.value)}
                                        />
                                    ) : fmt(row.from)}
                                </td>
                                <td>
                                    {isEdit ? (
                                        <input
                                            type="number"
                                            className="form-control form-control-sm"
                                            value={row.to}
                                            placeholder={isLast ? "Above (leave empty)" : ""}
                                            onChange={(e) => onChangeCell(i, "to", e.target.value)}
                                        />
                                    ) : (row.to === null || row.to === undefined ? "Above" : fmt(row.to))}
                                </td>
                                <td>
                                    {isEdit ? (
                                        <input
                                            type="number"
                                            className="form-control form-control-sm"
                                            value={row.rate}
                                            onChange={(e) => onChangeCell(i, "rate", e.target.value)}
                                        />
                                    ) : <span className="fw-semibold">{fmt(row.rate)}%</span>}
                                </td>
                                {isEdit && (
                                    <td className="text-end pe-3">
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-light-danger"
                                            onClick={() => onRemove(i)}
                                        >
                                            Remove
                                        </button>
                                    </td>
                                )}
                            </tr>
                        );
                    })}
                    {!isEdit && rows.length === 0 && (
                        <tr>
                            <td colSpan={3} className="text-center text-muted py-4">
                                No slabs configured
                            </td>
                        </tr>
                    )}
                </tbody>
            </table>

            {isEdit && (
                <>
                    <button type="button" className="btn btn-sm btn-light-primary mt-1" onClick={onAddRow}>
                        + Add Slab
                    </button>
                    <div className="text-muted fs-8 mt-2">
                        Leave <strong>To</strong> empty on the last (highest) slab — it applies to all income above its <strong>From</strong>.
                    </div>
                </>
            )}
        </div>
    );

    const renderRegimeCard = (regime) => {
        const isEditing = editingId === regime.id;
        const isOtherEditing = editingId !== null && editingId !== regime.id;
        const title = regime.taxRegime === "OLD" ? "Old Tax Regime" : "New Tax Regime";

        return (
            <div className="col-md-6 mb-6" key={regime.id}>
                <div className="card h-100 shadow-sm">
                    <div className="card-header d-flex align-items-center justify-content-between">
                        <div>
                            <h5 className="fw-bold mb-0">{title}</h5>
                            <span className="text-muted fs-7">FY {regime.financialYear}</span>
                        </div>
                        <div className="d-flex gap-2">
                            {!isEditing ? (
                                <>
                                    <button
                                        className="btn btn-sm btn-primary"
                                        onClick={() => startEdit(regime)}
                                        disabled={isOtherEditing || adding}
                                    >
                                        Edit
                                    </button>
                                    <button
                                        className="btn btn-sm btn-light-danger"
                                        onClick={() => setConfirmDeleteId(regime.id)}
                                        disabled={isOtherEditing || adding}
                                    >
                                        Delete
                                    </button>
                                </>
                            ) : (
                                <>
                                    <button className="btn btn-sm btn-light" onClick={cancelEdit}>
                                        Cancel
                                    </button>
                                    <button className="btn btn-sm btn-success" onClick={() => saveEdit(regime.id)}>
                                        Save
                                    </button>
                                </>
                            )}
                        </div>
                    </div>
                    <div className="card-body">
                        {isEditing
                            ? renderSlabTable(draftRows, true, updateDraftCell, removeDraftRow, addDraftRow)
                            : renderSlabTable(regime.slabs || [], false)}
                    </div>
                </div>
            </div>
        );
    };

    /* ----------------------------------------------------------------- */
    /* Add New Regime card                                                */
    /* ----------------------------------------------------------------- */
    const renderAddCard = () => (
        <div className="col-12 mb-6">
            <div className="card shadow-sm border-primary">
                <div className="card-header d-flex align-items-center justify-content-between bg-light-primary">
                    <h5 className="fw-bold mb-0 text-primary">Add New Tax Regime</h5>
                    <div className="d-flex gap-2">
                        <button className="btn btn-sm btn-light" onClick={cancelAdd}>Cancel</button>
                        <button className="btn btn-sm btn-success" onClick={saveNew}>Create</button>
                    </div>
                </div>
                <div className="card-body">
                    <div className="row mb-4 g-3 align-items-end">
                        <div className="col-md-5">
                            <label className="form-label fw-semibold">Financial Year <span className="text-danger">*</span></label>
                            <div className="d-flex align-items-center gap-2">
                                <input
                                    type="number"
                                    className="form-control"
                                    placeholder="2025"
                                    min="2000"
                                    max="2100"
                                    value={fromYear}
                                    onChange={(e) => {
                                        setFromYear(e.target.value);
                                        if (e.target.value) setToYear(String(Number(e.target.value) + 1));
                                        else setToYear("");
                                    }}
                                />
                                <span className="fw-semibold text-muted px-1">to</span>
                                <input
                                    type="number"
                                    className="form-control"
                                    placeholder="2026"
                                    min="2001"
                                    max="2101"
                                    value={toYear}
                                    onChange={(e) => setToYear(e.target.value)}
                                />
                            </div>
                            {fromYear && toYear && (
                                <div className="text-muted fs-8 mt-1">
                                    Will be saved as <strong>{fromYear}-{toYear}</strong>
                                </div>
                            )}
                        </div>
                        <div className="col-md-3">
                            <label className="form-label fw-semibold">Tax Regime <span className="text-danger">*</span></label>
                            <select
                                className="form-select"
                                value={newRegimeType}
                                onChange={(e) => setNewRegimeType(e.target.value)}
                            >
                                {REGIME_OPTIONS.map((r) => (
                                    <option key={r} value={r}>{r === "OLD" ? "Old Regime" : "New Regime"}</option>
                                ))}
                            </select>
                        </div>
                    </div>

                    <label className="form-label fw-semibold mb-2">Tax Slabs</label>
                    {renderSlabTable(newRows, true, updateNewCell, removeNewRow, addNewRow)}
                </div>
            </div>
        </div>
    );

    /* ----------------------------------------------------------------- */
    /* Confirm-delete dialog                                              */
    /* ----------------------------------------------------------------- */
    const renderDeleteConfirm = () => {
        if (!confirmDeleteId) return null;
        const regime = regimes.find((r) => r.id === confirmDeleteId);
        return (
            <div className="modal d-block" tabIndex="-1" style={{ backgroundColor: "rgba(0,0,0,0.4)" }}>
                <div className="modal-dialog modal-dialog-centered">
                    <div className="modal-content">
                        <div className="modal-header">
                            <h5 className="modal-title fw-bold">Deactivate Tax Regime</h5>
                        </div>
                        <div className="modal-body">
                            <p>
                                Are you sure you want to deactivate the{" "}
                                <strong>{regime?.taxRegime === "OLD" ? "Old" : "New"} Tax Regime</strong> for
                                FY <strong>{regime?.financialYear}</strong>?
                            </p>
                            <p className="text-muted small mb-0">
                                This will stop it from appearing in the list and from being used in
                                TDS calculations. The record is kept for audit purposes and can be
                                re-activated from the database if needed.
                            </p>
                        </div>
                        <div className="modal-footer">
                            <button className="btn btn-light" onClick={() => setConfirmDeleteId(null)}>
                                Cancel
                            </button>
                            <button className="btn btn-danger" onClick={confirmDelete}>
                                Yes, Deactivate
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        );
    };

    /* ----------------------------------------------------------------- */
    /* ROOT RENDER                                                        */
    /* ----------------------------------------------------------------- */
    return (
        <div className="w-100">
            {loading && <Loader />}
            {renderDeleteConfirm()}

            <div className="d-flex align-items-center justify-content-between mb-4">
                <p className="text-gray-700 fs-6 mb-0">
                    Manage income-tax slab ranges and rates for the OLD and NEW regimes.
                    Changes are validated and recorded in the audit trail.
                </p>
                {!adding && (
                    <button
                        className="btn btn-primary btn-sm ms-4 text-nowrap"
                        onClick={openAdd}
                        disabled={editingId !== null}
                    >
                        + Add New Regime
                    </button>
                )}
            </div>

            <div className="row">
                {adding && renderAddCard()}

                {regimes.length > 0
                    ? regimes.map((regime) => renderRegimeCard(regime))
                    : !loading && !adding && (
                        <div className="col-12">
                            <div className="alert alert-info d-flex align-items-center p-4">
                                <i className="bi bi-info-circle-fill fs-2 me-3"></i>
                                <span className="fw-semibold">
                                    No active tax slab regimes found. Use "+ Add New Regime" to create one.
                                </span>
                            </div>
                        </div>
                    )}
            </div>
        </div>
    );
}
