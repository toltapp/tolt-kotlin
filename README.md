# Tolt for Android

Let users enter a partner's code in your Android app and credit that partner with the user's purchases. Purchases reach Tolt through your RevenueCat integration, matched on the same app user id.

## Install

Add the dependency to your app module. The library needs `minSdk` 21 or later.

```kotlin
dependencies {
    implementation("com.tolt:tolt-android:0.1.0")
}
```

## Use

Configure once at launch, in your `Application`, with your organization's public id (the same `data-tolt` value tolt.js uses):

```kotlin
import com.tolt.Tolt

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Tolt.configure(this, publicId = "YOUR_PUBLIC_ID")
    }
}
```

When the user enters a code, claim it for their app user id. Use the id you pass to RevenueCat as `appUserID`. The claim functions suspend, so call them from a coroutine such as `lifecycleScope.launch`:

```kotlin
try {
    val result = Tolt.claim(code = code, appUserId = Purchases.sharedInstance.appUserID)
    // result.status is CLAIMED, or ALREADY_CLAIMED if this user claimed a code before
} catch (e: ToltException.CodeNotFound) {
    // show "That code doesn't exist"
} catch (e: ToltException.CodeConflict) {
    // the code is ambiguous, or the program's claim window has closed
}
```

Also store the code on the RevenueCat customer. Tolt uses it if a purchase arrives before the claim does:

```kotlin
Purchases.sharedInstance.setAttributes(Tolt.revenueCatAttributes(code))
```

The SDK records when the app was first opened and sends it with each claim, so programs with a claim window can enforce it.

## Partner links

Partners in an app program can also share a link instead of a code. The link records a click and sends the user to your deep link template (set in the program's settings), with the partner's code and click id filled in through `{code}` and `{click_id}`.

If the link opens your app through an App Link or a custom scheme, pass the `Uri` to Tolt. Use a template like `myapp://claim?tolt_code={code}&tolt_click={click_id}` and an intent filter for it:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="myapp" android:host="claim" />
</intent-filter>
```

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    val uri = intent.data ?: return
    lifecycleScope.launch { Tolt.claim(uri, appUserId = appUserId) }
}
```

Do the same with `intent.data` in `onCreate` for links that start the app. `claim(uri, appUserId)` returns `null` when the link has no `tolt_click` or `tolt_code`, so you can call it for every link your app opens.

If you use a deep linking provider, put `{code}` and `{click_id}` in its link parameters, then claim from its deep link callback, which also covers fresh installs:

```kotlin
Tolt.claimClick(clickId = clickId, appUserId = appUserId)
```

## Google Play installs

When the program has no deep link template but has a Google Play URL, partner links opened on Android go to Google Play and carry the code and click in the install referrer. Claim it when the app starts:

```kotlin
lifecycleScope.launch {
    Tolt.claimFromInstallReferrer(appUserId = Purchases.sharedInstance.appUserID)
}
```

It returns `null` when the install didn't come from a partner link. The referrer is claimed once per install: after a claim, a rejected claim (`CodeNotFound`, `CodeConflict`) or a referrer without Tolt values, later calls return `null` without contacting Google Play or Tolt. If Google Play can't be reached, or the claim fails with a network error, `RateLimited` or a 5xx `Server` error, the next call tries again.

## Errors

Claims throw a `ToltException`, or an `IOException` when the request can't reach Tolt.

| Exception | Meaning |
| --- | --- |
| `NotConfigured` | `Tolt.configure` was not called |
| `InvalidInput` | The code, click id or app user id was blank (`field` says which) |
| `CodeNotFound` | No active partner has this code, or the click doesn't exist |
| `CodeConflict` | The code belongs to more than one partner, or the claim window closed |
| `RateLimited` | Too many claims from this device's network; retry later |
| `Server` | Anything else, with the HTTP `status` and `message` |

## Sample app

`sample/` is a small app that claims codes, `toltdemo://claim` links and the install referrer, and logs each result under the `ToltSample` tag. Install it on a device or emulator with your public id:

```bash
./gradlew :sample:installDebug -Ptolt.publicId=YOUR_PUBLIC_ID
```

Then open a link the way a partner link would:

```bash
adb shell am start -a android.intent.action.VIEW -d "toltdemo://claim?tolt_code=SAM20"
```

To try real partner links, set the program's deep link template to `toltdemo://claim?tolt_code={code}&tolt_click={click_id}`.
