# Publishing Pulse on Google Play

Everything the Play Console asks for, ready to copy. Build with:

```bash
./gradlew bundlePlayRelease
```

Upload `app/build/outputs/bundle/playRelease/app-play-release.aab`.

## Builds

| Flavor | Use | Differences |
|---|---|---|
| `play` | Google Play (`.aab`) | No self-updater and no `REQUEST_INSTALL_PACKAGES` (Play policy); Play delivers updates |
| `github` | GitHub Releases (`.apk`) | Checks GitHub Releases and installs updates itself |

Both builds use the same package name, `com.dorp.pulse`, and the same signing key, so users can move from one to the other.

## Signing

- Join **Play App Signing** and use `~/.pulse-speed/release.jks` (alias `pulse`) as the **upload key**.
- After the first upload, copy the **App signing key certificate SHA‑1** (Play Console → Test and release → App integrity) into Firebase (Project settings → your Android app → Add fingerprint). Without it, Google sign-in fails on installs from Play.

## Store listing

- **App name:** Pulse: Speed Test & Ping
- **Short description (EN):** Fast internet speed test with ping, bufferbloat, network scan and history.
- **Short description (HE):** בדיקת מהירות אינטרנט מהירה עם פינג, Bufferbloat, סריקת רשת והיסטוריה.
- **Full description (EN):**

  > Pulse measures your internet connection with a clean Material You design.
  >
  > • Download, upload, ping and jitter against Cloudflare, Ookla (speedtest.net), Netflix FAST or LibreSpeed servers, or pick a specific server
  > • Bufferbloat grade: see how much lag you get when the line is busy
  > • Ping any host, your router or DNS servers (ICMP, TCP or DNS timing) with live charts and packet loss
  > • Network scan: find the devices on your Wi‑Fi
  > • History with trends, CSV export and share-as-link
  > • Optional Google sign-in to sync history across devices
  > • Light, dark and OLED themes, English and Hebrew
  >
  > No ads. No tracking.

- **Full description (HE):**

  > Pulse בודקת את חיבור האינטרנט שלכם בעיצוב Material You נקי.
  >
  > • הורדה, העלאה, פינג וג׳יטר מול שרתי Cloudflare,‏ Ookla (speedtest.net),‏ FAST של Netflix או LibreSpeed, או בחירת שרת מסוים
  > • ציון Bufferbloat: כמה השהיה יש כשהקו עמוס
  > • פינג לכל מארח, לנתב או לשרתי DNS (ICMP,‏ TCP או DNS) עם גרף חי ואובדן חבילות
  > • סריקת רשת: כל המכשירים ב-Wi‑Fi שלכם
  > • היסטוריה עם מגמות, ייצוא CSV ושיתוף בקישור
  > • התחברות עם Google לסנכרון ההיסטוריה בין מכשירים (לא חובה)
  > • ערכות נושא בהירה, כהה ו-OLED, עברית ואנגלית
  >
  > בלי פרסומות. בלי מעקב.

- **Category:** Tools · **Tags:** speed test, network
- **Contact email:** required by Play. Use an address you're willing to make public.
- **Graphics** (in this repo):
  - App icon 512×512: [`docs/play/icon-512.png`](play/icon-512.png)
  - Feature graphic 1024×500: [`docs/play/feature-graphic.png`](play/feature-graphic.png)
  - Phone screenshots (1080×2400): `docs/screenshots/start.png`, `testing.png`, `result.png`, `history.png`, `ping.png`, `scan.png`, `servers.png`, `hebrew.png`

## App content

| Section | Answer |
|---|---|
| Privacy policy | https://dor2500.github.io/pulse-speed/privacy/ |
| Ads | No ads |
| App access | All features work without an account (sign-in is optional) |
| Target audience | 13+ (not designed for children) |
| Content rating | Utility. No violence, sex, gambling or user-to-user communication. Answer "No" to all. |
| News app / Health / Financial | No |
| Government app | No |
| Account deletion URL | https://dor2500.github.io/pulse-speed/delete-account/ |

### Data safety

- **Does the app collect or share user data?** Yes (only when the user signs in for cloud sync).
- **Encrypted in transit:** Yes. **Users can request deletion:** Yes (in-app *Delete account* and the URL above).
- **Collected (not shared):**
  - *Personal info → Name, Email address:* from Google sign-in; used for **Account management**; **optional**.
  - *App activity → Other user-generated content* (speed test results): stored for **App functionality** (sync); **optional**.
  - *Device or other IDs:* not collected.
- **Not collected:** location, contacts, photos, files, messages, financial or health data.
- Speed test servers see the user's IP address as part of the test. That's traffic to a third-party service the user picks, not data Pulse collects.

Firebase is configured (project `pulse-speed-7d280`, Firestore in me-west1, Tel Aviv), so builds made with `app/google-services.json` include sign-in and the answers above apply. If you publish **without** Firebase (no `google-services.json`), sign-in is hidden. Then answer "No data collected".

## Android TV (optional)

The app already has a Leanback launcher entry. To list it for TV as well, Play needs TV screenshots and a 320×180 banner with the app name in the app. Leave Android TV unchecked in *Advanced settings → Form factors* until those are added.

## Known review risks

- Ookla and FAST are reached through the same public endpoints their own websites use. These aren't official APIs. If Play or either company objects, ship with Cloudflare and LibreSpeed only.
- New personal developer accounts must run a **closed test with at least 12 testers for 14 days** before production access.

## Checks run before release (v1.7.1)

- Microsoft Defender scan of the APK, AAB, source and site files: no threats.
- Android lint (`lintPlayRelease`, `lintGithubRelease`): 0 errors.
- Play build permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, plus Google Play services' own `READ_GSERVICES`. No dangerous permissions.
- Network endpoints in the code: Cloudflare, Ookla, FAST, LibreSpeed, GitHub (updater, GitHub build only) and the share-link page.
