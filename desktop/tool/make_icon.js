// The window's icon for Windows (windows/runner/resources/app_icon.ico) from the mark «Маршрут»:
//
//   node tool/make_icon.js <out dir>     the PNGs of each size (needs Playwright with its Chromium)
//   python3 tool/make_icon.py <out dir>  the .ico of them (needs Pillow)
//
// The drawing is the Android launcher's (ic_launcher_background and _foreground); here the mark is larger, as a
// Windows icon has no safe zone to keep clear, and larger still in the sizes of a taskbar and a title bar.
const {chromium} = require('playwright');
const sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256];
const icon = (transform) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" rx="24" fill="#121212"/><g transform="${transform}"><path d="M30 58V28M30 46a18 18 0 0 1 36 0v12" fill="none" stroke="#e2e2e2" stroke-width="12" stroke-linecap="round"/><circle cx="30" cy="76" r="6" fill="#3D5AFE"/><circle cx="66" cy="76" r="6" fill="#E5382D"/></g></svg>`;
(async () => {
  const out = process.argv[2];
  const browser = await chromium.launch();
  for (const px of sizes) {
    // the mark's box on its 96 grid is 24..72 by 22..82, its middle (48, 52)
    const scale = px <= 32 ? 1.35 : 1.1;
    const svg = icon(`translate(${54 - 48 * scale} ${54 - 52 * scale}) scale(${scale})`);
    const page = await browser.newPage({viewport: {width: px, height: px}, deviceScaleFactor: 1});
    await page.setContent(`<body style="margin:0;background:transparent">${svg.replace('<svg ', `<svg width="${px}" height="${px}" `)}</body>`);
    await page.screenshot({path: `${out}/icon-${px}.png`, omitBackground: true});
    await page.close();
  }
  await browser.close();
})();
