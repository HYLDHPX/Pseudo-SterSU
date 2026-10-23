const params = new URLSearchParams(location.search);
const chinese = (params.get('lang') || navigator.language).toLowerCase().startsWith('zh');
const copy = chinese ? {
  title: '\u8bc4\u8bba\u533a',
  loading: '\u6b63\u5728\u8fde\u63a5\u8bc4\u8bba\u670d\u52a1\u2026',
  failed: '\u8bc4\u8bba\u670d\u52a1\u6682\u65f6\u65e0\u6cd5\u8fde\u63a5\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002',
  retry: '\u91cd\u8bd5',
  placeholder: '\u5206\u4eab\u4f7f\u7528\u53cd\u9988\uff0c\u8bf7\u52ff\u53d1\u5e03\u5bc6\u7801\u6216\u9690\u79c1\u65e5\u5fd7\u3002\u8bc4\u8bba\u53ef\u80fd\u9700\u8981\u5ba1\u6838\u3002',
  guidance: '\u6635\u79f0\u4e0d\u4ee3\u8868\u5df2\u9a8c\u8bc1\u7684 GitHub \u8eab\u4efd\u3002\u8bf7\u6587\u660e\u4ea4\u6d41\uff0c\u4e0d\u8981\u5206\u4eab\u4e2a\u4eba\u9690\u79c1\u3002',
} : {
  title: 'Comments', loading: 'Connecting to the comment service...',
  failed: 'The comment service could not be reached. Please try again later.', retry: 'Retry',
  placeholder: 'Share feedback. Do not post passwords or private logs. Comments may require moderation.',
  guidance: 'Nicknames are not verified GitHub identities. Keep discussions respectful and protect your privacy.',
};
document.documentElement.lang = chinese ? 'zh-CN' : 'en';
document.title = copy.title;
document.querySelector('#guidance').textContent = copy.guidance;
const connection = document.querySelector('#connection');
const message = document.querySelector('#connection-message');
const retry = document.querySelector('#retry');
const container = document.querySelector('#waline');
retry.textContent = copy.retry;
let dark = params.get('dark') === 'true';
let instance;
let pending = false;
let disposed = false;
let controller;
let commentPath;
document.documentElement.dataset.theme = dark ? 'dark' : 'light';

// JavaScript-to-native bridges are deliberately absent. Only appearance can be updated by the host.
window.stersuComments = {
  setDarkMode(next) {
    if (typeof next !== 'boolean' || next === dark) return;
    dark = next;
    document.documentElement.dataset.theme = next ? 'dark' : 'light';
    // Waline resets the thread to location.pathname if path is omitted from update().
    instance?.update({ dark: next, path: commentPath });
  },
};

async function mountComments() {
  if (pending || disposed) return;
  pending = true;
  connection.hidden = false;
  retry.hidden = true;
  message.textContent = copy.loading;
  let timeout;
  try {
    const server = new URL(params.get('server'));
    if (server.protocol !== 'https:' || server.username || server.password || server.search || server.hash)
      throw new Error('Invalid comment server');
    commentPath = params.get('path');
    if (!/^\/stersu\/(theme|style)\/[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}$/.test(commentPath || ''))
      throw new Error('Invalid comment thread');
    const serverURL = server.href.replace(/\/+$/, '');
    controller = new AbortController();
    timeout = setTimeout(() => controller.abort(), 15000);
    const health = new URL(`${serverURL}/api/comment`);
    health.search = new URLSearchParams({ type: 'count', url: commentPath }).toString();
    const response = await fetch(health, { signal: controller.signal, credentials: 'omit' });
    if (!response.ok) throw new Error('Comment service unavailable');
    const result = await response.json();
    if (!result || result.errno !== 0 || !Object.prototype.hasOwnProperty.call(result, 'data'))
      throw new Error('Invalid Waline response');
    clearTimeout(timeout);
    const { init } = await import('./vendor/waline.js');
    if (disposed) return;
    container.hidden = false;
    instance = init({
      el: container, serverURL, path: commentPath, lang: chinese ? 'zh-CN' : 'en', dark,
      login: 'disable', meta: ['nick', 'mail'], requiredMeta: ['nick'],
      pageSize: 10, commentSorting: 'latest', wordLimit: 1000,
      emoji: false, search: false, imageUploader: false, texRenderer: false,
      highlighter: false, reaction: false, pageview: false, comment: false, noRss: true,
      locale: { placeholder: copy.placeholder },
    });
    connection.hidden = true;
  } catch {
    if (!disposed) {
      message.textContent = copy.failed;
      retry.hidden = false;
    }
  } finally {
    clearTimeout(timeout);
    pending = false;
  }
}

retry.addEventListener('click', mountComments);
window.addEventListener('pagehide', () => {
  disposed = true;
  controller?.abort();
  instance?.destroy();
});
mountComments();
