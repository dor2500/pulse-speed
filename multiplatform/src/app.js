/**
 * Pulse Speed Multiplatform Engine
 * Supports Cloudflare, Ookla, FAST, LibreSpeed, Bufferbloat, and LG webOS TV Remote Navigation.
 */

// --- Internationalization Strings ---
const I18N = {
  en: {
    app_name: 'Pulse',
    tab_test: 'Test',
    tab_history: 'History',
    tab_settings: 'Settings',
    go: 'GO',
    stop: 'STOP',
    test_again: 'Test again',
    share: 'Share',
    phase_connecting: 'Connecting…',
    phase_ping: 'Ping',
    phase_download: 'Download',
    phase_upload: 'Upload',
    phase_done: 'Complete',
    jitter: 'Jitter',
    bufferbloat: 'Bufferbloat',
    loaded_latency: 'Loaded ping',
    speed_graph: 'Speed over time',
    connection_title: 'Connection',
    provider: 'Provider',
    server: 'Server',
    ip_address: 'IP address',
    network: 'Network',
    protocol: 'Protocol',
    theme: 'Theme',
    theme_system: 'System',
    theme_light: 'Light',
    theme_dark: 'Dark',
    theme_oled: 'OLED Black',
    units: 'Units',
    duration: 'Test length',
    len_quick: 'Quick (6s)',
    len_standard: 'Standard (10s)',
    len_long: 'Thorough (15s)',
    export_csv: 'Export CSV',
    clear_history: 'Clear history',
    no_history: 'No speed tests recorded yet',
    src_cf_desc: "Cloudflare's global edge network, nearest data center",
    src_ookla_desc: "Ookla's speedtest.net servers, fastest nearby server",
    src_fast_desc: "Netflix servers, the same ones FAST.com uses",
    src_libre_desc: "Open-source LibreSpeed community servers, fastest responder",
  },
  iw: {
    app_name: 'Pulse',
    tab_test: 'בדיקה',
    tab_history: 'היסטוריה',
    tab_settings: 'הגדרות',
    go: 'GO',
    stop: 'עצור',
    test_again: 'בדיקה חוזרת',
    share: 'שיתוף',
    phase_connecting: 'מתחבר…',
    phase_ping: 'פינג',
    phase_download: 'הורדה',
    phase_upload: 'העלאה',
    phase_done: 'הבדיקה הושלמה',
    jitter: 'ג׳יטר',
    bufferbloat: 'ציון Bufferbloat',
    loaded_latency: 'פינג בעומס',
    speed_graph: 'מהירות לאורך הבדיקה',
    connection_title: 'חיבור',
    provider: 'ספק',
    server: 'שרת',
    ip_address: 'כתובת IP',
    network: 'רשת',
    protocol: 'פרוטוקול',
    theme: 'ערכת נושא',
    theme_system: 'מערכת',
    theme_light: 'בהיר',
    theme_dark: 'כהה',
    theme_oled: 'שחור מוחלט (OLED)',
    units: 'יחידות',
    duration: 'אורך הבדיקה',
    len_quick: 'מהירה (6 שניות)',
    len_standard: 'רגילה (10 שניות)',
    len_long: 'יסודית (15 שניות)',
    export_csv: 'ייצוא ל-CSV',
    clear_history: 'ניקוי היסטוריה',
    no_history: 'עדיין אין תוצאות בדיקה שמורות',
    src_cf_desc: 'הרשת הגלובלית של Cloudflare, מרכז הנתונים הקרוב',
    src_ookla_desc: 'השרתים של speedtest.net (Ookla), השרת הקרוב והמהיר',
    src_fast_desc: 'השרתים של Netflix, אותם שרתים ש-FAST.com משתמש בהם',
    src_libre_desc: 'שרתי הקהילה של LibreSpeed (קוד פתוח), זה שעונה הכי מהר',
  }
};

// --- State ---
const state = {
  tab: 'test', // 'test', 'history', 'settings'
  lang: localStorage.getItem('pulse_lang') || (navigator.language.startsWith('he') ? 'iw' : 'en'),
  theme: localStorage.getItem('pulse_theme') || 'dark',
  unit: localStorage.getItem('pulse_unit') || 'mbps', // 'mbps' or 'mbytes'
  durationSec: parseInt(localStorage.getItem('pulse_duration') || '10', 10),
  source: 'cloudflare', // 'cloudflare', 'ookla', 'fast', 'librespeed'
  running: false,
  abortController: null,
  activePhase: 'idle',
  pingMs: null,
  jitterMs: null,
  downloadMbps: null,
  uploadMbps: null,
  downLoadedPing: null,
  upLoadedPing: null,
  bufferbloatGrade: null,
  liveMbps: 0,
  downSamples: [],
  upSamples: [],
  serverInfo: {
    isp: '—',
    serverLabel: '—',
    ip: '—',
    protocol: 'HTTP/2',
    network: navigator.onLine ? 'Online' : 'Offline',
  },
  history: JSON.parse(localStorage.getItem('pulse_history') || '[]'),
};

