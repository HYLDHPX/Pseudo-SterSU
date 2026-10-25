// Observe the vendor's real read instead of making a second health-check request.
// No caching of comment bodies or account tokens; writes and login are untouched.
export function observeCommentReads(serverURL, state, timeoutMs = 15000) {
  const original = window.fetch;
  const endpoint = `${serverURL}/api/comment`;
  const active = new Set();
  let generation = 0;
  let disposed = false;
  const wrapped = async (input, options = {}) => {
    const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url, location.href);
    const method = options.method || input?.method || 'GET';
    if (url.origin + url.pathname !== endpoint || method.toUpperCase() !== 'GET')
      return original.call(window, input, options);
    const current = ++generation;
    const controller = new AbortController();
    const signal = options.signal || input?.signal;
    const abort = () => controller.abort();
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) abort();
    active.add(controller);
    const timeout = setTimeout(abort, timeoutMs);
    state('loading');
    try {
      const response = await original.call(window, input, { ...options, signal: controller.signal });
      const result = await response.clone().json();
      if (!response.ok || result?.errno !== 0 || !Array.isArray(result?.data?.data))
        throw new Error('Invalid comment response');
      if (!disposed && current === generation) state('ready');
      return response;
    } catch (error) {
      if (!disposed && current === generation && !signal?.aborted) state('failed');
      throw error;
    } finally {
      clearTimeout(timeout);
      signal?.removeEventListener('abort', abort);
      active.delete(controller);
    }
  };
  window.fetch = wrapped;
  return () => {
    disposed = true;
    active.forEach(controller => controller.abort());
    if (window.fetch === wrapped) window.fetch = original;
  };
}
