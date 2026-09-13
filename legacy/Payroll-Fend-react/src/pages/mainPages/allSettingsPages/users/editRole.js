import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { useNavigate, useParams } from "react-router-dom";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";

export default function EditRole() {
  const navigate = useNavigate();
  const { id } = useParams(); // roleId from route
  const organizationId = localStorage.getItem("organizationId");

  const [role, setRole] = useState({
    roleId: "",
    roleName: "",
    roleDescription: "",
    status: "",
    accessType: "",
    userActionRequired: false,
    isDefault: false,
  });

  const [loading, setLoading] = useState(false);

  // Fetch role details by ID
  useEffect(() => {
    const fetchRoleById = async () => {
      try {
        setLoading(true);
        const response = await axios.get(
          `${GlobalConst.API_URL}/api/roles/${id}`,
          {
            headers: {
              Authorization: `Bearer ${localStorage.getItem("__t")}`,
              organizationId: organizationId,
            },
          }
        );

        if (response.data && response.data.data) {
          setRole(response.data.data); // API returns OrganizationRoleDTO
        } else {
          errorMsg("Error", "Role details not found", false);
          navigate("/roles");
        }
      } catch (error) {
        console.error("Fetch Role Error:", error);
        errorMsg("Error", "Failed to fetch role details", false);
        navigate("/roles");
      } finally {
        setLoading(false);
      }
    };

    fetchRoleById();
  }, [id, navigate, organizationId]);

  // Handle input change
  const handleChange = (e) => {
    const { name, value } = e.target;
    setRole((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  // Save changes
  const handleSave = async () => {
    try {
      setLoading(true);

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/roles/${role.roleId}`,
        {
          roleName: role.roleName,
          roleDescription: role.roleDescription,
        },
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data.status === 200) {
        successMsg("Success", "Role updated successfully", false);
        navigate("/roles"); // redirect back
      }
    } catch (error) {
      console.error("Update Error:", error);
      if (error.response) {
        errorMsg(
          "Update Failed",
          error.response.data?.message || "Failed to update role",
          false
        );
      } else if (error.request) {
        errorMsg(
          "Network Error",
          "Cannot connect to the server. Please check your connection.",
          false
        );
      } else {
        errorMsg("Error", "An unexpected error occurred", false);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    navigate("/roles");
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Edit Role</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Edit Role</h5>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100" style={{ maxWidth: "600px" }}>
              {/* Role Name */}
              <div className="fv-row mb-10">
                <label
                  htmlFor="roleName"
                  className="form-label fs-6 fw-bold text-dark"
                >
                  Role Name <span className="text-danger">*</span>
                </label>
                <input
                  type="text"
                  name="roleName"
                  id="roleName"
                  value={role.roleName}
                  onChange={handleChange}
                  className="form-control form-control-lg form-control-solid"
                  style={{ maxWidth: "500px" }}
                  disabled={loading}
                />
              </div>

              {/* Description */}
              <div className="fv-row mb-10">
                <label
                  htmlFor="roleDescription"
                  className="form-label fs-6 fw-bold text-dark"
                >
                  Description <span className="text-danger">*</span>
                </label>
                <textarea
                  name="roleDescription"
                  id="roleDescription"
                  value={role.roleDescription}
                  onChange={handleChange}
                  className="form-control form-control-lg form-control-solid"
                  style={{ maxWidth: "500px", minHeight: "100px" }}
                  disabled={loading}
                />
              </div>

              {/* Status */}
              <div className="fv-row mb-10">
                <label className="form-label fs-6 fw-bold text-dark">
                  Status
                </label>
                <div>
                  <span
                    className={`badge ${
                      role.status === "active" ? "bg-success" : "bg-danger"
                    }`}
                  >
                    {role.status?.toUpperCase()}
                  </span>
                </div>
              </div>

              {/* Save & Cancel */}
              <div
                className="d-flex align-items-center mt-5"
                style={{ gap: "10px" }}
              >
                <button
                  type="button"
                  className="btn btn-lg btn-primary"
                  onClick={handleSave}
                  disabled={loading}
                >
                  {loading ? "Saving..." : "Save"}
                </button>
                <button
                  type="button"
                  className="btn btn-light"
                  onClick={handleCancel}
                  disabled={loading}
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
