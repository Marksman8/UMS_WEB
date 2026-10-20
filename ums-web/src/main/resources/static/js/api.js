// HTTP client: JSON in/out, CSRF header from the XSRF-TOKEN cookie, one error type for every failure.

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function csrfToken() {
  const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return m ? decodeURIComponent(m[1]) : '';
}

async function request(method, path, body, { text = false } = {}) {
  const headers = { Accept: 'application/json' };
  const init = { method, headers, credentials: 'same-origin' };
  if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken();
  if (body !== undefined) {
    headers['Content-Type'] = text ? 'text/plain' : 'application/json';
    init.body = text ? body : JSON.stringify(body);
  }
  let res;
  try {
    res = await fetch(path, init);
  } catch (e) {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Is it running?');
  }
  if (res.status === 204) return null;
  let data = null;
  const type = res.headers.get('content-type') || '';
  if (type.includes('json')) data = await res.json().catch(() => null);
  if (!res.ok) {
    const err = new ApiError(res.status, data?.code || 'ERROR', data?.message || `Request failed (${res.status})`);
    if (res.status === 401 && !path.startsWith('/api/auth/')) window.dispatchEvent(new CustomEvent('ums:signed-out'));
    throw err;
  }
  return data;
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body = {}) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  del: (path) => request('DELETE', path),
  postText: (path, text) => request('POST', path, text, { text: true }),
};

export const qs = (params) => {
  const p = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '') p.set(k, v); });
  const s = p.toString();
  return s ? `?${s}` : '';
};
