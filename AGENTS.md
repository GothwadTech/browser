# AGENTS.md — Gothwad TV Browser

This file is the single source of truth for any AI agent (Google AI Studio, or any other) working on this codebase. Read this fully before making any change. It exists because this project has repeatedly suffered from the same class of mistake: quick hacks that "look fixed" in one narrow test but quietly break something else (page rendering, input handling, memory, or crash safety). The rules below exist to prevent that pattern from recurring.

---

## 0. Mission Statement — Read This First

**This is not a "TV app that happens to browse the web." This is a full-grade web browser.** It must behave, render, and perform to the same standard as Chrome on a PC/desktop — not a diminished, TV-specific, best-effort approximation of a browser.

The non-negotiable engineering principle for every change made to this project:

> **Prefer the native platform capability over a custom workaround, always.** Android, WebView, and the underlying Chromium engine already solve almost every problem this app will ever face — zoom, scrolling, text selection, dark mode, input handling, memory management. When something doesn't work, the correct fix is almost always "stop interfering with the native mechanism" or "use the native API correctly," not "write custom code to fake the native behavior."

Every custom workaround this project has ever added ("jugaad") has caused a worse bug than the one it tried to fix:
- A custom CSS `zoom` hack (to work around not wiring up native zoom controls) broke fixed-position page elements site-wide.
- A custom mouse-to-touch event conversion (to work around one device's broken click delivery) broke text selection and mouse drag on every device.
- A custom "force dark" toggle (left on by default) silently repainted every website's colors incorrectly.
- A custom shortcut-recording routine (that didn't account for modifier keys generating their own key-up events) silently corrupted every recorded shortcut.

**The pattern is always the same: a workaround was added instead of using/fixing the native mechanism, and it caused collateral damage elsewhere.** Do not repeat this pattern. If you find yourself about to inject JavaScript/CSS to fake a browser feature, intercept and re-synthesize an input event, or special-case a device/chipset — stop, and find the native API that already does this correctly.

If a genuine device-specific or OS-level bug is discovered that cannot be fixed with a native API, document it clearly as a known limitation rather than papering over it with a hack that affects all devices.

---

## 1. What This App Is

- **Product:** Gothwad TV Browser — a full-featured Android web browser.
- **Platforms it must work correctly on, using the *same* codebase:**
  - **Android TV** (D-pad/remote as the primary input; large-screen, 10-foot UI)
  - **Android tablets** (touch as the primary input, keyboard/mouse optional)
- **Input devices it must support simultaneously and correctly, without any one input mode breaking another:**
  - TV remote / D-pad navigation
  - Physical USB or Bluetooth keyboard (typing, shortcuts, modifier combos)
  - Physical USB or Bluetooth mouse (hover, left-click, right-click, click-drag text selection, scroll wheel)
  - Touchscreen (tablets)
- **Engine:** Android System WebView (Chromium-based). This app does not ship its own rendering engine — it relies on the OS-provided WebView, so behavior should match what that WebView/Chromium version does natively unless there's a specific, justified reason to differ.
- **Tech stack:** Kotlin, Gradle (Kotlin DSL), Room (local DB), Coroutines (background work), scaffolded/maintained via Google AI Studio.
- **Distinct feature set beyond "just a browser":** tabbed browsing (top-tab bar + row/grid tab views), ad blocking, a virtual cursor system (for D-pad-driven pointer navigation), downloads manager, TV Notes, File Manager, App Lock (PIN), Clipboard Manager (with history), configurable keyboard shortcuts, multi-language UI (10+ languages), sidebar-based navigation for History/Downloads/Favorites/Notes/Clipboard/Tabs/File Manager.

---

## 2. Architecture Map

### WebView / rendering engine layer
| File | Responsibility |
|---|---|
| `webengine/webview/WebViewEx.kt` | The core `WebView` subclass. WebSettings configuration, zoom, desktop-mode, dark-mode/color settings, native input event entry points (`dispatchGenericMotionEvent`, `dispatchTouchEvent`, `setOnContextClickListener`), context-menu triggering. |
| `webengine/webview/WebViewExClients.kt` | `WebViewClient` / `WebChromeClient` implementations — page load callbacks, `onRenderProcessGone` crash handling, console messages, file chooser, download interception. |
| `webengine/webview/WebViewWebEngine.kt` | Wraps `WebViewEx` behind the app's `WebEngine` abstraction; owns per-tab WebView lifecycle, crash-recovery/reload logic, `trimMemory()`. |
| `webengine/webview/HomePageHelper.kt` | Custom handling for the app's internal home page (favicon interception, etc). |
| `assets/generic_injects.js` | JavaScript injected into every loaded page. Keep this minimal — anything here runs on every page load and, if it attaches scroll/touch listeners, can affect scroll smoothness site-wide. |

### Tabs & state
| File | Responsibility |
|---|---|
| `activity/main/TabsModel.kt` | Tab collection, tab switching, per-host config caching (async, non-blocking), memory-pressure and LRU-based tab trimming. |
| `model/WebTabState.kt` (common module) | Per-tab persisted state: URL, title, thumbnail, saved WebView state, `trimMemory()`/restore logic, `lastActiveTimestamp`. |
| `Config.kt` (common module) | All user-configurable preferences (SharedPreferences-backed), including `maxLiveTabs`, `keepAliveInBackground`, ad-block settings, etc. |

### Input handling (the most fragile area historically — read carefully before touching)
| File | Responsibility |
|---|---|
| `utils/HardwareInputManager.kt` (common module) | Central point for hardware input device policy: user-configurable per-device blocking (`isDeviceBlocked`). **Must never contain event-type conversion logic** (e.g. mouse→touch) — see §3. |
| `utils/DPADNavigationEventsAdapter.kt` (common module) | Translates D-pad/remote key events into virtual-cursor motion for the on-screen cursor system. |
| `utils/BackNavigationEventsAdapter.kt` (common module) | Handles BACK-equivalent input (remote back button, gamepad B) only. Must never be repurposed to intercept unrelated input (e.g. mouse buttons). |
| `widgets/cursor/CursorLayout.kt` (common module) | The `FrameLayout` overlay that hosts the virtual D-pad-driven cursor and dispatches touch/motion/key events to the WebView beneath it. **Any code path here that can call back into its own `dispatchTouchEvent`/`dispatchGenericMotionEvent` (directly or indirectly) must guarantee the recursion terminates — see the historical StackOverflow bug in §5.** |
| `widgets/cursor/CursorDrawerDelegate.kt` (common module) | Draws the virtual cursor, handles D-pad-to-cursor-motion translation, synthesizes `MotionEvent`s for D-pad "click" (`dispatchMotionEvent` → `surface.dispatchTouchEvent`). Any state flag guarding a synthetic-event dispatch must be updated *before* the dispatch call that can re-enter this code, not after. |
| `activity/main/MainActivityDpad.kt` | Window-level `dispatchKeyEvent` interception (runs before the WebView or any view gets the event) — this is where keyboard shortcuts and D-pad focus-navigation between header/home/webview are resolved. |
| `singleton/shortcuts/Shortcut.kt` / `ShortcutMgr.kt` | Shortcut definitions and runtime matching (key-down + key-up modifier matching). Must reject pure-modifier keycodes as a shortcut's "main" key (defense already added — do not remove). |
| `activity/main/dialogs/ShortcutDialog.kt` | UI for recording a new shortcut. Must ignore modifier keys' own key-up events when recording a combo — only the non-modifier key's release finalizes the recording. |

**Golden rule for this whole area:** real mouse input must always be delivered to the WebView as real mouse `MotionEvent`s (`SOURCE_MOUSE`, correct `TOOL_TYPE`), real touch input as real touch events, real keyboard input as real key events. Never intercept an event and re-emit it as a different event type. Chromium's own input handling (click, hover, drag-select, scroll) is far more correct than anything this app could reimplement.

### Memory / process lifecycle
| File | Responsibility |
|---|---|
| `BrowserApp.kt` | `Application` class. Wires `ProcessLifecycleOwner` observers for the keep-alive foreground service and the system clipboard listener. Registers `UncaughtExceptionHandler` if/when crash logging is added. |
| `service/keepalive/BrowserKeepAliveService.kt` | Lightweight foreground service, started when the app backgrounds and stopped when it foregrounds, to reduce (not guarantee) the chance of the OS killing the process. Must remain lightweight — no real background work here. |

### Ad blocking
| File | Responsibility |
|---|---|
| `activity/main/AdblockModel.kt` | Real filter-list-based ad/tracker blocking engine (Brave's `AdBlockClient`), background-thread list loading/parsing/caching. |
| `activity/main/MainActivityAdBlock.kt` | Wires the WebView's resource-request interception to `AdblockModel`, plus popup/new-window blocking (uses cached, non-blocking host-config lookups). |

### Clipboard
| File | Responsibility |
|---|---|
| `notes/clipboard/ClipboardRepository.kt` | Clipboard-history persistence, feedback-loop guarding (`isInternalClipboardWrite`, `markCopiedByApp`) so the app's own clipboard writes don't create duplicate history entries. |
| `notes/clipboard/ClipboardDao.kt` | Room DAO for clipboard history. |
| System `ClipboardManager.OnPrimaryClipChangedListener` (registered in `BrowserApp.kt`) | Captures *any* text copied anywhere — including WebView's native page-text-selection copy — into the app's clipboard history, respecting foreground-only reads and incognito mode. |

### Favicons
| File | Responsibility |
|---|---|
| `singleton/FaviconsPool.kt` (common module) | Favicon fetch/cache, with an in-memory `LruCache<host, filename>` to avoid repeated disk stats, background-thread fetch-on-miss. |

### UI shell / DPAD navigation
| File | Responsibility |
|---|---|
| `activity/main/MainActivityDpad.kt` | Header ↔ native-home-grid ↔ top-tab-bar D-pad focus routing. Any focus-mapping logic added here must be symmetric (moving down then up returns focus to the same control) and must only target currently-visible views. |
| `activity/main/dialogs/*SidebarPopup.kt` | Newer sidebar-based UI for History/Downloads/Favorites/Notes/Clipboard/Tabs/File Manager. |

---

## 3. Hard Rules — Do Not Violate These

These are not style preferences. Each one maps to a real, previously-shipped bug (see §5 for the full incident log). Violating any of these is very likely to reintroduce a fixed bug.

1. **Never inject custom CSS/JS to fake a native browser feature** (zoom, dark mode, layout scaling, etc.) if WebView/Chromium already exposes a native API for it. Use `zoomIn()`/`zoomOut()`/`zoomBy()` for zoom (already enabled via `setSupportZoom`/`builtInZoomControls`), not CSS `zoom` injection. Use WebView's native `prefers-color-scheme`/theme handling, not forced algorithmic darkening.
2. **Never convert a real input event into a synthetic event of a different type** (e.g., mouse `MotionEvent` → synthetic touch `MotionEvent`). Deliver events to WebView exactly as the OS reports them. If a specific device has a broken input driver, that is an OS/OEM-level bug outside this app's ability to safely work around without breaking other devices — do not add conversion/interception logic to compensate for it.
3. **Never add device-, chipset-, OEM-, or box-brand-specific logic anywhere.** All input, rendering, and performance fixes must be generic and based on documented, standard Android/WebView APIs. If a bug only reproduces on one brand of set-top box, investigate whether it's actually a general bug being *masked* on other devices (it usually is) before assuming it needs device-specific handling.
4. **Every WebView instance must have `onRenderProcessGone` handled**, recovering gracefully (recreate WebView + reload for the foreground tab, silent state-clear for background tabs) rather than letting a renderer crash take down the whole app.
5. **Never use `RecyclerView.Adapter.notifyDataSetChanged()`** for anything other than a genuine full-list replacement (e.g., "clear all"). Use `notifyItemChanged`/`Inserted`/`Removed`/`RangeChanged`, or `DiffUtil` for arbitrary list changes, computed off the main thread for large lists.
6. **Never perform blocking I/O (`runBlocking`, synchronous disk/DB/network calls) on the main thread or on a WebView callback thread** (`shouldInterceptRequest`, `onPageStarted`, etc.). Use coroutines with proper dispatchers and, where a fast synchronous answer is needed, an in-memory cache checked first.
7. **Any event-dispatch or drawing code that can re-enter itself** (directly or through a chain of helper calls) **must update its guard/state flag *before* the call that can cause re-entry, not after.** (See §5, CursorLayout StackOverflow incident — this exact ordering mistake caused an infinite-recursion crash.)
8. **Modifier-key handling (Ctrl/Alt/Shift/Meta) in any key-recording or key-matching code must explicitly treat pure-modifier keycodes specially.** A modifier key generates its own key-down/key-up events; code that records "the last key event" naively will get overwritten by the modifier's own release. Always ignore pure-modifier keycodes when determining the "main" key of a combo.
9. **Ad/tracker blocking must always be a real, functioning engine** — never a stub that returns `false` unconditionally while the Settings UI implies it's active. If disabling ad blocking is ever needed for debugging, gate it behind an explicit debug flag, never ship it silently disabled.
10. **Background tab memory management must be LRU-based and configurable, never fully disabled and never fully unbounded.** Tabs beyond `Config.maxLiveTabs` should be trimmed (state-saved, WebView released) oldest-first, transparently restored on revisit. The OS-level `onTrimMemory`/`onLowMemory` reactive safety net must always remain in place regardless of any proactive LRU logic.
11. **The system clipboard listener must guard against feedback loops** (the app's own clipboard writes must not create duplicate history entries) **and must respect incognito mode and foreground-only clipboard access** (Android 10+ restricts background clipboard reads — do not attempt them).
12. **Any new UI list, popup, or dialog added to the app (including new Sidebar popups) must follow the same standards as the rest of the app** — precise RecyclerView updates (rule 5), no blocking calls (rule 6), and must be reachable/dismissible correctly via D-pad, keyboard, and mouse alike.
13. **Page zoom logic lives exclusively in `PageZoomController.kt` and implements Chrome-desktop-style density zoom via dynamic `<meta name="viewport">` width override injected at document-start and on zoom changes. Native `WebView.zoomIn()`/`zoomOut()`/`zoomBy()` was replaced because it performed photo-style magnification without responsive layout reflow. Do NOT reintroduce `textZoom`, `setInitialScale()`, custom CSS `zoom` property injection, or switch back to native `zoomIn()`/`zoomOut()` as the primary zoom mechanism.**

---

## 4. Quality Bar — "PC Chrome Level"

Before considering any browsing-related feature "done," it must pass this bar:

- **Rendering:** A website must look pixel-identical (modulo the WebView/Chromium version's own rendering) to how it looks in desktop Chrome or stock mobile Chrome — no color shifts, no layout shifts, no missing/misplaced elements caused by this app's own code.
- **Zoom:** Must feel as smooth as native Chrome pinch-zoom (GPU-scale, not layout-reflow), and must never displace page elements.
- **Scrolling:** Must feel as smooth as stock WebView/Chrome scrolling — no jank introduced by app-level touch/scroll interception or heavy injected JS.
- **Text selection & copy/paste:** Click-drag mouse selection must show the native blue highlight and must copy correctly, exactly like desktop Chrome. Selected/copied text must also appear in the app's own Clipboard Manager.
- **Right-click:** Must behave predictably and be clearly either (a) the app's own page-action menu (Refresh/Share/Copy/Open External/etc., current behavior) or (b) deferring to page-native context behavior — whichever direction is chosen, it must be intentional and documented here, not an accidental side effect of input-handling code elsewhere.
- **Keyboard shortcuts:** Every shortcut configurable in Settings must actually fire reliably on a physical keyboard, matching what was recorded.
- **Multi-input parity:** Every core browsing action (click, scroll, zoom, select text, switch tabs, open new tab, navigate back) must work correctly via D-pad/remote, physical keyboard, and physical mouse — and via touch on tablets — without any one input method's handling code interfering with another's.
- **Stability:** No crashes from heavy JS/WASM sites (AI chat apps, etc.), no crashes from input-handling recursion, no crashes from popup/window teardown races.
- **Performance under load:** Opening 8+ tabs, switching between them, and scrolling long lists (history, downloads) must stay smooth; memory must stay bounded via the LRU tab-trimming policy, not by never releasing anything.

---

## 5. Incident Log — Bugs Already Found & Fixed (do not reintroduce)

Keep this list updated whenever a significant bug is found and fixed. This is the project's institutional memory.

| # | Bug | Root cause | Fix applied | Status |
|---|---|---|---|---|
| 1 | App crash when opening JS/WASM-heavy sites (AI Studio, Claude.ai, Gemini) | `onRenderProcessGone` was never overridden; renderer OOM crash took down the whole app | Implemented `onRenderProcessGone`: destroy crashed WebView safely, recreate + reload for foreground tab, silent state-clear for background tabs | ✅ Fixed |
| 2 | Ad blocking did nothing despite a Settings toggle | `isAd()`/`isAdBlockingEnabled()` hardcoded to return `false`/`false` | Real engine (Brave `AdBlockClient`) wired up, EasyList loaded/parsed/cached on a background thread | ✅ Fixed |
| 3 | Header ↔ native-home-grid D-pad navigation was asymmetric (down then up didn't return to the same control) | Two different, inconsistent column-mapping formulas used for the down vs. up direction | Both directions now use the same `headerViews` list and consistent ratio-mapping | ✅ Fixed |
| 4 | Top-tab-bar DOWN always focused the Home icon, even if hidden | No visibility check before requesting focus | Now uses only currently-visible header views | ✅ Fixed |
| 5 | Popup/new-window check blocked the main thread | `runBlocking(Dispatchers.Main.immediate)` around a DB query in `shouldBlockNewWindow` | In-memory cache checked first; async background fetch on cache miss | ✅ Fixed |
| 6 | Home-page favicon loading blocked the WebView resource thread | `runBlocking` around a favicon fetch | Fast local cache/disk check first; async fallback fetch on miss | ✅ Fixed |
| 7 | Home-button press felt laggy | `onPause()` saved tab state via `runBlocking` on the main thread | Changed to an async coroutine (`Dispatchers.IO`) | ✅ Fixed |
| 8 | App/tabs could be killed quickly in the background, with no "recent apps" concept on Android TV | No foreground service existed to raise process priority | Added `BrowserKeepAliveService`, lifecycle-driven via `ProcessLifecycleOwner`, configurable via `Config.keepAliveInBackground` | ✅ Fixed |
| 9 | Background tabs never released memory proactively | `trimMemory()` only ran reactively on OS `onTrimMemory` callbacks | Added LRU-based proactive trimming beyond `Config.maxLiveTabs`, with state-save-then-restore; OS reactive trimming kept as a safety net | ✅ Fixed |
| 10 | Mouse click-drag text selection showed no blue highlight; drag gestures could freeze/crash the WebView | `HardwareInputManager` converted real mouse `MotionEvent`s into synthetic finger-touch events, breaking Chromium's native mouse-drag-select handling and creating inconsistent pointer-type semantics (native hover as mouse, click/drag as fake touch) | Removed the synthetic-touch-conversion layer entirely; native mouse events now reach WebView unmodified | ✅ Fixed |
| 11 | Text/links copied from a webpage never appeared in the app's own Clipboard Manager | Only "Copy Link" (context menu) and manual entries were recorded; WebView's native page-text copy went straight to the OS clipboard, bypassing the app entirely | Added an app-level `OnPrimaryClipChangedListener`, with feedback-loop guarding and incognito/foreground-only safety | ✅ Fixed |
| 12 | Recorded keyboard shortcuts using a modifier (Ctrl+T, Ctrl+Tab, etc.) silently failed to fire | The shortcut-recording dialog's `onKeyUp` fired for every key release, including the modifier key's own release, which overwrote the just-recorded correct combo with `keyCode=<modifier>, modifiers=0` | Recording logic now explicitly ignores pure-modifier keycodes as the "main" recorded key; added a runtime safety-net that treats any already-corrupted saved shortcut as invalid, plus a one-time migration to clear pre-existing corrupted entries | ✅ Fixed |
| 13 | App crashed with `StackOverflowError` in `CursorLayout.dispatchTouchEvent` during certain D-pad-cursor-then-input sequences | `CursorDrawerDelegate.hideCursor()` called `dispatchMotionEvent()` → `surface.dispatchTouchEvent()` (re-entering `CursorLayout.dispatchTouchEvent`, which unconditionally calls `hideCursor()` again) *before* resetting the `dpadCenterPressed` guard flag to `false`, causing unbounded recursion | Moved `dpadCenterPressed = false` to before the recursive `dispatchMotionEvent()` call | ✅ Fixed |
| 14 | Certain interactive web elements (dropdowns/buttons in JS-heavy sites) failed to register mouse clicks on some devices, while scrolling and simple clicks worked fine | Root cause not fully isolated at the app level — evidence pointed at a system/driver-level input-delivery quirk on specific hardware (out of this app's control), compounded by the now-removed synthetic-touch-conversion hack which was an incomplete attempt to work around it | The device-specific workaround was removed per the "no jugaad" principle (see §3, rule 3); any remaining device-specific click issues are treated as a platform/OEM limitation, not something to special-case in app code | 🟡 Accepted limitation — do not reintroduce device-specific workarounds for this |
| 15 | Websites rendered with visibly wrong colors (inverted/dark navy backgrounds, discolored images) | Chromium's "Algorithmic Darkening"/Force Dark was applied automatically whenever the device's system UI was in night/dark mode, defaulted to enabled | Removed entirely — websites now always render their true, unmodified colors regardless of system theme | ✅ Fixed |
| 16 | Zoomed pages had page elements (e.g. Google's account/apps dropdown) rendering in the wrong screen position; zoom/scroll felt less smooth than stock Chrome | Custom JS-injected CSS `zoom` property on `<html>`, forcing width/min-width percentage overrides, broke `position: fixed`/`absolute` coordinate math and forced layout reflow instead of using native GPU-scale zoom | Removed the CSS-zoom injection; TV-remote zoom controls now call WebView's native `zoomIn()`/`zoomOut()`/`zoomBy()` (already enabled via `setSupportZoom`/`builtInZoomControls`) | ✅ Fixed |
| 17 (open) | `notifyDataSetChanged()` still used in several adapters (History, Downloads, Favorites, and multiple new `*SidebarPopup.kt` files) | Precise-update fix was only applied to the highest-frequency adapters (`TopTabsAdapter`, `TabsRowAdapter`); newer sidebar UI was added afterward using the old pattern | Not yet fixed — apply rule 5/§3 to all remaining adapters, including any new ones | 🟡 Open — low priority, not a functional bug, but should be closed out |
| 18 | Page zoom felt like photo magnification instead of desktop-Chrome-style density change; responsive pages never reflowed into desktop layouts | `WebView.zoomIn()`/`zoomOut()`/`zoomBy()` perform photo-style uniform magnification of the rendered bitmap without changing layout viewport width | Rewrote `PageZoomController.kt` to dynamically overwrite `<meta name="viewport">` with target CSS viewport width injected at document-start and on zoom changes, matching desktop Chrome's Ctrl+/Ctrl- density reflow behavior without breaking fixed-position elements | ✅ Fixed |
| 19 | Desktop mode toggle stuck in desktop loop; switching back to mobile mode failed to clear desktop user agent or restore mobile zoom | `WebViewEx.isDesktopModeEnabled()` checked `settings.userAgentString?.contains("Windows")`, which remained true after user untoggled desktop mode; also `applyDesktopMode()` skipped setting userAgentString when null, preventing WebView default UA restoration | Centralized desktop mode detection in `Config.isDesktopMode()`, unconditionally cleared `settings.userAgentString = null` to restore WebView default mobile UA, aligned initialization sequence in `WebViewWebEngine`, and fortified `PageZoomController` runtime script with `MutationObserver` | ✅ Fixed |

---

## 6. Verification Checklist — Run After *Any* Change

Because this app has a history of one fix quietly breaking another area, treat this as mandatory regression testing after any non-trivial change, not just for the specific feature touched:

- [ ] Open a heavy JS/WASM site (an AI chat app, etc.) — no crash, no freeze.
- [ ] Ads/trackers are actually blocked on a known ad-heavy site.
- [ ] D-pad navigation: header ↔ home-grid ↔ top-tab-bar, in both directions, lands on sensible/expected focus targets.
- [ ] Physical mouse: hover, left-click, right-click, and click-drag text selection (blue highlight appears) all work on a real webpage; copied text appears in Clipboard Manager.
- [ ] Physical keyboard: at least one modifier-based shortcut (e.g. Ctrl+T) fires correctly.
- [ ] Open 8+ tabs, switch between them rapidly — no visible jank, no crash.
- [ ] Background the app for a few minutes and resume — process should still be alive under normal conditions.
- [ ] Zoom in/out on a site with a fixed-position dropdown/menu (e.g. google.com's account menu) — menu stays correctly positioned at every zoom level.
- [ ] Load a page in both system light mode and system dark mode — page colors are identical (true to the site) in both.
- [ ] Scroll a long page — feels smooth, no stutter.
- [ ] No new `runBlocking`, synchronous I/O on main/WebView threads, or `notifyDataSetChanged()` introduced by the change (grep for these before finishing).
- [ ] No device/chipset/OEM-specific code introduced.

---

## 7. When In Doubt

If a requested feature or fix seems like it would require faking, intercepting, or re-implementing something the Android/WebView/Chromium platform already does — **stop and find the native API first.** State explicitly in your response which native API/mechanism you're using and why, before writing any custom logic. If no native mechanism exists for a genuine edge case, implement the smallest, most general fix possible, and add an entry to the Incident Log (§5) so future work doesn't reverse it.
