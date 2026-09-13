import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import _ from "lodash";
import { Formik, Form, Field, ErrorMessage } from "formik";
import "antd/dist/reset.css";

import { Table, Dropdown, Menu, Modal } from "antd";
import {
  MoreOutlined,
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
} from "@ant-design/icons";

import * as Yup from "yup";
import Loader from "../../../../shared/components/loaders/fullPageLoader";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";
import axios from "axios";
import { useDispatch } from "react-redux";
import { errorMsg, successMsg } from "../../../../shared/helpers/msgHelper";
import { format } from "date-fns";
import { useNavigate } from "react-router-dom";

const { confirm } = Modal;

export default function Users() {
  const navigate = useNavigate();
  const dispatch = useDispatch();
  const [signingIn, setSigningIn] = useState(false);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");

  // Fetch all users (organization users + employees)
  useEffect(() => {
    fetchUsers();
  }, []);

  const formatUserType = (userType) => {
    if (!userType) return "-";
    if (userType === "ORGANIZATION_USER") return "Organization User";
    if (userType === "EMPLOYEE") return "Employee";
    return userType;
  };

  const formatLastLogin = (timestamp) => {
    if (!timestamp) return "Never";
    try {
      return format(new Date(timestamp), "dd MMM yyyy, hh:mm a");
    } catch (e) {
      return "-";
    }
  };

  const fetchUsers = async () => {
    try {
      setLoading(true);
      const response = await axios.get(
        `${GlobalConst.API_URL}/api/invitations`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      // New response structure: data { users: [...], totalUsers, ... }
      if (
        response.data &&
        response.data.data &&
        Array.isArray(response.data.data.users)
      ) {
        const apiUsers = response.data.data.users;

        const usersData = apiUsers.map((user, index) => ({
          key: user.userId || index.toString(),
          userId: user.userId,
          username: user.name,
          email: user.email,
          avatar: `https://ui-avatars.com/api/?name=${encodeURIComponent(
            user.name || "U"
          )}`,
          role: user.userRole,
          status: (user.status || "INACTIVE").toUpperCase(),
          mobile: user.mobile,
          invitationType: user.invitationType,
          isSuperAdmin: user.isSuperAdmin,
          userType: user.userType,
          lastLoginTime: user.lastLoginTime,
          isCurrentUser: user.isCurrentUser,
        }));

        setUsers(usersData);
      }
    } catch (error) {
      console.error("API Error:", error);
      if (error.response) {
        errorMsg(
          "Error",
          error.response.data?.message || "Failed to load users",
          true
        );
      } else if (error.request) {
        errorMsg(
          "Network Error",
          "Cannot connect to the server. Please check your connection.",
          true
        );
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  // Show delete confirmation modal
  const showDeleteConfirm = (userId, username) => {
    confirm({
      title: "Are you sure you want to delete this user permanently?",
      icon: <ExclamationCircleOutlined style={{ color: "#ff4d4f" }} />,
      content: `User: ${username}`,
      okText: "Yes, Delete",
      okType: "danger",
      cancelText: "No, Cancel",
      centered: true,
      maskClosable: true,
      onOk() {
        handleDeleteUser(userId);
      },
      onCancel() {
        console.log("Delete cancelled");
      },
    });
  };

  const handleDeleteUser = async (userId) => {
    try {
      const response = await axios.delete(
        `${GlobalConst.API_URL}/api/invitations/${userId}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data.status === 200) {
        setUsers((prev) => prev.filter((user) => user.userId !== userId));
        successMsg("User deleted successfully");
      }
    } catch (error) {
      console.error("Delete Error:", error);
      errorMsg("Failed to delete user");
    }
  };

  // AntD Table columns
  const columns = [
    {
      title: "USER DETAILS",
      dataIndex: "username",
      key: "userDetails",
      onHeaderCell: () => ({
        style: {
          padding: "6px",
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      onCell: () => ({
        style: {
          padding: "6px",
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      render: (_, record) => (
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ position: "relative" }}>
            <img
              src={record.avatar}
              alt={record.username}
              style={{ width: 32, height: 32, borderRadius: "50%" }}
            />
            {record.isCurrentUser && (
              <span
                style={{
                  position: "absolute",
                  right: -2,
                  bottom: -2,
                  width: 10,
                  height: 10,
                  borderRadius: "50%",
                  backgroundColor: "#22c55e", // green dot
                  border: "2px solid #ffffff",
                }}
              />
            )}
          </div>
          <div>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 8,
              }}
            >
              <div
                style={{ color: "#1677ff", cursor: "pointer", lineHeight: 1.2 }}
                onClick={() => handleViewUser(record)}
              >
                {record.username}
              </div>
              {record.isCurrentUser && (
                <span
                  style={{
                    fontSize: 11,
                    padding: "2px 6px",
                    borderRadius: 999,
                    backgroundColor: "#e0f7e9",
                    color: "#166534",
                    fontWeight: 600,
                  }}
                >
                  Current User
                </span>
              )}
            </div>
            <div style={{ fontSize: 12, color: "#8c8c8c" }}>{record.email}</div>
          </div>
        </div>
      ),
    },
    {
      title: "TYPE",
      dataIndex: "userType",
      key: "userType",
      onHeaderCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      onCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      render: (userType) => (
        <span style={{ color: "#504d4dff" }}>{formatUserType(userType)}</span>
      ),
    },
    {
      title: "ROLE",
      dataIndex: "role",
      key: "role",
      onHeaderCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      onCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      render: (role) => (
        <span style={{ color: "#504d4dff" }}>{role || "-"}</span>
      ),
    },
    {
      title: "LAST LOGIN",
      dataIndex: "lastLoginTime",
      key: "lastLoginTime",
      onHeaderCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      onCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      render: (lastLoginTime) => (
        <span style={{ color: "#504d4dff" }}>
          {formatLastLogin(lastLoginTime)}
        </span>
      ),
    },
    {
      title: "STATUS",
      dataIndex: "status",
      key: "status",
      onHeaderCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      onCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      render: (status) => (
        <span
          style={{
            color: status === "ACTIVE" ? "#16a34a" : "#dc2626",
            fontWeight: 600,
          }}
        >
          {status}
        </span>
      ),
    },
    {
      title: "Actions",
      key: "actions",
      onHeaderCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      onCell: () => ({
        style: {
          backgroundColor: "#fff",
          border: "1px solid #d9d9d9",
          color: "#000",
          fontWeight: "bold",
        },
      }),
      render: (_, record) => {
        const menu = (
          <Menu
            onClick={({ key }) => {
              if (key === "edit") {
                handleEditUser(record);
              } else if (key === "view") {
                handleViewUser(record);
              } else if (key === "toggle-status") {
                handleToggleStatus(record.userId, record.status);
              } else if (key === "delete") {
                showDeleteConfirm(record.userId, record.username);
              }
            }}
            items={[
              { key: "view", label: "View Details", icon: <EyeOutlined /> },
              { key: "edit", label: "Edit", icon: <EditOutlined /> },
              {
                key: "toggle-status",
                label:
                  record.status === "ACTIVE" ? "Deactivate" : "Activate",
                icon:
                  record.status === "ACTIVE" ? (
                    <DeleteOutlined />
                  ) : (
                    <EyeOutlined />
                  ),
              },
              {
                key: "delete",
                label: "Delete",
                icon: <DeleteOutlined />,
                danger: true,
              },
            ]}
          />
        );
        return (
          <Dropdown
            overlay={menu}
            trigger={["click"]}
            placement="bottomRight"
          >
            <MoreOutlined
              style={{ fontSize: 20, cursor: "pointer", color: "#000" }}
            />
          </Dropdown>
        );
      },
    },
  ];

  const handleToggleStatus = async (userId, currentStatus) => {
    try {
      const newStatus = currentStatus === "ACTIVE" ? "inactive" : "active";
      const endpoint =
        newStatus === "active"
          ? `${GlobalConst.API_URL}/api/invitations/active/${userId}`
          : `${GlobalConst.API_URL}/api/invitations/inactive/${userId}`;

      const response = await axios.put(
        endpoint,
        {},
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId,
          },
        }
      );

      if (response.data.status === 200) {
        setUsers((prev) =>
          prev.map((user) =>
            user.userId === userId
              ? { ...user, status: newStatus.toUpperCase() }
              : user
          )
        );
        successMsg(`User status updated to ${newStatus}`);
      }
    } catch (error) {
      console.error("Toggle Status Error:", error);
      errorMsg("Failed to update user status");
    }
  };

  const handleEditUser = (user) => {
    navigate("/users/edit", { state: { user } });
  };

  const handleViewUser = (user) => {
    navigate("/users/view", { state: { user } });
  };

  const handleInviteUser = () => {
    navigate("/users/invite");
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Users</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Users</h5>
        <button className="btn btn-primary btn-sm" onClick={handleInviteUser}>
          Invite User
        </button>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100">
              <Table
                columns={columns}
                dataSource={users}
                loading={loading}
                bordered
                pagination={false}
                style={{ backgroundColor: "#f5f0fcff" }}
              />
            </div>
          </div>
        </div>
      </div>
      {signingIn && <Loader />}
    </>
  );
}
