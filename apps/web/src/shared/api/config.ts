// One boundary for public API configuration. No component owns a backend URL.
export const apiConfig = Object.freeze({
  baseUrl: (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, ''),
});
