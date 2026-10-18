const CN = require('path').resolve(__dirname, '..');
const { chromium } = require('playwright');
const fs = require('fs');
// usage: node shot_shorts.js "0:1,5,9;1:2,6" [page]
const plan = (process.argv[2] || '').split(';').filter(Boolean).map(s => { const [k, ts] = s.split(':'); return [+k, ts.split(',').map(Number)]; });
(async () => {
  const html = '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body>' + fs.readFileSync(CN + '/shorts.html', 'utf8').split('"miniaturas/').join('"' + CN + '/miniaturas/') + '</body></html>';
  fs.writeFileSync('test_shorts.html', html);
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const errs = [];
  const open = async (vp) => {
    const page = await browser.newPage({ viewport: vp });
    page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') errs.push(m.type() + ': ' + m.text()); });
    page.on('pageerror', e => errs.push('pageerror: ' + e.message));
    await page.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ path: 'three.min.js', contentType: 'application/javascript' }));
    await page.route('https://fonts.googleapis.com/**', r => r.abort());
    await page.goto('file://' + process.cwd() + '/test_shorts.html', { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(3000); return page;
  };
  const page = await open({ width: 1360, height: 900 });
  if (process.env.VLANG === 'en') { await page.click('#langEn'); await page.waitForTimeout(300); }
  await page.click('#play');
  for (const [k, ts] of plan) {
    await page.click('#picker li:nth-child(' + (k + 1) + ') button');
    for (const t of ts) {
      await page.evaluate(t => { const s = document.getElementById('scrub'); s.value = t; s.dispatchEvent(new Event('input')); }, t);
      await page.waitForTimeout(700);
      await page.locator('#stage').screenshot({ path: `ss/s${k}_${t}.png` });
    }
  }
  if (process.argv[3] === 'page') {
    await page.screenshot({ path: 'ss/page.png', fullPage: true });
    await page.close();
    const m = await open({ width: 390, height: 844 });
    await m.waitForTimeout(3000);
    console.log('mobile overflow:', await m.evaluate(() => document.documentElement.scrollWidth > window.innerWidth));
    await m.screenshot({ path: 'ss/mobile.png', fullPage: true });
  }
  const totals = await (async () => { try { return await page.evaluate(() => document.getElementById('scrub').max); } catch (e) { return '-'; } })();
  console.log('scrub max', totals);
  console.log([...new Set(errs)].slice(0, 15).join('\n') || 'no errors');
  await browser.close();
})();
