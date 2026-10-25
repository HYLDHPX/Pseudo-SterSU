import { createAccountSession } from './account-session.js';

export function createCommentAccount(serverURL, chinese, accountMode = false) {
  const copy = chinese ? {
    title: '评论账号', guest: '未登录', signedIn: '已登录评论账号', login: '邮箱登录', register: '注册账号',
    name: '名称', email: '邮箱', password: '密码', confirm: '确认密码', code: '六位验证码',
    twoFactor: '已开启两步验证？', save: '保存资料', logout: '退出登录', retry: '重试验证',
    avatar: '头像图片地址', avatarHelp: '使用公开可访问的 HTTPS 图片地址。头像和名称保存到评论账号，供其它设备和评论使用；留空保留当前头像。',
    description: '使用邮箱登录或注册，账号会同步到主题和界面风格的评论区。',
    manage: '前往我的', guestHint: '登录、注册和资料设置已移至商店 → 我的 → 评论账号。',
    checking: '正在验证登录状态…', busy: '正在处理…', saved: '资料已保存并同步。',
    registered: '注册成功，请使用邮箱登录。如收到验证邮件，请先完成邮箱验证。',
    verifyEmail: '注册成功，请先打开邮件中的验证链接，再使用邮箱登录。',
    failed: '操作失败，请检查邮箱、密码或验证码后重试；未验证邮箱需先完成验证。',
    unavailable: '暂时无法连接账号服务，请稍后重试。', storage: '无法保存账号，请检查应用存储后重试。',
    expired: '登录已过期，请重新使用邮箱登录。', mismatch: '两次输入的密码不一致。',
    invalidName: '请输入有效名称（1–80 字符）。', invalidAvatar: '头像必须是公开可访问的 HTTPS 图片地址。',
    profileFailed: '资料尚未确认保存，请重试验证或保存。', signOutFailed: '退出失败，请检查应用存储后重试。',
  } : {
    title: 'Comment account', guest: 'Not signed in', signedIn: 'Signed in to comments', login: 'Email sign-in', register: 'Register',
    name: 'Display name', email: 'Email', password: 'Password', confirm: 'Confirm password', code: 'Six-digit code',
    twoFactor: 'Two-factor authentication enabled?', save: 'Save profile', logout: 'Sign out', retry: 'Check session again',
    avatar: 'Avatar image URL', avatarHelp: 'Use a public HTTPS image URL. Your avatar and name are saved to the comment account for comments and other devices. Leave blank to keep the current avatar.',
    description: 'Sign in or register with email. This account is shared by theme and interface-style comments.',
    manage: 'Go to My', guestHint: 'Sign-in, registration and profile settings are in Store → My → Comment account.',
    checking: 'Checking your session…', busy: 'Working…', saved: 'Profile saved and synced.',
    registered: 'Registered. Sign in with email; verify your email first if a confirmation message arrives.',
    verifyEmail: 'Registered. Open the verification link in your email before signing in.',
    failed: 'Could not sign in or register. Check your email, password or authentication code. Verify your email if required.',
    unavailable: 'The account service could not be reached. Please retry.', storage: 'Your account could not be saved. Check app storage and retry.',
    expired: 'Your session expired. Sign in with email again.', mismatch: 'The passwords do not match.',
    invalidName: 'Enter a valid display name (1–80 characters).', invalidAvatar: 'Use a public HTTPS image URL for your avatar.',
    profileFailed: 'Profile save has not been confirmed. Check your session or save again.', signOutFailed: 'Sign-out failed. Check app storage and retry.',
  };
  const get = id => document.getElementById(id);
  let busy = false, disposed = false, registering = false, clientReady = false, profileDirty = false;
  let previousId;
  const localEvents = new WeakSet();
  const session = createAccountSession(serverURL, updateAccount);
  const feedback = (text, error = false) => {
    get('account-feedback').hidden = !text;
    get('account-feedback').textContent = text || '';
    get('account-feedback').dataset.error = String(error);
  };
  function avatarUrl(value) {
    if (!value) return '';
    try {
      const url = new URL(value);
      return url.protocol === 'https:' && !url.username && !url.password && value.length <= 2048 ? url.href : '';
    } catch { return ''; }
  }
  function syncClient() {
    if (!clientReady) return;
    const event = new StorageEvent('storage', { key: 'WALINE_USER', newValue: JSON.stringify(session.user || {}), storageArea: localStorage });
    localEvents.add(event);
    window.dispatchEvent(event);
  }
  function updateAccount(user) {
    if (disposed) return;
    get('account-name').textContent = user?.display_name || user?.email || copy.guest;
    get('account-status').textContent = user ? copy.signedIn : copy.guestHint;
    if (accountMode) {
      get('account-description').hidden = Boolean(user);
      get('email-panel').hidden = Boolean(user);
      get('profile-panel').hidden = !user;
      get('account-status').textContent = user ? copy.signedIn : copy.description;
      if (user) {
        get('profile-name').textContent = user.display_name || user.email || copy.signedIn;
        get('profile-email').textContent = user.email || '';
        if (!profileDirty || previousId !== user.objectId) {
          get('profile-display-name').value = user.display_name || '';
          get('profile-avatar-url').value = avatarUrl(user.avatar);
          profileDirty = false;
        }
        const avatar = avatarUrl(user.avatar);
        get('profile-avatar').hidden = !avatar;
        if (avatar) get('profile-avatar').src = avatar;
        else get('profile-avatar').removeAttribute('src');
      }
    }
    previousId = user?.objectId;
    syncClient();
  }
  function setBusy(value) {
    busy = value;
    get('email-fields').disabled = value;
    get('profile-fields').disabled = value;
    for (const id of ['tab-login', 'tab-register', 'account-logout', 'account-retry']) get(id).disabled = value;
    get('email-submit').textContent = value ? copy.busy : registering ? copy.register : copy.login;
    get('profile-submit').textContent = value ? copy.busy : copy.save;
  }
  function selectMode(register) {
    registering = register;
    for (const id of ['register-name-row', 'confirm-row']) get(id).hidden = !register;
    get('register-name').required = register;
    get('confirm-password').required = register;
    get('two-factor-row').hidden = register;
    get('two-factor').disabled = register;
    get('password').autocomplete = register ? 'new-password' : 'current-password';
    get('password').value = get('confirm-password').value = get('two-factor').value = '';
    get('tab-login').setAttribute('aria-pressed', String(!register));
    get('tab-register').setAttribute('aria-pressed', String(register));
    setBusy(false);
    feedback('');
  }
  const validName = value => value.length > 0 && value.length <= 80 && !/[\u0000-\u001f\u007f]/.test(value);
  async function restore() {
    if (busy || disposed) return;
    setBusy(true);
    get('account-status').textContent = copy.checking;
    if (accountMode) feedback(copy.checking);
    try {
      await session.restore();
      get('account-retry').hidden = true;
      feedback('');
    } catch (error) {
      get('account-status').textContent = error.rejected ? copy.expired : copy.unavailable;
      if (accountMode) {
        feedback(error.message === 'storage-unavailable' ? copy.storage : error.rejected ? copy.expired : copy.unavailable, true);
        get('account-retry').hidden = Boolean(error.rejected);
      }
    } finally { if (!disposed) setBusy(false); }
  }
  document.querySelectorAll('[data-copy]').forEach(el => { el.textContent = copy[el.dataset.copy]; });
  get('account').setAttribute('aria-label', copy.title);
  document.querySelector('.account-tabs').setAttribute('aria-label', copy.title);
  get('account').hidden = accountMode;
  get('account-editor').hidden = !accountMode;
  get('account-description').textContent = copy.description;
  get('tab-login').textContent = copy.login;
  get('tab-register').textContent = copy.register;
  get('account-manage').textContent = copy.manage;
  get('account-manage').hidden = accountMode || !window.SterSUCommentAccount?.postMessage;
  get('account-manage').onclick = () => window.SterSUCommentAccount?.postMessage('open-my');
  get('tab-login').onclick = () => selectMode(false);
  get('tab-register').onclick = () => selectMode(true);
  get('account-retry').onclick = restore;
  get('profile-avatar').onerror = () => { get('profile-avatar').hidden = true; };
  get('profile-form').oninput = () => { profileDirty = true; };
  get('email-form').onsubmit = async event => {
    event.preventDefault();
    if (busy || !accountMode) return;
    const email = get('email').value.trim(), password = get('password').value;
    const name = get('register-name').value.trim();
    if (registering && !validName(name)) { feedback(copy.invalidName, true); return; }
    if (registering && password !== get('confirm-password').value) { feedback(copy.mismatch, true); return; }
    const code = get('two-factor').value, registration = registering;
    setBusy(true);
    feedback(copy.busy);
    try {
      if (registration) {
        const result = await session.register(email, password, name);
        selectMode(false);
        feedback(result?.verify ? copy.verifyEmail : copy.registered);
      } else { await session.signIn(email, password, code); feedback(''); }
      get('account-retry').hidden = true;
    } catch (error) { feedback(error.message === 'storage-unavailable' ? copy.storage : copy.failed, true); }
    finally {
      get('password').value = get('confirm-password').value = get('two-factor').value = '';
      if (!disposed) setBusy(false);
    }
  };
  get('profile-form').onsubmit = async event => {
    event.preventDefault();
    if (busy || !accountMode || !session.user) return;
    const name = get('profile-display-name').value.trim(), avatar = get('profile-avatar-url').value.trim();
    if (!validName(name)) { feedback(copy.invalidName, true); return; }
    if (avatar && !avatarUrl(avatar)) { feedback(copy.invalidAvatar, true); return; }
    setBusy(true);
    feedback(copy.busy);
    try { await session.updateProfile(name, avatar); profileDirty = false; updateAccount(session.user); feedback(copy.saved); }
    catch (error) { feedback(error.message === 'storage-unavailable' ? copy.storage : copy.profileFailed, true); get('account-retry').hidden = false; }
    finally { if (!disposed) setBusy(false); }
  };
  get('account-logout').onclick = () => {
    if (busy || !accountMode) return;
    try { session.signOut(); profileDirty = false; get('profile-form').reset(); get('profile-avatar').removeAttribute('src'); feedback(''); }
    catch { feedback(copy.signOutFailed, true); }
  };
  const storageEvent = event => {
    if (event.key === 'WALINE_USER' && !localEvents.has(event)) event.stopImmediatePropagation();
    if (event.key === `STERSU_WALINE_USER:${serverURL}` && !disposed) void restore();
  };
  const messageEvent = event => event.stopImmediatePropagation();
  const guardSubmission = event => {
    if (!session.user && (busy || session.unverified) && (event.type === 'submit' || event.target.closest?.('.wl-btn.primary'))) {
      event.preventDefault(); event.stopImmediatePropagation(); get('account-status').textContent = copy.checking;
    }
  };
  const onFocus = () => { if (!document.hidden) void restore(); };
  window.addEventListener('storage', storageEvent, true);
  window.addEventListener('message', messageEvent, true);
  window.addEventListener('focus', onFocus);
  document.addEventListener('visibilitychange', onFocus);
  get('waline').addEventListener('click', guardSubmission, true);
  get('waline').addEventListener('submit', guardSubmission, true);
  try { localStorage.removeItem('WALINE_USER'); } catch { /* Report persistent storage failures on sign-in. */ }
  selectMode(false);
  updateAccount(null);
  return {
    restore,
    connect() { clientReady = true; syncClient(); },
    destroy() {
      disposed = true; session.destroy(); get('email-form').reset(); get('profile-form').reset();
      window.removeEventListener('storage', storageEvent, true);
      window.removeEventListener('message', messageEvent, true);
      window.removeEventListener('focus', onFocus);
      document.removeEventListener('visibilitychange', onFocus);
      get('waline').removeEventListener('click', guardSubmission, true);
      get('waline').removeEventListener('submit', guardSubmission, true);
    },
  };
}
