'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const core = require('../src/core');

async function fixture(t) {
  const container = await fs.mkdtemp(path.join(os.tmpdir(), 'mobicode-'));
  t.after(() => fs.rm(container, { recursive: true, force: true }));
  const root = path.join(container, 'project');
  await fs.mkdir(root);
  return { container, root };
}

test('file tools read project code and block traversal, escaping symlinks and credentials', async t => {
  const { container, root } = await fixture(t);
  await fs.writeFile(path.join(root, 'main.cpp'), 'int main() {}');
  await fs.writeFile(path.join(root, '.env'), 'PRIVATE=secret');
  await fs.writeFile(path.join(container, 'outside.txt'), 'outside');
  await fs.symlink(container, path.join(root, 'escape'));
  await fs.symlink(path.join(root, '.env'), path.join(root, 'innocent.txt'));
  assert.equal(await core.readProjectFile(root, 'main.cpp'), 'int main() {}');
  await assert.rejects(core.readProjectFile(root, '../outside.txt'), /outside/);
  await assert.rejects(core.workspacePath(root, path.join(root, 'main.cpp')), /relative/);
  await assert.rejects(core.workspacePath(root, 'escape/new/missing.txt'), /Symlink/);
  await assert.rejects(core.readProjectFile(root, '.env'), /secret/);
  await assert.rejects(core.readProjectFile(root, 'innocent.txt'), /secret/);
  assert.equal(await core.workspacePath(root, 'new/inside.cpp'), path.join(root, 'new/inside.cpp'));
  const entries = await core.listProject(root);
  assert.ok(entries.some(entry => entry.name === 'main.cpp'));
  assert.ok(!entries.some(entry => entry.name === '.env'));
});

test('binary and oversized files are not sent as code context', async t => {
  const { root } = await fixture(t);
  await fs.writeFile(path.join(root, 'binary.dat'), Buffer.from([0, 1, 2]));
  await fs.writeFile(path.join(root, 'huge.txt'), 'x'.repeat(96001));
  await assert.rejects(core.readProjectFile(root, 'binary.dat'), /Binary/);
  await assert.rejects(core.readProjectFile(root, 'huge.txt'), /96 KB/);
});

test('endpoint validation keeps keys out of URLs and requires secure remote transport', () => {
  assert.equal(core.apiUrl('https://provider.example/v1/'), 'https://provider.example/v1/chat/completions');
  assert.equal(core.apiUrl('http://127.0.0.1:1234/v1'), 'http://127.0.0.1:1234/v1/chat/completions');
  assert.throws(() => core.apiUrl('http://remote.example/v1'), /HTTPS/);
  assert.throws(() => core.apiUrl('https://provider.example/v1?key=secret'), /secret storage/);
  assert.throws(() => core.apiUrl('https://user:pass@provider.example/v1'), /secret storage/);
});

test('agent transport sends real HTTP requests with model, messages and tool calls', async t => {
  let received;
  const server = http.createServer(async (request, response) => {
    let body = '';
    for await (const chunk of request) body += chunk;
    received = { path: request.url, auth: request.headers.authorization, body: JSON.parse(body) };
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ choices: [{ message: { content: null, tool_calls: [
      { id: 'call-1', type: 'function', function: { name: 'read_file', arguments: '{"path":"main.cpp"}' } }
    ] } }] }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => server.close());
  const answer = await core.requestCompletion({
    base: 'http://127.0.0.1:' + server.address().port + '/v1', key: 'test-key', model: 'test-model',
    messages: [{ role: 'user', content: 'Read main.cpp' }], tools: [{ type: 'function', function: { name: 'read_file' } }]
  });
  assert.equal(received.path, '/v1/chat/completions');
  assert.equal(received.auth, 'Bearer test-key');
  assert.equal(received.body.model, 'test-model');
  assert.equal(received.body.tool_choice, 'auto');
  assert.equal(answer.tool_calls[0].function.name, 'read_file');
});

test('provider errors remain errors rather than simulated assistant replies', async () => {
  await assert.rejects(core.requestCompletion({
    base: 'https://provider.example/v1', model: 'model', messages: [],
    fetchImpl: async () => new Response('Invalid token', { status: 401 })
  }), /HTTP 401/);
  await assert.rejects(core.requestCompletion({
    base: 'https://provider.example/v1', model: 'model', messages: [],
    fetchImpl: async () => new Response('{}', { status: 200 })
  }), /did not return/);
});

test('reviewed shell commands return actual stdout and exit status', async t => {
  const { root } = await fixture(t);
  const result = await core.runCommand('printf "real-output"; exit 7', root);
  assert.equal(result.output, 'real-output');
  assert.equal(result.exitCode, 7);
});

test('command timeout and user cancellation stop running processes', async t => {
  const { root } = await fixture(t);
  const timed = await core.runCommand('sleep 30', root, undefined, 60);
  assert.equal(timed.stopped, 'Command timed out.');
  const controller = new AbortController();
  const running = core.runCommand('sleep 30', root, controller.signal, 10000);
  setTimeout(() => controller.abort(), 50);
  assert.equal((await running).stopped, 'Stopped by user.');
});
