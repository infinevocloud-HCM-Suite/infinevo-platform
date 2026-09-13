// src/shared/helpers/tokenHelper.js
import { jwtDecode } from "jwt-decode";

/**
 * Extract username (preferred_username) from JWT stored in localStorage
 */
export const getUserInfoFromToken = () => {
  const token = localStorage.getItem("__t");
  if (!token) return null;

  try {
    const decoded = jwtDecode(token);

    return {
      username: decoded.preferred_username || null,
      name: decoded.name || null,
      email: decoded.email || null,
      sub: decoded.sub || null, // unique user id
    };
  } catch (error) {
    console.error("Failed to decode token:", error);
    return null;
  }
};

/**
 * Generic function to get full decoded token
 */
export const getDecodedToken = () => {
  const token = localStorage.getItem("__t");
  if (!token) return null;

  try {
    return jwtDecode(token);
  } catch (error) {
    console.error("Failed to decode token:", error);
    return null;
  }
};
