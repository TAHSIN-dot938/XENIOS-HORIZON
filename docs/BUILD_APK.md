# Getting the debug APK — zero cost

**Why this document exists:** the environment I work in has no Android SDK, no Gradle and no internet, so I cannot compile an APK
myself (evidence in `docs/STATUS.md`). GitHub's free build servers can, hands-off, using `.github/workflows/android-build.yml`.
Everything below is free. Items marked *(expected)* are what the workflow is written to produce; **I have not yet seen a real run**.

## Option A — no Android Studio needed (≈10 minutes of your time, then it builds itself)
1. Create a free GitHub account → **New repository** (private is fine, leave it empty: no README/license).
2. In a terminal, in the folder containing `the-horizon.bundle`:
   ```
   git clone the-horizon.bundle horizon
   cd horizon
   git remote remove origin
   git remote add origin https://github.com/<YOUR_USER>/<YOUR_REPO>.git
   git push -u origin main
   ```
   *(expected last line)* `branch 'main' set up to track 'origin/main'.`
   The push starts the build automatically (it will warn that secrets are missing — that's fine for now).
3. Repo → **Settings → Secrets and variables → Actions → New repository secret**, add:
   - `SUPABASE_URL` = `https://<your-new-project>.supabase.co`
   - `SUPABASE_ANON_KEY` = the **anon / publishable** key. **Never** the `service_role` key.
   - (optional) `BACKEND_URL`
4. **Actions** tab → **android-build** → **Run workflow** → branch `main`.
5. Wait ~8–12 min. *(expected)* green check. Open the run → **Summary**. It should show a block like:
   ```
   package: name='com.tahsin.thehorizon' versionCode='1' versionName='0.1.0' ...
   sdkVersion:'26'            (minSdk 26 = Android 8.0)
   targetSdkVersion:'35'
   application-label:'THE HORIZON'
   uses-permission: ...INTERNET, ...ACCESS_NETWORK_STATE (+ one AndroidX-added signature permission)
   Verifies / Number of signers: 1 / CN=Android Debug      (debug-signed: installable, not Play-Store-ready)
   ## Android unit tests:  tests=74 passed=74 failures=0 errors=0 skipped=0
   ```
6. Scroll to **Artifacts** → download **the-horizon-debug-apk** (a zip) → unzip → `app-debug.apk`.
   If a step is red, open it, copy the error text and send it to me; I will fix the code and you re-run.
7. **Install:**
   - Phone: copy `app-debug.apk` over, open it, allow "Install unknown apps" for that file manager/browser → Install.
   - Or USB: enable Developer options + USB debugging (on Xiaomi/Redmi also **Install via USB**), then
     `adb install -r app-debug.apk` *(expected)* `Success`.

## Option B — local Android Studio (free)
1. Install Android Studio (bundles JDK 17 and the SDK manager). Open the `android/` folder. Studio reads
   `gradle/wrapper/gradle-wrapper.properties` and downloads Gradle 8.9 itself.
2. `cp local.properties.example local.properties`, set `SUPABASE_URL` and `SUPABASE_ANON_KEY` (anon key only).
3. Run ▶ on a device/emulator, **or** in the Studio terminal:
   ```
   gradle wrapper --gradle-version 8.9      # one-time, if you have Gradle installed; Studio's terminal can also use ./gradlew after sync
   ./gradlew clean assembleDebug
   ```
   *(expected)* `BUILD SUCCESSFUL`; APK at `android/app/build/outputs/apk/debug/app-debug.apk`.
4. Tests: `./gradlew testDebugUnitTest` *(expected)* `BUILD SUCCESSFUL`; report in `app/build/reports/tests/testDebugUnitTest/index.html` showing 74 tests.

## What a successful first launch looks like
Dark screen with the half-sun logo rising → "XENIOS IS WELCOMING YOU" → sign-in screen.
If you see *"Backend not configured"* the Supabase secrets/`local.properties` were empty when it was built.
