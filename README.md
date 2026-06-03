# AutoPilot — on-device Android automation (no root, no PC, no third‑party frameworks)

AutoPilot is a self-contained Android app that automates **other apps**
(Shopee, TikTok, Instagram, Facebook, …) directly on the phone. It is built on
Android's official **`AccessibilityService`** API — the only way to read and
control other apps' UI **without root and without a connected computer**.

There are **no third‑party automation dependencies** (no Appium, no UIAutomator
server, no ADB bridge). Everything runs as one normal APK you install on the
device.

> ⚠️ **Use responsibly.** Automating third‑party apps may violate their Terms of
> Service and can put your account at risk. This project is provided for
> personal/educational use — you are responsible for how you use it.

---

## How it works

| Capability | API used |
|---|---|
| Read the current screen (find buttons/text/fields) | `AccessibilityNodeInfo` (`rootInActiveWindow`, `findAccessibilityNodeInfosByViewId`) |
| Click / set text on a node | `AccessibilityNodeInfo.performAction(ACTION_CLICK / ACTION_SET_TEXT)` |
| Tap / swipe / scroll by coordinates (for video feeds, games, custom canvases) | `AccessibilityService.dispatchGesture()` |
| Back / Home / Recents / Notifications | `performGlobalAction()` |
| Floating Start/Stop control over any app | `WindowManager` overlay of type `TYPE_ACCESSIBILITY_OVERLAY` (no "draw over apps" permission needed) |

Automations are described as **JSON "macros"** — a list of steps. This means you
can add or tweak flows for new apps **without recompiling**.

### Project layout

```
app/src/main/java/com/autopilot/assistant/
├── service/
│   ├── AutomationAccessibilityService.kt  # the always-on service (the "brain")
│   └── OverlayController.kt               # draggable floating Start/Stop button
├── engine/
│   ├── MacroEngine.kt                     # executes a macro step-by-step
│   ├── NodeFinder.kt                      # find nodes by text / id / description
│   ├── Gestures.kt                        # tap / swipe via dispatchGesture
│   └── MacroLoader.kt                     # loads macros from assets + user folder
├── model/Macro.kt                         # Macro / Step data model + JSON parsing
└── ui/MainActivity.kt                     # enable service, list & run macros
app/src/main/assets/macros/*.json          # bundled example macros
```

---

## Build

Requirements: JDK 17 and the Android SDK (API 34). The Gradle wrapper is checked in.

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Install on a device (USB debugging on):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in **Android Studio** and press Run.

## Enable on the phone

1. Install the APK and open **AutoPilot**.
2. Tap **Open Accessibility settings** → find **"AutoPilot Automation"** →
   turn it **On** and accept the prompt.
3. Return to AutoPilot. Status should read **CONNECTED**, and a small floating
   **AutoPilot** button appears on screen.
4. Tap **Run** on a macro. AutoPilot steps aside and runs it on the target app.
5. Tap the floating button (it shows **STOP** while running) to abort anytime.

> The floating button is a `TYPE_ACCESSIBILITY_OVERLAY`, so it does **not**
> require the separate "Display over other apps" permission.

---

## Writing macros

A macro is a JSON object with `name` and a list of `steps`. Bundled examples
live in [`app/src/main/assets/macros`](app/src/main/assets/macros). To add your
own **without rebuilding**, drop `.json` files into the on-device folder shown
on the app's main screen:

```
Android/data/com.autopilot.assistant/files/macros/
```

A user file overrides a bundled macro with the same `id`.

### Step reference

| `type` | Fields | What it does |
|---|---|---|
| `launch_app` | `packageName` | Launch an app by package id |
| `click_text` / `click` | `text`, `match` | Find a node whose text matches and click it (falls back to tapping its center) |
| `click_id` | `viewId` | Click a node by resource id |
| `click_desc` | `description`, `match` | Click a node by content description |
| `set_text` / `type` | `viewId`/`text`/`description` selector + `text` | Type into a field |
| `tap` | `x`, `y` | Tap absolute screen coordinates |
| `swipe` | `x`,`y`,`x2`,`y2`,`durationMs` | Swipe between two points |
| `scroll` | `direction` (`up`/`down`/`left`/`right`), `durationMs` | Swipe-scroll the screen |
| `wait` / `delay` | `durationMs` | Pause |
| `wait_for_text` | `text`, `match`, `timeoutMs` | Wait until text appears |
| `wait_for_id` | `viewId`, `timeoutMs` | Wait until a view id appears |
| `back` / `home` / `recents` / `notifications` | — | Global navigation actions |

Common fields:
- `match`: `contains` (default), `exact`, or `ignore_case`.
- `optional`: if `true`, the macro continues even when the step fails (useful for
  selectors that may not always be present).

### Example

```json
{
  "id": "shopee_search",
  "name": "Shopee: search a keyword",
  "targetPackage": "com.shopee.id",
  "steps": [
    { "type": "launch_app", "packageName": "com.shopee.id" },
    { "type": "wait", "durationMs": 6000 },
    { "type": "click_desc", "description": "Search", "optional": true },
    { "type": "set_text", "viewId": "com.shopee.id:id/et_search_keyword", "text": "headphones", "optional": true }
  ]
}
```

---

## Important limitations (Android, not bugs)

- **Video/game content can't be read.** TikTok/Instagram video frames render in a
  `SurfaceView`/OpenGL surface that exposes no nodes. Their *buttons* (like,
  follow, comment, next) are usually accessibility nodes you can click; advance
  the feed with coordinate `scroll`/`swipe`.
- **Secure screens are off-limits.** Anything flagged `FLAG_SECURE` (e.g. payment
  / banking screens) cannot be read or captured.
- **Selectors drift.** Shopee/TikTok/IG/FB change layouts and obfuscate resource
  ids frequently. Prefer `text`/`description` selectors, keep coordinate taps as a
  fallback, and mark fragile steps `optional`. Because flows are JSON, you adjust
  them on-device without rebuilding.
- **User must enable the service manually** in Accessibility settings — Android
  requires this for security and it cannot be bypassed.
- **Google Play distribution** of non-accessibility AccessibilityService apps is
  restricted. For personal use, sideload the APK.

## How to capture selectors for a new app

To discover the `text` / `viewId` / `description` of on-screen elements while
developing, use the SDK's built‑in tools from a dev machine (only for inspection;
the app itself needs none of this at runtime):

```bash
adb shell uiautomator dump && adb pull /sdcard/window_dump.xml
# or use Android Studio's Layout Inspector
```

Then translate what you see into macro steps.
