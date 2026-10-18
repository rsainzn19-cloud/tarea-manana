const CN = require('path').resolve(__dirname, '..');
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const html = '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>' + fs.readFileSync(CN + '/mascotas.html', 'utf8') + '</body></html>';
  fs.writeFileSync('test_cast.html', html);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required'] });
  const route = async pg => { await pg.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' })); await pg.route('https://fonts.googleapis.com/**', r => r.fulfill({ path: 'fonts/fonts.css', contentType: 'text/css' })); await pg.route('https://fonts.gstatic.com/**', r => r.fulfill({ path: 'fonts/' + r.request().url().replace('https://fonts.gstatic.com/', '').replace(/\//g, '_'), contentType: 'font/woff2' })); };
  const p = await b.newPage({ viewport: { width: 1360, height: 900 } }); const errs = []; p.on('pageerror', e => errs.push(e.message)); await route(p);
  await p.goto('file://' + process.cwd() + '/test_cast.html', { waitUntil: 'load' }); await p.waitForTimeout(3000);
  await p.locator('#stage').screenshot({ path: 'vid/chk/cast_all.png' });
  const names = ['gato', 'dalmata', 'mapache', 'panda', 'ajolote', 'pinguino'];
  for (let i = 0; i < 6; i++) { await p.click('#cands li:nth-child(' + (i + 1) + ') .see'); await p.waitForTimeout(1800); await p.locator('#stage').screenshot({ path: 'vid/chk/cast_' + names[i] + '.png' }); }
  await p.screenshot({ path: 'vid/chk/cast_page.png', fullPage: true });
  await p.close(); const m = await b.newPage({ viewport: { width: 390, height: 844 } }); await route(m); await m.goto('file://' + process.cwd() + '/test_cast.html', { waitUntil: 'load' }); await m.waitForTimeout(2500);
  console.log('mobile overflow', await m.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)); await m.screenshot({ path: 'vid/chk/cast_mobile.png', fullPage: true });
  console.log(errs.join('\n') || 'no errors'); await b.close();
})();
