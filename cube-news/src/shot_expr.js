const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  fs.writeFileSync('test_brand.html', '<!doctype html><html><head><meta charset="utf-8"></head><body>' + fs.readFileSync('brand.html', 'utf8') + '</body></html>');
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  for (const e of ['serio', 'sorpresa', 'feliz', 'guino', 'enojado', 'triste', 'blink']) {
    const p = await b.newPage({ viewport: { width: 800, height: 800 } });
    await p.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' })); await p.route('https://fonts.googleapis.com/**', r => r.abort());
    await p.goto('file://' + process.cwd() + '/test_brand.html?e=' + e + '#avatar', { waitUntil: 'load', timeout: 120000 });
    await p.waitForFunction(() => document.title === 'READY', null, { timeout: 120000 });
    await p.locator('#stage').screenshot({ path: 'brand_out/ex_' + e + '.png' }); await p.close();
  }
  await b.close();
})();
