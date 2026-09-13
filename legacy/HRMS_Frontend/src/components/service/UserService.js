import axios from "axios";
import API_BASE_URL from "../config/apiConfig";

class UserService {

    static async login(email, password) {
  try {
    const response = await axios.post(
      `${API_BASE_URL}/auth/login`,
      { email, password },
      {
        headers: { "Content-Type": "application/json" },
        withCredentials: true,
      }
    );

    console.log("Login API response:", response.data);

if (response.data.token) {
  // ✅ Always save token
  localStorage.setItem("token", response.data.token);

  // Normalize roles
  const roles = (response.data.roles || []).map(r => r.toLowerCase());

  // Active role (backend may not send on first login, only after switch-role)
  const activeRole = response.data.activeRole ? response.data.activeRole.toLowerCase() : null;

  // Actions (may be empty initially)
  const actions = response.data.actions || [];

  // ✅ Save unified authState snapshot
  const authState = {
    roles,
    activeRole,
    actions,
    authenticated: true,
  };

  localStorage.setItem("authState", JSON.stringify(authState));

  // Also keep legacy keys if your app still checks them
  localStorage.setItem("roles", JSON.stringify(roles));
  if (activeRole) localStorage.setItem("activeRole", activeRole);
} else {
  console.warn("⚠️ No token found in login response");
}


    return response.data;
  } catch (err) {
    console.error("Login error:", err);
    throw err.response?.data || { message: "Login failed" };
  }
}



static async register(userData, token) {
  try {
    const verifyResponse = await axios.get(
      `${API_BASE_URL}/employees/by-emp-id/${userData.empId}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        withCredentials: true,
      }
    );

    if (verifyResponse.data.status !== 'success') {
      throw new Error(verifyResponse.data.message || 'Employee verification failed');
    }

    const response = await axios.post(
      `${API_BASE_URL}/register`,
      userData,
      {
        headers: { Authorization: `Bearer ${token}` },
        withCredentials: true,
      }
    );

    // RETURN A SMALL, PREDICTABLE OBJECT (avoid huge response.data)
    return {
      status: response.status, // 200
      message: response.data?.message,
      userId: response.data?.ourUsers?.id ?? null,
      // optionally include any other tiny field you need
    };
  } catch (err) {
    console.error("Registration error:", err.response?.data || err.message);
    // rethrow a minimal error payload (so frontend can read message)
    throw err.response?.data || { message: err.message || "Registration failed" };
  }
}

    static async toggleUserAccess(userId, isNonBlocked, token) {
        try {
            const response = await axios.put(
                `${API_BASE_URL}/employees/${userId}/access`,
                {},
                {
                    params: { isNonBlocked },
                    headers: {
                        'Authorization': `Bearer ${token}`,
                        'Content-Type': 'application/json'
                    }
                }
            );
            return response.data;
        } catch (error) {
            throw error;
        }
    }

    static async getCompleteProfile(token) {
        try {
            const response = await axios.get(`${API_BASE_URL}/get-complete-profile`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                withCredentials: true,
            });
            return response.data;
        } catch (err) {
            console.error("Complete profile error:", err.response?.data);
            throw err.response?.data || { message: "Failed to get complete profile" };
        }
    }

    static async getAllUsers(token) {
        try {
            const response = await axios.get(`${API_BASE_URL}/get-all-users`, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                withCredentials: true,
            });
            return response.data;
        } catch (err) {
            console.error("Error details:", err.response?.data);
            throw err;
        }
    }

    static async getYourProfile(token) {
        try {
            const response = await axios.get(`${API_BASE_URL}/get-profile`, {
                headers: { Authorization: `Bearer ${token}` },
                withCredentials: true,
            });
            return response.data;
        } catch (err) {
            throw err;
        }
    }

    static async getUserById(userId, token) {
        try {
            const response = await axios.get(`${API_BASE_URL}/get-user/${userId}`, {
                headers: { Authorization: `Bearer ${token}` },
                withCredentials: true,
            });
            return response.data;
        } catch (err) {
            throw err;
        }
    }

    static async deleteUser(userId, token) {
        try {
            const response = await axios.delete(`${API_BASE_URL}/deleteUser/${userId}`, {
                headers: { Authorization: `Bearer ${token}` },
                withCredentials: true,
            });
            return response.data;
        } catch (err) {
            throw err;
        }
    }

    static async updateUser(userId, userData, token) {
        try {
            const response = await axios.put(`${API_BASE_URL}/update/${userId}`, userData, {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json'
                },
                withCredentials: true,
            });
            return response.data;
        } catch (err) {
            console.error("Update error:", err.response?.data);
            throw err.response?.data || { message: "Update failed" };
        }
    }

static async logout() {
  try {
    const token = localStorage.getItem("token");
    if (token) {
      await axios.post(`${API_BASE_URL}/auth/logout`, {}, {
        headers: {
          Authorization: `Bearer ${token}`,
          "Content-Type": "application/json",
        },
        withCredentials: true,
      });
    }
  } catch (err) {
    console.error("Logout failed:", err);
    // we still clear client-side state even if backend call fails
  } finally {
    // ✅ Clear only auth-related keys
    localStorage.removeItem("token");
    localStorage.removeItem("roles");
    localStorage.removeItem("activeRole");
    localStorage.removeItem("actions");
    localStorage.removeItem("authState");

    sessionStorage.clear(); // optional, if you stored temporary auth state
  }

  return true;
}

    static isAuthenticated() {
        const token = localStorage.getItem("token");
        return !!token;
    }

    // ✅ Role checks based on activeRole
    static isAdmin() {
        const activeRole = localStorage.getItem("activeRole");
        return activeRole?.toLowerCase() === "admin";
    }

    static isUser() {
        const activeRole = localStorage.getItem("activeRole");
        return activeRole?.toLowerCase() === "user";
    }

    static isHR() {
        const activeRole = localStorage.getItem("activeRole");
        return activeRole?.toLowerCase() === "hr";
    }

    static isManager() {
        const activeRole = localStorage.getItem("activeRole");
        return activeRole?.toLowerCase() === "manager";
    }

    static isSupervisor() {
        const activeRole = localStorage.getItem("activeRole");
        return activeRole?.toLowerCase() === "supervisor";
    }

    static adminOnly() {
        return this.isAuthenticated() && this.isAdmin();
    }

    static hrOnly() {
        return this.isAuthenticated() && this.isHR();
    }

    static managerOnly() {
        return this.isAuthenticated() && this.isManager();
    }

    static supervisorOnly() {
        return this.isAuthenticated() && this.isSupervisor();
    }
}

export default UserService;
