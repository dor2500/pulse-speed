/*
 * Pulse for the web. A browser port of the Android app's UI (Material 3 Expressive) and test engine.
 * Speed test: Cloudflare / Ookla / FAST over fetch(). Ping: HTTPS round trips (browsers can't send ICMP).
 * Network scan, router ping and cloud sync need the Android app, and the page says so where they would be.
 */
(() => {
"use strict";

const VERSION = "1.7.0";
const RELEASES = "https://github.com/dor2500/pulse-speed/releases/latest";
const REPO = "https://github.com/dor2500/pulse-speed";
const RESULT_PAGE = new URL("../r/", location.href).href;

// ---------- Strings (same wording as the app's strings.xml) ----------
const STR = {
  en: {
    tab_test: "Test", tab_ping: "Ping", tab_history: "History", tab_settings: "Settings",
    go: "GO", tap_to_start: "Tap to start a speed test", phase_connecting: "Connecting…", phase_ping: "Ping",
    phase_download: "Download", phase_upload: "Upload", jitter: "Jitter", ms: "ms", test_again: "Test again", share: "Share",
    stop_test: "Stop test", graph_title: "Speed over time", connection_title: "Connection", provider: "Provider",
    ip_address: "IP address", server: "Server", network: "Network", protocol: "Protocol",
    net_online: "Online", net_offline: "Offline", net_wifi: "Wi‑Fi", net_cellular: "Cellular", net_ethernet: "Ethernet",
    error_title: "Test failed", error_offline: "No internet connection",
    error_generic: "Couldn’t reach the test server. Check your connection and try again.",
    error_source: "This server blocks tests from browsers. Try Cloudflare, or use the Android app.",
    src_locked: "Browsers can’t reach this test network. It’s available in the Android app.",
    src_cloudflare: "Cloudflare’s global network, nearest data center", src_ookla: "Ookla’s speedtest.net servers, fastest nearby server",
    src_fast: "Netflix servers, the same ones FAST.com uses", server_title: "Server",
    server_cf_note: "Automatic · Cloudflare routes you to its nearest data center", server_auto_long: "Automatic · fastest nearby",
    loaded_latency: "Loaded ping", bufferbloat: "Bufferbloat grade",
    bb_desc: { "A+": "Excellent: no lag under load", A: "Great for calls and gaming", B: "Good, with a little lag under load", C: "Noticeable lag when the line is busy", D: "Calls and games will stutter under load", F: "Severe lag whenever the line is busy" },
    history_empty_title: "No tests yet", history_empty_body: "Run a speed test and your results will show up here.",
    history_avg: "Average", history_best: "Best", tests: n => n === 1 ? "1 test" : `${n} tests`, clear_history: "Clear history",
    clear_history_confirm: "Delete all saved results?", delete: "Delete", export_csv: "Export CSV", share_link: "Share link",
    settings_appearance: "Appearance", settings_language: "Language", lang_system: "System", settings_theme: "Theme",
    theme_system: "System", theme_light: "Light", theme_dark: "Dark", theme_oled: "OLED Black", settings_test: "Speed test",
    settings_units: "Units", settings_duration: "Test length", len_quick: "Quick", len_standard: "Standard", len_long: "Thorough",
    settings_about: "About", about_version: "Version", about_engine_title: "Test servers",
    about_engine: "Cloudflare (in the browser). The Android app adds Ookla, FAST and LibreSpeed.", about_source: "Source code on GitHub",
    web_title: "Pulse for Android", web_desc: "Get the full app: real ICMP ping, network scan, cloud sync and in-app updates.",
    web_btn: "Download APK", web_badge: "Web",
    tool_ping: "Ping", tool_devices: "Network scan", ping_target_hint: "Host, domain or IP address", ping_group_dns: "Public DNS",
    ping_group_recent: "Recent", ping_method: "Method", ping_count: "Pings", ping_start: "Start ping", ping_stop: "Stop",
    ping_min: "Min", ping_avg: "Avg", ping_max: "Max", ping_loss: "Loss", ping_replies: "Replies", ping_last: v => `Last ${v} ms`,
    ping_timeout: "Timed out", ping_resolving: "Connecting…", ping_idle: "Pick a target and start",
    ping_web_note: "Browsers can’t send ICMP, so the web version times HTTPS round trips. Router and network-DNS pings are in the Android app.",
    ping_err_offline: "You’re offline. Connect to a network and try again.", ping_err_none: "No replies from that host.",
    scan_web_title: "Network scan is in the Android app", scan_web_body: "Browsers aren’t allowed to look at the devices on your local network. The Android app finds every device on your Wi‑Fi (router, phones, TVs, printers) in a few seconds.",
    copied: "Link copied", share_text: (d, u, p, j) => `Pulse speed test\n↓ Download ${d}\n↑ Upload ${u}\nPing ${p} ms · Jitter ${j} ms`,
  },
  iw: {
    tab_test: "בדיקה", tab_ping: "פינג", tab_history: "היסטוריה", tab_settings: "הגדרות",
    go: "GO", tap_to_start: "הקישו כדי להתחיל בדיקת מהירות", phase_connecting: "מתחבר…", phase_ping: "פינג",
    phase_download: "הורדה", phase_upload: "העלאה", jitter: "ג׳יטר", ms: "ms", test_again: "בדיקה חוזרת", share: "שיתוף",
    stop_test: "עצירת הבדיקה", graph_title: "מהירות לאורך הבדיקה", connection_title: "חיבור", provider: "ספק",
    ip_address: "כתובת IP", server: "שרת", network: "רשת", protocol: "פרוטוקול",
    net_online: "מחובר", net_offline: "לא מחובר", net_wifi: "Wi‑Fi", net_cellular: "סלולרי", net_ethernet: "אתרנט",
    error_title: "הבדיקה נכשלה", error_offline: "אין חיבור לאינטרנט",
    error_generic: "לא הצלחנו להגיע לשרת הבדיקה. בדקו את החיבור ונסו שוב.",
    error_source: "השרת הזה חוסם בדיקות מדפדפן. נסו את Cloudflare, או השתמשו באפליקציית האנדרואיד.",
    src_locked: "דפדפנים לא יכולים להגיע לרשת הבדיקה הזו. היא זמינה באפליקציית האנדרואיד.",
    src_cloudflare: "הרשת הגלובלית של Cloudflare, מרכז הנתונים הקרוב", src_ookla: "השרתים של speedtest.net (Ookla), השרת הקרוב והמהיר",
    src_fast: "השרתים של Netflix, אותם שרתים ש-FAST.com משתמש בהם", server_title: "שרת",
    server_cf_note: "אוטומטי · Cloudflare מנתבת למרכז הנתונים הקרוב", server_auto_long: "אוטומטי · הקרוב והמהיר",
    loaded_latency: "פינג בעומס", bufferbloat: "ציון עומס (Bufferbloat)",
    bb_desc: { "A+": "מצוין: אין השהיה בעומס", A: "מעולה לשיחות ולמשחקים", B: "טוב, עם מעט השהיה בעומס", C: "השהיה מורגשת כשהקו עמוס", D: "שיחות ומשחקים יקרטעו בעומס", F: "השהיה חמורה בכל פעם שהקו עמוס" },
    history_empty_title: "עוד אין בדיקות", history_empty_body: "הריצו בדיקת מהירות והתוצאות יופיעו כאן.",
    history_avg: "ממוצע", history_best: "שיא", tests: n => n === 1 ? "בדיקה אחת" : `${n} בדיקות`, clear_history: "ניקוי היסטוריה",
    clear_history_confirm: "למחוק את כל התוצאות השמורות?", delete: "מחיקה", export_csv: "ייצוא ל-CSV", share_link: "שיתוף קישור",
    settings_appearance: "מראה", settings_language: "שפה", lang_system: "מערכת", settings_theme: "ערכת נושא",
    theme_system: "מערכת", theme_light: "בהיר", theme_dark: "כהה", theme_oled: "OLED", settings_test: "בדיקת מהירות",
    settings_units: "יחידות", settings_duration: "אורך הבדיקה", len_quick: "מהירה", len_standard: "רגילה", len_long: "יסודית",
    settings_about: "אודות", about_version: "גרסה", about_engine_title: "שרתי בדיקה",
    about_engine: "Cloudflare (בדפדפן). באפליקציית האנדרואיד יש גם Ookla,‏ FAST ו-LibreSpeed.", about_source: "קוד המקור ב-GitHub",
    web_title: "Pulse לאנדרואיד", web_desc: "האפליקציה המלאה: פינג ICMP אמיתי, סריקת רשת, סנכרון בענן ועדכונים מתוך האפליקציה.",
    web_btn: "הורדת APK", web_badge: "ווב",
    tool_ping: "פינג", tool_devices: "סריקת רשת", ping_target_hint: "שם מארח, דומיין או כתובת IP", ping_group_dns: "DNS ציבורי",
    ping_group_recent: "אחרונים", ping_method: "שיטה", ping_count: "מספר פינגים", ping_start: "התחלת פינג", ping_stop: "עצירה",
    ping_min: "מינימום", ping_avg: "ממוצע", ping_max: "מקסימום", ping_loss: "אובדן", ping_replies: "תשובות", ping_last: v => `אחרון ${v} ms`,
    ping_timeout: "אין תשובה", ping_resolving: "מתחבר…", ping_idle: "בחרו יעד והתחילו",
    ping_web_note: "דפדפנים לא יכולים לשלוח ICMP, ולכן גרסת הווב מודדת זמן הלוך־חזור ב-HTTPS. פינג לנתב ול-DNS של הרשת זמין באפליקציית האנדרואיד.",
    ping_err_offline: "אין חיבור לרשת. התחברו ונסו שוב.", ping_err_none: "המארח הזה לא ענה.",
    scan_web_title: "סריקת רשת זמינה באפליקציית האנדרואיד", scan_web_body: "דפדפנים לא מורשים לראות את המכשירים ברשת המקומית. אפליקציית האנדרואיד מוצאת תוך שניות כל מכשיר ברשת ה-Wi‑Fi: נתב, טלפונים, טלוויזיות ומדפסות.",
    copied: "הקישור הועתק", share_text: (d, u, p, j) => `בדיקת מהירות Pulse\n↓ הורדה ${d}\n↑ העלאה ${u}\nפינג ${p} ms · ג׳יטר ${j} ms`,
  },
};

// ---------- Persistence ----------
const store = {
  get(key, fallback) { try { const v = localStorage.getItem(key); return v == null ? fallback : JSON.parse(v); } catch (e) { return fallback; } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) {} },
};
const settings = Object.assign({ lang: "system", theme: "system", unit: "mbps", length: 10 }, store.get("pulse_web_settings", {}), { source: "cloudflare" });
const saveSettings = () => store.set("pulse_web_settings", settings);
let history = store.get("pulse_web_history", []);
const saveHistory = () => store.set("pulse_web_history", history.slice(0, 200));

const langCode = () => settings.lang !== "system" ? settings.lang : /^(he|iw)/i.test(navigator.language || "") ? "iw" : "en";
let S = STR[langCode()];

function applyLang() {
  S = STR[langCode()];
  document.documentElement.lang = langCode() === "iw" ? "he" : "en";
  document.documentElement.dir = langCode() === "iw" ? "rtl" : "ltr";
}
function applyTheme() {
  const t = settings.theme;
  const dark = t === "dark" || t === "oled" || (t === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = t === "oled" ? "oled" : dark ? "dark" : "light";
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = getComputedStyle(document.documentElement).getPropertyValue("--surface").trim();
}
matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => { if (settings.theme === "system") applyTheme(); });

// ---------- Formatting ----------
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const num = v => typeof v === "number" && isFinite(v);
const conv = mbps => settings.unit === "mbytes" ? mbps / 8 : mbps;
const unitLabel = () => settings.unit === "mbytes" ? "MB/s" : "Mbps";
const fmtNum = v => v < 10 ? v.toFixed(2) : v < 100 ? v.toFixed(1) : v.toFixed(0);
const fmtSpeed = mbps => fmtNum(conv(mbps));
const fmtMs = ms => ms < 10 ? ms.toFixed(1) : ms.toFixed(0);
const icon = (name, cls = "") => `<span class="ms ${cls}" aria-hidden="true">${name}</span>`;
const $ = id => document.getElementById(id);
const css = name => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

function toast(text) {
  const el = $("toast");
  el.textContent = text;
  el.classList.add("show");
  clearTimeout(toast.t);
  toast.t = setTimeout(() => el.classList.remove("show"), 2200);
}

function netKind() {
  if (!navigator.onLine) return { label: S.net_offline, icon: "wifi_off" };
  const type = navigator.connection && navigator.connection.type;
  if (type === "wifi") return { label: S.net_wifi, icon: "wifi" };
  if (type === "cellular") return { label: S.net_cellular, icon: "signal_cellular_alt" };
  if (type === "ethernet") return { label: S.net_ethernet, icon: "settings_ethernet" };
  return { label: S.net_online, icon: "language" };
}

// ---------- Speed test engine (same method as the app: warm-up discarded, multi-stream) ----------
// Ookla, FAST and LibreSpeed refuse cross-origin requests from web pages (no CORS headers), so in the browser
// only Cloudflare can run; the others are shown locked, as in the app, with a pointer to the Android version.
const SOURCES = [
  { id: "cloudflare", name: "Cloudflare", desc: () => S.src_cloudflare },
  { id: "ookla", name: "Ookla", locked: true },
  { id: "fast", name: "FAST", locked: true },
  { id: "librespeed", name: "LibreSpeed", locked: true },
];
const nonce = () => Math.random().toString(36).slice(2);
const withQ = (url, extra) => url + (url.includes("?") ? "&" : "?") + extra;

class SourceBlocked extends Error {}

async function resolveEndpoints(source, signal) {
  if (source === "ookla") {
    let servers;
    try {
      servers = await (await fetch("https://www.speedtest.net/api/js/servers?engine=js&limit=5&https_functional=true", { signal })).json();
    } catch (e) { if (signal.aborted) throw e; throw new SourceBlocked(); }
    const s = servers && servers[0];
    if (!s) throw new SourceBlocked();
    const host = s.host.includes("://") ? new URL(s.host).host : s.host;
    return {
      serverLabel: `${s.sponsor} · ${s.name}`, isp: "", ip: "", protocol: "HTTPS",
      ping: `https://${host}/hello`,
      download: () => `https://${host}/download?nocache=${nonce()}&size=25000000`,
      upload: () => `https://${host}/upload?nocache=${nonce()}`,
    };
  }
  if (source === "fast") {
    let data;
    try {
      data = await (await fetch("https://api.fast.com/netflix/speedtest/v2?https=true&urlCount=5&token=YXNkZmFzZGxmbnNkYWZoYXNkZmhrYWxm", { signal })).json();
    } catch (e) { if (signal.aborted) throw e; throw new SourceBlocked(); }
    const targets = (data && data.targets) || [];
    if (!targets.length) throw new SourceBlocked();
    const at = i => { const u = new URL(targets[i % targets.length].url); return u; };
    const range = (i, bytes) => { const u = at(i); u.pathname += `/range/0-${bytes}`; u.searchParams.set("r", nonce()); return u.href; };
    const loc = targets[0].location || {};
    return {
      serverLabel: `FAST · ${[loc.city, loc.country].filter(Boolean).join(", ") || "Netflix"}`,
      isp: (data.client && data.client.isp) || "", ip: (data.client && data.client.ip) || "", protocol: "HTTPS",
      ping: range(0, 0),
      download: i => range(i, 26214400),
      upload: i => range(i, 0),
    };
  }
  let meta = {};
  try { meta = await (await fetch("https://speed.cloudflare.com/meta", { signal })).json(); } catch (e) { if (signal.aborted) throw e; }
  // meta.city is the client's city; the data center is meta.colo ({ iata, city } now, a plain IATA code in older responses).
  const colo = meta.colo && typeof meta.colo === "object" ? meta.colo : { iata: meta.colo };
  const place = [colo.city, colo.iata && `(${colo.iata})`].filter(Boolean).join(" ");
  return {
    serverLabel: `Cloudflare${place ? " · " + place : ""}`,
    isp: meta.asOrganization || "", ip: meta.clientIp || "", protocol: meta.httpProtocol || "HTTP/2",
    ping: "https://speed.cloudflare.com/__down?bytes=0",
    download: () => `https://speed.cloudflare.com/__down?bytes=25000000&r=${nonce()}`,
    upload: () => `https://speed.cloudflare.com/__up?r=${nonce()}`,
  };
}

async function timedFetch(url, signal) {
  const t0 = performance.now();
  const r = await fetch(withQ(url, "r=" + nonce()), { signal, cache: "no-store" });
  let ms = performance.now() - t0;
  const st = r.headers.get("Server-Timing");
  const m = st && /dur=([0-9.]+)/.exec(st);
  if (m) ms -= parseFloat(m[1]);
  try { await r.arrayBuffer(); } catch (e) {}
  return Math.max(0.5, ms);
}

async function measurePing(ep, signal, onSample) {
  try { await timedFetch(ep.ping, signal); } catch (e) { if (signal.aborted) throw e; }
  const samples = [];
  for (let i = 0; i < 12; i++) {
    if (signal.aborted) throw new DOMException("Aborted", "AbortError");
    try { const ms = await timedFetch(ep.ping, signal); samples.push(ms); onSample(ms, (i + 1) / 12); } catch (e) { if (signal.aborted) throw e; }
  }
  if (!samples.length) throw new Error("ping failed");
  const sorted = [...samples].sort((a, b) => a - b);
  let j = 0;
  for (let i = 1; i < samples.length; i++) j += Math.abs(samples[i] - samples[i - 1]);
  return { ping: sorted[Math.floor(sorted.length / 2)], jitter: samples.length > 1 ? j / (samples.length - 1) : 0 };
}

async function measureTransfer(kind, ep, durationMs, signal, onSample) {
  const streams = kind === "down" ? 6 : 4;
  const chunk = kind === "up" ? new Uint8Array(1024 * 1024) : null;
  let bytes = 0, warmBytes = 0, warmAt = 0, lastBytes = 0, lastAt = 0, smooth = 0;
  const start = performance.now(), end = start + durationMs, warmup = Math.min(2000, durationMs * 0.2);
  lastAt = start;
  const loaded = [];
  const local = new AbortController();
  const abortAll = () => local.abort();
  signal.addEventListener("abort", abortAll);

  const pinger = (async () => {
    await new Promise(r => setTimeout(r, warmup));
    while (performance.now() < end && !local.signal.aborted) {
      try { loaded.push(await timedFetch(ep.ping, local.signal)); } catch (e) {}
      await new Promise(r => setTimeout(r, 400));
    }
  })();

  const workers = Array.from({ length: streams }, async (_, idx) => {
    await new Promise(r => setTimeout(r, idx * 80));
    while (performance.now() < end && !local.signal.aborted) {
      try {
        if (kind === "down") {
          const r = await fetch(ep.download(idx), { signal: local.signal, cache: "no-store" });
          const reader = r.body.getReader();
          for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            bytes += value.length;
            if (performance.now() >= end) { reader.cancel().catch(() => {}); break; }
          }
        } else {
          await fetch(ep.upload(idx), { method: "POST", body: chunk, signal: local.signal, cache: "no-store" });
          bytes += chunk.length;
        }
      } catch (e) {
        if (local.signal.aborted) return;
        await new Promise(r => setTimeout(r, 200));
      }
    }
  });

  await new Promise(resolve => {
    const tick = setInterval(() => {
      const now = performance.now();
      const inst = (bytes - lastBytes) * 8 / ((now - lastAt) * 1000);
      smooth = smooth === 0 ? inst : smooth * 0.7 + inst * 0.3;
      lastBytes = bytes; lastAt = now;
      if (!warmAt && now - start >= warmup) { warmAt = now; warmBytes = bytes; }
      onSample(smooth, Math.min(1, (now - start) / durationMs));
      if (now >= end || signal.aborted) { clearInterval(tick); resolve(); }
    }, 200);
  });
  local.abort();
  signal.removeEventListener("abort", abortAll);
  await Promise.allSettled([...workers, pinger]);
  if (signal.aborted) throw new DOMException("Aborted", "AbortError");
  const ms = performance.now() - (warmAt || start);
  const mbps = ms > 0 ? (bytes - warmBytes) * 8 / (ms * 1000) : smooth;
  if (bytes === 0) throw new Error("no data");
  const sortedLoaded = loaded.sort((a, b) => a - b);
  return { mbps: Math.max(0.01, mbps), loadedPing: sortedLoaded.length ? sortedLoaded[Math.floor(sortedLoaded.length / 2)] : null };
}

