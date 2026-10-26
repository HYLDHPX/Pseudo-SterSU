import { createCommentAccount } from './auth.js';
import { observeCommentReads } from './network.js';

const params = new URLSearchParams(location.search);
const accountMode = params.get('mode') === 'account';
const chinese = (params.get('lang') || navigator.language).toLowerCase().startsWith('zh');
const copy = chinese ? {
  title: '\u8bc4\u8bba\u533a',
  loading: '\u6b63\u5728\u8fde\u63a5\u8bc4\u8bba\u670d\u52a1\u2026',
  failed: '\u8bc4\u8bba\u670d\u52a1\u6682\u65f6\u65e0\u6cd5\u8fde\u63a5\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002',
  retry: '\u91cd\u8bd5',
  optional: '\u53ef\u9009\uff0c\u4e0d\u4f1a\u516c\u5f00',
  placeholder: '\u5206\u4eab\u4f7f\u7528\u53cd\u9988\uff0c\u8bf7\u52ff\u53d1\u5e03\u5bc6\u7801\u6216\u9690\u79c1\u65e5\u5fd7\u3002\u8bc4\u8bba\u53ef\u80fd\u9700\u8981\u5ba1\u6838\u3002',
  guidance: '\u6635\u79f0\u4e0d\u4ee3\u8868\u5df2\u9a8c\u8bc1\u7684 GitHub \u8eab\u4efd\u3002\u8bf7\u6587\u660e\u4ea4\u6d41\uff0c\u4e0d\u8981\u5206\u4eab\u4e2a\u4eba\u9690\u79c1\u3002',
} : {
  title: 'Comments', loading: 'Connecting to the comment service...',
  failed: 'The comment service could not be reached. Please try again later.', retry: 'Retry',
  optional: 'optional, not public',
  placeholder: 'Share feedback. Do not post passwords or private logs. Comments may require moderation.',
  guidance: 'Nicknames are not verified GitHub identities. Keep discussions respectful and protect your privacy.',
};
document.documentElement.lang = chinese ? 'zh-CN' : 'en';
document.title = accountMode ? (chinese ? '\u8bc4\u8bba\u8d26\u53f7' : 'Comment account') : copy.title;
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
let stopObserving;
let commentPath;
let account;
let fontVersion;
let customFont;
const resizeEditor = () => document.querySelectorAll('.wl-editor')
  .forEach(editor => editor.dispatchEvent(new Event('autosize:update')));
document.documentElement.dataset.theme = dark ? 'dark' : 'light';

