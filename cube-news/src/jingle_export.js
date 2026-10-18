const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const html = '<!doctype html><html><head><meta charset="utf-8"></head><body>' + fs.readFileSync('shorts_build.html', 'utf8') + '</body></html>';
  fs.writeFileSync('test_jg.html', html);
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] }); const p = await b.newPage();
  await p.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' }));
  await p.route('https://fonts.googleapis.com/**', r => r.abort());
  await p.goto('file://' + process.cwd() + '/test_jg.html', { waitUntil: 'load' }); await p.waitForTimeout(1500);
  const wav = await p.evaluate(() => window.CUBE_EXPORT.jingleWav(48000)); fs.writeFileSync('vid/jingle.wav', Buffer.from(wav, 'base64'));
  await b.close();
})();
