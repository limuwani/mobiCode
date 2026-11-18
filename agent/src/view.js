'use strict';
const crypto = require('node:crypto');

function html(webview) {
  const nonce = crypto.randomBytes(18).toString('base64');
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${nonce}'; script-src 'nonce-${nonce}';">
  <style nonce="${nonce}">
  *{box-sizing:border-box}body{margin:0;padding:15px;font:13px var(--vscode-font-family);color:var(--vscode-foreground);background:var(--vscode-sideBar-background)}
  header{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:12px}h1{font-size:18px;margin:0}button{cursor:pointer;border:1px solid var(--vscode-widget-border,#424955);border-radius:7px;padding:9px 11px;color:var(--vscode-foreground);background:var(--vscode-button-secondaryBackground)}
  button:disabled{opacity:.5;cursor:default}button:focus-visible,textarea:focus-visible,select:focus-visible{outline:2px solid var(--vscode-focusBorder)}
  .intro{color:var(--vscode-descriptionForeground);line-height:1.55}#log{display:flex;flex-direction:column;gap:12px;margin:18px 0;min-height:100px}.message{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px;border-radius:9px;line-height:1.5;background:var(--vscode-editor-background)}
  .user{border-left:3px solid #38d8be}.tool{border-left:3px solid #387aff;font-size:12px}.error{border-left:3px solid var(--vscode-errorForeground)}.role{display:block;font-size:10px;letter-spacing:.08em;text-transform:uppercase;opacity:.6;margin-bottom:6px}
  textarea{width:100%;resize:vertical;min-height:110px;padding:12px;border-radius:8px;border:1px solid var(--vscode-input-border,#424955);background:var(--vscode-input-background);color:var(--vscode-input-foreground);font:inherit;line-height:1.5}
  .row{display:flex;gap:8px;align-items:center;margin-top:10px;flex-wrap:wrap}select{max-width:100%;padding:8px;color:var(--vscode-foreground);background:var(--vscode-dropdown-background);border:1px solid var(--vscode-dropdown-border,#424955);border-radius:6px}.send{background:#38d8be;color:#10151d;border-color:#38d8be;margin-left:auto}label{font-size:12px}.hint{font-size:11px;color:var(--vscode-descriptionForeground);line-height:1.5}#status{min-height:18px}
  </style></head><body>
  <header><h1>mobiCode Agent</h1><button id="settings">Provider</button></header>
  <p class="intro">Ask about your project, fix an error or build a feature. Agent mode can read project files, propose edits and request commands.</p>
  <div id="log" role="log" aria-label="Conversation"></div>
  <textarea id="prompt" aria-label="Message" placeholder="What are we building?"></textarea>
  <div class="row"><select id="mode" aria-label="Mode"><option value="chat">Chat</option><option value="agent">Agent</option></select>
  <label><input id="context" type="checkbox"> Include active file</label></div>
  <p class="hint">Included files and conversation are sent to your chosen provider. Review file diffs and commands before accepting them.</p>
  <div class="row"><button id="clear">Clear</button><button id="stop" disabled>Stop</button><button class="send" id="send">Send</button></div>
  <p id="status" class="hint" role="status"></p>
  <script nonce="${nonce}">
  const api=acquireVsCodeApi(), log=document.getElementById('log'), prompt=document.getElementById('prompt');
  const send=document.getElementById('send'), stop=document.getElementById('stop'), clear=document.getElementById('clear'), settings=document.getElementById('settings');
  function add(role,text){const box=document.createElement('div');box.className='message '+role;const label=document.createElement('span');label.className='role';label.textContent=role;box.append(label,document.createTextNode(text));log.append(box);box.scrollIntoView({block:'end'});}
  function submit(){if(send.disabled||!prompt.value.trim())return;api.postMessage({type:'send',text:prompt.value,mode:document.getElementById('mode').value,includeFile:document.getElementById('context').checked});prompt.value='';}
  send.addEventListener('click',submit);prompt.addEventListener('keydown',e=>{if(e.key==='Enter'&&(e.ctrlKey||e.metaKey)){e.preventDefault();submit();}});
  stop.onclick=()=>api.postMessage({type:'stop'});settings.onclick=()=>api.postMessage({type:'configure'});clear.onclick=()=>api.postMessage({type:'clear'});
  window.addEventListener('message',e=>{const m=e.data;if(m.type==='message')add(m.role,m.text);if(m.type==='reset'){log.replaceChildren();for(const entry of m.entries)add(entry.role,entry.text);}if(m.type==='state'){send.disabled=m.busy;stop.disabled=!m.busy;clear.disabled=m.busy;settings.disabled=m.busy;document.getElementById('status').textContent=m.status||'';}});
  api.postMessage({type:'ready'});
  </script></body></html>`;
}
module.exports = { html };