function bufferbloatGrade(base, down, up) {
  const d = Math.max(down != null ? down - base : 0, up != null ? up - base : 0, 0);
  return d <= 5 ? "A+" : d <= 15 ? "A" : d <= 30 ? "B" : d <= 60 ? "C" : d <= 150 ? "D" : "F";
}

// ---------- App state ----------
const blankTest = () => ({ phase: "idle", live: 0, progress: 0, ping: null, jitter: null, down: null, up: null,
  dlp: null, ulp: null, grade: null, downSamples: [], upSamples: [], server: null, error: null, finishedAt: null });
const app = { tab: "test", tool: "ping", test: blankTest(), abort: null };
const running = () => ["connecting", "ping", "download", "upload"].includes(app.test.phase);

async function startTest() {
  if (running()) return;
  if (!navigator.onLine) { app.test = { ...blankTest(), phase: "error", error: "offline" }; render(); return; }
  const ctl = new AbortController();
  app.abort = ctl;
  const t = app.test = { ...blankTest(), phase: "connecting" };
  render();
  const live = () => { if (app.test === t) updateLive(); };
  try {
    const ep = await resolveEndpoints(settings.source, ctl.signal);
    t.server = ep; t.phase = "ping"; render();
    const p = await measurePing(ep, ctl.signal, (ms, prog) => { t.ping = ms; t.progress = prog; live(); });
    Object.assign(t, { ping: p.ping, jitter: p.jitter, phase: "download", progress: 0 }); render();
    const dur = settings.length * 1000;
    const d = await measureTransfer("down", ep, dur, ctl.signal, (mbps, prog) => { t.live = mbps; t.progress = prog; t.downSamples.push(mbps); live(); });
    Object.assign(t, { down: d.mbps, dlp: d.loadedPing, live: 0, phase: "upload", progress: 0 }); render();
    const u = await measureTransfer("up", ep, dur, ctl.signal, (mbps, prog) => { t.live = mbps; t.progress = prog; t.upSamples.push(mbps); live(); });
    Object.assign(t, { up: u.mbps, ulp: u.loadedPing, live: d.mbps, progress: 1, phase: "done", finishedAt: Date.now() });
    t.grade = bufferbloatGrade(t.ping, t.dlp, t.ulp);
    history.unshift({
      time: t.finishedAt, d: t.down, u: t.up, p: t.ping, j: t.jitter, isp: ep.isp, srv: ep.serverLabel,
      src: settings.source, dlp: t.dlp, ulp: t.ulp, bbg: t.grade, ds: downsample(t.downSamples), us: downsample(t.upSamples),
    });
    saveHistory();
    render();
  } catch (e) {
    if (app.test !== t || ctl.signal.aborted) return;
    t.phase = "error";
    t.error = !navigator.onLine ? "offline" : e instanceof SourceBlocked ? "source" : "server";
    render();
  }
}
function stopTest() {
  if (app.abort) app.abort.abort();
  app.test = blankTest();
  render();
}

