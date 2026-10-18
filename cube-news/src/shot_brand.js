// usage: node shot_brand.js <outDir> [guides]
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const outDir = process.argv[2] || 'brand_out', guides = process.argv[3] === 'guides';
const SIZES = { avatar: [800, 800], yt: [2560, 1440], fb: [1702, 630] };
(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const html = '<!doctype html><html><head><meta charset="utf-8"></head><body>' + fs.readFileSync('brand.html', 'utf8') + '</body></html>';
  fs.writeFileSync('test_brand.html', html);
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  for (const k of Object.keys(SIZES)) {
    const [w, h] = SIZES[k];
    const page = await browser.newPage({ viewport: { width: w, height: h } });
    const errs = []; page.on('pageerror', e => errs.push(e.message)); page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
    await page.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' }));
    await page.route('https://fonts.googleapis.com/**', r => r.fulfill({ path: 'fonts/fonts.css', contentType: 'text/css' }));
    await page.route('https://fonts.gstatic.com/**', r => r.fulfill({ path: 'fonts/' + r.request().url().replace('https://fonts.gstatic.com/', '').replace(/\//g, '_'), contentType: 'font/woff2' }));
    await page.goto('file://' + process.cwd() + '/test_brand.html' + (guides ? '?guides' : '') + '#' + k, { waitUntil: 'load', timeout: 180000 });
    await page.waitForFunction(() => document.title === 'READY' || document.title.startsWith('FAIL'), null, { timeout: 180000 });
    console.log(k, await page.title(), errs.join(' | ') || 'ok', await page.evaluate(() => document.fonts.check('900 40px Archivo')));
    await page.locator('#stage').screenshot({ path: path.join(outDir, k + '.png'), type: 'png' });
    await page.close();
  }
  await browser.close();
})();
