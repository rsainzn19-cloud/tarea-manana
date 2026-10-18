const CN = require('path').resolve(__dirname, '..');
const { chromium } = require('playwright'); const fs = require('fs'), path = require('path');
(async () => {
  const items = [['gato', 'Gato'], ['dalmata', 'Dálmata'], ['mapache', 'Mapache'], ['panda', 'Panda'], ['ajolote', 'Ajolote'], ['pinguino', 'Pingüino']];
  const html = `<html><head><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wght@900"><style>body{margin:0;background:#0c1330;font-family:Archivo,sans-serif}.g{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;padding:10px;width:1580px}.c{position:relative}.c img{width:100%;display:block;border-radius:8px}.c b{position:absolute;left:14px;bottom:14px;background:#fff;color:#13204a;font-size:30px;padding:6px 12px;box-shadow:4px 4px 0 #ff6a1f;text-transform:uppercase}h1{margin:0;padding:18px 20px 4px;color:#fff;font-size:40px;text-transform:uppercase}h1 span{color:#ff6a1f}</style></head><body><h1>Casting de mascotas · <span>Cube News</span></h1><div class="g">${items.map(([id, n]) => `<div class="c"><img src="file://${path.resolve('vid/chk/cast_' + id + '.png')}"><b>${n}</b></div>`).join('')}</div></body></html>`;
  fs.writeFileSync('collage.html', html);
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1600, height: 740 } });
  await p.route('https://fonts.googleapis.com/**', r => r.fulfill({ path: 'fonts/fonts.css', contentType: 'text/css' }));
  await p.route('https://fonts.gstatic.com/**', r => r.fulfill({ path: 'fonts/' + r.request().url().replace('https://fonts.gstatic.com/', '').replace(/\//g, '_'), contentType: 'font/woff2' }));
  await p.goto('file://' + path.resolve('collage.html')); await p.waitForTimeout(800);
  await p.screenshot({ path: CN + '/miniaturas/casting-mascotas.png', fullPage: true }); await b.close();
})();
