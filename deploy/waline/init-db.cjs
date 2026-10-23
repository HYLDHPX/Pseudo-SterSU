// Official empty database for @waline/vercel 1.43.4. Never overwrite a live database.
const fs = require('node:fs/promises');
const path = require('node:path');
const { createHash, randomUUID } = require('node:crypto');

const seedUrl = 'https://raw.githubusercontent.com/walinejs/waline/c4b0f1ebb9a7970d36493d4c3580f3f43e098fa5/assets/waline.sqlite';
const seedHash = 'ac08959a80b2756701742d97ad445fab24597428b3bc56e0c87541c4ea8b1b37';
const signature = Buffer.from('SQLite format 3\0');

async function initializeDatabase(directory) {
  const destination = path.join(directory, 'waline.sqlite');
  try {
    const existing = await fs.open(destination, 'r');
    try {
      const header = Buffer.alloc(signature.length);
      await existing.read(header, 0, header.length, 0);
      if (!(await existing.stat()).isFile() || !header.equals(signature))
        throw new Error('Existing waline.sqlite is not a valid SQLite file; left unchanged. Restore a verified backup.');
      return 'Existing database preserved';
    } finally { await existing.close(); }
  } catch (error) {
    if (error.code !== 'ENOENT') throw error;
  }

  const response = await fetch(seedUrl, { signal: AbortSignal.timeout(20000) });
  if (!response.ok) throw new Error(`Official database download failed: HTTP ${response.status}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (createHash('sha256').update(bytes).digest('hex') !== seedHash)
    throw new Error('Official database checksum mismatch; nothing was installed');
  await fs.mkdir(directory, { recursive: true });
  const temporary = path.join(directory, `.waline-init-${randomUUID()}.tmp`);
  try {
    await fs.writeFile(temporary, bytes, { flag: 'wx', mode: 0o600 });
    // An atomic hard link fails if another process already created the destination.
    await fs.link(temporary, destination);
  } finally { await fs.unlink(temporary).catch(error => { if (error.code !== 'ENOENT') throw error; }); }
  return 'Official empty database initialized';
}

module.exports = { initializeDatabase };
if (require.main === module) {
  initializeDatabase(process.argv[2] || '/app/data').then(console.log).catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