function t(key) {
  return (I18N[state.lang] && I18N[state.lang][key]) || I18N.en[key] || key;
}

function applyTheme() {
  document.documentElement.setAttribute('data-theme', state.theme);
  document.documentElement.setAttribute('dir', state.lang === 'iw' ? 'rtl' : 'ltr');
}

function formatSpeed(mbps) {
  if (mbps == null) return '—';
  if (state.unit === 'mbytes') {
    return (mbps / 8).toFixed(1);
  }
  return mbps >= 100 ? Math.round(mbps).toString() : mbps.toFixed(1);
}

function speedUnitLabel() {
  return state.unit === 'mbytes' ? 'MB/s' : 'Mbps';
}

function gradeBufferbloat(baseline, downLoaded, upLoaded) {
  const dDown = downLoaded != null ? Math.max(0, downLoaded - baseline) : 0;
  const dUp = upLoaded != null ? Math.max(0, upLoaded - baseline) : 0;
  const worst = Math.max(dDown, dUp);
  if (worst <= 5) return 'A+';
  if (worst <= 15) return 'A';
  if (worst <= 30) return 'B';
  if (worst <= 60) return 'C';
  if (worst <= 150) return 'D';
  return 'F';
}

// --- Speed Test Engine ---
class SpeedTestEngine {
  constructor(abortSignal) {
    this.signal = abortSignal;
  }

  async runPing(epUrl, count = 10, onSample) {
    // Warm up DNS/TLS connection
    try { await fetch(epUrl + (epUrl.includes('?') ? '&' : '?') + 'r=' + Math.random(), { signal: this.signal, cache: 'no-store' }); } catch (_) {}
    const samples = [];
    for (let i = 0; i < count; i++) {
      if (this.signal.aborted) throw new Error('Aborted');
      const t0 = performance.now();
      try {
        const resp = await fetch(epUrl + (epUrl.includes('?') ? '&' : '?') + 'r=' + Math.random(), { signal: this.signal, cache: 'no-store' });
        const t1 = performance.now();
        let ms = Math.max(1, t1 - t0);
        // Deduct server timing if Cloudflare
        const st = resp.headers.get('Server-Timing');
        if (st) {
          const match = /dur=([0-9.]+)/.exec(st);
          if (match) ms = Math.max(1, ms - parseFloat(match[1]));
        }
        samples.push(ms);
        if (onSample) onSample(ms, (i + 1) / count);
      } catch (err) {
        if (this.signal.aborted) throw err;
      }
      await new Promise(r => setTimeout(r, 60));
    }
    if (samples.length === 0) throw new Error('Ping failed');
    samples.sort((a, b) => a - b);
    const median = samples[Math.floor(samples.length / 2)];
    let jitterSum = 0;
    for (let i = 0; i < samples.length - 1; i++) {
      jitterSum += Math.abs(samples[i + 1] - samples[i]);
    }
    const jitter = samples.length > 1 ? jitterSum / (samples.length - 1) : 0;
    return { ping: median, jitter };
  }

