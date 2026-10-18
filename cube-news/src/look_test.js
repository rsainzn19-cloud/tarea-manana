// renders one Short frame under several looks: node look_test.js <k> <lang> <t> <outPrefix>
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const [k, lang, t, out] = process.argv.slice(2);
  fs.writeFileSync('test_look.html', '<!doctype html><html><head><meta charset="utf-8"></head><body>' + fs.readFileSync('shorts_build.html', 'utf8') + '</body></html>');
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1080, height: 1920 } });
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' }));
  await p.route('https://fonts.googleapis.com/**', r => r.fulfill({ path: 'fonts/fonts.css', contentType: 'text/css' }));
  await p.route('https://fonts.gstatic.com/**', r => r.fulfill({ path: 'fonts/' + r.request().url().replace('https://fonts.gstatic.com/', '').replace(/\//g, '_'), contentType: 'font/woff2' }));
  await p.goto('file://' + process.cwd() + '/test_look.html', { waitUntil: 'load', timeout: 120000 });
  await p.evaluate(() => document.fonts.ready);
  await p.addStyleTag({ content: '.stage{position:fixed!important;left:0!important;top:0!important;width:1080px!important;height:1920px!important;max-width:none!important;border-radius:0!important;z-index:50}.monitor{padding:0!important}body{overflow:hidden}.hud{display:none!important}' });
  await p.evaluate(([k, lang]) => window.CUBE_EXPORT.load(+k, lang), [k, lang]);
  const looks = { a_normal: { ps1: 0, poly: false }, b_ps1: { ps1: 5, poly: false }, c_poly: { ps1: 0, poly: true }, d_both: { ps1: 5, poly: true } };
  for (const [name, look] of Object.entries(looks)) {
    await p.evaluate(o => window.CUBE_EXPORT.look(o), look);
    // step up to the moment like the real export, so a blink started by the jump has ended
    for (let i = 20; i >= 0; i--) await p.evaluate(x => window.CUBE_EXPORT.frame(x), +t - i / 30);
    await p.screenshot({ path: out + '_' + name + '.jpg', type: 'jpeg', quality: 90, clip: { x: 0, y: 0, width: 1080, height: 1920 } });
  }
  console.log(errs.join('\n') || 'no errors'); await b.close();
})();