function downsample(values, max = 30) {
  if (values.length <= max) return values.map(v => Math.round(v * 10) / 10);
  return Array.from({ length: max }, (_, i) => {
    const a = Math.floor(i * values.length / max), b = Math.max(a + 1, Math.floor((i + 1) * values.length / max));
    const part = values.slice(a, b);
    return Math.round(part.reduce((x, y) => x + y, 0) / part.length * 10) / 10;
  });
}

// ---------- Share links (same format as the Android app) ----------
async function buildLink(r) {
  const round = (v, p) => num(v) ? Math.round(v * 10 ** p) / 10 ** p : undefined;
  const o = { v: 1, t: Math.round(r.time / 1000), d: round(r.d, 2), u: round(r.u, 2), p: round(r.p, 1), j: round(r.j, 1), src: r.src, net: "web" };
  if (r.srv) o.srv = r.srv;
  if (r.isp) o.isp = r.isp;
  if (num(r.dlp)) o.dl = round(r.dlp, 1);
  if (num(r.ulp)) o.ul = round(r.ulp, 1);
  if (r.bbg) o.bb = r.bbg;
  if (r.ds && r.ds.length) o.ds = r.ds;
  if (r.us && r.us.length) o.us = r.us;
  const stream = new Blob([JSON.stringify(o)]).stream().pipeThrough(new CompressionStream("deflate-raw"));
  const bytes = new Uint8Array(await new Response(stream).arrayBuffer());
  let bin = "";
  bytes.forEach(b => { bin += String.fromCharCode(b); });
  return RESULT_PAGE + "#" + btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
async function shareResult(r) {
  const link = await buildLink(r);
  const text = S.share_text(`${fmtSpeed(r.d)} ${unitLabel()}`, `${fmtSpeed(r.u)} ${unitLabel()}`, fmtMs(r.p), fmtMs(r.j || 0));
  if (navigator.share) {
    try { await navigator.share({ title: "Pulse", text, url: link }); return; } catch (e) { if (e.name === "AbortError") return; }
  }
  try { await navigator.clipboard.writeText(`${text}\n${link}`); toast(S.copied); } catch (e) { prompt("", link); }
}

// ---------- Morphing gauge (port of ui/Gauge.kt) ----------
const SCALE = { mbps: [0, 5, 10, 50, 100, 250, 500, 1000, 2500], mbytes: [0, 1, 2, 5, 10, 25, 50, 100, 300] };
function gaugeFraction(v) {
  const s = SCALE[settings.unit];
  if (v <= 0) return 0;
  if (v >= s[s.length - 1]) return 1;
  let i = 0; while (s[i + 1] <= v) i++;
  return (i + (v - s[i]) / (s[i + 1] - s[i])) / (s.length - 1);
}
const gauge = { rotation: 0, breath: 0, energy: 0, morph: 0, ring: 0, ringAlpha: 0, last: 0, press: 1, pressTarget: 1 };
function stadiumPoint(th, hw, hh) {
  const dx = Math.cos(th), dy = Math.sin(th), seg = Math.max(0, hw - hh);
  let lo = 0, hi = hw + hh;
  for (let k = 0; k < 22; k++) { const t = (lo + hi) / 2; if (Math.hypot(Math.max(Math.abs(t * dx) - seg, 0), t * dy) < hh) lo = t; else hi = t; }
  return [lo * dx, lo * dy];
}
function blobPoints(g, cx, cy, radius, outer) {
  const pts = [], capScale = outer ? 1.12 : 1, N = 180;
  for (let i = 0; i < N; i++) {
    const th = -Math.PI / 2 + 2 * Math.PI * i / N;
    const c2 = Math.cos(2 * (th - g.rotation));
    const lobe = Math.pow((c2 * c2 + 0.035) / 1.035, 0.25);
    const petal = 1 - g.amp * (1 - lobe) + 0.012 * Math.sin(3 * th + g.breath);
    let x = cx + radius * petal * Math.cos(th), y = cy + radius * petal * Math.sin(th);
    if (g.morph > 0) {
      const [qx, qy] = stadiumPoint(th, g.capW * capScale, g.capH * capScale);
      x += (cx + qx - x) * g.morph; y += (cy + qy - y) * g.morph;
    }
    pts.push([x, y]);
  }
  return pts;
}
function pathFrom(ctx, pts) { ctx.beginPath(); pts.forEach(([x, y], i) => i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)); ctx.closePath(); }
function drawGauge(now) {
  requestAnimationFrame(drawGauge);
  const canvas = $("gauge");
  const dt = Math.min(0.05, (now - (gauge.last || now)) / 1000);
  gauge.last = now;
  if (!canvas) return;
  const t = app.test;
  const mode = t.phase === "idle" || t.phase === "error" ? "idle" : t.phase === "connecting" ? "connecting" : t.phase === "done" ? "done" : "running";
  const active = mode === "running" || mode === "connecting";
  const liveFrac = t.phase === "download" || t.phase === "upload" ? gaugeFraction(conv(t.live)) : 0;
  const ease = (cur, target, k) => cur + (target - cur) * Math.min(1, dt * k);
  gauge.energy = ease(gauge.energy, liveFrac, 3);
  gauge.morph = ease(gauge.morph, mode === "done" ? 1 : 0, 3.2);
  gauge.ring = mode === "running" ? ease(gauge.ring, t.progress, 10) : ease(gauge.ring, 0, 8);
  gauge.ringAlpha = ease(gauge.ringAlpha, active ? 1 : 0, 6);
  gauge.press = ease(gauge.press, gauge.pressTarget, 18);
  gauge.rotation = (gauge.rotation + dt * (active ? 0.35 + 1.9 * gauge.energy : 0.18)) % (2 * Math.PI);
  gauge.breath = (gauge.breath + dt * (active ? 2.4 + 5.5 * gauge.energy : 1.6)) % (2 * Math.PI);

  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) { canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr); }
  const ctx = canvas.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);
  const cx = w / 2, cy = h / 2;
  ctx.translate(cx, cy); ctx.scale(gauge.press, gauge.press); ctx.translate(-cx, -cy);
  const base = Math.min(w, h) * 0.40;
  const amp = mode === "idle" ? 0.20 : mode === "connecting" ? 0.17 : 0.15 + 0.16 * gauge.energy;
  const breathe = 1 + (active ? 0.018 + 0.035 * gauge.energy : 0.03) * Math.sin(gauge.breath);
  const g = { amp, rotation: gauge.rotation, breath: gauge.breath, morph: gauge.morph, capW: Math.min(w * 0.46, base * 2.6), capH: base * 0.56 };
  const upload = t.phase === "upload";
  const accent = upload ? css("--tertiary") : css("--primary");
  const fill = mode === "idle" ? css("--primary") : upload ? css("--tertiary-container") : css("--primary-container");

  ctx.globalAlpha = 0.12 * (1 - gauge.morph);
  ctx.fillStyle = accent;
  pathFrom(ctx, blobPoints(g, cx, cy, base * (1.08 + 0.06 * (0.5 + 0.5 * Math.sin(gauge.breath))), true));
  ctx.fill();
  ctx.globalAlpha = 1;

  if (gauge.ringAlpha > 0.01) {
    const pts = blobPoints(g, cx, cy, base * 1.16, true);
    ctx.lineWidth = 7; ctx.lineCap = "round"; ctx.lineJoin = "round";
    ctx.globalAlpha = gauge.ringAlpha;
    ctx.strokeStyle = css("--sc-highest");
    pathFrom(ctx, pts); ctx.stroke();
    if (gauge.ring > 0.002) {
      const n = Math.max(2, Math.round(pts.length * gauge.ring));
      ctx.strokeStyle = accent;
      ctx.beginPath();
      for (let i = 0; i < n; i++) i ? ctx.lineTo(pts[i % pts.length][0], pts[i % pts.length][1]) : ctx.moveTo(pts[0][0], pts[0][1]);
      if (gauge.ring >= 0.999) ctx.closePath();
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
  }
  ctx.fillStyle = fill;
  pathFrom(ctx, blobPoints(g, cx, cy, base * breathe, false));
  ctx.fill();
}
requestAnimationFrame(drawGauge);

