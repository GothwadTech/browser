# AI Studio Prompt — Gothwad Browser: Correct Page Zoom to Chrome-Desktop-Style Density Zoom (Not Photo-Scale Zoom)

You are working on the Gothwad Browser project. `PageZoomController.kt` (a protected file, see its existing header comment) currently implements page zoom using `WebView.zoomIn()`/`zoomOut()`/`zoomBy()` — Android's native page-scale zoom. This was a **mistaken choice of technique for the intended feature**, not a bug in the implementation itself. It has been confirmed working exactly as that API is documented to work, but that is not what the desired feature needs. Correct it as described below.

## Why the previous approach was wrong for this feature

`WebView.zoomIn()`/`zoomOut()`/`zoomBy()` perform **photo-style visual magnification**: the already-rendered layout is scaled up or down as a flat image, like zooming into a photograph. The page's own content arrangement never changes — the same elements, in the same relative positions, just bigger or smaller.

The actual desired behavior is what desktop Chrome does when you press **Ctrl+ / Ctrl-**: the page's *effective viewport width* changes, which causes the page's own responsive CSS to reflow into a denser or looser layout (e.g. a mobile hamburger menu becomes a full horizontal nav bar, a single column becomes multiple columns, more list items become visible without scrolling) — the browser then fits that reflowed content to the screen. This is fundamentally a **layout/reflow change**, not a post-render visual scale.

## The correct technique: dynamic viewport-width override (not CSS `zoom` property, not native scale-zoom)

This is a third, distinct technique from both previous attempts — it is not the same as the original broken CSS `zoom` property hack (that operated as a post-layout render-transform and broke `position: fixed` math), and it is not native scale-zoom (which never reflows layout at all). This technique controls the actual CSS layout viewport width that the page's own responsive design responds to — the same mechanism real mobile browsers use for "Request Desktop Site," just made continuously adjustable instead of a binary on/off.

Implement it as follows in `PageZoomController.kt`:

