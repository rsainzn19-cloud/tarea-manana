// usage: node export_short.js <k> <lang> <outDir> [fps] [maxSeconds] [audioOnly]
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
const [k, lang, outDir, fpsArg, maxArg, mode] = process.argv.slice(2);
const fps = +(fpsArg || 30);
(async () => {
  fs.mkdirSync(outDir, { recursive: true });
  const html = '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>' + fs.readFileSync('shorts_build.html', 'utf8') + '</body></html>';
  fs.writeFileSync('test_export.html', html);
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--autoplay-policy=no-user-gesture-required'] });
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  const errs = []; page.on('pageerror', e => errs.push(e.message)); page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  await page.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' }));
  await page.route('https://fonts.googleapis.com/**', r => r.fulfill({ path: 'fonts/fonts.css', contentType: 'text/css' }));
  await page.route('https://fonts.gstatic.com/**', r => r.fulfill({ path: 'fonts/' + r.request().url().replace('https://fonts.gstatic.com/', '').replace(/\//g, '_'), contentType: 'font/woff2' }));
  await page.goto('file://' + process.cwd() + '/test_export.html', { waitUntil: 'load', timeout: 120000 });
  await page.evaluate(() => document.fonts.ready);
  // the stage fills the whole 1080x1920 viewport, nothing else on the page shows
  await page.addStyleTag({ content: '.stage{position:fixed!important;left:0!important;top:0!important;width:1080px!important;height:1920px!important;max-width:none!important;border-radius:0!important;z-index:50}.monitor{padding:0!important}body{overflow:hidden}.hud{display:none!important}' });
  const total = await page.evaluate(([k, lang]) => window.CUBE_EXPORT.load(+k, lang), [k, lang]);
  await page.waitForTimeout(800);
  console.log('total', total.toFixed(3));
  if (mode && mode.startsWith('t=')) {
    for (const t of mode.slice(2).split(',').map(Number)) { await page.evaluate(t => window.CUBE_EXPORT.frame(t), t); await page.waitForTimeout(150); await page.evaluate(t => window.CUBE_EXPORT.frame(t), t); await page.screenshot({ path: path.join(outDir, 's_' + t + '.jpg'), type: 'jpeg', quality: 85, clip: { x: 0, y: 0, width: 1080, height: 1920 } }); }
    console.log(errs.slice(0, 8).join('\n') || 'no errors'); await browser.close(); return;
  }
  if (mode !== 'frames') { const wav = await page.evaluate(() => window.CUBE_EXPORT.wav(48000)); fs.writeFileSync(path.join(outDir, 'audio.wav'), Buffer.from(wav, 'base64')); }
  if (mode !== 'audio') {
    const n = Math.floor(Math.min(total, +(maxArg || 1e9)) * fps); const t0 = Date.now();
    for (let i = 0; i < n; i++) {
      await page.evaluate(t => window.CUBE_EXPORT.frame(t), i / fps);
      await page.screenshot({ path: path.join(outDir, 'f' + String(i).padStart(5, '0') + '.jpg'), type: 'jpeg', quality: 92, clip: { x: 0, y: 0, width: 1080, height: 1920 } });
      if (i % 30 === 0) console.log('frame', i, '/', n, ((Date.now() - t0) / (i + 1)).toFixed(0), 'ms/frame');
    }
  }
  console.log(errs.slice(0, 8).join('\n') || 'no errors');
  await browser.close();
})();
