# Setting up Google sign-in and cloud sync

Pulse builds and runs without Firebase; the **Cloud sync** card in Settings only appears when the build
includes a `google-services.json`. To turn it on:

1. **Create a Firebase project** at <https://console.firebase.google.com> (Analytics not needed).
2. **Add an Android app** with package name `com.dorp.pulse`. Add the **SHA-1** fingerprints of every key
   you sign with (Firebase uses them to create the Android OAuth client):
   ```bash
   keytool -list -v -keystore ~/.pulse-speed/release.jks -alias pulse
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```
3. **Authentication → Sign-in method → Google → Enable.** This creates the *Web client* whose ID the app
   passes to Credential Manager (it lands in `google-services.json` as `default_web_client_id`).
4. **Firestore Database → Create database** (production mode, any region), then paste
   [`firestore.rules`](../firestore.rules) into **Rules** and publish.
5. **Download `google-services.json`** into `app/` (it's git-ignored) and build:
   ```bash
   ./gradlew assembleRelease
   ```

Data layout: one document per test at `users/{uid}/results/{timestamp}`, with the same short keys the
app uses locally. The rules above let each user touch only their own documents.

Devices without Google Play services (Fire TV, some Android TV boxes) can't use Google sign-in; the card
explains that instead of failing silently.

## Share links

The **Share** button builds `https://dor2500.github.io/pulse-speed/r/#<data>`. The page is
[`docs/r/index.html`](r/index.html), served by GitHub Pages from the `docs/` folder on `main`
(Settings → Pages → Deploy from a branch → `main` / `/docs`). The result is deflated and base64url-encoded
into the URL fragment, which browsers never send to the server.
