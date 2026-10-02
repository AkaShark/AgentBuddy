#!/usr/bin/env node
// Deterministic exports from assets/brand/agentbuddy-mark.svg.
// Requires sharp on NODE_PATH and macOS iconutil for the ICNS container.
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const sharp = require('sharp');

const root = path.resolve(__dirname, '../..');
const ios = 'apps/ios/Sources/AgentBuddy';
const android = 'apps/android/app/src/main';
const desktop = 'apps/desktop/src-tauri/icons';
const ink = '#172B20';
const mint = '#C3E7B5';

async function main() {
  const master = await fs.readFile(path.join(root, 'assets/brand/agentbuddy-mark.svg'), 'utf8');
  const geometry = master.match(/<g[\s\S]*<\/g>/)[0];
  const mark = (color = ink) => geometry.replaceAll(ink, color);
  const svg = (body, viewBox = '0 0 24 24') =>
    `<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="${viewBox}">${body}</svg>`;
  const gradient = (top, bottom) => `<defs><linearGradient id="field" x2="0.7" y2="1"><stop stop-color="${top}"/><stop offset="1" stop-color="${bottom}"/></linearGradient></defs>`;
  const icon = (top, bottom, color) => svg(`${gradient(top, bottom)}<rect width="24" height="24" fill="url(#field)"/>${mark(color)}`);
  const light = icon('#D6F1CC', '#B3DFA6', ink);
  const dark = icon('#20382B', '#13241B', mint);
  const tinted = icon('#272727', '#141414', '#E4E4E4');
  const mac = svg(`${gradient('#D6F1CC', '#B3DFA6')}<rect x="2" y="2.25" width="20" height="20" rx="4.55" fill="${ink}" opacity="0.10"/><rect x="2" y="2" width="20" height="20" rx="4.55" fill="url(#field)"/><rect x="2.04" y="2.04" width="19.92" height="19.92" rx="4.51" fill="none" stroke="#FFFFFF" stroke-opacity="0.5" stroke-width="0.08"/><g transform="translate(2 2) scale(0.8333333)">${mark()}</g>`);
  const brand = svg(`<rect x="1" y="1" width="22" height="22" rx="6.3" fill="${mint}"/><g transform="translate(2.4 2.4) scale(0.8)">${mark()}</g>`);
  const adaptive = (color) => svg(`<g transform="translate(19.2 19.2) scale(2.9)">${mark(color)}</g>`, '0 0 108 108');
  async function write(relative, data) {
    const file = path.join(root, relative);
    await fs.mkdir(path.dirname(file), { recursive: true });
    await fs.writeFile(file, data);
  }
  async function png(source, size, relative, opaque = false) {
    let render = sharp(Buffer.from(source)).resize(size, size);
    render = opaque ? render.removeAlpha() : render.ensureAlpha();
    const data = await render.png().toBuffer();
    await write(relative, data);
    return data;
  }
  await write('assets/brand/app-icon.svg', light);
  await write('assets/brand/app-icon-dark.svg', dark);
  await write('assets/brand/app-icon-macos.svg', mac);
  await png(light, 1024, `${ios}/Assets.xcassets/AppIcon.appiconset/Icon-1024.png`, true);
  await png(dark, 1024, `${ios}/Assets.xcassets/AppIcon.appiconset/Icon-1024-dark.png`, true);
  await png(tinted, 1024, `${ios}/Assets.xcassets/AppIcon.appiconset/Icon-1024-tinted.png`, true);
  for (const file of [
    `${ios}/Resources/brand_logo.png`,
    `${ios}/Assets.xcassets/brand_logo.imageset/brand_logo.png`,
    'apps/ios/Sources/AgentBuddyLiveActivity/Assets.xcassets/brand_logo.imageset/brand_logo.png',
    `${android}/res/drawable-nodpi/brand_logo.png`,
  ]) await png(brand, 1024, file);
  await write(`${ios}/Resources/brand_logo.svg`, brand);
  await png(adaptive(ink), 432, `${android}/res/drawable-nodpi/ic_launcher_foreground.png`);
  await png(adaptive('#000000'), 432, `${android}/res/drawable-nodpi/ic_launcher_monochrome.png`);
  await png(light, 512, `${android}/play/listings/en-US/graphics/icon/1.png`, true);
  for (const [size, name] of [[32, '32x32'], [64, '64x64'], [128, '128x128'], [256, '128x128@2x'], [1024, 'icon']]) {
    await png(mac, size, `${desktop}/${name}.png`);
  }
  await png(svg(mark('#000000')), 44, `${desktop}/tray-icon.png`);
  // ICO supports embedded PNGs; keep the existing auxiliary Windows resource coherent.
  const icoSizes = [16, 32, 48, 256];
  const icoImages = await Promise.all(icoSizes.map(size => sharp(Buffer.from(light)).resize(size, size).png().toBuffer()));
  const icoHeader = Buffer.alloc(6 + icoSizes.length * 16);
  icoHeader.writeUInt16LE(1, 2);
  icoHeader.writeUInt16LE(icoSizes.length, 4);
  let offset = icoHeader.length;
  icoImages.forEach((image, index) => {
    const entry = 6 + index * 16;
    icoHeader[entry] = icoHeader[entry + 1] = icoSizes[index] % 256;
    icoHeader.writeUInt16LE(1, entry + 4);
    icoHeader.writeUInt16LE(32, entry + 6);
    icoHeader.writeUInt32LE(image.length, entry + 8);
    icoHeader.writeUInt32LE(offset, entry + 12);
    offset += image.length;
  });
  await write(`${desktop}/icon.ico`, Buffer.concat([icoHeader, ...icoImages]));
  const temp = await fs.mkdtemp(path.join(os.tmpdir(), 'agentbuddy-brand-'));
  try {
    const iconset = path.join(temp, 'AgentBuddy.iconset');
    await fs.mkdir(iconset);
    for (const points of [16, 32, 128, 256, 512]) {
      for (const scale of [1, 2]) {
        await sharp(Buffer.from(mac)).resize(points * scale, points * scale).png()
          .toFile(path.join(iconset, `icon_${points}x${points}${scale === 2 ? '@2x' : ''}.png`));
      }
    }
    execFileSync('iconutil', ['-c', 'icns', iconset, '-o', path.join(root, desktop, 'icon.icns')]);
  } finally {
    await fs.rm(temp, { recursive: true, force: true });
  }
  // Watch still consumes the Icon Composer bundle; keep it on the same mark.
  await write(`${ios}/AppIcon.icon/Assets/agentbuddy-link.svg`, svg(mark()));
  await write(`${ios}/AppIcon.icon/icon.json`, JSON.stringify({
    'color-space-for-untagged-svg-colors': 'srgb',
    fill: { solid: 'srgb:0.76471,0.90588,0.70980,1.00000' },
    groups: [{ layers: [{ 'image-name': 'agentbuddy-link.svg', name: 'AgentBuddy paired links' }],
      shadow: { kind: 'neutral', opacity: 0.15 }, translucency: { enabled: false, value: 0 } }],
    'supported-platforms': { circles: ['watchOS'], squares: 'shared' },
  }, null, 2) + '\n');
  console.log('Generated iOS, Android, macOS, menu-bar, and shared brand artwork.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
