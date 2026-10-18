const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const outDir = process.argv[2] || 'ss';
(async () => {
  const html = '<!doctype html><html><head><meta charset="utf-8"></head><body>' + fs.readFileSync('thumbs.html', 'utf8') + '</body></html>';
  fs.writeFileSync('test_thumbs.html', html);
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  for (const k of ['piloto', 'dolar', 'papa', 'avatar']) {
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    const errs = [];
    page.on('pageerror', e => errs.push(e.message));
    await page.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' }));
    await page.route('https://fonts.googleapis.com/**', r => r.fulfill({ path: 'fonts/fonts.css', contentType: 'text/css' }));
    await page.route('https://fonts.gstatic.com/**', r => { const f = 'fonts/' + r.request().url().replace('https://fonts.gstatic.com/', '').replace(/\//g, '_'); r.fulfill({ path: f, contentType: 'font/woff2' }); });
    await page.goto('file://' + process.cwd() + '/test_thumbs.html#' + k, { waitUntil: 'load', timeout: 120000 });
    await page.waitForFunction(() => document.title === 'READY' || document.title.startsWith('FAIL'), null, { timeout: 120000 });
    console.log(k, await page.title(), errs.join(' | ') || 'ok', await page.evaluate(() => document.fonts.check('900 40px Archivo')));
    await page.locator('#stage').screenshot({ path: path.join(outDir, k + (k === 'avatar' ? '.png' : '.jpg')), type: k === 'avatar' ? 'png' : 'jpeg', ...(k === 'avatar' ? {} : { quality: 90 }) });
    await page.close();
  }
  await browser.close();
})();
