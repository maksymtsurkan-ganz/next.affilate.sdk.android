# Next Affiliate Attribution SDK — Android

Lightweight Kotlin SDK that recovers the signed attribution token `nx_pb` (and, for the
deferred case, a `clickId`) for a merchant app and exposes it so the host app can forward it
into its server-to-server conversion / postback.

`nx_pb` is an **opaque** signed token — the SDK never parses it; it stores and forwards it
as-is. The NEX server decodes it at postback time.

- Pure Kotlin, **no heavy dependencies** (coroutines + `HttpURLConnection` + `SharedPreferences`).
- `minSdk 21`.
- All network calls are **best-effort**: they run on a background dispatcher, catch every
  error and return `null` — they never throw to the host app.

## Install

### Via JitPack (git, no registry)

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

App `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.maksymtsurkan-ganz:next.affilate.sdk.android:main-SNAPSHOT")
}
```

### Via git submodule (alternative)

```bash
git submodule add https://github.com/maksymtsurkan-ganz/next.affilate.sdk.android.git \
    libs/next-affiliate-sdk
```

`settings.gradle.kts`:

```kotlin
include(":sdk")
project(":sdk").projectDir = file("libs/next-affiliate-sdk/sdk")
```

App `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":sdk"))
}
```

## App setup

The SDK needs `INTERNET` (declared by the library manifest). To receive deep links, register
both intent-filters in your app's `AndroidManifest.xml` and route the incoming intent into
`handleLink`:

```xml
<activity android:name=".MainActivity" android:exported="true" android:launchMode="singleTask">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>

    <!-- Custom scheme: myapp://open?nx_pb=...&nx_click_id=... -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="myapp" android:host="open" />
    </intent-filter>

    <!-- App Links: https://<slug>.<baseDomain>/trk/<shortCode> -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="*.next-ads-server-dev.com" android:pathPrefix="/trk/" />
    </intent-filter>
</activity>
```

For App Links to verify automatically, the server must host an
`/.well-known/assetlinks.json` for the domain referencing your app's package + signing
certificate fingerprint.

## Usage (5 lines)

```kotlin
NextAffiliate.configure(context, NextAffiliateConfig(baseDomain = "next-ads-server-dev.com", scheme = "myapp"))
lifecycleScope.launch { NextAffiliate.checkDeferredOnFirstLaunch() }            // first launch only
lifecycleScope.launch { NextAffiliate.handleLink(intent.dataString) }          // on every deep-link
val attribution = NextAffiliate.getAttribution()                               // read stored value
attribution?.nxPb?.let { /* forward into your S2S conversion */ }
```

## Public API

| Member | Description |
| --- | --- |
| `configure(context, config)` / `init(...)` | Configure once, typically in `Application.onCreate`. |
| `suspend handleLink(url): Attribution?` | Handle a scheme or `https://.../trk/<code>` link. |
| `suspend checkDeferredOnFirstLaunch(): Attribution?` | Deferred match, guarded to run once per install. |
| `getAttribution(): Attribution?` | Currently stored attribution. |
| `clearAttribution()` | Wipe stored attribution (keeps the deferred-checked guard). |

```kotlin
data class Attribution(
    val nxPb: String?,
    val clickId: String?,
    val source: AttributionSource,   // SCHEME | UNIVERSAL_LINK | DEFERRED
) { val isAttributed: Boolean }      // nxPb != null || clickId != null
```

`NextAffiliateConfig(baseDomain, scheme, timeoutMs = 4000, deferredMatchBaseUrl = null)`.

## How it works

- **Custom scheme** (`myapp://open?nx_pb=…&nx_click_id=…`): reads the query params directly.
- **Universal / App Link** (`https://<slug>.<baseDomain>/trk/<code>`): re-hits the redirect
  with a **spoofed desktop User-Agent** and **does not follow redirects**, then reads `nx_pb`
  from the `Location` header. No `nx_pb` ⇒ not attributed. This call also records the click.
- **Deferred match** (`POST https://<baseDomain>/trk/deferred-match`, body
  `{"platform":"android"}`): on `matched` with a `clickId`, stores it. Runs once per install.

## Run the sample app

```bash
./gradlew :sample:installDebug      # requires the Android SDK + a device/emulator
```

The sample shows the current `Attribution` and has buttons: **Check deferred**,
**Simulate scheme link**, **Send test conversion**, **Clear attribution**. Its dev
`baseDomain` is set in `sample/.../SampleApp.kt`.

Simulate a real scheme open from a shell:

```bash
adb shell am start -a android.intent.action.VIEW \
    -d "myapp://open?nx_pb=DEMO_TOKEN&nx_click_id=demo-click" com.nextaffiliate.sample
```

## Build & test

```bash
./gradlew :sdk:assembleRelease       # build the library
./gradlew :sdk:testDebugUnitTest     # run the unit tests
```

## License

MIT — see [LICENSE](LICENSE).
