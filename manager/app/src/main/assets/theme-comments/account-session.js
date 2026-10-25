// The service URL is fixed by the app. Passwords never enter persistent storage.
export function createAccountSession(serverURL, onChange = () => {}) {
  const key = `STERSU_WALINE_USER:${serverURL}`;
  let user = null;
  let generation = 0;
  let request;
  let disposed = false;
  let restoring = false;
  let unverified = false;
  function parseUser(data) {
    if (!data || typeof data.token !== 'string' || !data.token.length || data.token.length > 16384 ||
        /[\s\u0000-\u001f\u007f]/.test(data.token) ||
        !((typeof data.objectId === 'string' && data.objectId.length > 0 && data.objectId.length <= 256) ||
          (Number.isSafeInteger(data.objectId) && data.objectId > 0))) throw new Error('invalid-session');
    return Object.fromEntries(['objectId', 'token', 'display_name', 'email', 'avatar', 'url', 'type']
      .filter(k => typeof data[k] === 'string' || typeof data[k] === 'number').map(k => [k, data[k]]));
  }
  async function api(endpoint, method = 'GET', body, token) {
    const controller = new AbortController();
    request?.abort();
    request = controller;
    const timeout = setTimeout(() => controller.abort(), 15000);
    try {
      const headers = {};
      if (body) headers['Content-Type'] = 'application/json';
      if (token) headers.Authorization = `Bearer ${token}`;
      const response = await fetch(`${serverURL}/api/${endpoint}`, {
        method, headers, body: body ? JSON.stringify(body) : undefined, signal: controller.signal,
        credentials: 'omit', redirect: 'error', cache: 'no-store', referrerPolicy: 'no-referrer',
      });
      const result = await response.json();
      if (!response.ok || result?.errno !== 0) {
        const error = new Error('request-failed');
        error.rejected = response.status === 401 || response.status === 403 || result?.errno === 401 || result?.errno === 403 ||
          /jwt|token.{0,20}(invalid|expired)|not logged|unauthorized/i.test(String(result?.errmsg || ''));
        throw error;
      }
      return result.data;
    } finally {
      clearTimeout(timeout);
      if (request === controller) request = null;
    }
  }
  function save(data) {
    const next = parseUser(data);
    try { localStorage.setItem(key, JSON.stringify(next)); }
    catch { throw new Error('storage-unavailable'); }
    user = next;
    unverified = false;
    onChange(user);
    return user;
  }
  function clearMeta(previous) {
    try {
      const meta = JSON.parse(localStorage.getItem('WALINE_USER_META') || '{}');
      if (previous?.email && meta.mail === previous.email) {
        const value = JSON.stringify({ nick: '', mail: '', link: '' });
        localStorage.setItem('WALINE_USER_META', value);
        window.dispatchEvent(new StorageEvent('storage', { key: 'WALINE_USER_META', newValue: value, storageArea: localStorage }));
      }
    } catch { /* A cleared session must not become authenticated again. */ }
  }
  function clear() {
    localStorage.removeItem(key);
    clearMeta(user);
    user = null;
    unverified = false;
    onChange(null);
  }
  return {
    get user() { return user; },
    get restoring() { return restoring; },
    get unverified() { return unverified; },
    async restore() {
      if (disposed || restoring) return;
      const current = ++generation;
      restoring = true;
      try {
        const saved = JSON.parse(localStorage.getItem(key) || 'null');
        if (!saved) { user = null; unverified = false; onChange(null); return; }
        unverified = true;
        const candidate = parseUser(saved);
        const data = await api('token', 'GET', undefined, candidate.token);
        if (!disposed && current === generation) save({ ...data, token: candidate.token });
      } catch (error) {
        if (!disposed && current === generation) {
          if (error.rejected || error.message === 'invalid-session' || error instanceof SyntaxError) {
            clear();
            error.rejected = true;
          }
          throw error;
        }
      } finally { if (current === generation) restoring = false; }
    },
    async signIn(email, password, code) {
      const current = ++generation;
      restoring = false;
      const data = await api('token', 'POST', { email, password, ...(code ? { code } : {}) });
      const candidate = parseUser(data);
      const verified = await api('token', 'GET', undefined, candidate.token);
      if (!disposed && current === generation) return save({ ...verified, token: candidate.token });
    },
    register: (email, password, displayName) => api('user', 'POST', { email, password, display_name: displayName }),
    async updateProfile(displayName, avatar) {
      const previous = user;
      if (!previous) throw new Error('invalid-session');
      const current = ++generation;
      restoring = false;
      await api('user', 'PUT', { display_name: displayName, ...(avatar ? { avatar } : {}) }, previous.token);
      const data = await api('token', 'GET', undefined, previous.token);
      if (data?.display_name !== displayName) throw new Error('profile-not-saved');
      if (!disposed && current === generation) return save({ ...data, token: previous.token });
    },
    signOut() { generation++; restoring = false; request?.abort(); clear(); },
    destroy() { disposed = true; generation++; request?.abort(); user = null; },
  };
}