// ---------- Charts ----------
function setupCanvas(c) {
  const dpr = window.devicePixelRatio || 1, w = c.clientWidth, h = c.clientHeight;
  c.width = Math.round(w * dpr); c.height = Math.round(h * dpr);
  const ctx = c.getContext("2d"); ctx.setTransform(dpr, 0, 0, dpr, 0, 0); ctx.clearRect(0, 0, w, h);
  return [ctx, w, h];
}
function drawLines(c, seriesList, pad = 6) {
  if (!c) return;
  const [ctx, w, h] = setupCanvas(c);
  const all = seriesList.flatMap(s => s.values);
  if (!all.length) return;
  const peak = Math.max(1, ...all);
  for (const s of seriesList) {
    const v = s.values;
    if (v.length < 2) continue;
    const pt = i => [pad + i * (w - pad * 2) / (v.length - 1), h - pad - (Math.max(0, v[i]) / peak) * (h - pad * 2)];
    ctx.beginPath();
    v.forEach((_, i) => { const [x, y] = pt(i); i ? ctx.lineTo(x, y) : ctx.moveTo(x, y); });
    const line = new Path2D(); v.forEach((_, i) => { const [x, y] = pt(i); i ? line.lineTo(x, y) : line.moveTo(x, y); });
    const area = new Path2D(line); area.lineTo(w - pad, h); area.lineTo(pad, h); area.closePath();
    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, s.color + "55"); grad.addColorStop(1, s.color + "00");
    ctx.fillStyle = grad; ctx.fill(area);
    ctx.strokeStyle = s.color; ctx.lineWidth = 2.5; ctx.lineJoin = "round"; ctx.lineCap = "round"; ctx.stroke(line);
  }
}
function hexOf(varName) {
  const v = css(varName);
  if (v.startsWith("#") && v.length === 7) return v;
  return "#888888";
}

