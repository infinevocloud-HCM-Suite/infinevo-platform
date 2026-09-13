// src/components/context/ProtectedRoute.jsx
import React, { useContext } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { userContext } from "../context/ContextProvider";

const ProtectedRoute = ({ children, allowedRoles }) => {
  const { authenticated, loading, roles, activeRole } = useContext(userContext);
  const location = useLocation();

  // 🕒 Step 1: Wait for Context hydration
  if (loading) {
    return (
      <div style={{ textAlign: "center", marginTop: "20%" }}>
        Checking session...
      </div>
    );
  }

  // 🧩 Step 2: Fallback to localStorage for consistency
  const stored = (() => {
    try {
      return JSON.parse(localStorage.getItem("authState")) || {};
    } catch {
      return {};
    }
  })();

  // 🪪 Step 3: Evaluate authentication properly
  const token = localStorage.getItem("token");
  const authed = authenticated || (token && (stored.authenticated ?? true));

// 🩹 Step 3b: Prevent false redirect when context still rehydrating
if (!authed) {
  // If token exists but context hasn't hydrated yet → wait instead of redirecting
  if (localStorage.getItem("token")) {
    return (
      <div style={{ textAlign: "center", marginTop: "20%" }}>
        Restoring your session...
      </div>
    );
  }
  // Only truly unauthenticated users go to login
  return <Navigate to="/" state={{ from: location }} replace />;
}

  // 🧩 Step 4: Normalize roles and active role
  const userRoles = (roles?.length ? roles : stored.roles || []).map(r =>
    r?.toLowerCase()
  );
  const currRole = (activeRole || stored.activeRole || "").toLowerCase();

  // 🪫 Step 5: If no allowedRoles passed → allow
  if (!allowedRoles || allowedRoles.length === 0) {
    return children;
  }

  const allowed = allowedRoles.map(r => r.toLowerCase());

  // ✅ Step 6: Prefer activeRole first
  if (currRole && allowed.includes(currRole)) {
    return children;
  }

  // 🔄 Step 7: Fallback — if any role matches
  const hasAccess = userRoles.some(r => allowed.includes(r));
  if (!hasAccess) {
    return <Navigate to="/unauthorized" state={{ from: location }} replace />;
  }

  console.debug("✅ ProtectedRoute OK:", { currRole, allowedRoles, authed });

  // ✅ Step 8: Otherwise allow the route
  return children;
};

export default ProtectedRoute;