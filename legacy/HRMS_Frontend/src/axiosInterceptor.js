// src/axiosInterceptor.js
import axios from "axios";


// These URLs must NEVER carry a JWT
const NO_AUTH_URLS = [
  "/auth/login",
  "/auth/forgot-password",
  "/auth/reset-password",
];

axios.interceptors.request.use(
  (config) => {
    const url = config.url || "";

    // If login / forgot-password / reset-password → remove token
    if (NO_AUTH_URLS.some((publicUrl) => url.includes(publicUrl))) {
      // Ignore per-request Authorization header if exists
      if (config.headers?.Authorization) {
        config.headers.Authorization = undefined;
      }

      return config;
    }


    return config;
  },
  (error) => Promise.reject(error)
);
