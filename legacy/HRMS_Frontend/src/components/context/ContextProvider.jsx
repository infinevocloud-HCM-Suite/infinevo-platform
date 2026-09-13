// src/components/context/ContextProvider.js
import React, { createContext, useState, useEffect } from "react";

export const userContext = createContext();

export const ContextProvider = ({ children }) => {
  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);

  const [roles, setRoles] = useState([]);
  const [activeRole, setActiveRole] = useState(null);
  const [actions, setActions] = useState([]);

useEffect(() => {
  const restoreAuth = async () => {
    try {
      const token = localStorage.getItem("token");
      const stored = JSON.parse(localStorage.getItem("authState") || "{}");

      const initAuthed = !!token && (stored.authenticated ?? true);
      const initRoles = (stored.roles || JSON.parse(localStorage.getItem("roles") || "[]")).map(r => r?.toLowerCase());
      const initActiveRole = (stored.activeRole || localStorage.getItem("activeRole") || null)?.toLowerCase();
      const initActions = stored.actions || JSON.parse(localStorage.getItem("actions") || "[]");

      // ✅ update all before ending loading
      setAuthenticated(initAuthed);
      setRoles(initRoles);
      setActiveRole(initActiveRole);
      setActions(initActions);
    } catch (e) {
      console.error("Auth bootstrap error:", e);
    } finally {
      // ⏳ Add small defer so context state is guaranteed applied before guards
      setTimeout(() => setLoading(false), 150);
    }
  };

  restoreAuth();
}, []);


const updateAuthState = (updates = {}) => {
  const normalizedRoles = (updates.roles ?? roles)?.map(r => r.toLowerCase());
  const normalizedActiveRole = updates.activeRole?.toLowerCase() ?? activeRole;

  setRoles(normalizedRoles);
  setActiveRole(normalizedActiveRole);
  setActions(updates.actions ?? actions);
  setAuthenticated(updates.authenticated ?? authenticated);

  localStorage.setItem("authState", JSON.stringify({
    roles: normalizedRoles,
    activeRole: normalizedActiveRole,
    actions: updates.actions ?? actions,
    authenticated: updates.authenticated ?? authenticated,
  }));
};

  const logout = () => {
    setAuthenticated(false);
    setRoles([]);
    setActiveRole(null);
    setActions([]);
    localStorage.removeItem("token");
    localStorage.removeItem("authState");
    localStorage.removeItem("roles");
    localStorage.removeItem("activeRole");
    localStorage.removeItem("actions");
  };

  return (
    <userContext.Provider
      value={{
        authenticated,
        loading,
        roles,
        activeRole,
        actions,
        updateAuthState,
        logout,
      }}
    >
      {children}
    </userContext.Provider>
  );
};

export default ContextProvider;