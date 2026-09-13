// src/shared/services/authService.js
export const registerUser = async (userData) => {
  // Implement your registration API call here
  try {
    const response = await fetch('/api/register', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(userData),
    });
    return await response.json();
  } catch (error) {
    throw error;
  }
};