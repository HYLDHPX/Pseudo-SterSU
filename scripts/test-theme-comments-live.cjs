// Read-only smoke test of the bundled client against an explicitly supplied service.
// Every remote request other than GET is blocked; no test comments are published.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const service = new URL(process.env.STERSU_WALINE_LIVE_SERVER || '');
assert.equal(service.protocol, 'https:');
assert.equal(service.username + service.password + service.search + service.hash, '');
const server = service.href.replace(/\/+$/, '');
const origin = 'https://appassets.androidplatform.net';
const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'manager/app/src/main/assets/theme-comments');
const output = path.join(root, 'out/theme-comments-live');
const mimeTypes = {
  'index.html': 'text/html', 'comments.js': 'text/javascript', 'comments.css': 'text/css',
  'auth.js': 'text/javascript', 'account-session.js': 'text/javascript', 'network.js': 'text/javascript',
  'vendor/waline.js': 'text/javascript', 'vendor/waline.css': 'text/css',
};

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    channel: process.env.STERSU_TEST_BROWSER || (process.platform === 'win32' ? 'msedge' : undefined),
  });
  const requests = [];
  const errors = [];
  const results = [];
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 760 }, locale: 'zh-CN' });
    await context.route('**/*', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.origin === origin && url.pathname.startsWith('/comments/')) {
        const file = url.pathname.slice('/comments/'.length);
        if (!mimeTypes[file]) return route.fulfill({ status: 404, body: 'Not found' });
        return route.fulfill({ contentType: mimeTypes[file], body: fs.readFileSync(path.join(assets, file)) });
      }
      if (url.origin === service.origin && url.pathname.startsWith(`${service.pathname.replace(/\/+$/, '')}/api/`)) {
        requests.push({ method: request.method(), url: request.url() });
        if (request.method() === 'GET') return route.continue();
      }
      return route.abort('blockedbyclient');
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    const open = async (kind, dark) => {
      const thread = `/stersu/${kind}/stersu-connection-check`;
      const query = new URLSearchParams({ server, path: thread, dark: String(dark), lang: 'zh-CN' });
      const response = page.waitForResponse(item => {
        const url = new URL(item.url());
        return url.origin === service.origin && url.pathname.endsWith('/api/comment') &&
          url.searchParams.get('path') === thread;
      }, { timeout: 30000 });
      await page.goto(`${origin}/comments/index.html?${query}`);
      const api = await response;
      assert.equal(api.status(), 200);
      const body = await api.json();
      assert.equal(body.errno, 0);
      assert.ok(Array.isArray(body.data.data));
      await page.locator('#waline:not([hidden]) .wl-editor').waitFor({ state: 'visible' });
      assert.equal(await page.locator('#connection').evaluate(node => node.hidden), true);
      assert.equal(await page.locator('html').getAttribute('data-theme'), dark ? 'dark' : 'light');
      assert.equal(await page.locator('#account-editor').isHidden(), true);
      assert.equal(await page.locator('#account-login').count(), 0);
      const editor = page.locator('.wl-editor');
      await editor.fill('Local unsent draft');
      await page.evaluate(next => window.stersuComments.setDarkMode(next), !dark);
      assert.equal(await editor.inputValue(), 'Local unsent draft');
      await page.evaluate(next => window.stersuComments.setDarkMode(next), dark);
      assert.equal(await page.locator('html').getAttribute('data-theme'), dark ? 'dark' : 'light');
      await page.screenshot({ path: path.join(output, `${kind}-${dark ? 'dark' : 'light'}.png`), fullPage: true });
      results.push(`${kind}: live HTTP 200, valid JSON, visible editor, login managed in My, theme switching retains unsent draft`);
    };
    await open('theme', false);
    await open('style', true);
    await page.goto(`${origin}/comments/index.html?${new URLSearchParams({ server, mode: 'account', lang: 'zh-CN' })}`);
    await page.locator('#email-submit:not(:disabled)').waitFor();
    assert.equal(await page.locator('#email-panel').isVisible(), true);
    await page.screenshot({ path: path.join(output, 'email-account.png'), fullPage: true });
    results.push('Account form loads from bundled assets without contacting an OAuth page or submitting credentials');
    assert.deepEqual(errors, []);
    assert.ok(requests.length >= 2);
    assert.ok(requests.every(request => request.method === 'GET'));
    const report = {
      server, environment: { node: process.version, platform: process.platform, browser: browser.version() },
      passed: results, remoteRequests: requests, errors,
      notTested: ['real account sign-in', 'submit', 'reply', 'like', 'moderation', 'database persistence', 'Android device'],
    };
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