// ---------- Test screen ----------
function metricCard(label, value, sub, ic, active, accent) {
  return `<div class="metric ${accent}${active ? " active" : ""}">
    <div class="badge">${icon(ic)}</div>
    <div class="lbl">${label}</div><div class="val">${value}</div><div class="sub">${sub}</div></div>`;
}
function metricsHtml() {
  const t = app.test;
  const downV = t.down ?? (t.phase === "download" ? t.live : null);
  const upV = t.up ?? (t.phase === "upload" ? t.live : null);
  return metricCard(S.phase_ping, num(t.ping) ? fmtMs(t.ping) : "—", num(t.jitter) ? `${S.jitter} ${fmtMs(t.jitter)}` : S.ms, "timer", t.phase === "ping", "s")
    + metricCard(S.phase_download, num(downV) ? fmtSpeed(downV) : "—", unitLabel(), "arrow_downward", t.phase === "download", "p")
    + metricCard(S.phase_upload, num(upV) ? fmtSpeed(upV) : "—", unitLabel(), "arrow_upward", t.phase === "upload", "t");
}
function centreHtml() {
  const t = app.test;
  if (t.phase === "idle" || t.phase === "error") return `<div class="go">${S.go}</div>`;
  if (t.phase === "connecting") return `<div class="readout" style="color:var(--on-primary-container)"><div class="t">${icon("language", "s16")}${S.phase_connecting}</div><div class="v">…</div></div>`;
  if (t.phase === "done") return `<div class="done-readout">${icon("arrow_downward", "s28")}<span class="v">${fmtSpeed(t.down)}</span><div class="u"><div class="title-m">${unitLabel()}</div><div class="label-m" style="opacity:.75">${S.phase_download}</div></div></div>`;
  const ping = t.phase === "ping", up = t.phase === "upload";
  const color = up ? "var(--on-tertiary-container)" : "var(--on-primary-container)";
  const title = ping ? S.phase_ping : up ? S.phase_upload : S.phase_download;
  const ic = ping ? "timer" : up ? "arrow_upward" : "arrow_downward";
  const value = ping ? (num(t.ping) ? fmtMs(t.ping) : "…") : fmtSpeed(t.live);
  return `<div class="readout" style="color:${color}"><div class="t">${icon(ic, "s16")}${title}</div><div class="v">${value}</div><div class="u">${ping ? S.ms : unitLabel()}</div></div>`;
}
function overallProgress() {
  const t = app.test;
  const idx = t.phase === "download" ? 1 : t.phase === "upload" ? 2 : 0;
  return Math.min(1, (idx + t.progress) / 3);
}
function actionsHtml() {
  const t = app.test;
  if (running()) return `<div class="progress"><i id="overall" style="width:${overallProgress() * 100}%"></i></div>
    <button class="btn danger" data-act="stop">${icon("stop", "s20")}${S.stop_test}</button>`;
  if (t.phase === "done") return `<div class="btn-row">
    <button class="btn filled big" data-act="start">${icon("refresh")}${S.test_again}</button>
    <button class="btn outline big" data-act="share-current">${icon("share", "s20")}${S.share}</button></div>`;
  return "";
}
function renderTest() {
  const t = app.test, net = netKind();
  const src = SOURCES.find(s => s.id === settings.source) || SOURCES[0];
  const grade = t.grade;
  const gradeCls = !grade ? "" : /^A/.test(grade) ? "g-ok" : /^[BC]/.test(grade) ? "g-warn" : "g-bad";
  return `<section class="screen">
    <div class="header"><h1 class="headline-l">Pulse<span class="web-badge">${S.web_badge}</span></h1>
      <span class="chip">${icon(net.icon)}${esc(net.label)}</span></div>
    <div class="chips" style="margin-top:12px">${SOURCES.map(s => s.locked
      ? `<button class="fchip locked" data-locked="1" ${running() ? "disabled" : ""}>${icon("lock", "s18")}${s.name}</button>`
      : `<button class="fchip${s.id === settings.source ? " on" : ""}" data-src="${s.id}" ${running() ? "disabled" : ""}>${s.id === settings.source ? icon("check", "s18") : ""}${s.name}</button>`).join("")}</div>
    <div class="body-s muted source-note">${esc(src.desc())}</div>
    <div class="server-row"><div class="badge">${icon(settings.source === "cloudflare" ? "auto_awesome" : "dns")}</div>
      <div class="txt"><div class="label-m muted">${S.server_title}</div><div class="body-m">${esc(t.server ? t.server.serverLabel : settings.source === "cloudflare" ? S.server_cf_note : S.server_auto_long)}</div></div></div>
    <div class="metrics" id="metrics">${metricsHtml()}</div>
    <div class="stage${t.phase === "idle" || t.phase === "error" ? " idle" : ""}" id="stage" role="button" tabindex="0" aria-label="${S.go}">
      <canvas id="gauge"></canvas><div class="centre" id="centre">${centreHtml()}</div>
      ${t.phase === "idle" || t.phase === "error" ? `<div class="hint body-m muted">${S.tap_to_start}</div>` : ""}</div>
    <div class="actions" id="actions">${actionsHtml()}</div>
    ${t.phase === "error" ? `<div class="card round" style="background:var(--error-container);color:var(--on-error-container)"><div class="card-head">${icon("error")}<div class="grow"><div class="title-m">${S.error_title}</div><div class="body-m">${t.error === "offline" ? S.error_offline : t.error === "source" ? S.error_source : S.error_generic}</div></div></div></div>` : ""}
    ${t.downSamples.length ? `<div class="card"><div class="graph-legend"><div class="title-m" style="flex:1">${S.graph_title}</div>
      <span class="legend"><i style="background:var(--primary)"></i>${S.phase_download}</span>${t.upSamples.length ? `<span class="legend"><i style="background:var(--tertiary)"></i>${S.phase_upload}</span>` : ""}</div>
      <canvas class="graph" id="graph"></canvas></div>` : ""}
    ${t.phase === "done" && grade ? `<div class="card alt"><div class="card-head"><span class="grade ${gradeCls}">${grade}</span><div class="grow"><div class="title-m">${S.bufferbloat}</div><div class="body-m muted">${S.bb_desc[grade]}</div>
      <div class="body-s muted" style="margin-top:4px">${S.loaded_latency}: <bdi dir="ltr">↓ ${num(t.dlp) ? fmtMs(t.dlp) : "—"} ms · ↑ ${num(t.ulp) ? fmtMs(t.ulp) : "—"} ms</bdi></div></div></div></div>` : ""}
    ${t.server ? `<div class="card round"><div class="title-m">${S.connection_title}</div>
      ${t.server.isp ? kv("business", S.provider, t.server.isp) : ""}${kv("dns", S.server, t.server.serverLabel)}
      ${t.server.ip ? kv("language", S.ip_address, t.server.ip) : ""}${kv(net.icon, S.network, net.label)}${kv("settings_ethernet", S.protocol, t.server.protocol)}</div>` : ""}
  </section>`;
}
const kv = (ic, k, v) => `<div class="kv">${icon(ic)}<div class="grow"><div class="label-m muted">${esc(k)}</div><div class="body-l">${esc(v)}</div></div></div>`;

