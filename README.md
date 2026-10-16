# my_android_deeplink_sdk

A self-hosted Android SDK for handling **Deep Links** and **Deferred Deep Links** on Android.  
⚡ No third-party services required — built with pure Kotlin.

---

## ✨ Features
- 📱 Native Android (Kotlin) implementation
- 🔗 Easy setup for deep links and deferred deep links
- ⚡️ No third-party dependencies beyond OkHttp — pure Kotlin implementation
- 🧪 Built-in support for custom JSON payloads
- 🛠 Compatible with Android API Level 21+
- 🔄 Coroutine-based async handling via `lifecycleScope`

---

## 📦 Installation

Copy the following Kotlin source files from this repository into your app module:

```
app/src/main/kotlin/com/tecocraft/sdk/myandroiddeeplinksdk/
├── MyDeeplinkSdk.kt
├── DynamicLinkBackendClient.kt
├── SmartLinking.kt
├── models.kt
├── MyDeeplinkSdkConfig.kt
└── Exceptions.kt
```

After copying the files, add the required dependencies to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
}
```

Then sync your project:

```sh
./gradlew --refresh-dependencies
```

> `--refresh-dependencies` forces Gradle to download the newly added libraries. Always sync after modifying `build.gradle.kts`.

---

## ⚡ Quick Start (60 seconds)

1) Add INTERNET permission

Add the permission to `app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

2) Add a custom scheme

Add an `intent-filter` inside your main `<activity>` in `AndroidManifest.xml`:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="song" />
</intent-filter>
```

3) Initialise the SDK

Create an `Application` subclass and call `init()` in `onCreate()`:
```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MyDeeplinkSdk.init(
            context = this,
            config  = mapOf(
                "app_id"                to packageName,
                "base_url"              to "https://backend.linkhas.com",
                "path_short_code_hosts" to listOf("backend.linkhas.com"),
            )
        )
    }
}
```

Register it in `AndroidManifest.xml`:
```xml
<application
    android:name=".MyApp"
    ... >
```

4) Start smart linking

Call `startSmartLinking()` from your launcher `Activity` and forward runtime intents via `onNewIntent()`:
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyDeeplinkSdk.startSmartLinking(
            scope        = lifecycleScope,
            launchIntent = intent,
            options      = SmartLinkingOptions(
                onSuccess = ::routeFromDeepLink,
            ),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        MyDeeplinkSdk.onNewIntent(
            scope   = lifecycleScope,
            intent  = intent,
            options = SmartLinkingOptions(onSuccess = ::routeFromDeepLink),
        )
    }
}
```

5) Handle the link

```kotlin
fun routeFromDeepLink(data: DynamicLinkData) {
    val payload = data.customData
    // Write your navigation logic here based on the payload
}
```

6) Validate

```sh
adb shell am start -W -a android.intent.action.VIEW -d "song://open/profile" com.yourapp.package
```

For HTTPS App Links, see the full Setup section below.

---

## 🚀 Usage

### Using the startSmartLinking method

`startSmartLinking()` initialises the SDK flow and starts listening for both the initial deep-link Intent and subsequent deep-link events.

```kotlin
MyDeeplinkSdk.startSmartLinking(
    scope        = lifecycleScope,
    launchIntent = intent,
    options      = SmartLinkingOptions(
        onSuccess = ::routeFromDeepLink,
        onError   = { e -> Log.e("DeepLink", "Error: $e") },
        onUrl     = { url -> Log.d("DeepLink", "Incoming URL: $url") },
    )
)
```

---

### Handling the Link Payload

`routeFromDeepLink` receives a `DynamicLinkData` object and handles navigation. The logic inside will vary based on your app's screen structure — customise it accordingly:

```kotlin
fun routeFromDeepLink(data: DynamicLinkData) {
    val payload = data.customData
    val screen  = payload["screen"]?.toString()?.trim()?.lowercase()

    when {
        screen == "library" || payload["song_id"] != null -> {
            val id = payload["song_id"]?.toString() ?: "UNKNOWN"
            Log.d("DeepLink", "Navigating to song: $id")
            startActivity(
                Intent(this, LibraryActivity::class.java)
                    .putExtra("song_id", id)
            )
        }
        screen == "profile" -> {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        else -> {
            // Navigate to home / fallback
        }
    }
}
```

> The payload keys (`screen`, `song_id`, etc.) are defined when you create the link in the Dynamic Link Tool.

---

## ✅ Validate Your Setup

Run these commands after building the app (replace `com.yourapp.package`):

- Android (Device/Emulator):
```sh
adb shell am start \
  -W -a android.intent.action.VIEW \
  -d "song://open/profile" \
  com.yourapp.package
```

If using HTTPS App Links, verify your hosted file:

- Android: `https://<your-domain>/.well-known/assetlinks.json`

---

## ⚙️ Setup

### 🔹 Android (Schemes & App Links)

1. Open `app/src/main/AndroidManifest.xml` and add intent filters inside your main `<activity>`.

Scheme-based (custom scheme like `song://`):
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="song" />
</intent-filter>

<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="newSong" />
</intent-filter>
```

App Links (HTTPS, verified):
```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data
        android:scheme="https"
        android:host="backend.linkhas.com"
        android:pathPrefix="/" />
</intent-filter>
```

2. Each scheme must have its own separate `intent-filter` block — do not combine multiple schemes in one filter.

3. Rebuild the Android app after any changes to `AndroidManifest.xml`.

### 🔹 Android Product Flavors (Multiple Environments)

If your project uses **product flavors** (e.g. `dev`, `staging`, `production`), each flavor typically has its own application ID. You need to register schemes for each flavor separately.

Flavor manifests are usually located at:

```
app/src/<flavor>/AndroidManifest.xml
```

Example for a `production` flavor:
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="song" />
</intent-filter>
```