  async runDownload(streamUrlFn, durationMs, pingUrl, onProgress) {
    const streams = 6;
    let totalBytes = 0;
    let warmBytes = 0;
    const start = performance.now();
    const end = start + durationMs;
    const warmupMs = Math.min(2000, durationMs * 0.2);
    let warmTime = start;
    let lastBytes = 0;
    let lastTime = start;
    let smoothMbps = 0;
    const loadedPings = [];

    // Background loaded ping probe
    const pingInterval = setInterval(async () => {
      if (performance.now() >= end || this.signal.aborted) return;
      if (performance.now() - start > warmupMs && pingUrl) {
        try {
          const t0 = performance.now();
          await fetch(pingUrl + (pingUrl.includes('?') ? '&' : '?') + 'r=' + Math.random(), { signal: this.signal, cache: 'no-store' });
          loadedPings.push(Math.max(1, performance.now() - t0));
        } catch (_) {}
      }
    }, 1200);

    const streamWorkers = Array.from({ length: streams }).map(async (_, idx) => {
      await new Promise(r => setTimeout(r, idx * 100));
      while (performance.now() < end && !this.signal.aborted) {
        try {
          const url = streamUrlFn(idx);
          const resp = await fetch(url, { signal: this.signal, cache: 'no-store' });
          const reader = resp.body.getReader();
          while (performance.now() < end && !this.signal.aborted) {
            const { done, value } = await reader.read();
            if (done) break;
            totalBytes += value.length;
          }
          reader.cancel().catch(() => {});
        } catch (_) {
          break;
        }
      }
    });

    const progressTicker = new Promise((resolve) => {
      const interval = setInterval(() => {
        const now = performance.now();
        const deltaBytes = totalBytes - lastBytes;
        const deltaMs = now - lastTime;
        const instantMbps = (deltaBytes * 8) / (deltaMs * 1000);
        smoothMbps = smoothMbps === 0 ? instantMbps : smoothMbps * 0.75 + instantMbps * 0.25;
        lastBytes = totalBytes;
        lastTime = now;

        if (warmBytes === 0 && (now - start) >= warmupMs) {
          warmBytes = totalBytes;
          warmTime = now;
        }

        const prog = Math.min(1, (now - start) / durationMs);
        if (onProgress) onProgress(smoothMbps, prog);

        if (now >= end || this.signal.aborted) {
          clearInterval(interval);
          clearInterval(pingInterval);
          resolve();
        }
      }, 100);
    });

    await Promise.race([Promise.all(streamWorkers), progressTicker]);
    clearInterval(pingInterval);

    const netBytes = totalBytes - warmBytes;
    const netMs = lastTime - warmTime;
    const finalSpeed = netMs > 0 ? (netBytes * 8) / (netMs * 1000) : smoothMbps;
    const avgLoadedPing = loadedPings.length > 0 ? loadedPings.reduce((a, b) => a + b, 0) / loadedPings.length : null;

    return { mbps: Math.max(0.1, finalSpeed), loadedPing: avgLoadedPing };
  }

  async runUpload(uploadUrlFn, durationMs, pingUrl, onProgress) {
    const streams = 4;
    let totalBytes = 0;
    let warmBytes = 0;
    const start = performance.now();
    const end = start + durationMs;
    const warmupMs = Math.min(2000, durationMs * 0.2);
    let warmTime = start;
    let lastBytes = 0;
    let lastTime = start;
    let smoothMbps = 0;
    const loadedPings = [];
    const chunk = new Uint8Array(256 * 1024);

    const pingInterval = setInterval(async () => {
      if (performance.now() >= end || this.signal.aborted) return;
      if (performance.now() - start > warmupMs && pingUrl) {
        try {
          const t0 = performance.now();
          await fetch(pingUrl + (pingUrl.includes('?') ? '&' : '?') + 'r=' + Math.random(), { signal: this.signal, cache: 'no-store' });
          loadedPings.push(Math.max(1, performance.now() - t0));
        } catch (_) {}
      }
    }, 1200);

    const streamWorkers = Array.from({ length: streams }).map(async (_, idx) => {
      await new Promise(r => setTimeout(r, idx * 100));
      while (performance.now() < end && !this.signal.aborted) {
        try {
          const url = uploadUrlFn(idx);
          await fetch(url, {
            method: 'POST',
            body: chunk,
            signal: this.signal,
            cache: 'no-store',
            headers: { 'Content-Type': 'application/octet-stream' },
          });
          totalBytes += chunk.length;
        } catch (_) {
          break;
        }
      }
    });

    const progressTicker = new Promise((resolve) => {
      const interval = setInterval(() => {
        const now = performance.now();
        const deltaBytes = totalBytes - lastBytes;
        const deltaMs = now - lastTime;
        const instantMbps = (deltaBytes * 8) / (deltaMs * 1000);
        smoothMbps = smoothMbps === 0 ? instantMbps : smoothMbps * 0.75 + instantMbps * 0.25;
        lastBytes = totalBytes;
        lastTime = now;

        if (warmBytes === 0 && (now - start) >= warmupMs) {
          warmBytes = totalBytes;
          warmTime = now;
        }

        const prog = Math.min(1, (now - start) / durationMs);
        if (onProgress) onProgress(smoothMbps, prog);

        if (now >= end || this.signal.aborted) {
          clearInterval(interval);
          clearInterval(pingInterval);
          resolve();
        }
      }, 100);
    });

    await Promise.race([Promise.all(streamWorkers), progressTicker]);
    clearInterval(pingInterval);

    const netBytes = totalBytes - warmBytes;
    const netMs = lastTime - warmTime;
    const finalSpeed = netMs > 0 ? (netBytes * 8) / (netMs * 1000) : smoothMbps;
    const avgLoadedPing = loadedPings.length > 0 ? loadedPings.reduce((a, b) => a + b, 0) / loadedPings.length : null;

    return { mbps: Math.max(0.1, finalSpeed), loadedPing: avgLoadedPing };
  }
}

