# Android

`assets/android/` holds a working Android engine with the same rules as the web one: the device
frame, the rounded-glass clip, the bottom fade, the shimmer, 3 loops then a rest frame, pausing
when hidden, and a still rest frame with animations off. Copy it into the app rather than
re-implementing it, so films look the same on every platform.

It needs Material Components for Android 1.10 or later (for the surface container colours) and
`androidx.core`. Kotlin, minSdk 26.

## Files

| File | What it does |
|---|---|
| `SkeletonFilmView.kt` | The engine: an abstract `View` that owns the device, playback, the glass clip, the shimmer and the parts a script draws with |
| `EmptyFilmState.kt` | Builds the state into a parent: the 380dp film slot, title, one-line description, a filled primary `MaterialButton` and an optional outlined secondary one |
| `ProjectsFilmView.kt` | An example film (the same story as `example-film.js`). Start a new film from it |

Copy all three into one package and change `package com.example.emptyfilm` to yours.

## Wire the theme

The film reads its colours from the Material 3 theme by default:

| Film token | Theme attribute |
|---|---|
| `PAGE` | `colorSurfaceContainerLow` |
| `CARD` | `colorSurfaceContainerLowest` by day, `colorSurfaceContainerHigh` by night |
| `RAISED` | `colorSurfaceContainerLowest` by day, `colorSurfaceContainerHighest` by night |
| `INK`, `INK_SOFT` | `colorOnSurface`, `colorOnSurfaceVariant` |
| `OUTLINE` | `colorOutlineVariant` |
| `ACCENT`, `ON_ACCENT` | `colorPrimary`, `colorOnPrimary` |

If the app has its own tokens, override `readColors()` once in a base film and return them:

```kotlin
override fun readColors() = FilmColors(
    page = ContextCompat.getColor(context, R.color.surface_page),
    card = ContextCompat.getColor(context, R.color.surface_card),
    // …raised, ink, inkSoft, outline, accent, onAccent
)
```

Never write a colour in a script; scripts name tokens (`CARD`, `ACCENT`). The film reads the colours
again after a configuration change (night mode). If the theme switches some other way, call
`readTheme()`.

## Write a film

Subclass `SkeletonFilmView`. Every coordinate is in dp, and the numbers are the same as in a web
script, so a film can be ported line by line.

```kotlin
import com.example.emptyfilm.FilmTone.*

class ExampleFilmView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : SkeletonFilmView(context, attrs) {

    override val loopSeconds = 9.4f
    override val restSeconds = 6.4f   // the frame that explains the feature on its own
    override val pace = 1.3f
    override val label = "A phone showing … then …"   // the whole story, for TalkBack
    // override val device = FilmDevice.BROWSER        // for a desktop feature

    override fun drawToolbar(t: Float) {
        // Device coordinates, in the toolbar band (toolbarY).
    }

    override fun drawScreen(t: Float, w: Float) {
        // Content coordinates: w = contentWidth (328 phone, 640 browser), scaled to fit for you.
        val alpha = ease(t, 0f, .4f) * (1 - ease(t, FADE_OUT, FADE_OUT + .6f))
        shape(CARD, 0f, 16f, w, 48f, 16f, alpha)
        circle(CARD, 28f, 40f, 14f, alpha)
        bar(CARD, 54f, 34f, w * .4f, 10f, alpha)
    }

    override fun drawOverScreen(t: Float) {
        // Device coordinates over the screen (screen.left/top/right): touches, sheets, scrims.
        touch(t, screen.left + 60, screen.top + 120, TAP, TAP + .5f)
    }

    private companion object {
        // Beats, in seconds into a loop, before the pace stretches them.
        const val TAP = 1.2f
        const val FADE_OUT = 8.6f
    }
}
```

What a film can call:

| Call | Draws |
|---|---|
| `bar(surface, x, y, w, h, alpha)` | A skeleton bar on `surface` (`PAGE`, `CARD`, `RAISED`), shimmering |
| `block(surface, x, y, w, h, r, alpha)` | A skeleton block with corners `r` |
| `circle(surface, cx, cy, r, alpha)` | A skeleton circle (icon well, avatar) |
| `shape(tone, x, y, w, h, r, alpha)` | A solid shape in a colour token: a card, a sheet, the accent |
| `shapeColor(color, …)` | The same in any colour, such as a mix of two tokens (`color(tone)` gives a token's) |
| `stroke(tone, x, y, w, h, r, alpha)` | A hairline edge |
| `toggle(x, cy, on, alpha)` | A switch, `on` from 0 to 1 |
| `touch(t, x, y, at, until, alpha)` | A finger: a dot landing at `at`, its ring spreading until `until` |
| `pointer(t, path, clicks, alpha)` | A mouse pointer for desktop films, along `path` (`PointerStop(at, x, y)`s), pressing at each second in `clicks` |
| `toDevice(x, y)` | Content coordinates to device ones, to aim a touch or pointer at something `drawScreen` drew |
| `shake(t, at)` | An x offset for "that did nothing": `canvas.translate(shake(t, TRY), 0f)` |
| `screenOff(t, at, until)` | The screen fading to black, as when it locks, and back |
| `canvas` | The canvas, for anything else (save and restore around your own transforms) |
| `screen`, `toolbarY`, `filmHeight` | Where the screen is, the toolbar's middle, the view's height (all in dp) |

Timing: `ease(t, a, b)` (smoothstep, for fades and moves), `linear(t, a, b)` (for spreads and
progress), `pulse(t, a, b)` (up and back down). Write beats as constants, not inline numbers.

Pass the surface a skeleton sits on: a bar on a card uses `CARD`, a bar straight on the screen
uses `PAGE`. Everything drawn in the three hooks is clipped to the screen's rounded glass.

## Show it

```kotlin
EmptyFilmState.show(
    parent = binding.content,
    film = ProjectsFilmView(context),
    title = getString(R.string.projects_empty_title),
    description = getString(R.string.projects_empty_desc),
    primary = getString(R.string.projects_new), onPrimary = ::openNewProject,
    // secondary = getString(R.string.projects_templates), onSecondary = ::openTemplates,
)
```

`show` builds the state from the theme's text appearances (`textAppearanceTitleLarge`,
`textAppearanceBodyLarge`) and `MaterialButton`. If the app has its own button or text components,
build the same structure with them instead: the film in a 380dp slot, then the title md under it,
the description xs under that, the primary xl under the description at the standard button
height, the secondary md under the primary (spacing in `EmptyFilmState.Spacing`).

The film starts when it is shown, pauses when its window is hidden, it is scrolled away or
detached, and carries on from there when it comes back. Call `restart()` to play it from the top,
for example when the user comes back to an empty tab.

## Checking it

- Record it on an emulator or test device: `adb shell screenrecord --time-limit 40 /sdcard/f.mp4`
  while opening the screen, pull it, and look at frames from each beat, in light and dark
  (`adb shell cmd uimode night yes`, then `no`). screenrecord stops writing frames when nothing on
  screen changes, so take the rest frame with `adb exec-out screencap -p` once the loops are done.
- Crop the top corners at full size: nothing past the rounded glass at any beat.
- Animations off: `adb shell settings put global animator_duration_scale 0`, open the screen, and
  two screenshots a few seconds apart must match the rest frame. Set it back to 1 afterwards.
- Send the app to the background mid-film and back: it resumes from the same beat.
