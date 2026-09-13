// src/components/context/ActionProtectedRoute.jsx
import React, { useContext } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { userContext } from "../context/ContextProvider";

const ActionProtectedRoute = ({ children, requiredActions }) => {
  const { authenticated, loading, actions, activeRole } = useContext(userContext);
  const location = useLocation();

  // 🕒 Step 1: Wait for context hydration
  if (loading) {
    return <div style={{ textAlign: "center", marginTop: "20%" }}>Authorizing...</div>;
  }

  // 🧩 Step 2: Fallback to localStorage for consistency
  const token = localStorage.getItem("token");
  const stored = (() => {
    try {
      return JSON.parse(localStorage.getItem("authState")) || {};
    } catch {
      return {};
    }
  })();

  // 🪪 Step 3: Verify authentication
  const authed = authenticated || (token && (stored.authenticated ?? true));
  if (!authed) {
    return <Navigate to="/" state={{ from: location }} replace />;
  }

  // 🧾 Step 4: Normalize actions from context + fallback
  const granted = new Set(
    (actions && actions.length ? actions : stored.actions || []).map(a => a?.toUpperCase())
  );
  const needed = (requiredActions || []).map(a => a?.toUpperCase());

  // 🧩 Step 5: If no required actions defined → allow
  if (!needed.length) return children;

  // 🧠 Step 6: Check if user has all required actions
  const ok = needed.every(a => granted.has(a));

  if (!ok) {
    console.warn(
      `⛔ Access denied for role "${activeRole || stored.activeRole}". Missing actions:`,
      needed.filter(a => !granted.has(a))
    );
    return <Navigate to="/unauthorized" state={{ from: location }} replace />;
  }

  // ✅ Step 7: All checks passed → allow
  return children;
};

export default ActionProtectedRoute;