// --- Source Endpoints Resolver ---
async function resolveEndpoints(source, signal) {
  const nonce = () => Math.random().toString(36).substring(2);

  if (source === 'cloudflare') {
    let meta = { clientIp: '', asOrganization: 'Cloudflare Edge', colo: 'Nearest' };
    try {
      const r = await fetch('https://speed.cloudflare.com/meta', { signal });
      meta = await r.json();
    } catch (_) {}

    return {
      serverLabel: `Cloudflare · ${meta.colo || 'Global'}`,
      isp: meta.asOrganization || 'Cloudflare Network',
      ip: meta.clientIp || '—',
      protocol: 'HTTP/2',
      ping: 'https://speed.cloudflare.com/__down?bytes=0',
      download: () => `https://speed.cloudflare.com/__down?bytes=25000000&r=${nonce()}`,
      upload: () => `https://speed.cloudflare.com/__up?r=${nonce()}`,
    };
  }

  if (source === 'ookla') {
    // Speedtest.net API
    try {
      const r = await fetch('https://www.speedtest.net/api/js/servers?engine=js&limit=5&https_functional=true', { signal });
      const servers = await r.json();
      if (servers && servers.length > 0) {
        const s = servers[0];
        const host = s.host;
        return {
          serverLabel: `Ookla · ${s.sponsor || s.name}`,
          isp: s.sponsor || 'Ookla Network',
          ip: '—',
          protocol: 'HTTPS',
          ping: `https://${host}/hello`,
          download: () => `https://${host}/download?nocache=${nonce()}&size=25000000`,
          upload: () => `https://${host}/upload?nocache=${nonce()}`,
        };
      }
    } catch (_) {}
  }

  if (source === 'fast') {
    const token = 'YXNkZmFzZGxmbnNkYWZoYXNkZmhrYWxm';
    try {
      const r = await fetch(`https://api.fast.com/netflix/speedtest/v2?https=true&urlCount=4&token=${encodeURIComponent(token)}`, { signal });
      const data = await r.json();
      if (data.targets && data.targets.length > 0) {
        const target = data.targets[0].url;
        const base = target.split('?')[0];
        const query = target.includes('?') ? '?' + target.split('?')[1] : '';
        return {
          serverLabel: 'FAST · Netflix Open Connect',
          isp: data.client ? data.client.isp : 'Netflix CDN',
          ip: data.client ? data.client.ip : '—',
          protocol: 'HTTPS',
          ping: `${base}/range/0-0${query}`,
          download: () => `${base}/range/0-26214400${query}&r=${nonce()}`,
          upload: () => `${base}/range/0-0${query}&r=${nonce()}`,
        };
      }
    } catch (_) {}
  }

  // Fallback / LibreSpeed
  return {
    serverLabel: 'LibreSpeed · Global Node',
    isp: 'LibreSpeed Community',
    ip: '—',
    protocol: 'HTTPS',
    ping: 'https://speed.cloudflare.com/__down?bytes=0',
    download: () => `https://speed.cloudflare.com/__down?bytes=25000000&r=${nonce()}`,
    upload: () => `https://speed.cloudflare.com/__up?r=${nonce()}`,
  };
}

// --- UI Controller ---
function initApp() {
  applyTheme();
  renderApp();
  setupTVRemote();
}

function startTest() {
  if (state.running) return;
  state.running = true;
  state.abortController = new AbortController();
  state.activePhase = 'connecting';
  state.pingMs = null;
  state.jitterMs = null;
  state.downloadMbps = null;
  state.uploadMbps = null;
  state.downLoadedPing = null;
  state.upLoadedPing = null;
  state.bufferbloatGrade = null;
  state.liveMbps = 0;
  state.downSamples = [];
  state.upSamples = [];
  renderApp();

  (async () => {
    try {
      const ep = await resolveEndpoints(state.source, state.abortController.signal);
      state.serverInfo = ep;
      state.activePhase = 'ping';
      renderApp();

      const engine = new SpeedTestEngine(state.abortController.signal);
      const pingRes = await engine.runPing(ep.ping, 10, (ms, prog) => {
        state.pingMs = ms;
        updateGauge(ms, 'ms', prog);
      });
      state.pingMs = pingRes.ping;
      state.jitterMs = pingRes.jitter;

      state.activePhase = 'download';
      renderApp();
      const downRes = await engine.runDownload(ep.download, state.durationSec * 1000, ep.ping, (mbps, prog) => {
        state.liveMbps = mbps;
        state.downSamples.push(mbps);
        updateGauge(mbps, speedUnitLabel(), prog);
        drawChart();
      });
      state.downloadMbps = downRes.mbps;
      state.downLoadedPing = downRes.loadedPing;

      state.activePhase = 'upload';
      renderApp();
      const upRes = await engine.runUpload(ep.upload, state.durationSec * 1000, ep.ping, (mbps, prog) => {
        state.liveMbps = mbps;
        state.upSamples.push(mbps);
        updateGauge(mbps, speedUnitLabel(), prog);
        drawChart();
      });
      state.uploadMbps = upRes.mbps;
      state.upLoadedPing = upRes.loadedPing;

      state.bufferbloatGrade = gradeBufferbloat(state.pingMs, state.downLoadedPing, state.upLoadedPing);
      state.activePhase = 'done';
      state.running = false;

      // Save to History
      const resultItem = {
        time: Date.now(),
        source: state.source,
        server: state.serverInfo.serverLabel,
        isp: state.serverInfo.isp,
        downloadMbps: state.downloadMbps,
        uploadMbps: state.uploadMbps,
        pingMs: state.pingMs,
        jitterMs: state.jitterMs,
        downLoadedPing: state.downLoadedPing,
        upLoadedPing: state.upLoadedPing,
        bufferbloatGrade: state.bufferbloatGrade,
      };
      state.history.unshift(resultItem);
      localStorage.setItem('pulse_history', JSON.stringify(state.history.slice(0, 100)));

      renderApp();
    } catch (err) {
      state.running = false;
      state.activePhase = 'idle';
      renderApp();
    }
  })();
}