function updateLive() {
  if (app.tab !== "test") return;
  const m = $("metrics"); if (m) m.innerHTML = metricsHtml();
  const c = $("centre"); if (c) c.innerHTML = centreHtml();
  const o = $("overall"); if (o) o.style.width = overallProgress() * 100 + "%";
  const g = $("graph");
  if (g) drawGraph();
  else if (app.test.downSamples.length === 1) render();
}
function drawGraph() {
  const t = app.test;
  drawLines($("graph"), [
    { values: t.downSamples.map(conv), color: hexOf("--primary") },
    { values: t.upSamples.map(conv), color: hexOf("--tertiary") },
  ]);
}

// ---------- Ping screen ----------
const PUBLIC_DNS = [
  { label: "Cloudflare", host: "1.1.1.1", url: "https://1.1.1.1/cdn-cgi/trace" },
  { label: "Google", host: "8.8.8.8", url: "https://dns.google/resolve?name=example.com" },
  { label: "Quad9", host: "9.9.9.9", url: "https://dns.quad9.net/" },
  { label: "OpenDNS", host: "208.67.222.222", url: "https://doh.opendns.com/" },
];
const ping = { host: store.get("pulse_web_ping_host", "1.1.1.1"), count: 10, running: false, samples: [], rtts: [], sent: 0, error: null, ctl: null,
  recents: store.get("pulse_web_ping_recents", []) };
function pingUrlFor(host) {
  const preset = PUBLIC_DNS.find(d => d.host === host);
  if (preset) return preset.url;
  const h = host.trim().replace(/^https?:\/\//i, "").replace(/\/.*$/, "");
  return `https://${h}/favicon.ico`;
}
async function httpsPing(url, signal, timeout = 2500) {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeout);
  const onAbort = () => ctl.abort();
  signal.addEventListener("abort", onAbort);
  const t0 = performance.now();
  try {
    await fetch(withQ(url, "pulse=" + nonce()), { mode: "no-cors", cache: "no-store", signal: ctl.signal });
    return performance.now() - t0;
  } catch (e) {
    return null;
  } finally { clearTimeout(timer); signal.removeEventListener("abort", onAbort); }
}
async function startPing(hostOverride) {
  const host = (hostOverride ?? ping.host).trim();
  if (!host) return;
  stopPing();
  Object.assign(ping, { host, samples: [], rtts: [], sent: 0, error: null });
  store.set("pulse_web_ping_host", host);
  if (!navigator.onLine) { ping.error = "offline"; render(); return; }
  const ctl = ping.ctl = new AbortController();
  ping.running = true;
  render();
  const url = pingUrlFor(host);
  await httpsPing(url, ctl.signal); // warm-up: DNS + TLS handshake, not counted
  for (let i = 0; ping.count === 0 || i < ping.count; i++) {
    if (ctl.signal.aborted) return;
    const t0 = performance.now();
    const ms = await httpsPing(url, ctl.signal);
    if (ctl.signal.aborted) return;
    ping.sent++;
    if (ms != null) ping.rtts.push(ms);
    ping.samples.push(ms);
    if (ping.samples.length > 120) ping.samples.shift();
    if (ping.sent === 1 && ms != null && !PUBLIC_DNS.some(d => d.host === host)) {
      ping.recents = [host, ...ping.recents.filter(h => h.toLowerCase() !== host.toLowerCase())].slice(0, 6);
      store.set("pulse_web_ping_recents", ping.recents);
    }
    updatePing();
    await new Promise(r => setTimeout(r, Math.max(0, 1000 - (performance.now() - t0))));
  }
  if (ping.rtts.length === 0) ping.error = "none";
  ping.running = false;
  render();
}
function stopPing() { if (ping.ctl) ping.ctl.abort(); ping.running = false; }
function pingStats() {
  const r = ping.rtts;
  let j = 0, n = 0;
  for (let i = 1; i < r.length; i++) { j += Math.abs(r[i] - r[i - 1]); n++; }
  return { min: r.length ? Math.min(...r) : null, avg: r.length ? r.reduce((a, b) => a + b, 0) / r.length : null, max: r.length ? Math.max(...r) : null,
    jitter: n ? j / n : null, loss: ping.sent ? (ping.sent - r.length) * 100 / ping.sent : null };
}
const latencyColor = ms => ms == null ? "var(--on-surface-variant)" : ms < 30 ? "var(--primary)" : ms < 100 ? "var(--tertiary)" : "var(--error)";
function pingLiveHtml() {
  const st = pingStats(), last = ping.samples[ping.samples.length - 1];
  return `<div class="big-num"><span class="v" style="color:${st.avg != null ? latencyColor(st.avg) : "var(--on-surface-variant)"}">${st.avg != null ? fmtMs(st.avg) : "—"}</span>
    <span class="u title-s muted">${S.ms} ${S.ping_avg.toLowerCase()}</span>
    ${ping.samples.length ? `<span class="last label-l" style="color:${last != null ? latencyColor(last) : "var(--error)"}">${last != null ? S.ping_last(fmtMs(last)) : S.ping_timeout}</span>` : ""}</div>`;
}
function statsHtml() {
  const st = pingStats();
  const cell = (l, v, u, color) => `<div><div class="label-m muted">${l}</div><span class="v"${color ? ` style="color:${color}"` : ""}>${v ?? "—"}</span>${v != null && u ? `<span class="u">${u}</span>` : ""}</div>`;
  return cell(S.ping_min, st.min != null ? fmtMs(st.min) : null, S.ms) + cell(S.ping_avg, st.avg != null ? fmtMs(st.avg) : null, S.ms) + cell(S.ping_max, st.max != null ? fmtMs(st.max) : null, S.ms)
    + cell(S.jitter, st.jitter != null ? fmtMs(st.jitter) : null, S.ms)
    + cell(S.ping_loss, st.loss != null ? st.loss.toFixed(0) : null, "%", st.loss > 0 ? "var(--error)" : "")
    + cell(S.ping_replies, ping.sent ? `${ping.rtts.length}/${ping.sent}` : null, "");
}
function drawBars() {
  const c = $("bars"); if (!c) return;
  const [ctx, w, h] = setupCanvas(c);
  const slots = 40, shown = ping.samples.slice(-slots), gap = 3, bw = (w - gap * (slots + 1)) / slots;
  const peak = Math.max(20, ...shown.filter(v => v != null));
  const col = { good: css("--primary"), mid: css("--tertiary"), bad: css("--error"), track: css("--sc-highest") };
  const rr = (x, y, ww, hh) => { ctx.beginPath(); ctx.roundRect(x, y, ww, hh, bw / 2); ctx.fill(); };
  for (let i = 0; i < slots; i++) {
    const x = gap + i * (bw + gap), k = i - (slots - shown.length);
    if (k < 0) { ctx.fillStyle = col.track; rr(x, h - bw - gap, bw, bw); continue; }
    const v = shown[k];
    if (v == null) { ctx.fillStyle = col.bad; rr(x, h - h * 0.18 - gap, bw, h * 0.18); continue; }
    const bh = Math.max(bw, (v / peak) * (h - gap * 2));
    ctx.fillStyle = v < 30 ? col.good : v < 100 ? col.mid : col.bad;
    rr(x, h - bh - gap, bw, bh);
  }
}
function updatePing() {
  if (app.tab !== "ping" || app.tool !== "ping") return;
  const l = $("ping-live"); if (l) l.innerHTML = pingLiveHtml();
  const s = $("ping-stats"); if (s) s.innerHTML = statsHtml();
  drawBars();
}
function seg(options, selected, attr, disabled) {
  return `<div class="seg">${options.map(([v, label]) => `<button data-${attr}="${v}" class="${String(v) === String(selected) ? "on" : ""}" ${disabled ? "disabled" : ""}>${String(v) === String(selected) ? icon("check", "s18") : ""}${label}</button>`).join("")}</div>`;
}
function renderPing() {
  const toolSeg = seg([["ping", S.tool_ping], ["devices", S.tool_devices]], app.tool, "tool", false);
  if (app.tool === "devices") {
    return `<section class="screen"><h1 class="headline-l page-title">${S.tab_ping}</h1>${toolSeg}
      <div class="card"><div class="card-head"><div class="ico">${icon("radar")}</div><div class="grow"><div class="title-m">${S.scan_web_title}</div></div></div>
      <p class="body-m muted" style="margin:14px 0 16px">${S.scan_web_body}</p>
      <a class="btn filled block" href="${RELEASES}" target="_blank" rel="noopener">${icon("android")}${S.web_btn}</a></div></section>`;
  }
  const chip = (label, host, ic) => `<button class="tchip${host === ping.host ? " on" : ""}" data-ping="${esc(host)}" ${ping.running ? "disabled" : ""}>${icon(ic, "s18")}<span><div class="l1">${esc(label)}</div><div class="l2">${esc(host)}</div></span></button>`;
  const status = ping.running && !ping.sent ? S.ping_resolving : ping.sent ? `HTTPS · ${esc(pingUrlFor(ping.host).replace(/^https:\/\//, "").replace(/\/.*$/, ""))}` : S.ping_idle;
  return `<section class="screen"><h1 class="headline-l page-title">${S.tab_ping}</h1>${toolSeg}
    <div class="card" style="margin-top:12px">
      <div class="card-head"><div class="ico">${icon("network_ping")}</div><div class="grow"><div class="title-m" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(ping.host || "—")}</div><div class="body-s muted">${status}</div></div></div>
      <div id="ping-live">${pingLiveHtml()}</div>
      <canvas class="bars" id="bars"></canvas>
      ${ping.error ? `<div class="err-row">${icon("error")}<span>${ping.error === "offline" ? S.ping_err_offline : S.ping_err_none}</span></div>` : ""}
      <div style="margin-top:16px">${ping.running
        ? `<button class="btn error block" data-act="ping-stop">${icon("stop")}${S.ping_stop}</button>`
        : `<button class="btn filled block" data-act="ping-start" ${ping.host.trim() ? "" : "disabled"}>${icon("play_arrow")}${S.ping_start}</button>`}</div>
    </div>
    <div class="field"><input id="ping-host" value="${esc(ping.host)}" autocomplete="off" autocapitalize="off" spellcheck="false" inputmode="url" enterkeyhint="go" ${ping.running ? "disabled" : ""}><label for="ping-host">${S.ping_target_hint}</label></div>
    <div class="section-label">${S.ping_group_dns}</div>
    <div class="chips">${PUBLIC_DNS.map(d => chip(d.label, d.host, "public")).join("")}</div>
    ${ping.recents.length ? `<div class="section-label">${S.ping_group_recent}</div><div class="chips">${ping.recents.map(h => chip(h, "HTTPS", "history").replace(`data-ping="HTTPS"`, `data-ping="${esc(h)}"`).replace(` on"`, `"`)).join("")}</div>` : ""}
    <div class="section-label">${S.ping_count}</div>
    ${seg([[10, "10"], [30, "30"], [0, "∞"]], ping.count, "count", ping.running)}
    <p class="body-s muted" style="margin:10px 4px 0">${S.ping_web_note}</p>
    <div class="card round" style="margin-top:16px"><div class="stats" id="ping-stats">${statsHtml()}</div></div>
  </section>`;
}