// Native-to-page calls update appearance or hand off the active browser login result.
// The local action/status channel has no root operations; tokens require service verification.
window.stersuComments = {
  restoreAccount() { void account?.restore(); },
  setAppearance(appearance) {
    if (!appearance || typeof appearance !== 'object') return;
    this.setDarkMode(appearance.dark);
    const root = document.documentElement;
    const previousFont = root.style.getPropertyValue('--comments-font-scale') + root.style.getPropertyValue('--comments-font-family');
    const colors = {
      background: '--comments-background', surface: '--waline-bg-color',
      surfaceVariant: '--waline-bg-color-light', surfaceHover: '--waline-bg-color-hover',
      foreground: '--comments-ink', muted: '--comments-muted',
      primary: '--waline-theme-color', onPrimary: '--comments-on-primary',
      outline: '--waline-border-color', error: '--comments-error',
    };
    for (const [key, variable] of Object.entries(colors)) {
      // Accept only literal host colors, never CSS URLs or arbitrary declarations.
      if (/^#[0-9a-f]{6}([0-9a-f]{2})?$/i.test(appearance[key] || ''))
        root.style.setProperty(variable, appearance[key]);
    }
    for (const [key, variable, min, max] of [
      ['fontScale', '--comments-font-scale', .5, 3],
      ['textOpacity', '--comments-text-opacity', 0, 1],
      ['iconOpacity', '--comments-icon-opacity', .2, 1],
      ['imageOpacity', '--comments-image-opacity', 0, 1],
    ]) {
      const value = appearance[key];
      if (typeof value === 'number' && Number.isFinite(value))
        root.style.setProperty(variable, String(Math.max(min, Math.min(max, value))));
    }
    const fonts = ['system-ui', 'sans-serif', 'serif', 'monospace', 'cursive', 'SterSUCustom'];
    if (fonts.includes(appearance.fontFamily))
      root.style.setProperty('--comments-font-family', `${appearance.fontFamily}, sans-serif`);
    if (appearance.fontFamily === 'SterSUCustom' && /^[0-9a-f]{64}$/.test(appearance.fontVersion || '') &&
        appearance.fontVersion !== fontVersion) {
      fontVersion = appearance.fontVersion;
      const version = fontVersion;
      const font = new FontFace('SterSUCustom', `url('./font.ttf?v=${version}')`);
      font.load().then(loaded => {
        if (version === fontVersion) {
          if (customFont) document.fonts.delete(customFont);
          document.fonts.add(loaded);
          customFont = loaded;
          resizeEditor();
        }
      }).catch(() => {
        if (version === fontVersion) fontVersion = undefined;
        // The platform font remains available if the custom file is unavailable.
      });
    }
    if (previousFont !== root.style.getPropertyValue('--comments-font-scale') + root.style.getPropertyValue('--comments-font-family'))
      resizeEditor();
  },
  setDarkMode(next) {
    if (typeof next !== 'boolean' || next === dark) return;
    dark = next;
    document.documentElement.dataset.theme = next ? 'dark' : 'light';
    // Waline resets the thread to location.pathname if path is omitted from update().
    instance?.update({ dark: next, path: commentPath });
  },
};
try { window.stersuComments.setAppearance(JSON.parse(params.get('appearance') || 'null')); } catch { /* Default palette. */ }

async function mountComments() {
  if (pending || disposed) return;
  if (instance) {
    // Use Waline's retry so the editor's draft and reply target remain mounted.
    container.querySelector('.wl-operation .wl-btn')?.click();
    return;
  }
  pending = true;
  connection.hidden = false;
  retry.hidden = true;
  message.textContent = copy.loading;
  try {
    const server = new URL(params.get('server'));
    if (server.protocol !== 'https:' || server.username || server.password || server.search || server.hash)
      throw new Error('Invalid comment server');
    commentPath = params.get('path');
    if (!accountMode && !/^\/stersu\/(theme|style)\/[a-zA-Z0-9][a-zA-Z0-9._-]{0,79}$/.test(commentPath || ''))
      throw new Error('Invalid comment thread');
    const serverURL = server.href.replace(/\/+$/, '');
    account ??= createCommentAccount(serverURL, chinese, accountMode);
    // A slow session check must not delay reading comments or typing a draft.
    void account.restore();
    if (accountMode) {
      connection.hidden = true;
      document.querySelector('#guidance').hidden = true;
      return;
    }
    const { init } = await import('./vendor/waline.js');
    if (disposed) return;
    stopObserving?.();
    stopObserving = observeCommentReads(serverURL, state => {
      if (disposed) return;
      connection.hidden = state === 'ready';
      retry.hidden = state !== 'failed';
      message.textContent = state === 'failed' ? copy.failed : copy.loading;
    });
    container.hidden = false;
    instance = init({
      el: container, serverURL, path: commentPath, lang: chinese ? 'zh-CN' : 'en', dark,
      login: 'disable', meta: ['nick', 'mail'], requiredMeta: ['nick'],
      pageSize: 10, commentSorting: 'latest', wordLimit: 1000,
      emoji: false, search: false, imageUploader: false, texRenderer: false,
      highlighter: false, reaction: false, pageview: false, comment: false, noRss: true,
      locale: { placeholder: copy.placeholder, optional: copy.optional },
    });
    account.connect();
  } catch {
    if (!disposed) {
      message.textContent = copy.failed;
      retry.hidden = false;
    }
  } finally {
    pending = false;
  }
}

retry.addEventListener('click', mountComments);
window.addEventListener('pagehide', () => {
  disposed = true;
  stopObserving?.();
  account?.destroy();
  instance?.destroy();
});
mountComments();
