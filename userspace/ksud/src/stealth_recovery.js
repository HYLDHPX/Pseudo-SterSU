const form = document.getElementById('recovery-form');
const input = document.getElementById('code');
const button = document.getElementById('submit');
const message = document.getElementById('message');

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  button.disabled = true;
  message.textContent = '';
  delete message.dataset.success;
  try {
    const body = JSON.stringify({ code: input.value });
    if (new TextEncoder().encode(body).length > 4096) {
      message.textContent = '密令过长，无法提交恢复请求';
      return;
    }
    const response = await fetch('/disable', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-SterSU-Recovery': '1' },
      body,
      cache: 'no-store',
      credentials: 'omit',
    });
    const result = await response.json();
    message.textContent = result.message || '请求失败';
    message.dataset.success = String(response.ok);
    if (response.ok) {
      input.value = '';
      form.hidden = true;
    }
  } catch {
    message.textContent = '连接失败，请使用手机拨号密令恢复';
  } finally {
    button.disabled = false;
  }
});
