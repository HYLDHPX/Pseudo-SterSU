// Local deployment safety tests. No container, account, or public service is created.
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { initializeDatabase } = require('../deploy/waline/init-db.cjs');

(async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'stersu-waline-init-'));
  const originalFetch = global.fetch;
  const results = [];
  try {
    const current = path.join(root, 'existing');
    await fs.mkdir(current);
    const existing = Buffer.from('SQLite format 3\0existing comments must remain untouched');
    await fs.writeFile(path.join(current, 'waline.sqlite'), existing);
    global.fetch = () => { throw new Error('An existing database must not trigger a download'); };
    assert.equal(await initializeDatabase(current), 'Existing database preserved');
    assert.deepEqual(await fs.readFile(path.join(current, 'waline.sqlite')), existing);
    results.push('An existing SQLite file is preserved without network access');

    await fs.writeFile(path.join(current, 'waline.sqlite'), 'not a database');
    await assert.rejects(initializeDatabase(current), /left unchanged/);
    assert.equal(await fs.readFile(path.join(current, 'waline.sqlite'), 'utf8'), 'not a database');
    results.push('An invalid existing database stops initialization without overwriting');

    const fresh = path.join(root, 'fresh');
    global.fetch = async () => new Response('untrusted data');
    await assert.rejects(initializeDatabase(fresh), /checksum mismatch/);
    await assert.rejects(fs.stat(path.join(fresh, 'waline.sqlite')), { code: 'ENOENT' });
    results.push('Checksum mismatch never creates a database');

    global.fetch = async () => new Response('', { status: 503 });
    await assert.rejects(initializeDatabase(fresh), /HTTP 503/);
    results.push('Download failure is explicit and leaves no partial database');

    // An offline fixture must still pass the production SHA-256 verification.
    // Without a fixture, download only the fixed official empty template.
    global.fetch = process.env.STERSU_WALINE_TEST_SEED
      ? async () => new Response(await fs.readFile(process.env.STERSU_WALINE_TEST_SEED)) : originalFetch;
    assert.equal(await initializeDatabase(fresh), 'Official empty database initialized');
    assert.equal((await fs.readFile(path.join(fresh, 'waline.sqlite'))).subarray(0, 16).toString(), 'SQLite format 3\0');
    assert.deepEqual((await fs.readdir(fresh)).sort(), ['waline.sqlite']);
    assert.equal(await initializeDatabase(fresh), 'Existing database preserved');
    results.push('The pinned official seed initializes once, verifies its hash and cleans temporary files');
    console.log(JSON.stringify({ passed: results }, null, 2));
  } finally {
    global.fetch = originalFetch;
    // Only the exact directory allocated by mkdtemp above is removed.
    await fs.rm(root, { recursive: true, force: true });
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
