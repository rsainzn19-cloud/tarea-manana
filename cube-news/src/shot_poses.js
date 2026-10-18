const CN = require('path').resolve(__dirname, '..');
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const html = '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>' + fs.readFileSync(CN + '/mr-news.html', 'utf8') + '</body></html>';
  fs.writeFileSync('test_mr.html', html);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const route = async pg => { await pg.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' })); await pg.route('https://fonts.googleapis.com/**', r => r.fulfill({ path: 'fonts/fonts.css', contentType: 'text/css' })); await pg.route('https://fonts.gstatic.com/**', r => r.fulfill({ path: 'fonts/' + r.request().url().replace('https://fonts.gstatic.com/', '').replace(/\//g, '_'), contentType: 'font/woff2' })); };
  const p = await b.newPage({ viewport: { width: 1360, height: 900 } }); const errs = []; p.on('pageerror', e => errs.push(e.message));
  await route(p); await p.goto('file://' + process.cwd() + '/test_mr.html', { waitUntil: 'load', timeout: 180000 }); await p.waitForTimeout(2500);
  await p.click('#camSeg button[data-v="front"]'); await p.waitForTimeout(900);
  for (const v of ['hype', 'flap', 'wow', 'shrug', 'think', 'lean', 'count']) { await p.click('#poseSeg button[data-v="' + v + '"]'); await p.waitForTimeout(700); await p.locator('#stage').screenshot({ path: 'vid/chk/pose_' + v + '.png' }); }
  await p.locator('.pgroup:has(#poseSeg)').screenshot({ path: 'vid/chk/pose_ctrl.png' });
  const m = await b.newPage({ viewport: { width: 390, height: 844 } }); await route(m); await m.goto('file://' + process.cwd() + '/test_mr.html', { waitUntil: 'load', timeout: 180000 }); await m.waitForTimeout(2000);
  console.log('mobile overflow', await m.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)); await m.locator('.pgroup:has(#poseSeg)').screenshot({ path: 'vid/chk/pose_ctrl_m.png' });
  console.log(errs.join('\n') || 'no errors'); await b.close();
})();
