// Run with Node.js + Playwright and an installed Chromium browser; no real comments are posted.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'manager/app/src/main/assets/theme-comments');
const output = path.join(root, 'out/theme-comments-browser');
const origin = 'https://appassets.androidplatform.net';
const server = 'https://comments.example.test';
const paths = ['/stersu/theme/test-theme', '/stersu/style/test-theme'];
const files = {
  'index.html': 'text/html', 'comments.js': 'text/javascript', 'comments.css': 'text/css',
  'vendor/waline.js': 'text/javascript', 'vendor/waline.css': 'text/css',
};
const results = [];
const errors = [];
const requests = [];
let serviceDown = false;
let multiplePages = false;
const comment = {
  objectId: 1, nick: 'Tester', mail: '', link: '', avatar: '',
  comment: '<p>Existing theme feedback</p>', url: paths[0],
  insertedAt: '2026-10-23T08:00:00.000Z', updatedAt: '2026-10-23T08:00:00.000Z',
  time: '2026-10-23T08:00:00.000Z',
  status: 'approved', like: 0, children: [], sticky: false,
};

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({
    headless: true,
    channel: process.env.STERSU_TEST_BROWSER || (process.platform === 'win32' ? 'msedge' : undefined),
  });
  const environment = { node: process.version, platform: process.platform, browser: browser.version() };
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 760 }, locale: 'zh-CN' });
    await context.route('**/*', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.origin === origin && url.pathname.startsWith('/comments/')) {
        const file = url.pathname.slice('/comments/'.length);
        if (!files[file]) return route.fulfill({ status: 404, body: 'Not found' });
        return route.fulfill({ contentType: files[file], body: fs.readFileSync(path.join(assets, file)) });
      }
      if (url.origin === server && url.pathname.startsWith('/api/')) {
        requests.push({ method: request.method(), url: request.url(), body: request.postDataJSON() });
        const headers = { 'access-control-allow-origin': origin, 'access-control-allow-headers': 'Content-Type' };
        if (request.method() === 'OPTIONS') return route.fulfill({ status: 204, headers });
        if (serviceDown) return route.fulfill({ status: 503, headers, body: 'Unavailable' });
        let data;
        if (request.method() === 'POST' && url.pathname === '/api/comment') {
          const submitted = request.postDataJSON();
          data = { ...comment, ...submitted, objectId: 2, comment: `<p>${submitted.comment}</p>` };
        } else if (url.searchParams.get('type') === 'count') data = [1];
        else if (url.pathname === '/api/comment') {
          const pageNumber = Number(url.searchParams.get('page') || 1);
          const item = { ...comment, url: url.searchParams.get('path') };
          if (pageNumber === 2) Object.assign(item, { objectId: 3, comment: '<p>Next page feedback</p>' });
          data = { page: pageNumber, pageSize: 10, totalPages: multiplePages ? 2 : 1, count: multiplePages ? 11 : 1, data: [item] };
        }
        else data = { ...comment, like: 1 };
        return route.fulfill({ headers, contentType: 'application/json', body: JSON.stringify({ errno: 0, errmsg: '', data }) });
      }
      // Do not send avatars or any other unexpected request to the real network.
      return route.abort('blockedbyclient');
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    page.on('dialog', async dialog => { errors.push(`Unexpected dialog: ${dialog.message()}`); await dialog.dismiss(); });
    const open = async (thread, dark = false, language = 'zh-CN') => {
      const query = new URLSearchParams({ server, path: thread, dark: String(dark), lang: language });
      await page.goto(`${origin}/comments/index.html?${query}`);
    };
    const checkContrast = async () => {
      const samples = await page.evaluate(() => {
        const rgb = value => value.match(/[\d.]+/g).slice(0, 3).map(Number);
        const luminance = color => rgb(color).map(n => {
          const s = n / 255;
          return s <= .04045 ? s / 12.92 : ((s + .055) / 1.055) ** 2.4;
        }).reduce((sum, n, i) => sum + n * [.2126, .7152, .0722][i], 0);
        const selectors = ['.wl-editor', 'input[name="nick"]', '.wl-btn.primary', '.wl-time', '.guidance', '.wl-like', '.wl-reply'];
        return selectors.map(selector => {
          const node = document.querySelector(selector);
          const style = getComputedStyle(node);
          const background = style.backgroundColor === 'rgba(0, 0, 0, 0)'
            ? getComputedStyle(document.querySelector('.wl-panel')).backgroundColor : style.backgroundColor;
          const a = luminance(style.color), b = luminance(background);
          return { selector, foreground: style.color, background, contrast: (Math.max(a, b) + .05) / (Math.min(a, b) + .05) };
        });
      });
      for (const sample of samples) assert.ok(sample.contrast >= 4.5, JSON.stringify(sample));
      for (const selector of ['.wl-like', '.wl-reply']) {
        const bounds = await page.locator(selector).first().boundingBox();
        assert.ok(bounds.width >= 44 && bounds.height >= 44, `${selector} touch target is too small`);
      }
      await page.locator('.wl-card .wl-content').first().evaluate(node => node.classList.add('expand'));
      assert.equal(await page.locator('.wl-card .wl-content').first().evaluate(node =>
        getComputedStyle(node, '::after').backgroundColor === getComputedStyle(document.body).backgroundColor), true);
      await page.locator('.wl-card .wl-content').first().evaluate(node => node.classList.remove('expand'));
      assert.equal(await page.getByText(/NaN/).count(), 0);
    };

    await open(paths[0]);
    await page.locator('.wl-editor').waitFor();
    await page.getByText('Existing theme feedback', { exact: true }).waitFor();
    await checkContrast();
    results.push('Official bundled Waline client loads the theme thread');
    await page.locator('input[name="nick"]').fill('SterSU tester');
    await page.locator('.wl-editor').fill('Draft retained across theme changes');
    await page.evaluate(() => window.stersuComments.setDarkMode(true));
    await page.waitForTimeout(50);
    assert.equal(await page.locator('.wl-editor').inputValue(), 'Draft retained across theme changes');
    assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
    await checkContrast();
    results.push('Changing day/night mode preserves the draft and the thread');
    await page.screenshot({ path: path.join(output, 'comments-dark.png'), fullPage: true });
    await page.evaluate(() => window.stersuComments.setDarkMode(false));
    await page.waitForTimeout(50);
    await checkContrast();
    assert.equal(await page.locator('.wl-editor').inputValue(), 'Draft retained across theme changes');
    results.push('Input, buttons, metadata and guidance remain readable in both themes');

    await page.locator('.wl-editor').fill('A test comment, only sent to the mock service');
    await page.locator('.wl-btn.primary').click();
    await page.waitForFunction(() => !document.querySelector('.wl-editor').value);
    const post = requests.find(request => request.method === 'POST' && request.body?.comment?.includes('only sent to the mock'));
    assert.ok(post);
    assert.equal(post.body.url, paths[0]);
    assert.equal(post.body.nick, 'SterSU tester');
    results.push('Submitting sends the correct content, nickname and stable theme path');

    await page.locator('.wl-reply').first().click();
    await page.locator('.wl-editor').fill('Mock reply');
    await page.locator('.wl-btn.primary').click();
    await page.waitForFunction(() => !document.querySelector('.wl-editor').value);
    const reply = requests.find(request => request.method === 'POST' && request.body?.comment === 'Mock reply');
    assert.ok(reply?.body.pid && reply?.body.rid);
    results.push('Replies include their parent and root identifiers');

    await page.locator('.wl-like').first().click();
    await page.waitForTimeout(150);
    assert.ok(requests.some(request => request.method === 'PUT'));
    results.push('Like actions reach the mock Waline endpoint');

    const previousSort = new URL(requests.filter(request => new URL(request.url).searchParams.has('sortBy')).at(-1).url).searchParams.get('sortBy');
    await page.locator('.wl-sort li').nth(1).click();
    await page.getByText('Existing theme feedback', { exact: true }).waitFor();
    const sorted = new URL(requests.filter(request => new URL(request.url).searchParams.has('sortBy')).at(-1).url);
    assert.notEqual(sorted.searchParams.get('sortBy'), previousSort);
    assert.equal(sorted.searchParams.get('page'), '1');
    results.push('Changing sort order reloads the first page with the selected order');

    multiplePages = true;
    await open(paths[1]);
    await page.locator('.wl-editor').waitFor();
    await page.locator('.wl-operation .wl-btn').waitFor();
    await page.locator('.wl-operation .wl-btn').click();
    await page.getByText('Next page feedback', { exact: true }).waitFor();
    assert.ok(requests.some(request => new URL(request.url).searchParams.get('page') === '2'));
    results.push('Pagination appends the next page without losing existing comments');
    multiplePages = false;
    assert.ok(requests.some(request => new URL(request.url).searchParams.get('path') === paths[1]));
    assert.ok(requests.filter(request => new URL(request.url).searchParams.has('path'))
      .every(request => paths.includes(new URL(request.url).searchParams.get('path'))));
    results.push('Interface styles use a separate thread namespace');
    await page.setViewportSize({ width: 280, height: 360 });
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    assert.equal(await page.locator('.wl-count').evaluate(node =>
      node.getBoundingClientRect().height <= parseFloat(getComputedStyle(node).lineHeight) + 1), true);
    await page.screenshot({ path: path.join(output, 'comments-small-window.png'), fullPage: true });
    await page.setViewportSize({ width: 960, height: 720 });
    await page.evaluate(() => document.documentElement.style.fontSize = '24px');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
    results.push('Small-window and tablet/large-font layouts do not overflow horizontally');
    await page.screenshot({ path: path.join(output, 'comments-tablet-large-font.png'), fullPage: true });

    serviceDown = true;
    await open(paths[0]);
    await page.locator('#retry:not([hidden])').waitFor();
    assert.equal(await page.locator('#waline').isVisible(), false);
    serviceDown = false;
    await page.locator('#retry').click();
    await page.locator('.wl-editor').waitFor();
    results.push('Service outage shows a recoverable error and retry reconnects');
    await page.setViewportSize({ width: 390, height: 760 });
    await open(paths[0], false, 'en');
    await page.locator('.wl-editor').waitFor();
    assert.equal(await page.locator('html').getAttribute('lang'), 'en');
    await checkContrast();
    await page.screenshot({ path: path.join(output, 'comments-light-en.png'), fullPage: true });
    results.push('English and light mode initialize correctly');
    assert.deepEqual(errors, []);
    const report = { environment, passed: results, errors, requestCount: requests.length };
    fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2));
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
