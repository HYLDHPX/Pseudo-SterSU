// Account scenarios for test-theme-comments.cjs. All writes use its in-memory mock service.
const assert = require('node:assert/strict');
const path = require('node:path');

module.exports = async function testAccount({ context, page, origin, server, output, open, requests, account, results,
  setExpired, setAuthDown, setDelayVerification }) {
  const key = `STERSU_WALINE_USER:${server}`;
  const saved = () => page.evaluate(key => JSON.parse(localStorage.getItem(key) || 'null'), key);
  const accountPage = async (dark = false, lang = 'en') => {
    await page.goto(`${origin}/comments/index.html?${new URLSearchParams({ server, mode: 'account', dark: String(dark), lang })}`);
    await page.locator('#email-submit:not(:disabled)').waitFor();
  };
  const signIn = async (password = 'mock-password') => {
    await page.locator('#email').fill(account.email);
    await page.locator('#password').fill(password);
    await page.locator('#email-submit').click();
  };
  await accountPage();
  assert.equal(await page.locator('#tab-login').textContent(), 'Email sign-in');
  assert.equal(await page.locator('#profile-panel').isHidden(), true);
  assert.equal(requests.some(r => /oauth|\/ui\/login/.test(r.url)), false);
  const reads = requests.filter(r => new URL(r.url).pathname.endsWith('/api/comment')).length;
  await page.screenshot({ path: path.join(output, 'account-email-light.png'), fullPage: true });
  assert.equal(await page.locator('#waline').isHidden(), true);
  assert.equal(requests.filter(r => new URL(r.url).pathname.endsWith('/api/comment')).length, reads);
  results.push('My account opens an email-only page without a fake discussion, popup or OAuth request');

  await page.locator('#tab-register').click();
  await page.locator('#register-name').fill('New tester');
  await page.locator('#email').fill('new@example.test');
  await page.locator('#password').fill('temporary-mock-password');
  await page.locator('#confirm-password').fill('different-password');
  const beforeRegistration = requests.length;
  await page.locator('#email-submit').click();
  assert.match(await page.locator('#account-feedback').textContent(), /do not match/);
  assert.equal(requests.length, beforeRegistration);
  await page.locator('#confirm-password').fill('temporary-mock-password');
  await page.locator('#email-submit').click();
  await page.waitForFunction(() => document.querySelector('#account-feedback').textContent.includes('verification link'));
  const registered = requests.find(r => r.method === 'POST' && new URL(r.url).pathname === '/api/user');
  assert.equal(registered.body.display_name, 'New tester');
  assert.equal(registered.body.email, 'new@example.test');
  assert.equal(await saved(), null);
  assert.equal(await page.locator('#password').inputValue(), '');
  results.push('Registration checks password confirmation, sends email/name and explains email verification without false sign-in');

  await signIn('wrong-password');
  await page.locator('#email-submit:not(:disabled)').waitFor();
  assert.equal(await saved(), null);
  assert.equal(await page.locator('#password').inputValue(), '');
  await signIn();
  await page.locator('#profile-panel:not([hidden])').waitFor();
  assert.equal((await saved()).objectId, account.objectId);
  const storage = await page.evaluate(() => ({ ...localStorage }));
  assert.equal(JSON.stringify(storage).includes('mock-password'), false);
  assert.equal(storage.WALINE_USER, undefined);
  results.push('Email login validates the token with the service, preserves server isolation and never stores passwords');

  const newName = 'Updated display name';
  const avatar = 'https://images.example.test/avatar.png';
  await context.route(avatar, route => route.fulfill({ contentType: 'image/png',
    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64') }));
  await page.locator('#profile-display-name').fill(newName);
  await page.locator('#profile-avatar-url').fill(avatar);
  await page.locator('#profile-submit').click();
  await page.waitForFunction(() => document.querySelector('#account-feedback').textContent === 'Profile saved and synced.');
  const updated = requests.filter(r => r.method === 'PUT' && new URL(r.url).pathname === '/api/user').at(-1);
  assert.deepEqual(updated.body, { display_name: newName, avatar });
  assert.equal(updated.authorization, `Bearer ${account.token}`);
  assert.equal((await saved()).display_name, newName);
  await page.reload();
  await page.locator('#profile-panel:not([hidden])').waitFor();
  assert.equal(await page.locator('#profile-display-name').inputValue(), newName);
  assert.equal(await page.locator('#profile-avatar').getAttribute('src'), avatar);
  results.push('Profile name/avatar are authenticated server updates and survive reload with a fresh server read');

  await page.locator('#profile-avatar-url').fill('http://images.example.test/avatar.png');
  const puts = requests.filter(r => r.method === 'PUT').length;
  await page.locator('#profile-submit').click();
  assert.match(await page.locator('#account-feedback').textContent(), /HTTPS/);
  assert.equal(requests.filter(r => r.method === 'PUT').length, puts);
  await page.locator('#profile-avatar-url').fill(avatar);
  await page.locator('#profile-display-name').fill('Unsaved draft name');
  await page.evaluate(() => window.stersuComments.restoreAccount());
  await page.locator('#profile-submit:not(:disabled)').waitFor();
  assert.equal(await page.locator('#profile-display-name').inputValue(), 'Unsaved draft name');
  results.push('Unsafe avatar URLs are rejected and session refresh preserves unsaved profile fields');

  await open('/stersu/style/test-theme', true, 'en');
  await page.locator('.wl-editor').waitFor();
  await page.waitForFunction(name => document.querySelector('#account-name').textContent === name, newName);
  assert.equal(await page.locator('#account-editor').isHidden(), true);
  await page.locator('.wl-editor').fill('Authenticated mock comment');
  await page.locator('.wl-btn.primary:not(:disabled)').click();
  await page.getByText('Authenticated mock comment', { exact: true }).waitFor();
  const post = requests.find(r => r.body?.comment === 'Authenticated mock comment');
  assert.equal(post.authorization, `Bearer ${account.token}`);
  assert.equal(post.body.nick, newName);
  results.push('The account saved in My is reused for authenticated style comments with the new name');

  await page.evaluate(() => {
    window.dispatchEvent(new MessageEvent('message', { origin: 'https://evil.test', source: window,
      data: { type: 'userInfo', data: { token: 'forged' } } }));
    window.dispatchEvent(new StorageEvent('storage', { key: 'WALINE_USER', newValue: JSON.stringify({ token: 'forged' }), storageArea: localStorage }));
  });
  assert.equal((await saved()).token, account.token);
  results.push('Unsolicited window messages and legacy storage events cannot replace the verified account');

  setDelayVerification(1200);
  await open('/stersu/theme/test-theme', false, 'en');
  await page.locator('.wl-editor').waitFor();
  await page.locator('.wl-editor').fill('Draft during restore');
  const beforePost = requests.filter(r => r.method === 'POST' && r.url.includes('/api/comment')).length;
  await page.locator('.wl-btn.primary:not(:disabled)').click();
  assert.equal(requests.filter(r => r.method === 'POST' && r.url.includes('/api/comment')).length, beforePost);
  await page.waitForFunction(name => document.querySelector('#account-name').textContent === name, newName);
  assert.equal(await page.locator('.wl-editor').inputValue(), 'Draft during restore');
  setDelayVerification(false);
  results.push('Slow account verification keeps comments readable, retains drafts and prevents unintended guest submission');

  setAuthDown(true);
  await accountPage(true);
  await page.locator('#account-retry:not([hidden])').waitFor();
  assert.ok(await saved());
  setAuthDown(false);
  await page.locator('#account-retry').click();
  await page.locator('#profile-panel:not([hidden])').waitFor();
  assert.equal(await page.locator('#account-retry').isHidden(), true);
  results.push('A temporary service outage retains the saved account and retry restores it');

  for (const dark of [false, true]) {
    await page.evaluate(dark => window.stersuComments.setDarkMode(dark), dark);
    for (const width of [240, 320, 600, 900]) {
      await page.setViewportSize({ width, height: 380 });
      await page.evaluate(() => window.stersuComments.setAppearance({ fontScale: 1.6 }));
      const size = await page.evaluate(() => ({ scroll: document.documentElement.scrollWidth, width: innerWidth }));
      assert.ok(size.scroll <= size.width + 1, `Account overflow at ${width}px`);
    }
    await page.screenshot({ path: path.join(output, `account-profile-${dark ? 'dark' : 'light'}.png`), fullPage: true });
  }
  await page.evaluate(() => window.stersuComments.setAppearance({ background: '#00000000', surface: '#ffffff66', imageOpacity: .7, textOpacity: 1 }));
  assert.equal(await page.locator('body').evaluate(n => getComputedStyle(n).backgroundColor), 'rgba(0, 0, 0, 0)');
  assert.equal(await page.locator('#profile-avatar').evaluate(n => getComputedStyle(n).opacity), '0.7');
  results.push('Account forms support light/dark themes, wallpaper transparency, independent avatar opacity and 240px windows at 160% fonts');

  await page.locator('#account-logout').click();
  assert.equal(await saved(), null);
  await open('/stersu/theme/test-theme', false, 'en');
  await page.locator('.wl-editor').waitFor();
  assert.equal(await page.locator('#account-name').textContent(), 'Not signed in');
  results.push('Sign-out in My removes the session for all comment threads');

  await page.evaluate(({ key, account }) => localStorage.setItem(key, JSON.stringify(account)), { key, account });
  setExpired(true);
  await accountPage();
  await page.waitForFunction(() => document.querySelector('#account-feedback').textContent.includes('expired'));
  assert.equal(await saved(), null);
  setExpired(false);
  await page.setViewportSize({ width: 390, height: 760 });
  await accountPage(false, 'zh-CN');
  await page.screenshot({ path: path.join(output, 'account-email-zh.png'), fullPage: true });
  assert.equal(await page.locator('#tab-login').textContent(), '\u90ae\u7bb1\u767b\u5f55');
  results.push('Expired sessions return to email login, and account controls are translated in Chinese and English');
};
