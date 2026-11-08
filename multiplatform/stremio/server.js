#!/usr/bin/env node
/**
 * Pulse Speed Stremio Addon Server
 * Zero-dependency standalone microservice conforming to Stremio Addon Protocol v3.
 * Can be run locally (node server.js) or deployed to Beamup, Render, Vercel, Railway, etc.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 7000;
const MANIFEST = JSON.parse(fs.readFileSync(path.join(__dirname, 'manifest.json'), 'utf8'));

const CATALOG_ITEMS = [
  {
    id: 'pulse:cloudflare',
    type: 'other',
    name: 'Pulse · Cloudflare Speed Test',
    poster: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/multiplatform/src/icon-256.png',
    background: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/docs/screenshots/start.png',
    description: 'Fast, edge-based latency, download and upload test via Cloudflare.',
  },
  {
    id: 'pulse:ookla',
    type: 'other',
    name: 'Pulse · Ookla (speedtest.net)',
    poster: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/multiplatform/src/icon-256.png',
    background: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/docs/screenshots/testing.png',
    description: 'Test against the closest speedtest.net server network.',
  },
  {
    id: 'pulse:fast',
    type: 'other',
    name: 'Pulse · FAST (Netflix CDN)',
    poster: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/multiplatform/src/icon-256.png',
    background: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/docs/screenshots/result.png',
    description: 'Direct measurement against Netflix Open Connect streaming servers.',
  },
  {
    id: 'pulse:bufferbloat',
    type: 'other',
    name: 'Pulse · Bufferbloat & Loaded Latency',
    poster: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/multiplatform/src/icon-256.png',
    background: 'https://raw.githubusercontent.com/dor2500/pulse-speed/main/docs/screenshots/history.png',
    description: 'Measure ping under heavy load (A+ to F grade) for gaming & video streaming diagnosis.',
  }
];

function sendJson(res, data) {
  res.writeHead(200, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer((req, res) => {
  const url = req.url.split('?')[0];

  // Enable CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Allow-Headers': '*',
    });
    res.end();
    return;
  }

  // 1. Manifest
  if (url === '/manifest.json') {
    return sendJson(res, MANIFEST);
  }

  // 2. Catalog
  if (url === '/catalog/other/pulse_speed_catalog.json' || url.startsWith('/catalog/other/pulse_speed_catalog/')) {
    return sendJson(res, { metas: CATALOG_ITEMS });
  }

  // 3. Meta detail
  const metaMatch = /^\/meta\/other\/(pulse:[a-z0-9_-]+)\.json$/i.exec(url);
  if (metaMatch) {
    const id = metaMatch[1];
    const item = CATALOG_ITEMS.find(c => c.id === id) || CATALOG_ITEMS[0];
    return sendJson(res, { meta: item });
  }

  // 4. Stream response
  const streamMatch = /^\/stream\/other\/(pulse:[a-z0-9_-]+)\.json$/i.exec(url);
  if (streamMatch) {
    const id = streamMatch[1];
    const targetSource = id.replace('pulse:', '');
    return sendJson(res, {
      streams: [
        {
          title: `Run ${targetSource.toUpperCase()} Speed Test`,
          externalUrl: `https://dor2500.github.io/pulse-speed/#source=${targetSource}`,
        }
      ]
    });
  }

  // 5. Embedded Web App files fallback
  const webDir = path.join(__dirname, '..', 'src');
  let filePath = path.join(webDir, url === '/' ? 'index.html' : url);
  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    let contentType = 'text/plain';
    if (filePath.endsWith('.html')) contentType = 'text/html';
    else if (filePath.endsWith('.css')) contentType = 'text/css';
    else if (filePath.endsWith('.js')) contentType = 'application/javascript';
    else if (filePath.endsWith('.png')) contentType = 'image/png';

    res.writeHead(200, { 'Content-Type': contentType, 'Access-Control-Allow-Origin': '*' });
    fs.createReadStream(filePath).pipe(res);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

server.listen(PORT, () => {
  console.log(`===============================================`);
  console.log(`  Pulse Speed Stremio Addon Server Running!    `);
  console.log(`  Listening at: http://localhost:${PORT}        `);
  console.log(`  Manifest URL: http://localhost:${PORT}/manifest.json`);
  console.log(`  Stremio Install: stremio://localhost:${PORT}/manifest.json`);
  console.log(`===============================================`);
});
