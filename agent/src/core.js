'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const { spawn } = require('node:child_process');

function apiUrl(base) {
  const url = new URL(base);
  const local = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && local)) {
    throw new Error('Use HTTPS for your provider, or HTTP on localhost for a local model.');
  }
  if (url.username || url.password || url.search || url.hash) throw new Error('Put the API key in secret storage, not in the endpoint URL.');
  url.pathname = url.pathname.replace(/\/+$/, '') + '/chat/completions';
  return url.toString();
}

function within(root, target) {
  const relative = path.relative(root, target);
  return relative === '' || (!relative.startsWith('..' + path.sep) && relative !== '..' && !path.isAbsolute(relative));
}

async function workspacePath(root, relative) {
  if (typeof relative !== 'string' || !relative || relative.includes('\0') || path.isAbsolute(relative)) {
    throw new Error('Use a relative path inside the open project.');
  }
  const resolvedRoot = await fs.realpath(root);
  const target = path.resolve(resolvedRoot, relative);
  if (!within(resolvedRoot, target)) throw new Error('Path is outside the open project.');
  // Check every existing ancestor: a missing file beneath an escaping symlink is also blocked.
  let ancestor = target;
  while (true) {
    try {
      const real = await fs.realpath(ancestor);
      if (!within(resolvedRoot, real)) throw new Error('Symlink points outside the open project.');
      return target;
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
      const parent = path.dirname(ancestor);
      if (parent === ancestor) throw new Error('No project parent exists.');
      ancestor = parent;
    }
  }
}

const PRIVATE = /(^|\/)(\.git|\.env(?:\..*)?|\.ssh|\.aws|\.gnupg|\.npmrc|\.pypirc|credentials(?:\.json)?|id_rsa|id_ed25519|[^/]+\.(?:pem|key|jks|keystore))(\/|$)/i;
function allowedFile(relative) {
  if (PRIVATE.test(relative.replace(/\\/g, '/'))) throw new Error('Credential and secret files are excluded from agent file tools.');
}

async function readProjectFile(root, relative) {
  allowedFile(relative);
  const target = await workspacePath(root, relative);
  const real = await fs.realpath(target);
  allowedFile(path.relative(await fs.realpath(root), real));
  const stat = await fs.stat(target);
  if (!stat.isFile() || stat.size > 96000) throw new Error('Read a text file smaller than 96 KB.');
  const bytes = await fs.readFile(target);
  if (bytes.includes(0)) throw new Error('Binary files are not supported.');
  return bytes.toString('utf8');
}

async function listProject(root, relative = '.') {
  allowedFile(relative);
  const directory = await workspacePath(root, relative);
  allowedFile(path.relative(await fs.realpath(root), await fs.realpath(directory)));
  const entries = await fs.readdir(directory, { withFileTypes: true });
  return entries.filter(e => !['node_modules', '.gradle', 'build', 'dist'].includes(e.name))
    .filter(e => !PRIVATE.test(e.name)).slice(0, 150)
    .map(e => ({ name: e.name, type: e.isSymbolicLink() ? 'symlink' : e.isDirectory() ? 'directory' : 'file' }));
}

async function requestCompletion({ base, key, model, messages, tools, signal, fetchImpl = fetch }) {
  const headers = { 'Content-Type': 'application/json' };
  if (key) headers.Authorization = 'Bearer ' + key;
  const body = { model, messages, stream: false };
  if (tools) { body.tools = tools; body.tool_choice = 'auto'; }
  const response = await fetchImpl(apiUrl(base), {
    method: 'POST', headers, body: JSON.stringify(body), redirect: 'error', signal
  });
  if (!response.ok) throw new Error(`Provider returned HTTP ${response.status}. Check your endpoint, model and key.`);
  // Bound memory even if the provider returns an unexpected response.
  const reader = response.body.getReader();
  const chunks = [];
  let bytes = 0;
  while (true) {
    const part = await reader.read();
    if (part.done) break;
    bytes += part.value.byteLength;
    if (bytes > 2000000) { await reader.cancel(); throw new Error('Provider response exceeded 2 MB.'); }
    chunks.push(Buffer.from(part.value));
  }
  const result = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  const message = result.choices?.[0]?.message;
  if (!message || (!message.content && !message.tool_calls)) throw new Error('Provider did not return a chat completion. Use a compatible chat-completions endpoint.');
  return { role: 'assistant', content: message.content || null, ...(message.tool_calls ? { tool_calls: message.tool_calls } : {}) };
}

/** A reviewed command has the same permissions as the Termux user; it is not an OS sandbox. */
function runCommand(command, cwd, signal, timeoutMs = 30000) {
  if (typeof command !== 'string' || !command.trim() || command.length > 8000) throw new Error('Command must be 1–8000 characters.');
  if (signal?.aborted) return Promise.reject(new Error('Stopped.'));
  return new Promise((resolve, reject) => {
    const child = spawn(process.env.SHELL || '/bin/sh', ['-c', command], {
      cwd, env: process.env, detached: true, stdio: ['ignore', 'pipe', 'pipe']
    });
    let output = '', total = 0, finished = false, stopReason = '', killTimer;
    const terminate = reason => {
      if (stopReason) return;
      stopReason = reason;
      try { process.kill(-child.pid, 'SIGTERM'); } catch { child.kill('SIGTERM'); }
      killTimer = setTimeout(() => {
        try { process.kill(-child.pid, 'SIGKILL'); } catch { child.kill('SIGKILL'); }
      }, 700);
      killTimer.unref();
    };
    const abort = () => terminate('Stopped by user.');
    signal?.addEventListener('abort', abort, { once: true });
    const timer = setTimeout(() => terminate('Command timed out.'), timeoutMs);
    const consume = bytes => {
      total += bytes.length;
      if (output.length < 24000) output += bytes.toString('utf8').slice(0, 24000 - output.length);
      if (total > 1000000 && !stopReason) terminate('Command output limit reached.');
    };
    child.stdout.on('data', consume);
    child.stderr.on('data', consume);
    const cleanup = () => {
      clearTimeout(timer); clearTimeout(killTimer);
      signal?.removeEventListener('abort', abort);
    };
    child.on('error', error => { if (!finished) { finished = true; cleanup(); reject(error); } });
    child.on('close', code => {
      if (finished) return;
      finished = true; cleanup();
      resolve({ exitCode: code, output, ...(stopReason ? { stopped: stopReason } : {}) });
    });
  });
}

module.exports = { apiUrl, within, workspacePath, allowedFile, readProjectFile, listProject, requestCompletion, runCommand };