1. Maintain a "density level" per tab (reuse the existing `tab.scale`/`currentAppliedZoomPercent` fields and `Config.STANDARD_ZOOM_LEVELS`/`WEB_PAGE_ZOOM_PERCENT_MIN`/`MAX` — same persistence mechanism as before, just change what the underlying number controls).
2. Ensure `settings.useWideViewPort = true` and `settings.loadWithOverviewMode = true` are set on the `WebView` (check `WebViewEx.kt`'s init block — these may already be set for the existing Desktop Mode feature; if so, reuse them, don't duplicate).
3. On each page load (`onPageStarted`, and again via a `WebViewCompat.addDocumentStartJavaScript` injection so it applies before the page's own scripts run, similar to the pattern already used elsewhere in this codebase for other document-start injections — check `WebViewEx.kt` for the existing pattern to follow), run JavaScript that:
   - Finds the page's existing `<meta name="viewport">` tag, or creates one if absent.
   - Sets its `content` attribute to `width=<target_css_width>, initial-scale=<computed_scale>` where `target_css_width` is calculated from the current density level (e.g., at 100% density, use a normal mobile width like `device-width`; as the user "zooms out" toward desktop-density, increase `target_css_width` toward typical desktop values like 980-1400 CSS pixels; as they "zoom in," decrease it back toward `device-width`).
   - The exact width-to-density-percent mapping should reuse `Config.STANDARD_ZOOM_LEVELS` as the discrete steps (same UI/button behavior as before), just reinterpreted as viewport-width steps instead of scale-factor steps.
4. Re-run this same injection whenever the density level changes on an already-loaded page (not just at page-start) — call `evaluateJavascript` with the same meta-tag-update logic immediately when `zoomIn()`/`zoomOut()`/`setPageZoom()` are invoked, so changing zoom on a page you're already viewing takes effect immediately without requiring a reload.
5. Keep the existing public method signatures unchanged (`zoomIn()`, `zoomOut()`, `zoomBy()`, `canZoomIn()`, `canZoomOut()`, `setPageZoom()`, `restoreZoomForTab()`, `onPageStarted()`) so `CursorMenuView.kt` and any other existing caller needs no changes — only the internal mechanism changes.
6. Remove the `onScaleChanged` listener/hook if it was only there to track native pinch-zoom scale changes for the old scale-based approach — with viewport-width-based density zoom, there is no native pinch-zoom scale to track in the same way (pinch-zoom, if the browser still allows it via `setSupportZoom`/`builtInZoomControls`, can remain as a completely separate, independent, orthogonal photo-style zoom layered on top if you want, but do not conflate its scale tracking with the density-level state — keep them as two unrelated axes if pinch-zoom is kept at all).

## Relationship to the existing "Desktop Mode" toggle — reconcile, don't duplicate

This codebase already has a separate `Desktop Mode` feature (user-agent override + `useWideViewPort`) elsewhere in `WebViewEx.kt`. This new density-zoom feature uses an overlapping mechanism (viewport width). Reconcile them cleanly:
- Desktop Mode's user-agent override should remain independent and unaffected by density-zoom — a user can have Desktop Mode on or off regardless of their chosen density-zoom level.
- Make sure the two features' viewport-width logic doesn't fight each other: if Desktop Mode is already forcing a fixed wide viewport, density-zoom's dynamic width adjustment should still apply on top of/consistently with whatever Desktop Mode has set, not silently overwrite or be overwritten by it in a way that causes flickering or inconsistent state between page loads. Decide on a single source of truth for the final `content` value written to the viewport meta tag that accounts for both settings together, and implement that clearly.

## Update the protected header comment in `PageZoomController.kt`

Replace the existing header comment (which currently — incorrectly — recommends native `WebView.zoomIn()/zoomOut()/zoomBy()` as "the only correct way") with an updated version reflecting what's actually been learned across all three attempts:

```kotlin
/**
 * ⚠️ PROTECTED FILE — READ BEFORE MODIFYING ⚠️
 *
 * This file has been rewritten three times after three different
 * implementations of "page zoom" each got something wrong:
 *   1. A custom CSS `zoom` property injected via JavaScript — broke
 *      position:fixed element placement site-wide (post-layout render
 *      transform, not a real layout change).
 *   2. WebSettings.textZoom + WebView.setInitialScale() — only scaled
 *      text (not buttons/images/layout) and broke layout on sites with
 *      their own viewport meta tag.
 *   3. WebView's native zoomIn()/zoomOut()/zoomBy() — technically correct
 *      "page scale" zoom (like zooming into a photo), but NOT what this
 *      app's zoom feature is supposed to do. The desired behavior is
 *      Chrome-desktop-style density zoom (Ctrl+/Ctrl-): changing the
 *      page's effective CSS viewport width so its own responsive layout
 *      reflows to show more/less content, not just visually scaling a
 *      fixed layout.
 *
 * The CORRECT technique (currently implemented): dynamically overwrite
 * the page's <meta name="viewport"> content attribute's width value,
 * injected via document-start JavaScript and re-applied immediately when
 * the zoom level changes on an already-loaded page. This causes the
 * page's own CSS to reflow naturally (exactly like resizing a real
 * browser window), with the browser correctly recalculating
 * position:fixed elements against the new viewport width — this is NOT
 * the same mechanism as attempt #1's CSS `zoom` property hack.
 *
 * Do NOT reintroduce textZoom, setInitialScale(), a CSS `zoom` property
 * injection, or switch back to native WebView.zoomIn()/zoomOut()/zoomBy()
 * as the primary zoom mechanism, without a very deliberate, explicit
 * reason. If you are an AI agent modifying this codebase for an
 * unrelated task, do not touch this file unless the task is specifically
 * about page zoom.
 */
```

## Update AGENTS.md

Update the existing Incident Log entry (#18) and Hard Rule (#13) about page zoom to reflect this third correction — note that the native `zoomIn()`/`zoomOut()`/`zoomBy()` approach, while functioning correctly as documented, was the wrong technique for this specific feature's intended UX, and that the final correct technique is dynamic viewport-width meta-tag override via document-start JavaScript injection, reconciled with the separate Desktop Mode feature.

## Verification checklist

- [ ] Zooming out on a responsive website causes its layout to visibly reflow into a denser/wider arrangement (e.g. more columns, full nav bar instead of hamburger menu, more visible content) — not just the same layout shrunk like a photo.
- [ ] Zooming in reverses this back toward the normal mobile-width layout.
- [ ] Test specifically on google.com with its account/apps-grid dropdown open, at multiple zoom/density levels — the dropdown must stay correctly positioned at every level (this is the specific regression from attempt #1 — confirm this new technique doesn't reintroduce it).
- [ ] Changing zoom level on a page that's already loaded (not just on fresh navigation) takes effect immediately, without requiring a manual reload.
- [ ] Desktop Mode (on or off) and density-zoom (at any level) work correctly in all four combinations without conflicting or flickering.
- [ ] Per-tab zoom/density level is still remembered correctly when switching between tabs.
- [ ] No `textZoom`, `setInitialScale()`, CSS `zoom` property injection, or native `WebView.zoomIn()/zoomOut()/zoomBy()` remains as the density-zoom mechanism.
- [ ] The protected header comment in `PageZoomController.kt` is updated to the corrected version above.
- [ ] `AGENTS.md` is updated per the instructions above.

Implement this fully in this session. Do not summarize what you would do — actually write and wire up the code.
