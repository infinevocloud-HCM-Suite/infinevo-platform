import React, { useState } from "react";
import { Helmet } from "react-helmet-async";
import { Button, Input, message } from "antd";
import { SearchOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate } from "react-router-dom";

const { Search } = Input;

export default function UnsubmittedList() {
  const navigate = useNavigate();
  const [searchEmployee, setSearchEmployee] = useState("");
  const [selectedEmployees, setSelectedEmployees] = useState([]);

  // Dummy data for employees yet to submit POI
  const [yetToSubmitEmployees, setYetToSubmitEmployees] = useState([
    {
      id: "PO34",
      name: "Shubham Kumar",
      email: "19she0qh@gmail.com",
      portalStatus: "Enabled",
      poiReleaseStatus: "Locked"
    },
    {
      id: "DY001",
      name: "Driseit Vassin",
      email: "drist@gmail.com",
      portalStatus: "Disabled",
      poiReleaseStatus: "-"
    },
    {
      id: "KX001",
      name: "Khurill Deshi",
      email: "khurill@gmail.com",
      portalStatus: "Enabled",
      poiReleaseStatus: "Locked"
    },
    {
      id: "EMP89",
      name: "SP VADAY",
      email: "yadavuvrpraksatv7@gmail.com",
      portalStatus: "Enabled",
      poiReleaseStatus: "Locked"
    }
  ]);

  // Filter yet to submit employees based on search
  const filteredYetToSubmit = yetToSubmitEmployees.filter(emp =>
    !searchEmployee || 
    emp.name.toLowerCase().includes(searchEmployee.toLowerCase()) ||
    emp.email.toLowerCase().includes(searchEmployee.toLowerCase()) ||
    emp.id.toLowerCase().includes(searchEmployee.toLowerCase())
  );

  // Navigate back to main POI page
  const navigateBack = () => {
    navigate("/proof-of-investment");
  };

  // Handle select all checkbox
  const handleSelectAll = (e) => {
    if (e.target.checked) {
      setSelectedEmployees(filteredYetToSubmit.map(emp => emp.id));
    } else {
      setSelectedEmployees([]);
    }
  };

  // Handle individual employee checkbox
  const handleEmployeeSelect = (employeeId, isChecked) => {
    if (isChecked) {
      setSelectedEmployees(prev => [...prev, employeeId]);
    } else {
      setSelectedEmployees(prev => prev.filter(id => id !== employeeId));
    }
  };

  // Check if all employees are selected
  const isAllSelected = filteredYetToSubmit.length > 0 && selectedEmployees.length === filteredYetToSubmit.length;

  // Check if some employees are selected
  const isIndeterminate = selectedEmployees.length > 0 && selectedEmployees.length < filteredYetToSubmit.length;

  // Handle send reminder
  const handleSendReminder = () => {
    if (selectedEmployees.length === 0) {
      message.warning("Please select at least one employee to send reminder");
      return;
    }

    const selectedNames = yetToSubmitEmployees
      .filter(emp => selectedEmployees.includes(emp.id))
      .map(emp => emp.name)
      .join(", ");

    message.success(`Reminder sent successfully to ${selectedEmployees.length} employee(s): ${selectedNames}`);
    
    // Clear selection after sending reminder
    setSelectedEmployees([]);
  };

  return (
    <>
      <Helmet>
        <title>Employees Yet to Submit POI | Admin</title>
      </Helmet>

      <div className="container-fluid p-0 bg-white">
        <div className="p-6">
          {/* Header with back button and Send Reminder button */}
          <div className="d-flex justify-content-between align-items-center mb-4">
            <div className="d-flex align-items-center">
              <Button 
                type="text" 
                icon={<ArrowLeftOutlined />} 
                onClick={navigateBack}
                className="me-3"
              >
              </Button>
              <div>
                <h2 className="fw-bolder mb-1">Employees Yet to Submit POI</h2>
                <div className="text-muted">List of employees who haven't submitted their Proof of Investment documents</div>
              </div>
            </div>
            
            {/* Send Reminder Button */}
            <Button 
              type="primary" 
              onClick={handleSendReminder}
              disabled={selectedEmployees.length === 0}
            >
              Send Reminder ({selectedEmployees.length})
            </Button>
          </div>

          {/* Search and Filters Section */}
          <div className="card mb-6">
            <div className="card-body">
              <div className="row g-4 align-items-end">
                <div className="col-md-6">
                  <label className="form-label small text-muted">Search Employee</label>
                  <Input
                    placeholder="Search by name, email, or employee ID..."
                    prefix={<SearchOutlined />}
                    value={searchEmployee}
                    onChange={(e) => setSearchEmployee(e.target.value)}
                    allowClear
                  />
                </div>
                <div className="col-md-6 text-end">
                  <div className="text-muted">
                    Total Employees: <strong>{yetToSubmitEmployees.length}</strong>
                    {selectedEmployees.length > 0 && (
                      <span className="text-primary ms-2">
                        • Selected: <strong>{selectedEmployees.length}</strong>
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Main Table */}
          <div className="card">
            <div className="card-header">
              <h5 className="card-title mb-0">
                Employees Yet to Submit Proof of Investment
                <span className="text-muted fs-6 ms-2">({filteredYetToSubmit.length} employees)</span>
              </h5>
            </div>
            <div className="card-body">
              <div className="table-responsive">
                <table className="table table-hover align-middle">
                  <thead>
                    <tr className="text-muted small bg-light">
                      <th style={{ width: '40px' }}>
                        <div className="form-check">
                          <input
                            className="form-check-input"
                            type="checkbox"
                            checked={isAllSelected}
                            ref={input => {
                              if (input) {
                                input.indeterminate = isIndeterminate;
                              }
                            }}
                            onChange={handleSelectAll}
                          />
                        </div>
                      </th>
                      <th>EMP ID</th>
                      <th>NAME</th>
                      <th>EMAIL ID</th>
                      <th>PORTAL STATUS</th>
                      <th>POI RELEASE STATUS</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredYetToSubmit.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="text-center text-muted py-5">
                          No employees found matching your search criteria.
                        </td>
                      </tr>
                    ) : (
                      filteredYetToSubmit.map((employee) => (
                        <tr key={employee.id}>
                          <td>
                            <div className="form-check">
                              <input
                                className="form-check-input"
                                type="checkbox"
                                checked={selectedEmployees.includes(employee.id)}
                                onChange={(e) => handleEmployeeSelect(employee.id, e.target.checked)}
                              />
                            </div>
                          </td>
                          <td className="fw-semibold">{employee.id}</td>
                          <td>
                            <div className="d-flex align-items-center">
                              <div className="symbol symbol-35px symbol-circle me-3">
                                <span className="symbol-label bg-warning text-white fw-bold">
                                  {employee.name.charAt(0)}
                                </span>
                              </div>
                              <div>
                                <div className="fw-bold text-dark">{employee.name}</div>
                              </div>
                            </div>
                          </td>
                          <td>{employee.email}</td>
                          <td>
                            <span className={`badge ${
                              employee.portalStatus === "Enabled" ? "badge-light-success" : "badge-light-danger"
                            }`}>
                              {employee.portalStatus}
                            </span>
                          </td>
                          <td>
                            <span className={`badge ${
                              employee.poiReleaseStatus === "Locked" ? "badge-light-warning" : "badge-light-secondary"
                            }`}>
                              {employee.poiReleaseStatus}
                            </span>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Additional Information Card */}
          <div className="card mt-4">
            <div className="card-body">
              <h6 className="fw-bold mb-3">About Employees Yet to Submit POI</h6>
              <div className="row">
                <div className="col-md-6">
                  <div className="d-flex align-items-start mb-3">
                    <i className="bi bi-info-circle text-primary me-2 mt-1"></i>
                    <div>
                      <div className="fw-semibold">Portal Status: Enabled</div>
                      <small className="text-muted">
                        Employees can access the POI portal but haven't submitted documents yet
                      </small>
                    </div>
                  </div>
                  <div className="d-flex align-items-start mb-3">
                    <i className="bi bi-info-circle text-primary me-2 mt-1"></i>
                    <div>
                      <div className="fw-semibold">POI Release: Locked</div>
                      <small className="text-muted">
                        POI submission feature is currently locked for these employees
                      </small>
                    </div>
                  </div>
                </div>
                <div className="col-md-6">
                  <div className="d-flex align-items-start mb-3">
                    <i className="bi bi-lightbulb text-warning me-2 mt-1"></i>
                    <div>
                      <div className="fw-semibold">Recommended Actions</div>
                      <small className="text-muted">
                        • Send reminder emails<br/>
                        • Check if employees need assistance<br/>
                        • Verify portal access permissions
                      </small>
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