const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const f = process.argv[2]; const html = '<!doctype html><html><head><meta charset="utf-8"></head><body>' + fs.readFileSync(f, 'utf8') + '</body></html>';
  fs.writeFileSync('test_err.html', html);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await b.newPage(); const errs = [];
  p.on('pageerror', e => errs.push('pageerror: ' + e.message + ' ' + (e.stack || '').split('\n').slice(0, 3).join(' | '))); p.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  await p.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' })); await p.route('https://fonts.googleapis.com/**', r => r.abort());
  await p.goto('file://' + process.cwd() + '/test_err.html', { waitUntil: 'load' }); await p.waitForTimeout(2500);
  console.log(errs.join('\n') || 'no errors'); await b.close();
})();
