// 浏览器版端到端测试：起本地服务端 → 两个客户端建房/加入/记分/撤销 → 校验 /m 页面
const { spawn } = require('child_process');
const path = require('path');
const os = require('os');
const fs = require('fs');

const PORT = 9110;
const DATA = path.join(os.tmpdir(), 'mjtest-' + Date.now() + '.json');
const srv = spawn(path.join(__dirname, 'mjserver-test.exe'), [], {
  env: { ...process.env, MJ_PORT: String(PORT), MJ_DATA: DATA, MJ_DIST: path.join(__dirname, 'dist'), MJ_WEB: path.join(__dirname, 'web') },
  stdio: ['ignore', 'pipe', 'pipe'],
});
let srvOut = '';
srv.stdout.on('data', d => srvOut += d);
srv.stderr.on('data', d => srvOut += d);

const BASE = `http://127.0.0.1:${PORT}`;
const WSURL = `ws://127.0.0.1:${PORT}/ws`;

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function mkClient(tag) {
  const ws = new WebSocket(WSURL);
  const queue = [];
  const waiters = [];
  ws.addEventListener('message', ev => {
    let m; try { m = JSON.parse(ev.data); } catch (e) { return; }
    const i = waiters.findIndex(w => w.type === m.type);
    if (i >= 0) { const w = waiters.splice(i, 1)[0]; w.resolve(m); }
    else queue.push(m);
  });
  ws.tag = tag;
  ws.next = (type, ms = 3000) => new Promise((resolve, reject) => {
    const hit = queue.findIndex(q => q.type === type);
    if (hit >= 0) return resolve(queue.splice(hit, 1)[0]);
    const w = { type, resolve: m => { clearTimeout(t); resolve(m); } };
    const t = setTimeout(() => { const k = waiters.indexOf(w); if (k >= 0) waiters.splice(k, 1); reject(new Error(tag + ' 等 ' + type + ' 超时')); }, ms);
    waiters.push(w);
  });
  ws.send2 = o => ws.send(JSON.stringify(o));
  ws.ready = new Promise((res, rej) => {
    ws.addEventListener('open', res);
    ws.addEventListener('error', () => rej(new Error(tag + ' 连接失败')));
  });
  return ws;
}

let pass = 0, fail = 0;
function check(name, cond, extra) {
  if (cond) { pass++; console.log('  ✓ ' + name); }
  else { fail++; console.log('  ✗ ' + name + (extra ? '  → ' + JSON.stringify(extra) : '')); }
}

(async () => {
  await sleep(700);
  try {
    const r = await fetch(BASE + '/m');
    const html = await r.text();
    check('/m 返回 200', r.status === 200, r.status);
    check('/m 是 HTML', (r.headers.get('content-type') || '').includes('text/html'));
    check('/m 内含页面标题', html.includes('麻将计分器') && html.includes('ws://'));

    const A = mkClient('A'); await A.ready;
    A.send2({ type: 'join', player_id: 'web-a', name: '小明' });
    const s1 = await A.next('state');
    const room = s1.room;
    check('建房拿到房号', /^\d{4,5}$/.test(room), room);
    check('建房者是房主', s1.host === 'web-a');
    check('state 带分值表', s1.scores && s1.scores.zimo === 10, s1.scores);

    // 不满 4 人时，吊风和记分都应被拒
    A.send2({ type: 'wind' });
    const eW = await A.next('error');
    check('不满4人吊风被拒', eW.msg.includes('4'), eW.msg);
    A.send2({ type: 'play', winner: 'web-a', kind: 'zimo' });
    const eP = await A.next('error');
    check('不满4人记分被拒', eP.msg.includes('4'), eP.msg);

    const B = mkClient('B'); await B.ready;
    B.send2({ type: 'join', room, player_id: 'web-b', name: '老王' });
    const s2 = await B.next('state');
    check('第二人进同一房', s2.room === room);
    check('房里两人', s2.players.length === 2, s2.players.length);
    await A.next('peers');

    const C = mkClient('C'); await C.ready;
    C.send2({ type: 'join', room, player_id: 'web-c', name: '阿芳' });
    await C.next('state');
    await A.next('peers');

    const D = mkClient('D'); await D.ready;
    D.send2({ type: 'join', room, player_id: 'web-d', name: '大刘' });
    const s4 = await D.next('state');
    check('满员4人', s4.players.length === 4, s4.players.length);
    await A.next('peers'); await B.next('peers'); await C.next('peers');

    // 满员后吊风：四家都有风位
    A.send2({ type: 'wind' });
    const w = await D.next('state');
    const winds = w.players.map(p => p.wind);
    check('吊风后四家都有风位', winds.every(x => x && x.length === 2), winds);
    check('风位不重复', new Set(winds).size === winds.length, winds);

    // A 自摸：其余三家各付 10
    A.send2({ type: 'play', winner: 'web-a', kind: 'zimo' });
    const r1 = await A.next('round');
    await B.next('round'); await C.next('round'); await D.next('round');
    check('自摸流水点数为 10', r1.rounds[0].points === 10, r1.rounds[0]);
    check('自摸三家付', r1.rounds[0].payers.length === 3, r1.rounds[0].payers);
    check('自摸赢家 +30', r1.balances['web-a'] === 30, r1.balances);

    // A 点 B 点炮：A 单付 20
    A.send2({ type: 'play', winner: 'web-b', kind: 'dianpao' });
    const r2 = await B.next('round');
    const seq2 = r2.rounds[0].seq;
    await A.next('round'); await C.next('round'); await D.next('round');
    check('点炮只一人付', r2.rounds[0].payers.length === 1 && r2.rounds[0].payers[0] === 'web-a', r2.rounds[0].payers);
    check('点炮后 B 净分 +10', r2.balances['web-b'] === 10, r2.balances);

    // 自己给自己点炮应被拒
    A.send2({ type: 'play', winner: 'web-a', kind: 'dianpao' });
    const e1 = await A.next('error');
    check('自己点自己报错', e1.msg.includes('输家'), e1.msg);

    // 非房主撤销应被拒
    B.send2({ type: 'undo', seq: seq2 });
    const e2 = await B.next('error');
    check('非房主不能撤销', e2.msg.includes('房主'), e2.msg);

    // 房主撤销
    A.send2({ type: 'undo', seq: seq2 });
    const u = await B.next('undid');
    check('撤销后只剩 1 局', u.rounds.length === 1, u.rounds.length);
    check('撤销后分数回退', u.balances['web-b'] === -10, u.balances);

    // 房主结束（B 队列里残留吊风时的旧 state，先消费掉再等结束广播）
    A.send2({ type: 'end' });
    await B.next('state');
    const c = await B.next('state');
    check('结束后 closed 为 true', c.closed === true);

    const h = await (await fetch(BASE + '/health')).json();
    check('/health 版本 0.8.2', h.version === '0.8.2', h.version);
    const m2 = await fetch(BASE + '/nope');
    check('未知路径 404', m2.status === 404, m2.status);

    A.close(); B.close(); C.close(); D.close();
  } catch (e) {
    fail++; console.log('  ✗ 异常: ' + e.message);
  }
  console.log('\n结果: ' + pass + ' 通过 / ' + fail + ' 失败');
  if (fail) console.log('服务端日志:\n' + srvOut.slice(-800));
  srv.kill();
  try { fs.unlinkSync(DATA); } catch (e) {}
  process.exit(fail ? 1 : 0);
})();
