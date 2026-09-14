# AI Studio Prompt — Gothwad Browser: Fix Page Zoom (Isolate Into Its Own Protected File)

You are working on the Gothwad Browser project. The page-zoom feature (TV-remote zoom-in/zoom-out controls) has now been broken **twice** by two different incorrect implementations:
1. First attempt: a custom CSS `zoom` property injected via JavaScript — broke `position: fixed` element positioning site-wide.
2. Second attempt (current, still broken): using `WebSettings.textZoom` (which only scales font size, not buttons/images/layout) combined with `WebView.setInitialScale()` (a one-time initial-render hint, not a live zoom control, which conflicts with pages' own `<meta name="viewport">` tags and breaks layout on some sites).

**Neither attempt used the actual correct API.** This task fixes it properly this time, and — because this exact feature has now regressed twice — isolates all of its logic into one dedicated, clearly-marked file so that unrelated future changes are far less likely to touch or break it by accident.

## Task 1 — Implement zoom using WebView's real native zoom API

Android's `WebView` class has built-in page-zoom methods that scale the **entire rendered page** (text, images, buttons, layout — everything, uniformly, like a camera zoom) without triggering a layout reflow and without any custom CSS/JS injection:
- `WebView.zoomIn(): Boolean`
- `WebView.zoomOut(): Boolean`
- `WebView.zoomBy(factor: Float)`
- `WebView.canZoomIn(): Boolean` / `WebView.canZoomOut(): Boolean` (deprecated but still functional — if targeting a newer API level where these are removed, track zoom-limit state manually based on `Config.WEB_PAGE_ZOOM_PERCENT_MIN`/`MAX` instead)

These already work correctly today in stock Chrome/WebView and are already enabled in this codebase (`setSupportZoom(true)`, `builtInZoomControls = true` in `WebViewEx.kt`'s init block) — they are simply not being called anywhere. This task's entire job is to wire the TV-remote zoom controls to these real methods instead of `textZoom`/`setInitialScale()`.

1. Remove the `textZoom` assignment and `setInitialScale()` call from `WebViewEx.kt`'s `applyZoom()` and `onPageStartedResetZoom()` — these must no longer be used to implement page zoom. (If `textZoom` is desired as a *separate*, distinct accessibility "larger text" feature independent of page zoom, that's a different feature with a different UI control — do not implement that as part of this task unless explicitly asked; for now, simply stop using `textZoom`/`setInitialScale` for the zoom-in/zoom-out buttons.)
2. Implement zoom using `WebView.zoomIn()` / `zoomOut()` / `zoomBy()` instead — see Task 2 for exactly where this logic should live.
3. Since native `WebView` zoom works in discrete steps controlled by the WebView/Chromium engine itself (not arbitrary percentages), adapt `Config.STANDARD_ZOOM_LEVELS`/`WEB_PAGE_ZOOM_PERCENT_MIN`/`MAX` handling as needed: either (a) call `zoomIn()`/`zoomOut()` directly and track/display the resulting zoom level for UI purposes via `WebView.getScale()` (available via reflection-free public API on modern WebView, or via `onScaleChanged` in `WebViewClient`) rather than trying to force an exact arbitrary percentage, or (b) use `zoomBy(factor)` with a calculated factor to approximate the desired standard levels. Prefer option (a) for correctness and simplicity — don't fight the engine's own zoom-stepping behavior.
4. Verify per-tab zoom level persistence still works (each tab should remember its own zoom level when switching tabs, as the current `tab.scale` field suggests is already intended) — using `WebView.getScale()`/`zoomBy()` to restore a tab's remembered zoom level when it becomes active again, rather than `setInitialScale()`.

## Task 2 — Isolate all page-zoom logic into one dedicated, protected file

Create a new file: `app/src/main/java/com/gothwad/browser/webengine/webview/PageZoomController.kt`

Move **all** zoom-related logic currently scattered across `WebViewEx.kt` (`applyZoom`, `onPageStartedResetZoom`, `currentAppliedZoomPercent`) and `WebViewWebEngine.kt` (`zoomIn`, `zoomOut`, `zoomBy`, `canZoomIn`, `canZoomOut`, `setPageZoom`) into this single new file, as a class (e.g. `class PageZoomController(private val webView: WebViewEx, private val tab: WebTabState)`) that owns:
- `zoomIn()`, `zoomOut()`, `zoomBy(factor: Float)`
- `canZoomIn()`, `canZoomOut()`
- `restoreZoomForTab()` (called when a tab becomes active, to reapply its remembered zoom level via the native API)
- `onPageStarted()` hook (called from the existing page-load lifecycle, to handle per-site zoom reset/restore correctly using the native API only)

`WebViewWebEngine.kt` and `WebViewEx.kt` should hold a single instance of `PageZoomController` and delegate their existing public `zoomIn()`/`zoomOut()`/etc. methods to it (keep the existing public method signatures used by `CursorMenuView.kt` and anywhere else in the app unchanged — only the internal implementation moves and gets fixed).

At the very top of `PageZoomController.kt`, add this exact comment block, unmodified:

```kotlin
/**
 * ⚠️ PROTECTED FILE — READ BEFORE MODIFYING ⚠️
 *
 * This file has been rewritten twice already after two separate incorrect
 * implementations broke page zoom in different ways:
 *   1. A custom CSS `zoom` property injected via JavaScript — broke
 *      position:fixed element placement site-wide.
 *   2. WebSettings.textZoom + WebView.setInitialScale() — only scaled text
 *      (not buttons/images/layout) and broke layout on sites with their own
 *      viewport meta tag.
 *
 * The ONLY correct way to implement page zoom in an Android WebView is via
 * WebView's own native zoom API: zoomIn(), zoomOut(), zoomBy(float),
 * canZoomIn(), canZoomOut(). These already scale the entire rendered page
 * (text, images, buttons, layout) uniformly, exactly like Chrome's own
 * zoom, with no custom CSS/JS injection and no layout reflow.
 *
 * Do NOT reintroduce textZoom, setInitialScale(), or any custom CSS/JS
 * zoom injection into this file, or move zoom logic back into WebViewEx.kt
 * or WebViewWebEngine.kt, without a very deliberate, explicit reason —
 * if you are an AI agent modifying this codebase for an unrelated task,
 * do not touch this file unless the task is specifically about page zoom.
 */
```

## Task 3 — Update AGENTS.md

Add page zoom explicitly to the Incident Log (§5) in the project's `AGENTS.md` as its own tracked entry noting it has now regressed twice via two different wrong implementations, and add a new line to the Hard Rules (§3) stating: "Page zoom logic lives exclusively in `PageZoomController.kt` and must use only WebView's native `zoomIn()`/`zoomOut()`/`zoomBy()` API — never `textZoom`, `setInitialScale()`, or custom CSS/JS zoom injection. Do not move this logic elsewhere or reimplement it inline in `WebViewEx.kt`/`WebViewWebEngine.kt` without explicit instruction to do so." If `AGENTS.md` doesn't exist yet in this exact project copy, create it following the same structure as the browser project's existing one (ask for the reference file if unsure, or create a minimal version covering just this rule if the full file isn't available).

## Verification checklist

- [ ] Zooming in/out via the remote controls scales the entire page — text, images, buttons, and layout all resize together, matching stock Chrome/WebView pinch-zoom behavior.
- [ ] Test on a site with its own explicit `<meta name="viewport">` tag (most modern sites) — zooming does not break or shift the layout.
- [ ] Test on both a "mobile" rendering site and a "desktop mode" site — zoom works correctly and consistently on both.
- [ ] Switching between tabs with different remembered zoom levels correctly restores each tab's own zoom level.
- [ ] No `textZoom` assignment or `setInitialScale()` call remains anywhere related to the page-zoom feature.
- [ ] All zoom logic lives in `PageZoomController.kt` only; `WebViewEx.kt` and `WebViewWebEngine.kt` only contain thin delegation calls to it.
- [ ] The protective comment block is present at the top of `PageZoomController.kt`.
- [ ] `AGENTS.md` is updated per Task 3.

Implement all three tasks fully in this session. Do not summarize what you would do — actually write and wire up the code.
