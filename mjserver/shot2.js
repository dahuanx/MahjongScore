// 无头 Chrome + CDP：注入模拟数据，截大厅/牌桌/流水/记分弹层四张图
const { spawn } = require('child_process');
const path = require('path');
const os = require('os');
const fs = require('fs');

const CHROME = 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe';
const PORT = 9111, DBG = 9223;
const DATA = path.join(os.tmpdir(), 'mjshot2-' + Date.now() + '.json');
const URL_ = 'http://127.0.0.1:' + PORT + '/m';
const OUTDIR = path.resolve(__dirname, '..');

const srv = spawn(path.join(__dirname, 'mjserver-test.exe'), [], {
  env: { ...process.env, MJ_PORT: String(PORT), MJ_DATA: DATA, MJ_DIST: path.join(__dirname, 'dist'), MJ_WEB: path.join(__dirname, 'web') },
  stdio: 'ignore',
});
const sleep = ms => new Promise(r => setTimeout(r, ms));

const FAKE = {
  type: 'state', room: '48213', you: 'web-a', host: 'web-a',
  players: [
    { id: 'web-a', name: '小明', seat: 0, host: true, online: true, wind: '东风' },
    { id: 'web-b', name: '老王头', seat: 1, host: false, online: true, wind: '南风' },
    { id: 'app-c', name: '阿强', seat: 2, host: false, online: false, wind: '西风' },
    { id: 'app-d', name: '小红', seat: 3, host: false, online: true, wind: '北风' },
  ],
  rounds: [
    { seq: 1, winner: 'web-a', kind: 'zimo', points: 10, payers: ['web-b', 'app-c', 'app-d'], ts: 1 },
    { seq: 2, winner: 'app-c', kind: 'dianpao', points: 20, payers: ['app-c'], ts: 2 },
    { seq: 3, winner: 'web-a', kind: 'baozhongbao', points: 40, payers: ['web-b', 'app-c', 'app-d'], ts: 3 },
  ],
  balances: { 'web-a': 110, 'web-b': -50, 'app-c': -20, 'app-d': -40 },
  scores: { pinghu: 5, dianpao: 20, zimo: 10, loubao: 20, baozhongbao: 40 },
};

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
  if (!target) { console.log('no target'); chrome.kill(); srv.kill(); process.exit(1); }

  const ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise(r => ws.onopen = r);
  let id = 0; const pending = {};
  ws.onmessage = ev => { const m = JSON.parse(ev.data); if (m.id && pending[m.id]) pending[m.id](m); };
  const call = (method, params) => new Promise(res => { const i = ++id; pending[i] = res; ws.send(JSON.stringify({ id: i, method, params })); });

  await call('Page.enable', {});
  await call('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await call('Page.navigate', { url: URL_ });
  await sleep(1500);

  async function shot(name) {
    const r = await call('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(OUTDIR, name), Buffer.from(r.result.data, 'base64'));
    console.log('shot ->', name);
  }
  async function evaljs(expr) {
    const r = await call('Runtime.evaluate', { expression: expr, returnByValue: true });
    return r.result.result && r.result.result.value;
  }

  await shot('web-1-lobby.png');
  await evaljs(`window.__MJ.handle(${JSON.stringify(FAKE)}); "ok"`);
  await sleep(250);
  await shot('web-2-table.png');
  await evaljs(`window.scrollTo(0, document.body.scrollHeight); "ok"`);
  await sleep(150);
  await shot('web-3-flow.png');
  await evaljs(`window.__MJ.openSheet('app-c'); "ok"`);
  await sleep(250);
  await shot('web-4-sheet.png');

  const sanity = await evaljs(`JSON.stringify({vw:innerWidth, sw:document.documentElement.scrollWidth})`);
  console.log('sanity:', sanity);

  ws.close(); chrome.kill(); srv.kill();
  try { fs.unlinkSync(DATA); } catch (e) {}
  process.exit(0);
})();