// ---------- History ----------
function renderHistory() {
  if (!history.length) {
    return `<section class="screen"><h1 class="headline-l page-title">${S.tab_history}</h1>
      <div class="empty"><div class="blob">${icon("history", "s28")}</div><div class="title-l">${S.history_empty_title}</div><p class="body-m muted">${S.history_empty_body}</p></div></section>`;
  }
  const avg = history.reduce((a, r) => a + r.d, 0) / history.length;
  const best = Math.max(...history.map(r => r.d));
  const avgPing = history.reduce((a, r) => a + r.p, 0) / history.length;
  const fmtDate = t => new Date(t).toLocaleString(langCode() === "iw" ? "he-IL" : undefined, { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  return `<section class="screen">
    <div class="header" style="padding:20px 0 12px"><h1 class="headline-l" style="flex:1">${S.tab_history}</h1>
      <button class="icon-btn" data-act="export" title="${S.export_csv}">${icon("ios_share")}</button>
      <button class="icon-btn" data-act="clear" title="${S.clear_history}">${icon("delete_sweep")}</button></div>
    <div class="summary"><div style="display:flex;align-items:center"><span class="label-l" style="flex:1">${S.tests(history.length)}</span>
      <span class="legend"><i style="background:var(--primary)"></i>${S.phase_download}</span></div>
      <canvas class="spark" id="spark"></canvas>
      <div class="sum-stats"><div><div class="label-m">${S.history_avg}</div><div class="v">${fmtSpeed(avg)}</div><div class="label-s">${unitLabel()}</div></div>
        <div><div class="label-m">${S.history_best}</div><div class="v">${fmtSpeed(best)}</div><div class="label-s">${unitLabel()}</div></div>
        <div><div class="label-m">${S.phase_ping}</div><div class="v">${fmtMs(avgPing)}</div><div class="label-s">${S.ms}</div></div></div></div>
    ${history.map((r, i) => `<div class="hitem">
      <div class="top"><span class="grow label-l muted">${esc(fmtDate(r.time))}</span>
        <button class="icon-btn" data-share="${i}" title="${S.share_link}">${icon("share", "s20")}</button>
        <button class="icon-btn" data-del="${i}" title="${S.delete}">${icon("delete", "s20")}</button></div>
      <div class="hero-dl">${icon("arrow_downward", "s28")}<span class="v">${fmtSpeed(r.d)}</span><span class="u">${unitLabel()}</span></div>
      <div class="labels">
        <span class="dlabel" style="background:var(--tertiary-container);color:var(--on-tertiary-container)">${icon("arrow_upward", "s16")}${fmtSpeed(r.u)} ${unitLabel()}</span>
        <span class="dlabel" style="background:var(--secondary-container);color:var(--on-secondary-container)">${icon("timer", "s16")}${fmtMs(r.p)} ${S.ms}</span>
        ${r.bbg ? `<span class="dlabel" style="background:var(--primary-container);color:var(--on-primary-container)">${icon("speed", "s16")}Bufferbloat ${esc(r.bbg)}</span>` : ""}
        ${r.srv || r.isp ? `<span class="dlabel" style="background:var(--sc-highest);color:var(--on-surface-variant)">${icon("place", "s16")}${esc(r.srv || r.isp)}</span>` : ""}
      </div></div>`).join("")}
  </section>`;
}
function exportCsv() {
  const rows = [["Timestamp", "Date", "Source", "Server", "ISP", "Download_Mbps", "Upload_Mbps", "Ping_ms", "Jitter_ms", "Down_Loaded_Ping_ms", "Up_Loaded_Ping_ms", "Bufferbloat_Grade"]];
  history.forEach(r => rows.push([r.time, new Date(r.time).toISOString(), r.src, r.srv, r.isp, r.d.toFixed(2), r.u.toFixed(2), r.p.toFixed(1), (r.j || 0).toFixed(1),
    num(r.dlp) ? r.dlp.toFixed(1) : "", num(r.ulp) ? r.ulp.toFixed(1) : "", r.bbg || ""]));
  const csv = rows.map(r => r.map(v => /[",\n]/.test(String(v ?? "")) ? `"${String(v).replace(/"/g, '""')}"` : (v ?? "")).join(",")).join("\n");
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([csv], { type: "text/csv" }));
  a.download = "pulse-history.csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

// ---------- Settings ----------
function renderSettings() {
  const block = (label, inner) => `<div class="lbl body-l">${label}</div>${inner}`;
  return `<section class="screen"><h1 class="headline-l page-title">${S.tab_settings}</h1>
    <div class="card highlight"><div class="card-head"><div class="ico primary">${icon("android")}</div>
      <div class="grow"><div class="title-m">${S.web_title}</div><div class="body-m" style="opacity:.8">${S.web_desc}</div></div></div>
      <div style="margin-top:14px"><a class="btn filled sm" href="${RELEASES}" target="_blank" rel="noopener">${icon("download", "s18")}${S.web_btn}</a></div></div>
    <div class="section-label">${S.settings_appearance}</div>
    <div class="settings-block">
      ${block(S.settings_language, seg([["system", S.lang_system], ["en", "English"], ["iw", "עברית"]], settings.lang, "lang"))}
      ${block(S.settings_theme, seg([["system", S.theme_system], ["light", S.theme_light], ["dark", S.theme_dark], ["oled", "OLED"]], settings.theme, "theme"))}
    </div>
    <div class="section-label">${S.settings_test}</div>
    <div class="settings-block">
      ${block(S.settings_units, seg([["mbps", "Mbps"], ["mbytes", "MB/s"]], settings.unit, "unit"))}
      ${block(S.settings_duration, seg([[6, S.len_quick], [10, S.len_standard], [15, S.len_long]], settings.length, "len"))}
    </div>
    <div class="section-label">${S.settings_about}</div>
    <div class="list">
      <div class="row">${icon("info")}<div class="grow"><div class="body-l">${S.about_version}</div><div class="body-m muted">${VERSION} · Web</div></div></div>
      <div class="row">${icon("public")}<div class="grow"><div class="body-l">${S.about_engine_title}</div><div class="body-m muted">${S.about_engine}</div></div></div>
      <a class="row" href="${REPO}" target="_blank" rel="noopener" style="color:inherit;text-decoration:none">${icon("code")}<div class="grow"><div class="body-l">${S.about_source}</div><div class="body-m muted">github.com/dor2500/pulse-speed</div></div></a>
    </div>
  </section>`;
}

// ---------- Shell ----------
const TABS = [["test", "speed"], ["ping", "network_ping"], ["history", "history"], ["settings", "settings"]];
function renderNav() {
  $("nav").innerHTML = TABS.map(([id, ic]) => `<button role="tab" aria-selected="${app.tab === id}" aria-label="${S["tab_" + id]}" title="${S["tab_" + id]}" class="${app.tab === id ? "on" : ""}" data-tab="${id}">${icon(ic, app.tab === id ? "" : "o")}</button>`).join("");
}
function render() {
  const main = $("app");
  main.innerHTML = app.tab === "test" ? renderTest() : app.tab === "ping" ? renderPing() : app.tab === "history" ? renderHistory() : renderSettings();
  renderNav();
  if (app.tab === "test" && $("graph")) drawGraph();
  if (app.tab === "ping") drawBars();
  if (app.tab === "history" && $("spark")) drawLines($("spark"), [{ values: history.slice(0, 30).reverse().map(r => conv(r.d)), color: hexOf("--primary") }], 8);
}

document.addEventListener("click", async e => {
  const el = e.target.closest("[data-locked],[data-tab],[data-src],[data-act],[data-tool],[data-ping],[data-count],[data-lang],[data-theme],[data-unit],[data-len],[data-share],[data-del],#stage");
  if (!el || el.disabled) return;
  const d = el.dataset;
  if (d.locked) { toast(S.src_locked); return; }
  if (d.tab) { app.tab = d.tab; render(); window.scrollTo(0, 0); return; }
  if (d.src) { settings.source = d.src; saveSettings(); app.test = blankTest(); render(); return; }
  if (d.tool) { app.tool = d.tool; render(); return; }
  if (d.ping) { startPing(d.ping); return; }
  if (d.count) { ping.count = +d.count; render(); return; }
  if (d.lang) { settings.lang = d.lang; saveSettings(); applyLang(); render(); return; }
  if (d.theme) { settings.theme = d.theme; saveSettings(); applyTheme(); render(); return; }
  if (d.unit) { settings.unit = d.unit; saveSettings(); render(); return; }
  if (d.len) { settings.length = +d.len; saveSettings(); render(); return; }
  if (d.share) { shareResult(history[+d.share]); return; }
  if (d.del) { history.splice(+d.del, 1); saveHistory(); render(); return; }
  if (el.id === "stage") { if (app.test.phase === "idle" || app.test.phase === "error") startTest(); return; }
  switch (d.act) {
    case "start": startTest(); break;
    case "stop": stopTest(); break;
    case "share-current": {
      const t = app.test;
      shareResult({ time: t.finishedAt, d: t.down, u: t.up, p: t.ping, j: t.jitter, isp: t.server.isp, srv: t.server.serverLabel, src: settings.source,
        dlp: t.dlp, ulp: t.ulp, bbg: t.grade, ds: downsample(t.downSamples), us: downsample(t.upSamples) });
      break;
    }
    case "ping-start": startPing(($("ping-host") || {}).value); break;
    case "ping-stop": stopPing(); render(); break;
    case "export": exportCsv(); break;
    case "clear": if (confirm(S.clear_history_confirm)) { history = []; saveHistory(); render(); } break;
  }
});
document.addEventListener("input", e => { if (e.target.id === "ping-host") ping.host = e.target.value; });
document.addEventListener("keydown", e => {
  if (e.target.id === "ping-host" && e.key === "Enter") { e.target.blur(); startPing(e.target.value); }
  if (e.target.id === "stage" && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); startTest(); }
});
const press = on => e => { if (e.target.closest("#stage.idle")) gauge.pressTarget = on ? 0.92 : 1; else gauge.pressTarget = 1; };
document.addEventListener("pointerdown", press(true));
document.addEventListener("pointerup", press(false));
document.addEventListener("pointercancel", press(false));
window.addEventListener("online", () => render());
window.addEventListener("offline", () => render());
let resizeT;
window.addEventListener("resize", () => { clearTimeout(resizeT); resizeT = setTimeout(render, 150); });

// Scalloped "cookie" outline for the empty-history badge (ScallopShape in the app).
(() => {
  const pts = [];
  for (let i = 0; i <= 360; i += 2) {
    const a = i * Math.PI / 180, r = 0.5 * (1 - 0.06 + 0.06 * Math.cos(8 * a));
    pts.push(`${(0.5 + r * Math.cos(a)).toFixed(4)},${(0.5 + r * Math.sin(a)).toFixed(4)}`);
  }
  document.getElementById("scallop-path").setAttribute("d", "M" + pts.join("L") + "Z");
})();

applyLang();
applyTheme();
render();
})();
