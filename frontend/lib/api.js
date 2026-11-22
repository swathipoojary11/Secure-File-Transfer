/**
 * Centralized REST API Client for Spring Boot Backend.
 * 
 * Networking Viva Explanation:
 * Handles HTTP client communication with the Spring Boot REST API:
 * - Dynamically resolves backend IP based on the accessing machine (local or peer LAN IP).
 * - Appends RFC 7519 Bearer token in the Authorization header.
 * - Inspects token expiry to auto-refresh stale tokens.
 * - Automatically recovers from 401 Unauthorized errors by refreshing session tokens.
 */

export function getBaseUrl() {
  if (typeof window !== 'undefined') {
    // Relative paths automatically proxy via Next.js rewrites to local backend
    return '';
  }
  return process.env.NEXT_PUBLIC_BACKEND_URL || 'http://127.0.0.1:8085';
}

const BASE_URL = '';

export async function getAuthToken() {
  if (typeof window === 'undefined') return null;

  let token = localStorage.getItem('dcn_jwt_token');

  // Verify token is not expired
  if (token && token.trim().length > 10) {
    try {
      const parts = token.split('.');
      if (parts.length === 3) {
        const payload = JSON.parse(atob(parts[1]));
        // If expired or expiring within 30 seconds, discard
        if (payload.exp && payload.exp * 1000 < Date.now() + 30000) {
          localStorage.removeItem('dcn_jwt_token');
          token = null;
        } else {
          return token;
        }
      }
    } catch (e) {
      localStorage.removeItem('dcn_jwt_token');
      token = null;
    }
  }

  // Fetch fresh demo token from active backend
  const baseUrl = getBaseUrl();
  try {
    const res = await fetch(`${baseUrl}/api/auth/demo-token`);
    if (res.ok) {
      const data = await res.json();
      if (data?.token) {
        localStorage.setItem('dcn_jwt_token', data.token);
        localStorage.setItem('dcn_user_email', data.email || 'operator@dcn.local');
        return data.token;
      }
    }
  } catch (err) {
    console.warn('[API CLIENT] Could not acquire token from', baseUrl, err.message);
  }

  return null;
}

export async function apiFetch(endpoint, options = {}) {
  const baseUrl = getBaseUrl();
  const token = await getAuthToken();
  const headers = {
    ...options.headers,
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const url = endpoint.startsWith('http') ? endpoint : `${baseUrl}${endpoint}`;

  let response = await fetch(url, {
    ...options,
    headers,
  });

  // If 401 Unauthorized, automatically clear stale token, refresh, and retry once
  if (response.status === 401 && typeof window !== 'undefined') {
    localStorage.removeItem('dcn_jwt_token');
    const freshToken = await getAuthToken();
    if (freshToken) {
      headers['Authorization'] = `Bearer ${freshToken}`;
      response = await fetch(url, {
        ...options,
        headers,
      });
    }
  }

  return response;
}

export async function apiGet(endpoint) {
  const res = await apiFetch(endpoint, { method: 'GET' });
  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`API Error ${res.status}: ${errText || res.statusText}`);
  }
  return res.json();
}

export async function apiPost(endpoint, body) {
  const isFormData = body instanceof FormData;
  const res = await apiFetch(endpoint, {
    method: 'POST',
    body: isFormData ? body : JSON.stringify(body),
  });

  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`API Error ${res.status}: ${errText || res.statusText}`);
  }
  return res.json();
}

export async function apiDelete(endpoint) {
  const res = await apiFetch(endpoint, { method: 'DELETE' });
  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(`API Error ${res.status}: ${errText || res.statusText}`);
  }
  return res.json();
}

export { BASE_URL };
