import React, { useState, useEffect } from "react";
import { Helmet } from "react-helmet-async";
import { Table, Dropdown, Menu, Modal } from "antd";
import {
  MoreOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  ExclamationCircleOutlined
} from "@ant-design/icons";
import { useNavigate } from "react-router-dom";
import { successMsg, errorMsg } from "../../../../shared/helpers/msgHelper";
import axios from "axios";
import { GlobalConst } from "../../../../shared/appConfig/globalConst";

const { confirm } = Modal;

const Roles = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [isModalVisible, setIsModalVisible] = useState(false);
  const [editingRole, setEditingRole] = useState(null);
  const [roles, setRoles] = useState([]);
  const [deleteRoleId, setDeleteRoleId] = useState(null);

  // Get organizationId from localStorage
  const organizationId = localStorage.getItem("organizationId");

  // Fetch all roles
  useEffect(() => {
    fetchRoles();
  }, []);

  const fetchRoles = async () => {
    try {
      setLoading(true);
      const response = await axios.get(`${GlobalConst.API_URL}/api/roles`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("__t")}`,
          organizationId: organizationId
        },
      });

      if (response.data && response.data.data) {
        const rolesData = response.data.data.map((role, index) => ({
          key: role.roleId || index.toString(),
          roleId: role.roleId,
          roleName: role.roleName,
          description: role.roleDescription,
          status: role.status,
          accessType: role.accessType,
          userActionRequired: role.userActionRequired,
          isDefault: role.isDefault
        }));
        setRoles(rolesData);
      }
    } catch (error) {
      console.error("API Error:", error);
      if (error.response) {
        errorMsg("Error", error.response.data?.message || "Failed to load roles", true);
      } else if (error.request) {
        errorMsg("Network Error", "Cannot connect to the server. Please check your connection.", true);
      } else {
        errorMsg("Error", "An unexpected error occurred", true);
      }
    } finally {
      setLoading(false);
    }
  };

  // Show delete confirmation modal
  const showDeleteConfirm = (roleId, roleName) => {
    confirm({
      title: 'Are you sure you want to delete this role permanently?',
      icon: <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />,
      content: `Role: ${roleName}`,
      okText: 'Yes, Delete',
      okType: 'danger',
      cancelText: 'No, Cancel',
      centered: true,
      maskClosable: true,
      onOk() {
        handleDeleteRole(roleId);
      },
      onCancel() {
        console.log('Delete cancelled');
      },
    });
  };

  const handleDeleteRole = async (roleId) => {
    try {
      const response = await axios.delete(
        `${GlobalConst.API_URL}/api/roles/${roleId}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data.status === 200) {
        setRoles(prev => prev.filter(role => role.roleId !== roleId));
        successMsg("Role deleted successfully");
      }
    } catch (error) {
      console.error("Delete Error:", error);
      errorMsg("Failed to delete role");
    }
  };

  // Table columns
  const columns = [
    {
      title: "ROLE NAME",
      dataIndex: "roleName",
      key: "roleName",
      render: (roleName) => (
        <div style={{ fontWeight: 600, color: '#1677ff' }}>
          {roleName}
        </div>
      ),
    },
    {
      title: "DESCRIPTION",
      dataIndex: "description",
      key: "description",
      render: (description) => (
        <div style={{ color: '#666', lineHeight: '1.4' }}>
          {description}
        </div>
      ),
    },
    {
      title: "STATUS",
      dataIndex: "status",
      key: "status",
      render: (status) => (
        <div style={{
          color: status === "active" ? '#52c41a' : '#ff4d4f',
          fontWeight: 600
        }}>
          {status?.toUpperCase()}
        </div>
      ),
    },
    {
      title: "ACTIONS",
      key: "actions",
      render: (_, record) => {
        const menu = (
          <Menu
            onClick={({ key }) => {
              if (key === "edit") {
                handleEditRole(record);
              } else if (key === "view") {
                handleViewRole(record);
              } else if (key === "toggle-status") {
                handleToggleStatus(record.roleId, record.status);
              } else if (key === "delete") {
                showDeleteConfirm(record.roleId, record.roleName);
              }
            }}
            items={[
              { key: "view", label: "View Details", icon: <EyeOutlined /> },
              { key: "edit", label: "Edit", icon: <EditOutlined /> },
              {
                key: "toggle-status",
                label: record.status === "active" ? "Deactivate" : "Activate",
                icon: record.status === "active" ? <DeleteOutlined /> : <PlusOutlined />,
              },
              {
                key: "delete",
                label: "Delete",
                icon: <DeleteOutlined />,
                danger: true
              },
            ]}
          />
        );
        return (
          <Dropdown overlay={menu} trigger={["click"]} placement="bottomRight">
            <MoreOutlined style={{ fontSize: 18, cursor: "pointer", color: '#000' }} />
          </Dropdown>
        );
      },
    }
  ];

  const handleToggleStatus = async (roleId, currentStatus) => {
    try {
      const newStatus = currentStatus === "active" ? "inactive" : "active";

      const response = await axios.put(
        `${GlobalConst.API_URL}/api/roles/${roleId}`,
        { status: newStatus },
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("__t")}`,
            organizationId: organizationId
          },
        }
      );

      if (response.data.status === 200) {
        setRoles(prev => prev.map(role =>
          role.roleId === roleId ? { ...role, status: newStatus } : role
        ));
        successMsg(`Role status updated to ${newStatus}`);
      }
    } catch (error) {
      console.error("Toggle Status Error:", error);
      errorMsg("Failed to update role status");
    }
  };

  const handleEditRole = (role) => {
    navigate(`/roles/edit/${role.roleId}`, { state: { roleId: role.roleId } });
  };

  const handleViewRole = (role) => {
    navigate('/roles/view', { state: { roleId: role.roleId } });
  };

  const handleCreateRole = () => {
    navigate('/roles/new');
  };

  return (
    <>
      <Helmet>
        <title>HRMS InfiNevoCloud - Roles</title>
      </Helmet>

      <div className="w-100 bg-white px-5 py-4 d-flex justify-content-between align-items-center border-bottom">
        <h5 className="mb-0 fw-semibold">Roles</h5>
        <button
          className="btn btn-primary btn-sm"
          onClick={handleCreateRole}
        >
          <PlusOutlined /> Create Role
        </button>
      </div>

      <div className="d-flex flex-column flex-lg-row flex-column-fluid">
        <div className="d-flex flex-column flex-lg-row-fluid py-2">
          <div className="container-fluid min-vh-100 d-flex align-items-start justify-content-start p-10 bg-white">
            <div className="w-100">
              <Table
                columns={columns}
                dataSource={roles}
                loading={loading}
                bordered
                pagination={false}
                scroll={{ x: true }}
                style={{ backgroundColor: '#f5f0fcff' }}
                responsive
              />
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default Roles;