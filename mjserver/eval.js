// 无头 Chrome + CDP：找出把页面撑出视口的元素
const { spawn } = require('child_process');
const path = require('path');
const os = require('os');
const fs = require('fs');

const CHROME = 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe';
const PORT = 9111, DBG = 9223;
const DATA = path.join(os.tmpdir(), 'mjeval-' + Date.now() + '.json');
const URL_ = 'http://127.0.0.1:' + PORT + '/m';

const srv = spawn(path.join(__dirname, 'mjserver-test.exe'), [], {
  env: { ...process.env, MJ_PORT: String(PORT), MJ_DATA: DATA, MJ_DIST: path.join(__dirname, 'dist'), MJ_WEB: path.join(__dirname, 'web') },
  stdio: 'ignore',
});

const sleep = ms => new Promise(r => setTimeout(r, ms));

(async () => {
  await sleep(800);
  const chrome = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--disable-extensions',
    '--remote-debugging-port=' + DBG, '--window-size=390,844', 'about:blank',
  ], { stdio: 'ignore' });

  let target = null;
  for (let i = 0; i < 20; i++) {
    await sleep(400);
    try {
      const list = await (await fetch('http://127.0.0.1:' + DBG + '/json/list')).json();
      target = list.find(t => t.type === 'page');
      if (target) break;
    } catch (e) {}
  }
  if (!target) { console.log('拿不到调试目标'); chrome.kill(); srv.kill(); process.exit(1); }

  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise(r => ws.onopen = r);
  let id = 0;
  const pending = {};
  ws.onmessage = ev => { const m = JSON.parse(ev.data); if (m.id && pending[m.id]) pending[m.id](m); };
  const call = (method, params) => new Promise(res => { const i = ++id; pending[i] = res; ws.send(JSON.stringify({ id: i, method, params })); });

  await call('Page.enable', {});
  await call('Page.navigate', { url: URL_ });
  await sleep(1600);

  const expr = `(function(){
    var out = {vw: innerWidth, sw: document.documentElement.scrollWidth, items: []};
    document.querySelectorAll('*').forEach(function(el){
      var r = el.getBoundingClientRect();
      if (r.right > innerWidth + 1) out.items.push({tag: el.tagName, cls: String(el.className).slice(0,40), id: el.id, w: Math.round(r.width), right: Math.round(r.right)});
    });
    return JSON.stringify(out);
  })()`;
  const res = await call('Runtime.evaluate', { expression: expr, returnByValue: true });
  console.log(res.result.result.value);

  ws.close(); chrome.kill(); srv.kill();
  try { fs.unlinkSync(DATA); } catch (e) {}
  process.exit(0);
})();