> If you are not using product flavors, you do not need this section. The standard Android setup above is sufficient.

---

## 🔗 Generating Links

Links are created and managed through the [Dynamic Link Tool](https://backend.linkhas.com) web interface.

1. Register and log in to your account.
2. Click **Add Project** and fill in your app details — package name, store URL, and publication status.
3. Inside the project, go to **View Links → Add Link**.
4. Set the title, description, and URL schemes. The scheme values must exactly match what is configured in `AndroidManifest.xml`.
5. Add a **JSON Data** payload to control in-app navigation:

```json
{
    "extra": { "source": "deeplink" },
    "screen": "library",
    "song_id": 2
}
```

---

## 📖 API Reference

### `MyDeeplinkSdk.init(context, config)`
Initialises the SDK. Must be called before `startSmartLinking`. Typically placed in `Application.onCreate()`.

### `MyDeeplinkSdk.startSmartLinking(scope, launchIntent, options)`
Starts listening for incoming deep link events and calls the provided callbacks.

### `MyDeeplinkSdk.onNewIntent(scope, intent, options)`
Call from `Activity.onNewIntent()` to handle deep links received while the app is already running.

### `MyDeeplinkSdk.stopSmartLinking()`
Cancels the active smart-linking coroutine job.

### `MyDeeplinkSdk.getLinkByCodeTyped(shortCode)`
Fetches and returns `DynamicLinkData` by short code. Must be called on a background thread.

### `MyDeeplinkSdk.getInitialLink(intent)`
Returns the deep-link URI that launched the app, or `null` if none.

### `MyDeeplinkSdk.hasHandledFirstInstall()`
Returns `true` if the deferred deep link first-install flow has already been completed.

### `MyDeeplinkSdk.resetFirstInstallFlag()`
Clears the first-install flag. Use in debug/QA builds to re-test the deferred deep link flow.

### `SmartLinkingOptions`

| Option | Type | Description |
|---|---|---|
| `onSuccess` | `((DynamicLinkData) -> Unit)?` | Called when a deep link is successfully resolved |
| `onError` | `((Throwable) -> Unit)?` | Called on any error during smart linking |
| `onUrl` | `((String) -> Unit)?` | Called when a deep-link URL is detected, before resolution |

### `DynamicLinkData`

| Field | Type | Description |
|---|---|---|
| `linkId` | `String` | Unique link identifier |
| `name` | `String` | Link name |
| `description` | `String` | Link description |
| `shortCode` | `String` | The short code used to look up the link |
| `customDomain` | `String?` | Custom domain, if configured |
| `projectId` | `String` | Parent project identifier |
| `androidScheme` | `String` | Android URL scheme configured for this link |
| `customData` | `Map<String, Any?>` | The JSON payload defined when the link was created |
| `project` | `DynamicLinkProject` | Parent project details |
| `params` | `Map<String, Any?>` | Alias for `customData` |

---

## 🧪 Testing

### Test Deep Links
The app must be installed on the device. Run the ADB command — it should open the app and navigate to the correct screen.

### Test Deferred Deep Links
The app must be published on Google Play Store. Click the link on a device where the app is not installed — it will redirect to the store, and after installation, navigate to the intended screen on first launch.

> If the app is not published, the SDK will return null as the response.

### Test Updated Logic Without Re-publishing

Once the app has been published at least once, you can test updated routing logic without submitting a new store release:

1. Implement the updated logic in your Kotlin codebase.
2. Completely uninstall the app from your test device.
3. Click the dynamic link — the device will be redirected to the Play Store page.
4. **Do not install from the store.** Instead, run the app directly from Android Studio.
5. The SDK will pick up the deferred payload and your updated logic will execute.

> This bypass only works after the app has been published at least once.

---

## 📋 Requirements

- Android API Level >= 21 (Android 5.0 Lollipop)
- Kotlin >= 1.9.0
- OkHttp >= 4.x
- AndroidX `lifecycle-runtime-ktx` (for `lifecycleScope`)
- `INTERNET` permission in `AndroidManifest.xml`

---

## 🤝 Contributing

Pull requests are welcome! For major changes, please open an issue first to discuss what you would like to change.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

MIT © [Tecocraft](https://github.com/tecocraft)

---

## 🛠️ Troubleshooting

- **Links not opening on Android**
    - Verify your `AndroidManifest.xml` intent-filters and ensure each scheme has its own separate filter block.
    - For App Links, confirm `assetlinks.json` is reachable and the SHA-256 fingerprints match your signing key.
    - Rebuild the app after any manifest changes.
    - Ensure `INTERNET` permission is declared in the manifest.

- **`MyDeeplinkSdk.init()` not called error**
    - Ensure `init()` is called in `Application.onCreate()`, not inside an Activity.
    - Register your `Application` subclass in `AndroidManifest.xml` via `android:name`.

- **SDK returning null for deferred deep links**
    - Ensure the app is published on Google Play Store. Deferred deep links do not work with unpublished apps.
    - Verify `app_id` matches your published package name exactly.

- **`postPendingRedirect` throws `IllegalStateException`**
    - `app_id` must be set in `MyDeeplinkSdk.init()`. Check your config map in `Application.onCreate()`.

- **Coroutine / threading errors**
    - All network calls run on `Dispatchers.IO` internally. Always pass `lifecycleScope` as the `scope` parameter.
    - Do not call `getLinkByCode()` or `postPendingRedirect()` directly on the main thread.

---

## 🙋‍♀️ Support

- 📧 Email: support@tecocraft.com
- 🐛 Issues: [GitHub Issues](https://github.com/linkhas/Flutter-Dynamic-Link-SDK/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/linkhas/Flutter-Dynamic-Link-SDK/discussions)