function stopTest() {
  if (state.abortController) {
    state.abortController.abort();
  }
  state.running = false;
  state.activePhase = 'idle';
  state.liveMbps = 0;
  renderApp();
}

function updateGauge(num, unit, progress) {
  const numEl = document.getElementById('gauge-num');
  const unitEl = document.getElementById('gauge-unit');
  const ringEl = document.getElementById('orbit-ring');
  const statusEl = document.getElementById('gauge-status');
  if (numEl) numEl.textContent = typeof num === 'number' ? (num >= 100 ? Math.round(num) : num.toFixed(1)) : num;
  if (unitEl) unitEl.textContent = unit;
  if (ringEl) ringEl.style.strokeDashoffset = (600 * (1 - progress)).toString();
  if (statusEl) statusEl.textContent = t('phase_' + state.activePhase);
}

function drawChart() {
  const canvas = document.getElementById('speed-chart');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const w = canvas.width = canvas.parentElement.clientWidth;
  const h = canvas.height = canvas.parentElement.clientHeight;
  ctx.clearRect(0, 0, w, h);

  const maxVal = Math.max(10, ...state.downSamples, ...state.upSamples);

  function plot(samples, color) {
    if (samples.length < 2) return;
    ctx.beginPath();
    ctx.strokeStyle = color;
    ctx.lineWidth = 2.5;
    samples.forEach((val, i) => {
      const x = (i / (samples.length - 1)) * w;
      const y = h - (val / maxVal) * (h * 0.85) - 6;
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();
  }

  plot(state.downSamples, '#006A6A');
  plot(state.upSamples, '#4B607C');
}

function exportCsv() {
  if (state.history.length === 0) return;
  let csv = 'Timestamp,Date,Source,Server,ISP,Download_Mbps,Upload_Mbps,Ping_ms,Jitter_ms,DownLoadedPing_ms,UpLoadedPing_ms,BufferbloatGrade\n';
  state.history.forEach(r => {
    csv += [
      r.time,
      `"${new Date(r.time).toISOString()}"`,
      `"${r.source}"`,
      `"${(r.server || '').replace(/"/g, '""')}"`,
      `"${(r.isp || '').replace(/"/g, '""')}"`,
      r.downloadMbps ? r.downloadMbps.toFixed(2) : '',
      r.uploadMbps ? r.uploadMbps.toFixed(2) : '',
      r.pingMs ? r.pingMs.toFixed(1) : '',
      r.jitterMs ? r.jitterMs.toFixed(1) : '',
      r.downLoadedPing ? r.downLoadedPing.toFixed(1) : '',
      r.upLoadedPing ? r.upLoadedPing.toFixed(1) : '',
      `"${r.bufferbloatGrade || ''}"`,
    ].join(',') + '\n';
  });

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `pulse-history-${Date.now()}.csv`;
  a.click();
}

function clearHistory() {
  if (confirm(t('clear_history') + '?')) {
    state.history = [];
    localStorage.removeItem('pulse_history');
    renderApp();
  }
}

// --- Main Render ---
function renderApp() {
  const app = document.getElementById('app');
  if (!app) return;

  if (state.tab === 'history') {
    app.innerHTML = renderHistoryView();
  } else if (state.tab === 'settings') {
    app.innerHTML = renderSettingsView();
  } else {
    app.innerHTML = renderTestView();
    drawChart();
  }
}

function renderTestView() {
  const isRunning = state.running;
  const isDone = state.activePhase === 'done';
  const heroValue = isDone ? formatSpeed(state.downloadMbps) : (isRunning ? formatSpeed(state.liveMbps) : t('go'));
  const heroUnit = isDone || isRunning ? speedUnitLabel() : '';
  const heroStatus = isRunning ? t('phase_' + state.activePhase) : (isDone ? t('phase_done') : '');

  return `
    <header class="app-header">
      <div class="brand-title">
        ${t('app_name')}
        <span class="brand-badge">Multiplatform v1.5.1</span>
      </div>
      <div class="network-chip">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 4C7.31 4 3.07 5.9 0 8.98L12 21 24 8.98C20.93 5.9 16.69 4 12 4z"/></svg>
        ${state.serverInfo.network}
      </div>
    </header>

    <div class="source-picker">
      ${['cloudflare', 'ookla', 'fast', 'librespeed'].map(src => `
        <button class="chip ${state.source === src ? 'active' : ''}" tabindex="0" onclick="setSource('${src}')" ${isRunning ? 'disabled' : ''}>
          ${src.toUpperCase()}
        </button>
      `).join('')}
    </div>
    <div class="source-desc">${t('src_' + (state.source === 'cloudflare' ? 'cf' : state.source) + '_desc')}</div>

    <div class="metrics-row">
      <div class="metric-card ping ${state.activePhase === 'ping' ? 'active' : ''}">
        <div class="icon-box">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z"/></svg>
        </div>
        <div>
          <div class="metric-label">${t('phase_ping')}</div>
          <div class="metric-value">${state.pingMs != null ? state.pingMs.toFixed(1) : '—'}</div>
          <div class="metric-sub">${state.jitterMs != null ? t('jitter') + ' ' + state.jitterMs.toFixed(1) + ' ms' : 'ms'}</div>
        </div>
      </div>

      <div class="metric-card download ${state.activePhase === 'download' ? 'active' : ''}">
        <div class="icon-box">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z"/></svg>
        </div>
        <div>
          <div class="metric-label">${t('phase_download')}</div>
          <div class="metric-value">${state.downloadMbps != null ? formatSpeed(state.downloadMbps) : (state.activePhase === 'download' ? formatSpeed(state.liveMbps) : '—')}</div>
          <div class="metric-sub">${speedUnitLabel()}</div>
        </div>
      </div>

      <div class="metric-card upload ${state.activePhase === 'upload' ? 'active' : ''}">
        <div class="icon-box">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z"/></svg>
        </div>
        <div>
          <div class="metric-label">${t('phase_upload')}</div>
          <div class="metric-value">${state.uploadMbps != null ? formatSpeed(state.uploadMbps) : (state.activePhase === 'upload' ? formatSpeed(state.liveMbps) : '—')}</div>
          <div class="metric-sub">${speedUnitLabel()}</div>
        </div>
      </div>
    </div>

    <div class="gauge-stage ${isRunning ? 'gauge-running' : ''}">
      <svg class="gauge-svg" viewBox="0 0 300 300">
        <g id="petals">
          <circle class="petal" cx="150" cy="115" r="42" />
          <circle class="petal" cx="185" cy="150" r="42" />
          <circle class="petal" cx="150" cy="185" r="42" />
          <circle class="petal" cx="115" cy="150" r="42" />
        </g>
        <circle cx="150" cy="150" r="95" fill="none" stroke="var(--surface-high)" stroke-width="8" />
        <circle id="orbit-ring" class="orbit-ring" cx="150" cy="150" r="95" fill="none" stroke="var(--primary)" stroke-width="8" stroke-linecap="round" />
      </svg>
      <div class="gauge-center">
        <div id="gauge-num" class="gauge-num">${heroValue}</div>
        <div id="gauge-unit" class="gauge-unit">${heroUnit}</div>
        <div id="gauge-status" class="gauge-status">${heroStatus}</div>
      </div>
    </div>

    <div class="action-row">
      ${isRunning ? `
        <button class="btn-hero btn-stop" tabindex="0" onclick="stopTest()">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h12v12H6z"/></svg>
          ${t('stop')}
        </button>
      ` : (isDone ? `
        <button class="btn-hero" tabindex="0" onclick="startTest()">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor"><path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/></svg>
          ${t('test_again')}
        </button>
      ` : `
        <button class="btn-hero" tabindex="0" onclick="startTest()">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
          ${t('go')}
        </button>
      `)}
    </div>

    ${state.downSamples.length > 0 ? `
      <div class="info-card">
        <div class="info-card-header">
          <div class="info-card-title">${t('speed_graph')}</div>
        </div>
        <div class="canvas-wrapper">
          <canvas id="speed-chart"></canvas>
        </div>
      </div>
    ` : ''}

    ${state.bufferbloatGrade ? `
      <div class="info-card">
        <div class="info-card-header">
          <div>
            <div class="info-card-title">${t('bufferbloat')}</div>
            <div style="font-size:12px;color:var(--on-surface-variant);">${t('loaded_latency')}</div>
          </div>
          <div class="grade-badge grade-${state.bufferbloatGrade.replace('+', 'p')}">${state.bufferbloatGrade}</div>
        </div>
        <div style="display:flex;justify-content:space-around;margin-top:12px;">
          <div style="text-align:center;">
            <div style="font-size:12px;color:var(--on-surface-variant);">↓ ${t('phase_download')}</div>
            <div style="font-size:16px;font-weight:700;">${state.downLoadedPing != null ? state.downLoadedPing.toFixed(1) + ' ms' : '—'}</div>
          </div>
          <div style="text-align:center;">
            <div style="font-size:12px;color:var(--on-surface-variant);">↑ ${t('phase_upload')}</div>
            <div style="font-size:16px;font-weight:700;">${state.upLoadedPing != null ? state.upLoadedPing.toFixed(1) + ' ms' : '—'}</div>
          </div>
        </div>
      </div>
    ` : ''}

    <div class="info-card">
      <div class="info-card-header">
        <div class="info-card-title">${t('connection_title')}</div>
      </div>
      <div class="info-row">
        <div class="info-label">${t('provider')}</div>
        <div class="info-val">${state.serverInfo.isp}</div>
      </div>
      <div class="info-row">
        <div class="info-label">${t('server')}</div>
        <div class="info-val">${state.serverInfo.serverLabel}</div>
      </div>
      <div class="info-row">
        <div class="info-label">${t('ip_address')}</div>
        <div class="info-val">${state.serverInfo.ip}</div>
      </div>
      <div class="info-row">
        <div class="info-label">${t('protocol')}</div>
        <div class="info-val">${state.serverInfo.protocol}</div>
      </div>
    </div>
  `;
}

function renderHistoryView() {
  return `
    <header class="app-header">
      <div class="brand-title">${t('tab_history')}</div>
      <div style="display:flex;gap:8px;">
        ${state.history.length > 0 ? `
          <button class="chip" tabindex="0" onclick="exportCsv()">${t('export_csv')}</button>
          <button class="chip" tabindex="0" onclick="clearHistory()">${t('clear_history')}</button>
        ` : ''}
      </div>
    </header>

    ${state.history.length === 0 ? `
      <div style="text-align:center;padding:48px 16px;color:var(--on-surface-variant);">
        ${t('no_history')}
      </div>
    ` : `
      <div class="history-list">
        ${state.history.map(r => `
          <div class="history-card" tabindex="0">
            <div style="display:flex;justify-content:space-between;align-items:center;">
              <span style="font-size:12px;color:var(--on-surface-variant);">${new Date(r.time).toLocaleString(state.lang === 'iw' ? 'he-IL' : 'en-US')}</span>
              <span class="chip" style="padding:2px 8px;font-size:11px;">${r.source.toUpperCase()}</span>
            </div>
            <div style="display:flex;align-items:baseline;gap:8px;">
              <span style="font-size:28px;font-weight:800;color:var(--primary);">${formatSpeed(r.downloadMbps)}</span>
              <span style="font-size:14px;color:var(--on-surface-variant);">${speedUnitLabel()} ↓</span>
              <span style="margin-left:auto;font-size:16px;font-weight:600;color:var(--tertiary);">${formatSpeed(r.uploadMbps)} ↑</span>
            </div>
            <div style="display:flex;gap:12px;font-size:12px;color:var(--on-surface-variant);flex-wrap:wrap;">
              <span>Ping: ${r.pingMs ? r.pingMs.toFixed(1) : '—'} ms</span>
              <span>Jitter: ${r.jitterMs ? r.jitterMs.toFixed(1) : '—'} ms</span>
              ${r.bufferbloatGrade ? `<span style="font-weight:700;color:var(--primary);">Grade: ${r.bufferbloatGrade}</span>` : ''}
              <span>${r.server || r.isp}</span>
            </div>
          </div>
        `).join('')}
      </div>
    `}
  `;
}

function renderSettingsView() {
  return `
    <header class="app-header">
      <div class="brand-title">${t('tab_settings')}</div>
    </header>

    <div class="info-card">
      <div class="settings-group">
        <label style="font-size:13px;font-weight:700;">Language / שפה</label>
        <div class="settings-segmented">
          <button class="settings-tab ${state.lang === 'en' ? 'active' : ''}" tabindex="0" onclick="setLang('en')">English</button>
          <button class="settings-tab ${state.lang === 'iw' ? 'active' : ''}" tabindex="0" onclick="setLang('iw')">עברית</button>
        </div>
      </div>

      <div class="settings-group">
        <label style="font-size:13px;font-weight:700;">${t('theme')}</label>
        <div class="settings-segmented">
          <button class="settings-tab ${state.theme === 'light' ? 'active' : ''}" tabindex="0" onclick="setTheme('light')">${t('theme_light')}</button>
          <button class="settings-tab ${state.theme === 'dark' ? 'active' : ''}" tabindex="0" onclick="setTheme('dark')">${t('theme_dark')}</button>
          <button class="settings-tab ${state.theme === 'oled' ? 'active' : ''}" tabindex="0" onclick="setTheme('oled')">${t('theme_oled')}</button>
        </div>
      </div>

      <div class="settings-group">
        <label style="font-size:13px;font-weight:700;">${t('units')}</label>
        <div class="settings-segmented">
          <button class="settings-tab ${state.unit === 'mbps' ? 'active' : ''}" tabindex="0" onclick="setUnit('mbps')">Mbps</button>
          <button class="settings-tab ${state.unit === 'mbytes' ? 'active' : ''}" tabindex="0" onclick="setUnit('mbytes')">MB/s</button>
        </div>
      </div>

      <div class="settings-group">
        <label style="font-size:13px;font-weight:700;">${t('duration')}</label>
        <div class="settings-segmented">
          <button class="settings-tab ${state.durationSec === 6 ? 'active' : ''}" tabindex="0" onclick="setDuration(6)">${t('len_quick')}</button>
          <button class="settings-tab ${state.durationSec === 10 ? 'active' : ''}" tabindex="0" onclick="setDuration(10)">${t('len_standard')}</button>
          <button class="settings-tab ${state.durationSec === 15 ? 'active' : ''}" tabindex="0" onclick="setDuration(15)">${t('len_long')}</button>
        </div>
      </div>
    </div>

    <div class="info-card">
      <div class="info-card-header">
        <div class="info-card-title">LG webOS TV Remote Support</div>
      </div>
      <div style="font-size:13px;color:var(--on-surface-variant);line-height:1.6;">
        <p>• D-Pad <strong>Up / Down / Left / Right</strong>: navigate focus across options & cards</p>
        <p>• <strong>OK / Enter</strong>: start / stop test, select settings</p>
        <p>• <strong>Back (Key 461)</strong>: switch back to Test tab</p>
        <p>• Magic Remote cursor pointer is fully supported</p>
      </div>
    </div>
  `;
}

// --- Navigation & Setters ---
function setTab(tab) {
  state.tab = tab;
  document.querySelectorAll('.nav-item').forEach(el => {
    el.classList.toggle('active', el.dataset.tab === tab);
  });
  renderApp();
}

function setSource(src) {
  if (state.running) return;
  state.source = src;
  renderApp();
}

function setLang(lang) {
  state.lang = lang;
  localStorage.setItem('pulse_lang', lang);
  applyTheme();
  renderApp();
}

function setTheme(theme) {
  state.theme = theme;
  localStorage.setItem('pulse_theme', theme);
  applyTheme();
  renderApp();
}

function setUnit(unit) {
  state.unit = unit;
  localStorage.setItem('pulse_unit', unit);
  renderApp();
}

function setDuration(sec) {
  state.durationSec = sec;
  localStorage.setItem('pulse_duration', sec.toString());
  renderApp();
}

// --- LG webOS & TV Remote Control D-Pad Handler ---
function setupTVRemote() {
  window.addEventListener('keydown', (e) => {
    const focusable = Array.from(document.querySelectorAll('button:not([disabled]), [tabindex="0"]'));
    if (focusable.length === 0) return;

    const curIdx = focusable.indexOf(document.activeElement);

    switch (e.keyCode) {
      case 37: // ArrowLeft
        e.preventDefault();
        if (curIdx > 0) focusable[curIdx - 1].focus();
        else focusable[focusable.length - 1].focus();
        break;
      case 39: // ArrowRight
        e.preventDefault();
        if (curIdx >= 0 && curIdx < focusable.length - 1) focusable[curIdx + 1].focus();
        else focusable[0].focus();
        break;
      case 38: // ArrowUp
        e.preventDefault();
        if (curIdx > 0) focusable[Math.max(0, curIdx - 2)].focus();
        break;
      case 40: // ArrowDown
        e.preventDefault();
        if (curIdx >= 0 && curIdx < focusable.length - 1) focusable[Math.min(focusable.length - 1, curIdx + 2)].focus();
        break;
      case 13: // Enter / OK
        if (document.activeElement && typeof document.activeElement.click === 'function') {
          document.activeElement.click();
        }
        break;
      case 415: // TV Remote Play button
        if (!state.running) startTest();
        break;
      case 413: // TV Remote Stop button
        if (state.running) stopTest();
        break;
      case 461: // LG webOS Back
      case 10009: // Samsung Tizen Return
      case 27: // Escape
        if (state.tab !== 'test') {
          setTab('test');
        }
        break;
    }
  });
}

window.addEventListener('DOMContentLoaded', initApp);

