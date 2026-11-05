// 用本机 Chrome 无头模式截本地服务的网页，检查真实渲染
const { spawn } = require('child_process');
const path = require('path');
const os = require('os');
const fs = require('fs');

const CHROME = 'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe';
const PORT = process.env.PORT || 9111;
const DATA = path.join(os.tmpdir(), 'mjshot-' + Date.now() + '.json');
const OUT = path.resolve(__dirname, '..', process.argv[2] || 'shot.png');
const W = process.argv[3] || '390,844';

const srv = spawn(path.join(__dirname, 'mjserver-test.exe'), [], {
  env: { ...process.env, MJ_PORT: String(PORT), MJ_DATA: DATA, MJ_DIST: path.join(__dirname, 'dist'), MJ_WEB: path.join(__dirname, 'web') },
  stdio: 'ignore',
});

setTimeout(() => {
  const c = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run',
    '--disable-extensions', '--force-device-scale-factor=1',
    '--window-size=' + W,
    '--screenshot=' + OUT,
    '--virtual-time-budget=1500',
    'http://127.0.0.1:' + PORT + '/m'
  ], { stdio: 'ignore' });
  c.on('exit', () => {
    srv.kill();
    try { fs.unlinkSync(DATA); } catch (e) {}
    console.log('shot ->', OUT, fs.existsSync(OUT) ? fs.statSync(OUT).size + ' bytes' : 'MISSING');
    process.exit(0);
  });
}, 900);
